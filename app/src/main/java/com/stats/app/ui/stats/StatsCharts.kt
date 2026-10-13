@file:OptIn(ExperimentalLayoutApi::class)

package com.stats.app.ui.stats

import android.content.Context
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.stats.app.R
import com.stats.app.ui.common.monthShortLabel

/** 空态占位 */
@Composable
fun EmptyHint(text: String) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(96.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/**
 * 积分趋势柱状图（纯布局实现，带生长动画）。
 * 负值柱按 0 显示（跨周期撤销导致的账务调整不在趋势中夸大）。
 */
@Composable
fun TrendBarChart(bars: List<TrendBar>, modifier: Modifier = Modifier) {
    if (bars.isEmpty() || bars.all { it.value == 0L }) {
        EmptyHint(stringResource(R.string.stats_empty_trend))
        return
    }
    val maxValue = bars.maxOf { it.value }.coerceAtLeast(1L)
    val cs = MaterialTheme.colorScheme
    val context = LocalContext.current

    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        bars.forEach { bar ->
            val fraction = (bar.value.coerceAtLeast(0L).toFloat() / maxValue).coerceIn(0f, 1f)
            val animated by animateFloatAsState(
                targetValue = fraction,
                animationSpec = tween(durationMillis = 650, easing = FastOutSlowInEasing),
                label = "trendBar",
            )
            Column(
                modifier = Modifier.weight(1f),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    text = compactPoints(context, bar.value),
                    fontSize = 10.sp,
                    color = if (bar.isCurrent) cs.primary else cs.onSurfaceVariant,
                    fontWeight = if (bar.isCurrent) FontWeight.SemiBold else FontWeight.Normal,
                    maxLines = 1,
                )
                Spacer(Modifier.height(4.dp))
                Box(
                    modifier = Modifier.height(116.dp),
                    contentAlignment = Alignment.BottomCenter,
                ) {
                    Box(
                        modifier = Modifier
                            .width(22.dp)
                            .height((4f + animated * 112f).dp)
                            .clip(
                                RoundedCornerShape(
                                    topStart = 6.dp,
                                    topEnd = 6.dp,
                                    bottomStart = 3.dp,
                                    bottomEnd = 3.dp,
                                )
                            )
                            .background(
                                if (bar.isCurrent) cs.primary
                                else cs.primary.copy(alpha = 0.38f)
                            ),
                    )
                }
                Spacer(Modifier.height(6.dp))
                val month = bar.monthIndex
                Text(
                    text = if (month != null) monthShortLabel(context, month) else bar.label,
                    fontSize = 10.sp,
                    color = if (bar.isCurrent) cs.primary else cs.onSurfaceVariant,
                    fontWeight = if (bar.isCurrent) FontWeight.SemiBold else FontWeight.Normal,
                    maxLines = 1,
                )
            }
        }
    }
}

/** 环形占比图（Canvas 自绘，展开动画） */
@Composable
fun DonutChart(
    slices: List<Pair<Color, Float>>,
    centerTop: String,
    centerBottom: String,
    modifier: Modifier = Modifier,
    canvasSize: Dp = 148.dp,
    strokeWidth: Dp = 24.dp,
) {
    val anim = remember { Animatable(0f) }
    LaunchedEffect(slices) {
        anim.snapTo(0f)
        anim.animateTo(1f, tween(durationMillis = 700, easing = FastOutSlowInEasing))
    }
    val emptyColor = MaterialTheme.colorScheme.surfaceVariant
    val cs = MaterialTheme.colorScheme

    Box(modifier = modifier.size(canvasSize), contentAlignment = Alignment.Center) {
        Canvas(Modifier.matchParentSize()) {
            val stroke = strokeWidth.toPx()
            val inset = stroke / 2f
            val topLeft = Offset(inset, inset)
            val arcSize = Size(size.width - stroke, size.height - stroke)
            if (slices.isEmpty()) {
                drawArc(
                    color = emptyColor,
                    startAngle = 0f,
                    sweepAngle = 360f,
                    useCenter = false,
                    topLeft = topLeft,
                    size = arcSize,
                    style = Stroke(width = stroke, cap = StrokeCap.Butt),
                )
            } else {
                var start = -90f
                slices.forEach { (color, frac) ->
                    val full = frac * 360f * anim.value
                    val sweep = (full - 3f).coerceAtLeast(0.3f)
                    drawArc(
                        color = color,
                        startAngle = start + 1.5f,
                        sweepAngle = sweep,
                        useCenter = false,
                        topLeft = topLeft,
                        size = arcSize,
                        style = Stroke(width = stroke, cap = StrokeCap.Butt),
                    )
                    start += full
                }
            }
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = centerTop,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
            )
            Text(
                text = centerBottom,
                style = MaterialTheme.typography.labelMedium,
                color = cs.onSurfaceVariant,
            )
        }
    }
}

/**
 * 打卡热力图（15 周 × 7 天，列 = 周，行 = 周一到周日）。
 * 每天的格子涂"当日积分最高的分类"的颜色；无正积分 = 浅灰空格；未来 = 透明。
 */
@Composable
fun HeatmapGrid(cells: List<HeatCell>, modifier: Modifier = Modifier) {
    val cs = MaterialTheme.colorScheme
    if (cells.isEmpty()) {
        EmptyHint(stringResource(R.string.stats_empty_heat))
        return
    }
    val weeks = cells.size / 7
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(3.dp, Alignment.CenterHorizontally),
    ) {
        for (w in 0 until weeks) {
            Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                for (d in 0 until 7) {
                    val cell = cells[w * 7 + d]
                    val color = when {
                        cell.future -> Color.Transparent
                        cell.colorValue == null -> cs.surfaceVariant.copy(alpha = 0.45f)
                        else -> Color(cell.colorValue)
                    }
                    Box(
                        Modifier
                            .size(13.dp)
                            .clip(RoundedCornerShape(3.dp))
                            .background(color)
                    )
                }
            }
        }
    }
}

/** 热力图图例：灰色 = 无积分；其余色块 = 当天积分最高的分类 */
@Composable
fun HeatmapLegend(items: List<HeatLegendItem>, modifier: Modifier = Modifier) {
    FlowRow(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        LegendItem(
            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
            label = stringResource(R.string.stats_heat_legend_none),
        )
        items.forEach { item ->
            LegendItem(color = Color(item.colorValue), label = item.name)
        }
    }
}

@Composable
private fun LegendItem(color: Color, label: String) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Box(
            Modifier
                .size(11.dp)
                .clip(RoundedCornerShape(3.dp))
                .background(color)
        )
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

private fun compactPoints(context: Context, v: Long): String {
    val zh = context.resources.configuration.locales[0].language == "zh"
    val cut = if (zh) 10000L else 1000L
    return when {
        v == 0L -> " "
        v >= cut -> context.getString(R.string.stats_compact_large, "%.1f".format(v / cut.toDouble()))
        else -> "%,d".format(v)
    }
}
