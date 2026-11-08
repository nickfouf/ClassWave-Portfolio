package com.nickfouf.classwave.network

import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.POST
import retrofit2.http.PUT
import retrofit2.http.DELETE
import retrofit2.http.Path

data class CourseResponse(
    val _id: String,
    val name: String,
    val description: String?,
    val teacher: TeacherInfo?,
    val enrolledStudents: List<StudentInfo>?,
    val color: String,
    val schedule: List<ScheduleItem>? = null,
    val isLive: Boolean = false
)

data class ScheduleItem(
    val id: String? = null,
    val date: String,
    val time: String,
    val topic: String,
    val groupId: String? = null,
    val repeatDays: List<Int>? = null,
    val repeatUntilDate: String? = null
)

data class UpdateCourseRequest(
    val name: String?,
    val schedule: List<ScheduleItem>? = null
)

data class TeacherInfo(
    val _id: String,
    val firstName: String,
    val lastName: String
)

data class StudentInfo(
    val _id: String,
    val firstName: String,
    val lastName: String
)

data class CreateCourseRequest(
    val name: String,
    val description: String,
    val color: String?
)

data class EnrollCourseRequest(
    val courseId: String
)

data class SetLiveStatusRequest(
    val isLive: Boolean
)

data class RemoveStudentRequest(
    val studentId: String
)

interface CourseApi {
    @GET("api/courses")
    suspend fun getCourses(@Header("Authorization") token: String): Response<List<CourseResponse>>

    @POST("api/courses")
    suspend fun createCourse(
        @Header("Authorization") token: String,
        @Body request: CreateCourseRequest
    ): Response<CourseResponse>

    @POST("api/courses/enroll")
    suspend fun enrollCourse(
        @Header("Authorization") token: String,
        @Body request: EnrollCourseRequest
    ): Response<CourseResponse>

    @PUT("api/courses/{id}")
    suspend fun updateCourse(
        @Header("Authorization") token: String,
        @Path("id") courseId: String,
        @Body request: UpdateCourseRequest
    ): Response<CourseResponse>

    @PUT("api/courses/{id}/live")
    suspend fun setCourseLiveStatus(
        @Header("Authorization") token: String,
        @Path("id") courseId: String,
        @Body request: SetLiveStatusRequest
    ): Response<CourseResponse>

    @DELETE("api/courses/{id}")
    suspend fun deleteCourse(
        @Header("Authorization") token: String,
        @Path("id") courseId: String
    ): Response<Unit>

    @POST("api/courses/{id}/unsubscribe")
    suspend fun unsubscribeCourse(
        @Header("Authorization") token: String,
        @Path("id") courseId: String
    ): Response<CourseResponse>

    @POST("api/courses/{id}/remove-student")
    suspend fun removeStudent(
        @Header("Authorization") token: String,
        @Path("id") courseId: String,
        @Body request: RemoveStudentRequest
    ): Response<CourseResponse>
}