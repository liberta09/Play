package com.kaan.play

import android.app.Activity
import android.app.UiModeManager
import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.os.Build
import android.util.Log
import java.io.PrintWriter
import java.io.StringWriter

/**
 * TV kutularında pencere açılışında setContent çökmelerine karşı kalkan ve teşhis yöneticisi.
 */
object StartupWindowGuard {

    const val EXTRA_SAFE_WINDOW = StartupGuardRules.EXTRA_SAFE_WINDOW
    const val EXTRA_DEBUG_FAIL_SETCONTENT = "play_debug_fail_setcontent"

    fun getDebugFailCount(intent: Intent?): Int {
        if (intent == null) return 0
        val intVal = intent.getIntExtra(EXTRA_DEBUG_FAIL_SETCONTENT, -1)
        if (intVal >= 0) return intVal
        val strVal = intent.getStringExtra(EXTRA_DEBUG_FAIL_SETCONTENT)
        return strVal?.toIntOrNull() ?: 0
    }

    /**
     * Yalnızca debug derlemede: play_debug_fail_setcontent > 0 ise kasıtlı RuntimeException fırlatır.
     * Yayın derlemesinde (BuildConfig.DEBUG == false) yok sayılır.
     */
    fun checkDebugFailSetContent(intent: Intent?) {
        if (!BuildConfig.DEBUG) return
        val count = getDebugFailCount(intent)
        if (count > 0) {
            throw RuntimeException("Window couldn't find content container view (play_debug_fail_setcontent=$count)")
        }
    }

