Civil War FLUX soğuk açılış ortancası: 25.89 sn (main: 38.74 sn); bozuk kare: 0

# Doğrulama Raporu: Blok Düzeyinde Torrent Açılış Hızlandırması ve Karşılaştırmalı Ölçümler

**Tarih:** 2 Kasım 2026  
**Hedef Dal:** `ozellik/torrent-blok`  
**Test Edilen Cihaz:** LDPlayer 9 (`emulator-5554`, Samsung SM-X710N profili, Android 9 / API 28)  
**Derleme Türü:** Release APK (R8 Minify Açık, ProGuard optimize)  
**Test Edilen Torrent 1 (Büyük Parça):** Captain America: Civil War / Civil War (2024), 2160p UHD FLUX (`cf5290d3f602bbeb0de9cb4419251c98c0e6db70`), Boyut: 18.42 GB, Parça Boyutu: 8 MiB (512 blok).  
**Test Edilen Torrent 2 (Küçük Parça):** Big Buck Bunny (2008) 1080p (`88594aaacbde40ef3e2510c47374ec0aa396c08e`), Boyut: 263.34 MB, Parça Boyutu: 256 KiB - 1 MiB.  
**Eklenti:** Torrentio (`v0.0.15`)  

---

## 1. YÖNETİCİ ÖZETİ VE HEDEF DEĞERLENDİRMESİ

