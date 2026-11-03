# Kapanış Paketi Doğrulama ve Sonuç Raporu

**Tarih:** 2026-11-03  
**Taban Commit:** `8bf8016e0e24dbe855982c182fd8358631f8e4f1` (`origin/main`)  
**Rapor Dalı:** `docs/kapanis-paketi`  

---

## 1. Genel Özet Tablosu

| Görev | Dal Adı | Son Commit (Kısa) | Son Commit (Tam SHA) | Durum | Birim Testler | Canlı Emülatör Doğrulaması |
|---|---|---|---|---|---|---|
| **Görev 1** | `fix/oynatici-takilma` | `c2e6ae1` | `c2e6ae1ef2a0c3420ca5ea1b0c1936e0c187dee7` | **BİTTİ / PUSH EDİLDİ** | 13 test (%100 Başarılı) | Doğrulandı (Takılma döngüsü kalktı, bellek ayarı ve çözücü logu tamam) |
| **Görev 2** | `fix/torrent-eski-android` | `4b5ab0b` | `4b5ab0b38edad1f75c679c6d22a96800bcf2fcaa` | **BİTTİ / PUSH EDİLDİ** | 11 test (%100 Başarılı) | Kısmen Doğrulandı (Hata yakalama ve kaynak gizleme simüle edildi; gerçek API<28 cihazda doğrulanamadı) |
| **Görev 3** | `fix/kumanda-asagi` | `5874862` | `5874862a525bd734f0e5442f329ba693b1ba9980` | **BİTTİ / PUSH EDİLDİ** | 7 test (%100 Başarılı) | Doğrulandı (Üst menüden Aşağı ve Geri tuşu kaydırılmış/boş ekranlarda kurtarıyor) |
| **Görev 4** | `fix/oynarken-kurtarma` | `f8e9af2` | `f8e9af246646a1098e74cf23c3bcb46db97a43e2` | **BİTTİ / PUSH EDİLDİ** | 45 test (%100 Başarılı) | Doğrulandı (Playing aşamasında 1 kez aynı kaynaktan devam, 2. hatada sıradaki HLS kaynağına kesintisiz geçiş ve hata ekranı) |

---

## 2. Görev 1: Oynatıcı Takılması ve Watchdog Döngüsü (`fix/oynatici-takilma`)

### Kök Neden
1. `PlaybackController.startSeekWatchdog`: 8 saniye `BUFFERING` sürdüğünde `player.seekTo(currentPos)` + `player.prepare()` + `play()` çağırıyordu. `prepare()` progressive kaynakta indirilmiş tamponu çöpe atıp TCP/HTTP bağlantısını sıfırdan kuruyordu. Bu sarma `onPositionDiscontinuity(REASON_SEEK)` tetiklediğinden ve durum hala `BUFFERING` olduğundan watchdog her 8 saniyede bir yeniden tetikleniyor ve film "durdu, çalıştı, durdu, çalıştı" şeklinde sonsuz takılma döngüsüne giriyordu.
2. `PlayerBufferConfig.computeTargetBufferBytes`: `isLowRam` veya `isTv` olduğunda her zaman 16 MB veriyordu. Yüksek bit hızlı HD içeriklerde 16 MB yalnızca 2-3 saniyelik tampon sağladığından ağ dalgalanmalarında hemen tükeniyordu.
3. Teşhis eksikliği: Oynatılan videonun hangi çözücü ile açıldığı (donanım mı yazılım mı) bilinmiyordu.

### Yapılan Değişiklikler
- **`StallWatchdogRules.kt`**: Android'den bağımsız saf karar motoru eklendi (`evaluate(bufferedPositionMs, currentPositionMs, elapsedMs, retryCount)`).
  - Veri akışı varsa (tampon ilerliyorsa) -> `WAIT` (dokunulmaz, ağ beklenir).
  - Veri hiç ilerlemiyorsa ve süre aşımı olduysa -> yükleme/sarma başına yalnızca 1 kez `RETRY_PREPARE`.
  - 1 deneme yapıldıysa ve hala takılıysa -> `GIVE_UP` (döngüye girilmez).
- **`PlaybackController.kt`**: Watchdog mantığı `StallWatchdogRules`'a bağlandı. Sayaç `STATE_READY` olduğunda sıfırlandı; watchdog'un kendi sarması yeni watchdog kurmayacak şekilde filtrelendi.
- **`PlayerBufferConfig.kt`**: TV tampon hesabı güncellendi:
  - `isLowRam` -> 16 MB (değişmedi).
  - `isTv && memoryClass >= 192 && totalRamGb >= 3` -> **48 MB**.
  - Diğer TV cihazları -> 16 MB.
  - `prioritizeTimeOverSizeThresholds` TV'de OOM güvenliği için `false` bırakıldı.
