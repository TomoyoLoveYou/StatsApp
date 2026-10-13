package com.stats.app.reminder

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.stats.app.util.AppLog
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import javax.inject.Inject

/** 闹钟触发接收器（由 AlarmManager 唤起） */
@AndroidEntryPoint
class ReminderReceiver : BroadcastReceiver() {

    @Inject
    lateinit var handler: ReminderHandler

    @Inject
    lateinit var catchUp: AutoCompleteCatchUp

    override fun onReceive(context: Context, intent: Intent) {
        val taskId = intent.getLongExtra(ReminderScheduler.EXTRA_TASK_ID, -1L)
        if (taskId <= 0) {
            AppLog.w("Reminder", "收到无效触发（缺少 taskId）")
            return
        }
        AppLog.i("Reminder", "闹钟触发 task=#$taskId")
        val pending = goAsync()
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            try {
                handler.onAlarmFired(taskId)
                // 既然进程活着，顺带补上其他任务在冻结期间漏掉的自动完成
                runCatching { catchUp.runAll() }
            } catch (t: Throwable) {
                AppLog.e("Reminder", "触发处理失败 task=#$taskId", t)
            } finally {
                pending.finish()
            }
        }
    }
}
