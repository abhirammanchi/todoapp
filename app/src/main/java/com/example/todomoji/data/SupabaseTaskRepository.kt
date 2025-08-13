package com.example.todomoji.data

import android.content.Context
import android.net.Uri
import com.example.todomoji.Supa
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.LocalTime
// For SupabaseTaskRepository.kt (or wherever used)
import kotlin.time.Duration.Companion.minutes
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.postgrest.query.filter.eq
import io.github.jan.supabase.postgrest.query.Order
import io.github.jan.supabase.realtime.realtime
import io.github.jan.supabase.realtime.channel
import io.github.jan.supabase.realtime.PostgresAction
import io.github.jan.supabase.realtime.postgresChangeFlow
import io.github.jan.supabase.storage.storage
import io.github.jan.supabase.storage.from




class SupabaseTaskRepository {

    private fun uid(): String =
        Supa.client.auth.currentUserOrNull()?.id ?: error("Not signed in")

    // Live list of ALL tasks for the user (you can filter by date in VM/UI)
    fun tasksFlow(): Flow<List<Task>> = callbackFlow {
        val pg = Supa.client.postgrest
        suspend fun emitAll() {
            val rows = pg.from("tasks")
                .select()
                .eq("user_id", uid())
                .decodeList<TaskRow>()
            trySend(rows.map { it.toDomain() })
        }

        // initial load
        CoroutineScope(Dispatchers.IO).launch { emitAll() }

        // subscribe to realtime changes, then reload
        val ch = Supa.client.realtime.channel("realtime:public:tasks")
        val job = launch(Dispatchers.IO) {
            ch.postgresChangeFlow<PostgresAction>(schema = "public") {
                table = "tasks"
            }
                .onStart { ch.subscribe() }
                .collect { emitAll() } // any change -> refetch
        }
        awaitClose { job.cancel(); ch.unsubscribe() }
    }

    // CRUD
    suspend fun addTask(title: String, due: LocalDate) {
        val pg = Supa.client.postgrest
        pg.from("tasks").insert(
            TaskRowInsert(
                user_id = uid(),
                title = title.trim(),
                due = due.toString()
            )
        )
    }

    suspend fun toggle(id: String, current: Boolean) {
        val pg = Supa.client.postgrest
        pg.from("tasks").update(mapOf("completed" to !current)).eq("id", id)
    }

    suspend fun delete(id: String) {
        Supa.client.postgrest.from("tasks").delete { eq("id", id) }
    }

    suspend fun schedule(id: String, start: LocalTime?, end: LocalTime?) {
        Supa.client.postgrest.from("tasks")
            .update(mapOf("start" to start?.toString(), "end" to end?.toString()))
            .eq("id", id)
    }

    // PHOTOS

    // Upload a local photo to Storage and create a DB row; return a signed URL
    suspend fun addPhoto(taskId: String, localUri: Uri, ctx: Context): String {
        val uid = uid()
        val key = "$uid/$taskId/${System.currentTimeMillis()}.jpg"
        ctx.contentResolver.openInputStream(localUri).use { input ->
            requireNotNull(input) { "Cannot open $localUri" }
            val bytes = input.readBytes()
            Supa.client.storage.from("photos").upload(key, bytes) { upsert = true }
        }
        Supa.client.postgrest.from("task_photos").insert(
            mapOf("user_id" to uid, "task_id" to taskId, "storage_path" to key)
        )
        val signed = Supa.client.storage.from("photos").createSignedUrl(key, 60.minutes) // 1h
        return signed
    }
    suspend fun loadTasks(supabase: SupabaseClient, userId: String): List<Task> {
        return supabase
            .from("tasks")
            .select {
                eq("user_id", userId)
                order("due", order=Order.ASCENDING)
            }
            .decodeList<Task>() // your @Serializable data class
    }

    // Example of signed URL (note Duration!):
    suspend fun signedPhotoUrl(
        supabase: SupabaseClient,
        storagePath: String
    ): String {
        val storage = supabase.storage
        val bucket = storage.from("photos")
        return bucket.createSignedUrl(path = storagePath, expiresIn = 10.minutes)
    }

    suspend fun rename(id: String, title: String) {
        Supa.client.postgrest.from("tasks").update(mapOf("title" to title.trim())).eq("id", id)
    }

    suspend fun setPriority(id: String, priority: Int) {
        Supa.client.postgrest.from("tasks").update(mapOf("priority" to priority)).eq("id", id)
    }


    // Stream photos for a given task as signed URLs
    fun photosFlow(taskId: String): Flow<List<TaskPhoto>> = callbackFlow {
        val pg = Supa.client.postgrest
        suspend fun emitAll() {
            val rows = pg.from("task_photos")
                .select()
                .eq("user_id", uid())
                .eq("task_id", taskId)
                .decodeList<TaskPhotoRow>()
            val urls = rows.map { row ->
                val url = Supa.client.storage.from("photos").createSignedUrl(row.storage_path, 60.minutes)
                TaskPhoto(url)
            }
            trySend(urls)
        }
        CoroutineScope(Dispatchers.IO).launch { emitAll() }

        val ch = Supa.client.realtime.channel("realtime:public:task_photos")
        ch.postgresChangeFlow<PostgresAction>(schema = "public", table = "task_photos")
            .onStart { ch.subscribe() }
            .collect { emitAll() }
        awaitClose { ch.unsubscribe() }
    }
}
