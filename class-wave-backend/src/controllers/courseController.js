const Course = require('../models/Course');

const reopenChatForLiveCourse = (app, course) => {
    if (course.isLive) {
        app.get('roomChatService')?.openRoom(course._id);
    }
};

const getCourses = async (req, res) => {
    try {
        if (req.user.role === 'teacher') {
            const courses = await Course.find({ teacher: req.user._id })
                .populate('teacher', 'firstName lastName')
                .populate('enrolledStudents', 'firstName lastName');
            res.json(courses);
        } else {
            const courses = await Course.find({ enrolledStudents: req.user._id })
                .populate('teacher', 'firstName lastName')
                .populate('enrolledStudents', 'firstName lastName');
            res.json(courses);
        }
    } catch (error) {
        res.status(500).json({ message: error.message });
    }
};

const createCourse = async (req, res) => {
    try {
        if (req.user.role !== 'teacher') {
            return res.status(403).json({ message: 'Μόνο οι καθηγητές μπορούν να δημιουργήσουν μαθήματα' });
        }
        
        const { name, description, color } = req.body;
        const course = await Course.create({
            name,
            description,
            teacher: req.user._id,
            color: color || '#3B82F6'
        });
        
        const populatedCourse = await Course.findById(course._id)
            .populate('teacher', 'firstName lastName')
            .populate('enrolledStudents', 'firstName lastName');
        res.status(201).json(populatedCourse);
    } catch (error) {
        res.status(500).json({ message: error.message });
    }
};

const enrollCourse = async (req, res) => {
    try {
        if (req.user.role !== 'student') {
            return res.status(403).json({ message: 'Μόνο οι μαθητές μπορούν να εγγραφούν' });
        }
        
        const { courseId } = req.body;
        const course = await Course.findById(courseId);
        
        if (!course) {
            return res.status(404).json({ message: 'Το μάθημα δεν βρέθηκε' });
        }
        
        if (!course.enrolledStudents.includes(req.user._id)) {
            course.enrolledStudents.push(req.user._id);
            await course.save();
        }
        
        const populatedCourse = await Course.findById(course._id)
            .populate('teacher', 'firstName lastName')
            .populate('enrolledStudents', 'firstName lastName');
        res.json(populatedCourse);
    } catch (error) {
        res.status(500).json({ message: error.message });
    }
};

const updateCourse = async (req, res) => {
    try {
        const course = await Course.findById(req.params.id);
        if (!course) return res.status(404).json({ message: 'Course not found' });
        if (course.teacher.toString() !== req.user._id.toString()) return res.status(403).json({ message: 'Unauthorized' });

        course.name = req.body.name || course.name;
        if (req.body.schedule) course.schedule = req.body.schedule;

        await course.save();
        const populatedCourse = await Course.findById(course._id)
            .populate('teacher', 'firstName lastName')
            .populate('enrolledStudents', 'firstName lastName');

        const io = req.app.get('io');
        if (io && populatedCourse) {
            const rooms = [`user_${populatedCourse.teacher._id || populatedCourse.teacher}`];
            populatedCourse.enrolledStudents.forEach(s => rooms.push(`user_${s._id || s}`));
            io.to(rooms).emit('course-updated', populatedCourse);
        }

        res.json(populatedCourse);
    } catch (error) {
        res.status(500).json({ message: error.message });
    }
};

const deleteCourse = async (req, res) => {
    try {
        const course = await Course.findById(req.params.id);
        if (!course) return res.status(404).json({ message: 'Course not found' });
        if (course.teacher.toString() !== req.user._id.toString()) return res.status(403).json({ message: 'Unauthorized' });

        await course.deleteOne();
        res.json({ message: 'Course deleted' });
    } catch (error) {
        res.status(500).json({ message: error.message });
    }
};

