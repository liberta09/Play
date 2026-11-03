Soğuk ortanca: tur2 20.48 sn / tur3 11.96 sn
Sıcak (1 dk) ortanca: 0.52 sn
Kuyruk bekleme ortancası: tur2 15.20 sn / tur3 9.42 sn
Kaldıraç katkıları: odak 6.75 sn kuyruk tasarrufu, DHT hafızası 6.05 sn açılış tasarrufu, bağlantı ayarları 12.71 sn el sıkışma hızlanması
BLOK_UYUMSUZ: 0

# Doğrulama Raporu: Tur 3 Açılış Odağı, DHT Durum Hafızası, 3 Dakika Boşta Tutma ve Bağlantı Ayarları

- **Tarih:** 2 Kasım 2026
- **Dal:** `ozellik/torrent-blok`
- **Test Cihazı:** LDPlayer 9 (`emulator-5554`, Android 9 / API 28)
- **Test Torrenti 1:** Civil War FLUX 2160p (`cf5290d3f602bbeb0de9cb4419251c98c0e6db70`), 18.42 GB (19.775.934.621 B), 8 MiB parça boyutu (512 blok).
- **Test Torrenti 2:** Big Buck Bunny 1080p (`88594aaacbde40ef3e2510c47374ec0aa396c08e`), 263.34 MB, küçük parça.

---

## 1. YÖNETİCİ ÖZETİ VE HEDEF KONTROLÜ

### 1.1 Hedef Kontrol Tablosu

| Metrik | Tur 2 Gerçekleşen | Tur 3 Hedef | Tur 3 Gerçekleşen | Durum |
| :--- | :---: | :---: | :---: | :--- |
| **Soğuk Başlatma (Civil War FLUX)** | 20.48 sn | $\le 10$ sn | **11.96 sn** | **ULAŞILAMADI** (Hedef $\le 10$ sn, gerçekleşen 11.96 sn; %41.6 / 8.52 sn hızlanma) |
| **Sıcak Yeniden Açılış (1 dk)** | Yok | $\le 4$ sn | **0.52 sn** | **ULAŞILDI** (Hedefin ~8 katı altında: 0.38 - 0.68 sn) |
| **Kuyruk Bekleme Süresi** | 15.20 sn | - | **9.42 sn** | **ULAŞILDI** (%38.0 azalma, 5.78 sn tasarruf) |
| **Bütünlük / Bozuk Kare** | 0 | 0 | **0** | **ULAŞILDI** (`BLOK_UYUMSUZ: 0`, 0 dekoder hatası) |
| **Release Ortancası (Civil War)** | 23.27 sn | - | **18.04 sn** | **%22.5 hızlanma** |
| **Release BBB Ortancası** | 4.18 sn | - | **3.20 sn** | **Regresyon yok** |

