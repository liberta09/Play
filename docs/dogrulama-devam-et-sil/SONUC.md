# Doğrulama Raporu: "İzlemeye Devam Et" Kartını Kaldırma Özelliği

**Tarih:** 2026-11-03  
**Dal:** `feat/devam-et-sil` (taban: `origin/main` @ `3bb1dd0`)  
**Sürüm:** 1.2.4 (sürüm artırılmadı)  
**Düzenek:** LDPlayer (127.0.0.1:5555), Android 9 x86_64, 1600x900  
**Fiziksel TV kutusu:** Doğrulanamadı (fiziksel TV kutusu bağlı değil; emülatör üzerinde kumanda keyevent'leri ve dokunmatik ile doğrulandı)

---

## 1. Görev ve Yapılanlar Özeti

Ana sayfadaki "İzlemeye devam et" satırında yer alan içerik kartlarını basılı tutarak veya kumanda Menü tuşuyla listeden kaldırma özelliği tamamlandı.

1. **Yol Haritası ve README Güncellemesi:**
   - [docs/yol-haritasi.md](file:///C:/Projeler/Play/docs/yol-haritasi.md): 2026-11-03 kullanıcı kararı karar günlüğüne eklendi.
   - [README.md](file:///C:/Projeler/Play/README.md): TV / Kumanda bölümüne CLAUDE.md kuralı gereği tuş karşılığı notu eklendi ("Her işlemin kumandada bir tuş karşılığı vardır: Tamam basılı tutma veya Menü tuşu").

2. **Çoklu Dil String Kaynakları (EN + TR):**
   - [app/src/main/res/values/strings.xml](file:///C:/Projeler/Play/app/src/main/res/values/strings.xml):
     - `continue_remove_message`: "Remove from Continue watching"
     - `continue_remove_confirm`: "Remove from list"
     - `continue_remove_cancel`: "Cancel"
   - [app/src/main/res/values-tr/strings.xml](file:///C:/Projeler/Play/app/src/main/res/values-tr/strings.xml):
     - `continue_remove_message`: "İzlemeye devam et'ten kaldır"
     - `continue_remove_confirm`: "Listeden kaldır"
     - `continue_remove_cancel`: "Vazgeç"
   - `StringResourcesTest`: Anahtar eşitliği doğrulandı ve başarıyla geçti.

3. **Android'den Bağımsız Saf Kural Sınıfı (`ContinueDeletionRules`):**
   - [app/src/main/java/com/kaan/play/data/library/ContinueDeletionRules.kt](file:///C:/Projeler/Play/app/src/main/java/com/kaan/play/data/library/ContinueDeletionRules.kt):
     - `targetFrom(item: ContinueItem)` / `targetFrom(entry: WatchHistoryEntity)`: Hedef çıkarımı.
     - `shouldDelete(entry, target)` / `filterRemaining(history, target)`: Dizide tüm bölümlerin, filmde tek kaydın eşleşmesi; olmayan kayıtta sessiz kalma.
     - `nextFocusedItem(removedIndex, remaining)`: Silme sonrası odak verilecek komşu öğe hesabı.
   - [app/src/test/java/com/kaan/play/data/library/ContinueDeletionRulesTest.kt](file:///C:/Projeler/Play/app/src/test/java/com/kaan/play/data/library/ContinueDeletionRulesTest.kt): 5 birim testiyle %100 kapsandı.

4. **Veri Katmanı (`LibraryRepository`):**
   - [app/src/main/java/com/kaan/play/data/library/LibraryRepository.kt](file:///C:/Projeler/Play/app/src/main/java/com/kaan/play/data/library/LibraryRepository.kt): `removeContinueWatching(type, tmdbId)` ve `removeContinueWatching(item: ContinueItem)` fonksiyonları eklendi.
   - `history.deleteForMedia(u, p, tmdbId, type.name)` çağrısı kullanılarak Room üzerinden tek sorguyla dizinin tüm bölümleri veya film kaydı silinir.
   - [app/src/test/java/com/kaan/play/data/library/LibraryTest.kt](file:///C:/Projeler/Play/app/src/test/java/com/kaan/play/data/library/LibraryTest.kt): Dizi ve film silme birim testleri eklendi.

5. **Arayüz ve Kumanda Etkileşimi (`PosterCard` & `HomeScreen`):**
   - [app/src/main/java/com/kaan/play/ui/components/PosterCard.kt](file:///C:/Projeler/Play/app/src/main/java/com/kaan/play/ui/components/PosterCard.kt):
     - `onLongClick` parametresi eklendi.
     - Kumanda Menü tuşu (`Key.Menu` / keycode 82) doğrudan `onLongClick` tetikler.
     - Kumanda Tamam tuşu (`Key.DirectionCenter`, `Key.Enter`, `Key.NumPadEnter`): ~0.5 sn basılı tutulduğunda (`delay(500)` veya native `FLAG_LONG_PRESS`) `onLongClick` tetiklenir; kısa basıp bırakıldığında normal `onClick` (oynatma) çalışır.
     - Dokunmatikte `combinedClickable` ile uzun basma `onLongClick` tetikler.
   - [app/src/main/java/com/kaan/play/ui/home/HomeScreen.kt](file:///C:/Projeler/Play/app/src/main/java/com/kaan/play/ui/home/HomeScreen.kt):
     - `AlertDialog` onay penceresi: Başlık içeriğin adı, metin "İzlemeye devam et'ten kaldır".
     - Düğmeler: "Vazgeç" ve "Listeden kaldır" (`PlayButtons.TextButton` odak halkalı).
     - Başlangıç odağı `rememberInitialFocus()` ile "Vazgeç" üzerindedir; kazara silme engellenir.
     - Geri tuşu = Vazgeç (`onDismissRequest`).
     - Silme işlemi animasyonsuz gerçekleşir; odak `continueRequesters` üzerinden komşu karta aktarılır; satır boşalırsa satır kapanır ve odak komşu satıra devredilir.
     - Favoriler ve indirilenler silinmez. Kütüphane geçmişi aynı tablodan beslendiği için silinen içerik orada da görünmez.

---

## 2. Senkronizasyon Riski Araştırması (`LocalSyncMerge`)

**Soru:** "LocalSyncMerge geçmişi en yeni `lastWatchedAt` birleşimiyle eşitliyor. Silinen kayıt Wi-Fi/bulut senkronunda diğer cihazdan GERİ GELEBİLİR mi? Hangi koşulda?"

### Kod İncelemesi Bulgusu
[LocalSyncMerge.kt](file:///C:/Projeler/Play/app/src/main/java/com/kaan/play/data/sync/local/LocalSyncMerge.kt#L237-L255) içindeki `mergeLibrary` fonksiyonu incelendi:
```kotlin
fun mergeLibrary(local: LocalProfileBackup?, remote: LocalProfileBackup?): LocalProfileBackup {
    val base = local ?: remote ?: return LocalProfileBackup()
    val history = LinkedHashMap<String, HistoryBackup>()
    for (h in (local?.history.orEmpty() + remote?.history.orEmpty())) {
        val key = historyKey(h)
        val current = history[key]
        if (current == null || newer(h, current)) history[key] = h
    }
    ...
```

### Sonuç (Geri Gelme Koşulu)
**EVET, silinen kayıt senkronizasyon ile GERİ GELİR.**
- **Koşul:** Cihaz A üzerinde bir içerik "İzlemeye devam et"ten (ve dolayısıyla `watch_history` tablosundan) silindiğinde, Cihaz A'nın yerel yedeğinde (`local?.history`) o kayıt artık yer almaz. Ancak aynı profili kullanan Cihaz B henüz o içeriği silmediyse, Cihaz B'nin yedeğinde (`remote?.history`) bu kayıt bulunmaktadır.
- Wi-Fi veya Supabase Bulut Senkronizasyonu çalıştığında, `mergeLibrary` birleşimi `(local.history + remote.history)` üzerinden iki listenin birleşim kümesini (union) alır. Cihaz A'da bu kayıt bulunmadığından `current == null` olur ve Cihaz B'deki kayıt Cihaz A'ya **yeniden ithal edilir (resurrection)**.
- Her iki cihazda da silinmediği sürece, silmeyen cihazla yapılan ilk senkronda silinen kart geri gelir.

### Öneri (Onay Beklenmektedir; Şema Değişikliği / Göç Yapılmadı)
Kural gereği kendiliğinden şema değişikliği yapılmamıştır. Bu durumun çözümü için ileride kullanıcı onayıyla şu mimari önerilmektedir:
- Room şema sürümünde (v7) hafif bir `deleted_history_tombstones` tablosu ya da `watch_history` üzerinde `deletedAt: Long?` alanı tanımlanması.
- `LocalSyncMerge` aşamasında, `h.lastWatchedAt < tombstone.deletedAt` olan kayıtların birleştirmeden elenmesi.
- Mezar taşlarının (tombstones) cihazlar arasında senkronize edilmesi ve 30 gün sonra otomatik temizlenmesi.

---

## 3. Test ve Derleme Sonuçları

### Birim Testleri
```bash
.\gradlew testDebugUnitTest
```
- **Toplam Test Sayısı:** 1201 (önceki: 1194, yeni: +7)
- **Başarısız:** 0
- **Atlanan:** 2 (önceden tanımlı harici ağ testleri)
- **Sonuç:** `BUILD SUCCESSFUL` (Tüm 1201 test eksiksiz geçti)

### Release Derlemesi
```bash
.\gradlew assembleRelease
```
- **Sonuç:** `BUILD SUCCESSFUL in 2m 54s`
- **APK Dosyası:** `app/build/outputs/apk/release/Play-1.2.4.apk`
- **Boyut:** 48,634,002 bayt (~46.38 MB)
- **SHA-256:** `1a7c79d49c2d159b64b6a31d8a971c2568289f743f9de756e38c1947353eb0c9`

---

## 4. Emülatör Doğrulama Kanıtları

Doğrulama betiği ile tüm senaryolar uçtan uca çalıştırılmış ve ekran görüntüleri `docs/dogrulama-devam-et-sil/` altına kaydedilmiştir:

| Ekran Görüntüsü | Açıklama |
|---|---|
| `01-kart-odak.png` | "İzlemeye devam et" satırındaki ilk kart ('Breaking Bad') odaklandı. |
| `02-dialog-menu-ile-acildi.png` | Kumandada Menü tuşuna (`Key.Menu` / 82) basıldığında onay penceresi açıldı. Başlık: "Breaking Bad", Metin: "İzlemeye devam et'ten kaldır". Odak "Vazgeç" düğmesinde. |
| `03-vazgec-ile-kapatildi.png` | Odak "Vazgeç"te iken Tamam'a basıldığında pencere kapandı, içerik veritabanında ve satırda kaldı. |
| `04-geri-ile-kapatildi.png` | Pencere açıkken kumanda Geri tuşuna basıldığında pencere kapandı, içerik silinmedi. |
| `05-dialog-odak-listeden-kaldir.png` | Yön tuşuyla odak "Listeden kaldır" kırmızı vurgulu düğmesine geçirildi. |
| `06-dizi-silindi-odak-komsuya-gecti.png` | Tamam'a basılarak silme onaylandı: Dizinin kaydı silindi, kart animasyonsuz kalktı, odak otomatik olarak komşu karta ('Fight Club') geçti. |
| `07-film-dialog-acildi.png` | Film kartında ('Fight Club') Menü tuşuyla silme penceresi açıldı. |
| `08-film-silindi-odak-diziye-gecti.png` | Film silindi (tek kayıt silindi), odak sağındaki 'Game of Thrones' kartına geçti. |
| `09-son-kart-silindi-satir-kapandi.png` | Çok bölümlü dizi ('Game of Thrones', S1E1 + S1E2): "Listeden kaldır" ile TÜM bölümleri silindi (bölüm sayısı: 2 -> 0). Satır tamamen boşaldığı için satır arayüzden kalktı ve odak komşu içeriğe devredildi. |
| `10-ust-menu-kopru.png` | Üst menü köprüleri (AŞAĞI/YUKARI/Geri geçişleri) denendi; menüye çıkış ve içeriğe dönüş sorunsuz çalıştı. |
| `11-kisa-tamam-oynatma.png` | Kısa Tamam basışının (normal seçim) hâlâ içeriği oynattığı/kaynak/detay akışını açtığı doğrulandı. |

---

## 5. Değiştirilen Dosyalar (`git diff --stat`)

```text
 README.md                                          |  2 +-
 .../com/kaan/play/data/library/LibraryRepository.kt|  9 +++
 .../com/kaan/play/ui/components/PosterCard.kt      | 98 +++++++++++++++++++++-
 .../java/com/kaan/play/ui/home/HomeScreen.kt       | 87 ++++++++++++++++++-
 .../java/com/kaan/play/ui/home/HomeViewModel.kt    |  7 ++
 app/src/main/res/values-tr/strings.xml             |  3 +
 app/src/main/res/values/strings.xml                |  3 +
 .../com/kaan/play/data/library/LibraryTest.kt      | 31 +++++++
 docs/yol-haritasi.md                               |  1 +
 9 files changed, 237 insertions(+), 4 deletions(-)

Untracked files:
 app/src/main/java/com/kaan/play/data/library/ContinueDeletionRules.kt
 app/src/test/java/com/kaan/play/data/library/ContinueDeletionRulesTest.kt
 docs/dogrulama-devam-et-sil/
```
