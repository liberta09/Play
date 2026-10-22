# Yanlış film sorunu — sonuç raporu

Dal: `fix-yanlis-film`. Test cihazı: LDPlayer (127.0.0.1:5555, 1600x900). Tarih: 2026-10-22.

## 1. Tekrar üretme (düzeltmeden ÖNCE, Play 1.1.5 yayın derlemesi)

Not (ortam): cihazdaki Play yayın anahtarıyla imzalı; debug APK üstüne kurulamıyor (`INSTALL_FAILED_UPDATE_INCOMPATIBLE`) ve `pm clear`/kaldırma yasak olduğundan, aynı kodun yerel yayın anahtarıyla imzalı `assembleRelease` derlemesi `adb install -r` ile kuruldu (veri korundu). Günlük etiketleri koddaki gerçek adlarla alındı: `PlayCs` (CsRuntime/CsStreamProvider), `PlayPlugin` (Nuvio JS eklentileri). `AddonStreamProvider` günlük yazmıyor. Adres/çerez/token günlüğe ve bu rapora **yazılmadı**: yalnızca sunucu adı.

### "İzle Lan" nedir, türü ne?
- **Tür: Nuvio JS eklentisi (Faz 2 "plugin deposu", 43 kazıyıcı)**; ayrıca "izlelan katalog" adlı bir Nuvio addon'u (yalnızca katalog) var. Ayarlar > Eklentiler > Yüklü eklentiler: "izlelan" (v1.14.623) "Nuvio" grubunda; kaynak listesindeki kazıyıcılar (Bogard, Dragon, Imu, Gorosei …) hep `cdn.izlelan.com/domains.json` alan adı yapılandırmasını çekiyor. CloudStream (.cs3) türünde "İzle Lan" yok (76 eklentinin hiçbiri).
- Kanıt: `once_eklentiler_listesi.png`.

### Üç film (Oynat'a basınca otomatik başlayan kaynak)
| Film | TMDB | Otomatik oynayan kaynak | Beklenen | Görülen |
|---|---|---|---|---|
| Yüzük Kardeşliği | 120 | **Bogard** (Nuvio JS, "Dublajlı", 1280x720) | filmin açılışı, süre ~3 sa | asansörde iki genç (başka yapım); süre çubuğu **33:25** (bir dizi bölümü) |
| İki Kule | 121 | **Bogard** | filmin açılışı | bardağa portakal suyu dökülen sahne (başka yapım) |
| Kralın Dönüşü | 122 | **Bogard** | filmin açılışı | loş bir oturma odası, iki kişi (başka yapım) |

Görüntüler (her biri bu adımda yeni alındı): `once_yuzuk_kardesligi_{a_arama,b_detay,c_ilk_kare,d_kaynak_paneli,e_sure_33_25}.png`, `once_iki_kule_{a_arama,b_detay,c_ilk_kare,d_oynatici_cubugu}.png`, `once_kralin_donusu_{a_arama,b_detay,c_ilk_kare,d_oynatici_cubugu}.png`. Kaynak paneli görüntülerinde "Çalıyor" Bogard'ın yanında; üst çubukta "Bogard · 1280x720 · Dublajlı (gömülü ses)".

### Eklentiye giden kimlik (günlükten)
- Nuvio JS eklentisi (Bogard/Dragon/…): `getStreams(tmdbId="120", mediaType="movie", season=null, episode=null)` — kimlik, tür doğru; belge §2.1 ile uyumlu. Eklentiye başlık/yıl gitmiyor (sözleşmede yok).
- Nuvio addon'u: `/stream/movie/{imdb}.json` (imdb TMDB `external_ids`'ten) — günlük yok, kod incelemesiyle doğrulandı.
- CloudStream: eklentiye yalnızca **Türkçe TMDB başlığı** gidiyor ("Yüzüklerin Efendisi: Yüzük Kardeşliği"); yıl, orijinal başlık, imdb/tmdb kimliği **gitmiyor** (`StreamRequest.year` hiç doldurulmuyor).

### Yüzük Kardeşliği (TMDB 120) — günlük özeti

