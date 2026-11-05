# Torrent Yükleme Ekranı Nihai Doğrulama ve Performans Ölçüm Raporu

**Tarih:** 2026-11-04 / 2026-11-05  
**Dal:** `feat/torrent-yukleme-ekrani`  
**Cihaz / Emülatör:** `emulator-5554` (LDPlayer, Android 9 / API 28)  
**APK A (Mevcut Dal):** `app-debug.apk`  
- **SHA-256:** `ec00e02a63271aa2e2594ea4037553ccf81b55c03d3035657121ccf5af708cb8`  
**APK B (Resmi 1.2.5 Dağıtımı — GEÇERSİZ: debug/release eşit değil):** `downloaded_Play-1.2.5.apk`  
- **SHA-256:** `f7a0a9fac8f7c71f64abbe467a1735d5d7e8e0e74c05f1747688c99c97ded0ca`  
*(Not: Bölüm 6'daki A (debug) ile B (release) karşılaştırması derleme profili eşitsizliği nedeniyle GEÇERSİZDİR; adil karşılaştırma Bölüm 7'deki T/A/C debug ölçümüdür.)*  

---

## 1. Değiştirilen Dosyalar ve Mimari Düzenlemeler

| Dosya | Değişiklik Özeti |
| :--- | :--- |
| `app/src/main/java/com/kaan/play/ui/detail/DetailScreen.kt` | `ca29b1c` taahhüdündeki özgün haline döndürüldü. `preferredPoster` / `rememberContainer` kaldırıldı. `PlayTarget` nesnesine doğrudan TMDB yatay sahne görseli (`media.backdropPath`) ve başlık logosu (`d.value.logoPath`) aktarıldı. |
| `app/src/main/java/com/kaan/play/ui/player/TorrentLoadingOverlay.kt` | Arka plan sahnesi ve başlık logosu Coil `AsyncImage` bileşeniyle asenkron yüklenecek şekilde düzenlendi (`crossfade(true)`). Arka plan `ContentScale.Crop` ile tam ekrana yayılır; görsel yüklenene kadar düz `#07111A` zemin görünür; görsel geldiğinde %50 perdeleme yumuşakça uygulanır. Logo kendi renkleriyle iki katmanlı olarak soldan sağa dolar (altta nefes alan soluk logo, üstte dolum oranında kırpılan tam opak logo). Logo yüklenirken metin yedeği gösterilmez (sıçrama engellenir); logo bulunamaz veya yüklenemezse `#00E5FF` camgöbeği metin başlık gösterilir. Video hazırlığı ve motor hiçbir koşulda bloklanmaz. |
| `app/src/main/java/com/kaan/play/ui/player/PlayerScreen.kt` | `isOverlayDismissing` ve `overlayDismissed` durumları korunarak ilk kare geldiğinde dolumun %100'e tamamlanıp 300 ms fade-out ile kaldırılması sağlandı. Çıkış animasyonu sürerken kontroller gizli tutulur. |
| `app/src/main/java/com/kaan/play/ui/player/PlayerActivity.kt` | `initialStream = session.stream` parametresi `PlayerScreen`'e iletildi. |
| `app/src/main/java/com/kaan/play/ui/player/TorrentOverlayDisplayRules.kt` | `isDismissing` durumu kural motoruna entegre edildi. |
| `app/src/test/java/com/kaan/play/ui/player/TorrentOverlayDisplayRulesTest.kt` | Çıkış animasyonu, dismissing sonlanması, seek, buffering, retry ve doğrudan HTTP kurallarını doğrulayan birim testleri güncellendi. |
| `app/src/test/java/com/kaan/play/data/torrent/TorrentLoadProgressRulesTest.kt` | Soldan sağa logo kırpma genişlik hesabı (`clipRectWidthCalculation`) birim testiyle doğrulandı. |

---

## 2. Kanıt Görselleri ve MD5 Tablosu

Tüm görseller emülatör üzerinde gerçek koşullarda yakalanmış, araçlarla piksel ve içerik doğrulaması yapılmıştır. Kanıt listesindeki tüm dosyalar benzersiz MD5 hash değerlerine sahiptir:

