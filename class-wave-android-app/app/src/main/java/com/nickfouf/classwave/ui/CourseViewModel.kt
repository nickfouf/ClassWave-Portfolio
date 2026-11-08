package com.nickfouf.classwave.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nickfouf.classwave.network.CourseResponse
import com.nickfouf.classwave.network.CreateCourseRequest
import com.nickfouf.classwave.network.EnrollCourseRequest
import com.nickfouf.classwave.network.UpdateCourseRequest
import com.nickfouf.classwave.network.RetrofitClient
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

import android.util.Log
import com.google.gson.Gson
import io.socket.client.IO
import io.socket.client.Socket
import org.json.JSONObject

sealed class CourseState {
    object Idle : CourseState()
    object Loading : CourseState()
    data class Success(val courses: List<CourseResponse>) : CourseState()
    data class Error(val message: String) : CourseState()
}

class CourseViewModel : ViewModel() {
    private val _courseState = MutableStateFlow<CourseState>(CourseState.Idle)
    val courseState: StateFlow<CourseState> = _courseState

    private var socket: Socket? = null
    private val gson = Gson()
    private val courseStateLock = Any()
    private val pendingCourseUpdates = linkedMapOf<String, CourseResponse>()
    private var fetchGeneration = 0

    fun connectSocket(token: String) {
        socket?.let { existingSocket ->
            if (!existingSocket.connected()) existingSocket.connect()
            return
        }

        try {
            val newSocket = IO.socket(com.nickfouf.classwave.BuildConfig.SERVER_URL)

            newSocket.on(Socket.EVENT_CONNECT) {
                newSocket.emit("authenticate", token)
            }

            newSocket.on("course-updated") { args ->
                try {
                    val jsonStr = (args[0] as JSONObject).toString()
                    val updatedCourse = gson.fromJson(jsonStr, CourseResponse::class.java)
                    updateCourseLocally(updatedCourse)
                } catch (e: Exception) {
                    Log.e("CourseViewModel", "Invalid course-updated payload", e)
                }
            }

            socket = newSocket
            newSocket.connect()
        } catch (e: Exception) {
            Log.e("CourseViewModel", "Socket connection failed", e)
        }
    }

    fun disconnectSocket() {
        socket?.off()
        socket?.disconnect()
        socket = null
    }

    fun updateCourseLocally(updatedCourse: CourseResponse) {
        synchronized(courseStateLock) {
            val currentState = _courseState.value
            if (currentState is CourseState.Success) {
                val courseExists = currentState.courses.any { it._id == updatedCourse._id }
                val newCourses = currentState.courses.map {
                    if (it._id == updatedCourse._id) updatedCourse else it
                }.let { courses ->
                    if (courseExists) courses else courses + updatedCourse
                }
                _courseState.value = CourseState.Success(newCourses)
            } else {
                pendingCourseUpdates[updatedCourse._id] = updatedCourse
            }
        }
    }

    fun fetchCourses(token: String) {
        val requestGeneration = synchronized(courseStateLock) {
            fetchGeneration += 1
            _courseState.value = CourseState.Loading
            fetchGeneration
        }

        viewModelScope.launch {
            try {
                val response = RetrofitClient.courseApi.getCourses("Bearer $token")
                if (response.isSuccessful && response.body() != null) {
                    synchronized(courseStateLock) {
                        if (requestGeneration == fetchGeneration) {
                            val serverCourses = response.body()!!
                            val serverCourseIds = serverCourses.mapTo(mutableSetOf()) { it._id }
                            val mergedCourses = serverCourses.map { course ->
                                pendingCourseUpdates[course._id] ?: course
                            } + pendingCourseUpdates.values.filter { it._id !in serverCourseIds }

                            pendingCourseUpdates.clear()
                            _courseState.value = CourseState.Success(mergedCourses)
                        }
                    }
                } else {
                    Log.e("CourseViewModel", "Fetch error: ${response.errorBody()?.string()}")
                    synchronized(courseStateLock) {
                        if (requestGeneration == fetchGeneration) {
                            _courseState.value = CourseState.Error("Αποτυχία φόρτωσης μαθημάτων")
                        }
                    }
                }
            } catch (e: Exception) {
                Log.e("CourseViewModel", "Fetch exception: ${e.message}")
                synchronized(courseStateLock) {
                    if (requestGeneration == fetchGeneration) {
                        _courseState.value = CourseState.Error("Σφάλμα δικτύου")
                    }
                }
            }
        }
    }

fun createCourse(token: String, name: String, description: String, color: String, onSuccess: () -> Unit) {
        viewModelScope.launch {
            try {
                val request = CreateCourseRequest(name, description, color)
                val response = RetrofitClient.courseApi.createCourse("Bearer $token", request)
                if (response.isSuccessful) {
                    fetchCourses(token)
                    onSuccess()
                } else {
                    Log.e("CourseViewModel", "Create error: ${response.errorBody()?.string()}")
                }
            } catch (e: Exception) {
                Log.e("CourseViewModel", "Create exception: ${e.message}")
            }
        }
    }

