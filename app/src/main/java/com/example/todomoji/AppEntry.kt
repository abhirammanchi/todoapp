package com.example.todomoji

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.lifecycle.viewmodel.compose.viewModel

@Composable
fun AppEntry() {
    val authVm: AuthViewModel = viewModel()
    val user by authVm.user.collectAsState()

    if (user == null) {
        AuthScreen(vm = authVm)
    } else {
        // Pass sign-out action down so you can log out from the Tasks screen
        TodomojiApp(onSignOut = { authVm.signOut() })
    }
}