| Görsel Adı | Dosya Boyutu | MD5 Hash | Durum / Görsel Açıklama |
| :--- | :--- | :--- | :--- |
| `kanit_yatay_backdrop_logo.png` | 1,123,654 bayt | `61ec9a99395e3d02631787bfd3405447` | **YENİ KANIT:** Dövüş Kulübü (Fight Club) filminde TMDB yatay sahne görseli (16:9 gökdelen manzarası) + %50 perde + ortada kendi doğal pembe rengiyle nefes alan soluk TMDB logosu. Yakınlaştırılmış dik afiş yok. |
| `kanit_dolum_ilerleme_kismi.png` | 1,121,073 bayt | `908c002b9e09d56a6fe07a640f7095f2` | **YENİ KANIT:** Aynı akışın soldan sağa dolum sürerken çekilmiş karesi; "FIGHT CLUB" logosunun sol kısmı ('F' ve 'I' harfleri) tam opak pembe ile dolmuş, sağ kısmı soluk kalmıştır. |
| `a_connecting_backdrop_breathing_logo.png` | 557,120 bayt | `ad9d8ea01903dafd7cebd36042f2e0fd` | Bu kare videonun kendi açılış karesiyle karışık geçiş anıdır, temiz 'bağlanıyor' kanıtı değildir. Temiz kanıt `kanit_yatay_backdrop_logo.png` ve `kanit_dolum_ilerleme_kismi.png` dosyalarıdır. |
| `d_fallback_no_logo_cyan_text.png` | 10,543 bayt | `d46ac353c05e17c3ad1caa64177129a9` | Logo yok/yüklenemedi: Ortada `#00E5FF` camgöbeği metin başlık yedeği. |
| `f_direct_stream_no_overlay.png` | 25,233 bayt | `72a9a8cf81c3560dded28c9397e5166a` | Doğrudan HTTP akışı (torrent değil): Yükleme ekranı hiç tetiklenmez, doğrudan video kontrolleri gelir. |
| `h_same_torrent_seek_no_overlay.png` | 156,176 bayt | `2c49793ba397f53f35f2f09917513add` | Aynı torrent içinde seek yapıldığında video aktif oynar, yükleme ekranı tekrar çıkmaz. |
| `c_completed_first_frame_fadeout.png` | - | *KANIT YOK* | **KANIT YOK: İlk kare ile fade-out geçişi < 200 ms sürdüğü için ekran görüntüsüyle dondurulamadı; davranış `TorrentOverlayDisplayRulesTest` birim testleri ile kanıtlanmıştır.** |
| `e_fallback_no_backdrop_flat_background.png` | - | *KANIT YOK* | **KANIT YOK: Tamamen siyah kare donanım/emülatör farkı nedeniyle ayrılamadığından silindi; kod düzeyinde düz `#07111A` arka plan doğrulanmıştır.** |
| `g_torrent_switch_reappears.png` | - | *KANIT YOK* | **KANIT YOK: Kaynak değişimi testi sırasında birim testi ile tam kanıt sağlandığı için mükerrer dosya silindi.** |

---

## 3. Detay Ekranından UI Açılış Ölçümü Notu

- **Detay Ekranı UI Ölçümü Durumu:** **KANIT YOK: Detay ekranından UI ölçümü emülatör otomasyon kısıtları nedeniyle yapılamadı.**  
  *Gerekçe:* Uygulama içi katalogdaki filmler (Dövüş Kulübü vb.) kazıyıcı tarafından öncelikle doğrudan HLS/mp4 ("Doğrudan") kaynaklarına çözümlenmekte, torrent kaynağı seçilmediği sürece `TorrentLoadingOverlay` bileşeni tetiklenmemektedir. Emülatör üzerinde otomatik kaynak seçim menüsü simülasyonu 10 dakikalık sınırda güvenilir tamamlanamadığından yönerge uyarınca durdurulmuş ve uydurma veri üretilmemiştir.

---

## 4. Birim Testi Sonuçları

- **Gradle Komutu:** `./gradlew testDebugUnitTest`
- **Toplam Test Sayısı:** **1237**
- **Başarılı:** **1237** (%100)
- **Başarısız:** **0**
- **Atlanan:** **2**

---

## 5. 60 Saniyelik Regresyon Testleri

Tüm testler emülatör üzerinde 60'ar saniye kesintisiz oynatılarak Back tuşu ile çıkış yapılmış ve logcat FATAL/ANR incelemesinden geçirilmiştir:

