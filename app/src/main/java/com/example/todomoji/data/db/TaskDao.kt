package com.example.todomoji.data.db

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface TaskDao {
    @Query("SELECT * FROM tasks ORDER BY id DESC")
    fun allTasks(): Flow<List<TaskEntity>>

    @Insert
    suspend fun insertTask(task: TaskEntity): Long

    @Query("UPDATE tasks SET completed = 1 - completed WHERE id = :id")
    suspend fun toggleTask(id: Long)

    @Query("DELETE FROM tasks WHERE id = :id")
    suspend fun deleteTask(id: Long)

    @Query("SELECT * FROM task_photos")
    fun allPhotos(): Flow<List<TaskPhotoEntity>>

    @Insert
    suspend fun insertPhoto(photo: TaskPhotoEntity): Long

    @Query("DELETE FROM task_photos WHERE id = :photoId")
    suspend fun deletePhoto(photoId: Long)

    @Query("DELETE FROM task_photos WHERE taskId = :taskId")
    suspend fun deletePhotosForTask(taskId: Long)

    // New: title, note, priority
    @Query("UPDATE tasks SET title = :title WHERE id = :id")
    suspend fun updateTitle(id: Long, title: String)

    @Query("UPDATE tasks SET note = :note WHERE id = :id")
    suspend fun updateNote(id: Long, note: String?)

    @Query("UPDATE tasks SET priority = :priority WHERE id = :id")
    suspend fun updatePriority(id: Long, priority: Int)

    // New: schedule (timebox)
    @Query("UPDATE tasks SET startIsoTime = :start, endIsoTime = :end WHERE id = :id")
    suspend fun updateSchedule(id: Long, start: String?, end: String?)
}
