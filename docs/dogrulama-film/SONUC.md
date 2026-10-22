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
