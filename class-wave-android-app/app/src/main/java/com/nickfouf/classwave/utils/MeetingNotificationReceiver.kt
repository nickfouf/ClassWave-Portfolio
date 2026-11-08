package com.nickfouf.classwave.utils

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.nickfouf.classwave.network.RetrofitClient
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class MeetingNotificationReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val courseId = intent.getStringExtra("course_id") ?: return
        val courseName = intent.getStringExtra("course_name") ?: return
        val isFiveMin = intent.getBooleanExtra("is_five_min", false)
        val isTeacher = intent.getBooleanExtra("is_teacher", false)

        val pendingResult = goAsync()
        
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val sharedPref = context.getSharedPreferences("ClassWavePrefs", Context.MODE_PRIVATE)
                val token = sharedPref.getString("auth_token", null)
                if (token != null) {
                    val response = RetrofitClient.courseApi.getCourses("Bearer $token")
                    if (response.isSuccessful) {
                        val courses = response.body() ?: emptyList()
                        val course = courses.find { it._id == courseId }
                        if (course != null) {
                            if (course.isLive) {
                                NotificationManagerHelper.showLiveClassNotification(
                                    context,
                                    course.name,
                                    courseId
                                )
                            } else {
                                NotificationManagerHelper.showScheduledClassNotification(
                                    context = context,
                                    courseName = course.name,
                                    courseId = courseId,
                                    isTeacher = isTeacher,
                                    isFiveMinutesBefore = isFiveMin
                                )
                            }
                        }
                    }
                }
            } catch (e: Exception) {
                Log.e("MeetingNotifReceiver", "Error fetching courses: ${e.message}")
            } finally {
                pendingResult.finish()
            }
        }
    }
}
