package com.kaan.play.data.settings

import com.kaan.play.data.update.UpdateChecker
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.util.concurrent.atomic.AtomicBoolean

/** "Bu sürümde neler yeni?" penceresinin açılış kararı (Android bağımsız). */
sealed interface WhatsNewDecision {
    /** Sürüm yükseltmesi algılandı ve başka engelleyici güncelleme yok: pencere açılır. */
    data object ShowDialog : WhatsNewDecision

    /** İlk kurulum: pencere gösterilmeden mevcut sürüm sessizce kaydedilir. */
    data class RecordSilently(val versionName: String) : WhatsNewDecision

    /** Aynı açılışta güncelleme penceresi çıkacak: bir sonraki açılışa ertelenir. */
    data object DeferToNextLaunch : WhatsNewDecision

    /** Aynı sürüm zaten görüldü (veya bilgi eksik): bir şey yapılmaz. */
    data object None : WhatsNewDecision

    /** Güncelleme denetimi henüz bitmedi. */
    data object Wait : WhatsNewDecision
}

/**
 * Sürüm notları ve açılış penceresi öncelik kuralları (Android bağımsız):
 * 1) Güncelleme -> sonraki açılış 2) Neler Yeni -> sonraki açılış 3) Duyuru.
 */
object WhatsNewRules {
    fun decide(
        currentVersionName: String,
        storedSeenVersion: String?,
        updateCheckCompleted: Boolean,
        hasUpdatePopup: Boolean,
        debugForceShow: Boolean = false,
    ): WhatsNewDecision {
        if (debugForceShow) return WhatsNewDecision.ShowDialog
        val cleanCurrent = currentVersionName.trim()
        if (cleanCurrent.isEmpty()) return WhatsNewDecision.None
        if (!updateCheckCompleted) return WhatsNewDecision.Wait

        val cleanStored = storedSeenVersion?.trim().orEmpty()
        if (cleanStored.isEmpty()) return WhatsNewDecision.RecordSilently(cleanCurrent)
        if (cleanStored == cleanCurrent) return WhatsNewDecision.None
        if (hasUpdatePopup) return WhatsNewDecision.DeferToNextLaunch
        return WhatsNewDecision.ShowDialog
    }

    fun shouldShowAnnouncementThisLaunch(
        hasUpdatePopup: Boolean,
        whatsNewShownThisLaunch: Boolean,
    ): Boolean = !hasUpdatePopup && !whatsNewShownThisLaunch
}

/** Oturum boyunca tek seferlik Neler Yeni durumunu ve öncelik kilidini yönetir. */
class WhatsNewCoordinator(
    private val currentVersionName: String,
    private val settings: SettingsRepository,
) {
    private val started = AtomicBoolean(false)

    private val _visible = MutableStateFlow(false)
    val visible: StateFlow<Boolean> = _visible.asStateFlow()

    private val _decided = MutableStateFlow(false)
    val decided: StateFlow<Boolean> = _decided.asStateFlow()

    @Volatile
    private var shownThisLaunch: Boolean = false

    val versionName: String get() = currentVersionName

    fun checkOnce(
        scope: CoroutineScope,
        updateChecker: UpdateChecker? = null,
        debugForceShow: Boolean = false,
    ) {
        if (debugForceShow) {
            shownThisLaunch = true
            _visible.value = true
            _decided.value = true
            return
        }
        if (!started.compareAndSet(false, true)) return
        scope.launch {
            if (updateChecker != null) {
                updateChecker.completed.first { it }
            }
            val stored = settings.lastWhatsNewVersion.first()
            val hasUpdate = updateChecker?.available?.value != null
            when (
                val decision = WhatsNewRules.decide(
                    currentVersionName = currentVersionName,
                    storedSeenVersion = stored,
                    updateCheckCompleted = true,
                    hasUpdatePopup = hasUpdate,
                )
            ) {
                WhatsNewDecision.ShowDialog -> {
                    shownThisLaunch = true
                    _visible.value = true
                }
                is WhatsNewDecision.RecordSilently -> {
                    settings.setLastWhatsNewVersion(decision.versionName)
                }
                WhatsNewDecision.DeferToNextLaunch,
                WhatsNewDecision.None,
                WhatsNewDecision.Wait -> Unit
            }
            _decided.value = true
        }
    }

    fun forceDebugShow() {
        shownThisLaunch = true
        _visible.value = true
        _decided.value = true
    }

    fun dismiss(scope: CoroutineScope) {
        _visible.value = false
        val clean = currentVersionName.trim()
        if (clean.isNotEmpty()) {
            scope.launch { settings.setLastWhatsNewVersion(clean) }
        }
    }

    fun blocksAnnouncementThisLaunch(): Boolean = shownThisLaunch
}
