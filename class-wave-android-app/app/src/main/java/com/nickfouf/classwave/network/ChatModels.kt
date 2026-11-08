package com.nickfouf.classwave.network

data class ChatMessage(
    val _id: String,
    val userId: String,
    val username: String,
    val text: String,
    val timestamp: String
)