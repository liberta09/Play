# Play — Yol haritası ve çalışma sistemi

> Bu dosya Play'in **uzun vadeli planının tek doğru kaynağıdır.** Her görev (Claude, Antigravity, masaüstü Claude) başlamadan önce bunu okur. Bir karar değişirse aynı işte bu dosya güncellenir.
> Son güncelleme: 2026-10-21. Durum: **plan aşaması, aciliyet yok.** Kod yazılmadan önce ilgili aşamanın şartnamesi yazılır ve kullanıcı onaylar.

## 1. Hedef
- Play'in pürüzsüz, kusursuza yakın çalışması. Hiçbir uygulama tamamen hatasız olmaz (eklentiler üçüncü taraf sitelere bağlı); hedef **hatalar kullanıcıya ulaşmadan yakalansın, ulaşırsa hemen görülüp çabuk düzeltilsin.**
- Play'den ek gelir (geliştirmenin kullanıcıyı finansal olarak zorlamaması).
- Acele yok: bir özellik bir ya da iki gün sürebilir. Önce altyapı, sonra özellik.

## 2. Çalışma sistemi (her özellik bu 7 adımdan geçer)
1. **Konuşma** (kullanıcı + Claude): ne, neden, "bu olmadan uygulama çalışır mı?"
2. **Şartname** (Claude yazar): ekranlar, düğmeler, her durum (internet yok, eklenti cevap vermiyor, TV kumandası, uzun Türkçe metin, iki dil) ve **kabul maddeleri** ("şu yapılınca şu görünmeli", tek tek kontrol edilebilir).
3. **Onay** (kullanıcı): onay olmadan yapım başlamaz.
4. **Yapım** (Antigravity ya da masaüstü Claude): küçük parçalar, ≤45 dk, tahmin yok, şartnameye göre. Tek kopyalanabilir blok prompt + model önerisi.
5. **Doğrulama** (Claude): GitHub'daki kodu, testleri ve duman testini inceler; kabul maddelerini tek tek işaretler. Bir madde tutmazsa geri döner.
6. **Beta:** Telegram'dan 3–5 gönüllü dener. Kullanıcı tester olmaz.
7. **Yayın ve duyuru:** yalnızca betada sorun yoksa ve kullanıcı açıkça onaylarsa. Release sonrası README "APK İNDİR" bağlantısı güncellenir.

Değişmez kurallar: main'e birleştirme ve release yalnızca kullanıcı onayıyla; force push yok; silme komutları yok; `local.properties`, keystore, API anahtarları asla commit/log; URL/çerez/token loglanmaz; video kaynağı/eklentisi gömülmez (istisnalar: 2 altyazı addon'u + kullanıcının kendi katalog addon'u); Google Play'e konmaz.

## 3. Aşamalar (önerilen sıra)

### Aşama 0 — Temel sağlamlaştırma (yeni özellikten önce)
- Otomatik hata raporu (kullanıcı izniyle; adres ve kişisel veri yok).
- "Sorun bildir" düğmesi (teşhis bilgisi Telegram/e-posta).
- GitHub'da her gönderimde otomatik derleme + test (bozuk kod ana dala girmesin).
- Beta kanalı (Telegram gönüllüleri; ileride destekçilere "erken erişim").
- **Web görünümü TV/kumanda desteği (kullanıcı listeye aldı, 2026-10-21):** TV'de normal kumandayla web görünümünde gezinilemiyor (telefonda dokunmatikle sorun yok). Hedef: yalnızca TV/kumanda için sanal imleç (ok tuşları hareket, Tamam tıklama, kenarda kaydırma), arama kutusunda ekran klavyesi, sitedeki tam ekran video (kumandayla durdur/sar), Geri tuşu sırası (tam ekran → sayfa geri → kapat), üst çubuğa erişim. Telefondaki davranış değişmez. Her sitede kusursuzluk vaat edilmez. Şartname yazılıp kullanıcı onaylayınca başlanır.
- **Oynatma takılması araştırması (kullanıcı listeye aldı, 2026-10-21):** TV kutusunda bazı kaynaklar (örn. bir dizi bölümü) Play'de ve alternatif oynatıcılarda takılıyor/duruyor, aynı içerik web sitesinin kendi oynatıcısında sorunsuz. Önce teşhis: hangi kaynak, kalite, boyut, doğrudan mı torrent mi, Wi-Fi mi kablo mu. Sonuca göre karar verilir.
- Açık hatalar: (a) TV kutusunda "Eklenti API'si bulunamadı" (CsDexLoader.loadApis boş; hata metni koda Türkçe gömülü, string resource olmalı); (b) arkadaşın sağlayıcı seçimi sorunu, 1.1.4'te yeniden denenecek; (c) bir sonraki duyuruya Telegram adresi: https://t.me/+o-RFlV4U3UY5NGU8

### Aşama 1 — Yeni bölüm bilgisi (ücretsiz)
- "İzlemeye devam et" kartında turkuaz **"Yeni bölüm"** rozeti; karta basınca o bölüm oynar.
- Detay ekranında "Son bölüm: S3 B5 · 2 gün önce" ve "Sonraki bölüm: 28 Ekim".
- Veri: TMDB (günde bir kontrol); yalnızca CloudStream'den açılan dizilerde eklentinin bölüm listesi (en iyi çaba, `CsNextEpisodeFinder`).
- Dikkat: bölüm TMDB'de yayınlansa da altyazılı/dublajlı kaynaklar günler sonra düşebilir; rozet birkaç saat gecikmeli gösterilebilir.
- **Karar (2026-10-21):** hem rozet hem ayrı **"Yeni bölümler" rafı** olacak. Raf yalnızca en az bir yeni bölüm varsa görünür (boş raf gösterilmez); şartnamede raf konumu ve sıralama netleştirilir.

