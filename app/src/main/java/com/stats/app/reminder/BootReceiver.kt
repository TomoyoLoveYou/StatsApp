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

/** 开机 / 应用更新后重建全部提醒，并补记账目 */
@AndroidEntryPoint
class BootReceiver : BroadcastReceiver() {

    @Inject
    lateinit var scheduler: ReminderScheduler

    @Inject
    lateinit var catchUp: AutoCompleteCatchUp

    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action ?: return
        if (action != Intent.ACTION_BOOT_COMPLETED && action != Intent.ACTION_MY_PACKAGE_REPLACED) {
            return
        }
        AppLog.i("Reminder", "收到 $action，开始重建全部提醒")
        val pending = goAsync()
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            try {
                scheduler.rescheduleAll()
                catchUp.runAll()
            } catch (t: Throwable) {
                AppLog.e("Reminder", "重建提醒失败", t)
            } finally {
                pending.finish()
            }
        }
    }
}
