# Doğrulama: CloudStream Eklenti API Sebepleri ve Kendini Onarma
- **Dal:** `tani-eklenti-api` | **Durum:** ÇALIŞTI | **Testler:** 877 test başarılı (0 hata, 0 başarısızlık)
- **Ayrıştırılan Sebepler (`CsApiResult`):**
  1. `PluginRowMissing`: Veritabanında eklenti satırı yok ("Eklenti bulunamadı").
  2. `DexFileMissing`: `.dex` dosyası eksik; depodan 1 kez otomatik indirilir, olmazsa "Eklenti dosyası bulunamadı, yeniden indirilemedi".
  3. `IncompatibleLibrary`: `NoSuchMethodError`, `NoClassDefFoundError`, `AbstractMethodError`, `LinkageError` ("Bu eklenti Play'in sürümüyle uyumlu değil").
  4. `LoadFailed`: Sınıf yükleme / çalıştırma hatası ("Eklenti yüklenemedi").
  5. `NoApiRegistered`: DEX sorunsuz yüklendi ancak API sunmuyor ("Eklenti API'si bulunamadı").
- **Kendini Onarma:** `DexFileMissing` durumunda depodan `downloadCs3` (SHA-256 denetimli) ile 1 kez otomatik indirilip tekrar denenir; ikinci kez denenmez; kullanıcı verisi/ayarları korunur.
- **Gizlilik & Günlük:** Tek satırlık `PlayCs` etiketi: `internalName: Sebep`. URL, token, çerez, hata mesajı loglanmaz.
- **Arayüz (`ProviderDetailScreen`):** Teknik ayrıntı (sınıf adı vb.) gizlendi; sebebe özel iki dilde (en/tr) string kaynakları bağlandı; "Yeniden dene" düğmesi korundu.
- **Değişen Dosyalar:** `CsApiResult.kt`, `CsDexLoader.kt`, `CsRepository.kt`, `ProviderDetailScreen.kt`, `strings.xml` (en, tr) ve birim testler (`CsApiResultTest`, `CsRepositoryLoadApisTest`).
