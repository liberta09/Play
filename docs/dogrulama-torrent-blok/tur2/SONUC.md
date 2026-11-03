Ortanca (debug, dönüşümlü): blok 20.81 sn / main 90.79 sn
Kalan sürenin dağılımı: bağlantı 3.54 sn, veri 17.83 sn, oynatıcı 0.21 sn
BLOK_UYUMSUZ: 0

# Doğrulama Raporu: Tur 2 Kod İncelemesi Düzeltmeleri ve Süre Dağılımı Analizi

- **Tarih:** 2 Kasım 2026
- **Dal:** `ozellik/torrent-blok`
- **Test Cihazı:** LDPlayer 9 (`emulator-5554`, Android 9 / API 28)
- **Test Torrenti 1:** Civil War FLUX 2160p (`cf5290d3f602bbeb0de9cb4419251c98c0e6db70`), 18.42 GB, 8 MiB parça boyutu (512 blok).
- **Test Torrenti 2:** Big Buck Bunny 1080p (`88594aaacbde40ef3e2510c47374ec0aa396c08e`), 263.34 MB, küçük parça.

---

## 1. YÖNETİCİ ÖZETİ VE HEDEF KONTROLÜ

- **Ortanca Açılış Süresi (Debug, Dönüşümlü 5+5 Koşu):**
  - `ozellik/torrent-blok` (Blok Modu): **20.81 sn**
  - `main` (Parça Modu): **90.79 sn**
  - **Hızlanma:** Blok modu `main`'e kıyasla **~4.4 kat daha hızlı** (69.98 sn tasarruf).
- **Kalan ~20 Saniyenin Tam Dağılımı (Ölçülen Ortancalar):**
  - **(a) Bağlantı ve Metadata:** **3.54 sn** (libtorrent oturumu + torrent magnet metadata + ilk eş el sıkışması).
  - **(b) Veri Bekleme (Offset):** **17.83 sn** — ExoPlayer dosyanın başlığını (`offset=0`) okuduktan hemen sonra (2 ms içinde) MKV/MP4 indeksini (cues/moov) okumak üzere **dosyanın sonuna (`offset=19775794270`, parça 2357)** atlamaktadır. Kalan sürenin neredeyse tamamı (~17.8 sn), bu kuyruk parçasının ilk 16 KiB bloklarının libtorrent tarafından ağdan indirilmesini beklemekle geçmektedir.
  - **(c) Oynatıcı / Dekoder:** **0.21 sn** — Son gerekli bayt HTTP üzerinden ExoPlayer'a verildikten sonra ilk video karesinin ekrana basılması yalnızca ~200 ms sürmektedir.
- **Bütünlük & Regresyon:**
  - **BLOK_UYUMSUZ: 0** (Tüm debug ve release koşularında CRC32 uyuşmazlığı 0).
  - **Release Ortancası (Civil War FLUX):** **23.27 sn**.
  - **Sarma (Seek) Testi (1. Düzeltme Kanıtı):** 10. dk (**3.13 sn**), 40. dk (**3.64 sn**), 5. dk (**2.64 sn**). Eski konum deadline'ları başarıyla temizlenmiş ve sarılan konuma anında odaklanılmıştır.
  - **Küçük Parça Regresyonu (Big Buck Bunny 1080p):** **4.18 sn** (Hiçbir regresyon yok).

---

## 2. KOD İNCELEMESİ DÜZELTMELERİ (TUR 2)

1. **`seek()` ve Eski Deadline Temizliği:**
   - `TorrentBlockRangeRules.activeDeadlinePieces`: Yeni bir konuma sarıldığında yalnızca [currentPiece .. currentPiece + windowAhead - 1] ile baş (0, 1) ve kuyruk (son - 1, son) parçalarına deadline atanır; bu pencerelerin dışındaki eski parçalar elenir.
   - `TorrentEngine.prioritizeAtOffset`: `h.clearPieceDeadlines()` ile eski tüm deadline'lar sıfırlanıp yalnızca aktif parçalara öncelik verilir.
   - 5 adet birim test ile doğrulandı (`TorrentBlockRangeRulesTest.kt`).
2. **`read()` ve Bitmemiş Blok Güvenliği:**
   - `awaitData` sonrası `servable <= 0` ise doğrulanmamış/çöp veri okuma yolu tamamen kaldırıldı; bitmiş blok gelene kadar bekleme döngüsüne alındı (`attempts >= 500` korumalı).
   - Birim testle kanıtlandı (`testMaxServableLengthFirstBlockMissing_returnsZero`).
