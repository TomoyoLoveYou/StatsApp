package com.stats.app

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import com.stats.app.data.repository.SettingsRepository
import com.stats.app.launcher.LauncherIconUpdater
import com.stats.app.reminder.AutoCompleteCatchUp
import com.stats.app.ui.StatsAppRoot
import com.stats.app.ui.theme.StatsAppTheme
import com.stats.app.util.AppLocale
import com.stats.app.util.AppLog
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @Inject
    lateinit var settingsRepository: SettingsRepository

    @Inject
    lateinit var catchUp: AutoCompleteCatchUp

    @Inject
    lateinit var launcherIcons: LauncherIconUpdater

    private val requestNotificationPermission =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            AppLog.i("UI", "通知权限请求结果: $granted")
        }

    /** 应用语言设置（包装 base context，使资源/Compose 文本随语言切换） */
    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(AppLocale.wrap(newBase))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        AppLog.i("UI", "MainActivity onCreate")
        enableEdgeToEdge()
        ensureNotificationPermission()
        setContent {
            val themeMode by settingsRepository.themeModeFlow.collectAsStateWithLifecycle()
            StatsAppTheme(themeMode = themeMode) {
                StatsAppRoot()
            }
        }
    }

    override fun onStart() {
        super.onStart()
        // 每次回到前台时兜底：补上"应用被冻结 / 杀死期间"漏掉的自动完成（幂等）
        lifecycleScope.launch { catchUp.runAll() }
        // 桌面图标跟随"昨日积分最高的分类"颜色（同一逻辑日内只计算一次）
        lifecycleScope.launch { launcherIcons.refresh() }
    }

    private fun ensureNotificationPermission() {
        if (Build.VERSION.SDK_INT >= 33) {
            val granted = checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) ==
                    PackageManager.PERMISSION_GRANTED
            if (!granted) {
                AppLog.i("UI", "请求通知权限")
                requestNotificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
    }
}
