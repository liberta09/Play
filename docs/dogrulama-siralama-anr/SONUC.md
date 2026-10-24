# Sıralama ve ANR Optimizasyonu Doğrulama Raporu

**Tarih:** 25 Ekim 2026  
**Dal:** `fix-siralama-anr`  
**Cihaz / Ortam:** LDPlayer 9 (Android, 1600x900)  
**Toplam Birim Test:** 981 test (979 GEÇTİ, 0 BAŞARISIZ, 0 HATA, 2 ATLANDI)

---

## 1. Kök Neden Analizi ve Uygulanan Çözümler

Samsung S22 Ultra / Android 16 üzerinde birden fazla eklenti (özellikle torrent kaynakları) etkinken "Kaynaklar yükleniyor…" ekranında meydana gelen ana iş parçacığı kilitlenmesi (6 ANR kaydı) ve çöp toplayıcı baskısı incelendi:

1. **`StreamLog.hostOf` İstisna Maliyeti:**
   - Adres `http://` veya `https://` ile başlamadığında (ör. `magnet:` linkleri) `HttpUrl.parse` dahili olarak `IllegalArgumentException` fırlatıyor ve `Throwable.fillInStackTrace` ana iş parçacığını kilitliyordu.
   - **Çözüm:** `http://` veya `https://` ile başlamayan tüm adresler OkHttp çağrılmadan doğrudan `"?"` döndürecek şekilde korundu. Ayrıca iş parçacığı güvenli, 512 elemanla sınırlı LRU önbellek eklendi.

2. **Karşılaştırıcı İçi Tekrarlanan Ağır Hesaplama:**
   - `SourceRanking.recommended` ve `SourceSort` karşılaştırıcı lambdası içinde her eleman çifti için `isSlowHost`, magnet kontrolü, çözünürlük katmanı ve boyut ayrıştırması tekrar tekrar $O(n \log n)$ defa hesaplanıyordu.
   - **Çözüm:** *Decorate-Sort-Undecorate* (Schwartzian transform) modeli uygulandı. Her kaynak için sıralama anahtarları bir kez hesaplanıp önbelleğe alındı (`DecoratedRecommended`, `DecoratedSize`, `DecoratedResolution`). `isSlowHost` host bazında tekil kontrol edildi. Sıralama çıktısı eski mantıkla bit-for-bit birebir aynı tutuldu.

3. **Ana İş Parçacığında Mükerrer Sıralama ve Eklenti Patlaması:**
   - `SourceCoordinator` her eklenti sonucunda listeyi ana iş parçacığında baştan sıralıyor; `probeSizes`, `maybeSuggestDub` ve `fallback` seçimleri listeyi tekrar tekrar dönüyordu.
   - **Çözüm:** Sıralanmış kaynak listesi önbelleğe alındı (`rankedCache`). `streams`, `sizes`, `preference` ve yavaş host değişmediği sürece liste yeniden hesaplanmaz. Eklenti sonuçları için 200 ms debounce/conflate uygulandı; sıralama işlemi `Dispatchers.Default` üzerine taşındı.

---

## 2. Birim Test Doğrulamaları

- `SourceRankingOptimizationTest`:
  - `hostOfReturnsQuestionMarkForMagnetAndInvalidUrlsWithoutException`: `magnet:?xt=urn:btih:...` adresinde sıfır istisna ve anında `"?"` dönüşü doğrulandı (< 1 ms).
  - `recommendedFinishesUnder300msFor3000SourcesWithHalfMagnetsAnd200Hosts`: 3000 kaynaklık (1500 magnet, 200 farklı host) karma listede JVM üzerinde sıralama süresi **45 ms** (300 ms sınırının çok altında) ölçüldü.
  - `oldAndNewSortingGiveExactSameResultAcross25MixedScenarios`: 25 farklı karma kaynak kombinasyonunda yeni optimize sıralamanın eski karşılaştırıcıyla %100 birebir özdeş sıralama dizilimi verdiği kanıtlandı.
  - `twoHundredConsecutiveResultsDoNotRecomputeRankingEveryTime`: `SourceCoordinator`'da 200 ardışık eklenti sonucunda sıralama önbelleklemesi sayesinde sıralama çağrısı sayısı < 10 kaldı.
- **Tüm Proje Testleri:** `./gradlew testDebugUnitTest` ile 981 test çalıştırıldı (979 başarılı, 0 hata, 0 başarısız, 2 atlandı).

---

## 3. Cihaz Üzerinde Doğrulama (LDPlayer)

Tüm eklentiler ve repolar açıkken:

### 3.1 Film Doğrulaması (Örümcek-Adam: Yepyeni Bir Gün)
- "Haftanın Filmleri" rafından film başlatıldı.
- İlk kaynak HTTP 500 yanıtı döndürdüğünde sistem otomatik olarak sonraki kaynağa geçti.
- **İlk Kare Süresi:** 1.74 saniye.
- **Süre Kontrolü:** 145.0 dk = 145 dk (Movie -> Ok).
- **ANR Durumu:** Sıfır ANR, sıfır donma.
- **Kanıt Ekran Görüntüsü:** `docs/dogrulama-siralama-anr/spider_man_ilk_kare.png`

### 3.2 Dizi Doğrulaması (Criminal Minds S01E21)
- Dizi detay sayfasından 1. Sezon 21. Bölüm seçilerek oynatma başlatıldı.
- Eklentilerden çok sayıda alternatif kaynak eşzamanlı toplandı.
- **İlk Kare Süresi:** 2.42 saniye.
- **Süre Kontrolü:** 43.7 dk (Tv -> Ok).
- **ANR Durumu:** Sıfır ANR, akıcı başlatma ve oynatma.
- **Kanıt Ekran Görüntüsü:** `docs/dogrulama-siralama-anr/criminal_minds_s01e21_ilk_kare.png`

### 3.3 Yük Testi (test_many_providers)
- Debug derlemede `--ez test_many_providers true` parametresiyle başlatıldı.
- 60 adet yapay test sağlayıcısı sağlayıcı listesine eklendi.
- Sağlayıcı seçici penceresi (ProviderSelectorSheet) açıldı, tam liste boyunca aşağı ve yukarı kaydırmalar yapıldı.
- Liste kaydırması tamamen akıcı gerçekleşti, hiçbir kare düşüşü veya ANR gözlenmedi.
- **Kanıt Ekran Görüntüsü:** `docs/dogrulama-siralama-anr/test_many_providers.png`

---

## 4. Sonuç Özeti

| Test Senaryosu | Beklenen | Ölçülen / Sonuç | Durum |
|---|---|---|---|
| `StreamLog.hostOf` magnet adresleri | İstisna yok, `"?"` | 0 istisna, hızlı LRU dönüşü | GEÇTİ |
| 3000 kaynak sıralama süresi | < 300 ms | 45 ms | GEÇTİ |
| 25 senaryoda eski/yeni sıralama eşitliği | %100 özdeş | %100 özdeş | GEÇTİ |
| Toplam birim test | 981 test | 979 geçti, 2 atlandı, 0 başarısız | GEÇTİ |
| Cihaz Film Testi (Spider-Man) | İlk kare < 5s, ANR yok | İlk kare 1.74s, 0 ANR | GEÇTİ |
| Cihaz Dizi Testi (Criminal Minds S01E21) | İlk kare < 5s, ANR yok | İlk kare 2.42s, 0 ANR | GEÇTİ |
| Yük Testi (60 sağlayıcı) | Akıcı liste, ANR yok | Akıcı kaydırma, 0 ANR | GEÇTİ |
