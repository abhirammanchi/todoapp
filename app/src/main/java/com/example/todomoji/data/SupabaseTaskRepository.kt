package com.example.todomoji.data

import android.content.Context
import android.net.Uri
import com.example.todomoji.Supa
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.launch
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.JsonObject


import java.time.LocalDate
import java.time.LocalTime
// For SupabaseTaskRepository.kt (or wherever used)
import kotlin.time.Duration.Companion.minutes
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.postgrest.query.Order
import io.github.jan.supabase.realtime.realtime
import io.github.jan.supabase.realtime.channel
import io.github.jan.supabase.realtime.PostgresAction
import io.github.jan.supabase.realtime.postgresChangeFlow
import io.github.jan.supabase.storage.storage
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.SupabaseClient







class SupabaseTaskRepository {

    private fun uid(): String =
        Supa.client.auth.currentUserOrNull()?.id ?: error("Not signed in")

    private fun uidOrNull(): String? = Supa.client.auth.currentUserOrNull()?.id

    // Live list of ALL tasks for the user (you can filter by date in VM/UI)
    fun tasksFlow(userId: String): kotlinx.coroutines.flow.Flow<List<Task>> =
        kotlinx.coroutines.flow.callbackFlow {
            val pg = Supa.client.postgrest

            suspend fun fetchOwned(): List<TaskRow> =
                pg.from("tasks").select {
                    filter { eq("user_id", userId) }
                }.decodeList()

            suspend fun fetchSharedIds(): List<String> =
                pg.from("task_collaborators").select {
                    filter { eq("user_id", userId) }
                }.decodeList<TaskCollaboratorRow>().map { it.task_id }

            suspend fun fetchTasksByIds(ids: List<String>): List<TaskRow> {
                if (ids.isEmpty()) return emptyList()
                val chunks = ids.chunked(25)
                val results = mutableListOf<TaskRow>()
                for (chunk in chunks) {
                    val rows = pg.from("tasks").select {
                        filter {
                            // If your DSL has IN: inList("id", chunk)
                            // Fallback: OR each id
                            or { chunk.forEach { eq("id", it) } }
                        }
                    }.decodeList<TaskRow>()
                    results += rows
                }
                return results
            }

            suspend fun emitAll() {
                val owned = fetchOwned()
                val sharedIds = fetchSharedIds()
                val sharedRows = fetchTasksByIds(sharedIds)
                val collabIdSet = sharedIds.toSet()
                val all = (owned + sharedRows).distinctBy { it.id }
                trySend(all.map { row ->
                    row.toDomain(
                        currentUserId = userId,
                        shared = row.id in collabIdSet || row.user_id != userId
                    )
                })
            }

            // initial load
            kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.IO)
                .launch { runCatching { emitAll() } }

            // realtime: tasks
            val chTasks = Supa.client.realtime.channel("public:tasks")
            val jobTasks = launch(kotlinx.coroutines.Dispatchers.IO) {
                runCatching {
                    chTasks.postgresChangeFlow<io.github.jan.supabase.realtime.PostgresAction>(
                        schema = "public"
                    ) {
                        table = "tasks"
                    }
                        .onStart { runCatching { chTasks.subscribe(blockUntilSubscribed = true) } }
                        .collect { runCatching { emitAll() } }
                }
            }

            // realtime: collaborators
            val chCollab = Supa.client.realtime.channel("public:task_collaborators")
            val jobCollab = launch(kotlinx.coroutines.Dispatchers.IO) {
                runCatching {
                    chCollab.postgresChangeFlow<io.github.jan.supabase.realtime.PostgresAction>(
                        schema = "public"
                    ) {
                        table = "task_collaborators"
                    }
                        .onStart { runCatching { chCollab.subscribe(blockUntilSubscribed = true) } }
                        .collect { runCatching { emitAll() } }
                }
            }

            awaitClose {
                jobTasks.cancel(); jobCollab.cancel()
                launch(kotlinx.coroutines.Dispatchers.IO + kotlinx.coroutines.NonCancellable) {
                    runCatching { chTasks.unsubscribe() }
                    runCatching { chCollab.unsubscribe() }
                }
            }
        }
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

    private suspend fun resolveUserIdByEmail(email: String): String? {
        val pg = Supa.client.postgrest
        // rpc parameters must be a JsonObject (not Map)
        // decodeAs<T>() doesn't accept nullable T, so wrap in runCatching and return null on failure
        return runCatching {
            pg.rpc(
                function = "resolve_user_by_email",
                parameters = buildJsonObject {
                    put("p_email", email.trim().lowercase())
                }
            ).decodeAs<String>()   // returns the uuid; throws if null → handled by runCatching
        }.getOrNull()
    }


