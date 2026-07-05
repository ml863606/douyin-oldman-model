package com.xyz.aitool.data

data class OperationLog(
    val id: Long,
    val timeMillis: Long,
    val action: String,
    val message: String,
)
