package com.xyz.aitool

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.xyz.aitool.data.AlertAction
import com.xyz.aitool.data.AlertSize
import com.xyz.aitool.data.CustomRule
import com.xyz.aitool.data.MonitorTarget
import com.xyz.aitool.data.RecognitionMode
import com.xyz.aitool.data.RuleTarget

@Composable
fun RulesTab(
    rulesEnabled: Boolean,
    alertMessage: String,
    alertAction: AlertAction,
    alertSize: AlertSize,
    recognitionMode: RecognitionMode,
    warningFontSize: Int,
    debugModeEnabled: Boolean,
    appSelectorExpanded: Boolean,
    monitorTargets: List<MonitorTarget>,
    customRulesExpanded: Boolean,
    titleRuleText: String,
    tagRuleText: String,
    textRuleText: String,
    customRules: List<CustomRule>,
    onRulesChanged: (Boolean) -> Unit,
    onAlertMessageChanged: (String) -> Unit,
    onAlertActionChanged: (AlertAction) -> Unit,
    onAlertSizeChanged: (AlertSize) -> Unit,
    onRecognitionModeChanged: (RecognitionMode) -> Unit,
    onWarningFontSizeChanged: (Int) -> Unit,
    onDebugModeChanged: (Boolean) -> Unit,
    onToggleAppSelector: () -> Unit,
    onTargetChanged: (MonitorTarget, Boolean) -> Unit,
    onToggleCustomRules: () -> Unit,
    onTitleRuleChanged: (String) -> Unit,
    onTagRuleChanged: (String) -> Unit,
    onTextRuleChanged: (String) -> Unit,
    onAddTitleRule: () -> Unit,
    onAddTagRule: () -> Unit,
    onAddTextRule: () -> Unit,
    onUpdateRule: (Long, RuleTarget, String) -> Unit,
    onRemoveRule: (Long) -> Unit,
) {
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        item {
            AppSelectorCard(
                expanded = appSelectorExpanded,
                targets = monitorTargets,
                onToggleExpanded = onToggleAppSelector,
                onTargetChanged = onTargetChanged,
            )
        }
        item {
            CustomRulesCard(
                expanded = customRulesExpanded,
                titleRuleText = titleRuleText,
                tagRuleText = tagRuleText,
                textRuleText = textRuleText,
                customRules = customRules,
                onToggleExpanded = onToggleCustomRules,
                onTitleRuleChanged = onTitleRuleChanged,
                onTagRuleChanged = onTagRuleChanged,
                onTextRuleChanged = onTextRuleChanged,
                onAddTitleRule = onAddTitleRule,
                onAddTagRule = onAddTagRule,
                onAddTextRule = onAddTextRule,
                onUpdateRule = onUpdateRule,
                onRemoveRule = onRemoveRule,
            )
        }
        item {
            RuleSettingsCard(
                rulesEnabled = rulesEnabled,
                alertMessage = alertMessage,
                alertAction = alertAction,
                alertSize = alertSize,
                recognitionMode = recognitionMode,
                warningFontSize = warningFontSize,
                debugModeEnabled = debugModeEnabled,
                onRulesChanged = onRulesChanged,
                onAlertMessageChanged = onAlertMessageChanged,
                onAlertActionChanged = onAlertActionChanged,
                onAlertSizeChanged = onAlertSizeChanged,
                onRecognitionModeChanged = onRecognitionModeChanged,
                onWarningFontSizeChanged = onWarningFontSizeChanged,
                onDebugModeChanged = onDebugModeChanged,
            )
        }
    }
}

