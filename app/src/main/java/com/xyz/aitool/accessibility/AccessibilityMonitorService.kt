package com.xyz.aitool.accessibility

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.graphics.Path
import android.graphics.Rect
import android.os.SystemClock
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import com.xyz.aitool.data.AlertAction
import com.xyz.aitool.capture.ScreenCaptureService
import com.xyz.aitool.capture.CaptureTextResult
import com.xyz.aitool.data.HitRepository
import com.xyz.aitool.data.ParsedVideoLog
import com.xyz.aitool.data.RecognitionMode
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
    private var currentWindowPackageName = ""
    private var currentWindowClassName = ""
    private var lastOcrDecisionFingerprint = ""
    private var lastInspectionDecisionFingerprint = ""

    override fun onServiceConnected() {
        warningOverlay = WarningOverlay(this)
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (warningOverlay?.isShowing == true) return

        val packageName = event?.packageName?.toString() ?: return
        if (packageName !in HitRepository.getMonitoredPackages(this)) {
            return
        }
        val className = event.className?.toString().orEmpty()
        rememberCurrentWindow(packageName, className, event.eventType)

        debounceJob?.cancel()
        debounceJob = scope.launch {
            delay(250)
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

        val windowClassName = currentActivityClassNameFor(packageName)
        if (!canInspectPackageScreen(packageName, windowClassName)) {
            recordInspectionDecisionIfNeeded(packageName, windowClassName, allowed = false)
            return
        }
        recordInspectionDecisionIfNeeded(packageName, windowClassName, allowed = true)

        val appLabel = resolveAppLabel(packageName)
        val recognitionMode = HitRepository.getRecognitionMode(this)
        val accessibilityCoordinateText = collectCaptionTextFromNodes(rootInActiveWindow, packageName)
        val accessibilityText = collectVisibleText(rootInActiveWindow)
        val preferOcr = shouldPreferOcr(packageName, windowClassName)
        val accessibilityTextLooksSelf = accessibilityText.isSelfUiText()
        if (accessibilityTextLooksSelf && !preferOcr && recognitionMode != RecognitionMode.OCR_COORDINATE) return

        val accessibilityCoordinateVideo = VideoTextParser.parse(
            rawText = accessibilityCoordinateText,
            source = "无障碍坐标",
            packageName = packageName,
            appLabel = appLabel,
        )

        val canUseOcr = canUseOcrFallback(packageName, windowClassName)
        val selectedVideo = when (recognitionMode) {
            RecognitionMode.ACCESSIBILITY_COORDINATE -> {
                val ocrVideo = if (canUseOcr && accessibilityCoordinateVideo.isLowQualityCaption()) {
                    recordOcrDecisionIfNeeded(packageName, windowClassName, accessibilityText.length, allowed = true)
                    captureBestOcrVideo(packageName, appLabel)
                } else {
                    null
                }
                recordRecognitionModeDecision(recognitionMode, accessibilityCoordinateVideo, ocrVideo)
                listOfNotNull(accessibilityCoordinateVideo, ocrVideo)
                    .maxByOrNull { it.qualityScore() }
                    ?: return
            }
            RecognitionMode.OCR_COORDINATE -> {
                if (!canUseOcr) {
                    recordOcrDecisionIfNeeded(packageName, windowClassName, accessibilityText.length, allowed = false)
                    return
                }
                recordOcrDecisionIfNeeded(packageName, windowClassName, accessibilityText.length, allowed = true)
                val ocrVideo = captureBestOcrVideo(packageName, appLabel)
                recordRecognitionModeDecision(recognitionMode, null, ocrVideo)
                ocrVideo ?: return
            }
            RecognitionMode.AUTO -> {
                val accessibilityScore = accessibilityCoordinateVideo?.qualityScore().orZero()
                val shouldTryOcr = canUseOcr && (preferOcr || accessibilityScore < OCR_ACCEPTABLE_SCORE)
                val ocrVideo = if (shouldTryOcr) {
                    recordOcrDecisionIfNeeded(packageName, windowClassName, accessibilityText.length, allowed = true)
                    captureBestOcrVideo(packageName, appLabel)
                } else {
                    recordOcrDecisionIfNeeded(packageName, windowClassName, accessibilityText.length, canUseOcr)
                    null
                }

                recordRecognitionModeDecision(recognitionMode, accessibilityCoordinateVideo, ocrVideo)
                listOfNotNull(accessibilityCoordinateVideo, ocrVideo)
                    .maxByOrNull { it.qualityScore() }
                    ?: return
            }
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
                ocrDurationMillis = selectedVideo.ocrDurationMillis,
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

    private suspend fun captureBestOcrVideo(packageName: String, appLabel: String): ParsedVideoLog? {
        var bestVideo: ParsedVideoLog? = null
        var bestScore = 0
        var stableFingerprint = ""
        var stableCount = 0
        val startedAt = SystemClock.elapsedRealtime()

        repeat(OCR_MAX_ATTEMPTS) { attemptIndex ->
            if (warningOverlay?.isShowing == true) return bestVideo

            val attempt = attemptIndex + 1
            val result = ScreenCaptureService.captureTextResultFromCurrentScreen()
            val captionText = result?.captionTextFromOcrLines().orEmpty()
            val video = VideoTextParser.parse(
                rawText = captionText.ifBlank { result?.text.orEmpty() },
                source = "OCR坐标",
                packageName = packageName,
                appLabel = appLabel,
            )?.copy(ocrDurationMillis = result?.durationMillis)
            val score = video?.qualityScore().orZero()
            val fingerprint = video?.rawText?.normalizeForFingerprint().orEmpty()

            if (fingerprint.isNotBlank() && fingerprint == stableFingerprint) {
                stableCount += 1
            } else {
                stableFingerprint = fingerprint
                stableCount = if (fingerprint.isBlank()) 0 else 1
            }

            if (score > bestScore) {
                bestScore = score
                bestVideo = video
            }

            recordOperation(
                "OCR采样",
                "第${attempt}次 分数=$score 作者=${video?.author?.ifBlank { "无" } ?: "无"} " +
                    "标题=${video?.title?.ifBlank { "无" } ?: "无"} 标签=${video?.tags?.size ?: 0} " +
                    "耗时=${result?.durationMillis?.formatSeconds() ?: "无"}",
            )

            if (score >= OCR_GOOD_ENOUGH_SCORE || (score >= OCR_ACCEPTABLE_SCORE && stableCount >= 2)) {
                return bestVideo
            }
            if (SystemClock.elapsedRealtime() - startedAt >= OCR_SCAN_WINDOW_MILLIS) {
                return bestVideo
            }
            delay(OCR_SAMPLE_INTERVAL_MILLIS)
        }

        return bestVideo
    }

    private fun CaptureTextResult.captionTextFromOcrLines(): String {
        if (lines.isEmpty()) return text
        val region = captionRegion()
        val metrics = resources.displayMetrics
        return lines
            .asSequence()
            .filter { line ->
                line.text.isNotBlank() &&
                    Rect.intersects(line.bounds, region) &&
                    line.bounds.left < metrics.widthPixels * CAPTION_TEXT_LEFT_EDGE_MAX_RATIO &&
                    line.bounds.bottom < metrics.heightPixels * CAPTION_TEXT_BOTTOM_LIMIT_RATIO
            }
            .sortedWith(compareBy({ it.bounds.top / 18 }, { it.bounds.left }))
            .map { it.text.trim() }
            .distinct()
            .joinToString("\n")
    }

    private fun ParsedVideoLog.qualityScore(): Int {
        var score = 0
        if (author.startsWith("@") && author.length >= 3) score += 4
        if (title.length >= 4) score += 5
        if (content.isNotBlank()) score += 1
        score += (tags.size * 2).coerceAtMost(4)
        if (rawText.contains("#")) score += 1
        if (rawText.length >= 20) score += 1
        return score
    }

    private fun Int?.orZero(): Int = this ?: 0

    private fun Long.formatSeconds(): String {
        return String.format(java.util.Locale.US, "%.2f秒", this / 1000.0)
    }

    private fun collectVisibleText(root: AccessibilityNodeInfo?): String {
        if (root == null) return ""
        val values = LinkedHashSet<String>()
        val displayMetrics = resources.displayMetrics
        val screenBounds = Rect(0, 0, displayMetrics.widthPixels, displayMetrics.heightPixels)
        fun visit(node: AccessibilityNodeInfo?) {
            if (node == null) return
            if (!node.isVisibleToUser) return

            val bounds = Rect()
            node.getBoundsInScreen(bounds)
            val nodeIsOnScreen = !bounds.isEmpty && Rect.intersects(bounds, screenBounds)
            if (nodeIsOnScreen) {
                listOf(node.text, node.contentDescription)
                    .mapNotNull { it?.toString()?.trim() }
                    .filter { it.length >= 2 }
                    .forEach(values::add)
            }

            for (index in 0 until node.childCount) {
                visit(node.getChild(index))
            }
        }
        visit(root)
        return values.joinToString(separator = "\n")
    }

    private fun collectCaptionTextFromNodes(root: AccessibilityNodeInfo?, packageName: String): String {
        if (root == null) return ""
        val region = captionRegion()
        val metrics = resources.displayMetrics
        val values = mutableListOf<PositionedText>()

        fun visit(node: AccessibilityNodeInfo?) {
            if (node == null) return
            if (!node.isVisibleToUser) return

            val bounds = Rect()
            node.getBoundsInScreen(bounds)
            val nodePackage = node.packageName?.toString().orEmpty()
            val inCaptionArea = !bounds.isEmpty &&
                Rect.intersects(bounds, region) &&
                bounds.left < metrics.widthPixels * CAPTION_TEXT_LEFT_EDGE_MAX_RATIO &&
                bounds.bottom < metrics.heightPixels * CAPTION_TEXT_BOTTOM_LIMIT_RATIO

            if (inCaptionArea && nodePackage == packageName) {
                listOf(node.text, node.contentDescription)
                    .mapNotNull { it?.toString()?.trim() }
                    .filter { it.length >= 2 }
                    .forEach { values += PositionedText(bounds = Rect(bounds), text = it) }
            }

            for (index in 0 until node.childCount) {
                visit(node.getChild(index))
            }
        }

        visit(root)
        return values
            .sortedWith(compareBy({ it.bounds.top / 18 }, { it.bounds.left }))
            .distinctBy { it.text }
            .joinToString("\n") { it.text }
    }

    private data class PositionedText(
        val bounds: Rect,
        val text: String,
    )

    private fun captionRegion(): Rect {
        val metrics = resources.displayMetrics
        return Rect(
            0,
            (metrics.heightPixels * CAPTION_TOP_RATIO).toInt(),
            (metrics.widthPixels * CAPTION_RIGHT_RATIO).toInt(),
            (metrics.heightPixels * CAPTION_BOTTOM_RATIO).toInt(),
        )
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

    private fun rememberCurrentWindow(packageName: String, className: String, eventType: Int) {
        if (className.isBlank()) return

        if (eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED || className.looksLikeActivityName()) {
            currentWindowPackageName = packageName
            currentWindowClassName = className
            recordOperation("Activity", "$packageName / $className")
        }
    }

    private fun currentActivityClassNameFor(packageName: String): String {
        return if (currentWindowPackageName == packageName) currentWindowClassName else ""
    }

    private fun canInspectPackageScreen(packageName: String, windowClassName: String): Boolean {
        if (packageName == DOUYIN_PACKAGE_NAME) {
            return windowClassName == DOUYIN_MAIN_ACTIVITY
        }
        return true
    }

    private fun canUseOcrFallback(packageName: String, windowClassName: String): Boolean {
        return canInspectPackageScreen(packageName, windowClassName)
    }

    private fun shouldPreferOcr(packageName: String, windowClassName: String): Boolean {
        return packageName == DOUYIN_PACKAGE_NAME && windowClassName == DOUYIN_MAIN_ACTIVITY
    }

    private fun String.looksLikeActivityName(): Boolean {
        return endsWith("Activity") || contains(".Activity")
    }

    private fun recordInspectionDecisionIfNeeded(
        packageName: String,
        windowClassName: String,
        allowed: Boolean,
    ) {
        if (packageName != DOUYIN_PACKAGE_NAME) return
        val fingerprint = "$packageName|$windowClassName|$allowed"
        if (fingerprint == lastInspectionDecisionFingerprint) return
        lastInspectionDecisionFingerprint = fingerprint
        recordOperation(
            "检测触发判断",
            "App=$packageName Activity=${windowClassName.ifBlank { "未识别" }} 允许检测=${if (allowed) "是" else "否"}",
        )
    }

    private fun recordOcrDecisionIfNeeded(
        packageName: String,
        windowClassName: String,
        accessibilityTextLength: Int,
        allowed: Boolean,
    ) {
        if (packageName != DOUYIN_PACKAGE_NAME) return
        val fingerprint = "$packageName|$windowClassName|$accessibilityTextLength|$allowed"
        if (fingerprint == lastOcrDecisionFingerprint) return
        lastOcrDecisionFingerprint = fingerprint
        recordOperation(
            "OCR触发判断",
            "App=$packageName Activity=${windowClassName.ifBlank { "未识别" }} 无障碍文本长度=$accessibilityTextLength 允许OCR=${if (allowed) "是" else "否"}",
        )
    }

    private fun recordRecognitionModeDecision(
        mode: RecognitionMode,
        accessibilityVideo: ParsedVideoLog?,
        ocrVideo: ParsedVideoLog?,
    ) {
        recordOperation(
            "识别方案",
            "方案=${mode.label} 无障碍分=${accessibilityVideo?.qualityScore().orZero()} " +
                "OCR分=${ocrVideo?.qualityScore().orZero()} " +
                "最终候选=${listOfNotNull(accessibilityVideo, ocrVideo).maxByOrNull { it.qualityScore() }?.source ?: "无"}",
        )
    }

    private fun ParsedVideoLog?.isLowQualityCaption(): Boolean {
        return this == null || qualityScore() < OCR_ACCEPTABLE_SCORE || title.isBlank()
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

    private companion object {
        const val DOUYIN_PACKAGE_NAME = "com.ss.android.ugc.aweme"
        const val DOUYIN_MAIN_ACTIVITY = "com.ss.android.ugc.aweme.main.MainActivity"
        const val OCR_MAX_ATTEMPTS = 4
        const val OCR_SAMPLE_INTERVAL_MILLIS = 280L
        const val OCR_SCAN_WINDOW_MILLIS = 2_200L
        const val OCR_ACCEPTABLE_SCORE = 8
        const val OCR_GOOD_ENOUGH_SCORE = 10
        const val CAPTION_TOP_RATIO = 0.60f
        const val CAPTION_BOTTOM_RATIO = 0.97f
        const val CAPTION_RIGHT_RATIO = 0.90f
        const val CAPTION_TEXT_LEFT_EDGE_MAX_RATIO = 0.32f
        const val CAPTION_TEXT_BOTTOM_LIMIT_RATIO = 0.965f
    }
}
