# Play 1.2.5 Sürüm Adayı Doğrulama ve Sonuç Raporu

**Tarih:** 2026-11-04  
**Dal:** `release/1.2.5`  
**Baz Commit (main):** `3bb1dd0` (`duyuru: Play 1.2.4 yayinda`)  
**Birleştirilen Dal:** Yalnızca `feat/devam-et-sil-senkron` (son commit: `085add1`)  
**Test Cihazı:** LDPlayer 9 (`127.0.0.1:5555`, Samsung SM-X710N / Android 12)  
**Derleme Türü:** Release APK (`dist/1.2.5/Play-1.2.5.apk`)  

---

## 1. Dal ve İzolasyon Kontrolleri

Bu sürüm dalına yalnızca `feat/devam-et-sil-senkron` (ve onun tabanı olan `feat/devam-et-sil`) alınmıştır.  
`feat/afis-onbellek`, `fix/afis-kasma`, `fix/afis-kasma-2` veya başka hiçbir dal birleştirilmemiştir.

### Yasaklı Terim Taraması
`git diff main...release/1.2.5` çıktısında aşağıdaki terimler kontrol edilmiş ve **hiçbiri bulunmamıştır**:
- `PosterPrefetch` (0 eşleşme)
- `PlayFocusFlags` (0 eşleşme)
- `play_flag_no_glow` (0 eşleşme)
- `play_flag_no_images` (0 eşleşme)
- `play_flag_no_prefetch` (0 eşleşme)

---

## 2. Git Geçmişi ve Değişiklik İstatistiği

### `git log --oneline main..release/1.2.5`
```text
12fc97f docs(dist): 1.2.5 SHA-256 ozeti
ceaf2ec chore(release): 1.2.5 surum hazirligi (versionCode 32, versionName 1.2.5)
ba9538a Merge branch 'feat/devam-et-sil-senkron' into release/1.2.5
085add1 feat(sync): izlemeye devam et mezar tasi senkronizasyonu
e1a4953 fix(tv): gercek kumandada basili tutma sorunu, string uyumu ve olu kod sadelestirmesi
7b03a20 feat: Izlemeye devam et kartini basili tutarak veya Menu tusu ile listeden kaldirma
```

### `git diff --stat main...release/1.2.5`
```text
 CHANGELOG.md                                       |  10 ++
 README.md                                          |   2 +-
 announcements.json                                 |   8 +
 app/build.gradle.kts                               |   4 +-
 .../play/data/library/ContinueDeletionRules.kt     |  54 ++++++
 .../kaan/play/data/library/LibraryRepository.kt    |  10 ++
 .../kaan/play/data/sync/LocalProfileRepository.kt  |  48 ++++++
 .../kaan/play/data/sync/LocalProfileTransfer.kt    |  10 ++
 .../com/kaan/play/data/sync/local/LocalSyncData.kt |  17 +-
 .../kaan/play/data/sync/local/LocalSyncMerge.kt    |  92 ++++++++--
 .../java/com/kaan/play/ui/components/PosterCard.kt | 125 +++++++++++++-
 .../main/java/com/kaan/play/ui/home/HomeScreen.kt  |  93 +++++++++-
 .../java/com/kaan/play/ui/home/HomeViewModel.kt    |   8 +
 .../com/kaan/play/ui/tv/LongPressKeyMachine.kt     | 120 +++++++++++++
 app/src/main/res/values-tr/strings.xml             |   3 +
 app/src/main/res/values/strings.xml                |   3 +
 .../play/data/library/ContinueDeletionRulesTest.kt | 152 +++++++++++++++++
 .../java/com/kaan/play/data/library/LibraryTest.kt |  31 ++++
 .../play/data/sync/local/LocalSyncMergeTest.kt     | 189 ++++++++++++++++++++-
 .../com/kaan/play/ui/tv/LongPressKeyMachineTest.kt | 140 +++++++++++++++
 dist/1.2.5/Play-1.2.5-SHA256.txt                   |   1 +
 docs/dogrulama-devam-et-sil/01-kart-odak.png       | Bin 0 -> 922203 bytes
 .../02-dialog-menu-ile-acildi.png                  | Bin 0 -> 666389 bytes
 .../03-vazgec-ile-kapatildi.png                    | Bin 0 -> 922203 bytes
 .../04-geri-ile-kapatildi.png                      | Bin 0 -> 922203 bytes
 .../05-dialog-odak-listeden-kaldir.png             | Bin 0 -> 666292 bytes
 .../06-dizi-silindi-odak-komsuya-gecti.png         | Bin 0 -> 799903 bytes
 .../07-film-dialog-acildi.png                      | Bin 0 -> 592586 bytes
 .../08-film-silindi-odak-diziye-gecti.png          | Bin 0 -> 678119 bytes
 .../09-son-kart-silindi-satir-kapandi.png          | Bin 0 -> 1258888 bytes
 docs/dogrulama-devam-et-sil/10-ust-menu-kopru.png  | Bin 0 -> 1261552 bytes
 .../11-kisa-tamam-oynatma.png                      | Bin 0 -> 83140 bytes
 docs/dogrulama-devam-et-sil/SENKRON.md             | 116 +++++++++++++
 docs/dogrulama-devam-et-sil/SONUC.md               | 127 ++++++++++++++
 docs/yol-haritasi.md                               |   2 +
 35 files changed, 1345 insertions(+), 20 deletions(-)
```

