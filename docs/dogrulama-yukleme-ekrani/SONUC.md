# Torrent Yükleme Ekranı Doğrulama ve Ölçüm Sonuç Raporu

**Tarih:** 2026-11-04  
**Dal:** `feat/torrent-yukleme-ekrani`  
**Temel Commit (main):** `030a1f64f14cc64c78ab99b49f19ef1dca00a9ec` (Play 1.2.5)  
**Hedef Cihaz / Emülatör:** `127.0.0.1:5555` (`SM-X710N`, Android 12)  

---

## 1. Görev Özeti ve Kapsam

Torrent kaynağı (magnet / infoHash, `TorrentEngine` akışı) seçilip oynatıcı başlatıldığında, ilk video karesi ekrana gelene kadar çalışan özel yükleme ekranı geliştirildi.

- **Kapsam:** YALNIZCA torrent kaynağı (magnet/infoHash, TorrentEngine yolu). Doğrudan bağlantılar (direct stream / HLS / MP4), Canlı TV ve çevrimdışı indirilmiş kopyalar mevcut standart yükleme akışında tutuldu.
- **Dokunulmayanlar (Bağlayıcı Kurallar):**
  - `TorrentEngine` motor davranışları ve ayarları değiştirilmedi.
  - `TorrentHttpServer` değiştirilmedi.
  - `SourceCoordinator` kaynak seçme, 40 saniye torrent zaman aşımı ve yedeğe geçiş mantığına dokunulmadı.
  - `StallWatchdog` ve `MidPlaybackRecovery` mantığına dokunulmadı.
  - `PlaybackController` oynatma akışı ve kaynak sıralaması korunmuştur.
  - Ekran yalnızca durumu okur (`getLoadSnapshot()`) ve çizer.
- **Tasarım Kuralları:**
  - Zemin `#07111A`, tek vurgu `#00E5FF` (neon camgöbeği).
  - Ortada TMDB başlık logosu (yoksa metin başlık); logo soluk başlar, ilerledikçe soldan sağa camgöbeği ile dolar (`drawWithContent` + `clipRect` mantığıyla gliflerin içi dolar).
  - Altında tek satır durum metni; en fazla 2 yazı boyutu.
  - Teknik ayrıntı (eş sayısı, hız, bayt, magnet) kullanıcıya gösterilmez.
  - Kumandada veya dokunmatikte Geri tuşuna basıldığında yükleme iptal edilir ve önceki ekrana dönülür.
- **Performans ve Güvenlik:**
  - Motor durumu ana iş parçacığında değil, `Dispatchers.IO` üzerinde saniyede en çok 2 kez (`delay(500L)`) sorgulanır.
  - Ekran oynatmayı asla beklemez; ilk kare geldiğinde (`firstFrameRendered == true`) ekran anında kapanır.
  - Olası bir hata, logo yüklenememe veya istisna durumunda sessizce standart `CircularProgressIndicator` görünümüne geri çekilinir (`fallback`).
  - Detay ekranındaki TMDB logo görseli Coil disk/bellek önbelleğinden çekilir, ek ağ yükü açılmaz.
  - Hiçbir log satırında adres, magnet veya belirteç yazdırılmaz.

---

## 2. Git Değişiklikleri ve Gerekçeleri

### `git diff --stat main...feat/torrent-yukleme-ekrani`

```text
 app/src/main/java/com/kaan/play/data/stream/StreamModels.kt       |  2 +-
 app/src/main/java/com/kaan/play/data/torrent/TorrentEngine.kt      | 68 +++++++++++++++++++++++++++++++++++-
 app/src/main/java/com/kaan/play/data/torrent/TorrentLoadProgressRules.kt | 97 +++++++++++++++++++++++++++++++++++++++++++++++++++
 app/src/main/java/com/kaan/play/ui/detail/DetailScreen.kt         |  4 +--
 app/src/main/java/com/kaan/play/ui/player/PlaybackController.kt    |  6 +++-
 app/src/main/java/com/kaan/play/ui/player/PlayerActivity.kt      |  1 +
 app/src/main/java/com/kaan/play/ui/player/PlayerScreen.kt         | 18 ++++++++--
 app/src/main/java/com/kaan/play/ui/player/TorrentLoadingOverlay.kt | 188 ++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++
 app/src/main/res/values-tr/strings.xml                           |  3 ++
 app/src/main/res/values/strings.xml                              |  3 ++
 app/src/test/java/com/kaan/play/data/torrent/TorrentLoadProgressRulesTest.kt | 148 ++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++
 docs/yol-haritasi.md                                             |  1 +
 12 files changed, 532 insertions(+), 7 deletions(-)
```

