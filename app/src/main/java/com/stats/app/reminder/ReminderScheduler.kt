package com.stats.app.reminder

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import com.stats.app.MainActivity
import com.stats.app.data.db.dao.TaskDao
import com.stats.app.data.db.entity.TaskEntity
import com.stats.app.data.model.PeriodType
import com.stats.app.data.model.RemindLevel
import com.stats.app.util.AppLog
import dagger.hilt.android.qualifiers.ApplicationContext
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 提醒调度器：计算下一次触发时间并注册 AlarmManager 闹钟。
 *
 * 策略（对齐方案文档）：
 *  - 闹钟级用 setAlarmClock（最强保证，状态栏显示闹钟图标）；
 *  - 其余级别用 setExactAndAllowWhileIdle（精确、不占状态栏）；
 *  - 每个任务一个 PendingIntent（requestCode = taskId）；
 *  - 提醒与自动完成使用同一套调度：都在任务设定的提醒时刻触发，
 *    自动完成任务在触发时检查"本期未完成"则静默打卡（不发通知）；
 *  - 常驻任务不调度（无提醒）；单次任务按"每天时刻"调度，完成后由处理器跳过。
 */
@Singleton
class ReminderScheduler @Inject constructor(
    @ApplicationContext private val context: Context,
    private val taskDao: TaskDao,
) {
    private val alarmManager = context.getSystemService(AlarmManager::class.java)

    /** 全量重注册（App 启动 / 开机 / 应用更新后调用） */
    suspend fun rescheduleAll() {
        val tasks = taskDao.getAllOnce()
        tasks.forEach { schedule(it) }
        AppLog.i("Reminder", "全量重注册完成，共 ${tasks.size} 个任务")
    }

    /** 为任务注册下一次触发（先取消旧的；停用 / 已删除 / 常驻任务不注册） */
    fun schedule(task: TaskEntity) {
        cancel(task.id)
        if (!task.enabled) return
        if (task.archivedAt != null) return // 已删除（历史任务）不提醒
        if (task.periodType == PeriodType.STANDING) return // 常驻任务不提醒
        val now = LocalDateTime.now()
        val next = nextTrigger(task, now)
        if (next == null) {
            AppLog.w("Reminder", "无法计算下次触发 task=#${task.id}")
            return
        }
        val triggerAt = next.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
        register(task, triggerAt)
        AppLog.i(
            "Reminder",
            "注册 task=#${task.id}「${task.name}」级别=${task.remindLevel} 下次=$next"
        )
    }

    /** 取消任务的全部常规闹钟 */
    fun cancel(taskId: Long) {
        alarmManager.cancel(pendingIntent(taskId, REQUEST_BASE + taskId.toInt()))
    }

    /** 贪睡：delayMillis 后再次触发（独立 requestCode，不影响常规下次注册） */
    fun snooze(taskId: Long, delayMillis: Long = 10 * 60 * 1000L) {
        val triggerAt = System.currentTimeMillis() + delayMillis
        val pi = pendingIntent(taskId, SNOOZE_BASE + taskId.toInt())
        runCatching {
            if (alarmManager.canScheduleExactAlarms()) {
                alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pi)
            } else {
                alarmManager.set(AlarmManager.RTC_WAKEUP, triggerAt, pi)
            }
        }.onFailure { AppLog.e("Reminder", "贪睡注册失败 task=#$taskId", it) }
        AppLog.i("Reminder", "贪睡 task=#$taskId 将于 ${delayMillis / 60000} 分钟后再次触发")
    }

    private fun register(task: TaskEntity, triggerAtMillis: Long) {
        val pi = pendingIntent(task.id, REQUEST_BASE + task.id.toInt())
        try {
            when {
                task.remindLevel == RemindLevel.ALARM && alarmManager.canScheduleExactAlarms() -> {
                    alarmManager.setAlarmClock(
                        AlarmManager.AlarmClockInfo(triggerAtMillis, showAppIntent()),
                        pi,
                    )
                }
                alarmManager.canScheduleExactAlarms() -> {
                    alarmManager.setExactAndAllowWhileIdle(
                        AlarmManager.RTC_WAKEUP, triggerAtMillis, pi
                    )
                }
                else -> {
                    // 降级：无精确闹钟权限时用非精确闹钟（可能延迟）
                    alarmManager.set(AlarmManager.RTC_WAKEUP, triggerAtMillis, pi)
                    AppLog.w("Reminder", "无精确闹钟权限，降级为普通闹钟 task=#${task.id}")
                }
            }
        } catch (t: Throwable) {
            AppLog.e("Reminder", "注册失败 task=#${task.id}", t)
        }
    }

    private fun pendingIntent(taskId: Long, requestCode: Int): PendingIntent {
        val intent = android.content.Intent(context, ReminderReceiver::class.java).apply {
            action = ACTION_FIRE
            putExtra(EXTRA_TASK_ID, taskId)
        }
        return PendingIntent.getBroadcast(
            context,
            requestCode,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    private fun showAppIntent(): PendingIntent {
        val intent = android.content.Intent(context, MainActivity::class.java).apply {
            flags = android.content.Intent.FLAG_ACTIVITY_NEW_TASK or
                    android.content.Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        return PendingIntent.getActivity(
            context, 0, intent, PendingIntent.FLAG_IMMUTABLE
        )
    }

    /**
     * 计算下一次触发时间（提醒与自动完成共用）。
     *  - 每日 / 单次：今天 HH:mm，已过则明天；
     *  - 每周：未来 8 天内第一个匹配的「提醒日」（未选 = 每天）；
     *  - 每月：本月「几号」HH:mm，已过或不存在则下月（自动收敛到月末）；
     *  - 每年：今年「月/日」，已过则明年；
     *  - 常驻：不提醒（返回 null，不调度）。
     */
    fun nextTrigger(task: TaskEntity, now: LocalDateTime): LocalDateTime? {
        if (!task.enabled) return null
        if (task.archivedAt != null) return null
        if (task.periodType == PeriodType.STANDING) return null
        val plan = task.remindPlan
        val parts = plan.time.split(":")
        val hour = parts.getOrNull(0)?.toIntOrNull()?.coerceIn(0, 23) ?: 8
        val minute = parts.getOrNull(1)?.toIntOrNull()?.coerceIn(0, 59) ?: 0
        fun todayAt(date: LocalDate): LocalDateTime = date.atTime(hour, minute)

        return when (task.periodType) {
            PeriodType.DAILY, PeriodType.ONE_TIME -> {
                val candidate = todayAt(now.toLocalDate())
                if (candidate.isAfter(now)) candidate else candidate.plusDays(1)
            }

            PeriodType.WEEKLY -> {
                val days = plan.weekDays.ifEmpty { (1..7).toList() }
                (0L..7L)
                    .map { todayAt(now.toLocalDate().plusDays(it)) }
                    .firstOrNull { it.isAfter(now) && it.dayOfWeek.value in days }
            }

            PeriodType.MONTHLY -> {
                val day = (plan.monthDay ?: 1).coerceIn(1, 31)
                fun candidateFor(monthStart: LocalDate): LocalDateTime {
                    val clampedDay = day.coerceAtMost(monthStart.lengthOfMonth())
                    return monthStart.withDayOfMonth(clampedDay).atTime(hour, minute)
                }
                val thisMonthStart = now.toLocalDate().withDayOfMonth(1)
                val thisMonth = candidateFor(thisMonthStart)
                if (thisMonth.isAfter(now)) thisMonth
                else candidateFor(thisMonthStart.plusMonths(1))
            }

            PeriodType.YEARLY -> {
                val month = (plan.yearMonth ?: 1).coerceIn(1, 12)
                val day = (plan.yearDay ?: 1).coerceIn(1, 31)
                fun candidateFor(year: Int): LocalDateTime {
                    val base = LocalDate.of(year, month, 1)
                    val clampedDay = day.coerceAtMost(base.lengthOfMonth())
                    return base.withDayOfMonth(clampedDay).atTime(hour, minute)
                }
                val thisYear = candidateFor(now.year)
                if (thisYear.isAfter(now)) thisYear else candidateFor(now.year + 1)
            }

            PeriodType.STANDING -> null
        }
    }

    companion object {
        const val ACTION_FIRE = "com.stats.app.REMINDER_FIRE"
        const val EXTRA_TASK_ID = "task_id"
        private const val REQUEST_BASE = 10000
        private const val SNOOZE_BASE = 90000
    }
}