### Aşama 2 — Gelir altyapısı
- "Geliştiriciyi destekle" düğmesi (Patreon / Shopier / Papara; **kullanıcı araştırıyor, acil değil**, şartname yazılmadan önce seçilir).
- **Destekçi kodu:** kod = ad + bitiş tarihi, geliştiricideki gizli anahtarla imzalı; uygulama yalnızca açık anahtarla cihazda doğrular (sunucu yok). Girişi Ayarlar > Hakkında içinde (Ayarlar'a yeni ana kalem yok).
- Destekçi özellikleri herkese **görünür ama kilitli** ("Destekçilere özel" + Destekle düğmesi). Süre bitince sessizce kilitlenir.
- Destekçilere özel: yeni bölüm **bildirimi** ve **"Bu hafta çıkacaklar" takvimi** (günlük arka plan kontrolü, cihazda), özel temalar/ikonlar, "Destekçi" rozeti, erken erişim, özel Telegram kanalı.
- Dürüst sınır: GPL-3.0 ve açık kod nedeniyle uygulama içi kilit kırılabilir; bu aşama için yeterli kabul edildi. Gerçek koruma yalnızca sunucu tarafı hizmetle mümkün (Aşama 4).
- Ücretli modele geçmeden önce **avukat görüşü** alınması önerildi (içerik değil yazılım/hizmet için ücret; telif riski).

### Aşama 3 — Bağlantılar (Trakt, Simkl)
- Trakt ve Simkl (ücretsiz kalır).
- **Watch List ayrı kalır:** kullanıcı kararı (2026-10-21), Watch List ile Play karıştırılmaz; kendi başına bir uygulama olarak ilerler. İleride ihtiyaç doğarsa ayrıca konuşulur.
- Misafir modu: giriş yapmadan her şey bugünkü gibi çalışır.
- Not: CLAUDE.md "Kapsam dışı" listesinde "tracker" ve "profil/kilit" var; bu aşamaya başlarken kural güncellenir.

### Aşama 4 — Hesap ve bulut (kullanıcı sayısı artınca)
- Kullanıcı adı + şifre girişi, bulut eşitleme (eklentiler, ayarlar, geçmiş), çoklu profil/çocuk profili, telefondan TV'ye Cast, bulutta altyazı çevirisi, kişisel öneri rafları.
- Sunucu tarafı destekçi kontrolü (cihaz sınırı 2–3). Sunucu maliyeti ve bakımı gerektirir.

## 3b. Fikir havuzu (karar verilmedi, planlanan iş DEĞİL)
Sadece fikir alışverişi; kullanıcı net bir karar vermedi. Konuşmak istediğinde ele alınır, aksi halde yapım başlamaz.

- **Zorunlu güncelleme (fikir):** GitHub'da küçük bir `min-version.json` (en düşük sürüm + mesaj); uygulama açılışta bir kez bakar (duyuru kontrolü gibi, sessiz hata). Yüklü sürüm küçükse tam ekran, kapatılamayan "Güncelle" penceresi (mevcut `UpdateFlow` kullanılır). İki seviye: önerilen ("Sonra" var) ve zorunlu (yalnızca güvenlik/veri bozan hata/çalışmayan varsayılan addon). Güvenceler: değer yayınlanmış en son sürümden büyükse ya da dosya bozuksa yok sayılır; internet yoksa engellenmez (çevrimdışı indirilmiş içerik izlenebilir, açık karar: tam kilit mi istenir?). Sınır: bu özellikten önceki sürümler (1.1.0–1.1.4) zorlanamaz; kod açık olduğu için derlenmiş mod sürümde atlanabilir.
- **Sahte/mod sürümlere karşı önlem (fikir, uygulama büyürse):** GPL-3.0 nedeniyle modlamayı yasaklayamayız; hedef kullanıcıyı sahte sürümlerden korumak. Resmi indirme yerlerini netleştirmek (GitHub releases + Telegram, SHA-256), resmi imza yoksa sakin bir uyarı, asıl koruma hesap/sunucu tarafında (Aşama 4).

## 4. Açık sorular (kullanıcıdan beklenen)
1. Ödeme sitesi: kullanıcı araştırıyor (Aşama 2 şartnamesinden önce gerekli).

## 5. Günlük ritim
- Her gün tek konu, 20–30 dk konuşma. Konuşulanlar bu dosyaya işlenir.
- Bir aşamanın şartnamesi bitip onaylanınca yapım başlar; yapım sürerken sıradaki aşama konuşulur.

## 6. Karar günlüğü
- 2026-10-21: Zorunlu güncelleme ve sahte/mod sürüm uyarısı sadece fikir olarak "Fikir havuzu"na yazıldı; plana alınmadı.
- 2026-10-21: Yeni bölüm için hem rozet hem "Yeni bölümler" rafı. Ödeme sitesi kullanıcı araştırınca seçilecek.
- 2026-10-21: Watch List, Play'e karıştırılmaz; ayrı bir oluşum olarak kalır.
- 2026-10-21: Gelir için bağış/destekçi modeli ile başlanır; hesap + bulut sonraya. Çalışma sistemi (yukarıdaki 7 adım) ve bu dosya oluşturuldu.
