package com.example.domain

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class ApiLog(
    val id: String = java.util.UUID.randomUUID().toString(),
    val timestamp: String = SimpleDateFormat("HH:mm:ss.SSS", Locale.getDefault()).format(Date()),
    val method: String,
    val endpoint: String,
    val requestBody: String,
    val responseCode: Int,
    val responseBody: String,
    val latencyMs: Long
)
