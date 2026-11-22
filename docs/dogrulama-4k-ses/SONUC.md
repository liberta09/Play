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

## Açık konular (yayın kararından önce)
1. **Varsayılan ses dili:** Play tercih edilen ses dili vermiyor; Media3 dosyanın varsayılan parçasını seçiyor. Eskiden bu cihazda yalnızca AAC çalınabildiği için İngilizce seçiliyordu; şimdi E-AC-3 de çalınabildiği için:
   - The Odyssey (4KHDHub 1080p, Jinbe 4K) ve Örümcek-Adam'da **Hintçe** açılıyor; İngilizce parça var.
   - Joker'de (Reanime, AAC) Japonca açılıyor; bu FFmpeg'den bağımsız, önceden de vardı.
   - E-AC-3 çözücüsü olan gerçek TV kutularında da aynı seçim zaten oluyordu. Öneri: oynatıcıya tercih edilen ses dili olarak uygulama dili, sonra İngilizce vermek. Bu iş kapsamında yapılmadı, karar kullanıcıda.
2. **Parçalar paneli:** desteklenen yeni parçalar dosyadaki etiketiyle görünüyor ("Dolby Digital Plus / 5.1 / 48 kHz / 640 kbps • E-AC3", "DTS-HD Master Audio … • DTS"). Arayüz kuralı 3 (teknik ayrıntı yok) açısından etiket sadeleştirmesi gerekebilir. Panelin bu davranışı bu işte değiştirilmedi.
3. **Açılış değişiklik notu:** "Bu sürümde neler yeni?" penceresi 1.3.3 için eski maddeleri gösteriyor (`10_acilis_neler_yeni.png`). Değişiklik notu güncellenmedi.
4. **Emülatörde 4K:** ilk kare kimi zaman 10 sn'yi aşıyor. Elle seçimde önceki kaynağa dönülüyor, otomatikte sıradakine geçiliyor. Bu mevcut kural; gerçek cihazda denenmeli.
