package com.nickfouf.classwave.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nickfouf.classwave.network.RetrofitClient
import com.nickfouf.classwave.network.UpdateProfileRequest
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

sealed class ProfileState {
    object Loading : ProfileState()
    data class Success(val id: String, val firstName: String, val lastName: String, val email: String, val role: String, val iconColor: String) : ProfileState()
    data class Error(val message: String) : ProfileState()
}

class ProfileViewModel : ViewModel() {
    private val _profileState = MutableStateFlow<ProfileState>(ProfileState.Loading)
    val profileState: StateFlow<ProfileState> = _profileState

    fun fetchProfile(token: String) {
        if (_profileState.value !is ProfileState.Success) {
            _profileState.value = ProfileState.Loading
        }
        
        viewModelScope.launch {
            try {
                val response = RetrofitClient.authApi.getProfile("Bearer $token")
                if (response.isSuccessful && response.body() != null) {
                    val body = response.body()!!
                    _profileState.value = ProfileState.Success(body._id, body.firstName, body.lastName, body.email, body.role, body.iconColor ?: "#1D4ED8")
                } else if (_profileState.value !is ProfileState.Success) {
                    _profileState.value = ProfileState.Error("Αποτυχία φόρτωσης προφίλ")
                }
            } catch (e: Exception) {
                if (_profileState.value !is ProfileState.Success) {
                    _profileState.value = ProfileState.Error("Σφάλμα δικτύου")
                }
            }
        }
    }

    fun updateProfile(token: String, firstName: String, lastName: String, iconColor: String, onSuccess: () -> Unit) {
        val currentRole = (_profileState.value as? ProfileState.Success)?.role ?: "student"
        _profileState.value = ProfileState.Loading
        viewModelScope.launch {
            try {
                val response = RetrofitClient.authApi.updateProfile("Bearer $token", UpdateProfileRequest(firstName, lastName, iconColor))
                if (response.isSuccessful && response.body() != null) {
                    val body = response.body()!!
                    _profileState.value = ProfileState.Success(body._id, body.firstName, body.lastName, body.email, body.role, body.iconColor ?: "#1D4ED8")
                    onSuccess()
                } else {
                    _profileState.value = ProfileState.Error("Αποτυχία ενημέρωσης")
                }
            } catch (e: Exception) {
                _profileState.value = ProfileState.Error("Σφάλμα δικτύου")
            }
        }
    }
}
