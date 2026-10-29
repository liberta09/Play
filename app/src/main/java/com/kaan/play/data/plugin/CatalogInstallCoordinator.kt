package com.kaan.play.data.plugin

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

sealed interface CatalogInstallOutcome {
    val url: String

    data class Success(override val url: String, val result: AddResult) : CatalogInstallOutcome
    data class Failure(override val url: String, val message: String) : CatalogInstallOutcome
}

/**
 * Katalog kurulumları ekranın rememberCoroutineScope'una değil uygulama scope'una bağlıdır.
 * Kullanıcı geri gidip kataloğu kapatsa da SourceInstaller işi tamamlanana kadar devam eder.
 */
class CatalogInstallCoordinator(
    private val appScope: CoroutineScope,
    private val installer: SourceInstaller,
) {
    private val _installingUrl = MutableStateFlow<String?>(null)
    val installingUrl: StateFlow<String?> = _installingUrl.asStateFlow()

    private val _outcome = MutableStateFlow<CatalogInstallOutcome?>(null)
    val outcome: StateFlow<CatalogInstallOutcome?> = _outcome.asStateFlow()

    @Synchronized
    fun install(url: String, sourceType: SourceType): Boolean {
        if (_installingUrl.value != null) return false
        _installingUrl.value = url
        _outcome.value = null
        appScope.launch {
            try {
                _outcome.value = CatalogInstallOutcome.Success(url, installer.add(url, sourceType))
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _outcome.value = CatalogInstallOutcome.Failure(url, e.message ?: e.javaClass.simpleName)
            } finally {
                _installingUrl.value = null
            }
        }
        return true
    }

    fun clearOutcome() {
        _outcome.value = null
    }
}
