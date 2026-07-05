package com.xyz.aitool.data

object VideoTextParser {
    private val tagRegex = Regex("#[\\p{IsHan}A-Za-z0-9_\\-]+")
    private val separatorRegex = Regex("[、，,。；;：:\\s]+")
    private val expandHintRegex = Regex("\\s*(展开|全文)?\\s*[vV∨⌄﹀˅]+$")
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
        "AI视频提醒助手",
        "运行状态",
        "规则设置",
        "解析日志",
        "操作日志",
        "最近命中",
        "记录日志",
        "自定义命中规则",
        "App选择器",
        "风险规则",
        "命中后的处理",
        "警告框大小",
        "风险弹窗字体",
        "Debug模式",
        "清空记录日志",
        "App",
        "清空",
        "未解析到",
        "无障碍文本",
        "截图OCR",
        "去汽水听",
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

        val authorIndex = lines.indexOfFirst { it.isAuthorLine() }
        val author = lines.getOrNull(authorIndex)
            ?.cleanAuthorLine()
            .orEmpty()

        val parsedLines = lines
            .mapIndexedNotNull { index, line ->
                if (index == authorIndex || line.isAuthorLine()) {
                    null
                } else {
                    val text = line.toContentCandidate()
                    if (text.isNoiseLine()) null else ParsedLine(index, text)
                }
            }
            .distinctBy { it.text }

        val titleCandidates = if (authorIndex >= 0) {
            parsedLines.filter { it.index > authorIndex }
        } else {
            parsedLines
        }
        val title = titleCandidates
            .firstOrNull { line -> line.text.length >= 2 }
            ?.text
            ?: parsedLines.firstOrNull { line -> line.text.length >= 4 }?.text
            ?: parsedLines.firstOrNull()?.text.orEmpty()

        val content = titleCandidates
            .map { it.text }
            .filter { it != title }
            .joinToString("\n")

        return ParsedVideoLog(
            id = now,
            timeMillis = now,
            packageName = packageName,
            appLabel = appLabel,
            author = author.take(80),
            title = title.take(120),
            content = content.take(500),
            tags = tags,
            rawText = joined.take(1000),
            source = source,
        )
    }

    private data class ParsedLine(val index: Int, val text: String)

    private fun String.toContentCandidate(): String {
        return tagRegex.replace(this, "")
            .cleanLine()
            .stripExpandHint()
            .stripTrailingCaptionDecoration()
            .cleanLine()
    }

    private fun String.isAuthorLine(): Boolean {
        val cleaned = trim()
        return cleaned.startsWith("@") && cleaned.length > 1
    }

    private fun String.cleanAuthorLine(): String {
        return replace(Regex("[`｀´'‘’“”]+"), "")
            .stripExpandHint()
            .trim()
            .split(Regex("\\s+"), limit = 2)
            .firstOrNull()
            .orEmpty()
            .trim('、', '，', ',', '。', ';', '；', ':', '：', '…', ' ', '\t')
    }

    private fun String.stripExpandHint(): String {
        return replace(expandHintRegex, "")
            .removeSuffix("展开")
            .trim()
    }

    private fun String.stripTrailingCaptionDecoration(): String {
        return replace(Regex("[、，,\\s]*(\\.{2,}|…+)$"), "")
            .trim()
    }

    private fun String.cleanLine(): String {
        return replace(Regex("[`｀´'‘’“”]+"), "")
            .replace(Regex("[、，,。；;\\s]{2,}"), "、")
            .trim()
            .trimEndPunctuation()
    }

    private fun String.trimEndPunctuation(): String {
        return trim()
            .trim('、', '，', ',', '。', ';', '；', ':', '：', '…', ' ', '\t')
    }

    private fun String.isNoiseLine(): Boolean {
        val compact = replace(separatorRegex, "")
        val ownTabSignalCount = listOf("运行状态", "规则设置", "最近命中", "记录日志").count { contains(it) }
        return isBlank() ||
            compact.isBlank() ||
            compact in noisyLines ||
            ownTabSignalCount >= 2 ||
            compact == "标签" ||
            compact == "标题" ||
            compact == "内容" ||
            compact == "App" ||
            startsWith("标签") ||
            startsWith("标题") ||
            startsWith("内容") ||
            startsWith("com.") ||
            startsWith("App=") ||
            startsWith("Activity=") ||
            startsWith("OCR触发判断") ||
            startsWith("检测触发判断") ||
            contains("还没有命中记录") ||
            contains("还没有解析日志") ||
            contains("开启无障碍服务") ||
            contains("无障碍文本长度") ||
            contains("允许OCR") ||
            contains("当前屏幕可见文字") ||
            contains("屏幕底部") ||
            contains("OCR识别耗时") ||
            contains("识别范围") ||
            startsWith("去汽水听") ||
            compact.startsWith("未点赞") ||
            compact.startsWith("未选中") ||
            compact == "拍同款" ||
            compact.matches(Regex("^(评论|收藏|分享|点赞|喜欢)\\d+[万wW]?$")) ||
            contains("按钮") && listOf("喜欢", "评论", "收藏", "分享", "点赞", "未选中", "未点赞").any(compact::contains) ||
            all { it.isDigit() } ||
            matches(Regex("[\\d.万wW]+")) ||
            length <= 2 && any { it.isDigit() }
    }
}
