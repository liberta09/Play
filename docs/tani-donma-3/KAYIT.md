# Acil Kayıt ve Tanı Raporu: Örümcek-Adam: Yepyeni Bir Gün Oynatma Takılması

## 1. Paket ve Sürüm Kontrolü
- **Komut:** `adb shell dumpsys package com.kaan.play | grep versionName`
- **Çıktı:** `versionName=1.1.13`
- **Durum:** Test edilen uygulama sürümü 1.1.13'tür.

## 2. Ekran Görüntüsü ve Görsel Durum
- **Dosya:** `docs/tani-donma-3/x.png` (1600x900, geçerli PNG)
- **Gözlem:** Kullanıcının 30+ saniyelik "Kaynaklar yükleniyor…" ve "Altyazı yükleniyor…" beklemesinin ardından işlem 20:04:35'te sonlandırıldığı için ekran görüntüsü anında cihaz ana başlatıcı ekranındadır (`com.ldmnq.launcher3`). Kullanıcının belirttiği süreç boyunca uygulamanın `PlayerScreen` bileşeninde `SourcePhase.Resolving` ("Kaynaklar yükleniyor…") ve `SubtitleStatus.Loading` ("Altyazı yükleniyor…") durumunda kaldığı log kayıtlarıyla doğrulanmıştır.

## 3. Logcat Zaman Damgası ve Akış Analizi (Son 10 Dakika)

### a) Oynat'a Basılma Anı
- **Zaman:** `10-25 20:02:47.222`
- **Log:** `I/ActivityManager: START u0 {cmp=com.kaan.play/.ui.player.PlayerActivity (has extras)} from uid 10188`
- **Açıklama:** Detay/Arama ekranından Oynat seçeneği tetiklendi ve `PlayerActivity` başlatıldı.

### b) Eklenti İsteklerinin Başlangıcı ve Cevapları
- **İstek Başlangıçları:** `20:02:47.229` (PlayerActivity başlangıcında tüm etkin sağlayıcılara eşzamanlı istek gönderildi).
- **Jinbe:**
  - Cevap Zamanı: `20:03:03.654` (~16.43 saniye sonra)
  - Sonuç: 1 akış döndü (Cloudflare R2, `4dad9ad3e94138b7447532d7884e3068.r2.cloudflarestorage.com`, Orijinal / altyazılı).
- **Urouge:**
  - Adım 2/3 (API): `20:03:03.829` (`lmscript.xyz`)
  - Adım 3/3 Tamamlandı: `20:03:04.346` - `20:03:04.351` (~17.13 saniye sonra)
  - Sonuç: 1 akış döndü (`srv321.biligglectich.cyou`, 1080p Orijinal m3u8).
- **Gorosei:**
  - Adım 3/3 Tamamlandı: `20:03:05.180` - `20:03:05.182` (~17.96 saniye sonra)
  - Sonuç: 1 HLS akışı döndü (`moon.goldenkite.top`, 4K Orijinal m3u8).
- **Diziroom:**
  - Ağ Trafiği: `20:03:04.191` ile `20:03:15.794` arasında HTTP 200 GET (`diziroom.com`).
  - Sonuç: Doğrudan eşleşen akış dönmedi / arama sürdü.

### c) Kaynak Listesine İlk Kaynağın Girdiği An
- **Zaman:** `10-25 20:03:03.654`
- **Açıklama:** Jinbe eklentisi Cloudflare R2 üzerindeki akışı `SourceCoordinator` akış listesine ekledi.

### d) "Kaynak Başlatılıyor" Durumu ve Zamanı
- **Zaman:** `10-25 20:03:03.654` - `20:03:04.000`
- **Açıklama:** 8 saniyelik dublaj bekleme süresi (`StartPolicy.DUB_WAIT_MS = 8_000L`) aşıldığı için (`elapsedMs = 16.43s > 8s`), Jinbe'den gelen ilk kaynak `SourceCoordinator` tarafından hemen `begin()` -> `loadStream()` zinciriyle ExoPlayer'a verildi. ExoPlayer `20:03:04.065`'te akış paketlerini okumaya başladı.

### e) "İlk Kare" Satırı
- **Durum:** YOK.
- **Açıklama:** ExoPlayer hiçbir zaman ilk video karesini çizemedi.

### f) Hata ve Çökme Satırları
- **Akış Ayrıştırma Hatası (ExoPlayer PesReader):**
  - `10-25 20:03:04.065 W/PesReader: Unexpected start code prefix: 2191963`
  - `10-25 20:03:07.059 W/PesReader: Unexpected start code prefix: 2191979`
  - `10-25 20:03:09.930 W/PesReader: Unexpected start code prefix: 2191995`
  - `10-25 20:03:10.601 W/PesReader: Unexpected start code prefix: 2192049`
  - `10-25 20:03:13.064 W/PesReader: Unexpected start code prefix: 2192013`
  - `10-25 20:03:15.933 W/PesReader: Unexpected start code prefix: 2192029`
  - `10-25 20:03:19.068 W/PesReader: Unexpected start code prefix: 2192045`
  - `10-25 20:03:21.970 W/PesReader: Unexpected start code prefix: 2192061`
  - `10-25 20:03:22.353 W/PesReader: Unexpected start code prefix: 2192107`
  - `10-25 20:03:25.075 W/PesReader: Unexpected start code prefix: 2192079`
  - `10-25 20:03:27.944 W/PesReader: Unexpected start code prefix: 2192095`
  - `10-25 20:03:30.916 - 20:04:24.709` arasında sürekli tekrar eden PES başlık hataları.
- **Grafik / Yüzey Hatası:**
  - `10-25 20:03:28.276 E/EGL_emulation: tid 8904: eglSurfaceAttrib(1504): error 0x3009 (EGL_BAD_MATCH)`
  - `10-25 20:03:28.276 W/OpenGLRenderer: Failed to set EGL_SWAP_BEHAVIOR on surface, error=EGL_BAD_MATCH`
- **Uygulama Durdurma:**
  - `10-25 20:04:35.020 I/ActivityManager: Force stopping com.kaan.play appid=10188 user=0: from pid 2256`
  - `10-25 20:04:35.020 I/ActivityManager: Killing 8854:com.kaan.play/u0a188 (adj 0): stop com.kaan.play`
  - `10-25 20:04:35.069 I/Zygote: Process 8854 exited due to signal (9)`

## 4. Karar
**(C) başlatıldı ama ilk kare gelmedi (kaynak açılmadı):** Oynatma 20:03:03.654'te gelen Jinbe akışıyla (~16.4 sn sonra) başlatılmış, ExoPlayer 20:03:04.065'te akışı ayrıştırmaya geçmiş ancak akış bozuk/geçersiz MPEG-TS paketleri içerdiği için sürekli `PesReader: Unexpected start code prefix` ve ardından `EGL_BAD_MATCH` hataları alınmış, 10 saniyelik ilk kare zaman aşımı aşılarak yedek dublaj/kaynak bekleme döngüsü içinde kalınmış ve ilk kare hiçbir zaman çizilememiştir.
