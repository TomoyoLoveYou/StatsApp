package com.stats.app.data.backup

import android.content.Context
import android.net.Uri
import androidx.room.withTransaction
import com.stats.app.R
import com.stats.app.data.db.AppDatabase
import com.stats.app.data.db.dao.CheckInDao
import com.stats.app.data.db.dao.PointsCategoryDao
import com.stats.app.data.db.dao.PointsRecordDao
import com.stats.app.data.db.dao.TaskDao
import com.stats.app.data.db.entity.CheckInEntity
import com.stats.app.data.db.entity.PointsCategoryEntity
import com.stats.app.data.db.entity.PointsRecordEntity
import com.stats.app.data.db.entity.TaskEntity
import com.stats.app.reminder.ReminderScheduler
import com.stats.app.util.AppLocale
import com.stats.app.util.AppLog
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/** 备份文件结构（schemaVersion 用于将来兼容校验；内容为 JSON，文件名建议 .txt 提升 SAF 兼容性） */
@Serializable
data class BackupFile(
    val schemaVersion: Int = 1,
    val exportedAt: Long,
    val tasks: List<TaskEntity>,
    val categories: List<PointsCategoryEntity>,
    val checkIns: List<CheckInEntity>,
    val records: List<PointsRecordEntity>,
)

data class BackupSummary(val tasks: Int, val categories: Int, val checkIns: Int, val records: Int)

/**
 * 数据备份：导出到 SAF URI / 从 SAF URI 导入恢复 / 从应用内快照快速恢复。
 *
 * 安全设计：
 *  - 导出的同时写一份内部快照 files/last_export.json（供"快速恢复"使用）；
 *  - 导入前自动把当前数据快照到 files/before_import.json（导错了可兜底）；
 *  - 导入为单事务整体替换，完成后重注册全部提醒。
 */
@Singleton
class BackupManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val database: AppDatabase,
    private val taskDao: TaskDao,
    private val checkInDao: CheckInDao,
    private val categoryDao: PointsCategoryDao,
    private val recordDao: PointsRecordDao,
    private val scheduler: ReminderScheduler,
) {

    private val json = Json { encodeDefaults = true }

    /** 导出全部数据到所选位置 */
    suspend fun exportTo(uri: Uri): Result<BackupSummary> = withContext(Dispatchers.IO) {
        runCatching {
            val backup = collect()
            val text = json.encodeToString(BackupFile.serializer(), backup)
            File(context.filesDir, SNAPSHOT_NAME).writeText(text)
            context.contentResolver.openOutputStream(uri)?.use { out ->
                out.write(text.toByteArray(Charsets.UTF_8))
            } ?: error(AppLocale.string(context, R.string.backup_error_write))
            AppLog.i(
                "Backup",
                "导出完成 tasks=${backup.tasks.size} categories=${backup.categories.size} " +
                        "checkIns=${backup.checkIns.size} records=${backup.records.size}"
            )
            summary(backup)
        }.onFailure { AppLog.e("Backup", "导出失败", it) }
    }

    /** 从所选文件导入恢复（覆盖当前数据） */
    suspend fun importFrom(uri: Uri): Result<BackupSummary> = withContext(Dispatchers.IO) {
        runCatching {
            val text = context.contentResolver.openInputStream(uri)?.use { input ->
                input.readBytes().toString(Charsets.UTF_8)
            } ?: error(AppLocale.string(context, R.string.backup_error_read))
            applyImport(text)
        }.onFailure { AppLog.e("Backup", "导入失败", it) }
    }

    /** 从应用内快照快速恢复（最近一次导出，无需选择文件） */
    suspend fun importFromInternalSnapshot(): Result<BackupSummary> = withContext(Dispatchers.IO) {
        runCatching {
            val file = File(context.filesDir, SNAPSHOT_NAME)
            require(file.exists()) { AppLocale.string(context, R.string.backup_error_no_snapshot) }
            applyImport(file.readText())
        }.onFailure { AppLog.e("Backup", "快速恢复失败", it) }
    }

    // ---- 内部 ----

    /** 导入核心：解析校验 → 快照当前数据 → 单事务替换 → 重注册提醒 */
    private suspend fun applyImport(text: String): BackupSummary {
        val backup = json.decodeFromString(BackupFile.serializer(), text)
        require(backup.schemaVersion in 1..SCHEMA_VERSION) {
            AppLocale.string(context, R.string.backup_error_unsupported_version, backup.schemaVersion)
        }

        // 导入前快照当前数据（误导入时的兜底）
        val current = collect()
        File(context.filesDir, BEFORE_IMPORT_NAME).writeText(
            json.encodeToString(BackupFile.serializer(), current)
        )

        database.withTransaction {
            // 先删引用方，再删被引用方
            recordDao.deleteAll()
            checkInDao.deleteAll()
            taskDao.deleteAll()
            if (backup.categories.isNotEmpty()) {
                categoryDao.deleteAll()
                backup.categories.forEach { categoryDao.insert(it) }
            }
            backup.tasks.forEach { taskDao.insert(it) }
            backup.checkIns.forEach { checkInDao.insert(it) }
            backup.records.forEach { recordDao.insert(it) }
        }
        scheduler.rescheduleAll()
        AppLog.i(
            "Backup",
            "导入完成 tasks=${backup.tasks.size} categories=${backup.categories.size} " +
                    "checkIns=${backup.checkIns.size} records=${backup.records.size}"
        )
        return summary(backup)
    }

    private suspend fun collect() = BackupFile(
        exportedAt = System.currentTimeMillis(),
        tasks = taskDao.getAllOnce(),
        categories = categoryDao.getAll(),
        checkIns = checkInDao.getAllOnce(),
        records = recordDao.getAllOnce(),
    )

    private fun summary(backup: BackupFile) = BackupSummary(
        tasks = backup.tasks.size,
        categories = backup.categories.size,
        checkIns = backup.checkIns.size,
        records = backup.records.size,
    )

    private companion object {
        const val SCHEMA_VERSION = 1
        const val SNAPSHOT_NAME = "last_export.json"
        const val BEFORE_IMPORT_NAME = "before_import.json"
    }
}
