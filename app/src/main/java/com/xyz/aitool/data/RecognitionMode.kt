package com.xyz.aitool.data

enum class RecognitionMode(val label: String, val description: String) {
    AUTO(
        label = "自动",
        description = "优先用无障碍坐标，质量不足时再用 OCR 坐标。",
    ),
    ACCESSIBILITY_COORDINATE(
        label = "无障碍坐标",
        description = "只读取目标 App 左下文案区域的可见节点，速度最快。",
    ),
    OCR_COORDINATE(
        label = "OCR坐标",
        description = "按 OCR 行坐标解析左下文案区域，适合无障碍节点不完整的视频。",
    ),
    PP_OCRV6(
        label = "PP-OCRv6",
        description = "使用 PP-OCRv6 识别屏幕底部文案区域；未打包模型时会自动降级到 ML Kit。",
    ),
}
