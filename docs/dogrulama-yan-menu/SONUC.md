# Doğrulama Raporu: Geniş Ekran / TV Açılır-Kapanır Yan Menü

**Tarih:** 25 Ekim 2026, 03:53  
**Dal:** `yan-menu-daralt`  
**Cihaz:** LDPlayer 9 (Android 9 / SDK 28, 1600×900)  
**Toplam Birim Testi:** 987 geçti, 0 başarısız, 2 atlandı.

---

## 1. Uygulanan Değişiklikler
- **Açılır-Kapanır Yan Menü (`PlayRoot.kt`):**
  - Geniş ekran/TV modunda (`showNav && layout.useRail`) yan menü varsayılan olarak kapalıdır (72 dp dar simge şeridi); logo yalnızca ▶ işareti, sekme simgeleri dikeyde sıralıdır.
  - Menü içine odak girdiğinde (`isMenuFocused && keyboard`) 180 ms süreyle (`animateDpAsState`) 184 dp genişliğe açılarak "Play" logosu ve sekme metinleri ("Ana sayfa", "Ara", "Kütüphane", "Ayarlar") görünür; odak menüden çıktığında yumuşakça 72 dp'ye kapanır.
  - İçerik alanı menü açılırken sıkışmaz; menü içerik alanının üzerine bindirilir (overlay) ve sağ kenarında ince `#1E3A5F` dikey çizgi yer alır; içerik alanı her zaman 72 dp dar şeride göre konumlanır.
  - Dokunmatik modda simgelere dokunmak menüyü açmadan doğrudan ilgili sekmeye gider; menü kapalı kalır.
  - Dikey telefon alt gezinme çubuğu (bottom bar) hiçbir değişikliğe uğramadan korunmuştur.
  - Odak halkası, odak hafızası (`rememberFocusMemory` ile son sekmenin hatırlanması) ve D-pad yön geçişleri eksiksiz korunmuştur.
- **Düzen Sabitleri (`PlayLayout.kt` & `PlayLayoutTest.kt`):**
  - `RAIL_COLLAPSED_WIDTH_DP = 72` ve `RAIL_EXPANDED_WIDTH_DP = 184` tanımlanmış ve birim testi eklenmiştir.
- **Belgelendirme:**
  - `CLAUDE.md` ("TV ve tablet uyumluluğu") ve `docs/yol-haritasi.md` ("Karar günlüğü") dosyalarına kullanıcı kararı işlenmiştir.

---

## 2. Cihaz Testi Sonuçları (LDPlayer)

1. **`menu_kapali.png` (✅):** Ana sayfa açıldığında yan menünün 72 dp dar simge şeridi olarak kapalı durduğu, logonun yalnızca ▶ simgesi olduğu ve afişlerin açılan alana doğru genişlediği doğrulanmıştır.
2. **`menu_acik.png` (✅):** D-pad ile sola gidilerek menüye odaklanıldığında menünün 184 dp'ye açılarak yazıların belirdiği ve içerik afişlerinin sıkışmadan menünün altında kaldığı (overlay) doğrulanmıştır.
3. **`menu_kapandi.png` (✅):** D-pad ile "Ara" sekmesine inilip seçildikten sonra sağa basılarak arama alanına geçildiğinde menünün tekrar 72 dp dar şeride kapandığı doğrulanmıştır.
4. **`dokunmatik.png` (✅):** Dokunmatik tıklama ile Kütüphane simgesine basıldığında menü açılmadan doğrudan Kütüphane sekmesine geçildiği ve Kütüphane simgesinin vurgulandığı doğrulanmıştır.


---

## 3. Sürüm ve Yayın Doğrulaması (1.1.12)
- **Sürüm:** 1.1.12 (versionCode 18)
- **Birim Testleri:** 987 geçti, 0 başarısız, 2 atlandı.
- **Sertifika SHA-256:** 5c7cd2bd91946586d125e59a848544f7db10b42ab0c0d3d6dd30c5b8c4e9f44f (apksigner ile doğrulandı).
- **APK SHA-256:** 79047b1b885f7c479cf1768b1d8fa499f96d268b46dd7772496aea7eed3bfbb8
- **Play-1.1.12-SHA256.txt:** BOM içermez, ilk 3 bayt: [55, 57, 48] ('7', '9', '0').
- **GitHub Release:** https://github.com/liberta09/Play/releases/tag/v1.1.12
- **En Son Etiket:** 1.1.12
- **REST İndirme Doğrulaması:** GitHub Release REST API üzerinden indirilen Play-1.1.12.apk dosyasının SHA-256 değeri (79047b1b885f7c479cf1768b1d8fa499f96d268b46dd7772496aea7eed3bfbb8), Play-1.1.12-SHA256.txt ile tam olarak eşleşmiştir.
