package com.stats.app.ui.tasks

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import com.stats.app.data.db.entity.PointsCategoryEntity
import com.stats.app.data.db.entity.TaskEntity
import com.stats.app.data.model.PeriodType
import com.stats.app.data.model.RemindLevel
import com.stats.app.data.model.RemindPlan
import com.stats.app.data.model.TriggerAction
import com.stats.app.data.repository.CategoryRepository
import com.stats.app.data.repository.TaskRepository
import com.stats.app.ui.TaskEditorRoute
import com.stats.app.util.AppLog
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class TaskEditorUiState(
    val loading: Boolean = true,
    val isNew: Boolean = true,
    val name: String = "",
    val note: String = "",
    val periodType: PeriodType = PeriodType.DAILY,
    val periodTarget: String = "1",
    val triggerAction: TriggerAction = TriggerAction.REMIND,
    val remindTime: String = "08:00",
    val remindLevel: RemindLevel = RemindLevel.NOTIFY,
    val vibrate: Boolean = false,
    val weekDays: List<Int> = emptyList(),
    val monthDay: String = "1",
    val yearMonth: String = "1",
    val yearDay: String = "1",
    val categoryId: Long = 0L,
    val points: String = "10",
    val enabled: Boolean = true,
    val categories: List<PointsCategoryEntity> = emptyList(),
    val saved: Boolean = false,
    val deleted: Boolean = false,
    val nameError: Boolean = false,
)

