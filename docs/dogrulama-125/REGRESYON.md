# Play 1.2.5 Regresyon Doğrulama Raporu

**Tarih:** 2026-11-04  
**Sürüm:** 1.2.5 (`versionCode=32`)  
**APK:** `dist/1.2.5/Play-1.2.5.apk`  
**APK SHA-256:** `f7a0a9fac8f7c71f64abbe467a1735d5d7e8e0e74c05f1747688c99c97ded0ca`  
**Test Cihazı:** LDPlayer Emulator (SM-X710N, Android 9, `127.0.0.1:5555`)  

---

## 1. Cihaz ve Kurulu Sürüm Kanıtı

```text
$ adb -s 127.0.0.1:5555 shell dumpsys package com.kaan.play | grep -E "versionCode|versionName"
    versionCode=32 minSdk=24 targetSdk=37
    versionName=1.2.5
```

---

## 2. Kurulu ve Açık Video Eklentileri

Uygulama içi `Ayarlar > Eklentiler` ve yerel `play.db` veritabanı sorgusuyla tespit edilen aktif eklentiler:

### A. Nuvio Eklentileri
1. **Anthology — Canlı TV** (Tür: Canlı TV / IPTV, `enabled=1`)
2. **Torrentio** (Tür: Torrent Stream, `enabled=1`)
3. **AltyazıDB** (Tür: Alt yazı, `enabled=1`)
4. **AltyazıTR** (Tür: Alt yazı, `enabled=1`)
5. **Play Özel Kataloglar** (Tür: Katalog, `enabled=1`)
6. **İzlemeli.com Katalog** (Tür: Katalog, `enabled=1`)

### B. Eklenti Havuzları (Plugin Repositories)
1. **izlelan** (`enabled=1`)
2. **DiziBOX TV for Nuvio** (`enabled=1`)
3. **HDFilmCehennemi for Nuvio** (`enabled=1`)

### C. Video Akış Eklentileri (Plugins - Tümü Açık / enabled=1)
- **HDFilmCehennemi** (Film, Dizi)
- **DiziBOX TV** (Dizi)
- **izlelan Video Eklentileri (39 adet):** Ace, Beckman, Bogard, Clover, Doflamingo, Dragon, Enel, Fujitora, Gaban, Garp, Gorosei, Hiriluk, Imu, Jinbe, JoyBoy, Kalgara, Katakuri, Kidd, Kizaru, Kuzan, Lili, Mihawk, Noland, Oden, Rayleigh, Roger, Ryuma, Sabo, Sakazuki, Saul, Sengoku, Shanks, Shiki, Smoker, Teach, Toki, TurkDizi, Urouge, Xebec.

*Kanıt Ekran Görüntüleri:*
- `20_eklentiler_listesi.png` (Eklenti listesi sayfası 1)
- `21_eklentiler_listesi_2.png` (Eklenti listesi sayfası 2)

---

## 3. Regresyon Oynatma Testleri

Tüm testler gerçek eklenti kaynakları üzerinden yapılmıştır (mock, test videosu veya BigBuckBunny kullanılmamıştır).

### Test 1: Örümcek-Adam (Film)
- **İçerik:** Örümcek Adam (2002 · Sam Raimi)
- **Kullanılan Eklenti:** `Urouge` (1920×1040, 1.8 GB akış)
- **İlk Kare Gelme Süresi:** 21.66 sn
- **10. Saniye Ekran Görüntüsü:** `24_spiderman_10s.png` (Zaman çizgisi: `2:04 / 2:01:12`)
- **60. Saniye Ekran Görüntüsü:** `25_spiderman_60s.png` (Zaman çizgisi: `2:54 / 2:01:12`)
- **Oynatma Durumu:** Zaman çizgisi 50 sn kesintisiz ilerledi, video akıcı şekilde oynatıldı.
- **Çıkış Davranışı:** Kumanda Geri tuşu (keyevent 4) ile oynatıcıdan sorunsuz çıkıldı, uygulama çökmeden detay sayfasında açık kaldı.

### Test 2: Joker (Film)
- **İçerik:** Joker (2019 · Todd Phillips)
- **Kullanılan Eklenti:** `Dragon` (1920×1036 akış, Türkçe gömülü altyazı)
- **İlk Kare Gelme Süresi:** 7.44 sn
- **10. Saniye Ekran Görüntüsü:** `26_joker_10s.png` (Zaman çizgisi: `0:11 / 2:01:49`, Warner Bros introsu)
- **60. Saniye Ekran Görüntüsü:** `27_joker_60s.png` (Zaman çizgisi: `1:01 / 2:01:49`, Arthur Fleck sahnesi)
- **Oynatma Durumu:** Zaman çizgisi 50 sn kesintisiz ilerledi, video ve altyazı senkron şekilde oynatıldı.
- **Çıkış Davranışı:** Kumanda Geri tuşu (keyevent 4) ile oynatıcıdan çıkıldı, uygulama detay sayfasında açık kaldı.