Nuvio JS eklentisi (İzle Lan ailesi) günlüğü:
- [Bogard] Tarama Başlatıldı -> TMDB: 120, Tür: movie 
- [Dragon] Tarama Başlatıldı -> TMDB: 120, Tür: movie, Sezon: -, Bölüm: - 
- [Bogard] Oynatıcı sayfasına bağlanılıyor (yalnızca sunucu): https://streamcorecdn.com
- [Bogard] TAMAMLANDI: 1 akış hazırlandı (0.94s) [{"quality":"1080p • Dublaj / Altyazılı","title":"Dublaj / Altyazılı","format":"m3u8"}]
- [Gorosei] Tarama Başlatıldı -> TMDB: 120, Tür: movie, Sezon: undefined, Bölüm: undefined 
- [Imu] Tarama Başlatıldı -> TMDB: 120, Tür: movie, Sezon: 1, Bölüm: 1 

CloudStream eklentileri — arama sonuçlarının ilk 5 ismi ve eşleşme:

| Eklenti | Sorgu | Sonuç sayısı | İlk 5 isim | matchResult | Akış |
|---|---|---|---|---|---|
| DiziMom | Yüzüklerin Efendisi: Yüzük Kardeşliği | 0 |  | bulundu | 0 |
| AnimeciX | Yüzüklerin Efendisi: Yüzük Kardeşliği | 0 |  | yok | 0 |
| XPrime | Yüzüklerin Efendisi: Yüzük Kardeşliği | 1 | The Lord of the Rings: The Fellowship of the Ring | yok | 0 |
| DiziKorea | Yüzüklerin Efendisi: Yüzük Kardeşliği | 0 |  | yok | 0 |
| FullHDFilm | Yüzüklerin Efendisi: Yüzük Kardeşliği | 0 |  | yok | 0 |
| Watch2Movies | Yüzüklerin Efendisi: Yüzük Kardeşliği | 0 |  | yok | 0 |
| SelcukFlix | Yüzüklerin Efendisi: Yüzük Kardeşliği | 0 |  | yok | 0 |
| FilmMakinesi | Yüzüklerin Efendisi: Yüzük Kardeşliği | 0 |  | yok | 0 |
| HDFilmCehennemi | Yüzüklerin Efendisi: Yüzük Kardeşliği | 1 | Yüzüklerin Efendisi Yüzük Kardeşliği - The Lord of the Rings: The Fellowship of the Ring | bulundu | 0 |
| DDizi | Yüzüklerin Efendisi: Yüzük Kardeşliği | 0 |  | yok | 0 |
| JetFilmizle | Yüzüklerin Efendisi: Yüzük Kardeşliği | 0 |  | yok | 0 |
| Anizm | Yüzüklerin Efendisi: Yüzük Kardeşliği | 0 |  | yok | 0 |
| WebteIzle | Yüzüklerin Efendisi: Yüzük Kardeşliği | 1 | Yüzüklerin Efendisi: Yüzük Kardeşliği | bulundu | 2 |
| FilmModu | Yüzüklerin Efendisi: Yüzük Kardeşliği | 0 |  | yok | 0 |
| SetFilmIzle | Yüzüklerin Efendisi: Yüzük Kardeşliği | 0 |  | yok | 0 |
| YabanciDizi | Yüzüklerin Efendisi: Yüzük Kardeşliği | 4 | The Lord of the Rings: The Return of the King, The Lord of the Rings: The Two Towers, The Lord of the Rings: The Fellowship of the Ring, The Lord of the Rings: The War of the Rohirrim | yok | 0 |
| BelgeselX | Yüzüklerin Efendisi: Yüzük Kardeşliği | 0 |  | yok | 0 |
| SinemaCX | Yüzüklerin Efendisi: Yüzük Kardeşliği | 16 | Buddy, Moana, Hazine Avcısının Maceraları, Kötü Elmalar, Kayıp Balık Dori | yok | 0 |
| TavsiyeFilmIzle | Yüzüklerin Efendisi: Yüzük Kardeşliği | 0 |  | yok | 0 |
| DiziPal | Yüzüklerin Efendisi: Yüzük Kardeşliği | 0 |  | yok | 0 |
| HDFilmİzle | Yüzüklerin Efendisi: Yüzük Kardeşliği | 0 |  | yok | None |
| KultFilmler | Yüzüklerin Efendisi: Yüzük Kardeşliği | 1 | Yüzüklerin Efendisi: Yüzük Kardeşliği | bulundu | 1 |
| HintFilmİzle | Yüzüklerin Efendisi: Yüzük Kardeşliği | 25 | Jailer 2, Khalifa: The Ruler, Enna Vilai, Kattalan, Toxic: A Fairytale for Grown-Ups | yok | None |
| SezonlukDizi | Yüzüklerin Efendisi: Yüzük Kardeşliği | 0 |  | yok | 0 |
| DiziGom | Yüzüklerin Efendisi: Yüzük Kardeşliği | 9 | Yüzüklerin Efendisi: Yüzük Kardeşliği, Yüzüklerin Efendisi: İki Kule, Yüzüklerin Efendisi: Kralın Dönüşü, The Lord of the Rings: The Hunt for Gollum, Encore, encore... | bulundu | 2 |