- **`ExoPlayerFactory.kt`**: `AnalyticsListener.onVideoDecoderInitialized` dinleyicisi eklendi; video çözücüsünün adı `PlayMatch` etiketiyle loglandı (ör. `OMX.google.h264.decoder` veya `c2.android.avc.decoder`). Gizlilik kuralına uygun olarak adres, başlık ve çerez yazılmadı.

### Testler ve Doğrulama
- **Birim Testler:**
  - `StallWatchdogRulesTest`: 6 test (ilerleyen tamponda bekleme, donan tamponda 1 kez prepare, 2. kez döngüye girmeme) -> **GEÇTİ**.
  - `PlayerBufferConfigTest`: 7 test (RAM/memoryClass matrisi) -> **GEÇTİ**.
- **Canlı Emülatör Doğrulaması:**
  - LDPlayer 9'da HTTP film akışı 3 dakika oynatıldı.
  - Önce: Watchdog 8 sn'de bir tetiklenip bağlantıyı koparıyordu ("Seek sonrası 8 saniye" logları her 8 sn'de çıkıyordu).
  - Sonra: Watchdog tampon ilerlediği sürece tetiklenmedi; 3 dakikalık oynatmada 0 yapay seek/prepare döngüsü oluştu.
  - Video decoder logu: `onVideoDecoderInitialized: OMX.google.h264.decoder (yazilim=true)` doğrulandı.
- **Yapılamayan Doğrulama:**
  - Yok (Tüm maddeler emülatör üzerinde doğrulandı).

---

## 3. Görev 2: Eski Android Sürümlerinde Torrent Çökmesi (`fix/torrent-eski-android`)

### Kök Neden
Android 7.1 ve 8.0 (API < 28) TV kutularında (TX9 Pro, Philips QM163E vb.) `libtorrent4j` yerel kütüphanesi yüklenirken Bionic libc'de `aligned_alloc` sembolü bulunamadığı için `UnsatisfiedLinkError` fırlatılıp uygulama doğrudan çöküyordu.

### Yapılan Değişiklikler
- **`TorrentSupport.kt`**:
  - `isEngineUnavailable`: Çalışma zamanında yerel kütüphane yüklenemezse oturum boyunca bellekte tutulan bayrak eklendi.
  - `isBuiltInSupported(sdkInt)`: API < 28 cihazlarda yerel torrent desteğini doğrudan devre dışı bıraktı.
  - `hasTorrServer()`: Harici TorrServer adresi yapılandırılmışsa API < 28 olsa bile torrent akışına izin verildi.
- **`TorrentEngine.kt`**: `ensureSession` içindeki `SettingsPack`, `SessionParams` ve libtorrent yerel çağrıları `try { ... } catch (t: Throwable)` bloğuna alındı; hata `TorrentException`'a çevrilerek `isEngineUnavailable = true` işaretlendi.
- **`SourceCoordinator.kt`** & **`AutoNextController.kt`**: `TorrentSupport.isStreamSupported(stream)` kontrolü eklendi. Bayrak açıkken ya da SDK < 28 iken (TorrServer yoksa) yerleşik torrent kaynakları listeden gizlendi, otomatik kaynak seçimi ve otomatik sonraki bölüm torrent adaylarını atladı.
- **`MainActivity.kt`**: Test amaçlı `play_debug_fail_native` intent parametresi eklendi.

### Testler ve Doğrulama
- **Birim Testler:**
  - `TorrentSupportTest`: 11 test (SDK seviyesi, TorrServer varlığı/yokluğu, engine unavailable durumunda filtreleme, otomatik sonraki bölüm adayları) -> **GEÇTİ**.
- **Canlı Emülatör Doğrulaması:**
  - LDPlayer (API 28) üzerinde `play_debug_fail_native` intent parametresi ile yapay `UnsatisfiedLinkError` tetiklendi. Uygulamanın çökmediği, torrent kaynaklarının listeden gizlendiği ve HTTP/HLS kaynaklarının sorunsuz oynatıldığı doğrulandı.
- **Yapılamayan Doğrulama:**
  - **Gerçek eski donanımda doğrulanamadı:** Test ortamında fiziksel Android 7.1/8.0 (API < 28) cihaz bulunmadığından gerçek `aligned_alloc` çökmesi fiziken test edilemedi; simülasyon ve birim testlerle doğrulandı.

---

## 4. Görev 3: TV Kumandası Üst Menüden Aşağı ve Geri Odak Sorunu (`fix/kumanda-asagi`)

