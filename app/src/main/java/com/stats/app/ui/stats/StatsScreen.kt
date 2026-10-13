@file:OptIn(ExperimentalMaterial3Api::class)

package com.stats.app.ui.stats

import android.content.Context
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SelectableDates
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.stats.app.R
import com.stats.app.data.model.StatRangeType
import com.stats.app.ui.common.dateShortLabel
import com.stats.app.ui.common.label
import com.stats.app.ui.common.monthShortLabel
import com.stats.app.ui.common.periodNavigatorLabel
import com.stats.app.ui.common.scoreTitle
import com.stats.app.ui.common.toUtcMillis
import com.stats.app.ui.common.trendSubtitle
import com.stats.app.ui.common.utcMillisToLocalDate
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.temporal.ChronoUnit
import java.time.temporal.WeekFields

/** 页面身份（范围 + 锚点）：用于判定"切换动画后新数据是否已到位" */
private data class PageKey(val range: StatRangeType, val anchor: LocalDate)

/** 该日期所在周的周一 */
private fun mondayOf(date: LocalDate): LocalDate =
    date.minusDays((date.dayOfWeek.value - 1).toLong())

/** 周期起点（用于计算周期间隔） */
private fun periodStart(type: StatRangeType, date: LocalDate): LocalDate = when (type) {
    StatRangeType.DAY -> date
    StatRangeType.WEEK -> mondayOf(date)
    StatRangeType.MONTH -> date.withDayOfMonth(1)
    StatRangeType.YEAR -> date.withDayOfYear(1)
}

/** 浏览周期距"今天所在周期"相隔多少期（用于选择"回到今天"的过渡动画） */
private fun periodGap(type: StatRangeType, anchor: LocalDate, today: LocalDate): Int {
    val a = periodStart(type, anchor)
    val t = periodStart(type, today)
    return when (type) {
        StatRangeType.DAY -> ChronoUnit.DAYS.between(a, t).toInt()
        StatRangeType.WEEK -> ChronoUnit.WEEKS.between(a, t).toInt()
        StatRangeType.MONTH -> ChronoUnit.MONTHS.between(a, t).toInt()
        StatRangeType.YEAR -> ChronoUnit.YEARS.between(a, t).toInt()
    }
}

