package com.stats.app.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import com.stats.app.data.db.entity.CheckInEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface CheckInDao {

    @Query("SELECT * FROM check_in WHERE taskId = :taskId AND periodKey = :periodKey AND revoked = 0")
    fun observeForPeriod(taskId: Long, periodKey: String): Flow<List<CheckInEntity>>

    @Query("SELECT * FROM check_in WHERE taskId = :taskId AND periodKey = :periodKey AND revoked = 0")
    suspend fun getForPeriod(taskId: Long, periodKey: String): List<CheckInEntity>

    @Insert
    suspend fun insert(checkIn: CheckInEntity): Long

    @Query("UPDATE check_in SET revoked = 1 WHERE id = :id")
    suspend fun revoke(id: Long)

    @Query("SELECT * FROM check_in WHERE taskId = :taskId ORDER BY completedAt DESC LIMIT :limit")
    fun observeRecentForTask(taskId: Long, limit: Int): Flow<List<CheckInEntity>>

    @Query("SELECT COUNT(*) FROM check_in WHERE revoked = 0")
    suspend fun countActive(): Int

    @Query("SELECT * FROM check_in ORDER BY completedAt DESC")
    suspend fun getAllOnce(): List<CheckInEntity>

    @Query("DELETE FROM check_in")
    suspend fun deleteAll()

    @Query("SELECT * FROM check_in WHERE taskId = :taskId AND periodKey = :periodKey AND revoked = 0 ORDER BY completedAt DESC LIMIT 1")
    suspend fun getLatestForPeriod(taskId: Long, periodKey: String): CheckInEntity?

    /** 该任务最近一次有效打卡（不限周期；单次任务撤销 / 常驻任务误触恢复用） */
    @Query("SELECT * FROM check_in WHERE taskId = :taskId AND revoked = 0 ORDER BY completedAt DESC LIMIT 1")
    suspend fun getLatestActive(taskId: Long): CheckInEntity?

    /** 该任务累计有效打卡数（单次任务的完成判定用） */
    @Query("SELECT COUNT(*) FROM check_in WHERE taskId = :taskId AND revoked = 0")
    suspend fun countActiveForTask(taskId: Long): Int

    @Query("SELECT taskId, periodKey, COUNT(*) as count FROM check_in WHERE revoked = 0 GROUP BY taskId, periodKey")
    fun observeActiveCounts(): Flow<List<TaskPeriodCount>>

    @Query("DELETE FROM check_in WHERE taskId = :taskId")
    suspend fun deleteForTask(taskId: Long)

    @Query("SELECT COUNT(*) FROM check_in WHERE taskId = :taskId AND revoked = 0 AND completedAt >= :dayStart AND completedAt < :dayEnd")
    suspend fun countOnDay(taskId: Long, dayStart: Long, dayEnd: Long): Int

    /** 指定周期内、某时刻之后的有效打卡数（漏触发补账防重复用） */
    @Query("SELECT COUNT(*) FROM check_in WHERE taskId = :taskId AND periodKey = :periodKey AND revoked = 0 AND completedAt >= :sinceMillis")
    suspend fun countActiveInPeriodSince(taskId: Long, periodKey: String, sinceMillis: Long): Int

    /** 每任务最近一次有效打卡时间（历史任务页"完成于"显示用） */
    @Query("SELECT taskId, MAX(completedAt) AS lastCompletedAt FROM check_in WHERE revoked = 0 GROUP BY taskId")
    fun observeLastActiveTimes(): Flow<List<TaskLastActiveRow>>
}

/** 每任务最近一次有效打卡时间的聚合行 */
data class TaskLastActiveRow(
    val taskId: Long,
    val lastCompletedAt: Long,
)

/** 按 (任务, 周期) 聚合的有效打卡计数（供列表进度显示与统计） */
data class TaskPeriodCount(
    val taskId: Long,
    val periodKey: String,
    val count: Int,
)
