# UI İnce Zaman Çizgisi ve 3003 / Sahte Kaynak Doğrulama Raporu

**Dal:** `fix-ui-ve-3003`  
**Tarih:** 2026-10-22  
**Birim Test Sonucu:** `.\gradlew testDebugUnitTest` -> **TOTAL TESTS: 863 | FAILURES: 0 | ERRORS: 0 | SKIPPED: 2**  
**Durum:** ÇALIŞTI  

---

## 1. B Maddesi: İnce Zaman Çizgisi (Canvas ProgressBar)
- **Dosya:** `app/src/main/java/com/kaan/play/ui/player/PlayerScreen.kt`
- **Uygulama Yöntemi:**
  - Material3 `Slider` bileşeni dahili minimum görsel sınırları nedeniyle ~16dp altına inmediğinden, `ProgressBar` içinde `Box(Modifier.height(48.dp))` ve özel `Canvas` çizimi uygulandı.
  - **İz (Track) Kalınlığı:** `4.dp.toPx()` (`StrokeCap.Round`), arka plan `%30 Beyaz`, aktif kısım `primary` turkuaz.
  - **Tutamaç (Thumb):** `6.dp.toPx()` yarıçap (çapı tam `12.dp`), yuvarlak `drawCircle`.
  - **Dokunma Alanı (Touch Target):** `48.dp` yükseklik korunarak dokunmatik kullanımı ve sürükleme kolaylığı sağlandı (`detectTapGestures` + `detectHorizontalDragGestures` ve `ProgressSeek.finishTargetMs`).
  - **Kumanda & Odak (D-pad):** `focusRequester`, `focusProperties { up = upFocus; down = downFocus }`, `focusable()`, Row üzerinde `focusRing(RoundedCornerShape(24.dp))` ve sol/sağ kumanda adımları aynen korundu.

### Cihaz Üzerinde Piksel Ölçüm Sonuçları (LDPlayer)
- **Ekran Çözünürlüğü:** 1600 × 900
- **Ekran Yoğunluğu (Density):** 240 dpi (`density = 240 / 160 = 1.5`, yani `1 dp = 1.5 px`)
- **İz (Track) Ölçülen Kalınlık:**
  - Piksel: **6 px** (y=768'den y=773'e dikey profil)
  - dp Karşılığı: $6\text{ px} / 1.5 = \mathbf{4.0\text{ dp}}$ (Hedef: ~4dp, Tam uyum)
- **Tutamaç (Thumb) Ölçülen Boyut:**
  - Piksel: **18 px × 18 px** (x=[114, 131], y=[762, 779])
  - dp Karşılığı: $18\text{ px} / 1.5 = \mathbf{12.0\text{ dp}}$ (Hedef: ~12dp, Tam uyum)
- **Dokunma Alanı Yüksekliği:**
  - $48\text{ dp} \times 1.5 = 72\text{ px}$ (dokunma ve sürükleme test edildi, çalışıyor)

---

## 2. C Maddesi: 3003 Hatası ve Sahte Kaynak Elemesi
1. **Android'den Bağımsız Boyut Kuralı (`SourceMediaRules`):**
   - **Dosya:** `app/src/main/java/com/kaan/play/data/stream/SourceMediaRules.kt`
   - **Birim Test:** `app/src/test/java/com/kaan/play/data/stream/SourceMediaRulesTest.kt` (7 test, hepsi başarılı).
   - Film için boyut biliniyorsa ve 20 MB'tan küçükse kaynak elenir (`MIN_MOVIE_BYTES = 20_000_000L`).
   - Dizi bölümü için boyut biliniyorsa ve 5 MB'tan küçükse kaynak elenir (`MIN_EPISODE_BYTES = 5_000_000L`).
   - Boyut bilinmiyorsa (null veya <= 0) kaynak kesinlikle **elenmez**.
   - `CompositeStreamProvider.forContent` ve `SourceCoordinator.acceptable / reportSize` aşamalarında erken filtreleme yapıldı.

2. **Oynatma Öncesi Medya Doğrulama (`SourceProbe`):**
   - **Dosya:** `app/src/main/java/com/kaan/play/data/download/SourceProbe.kt`
   - `probe()` metoduna zaman aşımı parametresi eklendi (`timeoutMs: Long = 2_000L`).
   - **Gizlilik Kuralı:** Adres, çerez ve token kesinlikle loglanmaz; yalnızca `httpUrl.host`, durum kodu, `Content-Type` ve uzunluk loglanır (`PlayProbe` etiketi).
   - Oynatmadan önce `SourceCoordinator.begin()` içinde 2 saniyelik zaman aşımıyla probe çalıştırılır. Zaman aşımı aşılırsa kaynak **elenmez** ve oynatmaya devam edilir.
   - `Content-Type: text/html` veya ilk baytlar medya değilse (`Probe.Rejected`) kaynak elenir ve sıradakine geçilir.
   - Tüm kaynaklar elenirse oynatıcı siyah ekranda bırakılmaz, "Bu içerik için kaynak bulunamadı" durumuna geçer (`startFailed("all-sources-rejected")`).
   - **Birim Test:** `app/src/test/java/com/kaan/play/ui/player/SourceCoordinatorTest.kt` (4 yeni test, toplam 23 test başarılı).

3. **Mevcut Kurallarla Çakışma Yok:**
   - `data/match/ContentDurationCheck` ve `StreamOwnership` dosyalarına dokunulmamış, kuralları bozulmamıştır.

---

## 3. Ekran Doğrulama Dosyaları
- `docs/dogrulama-ui/01_arama.png`: "Yüzüklerin" araması ve Yüzük Kardeşliği kartı.
- `docs/dogrulama-ui/02_detay.png`: "Yüzüklerin Efendisi: Yüzük Kardeşliği" detay ekranı ve Oynat butonu.
- `docs/dogrulama-ui/03_oynatici_ince_cubuk.png`: Oynayan gerçek videoda (Yüzük Kardeşliği) 4dp ince iz ve 12dp tutamaçlı zaman çizgisi.
- `docs/dogrulama-ui/03_cubuk_yakin_crop.png`: Zaman çizgisinin kırpılmış yakın görünümü (zaman metinleri, ince iz ve tutamaç).
- `docs/dogrulama-ui/03_cubuk_zoom_piksel.png`: İz (6 px = 4.0 dp) ve tutamacın (18 px = 12.0 dp) 4x büyütülmüş piksel ızgara kanıtı.
- `docs/dogrulama-ui/04_tv_dpad_odak.png`: TV D-pad odak halkası (`focusRing` 24dp) ve zaman çizgisi odağı.
- `docs/dogrulama-ui/kaynak_paneli.png`: Kaynak panelinde 1 MB sahte kaynakların listelenmediğinin doğrulanması.
