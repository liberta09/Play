# Neon + kumanda taşıması: doğrulama sonucu

Dal: `ozellik/neon-dpad-1.2.1` (taban `d247b96`). Tarih: 2026-10-29.

## Cihaz ve yöntem (önemli)
- Asıl LDPlayer örneği (index 0, `127.0.0.1:5555`) **hiç değiştirilmedi** (kurulum/kaldırma/temizlik yok). İçindeki uygulama `versionCode=54`, bu dal 27; `adb install -r -t` ve `-r -d -t` `INSTALL_FAILED_VERSION_DOWNGRADE` verdi.
- Kullanıcı izniyle yalnızca **PlayNeon** kopyasında (LDPlayer index 1, `emulator-5556`) `pm uninstall -k com.kaan.play` yapıldı (veri korundu). Kopyadaki 54 sürümünün Room şeması 7, bu dalınki 6 olduğu için açılışta çöktü ("migration from 7 to 6"). Veri silinmedi: `databases/play.db` → `play.db.v7-kenara` olarak kenara taşındı, ayrıca `db_yedek_v7/play.db` kopyası var. Uygulama yeni (boş) veritabanıyla açıldı; HDFilmCehennemi ve DiziBOX depoları uygulama içinden yeniden eklendi.
- Doğrulama APK'sı **yalnızca test için** `versionCode=99` ile derlendi (depoya girmeyen Gradle init betiği; `build.gradle` ve dal kodu değişmedi). `lastUpdateTime` (son kurulum): `2026-10-29 18:33:49`. Sürüm adı 1.2.0.
- Kullanılan tuşlar: `adb input keyevent DPAD_*`, `DPAD_CENTER`, `ENTER`, `BACK` (fare/dokunma yok; metin yazmak için `input text`).

## Derleme/test
- `.\gradlew testDebugUnitTest assembleDebug` BUILD SUCCESSFUL (son kodla, `versionCode=27`).
- `git diff --stat d247b96..HEAD -- app/src/main/java/com/kaan/play/data` boş.

## Gerçek cihazda GÖRÜLENLER (ekran görüntüsü var)
| Madde | Kanıt |
|---|---|
| Profil seçimi ("Kimin izlediğini seçin"): ilk profil odakta, halka + parlama; ad beyaz | `21-profil-secimi-odak.png`, `02-profil-odak-*.png` |
| Profil kurulumu: D-pad ad/PIN alanından çıkabiliyor, Oluştur düğmesi odak alıyor | `03g…03i` |
| Ana sayfa: kapak, kategori başlığında dolgu parlaması, kart odağı büyüme + parlama | `22`, `23-asagi-01…08` |
| AŞAĞI sırayla iner (kapak → "Trend filmler" başlığı → kartlar → "Trend diziler" başlığı → …), takılma/atlama görülmedi | `23-asagi-01…08` |
| YUKARI en üstte üst menüye çıkar | `24b` |
| Üst menüde profil simgesi → SAĞ "Eklenti seç" hapı; hapta SOL profil simgesi | `24c`, `24d`, `24e` |
| Geri menüdeyken odağı içeriğe döndürür, uygulamadan çıkmaz (düzeltme sonrası) | `25a` → `25b` |
| Ayarlar (odak halkası, odak hafızası); Wi-Fi senkron kartı Ayarlar'da ve profil ekranında | `12c`, `19b`, `18e`, `02-profil-odak-3-wifi`, `09-wifi-senkron-karti-acik` |
| Oynatıcı ayarlarında intro atlama ayarları ("Atlama butonlarını göster", "Atlama bölümlerini otomatik geç", İntro/Özet/Jenerik/Önizleme) | `20-oynatici-ayar-3`, `20-oynatici-ayar-4` |
| Eklentiler; "Kurulu tüm depolar": çöp kutusu → yenile → Geri; silme penceresinde varsayılan odak İptal | `15`, `15b`, `15c` |
| Yüklü eklentiler (rowControls): satır → anahtar → satır | `17b`, `17c` |
| Depo Kataloğu: kart odağı, geri düğmesi | `18b`, `18d` |
| Ara: alan, süzgeç çipleri, menüye YUKARI | `26`, `26c`, `26d` |
| Kütüphane (boş; odaklanacak öğe yok) | `27`, `27b` |

## Kaynak bulma (data/** değişmedi; sonuç olduğu gibi)
- **Mobland** (dizi, S1 B1): HDFilmCehennemi isteği gitti, DiziBOX bir akış verdi (`dbx.molystream.org`), oynadı; süre kontrolü `59,3 dk, beklenen 60 dk → Ok` (`mobland-logcat.txt`, `31c`). HDFilmCehennemi akış döndürmeden, oynatma başladıktan ~10 sn sonra "oynatmaya öncelik için durduruluyor" ile iptal edildi (`JobCancellationException`); yani bu denemede HDFilmCehennemi'nden kaynak **görülmedi**.
- **Örümcek-Adam: Yepyeni Bir Gün** (film, 2026): 1. denemede ekranda "Bu içerik için kaynak bulunamadı." çıktı ve günlükte hiç `PlayPlugin`/`PlayMatch` satırı yazılmadı (`35c`, nedeni bulunamadı). Aynı filmde "Oynat" yeniden basılınca HDFilmCehennemi akışı ~13 sn'de bulundu, oynadı (Sony açılışı görüldü, `36`), süre kontrolü `145 dk, beklenen 145 dk → Ok` (`orumcek-logcat.txt`). DiziBOX'a film isteği gitmedi (dizi sitesi; günlükte yalnızca HDFilmCehennemi).
- Çözülemeyen/açıklanamayan: Örümcek-Adam 1. denemesinin neden günlüksüz "bulunamadı" verdiği. Bu dalın sebebi olduğunu gösteren bir kanıt yok (data/** aynı), ama tersini de kanıtlamadım.

## GÖRÜLMEYENLER / sınırlar
- Kütüphane'de dolu favori/geçmiş/indirme satırları ve odakları (veri yoktu).
- TV/Canlı TV sekmesi, Kaynak seçim listesi, oynatıcı içi paneller (yalnızca kodda `focusRing` eklendi, ekranda denenmedi).
- Dokunmatik kullanım; telefon dikey düzen; İngilizce dil; uzun Türkçe metinle taşma; büyük yazı boyutu.
- "Detay" sayfasında yatay cihazda "Oynat" (film) düğmesi büyüyünce sol kenara taşıyor (`34`), kapak çubuğunda pill alt başlığı ("Tümünü göster") "Eklenti seç" hapının arkasında kalıyor (`23-asagi-03`): görsel pürüz, düzeltilmedi.
- Sezon açılır menüsü seçimi sonrası odak bir süre görünmez (`29g`, `29h`): 3 tuş sonra geri geliyor; `DropdownMenu` kaynaklı olabilir, bu iş kapsamında incelenmedi.
- Profil kapısı: uygulama her açılışta profil seçtiriyor (bu dalda da, 517469a'da da farklı; bizim mantık korundu).
- Profil ekranlarındaki gömülü Türkçe metinler (önceden vardı) çevrilmedi.
