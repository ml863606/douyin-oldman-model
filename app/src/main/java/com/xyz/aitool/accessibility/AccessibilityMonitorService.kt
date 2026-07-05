package com.xyz.aitool.accessibility

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.graphics.Path
import android.graphics.Rect
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import com.xyz.aitool.data.AlertAction
import com.xyz.aitool.capture.ScreenCaptureService
import com.xyz.aitool.data.HitRepository
import com.xyz.aitool.data.RiskHit
import com.xyz.aitool.data.VideoTextParser
import com.xyz.aitool.risk.RiskClassifier
import com.xyz.aitool.ui.WarningOverlay
import com.xyz.aitool.ui.WarningOverlayAction
import java.util.LinkedHashSet
import kotlin.coroutines.resume
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class AccessibilityMonitorService : AccessibilityService() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var debounceJob: Job? = null
    private var warningOverlay: WarningOverlay? = null
    private var lastFingerprint = ""
    private var lastRuleFingerprint = ""
    private var lastHitAt = 0L
    private var lastParsedFingerprint = ""

    override fun onServiceConnected() {
        warningOverlay = WarningOverlay(this)
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (warningOverlay?.isShowing == true) return

        val packageName = event?.packageName?.toString() ?: return
        if (packageName !in HitRepository.getMonitoredPackages(this)) {
            return
        }

        debounceJob?.cancel()
        debounceJob = scope.launch {
            delay(500)
            inspectCurrentScreen(packageName)
        }
    }

    override fun onInterrupt() {
        warningOverlay?.hide()
    }

    override fun onDestroy() {
        warningOverlay?.hide()
        scope.cancel()
        super.onDestroy()
    }

    private suspend fun inspectCurrentScreen(packageName: String) {
        if (warningOverlay?.isShowing == true) return

        val appLabel = resolveAppLabel(packageName)
        val accessibilityText = collectVisibleText(rootInActiveWindow)
        if (accessibilityText.isSelfUiText()) return

        val accessibilityVideo = VideoTextParser.parse(
            rawText = accessibilityText,
            source = "无障碍文本",
            packageName = packageName,
            appLabel = appLabel,
        )
        val selectedVideo = if (accessibilityVideo != null && accessibilityText.length >= 40) {
            accessibilityVideo
        } else {
            val ocrText = if (accessibilityText.length < 40) {
                ScreenCaptureService.captureTextFromCurrentScreen().orEmpty()
            } else {
                ""
            }
            if (ocrText.isSelfUiText()) return
            VideoTextParser.parse(
                rawText = ocrText,
                source = "截图OCR",
                packageName = packageName,
                appLabel = appLabel,
            ) ?: accessibilityVideo ?: return
        }

        val parsedFingerprint = selectedVideo.rawText.normalizeForFingerprint()
        if (parsedFingerprint != lastParsedFingerprint) {
            lastParsedFingerprint = parsedFingerprint
            HitRepository.recordVideoLog(this, selectedVideo)
        }

        if (!HitRepository.areRulesEnabled(this)) return

        val result = RiskClassifier.classify(selectedVideo, HitRepository.getCustomRules(this)) ?: return

        val fingerprint = selectedVideo.rawText.normalizeForFingerprint()
        val ruleFingerprint = result.matchedRules.joinToString("|").normalizeForFingerprint()
        val now = System.currentTimeMillis()
        if (fingerprint == lastFingerprint && ruleFingerprint == lastRuleFingerprint && now - lastHitAt < 10_000L) return

        lastFingerprint = fingerprint
        lastRuleFingerprint = ruleFingerprint
        lastHitAt = now

        HitRepository.recordHit(
            this,
            RiskHit(
                id = now,
                timeMillis = now,
                text = selectedVideo.rawText.trim().take(300),
                matchedRules = result.matchedRules,
                score = result.score,
                source = selectedVideo.source,
            ),
        )
        val alertAction = HitRepository.getAlertAction(this)
        warningOverlay?.show(
            message = HitRepository.getAlertMessage(this),
            detail = "命中：${result.matchedRules.take(3).joinToString("、")}",
            confirmText = alertAction.confirmText,
            alertSize = HitRepository.getAlertSize(this),
            messageTextSizeSp = HitRepository.getWarningFontSize(this),
            debugEnabled = HitRepository.isDebugModeEnabled(this),
            onAction = { action ->
                scope.launch {
                    performOverlayAction(alertAction, action)
                }
            },
        )
    }

    private fun collectVisibleText(root: AccessibilityNodeInfo?): String {
        if (root == null) return ""
        val values = LinkedHashSet<String>()
        fun visit(node: AccessibilityNodeInfo?) {
            if (node == null) return
            listOf(node.text, node.contentDescription)
                .mapNotNull { it?.toString()?.trim() }
                .filter { it.length >= 2 }
                .forEach(values::add)

            for (index in 0 until node.childCount) {
                visit(node.getChild(index))
            }
        }
        visit(root)
        return values.joinToString(separator = "\n")
    }

    private fun String.normalizeForFingerprint(): String {
        return lowercase()
            .replace(Regex("\\s+"), "")
            .take(120)
    }

    private fun String.isSelfUiText(): Boolean {
        if (isBlank()) return false
        val normalized = lowercase().replace(Regex("\\s+"), "")
        val selfSignals = listOf(
            "ai视频提醒助手",
            "解析日志",
            "最近命中",
            "自定义命中规则",
            "app选择器",
            "风险内容警告",
            "我知道了",
            "我知道了，跳过",
            "确定并跳过",
            "自动点不感兴趣",
            "上滑短",
            "上滑长",
            "节点滚动",
            "操作日志",
            "debug模式",
            "弹窗字体大小",
            "还没有命中记录",
            "还没有解析日志",
            "com.xyz.aitool",
        )
        val logCardSignals = listOf(
            "标题：",
            "标签：",
            "内容：",
            "无障碍文本",
            "截图ocr",
        )
        val hasSelfSignal = selfSignals.any { normalized.contains(it.lowercase().replace(Regex("\\s+"), "")) }
        val hasLogShape = logCardSignals.count { contains(it, ignoreCase = true) } >= 2
        return hasSelfSignal || hasLogShape
    }

    private fun resolveAppLabel(packageName: String): String {
        return runCatching {
            val info = packageManager.getApplicationInfo(packageName, 0)
            packageManager.getApplicationLabel(info).toString()
        }.getOrDefault(packageName)
    }

    private suspend fun performOverlayAction(alertAction: AlertAction, action: WarningOverlayAction) {
        if (action == WarningOverlayAction.DEFAULT_SKIP && alertAction == AlertAction.NOT_INTERESTED) {
            performNotInterestedFlow()
        } else {
            performSkipAction(action)
        }
    }

    private fun performSkipAction(action: WarningOverlayAction) {
        recordOperation("跳过测试", "执行：${action.label}")
        when (action) {
            WarningOverlayAction.DEFAULT_SKIP,
            WarningOverlayAction.SWIPE_UP_SHORT -> dispatchSwipe(
                startYRatio = 0.78f,
                endYRatio = 0.24f,
                durationMillis = 320L,
            )
            WarningOverlayAction.SWIPE_UP_LONG -> dispatchSwipe(
                startYRatio = 0.86f,
                endYRatio = 0.12f,
                durationMillis = 620L,
            )
            WarningOverlayAction.SWIPE_DOWN -> dispatchSwipe(
                startYRatio = 0.24f,
                endYRatio = 0.78f,
                durationMillis = 420L,
            )
            WarningOverlayAction.SCROLL_FORWARD -> scrollForwardNode()
        }
    }

    private fun dispatchSwipe(startYRatio: Float, endYRatio: Float, durationMillis: Long) {
        val metrics = resources.displayMetrics
        val startX = metrics.widthPixels * 0.5f
        val startY = metrics.heightPixels * startYRatio
        val endY = metrics.heightPixels * endYRatio
        val path = Path().apply {
            moveTo(startX, startY)
            lineTo(startX, endY)
        }
        val gesture = GestureDescription.Builder()
            .addStroke(GestureDescription.StrokeDescription(path, 0L, durationMillis))
            .build()
        dispatchGesture(gesture, null, null)
    }

    private suspend fun performNotInterestedFlow() {
        val metrics = resources.displayMetrics
        val x = metrics.widthPixels * 0.5f
        val y = metrics.heightPixels * 0.45f

        recordOperation("不感兴趣", "开始：准备长按当前视频")
        recordOperation("不感兴趣", "步骤1：长按视频，坐标 ${x.toInt()},${y.toInt()}，时长 900ms")
        val longPressOk = dispatchLongPress(x, y)
        recordOperation("不感兴趣", "步骤1结果：${if (longPressOk) "长按手势完成" else "长按手势被系统取消"}")
        if (!longPressOk) return

        recordOperation("不感兴趣", "步骤2：等待操作菜单出现 900ms")
        delay(900)

        val menuText = collectVisibleText(rootInActiveWindow)
        recordOperation(
            "不感兴趣",
            "步骤3：当前可见菜单文字：${menuText.ifBlank { "未读取到文字" }.take(260)}",
        )

        val targetNode = findNodeByText(
            rootInActiveWindow,
            listOf("不感兴趣", "减少此类作品", "不再推荐"),
        )
        if (targetNode == null) {
            recordOperation("不感兴趣", "步骤4结果：没有找到“不感兴趣”按钮")
            return
        }

        val bounds = Rect()
        targetNode.getBoundsInScreen(bounds)
        recordOperation("不感兴趣", "步骤4：找到按钮，区域 ${bounds.flattenToString()}，准备点击")

        val clickOk = clickNodeOrBounds(targetNode, bounds)
        recordOperation("不感兴趣", "步骤5结果：${if (clickOk) "点击已执行" else "点击失败"}")
    }

    private suspend fun dispatchLongPress(x: Float, y: Float): Boolean {
        val path = Path().apply {
            moveTo(x, y)
            lineTo(x, y)
        }
        val gesture = GestureDescription.Builder()
            .addStroke(GestureDescription.StrokeDescription(path, 0L, 900L))
            .build()
        return dispatchGestureAwait(gesture)
    }

    private suspend fun dispatchTap(x: Float, y: Float): Boolean {
        val path = Path().apply {
            moveTo(x, y)
            lineTo(x, y)
        }
        val gesture = GestureDescription.Builder()
            .addStroke(GestureDescription.StrokeDescription(path, 0L, 90L))
            .build()
        return dispatchGestureAwait(gesture)
    }

    private suspend fun dispatchGestureAwait(gesture: GestureDescription): Boolean {
        return kotlinx.coroutines.suspendCancellableCoroutine { continuation ->
            val submitted = dispatchGesture(
                gesture,
                object : GestureResultCallback() {
                    override fun onCompleted(gestureDescription: GestureDescription?) {
                        if (continuation.isActive) continuation.resume(true)
                    }

                    override fun onCancelled(gestureDescription: GestureDescription?) {
                        if (continuation.isActive) continuation.resume(false)
                    }
                },
                null,
            )
            if (!submitted && continuation.isActive) {
                continuation.resume(false)
            }
        }
    }

    private fun findNodeByText(root: AccessibilityNodeInfo?, keywords: List<String>): AccessibilityNodeInfo? {
        if (root == null) return null
        val text = listOf(root.text, root.contentDescription)
            .mapNotNull { it?.toString()?.trim() }
            .joinToString(" ")
        if (keywords.any { keyword -> text.contains(keyword, ignoreCase = true) }) {
            return root
        }
        for (index in 0 until root.childCount) {
            findNodeByText(root.getChild(index), keywords)?.let { return it }
        }
        return null
    }

    private suspend fun clickNodeOrBounds(node: AccessibilityNodeInfo, bounds: Rect): Boolean {
        var current: AccessibilityNodeInfo? = node
        while (current != null) {
            if (current.isClickable && current.performAction(AccessibilityNodeInfo.ACTION_CLICK)) {
                return true
            }
            current = current.parent
        }

        if (!bounds.isEmpty) {
            return dispatchTap(bounds.exactCenterX(), bounds.exactCenterY())
        }
        return false
    }

    private fun scrollForwardNode(): Boolean {
        fun visit(node: AccessibilityNodeInfo?): Boolean {
            if (node == null) return false
            if (node.isScrollable && node.performAction(AccessibilityNodeInfo.ACTION_SCROLL_FORWARD)) {
                return true
            }
            for (index in node.childCount - 1 downTo 0) {
                if (visit(node.getChild(index))) return true
            }
            return false
        }
        return visit(rootInActiveWindow) ||
            rootInActiveWindow?.performAction(AccessibilityNodeInfo.ACTION_SCROLL_FORWARD) == true
    }

    private fun recordOperation(action: String, message: String) {
        HitRepository.recordOperationLog(this, action, message)
    }
}
