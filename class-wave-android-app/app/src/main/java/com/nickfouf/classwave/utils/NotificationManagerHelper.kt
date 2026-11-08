package com.nickfouf.classwave.utils

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.nickfouf.classwave.MainActivity
import com.nickfouf.classwave.R

object NotificationManagerHelper {
    const val CHANNEL_CALLS = "calls_channel"
    const val CHANNEL_LIVE = "live_classes_channel"

    const val CALL_NOTIFICATION_ID = 1001
    private const val LIVE_NOTIFICATION_ID_BASE = 2000

    fun createNotificationChannels(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val callsChannel = NotificationChannel(
                CHANNEL_CALLS,
                context.getString(R.string.notif_channel_calls_name),
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = context.getString(R.string.notif_channel_calls_desc)
            }

            val liveChannel = NotificationChannel(
                CHANNEL_LIVE,
                context.getString(R.string.notif_channel_live_name),
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = context.getString(R.string.notif_channel_live_desc)
            }

            val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            nm.createNotificationChannel(callsChannel)
            nm.createNotificationChannel(liveChannel)
        }
    }

    fun showCallNotification(context: Context, courseName: String, courseId: String) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            return
        }

        val intent = Intent(context, CallService::class.java).apply {
            putExtra("course_name", courseName)
            putExtra("course_id", courseId)
        }
        ContextCompat.startForegroundService(context, intent)
    }

    fun cancelCallNotification(context: Context) {
        val intent = Intent(context, CallService::class.java).apply {
            action = "STOP_SERVICE"
        }
        context.startService(intent)
    }

    fun showLiveClassNotification(context: Context, courseName: String, courseId: String) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            return
        }

        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra("navigate_to", "video_room_$courseId")
        }
        val pendingIntent = PendingIntent.getActivity(
            context, courseId.hashCode(), intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val builder = NotificationCompat.Builder(context, CHANNEL_LIVE)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle(context.getString(R.string.notif_live_title))
            .setContentText(context.getString(R.string.notif_live_text, courseName))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .setColor(ContextCompat.getColor(context, R.color.purple_500))

        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        nm.notify(LIVE_NOTIFICATION_ID_BASE + courseId.hashCode(), builder.build())
    }

    fun showScheduledClassNotification(
        context: Context,
        courseName: String,
        courseId: String,
        isTeacher: Boolean,
        isFiveMinutesBefore: Boolean
    ) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            return
        }

        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra("navigate_to", "pre_call_$courseId")
        }
        val pendingIntent = PendingIntent.getActivity(
            context,
            courseId.hashCode(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val titleRes: Int
        val textRes: Int
        when {
            isTeacher && isFiveMinutesBefore -> {
                titleRes = R.string.notif_teacher_upcoming_title
                textRes = R.string.notif_teacher_upcoming_text
            }
            isTeacher -> {
                titleRes = R.string.notif_teacher_scheduled_title
                textRes = R.string.notif_teacher_scheduled_text
            }
            isFiveMinutesBefore -> {
                titleRes = R.string.notif_student_upcoming_title
                textRes = R.string.notif_student_upcoming_text
            }
            else -> {
                titleRes = R.string.notif_student_scheduled_title
                textRes = R.string.notif_student_scheduled_text
            }
        }

        val notification = NotificationCompat.Builder(context, CHANNEL_LIVE)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle(context.getString(titleRes))
            .setContentText(context.getString(textRes, courseName))
            .setStyle(
                NotificationCompat.BigTextStyle()
                    .bigText(context.getString(textRes, courseName))
            )
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .setColor(ContextCompat.getColor(context, R.color.purple_500))
            .build()

        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        nm.notify(LIVE_NOTIFICATION_ID_BASE + courseId.hashCode(), notification)
    }

    fun scheduleMeetingNotifications(context: Context, courses: List<com.nickfouf.classwave.network.CourseResponse>, userId: String) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as android.app.AlarmManager
        val sdf = java.text.SimpleDateFormat("yyyy-MM-dd HH:mm", java.util.Locale.US)
        
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            if (!alarmManager.canScheduleExactAlarms()) {
                return 
            }
        }

        val now = System.currentTimeMillis()

        for (course in courses) {
            val isTeacher = course.teacher?._id == userId
            course.schedule?.forEach { item ->
                    try {
                        val timeParts = item.time.split("-").map { it.trim() }
                        if (timeParts.isNotEmpty()) {
                            val startTimeStr = timeParts[0]
                            val dateTimeStr = "${item.date} $startTimeStr"
                            val date = sdf.parse(dateTimeStr)
                            
                            if (date != null && date.time > now) {
                                val exactIntent = Intent(context, MeetingNotificationReceiver::class.java).apply {
                                    putExtra("course_id", course._id)
                                    putExtra("course_name", course.name)
                                    putExtra("is_five_min", false)
                                    putExtra("is_teacher", isTeacher)
                                }
                                val exactPendingIntent = PendingIntent.getBroadcast(
                                    context,
                                    (course._id + (item.id ?: "") + "exact").hashCode(),
                                    exactIntent,
                                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                                )
                                alarmManager.setExactAndAllowWhileIdle(
                                    android.app.AlarmManager.RTC_WAKEUP,
                                    date.time,
                                    exactPendingIntent
                                )
                                
                                val fiveMinBefore = date.time - (5 * 60 * 1000)
                                if (fiveMinBefore > now) {
                                    val fiveMinIntent = Intent(context, MeetingNotificationReceiver::class.java).apply {
                                        putExtra("course_id", course._id)
                                        putExtra("course_name", course.name)
                                        putExtra("is_five_min", true)
                                        putExtra("is_teacher", isTeacher)
                                    }
                                    val fiveMinPendingIntent = PendingIntent.getBroadcast(
                                        context,
                                        (course._id + (item.id ?: "") + "fivemin").hashCode(),
                                        fiveMinIntent,
                                        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                                    )
                                    alarmManager.setExactAndAllowWhileIdle(
                                        android.app.AlarmManager.RTC_WAKEUP,
                                        fiveMinBefore,
                                        fiveMinPendingIntent
                                    )
                                }
                            }
                        }
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                }
        }
    }
}
