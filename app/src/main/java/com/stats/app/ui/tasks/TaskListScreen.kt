@file:OptIn(ExperimentalFoundationApi::class, ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)

package com.stats.app.ui.tasks

import android.content.Context
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.stats.app.R
import com.stats.app.data.db.entity.PointsCategoryEntity
import com.stats.app.data.db.entity.TaskEntity
import com.stats.app.data.model.PeriodType
import com.stats.app.data.model.TriggerAction
import com.stats.app.ui.common.backfillDayLabel
import com.stats.app.ui.common.currentPeriodLabel
import com.stats.app.ui.common.everyLabel
import com.stats.app.ui.common.label
import java.time.LocalDate

@Composable
fun TaskListScreen(
    onCreate: () -> Unit,
    onEdit: (Long) -> Unit,
    viewModel: TaskListViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val backfill by viewModel.backfill.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val haptic = LocalHapticFeedback.current
    val snackbarHostState = remember { SnackbarHostState() }

    // 长按菜单与删除确认
    var menuTask by remember { mutableStateOf<TaskEntity?>(null) }
    var deleteTarget by remember { mutableStateOf<TaskEntity?>(null) }

    // 单次任务完成后：提示已移至历史任务，并支持一键撤销（一次性事件，不会重复弹出）
    val doneMovedFormat = stringResource(R.string.task_done_moved)
    val undoLabel = stringResource(R.string.task_undo)
    LaunchedEffect(Unit) {
        viewModel.oneTimeEvents.collect { event ->
            val result = snackbarHostState.showSnackbar(
                message = doneMovedFormat.format(event.taskName),
                actionLabel = undoLabel,
            )
            if (result == SnackbarResult.ActionPerformed) {
                viewModel.undoOneTimeDone(event.taskId)
            }
        }
    }

    Box(Modifier.fillMaxSize()) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 12.dp, bottom = 100.dp),
        ) {
            item(key = "title") {
                Text(
                    text = stringResource(R.string.main_tab_tasks),
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.padding(bottom = 4.dp),
                )
            }

            if (state.loaded && state.tasks.isEmpty()) {
                item(key = "empty") { EmptyState(onCreate) }
            }

            PeriodType.entries.forEach { type ->
                val tasks = state.tasks.filter { it.periodType == type }
                if (tasks.isNotEmpty()) {
                    item(key = "header-$type") {
                        Text(
                            text = stringResource(R.string.task_section_header, type.label(context)),
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(top = 16.dp, bottom = 6.dp),
                        )
                    }
                    items(tasks, key = { it.id }) { task ->
                        TaskCard(
                            task = task,
                            category = state.categories[task.categoryId],
                            count = state.counts[task.id] ?: 0,
                            onCheckToggle = {
                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                viewModel.toggleCheckIn(task)
                            },
                            onClick = { onEdit(task.id) },
                            onLongPress = {
                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                menuTask = task
                            },
                        )
                    }
                }
            }
        }

        FloatingActionButton(
            onClick = onCreate,
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(20.dp),
        ) {
            Icon(Icons.Filled.Add, contentDescription = stringResource(R.string.task_new))
        }

        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 88.dp),
        )
    }

    // 长按操作菜单（按任务类型提供不同操作）
    menuTask?.let { task ->
        ModalBottomSheet(onDismissRequest = { menuTask = null }) {
            Column(Modifier.padding(bottom = 28.dp)) {
                Text(
                    text = task.name,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.padding(horizontal = 24.dp, vertical = 6.dp),
                )
                SheetAction(
                    text = if (task.enabled) {
                        stringResource(R.string.task_disable)
                    } else {
                        stringResource(R.string.task_enable)
                    },
                    onClick = {
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        viewModel.setEnabled(task, !task.enabled)
                        menuTask = null
                    },
                )
                when (task.periodType) {
                    PeriodType.STANDING -> {
                        if ((state.counts[task.id] ?: 0) > 0) {
                            SheetAction(
                                text = stringResource(R.string.task_undo_last),
                                onClick = {
                                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                    viewModel.undoLastAny(task)
                                    menuTask = null
                                },
                            )
                        }
                    }

                    PeriodType.ONE_TIME -> Unit // 单次任务通过圆点撤销，无需补卡

                    else -> {
                        SheetAction(
                            text = stringResource(R.string.task_backfill),
                            onClick = {
                                viewModel.openBackfill(task)
                                menuTask = null
                            },
                        )
                    }
                }
                SheetAction(
                    text = stringResource(R.string.task_delete),
                    color = MaterialTheme.colorScheme.error,
                    onClick = {
                        deleteTarget = task
                        menuTask = null
                    },
                )
            }
        }
    }

    // 删除确认
    deleteTarget?.let { task ->
        AlertDialog(
            onDismissRequest = { deleteTarget = null },
            title = { Text(stringResource(R.string.task_delete)) },
            text = { Text(stringResource(R.string.task_delete_confirm, task.name)) },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.archiveTask(task)
                    deleteTarget = null
                }) {
                    Text(stringResource(R.string.common_delete), color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { deleteTarget = null }) { Text(stringResource(R.string.common_cancel)) }
            },
        )
    }

    backfill?.let { bf ->
        BackfillDialog(
            state = bf,
            onSelect = { date ->
                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                viewModel.confirmBackfill(date)
            },
            onDismiss = { viewModel.dismissBackfill() },
        )
    }
}

