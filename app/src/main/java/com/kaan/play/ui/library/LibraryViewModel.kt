package com.kaan.play.ui.library

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kaan.play.data.db.DownloadEntity
import com.kaan.play.data.db.FavoriteEntity
import com.kaan.play.data.db.WatchHistoryEntity
import com.kaan.play.data.download.DownloadCenter
import com.kaan.play.data.library.LibraryRepository
import com.kaan.play.data.settings.SettingsRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class LibraryViewModel(
    private val library: LibraryRepository,
    private val downloadCenter: DownloadCenter,
    settings: SettingsRepository,
) : ViewModel() {

    /** null = henüz yüklenmedi (boş durum mesajı yanıp sönmesin). */
    val favorites: StateFlow<List<FavoriteEntity>?> =
        library.allFavorites.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    val history: StateFlow<List<WatchHistoryEntity>?> =
        library.watchHistory.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    val manuallyWatched: StateFlow<List<WatchHistoryEntity>?> =
        library.manuallyWatched.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    val downloads: StateFlow<List<DownloadEntity>?> =
        downloadCenter.rows.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    /** Sıradaki indirmeler ağ koşulunu bekliyor mu? */
    val waitingForNetwork: StateFlow<Boolean> = downloadCenter.waitingForNetwork

    /** "Sadece Wi-Fi'de indir" açık mı? (Bekleyen indirmenin "Wi-Fi bekleniyor" mu "Bağlantı bekleniyor" mu dediğini belirler.) */
    val wifiOnly: StateFlow<Boolean> =
        settings.downloadWifiOnly.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SettingsRepository.DEFAULT_DOWNLOAD_WIFI_ONLY)

    fun removeFavorite(favorite: FavoriteEntity) {
        viewModelScope.launch { library.removeFavorite(favorite.type, favorite.tmdbId) }
    }

    fun removeHistory(entry: WatchHistoryEntity) {
        viewModelScope.launch { library.removeHistory(entry.id) }
    }

    fun addHistoryToFavorites(entry: WatchHistoryEntity) {
        viewModelScope.launch { library.addHistoryToFavorites(entry) }
    }

    fun markHistoryWatched(entry: WatchHistoryEntity, onResult: (Boolean) -> Unit) {
        viewModelScope.launch { onResult(library.markHistoryWatched(entry)) }
    }

    fun clearHistory() {
        viewModelScope.launch { library.clearHistory() }
    }

    fun pauseDownload(entry: DownloadEntity) = downloadCenter.pause(entry.id)

    fun resumeDownload(entry: DownloadEntity) = downloadCenter.resume(entry.id)

    fun removeDownload(entry: DownloadEntity) = downloadCenter.remove(entry.id)

    fun removeAllDownloads() = downloadCenter.removeAll()
}