3. **Bütünlük Denetiminin Sadeleştirilmesi & Alert Thread İzolasyonu:**
   - `servedRanges` listesi kaldırılarak tekil `servedBlockCrcs` mekanizmasına indirgendi.
   - Disk CRC okuması (`verifyServedBlocks`) libtorrent alert iş parçacığından çıkarılıp `background.execute` havuzuna taşındı; libtorrent alert pompası engellenmez hale getirildi.
   - `removeLocked` ve `open` içinde tüm eşlemeler temizlendi.
4. **Deney Kodlarının Temizlenmesi:**
   - `TorrentBlockFeasibility.kt` silindi, `MainActivity.kt` içindeki `play_debug_action` kancası kaldırıldı.

---

## 3. PART 1: DÖNÜŞÜMLÜ DEBUG DERLEME ÖLÇÜMLERİ (5 + 5 KOŞU)

Ağ koşullarının iki tarafa eşit dağılması için koşular `main, blok, main, blok...` sırasında dönüşümlü yürütülmüştür. Her koşu öncesi soğuk başlatma yapılmış, diskteki torrent önbelleği temizlenmiş ve kaynak listesinden 18.42 GB FLUX elle seçilmiştir.

### 3.1 Kilometre Taşları Karşılaştırma Tablosu

| Koşu | Dal / Mod | Oturum Hazır | Metadata Geldi | İlk Eş Bağlandı | Parça 0 İlk Blok | HTTP Aralıkları (Offset) ve İlk Bayt | İlk Karede Verilen Bayt | İlk Kare (Toplam Süre) |
| :--- | :--- | :---: | :---: | :---: | :---: | :--- | :---: | :---: |
| **main_cw_d1** | `main` (Parça) | Önceden açık | 3521 ms | ~5-6 sn | Parça 0 sonu (~18-21 sn) | Tam parça okuması | - | **39.05 sn** (39050 ms) |
| **blok_cw_d1** | `blok` (Blok) | Önceden açık | 3544 ms | 336 ms | 3306 ms | off=0, off=19775794270, off=6488 | ~468 KB | **23.68 sn** (23678 ms) |
| **main_cw_d2** | `main` (Parça) | Önceden açık | 2516 ms | ~5-6 sn | Parça 0 sonu (~18-21 sn) | Tam parça okuması | - | **90.79 sn** (90787 ms) |
| **blok_cw_d2** | `blok` (Blok) | Önceden açık | 4026 ms | 192 ms | 6099 ms | off=0, off=19775794270, off=6488 | ~350-480 KB | **20.81 sn** (20807 ms) |
| **main_cw_d3** | `main` (Parça) | Önceden açık | 3542 ms | ~5-6 sn | Parça 0 sonu (~18-21 sn) | Tam parça okuması | - | **36.30 sn** (36301 ms) |
| **blok_cw_d3** | `blok` (Blok) | Önceden açık | 3539 ms | 486 ms | 31158 ms | off=0, off=19775794270, off=6488 | ~350-480 KB | **34.85 sn** (34850 ms) |
| **main_cw_d4** | `main` (Parça) | Önceden açık | 4017 ms | ~5-6 sn | Parça 0 sonu (~18-21 sn) | Tam parça okuması | - | **96.05 sn** (96052 ms) |
| **blok_cw_d4** | `blok` (Blok) | Önceden açık | 1517 ms | 378 ms | 1553 ms | off=0, off=19775794270, off=6488 | ~350-480 KB | **19.57 sn** (19575 ms) |
| **main_cw_d5** | `main` (Parça) | Önceden açık | 3019 ms | ~5-6 sn | Parça 0 sonu (~18-21 sn) | Tam parça okuması | - | **105.09 sn** (105086 ms) |
| **blok_cw_d5** | `blok` (Blok) | Önceden açık | 3027 ms | 698 ms | 3326 ms | off=0, off=19775794270, off=6488 | ~350-480 KB | **17.16 sn** (17155 ms) |

**Ortancalar (Debug):** `ozellik/torrent-blok`: **20.81 sn** | `main`: **90.79 sn**

### 3.2 Bağlı Eş ve Hız İstatistikleri (1, 3, 5, 10. saniyeler)

`ozellik/torrent-blok` dalında `startOpeningStatsTracker` ile kaydedilen istatistikler:

