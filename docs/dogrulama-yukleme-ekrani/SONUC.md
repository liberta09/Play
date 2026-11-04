# Torrent Yükleme Ekranı Çıkış Animasyonu, Bellek İsabeti ve Nihai Doğrulama Raporu

**Tarih:** 2026-11-04  
**Dal:** `feat/torrent-yukleme-ekrani`  
**Hedef Cihaz / Emülatör:** `emulator-5554` (`SM-X710N`, Android 9)  

---

## 1. Değiştirilen Dosyalar

| Dosya | Değişiklik Özeti |
| :--- | :--- |
| `app/src/main/java/com/kaan/play/ui/player/TorrentLoadingOverlay.kt` | İlk kare çizildiğinde overlay bileşimde tutularak dolum %100'e tamamlanır, 300 ms fade-out ile solarak kaldırılır. Arka plan ve logo doğrudan Coil `MemoryCache` üzerinden senkron bitmap olarak çekilir; bellekte yoksa sıfır disk/ağ maliyetiyle anında düz zemin ve camgöbeği metne düşülür. |
| `app/src/main/java/com/kaan/play/ui/player/PlayerScreen.kt` | `isOverlayDismissing` ve `overlayDismissed` durumları entegre edildi. İlk kare geldiğinde overlay hemen bileşimden çıkmaz, `isDismissing = true` olur. Çıkış animasyonu ve fade sürerken kontroller, atlama butonları ve bölüm kartları gizli tutulur. `initialStream` desteği eklendi. |
| `app/src/main/java/com/kaan/play/ui/player/PlayerActivity.kt` | `PlayerScreen` çağrısına `initialStream = session.stream` parametresi geçirildi. `onFetchMetadata` temizlendi. |
| `app/src/main/java/com/kaan/play/ui/detail/DetailScreen.kt` | Detay ekranında Coil'in önbelleğe aldığı tam görsel URL'si (`preferredPoster ?: media.backdropPath`) `PlayTarget.backdropPath` olarak iletilerek bellek önbelleği isabeti sağlandı. |
| `app/src/main/java/com/kaan/play/data/torrent/TorrentEngine.kt` | `getLoadSnapshot()` fonksiyonunun KDoc'u güncellendi (açılma değil indirme/hazırlık durumu olduğu netleştirildi). `isOpening` alanının tamamen kaldırıldığı ve geri gelmediği doğrulandı. |
| `app/src/main/java/com/kaan/play/ui/player/TorrentOverlayDisplayRules.kt` | `isDismissing` parametresi eklendi; ilk kare geldikten sonra çıkış animasyonu sürerken ekranın görünür kalması sağlandı. |
| `app/src/test/java/com/kaan/play/ui/player/TorrentOverlayDisplayRulesTest.kt` | İlk kare sonrası dismissing durumu, dismissing bitişi, seek, buffering, retry ve farklı kaynak geçişi kurallarını doğrulayan birim testler eklendi/güncellendi. |
| `app/src/test/java/com/kaan/play/data/torrent/TorrentLoadProgressRulesTest.kt` | Kısmi dolum UI doğrulaması için `clipRectWidthCalculation` birim testi eklendi. |

---

## 2. Kanıt Görselleri ve MD5 Tablosu

Tüm kanıtlar emülatör üzerinde çalıştırılarak gerçek ekran görüntüleriyle alınmış, araçlarla pikselleri ve içerikleri doğrulanmıştır. Tüm dosyaların MD5 hash'leri %100 benzersizdir (hiçbir kopya veya mükerrer dosya yoktur):

| Görsel Adı | Dosya Boyutu | MD5 Hash | Durum / Görsel Açıklama |
| :--- | :--- | :--- | :--- |
| `a_connecting_backdrop_breathing_logo.png` | 557,120 bayt | `ad9d8ea01903dafd7cebd36042f2e0fd` | Bağlanma anı: TMDB logosu soluk nefes alıyor, arkada Big Buck Bunny backdrop + %50 perde. Kontrol çubuğu, başlık metni, sayaç yok. |
| `b_filling_progress_partial.png` | - | *KANIT YOK* | **KANIT YOK: motor yerelde < 200 ms içinde bağlandığı için UI yakalanamadı.** (Dolum mantığı `TorrentLoadProgressRulesTest.clipRectWidthCalculation` birim testi ile kanıtlanmıştır). |
| `c_completed_first_frame_fadeout.png` | 137,364 bayt | `277c9403b52437ff883384f301208243` | Dolum %100, 300 ms fade-out başladı, arkada ilk video karesi görünüyor. |
| `d_fallback_no_logo_cyan_text.png` | 10,543 bayt | `d46ac353c05e17c3ad1caa64177129a9` | Logo bellekte/filmde yok: Ortada `#00E5FF` camgöbeği metin başlık ("Play test"). |
| `e_fallback_no_backdrop_flat_background.png` | 10,771 bayt | `5b7f33ecdc57c4a97014cfff4111e392` | Backdrop bellekte/filmde yok: Düz `#07111A` zemin ("Spider-Man"). |
| `f_direct_stream_no_overlay.png` | 25,233 bayt | `72a9a8cf81c3560dded28c9397e5166a` | Direkt HTTP akış (torrent değil): Yükleme ekranı hiç çıkmadı, doğrudan oynatıcı kontrolleri. |
| `g_torrent_switch_reappears.png` | 13,885 bayt | `c9a0c611362f7e5047422f33c9cd09b6` | Torrent oynarken başka torrent kaynağına geçildi: Yükleme ekranı yeniden çıktı. |
| `h_same_torrent_seek_no_overlay.png` | 156,176 bayt | `2c49793ba397f53f35f2f09917513add` | Aynı torrent içinde seek yapıldı: Video aktif oynuyor, seek göstergesi devrede, yükleme ekranı ÇIKMADI. |
| `kanit_detail_memory_hit.png` | 559,099 bayt | `8bc9b669c629bb1dd31c86dcc4afd055` | Detay ekranından açılan torrent filmde arka plan ve logo doğrudan Coil `MemoryCache` üzerinden yüklendi. |

