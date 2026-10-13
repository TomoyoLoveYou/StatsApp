package com.stats.app.ui.settings

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.stats.app.R
import com.stats.app.data.backup.BackupManager
import com.stats.app.data.model.AppLanguage
import com.stats.app.data.model.RemindLevel
import com.stats.app.data.model.ThemeMode
import com.stats.app.data.repository.SettingsRepository
import com.stats.app.reminder.NotificationHelper
import com.stats.app.util.AppLocale
import com.stats.app.util.AppLog
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class SettingsViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val notificationHelper: NotificationHelper,
    private val settingsRepository: SettingsRepository,
    private val backupManager: BackupManager,
) : ViewModel() {

    val dayStartHour: StateFlow<Int> = settingsRepository.dayStartHourFlow
    val themeMode: StateFlow<ThemeMode> = settingsRepository.themeModeFlow
    val appLanguage: StateFlow<AppLanguage> = settingsRepository.appLanguageFlow

    /** 最近一次备份操作的结果（对话框展示，用后清除） */
    private val _operation = MutableStateFlow<String?>(null)
    val operation: StateFlow<String?> = _operation.asStateFlow()

    fun testNotify(level: RemindLevel) {
        notificationHelper.showTest(level)
        AppLog.i("Settings", "测试提醒（$level）已触发")
    }

    fun setDayStartHour(hour: Int) = settingsRepository.setDayStartHour(hour)

    fun setThemeMode(mode: ThemeMode) = settingsRepository.setThemeMode(mode)

    /** 切换界面语言（同步系统级 per-app locale；Activity 重建由界面层负责） */
    fun setAppLanguage(lang: AppLanguage) {
        settingsRepository.setAppLanguage(lang)
        AppLocale.syncSystemLocale(context, lang)
    }

    fun exportBackup(uri: Uri) {
        viewModelScope.launch {
            _operation.value = backupManager.exportTo(uri).fold(
                onSuccess = {
                    AppLocale.string(
                        context, R.string.backup_export_success,
                        it.tasks, it.categories, it.checkIns, it.records,
                    )
                },
                onFailure = { AppLocale.string(context, R.string.backup_export_failed, errText(it)) },
            )
        }
    }

    /** 从应用内快照快速恢复（最近一次导出） */
    fun importFromSnapshot() {
        viewModelScope.launch {
            _operation.value = backupManager.importFromInternalSnapshot().fold(
                onSuccess = {
                    AppLocale.string(
                        context, R.string.backup_snapshot_success,
                        it.tasks, it.categories, it.checkIns, it.records,
                    )
                },
                onFailure = { AppLocale.string(context, R.string.backup_snapshot_failed, errText(it)) },
            )
        }
    }

    fun importBackup(uri: Uri) {
        viewModelScope.launch {
            _operation.value = backupManager.importFrom(uri).fold(
                onSuccess = {
                    AppLocale.string(
                        context, R.string.backup_import_success,
                        it.tasks, it.categories, it.checkIns, it.records,
                    )
                },
                onFailure = { AppLocale.string(context, R.string.backup_import_failed, errText(it)) },
            )
        }
    }

    fun dismissOperation() {
        _operation.value = null
    }

    private fun errText(t: Throwable): String =
        t.message ?: AppLocale.string(context, R.string.common_unknown_error)
}
