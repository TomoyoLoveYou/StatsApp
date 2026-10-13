package com.stats.app.launcher

import android.content.Context
import com.stats.app.data.model.StatRangeType
import com.stats.app.data.repository.CategoryRepository
import com.stats.app.data.repository.SettingsRepository
import com.stats.app.data.repository.StatsRepository
import com.stats.app.data.util.DateRanges
import com.stats.app.util.AppLog
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import java.time.LocalDate
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 每日刷新桌面图标：取"昨日积分最高的分类"的颜色。
 *
 *  - 口径与统计页"积分构成"一致（只计正贡献）；
 *  - 同一逻辑日内只计算一次（昨日数据在同一天内不会变）；
 *  - 昨日无任何正积分时退回默认图标。
 */
@Singleton
class LauncherIconUpdater @Inject constructor(
    @ApplicationContext private val context: Context,
    private val statsRepository: StatsRepository,
    private val categoryRepository: CategoryRepository,
    private val settingsRepository: SettingsRepository,
) {

    private var lastAppliedDay: LocalDate? = null

    suspend fun refresh() {
        withContext(Dispatchers.IO) {
            runCatching {
                val dayStartHour = settingsRepository.dayStartHour
                val today = DateRanges.logicalToday(dayStartHour)
                if (today == lastAppliedDay) return@withContext

                val yesterday = today.minusDays(1)
                val range = DateRanges.current(StatRangeType.DAY, yesterday)
                val (from, to) = DateRanges.toMillis(range, dayStartHour)

                val top = statsRepository.observePointsByCategory(from, to).first()
                    .filter { it.total > 0 }
                    .maxByOrNull { it.total }

                val color = top?.let { row ->
                    categoryRepository.observeAll().first()
                        .firstOrNull { it.id == row.categoryId }
                        ?.color
                }

                LauncherIcons.apply(context, color)
                lastAppliedDay = today
                AppLog.i(
                    "Launcher",
                    "图标刷新 逻辑日=$today 昨日榜首分类=${top?.categoryId ?: "无"} 颜色=${color ?: "默认"}"
                )
            }.onFailure { AppLog.i("Launcher", "图标刷新失败：$it") }
        }
    }
}
