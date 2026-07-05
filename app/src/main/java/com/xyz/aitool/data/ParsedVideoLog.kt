package com.xyz.aitool.data

data class ParsedVideoLog(
    val id: Long,
    val timeMillis: Long,
    val packageName: String,
    val appLabel: String,
    val title: String,
    val content: String,
    val tags: List<String>,
    val rawText: String,
    val source: String,
)
