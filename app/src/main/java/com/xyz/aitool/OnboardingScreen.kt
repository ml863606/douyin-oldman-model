package com.xyz.aitool

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

@Composable
fun OnboardingScreen(
    accessibilityEnabled: Boolean,
    batteryOptimized: Boolean,
    captureRunning: Boolean,
    onOpenAccessibility: () -> Unit,
    onOpenBatterySettings: () -> Unit,
    onStartCapture: () -> Unit,
    onFinish: () -> Unit,
) {
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            Card(
                shape = AppCardShape,
                colors = CardDefaults.cardColors(containerColor = AppDeep),
                elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
            ) {
                Column(
                    modifier = Modifier.padding(20.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Text(
                        text = "\u5148\u628A\u5B88\u62A4\u8BBE\u7F6E\u597D",
                        style = MaterialTheme.typography.headlineSmall,
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                    )
                    Text(
                        text = "\u8FD9\u51E0\u9879\u4F1A\u5F71\u54CD\u8BC6\u522B\u3001\u63D0\u9192\u548C\u540E\u53F0\u7A33\u5B9A\u6027\u3002\u8BBE\u7F6E\u597D\u4E4B\u540E\uFF0C\u518D\u4EA4\u7ED9\u5BB6\u4EBA\u4F7F\u7528\u4F1A\u7701\u5FC3\u5F88\u591A\u3002",
                        color = Color(0xFFCFE2DC),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            }
        }
        item {
            GuideStepCard(
                index = "1",
                title = "\u5F00\u542F\u65E0\u969C\u788D",
                description = "\u7528\u4E8E\u8BFB\u53D6\u5DF2\u9009\u62E9 App \u7684\u5C4F\u5E55\u6587\u5B57\uFF0C\u5E76\u5728\u547D\u4E2D\u98CE\u9669\u5185\u5BB9\u65F6\u663E\u793A\u5927\u53F7\u63D0\u9192\u3002",
                done = accessibilityEnabled,
                doneText = "\u5DF2\u5F00\u542F",
                actionText = if (accessibilityEnabled) "\u67E5\u770B\u8BBE\u7F6E" else "\u53BB\u5F00\u542F",
                onAction = onOpenAccessibility,
            )
        }
        item {
            GuideStepCard(
                index = "2",
                title = "\u5173\u95ED\u7535\u6C60\u4F18\u5316",
                description = "\u51CF\u5C11\u7CFB\u7EDF\u5728\u540E\u53F0\u6E05\u7406\u670D\u52A1\u7684\u6982\u7387\uFF0C\u8FDE\u7EED\u5237\u89C6\u9891\u65F6\u66F4\u7A33\u5B9A\u3002",
                done = !batteryOptimized,
                doneText = "\u5DF2\u653E\u884C",
                actionText = if (batteryOptimized) "\u53BB\u8BBE\u7F6E" else "\u67E5\u770B\u8BBE\u7F6E",
                onAction = onOpenBatterySettings,
            )
        }
        item {
            GuideStepCard(
                index = "3",
                title = "\u6388\u6743\u622A\u56FE\u8BC6\u522B",
                description = "\u65E0\u969C\u788D\u6587\u5B57\u4E0D\u8DB3\u65F6\uFF0C\u7528\u622A\u56FE OCR \u515C\u5E95\u8BC6\u522B\u6807\u9898\u3001\u5185\u5BB9\u548C\u6807\u7B7E\u3002",
                done = captureRunning,
                doneText = "\u5DF2\u6388\u6743",
                actionText = if (captureRunning) "\u91CD\u65B0\u6388\u6743" else "\u53BB\u6388\u6743",
                onAction = onStartCapture,
            )
        }
        item {
            Button(
                modifier = Modifier.fillMaxWidth(),
                shape = AppControlShape,
                onClick = onFinish,
            ) {
                Text("\u8FDB\u5165\u4E3B\u9875")
            }
        }
    }
}

@Composable
private fun GuideStepCard(
    index: String,
    title: String,
    description: String,
    done: Boolean,
    doneText: String,
    actionText: String,
    onAction: () -> Unit,
) {
    Card(
        shape = AppCardShape,
        colors = CardDefaults.cardColors(containerColor = AppSurface),
        border = BorderStroke(1.dp, AppLine),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
    ) {
        Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Row(
                    modifier = Modifier.weight(1f),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(
                        modifier = Modifier,
                        contentAlignment = Alignment.Center,
                    ) {
                        InfoChip(
                            text = index,
                            contentColor = if (done) AppAccent else AppDanger,
                            containerColor = if (done) AppAccentSoft else AppDangerSoft,
                        )
                    }
                    Column {
                        Text(title, fontWeight = FontWeight.SemiBold, color = AppInk)
                        Text(
                            if (done) doneText else "\u5F85\u8BBE\u7F6E",
                            color = if (done) AppAccent else AppDanger,
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                }
                OutlinedButton(
                    shape = AppControlShape,
                    border = BorderStroke(1.dp, AppLine),
                    onClick = onAction,
                ) {
                    Text(actionText)
                }
            }
            Text(description, color = AppMuted, style = MaterialTheme.typography.bodyMedium)
        }
    }
}
