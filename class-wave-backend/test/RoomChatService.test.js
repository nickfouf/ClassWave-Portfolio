const assert = require('node:assert/strict');
const test = require('node:test');

const { ChatClosedError, RoomChatService } = require('../src/services/RoomChatService');

const deferred = () => {
  let resolve;
  let reject;
  const promise = new Promise((resolvePromise, rejectPromise) => {
    resolve = resolvePromise;
    reject = rejectPromise;
  });
  return { promise, resolve, reject };
};

test('clearRoom waits for pending messages and then removes the complete room history', async () => {
  const pendingCreate = deferred();
  const calls = [];
  const service = new RoomChatService({
    Course: {
      exists: async () => ({ _id: 'course-1' })
    },
    Message: {
      create: async (message) => {
        calls.push('create-started');
        await pendingCreate.promise;
        calls.push('create-finished');
        return message;
      },
      deleteMany: async (filter) => {
        calls.push('delete');
        assert.deepEqual(filter, { roomId: 'course-1' });
        return { deletedCount: 3 };
      }
    }
  });

  const createPromise = service.createMessage({
    roomId: 'course-1',
    userId: 'user-1',
    username: 'Student',
    text: 'Hello'
  });
  const clearPromise = service.clearRoom('course-1');

  await assert.rejects(
    service.createMessage({
      roomId: 'course-1',
      userId: 'user-2',
      username: 'Late student',
      text: 'Too late'
    }),
    ChatClosedError
  );

  await new Promise((resolve) => setImmediate(resolve));
  assert.deepEqual(calls, ['create-started']);

  pendingCreate.resolve();
  await createPromise;
  const result = await clearPromise;

  assert.equal(result.deletedCount, 3);
  assert.deepEqual(calls, ['create-started', 'create-finished', 'delete']);
});

test('createMessage rejects messages when the course is not live', async () => {
  let createCalled = false;
  const service = new RoomChatService({
    Course: { exists: async () => null },
    Message: {
      create: async () => {
        createCalled = true;
      },
      deleteMany: async () => ({ deletedCount: 0 })
    }
  });

  await assert.rejects(
    service.createMessage({
      roomId: 'course-1',
      userId: 'user-1',
      username: 'Student',
      text: 'Hello'
    }),
    ChatClosedError
  );
  assert.equal(createCalled, false);
});

test('a failed cleanup reopens chat so ending the class can be retried', async () => {
  let shouldFail = true;
  const service = new RoomChatService({
    Course: { exists: async () => ({ _id: 'course-1' }) },
    Message: {
      create: async (message) => message,
      deleteMany: async () => {
        if (shouldFail) {
          shouldFail = false;
          throw new Error('database unavailable');
        }
        return { deletedCount: 1 };
      }
    }
  });

  await assert.rejects(service.clearRoom('course-1'), /database unavailable/);

  const message = await service.createMessage({
    roomId: 'course-1',
    userId: 'user-1',
    username: 'Student',
    text: 'Retry succeeded'
  });
  assert.equal(message.text, 'Retry succeeded');
});

test('openRoom allows messages when a later lesson starts in the same room', async () => {
  const service = new RoomChatService({
    Course: { exists: async () => ({ _id: 'course-1' }) },
    Message: {
      create: async (message) => message,
      deleteMany: async () => ({ deletedCount: 0 })
    }
  });

  await service.clearRoom('course-1');
  await assert.rejects(
    service.createMessage({
      roomId: 'course-1',
      userId: 'user-1',
      username: 'Student',
      text: 'Still closed'
    }),
    ChatClosedError
  );

  service.openRoom('course-1');
  const message = await service.createMessage({
    roomId: 'course-1',
    userId: 'user-1',
    username: 'Student',
    text: 'New lesson'
  });

  assert.equal(message.text, 'New lesson');
});