@HiltViewModel
class TaskEditorViewModel @Inject constructor(
    private val taskRepository: TaskRepository,
    private val categoryRepository: CategoryRepository,
    savedStateHandle: SavedStateHandle,
) : ViewModel() {

    private val taskId: Long? = savedStateHandle.toRoute<TaskEditorRoute>().taskId
    private var originalCreatedAt: Long? = null

    private val _uiState = MutableStateFlow(TaskEditorUiState())
    val uiState: StateFlow<TaskEditorUiState> = _uiState.asStateFlow()

    init {
        AppLog.i("TaskEditor", "初始化 taskId=$taskId（${if (taskId == null) "新建" else "编辑"}）")
        viewModelScope.launch {
            val categories = categoryRepository.getAll()
            val existing = taskId?.let { taskRepository.get(it) }
            if (existing != null) {
                originalCreatedAt = existing.createdAt
                val plan = existing.remindPlan
                _uiState.update {
                    it.copy(
                        loading = false,
                        isNew = false,
                        name = existing.name,
                        note = existing.note,
                        periodType = existing.periodType,
                        periodTarget = existing.periodTarget.toString(),
                        triggerAction = existing.triggerAction,
                        remindTime = plan.time,
                        remindLevel = existing.remindLevel,
                        vibrate = existing.vibrate,
                        weekDays = plan.weekDays,
                        monthDay = (plan.monthDay ?: 1).toString(),
                        yearMonth = (plan.yearMonth ?: 1).toString(),
                        yearDay = (plan.yearDay ?: 1).toString(),
                        categoryId = existing.categoryId,
                        points = existing.points.toString(),
                        enabled = existing.enabled,
                        categories = categories,
                    )
                }
                AppLog.i("TaskEditor", "已载入任务 #${existing.id}「${existing.name}」")
            } else {
                _uiState.update {
                    it.copy(
                        loading = false,
                        isNew = true,
                        categories = categories,
                        categoryId = categories.firstOrNull()?.id ?: 0L,
                    )
                }
            }
        }
    }

    fun setName(value: String) = _uiState.update { it.copy(name = value, nameError = false) }

    fun setNote(value: String) = _uiState.update { it.copy(note = value) }

    fun setPeriodType(value: PeriodType) = _uiState.update { it.copy(periodType = value) }

    fun setPeriodTarget(value: String) =
        _uiState.update { it.copy(periodTarget = value.filter { c -> c.isDigit() }.take(2)) }

    fun setTriggerAction(value: TriggerAction) = _uiState.update { it.copy(triggerAction = value) }

    fun setRemindTime(value: String) = _uiState.update { it.copy(remindTime = value) }

    fun setRemindLevel(value: RemindLevel) = _uiState.update { it.copy(remindLevel = value) }

    fun setVibrate(value: Boolean) = _uiState.update { it.copy(vibrate = value) }

    fun toggleWeekDay(day: Int) = _uiState.update { s ->
        val set = s.weekDays.toMutableList()
        if (day in set) set.remove(day) else set.add(day)
        s.copy(weekDays = set.sorted())
    }

    fun setMonthDay(value: String) =
        _uiState.update { it.copy(monthDay = value.filter { c -> c.isDigit() }.take(2)) }

    fun setYearMonth(value: String) =
        _uiState.update { it.copy(yearMonth = value.filter { c -> c.isDigit() }.take(2)) }

    fun setYearDay(value: String) =
        _uiState.update { it.copy(yearDay = value.filter { c -> c.isDigit() }.take(2)) }

    fun setCategory(id: Long) = _uiState.update { it.copy(categoryId = id) }

    fun setPoints(value: String) =
        _uiState.update { it.copy(points = value.filter { c -> c.isDigit() }.take(4)) }

    fun setEnabled(value: Boolean) = _uiState.update { it.copy(enabled = value) }

    /** 新建积分类别并自动选中 */
    fun createCategory(name: String, color: Long) {
        viewModelScope.launch {
            val id = categoryRepository.create(name, color)
            val categories = categoryRepository.getAll()
            _uiState.update { it.copy(categories = categories, categoryId = id) }
            AppLog.i("TaskEditor", "新建积分类别 #$id「$name」并选中")
        }
    }

    fun save() {
        val s = _uiState.value
        if (s.name.isBlank()) {
            AppLog.w("TaskEditor", "保存被拦截：名称为空")
            _uiState.update { it.copy(nameError = true) }
            return
        }
        viewModelScope.launch {
            val isSimple =
                s.periodType == PeriodType.ONE_TIME || s.periodType == PeriodType.STANDING
            val entity = TaskEntity(
                id = taskId ?: 0L,
                name = s.name.trim(),
                note = s.note.trim(),
                periodType = s.periodType,
                periodTarget = if (isSimple) 1 else (s.periodTarget.toIntOrNull()?.coerceAtLeast(1) ?: 1),
                remindPlan = buildRemindPlan(s),
                triggerAction = if (isSimple) TriggerAction.REMIND else s.triggerAction,
                remindLevel = s.remindLevel,
                vibrate = s.vibrate,
                categoryId = s.categoryId,
                points = s.points.toIntOrNull() ?: 0,
                enabled = s.enabled,
                createdAt = originalCreatedAt ?: System.currentTimeMillis(),
            )
            if (taskId == null) {
                taskRepository.create(entity)
            } else {
                taskRepository.update(entity)
            }
            AppLog.i("TaskEditor", "保存完成「${entity.name}」isNew=${taskId == null} 分值=${entity.points}")
            _uiState.update { it.copy(saved = true) }
        }
    }

    /** 按周期类型组装提醒计划（只存与当前周期相关的字段） */
    private fun buildRemindPlan(s: TaskEditorUiState): RemindPlan = when (s.periodType) {
        PeriodType.DAILY -> RemindPlan(time = s.remindTime)

        // 单次：每天提醒直到完成；常驻：不提醒（时间仅作占位）
        PeriodType.ONE_TIME, PeriodType.STANDING -> RemindPlan(time = s.remindTime)
        PeriodType.WEEKLY -> RemindPlan(time = s.remindTime, weekDays = s.weekDays)
        PeriodType.MONTHLY -> RemindPlan(
            time = s.remindTime,
            monthDay = s.monthDay.toIntOrNull()?.coerceIn(1, 31) ?: 1,
        )
        PeriodType.YEARLY -> RemindPlan(
            time = s.remindTime,
            yearMonth = s.yearMonth.toIntOrNull()?.coerceIn(1, 12) ?: 1,
            yearDay = s.yearDay.toIntOrNull()?.coerceIn(1, 31) ?: 1,
        )
    }

    fun delete() {
        val id = taskId ?: return
        viewModelScope.launch {
            taskRepository.get(id)?.let { taskRepository.archive(it) }
            AppLog.i("TaskEditor", "已删除任务 #$id（可在 设置→历史任务 找回）")
            _uiState.update { it.copy(deleted = true) }
        }
    }
}
