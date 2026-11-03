# 1.2.4 Birleştirme ve Doğrulama Sonuç Raporu

**Tarih:** 2026-11-03  
**Dal:** `release/1.2.4`  
**Baz Commit (main):** `8bf8016`  
**Kod Değişiklikleri Commit:** `c64a182` (`fix(release): 1.2.4 zorunlu duzeltmeler ve surum guncellemesi`)  
**Doğrulama ve Rapor Commit:** `b8512c9` (`docs(release): 1.2.4 dogrulama kanitlari ve sonuc raporu`)  

---

## 1. Birleştirilen Dallar ve SHA Değerleri

`main` (`8bf8016`) üzerinden açılan `release/1.2.4` dalına sırasıyla aşağıdaki 6 dal birleştirilmiştir:

1. **`fix/oynatici-takilma`** (`c2e6ae1`)
   - Oynatıcı takılma bekçisi döngüsü önleme, TV tampon optimizasyonu ve video çözücü loglama.
2. **`fix/torrent-eski-android`** (`4b5ab0b`)
   - Eski Android sürümlerinde (API < 26) native libtorrent yükleme çökmesi koruması ve yerleşik torrent kaynaklarının gizlenmesi.
3. **`fix/kumanda-asagi`** (`5874862`)
   - TV kumandasında üst menüden AŞAĞI ve Geri basıldığında içeriğe pürüzsüz geçiş kuralı.
4. **`fix/oynarken-kurtarma`** (`f8e9af2`)
   - Oynatma sırasında ağ kopması durumunda kalınan saniyeden otomatik kurtarma ve sıradaki yedek kaynağa (`Switch`) geçiş.
5. **`ozellik/torrent-blok`** (`5f92213`)
   - Torrent Tur 3 blok düzeyinde istek kuyruğu, Range istekleri ve ileri/geri sarma koruması.
6. **`fix/pencere-acilis-cokmesi`** (`6ad8ce6`)
   - `StartupWindowGuard` ile ilk açılışta pencere çökmesi ve race condition koruması.

---

## 2. Çakışmalar ve Çözüm Yöntemi

Birleştirme sırasında oluşan her çakışmada iki tarafın işlevselliği eksiksiz korunmuştur:

- **`fix/kumanda-asagi` birleştirmesi:**
  - `app/src/main/java/com/kaan/play/ui/home/HomeScreen.kt`: `focusMemory` ile kahraman (hero) kartının odak özellikleri ve satır kontrolleri birleştirildi.
  - `app/src/main/java/com/kaan/play/ui/tv/PlayFocus.kt`: Hem `Modifier.topBarItem` / `rowControl` uzantıları hem de `rememberFocusMemory` mekanizması korundu.
  - `app/src/main/java/com/kaan/play/ui/nav/PlayRoot.kt`: `TopBar` odak köprüleri ve alt sayfa geri tuşu yönetimi korundu.
- **`fix/oynarken-kurtarma` birleştirmesi:**
  - `app/src/main/java/com/kaan/play/ui/player/PlayerActivity.kt`: `fix/oynatici-takilma` ile gelen takılma bekçisi döngü koruması ile `fix/oynarken-kurtarma` ile gelen `MidPlaybackRecoveryRules` / kaynak değiştirme mantığı birleştirildi.
- **`ozellik/torrent-blok` birleştirmesi:**
  - `app/src/main/java/com/kaan/play/data/torrent/TorrentHttpServer.kt`: Tur 3 blok tabanlı torrent servis döngüsü, parça başı/sonu aralık hesaplamaları ve `Range` başlığı seek koruması korundu.
- **`fix/pencere-acilis-cokmesi` birleştirmesi:**
  - `app/src/main/java/com/kaan/play/MainActivity.kt`: `StartupWindowGuard` pencere koruma kontrolleri ile `handlePlayIntent` ve intent ekstraları blokları birleştirildi.

---

## 3. Zorunlu Düzeltmeler (ADIM 2)

### (a) `PlayRoot.kt` `returnFocusToContent`
- Başarı ölçütü `runCatching { currentContentFocusRequester.requestFocus() }.getOrDefault(false)` olarak güncellendi (hata fırlatılması ve false dönmesi başarısız sayılır).
- Başarısızlık durumunda karar `TopBarFocusRules` üzerinden verildi: Ana sayfada ise liste başa kaydırılıp `withFrameNanos` ile bir kare beklendikten sonra hedef tekrar denendi; yine başarısız olursa `focusManager.moveFocus(FocusDirection.Down)` çalıştırıldı.
- `PlayTopBar` ve `PlayTopBarItem` içindeki `onDown: () -> Boolean` fonksiyonu AŞAĞI tuşunu yalnızca odak gerçekten içeriğe geçtiyse tüketti (`true`), aksi halde olayı tüketmedi (`false`).

