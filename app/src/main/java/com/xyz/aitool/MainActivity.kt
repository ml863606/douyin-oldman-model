package com.xyz.aitool

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.projection.MediaProjectionManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import android.provider.Settings
import android.text.format.DateFormat
import android.util.Base64
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.core.graphics.drawable.toBitmap
import java.io.ByteArrayOutputStream
import com.xyz.aitool.capture.ScreenCaptureService
import com.xyz.aitool.data.AlertAction
import com.xyz.aitool.data.AlertSize
import com.xyz.aitool.data.CustomRule
import com.xyz.aitool.data.HitRepository
import com.xyz.aitool.data.MonitorTarget
import com.xyz.aitool.data.OperationLog
import com.xyz.aitool.data.ParsedVideoLog
import com.xyz.aitool.data.RiskHit
import com.xyz.aitool.data.RuleTarget
import kotlinx.coroutines.delay

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme(
                colorScheme = lightColorScheme(
                    primary = Color(0xFF2563EB),
                    secondary = Color(0xFF0F766E),
                    background = Color(0xFFF6F7FB),
                    surface = Color.White,
                ),
            ) {
                Surface(modifier = Modifier.fillMaxSize()) {
                    AppScreen()
                }
            }
        }
    }
}

@Composable
private fun AppScreen() {
    val context = LocalContext.current
    var rulesEnabled by remember { mutableStateOf(HitRepository.areRulesEnabled(context)) }
    var hits by remember { mutableStateOf(HitRepository.getHits(context)) }
    var videoLogs by remember { mutableStateOf(HitRepository.getVideoLogs(context)) }
    var operationLogs by remember { mutableStateOf(HitRepository.getOperationLogs(context)) }
    var customRules by remember { mutableStateOf(HitRepository.getCustomRules(context)) }
    var monitorTargets by remember { mutableStateOf(context.loadMonitorTargets()) }
    var accessibilityEnabled by remember { mutableStateOf(context.isAccessibilityEnabled()) }
    var captureRunning by remember { mutableStateOf(ScreenCaptureService.isRunning()) }
    var alertMessage by remember { mutableStateOf(HitRepository.getAlertMessage(context)) }
    var alertAction by remember { mutableStateOf(HitRepository.getAlertAction(context)) }
    var alertSize by remember { mutableStateOf(HitRepository.getAlertSize(context)) }
    var warningFontSize by remember { mutableStateOf(HitRepository.getWarningFontSize(context)) }
    var debugModeEnabled by remember { mutableStateOf(HitRepository.isDebugModeEnabled(context)) }
    var onboardingVisible by remember { mutableStateOf(!HitRepository.isOnboardingCompleted(context)) }
    var batteryOptimized by remember { mutableStateOf(!context.isIgnoringBatteryOptimizations()) }
    var titleRuleText by remember { mutableStateOf("") }
    var tagRuleText by remember { mutableStateOf("") }
    var textRuleText by remember { mutableStateOf("") }
    var appSelectorExpanded by remember { mutableStateOf(false) }
    var customRulesExpanded by remember { mutableStateOf(false) }
    var selectedTab by remember { mutableStateOf(0) }
    val lifecycleOwner = LocalLifecycleOwner.current

    fun refreshState() {
        accessibilityEnabled = context.isAccessibilityEnabled()
        captureRunning = ScreenCaptureService.isRunning()
        hits = HitRepository.getHits(context)
        videoLogs = HitRepository.getVideoLogs(context)
        operationLogs = HitRepository.getOperationLogs(context)
        customRules = HitRepository.getCustomRules(context)
        monitorTargets = context.loadMonitorTargets()
        alertMessage = HitRepository.getAlertMessage(context)
        alertAction = HitRepository.getAlertAction(context)
        alertSize = HitRepository.getAlertSize(context)
        warningFontSize = HitRepository.getWarningFontSize(context)
        debugModeEnabled = HitRepository.isDebugModeEnabled(context)
        batteryOptimized = !context.isIgnoringBatteryOptimizations()
    }

    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { }

    val screenCaptureLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult(),
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK && result.data != null) {
            ScreenCaptureService.start(context, result.resultCode, result.data!!)
            captureRunning = true
        }
    }

    fun openAccessibilitySettings() {
        context.startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
    }

    fun openBatteryOptimizationSettings() {
        val packageUri = Uri.parse("package:${context.packageName}")
        val requestIntent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, packageUri)
        val fallbackIntent = Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)
        runCatching { context.startActivity(requestIntent) }
            .recoverCatching { context.startActivity(fallbackIntent) }
            .onFailure {
                context.startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, packageUri))
            }
    }

    fun requestScreenCapture() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
        val manager = context.getSystemService(MediaProjectionManager::class.java)
        screenCaptureLauncher.launch(manager.createScreenCaptureIntent())
    }

    LaunchedEffect(Unit) {
        refreshState()
        while (true) {
            delay(1_000)
            hits = HitRepository.getHits(context)
            videoLogs = HitRepository.getVideoLogs(context)
            operationLogs = HitRepository.getOperationLogs(context)
            customRules = HitRepository.getCustomRules(context)
            monitorTargets = context.loadMonitorTargets()
            alertAction = HitRepository.getAlertAction(context)
            alertSize = HitRepository.getAlertSize(context)
            warningFontSize = HitRepository.getWarningFontSize(context)
            debugModeEnabled = HitRepository.isDebugModeEnabled(context)
            batteryOptimized = !context.isIgnoringBatteryOptimizations()
        }
    }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                refreshState()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    if (onboardingVisible) {
        OnboardingScreen(
            accessibilityEnabled = accessibilityEnabled,
            batteryOptimized = batteryOptimized,
            captureRunning = captureRunning,
            onOpenAccessibility = ::openAccessibilitySettings,
            onOpenBatterySettings = ::openBatteryOptimizationSettings,
            onStartCapture = ::requestScreenCapture,
            onFinish = {
                HitRepository.setOnboardingCompleted(context, true)
                onboardingVisible = false
            },
        )
        return
    }

    val tabs = listOf(
        BottomTab("运行状态", R.drawable.ic_tab_status),
        BottomTab("规则设置", R.drawable.ic_tab_rules),
        BottomTab("最近命中", R.drawable.ic_tab_hits),
        BottomTab("记录日志", R.drawable.ic_tab_logs),
    )

    Scaffold(
        containerColor = Color(0xFFF6F7FB),
        bottomBar = {
            NavigationBar(containerColor = Color.White) {
                tabs.forEachIndexed { index, tab ->
                    NavigationBarItem(
                        selected = selectedTab == index,
                        onClick = { selectedTab = index },
                        icon = {
                            androidx.compose.material3.Icon(
                                painter = painterResource(tab.iconRes),
                                contentDescription = tab.title,
                            )
                        },
                        label = {
                            Text(
                                text = tab.title,
                                maxLines = 1,
                                softWrap = false,
                                fontSize = 12.sp,
                            )
                        },
                    )
                }
            }
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            when (selectedTab) {
                0 -> StatusTab(
                    accessibilityEnabled = accessibilityEnabled,
                    captureRunning = captureRunning,
                    rulesEnabled = rulesEnabled,
                    alertAction = alertAction,
                    selectedAppCount = monitorTargets.count { it.selected },
                    hitCount = hits.size,
                    logCount = videoLogs.size + operationLogs.size,
                    onOpenAccessibility = { openAccessibilitySettings() },
                    onStartCapture = { requestScreenCapture() },
                    onStopCapture = {
                        ScreenCaptureService.stop(context)
                        captureRunning = false
                    },
                    onOpenOnboarding = { onboardingVisible = true },
                )
                1 -> RulesTab(
                    rulesEnabled = rulesEnabled,
                    alertMessage = alertMessage,
                    alertAction = alertAction,
                    alertSize = alertSize,
                    warningFontSize = warningFontSize,
                    debugModeEnabled = debugModeEnabled,
                    appSelectorExpanded = appSelectorExpanded,
                    monitorTargets = monitorTargets,
                    customRulesExpanded = customRulesExpanded,
                    titleRuleText = titleRuleText,
                    tagRuleText = tagRuleText,
                    textRuleText = textRuleText,
                    customRules = customRules,
                    onRulesChanged = { enabled ->
                        rulesEnabled = enabled
                        HitRepository.setRulesEnabled(context, enabled)
                    },
                    onAlertMessageChanged = { message ->
                        alertMessage = message
                        HitRepository.setAlertMessage(context, message)
                    },
                    onAlertActionChanged = { action ->
                        alertAction = action
                        HitRepository.setAlertAction(context, action)
                    },
                    onAlertSizeChanged = { size ->
                        alertSize = size
                        HitRepository.setAlertSize(context, size)
                    },
                    onWarningFontSizeChanged = { size ->
                        warningFontSize = size
                        HitRepository.setWarningFontSize(context, size)
                    },
                    onDebugModeChanged = { enabled ->
                        debugModeEnabled = enabled
                        HitRepository.setDebugModeEnabled(context, enabled)
                    },
                    onToggleAppSelector = { appSelectorExpanded = !appSelectorExpanded },
                    onTargetChanged = { target, selected ->
                        HitRepository.setPackageMonitored(
                            context = context,
                            packageName = target.packageName,
                            selected = selected,
                            label = target.label,
                            iconBase64 = context.encodeAppIcon(target.packageName),
                        )
                        monitorTargets = context.loadMonitorTargets()
                    },
                    onToggleCustomRules = { customRulesExpanded = !customRulesExpanded },
                    onTitleRuleChanged = { titleRuleText = it },
                    onTagRuleChanged = { tagRuleText = it },
                    onTextRuleChanged = { textRuleText = it },
                    onAddTitleRule = {
                        HitRepository.addCustomRule(context, RuleTarget.TITLE, titleRuleText)
                        titleRuleText = ""
                        customRules = HitRepository.getCustomRules(context)
                    },
                    onAddTagRule = {
                        HitRepository.addCustomRule(context, RuleTarget.TAG, tagRuleText)
                        tagRuleText = ""
                        customRules = HitRepository.getCustomRules(context)
                    },
                    onAddTextRule = {
                        HitRepository.addCustomRule(context, RuleTarget.TEXT, textRuleText)
                        textRuleText = ""
                        customRules = HitRepository.getCustomRules(context)
                    },
                    onRemoveRule = { id ->
                        HitRepository.removeCustomRule(context, id)
                        customRules = HitRepository.getCustomRules(context)
                    },
                )
                2 -> HitsTab(
                    hits = hits,
                    onClearHits = {
                        HitRepository.clearHits(context)
                        hits = emptyList()
                    },
                )
                3 -> LogsTab(
                    videoLogs = videoLogs,
                    operationLogs = operationLogs,
                    onClearAllLogs = {
                        HitRepository.clearRecordLogs(context)
                        hits = emptyList()
                        videoLogs = emptyList()
                        operationLogs = emptyList()
                    },
                    onClearVideoLogs = {
                        HitRepository.clearVideoLogs(context)
                        videoLogs = emptyList()
                    },
                    onClearOperationLogs = {
                        HitRepository.clearOperationLogs(context)
                        operationLogs = emptyList()
                    },
                )
            }
        }
    }
}

