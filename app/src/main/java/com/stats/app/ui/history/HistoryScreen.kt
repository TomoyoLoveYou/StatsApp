@file:OptIn(ExperimentalMaterial3Api::class)

package com.stats.app.ui.history

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.stats.app.R
import com.stats.app.data.db.entity.PointsCategoryEntity
import com.stats.app.ui.common.formatDateTime

/**
 * 历史任务页：
 *  - 已完成的单次任务（可撤销完成，回到任务列表）；
 *  - 已删除的任务（可恢复，回到任务列表）。
 */
@Composable
fun HistoryScreen(
    onBack: () -> Unit,
    viewModel: HistoryViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }

    // 操作反馈（一次性事件，不会重复弹出）
    LaunchedEffect(Unit) {
        viewModel.messages.collect { m ->
            snackbarHostState.showSnackbar(m)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.history_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.history_back),
                        )
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 8.dp, bottom = 40.dp),
        ) {
            item(key = "hint") {
                Text(
                    text = stringResource(R.string.history_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = 8.dp),
                )
            }

            if (state.loaded && state.completedOneTime.isEmpty() && state.archived.isEmpty()) {
                item(key = "empty") {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 100.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Text(
                            stringResource(R.string.history_empty_title),
                            style = MaterialTheme.typography.titleMedium,
                        )
                        Spacer(Modifier.height(8.dp))
                        Text(
                            text = stringResource(R.string.history_empty_desc),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }

            if (state.completedOneTime.isNotEmpty()) {
                item(key = "header-done") {
                    SectionHeader(stringResource(R.string.history_section_completed, state.completedOneTime.size))
                }
                items(state.completedOneTime, key = { "done-${it.task.id}" }) { item ->
                    val context = LocalContext.current
                    val timeSuffix = item.completedAt?.let { " · " + formatDateTime(context, it) }.orEmpty()
                    val subtitle = stringResource(R.string.history_completed) +
                            timeSuffix +
                            stringResource(R.string.history_points_suffix, item.task.points)
                    HistoryCard(
                        name = item.task.name,
                        category = state.categories[item.task.categoryId],
                        subtitle = subtitle,
                        actionLabel = stringResource(R.string.history_undo_complete),
                        onAction = { viewModel.undoCompleted(item.task) },
                    )
                }
            }

            if (state.archived.isNotEmpty()) {
                item(key = "header-archived") {
                    SectionHeader(stringResource(R.string.history_section_archived, state.archived.size))
                }
                items(state.archived, key = { "arch-${it.task.id}" }) { item ->
                    val context = LocalContext.current
                    val countSuffix = if (item.checkInCount > 0) {
                        stringResource(R.string.history_checkin_count, item.checkInCount)
                    } else {
                        ""
                    }
                    val subtitle = stringResource(
                        R.string.history_archived_at,
                        formatDateTime(context, item.archivedAt),
                    ) + countSuffix
                    HistoryCard(
                        name = item.task.name,
                        category = state.categories[item.task.categoryId],
                        subtitle = subtitle,
                        actionLabel = stringResource(R.string.history_restore_task),
                        onAction = { viewModel.restoreArchived(item.task) },
                    )
                }
            }
        }
    }
}

@Composable
private fun SectionHeader(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(top = 16.dp, bottom = 6.dp),
    )
}

@Composable
private fun HistoryCard(
    name: String,
    category: PointsCategoryEntity?,
    subtitle: String,
    actionLabel: String,
    onAction: () -> Unit,
) {
    val cs = MaterialTheme.colorScheme
    ElevatedCard(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
    ) {
        Row(
            modifier = Modifier.padding(start = 16.dp, end = 6.dp, top = 10.dp, bottom = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                Modifier
                    .size(14.dp)
                    .clip(CircleShape)
                    .background(category?.let { Color(it.color) } ?: cs.outlineVariant),
            )
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(name, style = MaterialTheme.typography.titleSmall)
                Spacer(Modifier.height(2.dp))
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = cs.onSurfaceVariant,
                )
            }
            TextButton(onClick = onAction) { Text(actionLabel) }
        }
    }
}
