package com.xyz.aitool.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
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

    @Test
    fun parserTreatsAtLineAsAuthorAndNextLineAsTitle() {
        val result = VideoTextParser.parse(
            rawText = """
                @柠宝妈妈要努力
                不知道要怎么做了 v
                未点赞，喜欢432，按钮
                评论117，按钮
                未选中，收藏7，按钮
                分享1，按钮
                拍同款
            """.trimIndent(),
            source = "截图OCR",
            packageName = "com.ss.android.ugc.aweme",
            appLabel = "抖音",
        )

        assertNotNull(result)
        assertEquals("@柠宝妈妈要努力", result!!.author)
        assertEquals("不知道要怎么做了", result.title)
        assertEquals("", result.content)
        assertFalse(result.title.startsWith("@"))
    }

    @Test
    fun parserKeepsCaptionAfterAuthorAndExtractsTags() {
        val result = VideoTextParser.parse(
            rawText = """
                @若风.
                他们说：喜欢拍花的人，内心都装满了温柔和对生活的向往 #inmyfeelings 展开⌄
            """.trimIndent(),
            source = "截图OCR",
            packageName = "com.ss.android.ugc.aweme",
            appLabel = "抖音",
        )

        assertNotNull(result)
        assertEquals("@若风.", result!!.author)
        assertTrue(result.title.startsWith("他们说"))
        assertEquals(listOf("#inmyfeelings"), result.tags)
    }

    @Test
    fun parserIgnoresOwnOverlayTextWhenDouyinCaptionIsStillVisible() {
        val result = VideoTextParser.parse(
            rawText = """
                运行状态 规则设置 最近命中 记录日志
                还没有解析日志。打开已选择的 App 后，新视频会记录标题、内容和标签。
                OCR触发判断
                App=com.ss.android.ugc.aweme Activity=com.ss.android.ugc.aweme.main.MainActivity 无障碍文本长度=246 允许OCR=是
                @摄影师星海📷
                摄影师开智前vs开智后 #自由感溢出屏幕 #世界这本书要多读几页 ... 展开⌄
                去汽水听 Tok It (HI Music) - Beatrixix
            """.trimIndent(),
            source = "截图OCR",
            packageName = "com.ss.android.ugc.aweme",
            appLabel = "抖音",
        )

        assertNotNull(result)
        assertEquals("@摄影师星海📷", result!!.author)
        assertEquals("摄影师开智前vs开智后", result.title)
        assertEquals(listOf("#自由感溢出屏幕", "#世界这本书要多读几页"), result.tags)
    }

    @Test
    fun parserKeepsLowBottomCaptionAfterAuthor() {
        val result = VideoTextParser.parse(
            rawText = """
                @玉上泉
                第 8 集｜欲买桂花同载酒，终不似，少年游#书法#诗词#语文#配音... 展开⌄
                首页
                朋友
                消息
                我
            """.trimIndent(),
            source = "无障碍坐标",
            packageName = "com.ss.android.ugc.aweme",
            appLabel = "抖音",
        )

        assertNotNull(result)
        assertEquals("@玉上泉", result!!.author)
        assertEquals("第 8 集｜欲买桂花同载酒，终不似，少年游", result.title)
        assertEquals(listOf("#书法", "#诗词", "#语文", "#配音"), result.tags)
    }
}