@Composable
fun StatsScreen(
    backToTodaySignal: Int = 0,
    viewModel: StatsViewModel = hiltViewModel(),
) {
    val pages by viewModel.pagesState.collectAsStateWithLifecycle()
    val state = pages.current
    // 当前打开的选择器（与统计范围一一对应：日=日历 / 周 / 月 / 年）
    var picker by remember { mutableStateOf<StatRangeType?>(null) }
    LaunchedEffect(Unit) { viewModel.refresh() }

    val scope = rememberCoroutineScope()
    val haptic = LocalHapticFeedback.current

    // ---- 状态 ----
    // 每页独立的滚动状态；拖动开始时把当前页的滚动进度同步给相邻页（两页"高度"一致）
    val listState = rememberLazyListState()
    val prevListState = rememberLazyListState()
    val nextListState = rememberLazyListState()
    // 快速回归动画中"被替换掉的旧页"专用（独立状态，避免与其替换者互相修正滚动位置）
    val leavingListState = rememberLazyListState()

    val canGoForward by rememberUpdatedState(pages.next != null)
    val currentPageKey by rememberUpdatedState(PageKey(state.rangeType, state.anchorDate))
    var dragOffset by remember { mutableFloatStateOf(0f) }
    var boxWidth by remember { mutableFloatStateOf(0f) }
    var neighbors by remember { mutableStateOf(false) }
    var switchFrom by remember { mutableStateOf<PageKey?>(null) }
    var settleJob by remember { mutableStateOf<Job?>(null) }
    /** "回到今天"快速回归动画中：被替换掉的旧页（继续滑出） */
    var leavingPage by remember { mutableStateOf<StatsUiState?>(null) }
    var returning by remember { mutableStateOf(false) }

    // 切换动画结束后、新数据到位（锚点 / 范围已变）即判定"过渡完成"：
    // 此刻把视口归位到当前页 —— 页面内容与动画终点完全一致，视觉上无缝衔接。
    val settled = switchFrom?.let {
        it.range != state.rangeType || it.anchor != state.anchorDate
    } == true
    LaunchedEffect(settled) {
        if (settled) {
            dragOffset = 0f
            switchFrom = null
            neighbors = false
        }
    }

    // ---- 动画工具 ----

    /** 松手 / 取消后把内容柔和地弹回原位（未达切换阈值） */
    fun bounceBack(from: Float) {
        settleJob?.cancel()
        settleJob = scope.launch {
            animate(from, 0f, animationSpec = tween(200)) { v, _ -> dragOffset = v }
            neighbors = false
        }
    }

    /** 越过阈值：整页沿拖动方向滑出屏幕、相邻页滑入居中，随后切换数据 */
    fun slideToNeighbor(dir: Int, from: Float, resetScrollToTop: Boolean = false) {
        settleJob?.cancel()
        settleJob = scope.launch {
            val target = if (dir > 0) -boxWidth else boxWidth
            animate(
                from,
                target,
                animationSpec = tween(280, easing = FastOutSlowInEasing),
            ) { v, _ -> dragOffset = v }
            if (resetScrollToTop) {
                // 动画终点时旧页已滑出屏幕：把当前页（即将被新数据替换）的滚动位置归零，
                // 使其与滑入的相邻页保持同一位置（顶部），避免衔接时跳变
                listState.requestScrollToItem(0, 0)
            }
            // 迁移期间画面停在相邻页上（内容即新数据），到位后由 settled 归位
            viewModel.movePeriod(dir)
        }
    }

    /**
     * 回到今天（带过渡动画，滚动位置直接归零 —— 不播放"滚到顶部"的过程，避免闪屏）：
     *  - 相隔一期：与手动左右切页相同的滑页动画，今天页以顶部进入；
     *  - 相隔较远：旧页从当前位置滑出、今天页从顶部快速冲入后减速落定。
     */
    fun playBackToToday() {
        if (returning || switchFrom != null || state.isCurrentPeriod) return
        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
        val gap = periodGap(state.rangeType, state.anchorDate, state.logicalToday)
        if (gap <= 1) {
            // 相邻一期：今天页即"下一期"页，直接以顶部位置准备就绪
            nextListState.requestScrollToItem(0, 0)
            neighbors = true
            switchFrom = currentPageKey
            slideToNeighbor(1, dragOffset, resetScrollToTop = true)
        } else {
            // 相隔较远：旧页保持当前位置滑出
            leavingListState.requestScrollToItem(
                listState.firstVisibleItemIndex,
                listState.firstVisibleItemScrollOffset,
            )
            leavingPage = state
            returning = true
            viewModel.backToToday()
            // 今天页滚动位置直接归零：滑入时即从顶部开始
            listState.requestScrollToItem(0, 0)
            settleJob?.cancel()
            settleJob = scope.launch {
                // 起步即最高速、逐渐减速停住 —— "快速切页然后落定"的手感
                animate(
                    0f,
                    -boxWidth,
                    animationSpec = tween(420, easing = LinearOutSlowInEasing),
                ) { v, _ -> dragOffset = v }
                dragOffset = 0f
                returning = false
                leavingPage = null
            }
        }
    }

    /** 选择器确认：先回顶再切数据（避免"新数据先出现在底部"的一帧），只作用于日界线内的日期 */
    fun applyPickedDate(date: LocalDate) {
        picker = null
        scope.launch {
            listState.scrollToItem(0)
            viewModel.selectDate(date)
        }
    }

    // 已在统计页时再次点击底部导航栏的"统计"键 -> 回到今天（页面内不再放按钮）
    LaunchedEffect(backToTodaySignal) {
        if (backToTodaySignal > 0 && !state.isCurrentPeriod) {
            playBackToToday()
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        // ---- 固定头部：标题 + 周期选择（不随内容滑动） ----
        Column(
            modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 12.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Text(
                text = stringResource(R.string.main_tab_stats),
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.SemiBold,
            )
            RangeSelector(selected = state.rangeType, onSelect = viewModel::setRange)
        }
        Spacer(Modifier.height(14.dp))

        // ---- 可滑动内容区（"摄像机平移"：相邻两页并排存在，视口在两页之间移动） ----
        // 内容跟随手指；滑动超过屏宽约 12% 即切换：整页沿拖动方向滑出、相邻页滑入居中。
        // 向左滑 = 看更近的周期（下一期），向右滑 = 看更早的周期（上一期）；
        // 已在本周期时向左滑到头 -> 阻尼（"到头了"的手感）且不切换。
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .clipToBounds()
                .onSizeChanged { boxWidth = it.width.toFloat() }
                .pointerInput(Unit) {
                    val maxDrag = size.width * 0.4f
                    val threshold = size.width * 0.12f

                    detectHorizontalDragGestures(
                        onDragStart = {
                            settleJob?.cancel()
                            if (returning) {
                                // 回归动画被手势打断：直接落定
                                dragOffset = 0f
                                returning = false
                                leavingPage = null
                            }
                            neighbors = true
                            // 相邻页的滚动进度与当前页对齐（拖动时两页垂直方向一致）
                            val idx = listState.firstVisibleItemIndex
                            val px = listState.firstVisibleItemScrollOffset
                            scope.launch {
                                prevListState.scrollToItem(idx, px)
                                if (pages.next != null) nextListState.scrollToItem(idx, px)
                            }
                        },
                        onDragCancel = { bounceBack(dragOffset) },
                        onDragEnd = {
                            val offset = dragOffset
                            when {
                                offset <= -threshold && canGoForward -> {
                                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                    switchFrom = currentPageKey
                                    slideToNeighbor(1, offset)
                                }

                                offset >= threshold -> {
                                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                    switchFrom = currentPageKey
                                    slideToNeighbor(-1, offset)
                                }

                                else -> bounceBack(offset)
                            }
                        },
                    ) { change, dragAmount ->
                        change.consume()
                        var amount = dragAmount
                        if (!canGoForward && amount < 0f) amount *= 0.25f // 到头阻尼
                        dragOffset = (dragOffset + amount).coerceIn(-maxDrag, maxDrag)
                    }
                }
        ) {
            val prevState = pages.prev
            val nextState = pages.next
            val leaving = leavingPage

            if (returning && leaving != null) {
                // 快速回归：旧页向左滑出、今天页从右侧冲入
                StatsPageContent(
                    state = leaving,
                    listState = leavingListState,
                    onMove = viewModel::movePeriod,
                    onPickDate = { picker = leaving.rangeType },
                    modifier = Modifier
                        .fillMaxSize()
                        .graphicsLayer {
                            translationX = if (settled) 0f else dragOffset
                        },
                )
                StatsPageContent(
                    state = state,
                    listState = listState,
                    onMove = viewModel::movePeriod,
                    onPickDate = { picker = state.rangeType },
                    modifier = Modifier
                        .fillMaxSize()
                        .graphicsLayer {
                            translationX = boxWidth + (if (settled) 0f else dragOffset)
                        },
                )
            } else {
                // 上一期页：位于屏幕左侧（拖右时随视口进入）
                if (neighbors && prevState != null) {
                    StatsPageContent(
                        state = prevState,
                        listState = prevListState,
                        onMove = viewModel::movePeriod,
                        onPickDate = { picker = prevState.rangeType },
                        modifier = Modifier
                            .fillMaxSize()
                            .graphicsLayer {
                                translationX = -boxWidth + (if (settled) 0f else dragOffset)
                            },
                    )
                }
                // 下一期页：位于屏幕右侧（拖左时随视口进入）
                if (neighbors && nextState != null) {
                    StatsPageContent(
                        state = nextState,
                        listState = nextListState,
                        onMove = viewModel::movePeriod,
                        onPickDate = { picker = nextState.rangeType },
                        modifier = Modifier
                            .fillMaxSize()
                            .graphicsLayer {
                                translationX = boxWidth + (if (settled) 0f else dragOffset)
                            },
                    )
                }
                // 当前页
                StatsPageContent(
                    state = state,
                    listState = listState,
                    onMove = viewModel::movePeriod,
                    onPickDate = { picker = state.rangeType },
                    modifier = Modifier
                        .fillMaxSize()
                        .graphicsLayer {
                            translationX = if (settled) 0f else dragOffset
                        },
                )
            }
        }
    }

    // ---- 周期选择器（日=日历 / 周列表 / 月网格 / 年网格） ----
    when (picker) {
        StatRangeType.DAY -> StatsDatePickerDialog(
            initialDate = state.anchorDate,
            maxDate = state.logicalToday,
            onConfirm = ::applyPickedDate,
            onDismiss = { picker = null },
        )

        StatRangeType.WEEK -> WeekPickerDialog(
            anchor = state.anchorDate,
            today = state.logicalToday,
            onConfirm = ::applyPickedDate,
            onDismiss = { picker = null },
        )

        StatRangeType.MONTH -> MonthPickerDialog(
            anchor = state.anchorDate,
            today = state.logicalToday,
            onConfirm = ::applyPickedDate,
            onDismiss = { picker = null },
        )

        StatRangeType.YEAR -> YearPickerDialog(
            anchor = state.anchorDate,
            today = state.logicalToday,
            onConfirm = ::applyPickedDate,
            onDismiss = { picker = null },
        )

        null -> Unit
    }
}