private data class BottomTab(
    val title: String,
    val iconRes: Int,
)

@Composable
fun HeaderPanel(
    accessibilityEnabled: Boolean,
    captureRunning: Boolean,
    rulesEnabled: Boolean,
    alertAction: AlertAction,
    selectedAppCount: Int,
    hitCount: Int,
    logCount: Int,
    modifier: Modifier = Modifier,
) {
    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(containerColor = Color(0xFF0F172A)),
    ) {
        Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    text = "AI视频提醒助手",
                    style = MaterialTheme.typography.headlineSmall,
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    text = "当前模式：${alertAction.label}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color(0xFFCBD5E1),
                )
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                SummaryPill("无障碍", if (accessibilityEnabled) "开" else "关")
                SummaryPill("OCR", if (captureRunning) "开" else "关")
                SummaryPill("规则", if (rulesEnabled) "开" else "关")
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text("监控 $selectedAppCount 个 App", color = Color(0xFFE2E8F0), style = MaterialTheme.typography.bodySmall)
                Text("命中 $hitCount / 日志 $logCount", color = Color(0xFFE2E8F0), style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}

@Composable
fun SummaryPill(label: String, value: String) {
    Card(colors = CardDefaults.cardColors(containerColor = Color(0xFF1E293B))) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(label, color = Color(0xFF94A3B8), style = MaterialTheme.typography.bodySmall)
            Text(value, color = Color.White, fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.bodySmall)
        }
    }
}

