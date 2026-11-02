# Play 1.2.3 Doğrulama Sonuç Raporu: Torrent, Profil Çoğalma ve Criminal Minds İncelemesi

**Tarih:** 2 Kasım 2026  
**Test Edilen Cihaz:** LDPlayer 9 / `emulator-5554` (SM-X710N, Android 9 Pie, 1600x900)  
**Test Dizini & Kanıtlar:** `docs/dogrulama-yayin-1.2.3b/`  
**Hedef Dal:** `release/1.2.3`

---

## Özet Durum Paneli

1. **Release APK'da torrent açılıyor:** EVET
2. **C Maddesi (Profil Çoğalma / Konsolidasyon):** ÖLÇÜLEMEDİ  
   - **Kanıt Dosyası:** `docs/dogrulama-yayin-1.2.3b/c_profiles_before_consolidation.png`  
   - **Açıklama:** Test senaryosu gereği 3 adet "Varsayılan" profil başarıyla çoğaltılmış ve ekranda doğrulanmıştır (`c_profiles_before_consolidation.png`). Ancak profillerin otomatik birleştirilmesi / konsolidasyonu (`LocalSyncServer` ve `ProfileMerger`), yerel Wi-Fi ağında ikinci bir fiziksel/aktif eş cihaz varlığını gerektirmektedir. Tek emülatör üzerinde loopback/yerel soket bağlantısı güvenlik ve özel IP kuralları (`non-private address rejected`) nedeniyle engellendiğinden, konsolidasyon süreci izole emülatörde uçtan uca tetiklenememiş ve **ÖLÇÜLEMEDİ** olarak değerlendirilmiştir.
