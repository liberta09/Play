# Eksikler-4 — sonuç

Tarih: 2026-10-28 · Dal: `ozellik/eksikler-4` (origin/main `2f90eb0` üzerinden) · Yayın yapılmadı, sürüm numarası ve etiket değişmedi.

## 1) Film/dizi açma (LDPlayer, ~13 dk)

Kurulum: `adb push … /data/local/tmp/p.apk` + `pm install -r -t` (Success). Akış her içerikte: arama → afişe dokun → Detay → Oynat → 35 sn bekle → Geri.

| İçerik | Detay | İlk kare (kaynak başlatmadan) | Süre denetimi | 35 sn sonra | Ekran görüntüleri |
|---|---|---|---|---|---|
| Örümcek-Adam: Yepyeni Bir Gün (film) | açıldı | ~1,1 sn (Enel) | 145,0 dk / beklenen 145 dk → Ok | oynuyor (Columbia açılışı) | `03-film-detay.png`, `04-film-oynat-35sn.png` |
| Criminal Minds S1B1 | açıldı | ~1,2 sn (Gaban) | 41,7 dk / 40 dk → Ok | oynuyor ("Criminal Minds · S1 B1", dublaj kartı) | `07-cm-detay.png`, `08-cm-bolumler.png`, `09-cm-b1-satir.png`, `10-cm-oynat-35sn.png` |
| Blue Bloods S1B1 | açıldı | ~1,1 sn (Gaban) | 42,8 dk / 43 dk → Ok | oynuyor | `12-bb-detay.png`, `13-bb-b1-satir.png`, `14-bb-oynat-35sn.png` |

- Log özeti (`PlayMatch`, sunucu adları maskeli): `log-film.txt`, `log-criminal-minds.txt`, `log-blue-bloods.txt`. Üç oturumda da `AndroidRuntime` FATAL ve ANR satırı yok; yanlış içerik/donma/yükleniyor'da kalma görülmedi. **Kod değişikliği gerekmedi.**
- Örnek satır: `süre kontrolü kaynak=Enel (<sunucu>) süre=145.0 dk beklenen=145 dk tür=Movie -> Ok`.
- Yan gözlem (düzeltildi, madde 2): `08-cm-bolumler.png` "17. 17. Bölüm" ikilenmesini ve geri düğmesinin bölüm kartlarının üstüne bindiğini gösteriyor.
- Yan gözlem (dokunulmadı): LDPlayer'da `adb input` ile arama kutusuna yazarken odak kutudan yan menüye kaçıyor (tuş olayları kumanda kipine geçiriyor). Gerçek dokunmatik klavyede denenmedi.

## 2) Detay sayfasında indir düğmesi

- Bölüm satırının sağında yuvarlak indir düğmesi; satır yine oynatır, ikisi kumandada ayrı odaklanır: `app/src/main/java/com/kaan/play/ui/detail/DetailScreen.kt:662` (`EpisodeRow` = kart + düğme), `:733` (`DetailDownloadButton`).
- Filmde Oynat'ın yanında aynı düğme: `DetailScreen.kt:349`.
- Durum (indirilmemiş / ilerleme halkası / tik / başarısız): `data/download/DownloadRules.kt:43` (`contentBadge`), çizim `ui/sources/SourcesSheet.kt` `DownloadStateIcon`.
- Düğme kaynak listesini indirme modunda açar: `DetailScreen.kt:197`, `ui/sources/SourcesSheet.kt:94` (`downloadMode`), yoklama `SourcesSheet.kt:168` (adaylar geldikçe `SourceProbe`, en çok 4 eşzamanlı), liste `SourcesSheet.kt:348`. Kurallar `data/download/DownloadPick.kt:24` (torrent ve http(s) dışı yok, yalnızca indirilebilir çıkanlar, önerilen en üstte ve seçili/odakta). Tek dokunuş mevcut `DownloadCenter.start` ile indirir (aynı içerik tek indirme, "Sadece Wi-Fi'de indir" ve alan denetimi aynen), indirme başlayınca liste kapanır. Hiçbiri yoksa: "Bu bölüm için indirilebilir kaynak bulunamadı" (filmde "Bu film için …").
- Bölüm başlığı ikilenmesi: `ui/detail/EpisodeTitles.kt:13` — TMDB adı boşsa ya da yalnızca numaraysa ("1. Bölüm", "Bölüm 1", "Episode 1") "1. Bölüm", doluysa "1. <ad>"; ad numarayla başlıyorsa numara bir kez. Test: `EpisodeTitlesTest`.
- Geri düğmesi: sayfa 120 dp'den fazla kayınca düğmenin arkasına zemin rengi şerit gelir, kartlar düğmenin altında kalır: `DetailScreen.kt:164`.
- Kütüphane > İndirilenler'e dokunulmadı. Sağlayıcı (CloudStream) detayında düğme yok (TMDB kimliği gerekir).

