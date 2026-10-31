# Son Derleme Regresyon Test Sonuçları

**Tarih / Saat:** 2026-10-31 03:15:00 (TSİ)  
**Dal:** `duzeltme/oynatici-saglamlik`  
**Son Çekilen Commit:** `a529706` (docs(toplu): DiziBOX tani kopyasi (ana sayfa/eslesme logu))  
**Cihaz:** Yalnızca `emulator-5558` (PlayNeon-130; index 0 ve `emulator-5556`'ya DOKUNULMADI)  
**Kural Uyumu:** KOD DEĞİŞTİRİLMEDİ (0 satır `.kt` değişikliği), uygulama verisi silinmedi / eklentiler korundu.

---

## 1. Derleme ve Unit Test Sonuçları

* **Komut:** `.\gradlew testDebugUnitTest assembleDebug`
* **Sonuç:** `BUILD SUCCESSFUL in 1m 15s`
* **Test İstatistikleri:**
  * Toplam Test Suite: **143**
  * Toplam Test Sayısı: **1116**
  * Başarıyla Geçen: **1114**
  * Başarısız / Kalan: **0**
  * Hata: **0**
  * Atlanan (Skipped): **2**
* **Kurulum:** `app-debug.apk` (71,329,178 bayt) derlendi ve `adb -s emulator-5558 install -r` ile başarıyla kuruldu.

---

## 2. Regresyon Ölçüm Tablosu (5 İçerik)

> Her içerik en az 60 saniye boyunca kesintisiz izlenmiş, ardından Geri tuşuna basılmış ve `logcat -b crash` kontrol edilmiştir.

| No | İçerik | Tür | Sağlayıcı / Kaynak | İstek → İlk Kare (sn) | 60 sn İzleme & Takılma | Tampon Süresi | Geri Tuşu | Çökme (Crash Log) |
|---|---|---|---|---|---|---|---|---|
| 1 | **Joker (2019)** | MOVIE | HDFilmCehennemi (`srv12.cdnimages1157.shop`) | **2.59 sn** | **0 takılma (0.0 sn)** | 20.6 sn | Sorunsuz | **YOK (0)** |
| 2 | **Örümcek-Adam: Yepyeni Bir Gün** | MOVIE | HDFilmCehennemi (`srv9.cdnimages2463.cyou`) | **2.28 sn** | **0 takılma (0.0 sn)** | 30.4 sn | Sorunsuz | **YOK (0)** |
| 3 | **Criminal Minds (S1E1)** | TV | DiziBOX TV (`dbx.molystream.org`) | **4.25 sn** | **0 takılma (0.0 sn)** | 15.6 sn | Sorunsuz | **YOK (0)** |
| 4 | **MobLand (S2 B7)** | TV | HDFilmCehennemi (`s231.rapidrame.com`) | **3.39 sn** | **0 takılma (0.0 sn)** | 33.5 sn | Sorunsuz | **YOK (0)** |
| 5 | **Canlı Kanal (TRT 1 HD)** | LIVE | Anthology — Canlı TV (`trt.daioncdn.net`) | **1.01 sn** | **0 takılma (0.0 sn)** | 15.5 sn | Sorunsuz | **YOK (0)** |

---

## 3. Ayrıntılı Ölçüm ve Doğrulama Kayıtları

### 1) Joker (2019)
- **Hedef:** TMDb `475557`, IMDb `tt7286456`
- **İstek Saati:** `03:13:02.857`
- **Akış Listeye Giriş:** `03:13:04.397` (HDFilmCehennemi, 1 akış)
- **İlk Kare Saati:** `03:13:05.447` (**2.59 sn**)
- **Süre Kontrolü:** `süre=121.8 dk beklenen=122 dk tür=Movie -> Ok`
- **60 sn Oynatma:** 74 saniye oynatıldı (`pos=74554ms`), `sayı=0 toplam=0.0 sn oynama=1 dk`, tampon: 20.6 sn
- **Geri Tuşu & Çökme:** Ana ekrana sorunsuz dönüldü, `logcat -b crash` temiz.

### 2) Örümcek-Adam: Yepyeni Bir Gün
- **Hedef:** TMDb `969681`, IMDb `tt23832104`
- **İstek Saati:** `03:05:20.106`
- **Akış Listeye Giriş:** `03:05:21.480` (HDFilmCehennemi, 1 akış)
- **İlk Kare Saati:** `03:05:22.382` (**2.28 sn**)
- **Süre Kontrolü:** `süre=145.0 dk beklenen=145 dk tür=Movie -> Ok`
- **60 sn Oynatma:** `sayı=0 toplam=0.0 sn oynama=1 dk`, tampon: 30.4 sn
- **Geri Tuşu & Çökme:** Ana ekrana sorunsuz dönüldü, `logcat -b crash` temiz.

### 3) Criminal Minds S1E1
- **Hedef:** TMDb `4057`, IMDb `tt0452046`, Sezon 1 Bölüm 1
- **İstek Saati:** `03:06:56.718`
- **Akış Listeye Giriş:** `03:07:00.077` (DiziBOX TV, 1 akış)
- **İlk Kare Saati:** `03:07:00.972` (**4.25 sn**)
- **Süre Kontrolü:** `süre=41.7 dk beklenen=40 dk tür=Tv -> Ok`
- **60 sn Oynatma:** `sayı=0 toplam=0.0 sn oynama=1 dk`, tampon: 15.6 sn
- **Geri Tuşu & Çökme:** Ana ekrana sorunsuz dönüldü, `logcat -b crash` temiz.

### 4) MobLand S2 B7
- **Hedef:** TMDb `247718`, IMDb `tt14940562`, Sezon 2 Bölüm 7
- **İstek Saati:** `03:08:33.184`
- **Akış Listeye Giriş:** `03:08:34.627` (HDFilmCehennemi, 1 akış)
- **İlk Kare Saati:** `03:08:36.573` (**3.39 sn**)
- **rplayer & CDN Kontrolü:** rapidrame master.m3u8 başarıyla çözüldü, referer başlıkları uygulandı.
- **60 sn Oynatma:** `sayı=0 toplam=0.0 sn oynama=1 dk`, tampon: 33.5 sn
- **Geri Tuşu & Çökme:** Ana ekrana sorunsuz dönüldü, `logcat -b crash` temiz.

### 5) Canlı Kanal (TRT 1 HD)
- **Hedef:** Canlı TV Menüsü > TRT 1 HD (`tv:TRT1.tr`)
- **İstek Saati:** `03:10:12.960`
- **İlk Kare Saati:** `03:10:13.976` (**1.01 sn**)
- **Kaynak:** `Anthology — Canlı TV (trt.daioncdn.net)`
- **60 sn Oynatma:** `sayı=0 toplam=0.0 sn oynama=1 dk`, bantTahmini: 91.36 Mbps, tampon: 15.5 sn
- **Geri Tuşu & Çökme:** Canlı TV listesine sorunsuz dönüldü, `logcat -b crash` temiz.

---

## 4. Kanıt Dosyaları

Tüm ölçümler, hassas verilerden arındırılmış günlükler ve ekran görüntüleri `docs/dogrulama-toplu/` altında kaydedilmiştir:
* Günlükler: `01-joker.log`, `01-joker-filtered.log`, `02-spiderman.log`, `02-spiderman-filtered.log`, `03-criminal-minds.log`, `03-criminal-minds-filtered.log`, `04-mobland.log`, `04-mobland-filtered.log`, `05-canli-trt1.log`, `05-canli-trt1-filtered.log`.
* Ekran Görüntüleri: Her test için 20sn, 60sn, kontroller ve geri tuşu ekran görüntüleri (`*-60sn.png`, `*-60sn-controls.png`, `*-geri.png`).