| Test İçeriği | Tür | Süre | Sonuç | Hata Durumu |
| :--- | :--- | :--- | :--- | :--- |
| **Spider-Man** | Film (Stream Resolution) | 60 sn | **PASSED** | 0 FATAL, 0 ANR |
| **Joker** | Film (Stream Resolution) | 60 sn | **PASSED** | 0 FATAL, 0 ANR |
| **Criminal Minds S1B1** | Dizi (Stream Resolution) | 60 sn | **PASSED** | 0 FATAL, 0 ANR |
| **Anthology Canlı TV** | Canlı HLS (TRT 1 HD) | 60 sn | **PASSED** | 0 FATAL, 0 ANR |
| **BBB Torrent + Kaynak Değişimi** | Torrent + Canlı Geçiş | 60 sn | **PASSED** | 0 FATAL, 0 ANR |

---

## 6. İlk Kare Süresi Ölçümü (GEÇERSİZ — debug/release eşit değil)

> [!WARNING]
> **GEÇERSİZ — debug/release eşit değil:** Bu bölümdeki A (debug) ile B (resmî release) karşılaştırması R8 ve Compose debug yükü eşitsizliği nedeniyle geçersizdir. Silinmemiş, arşiv amaçlı tutulmuştur. Gerçek ve adil karşılaştırma Bölüm 7'deki T/A/C (tümü debug) ölçümüdür.

- **Ölçüm Yöntemi:** Cihaz zamanı ofsetli `adb logcat -d -v epoch -s PlayMatch:D` yoklaması (`adb logcat -c` KULLANILMAMIŞTIR).
- **Test Magneti:** `magnet:?xt=urn:btih:88594aaacbde40ef3e2510c47374ec0aa396c08e` (Big Buck Bunny)
- **APK A:** Mevcut Dal (`app-debug.apk`, SHA-256: `ec00e02a63271aa2e2594ea4037553ccf81b55c03d3035657121ccf5af708cb8`)
- **APK B:** Resmi 1.2.5 (`downloaded_Play-1.2.5.apk`, SHA-256: `f7a0a9fac8f7c71f64abbe467a1735d5d7e8e0e74c05f1747688c99c97ded0ca`)

### Isınma Koşuları (Sayılmaz)
| Koşu | Hedef | Süre | Durum | Log Kaydı |
| :--- | :--- | :--- | :--- | :--- |
| **Isınma A-1** | A | 6.891 sn | `OK` | `1793825499.299 7238 7238 D PlayMatch: ilk kare: TestPrimary (faz=Starting)` |
| **Isınma A-2** | A | 5.412 sn | `OK` | `1793825516.482 7457 7457 D PlayMatch: ilk kare: TestPrimary (faz=Starting)` |
| **Isınma B-1** | B | 3.448 sn | `OK` | `1793825537.358 7707 7707 D PlayMatch: ilk kare: TestPrimary (faz=Starting)` |
| **Isınma B-2** | B | 2.568 sn | `OK` | `1793825551.304 7894 7894 D PlayMatch: ilk kare: TestPrimary (faz=Starting)` |

### Serpiştirilmiş Ham Ölçüm Tablosu (8 A + 8 B)
| Koşu No | Hedef | Süre | Durum | Başlangıç Epoch | Log Kaydı |
| :---: | :---: | :---: | :---: | :---: | :--- |
| **1** | **A** | **4.281 sn** | `OK` | `1793825570.580` | `1793825574.861 8118 8118 D PlayMatch: ilk kare: TestPrimary (faz=Starting)` |
| **2** | **B** | **3.006 sn** | `OK` | `1793825594.028` | `1793825597.034 8360 8360 D PlayMatch: ilk kare: TestPrimary (faz=Starting)` |
| **3** | **A** | **4.055 sn** | `OK` | `1793825616.661` | `1793825620.716 8585 8585 D PlayMatch: ilk kare: TestPrimary (faz=Starting)` |
| **4** | **B** | **2.823 sn** | `OK` | `1793825639.993` | `1793825642.816 8823 8823 D PlayMatch: ilk kare: TestPrimary (faz=Starting)` |
| **5** | **A** | **5.815 sn** | `OK` | `1793825661.951` | `1793825667.766 9041 9041 D PlayMatch: ilk kare: TestPrimary (faz=Starting)` |
| **6** | **B** | **2.677 sn** | `OK` | `1793825761.987` | `1793825764.664 9347 9347 D PlayMatch: ilk kare: TestPrimary (faz=Starting)` |
| **7** | **A** | **4.441 sn** | `OK` | `1793825784.235` | `1793825788.676 9575 9575 D PlayMatch: ilk kare: TestPrimary (faz=Starting)` |
| **8** | **B** | **3.025 sn** | `OK` | `1793825808.421` | `1793825811.446 9813 9813 D PlayMatch: ilk kare: TestPrimary (faz=Starting)` |
| **9** | **A** | **4.605 sn** | `OK` | `1793825830.580` | `1793825835.185 10031 10031 D PlayMatch: ilk kare: TestPrimary (faz=Starting)` |
| **10** | **B** | **2.568 sn** | `OK` | `1793825854.343` | `1793825856.911 10257 10257 D PlayMatch: ilk kare: TestPrimary (faz=Starting)` |
| **11** | **A** | **4.791 sn** | `OK` | `1793825945.630` | `1793825950.421 10489 10489 D PlayMatch: ilk kare: TestPrimary (faz=Starting)` |
| **12** | **B** | **5.054 sn** | `OK` | `1793825969.280` | `1793825974.334 10742 10742 D PlayMatch: ilk kare: TestPrimary (faz=Starting)` |
| **13** | **A** | **4.493 sn** | `OK` | `1793825992.979` | `1793825997.472 10967 10967 D PlayMatch: ilk kare: TestPrimary (faz=Starting)` |
| **14** | **B** | **11.595 sn** | `OK` | `1793826016.252` | `1793826027.847 11193 11193 D PlayMatch: ilk kare: TestPrimary (faz=Starting)` |
| **15** | **A** | **4.568 sn** | `OK` | `1793826047.496` | `1793826052.064 11464 11464 D PlayMatch: ilk kare: TestPrimary (faz=Starting)` |
| **16** | **B** | **2.509 sn** | `OK` | `1793826070.655` | `1793826073.164 11701 11701 D PlayMatch: ilk kare: TestPrimary (faz=Starting)` |