    fun enrollCourse(token: String, courseId: String, onSuccess: () -> Unit) {
        viewModelScope.launch {
            try {
                val request = EnrollCourseRequest(courseId)
                val response = RetrofitClient.courseApi.enrollCourse("Bearer $token", request)
                if (response.isSuccessful) {
                    fetchCourses(token)
                    onSuccess()
                } else {
                    Log.e("CourseViewModel", "Enroll error: ${response.errorBody()?.string()}")
                }
            } catch (e: Exception) {
                Log.e("CourseViewModel", "Enroll exception: ${e.message}")
            }
        }
    }

    fun updateCourse(token: String, courseId: String, name: String? = null, schedule: List<com.nickfouf.classwave.network.ScheduleItem>? = null, onSuccess: () -> Unit) {
        viewModelScope.launch {
            try {
                val response = RetrofitClient.courseApi.updateCourse("Bearer $token", courseId, UpdateCourseRequest(name = name, schedule = schedule))
                if (response.isSuccessful && response.body() != null) {
                    val updatedCourse = response.body()!!
                    updateCourseLocally(updatedCourse)
                    onSuccess()
                }
            } catch (e: Exception) {
                Log.e("CourseViewModel", "Update exception: ${e.message}")
            }
        }
    }

    fun deleteCourse(token: String, courseId: String, onSuccess: () -> Unit) {
        viewModelScope.launch {
            try {
                val response = RetrofitClient.courseApi.deleteCourse("Bearer $token", courseId)
                if (response.isSuccessful) {
                    fetchCourses(token)
                    onSuccess()
                }
            } catch (e: Exception) {
                Log.e("CourseViewModel", "Delete exception: ${e.message}")
            }
        }
    }

    fun setCourseLiveStatus(token: String, courseId: String, isLive: Boolean, onSuccess: () -> Unit) {
        viewModelScope.launch {
            try {
                val request = com.nickfouf.classwave.network.SetLiveStatusRequest(isLive)
                val response = RetrofitClient.courseApi.setCourseLiveStatus("Bearer $token", courseId, request)
                if (response.isSuccessful && response.body() != null) {
                    val updatedCourse = response.body()!!
                    updateCourseLocally(updatedCourse)
                    onSuccess()
                } else {
                    Log.e("CourseViewModel", "SetLive error: ${response.errorBody()?.string()}")
                }
            } catch (e: Exception) {
                Log.e("CourseViewModel", "SetLive exception: ${e.message}")
            }
        }
    }

    fun unsubscribeCourse(token: String, courseId: String, onSuccess: () -> Unit) {
        viewModelScope.launch {
            try {
                val response = RetrofitClient.courseApi.unsubscribeCourse("Bearer $token", courseId)
                if (response.isSuccessful) {
                    fetchCourses(token)
                    onSuccess()
                } else {
                    Log.e("CourseViewModel", "Unsubscribe error: ${response.errorBody()?.string()}")
                }
            } catch (e: Exception) {
                Log.e("CourseViewModel", "Unsubscribe exception: ${e.message}")
            }
        }
    }

    fun removeStudent(token: String, courseId: String, studentId: String, onSuccess: () -> Unit) {
        viewModelScope.launch {
            try {
                val request = com.nickfouf.classwave.network.RemoveStudentRequest(studentId)
                val response = RetrofitClient.courseApi.removeStudent("Bearer $token", courseId, request)
                if (response.isSuccessful && response.body() != null) {
                    val updatedCourse = response.body()!!
                    updateCourseLocally(updatedCourse)
                    onSuccess()
                } else {
                    Log.e("CourseViewModel", "RemoveStudent error: ${response.errorBody()?.string()}")
                }
            } catch (e: Exception) {
                Log.e("CourseViewModel", "RemoveStudent exception: ${e.message}")
            }
        }
    }
}