### İki Kule (TMDB 121) — günlük özeti

Nuvio JS eklentisi (İzle Lan ailesi) günlüğü:
- [Bogard] Tarama Başlatıldı -> TMDB: 121, Tür: movie 
- [Dragon] Tarama Başlatıldı -> TMDB: 121, Tür: movie, Sezon: -, Bölüm: - 
- [Bogard] Oynatıcı sayfasına bağlanılıyor (yalnızca sunucu): https://streamcorecdn.com
- [Bogard] TAMAMLANDI: 1 akış hazırlandı (0.73s) [{"quality":"1080p • Dublaj / Altyazılı","title":"Dublaj / Altyazılı","format":"m3u8"}]
- [Gorosei] Tarama Başlatıldı -> TMDB: 121, Tür: movie, Sezon: undefined, Bölüm: undefined 
- [Imu] Tarama Başlatıldı -> TMDB: 121, Tür: movie, Sezon: 1, Bölüm: 1 

CloudStream eklentileri — arama sonuçlarının ilk 5 ismi ve eşleşme:

| Eklenti | Sorgu | Sonuç sayısı | İlk 5 isim | matchResult | Akış |
|---|---|---|---|---|---|
| DiziMom | Yüzüklerin Efendisi: İki Kule | 0 |  | yok | 0 |
| DiziPal | Yüzüklerin Efendisi: İki Kule | 0 |  | yok | 0 |
| Watch2Movies | Yüzüklerin Efendisi: İki Kule | 0 |  | yok | 0 |
| DiziKorea | Yüzüklerin Efendisi: İki Kule | 0 |  | yok | 0 |
| AnimeciX | Yüzüklerin Efendisi: İki Kule | 0 |  | yok | 0 |
| FilmMakinesi | Yüzüklerin Efendisi: İki Kule | 0 |  | yok | 0 |
| JetFilmizle | Yüzüklerin Efendisi: İki Kule | 0 |  | yok | 0 |
| DDizi | Yüzüklerin Efendisi: İki Kule | 0 |  | yok | 0 |
| SetFilmIzle | Yüzüklerin Efendisi: İki Kule | 0 |  | yok | 0 |
| BelgeselX | Yüzüklerin Efendisi: İki Kule | 0 |  | yok | 0 |
| KultFilmler | Yüzüklerin Efendisi: İki Kule | 1 | Yüzüklerin Efendisi: İki Kule | bulundu | 1 |
| SinemaCX | Yüzüklerin Efendisi: İki Kule | 16 | Buddy, Moana, Hazine Avcısının Maceraları, Kötü Elmalar, Kayıp Balık Dori | yok | 0 |
| XPrime | Yüzüklerin Efendisi: İki Kule | 1 | The Lord of the Rings: The Two Towers | yok | 0 |
| YabanciDizi | Yüzüklerin Efendisi: İki Kule | 4 | The Lord of the Rings: The Return of the King, The Lord of the Rings: The Two Towers, The Lord of the Rings: The Fellowship of the Ring, The Lord of the Rings: The War of the Rohirrim | yok | 0 |
| TavsiyeFilmIzle | Yüzüklerin Efendisi: İki Kule | 0 |  | yok | 0 |
| HDFilmCehennemi | Yüzüklerin Efendisi: İki Kule | 1 | Yüzüklerin Efendisi İki Kule - The Lord of the Rings: The Two Towers | bulundu | 0 |
| HDFilmİzle | Yüzüklerin Efendisi: İki Kule | 0 |  | yok | None |
| FullHDFilm | Yüzüklerin Efendisi: İki Kule | 0 |  | yok | 0 |
| DiziGom | Yüzüklerin Efendisi: İki Kule | 9 | Yüzüklerin Efendisi: İki Kule, Yüzüklerin Efendisi: Yüzük Kardeşliği, Yüzüklerin Efendisi: Kralın Dönüşü, The Lord of the Rings: The Hunt for Gollum, Encore, encore... | bulundu | 2 |
| Anizm | Yüzüklerin Efendisi: İki Kule | 0 |  | yok | 0 |
| SelcukFlix | Yüzüklerin Efendisi: İki Kule | 0 |  | yok | 0 |
| SezonlukDizi | Yüzüklerin Efendisi: İki Kule | 0 |  | yok | 0 |
| FilmModu | Yüzüklerin Efendisi: İki Kule | 0 |  | yok | 0 |
| WebteIzle | Yüzüklerin Efendisi: İki Kule | 1 | Yüzüklerin Efendisi: İki Kule | bulundu | 2 |
| HintFilmİzle | Yüzüklerin Efendisi: İki Kule | 25 | Jailer 2, Khalifa: The Ruler, Enna Vilai, Kattalan, Toxic: A Fairytale for Grown-Ups | yok | None |