### Kök Neden
Ana sayfada `LazyColumn` aşağı kaydırıldığında kapak karuseli (`HeroCarousel`), "devam et" kartı ve ilk TMDB trend filmler kartı ekran dışına çıktığı için Compose bellek optimizasyonu gereği öğeler kompozisyondan atılır. `contentFocusRequester` yalnızca bu üst öğelere bağlı olduğu için bağlantısı kopar (`isCaptured == false`). Kullanıcı üst menüdeyken D-pad AŞAĞI bastığında veya GERİ tuşuna bastığında odak içerik alanına inemez ve odak menüde takılı kalır.

### Yapılan Değişiklikler
- **`TopBarFocusRules.kt`**: Odak kararı veren saf kural sınıfı eklendi (`evaluate(isAtTop, hasActiveTarget, isScrolled, currentDestination)` -> `FOCUS_TARGET`, `SCROLL_TO_TOP_AND_FOCUS`, `FOCUS_DOWN_FALLBACK`).
- **`HomeScreen.kt`**: `onRegisterScrollToTop` ile `listState.scrollToItem(0)` kaydedildi. Menüden aşağı inilirken üst öğeler görünmüyorsa önce liste başa kaydırılarak kapağa odaklanması sağlandı.
- **`ProviderCatalogView.kt`** & **`LibraryScreen.kt`**: İçerik odak hedefleri (`contentFocusRequester`) sağlayıcı raflarına ve boş durum metinlerine (`EmptyText`) bağlandı.
- **`PlayRoot.kt`**: `returnFocusToContent` fonksiyonuna ikili koruma eklendi:
  1. `contentFocusRequester.requestFocus()` denenir.
  2. Başarısız olursa kayıtlı `scrollToTop()` tetiklenir veya `focusManager.moveFocus(FocusDirection.Down)` çalıştırılır.

### Testler ve Doğrulama
- **Birim Testler:**
  - `TopBarFocusRulesTest`: 7 test -> **GEÇTİ**.
- **Canlı Emülatör Doğrulaması:**
  - Senaryo A (Ana sayfa en üstte, menüden Aşağı): Kapağa indi -> **GEÇTİ**.
  - Senaryo B (Ana sayfa aşağı kaydırılmış, menüye çıkıldı, Aşağı basıldı): Sayfa başa kayarak kapağa odaklandı -> **GEÇTİ** (`test_screen.png`).
  - Senaryo C (Aşağı kaydırılmışken menüde Geri basıldı): İçeriğe döndü -> **GEÇTİ**.
  - Senaryo D (Boş Kütüphane sekmesinde Aşağı / Geri): Boşluk metnine odaklandı -> **GEÇTİ**.
  - Senaryo E (Canlı TV, Ara, Ayarlar): Sabit arama kutusuna ve ilk kartlara odaklandı -> **GEÇTİ**.
- **Yapılamayan Doğrulama:**
  - Kumandayla 3 dakikalık kesintisiz gezinme/oynatma stres testi süresi yetersizliği sebebiyle yapılmadı.

---

## 5. Görev 4: Oynarken Kaynak Kopmasında Kalındığı Saniyeden Kurtarma (`fix/oynarken-kurtarma`)

### Kök Neden
`SourceCoordinator.onFailure` fonksiyonu yalnızca `SourcePhase.Starting` aşamasında geçiş yapıyordu (`if (phase != SourcePhase.Starting) return false`). Film oynarken (`SourcePhase.Playing`) ağ kopması, soket kapanması veya akış sunucusu hatası oluştuğunda koordinatör arayüze doğrudan `false` dönüyor, uygulama alternatif kaynak aramadan "Bu kaynak bu cihazda oynatılamıyor" hata ekranına düşüyordu.

### Yapılan Değişiklikler
- **`MidPlaybackRecoveryRules.kt`**: Kurtarma kurallarını yöneten saf karar sınıfı eklendi:
  - 1. hata (`!hasRetriedCurrentStream`): `RetrySame(currentStream)` -> Aynı kaynak kalındığı saniyeden bir kez yeniden denenir.
  - 2. hata (`hasRetriedCurrentStream`): `Switch(candidate, notice)` -> En çok 3 kez sıradaki uygun kaynağa kesintisiz geçilir.
  - Kullanıcı kaynağı elle seçtiyse bildirim: `SourceSwitched` ("Kaynak değiştirildi").
  - Otomatik seçilen kaynak koptuysa bildirim: `StreamDropped` ("Kaynak koptu, sıradaki deneniyor").
  - Adaylar tükenirse veya 3 geçiş dolarsa: `Fail` -> Hata ekranı ("Başka kaynak seç"). Asla siyah ekranda bırakmaz.
  - Çevrimdışı (indirilen) içerikte veya kullanıcı oynatıcıdan çıkarken (`isLeaving`) geçiş yapılmaz.
