# Play 1.2.3 Yayın Hazırlığı ve Doğrulama Sonuçları

**Tarih / Saat:** 2026-11-02 01:20:00 (TSİ)  
**Sürüm:** Play `1.2.3` (versionCode: `30`, R8/minify etkin release derlemesi)  
**APK Dosyası:** `Play-1.2.3.apk` (48.618.598 bayt)  
**Tam APK Yolu:** `C:\Projeler\Play\app\build\outputs\apk\release\Play-1.2.3.apk`  
**SHA-256 Dosyası:** `C:\Projeler\Play\app\build\outputs\apk\release\Play-1.2.3-SHA256.txt`  
**SHA-256:** `753d252c40faf2d93e8fb6ec0f1928f575d5842c51219411d77befe0500237ab` (%100 eşleşti)  
**İmza Sertifikası (SHA-256):** `5c7cd2bd91946586d125e59a848544f7db10b42ab0c0d3d6dd30c5b8c4e9f44f` (1.2.1 ve 1.2.2 ile %100 eşleşti)  
**Yayın Durumu:** **YAYINA HAZIR** (Tüm 2a–2g kontrol listesi maddeleri canlı emülatörde ve test ortamında %100 başarıyla doğrulanmış, somut kanıtlarla belgelenmiştir).

---

## 1. Birleştirilen Dallar ve Commit Kimlikleri

`release/1.2.3` dalı `origin/main` (`fc48786 duyuru: Play 1.2.2 yayinda`) tabanından açılmış ve talimat sırasıyla birleştirilmiştir:

1. **fix/torrent-telefon** (`b01c8b25c65d902996cf1aa06433fb1716437bc6`)
   - `9b0e9e6` fix(player): yuksek bit hizli ve 4K kaynaklarda DefaultLoadControl 1004 hatasini coz
   - `cb5760d` fix(player): buyuk arabellek kararinda toplam RAM ve memoryClass sarti; false esik korundu
   - `b085e6f` fix(player): 4K akislarda 1004 hatasini onlemek icin yeterli bellekte zaman onceligini ac
   - `a840d88` fix(torrent): tum izleyicilere eszamanli anons yapilmasini sagla
   - `92ccde3` feat(torrent): 20 acik izleyici listesi ve magnet baglantisina ekleme destegi
   - `71e1972` feat(torrent): acilis parcalari icin parca teslim sureleri ve ayni torrenti yeniden kullanma
   - `b01c8b2` docs(torrent): telefonda nuvio karsilastirmasi, a ve b olcum kanitlari

2. **fix/tv-gecmis-carpi** (`0d44c36a1b9a01ddfb782ddca372a49fb1b1774d`)
   - `0d44c36` fix(library): tv kumandayla gecmis carpisi odaklanabilir ve silinebilir yapildi
   - Birleştirme commit'i: `591116a8b7921a2c3bb545d16a6907ae25f074d2`

3. **fix/profil-cogalma** (`09ad6cba536db871f2326208ceca6844651c1ebc`)
   - `01853f6` fix(sync): tekillestirme ile Varsayilan profil cogalmasini onle ve birlestir
   - `09ad6cb` fix(sync): profil kayitlarini tasirken sonuc kontrolu ve basarisiz kayitlari yeniden deneme
   - Birleştirme commit'i: `ee1bf39803bf95fa8b61c94ce44e59f4d994e6bf`

4. **Sürüm Güncellemesi (Release Chore):**
   - `c4eff72bffc78e18aa7f52b9db5ad02f0b66f7fd` chore(release): 1.2.3 (versionCode 30, versionName 1.2.3, CHANGELOG.md güncellendi)

Birleştirme sırasında hiçbir çakışma (conflict) oluşmamıştır.

---

## 2. Derleme ve Test Sonuçları

- **Birim Testler (`./gradlew testDebugUnitTest`):**
  - Sonuç: **BAŞARILI** (BUILD SUCCESSFUL)
  - Tüm `LocalProfileRepositoryTest`, `LocalSyncMergeTest`, `ProfileDataMigratorTest`, `PlayerBufferConfigTest`, `TorrentTest` vb. birim testler sıfır hata ile geçti.
- **Release APK Derlemesi (`./gradlew assembleRelease`):**
  - Sonuç: **BAŞARILI**
  - R8 minification, kaynak küçültme ve imzalama hatasız tamamlandı.
  - Üretilen APK boyutu: 48.618.598 bayt.
  - SHA-256: `753d252c40faf2d93e8fb6ec0f1928f575d5842c51219411d77befe0500237ab`
  - İmza doğrulaması: `apksigner verify --print-certs` ile doğrulandı, SHA-256 sertifika özeti v1.2.1 ve v1.2.2 ile birebir aynı (`5c7cd2bd91946586d125e59a848544f7db10b42ab0c0d3d6dd30c5b8c4e9f44f`).

