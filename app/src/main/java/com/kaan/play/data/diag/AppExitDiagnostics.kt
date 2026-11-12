package com.kaan.play.data.diag

import android.app.ActivityManager
import android.content.Context
import android.os.Build
import androidx.annotation.RequiresApi

enum class ExitCategory {
    Anr,
    Crash,
    NativeCrash,
    InitFailure,
    ExcessiveResource,
    LowMemory,
    SystemOrOther,
}

enum class ExitAttribution {
    App,
    System,
}

data class ProcessExitRecord(
    val timestampMs: Long,
    val category: ExitCategory,
    val attribution: ExitAttribution,
)

data class AppExitSnapshot(
    val supported: Boolean,
    val exits: List<ProcessExitRecord>,
)

/**
 * Android 11+ (API 30) süreç kapanma nedenlerini saf Kotlin'de sınıflandırır.
 * Normal çıkışlar (kullanıcı kapattı, güncelleme, kendi normal çıkışı) gösterilmez.
 */
object AppExitClassifier {
    const val MAX_EXITS = 5

    // ApplicationExitInfo.REASON_* değerleri (Android 11 / API 30 SDK sabitleri)
    const val REASON_UNKNOWN = 0
    const val REASON_EXIT_SELF = 1
    const val REASON_SIGNALED = 2
    const val REASON_LOW_MEMORY = 3
    const val REASON_CRASH = 4
    const val REASON_CRASH_NATIVE = 5
    const val REASON_ANR = 6
    const val REASON_INITIALIZATION_FAILURE = 7
    const val REASON_PERMISSION_CHANGE = 8
    const val REASON_EXCESSIVE_RESOURCE_USAGE = 9
    const val REASON_USER_REQUESTED = 10
    const val REASON_USER_STOPPED = 11
    const val REASON_DEPENDENCY_DIED = 12
    const val REASON_OTHER = 13
    const val REASON_FREEZER = 14
    const val REASON_PACKAGE_STATE_CHANGE = 15
    const val REASON_PACKAGE_UPDATED = 16

    fun classify(reason: Int, timestampMs: Long): ProcessExitRecord? {
        val (category, attribution) = when (reason) {
            REASON_EXIT_SELF,
            REASON_PERMISSION_CHANGE,
            REASON_USER_REQUESTED,
            REASON_USER_STOPPED,
            REASON_PACKAGE_STATE_CHANGE,
            REASON_PACKAGE_UPDATED -> return null

            REASON_ANR -> ExitCategory.Anr to ExitAttribution.App
            REASON_CRASH -> ExitCategory.Crash to ExitAttribution.App
            REASON_CRASH_NATIVE -> ExitCategory.NativeCrash to ExitAttribution.App
            REASON_INITIALIZATION_FAILURE -> ExitCategory.InitFailure to ExitAttribution.App
            REASON_EXCESSIVE_RESOURCE_USAGE -> ExitCategory.ExcessiveResource to ExitAttribution.App

            REASON_LOW_MEMORY -> ExitCategory.LowMemory to ExitAttribution.System
            REASON_SIGNALED,
            REASON_DEPENDENCY_DIED,
            REASON_FREEZER,
            REASON_OTHER,
            REASON_UNKNOWN -> ExitCategory.SystemOrOther to ExitAttribution.System

            else -> ExitCategory.SystemOrOther to ExitAttribution.System
        }
        return ProcessExitRecord(
            timestampMs = timestampMs,
            category = category,
            attribution = attribution,
        )
    }

    fun filterRecentAbnormal(
        rawReasons: List<Pair<Int, Long>>,
        limit: Int = MAX_EXITS,
    ): List<ProcessExitRecord> =
        rawReasons
            .mapNotNull { (reason, ts) -> classify(reason, ts) }
            .sortedByDescending { it.timestampMs }
            .take(limit)
}

object AppExitDiagnostics {
    private const val RAW_FETCH_LIMIT = 25

    fun isSupported(sdkInt: Int = Build.VERSION.SDK_INT): Boolean =
        sdkInt >= Build.VERSION_CODES.R

    fun read(context: Context): AppExitSnapshot {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) {
            return AppExitSnapshot(supported = false, exits = emptyList())
        }
        return try {
            val am = context.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager
                ?: return AppExitSnapshot(supported = true, exits = emptyList())
            val rawReasons = readRawReasonsApi30(am, context.packageName)
            val mapped = AppExitClassifier.filterRecentAbnormal(rawReasons)
            AppExitSnapshot(supported = true, exits = mapped)
        } catch (_: Throwable) {
            AppExitSnapshot(supported = true, exits = emptyList())
        }
    }

    @RequiresApi(Build.VERSION_CODES.R)
    private fun readRawReasonsApi30(am: ActivityManager, packageName: String): List<Pair<Int, Long>> =
        am.getHistoricalProcessExitReasons(packageName, 0, RAW_FETCH_LIMIT)
            .map { info -> info.reason to info.timestamp }
}
