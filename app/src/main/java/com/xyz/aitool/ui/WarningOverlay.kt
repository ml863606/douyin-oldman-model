package com.xyz.aitool.ui

import android.graphics.PixelFormat
import android.accessibilityservice.AccessibilityService
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import com.xyz.aitool.data.AlertSize

enum class WarningOverlayAction(val label: String) {
    DEFAULT_SKIP("默认按钮"),
    SWIPE_UP_SHORT("上滑短"),
    SWIPE_UP_LONG("上滑长"),
    SWIPE_DOWN("下滑"),
    SCROLL_FORWARD("节点滚动"),
}

class WarningOverlay(private val service: AccessibilityService) {
    private val handler = Handler(Looper.getMainLooper())
    private val windowManager = service.getSystemService(WindowManager::class.java)
    private var currentView: View? = null

    val isShowing: Boolean
        get() = currentView != null

    fun show(
        message: String,
        detail: String,
        confirmText: String,
        alertSize: AlertSize,
        messageTextSizeSp: Int,
        debugEnabled: Boolean,
        onAction: (WarningOverlayAction) -> Unit,
    ) {
        handler.post {
            hide()

            val root = LinearLayout(service).apply {
                orientation = LinearLayout.VERTICAL
                gravity = Gravity.CENTER
                setPadding(
                    alertSize.rootPadding(),
                    alertSize.rootPadding(),
                    alertSize.rootPadding(),
                    alertSize.rootPadding(),
                )
                setBackgroundColor(Color.argb(168, 0, 0, 0))
            }

            val container = LinearLayout(service).apply {
                orientation = LinearLayout.VERTICAL
                gravity = Gravity.CENTER
                setPadding(42, 36, 42, 36)
                background = GradientDrawable().apply {
                    cornerRadius = 28f
                    setColor(Color.rgb(127, 29, 29))
                    setStroke(4, Color.rgb(254, 202, 202))
                }
            }

            container.addView(TextView(service).apply {
                text = "!!! 风险内容警告 !!!"
                setTextColor(Color.WHITE)
                textSize = (messageTextSizeSp + 4).toFloat()
                typeface = Typeface.DEFAULT_BOLD
                gravity = Gravity.CENTER
            })
            container.addView(TextView(service).apply {
                text = message
                setTextColor(Color.WHITE)
                textSize = messageTextSizeSp.toFloat()
                typeface = Typeface.DEFAULT_BOLD
                setPadding(0, 28, 0, if (debugEnabled) 16 else 8)
                gravity = Gravity.CENTER
            })
            if (debugEnabled) {
                container.addView(TextView(service).apply {
                    text = detail
                    setTextColor(Color.rgb(254, 226, 226))
                    textSize = 15f
                    maxLines = 4
                    gravity = Gravity.CENTER
                })
            }
            container.addActionButton(
                text = confirmText,
                action = WarningOverlayAction.DEFAULT_SKIP,
                topMargin = 30,
                primary = true,
                onAction = onAction,
            )
            if (debugEnabled) {
                container.addView(TextView(service).apply {
                    text = "如果默认跳不过，请依次测试下面按钮"
                    setTextColor(Color.rgb(254, 226, 226))
                    textSize = 13f
                    gravity = Gravity.CENTER
                    setPadding(0, 18, 0, 8)
                })
                container.addButtonRow(
                    firstText = "上滑短",
                    firstAction = WarningOverlayAction.SWIPE_UP_SHORT,
                    secondText = "上滑长",
                    secondAction = WarningOverlayAction.SWIPE_UP_LONG,
                    onAction = onAction,
                )
                container.addButtonRow(
                    firstText = "下滑",
                    firstAction = WarningOverlayAction.SWIPE_DOWN,
                    secondText = "节点滚动",
                    secondAction = WarningOverlayAction.SCROLL_FORWARD,
                    onAction = onAction,
                )
            }
            root.addView(
                container,
                LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    alertSize.containerHeight(),
                ),
            )

            val params = WindowManager.LayoutParams(
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
                PixelFormat.TRANSLUCENT,
            ).apply {
                gravity = Gravity.CENTER
            }

            windowManager.addView(root, params)
            currentView = root
        }
    }

    fun hide() {
        currentView?.let { view ->
            runCatching { windowManager.removeView(view) }
        }
        currentView = null
    }

    private fun LinearLayout.addButtonRow(
        firstText: String,
        firstAction: WarningOverlayAction,
        secondText: String,
        secondAction: WarningOverlayAction,
        onAction: (WarningOverlayAction) -> Unit,
    ) {
        val row = LinearLayout(service).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
        }
        row.addActionButton(firstText, firstAction, topMargin = 0, primary = false, onAction = onAction, weight = 1f)
        row.addActionButton(secondText, secondAction, topMargin = 0, primary = false, onAction = onAction, weight = 1f)
        addView(
            row,
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT,
            ).apply {
                topMargin = 10
            },
        )
    }

    private fun LinearLayout.addActionButton(
        text: String,
        action: WarningOverlayAction,
        topMargin: Int,
        primary: Boolean,
        onAction: (WarningOverlayAction) -> Unit,
        weight: Float = 0f,
    ) {
        addView(
            Button(service).apply {
                this.text = text
                textSize = if (primary) 20f else 15f
                setTextColor(Color.rgb(127, 29, 29))
                typeface = Typeface.DEFAULT_BOLD
                setPadding(16, 10, 16, 10)
                setOnClickListener {
                    hide()
                    handler.postDelayed({ onAction(action) }, 260L)
                }
            },
            LinearLayout.LayoutParams(
                if (weight > 0f) 0 else LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT,
                weight,
            ).apply {
                this.topMargin = topMargin
                leftMargin = if (weight > 0f) 5 else 0
                rightMargin = if (weight > 0f) 5 else 0
            },
        )
    }

    private fun AlertSize.rootPadding(): Int {
        return when (this) {
            AlertSize.FULLSCREEN -> 22
            AlertSize.LARGE -> 44
            AlertSize.COMPACT -> 86
        }
    }

    private fun AlertSize.containerHeight(): Int {
        return when (this) {
            AlertSize.FULLSCREEN -> LinearLayout.LayoutParams.MATCH_PARENT
            AlertSize.LARGE,
            AlertSize.COMPACT -> LinearLayout.LayoutParams.WRAP_CONTENT
        }
    }
}
