const mongoose = require('mongoose');

const roomSchema = new mongoose.Schema({
    title: {
        type: String,
        required: true
    },
    teacherId: {
        type: mongoose.Schema.Types.ObjectId,
        ref: 'User',
        required: true
    },
    isLive: {
        type: Boolean,
        default: false
    },
    participants: [{
        type: mongoose.Schema.Types.ObjectId,
        ref: 'User'
    }]
}, { timestamps: true });

module.exports = mongoose.model('Room', roomSchema);