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
- Dokunmatik kullanım, telefon dikey düzen, İngilizce dil, büyük yazı boyutu 2. turda görüldü (aşağıda); uzun Türkçe metinle taşma ve gerçek TV hâlâ görülmedi.
- "Detay" sayfasında yatay cihazda "Oynat" (film) düğmesi büyüyünce sol kenara taşıyor (`34`), kapak çubuğunda pill alt başlığı ("Tümünü göster") "Eklenti seç" hapının arkasında kalıyor (`23-asagi-03`): görsel pürüz, düzeltilmedi.
- Sezon açılır menüsü seçimi sonrası odak bir süre görünmez (`29g`, `29h`): 3 tuş sonra geri geliyor; `DropdownMenu` kaynaklı olabilir, bu iş kapsamında incelenmedi.
- Profil kapısı: uygulama her açılışta profil seçtiriyor (bu dalda da, 517469a'da da farklı; bizim mantık korundu).
- Profil ekranlarındaki gömülü Türkçe metinler (önceden vardı) çevrilmedi.

---

# 2. tur (2026-10-29): 6 düzeltme + kaynak bulma taban testi

Cihaz: yalnızca **PlayNeon** (LDPlayer index 1, `emulator-5556`; asıl örneğe dokunulmadı). Test APK'sı yine `versionCode=99` init betiğiyle; dal `versionCode=27` / `1.2.0`. `git diff --stat d247b96..HEAD -- app/src/main/java/com/kaan/play/data` **boş**. Birim testler (`testDebugUnitTest`) geçti. Ekran: 1600x900/240dpi → dikey testte `wm size 900x1600` + `wm density 350` (411 dp genişlik; LDPlayer `1080x2400` isteğini 1080x1800'e kırptı, bu yüzden 900x1600 kullanıldı). İş bitince `wm size reset` / `wm density reset` (özgün: 1600x900, 240), `font_scale` 1.0, Dil "Sistem", Tema "Koyu", iki eklenti de açık.

## 1) Kopyalama kuralı
Arama: Netflix, Disney, Hotstar, Prime Video, Hikari, Stremio, "Nuvio Cyan" (`app/src/main`, büyük/küçük harf duyarsız).
- **Değişti:** `ProfileAvatar.kt` ("Netflix / Disney+ tarzı…" → "Özgün çizim avatar kataloğu."), `PlayFocus.kt` ("Nuvio Cyan (#00E5FF)" → "elektrik camgöbeği (#00E5FF)"), `ProviderDetailRules.kt` ("Stremio türü" → "Addon meta türü"). Commit `cc31626`.
- **Dokunulmadı (teknik/protokol, arayüzde görünmez):** `Stremio…` özel veri sınıfı adları (`ui/detail/ProviderDetailScreen.kt`, `ui/addons/AddonDetailScreen.kt`); `data/**` içindeki protokol adları/yorumları (`NuvioAddonCatalog`, `ProviderModels`, `PluginRepoManifest`, `AddonManifest`, `AddonUrl` içindeki `stremio://` şeması, `AppContainer` yorumu, `DefaultSubtitleAddons` içindeki üçüncü taraf alan adı `altyazi-stremio.online`) — `data/**` bu turda kilitli. `strings.xml` / `values-tr`: eşleşme **yok**. `assets/adhosts.txt` yalnızca alan adı listesidir (`shikarirevent.qpon`, `vinealdisneyoscars.cyou` alt dize eşleşmesi). Hikari, Hotstar, Prime Video, Netflix: başka eşleşme yok.

## 2) CLAUDE.md
"Ekranlar" ve "TV ve tablet uyumluluğu" bölümlerindeki alt/yan menü anlatımı 2026-10-29 kararına göre güncellendi (yüzen hap üst menü, köprüler `contentFocusRequester` / `topBarFocusRequester`, profil simgesinden SAĞ ile "Eklenti seç", `tvFocusableScaleGlow`). Commit `f27ac95`. Ayrıca `docs/yol-haritasi.md` karar günlüğüne satır eklendi (`ba63876`). Başka kural değişmedi. Not: CLAUDE.md'nin "Sağlayıcı merkezli ana sayfa" bölümündeki "alt menünün hemen üzerinde" ifadesi, istek yalnızca iki bölümü kapsadığı için dokunulmadan kaldı.