### (b) `TopBarFocusRules.kt` Sınıfının Durumu
- `TopBarFocusRules` sınıfı ve birim testleri ölü kod olmaktan çıkarıldı ve `returnFocusToContent` mantığının karar merkezi yapıldı.
- **Sınıf Adı:** `com.kaan.play.ui.tv.TopBarFocusRules`
- **Metot İmzası:**
  ```kotlin
  fun decideStep(
      targetAttached: Boolean,
      focusTaken: Boolean,
      isHome: Boolean,
      isScrolled: Boolean,
  ): Step
  ```
- **Dönen Değerler:** `Step.INITIAL_TARGET`, `Step.SCROLL_AND_RETRY`, `Step.MOVE_FOCUS_DOWN`.
- `PlayRoot.kt` içindeki `returnFocusToContent()` bu saf fonksiyonu doğrudan çağırmaktadır.

### (c) `MainActivity.handlePlayIntent` Yayın Koruması
- `play_test_video`, `play_test_fallback`, `play_test_fail_after_ms`, `play_test_fail_repeats` intent bloklarının tamamı `if (BuildConfig.DEBUG)` bloğu içine alındı.
- Generik `"url"` ekstrası tamamen kaldırıldı; yalnızca `"play_test_video"` ekstrası kabul edildi.
- Adres şeması kesin olarak denetlendi: `http`, `https`, `file`, `content` dışındaki şemalar (`magnet:`, `javascript:` vb.) reddedildi.
- **Yayın Derlemesi Kanıtı:** Release APK'ya `adb shell am start -n com.kaan.play/.MainActivity --es play_test_video https://example.com/a.mp4` komutu gönderildiğinde `PlayerActivity` AÇILMADI; `MainActivity` ekranında kalındı. Kanıt: `docs/dogrulama-124/kanit-2c-debug-extra-release-ignored.txt`.

### (d) Birim Test
- **Test Sınıfı:** `com.kaan.play.ui.tv.TopBarFocusRulesTest`
- **Test Sayısı:** 6 birim test (hepsi geçti).
  1. `initial target takes focus directly`
  2. `home when scrolled triggers scroll and retry`
  3. `home when already at top falls back to moveFocusDown`
  4. `non home tab falls back to moveFocusDown`
  5. `unattached target on non home falls back to moveFocusDown`
  6. `unattached target on home when scrolled triggers scroll and retry`

---

## 4. Sürüm ve Derleme Metrikleri

- **versionCode:** 31
- **versionName:** 1.2.4
- **CHANGELOG.md:** TR ve EN sürümleri CRLF korunarak 1.2.4 başlığı altında 6 maddeyle güncellendi.
- **Birim Testler (`testDebugUnitTest`):**
  - Toplam: **1194** test
  - Başarılı: **1192** test
  - Atlanan (Skipped): **2** test (`testAllPlugins` - harici ağ erişimi gerektirdiği için kasti atlanır)
  - Hata / Başarısızlık: **0**
- **Release APK:**
  - Konum: `dist/1.2.4/Play-1.2.4.apk`
  - Boyut: **48,631,422 bayt** (46.38 MB)
  - SHA-256: `01d56587599bf9a2006756d61d728f9d6d33fe73da3c9d06f07a16ca1ab39052`
  - SHA-256 Dosyası: `dist/1.2.4/Play-1.2.4-SHA256.txt`
  - R8 / ProGuard Uyarıları: **0 uyarı** (temiz derleme).

---

## 5. Doğrulama ve Test Kanıt Tablosu (ADIM 5)

Tüm testler LDPlayer 9 (`emulator-5554`) üzerinde temiz kurulum (uninstall sonrası `dist/1.2.4/Play-1.2.4.apk` yüklemesi) ile ve yalnızca kumanda tuşları (`adb shell input keyevent`) kullanılarak yapılmıştır. Dokunma (`input tap`) KESİNLİKLE kullanılmamıştır.

