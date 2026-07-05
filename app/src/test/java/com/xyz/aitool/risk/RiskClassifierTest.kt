package com.xyz.aitool.risk

import com.xyz.aitool.data.CustomRule
import com.xyz.aitool.data.ParsedVideoLog
import com.xyz.aitool.data.RuleTarget
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class RiskClassifierTest {
    @Test
    fun flagsAiMoneyAndLeadGen() {
        val result = RiskClassifier.classify("AI副业一键变现，扫码进群免费领取资料")

        assertNotNull(result)
    }

    @Test
    fun allowsPlainAiEducation() {
        val result = RiskClassifier.classify("今天讲一下大模型的上下文窗口和推理能力")

        assertNull(result)
    }

    @Test
    fun flagsCustomTitleRule() {
        val result = RiskClassifier.classify(
            ParsedVideoLog(
                id = 1,
                timeMillis = 1,
                packageName = "com.test",
                appLabel = "测试",
                title = "别让爸妈再刷这种内容",
                content = "普通正文",
                tags = listOf("#生活"),
                rawText = "别让爸妈再刷这种内容\n#生活",
                source = "测试",
            ),
            listOf(CustomRule(id = 2, target = RuleTarget.TITLE, keyword = "爸妈")),
        )

        assertNotNull(result)
    }

    @Test
    fun flagsCustomTagRule() {
        val result = RiskClassifier.classify(
            ParsedVideoLog(
                id = 1,
                timeMillis = 1,
                packageName = "com.test",
                appLabel = "测试",
                title = "普通标题",
                content = "普通正文",
                tags = listOf("#AI副业避坑"),
                rawText = "普通标题\n#AI副业避坑",
                source = "测试",
            ),
            listOf(CustomRule(id = 2, target = RuleTarget.TAG, keyword = "副业避坑")),
        )

        assertNotNull(result)
    }

    @Test
    fun flagsCustomTagRuleWithoutHash() {
        val result = RiskClassifier.classify(
            ParsedVideoLog(
                id = 1,
                timeMillis = 1,
                packageName = "com.test",
                appLabel = "测试",
                title = "普通标题",
                content = "普通正文",
                tags = listOf("#真实生活"),
                rawText = "普通标题\n#真实生活",
                source = "测试",
            ),
            listOf(CustomRule(id = 2, target = RuleTarget.TAG, keyword = "真实生活")),
        )

        assertNotNull(result)
    }

    @Test
    fun flagsCustomTextRuleFromRawText() {
        val result = RiskClassifier.classify(
            ParsedVideoLog(
                id = 1,
                timeMillis = 1,
                packageName = "com.test",
                appLabel = "测试",
                title = "普通标题",
                content = "",
                tags = emptyList(),
                rawText = "45一份，太贵了，我一口没吃。#记录真实生活",
                source = "测试",
            ),
            listOf(CustomRule(id = 2, target = RuleTarget.TEXT, keyword = "太贵了")),
        )

        assertNotNull(result)
    }
}