@Composable
private fun RuleSettingsCard(
    rulesEnabled: Boolean,
    alertMessage: String,
    alertAction: AlertAction,
    alertSize: AlertSize,
    recognitionMode: RecognitionMode,
    warningFontSize: Int,
    debugModeEnabled: Boolean,
    onRulesChanged: (Boolean) -> Unit,
    onAlertMessageChanged: (String) -> Unit,
    onAlertActionChanged: (AlertAction) -> Unit,
    onAlertSizeChanged: (AlertSize) -> Unit,
    onRecognitionModeChanged: (RecognitionMode) -> Unit,
    onWarningFontSizeChanged: (Int) -> Unit,
    onDebugModeChanged: (Boolean) -> Unit,
) {
    Card(colors = CardDefaults.cardColors(containerColor = Color.White)) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Text("\u89C4\u5219\u8BBE\u7F6E", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("识别方案", fontWeight = FontWeight.SemiBold)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    RecognitionMode.entries.forEach { mode ->
                        RecognitionModeButton(
                            mode = mode,
                            selected = recognitionMode == mode,
                            modifier = Modifier.weight(1f),
                            onClick = onRecognitionModeChanged,
                        )
                    }
                }
                Text(recognitionMode.description, color = Color(0xFF64748B), style = MaterialTheme.typography.bodySmall)
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column {
                    Text("\u98CE\u9669\u89C4\u5219", fontWeight = FontWeight.SemiBold)
                    Text("\u5173\u95ED\u540E\u4E0D\u4F1A\u5F39\u51FA\u63D0\u9192", color = Color(0xFF64748B), style = MaterialTheme.typography.bodySmall)
                }
                Switch(checked = rulesEnabled, onCheckedChange = onRulesChanged)
            }
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("\u547D\u4E2D\u540E\u7684\u5904\u7406", fontWeight = FontWeight.SemiBold)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    AlertAction.entries.forEach { action ->
                        AlertActionButton(
                            action = action,
                            selected = alertAction == action,
                            modifier = Modifier.weight(1f),
                            onClick = onAlertActionChanged,
                        )
                    }
                }
                Text(alertAction.description, color = Color(0xFF64748B), style = MaterialTheme.typography.bodySmall)
            }
            OutlinedTextField(
                value = alertMessage,
                onValueChange = onAlertMessageChanged,
                modifier = Modifier.fillMaxWidth(),
                label = { Text("\u5F39\u7A97\u63D0\u9192\u6587\u6848") },
                minLines = 2,
                maxLines = 4,
            )
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("\u8B66\u544A\u6846\u5927\u5C0F", fontWeight = FontWeight.SemiBold)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    AlertSize.entries.forEach { size ->
                        AlertSizeButton(
                            size = size,
                            selected = alertSize == size,
                            modifier = Modifier.weight(1f),
                            onClick = onAlertSizeChanged,
                        )
                    }
                }
            }
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text("\u98CE\u9669\u5F39\u7A97\u5B57\u4F53", fontWeight = FontWeight.SemiBold)
                    Text("${warningFontSize}sp", color = Color(0xFFB91C1C), fontWeight = FontWeight.SemiBold)
                }
                Slider(
                    value = warningFontSize.toFloat(),
                    onValueChange = { value -> onWarningFontSizeChanged(value.toInt()) },
                    valueRange = 24f..42f,
                    steps = 17,
                )
                Text("\u9ED8\u8BA4 42sp\uFF0C\u9002\u5408\u7ED9\u5BB6\u4EBA\u505A\u5F3A\u63D0\u9192\u3002", color = Color(0xFF64748B), style = MaterialTheme.typography.bodySmall)
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column {
                    Text("Debug \u6A21\u5F0F", fontWeight = FontWeight.SemiBold)
                    Text("\u5F00\u542F\u540E\u5F39\u7A97\u663E\u793A\u547D\u4E2D\u89C4\u5219\u548C\u624B\u52BF\u6D4B\u8BD5\u6309\u94AE", color = Color(0xFF64748B), style = MaterialTheme.typography.bodySmall)
                }
                Switch(checked = debugModeEnabled, onCheckedChange = onDebugModeChanged)
            }
        }
    }
}
