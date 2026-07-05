package com.xyz.aitool.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Test

class VideoTextParserTest {
    @Test
    fun parserDoesNotTreatTagPrefixAsTitle() {
        val result = VideoTextParser.parse(
            rawText = """
                标签： #真实生活分享计划、、、、#扣位、、、、#产品设计、、、、#AI工具测评
                #奶茶店...、、、、#英语磨耳朵、、、、#口语天天练
            """.trimIndent(),
            source = "测试",
            packageName = "com.test",
            appLabel = "测试",
        )

        assertNotNull(result)
        assertEquals("", result!!.title)
        assertFalse(result.tags.any { it.contains("、") })
        assertEquals(
            listOf("#真实生活分享计划", "#扣位", "#产品设计", "#AI工具测评", "#奶茶店", "#英语磨耳朵", "#口语天天练"),
            result.tags,
        )
    }

    @Test
    fun parserRemovesOwnLogUiNoise() {
        val result = VideoTextParser.parse(
            rawText = """
                解析日志
                清空
                App
                com.ss.android.ugc.aweme
                无障碍文本
                标题：还没有命中记录。开启无障碍服务后，打开抖音刷到疑似内容时这里会出现记录
                标签：未解析到
                内容：解析日志
            """.trimIndent(),
            source = "测试",
            packageName = "com.ss.android.ugc.aweme",
            appLabel = "抖音",
        )

        assertNotNull(result)
        assertEquals("", result!!.title)
        assertEquals("", result.content)
        assertEquals(emptyList<String>(), result.tags)
    }
}
