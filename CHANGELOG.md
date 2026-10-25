# Changelog / Değişiklik günlüğü

## [1.1.12] — 2026-10-25

### Türkçe
- **Açılır-kapanır yan menü (TV ve geniş ekran):** Televizyon ve geniş ekranlarda yan menü artık kapalıyken dar bir simge şeridi; kumandayla sola gelince açılıyor, içeriğe daha çok yer kalıyor.

### English
- **Collapsible side navigation rail (TV and wide screens):** On TVs and wide screens, the side navigation rail is now a compact icon strip when idle and expands smoothly when focused via remote control, leaving more space for content.

## [1.1.11] — 2026-10-25

### Türkçe
- **Oynatma hızı seçeneği eklendi:** Oynatıcıdaki Parçalar paneline ses perdesi korumalı hız seçimi (0.75×, Normal, 1.25×, 1.5×, 1.75×, 2×) eklendi; hız değiştiğinde üst bilgi çubuğunda rozet gösterilir ve hız oturumluk uygulanır.
- **Zaman çizelgesindeki çerçeve kaldırıldı:** Oynatıcı açıldığında zaman çizelgesinin etrafında beliren camgöbeği odak çerçevesi kaldırıldı; D-pad ile odaklandığında yalnızca kaydırma tutamacı belirginleşecek şekilde arayüz sadeleştirildi.

### English
- **Added playback speed selection:** Added pitch-preserving playback speed options (0.75×, Normal, 1.25×, 1.5×, 1.75×, 2×) inside the Tracks panel; active speed is indicated with a top bar badge and applied on a per-session basis.
- **Removed timeline focus frame:** Removed the cyan rounded focus ring around the timeline bar; focusing via D-pad now subtly enlarges and highlights the thumb slider.

## [1.1.10] — 2026-10-25

### Türkçe
- **Çok sağlayıcılı aramalarda kilitlenme (ANR) düzeltildi:** Çok sayıda eklenti veya torrent kaynağı etkinken ana iş parçacığında meydana gelen sıralama yükü giderildi; magnet adreslerinde OkHttp istisna maliyeti engellendi ve akıllı sıralama önbelleği ile liste hesaplamaları hızlandırıldı.
- **Akıcı kaynak başlatma:** Ağır sıralama işlemleri arka plana taşınarak "Kaynaklar yükleniyor…" ekranındaki takılmalar ve çöp toplayıcı baskısı sonlandırıldı.

### English
- **Fixed freeze (ANR) when resolving multiple providers:** Eliminated main thread sorting overhead when multiple plugins or torrent sources are active; prevented OkHttp exception overhead on magnet links and accelerated ranking computations with a precomputed sorting key cache.
- **Smooth playback start:** Heavy ranking operations moved to background threads, ending UI freezes and garbage collection pressure on the "Loading sources…" screen.

## [1.1.9] — 2026-10-24

### Türkçe
- **Altyazı seçerken donma düzeltildi:** Altyazı indirme, zip açma, ayrıştırma ve zaman çizgisi indeksleme işlemleri tamamen arka plana taşındı; panelde "Uygula" seçildiğinde pencere anında kapanarak arayüzün kilitlenmesi (ANR) engellendi.
- **Altyazı paketlerinde doğru bölüm seçimi:** Çok dosyalı sezon paketlerinde bölüm numarası (`S01E21` / `1x21` / `E21` / `21`) akıllı şekilde taranarak izlenen bölüme ait doğru dosya otomatik olarak seçiliyor.

### English
- **Fixed freeze when selecting subtitles:** Subtitle downloading, zip extraction, parsing, and timeline indexing now run completely in the background; the panel closes immediately upon clicking "Apply", preventing UI freezes (ANRs).
- **Correct episode selection in subtitle packs:** Multi-file season archives are intelligently matched against the episode number (`S01E21` / `1x21` / `E21` / `21`) to automatically select the matching episode subtitle.

## [1.1.8] — 2026-10-23

### Türkçe
- **Canlı kanallarda kara ekran düzeltildi:** Canlı TV yayınlarında eklentinin kendi kaynak sırasına uyuluyor ve IPTV sağlayıcılarının döndürdüğü sahte yer tutucu dosyalar atlanarak gerçek yayın akışı açılıyor.
- **Canlı yayında boyut gizleme ve kanal uyarısı:** Canlı yayınlarda dosya boyutu gösterilmez; hiçbir yayın kaynağı açılmazsa kullanıcıya "Bu kanal şu an oynatılamıyor" bilgilendirme mesajı gösterilir.
- **Oynatıcı kilit simgesi:** Ekran kilitlendiğinde kilit simgesi birkaç saniye sonra kendiliğinden gizlenir, ekrana dokunulduğunda veya kumanda tuşuna basıldığında yeniden belirir.

### English
- **Fixed black screen on live channels:** Follows original addon stream order for live TV and skips fake placeholder streams returned by IPTV providers to play the actual broadcast.
- **Hidden file size and live channel notice:** File sizes are hidden during live streams; displays a clear "This channel is currently unavailable" notice if no streams can be opened.
- **Player lock icon auto-hide:** When the player is locked, the lock icon automatically hides after a few seconds and reappears on tap or remote control input.

## [1.1.7] — 2026-10-23

### Türkçe
- **Nuvio addon ayrıntı sayfası:** Yüklü bir Nuvio addon'unun sunduğu kataloglar, kaynak türleri ve içerik sayıları ayrıntı sayfasında görüntülenir; istenmeyen kataloglar tek dokunuşla kapatılarak ana sayfa ve arama listelerinden gizlenebilir.
- **Mükerrer altyazıların tekilleştirilmesi:** Aynı adres ve içeriğe sahip mükerrer altyazılar listeden temizlendi; aynı eklenti ve dildeki farklı altyazılar ayırt edici etiketlerle (dosya/sürüm veya numara) netleştirildi.

### English
- **Nuvio addon detail page:** View catalogs, resource types, and item counts provided by installed Nuvio addons; toggle off individual catalogs to hide them from home shelves and provider lists.
- **Subtitle deduplication:** Identical subtitle streams are collapsed into a single entry; multiple distinct subtitles for the same provider and language are clearly labeled with distinguishing badges (version or sequential numbers).

## [1.1.6] — 2026-10-23

### Türkçe
- **Yeni eklenti depolarında eklentiler açık gelir:** Eklenen yeni CloudStream ve Nuvio eklenti depolarındaki eklentiler varsayılan olarak açık duruma gelir.
- **Türkçe dublaj açılış beklemesi 8 sn:** Türkçe dublaj önceliği seçildiğinde eklentilerin yanıt vermesi için açılış beklemesi 8 saniyeye çıkarıldı (dublajlı kaynak geldiğinde beklemeden anında başlar).
- **Yedeğe geçişte dublaj bekleme:** Kaynak hatasında sonraki adaya geçilirken dublajlı kaynak için bekleme ve geçiş akışı iyileştirildi.
- **CloudStream eklenti hatalarında anlaşılır mesaj ve kendini onarma:** Eklenti hatalarında anlaşılır mesaj gösterilir ve eklenti kendini onarır; release derlemelerinde teknik hata satırı gizlenir.
- **İnce ilerleme çubuğu:** Oynatıcı arayüzünde daha zarif ve modern ince ilerleme çubuğu tasarımı.
- **Yanlış/sahte kaynak eleme:** İçeriğin TMDB çalışma süresi ve meta verileriyle uyuşmayan sahte/yanlış video kaynakları elenir.

- **Canlı TV kanalları:** Nuvio eklentilerindeki canlı televizyon kanalları sorunsuz açılıyor ve doğrudan oynatılabiliyor; canlı yayınlarda sonraki bölüm butonu gizleniyor.
- **Sağlayıcı seçici kaydırma:** Çok sayıda eklenti kuruluyken açılan sağlayıcı seçici penceresi en alta inildiğinde akıcı biçimde yukarı kaydırılabiliyor.
- **Takılmada otomatik kaynak geçişi:** İzlenen kaynak yavaşlayıp takıldığında uygulama yayını kesmeden aynı saniyeden otomatik olarak başka bir kaynağa geçiyor.
- **Yavaş kaynakların geriye düşmesi:** Takılan ve yavaşlayan sunucular oturum boyunca listede geriye alınarak hızlı kaynaklar önceliklendiriliyor.
- **Sade hata bildirimleri:** Oynatıcı hata bildirimlerindeki kafa karıştırıcı teknik satırlar gizlendi, kullanıcı dostu sade mesajlar sağlandı.
### English
- **Plugins enabled by default in new repositories:** Newly added CloudStream and Nuvio plugin repositories now have their plugins enabled by default.
- **Turkish dub opening wait 8s:** Opening wait time increased to 8 seconds when Turkish dub is preferred to allow plugins to respond (starts immediately when a dubbed stream arrives).
- **Dubbed source wait on fallback:** Improved dub waiting and candidate selection flow when falling back after a stream error.
- **Clear CloudStream plugin error messages and self-healing:** Understandable error notices and automatic recovery on plugin failures; technical error details hidden in release builds.
- **Thin progress bar:** Elegant, modern thin progress bar design in the video player.
- **Elimination of wrong/fake sources:** Video sources that fail to match TMDB runtime or metadata are eliminated.

- **Live TV channels:** Live TV channels from Nuvio addons open reliably and play directly; next episode buttons are hidden during live streams.
- **Provider picker scrolling:** Smooth scrolling in the provider selector sheet even with large lists of installed extensions.
- **Automatic stream switching on stalls:** If a video source becomes slow or stalls, the app automatically switches to another source from the exact same second.
- **Deprioritizing slow servers:** Servers that stall are deprioritized for the remainder of the session, favoring faster alternatives.
- **Cleaner error notices:** Technical error lines are hidden from player error notices in favor of clean, user-friendly messages.
## [1.1.5] — 2026-10-22

