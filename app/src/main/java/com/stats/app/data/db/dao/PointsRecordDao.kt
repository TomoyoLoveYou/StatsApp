package com.stats.app.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import com.stats.app.data.db.entity.PointsRecordEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface PointsRecordDao {

    @Insert
    suspend fun insert(record: PointsRecordEntity): Long

    @Query("SELECT * FROM points_record ORDER BY createdAt DESC LIMIT :limit")
    fun observeRecent(limit: Int): Flow<List<PointsRecordEntity>>

    /** 某次打卡获得的正积分（撤销时按"当时的分值"反向记账，保证账目自洽；不存在则 null） */
    @Query("SELECT value FROM points_record WHERE checkInId = :checkInId AND value > 0 ORDER BY id ASC LIMIT 1")
    suspend fun creditForCheckIn(checkInId: Long): Long?

    @Query("SELECT COALESCE(SUM(value), 0) FROM points_record WHERE createdAt >= :from AND createdAt < :to")
    suspend fun sumBetween(from: Long, to: Long): Long

    @Query("SELECT COUNT(*) FROM points_record")
    suspend fun countAll(): Int

    @Query("SELECT COALESCE(SUM(value), 0) FROM points_record")
    suspend fun sumAll(): Long

    @Query("SELECT * FROM points_record ORDER BY createdAt DESC")
    suspend fun getAllOnce(): List<PointsRecordEntity>

    @Query("DELETE FROM points_record")
    suspend fun deleteAll()

    @Query("DELETE FROM points_record WHERE checkInId IN (SELECT id FROM check_in WHERE taskId = :taskId)")
    suspend fun deleteForCheckInsOfTask(taskId: Long)
}
