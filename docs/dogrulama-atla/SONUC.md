# İntro/özet/jenerik atlama (TheIntroDB) — doğrulama sonucu

Tarih: 2026-10-28 · Dal: `ozellik/eksikler-4` · Kullanıcı kararı 2026-10-28 (tam sürüm: düğmeler + isteğe bağlı otomatik atlama + dört tür anahtarı).
Kaynak: ortak geliştiricinin 1.1.47 paketi, kök `C:\Users\okay\Desktop\Play-1.1.47\play-app` (yalnızca okundu).
Cihaz: LDPlayer (`127.0.0.1:5555`, 1600×900, Android 9). Sürüm adı değişmedi (1.1.20).

## Derleme, test, kurulum
| Adım | Sonuç |
|---|---|
| `gradlew testDebugUnitTest assembleDebug` | BUILD SUCCESSFUL; 138 test sınıfı, 1094 test, 0 başarısız, 0 hata (2 test önceden atlanıyordu) |
| Yeni testler | `IntroDbRepositoryTest` (modla aynı, 5), `SkipSegmentTest` (modla aynı, 10), `IntroDbPrivacyTest` (2), `SkipControllerTest` (9), `SkipSettingsTest` (4), `StringResourcesTest` geçti |
| `adb install -r -t` (uninstall yok) | `lastUpdateTime` 12:46:01 → 14:32:51; Hakkında notundan sonra yeniden kurulum → 15:00:18 |

## Sahte sunucu
Bilgisayarda `127.0.0.1:8765` (`mode`: intro = 5 sn–95 sn intro, 404, empty = boş diziler); `adb reverse tcp:8765 tcp:8765`;
uygulama `am start … --es play_introdb_endpoint http://127.0.0.1:8765/v4/media` ile açıldı (yalnızca debug).
Cihazdan gelen istekler (sunucu günlüğü, başlıklar dahil; başka başlık yok):

```
GET /v4/media?tmdb_id=4057&duration_ms=2502125&season=1&episode=1     (Criminal Minds S1B1)
GET /v4/media?tmdb_id=32692&duration_ms=2568375&season=1&episode=1    (Blue Bloods S1B1)
GET /v4/media?tmdb_id=969681&duration_ms=8700625                      (Örümcek-Adam, film: sezon/bölüm yok)
GET /v4/media?tmdb_id=475557&duration_ms=7309083                      (Joker, film)
    Host: 127.0.0.1:8765 · Connection: Keep-Alive · Accept-Encoding: gzip · User-Agent: okhttp/5.5.0
```
Akış adresi, başlık, çerez, Referer, profil, hesap gitmedi. Canlı kanalda **0 istek**.

## Kanıt maddeleri
| # | Madde | Sonuç | Görüntü |
|---|---|---|---|
| a | "İntroyu geç" görünüyor ve çalışıyor (dokunma) | ✅ intronun ilk 20 sn'sinde kontroller gizliyken de görünüyor; dokununca 0:53 → 1:37 (intro sonu 1:35) | `atla-1-kontroller-gizliyken-dugme.png`, `atla-1-dugme-kontroller.png`, `atla-1-dokunma-once.png`, `atla-1-dokunma-sonra.png` |
| b | Otomatik atlama açıkken bir kez geçiyor, geri sarınca döngü yok | ✅ 0:00'dan oynatınca ~18 sn sonra 1:53'te (atlanmasa ~0:18 olurdu); intronun içine (0:56) geri sarınca 8 sn sonra 1:03, tekrar atlama yok | `ayarlar-otomatik-acik.png`, `atla-2-otomatik-once.png`, `atla-2-otomatik-sonra.png`, `atla-2-geri-sar-hemen.png`, `atla-2-geri-sar-8sn-sonra-dongu-yok.png` |
| c | Dört tür anahtarı ayarlarda var ve çalışıyor | ✅ İntro kapatılınca intronun içinde (0:11) düğme yok; sonra yeniden açıldı | `ayarlar-oynatici-tr.png`, `ayarlar-oynatici-tr-ust.png`, `atla-3-kategori-intro-kapali.png`, `atla-3-intro-kapaliyken-dugme-yok.png`, `ayarlar-varsayilana-donuldu.png` |
| d | Veri yoksa (404 / boş) sessizce hiçbir şey yok, oynatma etkilenmiyor | ✅ Örümcek-Adam (404) ve Joker (boş yanıt) düğmesiz, kesintisiz oynadı; canlı kanalda istek de yok | `atla-4-veri-yok-dugme-yok-orumcek.png`, `atla-4-bos-yanit-dugme-yok-joker.png`, `atla-4-canli-kanal-dugme-yok.png` |
| e | Kumanda: odak, Tamam, Geri | ✅ düğme görünürken odak onda (beyaz odak halkası); Tamam 0:56 → 1:35; Geri düğmeyi kapattı, oynatıcı açık kaldı (0:09) | `atla-5-dpad-odak-once.png`, `atla-5-dpad-tamam-sonra.png`, `atla-5-geri-once.png`, `atla-5-geri-sonra-oynatici-acik.png` |
| f | Ayarlar > Oynatıcı iki dilde taşmıyor; Ayarlar ana kalemleri değişmedi | ✅ TR ve EN; ana ekran kodu değişmedi | `ayarlar-oynatici-tr*.png`, `ayarlar-oynatici-en.png`, `ayarlar-ana-tr.png`, `ayarlar-ana-en.png` |
| g | Hakkında'da gizlilik notu | ✅ | `hakkinda-introdb-gizlilik-tr.png` |