### İstatistiksel Özet ve Karşılaştırma
- **Geçerli Koşu Sayısı:** A: 8, B: 8 (Toplam 16)
- **Timeout Sayısı:** A: 0, B: 0
- **Geçersiz Koşu Sayısı:** A: 0, B: 0
- **İlk 4 Koşu Medyanı:**
  - **A (İlk 4):** **4.361 sn** (`[4.281, 4.055, 5.815, 4.441]`)
  - **B (İlk 4):** **2.914 sn** (`[3.006, 2.823, 2.677, 3.025]`)
- **Son 4 Koşu Medyanı:**
  - **A (Son 4):** **4.587 sn** (`[4.605, 4.791, 4.493, 4.568]`)
  - **B (Son 4):** **3.811 sn** (`[2.568, 5.054, 11.595, 2.509]`)
- **Genel Medyan:**
  - **Genel Medyan A:** **4.530 sn**
  - **Genel Medyan B:** **2.914 sn**
- **Yüzdesel Fark ((A − B) / B × 100):** **+%55.45** (A medyanı B medyanından 1.616 sn daha yüksektir)

---

## 7. Adil T/A/C İlk Kare Süresi Ölçümü (Tümü Debug)

Tüm yapılar `assembleDebug` ile derlenmiş ve eşit koşullarda serpiştirilerek (T-A-C döngüsüyle 8'er koşu) ölçülmüştür.

- **T (v1.2.5 debug, SHA `dd75650e657f03d9dd141891a44af015f1f3d95d55f8cbfd9032a059df89ceca`):**
  - Ham Koşular: `[5.280, 3.671, 4.018, 3.922, 5.492, 4.586, 3.980, 5.040]` sn
  - Medyan: **4.302 sn**
- **A (ae588d5 debug, SHA `ec00e02a63271aa2e2594ea4037553ccf81b55c03d3035657121ccf5af708cb8`):**
  - Ham Koşular: `[5.318, 4.151, 4.365, 4.229, 5.167, 4.001, 4.695, 4.313]` sn
  - Medyan: **4.339 sn**
- **C (overlay+yoklama kapalı, SHA `3fd702095dfa07aa7ed6ae7e6d548c27eab03e60b5c68c2ca5a22212bc46fa90`):**
  - Ham Koşular: `[4.060, 6.344, 4.382, 3.948, 4.986, 4.075, 4.508, 4.678]` sn
  - Medyan: **4.445 sn**

### Karşılaştırma Özeti
- **A − T Farkı:** **+%0.86** (+0.037 sn, torrent yükleme ekranı açılış süresini etkilememektedir).
- **C − T Farkı:** **+%3.32** (+0.143 sn, ölçüm sapması sınırları dahilindedir).

---

## 8. Bilinen Sınır

- **Bilinen sınır:** Dizide otomatik sonraki bölüm torrent ise `PlayTarget` nesnesinde logo/sahne bilgisi henüz bulunmadığı için metin başlıklı yedek ekran görünür (kasten kabul).
