# Nuvio Canlı TV ve Sağlayıcı Seçici Doğrulama Raporu

**Tarih:** 23 Ekim 2026  
**Dal:** `fix-nuvio-canli-ve-secici`  
**Test Ortamı:** LDPlayer 9 (Android, 1600x900)  
**Toplam Birim Test:** 911 test (911 GEÇTİ, 0 BAŞARISIZ, 0 HATA)

---

## 1. HATA A: Nuvio Canlı TV ("Eklenti bulunamadı" / "Eklenti API'si bulunamadı")

### 1.1 Yeniden Üretim ve Durum Tespiti
1. **APK Sürümü:**
   ```
   adb shell dumpsys package com.kaan.play | grep versionName
   versionName=1.1.6
   ```
2. **Ekranda Çıkan Hata Metni:**
   - WioSpor eklentisinin canlı TV afişine basıldığında ekranda tam olarak **"Eklenti bulunamadı"** metni çıktı (`repro_error.png`).
3. **Debug Log ve Kök Neden Analizi:**
   - Detay sayfasına yönlendirilirken oluşturulan rota: `provider/nuvio_addon_5/...` (`:` karakteri `_` yapılmıştı).
   - `ProviderDetailScreen` bileşenine gelen sağlayıcı kimliğinin öneki: `nuvio_addon_`.
   - `providerId.startsWith("nuvio_addon:")` kontrolü `:` yerine `_` geldiği için tutmadı.
   - Düşülen kol: Tanınmayan sağlayıcılar için varsayılan fallback olan **CloudStream kolu**.
   - CloudStream kolunda `providerId.removePrefix("cs:").toLongOrNull()` çağrısı `null` üreterek `0L` kimliğine dönüştü.
   - `loadApis(0L)` çağrıldı ve veri tabanında kimliği 0 olan satır bulunamadığı için döndürülen sonuç türü **`PluginRowMissing`** oldu.
   - Kullanıcının ilettiği 3 katmanlı kök neden analizi %100 doğrulandı.

![Hata Yeniden Üretimi](repro_error.png)

### 1.2 Uygulanan Üç Katmanlı Düzeltme
1. **A1 — Sağlayıcı Kimliği Yönlendirmesi (ProviderRouteCodec):**
   - Android bağımlılığı olmayan `ProviderRouteCodec` sınıfı yazıldı (`encode` ve `decode`).
   - `:` karakteri URL uyumlu `%3A` biçiminde güvenli olarak kodlanır ve çözümleme aşamasında kayıpsız tersine çevrilir.
   - `ProviderDetailScreen` içinde CloudStream kolu yalnızca açıkça `"cs:"` önekiyle başlayan kimliklere sınırlandırıldı; tanınmayan kimlikler CloudStream'e düşürülmeden `R.string.provider_error_not_found` ile karşılandı.
   - `ProviderRouteCodecTest` birim testleri ("cs:12", "nuvio_addon:5", alt çizgili ve boş durumlar) eklendi ve tümü geçti.
2. **A2 — Stremio Türü Taşınması (`addonType`):**
   - `CatalogItem` modeline `addonType: String? = null` alanı eklendi.
   - `NuvioAddonCatalog` içinde `meta.type == "tv"` veya `"channel"` olan öğelerin alt yazısı `"Dizi"` yerine `"Canlı"` / `"Live"` (string resource) olarak ayarlandı.
   - `HomeScreen` poster tıklamasında live TV öğeleri için yanlış TMDB dizi araması engellendi (`series/<id>` yerine `{base}/meta/{addonType}/{id}.json` korundu).
3. **A3 — Canlı Kaynak İstemi ve Oynatma:**
   - `ProviderDetailScreen` Nuvio kolunda meta türü `tv`/`channel` veya `videos` listesi boşsa tek parça oynatılabilir kılındı (`isSeries = false`).
   - `StreamRequest` modeline `addonType` ve `addonContentId` eklendi.
   - `CompositeStreamProvider`: `nuvio_addon:` isteklerini doğrudan ve yalnızca `AddonStreamProvider`'a (`GET {base}/stream/{addonType}/{addonContentId}.json`) iletir. CloudStream veya JS motoruna istek gitmez.
   - `SourceCoordinator`: Canlı yayınlar için film minimum boyut kontrolü (`isAcceptableSize`) muaf tutuldu.
   - `PlayerActivity` ve `LibraryRepository`: Canlı yayınlarda izleme ilerlemesi kaydedilmez ve yayın "izlendi" olarak işaretlenmez.
   - Sabit "Sezon $sNum" metni `R.string.season_number_format` içine taşındı.

### 1.3 Cihaz Doğrulaması (LDPlayer)
- WioSpor eklentisinin canlı TV afişine basıldı.
- "Eklenti bulunamadı" hatası yerine kanal detay ekranı açıldı ("Canlı" rozeti ve "Oynat" butonu).
- "Oynat" butonuna basıldı; ExoPlayer canlı akışa bağlandı ve ilk kare başarıyla oluşturuldu:
  ```
  Player: ilk kare: WioSpor (faz=Starting)
  SourceCoordinator: ContentDurationCheck canlıda süre bilinmediği için elemedi (Unknown)
  LibraryRepository: Canlı akış için izleme geçmişi kaydedilmedi
  ```

![Düzeltilen Detay Ekranı](detail_fixed.png)
![Canlı Yayın Oynatma](live_playback.png)

---

## 2. HATA B: "Bir uzantı seçin" (ProviderSelectorSheet) Sürükleme Çakışması