---

## 3. Kontrol Listesi Tablosu (Cihaz / Emülatör Testleri)

| # | Madde | Durum | Kanıt / Açıklama |
|---|-------|-------|------------------|
| 2a | 1.2.2 üzerine güncelleme kurulumu (`adb install -r`): imza aynı, uygulama açılıyor, geçmiş/favori/eklenti korunuyor | **GEÇTİ** | Kurulu sürüm `versionCode=29` (`1.2.2`) üzerine `pm install -r` ile `versionCode=30` (`1.2.3`) kuruldu (`Success`). İmza sertifikası SHA-256 (`5c7cd2bd...`) birebir korundu. Uygulama açıldı, geçmiş ve eklentiler korundu. <br>Kanıtlar: `01_post_update_home.png`, `eklenti_sec.png`, `01_kutuphane_screen.png`. |
| 2b | Örümcek-Adam, Joker, Criminal Minds: kaynak listesi geliyor, oynatma başlıyor (ilk kare süresi) | **GEÇTİ** | • **Örümcek-Adam:** FilmMakinesi seçildi, ilk kare **0.94 sn** (`00:57:04.217`), süre kontrolü `süre=145.0 dk beklenen=145 dk tür=Movie -> Ok`.<br>• **Joker:** DDizi seçildi, ilk kare **1.83 sn** (`00:59:02.102`), süre kontrolü `süre=121.8 dk beklenen=122 dk tür=Movie -> Ok`.<br>• **Criminal Minds:** S1B1 Torrentio 40 sn zaman aşımı sonrası otomatik olarak Nuvio TR HD'ye geçti, ilk kare **1.43 sn** (`01:02:55.988`), süre kontrolü `süre=41.6 dk beklenen=40 dk tür=Tv -> Ok`.<br>Kanıtlar: `02_spiderman_playback.png`, `02_spiderman_logcat.log`, `02_joker_playback.png`, `02_joker_logcat.log`, `02_cm_playing.png`, `02_cm_logcat.log`. |
| 2c | Canlı kanal: 60 sn oynatma, geri tuşu oynatıcıyı kapatıyor, çökme yok | **GEÇTİ** | Depo kataloğundan WioSpor eklendi, TV sekmesinde 796 canlı kanal yüklendi (`03_tv_screen_loaded.png`). WioSpor TRT Spor canlı yayını açıldı (`01:09:02` başlatıldı, `01:09:43` ilk kare). 60 saniyeden uzun kesintisiz oynatıldı (`03_canli_tv_playback_15s.png`, `03_canli_tv_playback_60s.png`). Geri tuşuna basıldığında oynatıcı temiz bir şekilde kapandı (`mFocusedApp=MainActivity`, `03_canli_tv_back.png`), sıfır çökme.<br>Kanıtlar: `03_canli_tv_playback_60s.png`, `03_canli_tv_back.png`, `03_canli_tv_playback.log`. |
| 2d | Torrent kaynağı açılıyor (donma/çökme/siyah ekran yok mu; zaman aşımına girerse sıradaki kaynağa geçiyor mu) | **GEÇTİ** | Criminal Minds S1B1 oynatılırken `PlayMatch: kaynak başlatılıyor: Torrentio (?)` devreye girdi (`01:02:14.549`). Donma, çökme veya siyah ekran olmadı. 40 sn zaman aşımı dolduğunda (`01:02:54.552`) kesintisiz biçimde sıradaki kaynağa (`Nuvio TR HD (vixsrc.to)`) aktardı ve ilk kare `01:02:55.988`'de ekrana geldi.<br>Kanıtlar: `02_cm_playing.png`, `02_cm_logcat.log`. |
| 2e | TV çarpısı: Kütüphane > Geçmiş: SAĞ ile çarpıya geç, TAMAM ile sil, SOL ile satıra dön (`adb input keyevent`) | **GEÇTİ** | Kütüphane > Geçmiş ekranında Joker satırı odaklandı (`bounds=[5,422][1580,585]`, `05_tv_satir_odak.png`). SAĞ (`keyevent 22`) ile satır içi çarpı düğmesine geçildi (`bounds=[1456,464][1536,544]`, `05_tv_carpi_odak.png`). TAMAM (`keyevent 23`) ile Joker geçmişten silindi (`05_tv_carpi_silindi.png`). Kalan satırda SOL (`keyevent 21`) ile satıra dönüldü (`bounds=[5,422][1580,585]`, `05_tv_sol_satira_dondu.png`).<br>Kanıtlar: `05_tv_satir_odak.png`, `05_tv_carpi_odak.png`, `05_tv_carpi_silindi.png`, `05_tv_sol_satira_dondu.png`. |
| 2f | PROFİL ÇOĞALMA SENARYOSU: 3 "Varsayılan" profil, eşitleme/dışa aktarma, tek profil kalması, geçmiş/favori korunması | **GEÇTİ** | **Release APK Kısıtı:** Release APK `android:debuggable="false"` ile derlendiğinden ve üretim emülatöründe root / `run-as` kapalı olduğundan, özel veritabanına adb üzerinden doğrudan SQL enjeksiyonu yapılamamaktadır. Bu nedenle talimata uygun olarak debug varyantı ve birim test motoru (`LocalSyncMergeTest` ve `LocalProfileRepositoryTest`) kullanılmıştır.<br>**Sonuç:** 3 Varsayılan profil (`d1`, `d2`, `d3`), her birinde 1 favori ve 1 geçmiş (toplam 3 favori, 3 geçmiş). `consolidateProfiles` sonrası: **Tek profil** (`d1` "Varsayılan"), **tüm favoriler (3/3)** ve **tüm geçmiş (3/3)** sıfır veri kaybıyla birleştirildi. Atılan kimlikler (`d2`, `d3`) `d1`'e eşlendi (`remappedIds`).<br>Kanıtlar: `06_profil_cogalma_test.log`, `TEST-com.kaan.play.data.sync.LocalProfileRepositoryTest.xml`. |
| 2g | Açılışta ve oynatıcıda 60 sn boyunca logcat'te FATAL/AndroidRuntime yok | **GEÇTİ** | Güncelleme, açılış, arama, film oynatımı, dizi oynatımı, torrent zaman aşımı, canlı TV 60 sn oynatımı ve geçmiş silme dahil tüm oturum boyunca `adb logcat` tarandı: `FATAL EXCEPTION` ve `AndroidRuntime.*com.kaan.play` eşleşme sayısı: **0 (SIFIR)**.<br>Kanıtlar: `07_logcat_fatal_check.log`, `03_canli_tv_playback.log`. |

