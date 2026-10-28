# TV sekmesi: büyük yazıda kanallar görünmüyordu — sonuç

Tarih: 2026-10-28 · Dal: `ozellik/eksikler-4` · Yayın yok, sürüm değişmedi.

## Kök neden

`app/src/main/java/com/kaan/play/ui/tv/TvScreen.kt` filtre çipi satırı (Tümü / Nuvio / CloudStream): üç `FilterChip` tek `Row` içinde, etiketler sarabiliyordu. Dar ekranda ya da büyük yazıda sığmayan çip etiketini harf harf alt alta sarıyor ve ekran boyu uzuyordu; `Row` yüksekliği o çipe uyduğundan `weight(1f)` ile yerleşen kanal ızgarasına sıfır yükseklik kalıyordu (kanallar "yok" görünüyordu). Normal yazıda da dar ekranda "CloudStream" 3+ satıra bölünüyordu.

Aynı hata cihazda yeniden üretildi (emülatörü kullanıcının telefonu gibi dar dikey 360 dp genişliğe aldım; 1600 px yatay emülatörde çipler sığdığı için hata görünmüyor):
- `once-font-1.0.png`: "Clo/udS/trea/m (0)" harf harf; çip satırı uzamış, ızgara küçülmüş.
- `once-font-1.3.png`: üçüncü çip ekran boyu şerit, kanal yok.
- `once-font-2.0.png`: Tümü + Nuvio iki satıra sarmış, kanal yok.

## Düzeltme (yalnızca bu)

`TvScreen.kt` (çip satırı, ~satır 186–209): (1) her etikete `maxLines = 1, softWrap = false`; (2) satır `Modifier.horizontalScroll(rememberScrollState())` ile yana kaydırılabilir, çipler kendi genişliğini alır. Başlık bloğu, arama kutusu, ızgara, renkler, boşluklar değişmedi; yeni string yok. Ek yükseklik alt sınırı gerekmedi.

Commit: `c2c1897`.

## Doğrulama

- `testDebugUnitTest` + `assembleDebug`: başarılı (test sayısı değişmedi; yalnızca arayüz).
- `adb install -r -t` → Success (kaldırma yok), **`lastUpdateTime=2026-10-28 12:46:01`**.
- Ortam: `wm size 720x1600`, `wm density 320` (360 dp), kanal listesi dolu (137 kanal). Denemeden sonra `font_scale 1.0`, `wm size reset`, `wm density reset` ile geri alındı (doğrulandı: 1.0 / 1600x900 / 240).

Öncesi/sonrası yan yana: `yan-yana-font-1.0.png`, `yan-yana-font-1.3.png`, `yan-yana-font-2.0.png` (sol ÖNCE, sağ SONRA). Tek tek: `once-font-*.png`, `sonra-font-*.png`.

1. **font_scale 1.0:** `sonra-font-1.0.png` — Tümü (137) ve Nuvio (137) tam, "CloudS…" kaydırılabilir satırda kesilmiş (yana kayar); etiket tek satır; kanallar (TRT 1 HD, ATV…) görünüyor. ✔
2. **font_scale 1.3:** `sonra-font-1.3.png` — çipler tek satır, kanal kartları görünüyor. ✔
3. **font_scale 2.0:** `sonra-font-2.0.png` — kanallar görünüyor; `sonra-font-2.0-cip-kaydirildi.png` — çip satırı yana kaydırıldı, "CloudStream (0)" tek satır tam görünüyor. ✔
4. **D-pad:** `dpad-1-cip-tumu.png` (arama kutusu → çipler), `dpad-2-cloudstream-cipi-gorunur-alana-geldi.png` (Sağ ×2: odak CloudStream çipine geçti ve satır kendiliğinden yana kaydı, çip görünür alana geldi), `dpad-3-kanal-karti.png` (Aşağı: ilk kanal kartı odak halkasıyla). ✔ Odak hafızası (`focusMemory`) koduna dokunulmadı.

Gözlem (değiştirilmedi): bu ekrandaki çipler Material `FilterChip` olduğu için kumanda odağı yalnızca hafif zemin tonuyla görünüyor (turkuaz halka yok, bkz. `dpad-2`); bu davranış düzeltmeden önce de böyleydi. İstenirse `focusRing` eklenebilir.

## Başka ekranlarda aynı risk var mı?

Sarabilen çip/etiket içeren ve altında `weight(1f)` liste/ızgara olan benzer yapıyı taradım:
- **Güvenli (aynı hata yok):** Ara çipleri (`FlowRow`, `FilterPill` tek satır), kaynak listesi eklenti süzgeci (`LazyRow`), eklenti seçici sekmeleri (`LazyRow`), oynatıcı kaynak paneli (`horizontalScroll`), altyazı biçimi seçenekleri (`FlowRow`). `FilterPill` zaten `maxLines = 1`.
- **Düşük ama sıfır değil (cihazda büyük yazıda denemedim, düzeltmedim):** `ProfileChooserScreen.kt` — `PrimaryTabRow` ("Profiller" / "Bulut Hesabı", iki eşit sekme; büyük yazıda sekme metni sarabilir ve satır uzar, ama yükseklik bir iki satırla sınırlı, ızgarayı sıfırlamaz), ve altındaki "Profilleri Yönet" + "Yeni Profil" `Row`'u (`SpaceBetween`; düğme etiketleri sarabilir). Benim yazdığım Wi-Fi kartındaki "Bağlan" `softWrap = false`, sorun yok.
- Diğer ekranları (Detay, Kütüphane, Ayarlar) yalnızca kod okuyarak taradım; sarabilen çip + `weight(1f)` listesi bulmadım. Cihazda büyük yazı taraması yapılmadı.
