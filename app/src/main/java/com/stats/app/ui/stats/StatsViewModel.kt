package com.stats.app.ui.stats

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.stats.app.R
import com.stats.app.data.db.dao.CategorySumRow
import com.stats.app.data.db.dao.DayCategorySumRow
import com.stats.app.data.db.dao.DaySumRow
import com.stats.app.data.db.dao.TaskPointsRow
import com.stats.app.data.db.dao.TaskRankRow
import com.stats.app.data.db.entity.PointsCategoryEntity
import com.stats.app.data.model.StatRangeType
import com.stats.app.data.repository.CategoryRepository
import com.stats.app.data.repository.SettingsRepository
import com.stats.app.data.repository.StatsRepository
import com.stats.app.data.util.DateRanges
import com.stats.app.util.AppLocale
import com.stats.app.util.AppLog
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.stateIn
import java.time.LocalDate
import javax.inject.Inject

/** 趋势图单柱 */
data class TrendBar(
    val label: String,
    val value: Long,
    val isCurrent: Boolean,
    /** 月视图专用：非空时轴标签按当前语言格式化为"10月" / "Oct" */
    val monthIndex: Int? = null,
)

/** 分类占比项 */
data class CategoryShare(
    val name: String,
    val colorValue: Long,
    val points: Long,
    val percent: Float,
)

/** 任务排行项（带分类信息：允许同名任务共存，靠分类区分显示） */
data class TaskRankItem(
    val taskId: Long,
    val name: String,
    val count: Int,
    val points: Long,
    val categoryName: String,
    val categoryColor: Long = 0xFF9E9E9E,
)

/** 热力图单元格：colorValue = 当日积分最高分类的颜色（null = 无正积分） */
data class HeatCell(val date: LocalDate, val colorValue: Long?, val future: Boolean)

/** 热力图图例项（窗口内出现过的分类，按积分降序） */
data class HeatLegendItem(val name: String, val colorValue: Long)

data class StatsUiState(
    val rangeType: StatRangeType = StatRangeType.DAY,
    /** 浏览锚点：统计展示"包含该日期的周期" */
    val anchorDate: LocalDate = LocalDate.now(),
    /** 当前逻辑日（用于"今天/昨天"标签与未来判断） */
    val logicalToday: LocalDate = LocalDate.now(),
    /** 所选周期的最后一天（含），用于"截至"类副标题 */
    val periodEndDate: LocalDate = LocalDate.now(),
    /** 所选周期是否就是当前周期 */
    val isCurrentPeriod: Boolean = true,
    val pointsTotal: Long = 0L,
    val pointsPrev: Long = 0L,
    val checkInCount: Int = 0,
    val streakDays: Int = 0,
    val trend: List<TrendBar> = emptyList(),
    val shares: List<CategoryShare> = emptyList(),
    val rank: List<TaskRankItem> = emptyList(),
    val heat: List<HeatCell> = emptyList(),
    val heatLegend: List<HeatLegendItem> = emptyList(),
)

/**
 * 统计三页：当前页 + 相邻两页。
 * 左右滑动采用"摄像机平移"动画（两页并排、视口平移），因此相邻页数据需始终提前就绪。
 */
