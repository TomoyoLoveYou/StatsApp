package com.stats.app.util

import android.app.LocaleManager
import android.content.Context
import android.content.res.Configuration
import android.content.res.Resources
import android.os.Build
import android.os.LocaleList
import com.stats.app.data.model.AppLanguage
import java.util.Locale

/**
 * 应用语言（应用内自管的 per-app locale）。
 *
 *  - SYSTEM：跟随本机语言；本机语言既不是中文也不是英文时，按英文处理。
 *  - ZH / EN：固定简体中文 / 英文。
 *
 * 机制：
 *  1. 偏好写入 SharedPreferences（与 [com.stats.app.data.repository.SettingsRepository] 同一文件），
 *     可在 `attachBaseContext` 中同步读取；
 *  2. Activity 的 base context 经 [wrap] 包装后，Compose 文本 / 框架组件资源随语言切换；
 *  3. API 33+ 额外同步系统级 per-app locale，使桌面图标名与系统"应用语言"入口保持一致。
 */
object AppLocale {

    private const val PREFS_NAME = "stats_settings"
    private const val KEY_APP_LANGUAGE = "app_language"

    /** 同步读取当前语言设置（可在 Hilt 注入前调用） */
    fun read(base: Context): AppLanguage {
        val raw = prefs(base).getString(KEY_APP_LANGUAGE, null)
        if (raw != null) {
            return runCatching { AppLanguage.valueOf(raw) }.getOrDefault(AppLanguage.SYSTEM)
        }
        // 尚未设置过：如是旧版本升级，沿用旧版本通过系统级 per-app locale 固定的语言
        return legacyLanguage(base) ?: AppLanguage.SYSTEM
    }

    /**
     * 旧版本（v0.7.12 及以前）把界面语言固定为 zh-CN（系统级 per-app locale）。
     * 首次升级到本版本时沿用该语言，避免界面语言突然变化；用户随后可自行切换。
     */
    private fun legacyLanguage(base: Context): AppLanguage? {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return null
        return runCatching {
            val locales = base.getSystemService(LocaleManager::class.java)?.applicationLocales
            if (locales == null || locales.isEmpty) return null
            val tag = locales.get(0).toLanguageTag()
            when {
                tag.startsWith("zh") -> AppLanguage.ZH
                tag.startsWith("en") -> AppLanguage.EN
                else -> null
            }
        }.getOrNull()
    }

    /** 同步写入语言设置（随后应重建 Activity / 同步系统级 locale） */
    fun write(base: Context, lang: AppLanguage) {
        prefs(base).edit().putString(KEY_APP_LANGUAGE, lang.name).apply()
    }

    /** 语言设置对应的实际 Locale（SYSTEM 已处理"非中英 → 英文"的回退） */
    fun resolve(lang: AppLanguage): Locale = when (lang) {
        AppLanguage.ZH -> Locale.SIMPLIFIED_CHINESE
        AppLanguage.EN -> Locale.ENGLISH
        AppLanguage.SYSTEM -> {
            val system = Resources.getSystem().configuration.locales.get(0)
            if (system.language == "zh") Locale.SIMPLIFIED_CHINESE else Locale.ENGLISH
        }
    }

    /**
     * 包装 base context，使该 context 的资源使用当前语言设置。
     * Activity 应在 `attachBaseContext` 中调用；通知等 Application 级场景可临时包装后取文案。
     */
    fun wrap(base: Context): Context {
        val locale = resolve(read(base))
        val config = Configuration(base.resources.configuration)
        config.setLocale(locale)
        config.setLayoutDirection(locale)
        return base.createConfigurationContext(config)
    }

    /** 便捷取值：按当前语言设置从资源中取字符串（用于通知等非 Activity 场景） */
    fun string(base: Context, resId: Int, vararg args: Any): String =
        if (args.isEmpty()) wrap(base).getString(resId) else wrap(base).getString(resId, *args)

    /** 同步系统级 per-app locale（API 33+）：桌面图标名与系统"应用语言"入口一致 */
    fun syncSystemLocale(base: Context, lang: AppLanguage) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return
        runCatching {
            val lm = base.getSystemService(LocaleManager::class.java) ?: return
            val target = resolve(lang)
            val current = lm.applicationLocales
            if (current.isEmpty || current.get(0).toLanguageTag() != target.toLanguageTag()) {
                lm.applicationLocales = LocaleList(target)
                AppLog.i("Locale", "系统级应用语言已同步为 ${target.toLanguageTag()}")
            }
        }.onFailure { AppLog.i("Locale", "系统级应用语言同步失败：$it") }
    }

    private fun prefs(base: Context) =
        base.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
}
