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
Sol menü: Ara, Ana sayfa, Canlı TV, Filmler, Diziler, Shorts Drama (aşağıya bakın), Kütüphane, Ayarlar.
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
- **Eşleşme (isteğe bağlı, varsayılan kapalı):** "Eşleşmiş cihazları hatırla" ayarı iki cihazda da açılır (telefonda
  Wi-Fi senkronizasyonu kartında, TV'de Ayarlar → Telefondan aktar sayfasında). TV'de "Kodu TV'de göster"; telefonda
  kod yazılıp Bağlan (ya da tersi). Kodlu eşitlemeden sonra kodu yazan cihaz karşının adını gösterip "Eşleş" diye sorar;
  kodu gösteren cihaz da gelen isteği "İzin ver / Reddet" ile onaylar. İki onay olmadan eşleşme kullanılmaz (sunucu
  onay bekleyen eşleşmeye 403 verir). Eşleşme sırrı ECDH + HKDF ile türetilir (`LocalSyncPairing`, `LocalSyncPairStore`).
  Eşleşmiş cihazlar uygulama açıkken kod girmeden eşitlenir (açılış, ön plana dönüş; en sık 30 sn'de bir). Eksik
  kaynaklar her zaman kullanıcı onayıyla kurulur ("Şunlar eklensin mi?"). Eşleşme tek tek kaldırılabilir; ayarı kapatmak
  bütün eşleşmeleri siler.
- **Telefondan TV'de oynat:** telefonda detay ekranının sağ üstündeki TV düğmesi (`TvCastButton`) açıkken Oynat, içeriği
  eşleşmiş TV'de açar. TV'de içeriğin eklentisi yoksa telefon kaynağı kendisi bulur ve doğrudan akış gönderir; TV'de
  eklenti kapalıysa yalnız o izleme için açılır.
- **Kumanda:** eşleşmiş cihaz varken telefonun üst menüsünde kumanda simgesi çıkar (`ui/remote/RemoteScreen.kt`): yön
  halkası, Tamam/Geri/Ana sayfa, oynat/duraklat, ±10 sn, ses, liste ve TV'ye yazı gönderme; üstte TV'de oynayanın kartı.
- **Güvenlik:** Wi-Fi eşitlemesinin bütün kuralları geçerli (yalnız özel IPv4, HMAC kanıtı, AES-GCM gövde, deneme
  sınırı). Oynatma ve kumanda uçları (`/play`, `/remote`) yalnız onaylanmış eşleşmeden gelen isteği kabul eder; tek
  kullanımlık kodla gelen istek reddedilir. Telefondan gelen doğrudan akışlar TV'de yeniden denetlenir (yalnız http/https,
  yerel/özel/loopback/link-local/CGNAT adres yok, sayısal adres biçimleri sıkı denetlenir, başlık izin listesi;
  `RemotePlayStreams.acceptIncoming`). Sınır: DNS ile çözülen adlar bağlantı anında yeniden denetlenmez (akış yalnız
  kullanıcının onayladığı eşli cihazdan gelir). Karşıdan gelen
  eşitleme yerel profil PIN'ini kaldıramaz ya da değiştiremez. Eşli sunucu yalnız uygulama ön plandayken açıktır.
  Not: 6 haneli kod parola tabanlı bir anlaşma (PAKE) değildir; eşleşme aynı ağdaki sahte bir cihaza karşı iki taraftaki
  kullanıcı onayıyla korunur. Yalnız kendi cihazlarınızla eşleşin.

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

## Shorts Drama (Arcana Drama TV'den; kaldırılabilir) / Shorts Drama (from Arcana Drama TV; removable)

**TR:** Shorts Drama menüsü Arcana Drama TV uygulamasında eklenmiştir; kısa dizileri Arcana Drama eklenti deposundan
gösterir (`https://raw.githubusercontent.com/banadabi/arcanadrama-addons/main/repo.json`). Depo kendiliğinden eklenmez:
kullanıcı Shorts Drama'yı açtığında bir kez onay penceresi çıkar ("Shorts Drama, kısa dizileri Arcana Drama eklenti
deposundan gösterir. Bu depo eklensin mi?", deponun host + yolu görünür, **Ekle / Vazgeç**, kumandayla kullanılır).
Vazgeç'te bölümde "Depo eklenmedi" ve **Tekrar sor** düğmesi kalır, pencere bir daha kendiliğinden açılmaz. Ana sayfa,
Filmler ve Diziler depoyu hiç eklemez. Yine de Play'in "varsayılan depo gömülmez" kuralıyla çelişir; istemezseniz şu
dosyaları/satırları kaldırarak tamamen çıkarabilirsiniz (ya da yalnız bu parçayı getiren commit'i geri alın:
`feat(tv): Shorts Drama section`):

- `app/src/tv/java/com/kaan/play/tv/ShortsDrama.kt` (depo adresi, depo tanıma, eklenti listesi, onaylı depo ekleme, onay
  penceresi) ve `app/src/testTv/java/com/kaan/play/tv/ShortDramaRecordTest.kt`;
- `TvApp.kt`: `Section.Shorts`, `shorts` modeli, `Section.Shorts ->` dalları ve `shorts.load()`;
- `TvHomeModel.kt`: `HomeKind.Shorts`, `HomeState.ShortsRepoMissing`, `addShortsRepo`, `declineShortsRepo`,
  `askShortsRepoAgain`, `shortsProviders`, `shortsRepos`, `KEY_SHORTS_REPO_DECLINED` ve "Shorts Drama" yorumlu satırlar;
- `TvHomeScreen.kt`: "Shorts Drama" yorumlu satırlar (onay dalı, satır başlığına eklenti adı, "İzlemeye devam et" süzgeci);
- `TvHomeCache.kt`: `HomeKind.Shorts` satırı;
- `res/values*/strings.xml` ve `strings_home.xml`: `tv_section_shorts`, `tv_home_installing_shorts`,
  `tv_home_shorts_install_failed`, `tv_shorts_repo_*`.

Davranış: depo eklendikten sonra "Shorts Drama" bölümü Arcana Drama depolarındaki (kök depo ve
`https://arcanadrama.download/ArcanaDrama/<ad>-repo.json` alt depoları) bütün açık eklentilerin satırlarını, eklenti adıyla
başlayan başlıklarla gösterir; bu eklentilerin "İzlemeye devam et" kayıtları yalnız burada görünür, Ana sayfada ve Ana
sayfa eklenti seçicisinde bu eklentiler yer almaz. Kullanıcı depoyu silebilir; silinince bölüm yeniden sorar.

**EN:** The Shorts Drama menu was added in the Arcana Drama TV app; it shows short dramas from the Arcana Drama add-on
repository. The repository is never added silently: opening Shorts Drama shows a one-time confirmation dialog (repository
host + path visible, **Add / Cancel**, remote-friendly); after Cancel the section shows "Repository not added" with an
**Ask again** button. Home, Movies and Series never add it. It still conflicts with Play's "no default repository is
bundled" rule; if you don't want it, remove it completely by deleting the files/lines listed above (or revert the single
commit `feat(tv): Shorts Drama section`).

