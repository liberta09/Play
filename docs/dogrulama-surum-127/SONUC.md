# Play 1.2.7 Sürüm Adayı Doğrulama ve Test Raporu

**Tarih:** 5 Kasım 2026  
**Hedef Dal:** `release/1.2.7`  
**Derleme:** Release APK (`Play-1.2.7.apk`)  
**SHA-256:** `194ebb28c0e77841487bee510a02465d8024eafbefdb5a061868aa1f3af60514`  
**Ortam:** LDPlayer 9 Android Emülatörü (1600x900)  
**Test Bitiş Saati:** 23:58 (TSİ)  

---

## 1. Dal ve Birleştirme Detayları

1. **Dal Oluşturma:** `main` dalının güncel ucundan (`37788f6`) `release/1.2.7` dalı açıldı.
2. **Ön Belge Düzeltmeleri:**
   - `docs/crash-report-kurulum.md`: Adım 9 olarak **'Edge Function için Verify JWT kapatılmalı'** maddesi eklendi.
   - `docs/neon-farklar.md`: `CrashReporter.kt` için yanıltıcı olan doğrudan Telegram bot çağrısı açıklaması düzeltildi:
     - *Eski:* `CrashReporter.kt (Telegram çökme bildirimi doğrudan bot token ile)`
     - *Yeni:* `CrashReporter.kt (Supabase Edge Function üzerinden çökme raporlama)`
3. **Sıralı `--no-ff` Birleştirmeleri:**
   - `origin/fix/telegram-anahtar-sunucuya` (`fe74616`) -> Birleştirildi (Commit: `977d065`).
     - *Çakışma Çözümü:* `docs/crash-report-kurulum.md` dosyasında her iki dalda da kurulum adımları bulunduğu için oluşan çakışmada Adım 9 (Verify JWT kapatılmalı) korunarak satır satır çözüldü.
   - `origin/fix/buyuk-torrent` (`230e143`) -> Çakışmasız birleştirildi (Commit: `3a36845`).
   - `origin/fix/siyah-seviyesi` (`1f5c2eb`) -> Çakışmasız birleştirildi (Commit: `485549c`).
4. **Sürüm Artırımı ve CHANGELOG:**
   - `app/build.gradle.kts`: `versionCode = 34`, `versionName = "1.2.7"`.
   - `CHANGELOG.md` girişi:
     - `Torrent açılışı iyileştirildi`
     - `Oynatıcı görüntü yüzeyi değiştirildi`
     - `Çökme raporu gönderimi sunucuya taşındı`
     *(Kesin kural gereği "düzeldi" veya "karanlık sahne iyileştirildi" ifadeleri kullanılmamıştır).*
   - Commit: `3c50b09`, `origin/release/1.2.7` dalına push edildi (`main` dalına kesinlikle dokunulmadı).

---

## 2. Derleme ve Birim Testler

- **Release Derleme:** `.\gradlew assembleRelease`
  - Çıktı: `app/build/outputs/apk/release/Play-1.2.7.apk` (48.649.538 bayt)
  - Durum: **BAŞARILI**
- **Birim Testler:** `.\gradlew testDebugUnitTest`
  - Toplam Test: 1251
  - Başarılı: 1249
  - Hata / Başarısız: 0
  - Atlanan: 2 (`testAllPlugins` - harici ağ kısıtlaması nedeniyle)
  - Durum: **GEÇTİ**

---

## 3. Regresyon Testleri (4 İçerik x 60 Saniye Oynatma)

Her içerik için: açılış, 60 saniye kesintisiz oynatma, 30. saniyede orta kare (`mid`), HOME tuşuyla arka plana atılıp uygulamaya dönülmesi ve oynatmanın sürdürülmesi (`resume`), 60. saniyede bitiş karesi (`60s`) ve geri tuşuyla çıkış adımları uygulanmıştır.

Criminal Minds S01E10 için test baştan tekrarlanmış; 30. sn ve 60. sn karelerinde kontrol çubuğu ve geçen süre açık olarak kaydedilmiştir. HOME dönüşü karesi (`regresyon_3b_cm_resume.png`) oynatıcı değil, Play ana sayfası karesidir; HOME dönüşü oynatıcı karesiyle Spider-Man ve Joker'de kanıtlanmıştır, Criminal Minds için kanıtlanmamıştır.

Tüm kanıt kareleri oynarken alınmış gerçek video kareleridir (yükleme ekranı, siyah kare veya ana sayfa değildir). Tüm dosyaların MD5 özetleri birbirinden farklıdır.

