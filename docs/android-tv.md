# Play TV (Android TV çeşidi) / Play TV (Android TV flavor)

## Türkçe

Play TV, Play'in Android TV için ayrı arayüzlü sürümüdür. Telefon uygulamasıyla **aynı kodu** kullanır (eklentiler,
CloudStream/Nuvio kaynak toplama, oynatıcı denetleyicisi, altyazı, profiller, Wi-Fi eşitlemesi, bulut hesabı); yalnız
ekranlar kumandaya göre yeniden çizilmiştir (Compose for TV).

### Yapı
- `:app` modülünde iki ürün çeşidi (product flavor, boyut `target`):
  - `phone`: bugünkü Play uygulaması, değişmeden (paket `com.kaan.play`, tek evrensel APK).
  - `tv`: Play TV (paket `com.kaan.play.tv`, `applicationIdSuffix ".tv"`). İkisi aynı cihaza yan yana kurulabilir.
- TV'ye özgü her şey `app/src/tv/` altında: `AndroidManifest.xml` (uygulama sınıfı `TvPlayApp`, TV ekranları,
  `LEANBACK_LAUNCHER`; telefon arayüzü `MainActivity` başlatıcıdan çıkar), `java/com/kaan/play/tv/*.kt`, `res/`.
- Simge ve TV afişi Play'inkidir (`app/src/main/res`); vurgu rengi Play'in camgöbeği `#00E5FF`, koyu zemin `#07111A`.
- TV bağımlılığı yalnız `tvImplementation(libs.androidx.tv.material)`.

### Ekranlar
Sol menü: Ara, Ana sayfa, Canlı TV, Filmler, Diziler, Kütüphane, Ayarlar.
- **Ana sayfa / Filmler / Diziler:** üstte odaktaki içeriğin büyük tanıtımı ve (odakta 3 sn kalınca) sesli önizleme;
  altında "İzlemeye devam et", addon katalogları, sabitlenen CloudStream eklentilerinin satırları ve TMDB satırları
  (Play ana sayfasının sırasıyla). "Eklenti seç" Play'in sabitleme (iğne) ayarını kullanır. "Tümünü gör" ızgarası.
- **Detay:** görsel, bilgi, özet, Oynat/Devam et, sezon ve bölüm satırı, oyuncular, benzerler, fragman, favori.
- **Oynatıcı:** Play'in `PlaybackController`'ı; kumanda eşlemesi, kaynak/altyazı/ses panelleri, sonraki bölüm, atlama
  düğmeleri, canlı yayında kanal listesi. Bot doğrulaması isteyen sitede Play'in doğrulama ekranı açılır.
