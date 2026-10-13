@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)

package com.stats.app.ui.tasks

import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberTimePickerState
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.stats.app.R
import com.stats.app.data.model.PeriodType
import com.stats.app.data.model.RemindLevel
import com.stats.app.data.model.TriggerAction
import com.stats.app.ui.common.CategoryEditDialog
import com.stats.app.ui.common.label

/**
 * 任务编辑器：
 * 卡片分区（基本信息 / 周期与目标 / 提醒 / 积分 / 其他）+ 底部固定保存栏；
 * 支持 每日 / 每周 / 每月 / 每年 / 单次 / 常驻 六种类型：
 *  - 单次：完成一次即结束，未完成时每天提醒（完成后自动停止）；
 *  - 常驻：不设提醒，可随时多次记录（每次 +分）。
 */
@Composable
fun TaskEditorScreen(
    onBack: () -> Unit,
    viewModel: TaskEditorViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    var showTimePicker by remember { mutableStateOf(false) }
    var showDeleteConfirm by remember { mutableStateOf(false) }
    var showNewCategory by remember { mutableStateOf(false) }

    LaunchedEffect(state.saved, state.deleted) {
        if (state.saved || state.deleted) onBack()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        if (state.isNew) {
                            stringResource(R.string.editor_title_new)
                        } else {
                            stringResource(R.string.editor_title_edit)
                        }
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.editor_back))
                    }
                },
            )
        },
        bottomBar = {
            Surface(tonalElevation = 3.dp) {
                Box(
                    Modifier
                        .fillMaxWidth()
                        .windowInsetsPadding(
                            WindowInsets.safeDrawing.only(WindowInsetsSides.Bottom)
                        )
                        .padding(start = 20.dp, end = 20.dp, top = 10.dp, bottom = 10.dp),
                ) {
                    Button(
                        onClick = viewModel::save,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(52.dp),
                    ) {
                        Text(stringResource(R.string.common_save), style = MaterialTheme.typography.titleMedium)
                    }
                }
            }
        },
    ) { padding ->
        if (state.loading) {
            Box(
                Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentAlignment = Alignment.Center,
            ) {
                CircularProgressIndicator()
            }
        } else {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 20.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                BasicSection(state, viewModel)
                PeriodSection(state, viewModel)
                RemindSection(state, viewModel, onPickTime = { showTimePicker = true })
                PointsSection(state, viewModel, onNewCategory = { showNewCategory = true })
                MiscSection(state, viewModel, onDelete = { showDeleteConfirm = true })
                Spacer(Modifier.height(4.dp))
            }
        }
    }

    if (showTimePicker) {
        RemindTimePickerDialog(
            initialTime = state.remindTime,
            onConfirm = {
                viewModel.setRemindTime(it)
                showTimePicker = false
            },
            onDismiss = { showTimePicker = false },
        )
    }

    if (showNewCategory) {
        CategoryEditDialog(
            title = stringResource(R.string.editor_new_category),
            usedColors = state.categories.map { it.color }.toSet(),
            onConfirm = { name, color ->
                viewModel.createCategory(name, color)
                showNewCategory = false
            },
            onDismiss = { showNewCategory = false },
        )
    }

    if (showDeleteConfirm) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            title = { Text(stringResource(R.string.task_delete)) },
            text = { Text(stringResource(R.string.task_delete_confirm, state.name)) },
            confirmButton = {
                TextButton(onClick = {
                    showDeleteConfirm = false
                    viewModel.delete()
                }) {
                    Text(stringResource(R.string.common_delete), color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirm = false }) { Text(stringResource(R.string.common_cancel)) }
            },
        )
    }
}

// ---- 分区 ----

