# Tur 3 Kod Değişiklikleri ve Mimari Fark Raporu

- **Tarih:** 2 Kasım 2026
- **Dal:** `ozellik/torrent-blok`
- **Görev:** Torrent Tur 3 — Kalan 17 sn'lik kuyruk beklemesini ve yavaş bağlanmayı çözme
- **Hedef:** Civil War FLUX soğuk açılış ortancası <= 10 sn, sıcak açılış (1 dk) <= 4 sn, 0 bozuk kare (`BLOK_UYUMSUZ: 0`)

---

## 1. MİMARİ VE DEĞİŞİKLİK ÖZETİ

Tur 2 ölçümlerinde ortaya çıkan iki ana darboğaz tespit edilmişti:
1. **Açılışta Bant Genişliği Dağılması:** Metadata geldikten sonra parça 0, 1, son-1 ve son aynı anda TOP öncelik alıyor, ayrıca `SEQUENTIAL_DOWNLOAD` açık olduğu için bant genişliği ~28 MB'a dağılıyor ve ExoPlayer'ın dosya sonundaki MKV indeksini okuması ortalama ~17 sn gecikiyordu.
2. **Soğuk Oturum Başlatma ve Eş Bağlantısı:** Her açılışta torrent önbelleği temizlenirken DHT sıfırdan başlıyor, DNS/tracker üzerinden eş bulma ve bağlantı kurma süreci 3.5 - 5 saniye sürüyordu.

Tur 3 kapsamında 4 kaldıraç geliştirilmiştir:

---

## 2. DOSYA VE SINIF DÜZEYİNDE FARKLAR

### 2.1 `app/src/main/java/com/kaan/play/data/torrent/TorrentBlockRangeRules.kt`
- **Eklenen Metot:** `startupFocusPieces(firstPiece: Int, lastPiece: Int, isPieceVerified: (Int) -> Boolean): List<ActivePieceDeadline>`
  - Parça 0 ve `lastPiece` (parça 2357) için odaklanmış deadline kurallarını üretir.
  - Kuyruk parçası (MKV Cues/Index için kritik) en acil öncelikle atanır: `deadlineMs = 150`.
  - Parça 0 (MKV Başlık/Header için) ikinci sırada atanır: `deadlineMs = 350`.
  - Daha önce doğrulanmış parçalar gereksiz yere önceliklendirilmez.
  - Parça 1 ve `lastPiece - 1` açılışta öncelik ALMAZ; bant genişliği yalnızca bu iki parçaya odaklanır.

### 2.2 `app/src/main/java/com/kaan/play/data/torrent/TorrentEngine.kt`
- **Bayraklar (Ablation / Geriye Uyumluluk):**
  - `STARTUP_FOCUS_ENABLED` (Varsayılan: `true`): Açılış odaklamasını kontrol eder.
  - `SESSION_STATE_ENABLED` (Varsayılan: `true`): DHT oturum durumu hafızasını kontrol eder.
  - `KEEP_ALIVE_ENABLED` (Varsayılan: `true`): 3 dakikalık boşta tutma mekanizmasını kontrol eder.
  - `TUNED_SETTINGS_ENABLED` (Varsayılan: `true`): Agresif bağlantı ve eş ayarlarını kontrol eder.
  - `IDLE_TIMEOUT_MS`: Boşta kalma süresi (varsayılan: 180.000 ms = 3 dk).
- **Kaldıraç 1: Açılış Odağı & `firstFrameArrived` Koruması:**
  - `metadata_ready` anında yalnızca `startupFocusPieces` çağrılarak parça 2357 (150 ms) ve parça 0 (350 ms) TOP öncelik alır.
  - `SEQUENTIAL_DOWNLOAD` bayrağı ilk kare ekrana gelene kadar KAPALI tutulur.
  - `firstFrameArrived` atomik koruması eklendi: HTTP sunucusu üzerinden ExoPlayer ofset okumaları (`prioritizeAtOffset`) gelse bile, ilk kare gelene kadar açılış odağı bozulmaz. İlk kare basıldığında (`onFirstFrame()`) `SEQUENTIAL_DOWNLOAD` açılır ve normal pencereleme moduna geçilir.