| # | Test Maddesi | Sonuç | Kanıt Dosyası / Detay |
|---|--------------|:-----:|-----------------------|
| 2(c) | Release APK'da test intent ekstralarının yok sayılması | **GEÇTİ** | `kanit-2c-debug-extra-release-ignored.txt`<br>`MainActivity` açık kaldı, `PlayerActivity` başlamadı. |
| 1 | Açılış, profil seçimi, Ana sayfa yüklenmesi, fatal çökme yok | **GEÇTİ** | `kanit-madde1-acilis.txt`<br>`madde1_acilis.png`, `madde1_profil_secimi.png`, `madde1_after_dialog.png`<br>Logcat `FATAL` taramasında 0 hata. |
| 2 | Üst menü 5 sekme (Ana sayfa, Ara, Canlı TV, Kütüphane, Ayarlar) AŞAĞI ve Geri | **GEÇTİ** | `kanit-madde2-sekmeler.txt`<br>Her sekmede AŞAĞI ile içeriğe geçiş (`y1 > 200`) ve Geri ile güvenli dönüş doğrulandı.<br>`madde2_tab_0_Ana_sayfa_down.png`..`madde2_tab_4_Ayarlar_back.png` (10 ekran görüntüsü). |
| 3 | Kullanıcı Senaryosu 1: Eklentisiz aşağı kaydır, menüye çık, OK basmadan AŞAĞI -> içeriğe in; Geri -> içeriğe dön | **GEÇTİ** | `kanit-senaryo1.txt`<br>`senaryo1_1_scrolled.png`, `senaryo1_2_topbar.png`<br>`senaryo1_3_content_after_down.png`, `senaryo1_4_content_after_back.png` |
| 4 | Kullanıcı Senaryosu 2: Hap butonuna git, menüye dön, Ana sayfaya OK bas, AŞAĞI bas -> içeriğe in, hapa kaçmasın | **GEÇTİ** | `kanit-senaryo2.txt`<br>`senaryo2_1_pill_focus.png`, `senaryo2_2_menu_again.png`, `senaryo2_3_after_down.png` |
| 5 | Eklenti seçili Ana sayfa AŞAĞI / Geri testi | **GEÇTİ** | `kanit-madde5.txt`<br>`madde5_provider_sheet.png`, `madde5_addon_selected.png`<br>`madde5_1_scrolled.png`, `madde5_2_topbar.png`<br>`madde5_3_content_after_down.png`, `madde5_4_content_after_back.png` |
| 6a | Örümcek-Adam kataloğu açılışı | **GEÇTİ** | `kanit-madde6.txt`<br>`madde6_spiderman_catalog.png`<br>Spider-Man 2017 Dizi, Örümcek-Adam 1994, 2002 Film, Eve Dönüş vb. listelendi. |
| 6b | Joker kataloğu açılışı ve detay ekranı | **GEÇTİ** | `kanit-madde6.txt`<br>`madde6_search_joker_results.png`<br>`madde6_joker_detail_opened.png` (Todd Phillips, TMDB 8.1, Oynat/İndir/Fragman). |
| 6c | Criminal Minds bölüm listesi | **GEÇTİ** | `kanit-madde6.txt`<br>`madde6_criminal_minds_search.png`<br>`madde6_criminal_minds_episodes.png`<br>1. Sezon seçici ve 1-6. bölümler süre, özet, görsel ve İndir butonuyla görüntülendi. |
| 6d | Canlı kanal 60 sn oynat | **doğrulanamadı** | `kanit-madde6.txt`<br>`madde2_tab_2_TV_down.png`<br>TV sekmesinde "Etkin eklentilerinizde canlı kanal bulunamadı" (akış kaynağı yok). |
| 6e | Film oynat (HTTP kaynak, 3 dk) | **doğrulanamadı** | `kanit-madde6.txt`<br>`madde6_joker_play.png`<br>Joker detayında "Oynat" basıldığında "Eklenti ekle" dialogu çıkmaktadır; temiz release APK'da harici video akış sağlayıcı eklenti bulunmamaktadır. |
| 6f | Oynatıcıda Geri | **doğrulanamadı** | `kanit-madde6.txt`<br>Video akış kaynağı olmadığı için oynatıcı başlatılamadı. |
| 7 | Oynarken bağlantı kopma ve kurtarma simülasyonu (Debug APK) | **GEÇTİ** | `kanit-madde7-oynarken-kurtarma.txt`<br>`madde7_recovery_playing.png`<br>2500 ms'de kopma simülasyonu: 1. kopmada `RetrySame`, 2. kopmada `Switch(TestFallback)` tetiklendi ve oynatıcı kapanmadan yayına devam etti. |
| 8 | Torrent: Captain America Civil War magnet soğuk açılış süresi | **doğrulanamadı** | `kanit-madde8-torrent.txt`<br>Temiz kurulumda torrent sağlayıcı eklentisi (Torrentio vb.) ve aktif eş bağlantısı bulunmamaktadır. |

---

## 6. Doğrulanamayan Maddelerin Açık Listesi

Yönerge gereği, ölçülemeyen veya ortam kısıtı nedeniyle gerçekleştirilemeyen maddeler tahmin edilmeden "doğrulanamadı" olarak işaretlenmiştir:

1. **Film Oynat (HTTP kaynak, 3 dk):** Temiz release APK kurulumunda video akış sağlayıcı eklenti yüklü olmadığından ve katalog öğelerinde "Oynat" tıklandığında "Eklenti ekle" penceresi çıktığından 3 dakikalık kesintisiz HTTP oynatımı doğrulanamadı.
2. **Oynatıcıda Geri:** Oynatıcı video akış kaynağı yokluğu nedeniyle release APK'da açılamadığı için oynatıcı içi Geri tuşu doğrulanamadı.
3. **Canlı TV 60 sn Oynat:** TV sekmesinde kanal listesi boş ("Etkin eklentilerinizde canlı kanal bulunamadı") olduğundan canlı yayın akışı doğrulanamadı.
4. **Torrent Soğuk Açılış Süresi (Captain America Civil War):** Temiz kurulumda torrent eklentisi bulunmadığı ve ADIM 2(c) gereği intent üzerinden raw magnet URL'leri güvenlik amacıyla engellendiği için torrent soğuk açılış süresi doğrulanamadı.
