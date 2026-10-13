package com.stats.app.data.db.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import com.stats.app.data.db.entity.TaskEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface TaskDao {

    @Query("SELECT * FROM task WHERE archivedAt IS NULL ORDER BY enabled DESC, id")
    fun observeAll(): Flow<List<TaskEntity>>

    @Query("SELECT * FROM task WHERE id = :id")
    suspend fun getById(id: Long): TaskEntity?

    @Insert
    suspend fun insert(task: TaskEntity): Long

    @Update
    suspend fun update(task: TaskEntity)

    @Delete
    suspend fun delete(task: TaskEntity)

    @Query("UPDATE task SET enabled = :enabled WHERE id = :id")
    suspend fun setEnabled(id: Long, enabled: Boolean)

    @Query("SELECT * FROM task ORDER BY id")
    suspend fun getAllOnce(): List<TaskEntity>

    /** 已删除（归档）的任务：历史任务页展示用，最近删除的在前 */
    @Query("SELECT * FROM task WHERE archivedAt IS NOT NULL ORDER BY archivedAt DESC")
    fun observeArchived(): Flow<List<TaskEntity>>

    /** 软删除 / 恢复：archivedAt = null 表示恢复 */
    @Query("UPDATE task SET archivedAt = :at WHERE id = :id")
    suspend fun setArchivedAt(id: Long, at: Long?)

    @Query("DELETE FROM task")
    suspend fun deleteAll()
}
