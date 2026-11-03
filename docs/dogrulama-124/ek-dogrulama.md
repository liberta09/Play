# release/1.2.4 Ek Doğrulama Sonuç Raporu (Release APK)

> [!NOTE]
> **KOD DEĞİŞİKLİĞİ BİLGİLENDİRMESİ:**  
> **Kod dosyalarında hiçbir değişiklik yapılmamıştır.**  
> Yapılan tüm işlemler ve doğrulamalar doğrudan `dist/1.2.4/Play-1.2.4.apk` (R8 ile küçültülmüş yayın APK'sı) üzerinde temiz kurulum ile gerçekleştirilmiştir.

**Tarih:** 2026-11-03  
**Dal:** `release/1.2.4`  
**Test Cihazı:** LDPlayer 9 Emülatörü (`emulator-5554`, Android 9 / API 28)  
**Derleme:** Release APK (`dist/1.2.4/Play-1.2.4.apk`)  

---

## 1. Hazırlık ve APK Doğrulaması

1. **SHA-256 Eşleşme Kontrolü:**
   - APK Dosyası: `dist/1.2.4/Play-1.2.4.apk`
   - Hesaplanan SHA-256: `01d56587599bf9a2006756d61d728f9d6d33fe73da3c9d06f07a16ca1ab39052`
   - `dist/1.2.4/Play-1.2.4-SHA256.txt`: `01d56587599bf9a2006756d61d728f9d6d33fe73da3c9d06f07a16ca1ab39052`
   - **Durum:** TAM EŞLEŞTİ.

2. **Temiz Kurulum:**
   - Cihazdaki önceki kurulum tamamen kaldırıldı (`adb uninstall com.kaan.play`).
   - Release APK temiz kuruldu (`adb install dist/1.2.4/Play-1.2.4.apk`).
   - Paket dökümü doğrulandı (`dumpsys package com.kaan.play`):
     - `versionCode=31`
     - `versionName=1.2.4`

3. **Çağrı Yöntemi:**
   - `PlayerActivity` (`com.kaan.play/.ui.player.PlayerActivity`) `ACTION_VIEW` intent'ini `PlaySession.from(intent)` üzerinden doğrudan karşılamaktadır.
   - Harici çağrı için `su 0 am start -a android.intent.action.VIEW -d <URI> -n com.kaan.play/.ui.player.PlayerActivity` komutu kullanılmıştır.

---

## 2. Ek Doğrulama Özet Tablosu

| Test | İçerik / Tür | Süre / Aralık | İlk Kare (Açılış Süresi) | Kumanda / İleri Sarma | Geri Tuşu | FATAL / Crash | Sonuç |
| :--- | :--- | :--- | :--- | :--- | :--- | :--- | :--- |
| **TEST A** | HTTP MP4 (`oceans.mp4`) | 3 dakika kesintisiz oynatma | **1.67 sn** | 3x ileri (Key 22), 2x geri (Key 21), 1x duraklat/devam (Key 23) | Sorunsuz çıktı (Launcher odaklandı) | 0 | **GEÇTİ** |
| **TEST B** | HLS Canlı Akış (`test-streams.mux.dev`) | 60 saniye kesintisiz oynatma | **2.21 sn** | Canlı HLS akışı (Canlı TV simülasyonu) | Sorunsuz çıktı (Launcher odaklandı) | 0 | **GEÇTİ** |
| **TEST C** | Torrent Magnet (Big Buck Bunny 1080p `88594aaa...`) | 3x soğuk (>3 dk aralık) + 1x sıcak (<3 dk) | Soğuk 1: **4.39 sn**<br>Soğuk 2: **2.74 sn**<br>Soğuk 3: **5.19 sn**<br>Sıcak: **5.24 sn** | 1. İleri: **0.95 sn**<br>2. İleri: **0.45 sn** | Sorunsuz çıktı (Launcher odaklandı) | 0 | **GEÇTİ** |

---

## 3. TEST A Detayları (HTTP MP4 - 3 Dakika Oynatma ve Kumanda)

- **Amaç:** Release APK'da HTTP video akışının açılışı, 3 dakika boyunca kararlı oynatılması, kumanda tuşları ve oynatıcıdan Geri tuşu ile çıkışın doğrulanması.
- **İlk Kare Süresi:** **1.67 sn** (`PlayMatch: ilk kare: TestPrimary (faz=Starting)`)
- **Kumanda Eylemleri:**
  - 10-14. saniyelerde 3 kez ileri sarma (`keyevent 22`).
  - 18-20. saniyelerde 2 kez geri sarma (`keyevent 21`).
  - 24-27. saniyelerde 1 kez duraklatma ve devam ettirme (`keyevent 23`).
  - 47 saniyelik test klibinin süre bitimiyle kapanmaması için periyodik geri sarma uygulanarak video 3 dakika (180 sn) boyunca canlı tutuldu.
  - 120. saniyede tam ekran oynatma ekran görüntüsü alındı (`test_a_playing.png`).
- **Geri Tuşu ve Kararlılık:**
  - 180. saniyede kumandadan Geri tuşu (`keyevent 4`) basıldı.
  - Odak penceresi: `mCurrentFocus=Window{257f2b6 u0 com.ldmnq.launcher3/com.android.launcher3.Launcher}`
  - Oynatıcıdan temiz bir şekilde çıkıldı, çökme yaşanmadı.
  - `FATAL` / `AndroidRuntime` hata sayısı: **0**.
- **Kanıt Dosyaları:**
  - `docs/dogrulama-124/test_a_playing.png`
  - `docs/dogrulama-124/test_a_before_back.png`
  - `docs/dogrulama-124/test_a_after_back.png`
  - `docs/dogrulama-124/kanit-test-a.txt`

---

## 4. TEST B Detayları (HLS Canlı Akış - 60 Saniye Oynatma)

- **Amaç:** Canlı kanal yerine geçen HLS (`.m3u8`) akışının Release APK'da açılışı, 60 saniye kesintisiz oynatılması ve Geri tuşu davranışının doğrulanması.
- **İlk Kare Süresi:** **2.21 sn** (`PlayMatch: ilk kare: TestPrimary (faz=Starting)`)
- **Oynatma:**
  - HLS akışı 60 saniye boyunca kesintisiz oynatıldı.
  - Video çözücü: `OMX.google.h264.decoder` (yazılım, 38 ms).
  - 30. saniyede tam ekran oynatma görüntüsü alındı (`test_b_playing.png`).
- **Geri Tuşu ve Kararlılık:**
  - 60. saniyede Geri tuşu (`keyevent 4`) basıldı.
  - Oynatıcı temiz bir şekilde kapandı ve Launcher ekranına dönüldü.
  - `FATAL` / `AndroidRuntime` hata sayısı: **0**.
- **Kanıt Dosyaları:**
  - `docs/dogrulama-124/test_b_playing.png`
  - `docs/dogrulama-124/test_b_before_back.png`
  - `docs/dogrulama-124/test_b_after_back.png`
  - `docs/dogrulama-124/kanit-test-b.txt`

---

## 5. TEST C Detayları (Big Buck Bunny Torrent Açılış ve İleri Sarma)

- **Amaç:** Release APK'da (R8 küçültülmüş derleme) yerleşik torrent motorunun (`libtorrent4j`) başlatılması, JNI kütüphanesinin yüklenmesi, POSIX Disk I/O ile blok düzeyinde indirme, soğuk/sıcak açılış süreleri, ileri sarma ve Geri tuşunun doğrulanması.
- **Test Torrenti:** Big Buck Bunny 1080p 30fps (`88594aaacbde40ef3e2510c47374ec0aa396c08e`).

### 5.1 Açılış Süreleri Tablosu (Açılış Emri → İlk Kare)

| Koşu No | Ölçüm Türü | Bekleme / Koşul | Süre (sn) | Log Satırı |
| :---: | :--- | :--- | :---: | :--- |
| **1** | Soğuk Açılış 1 | > 3 dk (Uygulama tamamen kapalı) | **4.39 sn** | `11-03 12:08:15.671 D/PlayMatch: ilk kare: TestPrimary (faz=Starting)` |
| **2** | Soğuk Açılış 2 | 185 sn bekleme (> 3 dk, tam kapatma) | **2.74 sn** | `11-03 12:11:28.353 D/PlayMatch: ilk kare: TestPrimary (faz=Starting)` |
| **3** | Soğuk Açılış 3 | 185 sn bekleme (> 3 dk, tam kapatma) | **5.19 sn** | `11-03 12:14:43.687 D/PlayMatch: ilk kare: TestPrimary (faz=Starting)` |
| **4** | Sıcak Açılış | 10 sn bekleme (< 3 dk aralık) | **5.24 sn** | `11-03 12:15:04.222 D/PlayMatch: ilk kare: TestPrimary (faz=Starting)` |

- **Soğuk Açılış Ortalaması:** **4.11 sn** (Ortanca: **4.39 sn**)
- **Sıcak Açılış Süresi:** **5.24 sn**

### 5.2 İleri Sarma (Seek) Performansı

Oynatma sırasında uygulanan iki ardışık ileri sarma eyleminin (Key 22) tepki süreleri:
1. **1. İleri Sarma:** **0.95 sn** (`11-03 12:15:10.578 D/PlayMatch: ilk kare: TestPrimary (faz=Playing)`)
2. **2. İleri Sarma:** **0.45 sn** (`11-03 12:15:15.115 D/PlayMatch: ilk kare: TestPrimary (faz=Playing)`)

### 5.3 Geri Tuşu ve Kararlılık

- Geri tuşuna basıldığında (`keyevent 4`) oynatıcıdan temiz bir şekilde çıkıldı.
- Son odak penceresi: `mCurrentFocus=Window{257f2b6 u0 com.ldmnq.launcher3/com.android.launcher3.Launcher}`
- **Torrent Motoru Durumu:** Release APK'da libtorrent başarıyla başlatılmış, eşlerle bağlantı kurulmuş ve POSIX Disk I/O üzerinden bloklar ExoPlayer'a aktarılmıştır. `UnsatisfiedLinkError`, JNI eksikliği veya R8 sınıf küçültme kaynaklı hiçbir hata oluşmamıştır.
- `FATAL` / `AndroidRuntime` hata sayısı: **0**.

- **Kanıt Dosyaları:**
  - `docs/dogrulama-124/test_c_playing.png`
  - `docs/dogrulama-124/test_c_before_back.png`
  - `docs/dogrulama-124/test_c_after_back.png`
  - `docs/dogrulama-124/kanit-test-c.txt`

---

## 6. Genel Değerlendirme

- Release APK (`dist/1.2.4/Play-1.2.4.apk`) harici intent çağrılarını (`http://`, `https://`, `magnet:`) eksiksiz karşılamaktadır.
- HTTP MP4 akışı, HLS Canlı akışı ve Big Buck Bunny torrent akışı 0 çökme ile akıcı şekilde oynatılmış, kumanda ileri/geri sarma ve Geri tuşu testlerinden tam başarıyla geçmiştir.
- **Genel Sonuç:** **GEÇTİ**
