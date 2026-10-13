@file:OptIn(ExperimentalMaterial3Api::class)

package com.stats.app.ui.categories

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
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.stats.app.R
import com.stats.app.data.db.entity.PointsCategoryEntity
import com.stats.app.ui.common.CategoryEditDialog

@Composable
fun CategoriesScreen(
    onBack: () -> Unit,
    viewModel: CategoriesViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    var showNew by remember { mutableStateOf(false) }
    var editTarget by remember { mutableStateOf<PointsCategoryEntity?>(null) }
    var deleteTarget by remember { mutableStateOf<PointsCategoryEntity?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.cat_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.cat_back),
                        )
                    }
                },
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = { showNew = true }) {
                Icon(Icons.Filled.Add, contentDescription = stringResource(R.string.cat_new))
            }
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 8.dp, bottom = 100.dp),
        ) {
            item(key = "hint") {
                Text(
                    text = stringResource(R.string.cat_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = 8.dp),
                )
            }
            items(state.rows, key = { it.category.id }) { row ->
                CategoryRowCard(
                    row = row,
                    onEdit = { editTarget = row.category },
                    onDelete = { deleteTarget = row.category },
                )
            }
        }
    }

    if (showNew) {
        CategoryEditDialog(
            title = stringResource(R.string.cat_new_title),
            usedColors = state.rows.map { it.category.color }.toSet(),
            onConfirm = { name, color ->
                viewModel.create(name, color)
                showNew = false
            },
            onDismiss = { showNew = false },
        )
    }

    editTarget?.let { cat ->
        CategoryEditDialog(
            title = stringResource(R.string.cat_edit_title),
            initialName = cat.name,
            initialColor = cat.color,
            usedColors = state.rows
                .filter { it.category.id != cat.id }
                .map { it.category.color }
                .toSet(),
            onConfirm = { name, color ->
                viewModel.update(cat, name, color)
                editTarget = null
            },
            onDismiss = { editTarget = null },
        )
    }

    deleteTarget?.let { cat ->
        val count = state.rows.firstOrNull { it.category.id == cat.id }?.taskCount ?: 0
        AlertDialog(
            onDismissRequest = { deleteTarget = null },
            title = { Text(stringResource(R.string.cat_delete_title)) },
            text = {
                Text(
                    if (count > 0) {
                        stringResource(R.string.cat_delete_in_use, count, cat.name)
                    } else {
                        stringResource(R.string.cat_delete_confirm, cat.name)
                    }
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.delete(cat)
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
}

@Composable
private fun CategoryRowCard(
    row: CategoryRow,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
) {
    val cat = row.category
    val cs = MaterialTheme.colorScheme
    ElevatedCard(
        onClick = onEdit,
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
                    .background(Color(cat.color)),
            )
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(cat.name, style = MaterialTheme.typography.titleSmall)
                Spacer(Modifier.height(2.dp))
                Text(
                    text = if (row.taskCount > 0) {
                        stringResource(R.string.cat_task_count, row.taskCount)
                    } else {
                        stringResource(R.string.cat_unused)
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = cs.onSurfaceVariant,
                )
            }
            IconButton(onClick = onDelete) {
                Icon(
                    imageVector = Icons.Filled.Delete,
                    contentDescription = stringResource(R.string.cat_delete_title),
                    tint = cs.onSurfaceVariant,
                )
            }
        }
    }
}
