# Play 1.1.47 — Gerçek Cihaz Test Planı

> **Amaç:** OCR ile jenerik başlangıcının metadata olmadan algılanmasını, sonraki bölüme geçişi ve bu sürümdeki profil/depo silme düzeltmelerini gerçek Android cihazda doğrulamak. Bu belge bir test senaryosudur; testlerin fiziksel cihazda çalıştırıldığı anlamına gelmez.

## 1. Test ortamı ve ön koşullar

- Android emülatörü değil, gerçek bir Android telefon veya TV cihazı kullanın. Test edilen APK: `Play 1.1.47` debug derlemesi.
- Cihaz modeli, Android sürümü, ekran çözünürlüğü, uygulama dili (Türkçe/İngilizce), ağ türü ve APK sürümünü sonuç formuna yazın.
- İzleme hakkınız olan, süresi en az 5 dakika olan bir **dizi bölümü** seçin. İstek bilgileri doğru TMDB dizi kimliği/sezon/bölümünü taşımalı ve bir sonraki bölüm bulunabilir olmalı.
- OCR’yi tek başına sınamak için seçilen akışta outro/chapter işareti bulunmamalı ve TheIntroDB’de aynı bölüm için kredi/jenerik zaman aralığı bulunmadığı doğrulanmalı. Böylece görülen düğme OCR hareket algılamasından kaynaklanır.
- DRM/korumalı yüzey kullanmayın: bu uygulama `TextureView` karesini cihaz üzerinde OCR’dan geçirir; korumalı yüzey kare vermiyorsa test OCR başarısını ölçmez. Mümkünse yüksek kontrastlı, ekranda en az dört ayrı yazı satırı ve yukarı doğru kayma içeren bir jenerik seçin.
- Örnek video veya kaynak size ait olmalı ya da test amacıyla kullanma izniniz bulunmalı. Ekran kaydı alacaksanız içerik paylaşma haklarına dikkat edin.

## 2. OCR pozitif senaryosu — metadata yokken kayan jenerik

1. Dizi bölümünü normal akıştan oynatın; player overlay ve erişilebilirlik kontrollerinin açık olduğundan emin olun. Otomatik sonraki bölüm geri sayımı ayarını bu senaryoda kapalı tutun; manuel OCR düğmesini ayrı sınayın.
2. Jenerikten önceki noktaya gidin. Konum, bölüm süresinin %50’sinden ileri olmalı; bu algılama eşiğinin ön koşuludur. Jenerik henüz başlamamışken oynatmayı sürdürün.
3. Jenerik başladığında video oynuyor olsun. OCR örnekleri yaklaşık 1,5 saniyede bir alınır; eşleşen en az dört yazı satırının birbirine yakın hızla yukarı kayması ve ardışık taramalarda devam etmesi gerekir. Hemen tek karede düğme çıkmaması beklenen davranıştır.
4. Kayan yazı başladıktan sonra en fazla 10 saniye gözlemleyin. Üst sağda “Sonraki bölüm”/“Bir sonraki bölüme geç” eylemi görünmelidir. Jeneriğin sabit bir karesi veya sıradan altyazı tek başına düğmeyi göstermemelidir.
5. Düğmeye dokunun. Bulunan sonraki bölümün kaynağı önceden aranmaya başlamış olmalı; yeni bölüm yüklenmeli ve ilk görüntü geldiğinde başlık/sezon/bölüm bilgisi değişmiş olmalıdır. Geçiş süresini (dokunuştan ilk kareye kadar) kaydedin. Yeni bölüm açılmadan eski bölümün görüntüsü siyah ekranda kalmamalıdır.
6. Geçişten sonra uygulamanın çökmediğini, oynatma kontrollerinin çalıştığını ve normal geri tuşuyla oynatıcıdan çıkılabildiğini doğrulayın.

**Beklenen:** Düğme yalnızca gerçekten kayan jenerik başladığında görünür; dokunma, mevcut bölümün sonunu beklemeden bulunan sonraki bölüme geçer. İlk kareye kadar ağ/kaynak çözümleme süresi kaydedilir; tek bir sabit süre eşiğiyle değerlendirilmez.

## 3. OCR negatif senaryoları ve sıfırlama