## English

Play TV is Play's Android TV flavor with its own remote-first UI. It shares **all** of the phone app's layers (add-ons,
CloudStream/Nuvio source collection, playback controller, subtitles, profiles, Wi-Fi sync, cloud account); only the
screens are TV-specific (Compose for TV).

- Product flavors in `:app` (dimension `target`): `phone` = today's app, unchanged (`com.kaan.play`, single universal APK);
  `tv` = Play TV (`com.kaan.play.tv`). Both can be installed side by side. TV sources live in `app/src/tv/`.
- Screens: Search, Home, Live TV, Movies, Series, Shorts Drama (see above), Library, Settings (Wi-Fi transfer, add-ons, player, profiles & account,
  general). Play's icon/banner and cyan accent.
- Phone link (opt-in, off by default, "Remember paired devices" on both devices): after a code-based Wi-Fi sync both
  users confirm the pairing (device name shown); paired devices then sync automatically while the app is open; missing
  sources still need confirmation; incoming direct streams are re-checked on the TV; a remote sync cannot remove or change
  a local profile PIN;
  "Play on TV" on the phone's detail screen opens the title on the paired TV (direct streams are sent when the TV lacks
  the add-on); the phone gets a remote-control screen. Play/remote endpoints accept paired devices only.
- Cloud: the TV uses Play's own cloud sync (email sign-in on the TV); sessions are not transferred from the phone.
- Build: `assembleTvDebug`, `assemblePhoneDebug`, `testPhoneDebugUnitTest testTvDebugUnitTest`, `assembleTvRelease`.
- Limits: no in-app updater/announcements on TV; the "separate Android TV UI" out-of-scope rule in `CLAUDE.md` needs an
  update if this flavor is accepted.
