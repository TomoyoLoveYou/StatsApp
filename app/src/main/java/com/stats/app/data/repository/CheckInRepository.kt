package com.stats.app.data.repository

import androidx.room.withTransaction
import com.stats.app.data.db.AppDatabase
import com.stats.app.data.db.dao.CheckInDao
import com.stats.app.data.db.dao.PointsRecordDao
import com.stats.app.data.db.dao.TaskLastActiveRow
import com.stats.app.data.db.dao.TaskPeriodCount
import com.stats.app.data.db.entity.CheckInEntity
import com.stats.app.data.db.entity.PointsRecordEntity
import com.stats.app.data.db.entity.TaskEntity
import com.stats.app.data.model.CheckInSource
import com.stats.app.data.util.DateRanges
import com.stats.app.data.util.PeriodKeys
import com.stats.app.reminder.NotificationHelper
import com.stats.app.util.AppLog
import kotlinx.coroutines.flow.Flow
import java.time.LocalDate
import java.time.ZoneId
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 打卡与积分流水。
 *
 * 规则（对齐方案文档）：
 *  - 打卡 = 写一条 CheckIn（带周期归属键）+ 一条正积分流水，事务保证一致；
 *  - 撤销 = 标记最新一条打卡为 revoked + 补一条负积分流水（不删历史）；
 *  - 补卡 = 指定归属日期打卡；积分流水的归属时间对非当日记录取该日 12:00；
 *  - AUTO 来源 = 到点自动完成（由提醒系统触发）；
 *  - 单次任务：完成一次即结束（撤销不限周期）；常驻任务：可多次完成、无完成态；
 *  - 归属日期一律使用**逻辑日**（受日界线设置影响：凌晨该点前算前一天）。
 */
@Singleton
class CheckInRepository @Inject constructor(
    private val database: AppDatabase,
    private val checkInDao: CheckInDao,
    private val pointsRecordDao: PointsRecordDao,
    private val notificationHelper: NotificationHelper,
    private val settingsRepository: SettingsRepository,
) {

    fun observeActiveCounts(): Flow<List<TaskPeriodCount>> = checkInDao.observeActiveCounts()

    /** 每任务最近一次有效打卡时间（历史任务页显示用） */
    fun observeLastActiveTimes(): Flow<List<TaskLastActiveRow>> = checkInDao.observeLastActiveTimes()

    suspend fun getForPeriod(taskId: Long, periodKey: String): List<CheckInEntity> =
        checkInDao.getForPeriod(taskId, periodKey)

    /** 逻辑日（凌晨日界线之前算前一天） */
    fun logicalToday(): LocalDate = DateRanges.logicalToday(settingsRepository.dayStartHour)

    /** 指定日期当天是否已有有效打卡（用于补卡防重复；按逻辑日边界） */
    suspend fun hasCheckOnDay(taskId: Long, date: LocalDate): Boolean {
        val h = settingsRepository.dayStartHour
        val start = DateRanges.startMillis(date, h)
        val end = DateRanges.startMillis(date.plusDays(1), h)
        return checkInDao.countOnDay(taskId, start, end) > 0
    }

    /** 该任务累计有效打卡数（单次任务的完成判定用） */
    suspend fun countActiveForTask(taskId: Long): Int = checkInDao.countActiveForTask(taskId)

    /** 打卡（date = null 时按逻辑日；可指定历史日期用于补卡） */
    suspend fun checkIn(
        task: TaskEntity,
        date: LocalDate? = null,
        source: CheckInSource = CheckInSource.MANUAL,
    ): Long {
        val d = date ?: logicalToday()
        val periodKey = PeriodKeys.periodKey(task.periodType, d)
        val timestamp = attributionTimestamp(d)
        val checkInId = database.withTransaction {
            val id = checkInDao.insert(
                CheckInEntity(
                    taskId = task.id,
                    periodKey = periodKey,
                    completedAt = timestamp,
                    source = source,
                )
            )
            pointsRecordDao.insert(
                PointsRecordEntity(
                    categoryId = task.categoryId,
                    checkInId = id,
                    value = task.points,
                    createdAt = timestamp,
                    note = "完成「${task.name}」",
                )
            )
            id
        }
        notificationHelper.cancelForTask(task.id)
        AppLog.i(
            "CheckIn",
            "打卡 task=#${task.id}「${task.name}」日期=$d 周期=$periodKey 来源=$source 加分=+${task.points} checkInId=$checkInId"
        )
        return checkInId
    }

    /** 撤销本周期内最新一次打卡（扣回积分；date = null 时按逻辑日） */
    suspend fun undoLatest(task: TaskEntity, date: LocalDate? = null) {
        val d = date ?: logicalToday()
        val periodKey = PeriodKeys.periodKey(task.periodType, d)
        val latest = checkInDao.getLatestForPeriod(task.id, periodKey)
        revokeCheckIn(task, latest, periodKey)
    }

    /** 撤销任务最近一次打卡（不限周期；用于单次任务的取消完成与常驻任务的误触恢复） */
    suspend fun undoLatestAny(task: TaskEntity) {
        val latest = checkInDao.getLatestActive(task.id)
        revokeCheckIn(task, latest, latest?.periodKey ?: "-")
    }

    /** 撤销核心：标记 revoked + 补一条负积分流水（不删历史） */
    private suspend fun revokeCheckIn(task: TaskEntity, latest: CheckInEntity?, periodKey: String) {
        if (latest == null) {
            AppLog.w("CheckIn", "撤销失败：task=#${task.id} 周期=$periodKey 没有可撤销的打卡")
            return
        }
        // 按"该次打卡当时获得的分值"反向记账（而非当前分值），保证 +X / -X 账目自洽
        val credited = pointsRecordDao.creditForCheckIn(latest.id)
        val amount = (credited ?: task.points.toLong()).toInt()
        val timestamp = System.currentTimeMillis()
        database.withTransaction {
            checkInDao.revoke(latest.id)
            pointsRecordDao.insert(
                PointsRecordEntity(
                    categoryId = task.categoryId,
                    checkInId = latest.id,
                    value = -amount,
                    createdAt = timestamp,
                    note = "撤销「${task.name}」",
                )
            )
        }
        AppLog.i(
            "CheckIn",
            "撤销打卡 task=#${task.id}「${task.name}」周期=$periodKey 扣分=-$amount checkInId=${latest.id}"
        )
    }

    /** 归属时间：逻辑日当日打卡记真实时间；补卡记归属日 12:00（避免边界问题） */
    private fun attributionTimestamp(date: LocalDate): Long =
        if (date == logicalToday()) {
            System.currentTimeMillis()
        } else {
            date.atTime(12, 0).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
        }
}
