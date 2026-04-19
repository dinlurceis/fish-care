package com.htn.fishcare.auth.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.auth.FirebaseAuth
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

data class LoginUiState(
    val email: String = "",
    val password: String = "",
    val confirmPassword: String = "",
    val isLoading: Boolean = false,
    val error: String? = null,
    val isLoginSuccess: Boolean = false,
    val isRegisterMode: Boolean = false
)

@HiltViewModel
class LoginViewModel @Inject constructor() : ViewModel() {

    private val auth = FirebaseAuth.getInstance()
    private val _uiState = MutableStateFlow(LoginUiState())
    val uiState: StateFlow<LoginUiState> = _uiState.asStateFlow()

    fun onEmailChange(email: String) {
        _uiState.value = _uiState.value.copy(email = email)
    }

    fun onPasswordChange(password: String) {
        _uiState.value = _uiState.value.copy(password = password)
    }
    
    fun onConfirmPasswordChange(password: String) {
        _uiState.value = _uiState.value.copy(confirmPassword = password)
    }

    fun toggleMode() {
        val currentMode = _uiState.value.isRegisterMode
        _uiState.value = _uiState.value.copy(
            isRegisterMode = !currentMode,
            error = null
        )
    }

    fun submit() {
        val email = _uiState.value.email.trim()
        val password = _uiState.value.password
        val isRegisterMode = _uiState.value.isRegisterMode

        if (email.isBlank() || password.isBlank()) {
            _uiState.value = _uiState.value.copy(error = "Vui lòng nhập đầy đủ thông tin")
            return
        }

        if (isRegisterMode) {
            val confirmPassword = _uiState.value.confirmPassword
            if (password != confirmPassword) {
                _uiState.value = _uiState.value.copy(error = "Mật khẩu xác nhận không khớp")
                return
            }
            if (password.length < 6) {
                _uiState.value = _uiState.value.copy(error = "Mật khẩu phải từ 6 ký tự trở lên")
                return
            }
            performRegister(email, password)
        } else {
            performLogin(email, password)
        }
    }

    private fun performLogin(email: String, password: String) {
        _uiState.value = _uiState.value.copy(isLoading = true, error = null)
        
        auth.signInWithEmailAndPassword(email, password)
            .addOnCompleteListener { task ->
                if (task.isSuccessful) {
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        isLoginSuccess = true
                    )
                } else {
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        error = task.exception?.message ?: "Đăng nhập thất bại"
                    )
                }
            }
    }
    
    private fun performRegister(email: String, password: String) {
        _uiState.value = _uiState.value.copy(isLoading = true, error = null)
        
        auth.createUserWithEmailAndPassword(email, password)
            .addOnCompleteListener { task ->
                if (task.isSuccessful) {
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        isLoginSuccess = true // Đăng ký thành công thì vào thẳng app luôn
                    )
                } else {
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        error = task.exception?.message ?: "Đăng ký thất bại"
                    )
                }
            }
    }

    fun clearError() {
        _uiState.value = _uiState.value.copy(error = null)
    }
}