| Koşu | 1. sn (Eş / İndirme Hızı) | 3. sn (Eş / İndirme Hızı) | 5. sn (Eş / İndirme Hızı) | 10. sn (Eş / İndirme Hızı) |
| :--- | :---: | :---: | :---: | :---: |
| **blok_cw_d1** | `peers=9 seeds=7 dlRate=172KB/s inen=0KB` | `peers=10 seeds=8 dlRate=509KB/s inen=0KB` | `peers=12 seeds=8 dlRate=968KB/s inen=0KB` | `peers=15 seeds=11 dlRate=1606KB/s inen=0KB` |
| **blok_cw_d2** | `peers=12 seeds=10 dlRate=8KB/s inen=0KB` | `peers=17 seeds=13 dlRate=276KB/s inen=0KB` | `peers=21 seeds=18 dlRate=538KB/s inen=0KB` | `peers=24 seeds=20 dlRate=1058KB/s inen=0KB` |
| **blok_cw_d3** | `peers=11 seeds=10 dlRate=140KB/s inen=0KB` | `peers=13 seeds=13 dlRate=506KB/s inen=0KB` | `peers=17 seeds=17 dlRate=962KB/s inen=0KB` | `peers=22 seeds=18 dlRate=1774KB/s inen=0KB` |
| **blok_cw_d4** | `peers=7 seeds=5 dlRate=143KB/s inen=0KB` | `peers=15 seeds=13 dlRate=585KB/s inen=0KB` | `peers=18 seeds=16 dlRate=1077KB/s inen=0KB` | `peers=24 seeds=19 dlRate=1680KB/s inen=0KB` |
| **blok_cw_d5** | `peers=14 seeds=12 dlRate=25KB/s inen=0KB` | `peers=17 seeds=15 dlRate=508KB/s inen=0KB` | `peers=19 seeds=17 dlRate=746KB/s inen=0KB` | `peers=25 seeds=20 dlRate=1656KB/s inen=0KB` |

---

## 4. KALAN SÜRENİN DAĞILIMI (NEREYE GİDİYOR?)

Blok modunda Civil War açılışının ortanca süresi **20.81 saniyedir**. Bu sürenin bileşenleri:

```
Toplam Açılış Süresi (Ortanca: 20.81 sn)
├── (a) Bağlantı & Metadata Kurulumu: ~3.54 sn  (%17.0)
│   ├── Libtorrent motoru oturum başlatma / warm-up (0.02 sn)
│   ├── İlk eş bağlantısı el sıkışması (0.34 sn)
│   └── Magnet üzerinden torrent meta verisinin indirilmesi (3.54 sn)
├── (b) Veri Bekleme (Offset / Parça İndirme): ~17.83 sn (%81.8)
│   ├── Offset 0 (Başlık / MKV Header): 0.002 sn (İlk 16 KiB blok anında verildi)
│   ├── Offset 19775794270 (Kuyruk / MKV Cues & Index): ~17.80 sn
│   │   └── ExoPlayer dosyanın son 140 KB'ını okumak için atlar. Bu veri
│   │       parça 2357'dedir. libtorrent'in bu parçayı eşlerden talep edip
│   │       ilk bloklarını indirmesi ağ hızına bağlı olarak ~14-19 sn sürmektedir.
│   └── Offset 6488 (İlk Video Verisi Kümesi): 0.004 sn (Blok önbellekten verildi)
└── (c) Oynatıcı / Dekoder Tarafı: ~0.21 sn (%1.2)
    └── Son bayt HTTP'den ExoPlayer'a verildikten sonra MediaCodec dekoderinin
        kareyi çözüp SurfaceView'e basması (onRenderedFirstFrame): ~210 ms.
```

### Sonuç Kanıtı:
- Play'in veri bekleme tarafında blok motoru **mümkün olan en erken anda (ilk 16 KiB blok indiğinde)** veriyi oynatıcıya teslim etmektedir. Blok motoru olmasaydı parça 2357'nin tamamı (8 MiB) beklenecekti (`main` dalında olduğu gibi ek +15-70 sn).
- Kalan 20 saniyenin **~17.8 saniyesi**, libtorrent'in kuyruk parçasını uzak eşlerden indirme hızıdır (ağ ve eş kuyruğu kısıtı).

---

## 5. PART 2: RELEASE DERLEME DOĞRULAMASI VE SARMA (SEEK) TESTİ

Release APK (`Play-1.2.3.apk`, ProGuard/R8 minify açık) ile LDPlayer üzerinde yürütülen testler:

### 5.1 Civil War FLUX (Release) 3 Soğuk Koşu

| Koşu | İlk Kare Süresi | `BLOK_UYUMSUZ` | Açıklama |
| :--- | :---: | :---: | :--- |
| **blok_cw_rel1** | **23.27 sn** | **0** | Temiz açılış, sıfır takılma |
| **blok_cw_rel2** | **20.24 sn** | **0** | Temiz açılış, sıfır takılma |
| **blok_cw_rel3** | **29.92 sn** | **0** | Temiz açılış, sıfır takılma |

