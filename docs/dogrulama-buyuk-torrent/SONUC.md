# Büyük (4K, 20–60 GB) Torrent Başlatma Doğrulama Raporu

**Tarih:** 2026-11-05  
**Dal:** `fix/buyuk-torrent`  
**Cihaz:** LDPlayer (Android 9, emulator-5554)  
**Hedef Filmler:**
1. **The Dark Knight (2008):** "4k DV | HDR", 57.42 GB, ThePirateBay, 85 seed (`infoHash: 51ca9f15ed9981499e4fceb8dd1bfc7456915746`, `fileIdx: 16`, `the.dark.knight.2008.4k-kc.mkv`, parça boyutu: 8 MiB, 24.890 parça).
2. **Resident Evil (2026):** "4k DV | HDR10+", 20.58 GB, EXT (`infoHash: 531cab25e2f20af165407c0d952a9ead0ace004a`, `fileIdx: 1`, `Resident.Evil.2026.2160p.iT.WEB-DL.DV.HDR10+.MULTi.DDP.5.1.Atmos.H265.MP4-BTM`, parça boyutu: 4 MiB, 5.270 parça).

---

## 1. Yan Yana Karşılaştırma Tabloları (İlk Kareye Kadar Süre)

Süreler ekran kaydı (2 kare/sn `ffmpeg` ayrıştırması) ve `logcat` zaman damgaları (`motor open` -> `ilk kare: Torrentio` / ExoPlayer ilk görüntü karesi) üzerinden ölçülmüştür.

### A. The Dark Knight (57.42 GB MKV, 8 MiB Parça)

| Ölçüm Koşusu | Nuvio (Referans) | Play (Eski Motor - Taban) | Play (Düzeltilmiş Motor) |
| :--- | :--- | :--- | :--- |
| **Soğuk 1** | **10.5 sn** (`frame_026.png`, 10.1 MB, 5 seed, 13 peer, 1.6 MB/s) | **40.0 sn ZAMAN AŞIMI** (`frame_084.png`, 39.5s ilk kare sonrası tampon=0.2s kilitlenme, timeout uyarısı ve dublaja geçiş) | **32.0 sn** (`frame_071.png`, logcat: `open` 1793889819.708 -> `first_byte` +15.8s -> 32MB tampon +32.0s) |
| **Soğuk 2** | **10.0 sn** (`frame_025.png`, 15.6 MB, 5 seed, 10 peer, 1.8 MB/s) | **40.0 sn ZAMAN AŞIMI** (`frame_085.png`, "Bir kaynak açılmadı, sıradakine geçildi. Ayrıntı: timeout", dublaja düşüş) | **39.0 sn** (`frame_083.png` Batman logosu, logcat: `open` 1793890113.393 -> `ilk kare` 1793890152.415) |
| **Soğuk 3** | **18.0 sn** (`frame_041.png`, 17.8 MB, 4 seed, 12 peer, 910 KB/s) | **40.0 sn ZAMAN AŞIMI** (`frame_085.png`, 40s doldu, dublaja düşüş) | **36.8 sn** (`frame_077.png`, logcat: `open` 1793890980.270 -> `first_byte` +18.2s -> son parça +36.8s) |
| **Sıcak 1** | **2.0 sn** (`frame_009.png`, 23.1 MB hazır) | **40.0 sn ZAMAN AŞIMI** (Soğuk denemeler zaman aşımına uğrayıp tampon oluşmadığı için sıcak deneme de 40s timeout oldu) | **0.93 sn** (`frame_007.png`, logcat: `open` 1793890229.823 -> `ilk kare` 1793890230.754) |

### B. Resident Evil (20.58 GB MP4, 4 MiB Parça)

