# Doğrulama Raporu: "İzlemeye Devam Et" Kartını Kaldırma Özelliği

**Tarih:** 2026-11-03  
**Dal:** `feat/devam-et-sil` (taban: `origin/main` @ `3bb1dd0`)  
**Sürüm:** 1.2.4 (sürüm artırılmadı)  
**Düzenek:** LDPlayer (127.0.0.1:5555), Android 9 x86_64, 1600x900  
**Fiziksel TV kutusu:** Doğrulanamadı (fiziksel TV kutusu bağlı değil; emülatör üzerinde kumanda keyevent'leri ve dokunmatik ile doğrulandı. Donanımsal tuş tekrarı için bkz. Bölüm 2 altındaki açıklama)

---

## 1. Görev ve Yapılanlar Özeti

Ana sayfadaki "İzlemeye devam et" satırında yer alan içerik kartlarını basılı tutarak veya kumanda Menü tuşuyla listeden kaldırma özelliği tamamlandı ve 3 kritik düzeltme uygulandı:

1. **Yol Haritası ve README Güncellemesi:**
   - [docs/yol-haritasi.md](file:///C:/Projeler/Play/docs/yol-haritasi.md): 2026-11-03 kullanıcı kararı karar günlüğüne eklendi.
   - [README.md](file:///C:/Projeler/Play/README.md): TV / Kumanda bölümüne CLAUDE.md kuralı gereği tuş karşılığı notu eklendi ("Her işlemin kumandada bir tuş karşılığı vardır: 'İzlemeye devam et' kartında Menü tuşu veya Tamam tuşunu ~0,5 sn basılı tutma onay penceresini açar").

2. **Çoklu Dil String Kaynakları (EN + TR Soru Cümlesi):**
   - [app/src/main/res/values/strings.xml](file:///C:/Projeler/Play/app/src/main/res/values/strings.xml):
     - `continue_remove_message`: "Remove from Continue watching?"
     - `continue_remove_confirm`: "Remove from list"
     - `continue_remove_cancel`: "Cancel"
   - [app/src/main/res/values-tr/strings.xml](file:///C:/Projeler/Play/app/src/main/res/values-tr/strings.xml):
     - `continue_remove_message`: "İzlemeye devam et\'ten kaldırılsın mı?"
     - `continue_remove_confirm`: "Listeden kaldır"
     - `continue_remove_cancel`: "Vazgeç"
   - `StringResourcesTest`: Anahtar eşitliği doğrulandı ve başarıyla geçti.

3. **Android'den Bağımsız Saf Kural Sınıfı (`ContinueDeletionRules`):**
   - [app/src/main/java/com/kaan/play/data/library/ContinueDeletionRules.kt](file:///C:/Projeler/Play/app/src/main/java/com/kaan/play/data/library/ContinueDeletionRules.kt):
     - `targetFrom(item: ContinueItem)` / `targetFrom(entry: WatchHistoryEntity)`: Hedef çıkarımı.
     - `shouldDelete(entry, target)` / `shouldDelete(item, target)`: Medya türü ve TMDb kimliği eşleşmesi.
     - `filterRemaining(history, target)` / `filterRemaining(items, target)`: Dizide tüm bölümlerin, filmde tek kaydın elenmesi.
     - `nextFocusedItem(removedIndex, remaining)`: Silme sonrası odak verilecek komşu öğe hesabı.
   - [app/src/test/java/com/kaan/play/data/library/ContinueDeletionRulesTest.kt](file:///C:/Projeler/Play/app/src/test/java/com/kaan/play/data/library/ContinueDeletionRulesTest.kt): 6 birim testiyle %100 kapsandı.

4. **HomeScreen Silme Mantığı Sadeleştirmesi (Ölü Kod Giderildi):**
   - [app/src/main/java/com/kaan/play/ui/home/HomeScreen.kt](file:///C:/Projeler/Play/app/src/main/java/com/kaan/play/ui/home/HomeScreen.kt): Silme onay butonundaki satır içi filtreleme kaldırılarak doğrudan `ContinueDeletionRules.targetFrom`, `shouldDelete`, `filterRemaining` ve `nextFocusedItem` fonksiyonları bağlandı.

5. **Gerçek Kumanda Basılı Tutma Sorununun Çözümü (`LongPressKeyMachine`):**
   - [app/src/main/java/com/kaan/play/ui/tv/LongPressKeyMachine.kt](file:///C:/Projeler/Play/app/src/main/java/com/kaan/play/ui/tv/LongPressKeyMachine.kt): Android ve Compose bağımsız saf durum makinesi.
   - Kural: 500 ms dolduğunda uzun basma algılanır/silahlanır (`LONG_PRESS_ARMED`) ve görsel ripple/basılı tutma sürer. Ancak pencere kullanıcı tuşu **BIRAKTIĞINDA** (`KeyUp` -> `ShowDialogOnRelease`) açılır. Böylece fiziksel kumandanın donanımsal tekrar eden `KeyDown` ve `KeyUp` olayları penceredeki odaklı "Vazgeç" düğmesine sızamaz.
   - Pencere kapandıktan sonra iç durum (`machine.reset()`, `currentPress = null`, `longPressJob = null`) tamamen temiz sıfırlanır; uzun basma sonrası yapılan ilk kısa Tamam basışı yutulmadan doğrudan oynatma/detay akışını açar.
   - [app/src/test/java/com/kaan/play/ui/tv/LongPressKeyMachineTest.kt](file:///C:/Projeler/Play/app/src/test/java/com/kaan/play/ui/tv/LongPressKeyMachineTest.kt): 7 birim testi ile tüm geçişler, tekrarlar, odak kaybı ve sıfırlanma doğrulandı.

---

## 2. Emülatör ve Donanım Kısıtları

> [!WARNING]
> **Fiziksel Kumanda Donanımsal Tuş Tekrarı:**
> Emülatörde ADB keyevent (`adb shell input keyevent`) mekanizmasıyla fiziksel kumandaların donanımsal otomatik tuş tekrar akışı (`repeatCount > 0` hızında sürekli basılı kalma) gerçekçi şekilde simüle edilememektedir. Bu sebeple donanımsal tuş tekrar davranışı emülatörde **doğrulanamadı, saf durum makinesi birim testiyle (`LongPressKeyMachineTest`) %100 kapsandı, gerçek kumandada kullanıcı denemesi gerekir.**

---

## 3. Senkronizasyon Riski Araştırması (`LocalSyncMerge`)

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

## 4. Test ve Derleme Sonuçları

### Birim Testleri
```bash
.\gradlew testDebugUnitTest
```
- **Toplam Test Sayısı:** 1209 (önceki: 1201, yeni: +8)
- **Başarısız:** 0
- **Atlanan:** 2 (önceden tanımlı harici ağ testleri)
- **Sonuç:** `BUILD SUCCESSFUL in 1m 3s` (%100 başarılı)

### Release Derlemesi
```bash
.\gradlew assembleRelease
```
- **Sonuç:** `BUILD SUCCESSFUL in 2m 36s`
- **APK Dosyası:** `app/build/outputs/apk/release/Play-1.2.4.apk`
- **Boyut:** 48,637,378 bayt (~46.38 MB)
- **SHA-256:** `bc4d5dbdaa53288ec079855407d34da7ca8ba429754245ccad0a23c4cc6144f7`

---

## 5. Emülatör Doğrulama Kanıtları

Doğrulama betikleri ile tüm senaryolar uçtan uca çalıştırılmış ve 11 adet ekran görüntüsü güncellenmiştir:

| Ekran Görüntüsü | Açıklama |
|---|---|
| `01-kart-odak.png` | "İzlemeye devam et" satırındaki ilk kart ('Breaking Bad') odaklandı. |
| `02-dialog-menu-ile-acildi.png` | Kumandada Menü tuşuna (`Key.Menu` / 82) basıldığında onay penceresi açıldı. Başlık: "Breaking Bad", Metin: "İzlemeye devam et'ten kaldırılsın mı?". Odak "Vazgeç" düğmesinde. |
| `03-vazgec-ile-kapatildi.png` | Odak "Vazgeç"te iken Tamam'a basıldığında pencere kapandı, içerik veritabanında ve satırda kaldı. |
| `04-geri-ile-kapatildi.png` | Pencere açıkken kumanda Geri tuşuna basıldığında pencere kapandı, içerik silinmedi. |
| `05-dialog-odak-listeden-kaldir.png` | Yön tuşuyla odak "Listeden kaldır" kırmızı vurgulu düğmesine geçirildi. |
| `06-dizi-silindi-odak-komsuya-gecti.png` | Tamam'a basılarak silme onaylandı: Dizinin kaydı silindi, kart animasyonsuz kalktı, odak otomatik olarak komşu karta ('Fight Club') geçti. |
| `07-film-dialog-acildi.png` | Film kartında ('Fight Club') Menü tuşuyla silme penceresi açıldı. |
| `08-film-silindi-odak-diziye-gecti.png` | Film silindi (tek kayıt silindi), odak sağındaki 'Game of Thrones' kartına geçti. |
| `09-son-kart-silindi-satir-kapandi.png` | Çok bölümlü dizi ('Game of Thrones', S1E1 + S1E2): "Listeden kaldır" ile TÜM bölümleri silindi (bölüm sayısı: 2 -> 0). Satır tamamen boşaldığı için satır arayüzden kalktı ve odak komşu içeriğe devredildi. |
| `10-ust-menu-kopru.png` | Üst menü köprüleri (AŞAĞI/YUKARI/Geri geçişleri) denendi; menüye çıkış ve içeriğe dönüş sorunsuz çalıştı. |
| `11-kisa-tamam-oynatma.png` | Uzun basıp pencere kapatıldıktan sonra yapılan İLK kısa Tamam basışının yutulmadığı, doğrudan oynatma/detay/eklenti akışını açtığı doğrulandı. |
