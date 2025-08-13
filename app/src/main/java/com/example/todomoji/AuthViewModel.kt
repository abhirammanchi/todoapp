package com.example.todomoji

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.jan.supabase.auth.Auth
import io.github.jan.supabase.auth.auth 
import io.github.jan.supabase.auth.providers.builtin.Email
import io.github.jan.supabase.auth.user.UserInfo
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class AuthUiState(
    val loading: Boolean = false,
    val error: String? = null
)

class AuthViewModel : ViewModel() {
    private val auth: Auth get() = Supa.client.auth

    private val _user = MutableStateFlow<UserInfo?>(auth.currentUserOrNull())
    val user: StateFlow<UserInfo?> = _user.asStateFlow()

    private val _ui = MutableStateFlow(AuthUiState())
    val ui: StateFlow<AuthUiState> = _ui.asStateFlow()

    init {
        viewModelScope.launch {
            // Keep user state in sync
            auth.sessionStatus.collect {
                _user.value = auth.currentUserOrNull()
            }
        }
    }

    fun signIn(email: String, password: String) = viewModelScope.launch {
        _ui.value = AuthUiState(loading = true)
        try {
            auth.signInWith(Email) {
                this.email = email
                this.password = password
            }
            _ui.value = AuthUiState(loading = false)
        } catch (t: Throwable) {
            _ui.value = AuthUiState(loading = false, error = t.message ?: "Sign in failed")
        }
    }

    fun signUp(email: String, password: String) = viewModelScope.launch {
        _ui.value = AuthUiState(loading = true)
        try {
            auth.signUpWith(Email) {
                this.email = email
                this.password = password
            }
            _ui.value = AuthUiState(loading = false)
        } catch (t: Throwable) {
            _ui.value = AuthUiState(loading = false, error = t.message ?: "Sign up failed")
        }
    }

    fun signOut() = viewModelScope.launch {
        try { auth.signOut() } catch (_: Throwable) {}
    }
}
