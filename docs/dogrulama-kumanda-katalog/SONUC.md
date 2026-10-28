# Kumanda (D-pad) ve dil: Depo Kataloğu + Eklentiler sayfaları

Dal: `ozellik/eksikler-4`. LDPlayer (1600x900), debug derleme, `adb install -r -t` (uninstall yok); son kurulum
`lastUpdateTime=2026-10-28 16:34:25`, sürüm 1.1.20. Gezinme yalnızca `adb shell input keyevent`
(DPAD_UP/DOWN/LEFT/RIGHT/CENTER, BACK); yalnızca profil seçimi ve "Dokunmatik" bölümündeki kontrol dokunuşla yapıldı.

## Kök nedenler
1. `RepoManagerCatalogScreen`: Material3 `Button`, ham `OutlinedTextField`, odak hedefi olmayan satır → odak halkası yok,
   başlangıç odağı/odak hafızası yok. Tüm metinler koda gömülüydü.
2. Satır (odak hedefi) içindeki denetimlere (anahtar, sil) **SAĞ tuşuyla ulaşılamıyordu**: tıklanabilir satır odaktayken Compose
   yön tuşlarını kendi çocuklarına indirmiyor (kanıt: `10-kurulu-tum-depolar-onceki-derleme-sag-calismiyordu.png`, SAĞ x3
   hiçbir şey değiştirmiyor). Aynı sorun Katalog satırındaki Yükle düğmesinde de vardı.
3. `PluginRow` (Nuvio Pluginler) ve `WebSiteRow` tıklanabilir satırlarında odak halkası çizilmiyordu.

## Yapılan (yalnızca odak/kumanda + string taşıma)
- **Depo Kataloğu**: `ui/components/PlayButtons.kt` Button'ı (özel biçim/boşluk için ek aşırı yükleme), arama kutusunda
  `focusRing` + `dpadLeavesTextField`, satırda `focusRing` + `focusable`, `rememberFocusMemory`/`focusMemory`
  (ilk satır ya da son odaklanan), liste hazır olunca başlangıç odağı, hata/boş durumda Tekrar dene düğmesine başlangıç odağı.
  Yükle'ye basınca düğme kaybolsa da odak satırda kalır. "Yüklü" etiketi (dolgulu küçük hap) ve yükleme halkası (24dp), 3dp
  halkalı odak çerçevesinden ayrışır. Geri ekranı kapatır (`navController.popBackStack`, ScreenHeader geri düğmesi de odaklanır).
- **Satır içi denetimler** (yeni `RowControls` yardımcıları, `ui/tv/PlayFocus.kt`): satır → SAĞ → anahtar → SAĞ → sil, SOL geri.
  Uygulandığı yerler: Kurulu tüm depolar / Nuvio Addon'ları / Yüklü eklentiler (`AddonRow`), Nuvio Pluginler (`PluginRow`,
  ayar sayfası olan satırlar), Addon detay (`CatalogManageRow` anahtarı), Web siteleri (`WebSiteRow` sil düğmesi).
  `PluginRow` ve `WebSiteRow` satırlarına eksik `focusRing` eklendi.
- **Dil**: 14 yeni anahtar (`repo_catalog_*`; `repo_catalog_type_cloudstream` `translatable="false"`), `values` + `values-tr`.
  Taşınan gömülü metin: 17 (Depo Kataloğu x2 yerde, alt başlık, arama ipucu, Tekrar Dene [mevcut `common_retry`], Depo bulunamadı,
  Sonuç bulunamadı, Bir hata oluştu, Katalog yüklenemedi, Yükle, Yüklü, 2 Toast, 3 tür etiketi, Addon detaydaki "Popüler
  Filmler/Diziler" [mevcut `home_row_popular_*`]). `StringResourcesTest` geçti.

## Kanıt (hepsi kumandayla)
| Dosya | Ne gösteriyor |
|---|---|
| 01 | Ekrana girince başlangıç odağı ilk satırda (turkuaz halka) |
| 02 | SAĞ → Yükle düğmesinde halka (satır halkası da görünür) |
| 03 | YUKARI → arama kutusunda halka |
| 04 | YUKARI → geri düğmesinde halka |
| 05 | Yükle'ye tek Tamam → "Yüklü" etiketi + "Depo eklendi" toast'ı; **halka satırda, odak kaybolmadı** |
| 05b | Yükleme sürerken halka + satırda ilerleme göstergesi (önceki derleme, aynı satır kodu) |
| 07 | Geri → Eklentiler sayfası |
| 08 | "Eklenti ekle" penceresi: halka |
| 11–14 | Yüklü eklentiler / Kurulu tüm depolar: satır, SAĞ ile anahtar halkası, SAĞ ile sil halkası |
| 15 | CloudStream Depoları (cihazda depo yok; boş durum, geri düğmesi halkası) |
| 16–17 | Nuvio Addon'ları: anahtar ve sil halkası |
| 18–19 | Addon detayı: anahtar halkası |
| 20, 20b | Nuvio Pluginler: satır halkası, SAĞ ile anahtar halkası |
| 30–32 | **İngilizce**: Addons sayfası, Repository catalog (arama ipucu, Install/Installed, "Nuvio repo/plugin"), Türkçe metin yok (kalanlar uzaktaki katalog verisi: "SİNEMA", "KATALOG", depo adları) |
| 40 | Dokunmatik: liste kayıyor, halka çıkmıyor, Yükle dokunuşla çalışıyor (spinner) |

Dil, test sonunda "Sistem" seçeneğine geri alındı.

## Regresyon
- `testDebugUnitTest`: geçti (EXIT=0), `StringResourcesTest` dahil.
- `logcat -b crash`: boş (gezinme ve yükleme boyunca).
- Dokunmatik: Ayarlar → Eklentiler → Depo Kataloğu açılıyor, kaydırma ve Yükle dokunuşu çalışıyor.

## Bilinen sınırlar (kapsam dışı, düzeltilmedi)
- **"Yüklü" etiketi klasör biçimli katalog adreslerinde hiç oluşmuyor**: yüklenen adres `AddonUrl.normalize` ile
  `.../manifest.json` olarak saklanıyor, katalog ise ham adresi karşılaştırıyor (örn. `https://katalog.ayruki.workers.dev/`).
  Yüklenince toast çıkıyor ama satır "Yükle" kalıyor; yeniden basınca "AlreadyAdded". `.json` ile biten adreslerde doğru çalışıyor.
  Davranış değişikliği olacağı için dokunulmadı.
- Yükleme hata iletileri ham istisna metnini gösteriyor ("Addon KindMismatch: nuvio_repo") ve katalog türü yanlış sınıflanabiliyor.
- Başka gömülü Türkçe metinler (bu görev dışı): profil seçici ("Kimin İzlediğini Seçin", ...), Ayarlar > "Profil seçimi",
  "Son profili hatırla".
- Web siteleri satırı: cihazda site eklenmediğinden satır düzeltmesi kodla doğrulandı, ekranda gösterilemedi.
