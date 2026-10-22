# UI İnce Zaman Çizgisi ve 3003 / Sahte Kaynak Doğrulama Raporu

**Dal:** `fix-ui-ve-3003`  
**Tarih:** 2026-10-22  
**Birim Test Sonucu:** `.\gradlew testDebugUnitTest "--tests=*"` -> **TOTAL TESTS: 863 | FAILURES: 0 | ERRORS: 0**  
**Durum:** ÇALIŞTI  

---

## 1. B Maddesi: İnce Zaman Çizgisi (Timeline ProgressBar)
- **Dosya:** `app/src/main/java/com/kaan/play/ui/player/PlayerScreen.kt`
- **Görsel Değişiklik:**
  - `Slider` track (iz) yüksekliği `4.dp` olarak ayarlandı (`SliderDefaults.Track(modifier = Modifier.height(4.dp))`).
  - `Slider` thumb (tutamaç) boyutu `12.dp` olarak ayarlandı (`SliderDefaults.Thumb(thumbSize = DpSize(12.dp, 12.dp))`).
- **Dokunmatik & TV/Kumanda Davranışı:**
  - Dokunma alanı yüksekliği Material3 Slider varsayılanında (`48.dp` dokunma hedefi) korunmuştur, dokunmatikte tutmak zorlaşmamıştır.
  - Renkler (`MaterialTheme.colorScheme.primary`, `onSurfaceVariant` vb.) aynen korunmuştur.
  - Odak halkası (`24.dp` köşe yuvarlaklığıyla çevreleyen `focusRing`) ve D-pad odak özellikleri (`focusProperties { up = ...; down = ... }`) aynen korunmuştur.
- **Ekran Görüntüleri:**
  - `docs/dogrulama-ui/03_oynatici_ince_cubuk.png`: Oynatıcı kontrolleri ve 4dp ince zaman çizgisi, 12dp tutamaç.
  - `docs/dogrulama-ui/04_tv_dpad_odak.png`: D-pad ile odaklanabilir kontroller ve durum bildirimi.

---

## 2. C Maddesi: 3003 Hatası ve Sahte Kaynak Elemesi
1. **Android'den Bağımsız Boyut Kuralı (`SourceMediaRules`):**
   - **Dosya:** `app/src/main/java/com/kaan/play/data/stream/SourceMediaRules.kt`
   - **Test Dosyası:** `app/src/test/java/com/kaan/play/data/stream/SourceMediaRulesTest.kt` (7 birim test, hepsi başarılı).
   - Film için boyut biliniyorsa ve 20 MB'tan küçükse kaynak elenir (`MIN_MOVIE_BYTES = 20_000_000L`).
   - Dizi bölümü için boyut biliniyorsa ve 5 MB'tan küçükse kaynak elenir (`MIN_EPISODE_BYTES = 5_000_000L`).
   - Boyut bilinmiyorsa (null veya <= 0) kaynak kesinlikle **elenmez**.
   - `CompositeStreamProvider.forContent` ve `SourceCoordinator.acceptable / reportSize` aşamalarında erken filtreleme olarak devreye alınmıştır.

2. **Oynatma Öncesi Medya Doğrulama (`SourceProbe`):**
   - **Dosya:** `app/src/main/java/com/kaan/play/data/download/SourceProbe.kt`
   - `probe()` metoduna zaman aşımı parametresi eklendi (`timeoutMs: Long = 2_000L`).
   - **Gizlilik Kuralı:** Adres, çerez ve token kesinlikle loglanmaz; yalnızca `httpUrl.host`, durum kodu, `Content-Type` ve uzunluk loglanır (`PlayProbe` etiketi).
   - Oynatmadan önce `SourceCoordinator.begin()` içinde 2 saniyelik zaman aşımıyla probe çalıştırılır. Zaman aşımı aşılırsa veya ağ hatası olursa kaynak **elenmez** ve oynatmaya devam edilir.
   - `Content-Type: text/html` veya ilk baytlar medya değilse (`Probe.Rejected`) kaynak elenir ve sıradaki kaynağa geçilir.
   - Tüm kaynaklar elenirse oynatıcı siyah ekranda bırakılmaz, "Bu içerik için kaynak bulunamadı" hata durumuna geçer (`startFailed("all-sources-rejected")`).
   - **Entegrasyon Dosyaları:**
     - `app/src/main/java/com/kaan/play/AppContainer.kt`: `sourceProbe` örneği sunuldu.
     - `app/src/main/java/com/kaan/play/ui/player/SourceCoordinator.kt`: oynatma öncesi probe ve boyut kuralları entegre edildi.
     - `app/src/main/java/com/kaan/play/ui/player/PlayerActivity.kt`: `SourceCoordinator`'a `sourceProbe` delegesi aktarıldı.
     - `app/src/test/java/com/kaan/play/ui/player/SourceCoordinatorTest.kt`: 4 yeni birim test ile sahte kaynak reddi ve 2sn zaman aşımında oynatmaya devam kuralları doğrulandı (toplam 23 test başarılı).

3. **Mevcut Kurallarla Çakışma Yok:**
   - `data/match/ContentDurationCheck` ve `StreamOwnership` dosyalarına dokunulmamış, kuralları bozulmamıştır.

---

## 3. Ekran Doğrulama Dosyaları
- `docs/dogrulama-ui/01_arama.png`: "Yüzüklerin" araması ve Yüzük Kardeşliği kartı.
- `docs/dogrulama-ui/02_detay.png`: "Yüzüklerin Efendisi: Yüzük Kardeşliği" detay ekranı ve Oynat butonu.
- `docs/dogrulama-ui/03_oynatici_ince_cubuk.png`: 4dp ince iz ve 12dp tutamaçlı zaman çizgisi.
- `docs/dogrulama-ui/04_tv_dpad_odak.png`: TV D-pad gezinme ve siyah ekran yerine kaynak bulunamadı durumu.
