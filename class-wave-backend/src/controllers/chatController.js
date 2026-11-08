const Message = require('../models/Message');

const getMessages = async (req, res) => {
    try {
        const { roomId } = req.params;
        const page = parseInt(req.query.page) || 1;
        const limit = parseInt(req.query.limit) || 20;
        const skip = (page - 1) * limit;

        const messages = await Message.find({ roomId })
            .sort({ timestamp: -1 })
            .skip(skip)
            .limit(limit);

        res.json(messages);
    } catch (error) {
        res.status(500).json({ message: error.message });
    }
};

module.exports = { getMessages };