package com.stats.app.di

import android.content.Context
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.sqlite.db.SupportSQLiteDatabase
import com.stats.app.data.db.AppDatabase
import com.stats.app.data.db.dao.CheckInDao
import com.stats.app.data.db.dao.PointsCategoryDao
import com.stats.app.data.db.dao.PointsRecordDao
import com.stats.app.data.db.dao.StatDao
import com.stats.app.data.db.dao.TaskDao
import com.stats.app.util.AppLog
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): AppDatabase {
        AppLog.i("DB", "打开数据库 stats.db（${context.packageName}）")
        return Room.databaseBuilder(context, AppDatabase::class.java, "stats.db")
            .addCallback(object : RoomDatabase.Callback() {
                override fun onCreate(db: SupportSQLiteDatabase) {
                    super.onCreate(db)
                    AppLog.i("DB", "数据库首次创建，预置默认积分类别")
                    // 预置默认积分类别（数据库首次创建时执行一次）
                    db.execSQL(
                        "INSERT INTO points_category (name, color, sortOrder) VALUES ('学习', 0xFF4A6CF7, 0)"
                    )
                    db.execSQL(
                        "INSERT INTO points_category (name, color, sortOrder) VALUES ('运动', 0xFF34A853, 1)"
                    )
                    db.execSQL(
                        "INSERT INTO points_category (name, color, sortOrder) VALUES ('生活', 0xFFF59E0B, 2)"
                    )
                }
            })
            .build()
    }

    @Provides
    fun provideTaskDao(db: AppDatabase): TaskDao = db.taskDao()

    @Provides
    fun provideCheckInDao(db: AppDatabase): CheckInDao = db.checkInDao()

    @Provides
    fun providePointsCategoryDao(db: AppDatabase): PointsCategoryDao = db.pointsCategoryDao()

    @Provides
    fun providePointsRecordDao(db: AppDatabase): PointsRecordDao = db.pointsRecordDao()

    @Provides
    fun provideStatDao(db: AppDatabase): StatDao = db.statDao()
}