## 3) Açık tema
- `HomeScreen` radyal gradyanı ve `PlayTopBar` hap rengi (`0xE60A131F`) artık `ui/theme/Backdrop.kt` üzerinden: koyu temada eski sabit değerler, açık temada `MaterialTheme.colorScheme`. Üst menü seçili simge/parlama rengi `colorScheme.primary` (koyuda `#00E5FF`, aynı).
- **Koyu tema bire bir aynı:** 4 ekran (Ana sayfa, Ara, Ayarlar, Detay) önce/sonra, durum çubuğu hariç tüm piksellerde fark **0** (`39-koyu-*-once/sonra.png`). Sonraki düzeltmelerden sonra son kontrol: Ara ve Ayarlar yine **0 piksel fark**; Ana sayfa ve Detay'da fark var, çünkü TMDB trendi/kapak ve "devam et" sırası değişti (içerik farkı, tema değil).
- Açık temada PNG: Ana sayfa `38a`, Ayarlar `38b`, Detay `38c`, Ara `38e`, üst menü `38f`, profil seçimi dikey `38g`.
- **Bulunup düzeltilenler:** (a) açık Detay'da TMDB başlık logosu beyaz olduğu için görünmüyordu (`38d`) → açık temada metin başlık (`TitleOrLogo(logoVisible)`); (b) profil seçimi ekranı açık temada koyu bordo gradyan üstünde koyu yazıydı → `profileBackdropBrush()` (koyuda aynı); (c) Bulut hesabı kartında "E-posta ile Giriş Yap" başlığı **koyu temada da** siyah yazıyla görünmez kalıyordu (kartın `Surface` rengi saydam olduğundan içerik rengi çözülmüyordu) → `contentColor = onSurface`. Commitler `0f3a9a6`, `64c6a3c`, `2c1d03b`.
- Kalan (düzeltilmedi): açık temada kapak karuselinin üstündeki koyu gradyan ile durum çubuğu zemini arasında hafif bir açık bant görünür (okunur, simgeler görünür).