| Senaryo | Uygulama | Beklenen sonuç |
| --- | --- | --- |
| Sabit yazı | %50 sonrası statik başlık/kart veya sabit oyuncu listesinde 10 saniye oynatın. | “Sonraki bölüm” düğmesi çıkmamalı. |
| Normal diyalog/altyazı | Ekranda metin varken ancak yazı yukarı doğru akmıyorken oynatın. | Yanlış jenerik algısı ve düğme olmamalı. |
| Duraklatılmış video | Jenerikten önce duraklatıp en az 10 saniye bekleyin; sonra oynatın. | Duraklatılmış halde yeni algı oluşmamalı; oynatma başlayınca gerçek kayma varsa algılanabilir. |
| Geri sarma | Düğme göründükten sonra tespit noktasından 15 saniyeden fazla geriye sarın. | Düğme gizlenmeli ve algılama durumu sıfırlanmalı; yeniden jenerik kaymasına gelince tekrar sınanmalı. |
| Film | Bir filmi jenerik sonuna kadar oynatın. | Diziye özel sonraki bölüm düğmesi görünmemeli. |
| Son bölüm / sonraki bölüm yok | TMDB veya eklenti bölüm listesinde sonraki bölümü olmayan dizi bölümünü oynatın. | Geçilecek bir sonraki bölüm olmadığı için eylem görünmemeli. |
| Kısa içerik | 5 dakikadan kısa bir video oynatın. | OCR jenerik taraması devreye girmemeli. |

> Korumalı yüzeyde kare alınamaması OCR için **geçersiz test kurulumu** sayılır; yanlış pozitif/negatif olarak raporlanmamalı. Varsa doğrulanmış outro metadata’sı ayrı yedek davranış olarak ayrıca sınanabilir; OCR-only vakasıyla karıştırmayın.

## 4. Oynatma ve geçiş regresyonları

- Bölümün başlangıcında, ortasında ve jenerik dışındaki bir metin sahnesinde “Sonraki bölüm” eyleminin görünmediğini kontrol edin.
- Jenerik algılanınca intro/özet atlama düğmesi aynı anda jenerik düğmesinin üstüne binmemeli; yanlış aralıkta intro/özet atlama yapılmamalı.
- Akışın sonraki bölümü bulunamazsa düğmenin görünmediğini; kaynak bulunması gecikirse uygulamanın çökmediğini ve kullanıcıyı siyah ekranda bırakmadığını kontrol edin. Çözümleme/stream sağlayıcı hatasını not edin.
- OCR görsel kareleri cihaz üzerinde işler; test sırasında ağ denetiminde kare veya OCR metni gönderildiğine dair istek olmamalıdır.

## 5. “Son profili hatırla” regresyonu

1. Ayarlar’da **Son profili hatırla** seçeneğini açın. Profil A’yı seçip uygulamayı tamamen kapatın (son uygulamalar ekranından da çıkarın) ve yeniden başlatın.
2. Profil seçici atlanıp Profil A doğrudan açılmalı. A’da PIN varsa otomatik geçilmemeli; Profil A adına PIN istemi gösterilmelidir.
3. Profil B’ye geçin, uygulamayı tamamen kapatıp yeniden açın. B açılmalı; önceki A açılmamalı.
4. Tercihi kapatın, uygulamayı yeniden başlatın. Bu kez profil seçici gelmeli.
5. Hatırlanan profil silinmiş veya gizlenmişse geçersiz/erişilemeyen profil otomatik açılmamalı; seçim ekranı gelmelidir.

## 6. Eklenti deposu ve alt depoları silme regresyonu

1. Bir üst CloudStream deposunu (P) kurun; kurulumun otomatik bulduğu en az iki alt depoyu (A, B) not edin. Ayrıca ilgisiz, bağımsız bir depo (C) kurun. Mümkünse başka bir üst deponun da kullandığı ortak alt depoyu (S) test kümesine ekleyin.
2. **Kurulu depolar / eklenti kataloğu** ekranında yalnızca P satırındaki çöp kutusuna dokunun ve onaylayın.
3. P ile yalnızca P’ye ait A/B alt depo kayıtları ve bunların kurulu eklenti dosyaları kaldırılmalı. Bağımsız C yerinde kalmalı. Başka üst depo tarafından da kullanılan S ve o üst depo korunmalı.
4. Kaldırma sonrası ekranı kapatıp tekrar açın; aynı sonuç kalıcı olmalı. Kaldırılan P/A/B tekrar listede görünmemeli. Başka depoların eklenti sayısı veya kullanılabilirliği değişmemeli.
5. P’yi yeniden ekleyin (test kaynağı uygunsa). Aynı alt depolar yeniden keşfedilebilmeli/kurulabilmeli; silme eski kayıt veya kod dosyası bırakmamalı.

## 7. İzlemeye devam et / izlendi / geçmişten kaldır regresyonu

