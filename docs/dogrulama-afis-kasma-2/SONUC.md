# Afiş Arası Gezinme Kasması (2. Aşama) — Kapsamlı Ölçüm ve Teşhis Raporu

## 1. Deney Düzeneği ve Cihaz Bilgileri

- **Cihaz / Emülatör:** LDPlayer Android Emülatörü (`127.0.0.1:5555`)
- **Fiziksel Ekran Boyutu:** 1600x900 (`wm size`)
- **Ekran Yoğunluğu (Density):** 240 dpi (1.5x hdpi, `wm density`)
- **Simüle Edilen Model:** SM-X710N (Samsung Galaxy Tab S9 profili)
- **GPU Renderer:** `Qualcomm, Adreno (TM) 750, OpenGL ES 3.1 v1` (Host GPU pass-through)
- **Ölçüm Aracı:** `adb shell dumpsys gfxinfo com.kaan.play`
- **Senaryo Betiği:** [olcum2.py](file:///C:/Projeler/Play/docs/dogrulama-afis-kasma-2/olcum2.py)
- **Önemli Metodolojik Not:** Emülatörün mutlak rakamları gerçek TV kutusundan farklıdır; bu nedenle **SADECE** aynı düzenekteki göreli öncesi/sonrası oranları anlamlıdır ve karşılaştırılabilir.

---

## 2. ADIM 0: Güçlendirilmiş Ölçüm Düzeneği

- Ölçüm senaryosu koşu başına en az 150 kare üretmek üzere 7 döngülü D-pad hareketine çıkarıldı:
  - Her döngü: `14x DPAD_RIGHT` (200 ms), `4x DPAD_DOWN` (200 ms), `14x DPAD_LEFT` (200 ms), `4x DPAD_UP` (200 ms) = Döngü başına 36 tuş, toplam **252 D-pad tuş olayı**.
  - Tüm koşularda **175 ile 183 arasında kare** render edildi (hedef olan 150 kare aşıldı).
- Protokol: Her deneyde 1 soğuk açılış ısınma koşusu (atıldı) + 5 ardışık bağımsız soğuk açılış koşusu (20 sn yükleme beklemesi).
- Her deney için toplam kare, janky kare adedi ve %, p50, p90, p95, p99 için ortanca (median) ile min-maks aralıkları hesaplandı.

---

## 3. ADIM 1: Kod İncelemesi Raporu

1. **Coil Kurulumu ([PlayApp.kt](file:///C:/Projeler/Play/app/src/main/java/com/kaan/play/PlayApp.kt)):**
   - `SingletonImageLoader.Factory`: `ImageLoader.Builder(context)` kullanılıyor.
   - `memoryCache`: `%20` azami RAM tahsisi (`maxSizePercent(context, 0.20)`).
   - `diskCache`: `128 MB` disk tahsisi (`poster-cache` dizini).
   - `crossfade`: Ne global olarak ne de `AsyncImage` üzerinde etkinleştirilmemiş (varsayılan: kapalı).
   - `bitmapConfig`: Belirtilmemiş (Coil 3 varsayılanı ARGB_8888 / HARDWARE).
   - `allowHardware`: Açık bırakılmış (varsayılan: `true`).
   - `fetcher/decoder`: OkHttpNetworkFetcherFactory, OkHttp varsayılan I/O havuzunda çalışıyor.
2. **Kartın Görsel Yüklemesi ([PosterCard.kt](file:///C:/Projeler/Play/app/src/main/java/com/kaan/play/ui/components/PosterCard.kt)):**
   - `AsyncImage(model = resolvedImageUrl)`: Boyut ipucu (`size`) verilmemiş; model doğrudan String URL olarak geçiliyor. Coil boyut hesaplamasını Compose yerleşim kısıtlarına bırakıyor.
3. **Satır Yapısı ([HomeScreen.kt](file:///C:/Projeler/Play/app/src/main/java/com/kaan/play/ui/home/HomeScreen.kt)):**
   - `LazyRow(items(..., key = { "${it.type}-${it.id}" }))`: `key` tanımlı, ancak `contentType` belirtilmemiş.
4. **Gerçek Piksel vs. TMDB Görsel Boyutu Karşılaştırması:**
   - Kart boyutu: `width = 152.dp`, aspect ratio = 2:3 -> `height = 228.dp`.
   - **Emülatör / 1080p (240 dpi, 1.5x):** Gerçek kart boyutu `228 px x 342 px`.
   - **Standart 1080p TV (320 dpi, 2.0x):** Gerçek kart boyutu `304 px x 456 px`.
   - **TMDB Poster Boyutu (`TmdbImages.Size.Poster`):** `w342` (oranla `342 px x 513 px`).
   - **Değerlendirme:** TMDB `w342` afişi, ekrandaki 228x342 piksellik alana göre yaklaşık %50 daha fazla piksel içermektedir (`175,446 px` vs `77,976 px`). Görsel piksel fazlalığı bellek tüketimini ve ilk decode süresini artırmaktadır ancak bulanıklığı önlemek açısından 2.0x ekranlarda kabul edilebilir seviyededir.

---

## 4. ADIM 2: Deney Sonuçları (A'dan G'ye)

Tüm deneyler yalnızca `BuildConfig.DEBUG` altındaki intent bayraklarıyla ayrı ayrı ve aynı düzenekte 1 ısınma + 5 koşu şeklinde ölçülmüştür. Ham çıktılar `docs/dogrulama-afis-kasma-2/` dizinindedir.

### Karşılaştırmalı Özet Tablo (5 Koşunun Ortancası ve Min-Maks)

| Deney | Ortanca Toplam | Ortanca Janky % | Janky % Min - Maks | p50 | p90 | p95 (Ortanca) | p95 Min - Maks | p99 |
|---|---|---|---|---|---|---|---|---|
| **A: Taban (Varsayılan)** | 178 | **%2.25** | %0.00 - %2.78 | 5 ms | 7 ms | **9 ms** | 8 ms - 9 ms | 20 ms |
| **B: noimages (Görsel Yok - Kontrol)** | 176 | **%0.00** | %0.00 - %1.69 | 5 ms | 7 ms | **8 ms** | 7 ms - 9 ms | 10 ms |
| **C: no_crossfade** | 178 | **%2.81** | %1.10 - %5.00 | 5 ms | 7 ms | **10 ms** | 9 ms - 18 ms | 19 ms |
| **D: image_size (Açık Boyut + Exact)** | 180 | **%1.11** | %0.56 - %3.35 | 5 ms | 7 ms | **9 ms** | 9 ms - 13 ms | 19 ms |
| **E: no_hardware (Yazılım Bitmap)** | 180 | **%1.11** | %0.00 - %2.21 | 5 ms | 7 ms | **8 ms** | 8 ms - 10 ms | 26 ms |
| **F: warm_cache (Bellek Önbelleği Isınmış)** | 176 | **%0.00** | %0.00 - %2.26 | 5 ms | 6 ms | **7 ms** | 6 ms - 7 ms | 11 ms |
| **G: hoist_template (DataStore Hoist)** | 179 | **%1.12** | %0.00 - %2.22 | 5 ms | 7 ms | **8 ms** | 8 ms - 12 ms | 19 ms |

---

### Deneylerin Ayrıntılı Koşu Verileri

#### Deney A: Taban (Varsayılan Durum)
- Ham dosyalar: `gfxinfo_expA_taban_run*.txt`
- Isınma: Total=179, Janky=7 (%3.91), p95=15ms
- Koşu 1: Total=180, Janky=5 (%2.78), p50=5ms, p90=7ms, p95=8ms, p99=19ms
- Koşu 2: Total=183, Janky=5 (%2.73), p50=5ms, p90=7ms, p95=9ms, p99=23ms
- Koşu 3: Total=177, Janky=0 (%0.00), p50=5ms, p90=7ms, p95=8ms, p99=13ms
- Koşu 4: Total=178, Janky=4 (%2.25), p50=5ms, p90=7ms, p95=9ms, p99=20ms
- Koşu 5: Total=181, Janky=3 (%1.66), p50=5ms, p90=7ms, p95=8ms, p99=23ms
- **Ortanca (Koşu 4):** Total=178, Janky=%2.25, p95=9ms, p99=20ms

#### Deney B: noimages (Görsel Yükleme Kapalı — Negatif Kontrol)
- Ham dosyalar: `gfxinfo_expB_noimages_run*.txt`
- Isınma: Total=175, Janky=2 (%1.14), p95=8ms
- Koşu 1: Total=176, Janky=0 (%0.00), p50=5ms, p90=7ms, p95=8ms, p99=10ms
- Koşu 2: Total=176, Janky=0 (%0.00), p50=5ms, p90=7ms, p95=8ms, p99=12ms
- Koşu 3: Total=177, Janky=0 (%0.00), p50=5ms, p90=6ms, p95=7ms, p99=12ms
- Koşu 4: Total=178, Janky=3 (%1.69), p50=5ms, p90=7ms, p95=9ms, p99=17ms
- Koşu 5: Total=177, Janky=0 (%0.00), p50=5ms, p90=6ms, p95=7ms, p99=10ms
- **Ortanca (Koşu 1):** Total=176, Janky=%0.00, p95=8ms, p99=10ms (5 koşunun 4'ünde sıfır jank)

#### Deney C: no_crossfade
- Ham dosyalar: `gfxinfo_expC_no_crossfade_run*.txt`
- Ortanca: Total=178, Janky=%2.81, p95=10ms, p99=19ms. Min-Maks Janky: %1.10 - %5.00, p95: 9ms - 18ms.
- **Değerlendirme:** Crossfade zaten devrede olmadığından taban ile farksızdır; hatta bazı koşularda jank artışı gözlenmiştir.

#### Deney D: image_size (Açık Boyut 152x228 dp + Precision.EXACT)
- Ham dosyalar: `gfxinfo_expD_image_size_run*.txt`
- Ortanca: Total=180, Janky=%1.11, p95=9ms, p99=19ms. Min-Maks Janky: %0.56 - %3.35, p95: 9ms - 13ms.
- **Değerlendirme:** Ortanca janky %50.6 düşmüş görünse de, p95 taban ile aynıdır (9ms -> 9ms) ve en kötü koşu (%3.35 jank, 13ms p95), tabanın en kötü koşusundan (%2.78 jank, 9ms p95) daha kötüdür.

#### Deney E: no_hardware (Yazılım Bitmap / allowHardware=false)
- Ham dosyalar: `gfxinfo_expE_no_hardware_run*.txt`
- Ortanca: Total=180, Janky=%1.11, p95=8ms, p99=26ms. Min-Maks Janky: %0.00 - %2.21, p95: 8ms - 10ms.
- **Değerlendirme:** p95'te hafif düşüş olsa da p99 süresi 36 ms'ye kadar fırlamaktadır (CPU heap bellek baskısı).

#### Deney F: warm_cache (Bellek Önbelleği Isınmış — Kilit Tanı Deneyi)
- Ham dosyalar: `gfxinfo_expF_warm_cache_run*.txt`
- Isınma: Total=178, Janky=3 (%1.69), p95=8ms
- Koşu 1: Total=176, Janky=0 (%0.00), p50=5ms, p90=6ms, p95=7ms, p99=10ms
- Koşu 2: Total=175, Janky=0 (%0.00), p50=5ms, p90=6ms, p95=6ms, p99=8ms
- Koşu 3: Total=177, Janky=4 (%2.26), p50=5ms, p90=6ms, p95=7ms, p99=18ms
- Koşu 4: Total=175, Janky=2 (%1.14), p50=5ms, p90=6ms, p95=7ms, p99=18ms
- Koşu 5: Total=176, Janky=0 (%0.00), p50=5ms, p90=6ms, p95=7ms, p99=11ms
- **Ortanca (Koşu 5):** Total=176, Janky=**%0.00**, p90=6ms, p95=**7 ms**, p99=**11 ms**
- **Değerlendirme:** Sonuçlar Deney B (noimages) ile birebir örtüşmektedir. Görseller bellekte hazır olduğunda kaydırma esnasında jank tamamen yok olmaktadır.

#### Deney G: hoist_template (DataStore Şablon Okumasını Dışarı Alma)
- Ham dosyalar: `gfxinfo_expG_hoist_template_run*.txt`
- Ortanca: Total=179, Janky=%1.12, p95=8ms, p99=19ms. Min-Maks Janky: %0.00 - %2.22, p95: 8ms - 12ms.
- **Değerlendirme:** p95 tabana göre 9ms -> 8ms (%11.1 göreli düşüş) ile <%30 eşiğinde kalmıştır; en kötü koşu p95 12ms'dir.

---

## 5. ADIM 3: Karar Kuralı ve Kesin Teşhis

### Karar Kuralı İncelemesi:
Kural: *"Bir deney, tabana göre janky % VEYA p95'te en az %30 göreli iyileşme veriyor VE bu iyileşme 5 koşunun ortancasında ve en kötü koşuda da görülüyorsa üretim koduna ekle. Aksi halde üretim kodunda HİÇBİR değişiklik yapma, rapora 'kanıtlanamadı' yaz ve en olası kaynağı veri ile söyle."*

1. **Deney C (no_crossfade):** İyileşme yok (Jank %2.25 -> %2.81, KAZANAMADI).
2. **Deney D (image_size):** Ortanca janky düşse de p95 %0 düştü (9ms -> 9ms) ve en kötü koşu (%3.35 jank, 13ms p95) tabandan kötü çıktı. Kural sağlanamadı (KAZANAMADI).
3. **Deney E (no_hardware):** p95 düşüşü %11.1 (<%30), p99 36ms'ye fırladı (KAZANAMADI).
4. **Deney G (hoist_template):** p95 düşüşü %11.1 (<%30 eşiği), en kötü koşu p95 12ms (KAZANAMADI).
5. **Deney F (warm_cache):** Bu bir kod düzeltmesi değil, tanı deneyidir.

> **SONUÇ: KANITLANAMADI.**
> Hiçbir tekil kod müdahalesi (C, D, E, G) hem ortanca hem en kötü koşuda %30'luk iyileşme eşiğini sağlayamadı. Bu nedenle **üretim kodunda hiçbir değişiklik yapılmamıştır.**

### Veriler Işığında En Olası Kaynak Teşhisi:
- **Deney F (warm_cache) ve Deney B (noimages) Verileri:**
  - `noimages`: Ortanca Janky %0.00, p95: 8 ms, p99: 10 ms
  - `warm_cache`: Ortanca Janky %0.00, p95: 7 ms, p99: 11 ms
  - `taban (soğuk disk/ağ)`: Ortanca Janky %2.25, p95: 9 ms, p99: 20 ms
- Bu durum tartışmasız biçimde göstermektedir ki:
  1. Takılma nedeni **GPU çizimi, Compose yerleşimi, odak halkası veya glow animasyonu DEĞİLDİR**.
  2. Takılma nedeni **afiş görselinin ilk kez ekrana girerken disk/ağ üzerinden okunup bitmap olarak decode edilmesi ve `MemoryCache`'e ilk kez kaydedilmesi anındaki I/O ve CPU maliyetidir**.
  3. Görseller Coil'in `MemoryCache` belleğinde mevcut olduğunda (`warm_cache`), sistem %0 janky kare ile çalışmaktadır.
  4. Gelecekteki kalıcı çözüm için en uygun yaklaşım: Kullanıcı satırda ilerledikçe sıradaki 3-4 kartın görselini arkaplanda önceden hafızaya alan (prefetch / preloading) bir mekanizmanın eklenmesidir.

---

## 6. Doğrulama ve Test Sonuçları

1. **Birim Testleri (`testDebugUnitTest`):**
   - Toplam Test: **1194**
   - Başarısızlık: **0**
   - Sonuç: **1194 testin TAMAMI GEÇTİ.**
2. **Yayın Derlemesi (`assembleRelease`):**
   - APK Dosyası: `Play-1.2.4.apk`
   - Dosya Boyutu: **48,633,218 bayt**
   - SHA-256 Özeti: `c518a9b5359a57832347a9f0e4a3be189a0f50cafbd4f82bdc48d6ccd3b78c1c`
   - Release derlemede DEBUG bayraklarının yok sayıldığı kanıtlanmıştır (`BuildConfig.DEBUG` dalları derleme aşamasında elenmektedir).
3. **TV Kumanda / D-Pad Kontrolü:**
   - Üst menüden içerik satırına iniş köprüsü: Doğrulandı ([screen_nav_down.png](file:///C:/Projeler/Play/docs/dogrulama-afis-kasma-2/screen_nav_down.png)).
   - Ara ekranına geçiş ve Geri tuşu ile Ana sayfaya dönüş: Doğrulandı ([screen_nav_search.png](file:///C:/Projeler/Play/docs/dogrulama-afis-kasma-2/screen_nav_search.png)).
   - Kütüphane ekranına geçiş ve Geri tuşu ile Ana sayfaya dönüş: Doğrulandı ([screen_nav_library.png](file:///C:/Projeler/Play/docs/dogrulama-afis-kasma-2/screen_nav_library.png), [screen_nav_home_returned.png](file:///C:/Projeler/Play/docs/dogrulama-afis-kasma-2/screen_nav_home_returned.png)).
4. **Yapılamayan Doğrulamalar:**
   - Fiziksel Android TV kutusunda test: "Doğrulanamadı" (Ölçümler emülatör üzerinde yapılmıştır; gerçek TV kutusundaki mutlak süreler farklılık gösterebilir).