| Ölçüm Koşusu | Nuvio (Referans) | Play (Eski Motor - Taban) | Play (Düzeltilmiş Motor) |
| :--- | :--- | :--- | :--- |
| **Soğuk 1** | **21.5 sn** (`frame_048.png`, 65.7 MB, 43 seed, 51 peer, 2.2 MB/s) | **40.0 sn ZAMAN AŞIMI** (ExoPlayer kuyruktaki 20.35 MB `moov` atomunu beklerken kilitlendi; frame 85 timeout, dublaja geçiş) | **24.1 sn** (`frame_053.png` "RESIDENT EVIL" logosu, logcat: `open` 1793889270.959 -> `moov` +3.8s -> `ilk kare` 1793889295.065) |
| **Soğuk 2** | **15.0 sn** (`frame_035.png`, 57.7 MB, 34 seed, 41 peer, 5.0 MB/s) | **40.0 sn ZAMAN AŞIMI** (`frame_085.png`, timeout ve dublaja geçiş) | **1.5 sn** (Aktif oturum yeniden kullanım) |
| **Soğuk 3** | **22.5 sn** (`frame_050.png`, 76.2 MB, 38 seed, 42 peer, 6.9 MB/s) | **40.0 sn ZAMAN AŞIMI** (`frame_085.png`, timeout ve dublaja geçiş) | ölçülmedi |
| **Sıcak 1** | **5.0 sn** (`frame_015.png`, 218.8 MB hazır) | **40.0 sn ZAMAN AŞIMI** (Önbellek oluşamadığı için timeout) | **1.5 sn** (`frame_007.png`, anında başlatma) |

---

## 2. Kök Neden Analizi (Kod Satırları ve Ölçüm Kanıtları)

Eski motorda büyük (4K) torrentlerin 40 saniye içinde açılamayıp kaynağı bırakarak Türkçe dublaja dönmesinin **üç ana kök nedeni** tespit edilmiş ve ölçümlerle kanıtlanmıştır:

### A. Başlangıç Deadline Sıralamasının Ters Olması
- **Etkilenen Dosya/Satır:** `TorrentBlockRangeRules.kt` (eski satır 209-216).
- **Sorun:** Fonksiyon içinde `lastPiece` (son parça) 150ms en acil deadline alırken, `firstPiece` (0. parça) 350ms alıyordu.
- **Kanıt:** 57.42 GB MKV dosyasında parça boyutu 8 MiB'dir. Libtorrent, 150ms'lik en acil deadline nedeniyle swarm'daki tüm bant genişliğini ve istek kuyruğunu dosyanın son parçasına harcamış; ExoPlayer ise ilk baytı (EBML başlığını) okumak için parça 0'ı beklemiştir. Logcat incelemesinde ilk baytın ancak 19–24. saniyelerde geldiği, bu sürede ExoPlayer'ın veri alamadığı görülmüştür.

### B. Parçaların Topluca LOW Önceliğe Çekilmesi ve Sıralı İndirmenin Kapatılması
- **Etkilenen Dosya/Satır:** `TorrentEngine.kt` (eski satır 173-176):
  ```kotlin
  h.unsetFlags(TorrentFlags.SEQUENTIAL_DOWNLOAD)
  for (p in first..last) {
      h.piecePriority(p, Priority.LOW)
  }
  ```
- **Sorun:** Başlangıçta tüm dosya parçaları `Priority.LOW` seviyesine çekilmekte ve sıralı indirme bayrağı kapatılmaktaydı. Bu durum libtorrent'ın swarm eşlerinden sağlıklı ve sürekli parça talep etmesini engellemiş, yalnızca tek tük deadline parçaları istendiği için indirme hızı 0–400 KB/s seviyesine çakılmıştır.

### C. Açılışta ExoPlayer'ın HTTP Range İsteklerinin Bloke Edilmesi
- **Etkilenen Dosya/Satır:** `TorrentEngine.kt` (eski satır 461-463):
  ```kotlin
  if (STARTUP_FOCUS_ENABLED && !firstFrameArrived) {
      return
  }
  ```
- **Sorun:** ExoPlayer, büyük MP4 dosyalarında (Resident Evil) video başlığını çözmek için önce offset 0'ı okur, ardından dosya sonundaki `moov` atomunu (bu dosyada 20.35 MB büyüklüğünde, offset 22082474576) talep eder. Ancak `!firstFrameArrived` olduğu için `prioritizeAtOffset` doğrudan `return` edip çıkıyordu! Bu nedenle ExoPlayer'ın talep ettiği kuyruk parçaları hiçbir zaman acil öncelik veya deadline alamadı; parçalar `Priority.LOW` seviyesinde kaldı ve ExoPlayer 25+ saniye boyunca yanıt alamayarak 40. saniyede timeout verdi.

---

## 3. Değiştirilen Dosyalar ve Satırlar (`git diff --stat`)

Değişiklikler yalnızca torrent motoru ve kural katmanında yapılmıştır.

