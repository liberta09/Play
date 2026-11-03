# Doğrulama Raporu: Afiş Satırlarında Önceden Belleğe Alma (Prefetch)

**Tarih:** 2026-11-03  
**Dal:** `feat/afis-onbellek`  
**Karar:** **KANITLANAMADI** (Ana koda eklenmedi, Adım 3 revert edildi)

---

## 1. Cihaz ve Ortam Bilgisi

Ölçümler sabit LDPlayer Android emülatör düzeneğinde gerçekleştirilmiştir:

| Parametre | Değer |
|---|---|
| Cihaz Modeli | `SM-X710N` |
| Ekran Çözünürlüğü (`wm size`) | `1600x900` |
| Ekran Yoğunluğu (`wm density`) | `240` (hdpi / 1.5x) |
| GLES Renderer | `Qualcomm, Adreno (TM) 750, OpenGL ES 3.1 v1` |
| Android Sürümü | Android 12 (API 32) |

---

## 2. Deney Düzeni ve Ölçüm Senaryosu

- **Senaryo (`olcum_prefetch.py`):**
  - Uygulama soğuk açılır (`am force-stop` ardından `am start`).
  - Ana sayfanın yüklenmesi için sabit 20 saniye beklenir.
  - İlk karta odaklanılır (`DPAD_DOWN`).
  - Ölçüm öncesi `dumpsys gfxinfo com.kaan.play reset` ve `RESET_PREFETCH_METRICS` broadcast'i ile sayaçlar sıfırlanır.
  - Genişletilmiş D-pad döngüsü (7 tekrar): 14 kez `DPAD_RIGHT` (200 ms aralık), 4 kez `DPAD_DOWN` (200 ms aralık), 14 kez `DPAD_LEFT` (200 ms aralık), 4 kez `DPAD_UP` (200 ms aralık) yürütülür (koşu başına 240+ kare).
  - Senaryo bitiminde `dumpsys gfxinfo`, `dumpsys meminfo` (PSS) ve Coil bellek isabet sayaçları toplanır.
- **Koşullar:**
  - **Durum A (Prefetch Açık / Varsayılan):** Yatayda ±4 kart ve dikeyde alt satırın ilk 6 kartı için arka planda Coil `imageLoader.execute` ile prefetch devrede.
  - **Durum B (Prefetch Kapalı):** `--ez play_flag_no_prefetch true` ile prefetch tamamen devre dışı.
- **Tekrarlar:** 1 ısınma koşusu (rapora dahil edilmez, atılır) + 5 ardışık ölçüm koşusu. Raporlanan değerler 5 koşunun ortancasıdır (min ve maks parantez içinde verilmiştir).

---

## 3. Ölçüm Sonuçları Karşılaştırma Tablosu

| Metrik | Durum A (Prefetch Açık) | Durum B (Prefetch Kapalı) | Fark / Değişim |
|---|---|---|---|
| **Toplam Kare Sayısı** | **244** (242 – 254) | **263** (247 – 264) | -19 kare |
| **Janky Frames % (Ortanca)** | **%2.46** (%1.65 – %4.72) | **%3.04** (%2.30 – %4.05) | **-%0.58** |
| **p50 Kare Süresi** | **5 ms** (5 – 5 ms) | **5 ms** (5 – 5 ms) | 0 ms |
| **p90 Kare Süresi** | **5 ms** (5 – 6 ms) | **6 ms** (6 – 6 ms) | -1 ms |
| **p95 Kare Süresi (Ortanca)** | **6 ms** (6 – 16 ms) | **11 ms** (8 – 12 ms) | **-5 ms** |
| **p99 Kare Süresi** | **34 ms** (34 – 40 ms) | **38 ms** (21 – 38 ms) | **-4 ms** |
| **PSS RAM Başlangıç** | **170.6 MB** (163.8 – 172.8 MB) | **149.7 MB** (146.6 – 152.6 MB) | +20.9 MB |
| **PSS RAM Bitiş** | **173.5 MB** (166.2 – 182.1 MB) | **154.0 MB** (153.1 – 161.9 MB) | +19.5 MB |
| **PSS RAM Değişimi %** | **+%1.7** (-%2.6 – +%11.1) | **+%2.9** (+%0.3 – +%7.2) | -%1.2 |
| **Bellek Önbellek İsabeti %** | **%4.0** (%2.0 – %11.0) | **%20.0** (%12.0 – %20.0) | **-%16.0** |

---

## 4. Karar Kuralı Değerlendirmesi

Kullanıcı gereksiniminde tanımlanan 3 karar kriteri:

