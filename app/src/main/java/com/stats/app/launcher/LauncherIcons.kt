package com.stats.app.launcher

import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager
import com.stats.app.ui.common.CategoryPalette
import com.stats.app.util.AppLog
import kotlin.math.abs

/**
 * 桌面启动图标动态切换。
 *
 * 每种（分类调色板）颜色预置一款自适应图标，由 Manifest 中对应的 activity-alias 承载；
 * 运行时**只启用其中一个** alias，其余禁用以避免桌面出现多个图标。
 * 图标颜色来源："昨日积分最高的分类"（见 [LauncherIconUpdater]）。
 */
object LauncherIcons {

    private const val PREFIX = "com.stats.app.launcher."
    private const val DEFAULT = "Default"

    /** 与 [CategoryPalette] 顺序一一对应的图标名（也是 alias 后缀） */
    private val COLOR_NAMES = listOf(
        "Blue", "Indigo", "Purple", "Pink", "Red", "Orange",
        "Lime", "Green", "Teal", "Cyan", "Slate", "Brown",
    )

    private val ALL: List<String> = listOf(DEFAULT) + COLOR_NAMES

    /** 颜色值 -> 图标名 */
    private val BY_COLOR: Map<Long, String> =
        CategoryPalette.mapIndexed { index, color -> color to COLOR_NAMES[index] }.toMap()

    /**
     * 把桌面图标切换为 [color] 对应的款式。
     * 颜色为空时退回默认款式；不在调色板中时取 RGB 距离最近的一款。
     */
    fun apply(context: Context, color: Long?) {
        val name = resolve(color)
        val pm = context.packageManager
        val pkg = context.packageName

        val target = ComponentName(pkg, PREFIX + name)
        val others = ALL.filter { it != name }.map { ComponentName(pkg, PREFIX + it) }

        val enabled = PackageManager.COMPONENT_ENABLED_STATE_ENABLED
        val disabled = PackageManager.COMPONENT_ENABLED_STATE_DISABLED
        val flags = PackageManager.DONT_KILL_APP

        if (pm.getComponentEnabledSetting(target) == enabled &&
            others.none { pm.getComponentEnabledSetting(it) == enabled }
        ) {
            return // 已是目标状态，无需触碰系统
        }

        // 先启用目标、再禁用其它：避免中途出现"没有图标"的空档
        runCatching { pm.setComponentEnabledSetting(target, enabled, flags) }
            .onFailure { AppLog.i("Launcher", "启用图标 $name 失败：$it") }
        others.forEach {
            runCatching { pm.setComponentEnabledSetting(it, disabled, flags) }
        }
        AppLog.i("Launcher", "桌面图标已切换为 $name")
    }

    /** 颜色 -> 图标名（精确匹配优先，否则取 RGB 距离最近的一款） */
    private fun resolve(color: Long?): String {
        if (color == null) return DEFAULT
        BY_COLOR[color]?.let { return it }
        return BY_COLOR.entries.minByOrNull { rgbDistance(it.key, color) }?.value ?: DEFAULT
    }

    private fun rgbDistance(a: Long, b: Long): Long {
        val dr = abs(((a shr 16) and 0xFF).toInt() - ((b shr 16) and 0xFF).toInt())
        val dg = abs(((a shr 8) and 0xFF).toInt() - ((b shr 8) and 0xFF).toInt())
        val db = abs((a and 0xFF).toInt() - (b and 0xFF).toInt())
        return (dr * dr + dg * dg + db * db).toLong()
    }
}
