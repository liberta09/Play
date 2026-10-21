# Şartname — Web görünümünde TV/kumanda desteği

Durum: **onay bekliyor** (yapım başlamadı). Bağlı olduğu madde: `docs/yol-haritasi.md` > Aşama 0.

## Amaç
TV kutusunda (normal ok tuşlu kumanda) Ayarlar > Eklentiler > Web siteleri'nden açılan bir sitede: gezinmek, arama yapmak, film açıp tam ekran izlemek ve çıkmak mümkün olsun. Telefonun (dokunmatik) davranışı **hiç değişmesin**.

## Mevcut durum (koddan)
- `WebActivity` (WebView + Compose çubuğu). Geri sırası zaten var: tam ekran video → web geçmişi → kapat. Tam ekran video `onShowCustomView` ile var. `mediaPlaybackRequiresUserGesture = true` (video bir tıklamayla başlar).
- Eksik: sayfada fare/imleç yok; WebView'a yalnızca odak veriliyor (`requestFocus`). Sitelerin çoğunda ok tuşları düğmeleri seçemiyor.
- Dokunmatik/TV ayrımı için `supportsTouch()` (PlayerLevels.kt) zaten var.

## Kapsam
1. **Sanal imleç** (yalnızca `supportsTouch() == false` iken): ok tuşları imleci oynatır (basılı tutunca hızlanır), Tamam/Enter imleç altına tıklar, imleç ekran kenarına dayanınca sayfa o yönde kayar. İmleç 3 sn tuş basılmazsa soluklaşır. Küçük, kendi çizdiğimiz turkuaz imleç. Çubukta "Yukarı" ile üst çubuğa geçiş (mevcut `onFocusLeaveUp`) korunur; çubuğa girince imleç gizlenir, çubuktan "Aşağı" ile sayfaya dönünce geri gelir.
2. **Yazı yazma:** imleçle bir metin kutusuna tıklayınca sistem ekran klavyesi açılır; yazılan metin kutuya düşer; Geri klavyeyi kapatır (sayfadan çıkmaz).
3. **Tam ekran video:** sitedeki oynatıcının tam ekran düğmesine imleçle basınca mevcut tam ekran yolu çalışır; tam ekranda imleç görünür kalır (site oynatıcısı düğmeleri için), Tamam tıklar. Geri tuşu önce tam ekrandan çıkarır.
4. **Geri tuşu sırası** (mevcut, korunur ve test edilir): klavye → tam ekran → web geçmişi → kapat.
5. **Telefon:** `supportsTouch() == true` iken imleç kodu hiç çalışmaz, görünmez; bugünkü davranış aynen kalır.

Kapsam dışı: her sitede kusursuz çalışma vaadi, imleç hız ayarı, yeni Ayarlar kalemi (yok), reklam/engelleme kurallarında değişiklik (yok), `WebPolicy`/`PlayWebViewClient` güvenlik kurallarının gevşetilmesi (yok).

## Kurallar
- Metin string resource (TR+EN), yeni görünür metin çıkarsa iki dosyaya eklenir.
- İmleç mantığı Android'den bağımsız bir sınıfta (`WebCursorController`): konum, hız/ivme, kenar kaydırma yönü, bekleme süresi; birim testli. Android kısmı yalnızca tuşları iletir ve imleci çizer.
- Tıklama, imleç koordinatına `MotionEvent` (DOWN+UP) göndererek yapılır (WebView'ın kendi dokunma yolu).
- URL/çerez/token loglanmaz. Güvenlik politikası aynen.
- Debug derlemede test için `play_force_remote` intent ekstrası (`supportsTouch` yerine "kumanda kipi" zorlar); yayın derlemesinde yok sayılır.

## Parçalar (her biri ≤45 dk, sırayla)
- **Parça 1:** `WebCursorController` + testleri; imleç çizimi; ok tuşları, Tamam tıklama, kenar kaydırma, imleç gizleme; çubuk ↔ sayfa geçişi; `play_force_remote`.
- **Parça 2:** klavye açma/kapama ve Geri sırasına klavyeyi ekleme.
- **Parça 3:** tam ekranda imleç; Geri sırası denetimi; duman testi kontrolü; belge güncellemesi (README + yol haritası).

## Kabul maddeleri
- K1: Kumanda kipinde, test sayfasında ok tuşları imleci hareket ettirir; basılı tutunca hızlanır; ekranın dışına çıkmaz. (birim test + cihaz)
- K2: Tamam, imlecin altındaki bağlantıya/düğmeye tıklar (test sayfasında tıklama sayacı artar).
- K3: İmleç alt/üst kenara dayanınca sayfa kayar; sayfa sonunda durur.
- K4: Metin kutusuna tıklayınca klavye açılır ve yazılan metin kutuda görünür; Geri önce klavyeyi kapatır, ikinci Geri sayfada geri gider.
- K5: Test sayfasındaki video, tam ekran düğmesine imleçle basınca tam ekrana geçer; imleç görünür; Tamam oynat/durdur düğmesine tıklar; Geri tam ekrandan çıkarır ve sayfa korunur.
- K6: Geri sırası: klavye → tam ekran → geçmiş → kapat; geçmiş bitince ekran kapanır.
- K7: Üst çubuğa Yukarı ile geçilir, imleç gizlenir; Aşağı ile sayfaya dönülür, imleç geri gelir.
- K8: 3 sn tuşsuzluktan sonra imleç soluklaşır; tuşa basınca geri gelir.
- K9: Dokunmatik kipte (varsayılan telefon) imleç hiç görünmez ve mevcut web testleri/davranış değişmez.
- K10: Tüm testler yeşil (`StringResourcesTest` dahil), duman testinde gerileme yok, release (R8) derlenir.
- K11 (kullanıcı denemesi, TV kutusu): gerçek bir sitede açma, gezinme, arama kutusuna yazma, bir videoyu tam ekranda izleyip çıkma çalışır. Siteye özgü kusurlar listelenir, kusursuzluk vaat edilmez.

## Riskler
- Bazı site oynatıcıları sentetik dokunuşu reddedebilir (güven denetimi); K11'de görülür, gerekirse ikinci tur.
- Emülatör (telefon) kumanda kipinde çalışsın diye `play_force_remote` gerekir; TV kutusundaki gerçek davranış yalnızca cihazda doğrulanır.
