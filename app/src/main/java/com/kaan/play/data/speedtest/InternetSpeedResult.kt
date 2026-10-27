package com.kaan.play.data.speedtest

data class InternetSpeedResult(
    val downloadMbps: Double,
    val pingMs: Long,
    val bytesDownloaded: Long,
)
