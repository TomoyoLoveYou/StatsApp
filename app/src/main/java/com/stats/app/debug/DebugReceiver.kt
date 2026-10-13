package com.stats.app.debug

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.stats.app.reminder.ReminderAlarmActivity
import com.stats.app.reminder.ReminderScheduler
import com.stats.app.util.AppLog
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * 调试广播接收器（仅开发期使用）。
 *
 * 用法（adb）：
 *   adb shell am broadcast -a com.stats.app.DEBUG -p com.stats.app --es cmd dump_status
 *   adb shell am broadcast -a com.stats.app.DEBUG -p com.stats.app --es cmd seed_demo
 *   adb shell am broadcast -a com.stats.app.DEBUG -p com.stats.app --es cmd seed_history
 *   adb shell am broadcast -a com.stats.app.DEBUG -p com.stats.app --es cmd clear_all
 *   adb shell am broadcast -a com.stats.app.DEBUG -p com.stats.app --es cmd dump_db
 *   adb shell am broadcast -a com.stats.app.DEBUG -p com.stats.app --es cmd dump_reminders
 *   adb shell am broadcast -a com.stats.app.DEBUG -p com.stats.app --es cmd open_alarm --el task_id 4
 *   adb shell am broadcast -a com.stats.app.DEBUG -p com.stats.app --es cmd dump_channels
 *   adb shell am broadcast -a com.stats.app.DEBUG -p com.stats.app --es cmd test_notify --es level BANNER
 *   adb shell am broadcast -a com.stats.app.DEBUG -p com.stats.app --es cmd catch_up
 *   adb shell am broadcast -a com.stats.app.DEBUG -p com.stats.app --es cmd mk_auto --es name CATEST --es time 09:00
 *   adb shell am broadcast -a com.stats.app.DEBUG -p com.stats.app --es cmd set_action --el task_id 1 --es action AUTO
 *
 * 受 android.permission.DUMP 保护，仅 adb shell / 系统可触发。
 */
@AndroidEntryPoint
class DebugReceiver : BroadcastReceiver() {

    @Inject
    lateinit var debugActions: DebugActions

    override fun onReceive(context: Context, intent: Intent) {
        val cmd = intent.getStringExtra("cmd")
        if (cmd.isNullOrBlank()) {
            AppLog.w("Debug", "收到调试广播但缺少 cmd 参数")
            return
        }
        AppLog.i("Debug", "收到调试命令: $cmd")
        val appContext = context.applicationContext

        // open_alarm 需要直接启动 Activity，不走协程
        if (cmd == "open_alarm") {
            val id = intent.getLongExtra("task_id", -1L)
            val alarmIntent = Intent(context, ReminderAlarmActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                putExtra(ReminderScheduler.EXTRA_TASK_ID, id)
                putExtra(ReminderAlarmActivity.EXTRA_TEST, true) // 静音模式
            }
            runCatching { appContext.startActivity(alarmIntent) }
                .onFailure { AppLog.e("Debug", "打开闹钟页失败", it) }
            AppLog.i("Debug", "已打开静音闹钟页 task=#$id")
            return
        }

        val pending = goAsync()
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            try {
                when (cmd) {
                    "dump_status" -> debugActions.dumpStatus()
                    "seed_demo" -> debugActions.seedDemo()
                    "seed_history" -> debugActions.seedHistory(
                        intent.getIntExtra("days", 77),
                    )
                    "clear_all" -> debugActions.clearAll()
                    "dump_db" -> debugActions.dumpDb(appContext)
                    "dump_reminders" -> debugActions.dumpReminders()
                    "set_time" -> debugActions.setRemindTime(
                        intent.getLongExtra("task_id", -1L),
                        intent.getStringExtra("time") ?: "08:00",
                    )
                    "dump_channels" -> debugActions.dumpChannels()
                    "test_notify" -> debugActions.testNotify(
                        intent.getStringExtra("level") ?: "NOTIFY",
                    )
                    "catch_up" -> debugActions.catchUpNow()
                    "mk_auto" -> debugActions.mkAutoTask(
                        intent.getStringExtra("name") ?: "AUTO测试",
                        intent.getStringExtra("time") ?: "08:00",
                    )
                    "set_action" -> debugActions.setAction(
                        intent.getLongExtra("task_id", -1L),
                        intent.getStringExtra("action") ?: "AUTO",
                    )
                    else -> AppLog.w(
                        "Debug",
                        "未知命令: $cmd（支持 dump_status / seed_demo / seed_history / clear_all / " +
                                "dump_db / dump_reminders / set_time / open_alarm / dump_channels / test_notify / " +
                                "catch_up / mk_auto / set_action）"
                    )
                }
            } catch (t: Throwable) {
                AppLog.e("Debug", "调试命令执行失败: $cmd", t)
            } finally {
                pending.finish()
            }
        }
    }
}
