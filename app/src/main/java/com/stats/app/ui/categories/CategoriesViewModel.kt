package com.stats.app.ui.categories

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.stats.app.data.db.entity.PointsCategoryEntity
import com.stats.app.data.repository.CategoryRepository
import com.stats.app.data.repository.TaskRepository
import com.stats.app.util.AppLog
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

data class CategoryRow(
    val category: PointsCategoryEntity,
    val taskCount: Int,
)

data class CategoriesUiState(
    val loaded: Boolean = false,
    val rows: List<CategoryRow> = emptyList(),
)

@HiltViewModel
class CategoriesViewModel @Inject constructor(
    private val categoryRepository: CategoryRepository,
    private val taskRepository: TaskRepository,
) : ViewModel() {

    val uiState: StateFlow<CategoriesUiState> = combine(
        categoryRepository.observeAll(),
        taskRepository.observeAll(),
    ) { categories, tasks ->
        CategoriesUiState(
            loaded = true,
            rows = categories.map { cat ->
                CategoryRow(category = cat, taskCount = tasks.count { it.categoryId == cat.id })
            },
        )
    }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = CategoriesUiState(),
        )

    fun create(name: String, color: Long) {
        viewModelScope.launch {
            categoryRepository.create(name, color)
        }
    }

    fun update(category: PointsCategoryEntity, name: String, color: Long) {
        viewModelScope.launch {
            categoryRepository.update(category.copy(name = name, color = color))
            AppLog.i("Categories", "更新类别 #${category.id} →「$name」")
        }
    }

    fun delete(category: PointsCategoryEntity) {
        viewModelScope.launch {
            categoryRepository.delete(category)
            AppLog.i("Categories", "删除类别 #${category.id}「${category.name}」")
        }
    }
}
