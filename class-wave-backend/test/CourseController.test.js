const assert = require('node:assert/strict');
const test = require('node:test');

const { reopenChatForLiveCourse } = require('../src/controllers/courseController');

test('starting a course through REST reopens its room chat', () => {
  const openedRooms = [];
  const app = {
    get: (key) => key === 'roomChatService'
      ? { openRoom: (roomId) => openedRooms.push(roomId.toString()) }
      : undefined
  };

  reopenChatForLiveCourse(app, { _id: 'course-1', isLive: true });
  assert.deepEqual(openedRooms, ['course-1']);
});

test('marking a course non-live does not reopen its room chat', () => {
  let openCalled = false;
  const app = {
    get: () => ({ openRoom: () => { openCalled = true; } })
  };

  reopenChatForLiveCourse(app, { _id: 'course-1', isLive: false });
  assert.equal(openCalled, false);
});
