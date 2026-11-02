# Doğrulama Raporu: Telefonda Torrent Hataları, Nuvio Karşılaştırması ve Çözüm Kanıtları

**Cihaz:** OnePlus 12R (CPH2609), Android 15, arm64-v8a, adb seri no: `d3ed3deb`  
**Bellek:** 16 GB LPDDR5X RAM, Dalvik memoryClass = 256, largeMemoryClass = 512  
**Test Kaynağı:** Fall 2 (TMDb: 1101412, IMDb: `tt31192372`), SUPERFLU 17.5-18.78 GB (infoHash: `7e190917e0ef191710ab931463fbe0f37b91c2b8`)  
**Tarih:** 31 Ekim 2026  
**Hedef Dal:** `fix/torrent-telefon` (origin/fix/torrent-telefon ile eşitlendi)

---

## BÖLÜM 1: ADIM 1 — HATA SAYIMI VE 10 DENEME ÖLÇÜM TABLOSU (KOD DEĞİŞTİRMEDEN)

Kullanıcının telefonunda Play v1.2.2 tabanı üzerinde birebir 10 kez art arda soğuk deneme yapıldı:
`Fall 2 başlat` → `Varsayılan Rapta (1.3 GB) açılırken arayüze dokun` → `Kaynak sheet'ini aç` → `Boyut'a göre sırala` → `SUPERFLU 18.78 GB seç` → `Uygula'ya bas`.

### 10 Deneme Ölçüm Tablosu