Not (zamanlama): ilk dokunma denemesinde düğme, ekran görüntüsünü incelerken gizlenmişti (segment başından 20 sn geçti, kontroller 4 sn'de kapandı); dokunuş videoya gitti (`atla-1-ilk-deneme-dugme-gizlenmisti.png`). Aynı denemede kumandanın ilk Tamam'ı da bu yüzden duraklattı. Düğme görünürken yapılan dokunma ve Tamam çalıştı (yukarıdaki satırlar). Bu, modun görünürlük kuralıdır.

## Regresyon (60 sn oynat, Geri, kilitlenme günlüğü)
| İçerik | Oynatma | Geri | FATAL/ANR | Görüntü |
|---|---|---|---|---|
| Örümcek-Adam: Yepyeni Bir Gün | 14:41:37 → 14:42:51 (~74 sn), 1:04 → 1:53 | ana sayfaya döndü | 0 | `regresyon-orumcek-60sn.png`, `regresyon-orumcek-geri.png` |
| Joker (2019) | 14:46:36 → 14:47:39; içerik 0:00 → 0:58 (58 sn) | Detay'a döndü | 0 | `regresyon-joker-60sn.png`, `regresyon-joker-geri.png` |
| Criminal Minds S1B1 | 14:49:48 → 14:50:52, 1:43 → 2:35 | 1. Geri dublaj kartını kapattı (mevcut davranış), 2. Geri ana sayfaya | 0 | `regresyon-criminal-basla.png`, `regresyon-criminal-60sn.png`, `regresyon-criminal-geri.png` |
| Canlı: TRT 1 HD | 14:48:16 → 14:49:20 (~64 sn) | TV sekmesine döndü | 0 | `regresyon-canli-60sn.png`, `regresyon-canli-geri.png` |

Oturum sonunda `logcat -b crash` içinde `com.kaan.play`: 0.
Ayrıca açılıyor: Ara + tür çipleri (`regresyon-ara-cipler.png`), Detay indir düğmesi (`regresyon-joker-detay-indir.png`), hız testi (`regresyon-hiz-testi.png`), TV sekmesi (`regresyon-tv-sekmesi.png`).
Deneme sonunda ayarlar varsayılana alındı (otomatik geç kapalı, dört tür açık, dil Sistem); `adb reverse` kaldırıldı.

## Porttan bağımsız gözlemler (dokunulmadı)
1. Ara kutusuna `adb shell input text` ile dokunarak yazınca ilk harften sonra odak yan menüye geçiyor; kutuya kumandayla (çipten Yukarı) gelince yazı sorunsuz. Ara koduna bu işte dokunulmadı.
2. Ana sayfadaki "İzlemeye devam et" sırası bir oynatmadan hemen sonra bazen az önce izleneni göstermedi (geçmişte doğru, en üstte); birkaç dakika sonra geri geldi (`gozlem-ana-sayfa-devam-listesi.png`).
3. Dil English iken profil seçme ekranı, "Profil seçimi / Son profili hatırla" satırları ve hız testi ekranı Türkçe kalıyor (koda gömülü metin).
4. Blue Bloods S1B1 için "Dragon" kaynağı 22:24 süreli, başka bir yapıma benzeyen bir video açtı (bölüm süresi ~43 dk; dizi için 3 kat toleransın içinde kaldığı için elenmedi).