- **Ara:** ekran klavyesi, son aramalar, sesli arama (tanıyıcı varsa); TMDB ve açık CloudStream eklentileri.
- **Kütüphane:** devam et, geçmiş, favoriler (Play'in favorileri).
- **Ayarlar:** Telefondan aktar (Wi-Fi), Eklentiler (depo/eklenti ekleme, kurulu depolar, eklenti ayarları, depo kataloğu,
  ana sayfa katalogları), Oynatıcı, Profiller ve hesap (profiller, PIN, Play'in bulut hesabı), Genel (dil, tema, hız
  testi, poster adres şablonu).

### Telefonla bağlantı
- **Eşleşme:** TV'de Ayarlar → Telefondan aktar → "Kodu TV'de göster"; telefonda Play → Ayarlar → Wi-Fi senkronizasyonu
  kartına kod yazılır (ya da tersi). İlk kodlu eşitlemeden sonra cihazlar **kalıcı olarak eşleşir** (ECDH ile türetilen
  eşleşme sırrı; `LocalSyncPairing`, `LocalSyncPairStore`). Eşleşmiş cihazlar uygulama açıkken kod girmeden eşitlenir
  (açılış, ön plana dönüş; en sık 30 sn'de bir). Eşleşmiş cihazlar arasında eksik kaynaklar onay sorulmadan kurulur.
  Eşleşme Wi-Fi kartından (telefon) ya da Telefondan aktar sayfasından (TV) kaldırılır.
- **Telefondan TV'de oynat:** telefonda detay ekranının sağ üstündeki TV düğmesi (`TvCastButton`) açıkken Oynat, içeriği
  eşleşmiş TV'de açar. TV'de içeriğin eklentisi yoksa telefon kaynağı kendisi bulur ve doğrudan akış gönderir; TV'de
  eklenti kapalıysa yalnız o izleme için açılır.
- **Kumanda:** eşleşmiş cihaz varken telefonun üst menüsünde kumanda simgesi çıkar (`ui/remote/RemoteScreen.kt`): yön
  halkası, Tamam/Geri/Ana sayfa, oynat/duraklat, ±10 sn, ses, liste ve TV'ye yazı gönderme; üstte TV'de oynayanın kartı.
- **Güvenlik:** Wi-Fi eşitlemesinin bütün kuralları geçerli (yalnız özel IPv4, HMAC kanıtı, AES-GCM gövde, deneme
  sınırı). Oynatma ve kumanda uçları (`/play`, `/remote`) yalnız eşleşmiş cihazdan gelen isteği kabul eder; tek
  kullanımlık kodla gelen istek reddedilir. Eşli sunucu yalnız uygulama ön plandayken açıktır.

### Bulut hesabı
TV, Play'in kendi bulut eşitlemesini (`SupabaseCloudSync`) kullanır: Ayarlar → Profiller ve hesap → Bulut hesabı'nda
e-posta + parolayla giriş/kayıt, "Şimdi eşitle", çıkış ve buluttan gelen kaynakların onayı. Oturum telefondan TV'ye
aktarılmaz; TV'de ayrıca giriş yapılır. Sunucu bilgisi derlemede yoksa panel bilgi notu gösterir.

### Derleme
```powershell
.\gradlew assembleTvDebug            # app\build\outputs\apk\tv\debug\app-tv-debug.apk
.\gradlew assemblePhoneDebug         # telefon
.\gradlew testPhoneDebugUnitTest testTvDebugUnitTest
.\gradlew assembleTvRelease          # PlayTV-<sürüm>.apk + PlayTV-<sürüm>-SHA256.txt
```

### Bilinen sınırlar
- TV çeşidinde uygulama içi güncelleme ve duyuru penceresi yok (yukarı akışın güncelleyicisi telefon APK'sını,
  `Play-<sürüm>.apk`, indirir); TV APK'sı ayrıca dağıtılmalıdır.
- `CLAUDE.md` "Kapsam dışı" maddesinde "ayrı bir Android TV arayüzü" yer alıyordu; bu çeşit o kararın güncellenmesini
  gerektirir.

## English

Play TV is Play's Android TV flavor with its own remote-first UI. It shares **all** of the phone app's layers (add-ons,
CloudStream/Nuvio source collection, playback controller, subtitles, profiles, Wi-Fi sync, cloud account); only the
screens are TV-specific (Compose for TV).

- Product flavors in `:app` (dimension `target`): `phone` = today's app, unchanged (`com.kaan.play`, single universal APK);
  `tv` = Play TV (`com.kaan.play.tv`). Both can be installed side by side. TV sources live in `app/src/tv/`.
- Screens: Search, Home, Live TV, Movies, Series, Library, Settings (Wi-Fi transfer, add-ons, player, profiles & account,
  general). Play's icon/banner and cyan accent.
- Phone link: after one code-based Wi-Fi sync the devices stay **paired** and sync automatically while the app is open;
  "Play on TV" on the phone's detail screen opens the title on the paired TV (direct streams are sent when the TV lacks
  the add-on); the phone gets a remote-control screen. Play/remote endpoints accept paired devices only.
- Cloud: the TV uses Play's own cloud sync (email sign-in on the TV); sessions are not transferred from the phone.
- Build: `assembleTvDebug`, `assemblePhoneDebug`, `testPhoneDebugUnitTest testTvDebugUnitTest`, `assembleTvRelease`.
- Limits: no in-app updater/announcements on TV; the "separate Android TV UI" out-of-scope rule in `CLAUDE.md` needs an
  update if this flavor is accepted.
