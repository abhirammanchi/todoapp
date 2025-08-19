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
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.LocalTime
import kotlin.time.Duration.Companion.minutes

// Supabase
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.postgrest.query.Order
import io.github.jan.supabase.postgrest.result.*   // decodeList / decodeSingle / decodeAs
import io.github.jan.supabase.realtime.realtime
import io.github.jan.supabase.realtime.channel
import io.github.jan.supabase.realtime.PostgresAction
import io.github.jan.supabase.realtime.postgresChangeFlow
import io.github.jan.supabase.storage.storage // <-- REQUIRED

import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.collectLatest
import android.util.Log
import kotlinx.serialization.json.jsonPrimitive

class SupabaseTaskRepository {
    // manual refresh trigger (no imports needed)
    private val refresh = kotlinx.coroutines.flow.MutableSharedFlow<Unit>(extraBufferCapacity = 1)

    private fun uid(): String =
        Supa.client.auth.currentUserOrNull()?.id ?: error("Not signed in")

    private fun uidOrNull(): String? =
        Supa.client.auth.currentUserOrNull()?.id

    // Live list of all tasks for userId (owned + shared)
    fun tasksFlow(userId: String): Flow<List<Task>> = callbackFlow {
        val pg = Supa.client.postgrest

        suspend fun fetchOwned(): List<TaskRow> =
            pg.from("tasks").select {
                // eq -> operator form
                filter{eq("user_id", userId)}
                order(column = "due", order = Order.ASCENDING)
            }.decodeList<TaskRow>()

        suspend fun fetchSharedIds(): List<String> =
            pg.from("task_collaborators").select {
                filter{eq("user_id", userId)}
            }.decodeList<TaskCollaboratorRow>().map { it.task_id }

        suspend fun fetchTasksByIds(ids: List<String>): List<TaskRow> {
            if (ids.isEmpty()) return emptyList()
            val chunks = ids.chunked(25)
            val acc = mutableListOf<TaskRow>()
            for (chunk in chunks) {
                // IN requires raw PostgREST syntax as a string
                val rows = pg.from("tasks").select {
                    filter { or { chunk.forEach { eq("id", it) } } }
                }.decodeList<TaskRow>()
                acc += rows
            }
            return acc
        }

        suspend fun emitAll() {
            val owned = fetchOwned()
            // ✅ always emit owned first so UI isn’t empty
            trySend(owned.map { row ->
                row.toDomain(currentUserId = userId, shared = false)
            })

            // now try shared, but never fail the whole thing
            val sharedIds = runCatching { fetchSharedIds() }.getOrElse { emptyList() }
            val shared = if (sharedIds.isEmpty()) emptyList() else runCatching {
                fetchTasksByIds(sharedIds)
            }.getOrElse { emptyList() }

            val collabIdSet = sharedIds.toSet()
            val all = (owned + shared).distinctBy { it.id }.map { row ->
                row.toDomain(
                    currentUserId = userId,
                    shared = row.id in collabIdSet || row.user_id != userId
                )
            }
            trySend(all)
        }


        // initial load
        launch(Dispatchers.IO) {
            try {
                emitAll()
                Log.d("TasksRepo", "initial emitAll OK")
            } catch (t: Throwable) {
                Log.e("TasksRepo", "emitAll failed", t)
            }
        }


        // realtime: tasks
        val chTasks = Supa.client.realtime.channel("realtime:public:tasks")
        val jobTasks = launch(Dispatchers.IO) {
                chTasks.postgresChangeFlow<PostgresAction>(schema = "public") {
                    table = "tasks"
                }.onStart {
                    runCatching { chTasks.subscribe(blockUntilSubscribed = true) }
                }.collect {  try { emitAll() } catch (t: Throwable) { Log.e("TasksRepo", "emitAll realtime tasks", t) } }
        }
      // realtime: collaborators
        val chCollab = Supa.client.realtime.channel("realtime:public:task_collaborators")
        val jobCollab = launch(Dispatchers.IO) {
            runCatching {
                chCollab.postgresChangeFlow<PostgresAction>(schema = "public") {
                    table = "task_collaborators"
                }.onStart {
                    runCatching { chCollab.subscribe(blockUntilSubscribed = true) }
                }.collect { runCatching { emitAll() } }
            }
        }
        val jobRefresh = launch(Dispatchers.IO) {
            refresh.collectLatest { try { emitAll() } catch (t: Throwable) { Log.e("TasksRepo", "emitAll refresh", t) } }
        }

        awaitClose {
            jobTasks.cancel(); jobCollab.cancel()
            launch(Dispatchers.IO + NonCancellable) {
                runCatching { chTasks.unsubscribe() }
                runCatching { chCollab.unsubscribe() }
            }
        }
    }

    // ---------- CRUD ----------

    suspend fun addTask(title: String, due: LocalDate) {
        Supa.client.postgrest.from("tasks").insert(
            TaskRowInsert(
                user_id = uid(),
                title = title.trim(),
                due = due.toString()
            )
        )
        refresh.tryEmit(Unit)
    }

    /** Insert and return the new id */
    suspend fun addTaskReturningId(title: String, due: LocalDate): String {
        val row = Supa.client.postgrest
            .from("tasks")
            .insert(
                TaskRowInsert(
                    user_id = uid(),
                    title = title.trim(),
                    due = due.toString()
                )
            ) {
                // request representation to decode the row back
                select()
            }
            .decodeSingle<TaskRow>()
        refresh.tryEmit(Unit)
        return row.id
    }