@Composable
private fun SheetAction(
    text: String,
    color: Color = Color.Unspecified,
    onClick: () -> Unit,
) {
    val cs = MaterialTheme.colorScheme
    Text(
        text = text,
        style = MaterialTheme.typography.bodyLarge,
        color = if (color == Color.Unspecified) cs.onSurface else color,
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 24.dp, vertical = 15.dp),
    )
}

@Composable
private fun EmptyState(onCreate: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 120.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(stringResource(R.string.task_empty_title), style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(8.dp))
        Text(
            text = stringResource(R.string.task_empty_hint),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(16.dp))
        Button(onClick = onCreate) { Text(stringResource(R.string.task_new)) }
    }
}

@Composable
private fun TaskCard(
    task: TaskEntity,
    category: PointsCategoryEntity?,
    count: Int,
    onCheckToggle: () -> Unit,
    onClick: () -> Unit,
    onLongPress: () -> Unit,
) {
    val cs = MaterialTheme.colorScheme
    val context = LocalContext.current
    val dark = isSystemInDarkTheme()
    val accent = category?.let { Color(it.color.toInt()) } ?: cs.primary
    val isStanding = task.periodType == PeriodType.STANDING
    val done = !isStanding && count >= task.periodTarget
    val enabled = task.enabled

    // 卡片底色：启用 = 分类色淡化混合（浅色主题更淡）；停用 = 中性灰
    val container = if (enabled) {
        lerp(cs.surfaceContainerLow, accent, if (dark) 0.30f else 0.15f)
    } else {
        cs.surfaceContainer
    }

    ElevatedCard(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
            .combinedClickable(onClick = onClick, onLongClick = onLongPress),
        colors = CardDefaults.elevatedCardColors(containerColor = container),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(IntrinsicSize.Min),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // 左侧分类色条（停用时灰化）
            Box(
                Modifier
                    .width(5.dp)
                    .fillMaxHeight()
                    .background(if (enabled) accent else cs.outlineVariant),
            )
            Row(
                modifier = Modifier
                    .weight(1f)
                    .padding(start = 13.dp, end = 6.dp, top = 12.dp, bottom = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text(
                        text = task.name,
                        style = MaterialTheme.typography.titleMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        textDecoration = if (done) TextDecoration.LineThrough else null,
                        color = if (enabled) Color.Unspecified else cs.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(2.dp))
                    Text(
                        text = task.subtitle(context, count),
                        style = MaterialTheme.typography.bodySmall,
                        color = if (enabled) {
                            cs.onSurfaceVariant
                        } else {
                            cs.onSurfaceVariant.copy(alpha = 0.6f)
                        },
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                Spacer(Modifier.width(8.dp))
                Text(
                    text = if (isStanding && enabled) {
                        stringResource(R.string.task_points_per_time, task.points)
                    } else {
                        "+${task.points}"
                    },
                    style = MaterialTheme.typography.titleSmall,
                    color = if (enabled) cs.primary else cs.onSurfaceVariant.copy(alpha = 0.6f),
                )
                Spacer(Modifier.width(6.dp))
                CheckRing(
                    done = done,
                    enabled = enabled,
                    count = count,
                    showCount = !done && count > 0 && (task.periodTarget > 1 || isStanding),
                    taskName = task.name,
                    onClick = onCheckToggle,
                )
            }
        }
    }
}

@Composable
private fun CheckRing(
    done: Boolean,
    enabled: Boolean,
    count: Int,
    showCount: Boolean,
    taskName: String,
    onClick: () -> Unit,
) {
    val cs = MaterialTheme.colorScheme
    val ringActionDesc = stringResource(
        R.string.task_ring_content_desc,
        stringResource(if (done) R.string.task_undo else R.string.task_checkin),
        taskName,
    )
    // 圆环不再使用分类色：完成 = 主题色实心；未完成 = 中性描边
    val ring = when {
        !enabled -> cs.outline.copy(alpha = 0.4f)
        done -> cs.primary
        else -> cs.outline
    }
    Box(
        modifier = Modifier
            .size(44.dp)
            .clip(CircleShape)
            .clickable(onClick = onClick)
            .semantics { contentDescription = ringActionDesc },
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier = Modifier
                .size(34.dp)
                .clip(CircleShape)
                .then(
                    if (done) {
                        Modifier.background(ring)
                    } else {
                        Modifier.border(2.dp, ring, CircleShape)
                    }
                ),
            contentAlignment = Alignment.Center,
        ) {
            when {
                done -> Icon(
                    imageVector = Icons.Filled.Check,
                    contentDescription = null,
                    tint = if (enabled) cs.onPrimary else cs.surface,
                    modifier = Modifier.size(18.dp),
                )
                showCount -> Text(
                    text = "$count",
                    color = ring,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium,
                )
            }
        }
    }
}

