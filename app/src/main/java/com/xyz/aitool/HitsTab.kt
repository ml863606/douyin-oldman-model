package com.xyz.aitool

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.xyz.aitool.data.RiskHit

@Composable
fun HitsTab(
    hits: List<RiskHit>,
    onClearHits: () -> Unit,
) {
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            SectionHeader(
                title = "最近命中",
                meta = "${hits.size} 条",
                actionText = "清空",
                onAction = onClearHits,
            )
        }
        if (hits.isEmpty()) {
            item {
                EmptyStateCard("还没有命中记录。开启无障碍服务后，命中的视频会出现在这里。")
            }
        } else {
            items(hits, key = { it.id }) { hit ->
                HitCard(hit = hit)
            }
        }
    }
}
