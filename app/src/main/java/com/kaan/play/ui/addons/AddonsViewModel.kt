package com.kaan.play.ui.addons

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kaan.play.data.addon.Addon
import com.kaan.play.data.addon.AddonException
import com.kaan.play.data.addon.AddonRepository
import com.kaan.play.data.cloudstream.CsRepo
import com.kaan.play.data.cloudstream.CsRepository
import com.kaan.play.data.cloudstream.InstalledCsPlugin
import com.kaan.play.data.plugin.AddResult
import com.kaan.play.data.plugin.InstalledPlugin
import com.kaan.play.data.plugin.PluginRepo
import com.kaan.play.data.plugin.PluginRepository
import com.kaan.play.data.plugin.SourceInstaller
import com.kaan.play.data.plugin.SourceType
import com.kaan.play.data.plugin.UpdatePlan
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** "Eklenti ekle" penceresinin durumu. Pencere kapalıyken ViewModel'de bu değer null'dır. */
data class AddDialogState(
    val input: String = "",
    val busy: Boolean = false,
    val error: AddonException? = null,
    val expectedType: SourceType? = null,
)

class AddonsViewModel(
    private val installer: SourceInstaller,
    repo: AddonRepository,
    private val pendingUrl: MutableStateFlow<String?>,
) : ViewModel() {

    /** Başarılı eklemelerin sade bildirimi (tek seferlik): "Eklenti eklendi" / "Eklenti deposu eklendi". */
    private val _added = MutableSharedFlow<AddResult>(extraBufferCapacity = 4)
    val added: SharedFlow<AddResult> = _added.asSharedFlow()

    /** null = henüz yüklenmedi (boş liste mesajı yanıp sönmesin). */
    val addons: StateFlow<List<Addon>?> =
        repo.addons.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    private val _dialog = MutableStateFlow<AddDialogState?>(null)
    val dialog: StateFlow<AddDialogState?> = _dialog.asStateFlow()

    private var submitJob: Job? = null

    init {
        // Dışarıdan gelen https eklenti bağlantısı: pencereyi adres dolu açar, kutuyu boşaltır.
        viewModelScope.launch {
            pendingUrl.filterNotNull().collect { url ->
                pendingUrl.value = null
                openAdd(url)
            }
        }
    }

    private var startHandled = false

    /** "Eklenti ekle" düğmesiyle gelinen açılışta pencereyi bir kez açar (yeniden oluşturmada tekrarlamaz). */
    fun startAddingOnce() {
        if (startHandled) return
        startHandled = true
        openAdd()
    }

    fun openAdd(prefill: String = "", expectedType: SourceType? = null) {
        submitJob?.cancel()
        _dialog.value = AddDialogState(input = prefill, expectedType = expectedType)
    }

    fun dismissAdd() {
        submitJob?.cancel()
        _dialog.value = null
    }

    fun onInputChange(text: String) {
        _dialog.update { it?.copy(input = text, error = null) }
    }

    fun submit() {
        val current = _dialog.value ?: return
        if (current.busy || current.input.isBlank()) return
        _dialog.value = current.copy(busy = true, error = null)
        submitJob = viewModelScope.launch {
            try {
                val result = installer.add(current.input, current.expectedType)
                _dialog.value = null
                _added.emit(result)
            } catch (e: AddonException) {
                _dialog.update { it?.copy(busy = false, error = e) }
            }
        }
    }

}

/** Bir deponun kazıyıcıları (Yüklü eklentiler listesinde depo adı altında). */
data class PluginGroup(val repo: PluginRepo, val plugins: List<InstalledPlugin>)

/** CloudStream deposunun eklentileri (Yüklü eklentiler listesinde CS depo adı altında). */
data class CsPluginGroup(val repo: CsRepo, val plugins: List<InstalledCsPlugin>)