/** 单页统计内容（当前页与相邻页共用；滚动状态由调用方注入以便跨页同步） */
@Composable
private fun StatsPageContent(
    state: StatsUiState,
    listState: LazyListState,
    onMove: (Int) -> Unit,
    onPickDate: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    LazyColumn(
        modifier = modifier,
        state = listState,
        contentPadding = PaddingValues(start = 20.dp, end = 20.dp, bottom = 28.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        item(key = "navigator") {
            PeriodNavigator(
                type = state.rangeType,
                anchor = state.anchorDate,
                today = state.logicalToday,
                isCurrent = state.isCurrentPeriod,
                onMove = onMove,
                onPickDate = onPickDate,
            )
        }
        item(key = "overview") { OverviewCard(state) }
        item(key = "trend") {
            StatsCard(
                title = stringResource(R.string.stats_trend_title),
                subtitle = state.rangeType.trendSubtitle(
                    context,
                    state.isCurrentPeriod,
                    state.periodEndDate,
                ),
            ) {
                TrendBarChart(state.trend)
            }
        }
        item(key = "share") {
            StatsCard(
                title = stringResource(R.string.stats_share_title),
                subtitle = stringResource(R.string.stats_share_subtitle),
            ) {
                ShareSection(state)
            }
        }
        item(key = "rank") {
            StatsCard(
                title = stringResource(R.string.stats_rank_title),
                subtitle = stringResource(R.string.stats_rank_subtitle),
            ) {
                RankSection(state.rank)
            }
        }
        item(key = "heatmap") {
            StatsCard(
                title = stringResource(R.string.stats_heat_title),
                subtitle = if (state.isCurrentPeriod) {
                    stringResource(R.string.stats_heat_subtitle_current)
                } else {
                    stringResource(
                        R.string.stats_heat_subtitle_past,
                        dateShortLabel(context, state.periodEndDate),
                    )
                },
            ) {
                Column {
                    HeatmapGrid(cells = state.heat)
                    Spacer(Modifier.height(10.dp))
                    HeatmapLegend(
                        items = state.heatLegend,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
        }
    }
}

@Composable
private fun RangeSelector(selected: StatRangeType, onSelect: (StatRangeType) -> Unit) {
    val context = LocalContext.current
    SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
        StatRangeType.entries.forEachIndexed { index, type ->
            SegmentedButton(
                selected = selected == type,
                onClick = { onSelect(type) },
                shape = SegmentedButtonDefaults.itemShape(
                    index = index,
                    count = StatRangeType.entries.size,
                ),
                icon = {}, // 不显示勾选图标（选中态靠底色区分）
            ) {
                Text(type.label(context))
            }
        }
    }
}

/** 周期导航条：◀ 上一期 / 日期按钮（点击打开对应选择器）/ ▶ 下一期（回到今天走底部导航栏） */
@Composable
private fun PeriodNavigator(
    type: StatRangeType,
    anchor: LocalDate,
    today: LocalDate,
    isCurrent: Boolean,
    onMove: (Int) -> Unit,
    onPickDate: () -> Unit,
) {
    val cs = MaterialTheme.colorScheme
    val context = LocalContext.current
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        FilledTonalIconButton(onClick = { onMove(-1) }) {
            Icon(
                Icons.AutoMirrored.Filled.KeyboardArrowLeft,
                contentDescription = stringResource(R.string.stats_cd_prev_period),
            )
        }
        Spacer(Modifier.width(10.dp))
        Surface(
            shape = RoundedCornerShape(16.dp),
            color = if (isCurrent) cs.surfaceVariant else cs.secondaryContainer,
            modifier = Modifier.weight(1f),
        ) {
            Row(
                modifier = Modifier
                    .clickable(onClick = onPickDate)
                    .padding(vertical = 11.dp, horizontal = 10.dp),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    Icons.Filled.DateRange,
                    contentDescription = stringResource(R.string.stats_cd_pick_date),
                    modifier = Modifier.size(17.dp),
                    tint = if (isCurrent) cs.onSurfaceVariant else cs.onSecondaryContainer,
                )
                Spacer(Modifier.width(7.dp))
                Text(
                    text = periodNavigatorLabel(context, type, anchor, today, isCurrent),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = if (isCurrent) cs.onSurfaceVariant else cs.onSecondaryContainer,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        Spacer(Modifier.width(10.dp))
        FilledTonalIconButton(onClick = { onMove(1) }, enabled = !isCurrent) {
            Icon(
                Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = stringResource(R.string.stats_cd_next_period),
            )
        }
    }
}

// ================= 周期选择器 =================

/** 日选择器：日历（禁止选择未来日期） */
@Composable
private fun StatsDatePickerDialog(
    initialDate: LocalDate,
    maxDate: LocalDate,
    onConfirm: (LocalDate) -> Unit,
    onDismiss: () -> Unit,
) {
    val maxMillis = maxDate.toUtcMillis()
    val pickerState = rememberDatePickerState(
        initialSelectedDateMillis = initialDate.toUtcMillis(),
        selectableDates = remember(maxMillis, maxDate.year) {
            object : SelectableDates {
                override fun isSelectableDate(utcTimeMillis: Long): Boolean =
                    utcTimeMillis <= maxMillis

                override fun isSelectableYear(year: Int): Boolean = year <= maxDate.year
            }
        },
    )
    DatePickerDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(
                onClick = {
                    pickerState.selectedDateMillis?.let { onConfirm(utcMillisToLocalDate(it)) }
                    onDismiss()
                },
                enabled = pickerState.selectedDateMillis != null,
            ) {
                Text(stringResource(R.string.common_confirm))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.common_cancel))
            }
        },
    ) {
        DatePicker(state = pickerState, showModeToggle = false)
    }
}