    /**
     * Pencere teşhis verilerini toplar.
     * Güvenlik: Intent ekstraları, adresler, kullanıcı verisi ve belirteçler YAZILMAZ.
     */
    fun buildDiagnosisReport(activity: Activity, activityName: String, throwable: Throwable): Pair<String, String> {
        val errMsg = throwable.message ?: throwable.javaClass.simpleName
        val headerMsg = "[Pencere teşhisi] $activityName: $errMsg"

        val uiModeManager = activity.getSystemService(Context.UI_MODE_SERVICE) as? UiModeManager
        val isTv = uiModeManager?.currentModeType == Configuration.UI_MODE_TYPE_TELEVISION ||
            (activity.resources.configuration.uiMode and Configuration.UI_MODE_TYPE_MASK) == Configuration.UI_MODE_TYPE_TELEVISION

        var windowNoTitle = false
        var windowActionBar = false
        var windowIsFloating = false
        var windowTranslucent = false
        runCatching {
            val typedArray = activity.theme.obtainStyledAttributes(
                intArrayOf(
                    android.R.attr.windowNoTitle,
                    android.R.attr.windowActionBar,
                    android.R.attr.windowIsFloating,
                    android.R.attr.windowIsTranslucent,
                )
            )
            windowNoTitle = typedArray.getBoolean(0, false)
            windowActionBar = typedArray.getBoolean(1, false)
            windowIsFloating = typedArray.getBoolean(2, false)
            windowTranslucent = typedArray.getBoolean(3, false)
            typedArray.recycle()
        }

        val peekDecorNull = (activity.window.peekDecorView() == null)

        val locale = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            val locales = activity.resources.configuration.locales
            if (!locales.isEmpty) locales[0] else null
        } else {
            @Suppress("DEPRECATION")
            activity.resources.configuration.locale
        }
        val language = locale?.toLanguageTag() ?: locale?.toString() ?: "unknown"
        val fontScale = activity.resources.configuration.fontScale
        val densityDpi = activity.resources.configuration.densityDpi
        val isInMultiWindow = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            activity.isInMultiWindowMode
        } else {
            false
        }

        val intentSafe = activity.intent?.getBooleanExtra(EXTRA_SAFE_WINDOW, false) ?: false
        val storedSafe = StartupGuardRules.isSafeWindow(activity)
        val isSafeTheme = StartupGuardRules.shouldUseSafeTheme(intentSafe, storedSafe)
        val elapsedMs = PlayApp.getElapsedProcessTimeMs()
        val intentAction = activity.intent?.action ?: "null"
        val intentFlags = "0x" + Integer.toHexString(activity.intent?.flags ?: 0)

        val writer = StringWriter()
        throwable.printStackTrace(PrintWriter(writer))
        val stackTrace = writer.toString()

        val diag = buildString {
            append("Teşhis Detayları:\n")
            append("- Etkinlik: ").append(activityName).append("\n")
            append("- Cihaz: ").append(Build.MANUFACTURER).append(" ").append(Build.MODEL)
                .append(" (SDK ").append(Build.VERSION.SDK_INT).append(", Android ").append(Build.VERSION.RELEASE).append(")\n")
            append("- UI Modu: ").append(if (isTv) "TV" else "Standart").append(" (isTv=").append(isTv).append(")\n")
            append("- Güvenli tema: ").append(if (isSafeTheme) "evet" else "hayır").append("\n")
            append("- Tema Özellikleri: windowNoTitle=").append(windowNoTitle)
                .append(", windowActionBar=").append(windowActionBar)
                .append(", windowIsFloating=").append(windowIsFloating)
                .append(", windowTranslucent=").append(windowTranslucent).append("\n")
            append("- Decor Durumu: peekDecorView == null: ").append(peekDecorNull).append("\n")
            append("- Çeviri Dili: ").append(language).append("\n")
            append("- FontScale: ").append(fontScale).append(", DensityDpi: ").append(densityDpi).append("\n")
            append("- Çoklu Pencere: ").append(isInMultiWindow).append("\n")
            append("- İşlem Açılışından Geçen Süre: ").append(elapsedMs).append(" ms\n")
            append("- Intent Action: ").append(intentAction).append(", Flags: ").append(intentFlags).append("\n\n")
            append("Stack Trace:\n").append(stackTrace)
        }

        return headerMsg to diag
    }

    /**
     * setContent içinde RuntimeException veya NullPointerException yakalandığında çağrılır.
     * 1) Teşhisi bekleyen çökme raporuna ekler.
     * 2) StartupGuardRules kuralına göre RETRY ise animasyonsuz yeniden başlatıp finish() eder;
     *    GIVE_UP ise finishAndRemoveTask() ile sessizce kapanır.
     */
    fun handleStartupContentFailure(activity: Activity, activityName: String, throwable: Throwable) {
        val (headerMsg, diagReport) = buildDiagnosisReport(activity, activityName, throwable)
        Log.e("StartupWindowGuard", "$headerMsg\n$diagReport", throwable)

        // (i) Bekleyen çökme raporuna ekle
        CrashReporter.recordPendingReport(
            context = activity,
            message = headerMsg,
            stackTrace = diagReport
        )

        // (ii) & (iii) Kural kontrolü
        val decision = StartupGuardRules.recordFailureAndDecide(activity, activityName)
        if (decision == StartupGuardRules.Decision.RETRY) {
            StartupGuardRules.setSafeWindow(activity, true)
            Log.w("StartupWindowGuard", "Açılış kalkanı: YENİDEN DENE ($activityName)")
            val restartIntent = Intent(activity.intent).apply {
                setClass(activity, activity.javaClass)
                putExtra(EXTRA_SAFE_WINDOW, true)
                addFlags(Intent.FLAG_ACTIVITY_NO_ANIMATION)
                if (activityName == "MainActivity") {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
                } else {
                    addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)
                }
            }
            if (BuildConfig.DEBUG) {
                val count = getDebugFailCount(activity.intent)
                if (count > 1) {
                    restartIntent.putExtra(EXTRA_DEBUG_FAIL_SETCONTENT, count - 1)
                } else {
                    restartIntent.removeExtra(EXTRA_DEBUG_FAIL_SETCONTENT)
                }
            }
            activity.finish()
            applyNoAnimation(activity)
            activity.startActivity(restartIntent)
            applyNoAnimation(activity)
        } else {
            StartupGuardRules.clearSafeWindow(activity)
            Log.e("StartupWindowGuard", "Açılış kalkanı: VAZGEÇ ($activityName, son 10 dk içinde >= 2 başarısızlık). Sessizce kapanıyor.")
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                runCatching { activity.finishAndRemoveTask() }.onFailure { activity.finish() }
            } else {
                activity.finish()
            }
        }
    }

    private fun applyNoAnimation(activity: Activity) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            activity.overrideActivityTransition(Activity.OVERRIDE_TRANSITION_OPEN, 0, 0)
            activity.overrideActivityTransition(Activity.OVERRIDE_TRANSITION_CLOSE, 0, 0)
        } else {
            @Suppress("DEPRECATION")
            activity.overridePendingTransition(0, 0)
        }
    }
}
