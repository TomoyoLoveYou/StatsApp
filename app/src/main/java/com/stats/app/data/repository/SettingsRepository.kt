package com.stats.app.data.repository

import android.content.Context
import com.stats.app.data.model.AppLanguage
import com.stats.app.data.model.ThemeMode
import com.stats.app.util.AppLocale
import com.stats.app.util.AppLog
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 应用偏好设置（SharedPreferences 存储 + StateFlow 暴露）。
 *
 *  - dayStartHour：日界线。0 = 自然日（00:00 分界）；1..4 = 凌晨该点之前记入前一天（熬夜党）。
 *  - themeMode：主题模式（跟随系统 / 浅色 / 深色）。
 *  - appLanguage：界面语言（跟随本机 / 中文 / 英文），存储逻辑见 [AppLocale]（需在 attachBaseContext 前可读）。
 */
@Singleton
class SettingsRepository @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    private val _dayStartHour = MutableStateFlow(prefs.getInt(KEY_DAY_START_HOUR, 0))
    val dayStartHourFlow: StateFlow<Int> = _dayStartHour.asStateFlow()

    /** 同步读取（非 UI 路径使用，如打卡归属计算） */
    val dayStartHour: Int get() = _dayStartHour.value

    private val _themeMode = MutableStateFlow(readThemeMode())
    val themeModeFlow: StateFlow<ThemeMode> = _themeMode.asStateFlow()

    private val _appLanguage = MutableStateFlow(AppLocale.read(context))
    val appLanguageFlow: StateFlow<AppLanguage> = _appLanguage.asStateFlow()

    fun setDayStartHour(hour: Int) {
        val clamped = hour.coerceIn(0, 4)
        prefs.edit().putInt(KEY_DAY_START_HOUR, clamped).apply()
        _dayStartHour.value = clamped
        AppLog.i("Settings", "日界线已设为 $clamped 点${if (clamped == 0) "（自然日）" else "（凌晨 $clamped 点前算前一天）"}")
    }

    fun setThemeMode(mode: ThemeMode) {
        prefs.edit().putString(KEY_THEME_MODE, mode.name).apply()
        _themeMode.value = mode
        AppLog.i("Settings", "主题模式已设为 $mode")
    }

    /** 切换界面语言：写入后由调用方重建 Activity / 同步系统级 locale */
    fun setAppLanguage(lang: AppLanguage) {
        AppLocale.write(context, lang)
        _appLanguage.value = lang
        AppLog.i("Settings", "应用语言已设为 $lang（实际 ${AppLocale.resolve(lang).toLanguageTag()}）")
    }

    private fun readThemeMode(): ThemeMode {
        val name = prefs.getString(KEY_THEME_MODE, null) ?: return ThemeMode.SYSTEM
        return runCatching { ThemeMode.valueOf(name) }.getOrDefault(ThemeMode.SYSTEM)
    }

    private companion object {
        const val PREFS_NAME = "stats_settings"
        const val KEY_DAY_START_HOUR = "day_start_hour"
        const val KEY_THEME_MODE = "theme_mode"
    }
}