@Composable
fun SectionHeader(
    title: String,
    meta: String,
    actionText: String,
    onAction: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column {
            Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            Text(meta, color = Color(0xFF64748B), style = MaterialTheme.typography.bodySmall)
        }
        TextButton(onClick = onAction) {
            Text(actionText)
        }
    }
}

@Composable
fun EmptyStateCard(text: String) {
    Card(colors = CardDefaults.cardColors(containerColor = Color.White)) {
        Text(
            text = text,
            modifier = Modifier.padding(16.dp),
            color = Color(0xFF64748B),
        )
    }
}

@Composable
private fun LegacyOnboardingScreen(
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
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        item {
            Card(colors = CardDefaults.cardColors(containerColor = Color(0xFF0F172A))) {
                Column(
                    modifier = Modifier.padding(20.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Text(
                        text = "先把守护设置好",
                        style = MaterialTheme.typography.headlineSmall,
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                    )
                    Text(
                        text = "这几项会影响识别、提醒和后台稳定性。设置好之后，再交给家人使用会省心很多。",
                        color = Color(0xFFCBD5E1),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            }
        }
        item {
            GuideStepCard(
                index = "1",
                title = "开启无障碍",
                description = "用于读取已选择 App 的屏幕文字，并在命中风险内容时显示大号提醒。",
                done = accessibilityEnabled,
                doneText = "已开启",
                actionText = if (accessibilityEnabled) "查看设置" else "去开启",
                onAction = onOpenAccessibility,
            )
        }
        item {
            GuideStepCard(
                index = "2",
                title = "关闭电池优化",
                description = "减少系统在后台清理服务的概率，连续刷视频时更稳定。",
                done = !batteryOptimized,
                doneText = "已放行",
                actionText = if (batteryOptimized) "去设置" else "查看设置",
                onAction = onOpenBatterySettings,
            )
        }
        item {
            GuideStepCard(
                index = "3",
                title = "授权截图识别",
                description = "无障碍文字不足时，用截图 OCR 兜底识别标题、内容和标签。",
                done = captureRunning,
                doneText = "已授权",
                actionText = if (captureRunning) "重新授权" else "去授权",
                onAction = onStartCapture,
            )
        }
        item {
            Button(
                modifier = Modifier.fillMaxWidth(),
                onClick = onFinish,
            ) {
                Text("进入主页")
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
    Card(colors = CardDefaults.cardColors(containerColor = Color.White)) {
        Column(
            modifier = Modifier.padding(16.dp),
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
                    Text(
                        text = index,
                        color = if (done) Color(0xFF15803D) else Color(0xFFB91C1C),
                        fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.titleLarge,
                    )
                    Column {
                        Text(title, fontWeight = FontWeight.SemiBold)
                        Text(
                            if (done) doneText else "待设置",
                            color = if (done) Color(0xFF15803D) else Color(0xFFB91C1C),
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                }
                OutlinedButton(onClick = onAction) {
                    Text(actionText)
                }
            }
            Text(description, color = Color(0xFF475569), style = MaterialTheme.typography.bodyMedium)
        }
    }
}

@Composable
fun StatusCard(
    accessibilityEnabled: Boolean,
    captureRunning: Boolean,
    onOpenAccessibility: () -> Unit,
    onStartCapture: () -> Unit,
    onStopCapture: () -> Unit,
    onOpenOnboarding: () -> Unit,
) {
    Card(colors = CardDefaults.cardColors(containerColor = Color.White)) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Text("运行控制", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                StateButton(
                    label = if (accessibilityEnabled) "无障碍 已开启" else "开启无障碍",
                    active = accessibilityEnabled,
                    modifier = Modifier.weight(1f),
                    onClick = onOpenAccessibility,
                )
                StateButton(
                    label = if (captureRunning) "截图 已授权" else "授权截图",
                    active = captureRunning,
                    modifier = Modifier.weight(1f),
                    onClick = if (captureRunning) onStopCapture else onStartCapture,
                )
            }
            OutlinedButton(
                modifier = Modifier.fillMaxWidth(),
                onClick = onOpenOnboarding,
            ) {
                Text("重新打开引导")
            }
        }
    }
}

@Composable
fun AlertActionButton(
    action: AlertAction,
    selected: Boolean,
    modifier: Modifier = Modifier,
    onClick: (AlertAction) -> Unit,
) {
    val contentPadding = PaddingValues(horizontal = 2.dp, vertical = 8.dp)
    if (selected) {
        Button(
            modifier = modifier,
            contentPadding = contentPadding,
            onClick = { onClick(action) },
        ) {
            Text(
                text = action.label,
                maxLines = 1,
                softWrap = false,
                fontSize = 13.sp,
            )
        }
    } else {
        OutlinedButton(
            modifier = modifier,
            contentPadding = contentPadding,
            onClick = { onClick(action) },
        ) {
            Text(
                text = action.label,
                maxLines = 1,
                softWrap = false,
                fontSize = 13.sp,
            )
        }
    }
}

@Composable
fun AlertSizeButton(
    size: AlertSize,
    selected: Boolean,
    modifier: Modifier = Modifier,
    onClick: (AlertSize) -> Unit,
) {
    if (selected) {
        Button(modifier = modifier, onClick = { onClick(size) }) {
            Text(size.label)
        }
    } else {
        OutlinedButton(modifier = modifier, onClick = { onClick(size) }) {
            Text(size.label)
        }
    }
}

@Composable
fun StateButton(
    label: String,
    active: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    if (active) {
        Button(modifier = modifier, onClick = onClick) {
            Text(label)
        }
    } else {
        OutlinedButton(modifier = modifier, onClick = onClick) {
            Text(label)
        }
    }
}

@Composable
fun AppSelectorCard(
    expanded: Boolean,
    targets: List<MonitorTarget>,
    onToggleExpanded: () -> Unit,
    onTargetChanged: (MonitorTarget, Boolean) -> Unit,
) {
    val selectedCount = targets.count { it.selected }
    Card(colors = CardDefaults.cardColors(containerColor = Color.White)) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(onClick = onToggleExpanded),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column {
                    Text("App 选择器", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                    Text(
                        "已选择 $selectedCount 个 App，默认包含抖音",
                        color = Color(0xFF64748B),
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
                Text(if (expanded) "收起" else "展开", color = Color(0xFF2563EB), fontWeight = FontWeight.SemiBold)
            }
            if (expanded) {
                targets.forEach { target ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onTargetChanged(target, !target.selected) },
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(target.label, fontWeight = FontWeight.SemiBold)
                            Text(target.packageName, color = Color(0xFF64748B), style = MaterialTheme.typography.bodySmall)
                        }
                        Checkbox(
                            checked = target.selected,
                            onCheckedChange = { selected -> onTargetChanged(target, selected) },
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun CustomRulesCard(
    expanded: Boolean,
    titleRuleText: String,
    tagRuleText: String,
    textRuleText: String,
    customRules: List<CustomRule>,
    onToggleExpanded: () -> Unit,
    onTitleRuleChanged: (String) -> Unit,
    onTagRuleChanged: (String) -> Unit,
    onTextRuleChanged: (String) -> Unit,
    onAddTitleRule: () -> Unit,
    onAddTagRule: () -> Unit,
    onAddTextRule: () -> Unit,
    onRemoveRule: (Long) -> Unit,
) {
    Card(colors = CardDefaults.cardColors(containerColor = Color.White)) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(onClick = onToggleExpanded),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column {
                    Text("自定义命中规则", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                    Text(
                        "${customRules.size} 条规则，支持标题/标签/文本包含",
                        color = Color(0xFF64748B),
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
                Text(if (expanded) "收起" else "展开", color = Color(0xFF2563EB), fontWeight = FontWeight.SemiBold)
            }
            if (expanded) {
                Text(
                    "支持“标题包含 xxx”“标签包含 xxx”和“文本包含 xxx”。命中后会像内置规则一样弹出提醒。",
                    color = Color(0xFF64748B),
                    style = MaterialTheme.typography.bodySmall,
                )
                RuleInputRow(
                    label = "标题包含",
                    value = titleRuleText,
                    onValueChange = onTitleRuleChanged,
                    onAdd = onAddTitleRule,
                )
                RuleInputRow(
                    label = "标签包含",
                    value = tagRuleText,
                    onValueChange = onTagRuleChanged,
                    onAdd = onAddTagRule,
                )
                RuleInputRow(
                    label = "文本包含",
                    value = textRuleText,
                    onValueChange = onTextRuleChanged,
                    onAdd = onAddTextRule,
                )
                if (customRules.isEmpty()) {
                    Text("暂无自定义规则", color = Color(0xFF94A3B8), style = MaterialTheme.typography.bodySmall)
                } else {
                    customRules.forEach { rule ->
                        RuleRow(rule = rule, onRemoveRule = onRemoveRule)
                    }
                }
            }
        }
    }
}

@Composable
fun RuleInputRow(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    onAdd: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        OutlinedTextField(
            value = value,
            onValueChange = onValueChange,
            modifier = Modifier.weight(1f),
            label = { Text(label) },
            singleLine = true,
        )
        Button(onClick = onAdd, enabled = value.isNotBlank()) {
            Text("添加")
        }
    }
}

@Composable
fun RuleRow(rule: CustomRule, onRemoveRule: (Long) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text("${rule.target.label}包含：${rule.keyword}", fontWeight = FontWeight.SemiBold)
            Text("自定义规则", color = Color(0xFF64748B), style = MaterialTheme.typography.bodySmall)
        }
        TextButton(onClick = { onRemoveRule(rule.id) }) {
            Text("删除")
        }
    }
}

@Composable
fun HitCard(hit: RiskHit) {
    Card(colors = CardDefaults.cardColors(containerColor = Color.White)) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text("风险分 ${hit.score}", fontWeight = FontWeight.Bold, color = Color(0xFFB91C1C))
                Text(hit.source, color = Color(0xFF475569), style = MaterialTheme.typography.bodySmall)
            }
            Text(hit.text, maxLines = 4)
            Text(
                text = "命中：${hit.matchedRules.joinToString("、")}",
                color = Color(0xFF475569),
                style = MaterialTheme.typography.bodySmall,
            )
            Text(
                text = DateFormat.format("MM-dd HH:mm:ss", hit.timeMillis).toString(),
                color = Color(0xFF94A3B8),
                style = MaterialTheme.typography.bodySmall,
            )
        }
    }
}

@Composable
fun VideoLogCard(log: ParsedVideoLog) {
    val context = LocalContext.current
    val cachedInfo = remember(log.packageName) {
        HitRepository.getCachedAppInfo(context, log.packageName)
    }
    val appLabel = cachedInfo?.label?.takeIf { it.isNotBlank() }
        ?: log.appLabel.ifBlank { "未知App" }
    val appIcon = remember(log.packageName, cachedInfo?.iconBase64) {
        cachedInfo?.iconBase64
            ?.decodeAppIcon()
            ?.asImageBitmap()
            ?: runCatching {
                context.packageManager
                    .getApplicationIcon(log.packageName)
                    .toBitmap(width = 96, height = 96, config = Bitmap.Config.ARGB_8888)
                    .asImageBitmap()
            }.getOrNull()
    }

    Card(colors = CardDefaults.cardColors(containerColor = Color.White)) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
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
                    if (appIcon != null) {
                        Image(
                            bitmap = appIcon,
                            contentDescription = appLabel,
                            modifier = Modifier.size(36.dp),
                        )
                    } else {
                        Box(modifier = Modifier.size(36.dp), contentAlignment = Alignment.Center) {
                            Text("App", color = Color(0xFF64748B), style = MaterialTheme.typography.bodySmall)
                        }
                    }
                    Column {
                        Text(appLabel, fontWeight = FontWeight.SemiBold)
                        Text(log.source, color = Color(0xFF475569), style = MaterialTheme.typography.bodySmall)
                    }
                }
                Text(
                    text = DateFormat.format("MM-dd HH:mm:ss", log.timeMillis).toString(),
                    color = Color(0xFF94A3B8),
                    style = MaterialTheme.typography.bodySmall,
                )
            }
            Text(
                text = "标题：${log.title.ifBlank { "未解析到" }}",
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                text = "标签：${if (log.tags.isEmpty()) "未解析到" else log.tags.joinToString("、")}",
                color = Color(0xFF0F766E),
                style = MaterialTheme.typography.bodySmall,
            )
            Text(
                text = "内容：${log.content.ifBlank { "未解析到" }}",
                maxLines = 5,
                color = Color(0xFF334155),
            )
        }
    }
}