### Test 3: Criminal Minds (Dizi Bölümü)
- **İçerik:** Criminal Minds (1. Sezon 1. Bölüm)
- **Kullanılan Eklenti:** `Ace` (1920×1080 akış)
- **İlk Kare Gelme Süresi:** 4.45 sn
- **10. Saniye Ekran Görüntüsü:** `28_criminal_minds_10s.png` (Zaman çizgisi: `0:09 / 41:33`)
- **60. Saniye Ekran Görüntüsü:** `29_criminal_minds_60s.png` (Zaman çizgisi: `0:59 / 41:33`)
- **Oynatma Durumu:** Zaman çizgisi 50 sn kesintisiz ilerledi, ses ve görüntü sorunsuz oynatıldı.
- **Çıkış Davranışı:** Kumanda Geri tuşu (keyevent 4) ile oynatıcıdan çıkıldı, uygulama açık kaldı.

### Test 4: Canlı Kanal (Canlı TV)
- **İçerik:** TRT 1 HD ("Mehmed: Fetihler Sultanı" yayını)
- **Kullanılan Eklenti:** `Anthology — Canlı TV` (1920×1080, `trt.daioncdn.net` HLS canlı akış)
- **İlk Kare Gelme Süresi:** 1.22 sn
- **10. Saniye Ekran Görüntüsü:** `22_canli_10s.png` (HLS canlı tampon penceresi `0:25 / 0:48`)
- **60. Saniye Ekran Görüntüsü:** `23_canli_60s.png` (Canlı yayın kesintisiz devam ediyor, sahne akışı ilerledi)
- **Oynatma Durumu:** 60 sn kesintisiz, donma/takılma olmadan canlı yayın sürdürüldü.
- **Çıkış Davranışı:** Kumanda Geri tuşu (keyevent 4) ile TV arayüzüne geri dönüldü, uygulama açık kaldı.

---

## 4. İzlemeye Devam Et Satırı Kontrolü

Oynatma testlerinden sonra ana sayfaya dönülerek "İzlemeye devam et" satırı denetlendi:
- **Ekran Görüntüsü:** `30_devam_et_yeni_kayitlar.png`
- **Sonuç:** Oynatılan üç içerik de en son izlenenden başlayarak sırayla satırda listelendi:
  1. `CRIMINAL MINDS` (S1 B1 ilerleme çubuğuyla)
  2. `JOKER` (İlerleme çubuğuyla)
  3. `SPIDER-MAN` (İlerleme çubuğuyla)

---

## 5. Logcat Çökme / ANR Taraması

`adb logcat -d` çıktısı taranarak `com.kaan.play` paketi için kaza, kritik istisna ve ANR kontrolü yapılmıştır:
- `FATAL EXCEPTION`: 0 adet (Hata yok)
- `AndroidRuntime`: 0 adet (Çökme yok)
- `ANR in com.kaan.play`: 0 adet (Donma / kilitlenme yok)

---

## 6. Özet Sonuç Tablosu

| Test Senaryosu | İçerik | Sağlayıcı / Eklenti | İlk Kare | 10 sn Kanıtı | 60 sn Kanıtı | Oynatıcıdan Çıkış |
|---|---|---|---|---|---|---|
| **Film 1** | Örümcek Adam (2002) | `Urouge` (1080p) | 21.66 sn | `24_spiderman_10s.png` | `25_spiderman_60s.png` | Başarılı (Açık kaldı) |
| **Film 2** | Joker (2019) | `Dragon` (1080p) | 7.44 sn | `26_joker_10s.png` | `27_joker_60s.png` | Başarılı (Açık kaldı) |
| **Dizi Bölümü** | Criminal Minds S01E01 | `Ace` (1080p) | 4.45 sn | `28_criminal_minds_10s.png` | `29_criminal_minds_60s.png` | Başarılı (Açık kaldı) |
| **Canlı Kanal** | TRT 1 HD | `Anthology — Canlı TV` | 1.22 sn | `22_canli_10s.png` | `23_canli_60s.png` | Başarılı (Açık kaldı) |