### Kralın Dönüşü (TMDB 122) — günlük özeti

Nuvio JS eklentisi (İzle Lan ailesi) günlüğü:
- [Dragon] Tarama Başlatıldı -> TMDB: 122, Tür: movie, Sezon: -, Bölüm: - 
- [Bogard] Tarama Başlatıldı -> TMDB: 122, Tür: movie 
- [Bogard] Oynatıcı sayfasına bağlanılıyor (yalnızca sunucu): https://streamcorecdn.com
- [Bogard] TAMAMLANDI: 1 akış hazırlandı (1.06s) [{"quality":"1080p • Dublaj / Altyazılı","title":"Dublaj / Altyazılı","format":"m3u8"}]
- [Gorosei] Tarama Başlatıldı -> TMDB: 122, Tür: movie, Sezon: undefined, Bölüm: undefined 
- [Imu] Tarama Başlatıldı -> TMDB: 122, Tür: movie, Sezon: 1, Bölüm: 1 

CloudStream eklentileri — arama sonuçlarının ilk 5 ismi ve eşleşme:

| Eklenti | Sorgu | Sonuç sayısı | İlk 5 isim | matchResult | Akış |
|---|---|---|---|---|---|
| DiziPal | Yüzüklerin Efendisi: Kralın Dönüşü | 0 |  | yok | 0 |
| AnimeciX | Yüzüklerin Efendisi: Kralın Dönüşü | 0 |  | yok | 0 |
| Watch2Movies | Yüzüklerin Efendisi: Kralın Dönüşü | 0 |  | yok | 0 |
| DiziKorea | Yüzüklerin Efendisi: Kralın Dönüşü | 0 |  | yok | 0 |
| XPrime | Yüzüklerin Efendisi: Kralın Dönüşü | 2 | The Return of the King, The Lord of the Rings: The Return of the King | yok | 0 |
| JetFilmizle | Yüzüklerin Efendisi: Kralın Dönüşü | 0 |  | yok | 0 |
| SetFilmIzle | Yüzüklerin Efendisi: Kralın Dönüşü | 0 |  | yok | 0 |
| FilmMakinesi | Yüzüklerin Efendisi: Kralın Dönüşü | 0 |  | yok | 0 |
| YabanciDizi | Yüzüklerin Efendisi: Kralın Dönüşü | 4 | The Lord of the Rings: The Return of the King, The Lord of the Rings: The Two Towers, The Lord of the Rings: The Fellowship of the Ring, The Lord of the Rings: The War of the Rohirrim | yok | 0 |
| DDizi | Yüzüklerin Efendisi: Kralın Dönüşü | 0 |  | yok | 0 |
| FullHDFilm | Yüzüklerin Efendisi: Kralın Dönüşü | 0 |  | yok | 0 |
| Anizm | Yüzüklerin Efendisi: Kralın Dönüşü | 0 |  | yok | 0 |
| BelgeselX | Yüzüklerin Efendisi: Kralın Dönüşü | 0 |  | yok | 0 |
| HDFilmCehennemi | Yüzüklerin Efendisi: Kralın Dönüşü | 1 | Yüzüklerin Efendisi Kralın Dönüşü - The Lord of the Rings: The Return of the King | bulundu | 0 |
| FilmModu | Yüzüklerin Efendisi: Kralın Dönüşü | 0 |  | yok | 0 |
| HDFilmİzle | Yüzüklerin Efendisi: Kralın Dönüşü | 0 |  | yok | None |
| KultFilmler | Yüzüklerin Efendisi: Kralın Dönüşü | 1 | Yüzüklerin Efendisi: Kralın Dönüşü | bulundu | 2 |
| DiziGom | Yüzüklerin Efendisi: Kralın Dönüşü | 9 | Yüzüklerin Efendisi: Kralın Dönüşü, Yüzüklerin Efendisi: Yüzük Kardeşliği, Yüzüklerin Efendisi: İki Kule, The Lord of the Rings: The Hunt for Gollum, Encore, encore... | bulundu | 2 |
| WebteIzle | Yüzüklerin Efendisi: Kralın Dönüşü | 1 | Yüzüklerin Efendisi: Kralın Dönüşü | bulundu | 2 |
| SelcukFlix | Yüzüklerin Efendisi: Kralın Dönüşü | 0 |  | yok | 0 |
| DiziMom | Yüzüklerin Efendisi: Kralın Dönüşü | 1 | Yüzüklerin Efendisi: Kralın Dönüşü | bulundu | 0 |
| HintFilmİzle | Yüzüklerin Efendisi: Kralın Dönüşü | 25 | Jailer 2, Khalifa: The Ruler, Enna Vilai, Kattalan, Toxic: A Fairytale for Grown-Ups | yok | None |
| SezonlukDizi | Yüzüklerin Efendisi: Kralın Dönüşü | 0 |  | yok | 0 |
| TavsiyeFilmIzle | Yüzüklerin Efendisi: Kralın Dönüşü | 1 | Yüzüklerin Efendisi: Kralın Dönüşü Full Hd izle | bulundu | 0 |
| SinemaCX | Yüzüklerin Efendisi: Kralın Dönüşü | 16 | Buddy, Moana, Hazine Avcısının Maceraları, Kötü Elmalar, Kayıp Balık Dori | yok | 0 |