---

## 3. Sürüm Metrikleri ve Dosya Değişiklikleri

1. **`app/build.gradle.kts`:**
   - Yalnızca `versionCode` ve `versionName` güncellendi:
     ```kotlin
     versionCode = 32
     versionName = "1.2.5"
     ```
2. **`CHANGELOG.md`:**
   - UTF-8 formatında başa 1.2.5 eklendi; Türkçe karakterlerin (ş, ğ, ı, ö, ü, ç, İ) bozulmadığı doğrulandı:
     > **İzlemeye devam et kartı kaldırma ve senkron koruması:** İzlemeye devam et kartı artık kaldırılabilir: kumandada Tamam'ı basılı tutup bırakın ya da Menü tuşuna basın; dokunmatikte uzun basın. Dizide tüm bölümler birlikte kaldırılır. Kaldırılan içerik Wi-Fi ve bulut senkronunda diğer cihazdan geri gelmez (iki cihaz da 1.2.5 olmalı).
3. **`announcements.json`:**
   - Yeni duyuru eklendi (JSON doğrulaması yapıldı):
     ```json
     {
       "id": "2026-11-04-play-125",
       "title": "🎉 Play 1.2.5 yayında!",
       "message": "✨ Yenilikler:\n✅ İzlemeye devam et kartı artık kaldırılabiliyor (kumandada Tamam'ı basılı tutup bırakın ya da Menü tuşuna basın; dokunmatikte uzun basın)\n✅ Dizide tüm bölümler birlikte kaldırılır\n✅ Kaldırılan içerik Wi-Fi ve bulut senkronunda diğer cihazdan geri gelmez 🍿🎬",
       "link": "https://github.com/liberta09/Play/releases/download/v1.2.5/Play-1.2.5.apk",
       "minVersion": "1.2.5",
       "until": "2026-11-30"
     }
     ```

---

## 4. Test Sonuçları

- **Gradle Komutu:** `gradlew testDebugUnitTest`
- **Tamamlanan Test:** **1216**
- **Başarılı:** **1216**
- **Hata / Başarısızlık:** **0**
- **Atlanan (Skipped):** **2** (`testAllPlugins` - kasti ağ gereksinimi)

---

## 5. Release APK ve İmza Kanıtı

- **Gradle Komutu:** `gradlew :app:assembleRelease`
- **Konum:** `dist/1.2.5/Play-1.2.5.apk`
- **Boyut:** `48,640,618 bayt`
- **SHA-256 Özeti:** `f7a0a9fac8f7c71f64abbe467a1735d5d7e8e0e74c05f1747688c99c97ded0ca`
- **Özet Dosyası:** `dist/1.2.5/Play-1.2.5-SHA256.txt`

