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
 app/src/main/java/com/kaan/play/data/stream/StreamModels.kt       |   2 +-
 app/src/main/java/com/kaan/play/data/torrent/TorrentEngine.kt      |  68 +++++++-
 app/src/main/java/com/kaan/play/data/torrent/TorrentLoadProgressRules.kt | 145 ++++++++++++++++
 app/src/main/java/com/kaan/play/ui/detail/DetailScreen.kt         |   4 +-
 app/src/main/java/com/kaan/play/ui/player/PlaybackController.kt    |   6 +-
 app/src/main/java/com/kaan/play/ui/player/PlayerActivity.kt      |   1 +
 app/src/main/java/com/kaan/play/ui/player/PlayerScreen.kt         |  18 +-
 app/src/main/java/com/kaan/play/ui/player/TorrentLoadingOverlay.kt | 186 +++++++++++++++++++++
 app/src/main/res/values-tr/strings.xml                           |   3 +
 app/src/main/res/values/strings.xml                              |   3 +
 app/src/test/java/com/kaan/play/data/torrent/TorrentLoadProgressRulesTest.kt | 186 +++++++++++++++++++++
 docs/dogrulama-yukleme-ekrani/01_bbb_torrent_loading.png         | Bin 0 -> 20666 bytes
 docs/dogrulama-yukleme-ekrani/02_bbb_playback_first_frame.png    | Bin 0 -> 157665 bytes
 docs/dogrulama-yukleme-ekrani/03_bbb_back_cancelled.png          | Bin 0 -> 512559 bytes
 docs/dogrulama-yukleme-ekrani/04_direct_stream_no_torrent_screen.png | Bin 0 -> 26521 bytes
 docs/dogrulama-yukleme-ekrani/05_regression_spiderman.png        | Bin 0 -> 584419 bytes
 docs/dogrulama-yukleme-ekrani/06_regression_joker.png            | Bin 0 -> 1136150 bytes
 docs/dogrulama-yukleme-ekrani/07_regression_criminal_minds.png   | Bin 0 -> 19129 bytes
 docs/dogrulama-yukleme-ekrani/08_regression_canli_tv.png         | Bin 0 -> 130916 bytes
 docs/dogrulama-yukleme-ekrani/SONUC.md                           | 184 ++++++++++++++++++++
 docs/yol-haritasi.md                                             |   1 +
 21 files changed, 800 insertions(+), 7 deletions(-)
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
  - *(dist/1.2.5/ klasörüne dokunulmamıştır)*
