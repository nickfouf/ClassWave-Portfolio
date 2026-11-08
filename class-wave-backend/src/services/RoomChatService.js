class ChatClosedError extends Error {
  constructor(roomId) {
    super(`Chat is closed for room ${roomId}.`);
    this.name = 'ChatClosedError';
  }
}

class RoomChatService {
  constructor({ Message, Course }) {
    this.Message = Message;
    this.Course = Course;
    this.roomOperations = new Map();
    this.closedRooms = new Set();
  }

  createMessage({ roomId, userId, username, text }) {
    const roomKey = roomId.toString();

    if (this.closedRooms.has(roomKey)) {
      return Promise.reject(new ChatClosedError(roomKey));
    }

    return this.enqueue(roomKey, async () => {
      const liveCourse = await this.Course.exists({ _id: roomId, isLive: true });
      if (!liveCourse) {
        throw new ChatClosedError(roomKey);
      }

      return this.Message.create({ roomId, userId, username, text });
    });
  }

  clearRoom(roomId) {
    const roomKey = roomId.toString();

    this.closedRooms.add(roomKey);

    return this.enqueue(roomKey, async () => {
      try {
        return await this.Message.deleteMany({ roomId });
      } catch (error) {
        this.closedRooms.delete(roomKey);
        throw error;
      }
    });
  }

  openRoom(roomId) {
    this.closedRooms.delete(roomId.toString());
  }

  enqueue(roomKey, operation) {
    const previousOperation = this.roomOperations.get(roomKey) || Promise.resolve();
    const currentOperation = previousOperation
      .catch(() => undefined)
      .then(operation);

    this.roomOperations.set(roomKey, currentOperation);
    currentOperation
      .finally(() => {
        if (this.roomOperations.get(roomKey) === currentOperation) {
          this.roomOperations.delete(roomKey);
        }
      })
      .catch(() => undefined);

    return currentOperation;
  }
}

module.exports = { ChatClosedError, RoomChatService };
