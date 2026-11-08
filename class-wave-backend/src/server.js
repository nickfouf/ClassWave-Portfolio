const express = require('express');
const http = require('http');
const { Server } = require('socket.io');
const cors = require('cors');
const mediasoup = require('mediasoup');
const jwt = require('jsonwebtoken');
require('dotenv').config();

const connectDB = require('./config/db');
const mediasoupConfig = require('./config/mediasoupConfig');
const roomManager = require('./mediasoup/RoomManager');
const authRoutes = require('./routes/authRoutes');
const courseRoutes = require('./routes/courseRoutes');
const chatRoutes = require('./routes/chatRoutes');
const Course = require('./models/Course');
const Message = require('./models/Message');
const { ChatClosedError, RoomChatService } = require('./services/RoomChatService');

const roomChatService = new RoomChatService({ Message, Course });

connectDB();

const app = express();
app.use(cors());
app.use(express.json());

app.use('/api/auth', authRoutes);
app.use('/api/courses', courseRoutes);
app.use('/api/chat', chatRoutes);

const server = http.createServer(app);
const io = new Server(server, {
  cors: {
    origin: '*',
    methods: ['GET', 'POST']
  }
});

app.set('io', io);
app.set('roomChatService', roomChatService);

const getRaisedHandsForRoom = (roomId) => {
  const room = io.sockets.adapter.rooms.get(roomId);
  if (!room) return [];

  return Array.from(room)
    .map((socketId) => io.sockets.sockets.get(socketId))
    .filter((participantSocket) => participantSocket?.handRaised && participantSocket.userId)
    .map((participantSocket) => ({
      userId: participantSocket.userId,
      username: participantSocket.username || 'Participant'
    }));
};

// Mediasoup workers
let workers = [];
let nextWorkerIdx = 0;

const createWorkers = async () => {
  const numWorkers = require('os').cpus().length;
  console.log(`[Mediasoup] Δημιουργία ${numWorkers} Workers...`);

  for (let i = 0; i < numWorkers; i++) {
    const worker = await mediasoup.createWorker({
      logLevel: mediasoupConfig.worker.logLevel,
      logTags: mediasoupConfig.worker.logTags,
      rtcMinPort: mediasoupConfig.worker.rtcMinPort,
      rtcMaxPort: mediasoupConfig.worker.rtcMaxPort,
    });

    worker.on('died', () => {
      console.error(`[Mediasoup] Ο Worker (PID: ${worker.pid}) κρασάρισε. Επανεκκίνηση...`);
      setTimeout(() => process.exit(1), 2000);
    });

    workers.push(worker);
  }
};

const getNextWorker = () => {
  const worker = workers[nextWorkerIdx];
  nextWorkerIdx = (nextWorkerIdx + 1) % workers.length;
  return worker;
};