1. **İsabet Oranı B'ye Göre En Az %80 Artmalı:**  
   - **KALDI (kanıtlanamadı)**  
   - **Sayısal Gerekçe:** Durum A'da ortanca bellek önbellek isabet oranı **%4.0** iken, Durum B'de **%20.0** olarak ölçülmüştür (fark: **-%16.0**). Beklenen $\ge +\%80$ artış sağlanamamış, aksine agresif prefetch istekleri Coil'in %20'lik bellek önbellek havuzunda hızlı yer değiştirmeye (cache thrashing / eviction) neden olarak isabet oranını düşürmüştür.
2. **Janky ve p95 Süreleri B'den Kötü Olmamalı (±%5 kabul):**  
   - **GEÇTİ**  
   - **Sayısal Gerekçe:** Janky kare oranı %2.46 vs %3.04 (-%0.58 iyileşme), p95 süresi 6 ms vs 11 ms (-5 ms iyileşme), p99 süresi 34 ms vs 38 ms (-4 ms iyileşme).
3. **PSS RAM Artışı En Çok %25 Olmalı:**  
   - **GEÇTİ**  
   - **Sayısal Gerekçe:** Durum A'da PSS RAM değişimi **+%1.7** olmuştur ($<\%25$).

### Sonuç ve Uygulanan Kural
Karar kuralı gereği:
> *"Karşılanamazsa ana koda ekleme, rapora 'kanıtlanamadı' yaz."*  
> *"Karar kuralı geçemezse 3. adımı geri al (revert commit), 4. commit rapora 'kanıtlanamadı' yazarak tamamlar."*

İsabet oranı kriteri karşılanamadığı için özellik **"kanıtlanamadı"** olarak sonuçlandırılmış; Adım 3 (HomeScreen satırlarına prefetch bağlama ve debug broadcast alıcısı) `e17d0e1` commit'i ile geri alınmış (revert) ve ana kod temiz bırakılmıştır.

---

## 5. TV Kutusu Uyarısı

> **ÖNEMLİ UYARI:**  
> Emülatör ölçümü gerçeği yansıtmaz, emülatörde GPU ve bellek sanallaştırılmıştır. Ancak AYNI düzenekte yapılan A/B karşılaştırmasında bellek önbellek isabet oranının artmadığı, aksine önbellek baskısı nedeniyle düştüğü tespit edilmiştir. Özelliğin performansı artırdığı **kanıtlanamadı**, kullanıcı TV'de denemeli veya farklı bir önbellek mimarisi tasarlanmalıdır.

---

## 6. Release APK ve Debug Flag İncelemesi

- **APK Dosyası:** `app/build/outputs/apk/release/Play-1.2.4.apk`
- **Boyut:** `48,639,042 bytes` (46.39 MB)
- **SHA-256 Özeti:** `c6536be5deb5b1b92de7c0a8c361ee6e5e8c886101c99b526d14b12e03bf8f58`

### Debug Flag'inin Release Derlemede Elendiğinin Kanıtı
1. `PosterPrefetchFlags.isPrefetchEnabled` getter'ı `!BuildConfig.DEBUG || !noPrefetch` tanımına sahiptir. Release derlemesinde `BuildConfig.DEBUG` sabiti `false` olduğundan ifade `true || ...` sabitine indirgenir ve R8 optimizasyonu ile flag kontrolleri derleme aşamasında elenir.
2. `MainActivity.kt` içerisindeki intent extra okuma ve `RESET_PREFETCH_METRICS` / `DUMP_PREFETCH_METRICS` broadcast alıcısı `if (BuildConfig.DEBUG)` bloğu içine alınmıştır. Release derlemesinde bu bloklar R8 tarafından dead code elimination ile bytecode'dan tamamen temizlenir.

---

## 7. Görsel Kanıtlar (Ekran Görüntüleri)

Uygulamanın gezinme, satır kaydırma ve sekme geçişlerinde bozulma olmadığını kanıtlayan ekran görüntüleri bu dizine kaydedilmiştir:

1. **Ana Sayfa Kaydırma:** `docs/dogrulama-afis-onbellek/ss1_ana_sayfa_kaydirma.png`  
   - Ana sayfa dikey kaydırmada kart diziliminin ve görsel yüklemelerinin düzgün çalıştığını doğrular.
2. **Yatay D-pad Gezinmesi:** `docs/dogrulama-afis-onbellek/ss2_yatay_gezinme.png`  
   - Satır içinde kartlar arası sağa/sola odak geçişlerinde odak halkası ve afişlerin stabil olduğunu doğrular.
3. **Aşağı Satıra Geçiş:** `docs/dogrulama-afis-onbellek/ss3_asagi_satira_gecis.png`  
   - D-pad aşağı tuşu ile sonraki satıra geçişte odak belleğinin ve görsel render'ın bozulmadığını gösterir.
4. **Kütüphane / Sekme Geçişi:** `docs/dogrulama-afis-onbellek/ss4_kutuphane_gecis.png`  
   - Ara ve Kütüphane ekranlarına geçişlerde hiçbir regresyon olmadığını doğrular.