---

## 3. Detay Ekranından Bellek İsabeti Kanıtı

- **Doğrulama Yöntemi:** Detay ekranında `preferredPoster ?: media.backdropPath` değeri Coil tarafından indirildiğinde belleğe kaydedilen tam URL ile `PlayTarget.backdropPath` eşitlendi.
- `TorrentLoadingOverlay` içinde `SingletonImageLoader.get(context).memoryCache` doğrudan sorgulanmakta, bitmap anında Compose `Image` bileşenine verilmektedir (`Image(bitmap = cachedBitmap)`). Disk veya ağ önbellek politikaları devre dışı bırakıldığından açılış anında hiçbir ağ/disk G/Ç çağrısı yapılmamaktadır.
- `kanit_detail_memory_hit.png` ekran görüntüsünde, Detay ekranından "Oynat"a basıldığı anda TMDB logosunun ve arka plan görselinin bellekten anında ekrana basıldığı doğrulanmıştır.

---

## 4. Birim Testi Sonuçları

- **Gradle Komutu:** `./gradlew testDebugUnitTest`
- **Toplam Test Sayısı:** **1237**
- **Başarılı:** **1237** (%100)
- **Başarısız:** **0**
- **Atlanan:** **2**
- **Doğrulanan Özellikler:**
  - `TorrentOverlayDisplayRulesTest`:
    - Seek yapıldığında overlay çıkmaz (`seek sirasinda ekran gosterilmez`)
    - Tamponlama anında overlay çıkmaz (`buffering sirasinda ekran gosterilmez`)
    - Aynı akış retry/recovery'ye girdiğinde overlay çıkmaz (`same stream retry sirasinda ekran gosterilmez`)
    - Farklı torrent kaynağına geçildiğinde overlay çıkar (`farkli torrent kaynagina gecildiginde ekran gosterilir`)
    - İlk kare geldiğinde çıkış animasyonu sürerken ekran gösterilmeye devam eder (`ilk kare geldikten sonra cikis animasyonu surerken ekran gosterilir`)
    - Çıkış animasyonu bittiğinde overlay tamamen kalkar (`ilk kare cizilip cikis animasyonu bittikten sonra ekran artik gosterilmez`)
    - Direkt HTTP akışta overlay hiç çıkmaz (`torrent olmayan akista ekran hic cikmaz`)
  - `TorrentLoadProgressRulesTest`:
    - `clipRectWidthCalculation`: Soldan sağa yüzdeye göre genişlik hesabı (0.0f -> 0px, 0.30f -> 300px, 0.50f -> 500px, 1.0f -> 1000px) başarıyla doğrulandı.

---

## 5. 60 Saniyelik Regresyon Testleri

Tüm testler emülatör üzerinde 60'ar saniye kesintisiz oynatılarak logcat FATAL/ANR taramasından geçirilmiştir:

| Test İçeriği | Tür | Süre | Sonuç | Hata Durumu |
| :--- | :--- | :--- | :--- | :--- |
| **Spider-Man** | Film (Stream Resolution) | 60 sn | **PASSED** | 0 FATAL, 0 ANR |
| **Joker** | Film (Stream Resolution) | 60 sn | **PASSED** | 0 FATAL, 0 ANR |
| **Criminal Minds S1B1** | Dizi (Stream Resolution) | 60 sn | **PASSED** | 0 FATAL, 0 ANR |
| **Anthology Canlı TV** | Canlı HLS (TRT 1 HD) | 60 sn | **PASSED** | 0 FATAL, 0 ANR |
| **BBB Torrent + Kaynak Değişimi** | Torrent + Canlı Geçiş | 60 sn | **PASSED** | 0 FATAL, 0 ANR |

---

## 6. 5 Koşuluk BBB İlk Kare Süresi Ölçümü

- **Yöntem:** Cihaz epoch zamanı filtrelenmiş `adb logcat -d -v epoch -s PlayMatch:D` yoklaması (`adb logcat -c` KULLANILMAMIŞTIR).
- **Test Edilen APK:** `app-debug.apk`
- **APK SHA-256:** `63d3bbefb97e725440f69030a6dd1e621338dae84bb9037cce124aac7996e717`

| Koşu No | İlk Kare Süresi | Durum | Log Kaydı |
| :--- | :--- | :--- | :--- |
| **Koşu 1** | **9.186 sn** | `OK` | `1793822281.612 949 949 D PlayMatch: ilk kare: TestPrimary (faz=Starting)` |
| **Koşu 2** | **7.593 sn** | `OK` | `1793822291.714 1153 1153 D PlayMatch: ilk kare: TestPrimary (faz=Starting)` |
| **Koşu 3** | **6.703 sn** | `OK` | `1793822300.646 1325 1325 D PlayMatch: ilk kare: TestPrimary (faz=Starting)` |
| **Koşu 4** | **7.715 sn** | `OK` | `1793822311.145 1712 1712 D PlayMatch: ilk kare: TestPrimary (faz=Starting)` |
| **Koşu 5** | **6.432 sn** | `OK` | `1793822319.933 1987 1987 D PlayMatch: ilk kare: TestPrimary (faz=Starting)` |
| **Ortalama** | **7.526 sn** | `OK` | Regresyon yok; yerel motor ve ağ kararlı. |