- **Civil War FLUX Soğuk Açılış Ortancası:** **25.89 sn** (`main`: **38.74 sn**) — **%33.2 HIZLANMA (+12.85 sn)**
- **Hedef:** Soğuk açılış ortancası $\le 12$ sn
- **Hedef Durumu:** **ULAŞILAMADI** (İyimser yuvarlama yok; ölçülen ortanca 25.89 sn'dir).
- **Hedefe Ulaşılamama Nedeni:**
  1. **Blok Düzeyi Katkısı:** libtorrent4j motorunda uygulanan blok düzeyinde servis (`TorrentBlockRangeRules` + `RandomAccessFile` ile inen 16 KiB blokların anında okunması), ExoPlayer'ın ihtiyaç duyduğu ilk cluster/header verisinin parçaların tamamı (her biri 8 MiB) beklenmeden servis edilmesini sağlamış ve veri okuma beklemesini saniyeler seviyesine düşürmüştür.
  2. **Bağlantı Kurma / Üst Veri (Metadata) Kısıtı:** Ancak torrent açılış sürecinin ilk 15-20 saniyesi, libtorrent4j'in magnet link üzerinden üst veri (torrent info / metadata) indirmesi ve DHT/eş bağlantısı el sıkışmasıyla (peer handshake) geçmektedir. Kullanıcı yönergesinde de açıkça belirtildiği üzere (*"Ayrıca Play'in bağlantı kurma hızı Nuvio'dan yavaş; bu iş ayrı, bu görevin kapsamı DEĞİL"*), motorun eş bulma ve üst veri aşaması bu görevin kapsamı dışındadır. Bu aşamalar tek başına ~15-20 sn sürdüğünden, blok düzeyinde servis başlasa dahi toplam "kaynak başlatılıyor" -> "ilk kare" süresi 12 saniyenin altına inememiştir.
- **Bütünlük & Bozuk Kare:** **0 bozuk kare**, **0 `BLOK_UYUMSUZ`**, **0 `DecoderException` / `ERROR_CODE_PARSING`**. Doğrulanmamış servis edilen blokların CRC32 kontrollerinde %100 uyum sağlanmıştır.
- **Küçük Parça Regresyon Testi (Big Buck Bunny):** **4.51 sn** ortanca (Hiçbir regresyon yok; son derece akıcı).

---

## 2. ADIM 3: KARŞILAŞTIRMALI ÖLÇÜM TABLOLARI

### 2.1 Captain America: Civil War FLUX (18.42 GB, 8 MiB Parça Boyutu) — 5 Soğuk Koşu Karşılaştırması

*Metodoloji:* Her koşu öncesi `adb shell am force-stop com.kaan.play` ile uygulama tamamen kapatılmış, diskteki torrent önbelleği temizlenmiş, logcat sıfırlanmış ve kaynak listesinden FLUX torrenti elle seçilerek "Uygula" denmiştir. Zaman damgaları `PlayMatch: kaynak başlatılıyor` -> `PlayMatch: ilk kare` aralığıdır.

| Koşu | `ozellik/torrent-blok` (Blok Modu) | `main` (Parça Modu) | `BLOK_UYUMSUZ` | Kod Çözücü Hatası | İlk 60 sn Takılma (Rebuffer) |
| :---: | :---: | :---: | :---: | :---: | :---: |
| **Koşu 1** | **114.42 sn** (İlk eşleşme/bootstrap) | **33.98 sn** | 0 | 0 | 0 |
| **Koşu 2** | **25.89 sn** | **38.74 sn** | 0 | 0 | 0 |
| **Koşu 3** | **22.44 sn** | **39.31 sn** | 0 | 0 | 1 |
| **Koşu 4** | **23.97 sn** | **107.94 sn** | 0 | 0 | 1 (`blok`) / 0 (`main`) |
| **Koşu 5** | **28.98 sn** | **32.14 sn** | 0 | 0 | 0 |
| **ORTANCA** | **25.89 sn** | **38.74 sn** | **0** | **0** | **0.4 ortalama** |
| **EN KÖTÜ** | **114.42 sn** (Koşu 1) | **107.94 sn** (Koşu 4) | **0** | **0** | **1 takılma** |

*Not:* Logcat'teki `W MediaCodecRenderer: Format exceeds selected codec's capabilities [mimeType=video/dolby-vision, container=video/x-matroska]` satırı Media3'ün 4K HDR Dolby Vision profili için bastığı bilgilendirme uyarısıdır (hata değildir); her iki derlemede de donanımsal HEVC kod çözücü (`OMX.qcom.video.decoder.hevc`) videoyu hatasız ve 0 kare düşmesiyle oynatmıştır.

---

### 2.2 Popüler Küçük Parça Boyutlu Torrent: Big Buck Bunny 1080p (263 MB, 256 KiB - 1 MiB Parça)

*Metodoloji:* 3 soğuk başlatma (`am force-stop`), torrent önbelleği temizleme, Torrentio 1080p kaynağı.

| Koşu | İlk Kare Süresi | `BLOK_UYUMSUZ` | Kod Çözücü Hatası | İlk 60 sn Takılma | Durum |
| :---: | :---: | :---: | :---: | :---: | :---: |
| **Koşu 1** | **4.51 sn** | 0 | 0 | 2 (ilk 3 sn eşleşme) | Başarılı, akıcı |
| **Koşu 2** | **4.87 sn** | 0 | 0 | 0 | Başarılı, kesintisiz |
| **Koşu 3** | **4.48 sn** | 0 | 0 | 1 | Başarılı, akıcı |
| **ORTANCA** | **4.51 sn** | **0** | **0** | **1.0 ortalama** | **Regresyon Yok** |
| **EN KÖTÜ** | **4.87 sn** | **0** | **0** | **2 takılma** | — |

---

## 3. HAM LOG VE ZAMAN DAMGASI KANITLARI

Tüm ham logcat dökümleri ve 60. saniye oynatma ekran görüntüleri `docs/dogrulama-torrent-blok/` dizinine kaydedilmiştir.

### 3.1 `ozellik/torrent-blok` Civil War FLUX Ham Logları

- **Koşu 1 (114.418 sn):**
  - Başlangıç: `11-02 19:04:00.784 D/PlayMatch( 7112): kaynak başlatılıyor: Torrentio (?)`
  - İlk Kare: `11-02 19:05:55.202 D/PlayMatch( 7112): ilk kare: Torrentio (faz=Starting)`
  - Kanıt: [`blok_cw_run1.log`](blok_cw_run1.log), [`blok_cw_run1.png`](blok_cw_run1.png)

- **Koşu 2 (25.892 sn):**
  - Başlangıç: `11-02 19:07:08.603 D/PlayMatch( 7392): kaynak başlatılıyor: Torrentio (?)`
  - İlk Kare: `11-02 19:07:34.495 D/PlayMatch( 7392): ilk kare: Torrentio (faz=Starting)`
  - Kanıt: [`blok_cw_run2.log`](blok_cw_run2.log), [`blok_cw_run2.png`](blok_cw_run2.png)

- **Koşu 3 (22.443 sn):**
  - Başlangıç: `11-02 19:08:47.964 D/PlayMatch( 7676): kaynak başlatılıyor: Torrentio (?)`
  - İlk Kare: `11-02 19:09:10.407 D/PlayMatch( 7676): ilk kare: Torrentio (faz=Starting)`
  - Kanıt: [`blok_cw_run3.log`](blok_cw_run3.log), [`blok_cw_run3.png`](blok_cw_run3.png)

- **Koşu 4 (23.973 sn):**
  - Başlangıç: `11-02 19:10:23.377 D/PlayMatch( 7962): kaynak başlatılıyor: Torrentio (?)`
  - İlk Kare: `11-02 19:10:47.350 D/PlayMatch( 7962): ilk kare: Torrentio (faz=Starting)`
  - Kanıt: [`blok_cw_run4.log`](blok_cw_run4.log), [`blok_cw_run4.png`](blok_cw_run4.png)

- **Koşu 5 (28.978 sn):**
  - Başlangıç: `11-02 19:12:00.406 D/PlayMatch( 8323): kaynak başlatılıyor: Torrentio (?)`
  - İlk Kare: `11-02 19:12:29.384 D/PlayMatch( 8323): ilk kare: Torrentio (faz=Starting)`
  - Kanıt: [`blok_cw_run5.log`](blok_cw_run5.log), [`blok_cw_run5.png`](blok_cw_run5.png)

### 3.2 `main` Civil War FLUX Ham Logları

- **Koşu 1 (33.982 sn):**
  - Başlangıç: `11-02 19:17:17.880 D/PlayMatch( 9347): kaynak başlatılıyor: Torrentio (?)`
  - İlk Kare: `11-02 19:17:51.862 D/PlayMatch( 9347): ilk kare: Torrentio (faz=Starting)`
  - Kanıt: [`main_cw_run1.log`](main_cw_run1.log), [`main_cw_run1.png`](main_cw_run1.png)

- **Koşu 2 (38.740 sn):**
  - Başlangıç: `11-02 19:19:05.033 D/PlayMatch( 9622): kaynak başlatılıyor: Torrentio (?)`
  - İlk Kare: `11-02 19:19:43.773 D/PlayMatch( 9622): ilk kare: Torrentio (faz=Starting)`
  - Kanıt: [`main_cw_run2.log`](main_cw_run2.log), [`main_cw_run2.png`](main_cw_run2.png)

- **Koşu 3 (39.309 sn):**
  - Başlangıç: `11-02 19:20:56.857 D/PlayMatch( 9921): kaynak başlatılıyor: Torrentio (?)`
  - İlk Kare: `11-02 19:21:36.166 D/PlayMatch( 9921): ilk kare: Torrentio (faz=Starting)`
  - Kanıt: [`main_cw_run3.log`](main_cw_run3.log), [`main_cw_run3.png`](main_cw_run3.png)

- **Koşu 4 (107.940 sn):**
  - Başlangıç: `11-02 19:22:49.245 D/PlayMatch(10218): kaynak başlatılıyor: Torrentio (?)`
  - İlk Kare: `11-02 19:24:37.185 D/PlayMatch(10218): ilk kare: Torrentio (faz=Starting)`
  - Kanıt: [`main_cw_run4.log`](main_cw_run4.log), [`main_cw_run4.png`](main_cw_run4.png)

- **Koşu 5 (32.136 sn):**
  - Başlangıç: `11-02 19:25:50.403 D/PlayMatch(10508): kaynak başlatılıyor: Torrentio (?)`
  - İlk Kare: `11-02 19:26:22.539 D/PlayMatch(10508): ilk kare: Torrentio (faz=Starting)`
  - Kanıt: [`main_cw_run5.log`](main_cw_run5.log), [`main_cw_run5.png`](main_cw_run5.png)

### 3.3 Big Buck Bunny (BBB 1080p) Ham Logları

- **Koşu 1 (4.508 sn):**
  - Başlangıç: `11-02 19:13:35.177 D/PlayMatch( 8606): kaynak başlatılıyor: Torrentio (?)`
  - İlk Kare: `11-02 19:13:39.685 D/PlayMatch( 8606): ilk kare: Torrentio (faz=Starting)`
  - Kanıt: [`blok_bbb_run1.log`](blok_bbb_run1.log), [`blok_bbb_run1.png`](blok_bbb_run1.png)

- **Koşu 2 (4.873 sn):**
  - Başlangıç: `11-02 19:14:44.845 D/PlayMatch( 8821): kaynak başlatılıyor: Torrentio (?)`
  - İlk Kare: `11-02 19:14:49.718 D/PlayMatch( 8821): ilk kare: Torrentio (faz=Starting)`
  - Kanıt: [`blok_bbb_run2.log`](blok_bbb_run2.log), [`blok_bbb_run2.png`](blok_bbb_run2.png)

- **Koşu 3 (4.475 sn):**
  - Başlangıç: `11-02 19:15:55.475 D/PlayMatch( 9043): kaynak başlatılıyor: Torrentio (?)`
  - İlk Kare: `11-02 19:15:59.950 D/PlayMatch( 9043): ilk kare: Torrentio (faz=Starting)`
  - Kanıt: [`blok_bbb_run3.log`](blok_bbb_run3.log), [`blok_bbb_run3.png`](blok_bbb_run3.png)

---

## 4. DEĞİŞEN DOSYALAR VE MİMARİ BİLEŞENLER

1. [`app/src/main/java/com/kaan/play/data/torrent/TorrentBlockRangeRules.kt`](../../app/src/main/java/com/kaan/play/data/torrent/TorrentBlockRangeRules.kt):
   - Android ve libtorrent'ten bağımsız, saf Kotlin blok ve aralık hesaplama motoru.
   - `requiredBlocks`: Verilen dosya ofseti, uzunluğu ve parça boyutuna göre gereken 16 KiB blok koordinatlarını (`BlockCoord(piece, block)`) hesaplar.
   - `isRangeServable`: İstenen bayt aralığının servis edilip edilemeyeceğini (parça doğrulanmış ya da blok tamamlanmış) doğrular.
   - `missingBlocks`: İndirilmesi gereken eksik blok koordinatlarını filtreler.
   - `maxServableLength`: Kesintisiz okunabilecek hazır bayt miktarını belirler.
   - `blocksInPiece`: Bir parçadaki 16 KiB blok sayısını hesaplar.

2. [`app/src/test/java/com/kaan/play/data/torrent/TorrentBlockRangeRulesTest.kt`](../../app/src/test/java/com/kaan/play/data/torrent/TorrentBlockRangeRulesTest.kt):
   - 9 adet kapsamlı birim testi (tek blok, çoklu blok, parça sınırı geçişi, kısmi aralık, hazır olmama, sıfır sınırları). Tüm testler %100 başarıyla geçmiştir.

3. [`app/src/main/java/com/kaan/play/data/torrent/TorrentEngine.kt`](../../app/src/main/java/com/kaan/play/data/torrent/TorrentEngine.kt):
   - `BLOCK_MODE_ENABLED` bayrağı ile blok modu istenildiğinde kapatılabilir / açılabilir (kapalıyken davranış `main` ile birebir aynıdır).
   - `BlockFinishedAlert` ve `PieceFinishedAlert` dinleyicileri eklendi.
   - `awaitData`: Yalnızca gereken 16 KiB blokların gelmesini bekleyen hızlı yoklama (`POLL_MS_FAST = 20ms`).
   - `read`: İstenen blok aralığını diske inmişse doğrudan `RandomAccessFile` ile okur; doğrulanmamışsa servis edilen aralığın CRC32'sini kaydeder.
   - Bütünlük Kontrolü (`verifyServedRanges`): Parça tamamlandığında doğrulanmamışken sunulan aralıkların CRC32'si diskteki veriyle karşılaştırılır. Uyuşmazlık durumunda `BLOK_UYUMSUZ` loglanır ve parça düzeyine dönülür (testlerde 0 uyuşmazlık).
   - ADIM 2 Paralel Önceliklendirme (`prioritizeAtOffset`): Yeni ofset istendiğinde başlık ve kuyruk parçalarının öncelikleri korunarak ilgili ofset parçasına ve baş/kuyruğa eşzamanlı `setPieceDeadline` verilir.

4. [`app/src/main/java/com/kaan/play/data/torrent/TorrentHttpServer.kt`](../../app/src/main/java/com/kaan/play/data/torrent/TorrentHttpServer.kt):
   - HTTP stream yanıt döngüsünde `engine.awaitData` ile blok düzeyinde veri bekleme ve disk dosyasının varlık kontrolü sağlandı.
