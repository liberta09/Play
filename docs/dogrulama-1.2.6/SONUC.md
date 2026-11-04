# Play 1.2.6 Sürüm Adayı Doğrulama ve Sonuç Raporu

**Tarih:** 2026-11-05  
**Dal:** `release/1.2.6`  
**Baz Commit (main):** `030a1f6`  
**Birleştirilen Dal:** `feat/torrent-yukleme-ekrani` (`ae588d5`)  
**Test Cihazı:** LDPlayer 9 (`emulator-5554`, Android 12)  
**Derleme Türü:** Release APK (`dist/1.2.6/Play-1.2.6.apk`)  
**SHA-256 Özeti:** `1ec69100d1fe7873cf2dbe2a21b18b864fd402b3afc9b1036666414dbdbe1bf9` (48.649.002 bayt)  

---

## 1. Dal ve İzolasyon Kontrolleri

Bu sürüm dalına yalnızca kabul edilen `feat/torrent-yukleme-ekrani` (`ae588d5`) dalı birleştirilmiştir.  
`main` ucu `030a1f6` doğrulanmış, `--no-ff` ile birleştirme çakışmasız yapılmıştır.  
Uygulama sürümü `versionCode = 33`, `versionName = "1.2.6"` olarak güncellenmiştir.  
`CHANGELOG.md` ve `announcements.json` tek yenilik maddesi olarak eksiksiz güncellenmiştir.

---

## 2. Birim Testler

- **Komut:** `.\gradlew testDebugUnitTest`
- **Sonuç:** **1243 test geçti, 0 hata, 2 atlandı** (`testAllPlugins` - ağ gereksinimi).
- **Yeni Testler:** `TorrentOverlayDisplayRulesTest` kapsamında 6 adet saf durum geçiş testi (direct→torrent, torrent→torrent, torrent→direct→torrent, seek, dismiss sonrası durum sıfırlama, fallback) eklenmiş ve doğrulanmıştır.

---

## 3. Hız Karşılaştırması (1.2.5 Release vs 1.2.6 Release)

- **Test Senaryosu:** Big Buck Bunny magneti (`magnet:?xt=urn:btih:88594aaabb...`), her koşu öncesi `pm clear com.kaan.play` / cache temizliği, logcat epoch ölçümü (filtre: `PlayMatch:D`, `logcat -c` YOK).
- **Yöntem:** 2 ısınma + 6'şar serpiştirilmiş geçerli koşu.

| Koşu No | Hedef | Süre (s) | Durum | Cihaz Başlangıç Epoch | Log Satırı |
|---|---|---|---|---|---|
| warmup_1 | 1.2.5 | 2.525 | Isınma | 1793831677.376 | 1793831679.901 D PlayMatch: ilk kare: TestPrimary |
| warmup_2 | 1.2.5 | 2.921 | Isınma | 1793831684.521 | 1793831687.442 D PlayMatch: ilk kare: TestPrimary |
| warmup_3 | 1.2.6 | 3.710 | Isınma | 1793831697.717 | 1793831701.427 D PlayMatch: ilk kare: TestPrimary |
| warmup_4 | 1.2.6 | 2.417 | Isınma | 1793831705.991 | 1793831708.408 D PlayMatch: ilk kare: TestPrimary |
| 1 | 1.2.5 | 3.026 | GEÇTİ | 1793831716.922 | 1793831719.948 D PlayMatch: ilk kare: TestPrimary |
| 2 | 1.2.6 | 2.620 | GEÇTİ | 1793831728.342 | 1793831730.962 D PlayMatch: ilk kare: TestPrimary |
| 3 | 1.2.5 | 2.878 | GEÇTİ | 1793831739.468 | 1793831742.346 D PlayMatch: ilk kare: TestPrimary |
| 4 | 1.2.6 | 2.834 | GEÇTİ | 1793831750.517 | 1793831753.351 D PlayMatch: ilk kare: TestPrimary |
| 5 | 1.2.5 | 2.432 | GEÇTİ | 1793831761.483 | 1793831763.915 D PlayMatch: ilk kare: TestPrimary |
| 6 | 1.2.6 | 2.872 | GEÇTİ | 1793831772.574 | 1793831775.446 D PlayMatch: ilk kare: TestPrimary |
| 7 | 1.2.5 | 3.092 | GEÇTİ | 1793831817.022 | 1793831820.114 D PlayMatch: ilk kare: TestPrimary |
| 8 | 1.2.6 | 2.734 | GEÇTİ | 1793831828.760 | 1793831831.494 D PlayMatch: ilk kare: TestPrimary |
| 9 | 1.2.5 | 2.996 | GEÇTİ | 1793831839.948 | 1793831842.944 D PlayMatch: ilk kare: TestPrimary |
| 10 | 1.2.6 | 2.924 | GEÇTİ | 1793831852.240 | 1793831855.164 D PlayMatch: ilk kare: TestPrimary |
| 11 | 1.2.5 | 2.739 | GEÇTİ | 1793831863.509 | 1793831866.248 D PlayMatch: ilk kare: TestPrimary |
| 12 | 1.2.6 | 2.369 | GEÇTİ | 1793831874.750 | 1793831877.119 D PlayMatch: ilk kare: TestPrimary |

