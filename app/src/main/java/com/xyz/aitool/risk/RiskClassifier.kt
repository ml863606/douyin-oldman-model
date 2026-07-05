package com.xyz.aitool.risk

import com.xyz.aitool.data.CustomRule
import com.xyz.aitool.data.ParsedVideoLog
import com.xyz.aitool.data.RuleTarget

data class RiskResult(
    val score: Int,
    val matchedRules: List<String>,
)

object RiskClassifier {
    private data class Rule(val keyword: String, val weight: Int)

    private val rules = listOf(
        Rule("ai副业", 4),
        Rule("ai赚钱", 4),
        Rule("deepseek赚钱", 4),
        Rule("chatgpt赚钱", 4),
        Rule("一键变现", 4),
        Rule("零基础赚钱", 4),
        Rule("免费领取", 3),
        Rule("扫码进群", 3),
        Rule("加群领取", 3),
        Rule("进群", 2),
        Rule("保姆级教程", 2),
        Rule("副业项目", 3),
        Rule("每天收益", 3),
        Rule("月入", 3),
        Rule("躺赚", 4),
        Rule("不用露脸", 2),
        Rule("无人直播", 3),
        Rule("自动剪辑", 2),
        Rule("矩阵号", 2),
        Rule("私信领取", 3),
        Rule("资料包", 2),
    )

    fun classify(text: String): RiskResult? {
        val normalized = text
            .lowercase()
            .replace(Regex("\\s+"), "")

        if (normalized.isBlank()) return null

        val matched = rules.filter { normalized.contains(it.keyword.lowercase()) }
        var score = matched.sumOf { it.weight }
        val labels = matched.map { it.keyword }.toMutableList()

        val mentionsAi = listOf("ai", "deepseek", "chatgpt", "人工智能", "大模型").any(normalized::contains)
        val mentionsMoney = listOf("赚钱", "副业", "变现", "收益", "月入", "项目").any(normalized::contains)
        val mentionsLeadGen = listOf("进群", "私信", "领取", "扫码", "加我").any(normalized::contains)

        if (mentionsAi && mentionsMoney) {
            score += 3
            labels += "AI+赚钱/副业"
        }
        if (mentionsAi && mentionsLeadGen) {
            score += 2
            labels += "AI+引流"
        }
        if (mentionsMoney && mentionsLeadGen) {
            score += 2
            labels += "赚钱+引流"
        }

        return if (score >= 4) {
            RiskResult(score = score, matchedRules = labels.distinct())
        } else {
            null
        }
    }

    fun classify(video: ParsedVideoLog, customRules: List<CustomRule>): RiskResult? {
        val builtInResult = classify(video.rawText)
        var score = builtInResult?.score ?: 0
        val labels = builtInResult?.matchedRules.orEmpty().toMutableList()

        customRules
            .filter { it.enabled && it.keyword.isNotBlank() }
            .forEach { rule ->
                val keyword = rule.keyword.normalize()
                val matched = when (rule.target) {
                    RuleTarget.TITLE -> video.title.normalize().contains(keyword)
                    RuleTarget.TAG -> video.tags.any { tag ->
                        val normalizedTag = tag.normalize()
                        val normalizedTagWithoutHash = normalizedTag.removePrefix("#")
                        normalizedTag.contains(keyword.removePrefix("#")) ||
                            normalizedTagWithoutHash.contains(keyword.removePrefix("#"))
                    }
                    RuleTarget.TEXT -> video.searchableText().normalize().contains(keyword.removePrefix("#"))
                }
                if (matched) {
                    score += 5
                    labels += "${rule.target.label}包含：${rule.keyword}"
                }
            }

        return if (score >= 4) {
            RiskResult(score = score, matchedRules = labels.distinct())
        } else {
            null
        }
    }

    private fun String.normalize(): String {
        return lowercase().replace(Regex("\\s+"), "")
    }

    private fun ParsedVideoLog.searchableText(): String {
        return listOf(title, content, tags.joinToString(" "), rawText)
            .joinToString("\n")
    }
}