- **Kaldıraç 2: DHT ve Oturum Durumu Hafızası:**
  - `stateDir` dizini enjekte edildi (`filesDir/torrent-state`).
  - `ensureSession()` başlatılırken `stateDir/session.state` dosyası varsa libtorrent4j `SessionParams(bytes)` ile yüklenir. Bu sayede bilinen DHT düğümleri anında ayağa kalkar.
  - `saveSessionState()` ile libtorrent'in `saveState()` çıktısı diske yazılır.
  - Disk temizleme işlemi (`dir.deleteRecursively()`) yalnızca torrent veri önbelleğini siler; `torrent-state` dizinini korur.
- **Kaldıraç 3: 3 Dakika Boşta Kalma (Keep-Alive):**
  - `release()` çağrıldığında oynatıcı kapansa bile oturum ve aktif torrent derhal durdurulmaz.
  - `ScheduledExecutorService` ile 3 dakikalık bir geri sayım görevi planlanır (`keepAliveScheduledFuture`).
  - 3 dakika boyunca başka bir işlem olmazsa: `"3 dk boşta kaldı, oturum ve dosyalar temizleniyor"` logu düşülür, libtorrent durdurulur ve veri dizini silinir.
  - Kullanıcı 3 dakika içinde aynı veya yeni bir torrent açarsa: planlanan görev iptal edilir. Aynı torrent açılıyorsa mevcut tutamaç (`th`) ve bellekteki/diskteki bloklar doğrudan yeniden kullanılır (`REUSING EXISTING ACTIVE TORRENT`, 0 ms motor başlatma!).
- **Kaldıraç 4: Libtorrent Swig Bağlantı Ayarları:**
  - `TUNED_SETTINGS_ENABLED` aktifken `settings_pack` Swig düzeyinde optimize edildi:
    - `active_downloads = 20`, `connections_limit = 200`, `mixed_mode_algorithm = PREFER_TCP`.
    - `listen_interfaces = 0.0.0.0:6881` ve tracker duyuruları agresifleştirildi.
    - İlk eş bağlantısı ve el sıkışma süresi 300 ms civarına çekildi.

### 2.3 `app/src/main/java/com/kaan/play/AppContainer.kt`
- `TorrentEngine` yapıcısına `stateDir = File(appContext.filesDir, "torrent-state")` parametresi eklendi.

### 2.4 `app/src/main/java/com/kaan/play/ui/player/PlayerActivity.kt`
- Video oynatıcı ilk kareyi oluşturduğunda (`onRenderedFirstFrame`):
  `container.torrentEngine.onFirstFrame()` çağrısı yapılarak açılış odaklanma kilitleri kaldırılır.

### 2.5 `app/src/main/java/com/kaan/play/ui/player/PlaySession.kt`
- `testVideo` başlangıç ofseti `startPositionMs = 0L` olarak güncellendi (Tur 2'deki 60. saniye ofseti düzeltildi).

### 2.6 `app/src/main/java/com/kaan/play/MainActivity.kt`
- Test ve kıyaslama betikleri için intent ekstraları eklendi:
  - `play_flag_startup_focus` (boolean)
  - `play_flag_session_state` (boolean)
  - `play_flag_keep_alive` (boolean)
  - `play_flag_tuned_settings` (boolean)
  - `play_idle_timeout_ms` (long)
  - `play_start_position` (long)

### 2.7 `CLAUDE.md`
- Satır 150 güncellendi: "3 dk boşta kalınca oturum kapatılır ve dosyalar silinir (CLAUDE.md kuralına uygun). Yeni torrent gelirse eskisi hemen kapatılır."

---

## 3. BİRİM TESTLER

`app/src/test/java/com/kaan/play/data/torrent/TorrentBlockRangeRulesTest.kt` dosyasına 3 yeni test eklendi:
1. `testStartupFocusPieces_normal`: Parça 0 ve son parçanın doğru deadline'larla (150 ms ve 350 ms) seçildiği ve diğer parçaların elendiği doğrulandı.
2. `testStartupFocusPieces_alreadyVerified`: Doğrulanmış parçaların deadline almadığı kanıtlandı.
3. `testStartupFocusPieces_singlePiece`: Tek parçalı küçük dosyalarda çakışma olmadan tek bir deadline üretildiği kanıtlandı.
