package com.stats.app.reminder

import com.stats.app.data.db.dao.CheckInDao
import com.stats.app.data.db.dao.TaskDao
import com.stats.app.data.model.CheckInSource
import com.stats.app.data.model.PeriodType
import com.stats.app.data.model.TriggerAction
import com.stats.app.data.repository.CheckInRepository
import com.stats.app.data.repository.SettingsRepository
import com.stats.app.data.util.DateRanges
import com.stats.app.data.util.PeriodKeys
import com.stats.app.util.AppLog
import kotlinx.coroutines.sync.withLock
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 闹钟触发处理器：检查状态 → 通知/自动打卡 → 注册下一次。
 * "是否已完成"的判断使用逻辑日（受日界线设置影响）：
 *  - 单次任务 = 累计有效打卡 ≥ 1；
 *  - 周期任务 = 本周期打卡数 ≥ 目标；
 *  - 常驻任务不提醒（触发即忽略）。
 *
 * 与漏触发补账（[AutoCompleteCatchUp]）共用 [ReminderLocks.checkIn]，
 * 避免"进程刚被唤醒时补账与广播处理并发"导致同一触发被记两次。
 */
@Singleton
class ReminderHandler @Inject constructor(
    private val taskDao: TaskDao,
    private val checkInDao: CheckInDao,
    private val checkInRepository: CheckInRepository,
    private val settingsRepository: SettingsRepository,
    private val scheduler: ReminderScheduler,
    private val notificationHelper: NotificationHelper,
) {

    suspend fun onAlarmFired(taskId: Long) {
        ReminderLocks.checkIn.withLock { handle(taskId) }
    }

    /** 实际处理逻辑；调用方需已持有 [ReminderLocks.checkIn] */
    private suspend fun handle(taskId: Long) {
        val task = taskDao.getById(taskId)
        if (task == null || !task.enabled) {
            AppLog.i("Reminder", "触发忽略 task=#$taskId（不存在或已停用）")
            return
        }
        if (task.archivedAt != null) {
            AppLog.i("Reminder", "触发忽略 task=#${task.id}（已删除）")
            return
        }
        if (task.periodType == PeriodType.STANDING) {
            AppLog.i("Reminder", "触发忽略 task=#${task.id}（常驻任务不提醒）")
            return
        }

        val today = DateRanges.logicalToday(settingsRepository.dayStartHour)
        // 完成判定：单次任务看累计；其余看本周期
        val count = when (task.periodType) {
            PeriodType.ONE_TIME -> checkInDao.countActiveForTask(task.id)
            else -> {
                val periodKey = PeriodKeys.periodKey(task.periodType, today)
                checkInDao.getForPeriod(task.id, periodKey).size
            }
        }
        val target = if (task.periodType == PeriodType.ONE_TIME) 1 else task.periodTarget

        when (task.triggerAction) {
            TriggerAction.AUTO_COMPLETE -> {
                if (count < target) {
                    checkInRepository.checkIn(task, source = CheckInSource.AUTO)
                    AppLog.i("Reminder", "到点自动完成 task=#${task.id}「${task.name}」")
                } else {
                    AppLog.i("Reminder", "自动完成跳过（已完成 $count/$target）")
                }
            }

            TriggerAction.REMIND -> {
                if (count >= target) {
                    AppLog.i("Reminder", "提醒跳过（已完成 $count/$target）")
                } else {
                    notificationHelper.showReminder(task)
                }
            }
        }

        // 注册下一次触发
        scheduler.schedule(task)
    }
}
