# 4K kaynaklarda ses yok: FFmpeg ses çözücüsü (1.3.3)

Dal: `fix/4k-ses` (main `d8939fa` üstünde). Cihaz: LDPlayer 127.0.0.1:5555 (Android 9, x86_64, 1600×900).
Kullanıcı kararı 2026-11-22: NextLib eklenir, APK artışı (~+18,6 MB) kabul, tüm ABI'ler kalır.

## Kök neden (İş 0, eski APK 1.3.2 / vc49)
- **Eklenti türleri:** "All-in-One-Nuvio" (65 eklenti, 4KHDHub bunun içinde) ve "izlelan" (40 eklenti, Jinbe bunun içinde) Nuvio eklenti deposu (JS kazıyıcı). CloudStream deposu yok (`00_depolar.png`, `00b_nuvio_pluginler.png`).
- **Eski APK'da 4KHDHub 4K (53,3 GB) ve Jinbe 4K (90,8 GB):** video oynuyor ama hiçbir ses çözücüsü oluşturulmuyor. AudioFlinger'da Play'in izi yok, Parçalar > Ses "Seçenek yok" (`01`–`04`, `is0_logcat.txt`, `is0_audioflinger.txt`).
- **Cihazın ses çözücüleri:** yalnızca mp3/aac/vorbis/opus/flac/amr/g711/raw (`is0_cihaz_ses_cozuculeri.xml`).
- **Yeni APK'nın tanı satırıyla doğrulanan ses parçaları:** iki 4K dosya aynı sürüm. Parçalar `audio/eac3` 5.1 (hi, ta, te) ve `audio/vnd.dts.hd` 5.1 (en; DTS-HD Master Audio). AAC parçası yok. Cihazda E-AC-3 ve DTS çözücüsü olmadığı için ses sessizce düşüyordu.
- **1080p karşılaştırma (eski APK):** Jinbe 1080p AAC 2 kanal 44,1 kHz ile sesli (`05_1080p_karsilastirma.png`).

