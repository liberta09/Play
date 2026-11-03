# Afiş Arası Gezinme Kasması Doğrulama Raporu

## 1. Özet ve Düzeneğin Tanımı

- **Amaç:** TV kumandasıyla Ana sayfa'da afişler arasında gezinirken (satır içi sağ-sol, satırlar arası aşağı-yukarı) odakta yaşanan hafif takılmanın nedenini ölçerek tespit etmek ve doğrulamak.
- **Düzeneğin Detayları:**
  - Cihaz: LDPlayer Android Emülatörü (`127.0.0.1:5555`)
  - Derleme türü: Debug APK (`installDebug`), Release derleme doğrulaması (`assembleRelease`)
  - Ölçüm aracı: `adb shell dumpsys gfxinfo com.kaan.play`
  - Senaryo betiği: `docs/dogrulama-afis-kasma/olcum.py`
  - **Önemli Not:** Emülatörün mutlak rakamları gerçek TV kutusundan farklıdır; bu yüzden **SADECE** aynı düzenekte yapılan göreli öncesi/sonrası karşılaştırmaları anlamlıdır.

### Senaryo Akışı
1. Uygulama soğuk açılır (`am force-stop` ardından `am start`).
2. Ana sayfanın yüklenmesi için sabit 20 saniye beklenir.
3. İlk kart satırına odaklanılır (`DPAD_DOWN`).
4. `dumpsys gfxinfo com.kaan.play reset` ile kare istatistikleri sıfırlanır.
5. Sabit ve tekrarlanabilir D-pad hareketi icra edilir:
   - 12 kez `DPAD_RIGHT` (aralarda 400 ms)
   - 6 kez `DPAD_DOWN` (aralarda 400 ms)
   - 12 kez `DPAD_LEFT` (aralarda 400 ms)
   - 6 kez `DPAD_UP` (aralarda 400 ms)
6. `dumpsys gfxinfo com.kaan.play` çıktısı alınarak ayrıştırılır ve ham metin kaydedilir.
7. İlk koşu ısınma kabul edilerek atılır, ardından 3 resmi koşu yapılır ve ortanca (median) koşu seçilir.

---

## 2. ADIM 1: Temel Ölçüm (Baseline)

- **Koşul:** `no_glow=False`, `no_images=False` (varsayılan durum).
- **Ham Çıktılar:**
  - Isınma: `docs/dogrulama-afis-kasma/gfxinfo_adim1_baseline_warmup.txt`
  - Koşu 1: `docs/dogrulama-afis-kasma/gfxinfo_adim1_baseline_run1.txt`
  - Koşu 2: `docs/dogrulama-afis-kasma/gfxinfo_adim1_baseline_run2.txt`
  - Koşu 3: `docs/dogrulama-afis-kasma/gfxinfo_adim1_baseline_run3.txt`

| Koşu | Toplam Kare | Janky Kare | Janky % | p50 (ms) | p90 (ms) | p95 (ms) | p99 (ms) |
|---|---|---|---|---|---|---|---|
| Isınma (atıldı) | 42 | 2 | 4.76% | - | - | 15 | - |
| Koşu 1 | 44 | 5 | 11.36% | 6 | 16 | 19 | 77 |
| Koşu 2 | 43 | 8 | 18.60% | 7 | 18 | 23 | 69 |
| Koşu 3 | 42 | 3 | 7.14% | 6 | 16 | 18 | 81 |
| **Ortanca (Koşu 1)** | **44** | **5** | **11.36%** | **6** | **16** | **19** | **77** |

---

## 3. ADIM 2: Neden-Sonuç Deneyi (Odak Animasyonu / Glow)

- **Şüphe:** `tvFocusableScaleGlow` animasyonunun kompozisyon aşamasında okunması ve border modifier'ının dinamik eklenip çıkarılması.
- **Yöntem:** Yalnızca `BuildConfig.DEBUG` altında çalışan `--ez play_flag_no_glow true` bayrağı eklendi. Bayrak açıkken odak ölçekleme (scale 1.15) ve parlama (glow) iptal edildi; yalnızca statik odak halkası korundu.
- **Ham Çıktılar:**
  - Isınma: `docs/dogrulama-afis-kasma/gfxinfo_adim2_noglow_warmup.txt`
  - Koşu 1: `docs/dogrulama-afis-kasma/gfxinfo_adim2_noglow_run1.txt`
  - Koşu 2: `docs/dogrulama-afis-kasma/gfxinfo_adim2_noglow_run2.txt`
  - Koşu 3: `docs/dogrulama-afis-kasma/gfxinfo_adim2_noglow_run3.txt`