1. İzlemeye devam et satırında yarım kalmış bir film ve bir dizinin kartını hazırlayın. Dizi için mümkünse farklı bölümlerde kayıt oluşturun; ekranda görünen kartın aktif kullanıcı/profil kapsamına ait olduğunu doğrulayın.
2. Dizi posterine uzun basın ve **İzlendi olarak işaretle** seçeneğine dokunun. Eylem başarılı mesajı göstermeli; dizi hemen İzlemeye devam et satırından kalkmalı ve **Kütüphane > İzlendi olarak işaretlenen filmler ve diziler** bölümünde poster/tür bilgisiyle görünmelidir. Bitmiş dizinin “sonraki bölüm” önerisi devam satırına geri eklenmemelidir.
3. Aynı adımı filmde uygulayın. Film de devam satırından kalkmalı ve aynı Kütüphane kategorisinde görünmelidir. Uygulamayı kapatıp açınca kategori kaydı korunmalıdır.
4. Bir başka yarım içerikte uzun basıp **Geçmişten kaldır** seçeneğine dokunun. Başlık devam satırından kaybolmalı; Kütüphane’de favori değilse izlendi kategorisinde de görünmemelidir. Yeniden açılışta geri gelmemelidir.
5. İzlendi işaretli bir dizinin yeni/başka bir bölümünü oynatıp en az 30 saniye ilerleyin. Yeni ilerleme kaydı başlığın manuel işaretinden daha yeni sayılmalı; dizi tekrar İzlemeye devam et listesinde görünmelidir.
6. İki kullanıcı profili varsa testi profil A’da yapıp sonra profil B’ye geçin. A’daki silme/izlendi eylemleri B’nin geçmiş veya Kütüphane içeriğini değiştirmemelidir.

**Beklenen:** Manuel izlendi başlıkları Continue sorgusuna alınmaz; Kütüphane’de profille sınırlı tek bir başlık olarak gösterilir. “Geçmişten kaldır” tüm bölüm kayıtlarını siler. Yeni oynatma yeni ve daha güncel devam kaydı oluşturur.

## 8. TheIntroDB v4 ve atlama düğmeleri regresyonu

**Ön koşul:** TheIntroDB v4’te timestamp kaydı bulunan, yasal olarak izlenebilir bir film ve bir dizi bölümü seçin. Test öncesinde seçilen kayıt türlerini/zaman aralıklarını servis yanıtından doğrulayın. Çevrimiçi ve çevrimdışı denemelerde uygulama loglarında kişisel bilgi/akış URL’si olmadığını gözlemleyin.

1. Ayarlar > Oynatıcı’da **Atlama butonlarını göster** açık, **Atlama bölümlerini otomatik geç** kapalı olsun. Intro, özet, jenerik ve önizleme kategorilerinin açık olduğunu doğrulayın. Uygulamayı yeniden açınca tercihler korunmalı.
2. Dizi bölümünü oynatın. Ağ gözleminde yalnızca `GET https://api.theintrodb.org/v4/media` görülmeli; `tmdb_id` veya kimlik yoksa `imdb_id`, bölümde `season`/`episode` ve bilinen `duration_ms` gönderilmelidir. v1/v2/v3, API anahtarı, video/stream URL’si veya video karesi gönderilmemeli. Oynatma ilk kareye ulaşmak için API yanıtını beklememelidir.
3. API’de kayıtlı bir segmentin aralığına gelin. Doğru türde düğme görünmeli; dokununca segmentin `end_ms` konumuna sarılmalıdır. `start_ms=null` için medya başlangıcı, `end_ms=null` için medya süresi kullanılmalıdır. 3 saniyeden kısa veya video süresini aşan bir segment düğme oluşturmamalıdır.
4. Oynatıcı kontrollerini gizleyin ve segment başlangıcına girin. Düğme ilk yaklaşık 20 saniye görünmeli, sonra kaybolmalıdır. Kontrolleri yeniden açınca segment aralığı içindeyse düğme tekrar görünmelidir. Kumandalı cihazda D-pad ile odaklanıp OK/Enter ile çalıştığını doğrulayın.
5. Aynı anda örtüşen iki doğrulanmış kategori varsa iki ayrı etiketli eylem gösterilmeli; tek düğme diğerinin erişimini engellememelidir.
6. Bir filmi de oynatın: sezon/bölüm parametreleri gönderilmemeli, içerik kimliği ve bilinen süre gönderilmelidir. Kimliği olmayan çevrimdışı/yerel içerikte ağ isteği olmamalı ve oynatma devam etmelidir.
7. 404 döndüren bir içerik seçin. Bu hata mesajı veya oynatma kesintisi oluşturmamalı; ilgili segment düğmesi gösterilmemelidir. Daha sonra aynı içeriği yeniden açınca sonuç kısa süreli bellek önbelleğinden dönmeli, yeni istek sayısını not edin.
8. Ağ isteğini çevrimdışıyken başlatın veya ağı çağrı sırasında kapatın. En çok yaklaşık 7 saniyede sessizce sonuçsuz kalmalı; oynatma/durdurma/seek kontrolleri çalışmaya devam etmeli, kullanıcıya ağ hata penceresi çıkmamalıdır.
9. **Otomatik geçiş** ayarını açık yapıp yeni bir segmentin başından oynatın: segment yalnızca bir kez otomatik atlanmalıdır. Konumu geriye sarıp aynı segment aralığına tekrar girin; aynı oturumda tekrar atlamamalıdır. Ayarı kapatınca düğme açık kalsa bile otomatik seek olmamalı. Her segment türünü ayrı kapatıp açarak kontrol edin.
10. `credits.end_ms=null` olan dizi bölümünde sonraki bölüm varsa, **Jeneriği geç** sonraki bölüme geçmelidir; sonraki bölüm yoksa medya sonuna sarılmalıdır. Mevcut OCR tabanlı “Bir sonraki bölüme geç” davranışının da çalışmaya devam ettiğini doğrulayın.