data class StatsPages(
    val current: StatsUiState = StatsUiState(),
    /** 上一期（更早）：可以无限回看，始终存在 */
    val prev: StatsUiState? = null,
    /** 下一期（更近）：当前周期已包含"今天"时为 null（不能再往后翻） */
    val next: StatsUiState? = null,
)

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class StatsViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val statsRepository: StatsRepository,
    private val categoryRepository: CategoryRepository,
    private val settingsRepository: SettingsRepository,
) : ViewModel() {

    private val rangeType = MutableStateFlow(StatRangeType.DAY)

    /** 浏览锚点日期：默认当前逻辑日；可通过翻页 / 日期选择器移动 */
    private val anchorDate = MutableStateFlow(
        DateRanges.logicalToday(settingsRepository.dayStartHour)
    )

    /** 刷新触发器（页面重新进入时递增，跨逻辑日时口径随之更新） */
    private val tick = MutableStateFlow(0L)

    /** 上一次计算时的逻辑日（用于"停留旧今天"时自动跟随） */
    private var lastLogicalToday: LocalDate =
        DateRanges.logicalToday(settingsRepository.dayStartHour)

    /** 统计三页（当前 + 相邻）：同一次计算、同一帧 emit，保证并排动画期间页面一致 */
    val pagesState: StateFlow<StatsPages> = combine(
        rangeType,
        anchorDate,
        settingsRepository.dayStartHourFlow,
        tick,
    ) { type, anchor, dayStartHour, _ ->
        val today = DateRanges.logicalToday(dayStartHour)
        val effective = if (anchor.isAfter(today)) today else anchor
        val isCurrent = DateRanges.current(type, effective).contains(today)
        val nextRaw = DateRanges.shift(type, effective, 1)
        Triple(
            Browse(type, effective, today, dayStartHour),
            Browse(type, DateRanges.shift(type, effective, -1), today, dayStartHour),
            if (isCurrent) {
                null
            } else {
                // 下一期锚点不得超过今天（末页落到"今天所在周期"）
                Browse(type, if (nextRaw.isAfter(today)) today else nextRaw, today, dayStartHour)
            },
        )
    }
        .flatMapLatest { (cur, prev, next) ->
            combine(
                pageFlow(cur),
                pageFlow(prev),
                if (next == null) flowOf(null) else pageFlow(next),
            ) { c, p, n ->
                StatsPages(current = c, prev = p, next = n)
            }
        }
        .onEach { pages ->
            val st = pages.current
            AppLog.i(
                "Stats",
                "统计刷新 type=${st.rangeType} 锚点=${st.anchorDate} 当期=${st.isCurrentPeriod} " +
                        "本期=${st.pointsTotal} 上期=${st.pointsPrev} " +
                        "打卡=${st.checkInCount} 连续=${st.streakDays} 分类数=${st.shares.size} " +
                        "| 相邻页 prev=${pages.prev?.anchorDate} next=${pages.next?.anchorDate}"
            )
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = StatsPages(),
        )

    /** 单个周期的完整统计流（当前页与相邻页共用同一套计算） */
    private fun pageFlow(b: Browse): Flow<StatsUiState> {
        val cur = DateRanges.current(b.type, b.anchor)
        val prev = DateRanges.previous(b.type, b.anchor)
        val buckets = DateRanges.trendBuckets(b.type, b.anchor, 7)
        val (curFrom, curTo) = DateRanges.toMillis(cur, b.dayStartHour)
        val (prevFrom, prevTo) = DateRanges.toMillis(prev, b.dayStartHour)
        val trendFrom = DateRanges.startMillis(buckets.first().start, b.dayStartHour)
        // 热力图锚定所选周期末尾（不超过今天）：回看历史时窗口随之平移
        val heatBase = minOf(cur.endExclusive.minusDays(1), b.today)
        val heatStart = heatmapStart(heatBase)
        val heatFrom = DateRanges.startMillis(heatStart, b.dayStartHour)
        val heatTo = DateRanges.startMillis(heatStart.plusDays(105), b.dayStartHour)

        val core = combine(
            statsRepository.observePointsSum(curFrom, curTo),
            statsRepository.observePointsSum(prevFrom, prevTo),
            statsRepository.observeCheckInCount(curFrom, curTo),
            statsRepository.observePointsByCategory(curFrom, curTo),
            statsRepository.observePointsByDay(trendFrom, curTo, b.dayStartHour),
        ) { curSum, prevSum, checkIns, catRows, dayRows ->
            Core(curSum, prevSum, checkIns, catRows, dayRows)
        }

        val extra = combine(
            statsRepository.observeTaskRank(curFrom, curTo, 5),
            statsRepository.observeTaskPoints(curFrom, curTo),
            statsRepository.observePointsByDayAndCategory(heatFrom, heatTo, b.dayStartHour),
            statsRepository.observeActiveDays(b.dayStartHour),
        ) { rank, taskPoints, heatRows, activeDays ->
            Extra(rank, taskPoints, heatRows, activeDays)
        }

        return combine(core, extra, categoryRepository.observeAll()) { c, e, categories ->
            buildState(b, cur, buckets, heatBase, c, e, categories)
        }
    }

    fun setRange(type: StatRangeType) {
        if (rangeType.value != type) {
            AppLog.i("Stats", "切换统计范围 -> $type")
            rangeType.value = type
        }
    }

    /** 按周期前后翻页（delta：-1 上一期 / +1 下一期），不越过今天 */
    fun movePeriod(delta: Int) {
        val today = DateRanges.logicalToday(settingsRepository.dayStartHour)
        val next = DateRanges.shift(rangeType.value, anchorDate.value, delta)
        anchorDate.value = if (next.isAfter(today)) today else next
        AppLog.i("Stats", "周期翻页 ${if (delta > 0) "+" else ""}$delta -> ${anchorDate.value}")
    }

    /** 跳转到包含该日期的周期（未来日期自动落到今天） */
    fun selectDate(date: LocalDate) {
        val today = DateRanges.logicalToday(settingsRepository.dayStartHour)
        anchorDate.value = if (date.isAfter(today)) today else date
        AppLog.i("Stats", "选择日期 -> ${anchorDate.value}")
    }

    /** 回到当前周期 */
    fun backToToday() {
        anchorDate.value = DateRanges.logicalToday(settingsRepository.dayStartHour)
        AppLog.i("Stats", "回到今天")
    }

    /** 页面重新进入时调用：触发全部聚合重新计算 */
    fun refresh() {
        val today = DateRanges.logicalToday(settingsRepository.dayStartHour)
        if (anchorDate.value == lastLogicalToday && today != lastLogicalToday) {
            // 停留在旧"今天"时，跨逻辑日后自动跟随到新"今天"
            anchorDate.value = today
        }
        lastLogicalToday = today
        tick.value += 1
    }

    // ---- 内部 ----

    /** 一次浏览请求：所选周期类型 + 锚点 + 当前逻辑日 + 日界线 */
    private data class Browse(
        val type: StatRangeType,
        val anchor: LocalDate,
        val today: LocalDate,
        val dayStartHour: Int,
    )

    private data class Core(
        val curSum: Long,
        val prevSum: Long,
        val checkIns: Int,
        val catRows: List<CategorySumRow>,
        val dayRows: List<DaySumRow>,
    )

    private data class Extra(
        val rank: List<TaskRankRow>,
        val taskPoints: List<TaskPointsRow>,
        val heatRows: List<DayCategorySumRow>,
        val activeDays: List<String>,
    )

    /** 热力图起始日：锚定日所在周周一往前推 14 周（共 15 列，与周口径一致） */
    private fun heatmapStart(base: LocalDate): LocalDate {
        val monday = base.minusDays((base.dayOfWeek.value - 1).toLong())
        return monday.minusWeeks(14)
    }

    private fun buildState(
        b: Browse,
        cur: DateRanges.DayRange,
        buckets: List<DateRanges.DayRange>,
        heatBase: LocalDate,
        c: Core,
        e: Extra,
        categories: List<PointsCategoryEntity>,
    ): StatsUiState {
        // 趋势分桶：按天（逻辑日）聚合数据落入各周期桶
        val dayMap = c.dayRows.associate { it.day to it.total }
        val trend = buckets.mapIndexed { i, bucket ->
            val sum = bucket.days().sumOf { dayMap[it.toString()] ?: 0L }
            val isMonth = b.type == StatRangeType.MONTH
            TrendBar(
                label = if (isMonth) {
                    bucket.start.monthValue.toString()
                } else {
                    DateRanges.bucketLabel(b.type, bucket)
                },
                value = sum,
                isCurrent = i == buckets.lastIndex,
                monthIndex = if (isMonth) bucket.start.monthValue else null,
            )
        }

        // 分类占比（只看正贡献；负值来自撤销，属于账务调整）
        val catMap = categories.associateBy { it.id }
        val posRows = c.catRows.filter { it.total > 0 }
        val posTotal = posRows.sumOf { it.total }.coerceAtLeast(1L)
        val shares = posRows.map { row ->
            val cat = catMap[row.categoryId]
            CategoryShare(
                name = cat?.name ?: AppLocale.string(context, R.string.stats_uncategorized),
                colorValue = cat?.color ?: 0xFF9E9E9E,
                points = row.total,
                percent = row.total.toFloat() / posTotal,
            )
        }.sortedByDescending { it.points }

        // 任务排行：次数（有效打卡）+ 积分（流水净额）+ 分类（同名任务靠分类区分）
        val ptsMap = e.taskPoints.associate { it.taskId to it.total }
        val rank = e.rank.map { r ->
            val cat = catMap[r.categoryId]
            TaskRankItem(
                taskId = r.taskId,
                name = r.taskName,
                count = r.count,
                points = ptsMap[r.taskId] ?: 0L,
                categoryName = cat?.name ?: AppLocale.string(context, R.string.stats_uncategorized),
                categoryColor = cat?.color ?: 0xFF9E9E9E,
            )
        }

        // 连续打卡：查看当前周期时从今天数（今天没打卡不算断）；
        // 查看历史周期时从该周期最后一天数（作为"截至该时期"的口径）
        val activeSet = e.activeDays.toHashSet()
        val inCurrent = cur.contains(b.today)
        val base = if (inCurrent) b.today else cur.endExclusive.minusDays(1)
        var streak = 0
        var cursor = if (activeSet.contains(base.toString())) base else base.minusDays(1)
        while (activeSet.contains(cursor.toString())) {
            streak++
            cursor = cursor.minusDays(1)
        }

        // 热力图：15 周 × 7 天；每天取当日积分最高的分类着色（过滤非正净额）
        val positiveRows = e.heatRows.filter { it.total > 0 }
        val heatColorMap = positiveRows.groupBy { it.day }.mapValues { (_, rows) ->
            val top = rows.maxByOrNull { it.total }!!
            catMap[top.categoryId]?.color ?: 0xFF9E9E9E
        }
        val start = heatmapStart(heatBase)
        val heat = (0 until 105).map { i ->
            val date = start.plusDays(i.toLong())
            HeatCell(
                date = date,
                colorValue = heatColorMap[date.toString()],
                future = date.isAfter(b.today),
            )
        }

        // 热力图图例：窗口内出现过的分类（按积分降序）
        val heatLegend = positiveRows
            .groupBy { it.categoryId }
            .map { (catId, rows) -> catId to rows.sumOf { it.total } }
            .sortedByDescending { it.second }
            .map { (catId, _) ->
                catMap[catId]?.let { HeatLegendItem(it.name, it.color) }
                    ?: HeatLegendItem(
                        AppLocale.string(context, R.string.stats_deleted_category),
                        0xFF9E9E9E,
                    )
            }

        return StatsUiState(
            rangeType = b.type,
            anchorDate = b.anchor,
            logicalToday = b.today,
            periodEndDate = cur.endExclusive.minusDays(1),
            isCurrentPeriod = inCurrent,
            pointsTotal = c.curSum,
            pointsPrev = c.prevSum,
            checkInCount = c.checkIns,
            streakDays = streak,
            trend = trend,
            shares = shares,
            rank = rank,
            heat = heat,
            heatLegend = heatLegend,
        )
    }
}