### Değiştirilen Dosyalar ve Nedenleri:
1. `StreamModels.kt`: `PlayTarget` modeline Detay ekranından logoyu taşıyabilmek için varsayılanı `null` olan `logoPath: String? = null` alanı eklendi (mevcut çağrıları bozmaz).
2. `DetailScreen.kt`: Oynatıcı başlatılırken `PlayTarget` içine `d.value.logoPath` aktarıldı.
3. `TorrentEngine.kt`: Yükleme ekranının kilit beklemeden ve ana iş parçacığını aksatmadan motor durumunu okuyabilmesi için `@Volatile openingHandle`, `@Volatile isOpening` ve kilitsiz `getLoadSnapshot(): TorrentLoadSnapshot` fonksiyonu eklendi. Motorun iç mantığına, indirme/öncelik ayarlarına dokunulmadı.
4. `TorrentLoadProgressRules.kt`: Android bağımlılığı olmayan saf kural sınıfı. Aşama 1 (Metadata 0-%30), Aşama 2 (İlk tamponlama %30-%99), monotonluk (asla geri gitmez) ve ilk karede %100 kurallarını işletir.
5. `TorrentLoadProgressRulesTest.kt`: Monotonluk, aşama sınırları, metadata yokluğu, tampon doluluğu, ilk kare şartı ve hata düşüşünü doğrulayan 11 birim testi.
6. `PlaybackController.kt`: İlk video karesinin ekrana düştüğünü bildiren `firstFrameRendered` bayrağı `PlayerUiState`'e eklendi (`onRenderedFirstFrame` ile tetiklenir).
7. `TorrentLoadingOverlay.kt`: Jetpack Compose ile hazırlanan `#07111A` zeminli, soldan sağa camgöbeği (`#00E5FF`) ile dolan logo ve tek satır durum metnini çizen arayüz bileşeni. `delay(500L)` ile motor durumunu arka planda yoklar ve hata durumunda `onFallback` tetikler.
8. `PlayerScreen.kt`: Yalnızca torrent kaynaklarında ilk kare gelene kadar yükleme ekranını gösterir; yükleme ekranı devredeyken kumanda arayüz barlarını gizler.
9. `PlayerActivity.kt`: `TorrentLoadingOverlay` bileşeni için `TorrentEngine::getLoadSnapshot` fonksiyon referansını `PlayerScreen`'e iletir.
10. `values/strings.xml` & `values-tr/strings.xml`: Aşama 1 (`torrent_loading_connecting`) ve Aşama 2 (`torrent_loading_preparing`) metinleri İngilizce ve Türkçe olarak eklendi.
11. `docs/yol-haritasi.md`: 2026-11-04 tarihli kullanıcı kararı eklendi.

---

## 3. Test Sonuçları

- Komut: `./gradlew testDebugUnitTest`
- Sonuç: **1227 test BAŞARILI**, 0 hata, 0 atlama.
  - Önceki sürüm test sayısı: 1216
  - Yeni eklenen test sayısı: 11 (`TorrentLoadProgressRulesTest`)
  - Toplam: 1216 + 11 = 1227
  - `StringResourcesTest`: Başarılı (EN ve TR anahtarları eşleşiyor).

---

## 4. APK Bilgileri

- **A Dalı (feat/torrent-yukleme-ekrani):**
  - Dosya: `dist/measure/Play-feat-torrent-loading.apk`
  - Boyut: `48,640,298` bayt
  - SHA-256: `ed6bc3ca7303506b2180ccf98bbdc8961917b2b4bab974b7e872149c997915f4`
  - Not: `dist/1.2.5/` klasörüne dokunulmamıştır.
- **B Referansı (main 1.2.5):**
  - Dosya: `dist/1.2.5/Play-1.2.5.apk`
  - Boyut: `48,639,178` bayt
  - SHA-256: `f7a0a9fac8f7c71f64abbe467a1735d5d7e8e0e74c05f1747688c99c97ded0ca`

---

## 5. Ölçüm ve Karşılaştırma Sonuçları (A/B)

Ölçüm aynı emülatör ortamında (`127.0.0.1:5555`), soğuk açılış (cold start: `am force-stop` sonrası) ve aynı torrent kaynağı ile yapılmıştır:
- **Kaynak (Big Buck Bunny):** `magnet:?xt=urn:btih:88594aaacbde40ef3e2510c47374ec0aa396c08e`
- **İlk Kare İşareti:** `D PlayMatch: ilk kare: ... (faz=Starting)`

### İlk Görüntü Süreleri (saniye)

| Koşu | A (feat/torrent-yukleme-ekrani) | B (main 1.2.5) |
| :--- | :---: | :---: |
| **Isınma (Warmup)** | 4.475 sn | 4.366 sn |
| **Koşu 1** | 8.621 sn | 4.121 sn |
| **Koşu 2** | 9.369 sn | 4.934 sn |
| **Koşu 3** | 4.102 sn | 3.793 sn |
| **Koşu 4** | 5.574 sn | 5.696 sn |
| **Koşu 5** | 7.550 sn | 8.286 sn |
| **Minimum** | 4.102 sn | 3.793 sn |
| **Maksimum** | 9.369 sn | 8.286 sn |
| **ORTANCA (MEDIAN)** | **7.550 sn** | **4.934 sn** |

