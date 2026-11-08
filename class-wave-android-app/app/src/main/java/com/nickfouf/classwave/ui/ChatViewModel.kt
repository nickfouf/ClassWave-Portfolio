package com.nickfouf.classwave.ui

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nickfouf.classwave.network.ChatMessage
import com.nickfouf.classwave.network.RetrofitClient
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class ChatViewModel : ViewModel() {
    private val _messages = MutableStateFlow<List<ChatMessage>>(emptyList())
    val messages: StateFlow<List<ChatMessage>> = _messages

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading

    private val _hasMore = MutableStateFlow(true)
    val hasMore: StateFlow<Boolean> = _hasMore

    private var currentPage = 1
    private var currentRoomId = ""
    private var requestGeneration = 0

    private val _unreadCount = MutableStateFlow(0)
    val unreadCount: StateFlow<Int> = _unreadCount

    fun loadInitialMessages(token: String, roomId: String) {
        requestGeneration++
        currentRoomId = roomId
        currentPage = 1
        _messages.value = emptyList()
        _hasMore.value = true
        _unreadCount.value = 0
        fetchMessages(token)
    }

    fun loadMoreMessages(token: String) {
        if (!_isLoading.value && _hasMore.value) {
            currentPage++
            fetchMessages(token)
        }
    }

    private fun fetchMessages(token: String) {
        val requestedRoomId = currentRoomId
        val requestedPage = currentPage
        val generation = requestGeneration
        _isLoading.value = true
        viewModelScope.launch {
            try {
                val response = RetrofitClient.chatApi.getMessages("Bearer $token", requestedRoomId, requestedPage, 20)
                if (generation != requestGeneration || requestedRoomId != currentRoomId) return@launch

                if (response.isSuccessful && response.body() != null) {
                    val newMessages = response.body()!!
                    if (newMessages.isEmpty()) {
                        _hasMore.value = false
                    } else {
                        _messages.update { current -> 
                            val combined = current + newMessages
                            combined.distinctBy { it._id }.sortedByDescending { it.timestamp }
                        }
                    }
                } else {
                    Log.e("ChatViewModel", "Error fetching messages: ${response.errorBody()?.string()}")
                }
            } catch (e: Exception) {
                Log.e("ChatViewModel", "Exception fetching messages: ${e.message}")
            } finally {
                if (generation == requestGeneration) {
                    _isLoading.value = false
                }
            }
        }
    }

    fun addIncomingMessage(message: ChatMessage, isChatVisible: Boolean) {
        _messages.update { current ->
            val combined = listOf(message) + current
            combined.distinctBy { it._id }.sortedByDescending { it.timestamp }
        }
        if (!isChatVisible) {
            _unreadCount.update { it + 1 }
        }
    }

    fun markChatAsRead() {
        _unreadCount.value = 0
    }

    fun clearMessages(roomId: String) {
        if (roomId != currentRoomId) return

        requestGeneration++
        _messages.value = emptyList()
        _unreadCount.value = 0
        _hasMore.value = false
        _isLoading.value = false
    }
}
