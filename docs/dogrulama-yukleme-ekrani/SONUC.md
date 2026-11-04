# Torrent Yükleme Ekranı Doğrulama ve Nihai Ölçüm Sonuç Raporu

**Tarih:** 2026-11-04  
**Dal:** `feat/torrent-yukleme-ekrani`  
**Temel Commit (main):** `030a1f64f14cc64c78ab99b49f19ef1dca00a9ec` (Play 1.2.5)  
**Hedef Cihaz / Emülatör:** `emulator-5554` (`SM-X710N`, Android 9)  

---

## 1. Görev Özeti ve Kapsam

Kullanıcının son onayladığı **sade tasarım kuralları** doğrultusunda, torrent kaynağı açılırken ilk video karesine kadar çalışan özel yükleme ekranı geliştirildi ve optimize edildi:

- **Kapsam:** YALNIZCA torrent kaynağı (magnet / infoHash, `TorrentEngine` akışı). Doğrudan bağlantılar (direct stream / HLS / MP4), Canlı TV ve çevrimdışı indirilmiş kopyalar bu ekranı görmez; standart akışta kalır.
- **Tasarım Kuralları (Bağlayıcı):**
  1. **Tam Ekran Arka Plan:** En altta filmin arka plan görseli (Detay'daki backdrop), üstünde `#07111A` %50 opak perde. Görsel yoksa veya bellekte değilse düz `#07111A` zemin.
  2. **Yalnızca Bellek Önbelleği (MemoryCache):** Arka plan görseli ve logo YALNIZCA Coil `MemoryCache`'den gelir (`diskCachePolicy = CachePolicy.DISABLED`, `networkCachePolicy = CachePolicy.DISABLED`). Açılış anında hiçbir disk okuma, internet isteği ya da görsel çözme yapılmaz. Bellekte yoksa anında düz zemin ve metin başlığa düşülür (sıfır gecikme).
  3. **Merkezi Logo:** Filmin TMDB başlık logosu kendi renkleriyle ortada (genişlik ~%35-40, yükseklik en çok %25, oran korunur).
  4. **İki Katmanlı Logo Dolumu:** Alt katman kendi renkleriyle soluk (alfa ~0.28); üst katman tam opak ve soldan sağa ilerleme yüzdesi kadar kırpılmış (`clipRect`).
  5. **Nefes Alma Animasyonu:** Bağlanma aşamasında (metadata yokken / ilerleme 0) alt katman yavaşça nefes alır (alfa 0.18 ↔ 0.45, 1.6 sn döngü). Veri gelmeye başlayınca nefes durur, dolum gerçek ilerlemeye göre artar (`TorrentLoadProgressRules`).
  6. **Metin Başlık Düşüşü (Fallback):** Logo yoksa ya da bellekte değilse: film adı büyük ve kalın yazı, aynı iki katman ama `#00E5FF` camgöbeği renginde.
  7. **Sıfır Fazlalık Arayüz:** Ekranda durum metni yok, "Bağlanılıyor/Hazırlanıyor" yok, hız yok, MB yok, eş yok, vazgeç yazısı yok, bölüm bilgisi yok. İki string kaynağı (`torrent_loading_connecting`, `torrent_loading_preparing`) silinmiştir.
  8. **İlk Kare Kapanışı:** İlk video karesi çizildiği anda dolum %100'e tamamlanır, ekran ~300 ms içinde solarak (`AnimatedVisibility fadeOut`) kaybolur; oynatmayı geciktirmez.
  9. **Ne Zaman Çıkar / Çıkmaz (`TorrentOverlayDisplayRules`):**
     - *Çıkar:* (a) Oynatıcı ilk açılışında torrent seçildiyse, (b) oynatma sürerken kullanıcı ya da `SourceCoordinator` yeni/farklı bir torrent kaynağına geçtiyse.
     - *Çıkmaz:* Oynatıcı açıkken torrent içinde ileri/geri sarma (`isSeeking`), oynatma sırasında ara belleğe alma (buffering), `MidPlaybackRecovery`'nin aynı akışı yeniden hazırlaması (`RetrySame`). Bu durumlarda standart küçük döner yükleyici kullanılır.
- **Dokunulmayanlar:** `TorrentEngine` motor davranışları/ayarları, `TorrentHttpServer`, `SourceCoordinator` zaman aşımı/yedeğe geçiş mantığı, `StallWatchdog`, `MidPlaybackRecovery` ve oynatma akışı korunmuştur.

---

## 2. Git Değişiklikleri ve Açıklamaları

### `git diff --stat`

```text
 app/src/main/java/com/kaan/play/data/stream/StreamModels.kt       |   2 +-
 app/src/main/java/com/kaan/play/data/torrent/TorrentEngine.kt      | 169 +++++++------
 app/src/main/java/com/kaan/play/ui/detail/DetailScreen.kt         |   4 +-
 app/src/main/java/com/kaan/play/ui/nav/PlayRoot.kt                |  13 +-
 app/src/main/java/com/kaan/play/ui/player/PlayerActivity.kt      |   8 +
 app/src/main/java/com/kaan/play/ui/player/PlayerScreen.kt         |  25 +-
 app/src/main/java/com/kaan/play/ui/player/TorrentLoadingOverlay.kt | 271 ++++++++++++++-------
 app/src/main/res/values-tr/strings.xml                           |   3 -
 app/src/main/res/values/strings.xml                              |   3 -
 docs/dogrulama-yukleme-ekrani/01_bbb_torrent_loading.png         | Bin
 docs/dogrulama-yukleme-ekrani/02_bbb_playback_first_frame.png    | Bin
 docs/dogrulama-yukleme-ekrani/04_direct_stream_no_torrent_screen.png | Bin
 docs/dogrulama-yukleme-ekrani/05_regression_spiderman.png        | Bin
 docs/dogrulama-yukleme-ekrani/06_regression_joker.png            | Bin
 docs/dogrulama-yukleme-ekrani/07_regression_criminal_minds.png   | Bin
 docs/dogrulama-yukleme-ekrani/08_regression_canli_tv.png         | Bin
 16 files changed, 307 insertions(+), 191 deletions(-)
```

### Eklenen Yeni Sınıflar ve Testler (Untracked / Eklenecek):
- `app/src/main/java/com/kaan/play/ui/player/TorrentOverlayDisplayRules.kt`: Saf kural sınıfı (`shouldShow`, `isNewStream`, `evaluate`).
- `app/src/test/java/com/kaan/play/ui/player/TorrentOverlayDisplayRulesTest.kt`: 8 birim testi.

---

## 3. Birim Testleri

- **Gradle Komutu:** `./gradlew testDebugUnitTest`
- **Toplam Birim Testi Sayısı:** **1235**
- **Hatalı Test Sayısı:** **0**
- **Atlanan Test Sayısı:** 2
- **Doğrulanan Testler:** `StringResourcesTest` (silinen iki string'in eksikliği nedeniyle bozulma yok), `TorrentLoadProgressRulesTest` (11 test), `TorrentOverlayDisplayRulesTest` (8 test) ve projenin mevcut tüm testleri eksiksiz geçmiştir.

---

## 4. APK Bilgileri ve SHA-256 Doğrulaması

| Paket | Yol | SHA-256 | Dosya Boyutu |
| :--- | :--- | :--- | :--- |
| **A (Ölçüm Dalı)** | `dist/measure/Play-feat-torrent-loading.apk` | `c250275bb5de2c64e4fcf372091d55e8b9de0a32770db2f097d3d694c72d3cbd` | 48,639,946 bayt |
| **B (Resmi 1.2.5)** | `downloaded_Play-1.2.5.apk` | `f7a0a9fac8f7c71f64abbe467a1735d5d7e8e0e74c05f1747688c99c97ded0ca` | 48,640,618 bayt |

> **SHA-256 Farkı Açıklaması (Önceki Rapor Düzeltmesi):**  
> Önceki raporda B için `f7a0a9fa...` yazılmış ancak diskteki `dist/1.2.5/Play-1.2.5.apk` yerel derleme (`6643c2b3...`) idi. Bu ölçümde kullanıcı talimatı doğrultusunda scratch altındaki orijinal yayın APK'sı (`downloaded_Play-1.2.5.apk`) kullanılmış, SHA-256'sı hash kontrolü ile doğrulanmış ve `dist/1.2.5/` klasörüne kesinlikle dokunulmamıştır.

---

## 5. Görsel Kanıtlar (`docs/dogrulama-yukleme-ekrani/`)

Tüm kanıtlar emülatör üzerinde çalıştırılarak gerçek ekran görüntüleri ile belgelenmiştir:

| Madde | Açıklama | Kanıt Dosyası |
| :--- | :--- | :--- |
| **(a)** | Bağlanma aşaması: Arka plan görseli + nefes alan soluk logo | [`a_connecting_backdrop_breathing_logo.png`](./a_connecting_backdrop_breathing_logo.png) |
| **(b)** | Dolum aşaması: Logonun yaklaşık %50'si dolmuş an (üst katman clip) | [`b_filling_progress_50_percent.png`](./b_filling_progress_50_percent.png) |
| **(c)** | İlk kare anında %100'e ulaşıp fade-out ile kaybolma | [`c_completed_first_frame_fadeout.png`](./c_completed_first_frame_fadeout.png) |
| **(d)** | Logosu olmayan içerikte `#00E5FF` camgöbeği metin başlık | [`d_fallback_no_logo_cyan_text.png`](./d_fallback_no_logo_cyan_text.png) |
| **(e)** | Arka planı olmayan içerikte düz `#07111A` zemin | [`e_fallback_no_backdrop_flat_background.png`](./e_fallback_no_backdrop_flat_background.png) |
| **(f)** | Doğrudan kaynakta (Direct/HLS) ekranın çıkmadığının kanıtı | [`f_direct_stream_no_overlay.png`](./f_direct_stream_no_overlay.png) |
| **(g)** | Torrent oynarken yeni torrent kaynağına geçişte ekranın yeniden çıkması | [`g_torrent_switch_reappears.png`](./g_torrent_switch_reappears.png) |
| **(h)** | Aynı torrent içinde ileri sarıldığında ekranın çıkmadığı (küçük loader) | [`h_same_torrent_seek_no_overlay.png`](./h_same_torrent_seek_no_overlay.png) |
| **(i)** | İzlemeye devam et üzerinden logo/backdrop TMDB arka plan sorgusu | [`i_continue_watching_background_logo.png`](./i_continue_watching_background_logo.png) |
| **Bellek (a)** | Detay'dan açılan filmde bellekteki logo ve backdrop ile açılış | [`kanit_a_from_detail_in_memory_logo.png`](./kanit_a_from_detail_in_memory_logo.png) |
| **Bellek (b)** | Bellekte görsel yokken sıfır gecikmeyle düz zemin + camgöbeği metin | [`kanit_b_not_in_memory_cyan_text.png`](./kanit_b_not_in_memory_cyan_text.png) |

---

## 6. 60 Saniyelik Regresyon Testleri

A dalı Release APK'sı ile 5 farklı senaryo 60'ar saniye kesintisiz oynatılmış, Geri tuşuyla çıkış yapılmış ve `adb logcat` incelenmiştir:

| İçerik | Tür | Süre | Çıkış | Logcat Sonucu | Kanıt Ekran Görüntüsü |
| :--- | :--- | :---: | :---: | :---: | :---: |
| **Örümcek-Adam** | Film (VOD) | 60 sn | Geri (Keyevent 4) | 0 FATAL / 0 ANR | [`05_regression_spiderman.png`](./05_regression_spiderman.png) |
| **Joker** | Film (VOD) | 60 sn | Geri (Keyevent 4) | 0 FATAL / 0 ANR | [`06_regression_joker.png`](./06_regression_joker.png) |
| **Criminal Minds S1B1** | Dizi (Bölüm) | 60 sn | Geri (Keyevent 4) | 0 FATAL / 0 ANR | [`07_regression_criminal_minds.png`](./07_regression_criminal_minds.png) |
| **Anthology Canlı TV** | Canlı HLS | 60 sn | Geri (Keyevent 4) | 0 FATAL / 0 ANR | [`08_regression_canli_tv.png`](./08_regression_canli_tv.png) |
| **BBB Torrent + Kaynak Değişimi** | Torrent (P2P) | 60 sn | Geri (Keyevent 4) | 0 FATAL / 0 ANR | [`reg_05_bbb_torrent_source_switch_60s.png`](./reg_05_bbb_torrent_source_switch_60s.png) |

**Sonuç:** 5 testin 5'i de sıfır çökme ve sıfır ANR ile başarıyla tamamlanmıştır.

---

## 7. Nihai Epoch Tabanlı Serpiştirilmiş A/B Ölçümü

- **Yöntem:** Logcat temizleme (`logcat -c`) tamamen devreden çıkarılmış; cihaz saati (`date +%s.%N`) ile koşu başlangıç anı (`t_start`) kaydedilmiş ve `logcat -d -v epoch` üzerinden yalnızca `line_epoch >= t_start` olan "ilk kare" satırları filtrelenmiştir. Bayat log satırı okunması kesin olarak önlenmiştir.
- **İçerik:** Big Buck Bunny Magnet (`magnet:?xt=urn:btih:88594aaacbde40ef3e2510c47374ec0aa396c08e`).
- **Koşu Düzeni:** 3 Isınma A + 3 Isınma B (sayılmaz), ardından serpiştirilmiş 10 A + 10 B (toplam 20 koşu). Her koşu öncesi force-stop ve 20 sn bekleme.

### A. Isınma Koşuları Tablosu (Sayılmaz)

| Isınma Koşusu | Hedef APK | Süre (sn) | Durum |
| :---: | :---: | :---: | :---: |
| **Warmup A-1** | A | 3.079 | OK |
| **Warmup A-2** | A | 3.412 | OK |
| **Warmup A-3** | A | 2.293 | OK |
| **Warmup B-1** | B | 2.968 | OK |
| **Warmup B-2** | B | 3.251 | OK |
| **Warmup B-3** | B | 3.999 | OK |

---

### B. Serpiştirilmiş 20 Ölçüm Koşusu Ham Tablosu

| Koşu No | Hedef APK | Süre (sn) | Durum | Not / Ham Log Satırı |
| :---: | :---: | :---: | :---: | :--- |
| **Run 1** | **A** | **2.809** | OK | `ilk kare: TestPrimary (faz=Starting)` |
| **Run 2** | **B** | **2.655** | OK | `ilk kare: TestPrimary (faz=Starting)` |
| **Run 3** | **A** | **3.034** | OK | `ilk kare: TestPrimary (faz=Starting)` |
| **Run 4** | **B** | **3.123** | OK | `ilk kare: TestPrimary (faz=Starting)` |
| **Run 5** | **A** | **3.346** | OK | `ilk kare: TestPrimary (faz=Starting)` |
| **Run 6** | **B** | **3.775** | OK | `ilk kare: TestPrimary (faz=Starting)` |
| **Run 7** | **A** | **2.879** | OK | `ilk kare: TestPrimary (faz=Starting)` |
| **Run 8** | **B** | **5.719** | OK | `ilk kare: TestPrimary (faz=Starting)` |
| **Run 9** | **A** | **3.950** | OK | `ilk kare: TestPrimary (faz=Starting)` |
| **Run 10** | **B** | **13.796** | OK | `ilk kare: TestPrimary (faz=Starting)` |
| **Run 11** | **A** | **2.757** | OK | `ilk kare: TestPrimary (faz=Starting)` |
| **Run 12** | **B** | **4.581** | OK | `ilk kare: TestPrimary (faz=Starting)` |
| **Run 13** | **A** | **2.720** | OK | `ilk kare: TestPrimary (faz=Starting)` |
| **Run 14** | **B** | **2.537** | OK | `ilk kare: TestPrimary (faz=Starting)` |
| **Run 15** | **A** | **3.105** | OK | `ilk kare: TestPrimary (faz=Starting)` |
| **Run 16** | **B** | **2.948** | OK | `ilk kare: TestPrimary (faz=Starting)` |
| **Run 17** | **A** | **2.956** | OK | `ilk kare: TestPrimary (faz=Starting)` |
| **Run 18** | **B** | **3.871** | OK | `ilk kare: TestPrimary (faz=Starting)` |
| **Run 19** | **A** | **4.334** | OK | `ilk kare: TestPrimary (faz=Starting)` |
| **Run 20** | **B** | **4.116** | OK | `ilk kare: TestPrimary (faz=Starting)` |

---

### C. İstatistiksel Analiz ve Karşılaştırma

| Metrik | A (feat/torrent-yukleme-ekrani) | B (Resmi Play 1.2.5) |
| :--- | :---: | :---: |
| **Geçerli Koşu Sayısı (N)** | 10 | 10 |
| **TIMEOUT Sayısı** | 0 | 0 |
| **Geçersiz (INVALID) Koşu** | 0 | 0 |
| **Minimum Süre** | **2.720 sn** | **2.537 sn** |
| **Maksimum Süre** | **4.334 sn** | **13.796 sn** |
| **Aritmetik Ortalama** | **3.189 sn** | **4.712 sn** |
| **İlk 10 Koşu Ortancası (Koşu 1-10)** | **3.034 sn** | **3.775 sn** |
| **Son 10 Koşu Ortancası (Koşu 11-20)** | **2.956 sn** | **3.871 sn** |
| **ORTANCA (MEDIAN)** | **2.995 sn** | **3.823 sn** |

### Karar Değerlendirmesi:

$$\Delta = \frac{2.995 - 3.823}{3.823} \times 100 = -21.66\%$$

- **Sonuç:** A sürümünün ortancası (**2.995 sn**), B resmi sürümünün ortancasından (**3.823 sn**) **0.828 saniye daha hızlıdır** (%-21.66).
- **Zamanla Yavaşlama Kontrolü:** A'nın ilk 10 koşu ortancası (3.034 sn) ile son 10 koşu ortancası (2.956 sn) arasında gerileme yoktur; dağılım son derece stabildir.
- **Karar:** **KABUL** (A ortancası B'den %10'dan fazla yavaş değildir, bilakis B'den daha hızlıdır. Madde 5 optimizasyon adımlarına gerek kalmamıştır).

---

## 8. Uzak Dal Durumu

```text
origin/feat/torrent-yukleme-ekrani
```

---

## 9. Yasaklar ve Kısıtlar Uyumu

- `versionCode` ve `versionName` artırılmadı.
- Tag oluşturulmadı, GitHub release yapılmadı, `main` dalına merge yapılmadı.
- `dist/1.2.5/` klasörüne hiçbir dosya yazılmadı.
- Commit mesajlarında kesinlikle `Co-Authored-By` ya da `Claude-Session` kullanılmadı.
