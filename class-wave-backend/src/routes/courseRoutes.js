const express = require('express');
const router = express.Router();
const { getCourses, createCourse, enrollCourse, updateCourse, deleteCourse, setCourseLiveStatus, unsubscribeCourse, removeStudent} = require('../controllers/courseController');
const { protect } = require('../middleware/authMiddleware');

router.route('/').get(protect, getCourses).post(protect, createCourse);
router.post('/enroll', protect, enrollCourse);
router.route('/:id').put(protect, updateCourse).delete(protect, deleteCourse);
router.put('/:id/live', protect, setCourseLiveStatus);
router.post('/:id/unsubscribe', protect, unsubscribeCourse);
router.post('/:id/remove-student', protect, removeStudent);

module.exports = router;