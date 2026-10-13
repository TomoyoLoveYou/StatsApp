package com.stats.app.reminder

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.media.RingtoneManager
import android.net.Uri
import android.os.Build
import android.os.VibrationEffect
import android.os.VibratorManager
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.stats.app.MainActivity
import com.stats.app.R
import com.stats.app.data.db.entity.TaskEntity
import com.stats.app.data.model.RemindLevel
import com.stats.app.util.AppLocale
import com.stats.app.util.AppLog
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 通知与震动。
 *
 * 渠道设计：四个级别各一个渠道（声音 / 横幅由渠道重要度控制）：
 *  - 静默：IMPORTANCE_LOW，无声音、无横幅；
 *  - 通知：IMPORTANCE_DEFAULT，提示音（不弹横幅）；
 *  - 横幅：IMPORTANCE_HIGH，提示音 + 顶部横幅（heads-up）；
 *  - 闹钟：IMPORTANCE_HIGH + 全屏意图（铃声与振动由闹钟页负责）。
 *
 * ⚠️ 渠道的重要度与声音在首次创建后**不可修改**，只能删除重建。
 * 因此 [ensureChannels] 会逐渠道校验配置，发现不一致（含历史版本残留）时自动重建。
 *
 * 震动为任务级独立开关，通过代码主动触发（渠道不带震动）。
 */
