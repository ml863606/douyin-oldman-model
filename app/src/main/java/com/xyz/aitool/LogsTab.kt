package com.xyz.aitool

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.xyz.aitool.data.OperationLog
import com.xyz.aitool.data.ParsedVideoLog

@Composable
fun LogsTab(
    videoLogs: List<ParsedVideoLog>,
    operationLogs: List<OperationLog>,
    videoLogsExpanded: Boolean,
    operationLogsExpanded: Boolean,
    onToggleVideoLogs: () -> Unit,
    onToggleOperationLogs: () -> Unit,
    onClearAllLogs: () -> Unit,
    onClearVideoLogs: () -> Unit,
    onClearOperationLogs: () -> Unit,
) {
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            OutlinedButton(
                modifier = Modifier.fillMaxWidth(),
                shape = AppControlShape,
                border = BorderStroke(1.dp, AppLine),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = AppDanger),
                onClick = onClearAllLogs,
            ) {
                Text("清空记录日志")
            }
        }
        item {
            CollapsibleLogHeader(
                title = "解析日志",
                meta = "${videoLogs.size} 条，实时刷新",
                expanded = videoLogsExpanded,
                onToggle = onToggleVideoLogs,
                onClear = onClearVideoLogs,
            )
        }
        if (videoLogsExpanded && videoLogs.isEmpty()) {
            item {
                EmptyStateCard("还没有解析日志。打开已选择的 App 后，新视频会记录标题、内容和标签。")
            }
        } else if (videoLogsExpanded) {
            items(videoLogs, key = { it.id }) { log ->
                VideoLogCard(log = log)
            }
        }
        item {
            CollapsibleLogHeader(
                title = "操作日志",
                meta = "${operationLogs.size} 条，实时刷新",
                expanded = operationLogsExpanded,
                onToggle = onToggleOperationLogs,
                onClear = onClearOperationLogs,
            )
        }
        if (operationLogsExpanded && operationLogs.isEmpty()) {
            item {
                EmptyStateCard("还没有操作日志。触发自动跳过或不感兴趣后，每一步会记录在这里。")
            }
        } else if (operationLogsExpanded) {
            items(operationLogs, key = { it.id }) { log ->
                OperationLogCard(log = log)
            }
        }
    }
}

@Composable
private fun CollapsibleLogHeader(
    title: String,
    meta: String,
    expanded: Boolean,
    onToggle: () -> Unit,
    onClear: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = AppInk)
            Text(meta, color = AppMuted, style = MaterialTheme.typography.bodySmall)
        }
        TextButton(onClick = onClear) {
            Text("清空", color = AppDanger)
        }
        TextButton(onClick = onToggle) {
            Text(if (expanded) "收起" else "展开", color = AppAccent)
        }
    }
}
