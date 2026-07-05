package com.xyz.aitool.data

object VideoTextParser {
    private val tagRegex = Regex("#[\\p{IsHan}A-Za-z0-9_\\-]+")
    private val separatorRegex = Regex("[、，,。；;：:\\s]+")
    private val noisyLines = setOf(
        "首页",
        "朋友",
        "消息",
        "我",
        "关注",
        "推荐",
        "精选",
        "搜索",
        "评论",
        "分享",
        "收藏",
        "转发",
        "点赞",
        "抖音商城",
        "解析日志",
        "最近命中",
        "自定义命中规则",
        "App",
        "清空",
        "未解析到",
        "无障碍文本",
        "截图OCR",
    )

    fun parse(
        rawText: String,
        source: String,
        packageName: String,
        appLabel: String,
        now: Long = System.currentTimeMillis(),
    ): ParsedVideoLog? {
        val lines = rawText
            .lineSequence()
            .map { it.cleanLine() }
            .filter { it.length >= 2 }
            .filterNot { it in noisyLines }
            .distinct()
            .toList()

        if (lines.isEmpty()) return null

        val joined = lines.joinToString("\n")
        val tags = tagRegex.findAll(joined)
            .map { it.value.trimEndPunctuation() }
            .filter { it.length > 1 }
            .distinct()
            .toList()

        val contentLines = lines
            .map { line -> tagRegex.replace(line, "").cleanLine() }
            .filterNot { it.isNoiseLine() }
            .distinct()

        val title = contentLines
            .firstOrNull { line -> line.length >= 4 }
            ?: contentLines.firstOrNull().orEmpty()

        val content = contentLines
            .filter { it != title }
            .joinToString("\n")

        return ParsedVideoLog(
            id = now,
            timeMillis = now,
            packageName = packageName,
            appLabel = appLabel,
            title = title.take(120),
            content = content.take(500),
            tags = tags,
            rawText = joined.take(1000),
            source = source,
        )
    }

    private fun String.cleanLine(): String {
        return replace(Regex("[`｀´'‘’“”]+"), "")
            .replace(Regex("[、，,。；;\\s]{2,}"), "、")
            .trim()
            .trimEndPunctuation()
    }

    private fun String.trimEndPunctuation(): String {
        return trim()
            .trim('、', '，', ',', '。', ';', '；', ':', '：', '.', '…', ' ', '\t')
    }

    private fun String.isNoiseLine(): Boolean {
        val compact = replace(separatorRegex, "")
        return isBlank() ||
            compact.isBlank() ||
            compact in noisyLines ||
            compact == "标签" ||
            compact == "标题" ||
            compact == "内容" ||
            compact == "App" ||
            startsWith("标签") ||
            startsWith("标题") ||
            startsWith("内容") ||
            startsWith("com.") ||
            contains("还没有命中记录") ||
            contains("开启无障碍服务") ||
            all { it.isDigit() } ||
            matches(Regex("[\\d.万wW]+")) ||
            length <= 2 && any { it.isDigit() }
    }
}