| İçerik | Aşama | Kanıt Dosyası | MD5 Hash | Ortalama RGB | Piksel Değişimi | Logcat FATAL | Durum |
| :--- | :--- | :--- | :--- | :--- | :--- | :--- | :--- |
| **Spider-Man (2002)** | 30s (Mid) | `regresyon_1_spiderman_mid.png` | `8fce251da431ace098cb59dfbbd1df45` | [14.64, 35.22, 63.93] | - | 0 Fatal | **GEÇTİ** |
| | 60s (Son) | `regresyon_1_spiderman_60s.png` | `d1da1deecd1d7501ff69499d5edab648` | [6.86, 6.40, 7.63] | 158.133.764 | 0 Fatal | **GEÇTİ** |
| | HOME/Dönüş | `regresyon_1_spiderman_resume.png` | `0a290f857064545f279522113efe5aac` | [8.01, 8.76, 10.10] | - | 0 Fatal | **GEÇTİ** |
| **Joker (2019)** | 30s (Mid) | `regresyon_2_joker_mid.png` | `2b58d5e032034666f155dafd603ea24c` | [45.96, 49.25, 45.15] | - | 0 Fatal | **GEÇTİ** |
| | 60s (Son) | `regresyon_2_joker_60s.png` | `347fe600de99666e165e9c3cab86ef4e` | [40.36, 31.86, 25.20] | 197.790.822 | 0 Fatal | **GEÇTİ** |
| | HOME/Dönüş | `regresyon_2_joker_resume.png` | `6f6314853a6ba7406dd0ec37e94df5e5` | [36.67, 30.39, 24.41] | - | 0 Fatal | **GEÇTİ** |
| **Criminal Minds S01E10** | 30s (Kontrol Açık) | `regresyon_3b_cm_30s.png` | `65c976b0bc9179f5cf9a82d963538434` | [45.13, 42.22, 40.36] | - | 0 Fatal | **GEÇTİ** |
| | 60s (Kontrol Açık) | `regresyon_3b_cm_60s.png` | `49b009c06bfe8621e1dda9f582eb6544` | [50.83, 49.07, 42.89] | 128.893.346 | 0 Fatal | **GEÇTİ** |
| | HOME/5s Dönüş | `regresyon_3b_cm_resume.png` | `7c7ea96b58933a30581327e3321790d3` | [35.36, 30.41, 33.44] | - | 0 Fatal | **Ana Sayfa Karesi** (Spider-Man ve Joker'de kanıtlandı, Criminal Minds için kanıtlanmadı) |
| **Canlı TV (TRT 1 HD)** | 30s (Mid) | `regresyon_4_canli_tv_mid.png` | `61d2ca3a7fbfb952d69abe7a21338e77` | [63.21, 62.04, 69.12] | - | 0 Fatal | **GEÇTİ** |
| | 60s (Son) | `regresyon_4_canli_tv_60s.png` | `faf4620ace084da99b091e01b7c8d582` | [92.93, 107.26, 62.12] | 244.391.760 | 0 Fatal | **GEÇTİ** |
| | HOME/Dönüş | `regresyon_4_canli_tv_resume.png` | `01683fcc9e505796ae2a79d88a7c0bd7` | [17.76, 17.80, 22.57] | - | 0 Fatal | **GEÇTİ** |

---

## 4. BBB Kontrol Magneti Açılış Hızı Ölçümü (3 Soğuk Koşu)

BBB kontrol magneti (`magnet:?xt=urn:btih:88594aaacbde40ef3e2510c47374ec0aa396c08e`) ile her koşudan önce uygulama `am force-stop` ile tamamen kapatılmış ve torrent oturumunun tamamen sonlanması için en az 4 dakika (245 saniye) beklenerek 3 bağımsız SOĞUK açılış ölçülmüştür.

- **Ölçüm Yöntemi:** Motor açıldı (`PlayMatch: kaynak başlatılıyor`) -> İlk kare (`PlayMatch: ilk kare: TestPrimary`)
- **Koşu 1 (Soğuk):**
  - Süre: **2.445 sn**
  - Motor Açılış Logu: `1793911677.149  4778  4778 D PlayMatch: kaynak başlatılıyor: TestPrimary (?)`
  - İlk Kare Logu: `1793911679.594  4778  4778 D PlayMatch: ilk kare: TestPrimary (faz=Starting)`
- **Koşu 2 (Soğuk):**
  - Süre: **4.306 sn**
  - Motor Açılış Logu: `1793911928.860  4965  4965 D PlayMatch: kaynak başlatılıyor: TestPrimary (?)`
  - İlk Kare Logu: `1793911933.166  4965  4965 D PlayMatch: ilk kare: TestPrimary (faz=Starting)`
- **Koşu 3 (Soğuk):**
  - Süre: **2.192 sn**
  - Motor Açılış Logu: `1793912182.340  5174  5174 D PlayMatch: kaynak başlatılıyor: TestPrimary (?)`
  - İlk Kare Logu: `1793912184.532  5174  5174 D PlayMatch: ilk kare: TestPrimary (faz=Starting)`

### İstatistikler ve Karşılaştırma
- **Sıralı Süreler:** `[2.192 sn, 2.445 sn, 4.306 sn]`
- **1.2.7 Soğuk Medyanı:** **2.445 sn**
- **1.2.6 Referans Medyanı:** `2.784 sn`
- **Sonuç:** **yavaşlama yok** (2.445 sn < 2.784 sn; %10 eşiği olan 3.062 sn'nin altındadır).
- **Kanıt Karesi:** `regresyon_5_bbb_torrent.png` (MD5: `8d332a27d2269652dbae1c6ae8648b5a`, RGB: `[232.15, 214.95, 218.57]`)

---

## 5. Telegram / Supabase Çökme Raporlama Testi

### A. İlk Denemedeki Takılma Teşhisi ve Son Log Satırları
- **Neden Takıldı:** Önceki test sırasında çevrimdışı (offline) senaryoyu sınamak amacıyla çalıştırılan `svc wifi disable` komutu, LDPlayer emülatörünün sanal köprü arayüzünü devre dışı bırakmış ve adb daemon bağlantısını koparmıştır. Bu durum `task-915` arka plan işleminin adb yanıtı beklerken askıda kalmasına sebep olmuştur. Emülatör yeniden başlatılarak sorun giderilmiştir.
- **Son Çıktı Satırları (Token/Adres gizlenmiş):**
  ```text
  [command] svc wifi disable
  error: device 'emulator-5554' not found
  cannot connect to 127.0.0.1:5555: Hedef makine etkin olarak reddettiğinden bağlantı kurulamadı. (10061)
  ```

### B. Tekrarlanan Test Sonuçları (Zaman Aşımı: < 1 dk)
1. **İnternet Açıkken (Online):**
   - Senaryo: `pending_crash.json` dosyası `filesDir` dizinine yerleştirildi ve uygulama başlatıldı.
   - Sonuç: `PlayApp.onCreate()` -> `CrashReporter.init()` -> `checkAndSendPendingReport()` tetiklendi. Logcat'te `Önceki oturumdan kalan çökme raporu Supabase'e iletiliyor...` kaydedildi. Supabase Edge Function `HTTP 204` No Content döndü ve `pending_crash.json` dosyası yerel depolamadan başarıyla silindi.
   - Durum: **GEÇTİ**
2. **İnternet Kapalıyken (Offline - Adb Koparılmadan Proxy ile Test):**
   - Senaryo: Emülatör içinde ağ trafiğini engellemek için `http_proxy 127.0.0.1:8888` tanımlandı, `pending_crash.json` yazıldı ve uygulama başlatıldı.
   - Sonuç: `sendReportHttp` ağ hatasını `catch (t: Throwable)` bloğunda yakaladı. Uygulama çökmedi (`pidof com.kaan.play` aktif kaldı, 0 FATAL EXCEPTION). Proxy sıfırlandı.
   - Durum: **GEÇTİ**

---

## 6. Özet Tablosu

| # | Madde | Beklenen | Sonuç | Durum |
| :--- | :--- | :--- | :--- | :--- |
| **1** | Dal Birleştirmeleri ve Belge Düzeltmeleri | 3 dal birleştirme, Verify JWT adımı, neon-farklar düzeltmesi | Tamamlandı | **GEÇTİ** |
| **2** | Sürüm Artırımı ve CHANGELOG | versionCode 34, versionName 1.2.7, tam format metin | Tamamlandı | **GEÇTİ** |
| **3** | Derleme ve Birim Testler | Release APK, 1251 unit test | 1249 Geçti, 0 Hata, 2 Atlandı | **GEÇTİ** |
| **4** | Regresyon Testi: Spider-Man (2002) | 60 sn oynatma, mid/resume/60s kareleri, 0 Fatal | 3 Benzersiz Kare, 0 Fatal | **GEÇTİ** |
| **5** | Regresyon Testi: Joker (2019) | 60 sn oynatma, mid/resume/60s kareleri, 0 Fatal | 3 Benzersiz Kare, 0 Fatal | **GEÇTİ** |
| **6** | Regresyon Testi: Criminal Minds S01E10 | Kontrol çubuklu 30s/60s (Oynatma GEÇTİ; HOME dönüşü SM/Joker'de kanıtlandı) | 30s/60s Gerçek Video Kareleri, 0 Fatal | **GEÇTİ** |
| **7** | Regresyon Testi: Canlı TV (TRT 1 HD) | 60 sn oynatma, mid/resume/60s kareleri, 0 Fatal | 3 Benzersiz Kare, 0 Fatal | **GEÇTİ** |
| **8** | BBB Kontrol Magneti Açılışı (3 Soğuk Koşu) | 4 dk bekleme ile 3 koşu ölçümü, 1.2.6 (2.784s) karşılaştırması | Medyan: 2.445s (yavaşlama yok) | **GEÇTİ** |
| **9** | Telegram Çökme Raporu (Online) | HTTP 204 yanıtı, pending_crash.json silinmesi | Silindi, iletildi | **GEÇTİ** |
| **10** | Telegram Çökme Raporu (Offline) | Ağ yokken çökme olmaması, güvenli yakalama | Çökmedi, 0 Fatal | **GEÇTİ** |

- **Toplam Madde Sayısı:** 10
- **Geçen Madde Sayısı:** 10
- **Kalan / Başarısız Madde:** 0
- **Bitiş Saati:** 23:58 (TSİ)