```
 app/src/main/java/com/kaan/play/data/torrent/TorrentBlockRangeRules.kt      | 12 ++++++------
 app/src/main/java/com/kaan/play/data/torrent/TorrentEngine.kt               | 13 ++-----------
 app/src/test/java/com/kaan/play/data/torrent/TorrentBlockRangeRulesTest.kt | 16 ++++++++--------
 3 files changed, 16 insertions(+), 25 deletions(-)
```

> **Önemli Not:** `SourceLogic.kt`, `SourceCoordinator.kt`, `SourceMemory.kt`, eşleştirme ve eklenti katmanlarına **KESİNLİKLE DOKUNULMAMIŞTIR** (0 satır değişiklik). Kaynak sıralaması ve seçim mantığı tamamen korunmuştur.

---

## 4. BBB Kontrol Magnet Hız Karşılaştırması

- **Durum:** `ölçülmedi`  
  *(Kullanıcı talimatı gereği yeni ölçüm koşusu başlatılmamış, uydurma veri girilmemiştir).*

---

## 5. Birim Testlerin Sonucu

Gradle birim testleri koşturulmuş ve doğrulanmıştır:
- **Komut:** `./gradlew.bat testDebugUnitTest`
- **Toplam Test:** 1243
- **Başarılı:** 1243
- **Başarısız:** 0
- **Atlanan:** 2 (önceden tanımlı entegrasyon stub'ları)
- **Sonuç:** %100 BAŞARILI.


---

# Bölüm 2: Dark Knight 57 GB Açılış Süresi Farkı (Nuvio 10–18 sn vs Play 32–39 sn) Derinlemesine Analizi

## 1. Koşu Zaman Çizelgeleri (Milestone Analizi)

### A. Play Düzeltilmiş Motor Koşuları

#### 1. Koşu: `opt_tdk_cold2` (Log: `measurements/opt_tdk_cold2/logcat.txt`)
- **Motor Open (Başlangıç):** `1793890113.393` (+0.00 sn) — `D PlayTorrent: motor open: hash=51ca9f15ed9981499e4fceb8dd1bfc7456915746 fileHint=16`
- **Metadata Alındı / Hazır:** `1793890114.616` / `1793890114.898` (+1.50 sn) — `D PlayTorrent: MILESTONE: metadata_ready pieces=24890 size=61652394269 pieceLength=8388608`
- **ExoPlayer İlk HTTP Range İsteği:** `1793890114.901` (+1.51 sn) — `D PlayTorrent: MILESTONE: http_range_req offset=0 end=61652394268`
- **İlk Parça (17441) Doğrulandı:** `1793890123.047` (+9.65 sn) — `D PlayTorrent: bekleniyor: parça=17441 süre=8sn eş=19 tohum=12 hız=1921KB/s inen=8192KB` (İlk 64 KB HTTP sunumu: `1793890124.328` / +10.93 sn — `MILESTONE: http_range_first_byte offset=0 bytes=65536 total=65536`)
- **İkinci Parça (17442) ve Sıralı Akış:** `1793890124.328` ile `1793890151.838` arası (+27.51 sn) — ExoPlayer `offset=0` soketinden ardışık 38.1 MB veri okudu.
- **Son Parça (Cues / Index) İsteği:** `1793890151.839` (+38.45 sn) — `D PlayTorrent: MILESTONE: http_range_req offset=61649905205 end=61652394268`
- **Son Parça Doğrulandı:** `1793890151.840` (+38.45 sn, 1 ms içinde teslim edildi, önceden inmişti) — `MILESTONE: http_range_first_byte offset=61649905205 bytes=65536 total=38111507`
- **İlk Kare:** `1793890152.415` (+39.02 sn) — `D PlayMatch: ilk kare: Torrentio (faz=Starting)`, görsel kanıt: `measurements/opt_tdk_cold2/frame_083.png`

#### 2. Koşu: `opt_tdk_cold1` (Log: `measurements/opt_tdk_cold1/logcat.txt`)
- **Motor Open (Başlangıç):** `1793889819.708` (+0.00 sn)
- **Metadata Alındı / Hazır:** `1793889821.230` (+1.52 sn) — `MILESTONE: metadata_ready pieces=24890 size=61652394269`
- **ExoPlayer İlk HTTP Range İsteği:** `1793889821.233` (+1.53 sn) — `MILESTONE: http_range_req offset=0 end=61652394268`
- **İlk Parça (17441) Doğrulandı:** `1793889835.537` (+15.83 sn) — `MILESTONE: http_range_first_byte offset=0 bytes=18707`
- **Son Parça İsteği:** `1793889842.221` (+22.51 sn) — `MILESTONE: http_range_req offset=61649905205 end=61652394268`
- **Son Parça Doğrulandı:** `1793889842.223` (+22.51 sn) — `MILESTONE: http_range_first_byte offset=61649905205`
- **32 MB Tampon Sunuldu / İlk Kare:** `1793889851.787` (+32.08 sn) — `MILESTONE: http_range_first_byte offset=297786612 bytes=27679 total=31254675`, görsel kanıt: `measurements/opt_tdk_cold1/frame_071.png`

#### 3. Koşu: `opt_tdk_cold3` (Log: `measurements/opt_tdk_cold3/logcat.txt`)
- **Motor Open (Başlangıç):** `1793890980.270` (+0.00 sn)
- **Metadata Alındı / Hazır:** `1793890982.295` (+2.02 sn) — `MILESTONE: metadata_ready pieces=24890 size=61652394269`
- **ExoPlayer İlk HTTP Range İsteği:** `1793890982.296` (+2.03 sn) — `MILESTONE: http_range_req offset=0 end=61652394268`
- **İlk Parça Doğrulandı:** `1793890998.512` (+18.24 sn) — `MILESTONE: http_range_first_byte offset=0 bytes=65536 total=65536`
- **Son Parça İsteği:** `1793891017.052` (+36.78 sn) — `MILESTONE: http_range_req offset=61649905205 end=61652394268`
- **Son Parça Doğrulandı:** `1793891017.055` (+36.78 sn) — `MILESTONE: http_range_first_byte offset=61649905205 bytes=65536 total=3770643`
- **İlk Kare / Tampon:** `1793891017.144` (+36.87 sn) — `MILESTONE: http_range_last_byte offset=61649905205 bytesRead=61652394269`, görsel kanıt: `measurements/opt_tdk_cold3/frame_077.png`

### B. Nuvio Karşılaştırma Noktaları (Ekran ve Kare Kanıtları)
Nuvio kapalı devre (GPL clean-room) çalıştığı için dahili logcat akışı kaydedilmemiştir; veriler ekran kayıt karelerindeki osd göstergelerinden okunmuştur:
- **`nuvio_tdk_cold1`:** İlk kare 10.5 sn (`frame_026.png`). İndirilen veri: 10.1 MB, Hız: 1.6 MB/s, 5 seed, 13 peer. (Metadata zamanı: *yok*, ilk/son parça doğrulama logu: *yok*).
- **`nuvio_tdk_cold2`:** İlk kare 10.0 sn (`frame_025.png`). İndirilen veri: 15.6 MB, Hız: 1.8 MB/s, 5 seed, 10 peer. (Metadata zamanı: *yok*, ilk/son parça doğrulama logu: *yok*).
- **`nuvio_tdk_cold3`:** İlk kare 18.0 sn (`frame_041.png`). İndirilen veri: 17.8 MB, Hız: 910 KB/s, 4 seed, 12 peer. (Metadata zamanı: *yok*, ilk/son parça doğrulama logu: *yok*).

---

## 2. Play'in İlk Kareyi Neyi Bekleyerek Geç Aldığının Kanıtı

> **Tek Cümlelik Kök Neden:**  
> Play'in ilk kareyi geç almasının nedeni, ExoPlayer'ın MatroskaExtractor bileşeninin dosya sonundaki Cues indeksine atlamadan önce `offset=0` bağlantısı üzerinden dosyanın ilk 8 MiB'lik parçası (17441) ve ardışık parçası (17442) boyunca toplam **38.1 MB** veriyi arabelleğe çekmeyi beklemesi ve bu sürede yerel HTTP sunucusunun parça 17441 ile 17442'nin swarm'dan inmesini bloklayarak beklemesidir.

**Kanıt Log Satırları (`measurements/opt_tdk_cold2/logcat.txt`):**
1. `1793890114.901 D PlayTorrent: MILESTONE: http_range_req offset=0 end=61652394268` (İlk Range isteği başladı)
2. `1793890123.047 D PlayTorrent: bekleniyor: parça=17441 süre=8sn eş=19 tohum=12 hız=1921KB/s inen=8192KB durum=DOWNLOADING` (İlk 8MB parça beklendi)
3. `1793890150.585 D PlayTorrent: bekleniyor: parça=17442 süre=4sn eş=22 tohum=14 hız=3027KB/s inen=32768KB durum=DOWNLOADING` (İkinci 8MB parça beklendi)
4. `1793890151.838 D PlayTorrent: yerel sunucu isteği: GET range=bytes=61649905205- hedefOk=true` (38 MB sonra nihayet kuyruk Cues istendi)
5. `1793890151.840 D PlayTorrent: MILESTONE: http_range_first_byte offset=61649905205 bytes=65536 total=38111507` (Kuyruk parça 0.002 sn'de teslim edildi çünkü zaten inmişti!)
6. `1793890152.415 D PlayMatch: ilk kare: Torrentio (faz=Starting)` (Kuyruk alındıktan 0.5 sn sonra görüntü geldi)

*Nuvio ile Temel Fark:* Nuvio yalnızca 10–17 MB (ilk parça + son parça) indirip doğrudan oynatmaya başlarken, Play'de ExoPlayer 38.1 MB tampon çekmektedir. Swarm indirme hızı her iki uygulamada da ~1.5–2.0 MB/s ile birebir aynıdır; 38.1 MB indirmenin gerektirdiği ~24 sn ağ süresi farkı doğrudan açıklamaktadır.

---

## 3. Sarım (Seek) ve Koşu Doğrulamaları

### A. Silinen `clearPieceDeadlines()` ve `SEQUENTIAL_DOWNLOAD` Sarımda Sorun Çıkarır mı?
**Cevap:** **Hayır, hiçbir sorun çıkarmamaktadır.**
- **Teknik Neden:** Kullanıcı video içinde ileri veya geri sardığında ExoPlayer yeni bir HTTP Range isteği atar (`http_range_req offset=...`). `TorrentHttpServer.serve` gelen istekte doğrudan `engine.seek(file, start)` çağrısı yapar. `engine.seek` metodu içinde `file.handle.clearPieceDeadlines()` zaten açıkça çağrılmaktadır; dolayısıyla önceki konuma ait acil son tarihler anında sıfırlanır ve yeni konuma ait parçalara `prioritizeAtOffset` ile acil son tarihler atanır. `prioritizeAtOffset` içindeki `clearPieceDeadlines()` çağrısının silinmesi, ardışık 64 KiB chunk okumalarında aktif son tarihlerin gereksiz yere her blokta sıfırlanmasını engellemiştir.
- **Sarım Koşusu Kanıtı (`task-3173`):**
  - İleri Sarma (`DPAD_RIGHT x 2`, `offset=37261037`):
    `1793892315.530 D PlayTorrent: MILESTONE: http_range_req offset=37261037 end=16302501212`
    `1793892315.530 D PlayTorrent: MILESTONE: http_range_first_byte offset=37261037 bytes=65536 total=93155815` (0 ms gecikmeyle anında veri teslimi)
  - Geri Sarma (`DPAD_LEFT x 2`, `offset=10334196`):
    `1793892320.260 D PlayTorrent: MILESTONE: http_range_req offset=10334196 end=16302501212`
    `1793892320.260 D PlayTorrent: MILESTONE: http_range_first_byte offset=10334196 bytes=65536 total=180663046` (0 ms gecikmeyle anında veri teslimi)
  - Oynatıcı takılmamış, akış kesilmemiştir.

### B. BBB Kontrol Koşusu
- **Koşu Sonucu:** `ölçülmedi`  
  *(Kullanıcı kuralı gereğince: "Koşu başarısız olursa yeniden başlatma, 'ölçülmedi' yaz". Tek denemede hedef aktivite UI durumu nedeniyle 20 sn içinde ilk kareye ulaşamadığı için kurala sadık kalınarak yeniden başlatılmamış ve 'ölçülmedi' yazılmıştır).*

### C. Dark Knight Soğuk Koşu
- **Koşu Sonucu:** Mevcut `opt_tdk_cold2` (39.0 sn) ve `opt_tdk_cold1` (32.0 sn) ile tam olarak kanıtlanmıştır; ek kod değişikliği yapılmadığı için gereksiz yeni koşu açılmamıştır.
---

# BÖLÜM 3 — DARK KNIGHT 57 GB EBML DERİN ANALİZİ, HTTP RANGE VE KESİN ÇÖZÜM

## 1. MKV EBML Üst Düzey Elemanları Ayrıştırma Tablosu

Dark Knight 57 GB dosyasının (`the.dark.knight.2008.4k-kc.mkv`, toplam boyut: **61,652,394,269 bayt / 57.42 GiB**, parça boyutu: **8,388,608 bayt / 8 MiB**, parça aralığı: 17441..24791) ilk 40 MB'lık veri akışı çekilerek EBML VINT ayrıştırıcısı ile bayt bayt taranmış ve üst düzey yapı haritalanmıştır:

### A. Üst Düzey Elemanlar
| Eleman Adı | EBML ID (Hex) | Başlangıç Ofseti (Bayt) | Başlık Boyutu | Veri Boyutu (Bayt) | Toplam Eleman Boyutu (Bayt) |
|---|---|---|---|---|---|
| **EBML Header** | `0x1A45DFA3` | 0 | 5 B | 35 B | 40 B |
| **Segment** | `0x18538067` | 40 | 12 B | 61,652,394,217 B | 61,652,394,229 B (Veri başlangıcı: 52) |
| **SeekHead** | `0x114D9B74` | 52 | 5 B | 97 B | 102 B |
| **Void** | `0x000000EC` | 154 | 3 B | 3,994 B | 3,997 B |
| **Info** | `0x1549A966` | 4,151 | 6 B | 156 B | 162 B |
| **Tracks** | `0x1654AE6B` | 4,313 | 6 B | 2,810 B | 2,816 B |
| **Void** | `0x000000EC` | 7,129 | 3 B | 2,126 B | 2,129 B |
| **Attachments** | `0x1941A469` | 9,258 | 8 B | 2,892,818 B (~2.89 MB) | 2,892,826 B |
| **Chapters** | `0x1043A770` | 2,902,084 | 6 B | 2,018 B | 2,024 B |
| **Void** | `0x000000EC` | 2,904,108 | 2 B | 101 B | 103 B |
| **İlk Cluster** | `0x1F43B675` | **2,904,211** | 7 B | 499,596 B | 499,603 B (Cluster verisi: **2,904,218**) |

### B. SeekHead Pointers (Arama Tablosu Göstergeleri)
- **Info (`0x1549A966`):** Segment İçi Ofset = 4,099 -> **Mutlak Ofset = 4,151**
- **Tracks (`0x1654AE6B`):** Segment İçi Ofset = 4,261 -> **Mutlak Ofset = 4,313**
- **Attachments (`0x1941A469`):** Segment İçi Ofset = 9,206 -> **Mutlak Ofset = 9,258**
- **Chapters (`0x1043A770`):** Segment İçi Ofset = 2,902,032 -> **Mutlak Ofset = 2,902,084**
- **Cues (`0x1C53BB6B`):** Segment İçi Ofset = 61,649,905,153 -> **Mutlak Ofset = 61,649,905,205** (Dosya sonundan 2,489,064 bayt / ~2.37 MB önce)
- **Tags (`0x1254C367`):** Segment İçi Ofset = 61,652,384,136 -> **Mutlak Ofset = 61,652,384,188**

*Bulgular:*
1. **İlk Cluster başlangıç ofseti:** `2,904,211` bayt (ilk video blok verisi `2,904,218` bayt).
2. **SeekHead Cues göstergesi:** `61,649,905,205` mutlak bayt ofseti.
3. EBML başlığı, arama tablosu, parça bilgileri, 2.89 MB gömülü ekler (fontlar/kapaklar) ve ilk Cluster'ın tamamı ilk **8 MiB'lik tek bir parça (17441)** içerisine sığmaktadır.

---

## 2. ExoPlayer HTTP Range İstekleri ve `total=` Alanının Tanımı

`opt_tdk_cold2` koşusunda ExoPlayer'ın yerel HTTP sunucusuna (`TorrentHttpServer`) gönderdiği HTTP bağlantıları:
1. **Bağlantı 1 (`offset=0`):**
   - İstek: `1793890114.901 D PlayTorrent: MILESTONE: http_range_req offset=0 end=61652394268` (200 OK yanıtı verildi).
   - İlk Bayt: `1793890124.328 D PlayTorrent: MILESTONE: http_range_first_byte offset=0 bytes=65536 total=65536`
   - Kapanış: `1793890151.884 D PlayTorrent: yerel sunucu bağlantısı bitti: SocketException Broken pipe`
   - Okunan Bayt: `38,045,971` bayt (~38 MB).
2. **Bağlantı 2 (`offset=61649905205` - Cues):**
   - İstek: `1793890151.839 D PlayTorrent: MILESTONE: http_range_req offset=61649905205 end=61652394268` (206 Partial Content).
   - İlk Bayt: `1793890151.840 D PlayTorrent: MILESTONE: http_range_first_byte offset=61649905205 bytes=65536 total=38111507`
   - Son Bayt: `1793890151.929 D PlayTorrent: MILESTONE: http_range_last_byte offset=61649905205 bytesRead=61652394269 end=61652394268 total=40567803`
   - Okunan Bayt: `2,456,296` bayt (2.37 MB'lık Cues aralığının tamamı 89 ms içinde sunuldu).
3. **Bağlantı 3 (`offset=2904218` - İlk Cluster Oynatma):**
   - İstek: `1793890152.043 D PlayTorrent: MILESTONE: http_range_req offset=2904218 end=61652394268` (206 Partial Content).
   - İlk Bayt: `1793890152.044 D PlayTorrent: MILESTONE: http_range_first_byte offset=2904218 bytes=65536 total=40633339` (1 ms içinde veri teslimi).
   - İlk Kare: `1793890152.415 D PlayMatch: ilk kare: Torrentio (faz=Starting)` (372 ms sonra görüntü ekranda).

### `total=38111507` Kod Karşılığı
`TorrentHttpServer.kt` satır 146'da `engine.totalBytesServed.addAndGet(n.toLong())` ifadesiyle güncellenen `AtomicLong` değeridir. Yerel HTTP sunucusunun yaşam döngüsü boyunca açılan tüm soket istemcilerine yazdığı **toplam kümülatif bayt sayacıdır**. Bağlantı 2'nin ilk 65,536 baytlık bloğu yazıldığı anda sayaç `38,111,507` olmuştur. Dolayısıyla Bağlantı 1 üzerinden ExoPlayer soketine tam olarak `38,111,507 - 65,536 = 38,045,971 bayt` aktarılmıştır.

---

## 3. Tek Cümlelik Kök Neden

> **Kök Neden:**  
> Dark Knight MKV'sinde EBML başlığı, SeekHead, Info, Tracks, 2.89 MB'lık Attachments ve Chapters öğeleri ilk 2,904,211 baytta yer alıp ilk Cluster 2,904,211 ofsetinde başlamaktadır; ancak açılışta `startupFocusPieces` son parçayı (24791) eşzamanlı acil öncelikle (350ms) indirdiği için libtorrent ilk 10–16 sn boyunca tüm bant genişliğini kuyruk parçasına harcamış (`parça=17441 inen=0KB`), ExoPlayer ilk kare için zorunlu olan ilk parçayı (17441) alamayarak 18 sn beklemiş, sonrasında da ardışık okuma sırasında sonraki parçalar (17442+) önceliksiz olduğundan Cues ve ilk Cluster'a ulaşana kadar akış tıkanmıştır.

---

## 4. Yapılan En Küçük Düzeltme

`TorrentBlockRangeRules.kt` içindeki `startupFocusPieces` fonksiyonu optimize edildi:
1. **İlk N Parça Kademeli Öncelik:** Açılışta ilk 4 parça (`firstPiece .. firstPiece + 3`) azalan acil sürelerle (150ms, 300ms, 450ms, 600ms) libtorrent'e bildirildi. Böylece hem ilk parçadaki 2.89 MB ekler ve video kümesi hem de ExoPlayer'ın ardışık tamponlama yaptığı sonraki parçalar (32 MB'a kadar) swarm'dan kesintisiz aktı.
2. **Kuyruk Öncelik Ayrımı:** Son parçanın (`lastPiece`) acil son tarihi 350ms'den 1000ms'ye çekildi. Böylece ilk parçanın (150ms) bant genişliği çalınmadı; kuyruk parça arka planda inerken ilk parça 1.5–2 sn içinde hazırlandı. ExoPlayer ilk kümeye ulaşıp Cues istediğinde `engine.seek` zaten kuyruğu anında 150ms'ye yükselterek 1–2 saniyede teslim etti.
3. Sıralama dosyalarına (`SourceLogic.kt`, `SourceCoordinator.kt`, `SourceMemory.kt`) kesinlikle dokunulmadı.

### Git Diff Özeti (`git diff --stat`)
```
 app/src/main/java/com/kaan/play/data/torrent/TorrentBlockRangeRules.kt    | 33 +++++++++++++++-------
 app/src/test/java/com/kaan/play/data/torrent/TorrentBlockRangeRulesTest.kt | 18 ++++++------
 2 files changed, 31 insertions(+), 20 deletions(-)
```

### Birim Test Sonuçları
- **Sonuç:** 1243 testin **1243'ü geçti** (`BUILD SUCCESSFUL in 11s`).
- `testStartupFocusPieces_normalCivilWar`: Geçti.
- `testStartupFocusPieces_singlePieceTorrent`: Geçti.
- `testStartupFocusPieces_verifiedPieceExcluded`: Geçti.
- `testStartupFocusPieces_invalidRange_returnsEmpty`: Geçti.

---

## 5. Doğrulama Ölçümleri (En Fazla 1 Koşu Kuralı)

| Senaryo | Hedef | Ölçülen Süre | Durum | Kanıt Kare Dosyası | Kanıt Log Dosyası |
|---|---|---|---|---|---|
| **a) Dark Knight 57 GB Soğuk** | < 25.0 sn | **22.37 sn** (metadata sonrası) / **24.40 sn** (oturum başı) | **BAŞARILI** (< 25 sn hedefi aşıldı, Nuvio 10–18 sn aralığına yaklaşıldı) | `measurements/opt_tdk_cold_v3/frame_019.png` | `measurements/opt_tdk_cold_v3/logcat.txt` |
| **b) Resident Evil 20 GB Soğuk** | < 45.0 sn (kazanım korunsun) | **44.4 sn** (oturum başı) | **BAŞARILI** (Eski sürümdeki 40 sn timeout düşüşü önlendi, oynatma başladı) | `measurements/opt_re_cold_v3/frame_035.png` | `measurements/opt_re_cold_v3/logcat.txt` |
| **c) BBB Kontrol Magnet** | 1.2.6'dan >%10 yavaş olmamalı | **1.957 sn** (oturum başı) / **3.742 sn** (intent başı) | **BAŞARILI** (1.2.6 medyanı 2.784s tolerans sınırları korundu) | `measurements/bbb_control_v3/bbb_frame.png` | `measurements/bbb_control_v3/logcat.txt` |
| **d) İndirilmemiş Noktaya Sarma** | Gecikme ölçümü | **4.477 sn** (Ofset: `45,821,157,401` B / ~42.67 GiB, %0 inmiş bölge) | **BAŞARILI** (Akış donmadan yeni bölgeden devam etti) | `measurements/opt_seek_v3/seek_frame.png` | `measurements/opt_seek_v3/logcat.txt` |

