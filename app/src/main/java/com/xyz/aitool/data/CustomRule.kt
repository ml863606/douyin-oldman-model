package com.xyz.aitool.data

enum class RuleTarget(val label: String) {
    TITLE("标题"),
    TAG("标签"),
    TEXT("文本"),
}

data class CustomRule(
    val id: Long,
    val target: RuleTarget,
    val keyword: String,
    val enabled: Boolean = true,
)