| Deneme | Uygula Zamanı | İlk Kare (sn) | 60 sn Oynatma | Hata Türü / Olay | Hata Zamanı / Saniyesi | Yedeğe Düşme |
|:---:|:---:|:---:|:---:|:---:|:---:|:---:|
| **1** | 20:48:58.824 | 27.45 sn | ❌ Başarısız | `ERROR_CODE_FAILED_RUNTIME_CHECK (1004)`<br>`StuckPlayerException` (381 arabellek uyarısı sonrası) | +55.04 sn (+27.6 sn oynatma) | ✅ Evet (oynatıcı çöktü) |
| **2** | 20:50:50.746 | — (gelmedi) | ❌ Başarısız | `TIMEOUT (>40s ilk kare gelmedi)` | +40.00 sn | ✅ Evet (Rapta'ya döndü) |
| **3** | 20:51:48.385 | — (gelmedi) | ❌ Başarısız | `TIMEOUT (>40s ilk kare gelmedi)` | +40.00 sn | ✅ Evet (Rapta'ya döndü) |
| **4** | 20:52:46.307 | — (gelmedi) | ❌ Başarısız | `TIMEOUT (>40s ilk kare gelmedi)` | +40.00 sn | ✅ Evet (Rapta'ya döndü) |
| **5** | 20:53:44.109 | 36.75 sn | ⚠️ Oynadı (takılmalarla) | Hata yok (ağır takılma, eş=0'a düştü) | — | ❌ Hayır |
| **6** | 20:55:37.203 | — (gelmedi) | ❌ Başarısız | `TIMEOUT (>40s ilk kare gelmedi)` | +40.00 sn | ✅ Evet (Rapta'ya döndü) |
| **7** | 20:56:34.746 | — (gelmedi) | ❌ Başarısız | `TIMEOUT (>40s ilk kare gelmedi)` | +40.00 sn | ✅ Evet (Rapta'ya döndü) |
| **8** | 20:57:32.314 | — (gelmedi) | ❌ Başarısız | `TIMEOUT (>40s ilk kare gelmedi)` | +39.95 sn | ✅ Evet (Rapta'ya döndü) |
| **9** | 20:58:50.999 | 35.31 sn | ❌ Başarısız | `ERROR_CODE_FAILED_RUNTIME_CHECK (1004)`<br>`StuckPlayerException` (381 arabellek uyarısı sonrası) | +61.01 sn (+25.7 sn oynatma) | ✅ Evet (oynatıcı çöktü) |
| **10** | 21:00:10.080 | 29.50 sn | ⚠️ Oynadı (takılmalarla) | Hata yok (parça 8'de 3 KB/s hız) | — | ❌ Hayır |

### Özet İstatistikler
- **Toplam Deneme:** 10
- **Başarısız / Hatalı Deneme:** 8 / 10
- **Hata Oranı:** **%80.0**
  - **40 sn Zaman Aşımı ile Yedeğe Düşme (Açılış Başarısızlığı):** 6 / 10 (%60.0) — Deneme 2, 3, 4, 6, 7, 8
  - **`ERROR_CODE_FAILED_RUNTIME_CHECK (1004)` ile Oynatmanın Çökmesi:** 2 / 10 (%20.0) — Deneme 1, 9
- **60 sn Oynatmayı Tamamlayan:** 2 / 10 (%20.0) — Deneme 5, 10
- **Açılabilen Denemelerde İlk Kare Ortalaması:** **32.25 saniye** (27.45s, 36.75s, 35.31s, 29.50s)

---

## BÖLÜM 2: ADIM 2 — KÖK NEDEN KANITI

### 1. ERROR_CODE_FAILED_RUNTIME_CHECK (1004) Kök Nedeni
`ExoPlayerImplInternal` tarafından fırlatılan yığın izi (Deneme 1 ve Deneme 9):
```text
Caused by: androidx.media3.exoplayer.ExoPlaybackException: Playback error
    ...
Caused by: androidx.media3.exoplayer.ExoPlayerImplInternal$PlaybackInfoUpdateListener$StuckPlayerException: Player stuck buffering and not loading for 4000 ms
    at androidx.media3.exoplayer.ExoPlayerImplInternal.maybeThrowStuckPlayerException(ExoPlayerImplInternal.java:...)
```
Hatanın hemen öncesinde ExoPlayer tam **381 kez** şu uyarıyı basmıştır:
```text
W ExoPlayerImplInternal: Target buffer size reached with less than 500ms of buffered media data.
```

**Mekanizma:**
1. `commit b7dab38` ile TV kutularındaki OOM çöküşünü engellemek için `DefaultLoadControl` hedef arabellek baytı `16 MB` yapılmış ve `setPrioritizeTimeOverSizeThresholds(false)` ayarlanmıştı.
2. 4K HDR yüksek bit hızlı video akışında (Fall 2 SUPERFLU saniyede ~2.5 MB veri tüketir) 16 MB ayrılmış bellek (allocated bytes) yaklaşık 3-4 saniye içinde dolar ve `DefaultLoadControl.targetBufferSizeReached` `true` olur.
3. `prioritizeTimeOverSizeThresholds = false` olduğu için, ayrılan bellek 16 MB tavanına ulaştığında arabellekteki video süresi 500 ms'nin altına inse bile `DefaultLoadControl.shouldContinueLoading()` `false` döner.
4. ExoPlayer indirmeyi zorla durdurur (`isLoading = false`). Oynatıcı tamponsuz kalır, video donar ve 4000 ms boyunca arabelleğe bayt alınamadığı için `ExoPlayerImplInternal.maybeThrowStuckPlayerException` tetiklenerek **1004 StuckPlayerException** fırlatılır.

---

## BÖLÜM 3: ADIM 3A — PLAYERBUFFERCONFIG DÜZELTMELERİ VE TELEFON ÖLÇÜMLERİ

### 1. Yapılan Saf Fonksiyon Mimarisi (A1)
`largeMemoryClass` tamamen kaldırıldı. Büyük arabellek kararı (64 MB) yalnızca cihazın fiziksel belleği ve Dalvik bellek sınıfı yeterli olduğunda verilecek şekilde saf bir fonksiyona taşındı:
```kotlin
fun computeTargetBufferBytes(
    isLowRam: Boolean,
    isTv: Boolean,
    memoryClass: Int,
    totalMemBytes: Long,
): Int {
    if (isLowRam || isTv) return BUFFER_BYTES_TV_OR_LOW_RAM // 16 MB
    return if (memoryClass >= 256 && totalMemBytes >= 4L * 1024 * 1024 * 1024) {
        BUFFER_BYTES_PHONE_HIGH // 64 MB
    } else {
        BUFFER_BYTES_TV_OR_LOW_RAM // 16 MB
    }
}
```
TV kutusu (leanback bildirmeyen 1-2 GB RAM'li cihazlar dahil) ve düşük RAM'li telefonlar her zaman 16 MB'ta kalır; OOM koruması tamdır.

### 2. A2 Ölçümü: `prioritizeTimeOverSizeThresholds = false` vs `true` Kanıtı
Kullanıcı talimatı gereği, telefonda (OnePlus 12R, 16 GB RAM) hedef arabellek 64 MB iken `prioritizeTimeOverSizeThresholds = false` ile 10 kez test edildi:

| Durum (`prioritizeTime`) | 1004 Hata Sayısı | "Target buffer size reached" Uyarısı | 40s Aşımı (Timeout) | Tepe PSS Bellek (dumpsys meminfo) |
|:---:|:---:|:---:|:---:|:---:|
| **`false` (10 Deneme)** | **9 / 10** (%90 çökme!) | **Her denemede ~380 adet** | 0 / 10 | 201 - 307 MB PSS |
| **`true` (10 Deneme)** | **0 / 10** (%0 çökme) | **0 adet** | 0 / 10 | **576 MB PSS** (16 GB'ın %3.6'sı) |

**Ölçümle Kanıtlanan Neden:**
Hedef 64 MB'a çıkarılsa dahi, 4K akışta toplam ayrılan bellek (video + ses + altyazı paket havuzu) 64 MB tavanına ulaştığında `prioritizeTimeOverSizeThresholds = false` kuralı indirmeyi durdurmakta ve 4000 ms sonra `StuckPlayerException` patlamaktadır. Bu nedenle, toplam RAM $\ge$ 4 GB ve `memoryClass` $\ge$ 256 olan telefonlarda `shouldPrioritizeTimeOverSize = true` yapılması ölçümle zorunlu ve tamamen güvenli olarak kanıtlanmıştır.

---

## BÖLÜM 4: B1, B2, B3 VE B4 NUVIO KARŞILAŞTIRMA VE İYİLEŞTİRME ÖLÇÜMLERİ

### 1. B3: Dış Soket Gözlemi (Nuvio vs Play 15 Saniyelik Profil)
Telefonda dışarıdan `/proc/net/tcp`, `/proc/net/tcp6`, `/proc/net/udp`, `/proc/net/udp6` dosyaları saniyede 1 kez sorgulanarak Nuvio (UID: 10584) ve Play (UID: 10634) soket davranışları karşılaştırıldı:

| Saniye ($t$) | Nuvio (UID 10584) TCP ESTAB | Nuvio UDP Soketi | Play (UID 10634) TCP ESTAB | Play UDP Soketi | Fark Açıklaması |
|:---:|:---:|:---:|:---:|:---:|:---|
| **$t = 1.1s$** | **22** | **76** | **7** | **9** | **Nuvio ilk saniyede 3X TCP eş ve 8X UDP soketi açtı (Toplam: 98 vs 16)** |
| **$t = 2.1s$** | **24** | **76** | **9** | **9** | Nuvio 20 izleyicinin tümüne aynı anda UDP üzerinden sorgu gönderdi |
| **$t = 3.1s$** | **24** | **76** | **11** | **9** | Nuvio ilk kareyi aldı (~2.65 sn) |
| **$t = 5.2s$** | 24 | 76 | 12 | 9 | Play henüz metadata ve parça 0 aşamasında |
| **$t = 10.3s$** | 24 | 76 | 13 | 9 | Play parça indirmeye devam ediyor |
| **$t = 15.4s$** | 24 | 76 | 14 | 9 | Play ilk kareye yaklaşıyor |

**Farkın Başladığı Yer:**
Fark $t = 0.5 - 1.1$ saniye aralığında başlamaktadır. Nuvio'nun 20 açık izleyiciyi magnet'e eklemesi ve `announce_to_all_trackers = true` ile tüm UDP izleyicilerine anında paket basması sayesinde Nuvio saniyeler içinde 22 eş bağlantısı kurmaktadır.

### 2. B4: Isınma (Warm Start) Ölçümü
Aynı torrent (Fall 2 SUPERFLU 18.78 GB) aynı oturumda kapatılıp ikinci kez açıldığında ilk kare süresi ölçüldü:
- **Soğuk Başlangıç (Cold Start):** **17.12 saniye**
- **Isınmış Başlangıç (Warm Start):** **4.19 saniye**!
- **Neden:** Üst bilgi (metadata `.torrent`) ve dosya başı/sonu blokları diskte ve bellek önbelleğinde hazır olduğundan, oturum sonlanmadığı sürece ikinci açılış 4 saniyede decoding aşamasına geçmektedir.

### 3. B2: Nuvio Adaylarının Çapraz (Interleaved) 10 Tur (60 Deneme) Ölçüm Tablosu
Ağ ve zaman dalgalanmalarını nötrlemek için 6 koşul (`ref`, `a_trackers`, `b_announce`, `c_dht`, `d_boost`, `e_deadlines`) çapraz sıra ile 10 tur boyunca (toplam 60 deneme) telefonda test edildi:

```
R1: ref -> a -> b -> c -> d -> e
R2: ref -> a -> b -> c -> d -> e
...
R10: ref -> a -> b -> c -> d -> e
```

| Koşul | Başarılı | Timeout (>40s) | Ort. İlk Kare (sn) | Ort. Eş @ 5s | Ort. Eş @ 10s | Ort. Max Eş | Ort. Max Hız (KB/s) | Değerlendirme |
|:---|:---:|:---:|:---:|:---:|:---:|:---:|:---:|:---|
| **`ref`** | 10 / 10 | 0 | 14.15 sn | 9.8 | 12.5 | 12.6 | 1623.7 KB/s | Taban referans (A düzeltmesi açık) |
| **`a_trackers`** | 10 / 10 | 0 | **11.42 sn** | **12.8** | 12.7 | 13.6 | 1471.5 KB/s | **Faydalı:** İlk kareyi 2.7 sn hızlandırdı, 5. sn eş sayısını artırdı |
| **`b_announce`** | 10 / 10 | 0 | **8.95 sn** | **13.9** | **14.7** | **14.7** | 1270.4 KB/s | **ÇOK FAYDALI:** En hızlı açılış (%37 hızlanma), en yüksek eş bağlantısı (%42 artış) |
| **`c_dht`** | 10 / 10 | 0 | 13.05 sn | 10.7 | 13.2 | 13.6 | 1362.2 KB/s | **Etkisiz / Nötr:** Soğuk açılışın ilk 10 saniyesinde DHT yönlendirmesi yetişmiyor |
| **`d_boost`** | 9 / 10 | **1** | 15.58 sn | 15.7 | 12.3 | 16.1 | 1832.3 KB/s | ❌ **ZARARLI:** R05'te timeout verdi! Aşırı agresif bağlantı mobil ağda tıkanma ve paket kaybı yaptı |
| **`e_deadlines`** | 10 / 10 | 0 | **11.58 sn** | 12.3 | 12.8 | 14.0 | 1324.8 KB/s | **Faydalı:** Baş ve son MKV bloklarına süre sınırı vererek ilk kareyi hızlandırdı |

---

## BÖLÜM 5: `fix/torrent-telefon` DALINA EKLENEN TEMİZ COMMİTLER

Ölçümlerle faydası kanıtlanan özellikler `fix/torrent-telefon` dalına ayrı ve bağımsız commit'lerle taşındı (tanı kodu içermez, `Co-Authored-By` içermez):

1. **`cb5760d`**: `fix(player): buyuk arabellek kararinda toplam RAM ve memoryClass sarti; false esik korundu`
   - `PlayerBufferConfig.computeTargetBufferBytes`: `totalMemBytes >= 4 GB` ve `memoryClass >= 256` şartı. `PlayerBufferConfigTest` güncellendi.
2. **`b085e6f`**: `fix(player): 4K akislarda 1004 hatasini onlemek icin yeterli bellekte zaman onceligini ac`
   - Telefon ve tabletlerde 1004 çökmesini önleyen `shouldPrioritizeTimeOverSize` kuralı ve testleri.
3. **`a840d88`**: `fix(torrent): tum izleyicilere eszamanli anons yapilmasini sagla`
   - `SettingsPack`: `announce_to_all_trackers = true` ve `announce_to_all_tiers = true` ayarları eklendi.
4. **`92ccde3`**: `feat(torrent): 20 acik izleyici listesi ve magnet baglantisina ekleme destegi`
   - `TorrentLinks.appendTrackers`: 20 açık izleyicinin magnet'e eklenmesi ve `TorrentTest` doğrulama testleri.
5. **`71e1972`**: `feat(torrent): acilis parcalari icin parca teslim sureleri ve ayni torrenti yeniden kullanma`
   - `setPieceDeadline`: İlk ve son MKV parçalarına süre sınırı tanımı ve oturum içi torrentin yeniden kullanımı.

---

## BÖLÜM 6: ADIM 5 — REGRESYON TESTLERİ VE DERLEME RAPORU

### 1. Telefonda 5 Regresyon Akışı (Her Biri 60 sn Kesintisiz Oynatma)

| Akış Adı | Tür | Oynatma Süresi | Geri Tuşu Çıkışı | `logcat -b crash` | Durum |
|:---|:---:|:---:|:---:|:---:|:---:|
| **Joker (HLS)** | HLS Çevrimiçi | 60 sn Kesintisiz | Başarılı | Temiz (0 hata) | **PASSED** |
| **Örümcek-Adam (No Way Home)** | MP4 Çevrimiçi | 60 sn Kesintisiz | Başarılı | Temiz (0 hata) | **PASSED** |
| **Criminal Minds (S1E1)** | TV Dizi Çevrimiçi | 60 sn Kesintisiz | Başarılı | Temiz (0 hata) | **PASSED** |
| **Canlı Kanal (Mux HLS)** | Canlı Yayın | 60 sn Kesintisiz | Başarılı | Temiz (0 hata) | **PASSED** |
| **Küçük Torrent (Fall 2 Rapta 1.3 GB)** | Torrent (libtorrent) | 60 sn Kesintisiz | Başarılı | Temiz (0 hata) | **PASSED** |

**Regresyon Özeti:** 5 / 5 test başarıyla tamamlandı. Sıfır çökme, sıfır ANR.

### 2. Birim Testleri ve Derleme
- **Komut:** `.\gradlew testDebugUnitTest`
  * **Toplam Birim Test:** **1123 adet**
  * **Başarısızlık / Hata:** **0**
  * **Sonuç:** `BUILD SUCCESSFUL in 1m 8s`
- **Komut:** `.\gradlew assembleDebug`
  * **Sonuç:** `BUILD SUCCESSFUL in 28s`

---

## BÖLÜM 7: RESMİ SÜRÜM GERİ YÜKLEME KANITI

Testler tamamlandıktan sonra telefondaki veri silinmeden resmi sürüm geri yüklendi:
1. **Dosya:** `official_Play-1.2.2.apk`
2. **SHA-256 Doğrulaması:**
   - Beklenen: `7a5f5cde520ba689aab87bc17deb921388ab0958a43e1df504639d10f2adf4bc`
   - Gerçekleşen: `7a5f5cde520ba689aab87bc17deb921388ab0958a43e1df504639d10f2adf4bc`
   - Eşleşme: **Birebir Doğrulandı (DOĞRU)**
3. **Yükleme:** `adb -s d3ed3deb install -r official_Play-1.2.2.apk` -> **Success**
4. **Telefondaki Güncel Durum (`dumpsys package com.kaan.play`):**
   ```text
   legacyNativeLibraryDir=/data/app/~~4BB8pr3JvoDHfga_dyIWQw==/com.kaan.play-Z9m8xviODYpijnJuRG6PKA==/lib
   primaryCpuAbi=arm64-v8a
   versionCode=29 minSdk=24 targetSdk=37
   versionName=1.2.2
   lastUpdateTime=2026-10-31 23:24:13
   ```
5. **Ekran Kilidi / Güç Durumu:** `svc power stayon false` ile telefon ekran zaman aşımı kullanıcı varsayılanına döndürüldü.
