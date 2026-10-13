@file:OptIn(ExperimentalLayoutApi::class)

package com.stats.app.ui.settings

import android.app.Activity
import android.app.AlarmManager
import android.app.NotificationManager
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.app.NotificationManagerCompat
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.stats.app.BuildConfig
import com.stats.app.R
import com.stats.app.data.model.AppLanguage
import com.stats.app.data.model.RemindLevel
import com.stats.app.data.model.ThemeMode
import com.stats.app.reminder.ReminderAlarmActivity
import com.stats.app.ui.common.dayStartHourLabel
import com.stats.app.ui.common.label
import java.time.LocalDate

private data class PermissionCheck(
    val title: String,
    val status: Boolean?,
    val okText: String,
    val badText: String,
    val onOpen: (Context) -> Unit,
)

@Composable
fun SettingsScreen(
    onOpenCategories: () -> Unit = {},
    onOpenHistory: () -> Unit = {},
    viewModel: SettingsViewModel = hiltViewModel(),
) {
    val context = LocalContext.current
    val activity = remember(context) { context.findActivity() }
    var refreshKey by remember { mutableIntStateOf(0) }

    // 从系统设置返回时刷新检测结果
    LifecycleResumeEffect(Unit) {
        refreshKey++
        onPauseOrDispose { }
    }

    val checks = remember(refreshKey) { collectChecks(context) }

    val dayStartHour by viewModel.dayStartHour.collectAsStateWithLifecycle()
    val themeMode by viewModel.themeMode.collectAsStateWithLifecycle()
    val appLanguage by viewModel.appLanguage.collectAsStateWithLifecycle()
    val operation by viewModel.operation.collectAsStateWithLifecycle()

    var showImportConfirm by remember { mutableStateOf(false) }
    var showQuickRestoreConfirm by remember { mutableStateOf(false) }
    var showDayStartDialog by remember { mutableStateOf(false) }
    var showThemeDialog by remember { mutableStateOf(false) }
    var showLanguageDialog by remember { mutableStateOf(false) }

    // 导出为 .txt（内容为 JSON）：提升国产 ROM 文件选择器的可见性（vivo 等不展示 .json）
    val exportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("text/plain")
    ) { uri -> if (uri != null) viewModel.exportBackup(uri) }

    val importLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri -> if (uri != null) viewModel.importBackup(uri) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp),
    ) {
        Spacer(Modifier.height(12.dp))
        Text(
            text = stringResource(R.string.settings_title),
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.SemiBold,
        )

        // ===== 偏好设置 =====
        SectionHeader(R.string.settings_section_prefs)
        SettingsGroup {
            SettingRow(
                title = stringResource(R.string.settings_day_start),
                subtitle = stringResource(R.string.settings_day_start_desc),
                value = dayStartHourLabel(context, dayStartHour),
                onClick = { showDayStartDialog = true },
            )
            ItemDivider()
            SettingRow(
                title = stringResource(R.string.settings_theme),
                subtitle = stringResource(R.string.settings_theme_desc),
                value = themeMode.label(context),
                onClick = { showThemeDialog = true },
            )
            ItemDivider()
            SettingRow(
                title = stringResource(R.string.settings_language),
                subtitle = stringResource(R.string.settings_language_desc),
                value = appLanguage.label(context),
                onClick = { showLanguageDialog = true },
            )
        }

        // ===== 提醒权限 =====
        SectionHeader(R.string.settings_section_permissions)
        SettingsGroup {
            checks.forEachIndexed { index, check ->
                if (index > 0) ItemDivider()
                PermissionRow(check = check, onOpen = { check.onOpen(context) })
            }
        }

        // ===== 测试提醒 =====
        SectionHeader(R.string.settings_section_test)
        SettingsGroup {
            Column(Modifier.padding(16.dp)) {
                Text(
                    text = stringResource(R.string.settings_test_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(10.dp))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    RemindLevel.entries.forEach { level ->
                        FilterChip(
                            selected = false,
                            onClick = {
                                if (level == RemindLevel.ALARM) {
                                    context.startActivity(
                                        Intent(context, ReminderAlarmActivity::class.java)
                                            .putExtra(ReminderAlarmActivity.EXTRA_TEST, true)
                                    )
                                } else {
                                    viewModel.testNotify(level)
                                }
                            },
                            label = { Text(level.label(context)) },
                        )
                    }
                }
            }
        }

        // ===== 任务 =====
        SectionHeader(R.string.settings_section_tasks)
        SettingsGroup {
            SettingRow(
                title = stringResource(R.string.settings_history),
                subtitle = stringResource(R.string.settings_history_desc),
                onClick = onOpenHistory,
            )
        }

        // ===== 积分 =====
        SectionHeader(R.string.settings_section_points)
        SettingsGroup {
            SettingRow(
                title = stringResource(R.string.settings_categories),
                subtitle = stringResource(R.string.settings_categories_desc),
                value = stringResource(R.string.settings_manage),
                onClick = onOpenCategories,
            )
        }

        // ===== 数据管理 =====
        SectionHeader(R.string.settings_section_data)
        SettingsGroup {
            SettingRow(
                title = stringResource(R.string.settings_export),
                subtitle = stringResource(R.string.settings_export_desc),
                onClick = { exportLauncher.launch("stats-backup-${LocalDate.now()}.txt") },
            )
            ItemDivider()
            SettingRow(
                title = stringResource(R.string.settings_quick_restore),
                subtitle = stringResource(R.string.settings_quick_restore_desc),
                onClick = { showQuickRestoreConfirm = true },
            )
            ItemDivider()
            SettingRow(
                title = stringResource(R.string.settings_import),
                subtitle = stringResource(R.string.settings_import_desc),
                onClick = { showImportConfirm = true },
            )
        }
        Spacer(Modifier.height(8.dp))
        Text(
            text = stringResource(R.string.settings_import_note),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        // ===== 关于 =====
        SectionHeader(R.string.settings_section_about)
        SettingsGroup {
            SettingRow(
                title = stringResource(R.string.settings_about_version, BuildConfig.VERSION_NAME),
            )
        }

        Spacer(Modifier.height(40.dp))
    }

    if (showDayStartDialog) {
        ChoiceDialog(
            title = stringResource(R.string.settings_day_start),
            current = dayStartHour,
            options = (0..4).map { hour -> hour to dayStartHourLabel(context, hour) },
            onSelect = {
                viewModel.setDayStartHour(it)
                showDayStartDialog = false
            },
            onDismiss = { showDayStartDialog = false },
        )
    }

    if (showThemeDialog) {
        ChoiceDialog(
            title = stringResource(R.string.settings_theme),
            current = themeMode,
            options = ThemeMode.entries.map { mode -> mode to mode.label(context) },
            onSelect = {
                viewModel.setThemeMode(it)
                showThemeDialog = false
            },
            onDismiss = { showThemeDialog = false },
        )
    }

    if (showLanguageDialog) {
        ChoiceDialog(
            title = stringResource(R.string.settings_language),
            current = appLanguage,
            options = AppLanguage.entries.map { lang -> lang to lang.label(context) },
            onSelect = { lang ->
                showLanguageDialog = false
                if (lang != appLanguage) {
                    viewModel.setAppLanguage(lang)
                    // 立即重建以应用新语言（系统级 locale 同步见 ViewModel）
                    activity?.recreate()
                }
            },
            onDismiss = { showLanguageDialog = false },
        )
    }

    if (showQuickRestoreConfirm) {
        AlertDialog(
            onDismissRequest = { showQuickRestoreConfirm = false },
            title = { Text(stringResource(R.string.settings_quick_restore)) },
            text = { Text(stringResource(R.string.settings_quick_restore_confirm)) },
            confirmButton = {
                TextButton(onClick = {
                    showQuickRestoreConfirm = false
                    viewModel.importFromSnapshot()
                }) { Text(stringResource(R.string.common_restore)) }
            },
            dismissButton = {
                TextButton(onClick = { showQuickRestoreConfirm = false }) {
                    Text(stringResource(R.string.common_cancel))
                }
            },
        )
    }

    if (showImportConfirm) {
        AlertDialog(
            onDismissRequest = { showImportConfirm = false },
            title = { Text(stringResource(R.string.settings_import)) },
            text = { Text(stringResource(R.string.settings_import_confirm)) },
            confirmButton = {
                TextButton(onClick = {
                    showImportConfirm = false
                    importLauncher.launch(arrayOf("*/*"))
                }) { Text(stringResource(R.string.common_pick_file)) }
            },
            dismissButton = {
                TextButton(onClick = { showImportConfirm = false }) {
                    Text(stringResource(R.string.common_cancel))
                }
            },
        )
    }

    if (operation != null) {
        AlertDialog(
            onDismissRequest = viewModel::dismissOperation,
            title = { Text(stringResource(R.string.settings_op_result)) },
            text = { Text(operation ?: "") },
            confirmButton = {
                TextButton(onClick = viewModel::dismissOperation) {
                    Text(stringResource(R.string.common_got_it))
                }
            },
        )
    }
}