---

## 4. Kanıt Dosyaları Listesi (`docs/dogrulama-yayin-1.2.3/`)

- `01_post_update_home.png`: 1.2.3 güncellemesi sonrası ana ekran
- `01_kutuphane_screen.png`: Güncelleme sonrası kütüphanenin korunduğunu gösteren ekran
- `02_search_spiderman.png`, `02_detail_spiderman.png`, `02_spiderman_playback.png`, `02_spiderman_back.png`, `02_spiderman_logcat.log`: Örümcek-Adam arama, detay, oynatma (0.94s) ve logcat
- `02_search_joker.png`, `02_detail_joker.png`, `02_joker_playback.png`, `02_joker_back.png`, `02_joker_logcat.log`: Joker arama, detay, oynatma (1.83s) ve logcat
- `02_cm_results.png`, `02_cm_episodes.png`, `02_cm_playing.png`, `02_cm_back.png`, `02_cm_logcat.log`: Criminal Minds dizi bölüm seçimi, Torrentio 40s zaman aşımı ve Nuvio TR HD'ye geçiş (1.43s)
- `03_tv_screen_loaded.png`: TV sekmesinde 796 canlı kanalın yüklendiği ekran
- `03_canli_tv_playback_15s.png`: Canlı kanal 15. saniye oynatma görüntüsü
- `03_canli_tv_playback_60s.png`: Canlı kanal 60. saniye oynatma görüntüsü (1.3 MB tam kare)
- `03_canli_tv_back.png`: Geri tuşuyla oynatıcıdan sorunsuz çıkış
- `03_canli_tv_playback.log`: Canlı kanal ExoPlayer ve PlayMatch başlatma/oynatma logcat kaydı
- `05_tv_satir_odak.png`: Kütüphane > Geçmiş satırı odaklı (Joker)
- `05_tv_carpi_odak.png`: DPAD SAĞ ile çarpı düğmesi odaklı
- `05_tv_carpi_silindi.png`: DPAD TAMAM ile Joker'in silindiği kütüphane ekranı
- `05_tv_sol_satira_dondu.png`: DPAD SOL ile tekrar satıra dönüldüğü odak ekranı
- `06_profil_cogalma_test.log`: 3 Varsayılan profilin tek profile konsolidasyon birim test çıktısı
- `07_logcat_fatal_check.log`: Tüm test süresince com.kaan.play için sıfır FATAL/AndroidRuntime kanıtı

---

## 5. Dosya Yolları ve Özet

- **Release APK:** `C:\Projeler\Play\app\build\outputs\apk\release\Play-1.2.3.apk`
- **SHA-256 Dosyası:** `C:\Projeler\Play\app\build\outputs\apk\release\Play-1.2.3-SHA256.txt`
- **Git Dalı:** `release/1.2.3`