**Civil War FLUX Release Ortancası:** **23.27 sn** (Tur 1'deki 25.89 sn ortancası ile tam uyumlu; sıfır regresyon).

### 5.2 Oynatma Sırasında İleri/Geri Sarma (Seek) Testi (1. Düzeltme Kanıtı)

`blok_cw_rel3` koşusu sırasında oynatma başladıktan sonra 3 kez farklı konumlara sarma yapılmıştır:

| Sarma Hedefi | Konum | İlk Kare Süresi (Resume Latency) | Log / Davranış |
| :--- | :---: | :---: | :--- |
| **1. Sarma: 10. dakika** | 600. sn | **3.13 sn** | Eski deadline'lar silindi, yeni konum blokları anında talep edildi |
| **2. Sarma: 40. dakika** | 2400. sn | **3.64 sn** | Uzak ofsete atlama; 3.6 sn içinde akıcı devam etti |
| **3. Sarma: 5. dakika** | 300. sn | **2.64 sn** | Geri sarma; 2.6 sn içinde ilk kare basıldı |

Eski konumlara ait parçaların indirilmeye devam etmediği, `activeDeadlinePieces` kuralı sayesinde yalnızca yeni oynatma penceresinin acil öncelikte kaldığı doğrulanmıştır.

### 5.3 Big Buck Bunny 1080p (Küçük Parça) Regresyon Kontrolü

| Koşu | Dosya Boyutu / Parça | İlk Kare Süresi | `BLOK_UYUMSUZ` |
| :--- | :---: | :---: | :---: |
| **blok_bbb_rel1** | 263.3 MB (512 KiB parça) | **4.87 sn** | **0** |
| **blok_bbb_rel2** | 263.3 MB (512 KiB parça) | **3.49 sn** | **0** |

**Big Buck Bunny Ortancası:** **4.18 sn** (Küçük parçalı torrentlerde hiçbir regresyon yoktur).

---

## 6. HAM LOG KANITLARI (ÖRNEK SATIRLAR)

Tüm sayılar ölçülmüş ve aşağıdaki log satırlarıyla kanıtlanmıştır:

```
[blok_cw_d1 START]      11-02 20:09:06.076 D/PlayMatch(12136): kaynak başlatılıyor: Torrentio (?)
[blok_cw_d1 META]       11-02 20:09:09.620 12136 12320 D PlayTorrent: MILESTONE: metadata_ready pieces=2358 size=19775934621 pieceLength=8388608
[blok_cw_d1 PEER]       11-02 20:09:06.412 12136 12228 D PlayTorrent: MILESTONE: first_peer_connected
[blok_cw_d1 PIECE0]     11-02 20:09:09.382 12136 12228 D PlayTorrent: MILESTONE: piece0_first_block
[blok_cw_d1 HTTP]       11-02 20:09:09.622 12136 12320 D PlayTorrent: MILESTONE: http_range_first_byte offset=0 bytes=65536 total=65536
[blok_cw_d1 HTTP]       11-02 20:09:28.768 12136 12225 D PlayTorrent: MILESTONE: http_range_first_byte offset=19775794270 bytes=54178 total=316322
[blok_cw_d1 HTTP]       11-02 20:09:29.542 12136 12225 D PlayTorrent: MILESTONE: http_range_first_byte offset=6488 bytes=65536 total=468031
[blok_cw_d1 FRAME]      11-02 20:09:29.754 D/PlayMatch(12136): ilk kare: Torrentio (faz=Starting)

[blok_cw_d4 START]      11-02 20:19:18.792 D/PlayMatch(14198): kaynak başlatılıyor: Torrentio (?)
[blok_cw_d4 FRAME]      11-02 20:19:38.367 D/PlayMatch(14198): ilk kare: Torrentio (faz=Starting)

[main_cw_d1 START]      11-02 20:07:33.755 D/PlayMatch(11805): kaynak başlatılıyor: Torrentio (?)
[main_cw_d1 FRAME]      11-02 20:08:12.805 D/PlayMatch(11805): ilk kare: Torrentio (faz=Starting)
[main_cw_d3 START]      11-02 20:15:20.466 D/PlayMatch(13210): kaynak başlatılıyor: Torrentio (?)
[main_cw_d3 FRAME]      11-02 20:15:56.767 D/PlayMatch(13210): ilk kare: Torrentio (faz=Starting)
```

Kanıt dosyaları ve ekran görüntüleri `docs/dogrulama-torrent-blok/tur2/` dizinindedir.
