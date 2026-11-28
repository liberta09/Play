# 1.3.3 ekleri: PR #5, PR #4, adres logu, "Neler yeni" (2026-11-28)

Kullanıcı kararı (2026-11-28): 1.3.3 yayını bekletildi. Ortak geliştiricinin iki PR'ı (#5 bölüm sonu, #4 dikey video) 1.3.3'e alındı. Sürüm 1.3.3 / versionCode 50 değişmedi.

## Commit'ler (dal `fix/4k-ses`)
| Commit | Yazar | Konu |
|---|---|---|
| `b0054df` | banadabi | PR #5: geçiş gerçek bölüm sonunu bekler, sonraki hedef korunur |
| `319ea79` | banadabi | PR #4: dikey/yatay tam ekran düğmesi |
| `9837375` | banadabi | PR #4: dikey videoyu algılama + Oynatıcı ayarı |
| `73e54b6` | liberta09 | Döndürme düğmesi ve dikey sığdırma PlayerScreen dışına (`OrientationAction.kt`) |
| `e9f3444` | liberta09 | "sahte kaynak" logu yalnızca sunucu adını yazar |
| `04d7506` | liberta09 | "Neler yeni" iki yeni madde (TR + EN) |

### Çakışma çözümleri
- **PR #5**
  - `PlayerActivity.prepareNext`: güncel hedef kurulumu yaş sınırı alanlarıyla birlikte korundu. Hemen ardına yalnızca `autoNext.rememberPreparedTarget(target)` eklendi.
  - `CHANGELOG`: iki tarafın metni de korundu.
  - `AutoNext.kt`, `AutoNextTest.kt`, `CLAUDE.md` ve `README` çakışmasız, PR'daki haliyle geldi.
- **PR #4**: güncel kod korundu ve PR'ın eklemeleri yanına kondu.
  - `PlaySession`: `liveChannelKey` alanının yanına yeni alanlar eklendi.
  - `PlayerActivity.onCreate`: canlı TV ve yaş sınırı kodu aynen kaldı. `orientationManuallyChosen` en başta atanıyor; ayar okuma ve yönün uygulanması `container` alındıktan sonra yapılıyor.
  - `PlayerScreen`: canlı TV ve Watch Party parametreleri korundu.
  - `SettingsRepository`, `PlayerSettingsScreen`, `strings` ve `yol-haritasi`: iki tarafın içeriği birleştirildi.
  - `CHANGELOG`: PR #4'ün ikinci commit'i kendi maddesini güncellediği için güncel metin alındı.
  - `VideoOrientationPolicyTest`: `PlaySession`'ı konumsal argümanla kuran testte araya `liveChannelKey` için `null` eklendi. Güncel kodda 10. parametre bu alan.
- **Satır sonları**: `PlayerActivity.kt` karışık satır sonlu. PR'ın LF eklediği ve değiştirdiği satırlar çevreyle aynı `\r\r\n` sonuna çevrildi. Diff'te yalnızca içerik değişikliği var.

### Uyarlama (R8 VerifyError dersi)
- Döndürme düğmesi `OrientationAction.kt` içindeki `OrientationToggleButton`'a taşındı. `Controls` içinde tek satırlık çağrı kaldı.
- Dikey sığdırma durumu `rememberPortraitResize` ile tutuluyor. Seçim mantığı `OrientationResize.display/next` içinde. `PlayerScreen`'de bu iş için iki satır kaldı.

## Testler
- `.\gradlew testDebugUnitTest`: **1574 test, 0 hata, 2 atlanan.**
  - Yeni testler: AutoNext testleri, `VideoOrientationPolicyTest`, `PortraitSessionTest`, `AutoPortraitSettingsTest`.
  - UpdateFlowTest bu turda kırılmadı.

## APK
- `Play-1.3.3.apk`: 68.016.916 B. İmzalı R8 release.
- SHA-256: `6340ae088048700ce2bd6b14c7be43881a042baa0659ae72a57b28f3038c1973`
- LDPlayer'a `adb install -r` ile kuruldu.