@Singleton
class NotificationHelper @Inject constructor(
    @ApplicationContext private val context: Context,
) {

    private val notificationManager: NotificationManager =
        context.getSystemService(NotificationManager::class.java)

    /** Application 启动时校验渠道（幂等；必要时代码重建） */
    fun ensureChannels() {
        val sound = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
        ensureChannel(
            CHANNEL_SILENT,
            AppLocale.string(context, R.string.notif_channel_silent_name),
            AppLocale.string(context, R.string.notif_channel_silent_desc),
            NotificationManager.IMPORTANCE_LOW, null,
        )
        ensureChannel(
            CHANNEL_NOTIFY,
            AppLocale.string(context, R.string.notif_channel_notify_name),
            AppLocale.string(context, R.string.notif_channel_notify_desc),
            NotificationManager.IMPORTANCE_DEFAULT, sound,
        )
        ensureChannel(
            CHANNEL_BANNER,
            AppLocale.string(context, R.string.notif_channel_banner_name),
            AppLocale.string(context, R.string.notif_channel_banner_desc),
            NotificationManager.IMPORTANCE_HIGH, sound,
        )
        ensureChannel(
            CHANNEL_ALARM,
            AppLocale.string(context, R.string.notif_channel_alarm_name),
            AppLocale.string(context, R.string.notif_channel_alarm_desc),
            NotificationManager.IMPORTANCE_HIGH, sound,
        )
        // 清理历史遗留渠道：同 ID 删除重建会恢复旧配置，只能用新 ID 迁移后弃用旧 ID
        LEGACY_CHANNELS.forEach { id ->
            if (notificationManager.getNotificationChannel(id) != null) {
                notificationManager.deleteNotificationChannel(id)
                AppLog.i("Notify", "已清理旧渠道 $id（历史配置残留）")
            }
        }
        AppLog.i("Notify", "通知渠道校验完成（勿扰生效=${isDndActive()}）")
    }

    /** 任务提醒：按级别分发到对应渠道 */
    fun showReminder(task: TaskEntity) {
        val compat = NotificationManagerCompat.from(context)
        if (!compat.areNotificationsEnabled()) {
            AppLog.w("Notify", "通知权限未授予，提醒未发送 task=#${task.id}")
            return
        }
        val channelId = channelFor(task.remindLevel)
        val builder = NotificationCompat.Builder(context, channelId)
            .setSmallIcon(R.drawable.ic_stat_reminder)
            .setContentTitle(task.name)
            .setContentText(AppLocale.string(context, R.string.notif_reminder_text, task.points))
            .setContentIntent(openAppIntent())
            .setAutoCancel(true)
            .setPriority(
                when (task.remindLevel) {
                    RemindLevel.SILENT -> NotificationCompat.PRIORITY_LOW
                    RemindLevel.NOTIFY -> NotificationCompat.PRIORITY_DEFAULT
                    else -> NotificationCompat.PRIORITY_HIGH
                }
            )

        if (task.remindLevel == RemindLevel.ALARM && canUseFullScreenIntent()) {
            builder.setFullScreenIntent(alarmActivityIntent(task.id), true)
        }

        compat.notify(task.id.toInt(), builder.build())
        if (task.vibrate) vibrateOnce()
        AppLog.i(
            "Notify",
            "已发提醒 task=#${task.id}「${task.name}」级别=${task.remindLevel}${dndSuffix()}"
        )
    }

    /** 设置页测试：不依赖任务的测试通知 */
    fun showTest(level: RemindLevel) {
        val compat = NotificationManagerCompat.from(context)
        if (!compat.areNotificationsEnabled()) {
            AppLog.w("Notify", "通知权限未授予，测试未发送")
            return
        }
        val channelId = channelFor(level)
        val builder = NotificationCompat.Builder(context, channelId)
            .setSmallIcon(R.drawable.ic_stat_reminder)
            .setContentTitle(
                AppLocale.string(
                    context, R.string.notif_test_title,
                    AppLocale.string(context, level.levelNameRes()),
                )
            )
            .setContentText(AppLocale.string(context, R.string.notif_test_text))
            .setContentIntent(openAppIntent())
            .setAutoCancel(true)
            .setPriority(
                when (level) {
                    RemindLevel.SILENT -> NotificationCompat.PRIORITY_LOW
                    RemindLevel.NOTIFY -> NotificationCompat.PRIORITY_DEFAULT
                    else -> NotificationCompat.PRIORITY_HIGH
                }
            )
        compat.notify(TEST_NOTIFICATION_ID, builder.build())
        if (level != RemindLevel.SILENT) vibrateOnce()
        AppLog.i("Notify", "已发测试通知 级别=$level${dndSuffix()}")
    }

    /** 清除测试通知（调试命令用） */
    fun cancelTest() {
        NotificationManagerCompat.from(context).cancel(TEST_NOTIFICATION_ID)
    }

    /** 打卡完成后撤掉该任务残留的通知 */
    fun cancelForTask(taskId: Long) {
        NotificationManagerCompat.from(context).cancel(taskId.toInt())
    }

    fun canUseFullScreenIntent(): Boolean =
        if (Build.VERSION.SDK_INT >= 34) {
            notificationManager.canUseFullScreenIntent()
        } else {
            true
        }

    /** 勿扰 / 专注模式是否正在生效（生效时系统的声音与横幅会被抑制，通知仍会进入通知栏） */
    fun isDndActive(): Boolean = when (notificationManager.currentInterruptionFilter) {
        NotificationManager.INTERRUPTION_FILTER_PRIORITY,
        NotificationManager.INTERRUPTION_FILTER_ALARMS,
        NotificationManager.INTERRUPTION_FILTER_NONE,
        -> true
        else -> false
    }

    /** 调试：渠道配置摘要（dump_channels 命令） */
    fun channelStatus(): String {
        val channels = notificationManager.notificationChannels.associateBy { it.id }
        val parts = ALL_CHANNELS.map { id ->
            val ch = channels[id]
            if (ch == null) {
                "$id=未创建"
            } else {
                "$id[重要度=${ch.importance} 声音=${if (ch.sound == null) "无" else "有"} 名称=${ch.name}]"
            }
        }
        val line = "CHANNELS ${parts.joinToString(" ")} 勿扰生效=${isDndActive()}"
        AppLog.i("Notify", line)
        return line
    }

    // ---- 内部 ----

    /**
     * 渠道校验：配置一致则只更新文案；不一致（含历史版本残留）则删除重建。
     * 只在真正不一致时执行重建，避免每次启动重置渠道。
     */
    private fun ensureChannel(
        id: String,
        name: String,
        description: String,
        importance: Int,
        sound: Uri?,
    ) {
        val existing = notificationManager.getNotificationChannel(id)
        if (existing != null) {
            val importanceOk = existing.importance == importance
            val soundOk = (existing.sound == null) == (sound == null)
            if (importanceOk && soundOk) {
                if (existing.name != name || existing.description != description) {
                    existing.name = name
                    existing.description = description
                    notificationManager.createNotificationChannel(existing)
                    AppLog.i("Notify", "渠道文案更新 $id")
                }
                return
            }
            AppLog.i(
                "Notify",
                "渠道配置不符，重建 $id：重要度 ${existing.importance}→$importance，" +
                        "声音 ${if (existing.sound == null) "无" else "有"}→${if (sound == null) "无" else "有"}"
            )
            notificationManager.deleteNotificationChannel(id)
        }
        notificationManager.createNotificationChannel(
            NotificationChannel(id, name, importance).apply {
                this.description = description
                setSound(sound, if (sound != null) defaultAudioAttributes() else null)
                enableVibration(false)
            }
        )
    }

    private fun channelFor(level: RemindLevel): String = when (level) {
        RemindLevel.SILENT -> CHANNEL_SILENT
        RemindLevel.NOTIFY -> CHANNEL_NOTIFY
        RemindLevel.BANNER -> CHANNEL_BANNER
        RemindLevel.ALARM -> CHANNEL_ALARM
    }

    private fun dndSuffix(): String =
        if (isDndActive()) "，⚠️ 勿扰生效中（声音与横幅会被系统抑制）" else ""

    private fun vibrateOnce() {
        runCatching {
            val vibrator = context.getSystemService(VibratorManager::class.java)
                ?.defaultVibrator ?: return
            vibrator.vibrate(
                VibrationEffect.createOneShot(400, VibrationEffect.DEFAULT_AMPLITUDE)
            )
        }
    }

    private fun defaultAudioAttributes(): AudioAttributes =
        AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_NOTIFICATION)
            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
            .build()

    private fun openAppIntent(): PendingIntent {
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        return PendingIntent.getActivity(
            context, NOTIFICATION_OPEN_REQUEST, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    private fun alarmActivityIntent(taskId: Long): PendingIntent {
        val intent = Intent(context, ReminderAlarmActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
            putExtra(ReminderScheduler.EXTRA_TASK_ID, taskId)
        }
        return PendingIntent.getActivity(
            context, ALARM_ACTIVITY_REQUEST_BASE + taskId.toInt(), intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    companion object {
        /**
         * ⚠️ 渠道 ID 变更记录：
         * reminder_silent（v1）在早期版本创建时意外带上了系统提示音；Android 的渠道配置创建后不可修改，
         * 且同 ID 删除重建会恢复旧配置（防篡改机制），因此静默渠道改用 v2 ID 迁移，v1 弃用并清理。
         */
        const val CHANNEL_SILENT = "reminder_silent_v2"
        const val CHANNEL_NOTIFY = "reminder_notify"
        const val CHANNEL_BANNER = "reminder_banner"
        const val CHANNEL_ALARM = "reminder_alarm"
        const val TEST_NOTIFICATION_ID = 990001
        private const val NOTIFICATION_OPEN_REQUEST = 880001
        private const val ALARM_ACTIVITY_REQUEST_BASE = 700000

        private val ALL_CHANNELS = listOf(CHANNEL_SILENT, CHANNEL_NOTIFY, CHANNEL_BANNER, CHANNEL_ALARM)
        private val LEGACY_CHANNELS = listOf("reminder_silent")
    }
}

private fun RemindLevel.levelNameRes(): Int = when (this) {
    RemindLevel.SILENT -> R.string.fmt_level_silent
    RemindLevel.NOTIFY -> R.string.fmt_level_notify
    RemindLevel.BANNER -> R.string.fmt_level_banner
    RemindLevel.ALARM -> R.string.fmt_level_alarm
}
