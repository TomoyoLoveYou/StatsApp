package com.stats.app.reminder

import com.stats.app.data.db.dao.CheckInDao
import com.stats.app.data.db.dao.TaskDao
import com.stats.app.data.db.entity.TaskEntity
import com.stats.app.data.model.CheckInSource
import com.stats.app.data.model.PeriodType
import com.stats.app.data.model.TriggerAction
import com.stats.app.data.repository.CheckInRepository
import com.stats.app.data.repository.SettingsRepository
import com.stats.app.data.util.PeriodKeys
import com.stats.app.util.AppLog
import kotlinx.coroutines.sync.withLock
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 自动完成"漏触发补账"。
 *
 * 背景：应用被系统冻结 / 杀死时，到点的闹钟无法唤醒进程，自动完成任务会漏记账；
 * 用户之后启动应用（或应用被任意提醒唤醒）时，本类把"这段时间里已到点、但没有记上的
 * 自动完成"逐条补记，保证账目与"任务本应自动完成"的语义一致。
 *
 * 规则：
 *  - 只处理"启用中 / 非归档 / 非常驻 / 到点行为 = 自动完成"的任务；
 *  - 扫描窗口 = 最近 [WINDOW_DAYS] 天，且不早于任务创建时间
 *    （避免新建任务时误补创建之前的历史）；
 *  - 对窗口内每一个"已到点"的触发时刻（按当前调度规则枚举）：
 *      ① 该触发归属周期内已有打卡数 ≥ 目标次数 → 跳过（已被手动打卡或正常触发覆盖）；
 *      ② 该触发时刻之后、同周期内已有打卡 → 跳过（这一次已被正常触发处理过，
 *         防止计数型任务被补重复）；
 *      ③ 否则补记一条：来源 = 自动，归属日期 = 该触发的逻辑日（受日界线设置影响），
 *         与实时触发的归属口径完全一致。
 *  - 与 [ReminderHandler] 共用 [ReminderLocks.checkIn]，防止并发重复记账。
 */
@Singleton
class AutoCompleteCatchUp @Inject constructor(
    private val taskDao: TaskDao,
    private val checkInDao: CheckInDao,
    private val checkInRepository: CheckInRepository,
    private val settingsRepository: SettingsRepository,
    private val scheduler: ReminderScheduler,
) {

    /** 对所有自动完成任务补账；返回本次补记的总条数 */
    suspend fun runAll(): Int {
        val candidates = taskDao.getAllOnce().filter {
            it.enabled && it.archivedAt == null &&
                    it.periodType != PeriodType.STANDING &&
                    it.triggerAction == TriggerAction.AUTO_COMPLETE
        }
        if (candidates.isEmpty()) return 0
        val now = LocalDateTime.now()
        var total = 0
        ReminderLocks.checkIn.withLock {
            candidates.forEach { total += runOne(it, now) }
        }
        if (total > 0) {
            AppLog.i("CatchUp", "补账完成：共补记 $total 条（扫描 ${candidates.size} 个自动完成任务）")
        }
        return total
    }

    /** 单任务补账；调用方需已持有 [ReminderLocks.checkIn] */
    private suspend fun runOne(task: TaskEntity, now: LocalDateTime): Int {
        val zone = ZoneId.systemDefault()
        val dayStartHour = settingsRepository.dayStartHour
        val created = LocalDateTime.ofInstant(Instant.ofEpochMilli(task.createdAt), zone)
        val windowStart = maxOf(now.minusDays(WINDOW_DAYS), created)

        var cursor = windowStart.minusSeconds(1)
        var filled = 0
        var scanned = 0
        while (scanned++ < MAX_OCCURRENCES) {
            val occurrence = scheduler.nextTrigger(task, cursor) ?: break
            if (occurrence.isAfter(now)) break
            cursor = occurrence

            // 该次触发应归属的逻辑日（与实时触发一致：日界线之前的时刻算前一天）
            val logicalDate = if (occurrence.hour < dayStartHour) {
                occurrence.toLocalDate().minusDays(1)
            } else {
                occurrence.toLocalDate()
            }
            val periodKey = PeriodKeys.periodKey(task.periodType, logicalDate)
            val target = if (task.periodType == PeriodType.ONE_TIME) 1 else task.periodTarget

            // ① 周期已达目标 → 跳过
            val count = if (task.periodType == PeriodType.ONE_TIME) {
                checkInDao.countActiveForTask(task.id)
            } else {
                checkInDao.getForPeriod(task.id, periodKey).size
            }
            if (count >= target) continue

            // ② 该触发时刻之后、同周期内已有打卡（正常触发刚处理过）→ 跳过
            val sinceMillis = occurrence.atZone(zone).toInstant().toEpochMilli()
            if (checkInDao.countActiveInPeriodSince(task.id, periodKey, sinceMillis) > 0) continue

            // ③ 补记
            checkInRepository.checkIn(task, date = logicalDate, source = CheckInSource.AUTO)
            filled++
            AppLog.i(
                "CatchUp",
                "补账 task=#${task.id}「${task.name}」漏触发=$occurrence 归属=$logicalDate 周期=$periodKey 加分=+${task.points}"
            )
        }
        return filled
    }

    companion object {
        /** 扫描窗口：最近 N 天（更早的漏触发不再自动补，避免长期不用后一次性大量补记） */
        private const val WINDOW_DAYS = 7L

        /** 单任务单次扫描的最大触发次数（防异常循环） */
        private const val MAX_OCCURRENCES = 60
    }
}