/** 周选择器：从本周往前列出每一周（ISO 周号 + 日期范围） */
@Composable
private fun WeekPickerDialog(
    anchor: LocalDate,
    today: LocalDate,
    onConfirm: (LocalDate) -> Unit,
    onDismiss: () -> Unit,
) {
    val todayMonday = mondayOf(today)
    val anchorMonday = mondayOf(anchor)
    val weeksBack = ChronoUnit.WEEKS.between(anchorMonday, todayMonday).toInt()
    val count = (weeksBack + 1).coerceAtLeast(14)
    val weeks = remember(todayMonday, count) {
        (0 until count).map { todayMonday.minusWeeks(it.toLong()) }
    }
    val selectedIndex = weeks.indexOf(anchorMonday).coerceAtLeast(0)
    val weekListState = rememberLazyListState(
        initialFirstVisibleItemIndex = (selectedIndex - 3).coerceAtLeast(0),
    )
    val wf = remember { WeekFields.ISO }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Surface(
            shape = MaterialTheme.shapes.extraLarge,
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            modifier = Modifier.fillMaxWidth(0.92f),
        ) {
            Column(Modifier.padding(top = 20.dp, bottom = 6.dp)) {
                Text(
                    text = stringResource(R.string.stats_picker_week),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.padding(horizontal = 24.dp),
                )
                Spacer(Modifier.height(8.dp))
                LazyColumn(state = weekListState, modifier = Modifier.height(380.dp)) {
                    itemsIndexed(weeks) { _, monday ->
                        val sunday = monday.plusDays(6)
                        val selected = monday == anchorMonday
                        val isThisWeek = monday == todayMonday
                        val cs = MaterialTheme.colorScheme
                        val fg = if (selected) cs.onSecondaryContainer else cs.onSurface
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(
                                    if (selected) cs.secondaryContainer else Color.Transparent
                                )
                                .clickable { onConfirm(monday) }
                                .padding(horizontal = 24.dp, vertical = 13.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                text = "W%02d".format(monday.get(wf.weekOfWeekBasedYear())),
                                style = MaterialTheme.typography.bodyLarge,
                                fontWeight = FontWeight.SemiBold,
                                color = fg,
                            )
                            Spacer(Modifier.width(14.dp))
                            Text(
                                text = "%d/%d – %d/%d".format(
                                    monday.monthValue, monday.dayOfMonth,
                                    sunday.monthValue, sunday.dayOfMonth,
                                ),
                                style = MaterialTheme.typography.bodyMedium,
                                color = if (selected) cs.onSecondaryContainer else cs.onSurfaceVariant,
                            )
                            Spacer(Modifier.weight(1f))
                            if (isThisWeek) {
                                Text(
                                    text = stringResource(R.string.stats_this_week_badge),
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.SemiBold,
                                    color = if (selected) cs.onSecondaryContainer else cs.primary,
                                )
                            }
                        }
                    }
                }
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp),
                    horizontalArrangement = Arrangement.End,
                ) {
                    TextButton(onClick = onDismiss) { Text(stringResource(R.string.common_cancel)) }
                }
            }
        }
    }
}

