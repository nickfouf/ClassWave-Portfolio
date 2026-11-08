const mongoose = require('mongoose');

const courseSchema = new mongoose.Schema({
    name: {
        type: String,
        required: true
    },
    description: {
        type: String
    },
    teacher: {
        type: mongoose.Schema.Types.ObjectId,
        ref: 'User',
        required: true
    },
    enrolledStudents: [{
        type: mongoose.Schema.Types.ObjectId,
        ref: 'User'
    }],
    color: {
        type: String,
        default: '#3B82F6'
    },
    schedule: [{
        id: String,
        date: String,
        time: String,
        topic: String,
        groupId: String,
        repeatDays: [Number],
        repeatUntilDate: String
    }],
    isLive: {
        type: Boolean,
        default: false
    }
}, { timestamps: true });

module.exports = mongoose.model('Course', courseSchema);