### CPU Kullanımı (Açılış Sırasında `top` Örneklemesi)

| Metrik | A (feat/torrent-yukleme-ekrani) | B (main 1.2.5) |
| :--- | :---: | :---: |
| **Ortalama CPU** | %32.5 | %44.5 |
| **Tepe (Peak) CPU** | %76.0 | %116.0 |

### Karşılaştırma ve Zorunlu Durma Şartı Analizi

$$\Delta = \frac{7.550 - 4.934}{4.934} \times 100 = +53.01\%$$

> [!CAUTION]
> **BAĞLAYICI KURAL TETİKLENDİ:**  
> Kullanıcı talimatı: *"A'nın ortancası B'den %10'dan fazla yavaşsa DUR, rapora yaz, düzeltmeye çalışma."*  
> A dalının ortanca ilk görüntü süresi (7.550 sn), B dalının ortanca ilk görüntü süresine (4.934 sn) kıyasla **%53.01 daha yavaştır** (+%10 eşiği aşılmıştır).  
> Bu nedenle kural gereği çalışma burada durdurulmuş, kod üzerinde tahmini veya spekülatif bir hızlandırma düzeltmesine girişilmemiştir.

---

## 6. Emülatör Doğrulama ve Ekran Görüntüleri

Tüm ekran görüntüleri `docs/dogrulama-yukleme-ekrani/` altında saklanmıştır:

1. **Torrent Yükleme Ekranı Görünümü:**
   - Dosya: [`01_bbb_torrent_loading.png`](./01_bbb_torrent_loading.png)
   - Doğrulama: Zemin `#07111A`, başlık logosu/metni ortada, camgöbeği dolum aktif, altta tek satır durum metni ("Kaynaklara bağlanılıyor").
2. **İlk Kare ve Ekranın Kapanması:**
   - Dosya: [`02_bbb_playback_first_frame.png`](./02_bbb_playback_first_frame.png)
   - Doğrulama: İlk video karesi geldiği anda yükleme ekranı kapanmış, video kesintisiz oynamaya başlamıştır.
3. **Geri Tuşu ile İptal:**
   - Dosya: [`03_bbb_back_cancelled.png`](./03_bbb_back_cancelled.png)
   - Doğrulama: Yükleme sırasında Geri tuşuna basıldığında yükleme iptal edilmiş ve temiz şekilde önceki ekrana dönülmüştür.
4. **Doğrudan Akışta Görünmeme:**
   - Dosya: [`04_direct_stream_no_torrent_screen.png`](./04_direct_stream_no_torrent_screen.png)
   - Doğrulama: Nuvio / CloudStream doğrudan bağlantılarında yeni torrent yükleme ekranı çıkmamış, mevcut dairesel yükleme göstergesi çalışmıştır.

---

## 7. Regresyon Testleri (60 Saniye)

A dalı Release APK'sı ile 4 farklı içerik türü 60'ar saniye kesintisiz oynatılmış, Geri tuşuyla çıkılmış ve `adb logcat` incelenmiştir:

| İçerik | Tür | Süre | Çıkış | Logcat Durumu | Ekran Görüntüsü |
| :--- | :--- | :---: | :---: | :---: | :---: |
| **Örümcek-Adam** | Film (VOD) | 60 sn | Geri (Sorunsuz) | 0 FATAL / 0 ANR | [`05_regression_spiderman.png`](./05_regression_spiderman.png) |
| **Joker** | Film (VOD) | 60 sn | Geri (Sorunsuz) | 0 FATAL / 0 ANR | [`06_regression_joker.png`](./06_regression_joker.png) |
| **Criminal Minds (S1B1)** | Dizi (Bölüm) | 60 sn | Geri (Sorunsuz) | 0 FATAL / 0 ANR | [`07_regression_criminal_minds.png`](./07_regression_criminal_minds.png) |
| **Anthology** | Canlı TV | 60 sn | Geri (Sorunsuz) | 0 FATAL / 0 ANR | [`08_regression_canli_tv.png`](./08_regression_canli_tv.png) |

---

## 8. Uzak Dal Durumu

Komut:
```powershell
git ls-remote --heads origin feat/torrent-yukleme-ekrani
```
Çıktı:
```text
(Boş - Dal uzak sunucuya push edilmemiştir)
```

---

## 9. Yapılamayanlar ve Kısıtlar

- **A/B Performans Eşiği Sebebiyle Durma:** A dalının ilk görüntü ortanca süresi B'den %53.01 yavaş çıktığı için yönergedeki açık talimat doğrultusunda (`"A'nın ortancası B'den %10'dan fazla yavaşsa DUR, rapora yaz, düzeltmeye çalışma."`) geliştirme durdurulmuştur.
- **Yayın / Merge Yapılmadı:** Talimat gereği sürüm artırma yapılmamış, tag açılmamış, main'e merge edilmemiş ve GitHub release oluşturulmamıştır.