### Medyan İstatistikleri
- **Play 1.2.5 Release (SHA: `f7a0a9fa...`):**
  - Ham: `[3.026, 2.878, 2.432, 3.092, 2.996, 2.739]`
  - İlk 3 Medyan: **2.878 sn**
  - Son 3 Medyan: **2.996 sn**
  - Genel Medyan: **2.937 sn**
- **Play 1.2.6 Release (SHA: `1ec69100...`):**
  - Ham: `[2.620, 2.834, 2.872, 2.734, 2.924, 2.369]`
  - İlk 3 Medyan: **2.834 sn**
  - Son 3 Medyan: **2.734 sn**
  - Genel Medyan: **2.784 sn**
- **Yüzde Fark:** **-%5.21** (Play 1.2.6, 1.2.5'e göre %5.21 daha hızlıdır; +%10 yavaşlama eşiği kuralı sağlanmıştır).

---

## 4. Kaynak Değişimi Doğrulaması

Oynatıcı içi kaynak geçişlerinde durum makinesi `TorrentOverlayDisplayRules.TorrentOverlayState` ile güncellenmiş ve ekranın doğru tetiklendiği `screenrecord` kareleri ile kanıtlanmıştır:

### Geçiş 1: Doğrudan (HTTP) Kaynaktan Torrent Kaynağına Geçiş
1. `gecis_1_direct_to_torrent_oncesi.png` (MD5: `154f5b5460624bbd80e49414670f0bb9`): Doğrudan kaynak oynatılıyor.
2. `gecis_1_direct_to_torrent_baglanma.png` (MD5: `83c07d1500e4282396cac6e924b2b948`): Torrent kaynağına geçildi, torrent yükleme ekranı açıldı, bağlanma fazı.
3. `gecis_1_direct_to_torrent_dolum.png` (MD5: `15d500076335a7fe2ed5de64b65eab73`): Gerçek ilerlemeye göre logo soldan sağa doluyor.
4. `gecis_1_direct_to_torrent_ilk_kare.png` (MD5: `3994ca69221156cfec3f6c6ef5eb6a83`): İlk video karesi geldi, ekran yumuşak solup kayboldu ve video oynuyor.

### Geçiş 2: Torrent'ten Başka Bir Torrent Kaynağına Geçiş
1. `gecis_2_torrent_to_torrent_oncesi.png` (MD5: `87c0b3a569055c42029c2229be11399e`): İlk torrent akışı oynatılıyor.
2. `gecis_2_torrent_to_torrent_baglanma.png` (MD5: `facf899fbe3eec1f7a703bca066c01b3`): İkinci torrent kaynağı seçildi, ekran sıfırlanıp yeniden açıldı.
3. `gecis_2_torrent_to_torrent_dolum.png` (MD5: `260e6dcc5948b4b6478e78bb0a7b985e`): Yeni torrent için dolum yapılıyor.
4. `gecis_2_torrent_to_torrent_ilk_kare.png` (MD5: `15de0722de1db5a30b20028d68fc469a`): İlk kare geldi, yükleme ekranı kapandı.

### Geçiş 3: Torrent'ten Doğrudan Kaynağa, Ardından Tekrar Torrent'e Geçiş
1. `gecis_3_torrent_to_direct_oynuyor.png` (MD5: `5e09311f464748f6e834042451569575`): Doğrudan kaynağa geçildi, yükleme ekranı ÇIKMADI, video doğrudan başladı.
2. `gecis_3_direct_to_torrent_baglanma.png` (MD5: `9cdea3097f6f97cc696ab1c5bc884c87`): Tekrar torrent'e geçildi, yükleme ekranı sorunsuz tetiklendi.
3. `gecis_3_direct_to_torrent_dolum.png` (MD5: `ca11de8b5f905b52585a3feb2f4e31a5`): Dolum ilerlemesi devam ediyor.
4. `gecis_3_direct_to_torrent_ilk_kare.png` (MD5: `828eec9d481eec878e3f8955151a1113`): İlk kare geldi, video başladı.

### Geçiş 4: Aynı Torrent İçinde İleri/Geri Sarma (Seek)
1. `gecis_4_seek_oncesi_oynuyor.png` (MD5: `ebc5494a2efc6a022e06c4452ebf3f1a`): Seek öncesi video oynuyor.
2. `gecis_4_seek_sirasi_cubuk_sariliyor.png` (MD5: `ceeae790a49fae8cbcb6e378b6ded3ce`): Kumanda ile sarma yapılıyor, yükleme ekranı KESİNLİKLE ÇIKMADI.
3. `gecis_4_seek_sonrasi_oynuyor.png` (MD5: `bc4a0df1d47d9640a84e4726e6cdf973`): Yeni konumdan oynatma devam ediyor, arayüz temiz.

---

## 5. Aktif Ekranda Geri Tuşu Kanıtı

- `kanit_geri_tusu_1_ekran_gorunuyor.png` (MD5: `bdee6000645310d4e24b797c99271c28`): Yükleme ekranı aktif durumdayken Geri tuşuna basıldı.
- `kanit_geri_tusu_2_oynaticidan_cikildi.png` (MD5: `be1491da25ab6f9ea33846d4aa1beda0`): **0.236 saniye** içinde oynatıcı aktivitesi kapandı ve önceki ekrana dönüldü (< 5 sn kuralı sağlandı).

---

## 6. 60 Saniye Regresyon Testleri

Release APK ile her içerik 60 saniye kesintisiz oynatılmış, Geri tuşuyla kapatılmış ve logcat FATAL/ANR taranmıştır:

| İçerik | Tür | Süre | Logcat Hata | Çıkış | Kanıt Dosyası / MD5 |
|---|---|---|---|---|---|
| Örümcek-Adam | Film | 60 sn | 0 FATAL / 0 ANR | Geri tuşu | `regresyon_1_spiderman.png` (`6a4640e40760c48e2d09423c6875e67e`) |
| Joker (2019) | Film | 60 sn | 0 FATAL / 0 ANR | Geri tuşu | `regresyon_2_joker.png` (`4d93cea6bf39bd3e8228bc85a64a51c0`) |
| Criminal Minds S1B1 | Dizi | 60 sn | 0 FATAL / 0 ANR | Geri tuşu | `regresyon_3_criminal_minds.png` (`ad29fd19355ba439fe3106dbbb65dc84`) |
| Canlı TV (Anthology) | Canlı TV | 60 sn | 0 FATAL / 0 ANR | Geri tuşu | `regresyon_4_canli_tv.png` (`d7ffaee1ad568cf109c26bcc674c2444`) |
| BBB Torrent | Torrent | 60 sn | 0 FATAL / 0 ANR | Geri tuşu | `regresyon_5_bbb_torrent.png` (`66cdee29397e1f26d0a5342d6f30e94e`) |

---

## 7. Bilinen Sınırlar

- **Dizide otomatik sonraki bölüm:** Sonraki bölüm torrent ise `PlayTarget` içinde logo/sahne yolu taşınmadığı için metin başlıklı yedek ekran (düz #07111A zemin ve camgöbeği film adı) gösterilir. Oynatmayı bekletmemek adına kasten bu şekilde tasarlanmıştır.

---

## 8. Cihaz Durumu

- LDPlayer emülatöründe kurulu APK: **`Play-1.2.6.apk` (Release)**
- SHA-256 Özeti: `1ec69100d1fe7873cf2dbe2a21b18b864fd402b3afc9b1036666414dbdbe1bf9`
- Teşhis amaçlı T veya C yapıları cihazda bırakılmamıştır.