| Koşu | Toplam Kare | Janky Kare | Janky % | p50 (ms) | p90 (ms) | p95 (ms) | p99 (ms) |
|---|---|---|---|---|---|---|---|
| Isınma (atıldı) | 33 | 2 | 6.06% | - | - | 16 | - |
| Koşu 1 | 33 | 5 | 15.15% | 7 | 19 | 31 | 73 |
| Koşu 2 | 33 | 3 | 9.09% | 6 | 15 | 19 | 77 |
| Koşu 3 | 33 | 2 | 6.06% | 5 | 13 | 38 | 81 |
| **Ortanca (Koşu 2)** | **33** | **3** | **9.09%** | **6** | **15** | **19** | **77** |

### Karşılaştırma ve Karar:
- **Janky Yüzdesi:** %11.36 -> %9.09 (Göreli düşüş: **%19.98**; hedef eşik: **en az %30**)
- **p95 Kare Süresi:** 19 ms -> 19 ms (Göreli düşüş: **%0.0**; hedef eşik: **en az %30**)
- **p90 Kare Süresi:** 16 ms -> 15 ms (%6.25 düşüş)
- **p99 Kare Süresi:** 77 ms -> 77 ms (%0.0 düşüş)

> **Karar Kuralı Sonucu:** %30 göreli iyileşme eşiği **sağlanamadı**. Odak animasyonu (`tvFocusableScaleGlow`) afiş arası gezinmedeki takılmanın **ana nedeni değildir**. ADIM 3 kuralı uyarınca odak kodu değiştirilmedi, ikinci şüpheli denendi.

---

## 4. ADIM 3: İkinci Şüpheli Deneyi (Afiş Görseli / Coil Yüklemesi)

- **İkinci Şüpheli:** Gezinme esnasında yeni kartlar ekrana girdikçe Coil üzerinden `AsyncImage` görsel çözümleme ve doku yükleme maliyeti.
- **Yöntem:** Yalnızca `BuildConfig.DEBUG` altında çalışan `--ez play_flag_no_images true` bayrağı eklendi. Bayrak açıkken `PosterCard` içindeki `AsyncImage` çağrısı atlanarak afişler düz yer tutucu rengiyle çizildi.
- **Ham Çıktılar:**
  - Isınma: `docs/dogrulama-afis-kasma/gfxinfo_adim3_noimages_warmup.txt`
  - Koşu 1: `docs/dogrulama-afis-kasma/gfxinfo_adim3_noimages_run1.txt`
  - Koşu 2: `docs/dogrulama-afis-kasma/gfxinfo_adim3_noimages_run2.txt`
  - Koşu 3: `docs/dogrulama-afis-kasma/gfxinfo_adim3_noimages_run3.txt`

| Koşu | Toplam Kare | Janky Kare | Janky % | p50 (ms) | p90 (ms) | p95 (ms) | p99 (ms) |
|---|---|---|---|---|---|---|---|
| Isınma (atıldı) | 38 | 0 | 0.0% | - | - | 16 | - |
| Koşu 1 | 38 | 0 | 0.0% | 6 | 8 | 9 | 16 |
| Koşu 2 | 39 | 2 | 5.13% | 5 | 15 | 17 | 17 |
| Koşu 3 | 38 | 0 | 0.0% | 5 | 8 | 10 | 13 |
| **Ortanca (Koşu 3)** | **38** | **0** | **0.0%** | **5** | **8** | **10** | **13** |

### İkinci Şüpheli Karşılaştırması:
- **Janky Yüzdesi:** %11.36 -> **%0.0** (Göreli düşüş: **%100**, jank tamamen ortadan kalktı)
- **p90 Kare Süresi:** 16 ms -> **8 ms** (Göreli düşüş: **%50.0**)
- **p95 Kare Süresi:** 19 ms -> **10 ms** (Göreli düşüş: **%47.37**)
- **p99 Kare Süresi:** 77 ms -> **13 ms** (Göreli düşüş: **%83.12**)

