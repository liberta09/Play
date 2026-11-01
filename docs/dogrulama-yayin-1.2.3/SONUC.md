# Play 1.2.3 Yayın Hazırlığı ve Doğrulama Sonuçları

**Tarih / Saat:** 2026-11-02 00:30:00 (TSİ)  
**Sürüm:** Play `1.2.3` (versionCode: `30`, R8/minify etkin release derlemesi)  
**APK Dosyası:** `Play-1.2.3.apk` (48.618.598 bayt)  
**Tam APK Yolu:** `C:\Projeler\Play\app\build\outputs\apk\release\Play-1.2.3.apk`  
**SHA-256 Dosyası:** `C:\Projeler\Play\app\build\outputs\apk\release\Play-1.2.3-SHA256.txt`  
**SHA-256:** `753d252c40faf2d93e8fb6ec0f1928f575d5842c51219411d77befe0500237ab`  
**İmza Sertifikası (SHA-256):** `5c7cd2bd91946586d125e59a848544f7db10b42ab0c0d3d6dd30c5b8c4e9f44f` (1.2.1 ve 1.2.2 ile %100 eşleşti)  
**Yayın Durumu:** **YAYIN HAZIR DEĞİL** (Kural gereği: Emülatör/cihaz açık olmadığı için 2a–2g canlı kontrol listesi tamamlanamamış, 'yapılmadı' olarak işlenmiştir; 'geçti' kanıtı olmayan madde kaldığı sürece yayın hazır değildir).

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
  - Sonuç: **BAŞARILI** (BUILD SUCCESSFUL in 1m 49s)
  - 28 actionable task (14 executed, 14 up-to-date)
  - Tüm `LocalProfileRepositoryTest`, `LocalSyncMergeTest`, `ProfileDataMigratorTest`, `PlayerBufferConfigTest`, `TorrentTest` vb. birim testler sıfır hata ile geçti.
- **Release APK Derlemesi (`./gradlew assembleRelease`):**
  - Sonuç: **BAŞARILI** (BUILD SUCCESSFUL in 2m 58s)
  - 54 actionable task (25 executed, 29 up-to-date)
  - R8 minification, kaynak küçültme ve imzalama hatasız tamamlandı.
  - Üretilen APK boyutu: 48.618.598 bayt.
  - İmza doğrulaması: `apksigner verify --print-certs` ile doğrulandı, SHA-256 sertifika özeti v1.2.1 ve v1.2.2 ile birebir aynı (`5c7cd2bd91946586d125e59a848544f7db10b42ab0c0d3d6dd30c5b8c4e9f44f`).

---

## 3. Kontrol Listesi Tablosu (Cihaz / Emülatör Testleri)

> [!WARNING]
> Cihaz bağlantı denetimi:
> `adb devices` çalıştırıldı -> Liste boş.
> `adb connect 127.0.0.1:5555` çalıştırıldı -> `cannot connect to 127.0.0.1:5555: Hedef makine etkin olarak reddettiğinden bağlantı kurulamadı. (10061)`
> Sistemde çalışan LDPlayer veya başka bir emülatör bulunamadı.
> Kural: *"Telefon yok/emülatör açılmıyorsa ilgili maddeyi 'yapılmadı' yaz, tahmin yazma."*

| # | Madde | Durum | Kanıt / Açıklama |
|---|-------|-------|------------------|
| 2a | 1.2.2 üzerine güncelleme kurulumu (imza aynı, uygulama açılıyor, geçmiş/favori/eklenti korunuyor) | **YAPILMADI** | Cihaz/emülatör bağlı değil (`adb connect 127.0.0.1:5555` bağlantı reddedildi 10061). İmza sertifikası SHA-256 (`5c7cd2bd91946586d125e59a848544f7db10b42ab0c0d3d6dd30c5b8c4e9f44f`) v1.2.2 ile statik olarak eşleşti ancak canlı kurulum denenemedi. |
| 2b | Örümcek-Adam, Joker, Criminal Minds: kaynak listesi geliyor, oynatma başlıyor (ilk kare süresi) | **YAPILMADI** | Cihaz/emülatör bağlı değil (`adb devices` boş). |
| 2c | Canlı kanal: 60 sn oynatma, geri tuşu kapatıyor, çökme yok | **YAPILMADI** | Cihaz/emülatör bağlı değil (`adb devices` boş). |
| 2d | Torrent kaynağı açılıyor (Civil War FLUX veya başka; donma/çökme yok mu) | **YAPILMADI** | Cihaz/emülatör bağlı değil (`adb devices` boş). |
| 2e | TV çarpısı: Kütüphane > Geçmiş: SAĞ ile çarpıya geç, TAMAM ile sil, SOL ile satıra dön | **YAPILMADI** | Cihaz/emülatör bağlı değil (`adb devices` boş). (Birim testleri ve kod incelemesi `fix/tv-gecmis-carpi` kapsamında doğrulanmıştır). |
| 2f | PROFİL ÇOĞALMA SENARYOSU: 3 "Varsayılan" profil, eşitleme/dışa aktarma, tek profil kalması, geçmiş/favori korunması | **YAPILMADI** | Canlı arayüz/cihaz testi yapılamadı (`adb devices` boş). Ancak Room ve migration mantığı `ProfileDataMigratorTest` ve `LocalSyncMergeTest` birim testlerinde %100 başarıyla doğrulanmıştır. |
| 2g | Açılışta ve oynatıcıda 60 sn boyunca logcat'te FATAL/AndroidRuntime yok | **YAPILMADI** | Cihaz/emülatör bağlı değil (`adb devices` boş). |

---

## 4. Bilinen Sınırlar

1. **Canlı Cihaz Doğrulaması:** LDPlayer emülatörü (127.0.0.1:5555) çalışır durumda olmadığından ve fiziksel cihaz bağlanmadığından, canlı UI ve oynatıcı testleri (2a–2g) icra edilememiştir.
2. **Yayın Kararı:** Şartnamedeki *"Bir madde kalırsa yayın hazır DEĞİL de"* ve *"BAŞARISIZ OLAN HER MADDEYİ AÇIKÇA YAZ; 'geçti' demek için kanıt şart"* kuralları gereğince, sürüm **YAYIN HAZIR DEĞİL** statüsündedir. Kullanıcı emülatörü başlattığında kontrol listesi maddeleri 2a–2g adım adım icra edilip kanıtları toplandıktan sonra nihai yayın yapılabilir.

---

## 5. Dosya Yolları ve Özet

- **Release APK:** `C:\Projeler\Play\app\build\outputs\apk\release\Play-1.2.3.apk`
- **SHA-256 Dosyası:** `C:\Projeler\Play\app\build\outputs\apk\release\Play-1.2.3-SHA256.txt`
- **Git Dalı:** `release/1.2.3`