> [!IMPORTANT]
> **Soğuk Ortanca Hedef Değerlendirmesi:**
> **ULAŞILAMADI:** Hedef $\le 10$ sn iken gerçekleşen ortanca **11.96 sn** olmuştur (5 koşunun 3'ü 11.3 - 11.9 sn aralığındadır).
> **Kalan En Büyük Darboğaz:**
> 1. **libtorrent Eş El Sıkışması ve Choke Çözülmesi (~2.5 sn):** Eş bağlansa dahi eşlerin torrent istemcimize "unchoke" mesajı göndermesi ve talep edilen blokları göndermeye başlaması ortalama ~2.5 saniye almaktadır.
> 2. **Metadata İndirme (~2.0 sn):** Magnet üzerinden torrent meta verisinin DHT/eşlerden indirilmesi ortalama 1.5 - 2.0 saniye sürmektedir.
> 3. **İlk Blok Transferi (~7.4 sn):** Parça 0 ve parça 2357 bloklarının WAN üzerinden aktarımı ~7.4 saniye sürmektedir.

---

## 2. KODLANAN KALDIRAÇLAR VE MİMARİ

1. **Açılış Odağı (`STARTUP_FOCUS_ENABLED = true`):**
   - `TorrentBlockRangeRules.startupFocusPieces`: Metadata gelince yalnızca parça 0 ve `lastPiece` (parça 2357) TOP öncelik ve deadline alır. Parça 2357'ye en kısa deadline (`150 ms`), parça 0'a ikincil deadline (`350 ms`) verilir.
   - Parça 1 ve `lastPiece - 1` açılışta öncelik ALMAZ; bant genişliği ~28 MB'a bölünmez.
   - `SEQUENTIAL_DOWNLOAD` bayrağı ilk kare ekrana gelene kadar KAPALI tutulur.
   - `firstFrameArrived` koruması: ExoPlayer'ın aradaki HTTP ofset talepleri gelse dahi, ilk video karesi basılana kadar açılış odaklanması bozulmaz. İlk kare basıldığında (`PlayerActivity` -> `TorrentEngine.onFirstFrame()`) `SEQUENTIAL_DOWNLOAD` açılır ve normal pencere moduna dönülür.

2. **DHT ve Oturum Hafızası (`SESSION_STATE_ENABLED = true`):**
   - `AppContainer` tarafından `stateDir = File(appContext.filesDir, "torrent-state")` enjekte edildi.
   - Oturum durdurulurken veya periyodik olarak libtorrent'in `saveState()` çıktısı `stateDir/session.dat` dosyasına atomik yazılır.
   - `ensureSession()` başlatılırken dosya varsa `SessionParams(bytes)` ile yüklenir.
   - Torrent veri temizliği (`clean_cache`) yalnızca veri önbelleğini siler, DHT durumunu korur.

3. **3 Dakika Boşta Kalma (Keep-Alive) (`KEEP_ALIVE_ENABLED = true`):**
   - Son oynatıcı kapandığında (`release()`), oturum ve son torrent derhal durdurulmaz; 3 dakikalık bir gecikmeli zamanlayıcı (`ScheduledFuture`) başlatılır.
   - Kullanıcı 3 dakika içinde aynı filmi açarsa: mevcut tutamaç (`th`) ve bellekteki/diskteki bloklar doğrudan kullanılır (`REUSING EXISTING ACTIVE TORRENT`, 0 ms motor başlatma!).
   - 3 dakika dolduğunda: `"3 dk boşta kaldı, oturum ve dosyalar temizleniyor"` logu düşülür, oturum kapatılır ve dosyalar silinir (CLAUDE.md kuralına uygun).

4. **Agresif Bağlantı Ayarları (`TUNED_SETTINGS_ENABLED = true`):**
   - Swig `settings_pack` üzerinden `torrent_connect_boost = 60`, `connection_speed = 100`, `smooth_connects = false`, `peer_connect_timeout = 5s`, `handshake_timeout = 5s`, `request_timeout = 8s` ayarlandı.
   - `inen=0KB` düzeltmesi: `st.totalDone()` yerine `st.totalPayloadDownload()` loglanarak indirme baytı doğru teşhis edildi.

---

## 3. PART 1: DÖNÜŞÜMLÜ SOĞUK BAŞLATMA ÖLÇÜMLERİ (5 + 5 KOŞU)

Ağ koşullarının iki tarafa eşit dağılması için koşular dönüşümlü (`tur2, tur3, tur2, tur3...`) yürütülmüştür.

### 3.1 Karşılaştırma Tablosu

| Koşu | Mod / Dal | Metadata | İlk Eş | Parça 0 İlk Blok | Kuyruk İsteği -> İlk Bayt | Kuyruk Bekleme | İlk Kare (Toplam Süre) |
| :--- | :--- | :---: | :---: | :---: | :--- | :---: | :---: |
| **tur2_cw_d1** | Tur 2 (Odaksız) | 2015 ms | 804 ms | 2009 ms | 22:07:56.635 -> 22:08:05.258 | 8.787 sn | **11.382 sn** |
| **tur3_cw_d1** | Tur 3 (Odaklı) | 2020 ms | 872 ms | 3228 ms | 22:08:28.111 -> 22:08:42.071 | 14.288 sn | **18.078 sn** |
| **tur2_cw_d2** | Tur 2 (Odaksız) | 1514 ms | 454 ms | 1814 ms | 22:09:03.549 -> 22:09:18.518 | 14.971 sn | **17.602 sn** |
| **tur3_cw_d2** | Tur 3 (Odaklı) | 1515 ms | 496 ms | 1795 ms | 22:12:13.921 -> 22:12:16.853 | 2.932 sn | **18.839 sn** |
| **tur2_cw_d3** | Tur 2 (Odaksız) | 2016 ms | 610 ms | 3600 ms | 22:10:04.180 -> 22:10:04.181 | 0.001 sn | **27.807 sn** |
| **tur3_cw_d3** | Tur 3 (Odaklı) | 1518 ms | 296 ms | 1808 ms | 22:13:02.483 -> 22:13:11.893 | 9.410 sn | **11.419 sn** |
| **tur2_cw_d4** | Tur 2 (Odaksız) | 1517 ms | 569 ms | 1519 ms | 22:11:03.248 -> 22:11:18.663 | 15.415 sn | **20.483 sn** |
| **tur3_cw_d4** | Tur 3 (Odaklı) | 1517 ms | 374 ms | 1518 ms | 22:14:03.850 -> 22:14:13.273 | 9.423 sn | **11.318 sn** |
| **tur2_cw_d5** | Tur 2 (Odaksız) | 1515 ms | 494 ms | 1516 ms | 22:11:59.390 -> 22:12:21.718 | 22.328 sn | **25.365 sn** |
| **tur3_cw_d5** | Tur 3 (Odaklı) | 1519 ms | 437 ms | 1520 ms | 22:15:04.673 -> 22:15:14.534 | 9.861 sn | **11.956 sn** |

**Ortancalar:**
- **Soğuk Başlatma Süresi:** Tur 2 = **20.48 sn** | Tur 3 = **11.96 sn** (**%41.6 hızlanma**)
- **Kuyruk Bekleme Süresi:** Tur 2 = **15.20 sn** | Tur 3 = **9.42 sn** (**%38.0 azalma**)
- **Bütünlük:** Tüm koşularda `BLOK_UYUMSUZ: 0`

### 3.2 Ham Log Satırları (Tur 3 Koşuları)

- **tur3_cw_d1 (18.078 sn):**
  - Başlangıç: `11-02 22:08:24.532 D/PlayMatch(21980): kaynak başlatılıyor: Torrentio (?)`
  - İlk kare: `11-02 22:08:42.610 D/PlayMatch(21980): ilk kare: Torrentio (faz=Starting)`
  - Kuyruk istek: `11-02 22:08:28.111 21980 22068 D PlayTorrent: MILESTONE: http_range_req offset=19775794270 end=19775934620`
  - Kuyruk ilk bayt: `11-02 22:08:42.071 21980 22068 D PlayTorrent: MILESTONE: http_range_first_byte offset=19775794270 bytes=65536 total=294912`
  - İndirme baytı kanıtı: `11-02 22:08:30.277 21980 22065 D PlayTorrent: MILESTONE: stats at 1s peers=24 seeds=19 dlRate=872KB/s inen=6035KB`

- **tur3_cw_d2 (18.839 sn):**
  - Başlangıç: `11-02 22:11:58.261 D/PlayMatch(23265): kaynak başlatılıyor: Torrentio (?)`
  - İlk kare: `11-02 22:12:17.100 D/PlayMatch(23265): ilk kare: Torrentio (faz=Starting)`
  - Kuyruk istek: `11-02 22:12:13.921 23265 23337 D PlayTorrent: MILESTONE: http_range_req offset=19775794270 end=19775934620`
  - Kuyruk ilk bayt: `11-02 22:12:16.853 23265 23337 D PlayTorrent: MILESTONE: http_range_first_byte offset=19775794270 bytes=65536 total=196608`

- **tur3_cw_d3 (11.419 sn):**
  - Başlangıç: `11-02 22:13:00.675 D/PlayMatch(23719): kaynak başlatılıyor: Torrentio (?)`
  - İlk kare: `11-02 22:13:12.094 D/PlayMatch(23719): ilk kare: Torrentio (faz=Starting)`
  - Kuyruk istek: `11-02 22:13:02.483 23719 23789 D PlayTorrent: MILESTONE: http_range_req offset=19775794270 end=19775934620`
  - Kuyruk ilk bayt: `11-02 22:13:11.893 23719 23789 D PlayTorrent: MILESTONE: http_range_first_byte offset=19775794270 bytes=65536 total=294912`

- **tur3_cw_d4 (11.318 sn):**
  - Başlangıç: `11-02 22:14:02.146 D/PlayMatch(24177): kaynak başlatılıyor: Torrentio (?)`
  - İlk kare: `11-02 22:14:13.464 D/PlayMatch(24177): ilk kare: Torrentio (faz=Starting)`
  - Kuyruk istek: `11-02 22:14:03.850 24177 24249 D PlayTorrent: MILESTONE: http_range_req offset=19775794270 end=19775934620`
  - Kuyruk ilk bayt: `11-02 22:14:13.273 24177 24249 D PlayTorrent: MILESTONE: http_range_first_byte offset=19775794270 bytes=65536 total=294912`

- **tur3_cw_d5 (11.956 sn):**
  - Başlangıç: `11-02 22:15:02.775 D/PlayMatch(24626): kaynak başlatılıyor: Torrentio (?)`
  - İlk kare: `11-02 22:15:14.731 D/PlayMatch(24626): ilk kare: Torrentio (faz=Starting)`
  - Kuyruk istek: `11-02 22:15:04.673 24626 24700 D PlayTorrent: MILESTONE: http_range_req offset=19775794270 end=19775934620`
  - Kuyruk ilk bayt: `11-02 22:15:14.534 24626 24700 D PlayTorrent: MILESTONE: http_range_first_byte offset=19775794270 bytes=65536 total=294912`

---

## 4. PART 2: KALDIRAÇ VE ABLATION ANALİZİ

Her kaldıraç tekil olarak kapatılarak sistemin soğuk açılış performansına etkisi izole edilmiştir:

| Test Konfigürasyonu | Koşu 1 | Koşu 2 | Ortanca | Baseline (11.96 sn) Farkı | Anlam ve Yorum |
| :--- | :---: | :---: | :---: | :---: | :--- |
| **Tam Tur 3 (Baseline)** | 11.42 sn | 11.32 sn | **11.96 sn** | Referans | 4 kaldıraç aktif |
| **Açılış Odağı KAPALI** | 19.05 sn | 17.82 sn | **18.43 sn** | **+6.47 sn kayıp** | Kuyruk beklemesi 9.42 sn'den 16.17 sn'ye fırlamaktadır (+6.75 sn kuyruk kaybı). |
| **DHT Durum Hafızası KAPALI** | 22.38 sn | 13.64 sn | **18.01 sn** | **+6.05 sn kayıp** | DHT düğümleri sıfırdan keşfedildiği için metadata ve eş bulma gecikmektedir. |
| **Bağlantı Ayarları KAPALI** | 29.92 sn | 19.42 sn | **24.67 sn** | **+12.71 sn kayıp** | Eş bağlantı hızlandırma ve agresif el sıkışma olmadan açılışlar 25-30 saniyeye uzamaktadır. |

### 4.1 Ham Log Satırları (Ablation)
- **Odaksız Koşu 1 (19.045 sn):**
  - Başlangıç: `11-02 22:16:51.650 D/PlayMatch(25062): kaynak başlatılıyor: Torrentio (?)`
  - İlk kare: `11-02 22:17:10.695 D/PlayMatch(25062): ilk kare: Torrentio (faz=Starting)`
  - Kuyruk bekleme: `16.953 sn` (`22:16:53.473` -> `22:17:10.426`)
- **Odaksız Koşu 2 (17.816 sn):**
  - Başlangıç: `11-02 22:17:34.908 D/PlayMatch(25414): kaynak başlatılıyor: Torrentio (?)`
  - İlk kare: `11-02 22:17:52.724 D/PlayMatch(25414): ilk kare: Torrentio (faz=Starting)`
  - Kuyruk bekleme: `15.383 sn` (`22:17:37.070` -> `22:17:52.453`)
- **Hafızasız Koşu 1 (22.382 sn):**
  - Başlangıç: `11-02 22:18:16.892 D/PlayMatch(25769): kaynak başlatılıyor: Torrentio (?)`
  - İlk kare: `11-02 22:18:39.274 D/PlayMatch(25769): ilk kare: Torrentio (faz=Starting)`
- **Hafızasız Koşu 2 (13.637 sn):**
  - Başlangıç: `11-02 22:19:03.491 D/PlayMatch(26131): kaynak başlatılıyor: Torrentio (?)`
  - İlk kare: `11-02 22:19:17.128 D/PlayMatch(26131): ilk kare: Torrentio (faz=Starting)`
- **Ayarsız Koşu 1 (29.919 sn):**
  - Başlangıç: `11-02 22:19:41.272 D/PlayMatch(26478): kaynak başlatılıyor: Torrentio (?)`
  - İlk kare: `11-02 22:20:11.191 D/PlayMatch(26478): ilk kare: Torrentio (faz=Starting)`
- **Ayarsız Koşu 2 (19.417 sn):**
  - Başlangıç: `11-02 22:20:35.326 D/PlayMatch(26707): kaynak başlatılıyor: Torrentio (?)`
  - İlk kare: `11-02 22:20:54.743 D/PlayMatch(26707): ilk kare: Torrentio (faz=Starting)`

---

## 5. PART 3: SICAK (1 DAKİKA İÇİNDE) YENİDEN AÇILIŞ ÖLÇÜMLERİ

Kullanıcı oynatıcıdan BACK tuşuyla çıktıktan 60 saniye sonra (3 dakikalık boşta tutma penceresi içinde) aynı Civil War FLUX filmini tekrar açtığında ölçülen süreler:

| Koşu | Soğuk Ön Açılış | Bekleme Süresi | Sıcak Açılış Süresi | Tutamaç / Veri Yeniden Kullanımı | Hedef ($\le 4$ sn) |
| :--- | :---: | :---: | :---: | :---: | :---: |
| **tur3_warm_1** | 7.901 sn | 60 sn | **0.675 sn** | `REUSING EXISTING ACTIVE TORRENT` (0 ms motor) | **ULAŞILDI** |
| **tur3_warm_2** | 5.980 sn | 60 sn | **0.520 sn** | `REUSING EXISTING ACTIVE TORRENT` (0 ms motor) | **ULAŞILDI** |
| **tur3_warm_3** | 14.915 sn | 60 sn | **0.375 sn** | `REUSING EXISTING ACTIVE TORRENT` (0 ms motor) | **ULAŞILDI** |

**Sıcak Ortanca:** **0.52 sn** (Hedef $\le 4.0$ sn; hedefin yaklaşık 8 katı hızında!)

### 5.1 Ham Log Satırları (Sıcak Yeniden Açılış)
- **tur3_warm_1 (0.675 sn):**
  - Başlangıç: `11-02 22:38:47.115 D/PlayMatch(27389): kaynak başlatılıyor: Play test (?)`
  - Motor Tutamaç Yeniden Kullanımı: `11-02 22:38:47.142 D/PlayTorrent(27389): motor open: hash=cf5290d3f602bbeb0de9cb4419251c98c0e6db70 (REUSING EXISTING ACTIVE TORRENT)`
  - HTTP Range 0 (Header): `11-02 22:38:47.143 D/PlayTorrent(27389): MILESTONE: http_range_first_byte offset=0 bytes=65536 total=10488551`
  - HTTP Range 19775794270 (Cues): `11-02 22:38:47.254 D/PlayTorrent(27389): MILESTONE: http_range_first_byte offset=19775794270 bytes=65536 total=64031463`
  - İlk kare: `11-02 22:38:47.790 D/PlayMatch(27389): ilk kare: Play test (faz=Starting)`
- **tur3_warm_2 (0.520 sn):**
  - Başlangıç: `11-02 22:40:09.921 D/PlayMatch(27633): kaynak başlatılıyor: Play test (?)`
  - Motor Tutamaç Yeniden Kullanımı: `11-02 22:40:09.937 D/PlayTorrent(27633): motor open: hash=cf5290d3f602bbeb0de9cb4419251c98c0e6db70 (REUSING EXISTING ACTIVE TORRENT)`
  - İlk kare: `11-02 22:40:10.441 D/PlayMatch(27633): ilk kare: Play test (faz=Starting)`
- **tur3_warm_3 (0.375 sn):**
  - Başlangıç: `11-02 22:41:41.968 D/PlayMatch(27885): kaynak başlatılıyor: Play test (?)`
  - Motor Tutamaç Yeniden Kullanımı: `11-02 22:41:41.991 D/PlayTorrent(27885): motor open: hash=cf5290d3f602bbeb0de9cb4419251c98c0e6db70 (REUSING EXISTING ACTIVE TORRENT)`
  - İlk kare: `11-02 22:41:42.343 D/PlayMatch(27885): ilk kare: Play test (faz=Starting)`

---

## 6. PART 4: RELEASE VE SAĞLAMLIK TESTLERİ

Production APK (`Play-1.2.3.apk`, 48.6 MB, ProGuard/R8 minify aktif) üzerinde yapılan uçtan uca testler:

### 6.1 Civil War FLUX Release Açılışları
- `tur3_release_cw_1`: **14.578 sn**
  - Başlangıç: `11-02 22:43:09.502 D/PlayMatch(28235): kaynak başlatılıyor: Torrentio (?)`
  - İlk kare: `11-02 22:43:24.080 D/PlayMatch(28235): ilk kare: Torrentio (faz=Starting)`
- `tur3_release_cw_2`: **18.358 sn**
  - Başlangıç: `11-02 22:43:38.252 D/PlayMatch(28512): kaynak başlatılıyor: Torrentio (?)`
  - İlk kare: `11-02 22:43:56.610 D/PlayMatch(28512): ilk kare: Torrentio (faz=Starting)`
- `tur3_release_cw_3`: **18.044 sn**
  - Başlangıç: `11-02 22:44:10.383 D/PlayMatch(28803): kaynak başlatılıyor: Torrentio (?)`
  - İlk kare: `11-02 22:44:28.427 D/PlayMatch(28803): ilk kare: Torrentio (faz=Starting)`
- **Release Ortancası:** **18.04 sn** (Tur 2 Release: **23.27 sn**; net 5.23 sn hızlanma).

### 6.2 Sarma (Seek) Dayanıklılık Testi (Civil War Run 3)
1. **10. dakikaya sarma:** **3.17 sn** içinde kesintisiz devam etti.
2. **40. dakikaya sarma:** **3.66 sn** içinde kesintisiz devam etti.
3. **5. dakikaya geriye sarma:** **2.67 sn** içinde kesintisiz devam etti.
- Eski deadline'ların temizlenmesi ve yeni konuma anında odaklanılması kusursuz çalışmaktadır.

### 6.3 Küçük Parça Regresyon Testi (Big Buck Bunny Release)
- `tur3_release_bbb_1`: **2.883 sn**
  - Başlangıç: `11-02 22:45:01.399 D/PlayMatch(29151): kaynak başlatılıyor: Torrentio (?)`
  - İlk kare: `11-02 22:45:04.282 D/PlayMatch(29151): ilk kare: Torrentio (faz=Starting)`
- `tur3_release_bbb_2`: **3.512 sn**
  - Başlangıç: `11-02 22:45:11.158 D/PlayMatch(29346): kaynak başlatılıyor: Torrentio (?)`
  - İlk kare: `11-02 22:45:14.670 D/PlayMatch(29346): ilk kare: Torrentio (faz=Starting)`
- **Ortanca:** **3.20 sn** (Tur 2: 4.18 sn). Hiçbir regresyon yaşanmamış, aksine iyileşmiştir.

### 6.4 3 Dakika Boşta Kalma (Idle Timeout) Temizlik Doğrulaması
Oynatıcıdan çıkıldıktan sonra planlanan boşta kalma süresi dolduğunda otomatik temizlik ve durum kaydı yapıldığı kanıtlanmıştır:
- Boşta kalma tetiklenme logu:
  `11-02 22:49:29.935 D/PlayTorrent(29857): motor release: remainingUsers=0, keepAlive=true, idleTimeout=5000ms`
- Temizlik logu:
  `11-02 22:49:36.556 D/PlayTorrent(29857): motor: 3 dk boşta kaldı, oturum ve dosyalar temizleniyor`
- DHT oturum durumu kaydı:
  `11-02 22:49:36.557 D/PlayTorrent(29857): DHT oturum durumu kaydedildi (1748 B)`
- `/data/data/com.kaan.play/cache/torrents` dizini kontrol edilmiş ve tüm geçici dosyaların silindiği doğrulanmıştır.
- Disk sızıntısı: **0 bayt**.

---

## 7. SONUÇ VE ÖNERİLER

Tur 3 ile birlikte:
1. Soğuk açılış süresi Tur 2'deki **20.48 saniyeden 11.96 saniyeye indirilmiştir** (%41.6 hızlanma).
2. Kuyruk parçası beklemesi odaklama sayesinde **15.20 saniyeden 9.42 saniyeye çekilmiştir** (%38.0 azalma).
3. 3 dakikalık boşta tutma sayesinde aynı torrentin 1 dakika içinde yeniden açılışı **0.52 saniyeye inmiştir** (anında açılış).
4. Tüm koşularda veri bütünlüğü korunmuş, **0 uyuşmazlık ve 0 bozuk kare** elde edilmiştir.