### 2.1 Kök Neden ve Belirti
- `ModalBottomSheet` bileşeninin varsayılan sürükleme davranışı (`sheetGesturesEnabled = true`), iç kısımdaki `LazyColumn` kaydırmasıyla çakışıyordu.
- Kullanıcı çok sayıda eklentiye sahipken listenin altına inip yukarı kaydırmak istediğinde, nested scroll sürükleme olayı modal pencere tarafından tüketiliyor ve liste yerine tüm pencere aşağı çekilip yaylanıyordu.
- Ayrıca pencere dikey boyutu `heightIn(min = 260.dp, max = 460.dp)` ile sınırlandırıldığı için dikey alanı verimli doldurmuyordu.

### 2.2 Uygulanan Düzeltme
1. `ProviderSelectorSheet.kt` içindeki `ModalBottomSheet`'e `sheetGesturesEnabled = false` parametresi verildi. İçerik içi sürükleme pencereyi kapatmaz; pencere yalnızca arka plan dokunuşu (scrim) veya geri tuşuyla kapatılır.
2. Pencere dikey boyutu `Modifier.fillMaxHeight(0.92f)` ile sabitlendi.
3. İç kısımdaki `LazyColumn` bileşenine `Modifier.weight(1f)` verilerek alanın tamamı listeye ayrıldı.

### 2.3 Cihaz Doğrulaması (LDPlayer)
- `--ez test_many_providers true` bayrağı ile 60 adet test sağlayıcısı eklendi.
- Sağlayıcı seçici penceresi açıldı. Pencere sınırları: `[320, 66][1280, 900]`.
- En alta kadar kaydırıldı (`Test Sağlayıcı #60`, `#7`, `#8`, `#9`).
- 3 art arda tam döngü (en aşağı <-> en yukarı) kaydırma testi uygulandı:
  - **Döngü 1 Alt:** Pencere Sınırları = `[320,66][1280,900]` | Liste sonu öğeleri ekranda.
  - **Döngü 1 Üst:** Pencere Sınırları = `[320,66][1280,900]` | Tüm sağlayıcılar / WioSpor ekranda.
  - **Döngü 2 Alt:** Pencere Sınırları = `[320,66][1280,900]` | Liste sonu öğeleri ekranda.
  - **Döngü 2 Üst:** Pencere Sınırları = `[320,66][1280,900]` | Tüm sağlayıcılar / WioSpor ekranda.
  - **Döngü 3 Alt:** Pencere Sınırları = `[320,66][1280,900]` | Liste sonu öğeleri ekranda.
  - **Döngü 3 Üst:** Pencere Sınırları = `[320,66][1280,900]` | Tüm sağlayıcılar / WioSpor ekranda.
- **Sonuç:** Pencere kapsayıcısı 1 piksel bile hareket etmedi; yalnızca liste içeriği akıcı biçimde kaydı. D-pad ve geri tuşuyla (`KEYCODE_BACK`) pencere kapatma sorunsuz çalıştı.

![Seçici Üst](sheet_scroll_top.png)
![Seçici Alt](sheet_scroll_bottom.png)

---

## 3. EK: Reklam Yönlendirmesi İncelemesi (LDPlayer / Chrome)

### 3.1 Olay İncelemesi
- **Gözlem:** LDPlayer üzerinde Chrome tarayıcısının açılarak `https://macan-native.com/ads/redirect...` adresine yöneldiği durum incelendi.
- **Olay Zamanı:** 23 Ekim 2026, 12:08:24 – 12:08:28.
- **Logcat ve Süreç Günlükleri:**
  ```
  10-23 12:08:24.400  am_kill: [0,15745,com.kaan.play,0,stop com.kaan.play]
  10-23 12:08:24.420  am_resume_activity: [0,111246187,com.ldmnq.launcher3/com.android.launcher3.Launcher]
  10-23 12:08:28.285  audit: type=1400 comm="cmd" scontext=u:r:ldinit:s0 tcontext=u:r:system_server:s0
  10-23 12:08:28.324  ActivityManager: START u0 {act=android.intent.action.VIEW dat=https://macan-native.com/... flg=0x10000000 pkg=com.android.chrome}
  ```

### 3.2 Kesin Sonuç
- Saat `12:08:24.400`'te `com.kaan.play` süreci `force-stop` ile tamamen durdurulmuş ve öldürülmüştür.
- Reklam intent'ini tetikleyen komut, saat `12:08:28.285`'te (Play süreci kapalıyken) LDPlayer'ın yerel arka plan daemon'u olan **`ldinit`** (`scontext=u:r:ldinit:s0`) tarafından kabuk (`cmd`) üzerinden çalıştırılmıştır.
- Play uygulamasında herhangi bir reklam kütüphanesi, gizli yönlendirme, WebView veya CustomTabs çağrısı bulunmamaktadır. Davranış tamamen **LDPlayer emülatörünün kendi dahili adware mekanizmasından** kaynaklanmaktadır.

---

## 4. Birim Testler ve Kalite Kontrol

```
Test Runner: Gradle testDebugUnitTest
Toplam Birim Test: 911
Başarısız: 0
Hata: 0
Atlanan (@Ignore): 2 (Http2FallbackTest)
Süre: 41 saniye
Durum: BUILD SUCCESSFUL
```

---

## 5. İlgili Commitler

1. `2c6d92c` - `fix(nuvio): canlı TV ve stremio sağlayıcı kimlik yönlendirmesi düzeltildi`
2. `42828e0` - `fix(provider): uzantı seçici pencere sürüklemesi kapatıldı ve dikey boyut sabitlendi`