@Composable
private fun BasicSection(state: TaskEditorUiState, viewModel: TaskEditorViewModel) {
    EditorSection(icon = Icons.Filled.Edit, title = stringResource(R.string.editor_section_basic)) {
        OutlinedTextField(
            value = state.name,
            onValueChange = viewModel::setName,
            label = { Text(stringResource(R.string.editor_field_name)) },
            placeholder = { Text(stringResource(R.string.editor_field_name_hint)) },
            isError = state.nameError,
            supportingText = if (state.nameError) {
                { Text(stringResource(R.string.editor_name_error)) }
            } else {
                null
            },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )

        OutlinedTextField(
            value = state.note,
            onValueChange = viewModel::setNote,
            label = { Text(stringResource(R.string.editor_field_note)) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Composable
private fun PeriodSection(state: TaskEditorUiState, viewModel: TaskEditorViewModel) {
    val context = LocalContext.current
    EditorSection(icon = Icons.Filled.DateRange, title = stringResource(R.string.editor_section_period)) {
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            PeriodType.entries.forEach { type ->
                FilterChip(
                    selected = state.periodType == type,
                    onClick = { viewModel.setPeriodType(type) },
                    label = { Text(type.label(context)) },
                )
            }
        }

        when (state.periodType) {
            PeriodType.ONE_TIME -> SectionHint(stringResource(R.string.editor_hint_onetime))
            PeriodType.STANDING -> SectionHint(stringResource(R.string.editor_hint_standing))

            else -> Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(stringResource(R.string.editor_field_target), style = MaterialTheme.typography.bodyLarge)
                    Text(
                        text = targetSummary(context, state.periodType, state.periodTarget),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                NumberStepper(
                    value = state.periodTarget,
                    onValueChange = viewModel::setPeriodTarget,
                    minValue = 1,
                    maxValue = 99,
                    step = 1,
                )
            }
        }
    }
}

@Composable
private fun RemindSection(
    state: TaskEditorUiState,
    viewModel: TaskEditorViewModel,
    onPickTime: () -> Unit,
) {
    val context = LocalContext.current
    if (state.periodType == PeriodType.STANDING) return // 常驻任务不提醒

    val isOneTime = state.periodType == PeriodType.ONE_TIME
    val showAuto = !isOneTime && state.triggerAction == TriggerAction.AUTO_COMPLETE

    EditorSection(
        icon = Icons.Filled.Notifications,
        title = if (showAuto) stringResource(R.string.editor_section_auto) else stringResource(R.string.editor_section_remind),
    ) {
        if (!isOneTime) {
            SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                TriggerAction.entries.forEachIndexed { index, action ->
                    SegmentedButton(
                        selected = state.triggerAction == action,
                        onClick = { viewModel.setTriggerAction(action) },
                        shape = SegmentedButtonDefaults.itemShape(
                            index = index,
                            count = TriggerAction.entries.size,
                        ),
                    ) {
                        Text(action.label(context))
                    }
                }
            }
        }

        TimeRow(
            label = if (showAuto) stringResource(R.string.editor_field_auto_time) else stringResource(R.string.editor_field_remind_time),
            time = state.remindTime,
            onClick = onPickTime,
        )

        if (!isOneTime) {
            RemindDatePicker(
                periodType = state.periodType,
                weekDays = state.weekDays,
                monthDay = state.monthDay,
                yearMonth = state.yearMonth,
                yearDay = state.yearDay,
                onToggleWeekDay = viewModel::toggleWeekDay,
                onMonthDay = viewModel::setMonthDay,
                onYearMonth = viewModel::setYearMonth,
                onYearDay = viewModel::setYearDay,
            )
        }

        if (isOneTime) {
            SectionHint(stringResource(R.string.editor_hint_onetime_remind))
        }

        if (showAuto) {
            SectionHint(stringResource(R.string.editor_hint_auto))
        } else {
            Text(stringResource(R.string.editor_field_level), style = MaterialTheme.typography.bodyLarge)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                RemindLevel.entries.forEach { level ->
                    FilterChip(
                        selected = state.remindLevel == level,
                        onClick = { viewModel.setRemindLevel(level) },
                        label = { Text(level.label(context)) },
                    )
                }
            }
            SectionHint(levelHint(context, state.remindLevel))

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text(stringResource(R.string.editor_field_vibrate), style = MaterialTheme.typography.bodyLarge)
                    Text(
                        text = stringResource(R.string.editor_vibrate_hint),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Switch(checked = state.vibrate, onCheckedChange = viewModel::setVibrate)
            }
        }
    }
}

@Composable
private fun PointsSection(
    state: TaskEditorUiState,
    viewModel: TaskEditorViewModel,
    onNewCategory: () -> Unit,
) {
    EditorSection(icon = Icons.Filled.Star, title = stringResource(R.string.editor_section_points)) {
        Text(stringResource(R.string.editor_field_category), style = MaterialTheme.typography.bodyLarge)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            state.categories.forEach { category ->
                FilterChip(
                    selected = state.categoryId == category.id,
                    onClick = { viewModel.setCategory(category.id) },
                    label = { Text(category.name) },
                    leadingIcon = {
                        Box(
                            Modifier
                                .size(10.dp)
                                .clip(CircleShape)
                                .background(Color(category.color.toInt())),
                        )
                    },
                )
            }
            FilterChip(
                selected = false,
                onClick = onNewCategory,
                label = { Text(stringResource(R.string.editor_new_category_chip)) },
            )
        }

        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(stringResource(R.string.editor_field_points), style = MaterialTheme.typography.bodyLarge)
                Text(
                    text = stringResource(R.string.editor_points_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            NumberStepper(
                value = state.points,
                onValueChange = viewModel::setPoints,
                minValue = 0,
                maxValue = 999,
                step = 5,
            )
        }
    }
}

@Composable
private fun MiscSection(
    state: TaskEditorUiState,
    viewModel: TaskEditorViewModel,
    onDelete: () -> Unit,
) {
    EditorSection(icon = Icons.Filled.Settings, title = stringResource(R.string.editor_section_misc)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(stringResource(R.string.editor_field_enabled), style = MaterialTheme.typography.bodyLarge)
                Text(
                    text = stringResource(R.string.editor_enabled_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Switch(checked = state.enabled, onCheckedChange = viewModel::setEnabled)
        }

        if (!state.isNew) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .clickable(onClick = onDelete)
                    .padding(vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    Icons.Filled.Delete,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                    tint = MaterialTheme.colorScheme.error,
                )
                Spacer(Modifier.width(10.dp))
                Text(
                    text = stringResource(R.string.task_delete),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.error,
                )
            }
        }
    }
}

