package com.example.todomoji

import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.createSupabaseClient
import io.github.jan.supabase.auth.Auth
import io.github.jan.supabase.functions.Functions
import io.github.jan.supabase.postgrest.Postgrest
import io.github.jan.supabase.realtime.Realtime
import io.github.jan.supabase.storage.Storage

object Supa {
    lateinit var client: SupabaseClient
    fun init(url: String, anonKey: String) {
        client = createSupabaseClient(url, anonKey) {
            install(Auth)
            install(Postgrest)
            install(Storage)
            install(Realtime)
            install(Functions)
        }
    }
}