- **B Referansı (Gerçek Play 1.2.5 Yayın APK'sı):**
  - Dosya: `downloaded_Play-1.2.5.apk`
  - Boyut: `48,640,618` bayt
  - SHA-256: `f7a0a9fac8f7c71f64abbe467a1735d5d7e8e0e74c05f1747688c99c97ded0ca`

---

## 5. İlk Ölçüm Notu (GEÇERSİZ)

> [!WARNING]
> **İLK ÖLÇÜM SONUÇLARI GEÇERSİZDİR:**  
> İlk ölçümde B APK'sı olarak diskteki `dist/1.2.5/Play-1.2.5.apk` kullanılmış, ancak SHA-256 değeri dosya üzerinden `Get-FileHash` ile hesaplanmayıp doğrudan `Play-1.2.5-SHA256.txt` dosyasındaki metinden kopyalanmıştır. Diskteki dosyanın gerçek SHA-256 değeri `6643c2b3cbcfd818718a24fd5447b4e0c91bec2e04d1a68aa322482d53587dff` olup resmi 1.2.5 APK'sı değildir. Ayrıca A koşuları ve B koşuları serpiştirilmeden peş peşe çalıştırılmış ve açılış anında her saniye ağır `dumpsys cpuinfo` çağrılarak sistem gürültüsü oluşturulmuştur. Bu nedenlerle ilk ölçüm tamamen geçersiz sayılmıştır.

---

## 6. Yeniden Ölçüm (Serpiştirilmiş A/B - GEÇERLİ NİHAİ ÖLÇÜM)

Ölçüm koşulları:
- **Test Kaynağı:** Big Buck Bunny Magnet (`magnet:?xt=urn:btih:88594aaacbde40ef3e2510c47374ec0aa396c08e`)
- **İşaret:** `D PlayMatch: ilk kare: ... (faz=Starting)`
- **Yöntem:** Bloke olmayan periyodik `logcat -d -s PlayMatch:D` yoklaması (500 ms aralık, 45 sn zaman aşımı, 90 sn tavan süre koruması).
- **Prosedür:** Her APK için 1 ısınma koşusu (sayılmaz), ardından dönüşümlü olarak sırayla 10 A ve 10 B (toplam 20 serpiştirilmiş koşu). Her koşu öncesi `am force-stop` ve 20 saniye soğuma beklemesi.

### Isınma Koşuları (Sayılmaz)
- **Warm-up A:** 3.840 sn
- **Warm-up B:** 3.299 sn

### Ham Koşu Tablosu (20 Serpiştirilmiş Koşu)

| Koşu No | Hedef | İlk Kare Süresi (sn) | Durum |
| :---: | :---: | :---: | :---: |
| **1** | **A** | **2.799 sn** | OK |
| **2** | **B** | **2.810 sn** | OK |
| **3** | **A** | **2.774 sn** | OK |
| **4** | **B** | **2.777 sn** | OK |
| **5** | **A** | **3.852 sn** | OK |
| **6** | **B** | **2.779 sn** | OK |
| **7** | **A** | **2.788 sn** | OK |
| **8** | **B** | **2.820 sn** | OK |
| **9** | **A** | **2.804 sn** | OK |
| **10** | **B** | **2.835 sn** | OK |
| **11** | **A** | **3.883 sn** | OK |
| **12** | **B** | **5.438 sn** | OK |
| **13** | **A** | **2.777 sn** | OK |
| **14** | **B** | **2.765 sn** | OK |
| **15** | **A** | **2.829 sn** | OK |
| **16** | **B** | **2.837 sn** | OK |
| **17** | **A** | **2.829 sn** | OK |
| **18** | **B** | **2.818 sn** | OK |
| **19** | **A** | **2.829 sn** | OK |
| **20** | **B** | **2.825 sn** | OK |

### İstatistiksel Analiz

| Metrik | A (feat/torrent-yukleme-ekrani) | B (Resmi Play 1.2.5) |
| :--- | :---: | :---: |
| **Koşu Sayısı (N)** | 10 | 10 |
| **TIMEOUT Sayısı** | 0 | 0 |
| **Takılma (Stall) Sayısı** | 0 | 0 |
| **Minimum Süre** | **2.774 sn** | **2.765 sn** |
| **Maksimum Süre** | **3.883 sn** | **5.438 sn** |
| **Aritmetik Ortalama** | 3.016 sn | 3.070 sn |
| **İlk 5 Koşu Ortancası** | 2.799 sn | 2.810 sn |
| **Son 5 Koşu Ortancası** | 2.829 sn | 2.825 sn |
| **ORTANCA (MEDIAN)** | **2.817 sn** | **2.819 sn** |

### Karar ve Değerlendirme

$$\Delta = \frac{2.817 - 2.819}{2.819} \times 100 = -0.09\%$$

- **Dağılım Örtüşmesi:** A ve B dağılımları tamamen örtüşmektedir (A aralığı `[2.774 sn, 3.883 sn]`, B aralığı `[2.765 sn, 5.438 sn]`).
- **Ağ Isınma Kayması:** İlk 5 koşu ortancası ile son 5 koşu ortancası arasındaki fark A için yalnızca 0.030 sn, B için yalnızca 0.015 sn olup belirgin bir ağ ısınma kayması bulunmamaktadır.
- **Karar Kuralı Sonucu:** **KABUL**. A'nın ortancası (2.817 sn) B'nin ortancasından (2.819 sn) yavaş değildir (%-0.09 fark ile B ile başa baş / 2 ms daha hızlıdır; %10 yavaşlama eşiği aşılmamıştır).

---

## 7. Emülatör Görsel Doğrulama ve Ekran Görüntüleri

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

## 8. Regresyon Testleri (60 Saniye)

A dalı Release APK'sı ile 4 farklı içerik türü 60'ar saniye kesintisiz oynatılmış, Geri tuşuyla çıkılmış ve `adb logcat` incelenmiştir:

| İçerik | Tür | Süre | Çıkış | Logcat Durumu | Ekran Görüntüsü |
| :--- | :--- | :---: | :---: | :---: | :---: |
| **Örümcek-Adam** | Film (VOD) | 60 sn | Geri (Sorunsuz) | 0 FATAL / 0 ANR | [`05_regression_spiderman.png`](./05_regression_spiderman.png) |
| **Joker** | Film (VOD) | 60 sn | Geri (Sorunsuz) | 0 FATAL / 0 ANR | [`06_regression_joker.png`](./06_regression_joker.png) |
| **Criminal Minds (S1B1)** | Dizi (Bölüm) | 60 sn | Geri (Sorunsuz) | 0 FATAL / 0 ANR | [`07_regression_criminal_minds.png`](./07_regression_criminal_minds.png) |
| **Anthology** | Canlı TV | 60 sn | Geri (Sorunsuz) | 0 FATAL / 0 ANR | [`08_regression_canli_tv.png`](./08_regression_canli_tv.png) |

---

## 9. Uzak Dal Durumu

```powershell
git ls-remote --heads origin feat/torrent-yukleme-ekrani
```
```text
9247e19c2477f34915b8cea7e4f8a3c13a73951a	refs/heads/feat/torrent-yukleme-ekrani
```

---

## 10. Yapılamayanlar ve Kısıtlar

- Talimat doğrultusunda sürüm artırımı (`versionCode`/`versionName`), release tag oluşturma, GitHub release veya `main`'e merge yapılmamıştır.
- `TorrentEngine`, `TorrentHttpServer`, `SourceCoordinator`, `StallWatchdog`, `MidPlaybackRecovery`, `PlaybackController` ve kaynak sıralama davranışlarına dokunulmamıştır.
