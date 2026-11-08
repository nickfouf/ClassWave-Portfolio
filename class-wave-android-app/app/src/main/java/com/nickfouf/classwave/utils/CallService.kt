package com.nickfouf.classwave.utils

import android.app.Notification
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.app.Person
import androidx.core.content.ContextCompat
import com.nickfouf.classwave.MainActivity
import com.nickfouf.classwave.R

class CallService : Service() {
    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == "STOP_SERVICE") {
            stopForeground(true)
            stopSelf()
            return START_NOT_STICKY
        }

        val courseName = intent?.getStringExtra("course_name") ?: "Class"
        val courseId = intent?.getStringExtra("course_id") ?: ""

        val notificationIntent = Intent(this, MainActivity::class.java).apply {
            this.flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra("navigate_to", "video_room_$courseId")
        }
        
        val pendingIntent = PendingIntent.getActivity(
            this, courseId.hashCode(), notificationIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val hangupIntent = Intent(this, MainActivity::class.java).apply {
            this.flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra("navigate_to", "video_room_$courseId")
            putExtra("prompt_hangup", true)
        }
        val hangupPendingIntent = PendingIntent.getActivity(
            this, 1, hangupIntent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val person = Person.Builder()
            .setName(courseName)
            .build()

        val builder = NotificationCompat.Builder(this, NotificationManagerHelper.CHANNEL_CALLS)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle(getString(R.string.notif_call_title, courseName))
            .setContentText(getString(R.string.notif_call_text))
            .setOngoing(true)
            .setAutoCancel(false)
            .setCategory(NotificationCompat.CATEGORY_CALL)
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setContentIntent(pendingIntent)
            .setColor(ContextCompat.getColor(this, R.color.purple_500))
            .setStyle(NotificationCompat.CallStyle.forOngoingCall(person, hangupPendingIntent))

        val notification = builder.build()
        notification.flags = notification.flags or Notification.FLAG_ONGOING_EVENT or Notification.FLAG_NO_CLEAR

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                var serviceType = 0
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    serviceType = ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE or ServiceInfo.FOREGROUND_SERVICE_TYPE_CAMERA
                    if (Build.VERSION.SDK_INT >= 34) {
                        serviceType = serviceType or ServiceInfo.FOREGROUND_SERVICE_TYPE_PHONE_CALL
                    }
                }
                startForeground(NotificationManagerHelper.CALL_NOTIFICATION_ID, notification, serviceType)
            } else {
                startForeground(NotificationManagerHelper.CALL_NOTIFICATION_ID, notification)
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }

        return START_STICKY
    }
}
