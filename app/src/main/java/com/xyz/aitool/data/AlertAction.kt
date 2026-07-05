package com.xyz.aitool.data

enum class AlertAction(val label: String, val description: String, val confirmText: String) {
    STRONG_REMINDER(
        label = "强提醒",
        description = "命中后必须手动确认，确认后自动跳到下一个视频。",
        confirmText = "我知道了，跳过",
    ),
    SKIP_AFTER_CONFIRM(
        label = "跳过",
        description = "命中后必须手动确认，确认后自动跳过当前视频。",
        confirmText = "确定并跳过",
    ),
    NOT_INTERESTED(
        label = "不感兴趣",
        description = "命中后必须手动确认，确认后自动长按视频并点击“不感兴趣”。",
        confirmText = "自动点不感兴趣",
    ),
}
