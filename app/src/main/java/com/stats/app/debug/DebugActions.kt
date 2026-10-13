package com.stats.app.debug

import android.content.Context
import com.stats.app.data.db.dao.CheckInDao
import com.stats.app.data.db.dao.PointsCategoryDao
import com.stats.app.data.db.dao.PointsRecordDao
import com.stats.app.data.db.dao.TaskDao
import com.stats.app.data.db.entity.CheckInEntity
import com.stats.app.data.db.entity.PointsCategoryEntity
import com.stats.app.data.db.entity.PointsRecordEntity
import com.stats.app.data.db.entity.TaskEntity
import com.stats.app.data.model.CheckInSource
import com.stats.app.data.model.PeriodType
import com.stats.app.data.model.RemindLevel
import com.stats.app.data.model.RemindPlan
import com.stats.app.data.model.TriggerAction
import com.stats.app.data.util.PeriodKeys
import com.stats.app.reminder.AutoCompleteCatchUp
import com.stats.app.reminder.NotificationHelper
import com.stats.app.reminder.ReminderScheduler
import com.stats.app.util.AppLog
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.File
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.util.Random
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 调试动作集合：通过 adb 广播触发，用于开发期自检。
 * 支持命令见 [DebugReceiver]。
 */
@Singleton
class DebugActions @Inject constructor(
    private val taskDao: TaskDao,
    private val checkInDao: CheckInDao,
    private val categoryDao: PointsCategoryDao,
    private val recordDao: PointsRecordDao,
    private val scheduler: ReminderScheduler,
    private val notificationHelper: NotificationHelper,
    private val catchUp: AutoCompleteCatchUp,
) {

    /** 数据状态体检：把各表数量与积分总额打到日志（一行结构化文本） */
    suspend fun dumpStatus(): String {
        val tasks = taskDao.getAllOnce()
        val categories = categoryDao.getAll()
        val checkIns = checkInDao.countActive()
        val records = recordDao.countAll()
        val total = recordDao.sumAll()
        val line = "STATUS tasks=${tasks.size} enabled=${tasks.count { it.enabled }} " +
                "categories=${categories.size} checkIns=$checkIns records=$records pointsTotal=$total"
        AppLog.i("Debug", line)
        return line
    }

    /** 打印每个任务的下次提醒时间和 plan 细节（验证调度计算） */
    suspend fun dumpReminders() {
        val tasks = taskDao.getAllOnce()
        val now = LocalDateTime.now()
        AppLog.i("Debug", "REMINDER 共 ${tasks.size} 个任务，当前时间=$now")
        tasks.forEach { t ->
            val next = scheduler.nextTrigger(t, now)
            AppLog.i(
                "Debug",
                "REMINDER task=#${t.id}「${t.name}」启用=${t.enabled} 行为=${t.triggerAction} " +
                        "级别=${t.remindLevel} 计划=${t.remindPlan.time} 周选=${t.remindPlan.weekDays} " +
                        "月日=${t.remindPlan.monthDay} 下次=${next ?: "无"}"
            )
        }
    }

    /** 设置任务提醒时刻并重新调度（调试用，验证真实闹钟投递） */
    suspend fun setRemindTime(taskId: Long, time: String) {
        val task = taskDao.getById(taskId)
        if (task == null) {
            AppLog.w("Debug", "set_time 失败：任务 #$taskId 不存在")
            return
        }
        val updated = task.copy(remindPlan = task.remindPlan.copy(time = time))
        taskDao.update(updated)
        scheduler.schedule(updated)
        AppLog.i("Debug", "已设置 task=#$taskId「${task.name}」提醒时刻=$time 并重调度")
    }

    /** 手动执行一次"漏触发补账"（调试用，验证补账逻辑） */
    suspend fun catchUpNow() {
        val n = catchUp.runAll()
        AppLog.i("Debug", "catch_up 完成：本次补记 $n 条")
    }

    /** 创建"自动完成"测试任务（调试用；默认每日、08:00、10 分） */
    suspend fun mkAutoTask(name: String, time: String): Long {
        val cat = categoryDao.getAll().firstOrNull()?.id ?: 0L
        val entity = TaskEntity(
            name = name.ifBlank { "AUTO测试" },
            periodType = PeriodType.DAILY,
            triggerAction = TriggerAction.AUTO_COMPLETE,
            remindPlan = RemindPlan(time = time),
            categoryId = cat,
            points = 10,
        )
        val id = taskDao.insert(entity)
        taskDao.getById(id)?.let { scheduler.schedule(it) }
        AppLog.i("Debug", "已创建自动完成任务 #$id「${entity.name}」时间=$time 分值=${entity.points}")
        return id
    }

    /** 切换任务的到点行为（调试用；action = AUTO / REMIND） */
    suspend fun setAction(taskId: Long, actionName: String) {
        val task = taskDao.getById(taskId)
        if (task == null) {
            AppLog.w("Debug", "set_action 失败：任务 #$taskId 不存在")
            return
        }
        val action = when (actionName.uppercase()) {
            "AUTO" -> TriggerAction.AUTO_COMPLETE
            "REMIND" -> TriggerAction.REMIND
            else -> null
        }
        if (action == null) {
            AppLog.w("Debug", "set_action 失败：未知行为 $actionName（可选 AUTO / REMIND）")
            return
        }
        val updated = task.copy(triggerAction = action)
        taskDao.update(updated)
        scheduler.schedule(updated)
        AppLog.i("Debug", "已设置 task=#$taskId「${task.name}」到点行为=$action 并重调度")
    }

    /** 注入演示任务（覆盖四种周期 / 提醒与自动完成 / 不同分值分类） */
    suspend fun seedDemo() {
        val cats = categoryDao.getAll()
        fun catId(name: String): Long =
            cats.firstOrNull { it.name == name }?.id ?: cats.firstOrNull()?.id ?: 0L

        val samples = listOf(
            TaskEntity(
                name = "早起喝一杯水",
                periodType = PeriodType.DAILY,
                remindPlan = RemindPlan(time = "08:00"),
                remindLevel = RemindLevel.BANNER,
                categoryId = catId("生活"),
                points = 10,
            ),
            TaskEntity(
                name = "读书 30 分钟",
                periodType = PeriodType.DAILY,
                remindPlan = RemindPlan(time = "21:00"),
                remindLevel = RemindLevel.ALARM,
                categoryId = catId("学习"),
                points = 20,
            ),
            TaskEntity(
                name = "本周锻炼 3 次",
                periodType = PeriodType.WEEKLY,
                periodTarget = 3,
                remindPlan = RemindPlan(time = "19:00"),
                remindLevel = RemindLevel.NOTIFY,
                categoryId = catId("运动"),
                points = 15,
            ),
            TaskEntity(
                name = "每月复盘",
                periodType = PeriodType.MONTHLY,
                remindPlan = RemindPlan(time = "20:00", monthDay = 1),
                remindLevel = RemindLevel.SILENT,
                categoryId = catId("生活"),
                points = 30,
            ),
            TaskEntity(
                name = "记账",
                periodType = PeriodType.DAILY,
                triggerAction = TriggerAction.AUTO_COMPLETE,
                categoryId = catId("生活"),
                points = 5,
            ),
            TaskEntity(
                name = "买一次水果",
                periodType = PeriodType.ONE_TIME,
                remindPlan = RemindPlan(time = "18:00"),
                remindLevel = RemindLevel.NOTIFY,
                categoryId = catId("生活"),
                points = 10,
            ),
            TaskEntity(
                name = "喝水",
                periodType = PeriodType.STANDING,
                categoryId = catId("生活"),
                points = 5,
            ),
        )
        samples.forEach { taskDao.insert(it) }
        AppLog.i("Debug", "已注入 ${samples.size} 个演示任务: ${samples.joinToString { it.name }}")
    }

    /**
     * 注入历史打卡数据（统计验证用）：
     * 清空打卡与流水后，为最近 [days] 天按任务周期生成符合直觉的演示记录。
     * 返回生成条数摘要。
     */
    suspend fun seedHistory(days: Int = 77): String {
        checkInDao.deleteAll()
        recordDao.deleteAll()
        val tasks = taskDao.getAllOnce().filter { it.archivedAt == null }
        if (tasks.isEmpty()) {
            AppLog.w("Debug", "seed_history 失败：没有任何任务（先 seed_demo）")
            return "no tasks"
        }
        val rand = Random(20261010L) // 固定种子，结果可重现
        val zone = ZoneId.systemDefault()
        val today = LocalDate.now()
        val now = LocalDateTime.now()
        var checkIns = 0
        var records = 0

        for (offset in days downTo 0) {
            val date = today.minusDays(offset.toLong())
            for (task in tasks) {
                val probability = when (task.periodType) {
                    PeriodType.DAILY -> 0.72
                    PeriodType.WEEKLY -> 0.42
                    PeriodType.MONTHLY -> if (date.dayOfMonth <= 3) 0.5 else 0.0
                    PeriodType.YEARLY -> if (date.monthValue == 1 && date.dayOfMonth <= 2) 0.8 else 0.0
                    PeriodType.ONE_TIME -> if (offset == days) 0.8 else 0.0 // 仅在最早一天完成一次
                    PeriodType.STANDING -> 0.55
                }
                if (rand.nextDouble() >= probability) continue

                // 当天的时间取 07:00 ~ 21:00 随机；若是今天则不产生未来时间
                val maxHour = if (offset == 0) now.hour.coerceIn(8, 21) else 21
                val hour = 7 + rand.nextInt((maxHour - 7).coerceAtLeast(1))
                val minute = rand.nextInt(60)
                val millis = date.atTime(hour, minute).atZone(zone).toInstant().toEpochMilli()

                val checkInId = checkInDao.insert(
                    CheckInEntity(
                        taskId = task.id,
                        periodKey = PeriodKeys.periodKey(task.periodType, date),
                        completedAt = millis,
                        source = if (task.triggerAction == TriggerAction.AUTO_COMPLETE) {
                            CheckInSource.AUTO
                        } else {
                            CheckInSource.MANUAL
                        },
                    )
                )
                recordDao.insert(
                    PointsRecordEntity(
                        categoryId = task.categoryId,
                        checkInId = checkInId,
                        value = task.points,
                        createdAt = millis,
                        note = "演示数据",
                    )
                )
                checkIns++
                records++
            }
        }
        val line = "seed_history 完成：$days 天窗口，checkIns=$checkIns records=$records"
        AppLog.i("Debug", line)
        return line
    }

    /** 清空任务 / 打卡 / 积分记录（保留积分类别） */
    suspend fun clearAll() {
        taskDao.deleteAll()
        checkInDao.deleteAll()
        recordDao.deleteAll()
        AppLog.i("Debug", "已清空任务/打卡/积分记录（保留积分类别）")
    }

    /** 打印通知渠道配置摘要 + 勿扰状态（验证提示音/横幅链路） */
    fun dumpChannels() {
        notificationHelper.channelStatus()
    }

    /** 发送指定级别的测试通知（真机验证用；level=CANCEL 时清除测试通知） */
    fun testNotify(levelName: String) {
        if (levelName.equals("CANCEL", ignoreCase = true)) {
            notificationHelper.cancelTest()
            AppLog.i("Debug", "已清除测试通知")
            return
        }
        val level = runCatching { RemindLevel.valueOf(levelName.uppercase()) }.getOrNull()
        if (level == null) {
            AppLog.w("Debug", "test_notify 失败：未知级别 $levelName（可选 SILENT / NOTIFY / BANNER / ALARM / CANCEL）")
            return
        }
        notificationHelper.showTest(level)
    }

    /** 导出全量数据为 JSON，写入 app 内部 files/dump.json，通过 run-as 拉取 */
    suspend fun dumpDb(context: Context): String {
        val dump = DebugDump(
            exportedAt = System.currentTimeMillis(),
            tasks = taskDao.getAllOnce(),
            categories = categoryDao.getAll(),
            checkIns = checkInDao.getAllOnce(),
            records = recordDao.getAllOnce(),
        )
        val json = Json { prettyPrint = true; encodeDefaults = true }
        val text = json.encodeToString(DebugDump.serializer(), dump)
        val file = File(context.filesDir, "dump.json")
        file.writeText(text)
        AppLog.i("Debug", "dump 已写入 ${file.absolutePath} （${text.length} chars）")
        return text
    }
}

@Serializable
data class DebugDump(
    val exportedAt: Long,
    val tasks: List<TaskEntity>,
    val categories: List<PointsCategoryEntity>,
    val checkIns: List<CheckInEntity>,
    val records: List<PointsRecordEntity>,
)