## Yapılan
- **Paket:** `io.github.anilbeesetti:nextlib-media3ext:1.11.1-0.19.0` (NextLib, **GPL-3.0**; Media3 1.11.1'e bağlı). Jellyfin `media3-ffmpeg-decoder` en yeni sürümü `1.9.0+1` olduğu için uyumsuz.
- **`PlayRenderersFactory`:** `DefaultRenderersFactory` + `EXTENSION_RENDERER_MODE_ON`. Önce cihaz MediaCodec çözücüsü, olmazsa NextLib `FfmpegAudioRenderer`. Yalnızca ses eklenir; video ve altyazı Media3'te kalır. Yerel kütüphane yüklenemezse sessizce yalnızca cihaz çözücüleri kullanılır.
  - Fabrika `PlaybackController` ve `DownloadCenter` (DownloadHelper) içinde kullanılır. `setEnableDecoderFallback(true)` duruyor.
  - `proguard-rules.pro`'ya NextLib keep kuralları eklendi.
- **Sesi çalınamayan kaynak (`AudioPlayabilityRules`, Android'siz):** ses parçası var ama hiçbiri desteklenmiyorsa elenir; parça yoksa ya da biri destekliyse elenmez.
  - `SourceCoordinator.onAudioUnplayable`: otomatik modda `StreamVerdicts` ile reddedilir ve kaldığı saniyeden sıradaki kaynağa geçilir. Kaynak hatası ya da kara liste sayılmaz.
  - Elle seçilmişse (ya da "Kaynağı otomatik seç" kapalıysa) yalnızca ileti çıkar: "Bu kaynağın sesi bu cihazda çalınamıyor, başka kaynak dene" / "This source's audio can't be played on this device. Try another source.".
  - Ses parçası seçimine dokunulmaz.
- **Tanı logu (`PlayMatch`, adres yok):** `ses parçaları: <mime> <kanal>k dil=.. destek=.. seçili=..` ve `Ses çözücü: decoder=..`.
- **Diğer:** CLAUDE.md "Mimari kararlar"a FFmpeg istisnası eklendi. Sürüm 1.3.3 / versionCode 50.

## Testler
- `.\gradlew testDebugUnitTest`: **1545 test, 0 hata, 2 atlanan.** UpdateFlowTest bu çalıştırmada geçti.
- Yeni testler: `AudioPlayabilityRulesTest` (5) ve `SourceCoordinatorAudioTest` (3: otomatikte geçiş + aynı saniye + ret kaydı; elle seçimde yalnızca ileti; aynı kaynak için tek bildirim).

## APK
- **Önce:** 49.447.213 B. 1.3.2 release, 13 Kasım derlemesi; main yeniden derlenip ölçülmedi.
- **Sonra:** **68.012.196 B (+18,56 MB)**. Artış yerel kütüphanelerden: arm64 5,9 MB, armeabi-v7a 5,7 MB, x86_64 6,9 MB (sıkıştırılmış).
- `Play-1.3.3.apk` SHA-256: `5dbfca61f2b4fa02261e5c4220b2df7f0d9d1c9e951b8fb3dea04fb143273f8a`
- İmzalı R8 release; `adb install -r` ile üzerine kuruldu. Eklentiler ve ayarlar yerinde kaldı.

## Cihaz doğrulaması (yeni APK)
| Madde | Sonuç | Kanıt |
|---|---|---|
| 6a Jinbe 4K | Sesli. `Ses çözücü: decoder=ffmpegLavc63.1.101-eac3`; İngilizce DTS-HD elle seçilince `…-dca`. AudioFlinger'da Play izi 6 kanal 48 kHz aktif. Parçalar'da 4 ses parçası | `11`, `12`, `yeni_logcat.txt`, `yeni_audioflinger.txt` |
| 6a 4KHDHub 4K (İş 0'daki aynı 53,3 GB dosya) | Sesli, `…-dca` (İngilizce hatırlandı), 6 kanal aktif iz. İlk denemede 4K ilk kare 10 sn'de gelmedi ve elle seçim kuralıyla önceki kaynağa dönüldü (ses çözücü yine açılmıştı); ikinci denemede açıldı | `15`, `16`, `yeni_logcat.txt` |
| 6b Jinbe 1080p | Cihaz çözücüsü `OMX.google.aac.decoder`, AAC 2 kanal 44,1 kHz, FFmpeg'e düşmüyor, underrun 0 | `17`, `yeni_logcat.txt` |
| 6c 4K senkron / sarma | ~2 dk oynadı, ileri (16:34) ve geri (~9:00) sarıldı. Sardıktan sonraki 30'ar sn'de underrun ve hata yok, ses izi aktif. Normal oynatmada `AudioTrack … underrun` uyarıları var; bu emülatörün 4K HEVC/DV çözümünden geliyor (FFmpeg'den önce de saniyede 10–15 kare düşüyordu). Görüntü-ses senkronu emülatörde kulakla/gözle ölçülemedi | `13`, `14`, `yeni_logcat.txt` |
| 6d regresyon | Örümcek-Adam (FFmpeg eac3), Joker (AAC), Criminal Minds (AAC), Canlı ATV (AAC) 60'ar sn sesli; Geri çalışıyor. FATAL / VerifyError / UnsatisfiedLinkError: 0 | `r_20`–`r_23` |
| 6e indirme | Başlatıldı (312 MB / 1,2 GB), iptal edildi, liste boş, çökme yok. Dosya boyutu bilinen düz bir dosyaydı; HLS/DASH (DownloadHelper) yolu bu denemeyle kanıtlanmadı | `24`–`26` |

## Ses dili sırası (Kullanıcı kararı 2026-11-23)
FFmpeg eklenince bütün parçalar çalınabilir oldu ve oynatıcı dosyanın varsayılan parçasını (Hintçe) seçiyordu. Bunun yerine:
- **Sıra:** "Türkçe dublajı öncele" açıkken Türkçe → İngilizce → dosyanın varsayılanı; kapalıyken İngilizce → dosyanın varsayılanı.
- **Kural `AudioLanguagePolicy`** (Android'siz):
  - Yalnızca desteklenen parçalar aday olur.
  - Türkçe `TurkishAudioTrackRules.isTurkishTrack` ile, İngilizce `SubtitleLanguages.normalize(dil) == "en"` ile (en/eng/English) tanınır.
  - Hiçbir parçada dil bilgisi yoksa karar verilmez; öğrenilmiş parça akışı aynen kalır.
- **İlk seçim:** `PlaybackController.setPreferredAudioLanguages("tr","en")` (dublaj önceliği kapalıysa yalnızca "en"). Böylece Hintçe hiç açılmaz.
- **`syncAudio`:** elle seçim yoksa ve Türkçe doğrulayıcı işi üstlenmediyse kural uygulanır.
- **Dokunulmayanlar:** kullanıcının elle seçtiği ses, öğrenilmiş parça, Türkçe doğrulayıcı ve çevrimdışı kopya. Çevrimdışında ne tercih parametresi ne yeni kural uygulanır; eski Türkçe dalı aynen çalışır.
- **Testler:** `AudioLanguagePolicyTest` 9 test: istenen (a)–(g) + "hedef zaten seçili" + dil sırası. Tüm paket **1554 test, 0 hata, 2 atlanan.**
- **APK:** 68.010.252 B, SHA-256 `dfb923548f5e7ae5ece95035bddc7f356a7b0a82e4d853f256f5f5618c1d6f8f`, `adb install -r` ile kuruldu.

| Madde | Seçilen ses | Kanıt |
|---|---|---|
| Ayar | "Türkçe dublajı öncele" açık | `dil_00_ayar.png` |
| The Odyssey, otomatik başlangıç (4KHDHub 1080p: en AAC + hi/ta/te E-AC-3) | **en** AAC, cihaz çözücüsü | `dil_logcat.txt` |
| 3 Jinbe 4K (hi/ta/te E-AC-3 + en DTS-HD) | **en** DTS-HD (`ffmpeg…-dca`); E-AC-3 çözücüsü hiç açılmadı. Aktif akış 6 kanal 48 kHz | `dil_01_jinbe4k_parcalar.png`, `dil_audioflinger.txt` |
| 3 4KHDHub 4K (aynı parça seti, 53,3 GB) | **en** DTS-HD; aktif akış 6 kanal | `dil_02_4khub4k_oynatici.png`, `dil_02_4khub4k_parcalar.png` |
| 3 Örümcek-Adam (4KHDHub, hi/ta/te/en E-AC-3) | **en** E-AC-3; aktif akış | `dil_03_orumcek_adam_parcalar.png` |
| 4 Türkçe dublaj: Ters Yüz 2, Imu ("Dublaj / Altyazılı"; tr + en AAC) | **tr**; üst çubukta "Dublajlı (gömülü ses)" | `dil_04_tr_dublaj_oynatici.png`, `dil_04_tr_dublaj_parcalar.png` |
| 5 Elle Hintçe (4KHDHub 4K) | 30 sn sonra hâlâ **hi**; otomatik geri dönüş yok | `dil_05_elle_hintce_30sn.png` |
| 6 Joker | Reanime açıldı (en seçildi; eskiden ja), süre kontrolü 23,6 dk ≠ 122 dk ile elendi, Castle (und AAC) sesli | `dil_06_joker.png` |
| 6 Criminal Minds | Castle, und AAC, sesli, Geri çalışıyor | `dil_07_criminal_minds.png` |
| 6 Canlı A2 TV | und AAC, sesli, Geri çalışıyor | `dil_08_canli_a2.png` |
| 7 "Neler yeni" (Türkçe) | Yeni üç madde, taşma yok; 1.3 yazı ölçeğinde de sığıyor | `dil_09_neler_yeni_tr.png`, `dil_10_neler_yeni_tr_buyuk_yazi.png` |

Tüm turda FATAL / VerifyError / UnsatisfiedLinkError: 0 (`dil_logcat.txt`).

**"Neler yeni" görüntüsü nasıl alındı:** cihaz 1.3.3'ü daha önce "görüldü" kaydetmişti. Ayarlar silinmeden pencereyi yeniden açmak için kodu değiştirmeyen bir Gradle init betiğiyle yalnızca versionName'i "1.3.3-wn" olan geçici bir derleme kuruldu (aynı versionCode 50). Ardından gerçek 1.3.3 APK'sı (`dfb92354…`) yeniden kuruldu ve pencere "Sürüm 1.3.3" başlığıyla açıldı. Cihazda kalan sürüm gerçek 1.3.3'tür.

## Açık konular
1. **Parçalar paneli:** desteklenen parçalar dosyadaki teknik etiketiyle görünüyor. Kullanıcı kararıyla panelde kalabilir; değiştirilmedi.
2. **Emülatörde 4K:** ilk kare kimi zaman 10 sn'yi aşıyor. Elle seçimde önceki kaynağa dönülüyor, otomatikte sıradakine geçiliyor. Bu mevcut kural; gerçek cihazda denenmeli.
3. **Türkçe dublaj denemesi:** Imu akışında Türkçe parça zaten listede ilkti. Türkçenin varsayılan olmadığı, ikinci sırada olduğu çok sesli bir dosya bulunamadı; bu durum birim testle (b) güvence altında.

## Düzeltme: etiketsiz Türkçe dublaj (2026-11-28)
**Sorun:** "TR Dublaj" işaretli kaynakta Türkçe ses parçası etiketsizse (dil null / "und", adında Türkçe işareti yok) ve yanında etiketli İngilizce varsa, ses sırası kuralı İngilizceyi seçiyordu. Eskiden dosyanın varsayılanı, yani dublaj çalıyordu.

**Düzeltme:** `AudioLanguagePolicy.choose(tracks, preferDub, streamDubbed = false)`.
- `preferDub && streamDubbed` ise ve etiketli Türkçe parça yoksa İngilizceye geçilmez.
  - Dil bilgisi olmayan (null/boş/"und") desteklenen ilk parça seçilir; zaten seçiliyse dokunulmaz.
  - Öyle parça yoksa dosyanın varsayılanı kalır.
- Etiketli Türkçe varsa her zamanki gibi o seçilir. `streamDubbed` false iken davranış değişmedi.
- `PlayerActivity.syncAudio()` çağrısı `streamDubbed = currentStream?.dubbed == true` verir. `setPreferredAudioLanguages` aynen kaldı.

**Testler:** `AudioLanguagePolicyTest` (h)–(m) eklendi, toplam 15. Tüm paket **1560 test, 0 hata, 2 atlanan**.

**APK:** `Play-1.3.3.apk` SHA-256 `1128520631bcfa2c0f1737b66e9dbdfc71bd7c80d29da4d494ad32192184a22d`. `adb install -r` ile kuruldu.

**Cihaz turu:** kanıt `etk_*.png` ve `etk_logcat.txt` (yalnızca "ses parçaları" ve "Ses çözücü" satırları, adres yok).

| İçerik | Seçilen ses | Kanıt |
|---|---|---|
| Ters Yüz 2 (otomatik başlangıç Imu'ya geçti, "Dublajlı (gömülü ses)") | **tr** AAC | `etk_01_ters_yuz2_imu_parcalar.png` |
| The Odyssey → Jinbe 4K (hi/ta/te E-AC-3 + en DTS-HD) | **en** DTS-HD (`ffmpeg…-dca`) | `etk_02_jinbe4k_parcalar.png` |
| Örümcek-Adam (4KHDHub; hi/ta/te/en E-AC-3, Türkçe yok) | **en** E-AC-3 7.1 | `etk_03_orumcek_adam_parcalar.png` |
| Joker | Reanime (ja/en → en) süre uyuşmazlığıyla elendi; Castle (und AAC) 60 sn oynadı, Geri çalıştı | `etk_04_joker.png` |
| Criminal Minds S1 B7 | Castle (und AAC) 60 sn oynadı, Geri çalıştı | `etk_05_criminal_minds.png` |
| Canlı A2 TV HD | und AAC 60 sn; AudioFlinger'da Play izi aktif (stereo 48 kHz, underrun 0), Geri çalıştı | `etk_06_canli_a2.png` |

- FATAL / VerifyError / UnsatisfiedLinkError: 0.
- Jinbe 4K'dan çıkılırken bir kez `ERROR_CODE_TIMEOUT` görüldü. Bu, emülatörde 4K akışta bilinen ağ/çözüm yavaşlığıdır.
- **Sınır:** yeni dal (dublajlı kaynak + etiketsiz Türkçe parça) cihazda denenemedi. Elde böyle bir dosya yok; Imu'daki parça `tr` etiketli. Bu durum yalnızca birim testlerle (h, i, k) güvence altında.