// ---- 通用组件 ----

/** 分区卡片：小图标 + 标题 + 内容 */
@Composable
private fun EditorSection(
    icon: ImageVector,
    title: String,
    content: @Composable ColumnScope.() -> Unit,
) {
    ElevatedCard(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    icon,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                    tint = MaterialTheme.colorScheme.primary,
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                )
            }
            content()
        }
    }
}

@Composable
private fun SectionHint(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

/** 可点击的时间选择行：左侧标题 + 右侧时间值 + 箭头 */
@Composable
private fun TimeRow(label: String, time: String, onClick: () -> Unit) {
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier
                .clickable(onClick = onClick)
                .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.bodyLarge,
                modifier = Modifier.weight(1f),
            )
            Text(
                text = time,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.primary,
            )
            Spacer(Modifier.width(6.dp))
            Icon(
                Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = null,
                modifier = Modifier.size(20.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/** 数字步进器：[−] [可输入数字] [＋] */
@Composable
private fun NumberStepper(
    value: String,
    onValueChange: (String) -> Unit,
    minValue: Int,
    maxValue: Int,
    step: Int,
) {
    val current = value.toIntOrNull() ?: minValue
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        StepperButton(label = "−", enabled = current > minValue) {
            onValueChange((current - step).coerceAtLeast(minValue).toString())
        }
        OutlinedTextField(
            value = value,
            onValueChange = onValueChange,
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            textStyle = MaterialTheme.typography.titleMedium.copy(textAlign = TextAlign.Center),
            modifier = Modifier.width(78.dp),
        )
        StepperButton(label = "＋", enabled = current < maxValue) {
            onValueChange((current + step).coerceAtMost(maxValue).toString())
        }
    }
}

@Composable
private fun StepperButton(label: String, enabled: Boolean, onClick: () -> Unit) {
    FilledTonalIconButton(
        onClick = onClick,
        enabled = enabled,
        modifier = Modifier.size(42.dp),
    ) {
        Text(label, style = MaterialTheme.typography.titleMedium)
    }
}

// ---- 数据辅助 ----

/** 周期 + 目标次数的摘要说明（单次 / 常驻不显示目标次数，不会走到这两支） */
private fun targetSummary(context: Context, type: PeriodType, targetText: String): String {
    val target = targetText.toIntOrNull() ?: 1
    val unit = context.getString(
        when (type) {
            PeriodType.DAILY -> R.string.fmt_every_daily
            PeriodType.WEEKLY -> R.string.fmt_every_weekly
            PeriodType.MONTHLY -> R.string.fmt_every_monthly
            PeriodType.YEARLY -> R.string.fmt_every_yearly
            PeriodType.ONE_TIME -> R.string.fmt_period_once
            PeriodType.STANDING -> R.string.fmt_period_standing
        }
    )
    return if (target <= 1) {
        context.getString(R.string.editor_target_summary_once, unit)
    } else {
        context.getString(R.string.editor_target_summary_count, unit, target)
    }
}

/** 提醒级别说明（与通知渠道行为一致） */
private fun levelHint(context: Context, level: RemindLevel): String = context.getString(
    when (level) {
        RemindLevel.SILENT -> R.string.editor_level_hint_silent
        RemindLevel.NOTIFY -> R.string.editor_level_hint_notify
        RemindLevel.BANNER -> R.string.editor_level_hint_banner
        RemindLevel.ALARM -> R.string.editor_level_hint_alarm
    }
)

/** 周期相关的日期选择（提醒与自动完成共用）：每周选执行日 / 每月几号 / 每年月日 */
@Composable
private fun RemindDatePicker(
    periodType: PeriodType,
    weekDays: List<Int>,
    monthDay: String,
    yearMonth: String,
    yearDay: String,
    onToggleWeekDay: (Int) -> Unit,
    onMonthDay: (String) -> Unit,
    onYearMonth: (String) -> Unit,
    onYearDay: (String) -> Unit,
) {
    val context = LocalContext.current
    when (periodType) {
        PeriodType.DAILY, PeriodType.ONE_TIME, PeriodType.STANDING -> Unit

        PeriodType.WEEKLY -> Column {
            Text(
                text = stringResource(R.string.editor_field_weekdays),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(6.dp))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                (1..7).forEach { day ->
                    FilterChip(
                        selected = day in weekDays,
                        onClick = { onToggleWeekDay(day) },
                        label = { Text(weekDayName(context, day)) },
                    )
                }
            }
        }

        PeriodType.MONTHLY -> OutlinedTextField(
            value = monthDay,
            onValueChange = onMonthDay,
            label = { Text(stringResource(R.string.editor_field_month_day)) },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            singleLine = true,
            modifier = Modifier.width(180.dp),
        )

        PeriodType.YEARLY -> Row(
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            OutlinedTextField(
                value = yearMonth,
                onValueChange = onYearMonth,
                label = { Text(stringResource(R.string.editor_field_month)) },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                singleLine = true,
                modifier = Modifier.width(110.dp),
            )
            OutlinedTextField(
                value = yearDay,
                onValueChange = onYearDay,
                label = { Text(stringResource(R.string.editor_field_day)) },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                singleLine = true,
                modifier = Modifier.width(110.dp),
            )
        }
    }
}

@Composable
private fun RemindTimePickerDialog(
    initialTime: String,
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    val parts = initialTime.split(":")
    val pickerState = rememberTimePickerState(
        initialHour = parts.getOrNull(0)?.toIntOrNull() ?: 8,
        initialMinute = parts.getOrNull(1)?.toIntOrNull() ?: 0,
        is24Hour = true,
    )
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Surface(
            shape = MaterialTheme.shapes.extraLarge,
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 6.dp,
            modifier = Modifier.padding(horizontal = 24.dp),
        ) {
            Column(
                modifier = Modifier.padding(20.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(stringResource(R.string.editor_pick_time_title), style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(16.dp))
                TimePicker(state = pickerState)
                Spacer(Modifier.height(8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                ) {
                    TextButton(onClick = onDismiss) { Text(stringResource(R.string.common_cancel)) }
                    Spacer(Modifier.width(8.dp))
                    Button(onClick = {
                        onConfirm("%02d:%02d".format(pickerState.hour, pickerState.minute))
                    }) {
                        Text(stringResource(R.string.common_confirm))
                    }
                }
            }
        }
    }
}

private fun weekDayName(context: Context, day: Int): String = context.getString(
    when (day) {
        1 -> R.string.editor_weekday_mon
        2 -> R.string.editor_weekday_tue
        3 -> R.string.editor_weekday_wed
        4 -> R.string.editor_weekday_thu
        5 -> R.string.editor_weekday_fri
        6 -> R.string.editor_weekday_sat
        else -> R.string.editor_weekday_sun
    }
)