/** Insert a task and return its id (text/uuid from DB). */
suspend fun addTaskReturningId(title: String, due: LocalDate): String {
    val pg = Supa.client.postgrest
    // Insert with `returning=representation` to get the row back
    val inserted: List<TaskRow> = pg
        .from("tasks")
        .insert(
            TaskRowInsert(
                user_id = uid(),
                title = title.trim(),
                due = due.toString()
            ),
            returning = io.github.jan.supabase.postgrest.query.Returning.Representation
        )
        .decodeList()

    return inserted.first().id
}

/** Given an email, resolve user id via RPC and add as collaborator to a task. */
suspend fun addCollaboratorByEmail(taskId: String, email: String): Boolean {
    val pg = Supa.client.postgrest

    // Call your SQL function resolve_user_by_email(text) -> uuid
    val params: JsonObject = buildJsonObject { put("p_email", email.trim().lowercase()) }
    val resolved: String? = pg.rpc("resolve_user_by_email", params).decodeAs<String?>()
    val collaboratorId = resolved ?: return false

    // Insert into join table
    pg.from("task_collaborators").insert(
        mapOf("task_id" to taskId, "user_id" to collaboratorId)
    )
    return true
}


suspend fun toggle(id: String, current: Boolean) {
        val pg = Supa.client.postgrest
        pg.from("tasks").update(mapOf("completed" to !current)) {
            filter {
                eq("id", id)
                eq("user_id", uid()) // always pair id + user filter for safety
            }
        }
    }

    suspend fun delete(id: String) {
        Supa.client.postgrest.from("tasks").delete {
            filter {
                eq("id", id)
                eq("user_id", uid()) // always pair id + user filter for safety
            }
        }
    }

    suspend fun schedule(id: String, start: LocalTime?, end: LocalTime?) {
        Supa.client.postgrest.from("tasks")
            .update(mapOf("start" to start?.toString(), "end" to end?.toString()))
            {
                filter {
                    eq("id", id)
                    eq("user_id", uid()) // always pair id + user filter for safety
                }
            }
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
        val rows = supabase.postgrest
            .from("tasks")
            .select {
                filter { eq("user_id", userId) }
                order("due", order=Order.ASCENDING)
            }
            .decodeList<TaskRow>() // your @Serializable data class
        return rows.map { it.toDomain(currentUserId = userId, shared = false) }
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
        Supa.client.postgrest.from("tasks").update(mapOf("title" to title.trim()))
        { filter {
            eq("id", id)
            eq("user_id", uid()) // always pair id + user filter for safety
        }
        }
    }

    suspend fun setPriority(id: String, priority: Int) {
        Supa.client.postgrest.from("tasks").update(mapOf("priority" to priority))
        { filter {
            eq("id", id)
            eq("user_id", uid()) // always pair id + user filter for safety
        }
        }
    }


    // Stream photos for a given task as signed URLs
    fun photosFlow(taskId: String): Flow<List<TaskPhoto>> = callbackFlow {
        val userId = uidOrNull()
        if (userId == null) {
            trySend(emptyList())
            close()
            return@callbackFlow
        }
        val pg = Supa.client.postgrest
        suspend fun emitAll() {
            val rows: List<TaskPhotoRow> = pg
                .from("task_photos")
                .select {
                    filter {
                        eq("user_id", userId)
                        eq("task_id", taskId)
                    }
                }
                .decodeList()

            val urls: List<TaskPhoto> = rows.map { row ->
                val url: String = Supa.client.storage
                    .from("photos")
                    .createSignedUrl(row.storage_path, 60.minutes)
                TaskPhoto(url)
            }

            trySend(urls)
        }
        CoroutineScope(Dispatchers.IO).launch { runCatching { emitAll() } }

        val ch = Supa.client.realtime.channel("public:task_photos:$taskId")
        val job = launch(Dispatchers.IO) {
            ch.postgresChangeFlow<PostgresAction>(schema = "public") {
                table = "task_photos"
            }
                .onStart { runCatching { ch.subscribe() } }
                .collect { runCatching { emitAll() } }
        }
        awaitClose {
            job.cancel()
            launch(Dispatchers.IO) {
                runCatching { ch.unsubscribe() }
            }
        }
    }
}