---

## 2. Kök neden (kanıtlı)

**Yanlış video Play'in çalıştırma ortamından değil, eklentinin/sitenin verisinden geliyor: Bogard (İzle Lan ailesi) `tmdbId` 120 için 33 dakikalık bir videonun listesini veriyor.** Bunu üç bağımsız yoldan doğruladım (adresler/belirteçler yazılmadı; yalnızca sunucu adı, durum, content-type, uzunluk ve SHA-256 özetleri):

| Karşılaştırma | Node (gerçek ağ, düz JS) | Play (QuickJS `PluginRuntime`, cihaz) |
|---|---|---|
| `cdn.izlelan.com` yapılandırma | 200, application/json, 2125 bayt | 200, application/json, 2125 bayt |
| Bogard oynatıcı sayfası (`streamcorecdn.com`) | 200, text/html, **238471** bayt | 200, text/html, **238471** bayt |
| Eklentinin döndürdüğü akış adresinin parametreleri | `id=120`, `m=#fc613b(len6)`, `token=#b706af(len22)` | `id=120`, `m=#fc613b(len6)`, `token=#b706af(len22)` — **özetler bire bir aynı** |
| Çalma listesinin süresi | **33,4 dk** (TMDB 121 için 31,2 dk) | ExoPlayer **33:25** |

