import { Device } from 'mediasoup-client';
import { io } from 'socket.io-client';

const query = new URLSearchParams(window.location.search);
const participant = {
  userId: query.get('userId'),
  username: query.get('username')
};
const roomId = query.get('roomId');
const serverUrl = query.get('serverUrl');

let device;
let producer;
let sendTransport;
let joining = false;

function report(state, detail = '') {
  const message = { userId: participant.userId, username: participant.username, state, detail };
  console.log(JSON.stringify(message));
  window.reportDummyStatus?.(message);
}

function emitWithAck(event, payload) {
  return new Promise((resolve, reject) => {
    socket.timeout(10_000).emit(event, payload, (error, response) => {
      if (error) {
        reject(new Error(`${event} timed out`));
        return;
      }

      if (response?.success === false) {
        reject(new Error(response.error || `${event} failed`));
        return;
      }

      resolve(response);
    });
  });
}

function createSyntheticVideoTrack() {
  const canvas = document.querySelector('#dummy-video');
  const context = canvas.getContext('2d');
  context.fillStyle = '#111827';
  context.fillRect(0, 0, canvas.width, canvas.height);

  const stream = canvas.captureStream(1);
  const [track] = stream.getVideoTracks();
  if (!track) throw new Error('Unable to create the synthetic video track');
  return track;
}

async function joinLesson() {
  if (joining) return;
  joining = true;

  try {
    report('joining');
    const joinResponse = await emitWithAck('join-room', {
      roomId,
      userId: participant.userId,
      username: participant.username,
      isMuted: true,
      isPaused: true
    });

    device = new Device({ handlerName: 'Chrome111' });
    await device.load({ routerRtpCapabilities: joinResponse.rtpCapabilities });

    const transportResponse = await emitWithAck('createWebRtcTransport', { roomId });
    sendTransport = device.createSendTransport(transportResponse.params);

    sendTransport.on('connect', async ({ dtlsParameters }, callback, errback) => {
      try {
        await emitWithAck('transport-connect', {
          transportId: sendTransport.id,
          dtlsParameters
        });
        callback();
      } catch (error) {
        errback(error);
      }
    });

    sendTransport.on('produce', async ({ kind, rtpParameters }, callback, errback) => {
      try {
        const response = await emitWithAck('transport-produce', {
          transportId: sendTransport.id,
          kind,
          rtpParameters,
          roomId,
          userId: participant.userId
        });
        callback({ id: response.id });
      } catch (error) {
        errback(error);
      }
    });

    producer = await sendTransport.produce({
      track: createSyntheticVideoTrack(),
      paused: true,
      zeroRtpOnPause: true
    });

    socket.emit('microphone-state-changed', { roomId, isMuted: true });
    socket.emit('camera-state-changed', { roomId, isPaused: true });
    report('ready', 'microphone off, camera off');
  } catch (error) {
    report('error', error instanceof Error ? error.message : String(error));
  } finally {
    joining = false;
  }
}

function closeMedia() {
  producer?.close();
  sendTransport?.close();
  producer = undefined;
  sendTransport = undefined;
  device = undefined;
}

const socket = io(serverUrl, {
  transports: ['websocket'],
  reconnection: true
});

socket.on('connect', joinLesson);
socket.on('connect_error', (error) => report('error', `connection: ${error.message}`));
socket.on('disconnect', (reason) => {
  closeMedia();
  report('disconnected', reason);
});

window.addEventListener('beforeunload', () => {
  closeMedia();
  socket.disconnect();
});
