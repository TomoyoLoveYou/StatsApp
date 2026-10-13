@file:OptIn(ExperimentalLayoutApi::class)

package com.stats.app.ui.common

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import com.stats.app.R

/**
 * 积分类别新建 / 编辑对话框（任务编辑器与类别管理页共用）。
 * 名称 + 色板选色；[usedColors] 传入**其他类别**已使用的颜色，这些颜色不显示在候选中
 * （保证不同类别颜色不重复）；编辑时传入的颜色若等于本类别当前色则保留显示。
 */
@Composable
fun CategoryEditDialog(
    title: String,
    initialName: String = "",
    initialColor: Long? = null,
    usedColors: Set<Long> = emptySet(),
    onConfirm: (name: String, color: Long) -> Unit,
    onDismiss: () -> Unit,
) {
    // 候选色：剔除其他类别已占用的颜色；编辑时保留自己当前的颜色
    val options = remember(usedColors, initialColor) {
        CategoryPalette.filter { it !in usedColors || it == initialColor }
    }
    var name by remember { mutableStateOf(initialName) }
    var color by remember { mutableStateOf(initialColor ?: options.firstOrNull()) }
    val cs = MaterialTheme.colorScheme

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text(stringResource(R.string.dlg_category_name)) },
                    placeholder = { Text(stringResource(R.string.dlg_category_name_hint)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(16.dp))
                Text(
                    text = stringResource(R.string.dlg_color),
                    style = MaterialTheme.typography.labelLarge,
                    color = cs.primary,
                )
                Text(
                    text = stringResource(R.string.dlg_color_used_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = cs.onSurfaceVariant,
                )
                Spacer(Modifier.height(10.dp))
                if (options.isEmpty()) {
                    Text(
                        text = stringResource(R.string.dlg_color_exhausted),
                        style = MaterialTheme.typography.bodySmall,
                        color = cs.error,
                    )
                } else {
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        options.forEach { value ->
                            val selected = value == color
                            Box(
                                modifier = Modifier
                                    .size(38.dp)
                                    .clip(CircleShape)
                                    .background(Color(value))
                                    .then(
                                        if (selected) {
                                            Modifier.border(3.dp, cs.onSurface, CircleShape)
                                        } else {
                                            Modifier
                                        }
                                    )
                                    .clickable { color = value },
                                contentAlignment = Alignment.Center,
                            ) {
                                if (selected) {
                                    Icon(
                                        imageVector = Icons.Filled.Check,
                                        contentDescription = null,
                                        tint = Color.White,
                                        modifier = Modifier.size(18.dp),
                                    )
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = { color?.let { c -> onConfirm(name.trim(), c) } },
                enabled = name.isNotBlank() && color != null,
            ) { Text(stringResource(R.string.common_confirm)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.common_cancel)) }
        },
    )
}
