package com.stats.app

import android.app.Application
import android.os.Process
import com.stats.app.reminder.AutoCompleteCatchUp
import com.stats.app.reminder.NotificationHelper
import com.stats.app.reminder.ReminderScheduler
import com.stats.app.util.AppLocale
import com.stats.app.util.AppLog
import dagger.hilt.android.HiltAndroidApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltAndroidApp
class StatsApp : Application() {

    @Inject
    lateinit var notificationHelper: NotificationHelper

    @Inject
    lateinit var scheduler: ReminderScheduler

    @Inject
    lateinit var catchUp: AutoCompleteCatchUp

    override fun onCreate() {
        super.onCreate()
        // 应用语言：把系统级 per-app locale 与用户设置对齐（桌面图标名 / 系统设置入口）
        AppLocale.syncSystemLocale(this, AppLocale.read(this))
        AppLog.i(
            "App",
            "启动 版本=${BuildConfig.VERSION_NAME} debug=${BuildConfig.DEBUG} pid=${Process.myPid()} " +
                    "语言=${AppLocale.read(this)}"
        )
        notificationHelper.ensureChannels()
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            scheduler.rescheduleAll()
            // 补上"应用被冻结 / 杀死期间"漏掉的自动完成
            catchUp.runAll()
        }
    }
}
