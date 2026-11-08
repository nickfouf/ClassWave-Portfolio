const User = require('../models/User');
const jwt = require('jsonwebtoken');

const generateToken = (id) => {
    return jwt.sign({ id }, process.env.JWT_SECRET, { expiresIn: '30d' });
};

const registerUser = async (req, res) => {
    console.log('\n--- Νέα Προσπάθεια Εγγραφής ---');
    
    try {
        const { firstName, lastName, email, password, role } = req.body;

        if (!firstName || !lastName || !email || !password) {
            console.log('ΑΠΟΤΥΧΙΑ: Λείπουν υποχρεωτικά πεδία');
            return res.status(400).json({ message: 'Παρακαλώ συμπληρώστε όλα τα πεδία' });
        }

        console.log('Έλεγχος βάσης για email:', email);
        const emailExists = await User.findOne({ email });
        if (emailExists) {
            console.log('ΑΠΟΤΥΧΙΑ: Το email υπάρχει ήδη στη βάση.');
            return res.status(400).json({ message: 'Το email χρησιμοποιείται ήδη' });
        }

        console.log('Δημιουργία χρήστη στη βάση...');
        const user = await User.create({ firstName, lastName, email, password, role });

        console.log('ΕΠΙΤΥΧΙΑ: Ο χρήστης δημιουργήθηκε με ID:', user._id);
        res.status(201).json({
            _id: user._id,
            firstName: user.firstName,
            lastName: user.lastName,
            role: user.role,
            iconColor: user.iconColor,
            token: generateToken(user._id)
        });
    } catch (error) {
        console.error('*** ΣΦΑΛΜΑ SERVER ***:', error);
        res.status(500).json({ message: error.message });
    }
};

const loginUser = async (req, res) => {
    try {
        const { email, password } = req.body;

        const user = await User.findOne({ email });

        if (user && (await user.matchPassword(password))) {
            res.json({
                _id: user._id,
                firstName: user.firstName,
                lastName: user.lastName,
                role: user.role,
                token: generateToken(user._id)
            });
        } else {
            res.status(401).json({ message: 'Λάθος email ή κωδικός' });
        }
    } catch (error) {
        res.status(500).json({ message: error.message });
    }
};

const updateProfile = async (req, res) => {
    try {
        const user = await User.findById(req.user._id);
        if (user) {
            user.firstName = req.body.firstName || user.firstName;
            user.lastName = req.body.lastName || user.lastName;
            if (req.body.iconColor) {
                user.iconColor = req.body.iconColor;
            }
            const updatedUser = await user.save();
            res.json({
                _id: updatedUser._id,
                firstName: updatedUser.firstName,
                lastName: updatedUser.lastName,
                email: updatedUser.email,
                role: updatedUser.role,
                iconColor: updatedUser.iconColor
            });
        } else {
            res.status(404).json({ message: 'Ο χρήστης δεν βρέθηκε' });
        }
    } catch (error) {
        res.status(500).json({ message: error.message });
    }
};

module.exports = { registerUser, loginUser, updateProfile };