@Composable
private fun BackfillDialog(
    state: BackfillUiState,
    onSelect: (LocalDate) -> Unit,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.task_backfill_title, state.task.name)) },
        text = {
            Column {
                Text(
                    text = stringResource(R.string.task_backfill_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(12.dp))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    state.options.forEach { option ->
                        FilterChip(
                            selected = false,
                            enabled = !option.alreadyDone,
                            onClick = { onSelect(option.date) },
                            label = { Text(backfillDayLabel(context, option.date)) },
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.common_cancel)) }
        },
    )
}

private fun TaskEntity.subtitle(context: Context, count: Int): String = when (periodType) {
    PeriodType.STANDING ->
        if (count > 0) {
            context.getString(R.string.task_sub_standing_counted, count)
        } else {
            context.getString(R.string.task_sub_standing)
        }

    PeriodType.ONE_TIME ->
        if (count >= 1) {
            context.getString(R.string.task_sub_onetime_done)
        } else {
            context.getString(
                R.string.task_sub_onetime_remind,
                remindPlan.time,
                remindLevel.label(context),
            )
        }

    else -> {
        val period = if (periodTarget > 1) {
            context.getString(
                R.string.task_sub_period_progress,
                periodType.currentPeriodLabel(context),
                count,
                periodTarget,
            )
        } else {
            periodType.everyLabel(context)
        }
        val action = if (triggerAction == TriggerAction.AUTO_COMPLETE) {
            context.getString(R.string.task_sub_auto_complete)
        } else {
            context.getString(
                R.string.task_sub_remind,
                remindPlan.time,
                remindLevel.label(context),
            )
        }
        "$period · $action"
    }
}
