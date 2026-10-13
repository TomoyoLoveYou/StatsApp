package com.stats.app.data.db

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import com.stats.app.data.db.dao.CheckInDao
import com.stats.app.data.db.dao.PointsCategoryDao
import com.stats.app.data.db.dao.PointsRecordDao
import com.stats.app.data.db.dao.StatDao
import com.stats.app.data.db.dao.TaskDao
import com.stats.app.data.db.entity.CheckInEntity
import com.stats.app.data.db.entity.PointsCategoryEntity
import com.stats.app.data.db.entity.PointsRecordEntity
import com.stats.app.data.db.entity.TaskEntity

@Database(
    entities = [
        TaskEntity::class,
        CheckInEntity::class,
        PointsCategoryEntity::class,
        PointsRecordEntity::class,
    ],
    version = 1,
    exportSchema = false,
)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun taskDao(): TaskDao
    abstract fun checkInDao(): CheckInDao
    abstract fun pointsCategoryDao(): PointsCategoryDao
    abstract fun pointsRecordDao(): PointsRecordDao
    abstract fun statDao(): StatDao
}