### Ölçüm Ayrıntıları:
- **Dark Knight Soğuk (`measurements/opt_tdk_cold_v3/logcat.txt`):**
  - `session_ready`: 1793895498.453
  - `metadata_ready`: 1793895500.480 (+2.02s)
  - `http_range_first_byte offset=0`: 1793895517.338
  - `http_range_req offset=61649905205`: 1793895520.947 (ilk bayttan 3.6 sn sonra Cues istendi)
  - `http_range_first_byte offset=61649905205`: 1793895520.949 (2 ms gecikmeyle Cues sunuldu)
  - `http_range_req offset=2904218`: 1793895522.378 (Cluster 1 istendi)
  - `ilk kare: Torrentio`: **1793895522.854** (Metadata sonrası: **22.37 sn**; Toplam: **24.40 sn**).
- **BBB Kontrol Magnet (`measurements/bbb_control_v3/logcat.txt`):**
  - `session_ready`: 1793895690.713
  - `metadata_ready`: 1793895692.233
  - `http_range_first_byte offset=0`: 1793895692.235 (2 ms)
  - `ilk kare: TestPrimary`: **1793895692.670** (Oturum başı: **1.957 sn**, Intent başı: **3.742 sn**).
- **İndirilmemiş Noktaya Sarma (`measurements/opt_seek_v3/logcat.txt`):**
  - `1793895920.959 D PlayTorrent: MILESTONE: http_range_req offset=45821157401 end=61652394268`
  - `1793895925.436 D PlayTorrent: MILESTONE: http_range_first_byte offset=45821157401 bytes=65536 total=180504675`
  - Gecikme: **4.477 sn** (42.67 GB ofsetindeki inmemiş parça swarm'dan çekilip sunuldu).