- Aynı sayfa, aynı sorgu, aynı akış parametresi: yani `PluginRuntime` (fetch, çerez, başlık, yönlendirme, atob/btoa, URL) bu vakada Node'dan **farklı sonuç vermiyor**. Eklenti hata verip "varsayılan akış" döndürmüyor; sitenin kendi `film/120` kaydı bu ortamda bir dizi bölümüne (31–52 dk) çıkıyor ve eklenti içeriği doğrulamıyor.
- Aynı ailenin diğer kazıyıcıları (Dragon, Imu, Gorosei, Ace: Node'da; Enel: cihazda) 178 dk (kısa sürüm) ya da 228 dk (uzatılmış sürüm) süreli listeler veriyor: doğru film uzunluğu. Yalnızca Bogard yanlış.
- **Karşılaştırılamayan:** "Nuvio uygulamasında doğru açılıyor" iddiasını doğrulayamadım — cihazda Nuvio yok; NuvioMobile kaynağını da karşılaştırmadım. Olası açıklama (kanıtsız): Nuvio'da başka bir kaynak ilk sırada olabilir ya da Bogard o ortamda farklı bir yanıt alıyor olabilir. Bunu bilmediğimi yazıyorum.
- Eklentiye giden kimlik doğru: `getStreams("120","movie",null,null)`; `PluginCallRules` artık tutarsız kimlik/türü eklentiye hiç göndermiyor ve çağrıyı günlüğe yazıyor (`PlayPlugin: <ad>: istek tmdbId=… tür=… sezon=… bölüm=…`). Eklentiye dil parametresi gitmiyor (sözleşmede yok; TMDB çağrılarını eklenti kendisi yapıyor).

### Kullanıcının "Dragon da yanlış film veriyor" bildirimi
- Dragon'un tüm akışlarını cihazda tek tek denedim. **Yanlış film bulamadım.** Düzeltmeden önce Dragon akışlarının hepsi ExoPlayer'da `ERROR_CODE_PARSING_CONTAINER_UNSUPPORTED (3003)` ile açılmıyordu (`sonra_dragon_akis*_kare.png`: açılmayınca sıradaki kaynak Gaban'a geçiliyor). Sebep: eklentiler `"format":"m3u8"` bildiriyor, adres uzantısız (`…/play?id=…`) ve Play `format` alanını okumuyordu → tür çıkarılamadı. Bu Play'e ait gerçek bir Nuvio farkıydı (a603691). Düzeltmeden sonra Dragon akışları açılıyor ve doğru filmi gösteriyor (`final_dragon_akis1_kare.png` Bag End'de Gandalf+Frodo; `final_dragon_akis4_kare.png` Orthanc'ta Gandalf+Saruman); süre kontrolü 228,3 / 228,3 / 178,4 dk → Ok.
- Dragon'un 3. akışı bu denemede `ERROR_CODE_IO_UNSPECIFIED (2000)` verdi ve aynı eklentinin başka akışına geçildi (site/ağ kaynaklı, düzeltilmedi).

### İçerik güvencesi (afbec78) yanlış videoyu neden elemedi?
- Cihazdaki ilk APK (19:06, `main` 5fde44b) güvence öncesiydi: elemedi, çünkü kod yoktu.
- afbec78 sonrası derleme (19:45) cihazda **Bogard'ı eledi**: `süre kontrolü kaynak=Bogard süre=33.4 dk beklenen=228 dk tür=Movie -> Mismatch` (sonraki denemede 31,2/236 ve 51,7/263 dk, hep Mismatch). Beklenen süre TMDB'nin Türkçe kaydındaki (uzatılmış) süredir (228/236/263 dk); ExoPlayer süresi 33:25. Karar günlüğü yoktu, ben ekledim (`PlayMatch`).
- Ama Bogard'dan sonra seçilen kaynak (Dragon, 3003 hatası) **hata ekranı** gösterdi (`trace2_c_ilk_kare.png`): eski kaynaktan kalma "ilk kare" olayı 2 ms sonra yeni kaynağa yazılıyor, faz erken "Playing" oluyor, bu yüzden hata yedeğe düşmüyordu. Bu bir hataydı; 95d2bbc ile düzeltildi (olay `STALE_FRAME_GUARD_MS` süresince yok sayılır; birim testli) ve `ilk kare olayı yok sayıldı (eski kaynaktan kalma)` günlüğü cihazda görüldü.

## 3. Düzeltmeler (genel güvence)

1. **CloudStream eşleştirme** (`CsMatchRules`, Android'den bağımsız): yerel/özgün/alternatif başlıkla arama; yılı farklı aday asla (±1); alt başlıklı/numaralı aday yalnızca yıl birebirse; en yüksek puan; eşik altı yok; `load()` sonrası ad/yıl/kimlik doğrulaması; film isteğine çok bölümlü dizi, dizi isteğine tek parça film, başka sezonun bölümü verilmez. Cihaz kanıtı (Dune 2021): `Dune: Çöl Gezegeni – Bölüm Üç` (2026) ve `Dune` (1984) kaynakları `kaynak ATILDI … yıl farklı` ile atıldı.
2. **Süre güvencesi, tüm eklenti türleri** (`ContentDurationCheck`): film süresi TMDB süresinin %50'sinden kısa/%180'inden uzunsa, dizi bölümünde 3 kattan fazla uyumsuzlukta kaynak elenir; süre bilinmiyorsa (canlı, belirsiz) elenmez. Elenen kaynak listeye girmez (`StreamVerdicts`), sıradakine geçilir; hepsi elenirse "Bu içerik için kaynak bulunamadı".
3. **Sahiplik damgası** (`StreamOwnership`): birleşik sağlayıcıdan çıkan her kaynak içerik anahtarıyla damgalanır; başka içeriğin kaynağı (yedek listeye karışsa bile) oynatılmaz.
4. **Nuvio JS çağrı doğrulaması** (`PluginCallRules`) ve günlükler (`PlayPlugin`, `PlayPluginNet`, `PlayAddon`, `PlayMatch`): yalnızca sunucu adı, durum, content-type, uzunluk, karma.
5. **Yan hatalar** (kök nedenle birlikte bulundu): eklenti `format` alanı (3003), eski kaynağın altyazısı yeni kaynağa taşınıyordu, bayat ilk kare olayı.

## 4. Doğrulama (düzeltmeden SONRA; cihaz APK'sı = `a603691` yayın imzalı yerel `assembleRelease`)

| Film | Otomatik oynayan kaynak / süre kontrolü | İlk kare (yeni görüntü) | Doğru film mi? |
|---|---|---|---|
| Yüzük Kardeşliği | Ace, 228,2 dk / beklenen 228 → Ok (bir denemede Bogard 33,4 dk → Mismatch → elendi → Ace) | Frodo, Bag End, Türkçe altyazı | **EVET** (`sonra_yuzuk_kardesligi_c_ilk_kare.png`) |
| İki Kule | Bogard 31,2 dk / 236 → **Mismatch**, elendi; Ace 235,5 dk → Ok | Gandalf (Balrog sahnesi) | **EVET** (`sonra_iki_kule_c_ilk_kare.png`) |
| Kralın Dönüşü | Bogard 51,7 dk / 263 → **Mismatch**, elendi; Ace 263,2 dk → Ok | Rohan salonunda Merry ve Pippin masada | **EVET** (`sonra_kralin_donusu_c_ilk_kare.png`) |
| Dune (2021) | Ace, 149,2 dk / 155 → Ok; eşleştirmede 1984 ve "Bölüm Üç" (2026) atıldı | ilk kare siyah (film siyahla açılıyor); 2:04'te kum rengi sahne; sonra Paul masada (`sonra_dune_h_video_kare_2.png`) | **EVET** (süre 2:29:09; ilk kare tek başına kanıt değildi, ilerletip baktım) |
| Joker (2019) | Enel, 121,8 dk / 122 → Ok; bazı kaynaklar 3003/500 verdi, yedeğe düşüldü | Arthur, soyunma odasında makyaj aynasında | **EVET** (`sonra_joker_c_ilk_kare.png`) |

Başka film oynayan yok. Uyarılar: (a) her ortamda Bogard her seferinde 33–52 dk'lık yanlış video üretiyor ve her seferinde elendi; kullanıcıya gösterilmeden önce ~2 sn ilk kare çiziliyor (süre, oynatıcı hazır olunca öğrenilir). (b) Eleme, süresi bilinmeyen kaynağı elemez; süresi doğru ama başka içerik olan bir kaynak bu güvenceyle yakalanamaz (eşleştirme/kimlik doğrulaması ayrı katman).

## 5. Testler ve commit'ler
- `.\gradlew testDebugUnitTest`: **852 test, 0 hata, 2 atlandı** (yeni: `ContentMatchTest`, `ContentDurationCheckTest`, `PluginCallRulesTest`, `PluginStreamFormatTest`, `SourceCoordinatorContentGuardTest`, TMDB başlık testleri).
- Commit'ler (dal `fix-yanlis-film`): 48889c2 (taban rapor), dfdb93e (kurallar), 7a0953b (CloudStream), afbec78 (süre güvencesi), 95d2bbc (bayat ilk kare + günlükler), ceb7bc9 (altyazı), 72fbf7c (başlık parçaları), a603691 (`format`); bu belge ayrı commit.
- Ortam notu: ağ/WebView çözücüleri bu LDPlayer'da bazen çöküyor ("No space left on device" cihaz günlüğünde); çok eklentili aramalar yavaşlayabiliyor.
- Geçici JS dökümü yaması commit'e girmedi; eklenti kodu yalnızca yerel çözümleme için çekildi (depo adresi yazılmadı).