// ===== 布局组件 =====

/** 分组标题（上间距 + 标题 + 下间距，与分组卡片配套） */
@Composable
private fun SectionHeader(textRes: Int) {
    Spacer(Modifier.height(26.dp))
    Text(
        text = stringResource(textRes),
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.primary,
        fontWeight = FontWeight.SemiBold,
    )
    Spacer(Modifier.height(8.dp))
}

/** 分组卡片：一组的多个行共用一张卡片，行之间用 [ItemDivider] 分隔 */
@Composable
private fun SettingsGroup(content: @Composable ColumnScope.() -> Unit) {
    ElevatedCard(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.elevatedCardColors(),
    ) {
        Column(content = content)
    }
}

/** 行间分隔线（左缩进对齐文字） */
@Composable
private fun ItemDivider() {
    HorizontalDivider(
        modifier = Modifier.padding(start = 16.dp, end = 16.dp),
        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f),
    )
}

/** 设置行：标题 + 可选副标题 + 可选右侧值 + 可选点击 */
@Composable
private fun SettingRow(
    title: String,
    subtitle: String? = null,
    value: String? = null,
    onClick: (() -> Unit)? = null,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .then(
                if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier
            )
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleSmall)
            if (subtitle != null) {
                Spacer(Modifier.height(2.dp))
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        if (value != null) {
            Spacer(Modifier.width(12.dp))
            Text(
                text = value,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.Medium,
            )
        }
        if (onClick != null) {
            Spacer(Modifier.width(6.dp))
            Text(
                text = "›",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun PermissionRow(check: PermissionCheck, onOpen: () -> Unit) {
    val dotColor = when (check.status) {
        true -> Color(0xFF34A853)
        false -> Color(0xFFE24B4A)
        null -> Color(0xFFB4B2A9)
    }
    val statusText = when (check.status) {
        true -> check.okText
        false -> check.badText
        null -> stringResource(R.string.settings_status_check)
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onOpen)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier
                .size(10.dp)
                .clip(CircleShape)
                .background(dotColor),
        )
        Spacer(Modifier.width(12.dp))
        Text(
            text = check.title,
            style = MaterialTheme.typography.titleSmall,
            modifier = Modifier.weight(1f),
        )
        Text(
            text = statusText,
            style = MaterialTheme.typography.bodySmall,
            color = if (check.status == false) {
                MaterialTheme.colorScheme.error
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant
            },
        )
        Spacer(Modifier.width(6.dp))
        Text(
            text = "›",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/** 单选对话框（日界线 / 主题 / 语言共用） */
@Composable
private fun <T> ChoiceDialog(
    title: String,
    current: T,
    options: List<Pair<T, String>>,
    onSelect: (T) -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column {
                options.forEach { (value, label) ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onSelect(value) }
                            .padding(vertical = 2.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        RadioButton(
                            selected = value == current,
                            onClick = { onSelect(value) },
                        )
                        Spacer(Modifier.width(4.dp))
                        Text(label, style = MaterialTheme.typography.bodyLarge)
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.common_cancel)) }
        },
    )
}

private fun Context.findActivity(): Activity? {
    var ctx: Context? = this
    while (ctx is ContextWrapper) {
        if (ctx is Activity) return ctx
        ctx = ctx.baseContext
    }
    return null
}

private fun collectChecks(context: Context): List<PermissionCheck> {
    val packageName = context.packageName
    val notifOk = NotificationManagerCompat.from(context).areNotificationsEnabled()
    val exactAlarmOk = context.getSystemService(AlarmManager::class.java)
        ?.canScheduleExactAlarms() ?: false
    val fullScreenOk = if (Build.VERSION.SDK_INT >= 34) {
        context.getSystemService(NotificationManager::class.java)
            ?.canUseFullScreenIntent() ?: true
    } else {
        true
    }
    val batteryOk = context.getSystemService(PowerManager::class.java)
        ?.isIgnoringBatteryOptimizations(packageName) ?: false

    fun str(resId: Int): String = context.getString(resId)

    return listOf(
        PermissionCheck(
            title = str(R.string.settings_perm_notify),
            status = notifOk,
            okText = str(R.string.settings_status_on),
            badText = str(R.string.settings_status_off),
            onOpen = { ctx ->
                safeStart(
                    ctx,
                    Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).apply {
                        putExtra(Settings.EXTRA_APP_PACKAGE, packageName)
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    },
                    packageName,
                )
            },
        ),
        PermissionCheck(
            title = str(R.string.settings_perm_exact_alarm),
            status = exactAlarmOk,
            okText = str(R.string.settings_status_allowed),
            badText = str(R.string.settings_status_disallowed),
            onOpen = { ctx ->
                safeStart(
                    ctx,
                    Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM).apply {
                        data = Uri.parse("package:$packageName")
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    },
                    packageName,
                )
            },
        ),
        PermissionCheck(
            title = str(R.string.settings_perm_fullscreen),
            status = fullScreenOk,
            okText = str(R.string.settings_status_allowed),
            badText = str(R.string.settings_status_disallowed),
            onOpen = { ctx ->
                if (Build.VERSION.SDK_INT >= 34) {
                    safeStart(
                        ctx,
                        Intent(Settings.ACTION_MANAGE_APP_USE_FULL_SCREEN_INTENT).apply {
                            data = Uri.parse("package:$packageName")
                            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        },
                        packageName,
                    )
                } else {
                    openAppDetails(ctx, packageName)
                }
            },
        ),
        PermissionCheck(
            title = str(R.string.settings_perm_battery),
            status = batteryOk,
            okText = str(R.string.settings_status_exempt),
            badText = str(R.string.settings_status_restricted),
            onOpen = { ctx ->
                safeStart(
                    ctx,
                    Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                        data = Uri.parse("package:$packageName")
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    },
                    packageName,
                )
            },
        ),
        PermissionCheck(
            title = str(R.string.settings_perm_autostart),
            status = null,
            okText = "",
            badText = "",
            onOpen = { ctx -> openAppDetails(ctx, packageName) },
        ),
    )
}

private fun safeStart(context: Context, intent: Intent, packageName: String) {
    runCatching { context.startActivity(intent) }
        .onFailure { openAppDetails(context, packageName) }
}

private fun openAppDetails(context: Context, packageName: String) {
    runCatching {
        context.startActivity(
            Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                data = Uri.parse("package:$packageName")
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
        )
    }
}