/** "Yüklü eklentiler" listesi: addon'lar, Nuvio deposu kazıyıcıları ve CS eklentileri (aç/kapat; varsayılan kapalı). */
class InstalledAddonsViewModel(
    private val repo: AddonRepository,
    plugins: PluginRepository,
    private val csRepos: CsRepository,
) : ViewModel() {

    private val pluginRepo = plugins

    /** null = henüz yüklenmedi (boş liste mesajı yanıp sönmesin). */
    val addons: StateFlow<List<Addon>?> =
        repo.addons.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    val groups: StateFlow<List<PluginGroup>?> =
        combine(plugins.repos, plugins.plugins) { repos, all ->
            repos.mapNotNull { r -> all.filter { it.repoId == r.id }.takeIf { it.isNotEmpty() }?.let { PluginGroup(r, it) } }
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    val csGroups: StateFlow<List<CsPluginGroup>?> =
        combine(csRepos.repos, csRepos.plugins) { repos, all ->
            repos.mapNotNull { r -> all.filter { it.repoId == r.id }.takeIf { it.isNotEmpty() }?.let { CsPluginGroup(r, it) } }
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    fun setEnabled(addon: Addon, enabled: Boolean) {
        viewModelScope.launch { repo.setEnabled(addon.id, enabled) }
    }

    fun setPluginEnabled(plugin: InstalledPlugin, enabled: Boolean) {
        viewModelScope.launch { pluginRepo.setPluginEnabled(plugin.id, enabled) }
    }

    fun setCsPluginEnabled(plugin: InstalledCsPlugin, enabled: Boolean) {
        viewModelScope.launch { csRepos.setEnabled(plugin.id, enabled) }
    }

    fun remove(addon: Addon) {
        viewModelScope.launch { repo.remove(addon.id) }
    }
}

/** CloudStream depo yönetimi: listele ve kaldır. */
class CsReposViewModel(private val csRepos: CsRepository) : ViewModel() {

    val repos: StateFlow<List<CsRepo>?> =
        csRepos.repos.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    fun remove(repo: CsRepo) {
        viewModelScope.launch { csRepos.remove(repo.id) }
    }
}

/** Bir güncelleme denetiminin ya da işleminin sonucu (tek seferlik bildirim). */
sealed interface RepoEvent {
    data object UpToDate : RepoEvent
    data class Updated(val count: Int) : RepoEvent
    data class Failed(val error: AddonException) : RepoEvent
}

/** "Kurulu tüm depolar": depoları listeler; güncelleme denetimi (sürüm karşılaştırma), güncelleme ve kaldırma. */
class RepositoriesViewModel(private val plugins: PluginRepository) : ViewModel() {

    val repos: StateFlow<List<PluginRepo>?> =
        plugins.repos.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    /** Denetlenen/güncellenen deponun kimliği (düğme dönen gösterge olur); yoksa null. */
    private val _busy = MutableStateFlow<Long?>(null)
    val busy: StateFlow<Long?> = _busy.asStateFlow()

    /** Bulunan güncelleme planı: arayüz "Güncelle / Vazgeç" penceresi gösterir. */
    private val _plan = MutableStateFlow<Pair<PluginRepo, UpdatePlan>?>(null)
    val plan: StateFlow<Pair<PluginRepo, UpdatePlan>?> = _plan.asStateFlow()

    private val _events = MutableSharedFlow<RepoEvent>(extraBufferCapacity = 4)
    val events: SharedFlow<RepoEvent> = _events.asSharedFlow()

    fun check(repo: PluginRepo) {
        if (_busy.value != null) return
        _busy.value = repo.id
        viewModelScope.launch {
            try {
                val found = plugins.checkForUpdates(repo.id)
                if (found.isEmpty) _events.emit(RepoEvent.UpToDate) else _plan.value = repo to found
            } catch (e: AddonException) {
                _events.emit(RepoEvent.Failed(e))
            } finally {
                _busy.value = null
            }
        }
    }

    fun applyPlan() {
        val (repo, found) = _plan.value ?: return
        _plan.value = null
        _busy.value = repo.id
        viewModelScope.launch {
            try {
                _events.emit(RepoEvent.Updated(plugins.applyUpdate(found).updated))
            } catch (e: AddonException) {
                _events.emit(RepoEvent.Failed(e))
            } finally {
                _busy.value = null
            }
        }
    }

    fun dismissPlan() {
        _plan.value = null
    }

    fun remove(repo: PluginRepo) {
        viewModelScope.launch { plugins.remove(repo.id) }
    }
}