## 3) İnternet hız testi ("Test tamamlanamadı")

- Kök neden: `measureDownload` gövdeyi `rememberCoroutineScope` (ana iş parçacığı) içinde okuyordu → `NetworkOnMainThreadException`, `runCatching` bunu (ve iptali) yutup `null` döndürüyordu. Ölçüm artık `Dispatchers.IO`'da: `data/speedtest/InternetSpeedTest.kt:68`.
- Ortak `httpClient` interceptor'ları incelendi (eklenti ağ koruması `SafePluginInterceptor`/`PlayNetworkGuard`, site yeniden yazma/gövde değiştirme köprüleri, 10/20 sn zaman aşımları). Hız testi bunlardan bağımsız sade istemci kullanıyor: `InternetSpeedTest.kt:153`, bağlantı `AppContainer.kt:287`.
- Cloudflare reddederse OVH herkese açık deneme dosyasına (`Range` ile istenen kadar) geri düşer: `InternetSpeedTest.kt:148`.
- Gerçek hata loglanır (`PlaySpeed`: uç adı + hata sınıfı + HTTP kodu, adres yok); iptal yutulmaz: `InternetSpeedTest.kt:50`. Sınıflandırma `:164`.
- Ekranda kısa sebep (iki dilde): "Test tamamlanamadı: Sunucuya ulaşılamadı" / "… Zaman aşımı": `ui/settings/SpeedTestScreen.kt:76`.
- Test: `InternetSpeedTestTest` (MockWebServer sahte yanıtları: hız hesabı, Range yok sayılınca sınırda kesme, 403'te yedeğe geçiş, hepsi reddederse/kapalı sunucuda "ulaşılamadı", yavaş sunucuda "zaman aşımı", logda adres yok).

## 4) Wi-Fi senkronizasyonu

Çekirdek (Android'siz, `app/src/main/java/com/kaan/play/data/sync/local/`):
- `LocalSyncServer.kt:29` — TCP 43821, `GET /health` (şifresiz, nonce verir), `GET /profiles`, `POST /sync` (JSON, AES-GCM); kanıt denetimi `:152` (yanlış = 401, yeniden oynatma = 401); yalnızca özel IPv4; gövde 5 MB (413); okuma zaman aşımı 10 sn; 5. yanlışta kapanır.
- `LocalSyncCrypto.kt:40` — anahtar koddan + iki nonce'tan HKDF-SHA256 (RFC 5869 test vektörüyle doğrulandı), HMAC istek kanıtı, `:57` AES-256-GCM (ek doğrulanmış veri = yol). Kod ağda düz gitmez.
- `LocalSyncProtocol.kt:43` kod (`SecureRandom`, 6 hane), `:52` deneme sınırı, `:70` özel ağ denetimi.
- `LocalSyncClient.kt:44` — sade OkHttp; DNS yok (yalnızca rakamlı özel IPv4), vekil yok, yönlendirme yok.
- `LocalSyncDiscovery.kt:23` UDP 43822 cevaplayıcı ("PLAY_SYNC_DISCOVER" → "PLAY_SYNC_HERE"), `:72` yayınla arama (kendi adresi atlanır).
- `LocalSyncMerge.kt:43` — profiller (PIN hariç; yerel PIN korunur), geçmiş/kaldığı yer (en yeni `lastWatchedAt`, eşitlikte ileri konum), favoriler birleşim, eksik kaynaklar onaya, ortak kaynakların durumlarında kodu gösteren cihaz esas.
- Main'deki parçalar kullanıldı: `LocalProfileRepository` (+ `replaceProfilesKeepingActive`, `data/sync/LocalProfileRepository.kt:339`), `LocalSyncBackup.mergeJsonFor`, `LocalProfileTransfer`/`SourceTransferItem`/`PluginStateTransfer` (varsayılanlı yeni alanlar: eklenti ayarları, ad, kapalı kataloglar, cihaz adı).

Android:
- `LocalSyncController.kt:71` `open()` (kart açılınca; kod + sunucu + UDP + NSD + MulticastLock, 3 dk zamanlayıcı), `:116` `close()` (kart kapanınca / arka plan / süre doldu), `:137` `connect()` (UDP → yoksa NSD → yoksa "Cihaz bulunamadı; iki cihaz aynı Wi-Fi'de mi?").
- `LocalSyncData.kt:45` dışa aktarma (profiller, geçmiş, favoriler, tüm kaynaklar: Nuvio addon'ları dahil altyazı addon'ları, Nuvio depoları, CloudStream depoları, web siteleri; eklenti açık/kapalı + `hasSettings` değerleri + katalog anahtarları), `:114` onaylanan kaynağı `SourceInstaller`/`WebSiteRepository` ile kurup durumları gönderenle aynı yapma (dosya kopyalanmaz).
- `LocalSyncNsd.kt:27` MulticastLock, `:43` NSD `_playsync._tcp`.
- `AppContainer.kt:455` tembel `localSync`: **uygulama açılışında hiçbir sunucu başlamaz** (PlayApp/MainActivity/PlayRoot'a dokunulmadı).
- Kart `ui/settings/WifiSyncCard.kt:73`: "Wi-Fi senkronizasyonu · Yerel", açıklama, "Kod: 123456", "Cihaz kodu", "Bağlan" (`:206` `maxLines = 1, softWrap = false`, düğme kendi genişliğini alır), ilerleme ve "Eşitlendi: 2 profil, 37 geçmiş, 6 addon, 4 depo", "Şunlar eklensin mi?" (adresler görünür, "Hepsini ekle" + tek tek seçim). Android 17+ `ACCESS_LOCAL_NETWORK` çalışma anında `:107` (manifest `AndroidManifest.xml:21`). Elle adres yalnızca debug `:103`. Yerleşim: `ProfileChooserScreen.kt:179`, `SettingsScreen.kt:168`.
- `@Preview`: dar telefon (320 dp), büyük yazı (fontScale 2), TV (960×540 dp), İngilizce büyük yazı — `WifiSyncCard.kt:370`. **Not:** bu ortamda Android Studio yok; önizlemeler derleniyor ama görsel olarak çizdirilemedi. "Bağlan"ın tek satır kalması yapı gereği (`softWrap = false`, ağırlık kod kutusunda) sağlanıyor; cihazda/Studio'da göz kontrolü kullanıcıda.

Testler: `LocalSyncCoreTest` (kod, deneme sınırı, ağ, HKDF/AES-GCM/kanıt), `LocalSyncMergeTest`, `LocalSyncIntegrationTest` (127.0.0.1 gerçek soket: şifreli el sıkışma, yanlış kodda 401, 5. yanlışta sunucu kapanıyor ve doğru kod da artık işe yaramıyor, yeniden oynatma 401, çift yönlü birleştirme sonunda aynı veri, PIN ağdan geçmiyor, özel olmayan adres reddi, UDP bulma).

Bilinen sınırlar: iki tarafta da olan kaynakların açık/kapalı ve ayar farklarında kodu gösteren cihaz esas alınır (zaman damgası yok). Profil sırası iki cihazda farklı olabilir (içerik aynı). Eksik kaynak onayı her cihazda ayrı sorulur.

## Test çıktısı

- `.\gradlew testDebugUnitTest`: **1051 test, 0 başarısız, 0 hata, 2 atlanan** (130 sınıf). Yeni: `InternetSpeedTestTest` 9, `EpisodeTitlesTest` 4, `DownloadPickTest` 5, `LocalSyncCoreTest` 11, `LocalSyncMergeTest` 7, `LocalSyncIntegrationTest` 7.
- `.\gradlew assembleDebug`: BUILD SUCCESSFUL.

## Debug APK

`C:\Projeler\Play\app\build\outputs\apk\debug\app-debug.apk` (1–4. maddeleri içerir; LDPlayer'daki 1. madde denemesi değişikliklerden önceki main derlemesiyle yapıldı).

Cihaz testi (2–4) kullanıcıda.

Dal: `ozellik/eksikler-4` · son commit: `4b2bcd7` (bu dosya ondan sonraki commit'le eklendi)