@Composable
fun OperationLogCard(log: OperationLog) {
    Card(colors = CardDefaults.cardColors(containerColor = Color.White)) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(log.action, fontWeight = FontWeight.SemiBold, color = Color(0xFF0F172A))
                Text(
                    text = DateFormat.format("MM-dd HH:mm:ss", log.timeMillis).toString(),
                    color = Color(0xFF94A3B8),
                    style = MaterialTheme.typography.bodySmall,
                )
            }
            Text(log.message, color = Color(0xFF334155), style = MaterialTheme.typography.bodyMedium)
        }
    }
}

private fun Context.encodeAppIcon(packageName: String): String {
    return runCatching {
        val bitmap = packageManager
            .getApplicationIcon(packageName)
            .toBitmap(width = 96, height = 96, config = Bitmap.Config.ARGB_8888)
        ByteArrayOutputStream().use { output ->
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, output)
            Base64.encodeToString(output.toByteArray(), Base64.NO_WRAP)
        }
    }.getOrDefault("")
}

private fun String.decodeAppIcon(): Bitmap? {
    if (isBlank()) return null
    return runCatching {
        val bytes = Base64.decode(this, Base64.DEFAULT)
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
    }.getOrNull()
}

private fun Context.loadMonitorTargets(): List<MonitorTarget> {
    val selectedPackages = HitRepository.getMonitoredPackages(this)
    val launchIntent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
    val apps = packageManager.queryIntentActivities(launchIntent, PackageManager.MATCH_ALL)
        .map { resolveInfo ->
            val packageName = resolveInfo.activityInfo.packageName
            MonitorTarget(
                packageName = packageName,
                label = resolveInfo.loadLabel(packageManager).toString().ifBlank { packageName },
                selected = packageName in selectedPackages,
            )
        }
        .filterNot { it.packageName == packageName }
        .distinctBy { it.packageName }

    val knownPackages = apps.map { it.packageName }.toSet()
    val missingDefaults = HitRepository.DEFAULT_MONITORED_PACKAGES
        .filterNot { it in knownPackages }
        .map { packageName ->
            MonitorTarget(
                packageName = packageName,
                label = if (packageName.endsWith(".lite")) "抖音极速版" else "抖音",
                selected = packageName in selectedPackages,
            )
        }

    return (apps + missingDefaults)
        .sortedWith(
            compareByDescending<MonitorTarget> { it.selected }
                .thenByDescending { it.packageName in HitRepository.DEFAULT_MONITORED_PACKAGES }
                .thenBy { it.label.lowercase() },
        )
        .take(80)
}

private fun Context.isAccessibilityEnabled(): Boolean {
    val enabledServices = Settings.Secure.getString(
        contentResolver,
        Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES,
    ).orEmpty()
    return enabledServices.contains(
        "$packageName/com.xyz.aitool.accessibility.AccessibilityMonitorService",
        ignoreCase = true,
    )
}

private fun Context.isIgnoringBatteryOptimizations(): Boolean {
    return runCatching {
        getSystemService(PowerManager::class.java).isIgnoringBatteryOptimizations(packageName)
    }.getOrDefault(false)
}
