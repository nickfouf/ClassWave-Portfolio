package com.nickfouf.classwave.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nickfouf.classwave.network.LoginRequest
import com.nickfouf.classwave.network.RegisterRequest
import com.nickfouf.classwave.network.RetrofitClient
import kotlinx.coroutines.flow.MutableStateFlow
import android.util.Log
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

sealed class AuthState {
    object Idle : AuthState()
    object Loading : AuthState()
    data class Success(val token: String) : AuthState()
    data class Error(val messageId: Int) : AuthState()
}

class AuthViewModel : ViewModel() {

    private val _authState = MutableStateFlow<AuthState>(AuthState.Idle)
    val authState: StateFlow<AuthState> = _authState

    fun resetState() {
        _authState.value = AuthState.Idle
    }

    fun register(firstName: String, lastName: String, email: String, pass: String, role: String) {
        _authState.value = AuthState.Loading
        Log.d("AuthDebug", "Ξεκινάει request εγγραφής -> Name: $firstName $lastName, Email: $email, Role: $role")

        viewModelScope.launch {
            try {
                val response = RetrofitClient.authApi.register(RegisterRequest(firstName, lastName, email, pass, role))
                Log.d("AuthDebug", "HTTP Status Code: ${response.code()}")

                if (response.isSuccessful && response.body() != null) {
                    val token = response.body()!!.token
                    Log.d("AuthDebug", "Επιτυχία! Πήραμε Token: ${token.take(10)}...")
                    _authState.value = AuthState.Success(token)
                } else {
                    val errorBodyString = response.errorBody()?.string()
                    Log.e("AuthDebug", "Αποτυχία API. HTTP ${response.code()} | Body: $errorBodyString")
                    _authState.value = AuthState.Error(com.nickfouf.classwave.R.string.error_register)
                }
            } catch (e: Exception) {
                Log.e("AuthDebug", "Εξαίρεση Δικτύου: ${e.message}", e)
                _authState.value = AuthState.Error(com.nickfouf.classwave.R.string.error_network)
            }
        }
    }

    fun login(email: String, pass: String) {
        _authState.value = AuthState.Loading

        viewModelScope.launch {
            try {
                val response = RetrofitClient.authApi.login(LoginRequest(email, pass))

                if (response.isSuccessful && response.body() != null) {
                    val token = response.body()!!.token
                    _authState.value = AuthState.Success(token)
                } else {
                    _authState.value = AuthState.Error(com.nickfouf.classwave.R.string.error_invalid_credentials)
                }
            } catch (e: Exception) {
                _authState.value = AuthState.Error(com.nickfouf.classwave.R.string.error_network)
            }
        }
    }
}
