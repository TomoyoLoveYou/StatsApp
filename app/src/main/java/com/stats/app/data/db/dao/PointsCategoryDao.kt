package com.stats.app.data.db.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import com.stats.app.data.db.entity.PointsCategoryEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface PointsCategoryDao {

    @Query("SELECT * FROM points_category ORDER BY sortOrder, id")
    fun observeAll(): Flow<List<PointsCategoryEntity>>

    @Query("SELECT * FROM points_category ORDER BY sortOrder, id")
    suspend fun getAll(): List<PointsCategoryEntity>

    @Insert
    suspend fun insert(category: PointsCategoryEntity): Long

    @Update
    suspend fun update(category: PointsCategoryEntity)

    @Delete
    suspend fun delete(category: PointsCategoryEntity)

    @Query("DELETE FROM points_category")
    suspend fun deleteAll()
}