### Türkçe
- **Web görünümünde kumanda desteği:** Sanal fare imleci, ekran klavyesi (arama ve form alanlarında otomatik açılır) ve video başladığında otomatik tam ekrana geçiş eklendi.
- **Güçlü reklam ve popunder engeli:** EasyList ve Türkçe filtre listeleri derlenerek web görünümüne entegre edildi. Video oynatıcı üstündeki afişler ve sahte butonlar gizlendi, yeni pencere/popunder açılması engellendi.
- **Tam ekranda imleç:** Tam ekrana ve yatay düzene geçildiğinde imlecin doğru konumlandırılması ve görünürlüğü sağlandı.

### English
- **Remote control support in web view:** Added virtual cursor, on-screen keyboard (auto-opens for search and input fields), and automatic fullscreen when video playback starts.
- **Stronger ad and popunder blocking:** EasyList and Turkish filter lists integrated into web view. Floating ad banners and fake buttons over video players hidden, popunder and new window creation blocked.
- **Fullscreen cursor visibility:** Improved cursor centering and visibility when entering fullscreen and landscape orientation.


## [1.1.4] — 2026-10-21

### Türkçe
- **Kurulu tüm depolar:** Ekran artık tüm Nuvio ve CloudStream depolarını gösteriyor (Nuvio addon'ları, Nuvio eklenti depoları ve CloudStream depoları; iki bölümde).
- **Eklenti seçici:** Nuvio bölümü en üstte, CloudStream bölümü altında; iğnelenenler kendi bölümünün başında. Yalnızca katalog veren Nuvio kaynakları listelenir.
- **Seçim düzeltmesi:** Eklenti seçerken seçimin uygulanmaması düzeltildi; seçim hemen uygulanır, düğme yeni adı gösterir ve ana sayfa yeni sağlayıcının raflarına geçer.

### English
- **All installed repositories:** The screen now shows every Nuvio and CloudStream repository (Nuvio addons, Nuvio plugin repositories and CloudStream repositories, in two sections).
- **Addon picker:** The Nuvio section comes first, CloudStream below it; pinned items stay at the top of their own section. Only Nuvio sources that provide catalogs are listed.
- **Selection fix:** Fixed picking an addon not taking effect; the choice now applies right away, the button shows the new name and the home screen switches to the new provider's rows.

## [1.1.3] — 2026-10-21

### Türkçe
- **Kumandayla sarma düzeltmesi:** Kumandayla ilerleme çubuğunda sarınca videonun başa dönmesi düzeltildi. Parmakla sürükleyerek sarma eskisi gibi çalışır.
- **Telegram kanalı duyurusu:** "Play Telegram kanalı açıldı" duyurusu eklendi (1.1.3 ve sonrası, 31 Aralık 2026'ya kadar).

### English
- **Remote seek fix:** Seeking on the progress bar with a remote no longer jumps the video back to the start. Dragging with a finger works as before.
- **Telegram channel announcement:** Added the "Play Telegram channel is open" announcement (1.1.3 and later, until 31 December 2026).

## [1.1.2] — 2026-10-21

### Türkçe
- **Ağ koruması çökme düzeltmesi:** Ağ koruması (`PlayNetworkGuard`) yerel/özel bir adrese giden isteği engellediğinde uygulama artık kapanmaz; istek sıradan bir ağ hatası gibi sessizce başarısız olur (ör. yerel ağdaki bir addon adresi). Engellenen adresler değişmedi.
- **Telegram duyurusu:** "Play yayında" duyurusu Telegram kanalı bağlantısıyla ("Aç" düğmesi) yenilendi; 1.1.2 ve sonrasında, 31 Aralık 2026'ya kadar gösterilir.

### English
- **Network guard crash fix:** When the network guard (`PlayNetworkGuard`) blocks a request to a local/private address, the app no longer closes; the request fails quietly like a normal network error (e.g. an addon on the local network). The set of blocked addresses is unchanged.
- **Telegram announcement:** The "Play is live" announcement now links to the Telegram channel ("Open" button); shown on 1.1.2 and later, until 31 December 2026.

## [1.1.1] — 2026-10-21

### Türkçe
- **Hazır katalog: Play Özel Kataloglar (Kullanıcı kararı 2026-10-21):** İlk kurulumda (ve altyazı eklentilerini önceden almış kullanıcılarda bir kez) Play Özel Kataloglar katalog eklentisi açık olarak eklenir; yalnızca katalog verir, akış vermez. Silinirse geri gelmez. Rafları ana sayfada diğer katalog eklentilerinin ve TMDB raflarının önünde gelir; aynı adlı TMDB rafı ("Popüler filmler/diziler") tekrar gösterilmez. Sunucu kapalıysa raflar sessizce gizlenir.
- **"Play yayında" duyurusu:** Uygulama içi duyuru eklendi (30 Kasım 2026'ya kadar).
- **Tek komutluk otomatik duman testi (Kullanıcı kararı 2026-10-21):** `python tools/smoke_test.py` ile uygulamanın ana yollarını (CloudStream eklentileri, Nuvio addon'ları, JS kazıyıcılar, AltyazıDB/AltyazıTR/çevrimiçi altyazı, bölüm devam ve GitHub güncelleme/duyuru) en çok 15 dakikada deneyen ve `docs/smoke-test.md` tablosu üreten otomatik test altyapısı eklendi. Önceki çalıştırmalarla (`docs/smoke-test.prev.json`) karşılaştırılarak gerilemeler (regresyonlar) tespit edilir ve gerileme durumunda betik hata koduyla sonlanır.

### English
- **Built-in catalog: Play Custom Catalogs (User decision 2026-10-21):** On first install (and once for users who already received the subtitle addons) the Play Custom Catalogs addon is added enabled; it provides catalogs only, no streams. Once deleted it does not come back. Its rows come first on the home screen, before other catalog addons and TMDB rows; a TMDB row with the same name ("Popular movies/series") is not shown twice. If the server is down, the rows are hidden silently.
- **"Play is live" announcement:** In-app announcement added (until 30 November 2026).
- **Single-command automated smoke test (User decision 2026-10-21):** Added `python tools/smoke_test.py` automated test runner that verifies main application paths (CloudStream plugins, Nuvio addons, JS scrapers, AltyazıDB/AltyazıTR/online subtitles, next episode, GitHub update & announcements) in at most 15 minutes and outputs `docs/smoke-test.md`. Compares against previous runs (`docs/smoke-test.prev.json`) to detect regressions.

## [1.1.0] — 2026-10-21

### Türkçe
- **Tek evrensel APK (Kullanıcı kararı 2026-10-19):** Telefon, tablet ve Android TV aynı APK dosyasını kullanır. `splits.abi` kapatılarak tek evrensel APK'ya (`Play-1.1.0.apk`) geçildi (`arm64-v8a`, `armeabi-v7a` ve `x86_64` yerel kütüphaneleri aynı dosyada). Uygulama içi güncelleme önce tek evrensel APK'yı arar, bulunamazsa eski sürümlerle uyumluluk için işlemci mimarisine özel adlara geri düşer.
- **Hazır altyazı eklentileri (Kullanıcı kararı 2026-10-20):** İlk kurulumda AltyazıDB ve AltyazıTR altyazı eklentileri otomatik olarak eklenir ve açık gelir; video kaynağı/kataloğu gömülmez. Kullanıcı silerse bir daha geri gelmez. Menü taşmasını önlemek için eklenti ve dil başına en çok 10 altyazı sonucu listelenir.
- **CloudStream Sıradaki Bölüm ve Devam Et Akışı:** Bitirilen CloudStream dizilerinde bir sonraki bölüm (`CsNextEpisodeFinder`) "İzlemeye devam et" satırında "Sıradaki: SX BX" olarak listelenir, tıklandığında doğrudan sıradaki bölüm oynatılır. Detay ekranındaki sezon seçimi ve bölüm tıklama akışı sağlamlaştırıldı.
- **HTTP/2 PROTOCOL_ERROR Teşhisi ve HTTP/1.1 Geri Dönüşü:** Sunucu kaynaklı HTTP/2 akış sıfırlama (PROTOCOL_ERROR) durumlarında HTTP/1.1'e otomatik geri dönüş (`executeWithHttp1Fallback`) eklendi; tanı aracında torrent akışları atlama/ayrıştırma mantığı düzeltildi.
- **Nuvio JS Eklentileri ve CloudStream Zaman Aşımı İyileştirmesi:** QuickJS eklentilerinde (Dizilla, SelcukFlix) geçersiz TMDB anahtarları ağ köprüsünde gerçek anahtarla ikame edildi (`substituteTmdbKey`); CloudStream kaynak çözümleme zaman aşımı 30 saniyeye çıkarıldı.
- **Altyazı Başlıkları ve 403 Engellerinin Çözülmesi:** CloudStream ve Nuvio eklentilerinde altyazıların 403 Forbidden hatası almasını önlemek için akışın `Referer`, `User-Agent` ve `proxyHeaders` başlıkları altyazı indirme isteklerine iletildi (başlıklar loglanmaz). FilmMakinesi ("By Any Means" 1275 satır Türkçe altyazı) ve çevrimdışı altyazı indirme istekleri doğrulandı.
- **Başlık Temizleme (`TitleCleaner`):** Film ve dizi başlıklarındaki site ekleri ("izle", "Türkçe Dublaj izle", "Full HD izle", "(2026)" vb.) temizlendi, yıl bilgisi ayrıştırılarak ekranda ve aramalarda temiz başlık kullanımı sağlandı.
- **Çevrimiçi Kimlik Çözümleme Pipeline'ı (`MediaIdentityResolver`):** CloudStream içeriklerinde eksik olan IMDb/TMDB kimlikleri `syncData` -> TMDB başlık+yıl araması -> `external_ids` -> SubDL başlık araması sırasıyla çözülerek `PlaySession`'a ve EN->TR otomatik çeviriye bağlandı.
- **Oynatma ve Altyazı Tanısı Güçlendirmesi:** Tanı aracına altyazı doğrulama (eklenti ve internet altyazı sayıları/cues) ve Nuvio kaynakları teşhis desteği eklendi; 29 CloudStream eklentisi ve 9 Nuvio kaynağıyla tam tanı çalıştırıldı, `docs/oynatma-tanisi.md` güncellendi.
- **Ağ Güvenliği Daraltması (`PlayNetworkGuard`):** `localhost` ve `127.x` üzerindeki serbest döngü adresleri yalnızca Play'in kendi Torrent HTTP sunucu portu ve eklentilerin çalışma anında dinamik açtığı yerel vekil portlarıyla sınırlandırıldı; yetkisiz loopback, `0.0.0.0`, `::` ve `::1` tamamen engellendi. IPv6 sabit denetimi alan adlarından ayrıştırılarak `fc*` ile başlayan genel alan adlarının (örn. `fcbarcelona.com`) yanlışlıkla engellenmesi giderildi. HintFilmIzle'nin yerel vekil üzerinden akışı doğrulandı.
- **Çıkarıcı (Extractor) İyileştirmeleri ve Yeni Çözücüler:**
  - **SezonlukDizi:** Byse CDN (`bysejikuar.com`) için `ByseJikuar` çıkarıcısı eklendi; sitenin güncel `dataAlternatif22` ve `dataEmbed22` AJAX uç noktaları HTML köprüsü ile desteklendi ve 1080p M3U8 akış doğrulaması sağlandı.
  - **SinemaTV:** `shortener2.link` M3U8 ana oynatma listelerindeki göreli rotalar `/content-router/r/` köprüsüyle normalize edilerek HTTP 401 yetkilendirme engeli çözüldü; 720p akışları sorunsuz oynatılır hale getirildi.
  - **DiziMag:** `japierdolevid.com` korumalı oynatıcısı için `JapierdoleExtractor` (AES-CBC ve challenge çözücü) eklendi ve birim testlerle doğrulandı.
- **Güçlendirilmiş Oynatma Tanısı ve Depo Önceliği:** Oynatma tanısında medya geçerliliği yalnızca HTTP HEAD ile değil, ilk ~512 KB'taki gerçek medya imzaları (`#EXTM3U`, MP4 `ftyp`, MKV `EBML`, TS `0x47`) ile ölçülecek şekilde güçlendirildi; bilinmeyen çözünürlüklerde `?` formatına geçildi. Tanılama aracında (`PlayDiagRunner`) eski/çalışmayan çatalların seçilmesini önlemek amacıyla eklentiler güncel kaynak depolarına (`gsrepo`, `WioSinema`, vb.) yönlendirildi; HLS oynatma listelerinde CDN'lerin HTTP 400 hatası vermesini önlemek amacıyla `.m3u8` isteklerinde `Range` başlığı kaldırıldı ve tanılama sıralı/kontrollü hale getirildi.
- **Canlı Depo Test İzolasyonu:** `CentralReposCompatibilityTest` canlı ağ bağımlılığı varsayılan birim test koşumundan ayrılarak `play.test.liveCentralRepos` sistem özelliğine bağlandı.
- **Evrensel Nuvio & CloudStream Depo Uyumluluğu ve Tek Dokunuşla Ekleme:**
  - **Karma ve Zengin Manifest Desteği:** `scrapers` dizisi içeren depolar (örn. Jasoelys, HANS.MEGA, MANİTUX, BRONZE CLOUD) Stremio'dan kalma `resources` veya `types` alanları içerse dahi `scrapers` alanına öncelik verilerek kesin olarak Nuvio Plugin deposu olarak sınıflandırılır; bilinmeyen üst düzey alanlar güvenle yok sayılır.
  - **Ayrıştırıcı Toleransı:** Kazıyıcılardaki `supportedTypes` ve `contentLanguage` alanlarının boşluk/virgülle ayrılmış düz metin (`"series tv"`, `"tr en"`) olarak gelmesi desteklendi. `hasSettings` metin `"true"` desteği eklendi. `stremio://` ve `cloudstreamrepo://` şemaları normalize edildi.
  - **Tek Dokunuşla Akıllı Öneri:** Kullanıcı bir eklenti bağlantısını yanlış kutuya (ör. Plugin kutusuna Addon veya CloudStream adresi) girdiğinde hata vermek yerine `"Bu bir <tür>. <tür> olarak ekleyeyim mi?"` (TR+EN) önerisi sunulur ve tek dokunuşla doğru türe geçip eklenir.
  - **57 Gerçek Depo Uyum Testi:** Topluluğun 57 deposunun (31 CloudStream, 26 Nuvio) tamamı canlı ağ üzerinde otomatik test edildi; 57 deponun tamamı %100 başarıyla eklendi ve yanlış kutu önerisi doğrulandı.
- **Nuvio Tarzı Birleşik Ana Sayfa ve Tembel Katalog Yükleme:**
  - **Varsayılan Birleşik Akış:** "Tüm sağlayıcılar" modunda kurulu ve açık her katalog addon'unun rafları (manifest'teki `catalogs` sırasıyla) ve iğnelenmiş (pin) CloudStream eklentilerinin rafları, TMDB raflarının üzerinde kendiliğinden alt alta listelenir. Hiçbir katalog addon'u veya iğneli eklenti yoksa TMDB düzeni korunur.
  - **Tembel ve Paralel Yükleme:** Raflar ekranda görünür oldukça tembel (lazy) olarak yüklenir; aynı anda en fazla 3 sağlayıcı paralel istek yapacak şekilde (`Semaphore(3)`) sınırlandırılmıştır. 5 dakikalık önbellek geçerlidir. Bir sağlayıcı hata verirse ekran hata kartlarıyla doldurulmadan o sağlayıcının rafları sessizce gizlenir.
  - **Raf Başlığı ve Sağlayıcı Rozeti:** Her rafın sol üstünde katalog adı, altında sağlayıcı rozeti (ör. "Jasoelys"); sağ üstte ise "Tümünü göster >" düğmesi yer alır.
  - **Katalog Tam Sayfa & Sayfalama:** "Tümünü göster" tıklandığında `ProviderCategoryScreen` açılarak kataloğun tüm içeriği ızgarada listelenir, aşağı kaydırıldıkça sonraki sayfalar (`loadMore`) otomatik yüklenir.
- **Katalog Eklentileri, Afiş Doğrudan Detay ve Seçici Filtreleme:**
  - **Katalog Addon'ları Desteği:** `AddonManifestParser`, akış ya da altyazı sunmasa dahi `catalog` kaynağı ve dolu `catalogs` listesi içeren Stremio eklentilerini (örn. Jasoelys) kabul edecek şekilde güncellendi. Yalnızca katalog sunan eklentiler akış kaynak listesine dahil edilmez ve akış isteği almaz. Eklenti listelerinde türü "Katalog eklentisi" (EN: "Catalog extension") olarak gösterilir.
  - **Afiş Tıklaması ve Doğrudan Detay:** Katalog öğesinin kimliği `tt...` (IMDb) veya `tmdb:...` ise başlık araması yapılmadan doğrudan TMDB Detay ekranı açılır (`tt...` -> TMDB find API). Farklı kimliklerde eklentinin `meta` kaynağıyla basit detay ekranı açılır, bulunamazsa başlıkla aranır.
  - **Sağlayıcı Seçici Filtreleme:** Seçicide yalnızca kataloğu olan sağlayıcılar listelenir: `catalogs` listesi dolu Nuvio addon'ları ve `mainPage`'i olan CloudStream eklentileri (boş olanlar gizlenir ve önbelleklenir). Kataloğu olmayan Nuvio JS kazıyıcılar ve yalnızca akış/altyazı veren eklentiler seçiciden kaldırıldı, ancak video kaynağı olarak eksiksiz çalışmaya devam eder. Önceden seçilmiş gizlenen sağlayıcılar seçimden sessizce düşürülür; geçerli seçim kalmazsa "Tüm sağlayıcılar" (TMDB) açılır.
- **CloudStream Eklentileri Video Akışı & Çıkarıcı (Extractor) Düzeltmesi:**
  - **`LoadResponse.addActorsOnly` ClassCastException Düzeltmesi:** AnimeciX ve DiziBox eklentilerinin `load()` aşamasında `List<Actor>` nesnelerini eklerken JVM üzerinde `Actor cannot be cast to String` hatasıyla çökmesi ve video aramasını durdurması giderildi; `addActors`, `addActorNames` ve `addActorsOnly` yöntemleri tüm aktör türlerini (`ActorData`, `Actor`, `String`) güvenle işleyecek şekilde güncellendi.
  - **Gelişmiş Sezon/Bölüm Eşleme (`resolveData`):** Eklentilerden dönen bölümlerin (`AnimeLoadResponse` ve `TvSeriesLoadResponse`) sezon/bölüm numaralarının bulunamadığı durumlarda tam eşleşme, başlık/URL üzerinden bölüm numarası çıkarma ve indeks tabanlı akıllı eşleme mantığı eklendi.
  - **Genel Web & Paketli JS Çıkarıcı Desteği (`JsUnpacker`):** VidMoly, Streamtape, FileLions ve genel gömülü oynatıcılarda kullanılan Dean Edwards packed JS (`eval(p,a,c,k,e,d)`) kodlarını çözen `JsUnpacker` eklendi; `loadExtractor` yerleşik çıkarıcısı olmayan kaynaklarda HTML ve paketli betikleri analiz ederek `.m3u8` ve `.mp4` video bağlantılarını otomatik olarak çıkarır hale getirildi. AnimeciX (TauVideo) ve DiziBox üzerinde Hanma Baki S1 B1 video oynatması LDPlayer üzerinde test edilip doğrulandı.
- **Navigasyon ve Arama Ekranında Kalma Sorunu Düzeltmesi:**
  - Arama veya alt ekranlardayken menüden (alt menü / yan menü) "Ana sayfa" sekmesine tıklandığında Navigation Compose'un startDestination üzerindeki kayıtlı çocuk yığınını geri yüklemesi sebebiyle ekranın Arama ekranında takılı kalması giderildi; `openTab(Tab.Home)` doğrudan `popBackStack(Tab.Home.route, inclusive = false)` ile ana sayfaya geri dönecek şekilde düzenlendi.
  - Sekme seçim kontrolü (`selected`) sorgu parametresi içeren yollar (`search?q=...`) için güncellendi.
- **Katalog Afiş Başlık Temizleme ve TMDB Eşleştirme İyileştirmesi:**
  - AnimeciX gibi sağlayıcıların afiş başlıklarına eklediği bölüm/sezon kalıpları (`S4B11 - LINK CLICK`, `1. Sezon 5. Bölüm - ...`, `... - 12. Bölüm`, `[Altyazılı]`, vb.) `cleanCatalogTitle` ile temizlenerek TMDB aramasına iletilmesi sağlandı.
  - Bu sayede afişe tıklandığında dizinin doğrudan Detay ekranı açılır; TMDB'de doğrudan eşleşme bulunamaması durumunda ise arama kutusuna temizlenmiş dizi adı yazılarak sıfır sonuçlu boş arama ekranı açılması engellendi.
- **DiziKorea ve Mükerrer Katalog Öğeleri Arayüz Çökmesi Düzeltmesi:**
  - Bazı sağlayıcıların (ör. DiziKorea "Çin Dizileri" rafı) aynı içerik kartını birden fazla kez döndürmesi durumunda Jetpack Compose'un `LazyRow` bileşeninde tetiklenen `IllegalArgumentException: Key was already used` hatası ve uygulamanın kapanması giderildi.
  - `CsCatalog` ve `NuvioAddonCatalog` katmanında öğeler `distinctBy` ile temizlendi.
  - `HomeScreen` üzerinde katalog satırları ve afiş kartları için `itemsIndexed` kullanılarak anahtarlara benzersiz liste indeksi eklendi (`${row.id}_${item.id}_$index`), mükerrer veri gelse dahi arayüzün çökmesi kesin olarak önlendi.
- **CloudStream Eklentileri Evrensel ABI ve AnimeciX Düzeltmeleri:**
  - **AnimeciX & Anime Yanıt Desteği:** `AnimeSearchResponse` sınıfına `sub` ve `dub` alanları, `MainAPI` sınıfına `newAnimeSearchResponse` ve `newAnimeLoadResponse` fabrika yöntemleri eklenerek AnimeciX'in `NoSuchMethodError: newAnimeSearchResponse$default` hatası giderildi; "Son Eklenen Bölümler", "Seriler" ve "Filmler" rafları afişleriyle yüklendi.
  - **40 CloudStream DEX Eklentisi Kapsamlı Otomasyon Denetimi:** Cihazdaki 40 eklentinin tamamı otomatik test aracıyla denendi; 38 eklenti %100 başarıyla onlarca raf ve yüzlerce afiş listeledi. Yalnızca 2 eklentide dış servis kaynaklı sınırlama (Dizipal ISP bağlantı sıfırlama, SineWix kapatılan API sunucusu) tespit edildi, sıfır çökme sağlandı.
  - **Doğrudan `BasePlugin` Kalıtımı (`CsDexLoader`):** `BasePlugin`'i doğrudan genişleten eklentiler (FilmMakinesi, HDFilmCehennemi) tanınarak `load()` ve `load(context)` fonksiyonları çalıştırıldı; FilmMakinesi (11 raf) ve HDFilmCehennemi (20 raf) katalogları başarıyla yüklendi.
  - **Evrensel ABI Kapsamı:** `LoadResponse.imdbId`, `LoadResponse.addActorNames/addActorsOnly/addImdbId`, `AppUtils.toJson`, `SingleSelectionHelper.showDialog`, `com.lagradost.api.Log`, `ExtractorApi.getQualityFromName`, `INFER_TYPE`, `ExtractorApi.fixUrl`, `ExtractorLink` 9-argümanlı kurucusu ve `NiceResponse.body` uygulandı.
  - **Hint Film Izle & Çift Paket Desteği:** CloudStream ekosistemindeki eklentilerin farklı paketlerden (`com.lagradost.cloudstream3` ve `com.lagradost.cloudstream3.network`) import ettiği `CloudflareKiller` ikili arayüz shimi ile her iki yoldan da erişilebilir hale getirildi; `NoClassDefFoundError` giderilerek Hint Film Izle kataloğu (20+ raf) çalıştırıldı.
  - **WebteIzle & Otomatik Kodlama Algılama (`NiceHttp`):** Windows-1254 (ISO-8859-9) gibi UTF-8 dışı kodlama kullanan yerel siteler için HTTP `Content-Type` ve HTML `<meta charset>` etiketlerine dayalı otomatik kodlama algılama eklendi; Türkçe karakterlerin (`ı, ş, ğ, ü, ö, ç`) soru işareti (`?`) olarak bozulması önlendi.
  - **Sıralı İstek Throttling:** Çok kategorili sağlayıcılarda HTTP 429 bot koruması oran sınırına takılmamak için raflar arasına 100 ms gecikme eklendi.
- **DiziBox "Katalog içeriği bulunamadı" düzeltmesi:** Uygulama yeniden açılınca WebView'de kalan eski Cloudflare çerezi "geçerli" sanılıyor, site yine engel sayfası verdiği için katalog boş görünüyor ve "Doğrula" düğmesi çıkmıyordu. Artık çerezle de engel gelirse eski çerez silinir, bir kez taze çözülür, olmazsa kartta "Doğrula" çıkar. Doğrulama ekranı eski çerezle kendiliğinden kapanmaz. Engel, eklentinin gittiği gerçek alan adından algılanır.
- **Cloudflare Turnstile İnteraktif Doğrulama (`CloudflareVerifyActivity`):** Arka plan gizli çözücünün otomatik geçemediği Cloudflare Turnstile ("Ben robot değilim" onay kutusu) korumaları için kullanıcı etkileşimli tam ekran doğrulama aktivitesi eklendi. Ana sayfa hata kartında çıkan "Doğrula" düğmesine basıldığında güvenli ve temiz bir WebView açılır. Kullanıcı kutucuğu işaretlediği anda `cf_clearance` algılanır, çerezler ve WebView'in User-Agent'ı `CloudflareCache`'e yazılır, aktivite otomatik kapanır ve katalog yenilenir. Cloudflare'in `cf_clearance` çerezini challenge'ı çözen tarayıcının User-Agent'ına bağlaması sebebiyle, `NiceHttp` ve `CloudflareKiller` katmanında önbellekteki gerçek WebView User-Agent'ı isteğe enjekte edilerek 403 engelleri aşılır. DiziBox üzerinde test edilip onaylandı.
- **Cloudflare Challenge & WebViewResolver Çözücü:** CloudStream eklentilerinin Cloudflare bot koruması (`HTTP 403`, `503`, `Cf-Mitigated: challenge`, Turnstile) ile korunan sitelerdeki isteklerini aşmak için gizli ekran dışı Android WebView tabanlı `CloudflareSolver` ve OkHttp `CloudflareKiller` (Interceptor) mimarisi eklendi. `WebViewResolver` ikili arayüzü gerçek arayüze bağlanarak dinamik URL çözme aktif edildi. `NiceHttp` istek katmanına genel challenge algılama ve tek seferlik otomatik yeniden deneme eklendi. Alan adı bazlı çerez izolasyonu (`CloudflareCache`) sağlandı. Challenge süresince ana sayfada "Site doğrulanıyor…" rozeti ve iskelet animasyonu gösterilir; interaktif Turnstile gibi bot koruması aşılamayan durumlarda "Site doğrulaması gerekiyor" hata kartı, "Doğrula" ve "Yeniden dene" aksiyonları sunulur. HDFilmCehennemi, Dizilla, FilmMakinesi, SetFilm, FullHDFilmizlesene, YesilcamTV ve Cinemeta katalogları LDPlayer üzerinde test edilip doğrulandı.
- **Sağlayıcı kataloğu (Aşama B):** Seçili eklentinin kendi kataloğunu doğrudan ana sayfada gösterme desteği eklendi. CloudStream eklentileri için `MainAPI.mainPage` ve `getMainPage(page, request)` ikili arayüzü birebir uygulanarak eklentinin kendi ana sayfa satırları (Dizilla, SetFilm, FilmMakinesi vb.) afişleriyle çekilir. Nuvio Stremio addon'ları için manifest'teki `catalogs` listesi üzerinden `{base}/catalog/{type}/{id}.json` ile katalog çekilir (Cinemeta popüler filmler ve diziler). Katalog sunmayan Nuvio JS kazıyıcılar seçildiğinde bilgilendirme kartı ve TMDB beslemesine yönlendirme sunulur. 5 dakikalık bellek içi önbellek (`CatalogCache`), her satır çağrısı için 10 sn bağımsız zaman aşımı ve çökme koruması, hata durumunda "Yeniden dene" desteği, yükleme anında iskelet yer tutucular mevcuttur. Çoklu eklenti seçiminde satırlar sıralı olarak birleştirilir ve sağlayıcı etiketleriyle gösterilir. Sağlayıcı afişine basıldığında içerik başlığı TMDB ile eşleştirilerek Detay ekranı açılır (eşleşmezse arama açılır). Görsel yüklemelerinde Cloudflare/CDN HTTP 403 engeli tarayıcı User-Agent'ı enjeksiyonu ile çözüldü.
- **Sağlayıcı merkezli ana sayfa (Aşama A):** ana sayfanın sağ altına seçili eklenti adını (ya da "Eklenti seç") gösteren yüzen bir hap buton eklendi. Butona dokunulunca açılan "Bir uzantı seçin" seçici ekranında CloudStream, Nuvio addon ve Nuvio plugin'leri aranabilir, filtrelenebilir (Hepsi / CloudStream / Nuvio) ve sabitlenebilir (sabitlenenler üste çıkar). Tek dokunuş tek sağlayıcıyı seçer; yarım saniye (0.5s) basılı tutma çoklu seçim modunu açar (birden fazla sağlayıcı ve üstte "Uygula" düğmesi). Seçim DataStore'da kalıcıdır. Bir sağlayıcı seçildiğinde ana sayfada iskelet yer tutucular ve "Katalog yakında" gösterilir; "Tüm sağlayıcılar" seçiliyken mevcut TMDB akışı aynen korunur. TV/kumanda tam desteklenir.
- **Hızlı başlangıç:** filme/bölüme basınca kaynak listesi beklenmez; uygun ilk kaynak bulunur bulunmaz oynatma başlar, kaynaklar arka planda yüklenirken "Kaynaklar yükleniyor…" gösterilir. Eski davranış (önce kaynak listesi) Ayarlar > Oynatıcı > "Kaynağı otomatik seç" kapatılınca geri gelir.
- **Dublajlı / Altyazılı etiketi:** her kaynak etiketlenir. Dublaj; "TR Dublaj", "Türkçe Dublaj", "Dublajlı", "Dublaj", "TR Dub", "Turkish Dubbed", "Dual Audio TR", "TR-EN" ifadelerinden anlaşılır (büyük/küçük harf ve Türkçe karakter fark etmez); "dublajsız" dublaj sayılmaz; emin olunamayan kaynak "Altyazılı" olur.
- **Başlangıç sırası:** Türkçe dublajlı kaynak için en çok 3 sn beklenir (Ayarlar > Oynatıcı > "Türkçe dublajı öncele", varsayılan açık). Dublaj yoksa altyazılı başlar ve Türkçe altyazı kendiliğinden açılır (videonun içindeki Türkçe → çevrimiçi Türkçe → videonun içindeki İngilizce). Dublajlı kaynak sonradan gelirse köşede "Dublajlı kaynak bulundu, geçilsin mi?" kartı çıkar; geçiş aynı saniyeden sürer. Elle seçtiğin altyazıya/sese dokunulmaz.
- **Akıllı sıralama:** doğrudan bağlantı torrent'ten önce gelir; cihazın çözemeyeceği çözünürlükteki (ör. 4K) ve çok büyük (~15 GB+) dosyalar sona kalır; önceki seçimin önde gelir. İlk görüntü 10 sn içinde gelmezse (torrent'te 40 sn) ya da hata olursa sıradaki kaynağa geçilir; hiçbiri açılmazsa "Başka kaynak seç" çıkar, ekran siyah kalmaz. Videoda gömülü Türkçe ses varsa kendiliğinden seçilir ("Dublajlı (gömülü ses)").
- **Oynatıcı arayüzü:** üstte kaynak adı · çözünürlük · boyut (ör. "Ana Kaynak 1 · 1920×872 · 22 GB") ve altında başlık; ortada −10 / oynat-durdur / +10; altta ilerleme çubuğu. Alt düğmeler: Kilitle, Yeniden boyutlandır (sığdır / doldur / uzat / 16:9 / 4:3; kalıcı), Altyazı senkronu, Kaynak, Parçalar.
- **Kilit:** dokunuşları ve hareketleri (ses/parlaklık dahil) kapatır; yalnızca kilit açma simgesi görünür.
- **Kaynak paneli:** eklentiye göre gruplu kaynaklar (etiket, çözünürlük, boyut, doğrudan/torrent, çalıyor işareti), sıralama (Önerilen / Boyut / Çözünürlük) ve süzgeç (Tümü / Dublajlı / Altyazılı); kaynaklar yüklendikçe liste dolar. Sağda altyazılar: "Altyazı yok", videonun içindekiler, "Dosyadan yükle", "İnternetten yükle", "İlk kullanılabiliri yükle". **Uygula**'ya basana kadar hiçbir şey değişmez, **İptal** her şeyi bırakır.
- **Parçalar paneli:** video kaliteleri ("Otomatik" dahil) ve ses parçaları ("Türkçe • Stereo • AAC").
- **Kaynak değişince** oynatma aynı saniyeden sürer; yeni kaynak açılmazsa önceki kaynağa dönülür ve kısa bir mesaj çıkar. Elle seçilen kaynak aynı dizinin sonraki bölümünde ilk denenir; torrent'ten ayrılınca eski torrent oturumu kapanır.
- **Dosya boyutu:** eklentinin verdiği boyut → torrent bilgisi → HTTP HEAD (`Content-Length`); sessizce başarısız olur, oynatmayı geciktirmez. Bilinmiyorsa hiçbir şey yazılmaz ("0 GB" asla çıkmaz).
- **Çevrimiçi altyazı:** IMDb kimliğiyle (dizide sezon/bölümle) SubDL (anahtar varsa) + OpenSubtitles tek listede; Türkçe önce, sonra İngilizce; sonuçta dil, kaynak adı ve sürüm adı görünür. .zip içinden .srt/.vtt/.ass çıkarılır, kodlama (UTF-8 / Windows-1254) bulunur. Sonuçlar film başına önbelleğe alınır. Ağ hatasında kısa bir mesaj çıkar, oynatma bozulmaz; hiç altyazı yoksa belirgin bir "Çevrimiçi ara" satırı görünür.
- Kumanda (TV): paneller D-pad ile gezilir, Tamam seçer, Geri paneli kapatır.
- **SubDL Türkçe sonuçları:** altyazı dil dil aranır (önce yalnız Türkçe; hiç Türkçe yoksa İngilizce). Eskiden tek istekte iki dil istenince 30'luk sayfa İngilizceyle doluyor, Türkçe çoğu kez tek sonuç kalıyordu (Matrix: 1 → 14). Önbellek film/bölüm ve dil başınadır.
- **Anahtar önbelleğe yazılmaz:** SubDL cevabındaki adresler `api_key` içerir; artık adreslerden silinir (indirme anonim yapılır), arama önbelleğine yazılmaz, eski önbellek dosyalarında kalan anahtar okunurken temizlenir.
- **Altyazı otomatik çeviri:** Türkçe altyazı hiçbir kaynakta yoksa ve İngilizce altyazı varsa, İngilizce cihaz üstünde (Google ML Kit, sunucuya metin gitmez) Türkçeye çevrilir. İngilizce hemen görünür, çeviri arka planda biter ("Altyazı çevriliyor… %n"), elle seçmediysen otomatik Türkçeye geçilir; menüde "Türkçe (otomatik çeviri)" ayrı satırdır, gerçek Türkçe her zaman önce gelir. Zamanlar korunur, çeviri önbelleğe alınır (aynı film yeniden çevrilmez), senkron ayarı çalışır. Dil modeli ilk kullanımda bir kez iner (~44 MB, "Sadece Wi-Fi'de indir" ayarına uyar). Ayarlar > Oynatıcı > "Türkçe yoksa İngilizceyi otomatik çevir" (varsayılan açık). APK ABI başına ~6–7,5 MB büyür.

### English
- **Single universal APK (User decision 2026-10-19):** Phone, tablet, and Android TV use the same APK file. Disabled `splits.abi` in favour of a single universal APK (`Play-1.1.0.apk`) containing `arm64-v8a`, `armeabi-v7a`, and `x86_64` native libraries. In-app update first searches for the universal APK and falls back to ABI-specific names for older releases.
- **Pre-seeded subtitle addons (User decision 2026-10-20):** AltyazıDB and AltyazıTR subtitle addons are pre-seeded and enabled on first launch; no video catalogs are bundled. If removed, they are never re-added. Capped at 10 results per addon and language to prevent UI overflow.
- **Universal Nuvio & CloudStream Repository Compatibility & One-Tap Suggestion:**
  - **Mixed Manifest Classification:** Repositories containing a `scrapers` array (e.g. Jasoelys, HANS.MEGA, MANİTUX, BRONZE CLOUD) are prioritized and classified strictly as Nuvio Plugin repositories even if they contain extraneous `resources` or `types` fields; unknown root fields are safely ignored.
  - **Parser Tolerance:** Handled space/comma-separated strings for scraper `supportedTypes` and `contentLanguage` (`"series tv"`, `"tr en"`). Added boolean string `"true"` support for `hasSettings`. Normalized `stremio://` and `cloudstreamrepo://` schemes.
  - **One-Tap Type Suggestion:** When entering a repository into the wrong add dialog, a friendly banner suggests `"This is a <type>. Would you like to add it as a <type>?"` with a one-tap action to switch type and install.
  - **57 Central Repos Tested:** Verified all 57 community repositories (31 CloudStream, 26 Nuvio) with 100% installation success and wrong-box suggestion verification.
- **Nuvio-Style Unified Home Screen & Lazy Catalog Loading:**
  - **Default Unified Feed:** When "All providers" mode is active, shelves from every installed and enabled catalog addon (in manifest catalog order) and pinned CloudStream extensions are automatically stacked vertically above TMDB rows on the Home screen. When no catalog addons or pins exist, the default TMDB layout is preserved.
  - **Lazy & Throttled Loading:** Catalog shelves load lazily as they scroll into view, throttled to a maximum of 3 concurrent provider requests via `Semaphore(3)`. 5-minute memory cache is applied. If a provider fails to load, its rows are hidden silently without cluttering the screen with error cards.
  - **Row Header & Provider Badge:** Each shelf displays its catalog title and provider badge (e.g. "Jasoelys") with a "Show all >" button on the right.
  - **Full-Page Catalog Grid & Pagination:** Tapping "Show all" opens `ProviderCategoryScreen` displaying the entire catalog in a grid with infinite-scroll pagination (`loadMore`).
- **Catalog Addons Support, Direct Poster Details & Selector Filtering:**
  - **Catalog Addon Support:** `AddonManifestParser` now accepts Stremio addons that provide `catalog` resources and a non-empty `catalogs` list (e.g. Jasoelys), even without stream or subtitle resources. Addons providing catalogs only do not enter stream source lists or receive stream requests. Displayed as "Katalog eklentisi" / "Catalog extension" in addon lists.
  - **Direct Poster Details:** Clicking a catalog item with an IMDb ID (`tt...`) or TMDB ID (`tmdb:...`) directly opens the TMDB Detail screen without querying search (`tt...` -> TMDB find API). Other IDs fallback to the addon's `meta` resource, or title search.
  - **Provider Selector Filtering:** Only providers with catalogs are listed in the selector: Nuvio addons with non-empty `catalogs` and CloudStream extensions with valid `mainPage` definitions (empty ones are hidden and cached). Catalog-less Nuvio JS plugins and stream/subs-only addons are excluded from the selector, but continue functioning as stream sources. Deselected providers gracefully fall back to TMDB ("Tüm sağlayıcılar").
- **CloudStream Plugins Video Extraction & Playback Fixes:**
  - **`LoadResponse.addActorsOnly` ClassCastException Fix:** Resolved a `ClassCastException` in AnimeciX and DiziBox when passing `List<Actor>` to `addActorsOnly`, which crashed the `load()` step and prevented video discovery.
  - **Smart Episode Resolution (`resolveData`):** Added fuzzy episode number extraction from titles/URLs and index-based fallbacks for `TvSeriesLoadResponse` and `AnimeLoadResponse`.
  - **Generic Web & Packed JS Stream Extraction (`JsUnpacker`):** Added `JsUnpacker` for unpacking Dean Edwards packed JavaScript (`eval(p,a,c,k,e,d)`); updated `loadExtractor` to automatically parse embed pages (such as VidMoly, Streamtape, FileLions) for direct `.m3u8` and `.mp4` streams. Verified video playback with AnimeciX (TauVideo) and DiziBox on LDPlayer.
- **Navigation Trap & Home Tab Backstack Fix:**
  - Fixed an issue where tapping the "Home" tab while on the Search screen (or sub-routes) trapped the user on Search due to Navigation Compose's start destination restoring saved child backstack state; `openTab(Tab.Home)` now explicitly calls `popBackStack(Tab.Home.route, inclusive = false)` to unconditionally return to the home screen.
  - Tab selection logic (`selected`) now properly matches parameterized query routes (`search?q=...`).
- **Catalog Item Title Cleansing & TMDB Matching:**
  - Added `cleanCatalogTitle` to strip episode/season prefixes (e.g. `S4B11 - LINK CLICK`), suffixes (` - 12. Bölüm`), and quality/dub/sub tags before querying TMDB.
  - Clicking catalog posters directly resolves and opens the show's DetailScreen; if TMDB yields no match, search opens with the sanitized title rather than raw unmatchable episode tags.
- **DiziKorea & Duplicate Catalog Item Crash Fix:**
  - Resolved `IllegalArgumentException: Key was already used` in Jetpack Compose `LazyRow` triggered when providers return duplicate items in the same section (e.g. DiziKorea "Çin Dizileri").
  - Deduplicated item lists in `CsCatalog` and `NuvioAddonCatalog` using `distinctBy`.
  - Updated `HomeScreen` catalog lists to use `itemsIndexed` with unique index-suffixed keys (`${row.id}_${item.id}_$index`), ensuring complete resilience against duplicate items across all providers.
- **Universal CloudStream ABI & AnimeciX Fixes:**
  - **AnimeciX & Anime Response Support:** Added `sub` and `dub` properties to `AnimeSearchResponse` and factory functions `newAnimeSearchResponse` and `newAnimeLoadResponse` to `MainAPI`, resolving AnimeciX's `NoSuchMethodError` and loading its episodes, series, and movie rows with posters.
  - **40 CloudStream DEX Plugins Comprehensive Device Audit:** Automated device testing across all 40 installed DEX plugins confirmed 38 plugins 100% operational with full catalogs. Only 2 plugins experienced external upstream server issues (Dizipal ISP connection reset, SineWix deprecated API route), with zero app crashes.
  - **Direct `BasePlugin` Subclass Loading (`CsDexLoader`):** Added reflective discovery and loading for plugins directly subclassing `BasePlugin` (FilmMakinesi, HDFilmCehennemi), restoring 11 and 20 catalog rows respectively.
  - **Universal ABI Coverage:** Implemented `LoadResponse.imdbId`, `LoadResponse.addActorNames/addActorsOnly/addImdbId`, `AppUtils.toJson`, `SingleSelectionHelper.showDialog`, `com.lagradost.api.Log`, `ExtractorApi.getQualityFromName`, `INFER_TYPE`, `ExtractorApi.fixUrl`, 9-arg `ExtractorLink` constructor, and `NiceResponse.body`.
  - **Hint Film Izle & Dual-Package Support:** Added binary interface shims for `CloudflareKiller` across both package locations (`com.lagradost.cloudstream3` and `com.lagradost.cloudstream3.network`), resolving `NoClassDefFoundError` and successfully loading Hint Film Izle's 20+ categories.
  - **WebteIzle & Automatic Charset Detection (`NiceHttp`):** Added automatic charset detection via HTTP `Content-Type` headers and HTML `<meta charset>` tags for legacy Windows-1254 (ISO-8859-9) sites, preventing garbled Turkish characters (`?`).
  - **Sequential Request Throttling:** Added polite 100 ms delays between section fetches in `CsRuntime` to avoid triggering Cloudflare HTTP 429 rate limits on plugins with dozens of categories.
- **Cloudflare Turnstile Interactive Verification (`CloudflareVerifyActivity`):** Added a user-facing full-screen interactive verification activity for sites protected by Cloudflare Turnstile ("Are you human?" checkbox) where background solvers cannot solve challenges automatically. When the home error card presents the "Verify" button, a clean, secure WebView opens. Tapping the Turnstile checkbox triggers automatic detection of `cf_clearance`, stores the cookies and the WebView's exact User-Agent into `CloudflareCache`, automatically dismisses the activity, and reloads the catalog. Overrides any hardcoded static desktop User-Agents in `NiceHttp` and `CloudflareKiller` with the solver's cached User-Agent to satisfy Cloudflare's cryptographic UA-clearance binding. Tested and verified with DiziBox.
- **Cloudflare Challenge & WebViewResolver Solver:** Integrated an off-screen hidden Android WebView solver (`CloudflareSolver`) and OkHttp `CloudflareKiller` (Interceptor) architecture to overcome Cloudflare bot protection (`HTTP 403`, `503`, `Cf-Mitigated: challenge`, Turnstile) on CloudStream plugin requests. Connected the `WebViewResolver` binary interface to perform real dynamic URL resolution. Added global challenge interception and single retry logic to `NiceHttp`. Provided strict per-domain cookie isolation (`CloudflareCache`). During verification, a "Verifying site…" status badge with shimmer animation is shown; when bot protection requires user interaction, a "Site verification required" card with "Verify" and "Try again" actions is displayed. Tested and confirmed on LDPlayer with HDFilmCehennemi, Dizilla, FilmMakinesi, SetFilm, FullHDFilmizlesene, YesilcamTV, and Cinemeta.
- **Provider catalog on home screen (Phase B):** Displays the active provider's native catalog directly on the home screen. Implemented full binary ABI compatibility for CloudStream plugins via `MainAPI.mainPage` and `getMainPage(page, request)` to fetch home sections with posters (tested with Dizilla, SetFilm, FilmMakinesi). Added support for Nuvio Stremio addons via manifest `catalogs` querying `{base}/catalog/{type}/{id}.json` (e.g. Cinemeta popular movies and series). For Nuvio JS scrapers that do not provide a catalog, an informative banner is displayed with seamless fallback to the TMDB feed. Includes a 5-minute thread-safe in-memory cache (`CatalogCache`), 10-second per-section timeouts with crash isolation, error states with "Try again" action, and shimmer skeletons during loading. When multiple providers are selected, their rows are concatenated in order with provider badges. Tapping a poster searches TMDB to navigate directly to the Detail screen (falling back to Search with title prefilled if not matched). Bypassed Cloudflare/CDN HTTP 403 image blocking by injecting a browser User-Agent in Coil's `SingletonImageLoader`.
- **Provider-centric home screen (Phase A):** added a floating pill button in the bottom right of the home screen displaying the active extension name (or "Select extension"). Tapping it opens the "Select an extension" modal sheet where CloudStream, Nuvio addons and Nuvio plugins can be searched, filtered by tab (All / CloudStream / Nuvio), and pinned to the top. A single tap selects a single provider; long pressing (0.5s) activates multi-select mode with checkmarks and an "Apply" button. The selection persists in DataStore. When a provider is selected, skeleton placeholders and a "Catalog coming soon" state are displayed on the home feed; "All providers" keeps the default TMDB feed unchanged. Full D-pad/remote support.
- **Fast start:** opening a movie or episode no longer waits for the source list; playback starts as soon as a suitable first source is found, with "Loading sources…" shown while the rest load in the background. The old behaviour (source list first) returns when Settings > Player > "Pick the source automatically" is off.
- **Dubbed / Subtitled tag:** every source is tagged. A Turkish dub is recognised from "TR Dublaj", "Türkçe Dublaj", "Dublajlı", "Dublaj", "TR Dub", "Turkish Dubbed", "Dual Audio TR" and "TR-EN" (case and Turkish characters don't matter); "dublajsız" ("no dub") doesn't count; anything unclear is "Subtitled".
- **Start order:** it waits at most 3 s for a Turkish dubbed source (Settings > Player > "Prefer Turkish dubbing", on by default). With no dub it starts subtitled and opens Turkish subtitles by itself (embedded Turkish → online Turkish → embedded English). If a dubbed source turns up later a corner card asks "Dubbed source found. Switch?"; switching continues from the same second. Subtitles or audio you chose by hand are never touched.
- **Smart ordering:** direct links come before torrents; sources the device can't decode (e.g. 4K) and very large files (~15 GB+) go last; your previous choice leads. If no first frame appears within 10 s (40 s for torrents) or an error occurs, the next source is tried; if none opens, "Pick another source" appears instead of a black screen. An embedded Turkish audio track is selected automatically ("Dubbed (embedded audio)").
- **Player UI:** the top bar shows source name · resolution · size (e.g. "Main Source 1 · 1920×872 · 22 GB") with the title underneath; centre −10 / play-pause / +10; seek bar at the bottom. Bottom buttons: Lock, Resize (fit / fill / stretch / 16:9 / 4:3; remembered), Subtitle sync, Source, Tracks.
- **Lock:** disables touches and gestures (volume/brightness included); only an unlock icon is visible.
- **Source panel:** sources grouped by addon (tag, resolution, size, direct/torrent, playing marker), sorting (Recommended / Size / Resolution) and a filter (All / Dubbed / Subtitled); the list fills in as sources load. On the right, subtitles: "No subtitles", embedded ones, "Load from file", "Search online", "Load first available". Nothing changes until you press **Apply**; **Cancel** drops everything.
- **Tracks panel:** video qualities (including "Auto") and audio tracks ("Turkish • Stereo • AAC").
- **Changing source** resumes from the same second; if the new source fails to open the previous one comes back with a short message. A source picked by hand is tried first in the same show's next episode; leaving a torrent closes its session.
- **File size:** size from the addon → torrent metadata → HTTP HEAD (`Content-Length`); it fails silently and never delays playback. If unknown nothing is shown (never "0 GB").
- **Online subtitles:** looked up by IMDb id (plus season/episode) from SubDL (when a key is set) and OpenSubtitles in one list; Turkish first, then English; each result shows language, source name and release name. .srt/.vtt/.ass are extracted from .zip files and the encoding (UTF-8 / Windows-1254) is detected. Results are cached per title. On a network error a short message appears and playback is unaffected; with no subtitles at all a prominent "Search online" row shows.
- Remote (TV): panels are navigated with the D-pad, OK selects, Back closes the panel.
- **SubDL Turkish results:** subtitles are searched one language at a time (Turkish first; English only if there is no Turkish). Asking for two languages in one request used to fill the 30-item page with English, often leaving a single Turkish result (Matrix: 1 → 14). The cache is per title/episode and language.
- **API key is never cached:** SubDL result addresses contain `api_key`; it is now stripped from the addresses (downloads are anonymous), never written to the search cache, and cleaned out of old cache files when they are read.
- **Subtitle auto-translation:** if no source has Turkish subtitles but English ones exist, the English is translated to Turkish on the device (Google ML Kit; no subtitle text leaves the device). English shows at once, the translation finishes in the background ("Translating subtitles… n%") and, unless you chose by hand, Turkish is selected automatically; the menu lists "Turkish (auto-translated)" as its own row and real Turkish always wins. Timings are kept, the translation is cached (the same film isn't translated again) and the sync setting works. The language model downloads once on first use (~44 MB, honours "Download on Wi-Fi only"). Settings > Player > "Auto-translate English if no Turkish" (on by default). The APK grows by about 7 MB per ABI.

## [1.0.4] — 2026-10-14

### Türkçe
- **Altyazı Başlıkları ve 403 Engellerinin Çözülmesi:** CloudStream ve Nuvio eklentilerinde altyazıların 403 Forbidden hatası almasını önlemek için akışın `Referer`, `User-Agent` ve `proxyHeaders` başlıkları altyazı indirme isteklerine iletildi (başlıklar loglanmaz). FilmMakinesi ("By Any Means" 1275 satır Türkçe altyazı) ve çevrimdışı altyazı indirme istekleri doğrulandı.
- **Başlık Temizleme (`TitleCleaner`):** Film ve dizi başlıklarındaki site ekleri ("izle", "Türkçe Dublaj izle", "Full HD izle", "(2026)" vb.) temizlendi, yıl bilgisi ayrıştırılarak ekranda ve aramalarda temiz başlık kullanımı sağlandı.
- **Çevrimiçi Kimlik Çözümleme Pipeline'ı (`MediaIdentityResolver`):** CloudStream içeriklerinde eksik olan IMDb/TMDB kimlikleri `syncData` -> TMDB başlık+yıl araması -> `external_ids` -> SubDL başlık araması sırasıyla çözülerek `PlaySession`'a ve EN->TR otomatik çeviriye bağlandı.
- **Oynatma ve Altyazı Tanısı Güçlendirmesi:** Tanı aracına altyazı doğrulama (eklenti ve internet altyazı sayıları/cues) ve Nuvio kaynakları teşhis desteği eklendi; 29 CloudStream eklentisi ve 9 Nuvio kaynağıyla tam tanı çalıştırıldı, `docs/oynatma-tanisi.md` güncellendi.
- **Sonraki bölüm otomatik açılır:** dizide bölüm bitmeye yaklaşınca (son ~20 saniye) köşede "Sonraki bölüm: S1 B4 · 10 sn" kartı çıkar; süre dolunca sonraki bölüm kendiliğinden başlar. Kartta "Şimdi oynat" ve "İptal" var; iptal edersen o bölümde kart bir daha çıkmaz. Filmlerde ve dizinin son bölümünde kart çıkmaz.
- Sonraki bölümün kaynağı kendiliğinden seçilir: önce izlediğin eklenti ve sunucuyla aynısı denenir, açılmazsa sıradaki kaynağa geçilir. Hiçbiri açılmazsa kart "Başka kaynak seç" der, ekran siyah kalmaz. Bölüm indirilmişse indirilmiş kopya oynar.
- Biten bölüm izlendi olarak kaydedilir; altyazı dili ve görünümü sonraki bölümde de geçerlidir (Türkçe otomatik aranır). Torrent kaynağında önceki torrent oturumu kapatılır.
- **Ayarlar > Oynatıcı:** "Sonraki bölümü otomatik oynat" anahtarı (varsayılan açık) ve geri sayım süresi (5 / 10 / 15 sn).
- Kumanda (TV): kart odaklanır; Tamam "Şimdi oynat", Geri "İptal".
- **Güncelleme indirmesi arka planda:** indirme artık bildirimde ilerlemeyle (İptal düğmeli) sürer, uygulamadan çıksan da kesilmez; bitince bildirime dokununca kurulum ekranı açılır. Ağ kesilirse ya da uygulama kapanırsa kaldığı yerden devam eder (sunucu desteklemezse baştan başlar); SHA-256 doğrulaması aynen sürer. İndirilemezse "Tekrar dene" var.
- Android 13+'ta bildirim izni istenir; reddedersen indirme uygulama açıkken yine çalışır.

### English
- **The next episode plays automatically:** near the end of an episode (last ~20 seconds) a "Next episode: S1 E4 · 10 s" card appears in the corner; when the countdown ends the next episode starts by itself. The card has "Play now" and "Cancel"; cancel it and it won't come back for that episode. No card for movies or a show's last episode.
- The next episode's source is picked for you: the addon and server you were watching are tried first, then the next source in line. If none opens, the card says "Pick another source" — the screen never stays black. If the episode is downloaded, the downloaded copy plays.
- The finished episode is marked as watched; your subtitle language and appearance carry over (Turkish is searched automatically). With a torrent source the previous torrent session is closed.
- **Settings > Player:** an "Autoplay next episode" switch (on by default) and the countdown length (5 / 10 / 15 s).
- Remote (TV): the card takes focus; OK is "Play now", Back is "Cancel".
- **Update downloads in the background:** the download now runs with a progress notification (with a Cancel button) and keeps going when you leave the app; tap the notification when it's done to open the installer. If the connection drops or the app closes it resumes where it stopped (or starts over if the server can't resume); SHA-256 verification is unchanged. If it fails there's a "Try again" button.
- On Android 13+ the notification permission is requested; if you decline, the download still works while the app is open.

## [1.0.3] — 2026-10-14

### Türkçe
- **Altyazı Başlıkları ve 403 Engellerinin Çözülmesi:** CloudStream ve Nuvio eklentilerinde altyazıların 403 Forbidden hatası almasını önlemek için akışın `Referer`, `User-Agent` ve `proxyHeaders` başlıkları altyazı indirme isteklerine iletildi (başlıklar loglanmaz). FilmMakinesi ("By Any Means" 1275 satır Türkçe altyazı) ve çevrimdışı altyazı indirme istekleri doğrulandı.
- **Başlık Temizleme (`TitleCleaner`):** Film ve dizi başlıklarındaki site ekleri ("izle", "Türkçe Dublaj izle", "Full HD izle", "(2026)" vb.) temizlendi, yıl bilgisi ayrıştırılarak ekranda ve aramalarda temiz başlık kullanımı sağlandı.
- **Çevrimiçi Kimlik Çözümleme Pipeline'ı (`MediaIdentityResolver`):** CloudStream içeriklerinde eksik olan IMDb/TMDB kimlikleri `syncData` -> TMDB başlık+yıl araması -> `external_ids` -> SubDL başlık araması sırasıyla çözülerek `PlaySession`'a ve EN->TR otomatik çeviriye bağlandı.
- **Oynatma ve Altyazı Tanısı Güçlendirmesi:** Tanı aracına altyazı doğrulama (eklenti ve internet altyazı sayıları/cues) ve Nuvio kaynakları teşhis desteği eklendi; 29 CloudStream eklentisi ve 9 Nuvio kaynağıyla tam tanı çalıştırıldı, `docs/oynatma-tanisi.md` güncellendi.
- **Uygulama içi güncelleme:** yeni sürüm çıkınca "Güncelle" APK'yı uygulama içinde indirir (ilerleme çubuğuyla), cihazın işlemci türüne uyan dosyayı kendisi seçer (arm64-v8a, armeabi-v7a, x86_64), release'teki SHA-256 dosyasıyla doğrular ve sistem kurulum ekranını açar. Artık hangi APK'yı indireceğini seçmen gerekmez.
- "Bilinmeyen uygulamaları yükle" izni yoksa ilgili ayar sayfasına yönlendirir; geri dönünce kurulum kaldığı yerden sürer.
- İndirme hatasında, ağ kesilmesinde ve özet uyuşmazlığında uygulama çökmez; dosya kurulmaz ve "Tarayıcıda aç" ile release sayfasına gidilebilir. Uygun APK bulunamazsa eski yöntem (tarayıcı) sürer.

### English
- **In-app updates:** when a new version is out, "Update" downloads the APK inside the app (with a progress bar), picks the file that matches your device's CPU type (arm64-v8a, armeabi-v7a, x86_64), verifies it against the release's SHA-256 file and opens the system installer. You no longer have to pick an APK.
- If the "Install unknown apps" permission is off, it sends you to the right settings page and carries on when you come back.
- A failed download, a dropped connection or a checksum mismatch never crashes the app; the file isn't installed and "Open in browser" takes you to the release page. If no matching APK is found, the old browser method is used.

## [1.0.2] — 2026-10-14

### Türkçe
- **Altyazı Başlıkları ve 403 Engellerinin Çözülmesi:** CloudStream ve Nuvio eklentilerinde altyazıların 403 Forbidden hatası almasını önlemek için akışın `Referer`, `User-Agent` ve `proxyHeaders` başlıkları altyazı indirme isteklerine iletildi (başlıklar loglanmaz). FilmMakinesi ("By Any Means" 1275 satır Türkçe altyazı) ve çevrimdışı altyazı indirme istekleri doğrulandı.
- **Başlık Temizleme (`TitleCleaner`):** Film ve dizi başlıklarındaki site ekleri ("izle", "Türkçe Dublaj izle", "Full HD izle", "(2026)" vb.) temizlendi, yıl bilgisi ayrıştırılarak ekranda ve aramalarda temiz başlık kullanımı sağlandı.
- **Çevrimiçi Kimlik Çözümleme Pipeline'ı (`MediaIdentityResolver`):** CloudStream içeriklerinde eksik olan IMDb/TMDB kimlikleri `syncData` -> TMDB başlık+yıl araması -> `external_ids` -> SubDL başlık araması sırasıyla çözülerek `PlaySession`'a ve EN->TR otomatik çeviriye bağlandı.
- **Oynatma ve Altyazı Tanısı Güçlendirmesi:** Tanı aracına altyazı doğrulama (eklenti ve internet altyazı sayıları/cues) ve Nuvio kaynakları teşhis desteği eklendi; 29 CloudStream eklentisi ve 9 Nuvio kaynağıyla tam tanı çalıştırıldı, `docs/oynatma-tanisi.md` güncellendi.
- **Yerleşik torrent/P2P motoru:** infoHash veya magnet döndüren addon ve Nuvio eklentilerinin kaynakları artık ek kurulum olmadan uygulama içinde oynatılıyor (libtorrent4j; yalnızca izlenen dosya indirilir, oynatıcı kapanınca geçici dosyalar silinir). İsteyen kendi TorrServer uyumlu sunucusunu Eklentiler ekranından girebilir.
- **Düzeltme:** Patron (PatronMulti) gibi eklentilerin bazı kaynaklarında, eklentinin gönderdiği başlıkları reddeden sunucular yüzünden çıkan "Bu kaynak şu an kullanılamıyor" hatası giderildi; reddedilen istek başlıksız yeniden denenir.
- **Çevrimiçi altyazı arama:** altyazı menüsünde "Çevrimiçi altyazılar" bölümü; Türkçe öncelikli (Ayarlar > Oynatıcı > Tercih edilen altyazı dili: Türkçe / İngilizce), ardından İngilizce. Altyazı addon'u kurmak gerekmez.
- **Dosyadan altyazı ekleme:** altyazı menüsünden cihazdaki .srt / .vtt / .ass dosyası seçilip hemen açılır; menüde ses parçası seçimi de var.
- **Oynatıcıda jestler (dokunmatik):** ekranın sağ yarısında dikey kaydırma ses, sol yarısında parlaklık (yalnızca oynatıcı penceresi; sistem ayarı değişmez, çıkışta eski haline döner). Kontrol çubukları ve TV/kumanda etkilenmez.
- Torrent kaynaklarında çevrimdışı indirme simgesi yoktur; önerilen kaynakta doğrudan bağlantı torrentten önce gelir.
- APK'lar işlemci türüne göre ayrı yayımlanır (arm64-v8a, armeabi-v7a, x86_64).

### English
- **Built-in torrent/P2P engine:** sources from addons and Nuvio plugins that return an infoHash or magnet link now play inside the app with no extra setup (libtorrent4j; only the watched file is downloaded, temporary files are deleted when the player closes). Users who run their own TorrServer-compatible server can enter it on the Addons screen.
- **Fix:** "This source isn't available right now" on some sources of plugins such as Patron (PatronMulti), caused by servers rejecting the headers the plugin sends; the rejected request is retried without them.
- **Online subtitle search:** an "Online subtitles" section in the subtitle menu, Turkish first (Settings > Player > Preferred subtitle language: Turkish / English), then English. No subtitle addon needed.
- **Add subtitles from a file:** pick a .srt / .vtt / .ass file from the device in the subtitle menu and it opens right away; the menu also lets you choose the audio track.
- **Player gestures (touch):** vertical swipe on the right half of the screen changes volume, on the left half brightness (player window only; the system setting is untouched and restored on exit). Control bars and TV/remote use are unaffected.
- Torrent sources have no offline download button; direct links are recommended before torrents.
- APKs are published per CPU type (arm64-v8a, armeabi-v7a, x86_64).

## [1.0.0] — 2026-10-13

İlk sürüm / First release.

### Türkçe
- **Altyazı Başlıkları ve 403 Engellerinin Çözülmesi:** CloudStream ve Nuvio eklentilerinde altyazıların 403 Forbidden hatası almasını önlemek için akışın `Referer`, `User-Agent` ve `proxyHeaders` başlıkları altyazı indirme isteklerine iletildi (başlıklar loglanmaz). FilmMakinesi ("By Any Means" 1275 satır Türkçe altyazı) ve çevrimdışı altyazı indirme istekleri doğrulandı.
- **Başlık Temizleme (`TitleCleaner`):** Film ve dizi başlıklarındaki site ekleri ("izle", "Türkçe Dublaj izle", "Full HD izle", "(2026)" vb.) temizlendi, yıl bilgisi ayrıştırılarak ekranda ve aramalarda temiz başlık kullanımı sağlandı.
- **Çevrimiçi Kimlik Çözümleme Pipeline'ı (`MediaIdentityResolver`):** CloudStream içeriklerinde eksik olan IMDb/TMDB kimlikleri `syncData` -> TMDB başlık+yıl araması -> `external_ids` -> SubDL başlık araması sırasıyla çözülerek `PlaySession`'a ve EN->TR otomatik çeviriye bağlandı.
- **Oynatma ve Altyazı Tanısı Güçlendirmesi:** Tanı aracına altyazı doğrulama (eklenti ve internet altyazı sayıları/cues) ve Nuvio kaynakları teşhis desteği eklendi; 29 CloudStream eklentisi ve 9 Nuvio kaynağıyla tam tanı çalıştırıldı, `docs/oynatma-tanisi.md` güncellendi.
- **Ana sayfa ve arama:** TMDB'den beslenen kapak karuseli, "devam et" sırası, trend/popüler sıraları ve "tümü" ekranları; film ve dizi arama.
- **Detay:** arka plan görseli, başlık logosu, puan ve yaş sınırı, oyuncular, sezon/bölüm seçici; Türkçe özet boşsa İngilizce yedek.
- **Kaynak seçimi:** eklentilerden paralel kaynak toplama, eklenti süzgeci, en uygun kaynak seçili; satırda eklenti adı, kalite, boyut, ses dili ve altyazı bilgisi.
- **Oynatıcı:** Media3 ile tam ekran oynatma (düz dosya, HLS, DASH), ileri-geri, hız, kaldığın yerden devam.
- **Kütüphane:** favoriler, izleme geçmişi ve indirilenler.
- **Ayarlar:** eklentiler, oynatıcı, tema, dil (Sistem / English / Türkçe), hakkında. Arayüz Türkçe ve İngilizce.
- **Web sitesi ekleme:** reklam ve açılır pencere engelli sade web görünümü.
- **TV, tablet ve kumanda desteği:** Android TV girişi ve afişi, kumanda odak halkası, geniş ekranda yan menü ve iki sütunlu Detay.
- **Nuvio eklenti sistemi:** manifest URL'li addon'lar ve JS kazıyıcı depoları; eklenti türünü adresin içeriğinden anlama, depo güncelleme denetimi, eklenti başına anahtar ve ayarlar.
- **QuickJS çalıştırıcı:** kazıyıcılar yalıtılmış QuickJS ortamında çalışır; ağ yalnızca genel adreslere, süre/bellek/istek sınırlı, dosya sistemi erişimi yok.
- **Altyazı:** altyazı addon'larında arama, tek birleşik seçici (dile göre gruplu), otomatik varsayılan dil, karakter kodlaması tespiti (windows-1254 dahil), elle senkron (−0,5 / −0,1 / +0,1 / +0,5 sn) ve görünüm ayarları.
- **Çevrimdışı indirme:** kaynak listesinden film/bölüm indirme, ilerleme bildirimi, "Sadece Wi-Fi'de indir", Kütüphane > İndirilenler, altyazılarıyla çevrimdışı oynatma ve aynı geçmişe yazma.
- Uygulama hiçbir içerik, addon veya eklenti içermez; hepsi kullanıcının eklediği adreslerden gelir.

### English
- **Home and search:** TMDB-powered cover carousel, "continue watching" row, trending/popular rows with "see all" screens; movie and series search.
- **Details:** backdrop, title logo, rating and age badge, cast, season/episode picker; English fallback when the Turkish overview is empty.
- **Source picker:** parallel source gathering from addons, addon filter, best source preselected; each row shows addon name, quality, size, audio language and subtitle info.
- **Player:** full-screen Media3 playback (progressive, HLS, DASH), seek, speed, resume where you left off.
- **Library:** favorites, watch history and downloads.
- **Settings:** addons, player, theme, language (System / English / Türkçe), about. UI in English and Turkish.
- **Add a website:** a simple web view with ad and pop-up blocking.
- **TV, tablet and remote support:** Android TV launcher entry and banner, remote focus ring, side navigation and two-column details on wide screens.
- **Nuvio addon system:** manifest-URL addons and JS scraper repositories; the type is detected from the URL's content, repository update checks, per-plugin toggles and settings.
- **QuickJS runtime:** scrapers run in a sandboxed QuickJS environment; network limited to public addresses, time/memory/request limits, no file-system access.
- **Subtitles:** subtitle addon search, a single merged picker (grouped by language), automatic default language, character-encoding detection (including windows-1254), manual sync (−0.5 / −0.1 / +0.1 / +0.5 s) and appearance settings.
- **Offline downloads:** download movies/episodes from the source list, progress notification, "Download on Wi-Fi only", Library > Downloads, offline playback with subtitles, writing to the same watch history.
- The app ships with no content, addons or plugins; everything comes from URLs you add.
