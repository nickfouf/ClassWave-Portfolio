const mongoose = require('mongoose');

// Database connection
const connectDB = async () => {
    try {
        const conn = await mongoose.connect(process.env.MONGO_URI);
        console.log(`[MongoDB] Συνδέθηκε επιτυχώς: ${conn.connection.host}`);
    } catch (error) {
        console.error(`[MongoDB] Σφάλμα σύνδεσης: ${error.message}`);
        process.exit(1);
    }
};

module.exports = connectDB;
