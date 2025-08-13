package com.example.todomoji

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.example.todomoji.ui.theme.TodomojiTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        Supa.init(
            url = BuildConfig.SUPABASE_URL,
            anonKey = BuildConfig.SUPABASE_ANON_KEY
        )
        Log.d("Supa", "current user = ${Supa.client.auth.currentUserOrNull()?.id}")
        setContent { com.example.todomoji.ui.theme.TodomojiTheme { AppEntry() } }
    }
}