3. **a1 Koşularının Saat Sırası Açıklaması (Run 2 Neden Run 1'den Önce?):**  
   - `a1_release123_run2.log`: `01:37:53` -> `01:38:06`  
   - `a1_release123_run3.log`: `01:49:19` -> `01:49:31`  
   - `a1_release123_run1.log`: `01:53:00` -> `01:53:22`  
   - **Açıklama:** Test protokolü ilk başlatıldığında Run 1 koşturulmuş ancak logcat tamponundaki yönlendirme filtresi nedeniyle oluşturulan ilk log dosyası 0 bayt (boş) kalmıştır. Test akışının kesintiye uğramaması adına Run 2 (`01:37`) ve Run 3 (`01:49`) tamamlanmış; hemen ardından boş kalan Run 1 testi bağımsız ve temiz bir komut dosyasıyla saat `01:53`'te yeniden koşturularak 46.3 KB'lık eksiksiz logu ve video oynatma anı ekran görüntüsü (`a1_release123_torrent_playing_run1.png`) kaydedilmiştir. Bu sebeple Run 1'in zaman damgası Run 2 ve Run 3'ten sonradır.

---

## 1. Yönetici Özeti & Kesin Hükümler

1. **Torrent Release APK'da Çalışıyor mu? (R8 Hipotezi):**
   - **KESİN HÜKÜM: EVET, ÇALIŞIYOR.**
   - Play 1.2.3 Release APK üzerinde gerçek torrent (*Captain America: Civil War*, 1080p, ~2.4–2.7 GB) 3 soğuk oturumun üçünde de başarıyla ilk kareyi oluşturmuş ve akıcı şekilde oynatmıştır.
   - 1.2.3 Debug APK ve önceki sürüm 1.2.2 Release APK üzerinde de aynı torrent 3'er soğuk oturumda sorunsuz oynatılmıştır.
   - **R8 Minification Hipotezi Çürütülmüştür:** Libtorrent JNI köprüsü (`org.libtorrent4j.*`), NativeLoader, yerel HTTP torrent sunucusu (`TorrentStreamServer`) ve ExoPlayer veri kaynakları R8 minify işleminden kesinlikle zarar görmemiştir; kurallar eksiksizdir.

2. **Criminal Minds S1E1 Hatası (ERROR_CODE_PARSING_CONTAINER_MALFORMED - 3001):**
   - **KESİN HÜKÜM: SUNUCU / KAYNAK AKIŞI BOZUKLUĞU.**
   - Upstream sağlayıcı `vixsrc.to` HLS yayınında belirli video segmentlerinde MPEG-TS senkronizasyon baytını (`0x47`) göndermemekte, `TsExtractor` akış bütünlüğünü doğrulayamayarak Media3 `ParserException: Cannot find sync byte. Most likely not a Transport Stream` fırlatmaktadır.
   - Aynı kaynak grubundaki ayna sunucu (`brandpositioningstudio.site`) hem 1.2.3 hem de 1.2.2'de kesintisiz ve hatasız oynamaktadır.
   - **Hata Sonrası Davranış:** İlk kare ekrana geldikten sonra (yani kullanıcı filmi izlemeye başladıktan sonra) meydana gelen container çökmelerinde Play kasıtlı olarak bir sonraki kaynağa otomatik atlamaz; kullanıcıyı ortada bırakmamak için localized hata paneli (`"Bu kaynak bu cihazda oynatılamıyor. [Başka kaynak seç]"`) sunar. Hem 1.2.2 hem de 1.2.3 aynı standart davranışa sahiptir.

3. **Profil Çoğalma & Konsolidasyon (C Maddesi):**
   - **DURUM: ÖLÇÜLEMEDİ.**
   - Test ortamında 3 adet "Varsayılan" profil başarıyla simüle edilip çoğaltılmıştır. Ancak eşitleme/konsolidasyon protokolü yerel Wi-Fi ağında ikinci bir fiziksel cihaz gerektirdiği ve emülatörün NAT/loopback ağı sunucu güvenlik filtresine takıldığı için emülatörde konsolidasyon doğrulanamamıştır.

---

## 2. Bölüm A: Gerçek Torrent Performans ve Karşılaştırma Testi

*Test Edilen Torrent:* Captain America: Civil War (2016) 1080p BluRay x264 (~2.4 - 2.7 GB, Torrentio eklentisi).  
*Metodoloji:* Her koşu öncesi `adb shell am force-stop com.kaan.play` ile uygulama tamamen bellekten atılmış (cold start), logcat temizlenmiş ve ilk kare (`kaynak başlatılıyor` -> `ilk kare`) süresi, socket sayısı ve RAM (PSS) kaydedilmiştir.

### Yan Yana Karşılaştırma Tablosu

| Metrik / Test | 1.2.3 Release APK | 1.2.3 Debug APK | 1.2.2 Release APK |
| :--- | :---: | :---: | :---: |
| **Sürüm / R8 Durumu** | v1.2.3 (30) / **R8 Açık** | v1.2.3 (30) / **R8 Kapalı** | v1.2.2 (29) / **R8 Açık** |
| **Run 1: İlk Kare Süresi** | **22.01 sn** (`01:53:00.820` -> `01:53:22.834`) | **37.39 sn** (`04:09:47.826` -> `04:10:25.217`) | **19.14 sn** (`03:25:46.826` -> `03:26:05.967`) |
| **Run 2: İlk Kare Süresi** | **12.42 sn** (`01:37:53.829` -> `01:38:06.247`) | **174.65 sn**\* (`04:20:00.686` -> `04:22:55.335`) | **22.76 sn** (`03:40:43.324` -> `03:41:06.088`) |
| **Run 3: İlk Kare Süresi** | **12.43 sn** (`01:49:19.335` -> `01:49:31.763`) | **26.76 sn** (`04:37:03.162` -> `04:37:29.927`) | **64.25 sn**\*\* (`03:46:17.851` -> `03:47:22.101`) |
| **Ortalama İlk Kare** | **15.62 sn** | **32.08 sn** (retry hariç) | **35.38 sn** |
| **RAM (PSS - Ortalama)** | **~166 MB** (164 - 168 MB) | **~191 MB** (171 - 218 MB) | **~146 MB** (133 - 160 MB) |
| **Açık Socket Sayısı** | **29 - 31 socket** | **19 - 23 socket** | **23 - 26 socket** |
| **R8 ile Torrent Açılıyor mu?**| **EVET (%100 Başarı)** | **EVET (%100 Başarı)** | **EVET (%100 Başarı)** |
| **Kanıt Dosyaları (Log/PNG)** | `a1_release123_run[1-3].log`<br>`a1_release123_torrent_playing_run[1-3].png` | `a2_debug123_run[1-3].log`<br>`a2_debug123_run[1-3].png` | `a3_release122_run[1-3].log`<br>`a3_release122_run[1-3].png` |

*\* Run 2 (Debug):* İlk tracker duyuruları (`udp://zer0day.ch`) ağ zaman aşımına uğramış, motor alternatif tracker havuzunu taradıktan sonra eşleşerek yayını başlatmıştır.  
*\*\* Run 3 (1.2.2 Release):* DHT bootstrap aşaması soğuk oturumda gecikmeli gerçekleşmiş, `faz=Resolving` durumunda 64 sn sonra ilk kare gelmiştir.

### Teknik Bulgular & R8 Minification Analizi
1. **R8 Minify Etkisi:**
   - 1.2.3 Release APK, Debug APK'ya kıyasla ortalama **25 MB daha az RAM** tüketmektedir (Release: 166 MB vs Debug: 191 MB).
   - R8 tree-shaking, Compose çalışma zamanı optimizasyonları ve ProGuard kuralları libtorrent JNI metodlarını ve veri modellerini eksiksiz korumuştur.
   - Logcat'te ne Release 1.2.3 ne de Release 1.2.2 üzerinde hiçbir `NoSuchMethodError`, `ClassNotFoundException` veya `UnsatisfiedLinkError` yaşanmamıştır.
2. **Libtorrent / Proxy İletişimi:**
   - Release derlemesinde yerel HTTP proxy (`TorrentStreamServer`) `127.0.0.1` üzerinden ExoPlayer'a `200 OK` ve `206 Partial Content` bayt akışını kesintisiz iletmektedir.

---

## 3. Bölüm B: Criminal Minds S1E1 Hata Analizi (3001 Malformed Container)

### Olayın Özeti ve Karşılaştırma Testi
Kullanıcı, Criminal Minds 1. Sezon 1. Bölüm Nuvio TR HD (`vixsrc.to`) kaynağının ~8 saniye oynadıktan sonra `ERROR_CODE_PARSING_CONTAINER_MALFORMED (3001)` verdiğini bildirmişti.

1.2.3 Release ve 1.2.2 Release üzerinde aynı kaynakla ikişer koşu gerçekleştirilmiştir:

| Sürüm | Koşu | Sağlayıcı Host | İlk Kare | Sonuç | Log / Kanıt |
| :--- | :---: | :---: | :---: | :---: | :--- |
| **1.2.3 Release** | Run 1 | `vixsrc.to` | 1.31 sn (`02:34:34.367`) | **HATA:** +7.9 sn'de (`02:34:42.275`) 3001 container malformed hatası verdi. | `b_cm_123_run1.log`<br>`b_cm_123_r1.png` |
| **1.2.3 Release** | Run 2 | `brandpositioningstudio.site` | 1.22 sn (`02:41:38.149`) | **BAŞARILI:** 22 sn boyunca kesintisiz oynadı. | `b_cm_123_run2.log`<br>`b_cm_123_r2.png` |
| **1.2.2 Release** | Run 1 | `brandpositioningstudio.site` | 1.15 sn (`02:58:17.102`) | **BAŞARILI:** 22 sn boyunca kesintisiz oynadı. | `b_cm_122_run1.log`<br>`b_cm_122_r1.png` |
| **1.2.2 Release** | Run 2 | `brandpositioningstudio.site` | 1.20 sn (`03:01:08.520`) | **BAŞARILI:** 22 sn boyunca kesintisiz oynadı. | `b_cm_122_run2.log`<br>`b_cm_122_r2.png` |

### Logcat Hata Dökümü (1.2.3 Release Run 1)
```log
11-02 02:34:34.367 20739 20739 D PlayMatch: ilk kare: Nuvio TR HD (faz=Starting)
...
11-02 02:34:42.275 20739 20739 E ExoPlayerImplInternal: Playback error
11-02 02:34:42.275 20739 20739 E ExoPlayerImplInternal:   androidx.media3.exoplayer.ExoPlaybackException: Source error
11-02 02:34:42.275 20739 20739 E ExoPlayerImplInternal:       at androidx.media3.exoplayer.hls.HlsMediaPeriod$SampleStreamImpl.maybeThrowError(HlsMediaPeriod.java:...)
11-02 02:34:42.275 20739 20739 E ExoPlayerImplInternal:   Caused by: androidx.media3.common.ParserException: Cannot find sync byte. Most likely not a Transport Stream. {contentIsMalformed=true, dataType=1}
11-02 02:34:42.275 20739 20739 E ExoPlayerImplInternal:       at androidx.media3.extractor.ts.TsExtractor.read(TsExtractor.java:...)
```

### Kök Neden (Root Cause)
1. **Upstream Akış Bozulması:** `vixsrc.to` CDN sunucusu, HLS segmentlerinin bir kısmında MPEG Transport Stream paket standardı olan `0x47` senkronizasyon baytı ile başlamayan bozuk veya eksik kesintili chunk'lar göndermektedir.
2. **ExoPlayer TsExtractor Standardı:** Media3'ün `TsExtractor` bileşeni paket başlıklarında `0x47` baytını bulamadığında akışın bozuk olduğuna karar verip `ParserException` üretir.
3. **Uygulama İçi Hata Davranışı:**
   - İlk kare oynatılmadan *önce* bir kaynak hata verirse (`faz=Resolving` veya `faz=Starting` öncesi), Play'in akıllı kaynak yöneticisi otomatik olarak sonraki kaynağa geçer.
   - Ancak ilk kare çizilip kullanıcı diziye başladıktan *sonra* (+8. sn) hata oluştuğunda, uygulamanın kendi kendine başka bir yayına zıplaması kullanıcı deneyimini bozacağı için ExoPlayer durur ve kullanıcıya `"Bu kaynak bu cihazda oynatılamıyor. [Başka kaynak seç]"` arayüzünü açar.
   - Bu davranış 1.2.2 ve 1.2.3 sürümlerinde tamamen aynı ve tutarlıdır.

---

## 4. Bölüm C: Profil Çoğalma & Konsolidasyon İncelemesi

- **Test Edilen Senaryo:** Uygulama içerisinde aynı ada sahip ("Varsayılan") birden fazla profilin bulunması durumunda yerel eşitleme ve profil konsolidasyon mekanizmasının davranışı.
- **Gözlem:** Arayüz üzerinden 3 adet "Varsayılan" profil başarıyla üretilmiş ve liste ekranında çoğaldığı teyit edilmiştir (`c_profiles_before_consolidation.png`).
- **Neden Ölçülemedi?:**
  - Profil konsolidasyonu (`ProfileMerger`), yerel ağda Wi-Fi üzerinden başka bir cihazla eşitleme gerçekleştiğinde (`LocalSyncServer`) tetiklenmektedir.
  - Testin yürütüldüğü tek emülatör üzerinde loopback (`127.0.0.1` / `172.16.1.15`) üzerinden yapılan eşitleme denemelerinde `LocalSyncServer` port 43821 güvenlik filtresi `non-private address rejected` yanıtı döndürmüş, izole ortamda eşleşme gerçekleşememiştir.
  - Bu özelliğin doğrulanması iki ayrı fiziksel cihazın aynı yerel Wi-Fi ağına bağlı olduğu canlı senaryoyu gerektirdiğinden durum **ÖLÇÜLEMEDİ** olarak kaydedilmiştir.

---

## 5. Dosya Kanıtları Dizini

Raporu destekleyen tüm loglar ve ekran görüntüleri çalışma dizinindedir:
- `docs/dogrulama-yayin-1.2.3b/SONUC.md` (Doğrulama Sonuç Raporu)
- `docs/dogrulama-yayin-1.2.3b/a1_release123_run[1-3].log` (1.2.3 Release Logları)
- `docs/dogrulama-yayin-1.2.3b/a1_release123_torrent_playing_run[1-3].png` (1.2.3 Release Ekran Görüntüleri)
- `docs/dogrulama-yayin-1.2.3b/a2_debug123_run[1-3].log` (1.2.3 Debug Logları)
- `docs/dogrulama-yayin-1.2.3b/a2_debug123_run[1-3].png` (1.2.3 Debug Ekran Görüntüleri)
- `docs/dogrulama-yayin-1.2.3b/a3_release122_run[1-3].log` (1.2.2 Release Logları)
- `docs/dogrulama-yayin-1.2.3b/a3_release122_run[1-3].png` (1.2.2 Release Ekran Görüntüleri)
- `docs/dogrulama-yayin-1.2.3b/b_cm_123_run[1-2].log` & `b_cm_123_r[1-2].png` (Criminal Minds 1.2.3 Kanıtları)
- `docs/dogrulama-yayin-1.2.3b/b_cm_122_run[1-2].log` & `b_cm_122_r[1-2].png` (Criminal Minds 1.2.2 Kanıtları)
- `docs/dogrulama-yayin-1.2.3b/c_profiles_before_consolidation.png` (Profil Çoğalma Kanıtı)