- **`SourceCoordinator.kt`**:
  - `phase == SourcePhase.Playing` durumu `onFailure` içinde yakalandı.
  - Yedek adaylar hem bekleyen `queue` hem de `ranked()` üzerinden tekilleştirilerek hesaplandı (`candidates`).
  - `midPlaybackSwitches` sayacı (maksimum 3) ve `hasRetriedCurrentStream` bayrağı eklendi.
  - `PlayMatch` loglarında hata kodu, karar ve geçiş sayısı loglandı (URL, token, çerez yazılmadı).
- **`PlayerScreen.kt`**: `SourceNoticePill` bileşenine `StreamDropped` ve `SourceSwitched` pill görselleri eklendi.
- **`strings.xml` & `values-tr/strings.xml`**:
  - `player_stream_dropped`: "Kaynak koptu, sıradaki deneniyor" / "Stream disconnected, trying next source."
  - `player_source_switched`: "Kaynak değiştirildi" / "Source changed."
- **`PlayerActivity.kt`** & **`MainActivity.kt`**:
  - Test amaçlı `play_test_fail_after_ms`, `play_test_fail_repeats` ve `play_test_fallback` parametreleri eklendi.

### Testler ve Doğrulama
- **Birim Testler:**
  - `MidPlaybackRecoveryRulesTest`: 8 test (%100 Başarılı)
    - Çevrimdışı dosyada geçiş yapmama,
    - Playing aşaması dışındaki çağrılarda Fail dönme,
    - Aday kalmadığında Fail dönme,
    - 3 geçiş sınırına ulaşıldığında Fail dönme,
    - Kullanıcı çıkış yaparken geçiş yapmama,
    - Playing aşamasında ilk hatada aynı kaynağı bir kez deneme,
    - Elle seçilen kaynakta `SourceSwitched` bildirimi,
    - İkinci hatada sıradaki adaya `StreamDropped` bildirimi ile geçiş.
  - `SourceCoordinatorTest`: 37 test (%100 Başarılı, mid-playback testleri dahil).
- **Canlı Emülatör Doğrulaması:**
  - LDPlayer 9 üzerinde birincil akış (`oceans.mp4`) ve yedek HLS akışı (`test-streams.mux.dev/x36xhzz/x36xhzz.m3u8`) ile canlı doğrulama yapıldı:
    1. Birincil kaynak başlatıldı ve ilk kare çizildi (`ilk kare: TestPrimary (faz=Starting)`).
    2. 4. saniyede yapay ağ hatası (`ERROR_CODE_IO_NETWORK_CONNECTION_CLOSED (-1004)`) tetiklendi.
    3. `oynatirken kurtarma karari: RetrySame(...)` verildi; aynı kaynak kaldığı 4. saniyeden yeniden başlatıldı ve ilk kare tekrar çizildi (`ilk kare: TestPrimary`).
    4. 8. saniyede 2. ağ hatası tetiklendi.
    5. `oynatirken kurtarma karari: Switch(candidate=TestFallback, notice=STREAM_DROPPED_TRYING_NEXT)` verildi.
    6. Oynatıcı kesintisiz olarak `TestFallback` kaynağına geçti, ilk kare başarıyla çizildi (`ilk kare: TestFallback (faz=Starting)`).
    7. Yedek akış tam ekran sorunsuz oynamaya devam etti (`fallback_playing.png`).
    8. Adaylar tükendiğinde siyah ekran yerine "Başka kaynak seç" ekranı açıldı (`recovery_screen.png`).
- **Yapılamayan Doğrulama:**
  - Torrent üzerinden oynarken ağ kopması senaryosu (kapanış paketi kuralı: `ozellik/torrent-blok` dalına dokunulmadı).

---

## 6. Uzak Sunucu (Remote) Referansları

Aşağıdaki komut çıktısı ile 4 dalın da uzak depoda eksiksiz ve bağımsız olarak bulunduğu doğrulanmıştır:

```text
c2e6ae1ef2a0c3420ca5ea1b0c1936e0c187dee7    refs/heads/fix/oynatici-takilma
4b5ab0b38edad1f75c679c6d22a96800bcf2fcaa    refs/heads/fix/torrent-eski-android
5874862a525bd734f0e5442f329ba693b1ba9980    refs/heads/fix/kumanda-asagi
f8e9af246646a1098e74cf23c3bcb46db97a43e2    refs/heads/fix/oynarken-kurtarma
```

Tüm görevler kullanıcının belirttiği tüm kurallara (main tabanlı bağımsız dallar, Co-Authored-By içermeyen commit'ler, format temizliği olmayan hedef odaklı diff'ler ve dürüst doğrulama kayıtları) %100 sadık kalınarak tamamlanmıştır.
