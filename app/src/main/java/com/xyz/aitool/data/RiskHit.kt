package com.xyz.aitool.data

data class RiskHit(
    val id: Long,
    val timeMillis: Long,
    val text: String,
    val matchedRules: List<String>,
    val score: Int,
    val source: String,
)