> **Teşhis:** Afişler arası gezinmedeki takılmanın ve kare atlamalarının kesin kaynağı **Coil afiş görseli yükleme, bellekten çözme (decode) ve GPU doku transferi** sürecidir. Odak animasyonları tek başına jank üretmemektedir.
> **Talimat gereği ("düzeltmeyi bana bırak"):** İkinci şüpheli için üretim kodu düzeltmesi yapılmamış, ölçüm ve analiz sonuçları kullanıcıya bırakılmıştır.

---

## 5. Bütünsel Karşılaştırma Özeti

| Deney Senaryosu | Janky % (Ortanca) | p50 | p90 | p95 | p99 | %30 Göreli Eşik Sağlandı mı? |
|---|---|---|---|---|---|---|
| **1. Baseline (Normal Durum)** | %11.36 | 6 ms | 16 ms | 19 ms | 77 ms | - |
| **2. Glow Kapalı (`no_glow`)** | %9.09 | 6 ms | 15 ms | 19 ms | 77 ms | **HAYIR** (%19.98 düşüş, p95 değişmedi) |
| **3. Görsel Kapalı (`no_images`)** | **%0.0** | 5 ms | 8 ms | 10 ms | 13 ms | **EVET** (%100 jank düşüşü, p95 %47 düşüş) |

---

## 6. Doğrulama ve Testler

1. **Birim Testleri (`testDebugUnitTest`):**
   - Toplam Test Sayısı: **1194**
   - Başarısızlık (Failure): **0**
   - Sonuç: **1194 testin TAMAMI GEÇTİ.**
2. **Yayın Derlemesi (`assembleRelease`):**
   - Sonuç: Başarıyla derlendi (`BUILD SUCCESSFUL in 7m 26s`).
   - Üretilen APK: `Play-1.2.4.apk` (48,632,998 bayt). Yayınlanmadı.
3. **TV Kumanda / D-Pad Kontrolü:**
   - Ana sayfa kartlar arası ve satırlar arası gezinme: Doğrulandı.
   - Üst menüye çıkış ve sekme geçişi: Doğrulandı.
   - Ara ekranına geçiş ve D-pad odak kontrolü: Doğrulandı (`screen_search.png`).
   - Kütüphane ekranına geçiş ve D-pad odak kontrolü: Doğrulandı (`screen_library.png`).
   - Geri (Back) tuşu ile Ana sayfaya dönüş: Doğrulandı (`screen_back_home.png`).

---

## 7. Değişen Dosyalar

- `app/src/main/java/com/kaan/play/MainActivity.kt`: DEBUG intent bayrakları desteği (`play_flag_no_glow`, `play_flag_no_images`).
- `app/src/main/java/com/kaan/play/ui/tv/PlayFocus.kt`: `PlayFocusFlags` nesnesi ve `noGlow` bayrağının DEBUG denetimi.
- `app/src/main/java/com/kaan/play/ui/components/PosterCard.kt`: `noImages` bayrağının DEBUG denetimi.
- `docs/dogrulama-afis-kasma/olcum.py`: Otomatik ve tekrarlanabilir ölçüm senaryosu betiği.
- `docs/dogrulama-afis-kasma/olcum.ps1`: Ölçüm çalıştırma PowerShell sarmalayıcısı.
- `docs/dogrulama-afis-kasma/gfxinfo_*.txt`: Ham dumpsys gfxinfo dökümleri (toplam 12 adet dosya).

---

## 8. Yapılamayan Doğrulamalar

- **Fiziksel TV Donanımı Ölçümü:** Ölçümler LDPlayer emülatörü üzerinde tamamlanmıştır. Gerçek Android TV donanımı (SoC/GPU) üzerindeki mutlak milisaniye değerleri farklılık gösterebilir ("doğrulanamadı"). Ancak düzenek içi göreli oranlar donanımdan bağımsız olarak sorunun görsel çözme hattında olduğunu açıkça ortaya koymaktadır.