// Socket events
io.on('connection', (socket) => {
  console.log(`[Socket] Νέος χρήστης συνδέθηκε: ${socket.id}`);

  socket.on('authenticate', (token) => {
    try {
      const decoded = jwt.verify(token, process.env.JWT_SECRET);
      socket.join(`user_${decoded.id}`);
      console.log(`[Socket] Χρήστης ${decoded.id} εγγράφηκε για dashboard updates.`);
    } catch (error) {
      console.error('[Socket] Αποτυχία αυθεντικοποίησης στο dashboard:', error.message);
    }
  });

  socket.on('subscribe-room-info', async ({ roomId } = {}) => {
    if (typeof roomId !== 'string' || !roomId) return;

    await socket.join(`info_${roomId}`);
    const count = io.sockets.adapter.rooms.get(roomId)?.size || 0;

    try {
      const course = await Course.findById(roomId)
        .select('isLive updatedAt')
        .lean();
      socket.emit('room-info-update', {
        roomId,
        count,
        isLive: course?.isLive === true,
        updatedAt: course?.updatedAt
      });
    } catch (error) {
      console.error(`[Socket] Failed to load room info for ${roomId}:`, error.message);
      socket.emit('room-participants-update', { roomId, count });
    }
  });

  socket.on('unsubscribe-room-info', ({ roomId }) => {
    socket.leave(`info_${roomId}`);
  });

  socket.on('join-room', async ({ roomId, userId, username, isMuted = true, isPaused = true }, callback) => {
    try {
      const room = io.sockets.adapter.rooms.get(roomId);
      if (room) {
        for (const existingSocketId of room) {
          const existingSocket = io.sockets.sockets.get(existingSocketId);
          if (existingSocket && existingSocket.userId === userId && existingSocket.id !== socket.id) {
            console.log(`[Room] Βρέθηκε stale connection (${existingSocketId}) για τον χρήστη ${userId}. Κλείσιμο transports...`);
            if (existingSocket.transports) {
              for (const transport of existingSocket.transports.values()) {
                transport.close();
              }
              existingSocket.transports.clear();
            }
            existingSocket.disconnect(true);
          }
        }
      }

      socket.userId = userId;
      socket.username = username;
      socket.microphoneMuted = Boolean(isMuted);
      socket.cameraPaused = Boolean(isPaused);
      socket.handRaised = false;
      socket.join(roomId);
      
      const worker = getNextWorker();
      const router = await roomManager.getOrCreateRoom(roomId, worker);

      try {
        await roomManager.getOrCreateActiveSpeakerObserver(roomId, (producer) => {
          const activeUserId = producer.appData?.userId;
          if (activeUserId) {
            io.to(roomId).emit('active-speaker-changed', { userId: activeUserId });
          }
        });
      } catch (observerError) {
        console.error('[Mediasoup] Αποτυχία αρχικοποίησης active-speaker detection:', observerError);
      }
      
      socket.to(roomId).emit('user-joined', { userId, username });
      console.log(`[Room] Ο ${username} μπήκε στο δωμάτιο ${roomId}`);

      const count = io.sockets.adapter.rooms.get(roomId)?.size || 0;
      io.to(`info_${roomId}`).emit('room-participants-update', { count });

      callback({
        success: true,
        rtpCapabilities: router.rtpCapabilities,
        raisedHands: getRaisedHandsForRoom(roomId)
      });

    } catch (error) {
      console.error('[Room] Σφάλμα κατά την ένταξη:', error);
      callback({ success: false, error: error.message });
    }
  });

  socket.on('getProducers', ({ roomId }, callback) => {
    const producers = [];
    const room = io.sockets.adapter.rooms.get(roomId);
    if (room) {
      for (const socketId of room) {
        if (socketId === socket.id) continue;

        const clientSocket = io.sockets.sockets.get(socketId);
        if (clientSocket && clientSocket.producers) {
          for (const [id, producer] of clientSocket.producers.entries()) {
            producers.push({
              producerId: producer.id,
              userId: clientSocket.userId || 'Unknown',
              username: clientSocket.username || 'Συμμετέχων',
              kind: producer.kind,
              isPaused: clientSocket.cameraPaused || false,
              isMuted: clientSocket.microphoneMuted ?? true
            });
          }
        }
      }
    }
    callback(producers);
  });

  socket.on('send-chat-message', async (payload = {}, callback) => {
    const acknowledge = typeof callback === 'function' ? callback : () => {};
    const { roomId, message } = payload;
    const normalizedMessage = typeof message === 'string' ? message.trim() : '';

    if (typeof roomId !== 'string' || !socket.rooms.has(roomId) || !socket.userId) {
      acknowledge({ success: false, error: 'You must join the room before sending messages.' });
      return;
    }

    if (!normalizedMessage || normalizedMessage.length > 2000) {
      acknowledge({ success: false, error: 'Messages must contain between 1 and 2000 characters.' });
      return;
    }

    try {
      const newMessage = await roomChatService.createMessage({
        roomId,
        userId: socket.userId,
        username: socket.username || 'Participant',
        text: normalizedMessage
      });
      io.to(roomId).emit('receive-chat-message', {
        _id: newMessage._id,
        userId: newMessage.userId,
        username: newMessage.username,
        text: newMessage.text,
        timestamp: newMessage.timestamp
      });
      acknowledge({ success: true, messageId: newMessage._id });
    } catch (error) {
      if (error instanceof ChatClosedError) {
        acknowledge({ success: false, error: 'Chat is only available while the class is live.' });
        return;
      }

      console.error('[Socket] Chat Error:', error);
      acknowledge({ success: false, error: 'Unable to send the message.' });
    }
  });

  socket.on('raise-hand', (payload = {}, callback) => {
    const { roomId, isRaised } = payload;
    const acknowledge = typeof callback === 'function' ? callback : () => {};

    if (typeof roomId !== 'string' || !socket.rooms.has(roomId) || !socket.userId) {
      acknowledge({ success: false, error: 'You must join the room before changing hand state.' });
      return;
    }

    if (typeof isRaised !== 'boolean') {
      acknowledge({ success: false, error: 'isRaised must be a boolean.' });
      return;
    }

    socket.handRaised = isRaised;
    const handState = {
      userId: socket.userId,
      username: socket.username || 'Participant',
      isRaised
    };

    io.to(roomId).emit('hand-raised', handState);
    acknowledge({ success: true, ...handState });
  });

  socket.on('camera-state-changed', ({ roomId, isPaused } = {}) => {
    if (typeof roomId !== 'string' || !socket.rooms.has(roomId) || !socket.userId) return;

    socket.cameraPaused = Boolean(isPaused);
    socket.to(roomId).emit('user-camera-state-changed', {
      userId: socket.userId,
      isPaused: socket.cameraPaused
    });
  });

  socket.on('microphone-state-changed', ({ roomId, isMuted }) => {
    if (!socket.rooms.has(roomId) || !socket.userId) return;

    socket.microphoneMuted = Boolean(isMuted);
    socket.to(roomId).emit('user-microphone-state-changed', {
      userId: socket.userId,
      isMuted: socket.microphoneMuted
    });
  });

    // WebRTC transport
    socket.on('createWebRtcTransport', async ({ roomId }, callback) => {
        try {
            const { transport, params } = await roomManager.createTransport(roomId);

            if (!socket.transports) socket.transports = new Map();
            socket.transports.set(transport.id, transport);

            transport.on('dtlsstatechange', dtlsState => {
                if (dtlsState === 'closed' || dtlsState === 'failed') {
                    transport.close();
                }
            });

            callback({ success: true, params });
        } catch (error) {
            console.error('[Mediasoup] Σφάλμα δημιουργίας transport:', error);
            callback({ success: false, error: error.message });
        }
    });

    socket.on('transport-connect', async ({ transportId, dtlsParameters }, callback) => {
        try {
            const transport = socket.transports.get(transportId);
            if (!transport) {
                throw new Error(`Το Transport με ID ${transportId} δεν βρέθηκε.`);
            }

            await transport.connect({ dtlsParameters });

            callback({ success: true });
        } catch (error) {
            console.error('[Mediasoup] Σφάλμα στο transport-connect:', error);
            callback({ success: false, error: error.message });
        }
    });

    // Media producer
    socket.on('transport-produce', async ({ transportId, kind, rtpParameters, roomId, userId }, callback) => {
        try {
            const transport = socket.transports.get(transportId);
            if (!transport) {
                throw new Error(`Το Transport με ID ${transportId} δεν βρέθηκε.`);
            }

            const producerUserId = socket.userId || userId;
            const producer = await transport.produce({
              kind,
              rtpParameters,
              appData: {
                roomId,
                userId: producerUserId
              }
            });

            if (!socket.producers) socket.producers = new Map();
            socket.producers.set(producer.id, producer);

            if (kind === 'audio') {
              try {
                await roomManager.registerAudioProducer(roomId, producer);
              } catch (observerError) {
                console.error('[Mediasoup] Αποτυχία παρακολούθησης audio producer:', observerError);
              }
            }

            console.log(`[Mediasoup] Νέος Producer (${kind}) από τον χρήστη ${producerUserId} στο δωμάτιο ${roomId}`);

            socket.to(roomId).emit('new-producer', {
                producerId: producer.id,
                userId: producerUserId,
                username: socket.username || 'Συμμετέχων',
                kind: producer.kind,
                isPaused: socket.cameraPaused || false,
                isMuted: socket.microphoneMuted ?? true
            });

            producer.on('transportclose', () => {
                producer.close();
                socket.producers.delete(producer.id);
            });

            callback({ success: true, id: producer.id });
        } catch (error) {
            console.error('[Mediasoup] Σφάλμα στο transport-produce:', error);
            callback({ success: false, error: error.message });
        }
    });

  // Media consumer
  socket.on('transport-consume', async ({ transportId, producerId, rtpCapabilities, roomId }, callback) => {
    try {
      console.log(`[Mediasoup] Αίτημα consume (Producer: ${producerId}) από το socket: ${socket.id}`);
      const router = roomManager.routers.get(roomId);
      const transport = socket.transports.get(transportId);

      if (!router || !transport) throw new Error('Δεν βρέθηκε Router ή Transport');

      if (!router.canConsume({ producerId, rtpCapabilities })) {
        throw new Error('Δεν είναι δυνατή η κατανάλωση αυτού του producer');
      }

      const consumer = await transport.consume({
        producerId,
        rtpCapabilities,
        paused: true,
      });

      if (!socket.consumers) socket.consumers = new Map();
      socket.consumers.set(consumer.id, consumer);

      console.log(`[Mediasoup] Δημιουργήθηκε Consumer ${consumer.id} για τον ${socket.id}`);

      consumer.on('transportclose', () => socket.consumers.delete(consumer.id));
      consumer.on('producerclose', () => {
        socket.consumers.delete(consumer.id);
        socket.emit('producer-closed', { producerId });
      });

      callback({
        success: true,
        params: {
          id: consumer.id,
          producerId: consumer.producerId,
          kind: consumer.kind,
          rtpParameters: consumer.rtpParameters
        }
      });
    } catch (error) {
      console.error('[Mediasoup] Σφάλμα στο transport-consume:', error);
      callback({ success: false, error: error.message });
    }
  });

  socket.on('consumer-resume', async ({ consumerId }, callback) => {
    try {
      console.log(`[Mediasoup] Αίτημα resume για τον Consumer: ${consumerId}`);
      const consumer = socket.consumers?.get(consumerId);
      if (consumer) {
        await consumer.resume();
        console.log(`[Mediasoup] Ο Consumer ${consumerId} ξεκίνησε τη ροή με επιτυχία!`);
        if (callback) callback({ success: true });
      } else {
        console.log(`[Mediasoup] Προσοχή: Δεν βρέθηκε ο Consumer ${consumerId}`);
        if (callback) callback({ success: false, error: 'Consumer not found' });
      }
    } catch (e) {
      console.error('[Mediasoup] Σφάλμα στο consumer-resume:', e);
      if (callback) callback({ success: false, error: e.message });
    }
  });

  socket.on('set-pinned-user', async ({ producerId }) => {
    try {
      console.log(`[Mediasoup] Αίτημα set-pinned-user για Producer: ${producerId}`);
      if (!socket.consumers) {
         console.log(`[Mediasoup] Δεν υπάρχουν consumers για το socket.`);
         return;
      }
      for (const [id, consumer] of socket.consumers.entries()) {
        console.log(`[Mediasoup] Ελέγχουμε τον consumer ${id} τύπου ${consumer.type}`);
        if (consumer.type === 'simulcast' || consumer.type === 'svc') {
          if (producerId && consumer.producerId === producerId) {
            console.log(`[Mediasoup] Ορισμός υψηλής ανάλυσης για τον ${consumer.producerId}`);
            await consumer.setPreferredLayers({ spatialLayer: 2 });
          } else {
            console.log(`[Mediasoup] Ορισμός χαμηλής ανάλυσης για τον ${consumer.producerId}`);
            await consumer.setPreferredLayers({ spatialLayer: 0 });
          }
        }
      }
    } catch (err) {
      console.error('[Mediasoup] Σφάλμα στο set-pinned-user:', err);
    }
  });

  socket.on('start-class', async ({ roomId, userId }) => {
    try {
      console.log(`[Mediasoup] Ο καθηγητής ${userId} ξεκίνησε το μάθημα ${roomId}`);
      const updatedCourse = await Course.findByIdAndUpdate(roomId, { isLive: true }, { new: true })
        .populate('teacher', 'firstName lastName')
        .populate('enrolledStudents', 'firstName lastName');
      const livePayload = {
        roomId,
        isLive: true,
        updatedAt: updatedCourse?.updatedAt
      };
      io.to(roomId).emit('class-started', livePayload);
      io.to(`info_${roomId}`).emit('class-started', livePayload);
      
      if (updatedCourse) {
        roomChatService.openRoom(roomId);
        const rooms = [`user_${updatedCourse.teacher._id || updatedCourse.teacher}`];
        updatedCourse.enrolledStudents.forEach(s => rooms.push(`user_${s._id || s}`));
        io.to(rooms).emit('course-updated', updatedCourse);
      }
    } catch (err) {
      console.error('[Mediasoup] Σφάλμα κατά την εκκίνηση του μαθήματος:', err);
    }
  });

  socket.on('end-class', async (payload = {}, callback) => {
    const acknowledge = typeof callback === 'function' ? callback : () => {};
    const { roomId, token } = payload;
    let chatClosureStarted = false;

    try {
      if (typeof roomId !== 'string' || !socket.rooms.has(roomId)) {
        throw new Error('You must join the room before ending the class.');
      }

      if (typeof token !== 'string' || !token) {
        throw new Error('Authentication is required to end the class.');
      }

      const decodedToken = jwt.verify(token, process.env.JWT_SECRET);
      const authenticatedUserId = decodedToken.id?.toString();
      const course = await Course.findById(roomId);

      if (!course) {
        throw new Error('Course not found.');
      }

      if (course.teacher.toString() !== authenticatedUserId) {
        throw new Error('Only the course teacher can end the class.');
      }

      console.log(`[Mediasoup] Ο καθηγητής ${authenticatedUserId} τερμάτισε το μάθημα ${roomId}`);

      chatClosureStarted = true;
      const deletionResult = await roomChatService.clearRoom(roomId);

      course.isLive = false;
      await course.save();
      await course.populate('teacher', 'firstName lastName');
      await course.populate('enrolledStudents', 'firstName lastName');

      const endedPayload = {
        roomId,
        isLive: false,
        chatCleared: true,
        updatedAt: course.updatedAt
      };
      io.to(roomId).emit('class-ended', endedPayload);
      io.to(`info_${roomId}`).emit('class-ended', endedPayload);

      const rooms = [`user_${course.teacher._id || course.teacher}`];
      course.enrolledStudents.forEach(s => rooms.push(`user_${s._id || s}`));
      io.to(rooms).emit('course-updated', course);
      
      roomManager.removeRoom(roomId);
      acknowledge({ success: true, deletedMessages: deletionResult.deletedCount });
    } catch (err) {
      if (chatClosureStarted) {
        roomChatService.openRoom(roomId);
      }
      console.error('[Mediasoup] Σφάλμα κατά τον τερματισμό του μαθήματος:', err);
      acknowledge({ success: false, error: err.message || 'Unable to end the class.' });
    }
  });

  socket.on('disconnecting', () => {
    for (const room of socket.rooms) {
      if (room !== socket.id && !room.startsWith('info_') && !room.startsWith('user_')) {
        socket.to(room).emit('user-left', {
          socketId: socket.id,
          userId: socket.userId
        });
        console.log(`[Socket] O χρήστης βγήκε από το δωμάτιο ${room}`);

        const roomObj = io.sockets.adapter.rooms.get(room);
        const count = roomObj ? roomObj.size - 1 : 0;
        io.to(`info_${room}`).emit('room-participants-update', { count: Math.max(0, count) });
      }
    }
  });

  socket.on('disconnect', () => {
    console.log(`[Socket] Αποσύνδεση: ${socket.id}`);
    if (socket.transports) {
      for (const transport of socket.transports.values()) {
        transport.close();
      }
      socket.transports.clear();
    }
  });
});

// Server start
const PORT = process.env.PORT || 3000;

createWorkers().then(() => {
  console.log('- Όλοι οι Mediasoup Workers είναι έτοιμοι.');
  server.listen(PORT, '0.0.0.0', () => {
    console.log(`- Ο server τρέχει στο http://localhost:${PORT}`);
  });
}).catch(err => {
  console.error('- Αποτυχία εκκίνησης Mediasoup Workers:', err);
});