/** 月选择器：年份可切换 + 12 个月网格（未来月份不可选） */
@Composable
private fun MonthPickerDialog(
    anchor: LocalDate,
    today: LocalDate,
    onConfirm: (LocalDate) -> Unit,
    onDismiss: () -> Unit,
) {
    var year by remember { mutableIntStateOf(anchor.year) }
    val cs = MaterialTheme.colorScheme
    val context = LocalContext.current

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Surface(
            shape = MaterialTheme.shapes.extraLarge,
            color = cs.surfaceContainerHigh,
            modifier = Modifier.fillMaxWidth(0.92f),
        ) {
            Column(Modifier.padding(top = 20.dp, bottom = 6.dp)) {
                Text(
                    text = stringResource(R.string.stats_picker_month),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.padding(horizontal = 24.dp),
                )
                Spacer(Modifier.height(4.dp))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    IconButton(onClick = { year -= 1 }) {
                        Icon(
                            Icons.AutoMirrored.Filled.KeyboardArrowLeft,
                            contentDescription = stringResource(R.string.stats_cd_prev_year),
                        )
                    }
                    Text(
                        text = stringResource(R.string.stats_year_number, year),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.weight(1f),
                    )
                    IconButton(onClick = { year += 1 }, enabled = year < today.year) {
                        Icon(
                            Icons.AutoMirrored.Filled.KeyboardArrowRight,
                            contentDescription = stringResource(R.string.stats_cd_next_year),
                        )
                    }
                }
                Spacer(Modifier.height(2.dp))
                Column(Modifier.padding(horizontal = 16.dp)) {
                    for (row in 0 until 4) {
                        Row(Modifier.fillMaxWidth()) {
                            for (col in 0 until 3) {
                                val month = row * 3 + col + 1
                                val isFuture = year > today.year ||
                                        (year == today.year && month > today.monthValue)
                                val isSelected = year == anchor.year && month == anchor.monthValue
                                val isThisMonth = year == today.year && month == today.monthValue
                                val bg = when {
                                    isSelected -> cs.primary
                                    isThisMonth -> cs.secondaryContainer
                                    else -> Color.Transparent
                                }
                                val fg = when {
                                    isFuture -> cs.onSurface.copy(alpha = 0.38f)
                                    isSelected -> cs.onPrimary
                                    isThisMonth -> cs.onSecondaryContainer
                                    else -> cs.onSurface
                                }
                                Box(
                                    modifier = Modifier
                                        .weight(1f)
                                        .padding(4.dp)
                                        .clip(RoundedCornerShape(14.dp))
                                        .background(bg)
                                        .clickable(enabled = !isFuture) {
                                            onConfirm(LocalDate.of(year, month, 1))
                                        }
                                        .padding(vertical = 13.dp),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    Text(
                                        text = monthShortLabel(context, month),
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = if (isSelected || isThisMonth) {
                                            FontWeight.SemiBold
                                        } else {
                                            FontWeight.Normal
                                        },
                                        color = fg,
                                    )
                                }
                            }
                        }
                    }
                }
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp),
                    horizontalArrangement = Arrangement.End,
                ) {
                    TextButton(onClick = onDismiss) { Text(stringResource(R.string.common_cancel)) }
                }
            }
        }
    }
}

