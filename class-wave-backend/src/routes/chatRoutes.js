const express = require('express');
const router = express.Router();
const { getMessages } = require('../controllers/chatController');
const { protect } = require('../middleware/authMiddleware');

router.get('/:roomId', protect, getMessages);

module.exports = router;