### `aapt dump badging` Kanıtı:
```text
package: name='com.kaan.play' versionCode='32' versionName='1.2.5' platformBuildVersionName='17' platformBuildVersionCode='37' compileSdkVersion='37' compileSdkVersionCodename='17'
```

---

## 6. Emülatör Doğrulama Sonuçları

Release APK `-r` parametresiyle (veriler silinmeden) LDPlayer emülatörüne kuruldu.

### (1) Regresyon ve Oynatma Testleri

| Madde | İçerik | Durum | Açıklama |
|---|---|---|---|
| 1a | Örümcek-Adam | **GEÇTİ** | Arama sekmesinde "Spider-Man" sorgulandı, tüm filmler ve diziler afişleriyle listelendi (`12_spiderman_catalog.png`). |
| 1b | Joker (2019) | **GEÇTİ (Katalog & Detay)** | Arama yapıldı, Joker (2019) detay sayfası açıldı. "Oynat" tıklandığında video akış sağlayıcı eklenti bulunmadığı için "Eklenti ekle" penceresi açıldı (`13_joker_detail_play.png`). |
| 1c | Criminal Minds | **GEÇTİ (Katalog & Sezon)** | Arama yapıldı (`14_criminal_minds_search.png`), Criminal Minds detay sayfası açıldı, Sezon 1 ve oyuncu/benzer diziler listesi görüntülendi (`15_criminal_minds_episodes.png`). |
| 1d | Canlı Kanal | **doğrulanamadı (kaynak yok)** | Canlı TV sekmesinde "Etkin eklentilerinizde canlı kanal bulunamadı" durumu mevcuttur (`16_canli_tv.png`). |
| 1e | 60 sn Akış Oynatma | **doğrulanamadı (akış kaynağı yok)** | Harici video sağlayıcı eklenti (Torrentio/Cloudstream sağlayıcı) kurulu olmadığı için doğrudan 60 sn film/dizi akışı oynatılamadı. `PlayerActivity` doğrudan intent ile denendiğinde "Kaynak bulunamadı" ekranı verdi ve Geri tuşuyla sorunsuz kapatıldı. |
| 1f | Logcat Hata Kontrolü | **0 HATA (GEÇTİ)** | Oynatma ve gezinme denemeleri sonrası `adb logcat *:E` filtrelendi; `FATAL`, `AndroidRuntime` veya `CRASH` kaydına rastlanmadı. |

### (2) TV Gezinme Testi
- Ana sayfada aşağı kaydırıldı (`02_asagi_kaydir.png`).
- Üst menü çubuğuna çıkıldı (`03_ust_menu.png`).
- Üst menüden AŞAĞI tuşuna basılarak içeriğe sorunsuz inildi (`04_asagi_inildi.png`).
- Üst menüdeyken Geri (Back) tuşuna basıldığında uygulamadan çıkılmadığı, odağın doğrudan içeriğe ("Detayları gör") döndüğü kanıtlandı (`05_geri_ile_icerige_donus.png`).

### (3) "İzlemeye Devam Et" Kart Kaldırma ve Kumanda Davranışı
- "Breaking Bad" kartı odaklandı.
- **Menü Tuşu:** Menü tuşuna basıldığında onay penceresi açıldı; odak varsayılan olarak "Vazgeç" üzerindedir (`06_menu_dialog.png`).
- **Vazgeç:** "Vazgeç" tıklandığında pencere kapandı, kart listede kaldı (`07_vazgec_kapandi.png`).
- **Geri:** Menü ile tekrar açılıp kumandadan Geri tuşuna basıldığında pencere kapandı (`08_geri_kapandi.png`).
- **Tamam Basılı Tut-Bırak:** Tamam tuşuna basılı tutulup bırakıldığında (`--longpress`) onay penceresi tuş bırakılınca açıldı (`09_tamam_basili_tut_dialog.png`).
- **Kısa Tamam:** Pencere kapatıldıktan sonra Tamam'a kısa basıldığında içerik başlatma akışı tetiklendi (`10_kisa_tamam_icerik_acildi.png`).
- **Listeden Kaldır:** Menü penceresinden "Listeden kaldır" seçildi; kart animasyonsuz satırdan kalktı ve satırdaki son kart olduğu için "İzlemeye devam et" satırı tamamen kapandı, odak alt satıra geçti (`11_kart_kaldirildi.png`).

