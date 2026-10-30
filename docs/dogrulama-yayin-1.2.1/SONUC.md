# Play 1.2.1 yayın doğrulaması (2026-10-30, PlayNeon emulator-5556)

Durum: tamamlandı (2026-10-30). Joker (2019) HDFilmCehennemi'nde 1.2.0 release'te de 1.2.1 release'te de oynamıyor (aynı hata); site/servis sorunu, 1.2.1 regresyonu değil. Yalnızca `kanit/yayin-1.2.1` dalına itildi, main'e dokunulmadı.

| # | Madde | Sonuç | Kanıt |
|---|-------|-------|-------|
| 1 | Açılışta profil kapısı yok (3 kez force-stop + aç) | GEÇTİ (3/3 doğrudan ana sayfa; "Son profili hatırla" açık) | 01-acilis-1..3.png, 07-ayarlar-profil-wifi.png, 01-07-acilis-ayarlar.log |
| 2 | Örümcek-Adam: Yepyeni Bir Gün, HDFilmCehennemi | GEÇTİ (1. denemede; süre kontrolü Ok; Geri → Detay) | 02-oynat-10sn/30sn/60sn.png, 02-geri-detay.png, 02-orumcek-logcat.log |
| 3 | Joker (2019), HDFilmCehennemi, PlayNeon 1.2.1 | BAŞARISIZ 0/3 (3002); aşağıdaki klon karşılaştırması 1.2.0'da da aynı | 03-d1/d2/d3-*.png, 03-d1/d2/d3-logcat.log |
| 3b | Joker ×3 klonda 1.2.0 release | 0/3: her üçünde ERROR_CODE_PARSING_MANIFEST_MALFORMED (3002), "Hiçbir kaynak açılamadı" | 08-joker-v120-d1..d3-* |
| 3c | Joker ×3 klonda 1.2.1 release (1.2.0 üstüne `install -r`) | 0/3: aynı hata 3002 | 08-joker-v121-d1..d3-* |
| 3d | Başka film (Örümcek-Adam: Yepyeni Bir Gün), klon 1.2.1 | GEÇTİ: 60 sn oynadı (konum 0:58 / 2:25:01, süre kontrolü Ok), Geri oynatıcıdan çıkarıp arama/Detay akışına döndürdü, FATAL 0 | 10-baska-film-*.png, 10-baska-film-logcat.log |
| 4 | Criminal Minds S1 B1 (DiziBOX), D-pad | GEÇTİ (sezon seçici açıldı, Aşağı/Sağ/Sol ile bölüm listesi gezildi, B1 açıldı, süre Ok, Geri → Detay) | 04-*.png, 04-criminal-minds-logcat.log |
| 5 | Canlı TV | GEÇTİ (kullanıcı testi): kullanıcı canlı kanal (Anthology — Canlı TV) izledi; logcat: FATAL EXCEPTION 0, AndroidRuntime E/F 0, ANR 0, takılma sayısı 0 (ilk deneme "0 canlı kanal" idi, kullanıcı sonra kanal ekledi) | 05-canli-tv-kullanici-logcat.log (önceki: 05-canli-tv*.png) |
| 6 | Oynatıcı içi paneller (release 1.2.1, D-pad) | GEÇTİ (Kaynak ve altyazı paneli, Parçalar paneli: açıldı, D-pad ile gezildi, odak halkası göründü, seçim Uygula'ya kadar aşamalı, Geri paneli kapattı ve oynatıcıdan ÇIKMADI; ikinci Geri oynatıcıyı kapattı. Altyazı menüsü ayrı değil, "Kaynak ve altyazı" panelinin sağ sütunudur) | 06a-kaynak-odak/panel/geri-sonrasi.png, 06b-altyazi-gezinme/secili/geri-sonrasi-yeniden-acilis.png, 06c-parcalar-odak/panel/gezinme/geri-sonrasi.png |
| 7 | Hakkında + Wi-Fi kartı | GEÇTİ (Hakkında "Sürüm 1.2.1"; Wi-Fi kartı açıldı, kod göründü) | 07-hakkinda.png, 07-wifi-karti.png |

Joker notu: 1. deneme taze sorgu (HDFilmCehennemi master.txt 200 döndü, oynatıcı manifest'i çözemedi). 2. deneme ~5 dk'lık bellek içi önbellekten aynı adres. 3. deneme uygulama yeniden başlatıldıktan sonra, aynı sunucu (srv12.cdnimages1157.shop), aynı hata.
Tüm günlüklerde FATAL EXCEPTION: 0.

## Joker karşılaştırması (devam)
- Resmî 1.2.0 APK indirildi: SHA-256 d612ea38…c37f, sertifika 5c7cd2bd…9f44f (beklenenle aynı; 1.2.1'in sertifikası da aynı).
- PlayNeon'da 1.2.0 kurulamadı (INSTALL_FAILED_VERSION_DOWNGRADE; `-k` kaydı versionCode 28'i tutuyor). Karar: klon `PlayNeon-130` (LDPlayer index 2, emulator-5558). Klonda root yok (`su` yok, `adb root` production build), `allowBackup=false` → veri yedeklenemiyor; talimat gereği DURULDU.

## İntro/özet atlama (PlayNeon, 1.2.1 release, gerçek TheIntroDB verisi, Criminal Minds S1B2 DiziBOX; özet 0:01–1:00, intro 6:20–6:50)
| Alt madde | Sonuç | Kanıt |
|---|---|---|
| Sağ altta "İntroyu geç" / "Özeti geç", kontroller gizliyken de | GEÇTİ (segmentin ilk 20 sn'sinde kontroller gizliyken görünür; kontroller açılınca da görünür; camgöbeği dolgu, beyaz yazı, odakta halka; okunaklı) | intro-1, intro-14, intro-15 |
| Dokununca atlıyor (önce/sonra konum, video duraklatılmış) | GEÇTİ: 6:22 → 6:50 (intro sonu) | intro-6, intro-7 |
| D-pad Tamam atlıyor | GEÇTİ: 6:45 → 6:50 (video duraklatılmış). Düğme gizliyken (20 sn penceresi bitmiş, kontroller kapalı) ilk Tamam düğmeye değil oynat/duraklata gider ve kontrolleri açar; ikinci Tamam (odak düğmede, halka görünür) atlar | intro-9, intro-10, intro-11 |
| Atlanan segment o oturum için kapanıyor | GEÇTİ: atladıktan sonra 6:30'a geri sarınca düğme yok | intro-8 |
| Geri düğmeyi kapatıp oynatıcıda kalıyor | KISMEN: odak düğmede (halka) ve kontroller GİZLİYKEN Geri düğmeyi kapattı, `PlayerActivity` açık kaldı. Kontroller AÇIKKEN ya da dokunma modunda (halka yok) Geri oynatıcıdan ÇIKTI (3/3 denemede MainActivity) | intro-17/18 (kalıyor); intro-12/13, intro-15/16, intro-19/20 (çıkıyor) |
Not: 1.2.0 doğrulamasında (docs/dogrulama-atla) Geri "düğmeyi kapattı, oynatıcı açık kaldı"; o testte düğme odaklıydı. Aynı koşul burada da geçti; kontroller açıkken/dokunma modunda çıkış davranışı karşılaştırılmadı ve araştırılmadı (düzeltmeye girişilmedi). Klonda 1.2.0 kurulunca aynı denemeyle karşılaştırılabilir.

## Joker karşılaştırması: sonuç (klon PlayNeon-130, emulator-5558)
Yöntem: PlayNeon'da 1.2.0 kurulamadığı için (INSTALL_FAILED_VERSION_DOWNGRADE) klon oluşturuldu, klonda uygulama tam kaldırıldı, resmî Play-1.2.0.apk (sha256 d612ea38…c37f, sertifika 5c7cd2bd…9f44f) kuruldu; kullanıcı HDFilmCehennemi ve DiziBOX depolarını elle ekledi ("5 eklenti"). Her denemede `force-stop` (öncesi pid dolu, sonrası boş), logcat -c, sıfırdan açılış, arama → Detay → Oynat, 45 sn bekleme, 60 sn logcat; denemeler arası bekleme yok (süreç ölünce bellek içi ~5 dk sonuç önbelleği de silinir; her günlükte tazelenen ağ istekleri var: api.themoviedb.org, hdfilmcehennemi.* GET 200).
| Sürüm | Deneme 1 | Deneme 2 | Deneme 3 | Sonuç |
|---|---|---|---|---|
| 1.2.0 release | 3002, "Hiçbir kaynak açılamadı" | 3002 | 3002 | 0/3 |
| 1.2.1 release (yükseltme, veri korundu) | 3002 | 3002 | 3002 | 0/3 |
Hata her yerde `PlayMatch: oynatıcı hatası: HDFilmCehennemi (faz=Starting) ERROR_CODE_PARSING_MANIFEST_MALFORMED (3002)`: sitenin HLS master/manifest yanıtı oynatıcıda ayrıştırılamıyor; iki sürümde aynı kod ve aynı kaynak. FATAL EXCEPTION her denemede 0.
Geçersiz sayılan koşular (`gecersiz/`): klon çok yavaş açıldı (2,5 dk) ya da `monkey` uygulamayı başlatamadı, arama/Oynat zincirine girilmedi; 1.2.0 deneme 2 ve 1.2.1 deneme 2 aynı koşullarda yeniden koşuldu (betiğe açılış bekleme/yeniden başlatma eklendi). Sonuç sayılanlar yukarıdaki üçer denemedir.
Not: 1.2.0'da açılışta profil seçme kapısı çıkıyordu ("Kimin İzlediğini Seçin", elle KAAAN seçildi), 1.2.1'de doğrudan ana sayfa açıldı (madde 1 ile tutarlı).
Başka film: Joker'in site sorunu olduğu iki sürümde aynı hata koduyla belgelendi; aynı sitede başka film oynuyor (3d, ayrıca madde 2). 3d'de iki Geri basıldı (ilki kontrolleri/oynatıcıyı kapattı, ikincisi bir önceki ekranı); tek Geri ile Detay'a dönüş bu klon koşusunda ayrıca ayrıştırılmadı.