/** 年选择器：从今年往前列出若干年（不显示未来年份） */
@Composable
private fun YearPickerDialog(
    anchor: LocalDate,
    today: LocalDate,
    onConfirm: (LocalDate) -> Unit,
    onDismiss: () -> Unit,
) {
    // 至少覆盖最近 12 年；若当前浏览更早则跟随，但最多往前 20 年
    val minYear = minOf(today.year - 11, anchor.year).coerceAtLeast(today.year - 19)
    val years = remember(today.year, minYear) { (today.year downTo minYear).toList() }
    val cs = MaterialTheme.colorScheme

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Surface(
            shape = MaterialTheme.shapes.extraLarge,
            color = cs.surfaceContainerHigh,
            modifier = Modifier.fillMaxWidth(0.92f),
        ) {
            Column(Modifier.padding(top = 20.dp, bottom = 6.dp)) {
                Text(
                    text = stringResource(R.string.stats_picker_year),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.padding(horizontal = 24.dp),
                )
                Spacer(Modifier.height(14.dp))
                Column(Modifier.padding(horizontal = 16.dp)) {
                    years.chunked(3).forEach { rowYears ->
                        Row(Modifier.fillMaxWidth()) {
                            rowYears.forEach { y ->
                                val isSelected = y == anchor.year
                                val isThisYear = y == today.year
                                val bg = when {
                                    isSelected -> cs.primary
                                    isThisYear -> cs.secondaryContainer
                                    else -> Color.Transparent
                                }
                                val fg = when {
                                    isSelected -> cs.onPrimary
                                    isThisYear -> cs.onSecondaryContainer
                                    else -> cs.onSurface
                                }
                                Box(
                                    modifier = Modifier
                                        .weight(1f)
                                        .padding(4.dp)
                                        .clip(RoundedCornerShape(14.dp))
                                        .background(bg)
                                        .clickable { onConfirm(LocalDate.of(y, 1, 1)) }
                                        .padding(vertical = 13.dp),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    Text(
                                        text = "%d".format(y),
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = if (isSelected || isThisYear) {
                                            FontWeight.SemiBold
                                        } else {
                                            FontWeight.Normal
                                        },
                                        color = fg,
                                    )
                                }
                            }
                            // 末行补齐空位，保持列宽一致
                            repeat(3 - rowYears.size) {
                                Box(Modifier.weight(1f))
                            }
                        }
                    }
                }
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp),
                    horizontalArrangement = Arrangement.End,
                ) {
                    TextButton(onClick = onDismiss) { Text(stringResource(R.string.common_cancel)) }
                }
            }
        }
    }
}