    /** collaboratorEmail -> user uuid via rpc('resolve_user_by_email') */
    suspend fun addCollaboratorByEmail(taskId: String, email: String) {
        val payload = kotlinx.serialization.json.JsonObject(
            mapOf("p_email" to kotlinx.serialization.json.JsonPrimitive(email.trim().lowercase()))
        )

        val res = try {
            Supa.client.postgrest.rpc(function = "get_user_id_by_email", parameters = payload)
        } catch (t: Throwable) {
            android.util.Log.e("TasksRepo", "rpc call failed", t)
            return
        }

        // Robustly extract UUID from any of these shapes:
        // 1) "uuid-string"
        // 2) {"get_user_id_by_email":"uuid-string"}
        // 3) ["uuid-string"]
        val collaboratorId = try {
            val el = res.decodeAs<kotlinx.serialization.json.JsonElement>()
            when (el) {
                is kotlinx.serialization.json.JsonPrimitive -> el.content
                is kotlinx.serialization.json.JsonObject -> el.values.firstOrNull()?.jsonPrimitive?.content
                is kotlinx.serialization.json.JsonArray -> el.firstOrNull()?.jsonPrimitive?.content
                else -> null
            }
        } catch (t: Throwable) {
            android.util.Log.e("TasksRepo", "rpc decode failed", t)
            null
        } ?: return

        try {
            Supa.client.postgrest.from("task_collaborators")
                .insert(mapOf("task_id" to taskId, "user_id" to collaboratorId))
            android.util.Log.d("TasksRepo", "inserted collaborator row for $collaboratorId")
            refresh.tryEmit(Unit)
        } catch (t: Throwable) {
            android.util.Log.e("TasksRepo", "insert collaborator failed", t)
        }
    }






    suspend fun toggle(id: String, currentCompleted: Boolean) {
        Supa.client.postgrest.from("tasks").update(
            mapOf("completed" to !currentCompleted)
        ) {
            filter{eq("id", id)}
        }
        refresh.tryEmit(Unit)
    }

    suspend fun delete(id: String) {
        Supa.client.postgrest.from("tasks").delete {
            filter{eq("id", id)}
        }
        refresh.tryEmit(Unit)
    }

    suspend fun schedule(id: String, start: LocalTime?, end: LocalTime?) {
        Supa.client.postgrest.from("tasks").update(
            mapOf("start" to start?.toString(), "end" to end?.toString())
        ) {
            filter{ eq("id", id) }
        }
        refresh.tryEmit(Unit)
    }

    suspend fun rename(id: String, title: String) {
        Supa.client.postgrest.from("tasks").update(mapOf("title" to title.trim())) {
            filter{ eq("id", id) }
        }
        refresh.tryEmit(Unit)
    }

    suspend fun setPriority(id: String, priority: Int) {
        Supa.client.postgrest.from("tasks").update(mapOf("priority" to priority)) {
            filter{ eq("id", id) }
        }
        refresh.tryEmit(Unit)
    }

    // ---------- PHOTOS ----------

    /** Upload a local photo and return a signed URL */
    suspend fun addPhoto(taskId: String, localUri: Uri, ctx: Context): String {
        val user = uid()
        val key = "$user/$taskId/${System.currentTimeMillis()}.jpg"

        ctx.contentResolver.openInputStream(localUri).use { input ->
            requireNotNull(input) { "Cannot open $localUri" }
            val bytes = input.readBytes()
            Supa.client.storage.from("photos").upload(key, bytes) { upsert = true }
        }

        Supa.client.postgrest.from("task_photos").insert(
            mapOf("user_id" to user, "task_id" to taskId, "storage_path" to key)
        )

        // Duration, not Int
        return Supa.client.storage.from("photos").createSignedUrl(key, 60.minutes)
    }

    /** Live signed URLs for a task's photos */
    fun photosFlow(taskId: String): Flow<List<TaskPhoto>> = callbackFlow {
        val pg = Supa.client.postgrest

        suspend fun emitAll() {
            val rows = pg.from("task_photos").select {
                filter{ eq("task_id", taskId) }
                order(column = "created_at", order = Order.DESCENDING)
            }.decodeList<TaskPhotoRow>()

            val urls = rows.map { row ->
                TaskPhoto(
                    url = Supa.client.storage.from("photos").createSignedUrl(row.storage_path, 60.minutes)
                )
            }
            trySend(urls)
        }

        launch(Dispatchers.IO) { runCatching { emitAll() } }

        val ch = Supa.client.realtime.channel("realtime:public:task_photos")
        val job = launch(Dispatchers.IO) {
            runCatching {
                ch.postgresChangeFlow<PostgresAction>(schema = "public") { table = "task_photos" }
                    .onStart { runCatching { ch.subscribe(blockUntilSubscribed = true) } }
                    .collect { runCatching { emitAll() } }
            }
        }

        awaitClose {
            job.cancel()
            launch(Dispatchers.IO + NonCancellable) { runCatching { ch.unsubscribe() } }
        }
    }

    // ---------- helpers for one-off loads ----------

    suspend fun loadTasks(supabase: io.github.jan.supabase.SupabaseClient, userId: String): List<Task> {
        val rows = supabase.from("tasks").select {
            filter{ eq("user_id", userId) }
            order(column = "due", order = Order.ASCENDING)
        }.decodeList<TaskRow>()
        return rows.map { it.toDomain(currentUserId = userId, shared = false) }
    }

    suspend fun signedPhotoUrl(supabase: io.github.jan.supabase.SupabaseClient, storagePath: String): String {
        return supabase.storage.from("photos").createSignedUrl(path = storagePath, expiresIn = 10.minutes)
    }
}