## Cihaz sonuçları
| Madde | Sonuç | Kanıt |
|---|---|---|
| VerifyError | Oynatıcı (Joker) açıldı, `VerifyError` 0 | `01` |
| Ayar | Geri sayım 10 sn; "Dikey videoları otomatik dikey oynat" açık | `02` |
| #5 kart | Criminal Minds S1 B7: 12:38:15'te 42:47/43:41 konumuna sarıldı. Kart 12:38:56–57 arasında "9 sn" ile çıktı ve video konumuyla azaldı. 12:39:06'da "1 sn" iken bölüm hâlâ oynuyordu. Yeni PlayerActivity (S1 B8) 12:39:06.340'ta açıldı; beklenen bitiş ≈12:39:09'du | `03_*`, `logcat_temiz.txt` |
| #5 doğal son (2 kez daha) | B8: 12:41:43'te 42:53/43:18 (25 sn kala), B9 12:42:08.538'de açıldı. B9: 41:53/42:18, B10 25 sn sonra (12:42:56.640) açıldı. Geçişler süre bitince oldu | `06`, `06b` |
| #5 duraklatma | Kart açıkken duraklatıldı. Sayaç 12:41:21'de ve 5 sn sonra da "9 sn" gösterdi (konum 43:10) | `04` |
| #5 geri sarma | İki kez 10 sn geri sarıldı (42:50). Kart kapandı, oynarken de çıkmadı | `05` |
| #5 Şimdi oynat | Kart "9 sn" iken basıldı (12:44:33.380 cihaz saati). Yeni PlayerActivity (S1 B11) 95 ms sonra açıldı | `07` |
| Bölümler paneli | Panel açıldı ("Şimdi oynuyor" 11. bölüm). 13. bölüm seçildi: "S1 B13 için kaynak aranıyor…" sırasında B11 oynadı, sonra B13 açıldı | `08`, `09` |
| #4 otomatik dikey | 1080×1920 test videosu ekranı dikeye çevirdi (ROTATION_270) | `13`, `14` |
| #4 düğme | Yataya geçti (ROTATION_0), sonra yeniden dikeye döndü (ROTATION_270) | `16`, `17` |
| #4 boyutlandırma | Dikeyde "Doldur (kırp)" seçildi. DataStore'a `resize_mode` yazılmadı; yatayda video "Sığdır" ile göründü | `15`, `16` |
| #4 ayar kapalı | Anahtar kapatılınca (`auto_portrait_videos=false`) aynı dikey video yatayda kaldı (ROTATION_0). Anahtar sonra tekrar açıldı (`true`) | `18`, `19`, `20` |
| #4 yatay film | Joker yatayda açıldı (ROTATION_0). Düğme sağ üstte görünür; basılmadıkça yön değişmedi | `01` |
| 4K ses | Jinbe 4K: **İngilizce** DTS-HD (`ffmpeg…-dca`). Play'in 6 kanallı ses izi aktif, 30 sn+ | `24`, `logcat_temiz.txt` |
| Regresyon | Örümcek-Adam (FFmpeg eac3, 8 kanal iz aktif), Joker (Castle AAC), Criminal Minds (DiziBOX AAC) ve Canlı A2 TV (AAC) 60'ar sn+ oynadı; Play'in ses izi aktif, Geri çalıştı | `10`, `12`, `logcat_temiz.txt` |
| Çökme | Tüm turda FATAL / VerifyError / UnsatisfiedLinkError: 0 | `logcat_temiz.txt` |
| "Neler yeni" | 5 madde TR'de sığıyor; 1,3 yazı ölçeğinde ve EN'de taşma yok | `21`, `22`, `23` |
| İş 3 | Yeni log satırı yalnızca sunucuyu yazıyor (`… (sahte kaynak): 127.0.0.1 (12261432B)`) | test paketi günlüğü |

## Sınırlar ve sapmalar
- **Dikey video testi release APK'da yapılamadı.** Release'te yerel bir videoyu Play'e vermenin yolu yok:
  - Addon ve oynatıcı ağı (`PlayNetworkGuard`) loopback ve özel adresleri engelliyor.
  - `play_test_video` girişi yalnızca debug derlemede var.
  - Bu yüzden aynı kod, bir init betiğiyle (`applicationIdSuffix ".dikeytest"`, kodda değişiklik yok) ayrı paket adlı debug derlemesi olarak yan yana kuruldu.
  - `play_introdb_endpoint` ile yalnızca 8799 numaralı loopback portu açıldı ve `adb reverse` ile video verildi.
  - Bu derleme R8'den geçmedi. R8/VerifyError kontrolü asıl release APK'da yapıldı.
  - `com.kaan.play.dikeytest` paketi cihazda duruyor (kaldırma yasak olduğu için kaldırılmadı).
- Test videosu 4 Mbit/s ile ~31 MB üretildi. 12 MB'lık ilk sürüm, film için 20 MB "sahte kaynak" alt sınırına takıldı.
- Örümcek-Adam ekran görüntüsünde video alanı siyah çıktı. Konum ilerliyordu (26:29) ve ses izi aktifti; siyah görüntünün nedeni ayrıca doğrulanmadı.
- "Neler yeni" görüntüsü için test paketindeki `last_whats_new_version` değeri `1.3.2` yapıldı. Asıl uygulamanın verisine dokunulmadı.
- Sistem yazı ölçeği 1,3'e alınıp 1,0'a geri döndürüldü.
- Asıl uygulamada test sırasında Criminal Minds S1 B13'e kadar ilerlendi; "devam et" kaydı değişti.
- Jinbe ilk denemede "yanıt vermedi" (site tarafı). ~20 dk sonra ikinci denemede geldi ve oynadı (`11`, `24`).