## 4) Dikey telefon (900x1600 @350dpi ≈ 411 dp)
- Üst hap menü 6 öğeyle **sığar** (menü genişliği ≈ %73); 320 dp genişlikte (`density 450`) menü neredeyse kenardan kenara ama taşmaz, üst üste binme yok (`43`). Durum çubuğuyla çakışma yok.
- Dokunmatik: Ana sayfa `40a`, Ara `40b`, Kütüphane `40c`, Ayarlar `40d`, Detay `40e/40f`, Profil seçimi `40g`. Kumanda (`keyevent`): Profil seçimi `41a`, Ana sayfa + üst menü odağı `41b`, Ara çipi `41c`, Kütüphane `41d`, Ayarlar `41e`. Her adımda odak halkası görünür; taşma/üst üste binme yok.
- İngilizce: Ayarlar `42a`; yazı boyutu 1.3: Ana sayfa `42b`, Ayarlar `42d`.
- **Bulunup düzeltilen:** yazı 1.3 + İngilizce'de "Poster URL" satırı "Poste…" diye kısalıyordu, uzun değer ("Custom poster template") başlığı eziyordu (`42c`) → `PlayCardRow`'da değer varken ağırlıklar 0,8 : 1,2, değer sağa yaslı; başlık tam görünür, değer "Custom poster t…" diye kısalır (`42d`). Normal yazıda (1.0) satır aynı (`42e`; yatay Ayarlar piksel farkı 0). Commit `460fc05`.
- **Bulunup düzeltilmeyen:** İngilizce arayüzde Türkçe gömülü metinler (Ayarlar'da "Profil seçimi / Profil oluştur ve geçiş yap", "Son profili hatırla", "Bulut hesabı ve senkronizasyon…"; Profil seçimi, Profil kurulumu, Bulut hesabı formları). `ui/` altında Türkçe karakterli ≈ 80 sabit metin var (en çok `ProfileChooserScreen` 31, `ProfileScreen` 28, `ProfileSetupScreen` 11, `SettingsScreen` 6). CLAUDE.md "hiçbir metin koda gömülmez" kuralına aykırı; bu turun kapsamı dışında, ayrı iş.
- "Eklenti seç" hapı dikeyde de kart altyazılarının üstüne biner (bilinen pürüz).

## 5) Kaynak bulma taban testi (düzeltme yok, yalnızca ölçüm)
Yöntem: `git worktree add ..\Play-taban d247b96` (saf v1.2.0; test için `versionCode=99`), PlayNeon'a kuruldu; sonra `ozellik/neon-dpad-1.2.1` derlemesi. Her denemede uygulama kapatılıp açıldı (`force-stop`), arama → içerik → Oynat (dizide 1. sezon 1. bölüm satırı); günlük `adb logcat -s PlayCs PlayMatch PlayPlugin` (`taban-testi/`). Ölçüt: günlükte `kaynak başlatılıyor` satırı = **bulundu**; ekranda "Bu içerik için kaynak bulunamadı." = **bulunamadı**. Otomasyon `uiautomator` dökümü + `input tap` ile yapıldı; yalnızca içerik detayı doğru açıldıktan sonra Oynat'a basılan denemeler geçerli sayıldı. Her ölçümde tek eklenti açık, diğeri kapalı bırakıldı (Mobland için ayrı ayrı). İş bitince iki eklenti de açıldı; worktree kaldırıldı.

| İçerik / eklenti | Taban (v1.2.0, `d247b96`) | Dal (`ozellik/neon-dpad-1.2.1`) |
|---|---|---|
| Örümcek-Adam: Yepyeni Bir Gün · HDFilmCehennemi | 3 geçerli denemede **3 bulundu**, 0 bulunamadı | 3 geçerli denemede **2 bulundu**, **1 bulunamadı** |
| Mobland S1 B1 · HDFilmCehennemi | 1/1 bulundu | 1/1 bulundu |
| Mobland S1 B1 · DiziBOX | 1/1 bulundu | 1/1 bulundu |

Ayrıntı:
- Taban: Örümcek-Adam denemeleri 1, 3, 4 geçerli (hepsi `kaynak başlatılıyor`); deneme 2'de otomasyon içerik detayını açamadı, **sayılmadı**. İlk otomasyon koşusunun (doğrulamasız betik) sonuçları geçersiz sayıldı ve silindi.
- Dal: deneme 1 "bulunamadı": günlükte yalnızca `HDFilmCehennemi: istek tmdbId=969681 tür=movie` satırı var, eklentiden akış/hata satırı yok; ekranda 5 sn içinde "Bu içerik için kaynak bulunamadı." + "Başka kaynak seç" görünür (günlük `taban-testi/dal-orumcek-1.txt`). Denemeler 2 ve 3: `kaynak başlatılıyor`, süre kontrolü Ok.
- Bu tablo sonucu olduğu gibi gösterir; deneme sayısı küçüktür (3 + 1 + 1), yorum yapılmadı. Not: 1. turdaki Örümcek-Adam 1. denemesinin günlüksüz "bulunamadı" belirtisi bu turda dalda bir kez daha görüldü (günlükte yalnızca istek satırı); tabanın 3 geçerli denemesinde görülmedi.
- Dosyalar: `taban-testi/*.txt` (günlükler), `*-sonuc-*.json` (ham sonuçlar; `taban-film-sonuc.json` 2. otomasyon koşusunun ham çıktısıdır: 2. deneme "geçersiz").

## 6) focusRing rengi
- `focusRing(color)` parametresini yok sayıyordu (halka/iç çizgi/parlama sabit `#00E5FF`). Şimdi `tvFocusableScaleGlow(glowColor)` halkada, iç çizgide ve dolgu parlamasında verilen rengi kullanır; verilmezse temanın vurgusu (`colorScheme.primary` = turkuaz; açık temada `#00838F`). Eski çağrı yerleri: `color` veren tek yer `ui/components/PlayButtons.kt` (3 çağrı, `onFilled()`); başka `focusRing(..., color)` çağrısı yok.
- Dolu turkuaz düğmede ters renk: `onFilled()` = `colorScheme.onPrimary` (koyuda `#021B24`, açıkta beyaz) ve `strong = true` ile halka 4/2 dp (öncekinde ince çizgi dolgudan ayırt edilmiyordu: `37c` önce, `37` / `37b` sonra). Commit `4e8f749`.
- Ekran görüntüsü: Profil seçimi'nde "Yeni Profil" dolu düğme odağı (`37-dolu-dugme-odak.png`, yakın plan `37b`).

## Bilinen pürüz (bu turda DÜZELTİLMEDİ, istek gereği)
- Detay'da yatay ekranda Oynat düğmesinin sola kayması / çok genişlemesi (`34`, `29b`, `38c`).
- "Eklenti seç" hapının "Tümünü göster"i (ve kart altyazılarını) örtmesi (`23-asagi-03`, dikeyde de).
- Sezon menüsü seçimi sonrası odağın ~3 tuş görünmemesi (`29g`, `29h`).

---

# 3. tur (2026-10-29/30): Örümcek-Adam · HDFilmCehennemi, 10'ar deneme

Yalnızca HDFilmCehennemi açık (DiziBOX kapalı). Sıra: taban 1–5, dal 1–5, taban 6–10, dal 6–10; her blokta ilgili APK `adb install -r -t -d` ile kuruldu (`versionCode=99` test derlemesi; taban = saf `d247b96`, dal = `ozellik/neon-dpad-1.2.1` kodu). Her denemede uygulama `force-stop` ile kapatılıp açıldı, arama → Detay → Oynat; Oynat'tan sonra en çok 45 sn beklendi; her deneme için Oynat anından başlayan 60 sn'lik `adb logcat -s PlayCs PlayMatch PlayPlugin PlayPluginNet` kaydı `taban-testi/olcum-<sürüm>-<no>.txt` dosyalarında, ham sonuçlar `olcum-*.json`.

Ölçütler: **BULUNDU** = günlükte `kaynak başlatılıyor` satırı; **BULUNAMADI** = ekranda "Bu içerik için kaynak bulunamadı." (45 sn içinde ne akış ne mesaj olsaydı yine BULUNAMADI/zaman aşımı yazılacaktı; olmadı); **GEÇERSİZ** = Detay açılmadı / otomasyon hatası (sayılmaz; bu turda **0** geçersiz). "Saniye (istek→başlama)": günlükte `istek` satırı ile `kaynak başlatılıyor` satırı arası. "Ekran sn": Oynat'a basıştan, uiautomator yoklaması sonucunda sonucun görüldüğü ana kadar (yoklama yavaştır, kaba değerdir).

| Sürüm | Deneme | Sonuç | Saniye (istek→başlama) | Ekran sn |
|---|---|---|---|---|
| taban | 1 | BULUNDU | 1,8 | 15,6 |
| taban | 2 | BULUNDU | 1,1 | 3,3 |
| taban | 3 | BULUNDU | 1,7 | 6,8 |
| taban | 4 | BULUNDU | 1,5 | 3,3 |
| taban | 5 | BULUNDU | 1,9 | 7,8 |
| taban | 6 | BULUNAMADI | – | 17,9 |
| taban | 7 | BULUNDU | 1,5 | 3,4 |
| taban | 8 | BULUNDU | 1,8 | 7,3 |
| taban | 9 | BULUNAMADI | – | 17,9 |
| taban | 10 | BULUNDU | 1,6 | 6,6 |
| dal | 1 | BULUNAMADI | – | 17,9 |
| dal | 2 | BULUNDU | 2,4 | 15,6 |
| dal | 3 | BULUNDU | 13,0 | 16,5 |
| dal | 4 | BULUNDU | 1,4 | 3,3 |
| dal | 5 | BULUNDU | 1,6 | 3,4 |
| dal | 6 | BULUNDU | 1,7 | 3,4 |
| dal | 7 | BULUNDU | 1,8 | 7,3 |
| dal | 8 | BULUNDU | 12,2 | 25,4 |
| dal | 9 | BULUNDU | 1,5 | 3,3 |
| dal | 10 | BULUNDU | 1,4 | 3,3 |

Özet: taban **8 BULUNDU / 2 BULUNAMADI**; dal **9 BULUNDU / 1 BULUNAMADI**; geçersiz 0. Dalın BULUNAMADI oranı tabandan kötü değil (eşik 7/10), bu yüzden istenen ek `git diff --stat` / iptal edebilecek değişiklik listesi **çıkarılmadı**.

BULUNAMADI denemelerinin günlükleri (yalnızca olgu):
- taban 6: `istek` + `PlayCs` + 3 ağ satırı (TMDB, hdfilmcehennemi.nl ×2); 60 sn boyunca başka satır yok; akış satırı yok.
- taban 9: `istek` + ağ satırları; hdfilmcehennemi.mobi'ye ilk yanıt ≈12,6 sn sonra, ikincisi ≈15,8 sn sonra; akış satırı yok.
- dal 1: `istek` + ağ satırları; hdfilmcehennemi.mobi yanıtı ≈12,7 sn sonra; akış satırı yok.
- Üç denemede de (ve 20 denemenin hiçbirinde) günlükte `JobCancellationException`, "iptal", zaman aşımı ya da `timeout` satırı **yok**. Ekranda mesajın görüldüğü an (≈17,9 sn) ile günlük akışı arasındaki ilişki yorumlanmadı.
- Başlangıcı uzun olan iki BULUNDU denemesinde (dal 3: 13,0 sn; dal 8: 12,2 sn) günlükte hdfilmcehennemi.nl satırlarından sonra ≈12 sn boşluk, sonra hdfilmcehennemi.mobi yanıtı ve akış satırı var; aynı ≈12 sn boşluk BULUNAMADI olan taban 9 ve dal 1 günlüklerinde de görülür (taban 6'da .mobi satırı hiç yok). Hızlı BULUNDU denemelerinde (≈1–2 sn) boşluk yok. Nedeni araştırılmadı.