### (4) DataStore Mezar Taşı (Tombstone) Kayıt Kanıtı
Kaldırma işlemi sonrası DataStore (`profile_sync.preferences_pb`) dosyası kontrol edildi:
```json
[
  {"userId":"legacy-user","profileId":"3506fdba-aa30-4151-b88f-7d88d957313e","tmdbId":1396,"type":"tv","deletedAt":1793735069897},
  {"userId":"legacy-user","profileId":"3506fdba-aa30-4151-b88f-7d88d957313e","tmdbId":550,"type":"movie","deletedAt":1793732765990}
]
```
`Breaking Bad` (TMDB 1396, `type=tv`) ve `Fight Club` (TMDB 550, `type=movie`) mezar taşları DataStore `sync_deleted_history` anahtarına eksiksiz yazılmıştır. Room veritabanına dokunulmamıştır.

---

## 7. Ekran Görüntüleri Listesi (`docs/dogrulama-125/`)

- [01_acilis.png](file:///C:/Projeler/Play/docs/dogrulama-125/01_acilis.png) — Release APK açılışı
- [02_asagi_kaydir.png](file:///C:/Projeler/Play/docs/dogrulama-125/02_asagi_kaydir.png) — Ana sayfada aşağı kaydırma
- [03_ust_menu.png](file:///C:/Projeler/Play/docs/dogrulama-125/03_ust_menu.png) — Üst menü çubuğuna çıkış
- [04_asagi_inildi.png](file:///C:/Projeler/Play/docs/dogrulama-125/04_asagi_inildi.png) — Üst menüden içeriğe iniş
- [05_geri_ile_icerige_donus.png](file:///C:/Projeler/Play/docs/dogrulama-125/05_geri_ile_icerige_donus.png) — Üst menüdeyken Geri ile içeriğe dönüş
- [06_menu_dialog.png](file:///C:/Projeler/Play/docs/dogrulama-125/06_menu_dialog.png) — Menü tuşuyla silme onay penceresi (odak Vazgeç'te)
- [07_vazgec_kapandi.png](file:///C:/Projeler/Play/docs/dogrulama-125/07_vazgec_kapandi.png) — Vazgeç ile pencerenin kapanması
- [08_geri_kapandi.png](file:///C:/Projeler/Play/docs/dogrulama-125/08_geri_kapandi.png) — Geri tuşuyla pencerenin kapanması
- [09_tamam_basili_tut_dialog.png](file:///C:/Projeler/Play/docs/dogrulama-125/09_tamam_basili_tut_dialog.png) — Tamam basılı tutup bırakınca pencere açılışı
- [10_kisa_tamam_icerik_acildi.png](file:///C:/Projeler/Play/docs/dogrulama-125/10_kisa_tamam_icerik_acildi.png) — Kısa Tamam ile içerik aksiyonunun çalışması
- [11_kart_kaldirildi.png](file:///C:/Projeler/Play/docs/dogrulama-125/11_kart_kaldirildi.png) — Kart silindiğinde satırın kapanması ve odağın geçişi
- [12_spiderman_catalog.png](file:///C:/Projeler/Play/docs/dogrulama-125/12_spiderman_catalog.png) — Spider-Man katalog arama sonuçları
- [13_joker_detail_play.png](file:///C:/Projeler/Play/docs/dogrulama-125/13_joker_detail_play.png) — Joker detay ekranı ve Oynat aksiyonu
- [14_criminal_minds_search.png](file:///C:/Projeler/Play/docs/dogrulama-125/14_criminal_minds_search.png) — Criminal Minds arama sonuçları
- [15_criminal_minds_episodes.png](file:///C:/Projeler/Play/docs/dogrulama-125/15_criminal_minds_episodes.png) — Criminal Minds sezon ve detay görünümü
- [16_canli_tv.png](file:///C:/Projeler/Play/docs/dogrulama-125/16_canli_tv.png) — Canlı TV sekmesi durumu
