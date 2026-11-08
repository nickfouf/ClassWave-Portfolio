package com.nickfouf.classwave.network

import retrofit2.Response
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.Path
import retrofit2.http.Query

interface ChatApi {
    @GET("api/chat/{roomId}")
    suspend fun getMessages(
        @Header("Authorization") token: String,
        @Path("roomId") roomId: String,
        @Query("page") page: Int,
        @Query("limit") limit: Int
    ): Response<List<ChatMessage>>
}