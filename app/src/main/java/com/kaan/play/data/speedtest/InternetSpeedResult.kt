package com.kaan.play.data.speedtest

data class InternetSpeedResult(
    val downloadMbps: Double,
    val bytesDownloaded: Long,
    val durationMs: Long = 0L,
    val pingMs: Long = 0L,
)