// ================= 内容组件 =================

@Composable
private fun OverviewCard(state: StatsUiState) {
    val cs = MaterialTheme.colorScheme
    val context = LocalContext.current
    val gradient = Brush.linearGradient(listOf(cs.primary, cs.primary.copy(alpha = 0.75f)))
    val delta = state.pointsTotal - state.pointsPrev

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(24.dp))
            .background(gradient)
            .padding(20.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = state.rangeType.scoreTitle(context, state.isCurrentPeriod),
                style = MaterialTheme.typography.labelLarge,
                color = cs.onPrimary.copy(alpha = 0.85f),
                modifier = Modifier.weight(1f),
            )
            Text(
                text = deltaText(context, delta),
                style = MaterialTheme.typography.labelMedium,
                color = cs.onPrimary.copy(alpha = 0.9f),
            )
        }
        Spacer(Modifier.height(6.dp))
        Text(
            text = "%,d".format(state.pointsTotal),
            style = MaterialTheme.typography.displaySmall,
            fontWeight = FontWeight.Bold,
            color = cs.onPrimary,
        )
        Spacer(Modifier.height(14.dp))
        Box(
            Modifier
                .fillMaxWidth()
                .height(1.dp)
                .background(cs.onPrimary.copy(alpha = 0.25f))
        )
        Spacer(Modifier.height(12.dp))
        Row {
            MetricCell(
                label = stringResource(R.string.stats_metric_checkins),
                value = pluralStringResource(R.plurals.stats_value_times, state.checkInCount, state.checkInCount),
                modifier = Modifier.weight(1f),
            )
            MetricCell(
                label = stringResource(R.string.stats_metric_streak),
                value = pluralStringResource(R.plurals.stats_value_days, state.streakDays, state.streakDays),
                modifier = Modifier.weight(1f),
            )
        }
    }
}