const setCourseLiveStatus = async (req, res) => {
    try {
        const course = await Course.findById(req.params.id);
        if (!course) return res.status(404).json({ message: 'Course not found' });
        if (course.teacher.toString() !== req.user._id.toString()) return res.status(403).json({ message: 'Unauthorized' });

        course.isLive = req.body.isLive;
        await course.save();

        reopenChatForLiveCourse(req.app, course);

        const populatedCourse = await Course.findById(course._id)
            .populate('teacher', 'firstName lastName')
            .populate('enrolledStudents', 'firstName lastName');

        const io = req.app.get('io');
        if (io && populatedCourse) {
            const rooms = [`user_${populatedCourse.teacher._id || populatedCourse.teacher}`];
            populatedCourse.enrolledStudents.forEach(s => rooms.push(`user_${s._id || s}`));
            io.to(rooms).emit('course-updated', populatedCourse);

            const courseId = populatedCourse._id.toString();
            const liveEvent = populatedCourse.isLive ? 'class-started' : 'class-ended';
            const livePayload = {
                roomId: courseId,
                isLive: populatedCourse.isLive,
                updatedAt: populatedCourse.updatedAt
            };
            io.to(courseId).emit(liveEvent, livePayload);
            io.to(`info_${courseId}`).emit(liveEvent, livePayload);
        }

        res.json(populatedCourse);
    } catch (error) {
        res.status(500).json({ message: error.message });
    }
};

const unsubscribeCourse = async (req, res) => {
    try {
        if (req.user.role !== 'student') {
            return res.status(403).json({ message: 'Μόνο οι μαθητές μπορούν να διαγραφούν' });
        }
        
        const course = await Course.findById(req.params.id);
        
        if (!course) {
            return res.status(404).json({ message: 'Το μάθημα δεν βρέθηκε' });
        }
        
        course.enrolledStudents = course.enrolledStudents.filter(
            id => id.toString() !== req.user._id.toString()
        );
        await course.save();
        
        const populatedCourse = await Course.findById(course._id)
            .populate('teacher', 'firstName lastName')
            .populate('enrolledStudents', 'firstName lastName');
            
        const io = req.app.get('io');
        if (io && populatedCourse) {
            const rooms = [`user_${populatedCourse.teacher._id || populatedCourse.teacher}`];
            populatedCourse.enrolledStudents.forEach(s => rooms.push(`user_${s._id || s}`));
            io.to(rooms).emit('course-updated', populatedCourse);
        }
        
        res.json(populatedCourse);
    } catch (error) {
        res.status(500).json({ message: error.message });
    }
};

const removeStudent = async (req, res) => {
    try {
        const course = await Course.findById(req.params.id);
        if (!course) return res.status(404).json({ message: 'Course not found' });
        if (course.teacher.toString() !== req.user._id.toString()) return res.status(403).json({ message: 'Unauthorized' });

        const { studentId } = req.body;
        
        course.enrolledStudents = course.enrolledStudents.filter(
            id => id.toString() !== studentId
        );
        await course.save();
        
        const populatedCourse = await Course.findById(course._id)
            .populate('teacher', 'firstName lastName')
            .populate('enrolledStudents', 'firstName lastName');
            
        const io = req.app.get('io');
        if (io && populatedCourse) {
            const rooms = [`user_${populatedCourse.teacher._id || populatedCourse.teacher}`];
            populatedCourse.enrolledStudents.forEach(s => rooms.push(`user_${s._id || s}`));
            io.to(rooms).emit('course-updated', populatedCourse);
            
            io.to(`user_${studentId}`).emit('course-removed', { courseId: course._id });
        }
        
        res.json(populatedCourse);
    } catch (error) {
        res.status(500).json({ message: error.message });
    }
};

module.exports = {
    getCourses,
    createCourse,
    enrollCourse,
    updateCourse,
    deleteCourse,
    setCourseLiveStatus,
    unsubscribeCourse,
    removeStudent,
    reopenChatForLiveCourse
};