**Beklenen:** API hatası, 404 veya yavaş yanıt oynatmayı bloklamaz. Düğmeler yalnızca doğrulanmış geçerli aralıklarda görünür; kategori ayarları, manuel seek/geri sarma, TV odağı ve otomatik-atla oturum sınırı beklenen şekilde çalışır.

## 9. CloudStream Molystream/Vidmoly çözücü regresyonu

1. CloudStream kaynağında Vidmoly/Molystream ailesini kullanan, izinli ve çalışır bir video bağlantısı bulun. URL alan adının `molystream.org`, `dbx.molystream.org` veya `moly.to` olduğuna bakın.
2. İçeriği normal eklenti akışından açın. Bu üç host için Vidmoly extractor doğru linki (HLS veya doğrudan video) üretmeli; oynatıcı siyah ekranda kalmamalı. İlk kareye ulaşma süresini ve dönen bağlantı türünü kaydedin.
3. Uygulamayı kapatıp yeniden açmadan başka bir CloudStream linki deneyin. Global kayıt ve doğrudan çözümleme birlikte çalışmalı; aynı host için extractor listesinde yinelenen alias oluşmamalı.
4. Bu üç host dışında, bozuk veya erişilemeyen bir URL deneyin. Uygulama çökmemeli; diğer kayıtlı extractor’ların denemesi devam etmeli ve anlaşılır kaynak hatası gösterilmelidir.
5. Kaynak listesinden farklı bir geçerli eklenti akışını da açın. Molystream extractor eklenmesi diğer CloudStream sağlayıcılarının çalışmasını bozmamalıdır.

**Beklenen:** Üç Vidmoly alan adı doğru extractor’a yönlendirilir, geçersiz host sessizce diğer adaylara bırakılır ve çift kayıt / uygulama çökmesi oluşmaz. Bu senaryo gerçek cihazda çalıştırılmadan test planı yalnızca prosedür sayılır.

## 10. Sonuç formu

| Alan | Sonuç |
| --- | --- |
| Cihaz / Android sürümü / APK sürümü |  |
| Molystream alias alan adı ve çözülen kaynak türü |  |
| Yinelenen extractor / diğer sağlayıcı regresyonu |  |
| Ekran dili / ağ |  |
| Dizi, TMDB kimliği, S/E, süre |  |
| Kaynak / DRM durumu / video yüzeyi |  |
| TheIntroDB veya chapter outro işareti yokluğu doğrulandı mı? |  |
| Jenerik başlangıç zamanı ve düğmenin görünme zamanı |  |
| Düğme yanlış sahnede göründü mü? |  |
| Dokunuştan sonraki bölümün ilk karesine geçiş süresi |  |
| Sonraki bölüm / bölüm bilgisi doğru mu? |  |
| Geri sarma ve negatif test sonuçları |  |
| Profil hatırlama sonucu |  |
| v4 istek parametreleri / API kimliği |  |
| 404 / bağlantısız test / oynatma kesintisi |  |
| Skip düğmesi, kategori ayarları ve D-pad |  |
| Otomatik atla kapalı/açık ve rewind sonucu |  |
| Açık uçlu jenerik sonraki bölüme geçişi |  |
| İzlenme işareti / Kütüphane kategorisi / yeni bölümde devam |  |
| Geçmişten kaldırma sonucu |  |
| P/A/B/C/S depo silme sonucu |  |
| Ekran kaydı / log / hata notları |  |

**Sonuç durumu:** ☐ Başarılı ☐ Başarısız ☐ Test kurulumu geçersiz (ör. DRM yüzeyinden kare alınamıyor)