@Composable
private fun MetricCell(label: String, value: String, modifier: Modifier = Modifier) {
    val cs = MaterialTheme.colorScheme
    Column(modifier = modifier) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = cs.onPrimary.copy(alpha = 0.75f),
        )
        Spacer(Modifier.height(2.dp))
        Text(
            text = value,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            color = cs.onPrimary,
        )
    }
}

private fun deltaText(context: Context, delta: Long): String = when {
    delta > 0L -> context.getString(R.string.stats_delta_up, delta)
    delta < 0L -> context.getString(R.string.stats_delta_down, delta)
    else -> context.getString(R.string.stats_delta_same)
}

@Composable
private fun ShareSection(state: StatsUiState) {
    if (state.shares.isEmpty()) {
        EmptyHint(stringResource(R.string.stats_empty_period_points))
        return
    }
    Row(verticalAlignment = Alignment.CenterVertically) {
        DonutChart(
            slices = state.shares.map { Color(it.colorValue) to it.percent },
            centerTop = "%,d".format(state.shares.sumOf { it.points }),
            centerBottom = stringResource(R.string.stats_center_points),
        )
        Spacer(Modifier.width(18.dp))
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            state.shares.forEach { share -> ShareLegendRow(share) }
        }
    }
}

@Composable
private fun ShareLegendRow(share: CategoryShare) {
    val cs = MaterialTheme.colorScheme
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            Modifier
                .size(9.dp)
                .clip(CircleShape)
                .background(Color(share.colorValue))
        )
        Spacer(Modifier.width(8.dp))
        Text(
            text = share.name,
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.weight(1f),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            text = formatPercent(share.percent),
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.SemiBold,
        )
        Spacer(Modifier.width(8.dp))
        Text(
            text = "+${share.points}",
            style = MaterialTheme.typography.labelMedium,
            color = cs.onSurfaceVariant,
        )
    }
}

@Composable
private fun RankSection(rank: List<TaskRankItem>) {
    if (rank.isEmpty()) {
        EmptyHint(stringResource(R.string.stats_empty_period_checkins))
        return
    }
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        rank.forEachIndexed { index, item -> RankRow(rankNo = index + 1, item = item) }
    }
}

@Composable
private fun RankRow(rankNo: Int, item: TaskRankItem) {
    val cs = MaterialTheme.colorScheme
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(26.dp)
                .clip(CircleShape)
                .background(
                    when (rankNo) {
                        1 -> cs.primary
                        2, 3 -> cs.primaryContainer
                        else -> cs.surfaceVariant
                    }
                ),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = "$rankNo",
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold,
                color = when (rankNo) {
                    1 -> cs.onPrimary
                    2, 3 -> cs.onPrimaryContainer
                    else -> cs.onSurfaceVariant
                },
            )
        }
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = item.name,
                style = MaterialTheme.typography.bodyLarge,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(2.dp))
            // 分类标签：允许同名任务共存时靠分类区分
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier
                        .size(8.dp)
                        .clip(CircleShape)
                        .background(Color(item.categoryColor))
                )
                Spacer(Modifier.width(4.dp))
                Text(
                    text = item.categoryName,
                    style = MaterialTheme.typography.labelSmall,
                    color = cs.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        Column(horizontalAlignment = Alignment.End) {
            Text(
                text = pluralStringResource(R.plurals.stats_value_times, item.count, item.count),
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
            )
            if (item.points != 0L) {
                Text(
                    text = if (item.points > 0) {
                        stringResource(R.string.stats_value_points_positive, item.points)
                    } else {
                        stringResource(R.string.stats_value_points, item.points)
                    },
                    style = MaterialTheme.typography.labelSmall,
                    color = cs.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun StatsCard(
    title: String,
    subtitle: String? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    ElevatedCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(18.dp)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
            if (subtitle != null) {
                Spacer(Modifier.height(2.dp))
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Spacer(Modifier.height(14.dp))
            content()
        }
    }
}

private fun formatPercent(p: Float): String {
    val v = p * 100f
    return if (v < 1f && v > 0f) "<1%" else "%.0f%%".format(v)
}
