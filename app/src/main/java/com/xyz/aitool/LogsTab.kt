package com.xyz.aitool

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.xyz.aitool.data.OperationLog
import com.xyz.aitool.data.ParsedVideoLog

@Composable
fun LogsTab(
    videoLogs: List<ParsedVideoLog>,
    operationLogs: List<OperationLog>,
    onClearAllLogs: () -> Unit,
    onClearVideoLogs: () -> Unit,
    onClearOperationLogs: () -> Unit,
) {
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        item {
            OutlinedButton(
                modifier = Modifier.fillMaxWidth(),
                onClick = onClearAllLogs,
            ) {
                Text("清空记录日志")
            }
        }
        item {
            SectionHeader(
                title = "解析日志",
                meta = "${videoLogs.size} 条，实时刷新",
                actionText = "清空",
                onAction = onClearVideoLogs,
            )
        }
        if (videoLogs.isEmpty()) {
            item {
                EmptyStateCard("还没有解析日志。打开已选择的 App 后，新视频会记录标题、内容和标签。")
            }
        } else {
            items(videoLogs, key = { it.id }) { log ->
                VideoLogCard(log = log)
            }
        }
        item {
            SectionHeader(
                title = "操作日志",
                meta = "${operationLogs.size} 条，实时刷新",
                actionText = "清空",
                onAction = onClearOperationLogs,
            )
        }
        if (operationLogs.isEmpty()) {
            item {
                EmptyStateCard("还没有操作日志。触发自动跳过或不感兴趣后，每一步会记录在这里。")
            }
        } else {
            items(operationLogs, key = { it.id }) { log ->
                OperationLogCard(log = log)
            }
        }
    }
}
