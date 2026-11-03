# İzlemeye Devam Et — Senkronizasyon Mezar Taşı (Tombstone) Doğrulama Raporu

**Tarih:** 2026-11-03  
**Dal:** `feat/devam-et-sil-senkron` (taban: `feat/devam-et-sil` @ `e1a4953`)  
**Hedef:** "İzlemeye devam et" satırından silinen içeriklerin Wi-Fi veya bulut senkronizasyonunda diğer cihazlardan geri gelmesini engellemek.

---

## 1. Mimari Tasarım ve Bağlayıcı Kurallar

1. **Room ve Supabase Şeması Dokunulmazlığı:**
   - Room şeması, `PlayDatabase` sürümü, migration ve Supabase tablo yapısına kesinlikle dokunulmamıştır.
   - Mezar taşları (tombstones) `LocalProfileRepository` bünyesinde DataStore `Preferences` içerisinde (`sync_deleted_history` String anahtarı) profil başına JSON formatında saklanır.
   - Profil başına en fazla 500 kayıt tutulur ve 90 günden eski kayıtlar budanır (`MAX_DELETED_HISTORY_PER_PROFILE = 500`, `DELETED_HISTORY_RETENTION_MS = 90 gün`).
2. **Ortak Senkron Yükü:**
   - Wi-Fi ve Supabase bulut senkronizasyonunun ortak aktarım modeli olan `LocalProfileTransfer` sınıfına varsayılanı boş liste olan `val deletedHistory: List<DeletedHistoryItem> = emptyList()` alanı eklendi.
3. **Android'den Bağımsız Birleştirme Mantığı (`LocalSyncMerge`):**
   - Mezar taşları her iki tarafın listelerinden birleştirilir (aynı profil + içerik için en yeni `deletedAt`).
   - Geçmiş kayıtları mezar taşlarıyla filtrelenir: `deletedAt >= lastWatchedAt` olan kayıtlar birleşimden çıkarılır.
   - Silindikten SONRA tekrar izlenen içerikler (`lastWatchedAt > deletedAt`) korunur ve geri gelir.
   - Diziler için mezar taşı (`type = "tv"`) dizinin tüm bölümlerini kapsar; silinme tarihinden sonra izlenen yeni bölüm varsa yalnızca o bölüm korunur.
   - Farklı profiller tamamen yalıtılmıştır (profil 1 mezar taşı profil 2'yi etkilemez).
   - `LocalSyncData.apply(plan)` aşamasında yerel Room veritabanından mezar taşına uyan (`lastWatchedAt <= item.deletedAt`) geçmiş satırları da fiziksel olarak silinir.

---

## 2. Geriye Dönük Uyumluluk Kanıtı (`ignoreUnknownKeys = true`)

1.2.4 ve önceki sürümlerin yeni `deletedHistory` alanını içeren JSON yükünü aldıklarında çökmemesi ve yeni sürümün eski yükleri hatasız okuyabilmesi doğrulanmıştır:

| Dosya | Satır Numarası | Kod Bloğu / Kanıt |
|---|---|---|
| `app/src/main/java/com/kaan/play/data/sync/local/LocalSyncMerge.kt` | 55 | `private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }` |
| `app/src/main/java/com/kaan/play/data/sync/LocalProfileRepository.kt` | 27 | `private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }` |
| `app/src/main/java/com/kaan/play/data/sync/SupabaseCloudSync.kt` | 52, 335 | `private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }` |
| `app/src/main/java/com/kaan/play/data/sync/local/LocalSyncBackup.kt` | 17 | `private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }` |
| `app/src/main/java/com/kaan/play/data/sync/local/LocalSyncClient.kt` | 49 | `private val json = Json { ignoreUnknownKeys = true }` |
| `app/src/main/java/com/kaan/play/data/sync/local/LocalSyncCrypto.kt` | 32 | `private val json = Json { ignoreUnknownKeys = true }` |

Birim testiyle de (`newPayloadReadByOldSchemaWithIgnoreUnknownKeys` ve `oldPayloadWithoutDeletedHistoryParses`) çift yönlü uyumluluk kanıtlanmıştır:
- Eski şema parser'ı yeni JSON'u hatasız çözmekte ve profiller, yedekler ve kaynakları korumaktadır.
- Yeni sürüm, `deletedHistory` alanı bulunmayan eski JSON'ları varsayılan `emptyList()` ile çözmektedir.

---

## 3. Kod Değişiklikleri ve `git diff --stat`

`git diff --stat feat/devam-et-sil`:
```text
 .../kaan/play/data/library/LibraryRepository.kt    |   1 +
 .../kaan/play/data/sync/LocalProfileRepository.kt  |  48 ++++++
 .../kaan/play/data/sync/LocalProfileTransfer.kt    |  10 ++
 .../com/kaan/play/data/sync/local/LocalSyncData.kt |  17 +-
 .../kaan/play/data/sync/local/LocalSyncMerge.kt    |  92 ++++++++--
 .../play/data/sync/local/LocalSyncMergeTest.kt     | 189 ++++++++++++++++++++-
 docs/yol-haritasi.md                               |   1 +
 7 files changed, 345 insertions(+), 13 deletions(-)
```

### Değiştirilen Dosyalar ve Gerekçeleri:
1. [LibraryRepository.kt](file:///C:/Projeler/Play/app/src/main/java/com/kaan/play/data/library/LibraryRepository.kt): `removeContinueWatching` fonksiyonu çağrıldığında mezar taşını DataStore'a yazması için `profile?.recordDeletedHistory(...)` çağrısı eklendi.
2. [LocalProfileRepository.kt](file:///C:/Projeler/Play/app/src/main/java/com/kaan/play/data/sync/LocalProfileRepository.kt): Mezar taşlarının DataStore'da tutulması, okunması, kaydedilmesi, 500 adet/90 gün sınırıyla budanması ve profil silindiğinde mezar taşlarının da temizlenmesi sağlandı.
3. [LocalProfileTransfer.kt](file:///C:/Projeler/Play/app/src/main/java/com/kaan/play/data/sync/LocalProfileTransfer.kt): `DeletedHistoryItem` veri sınıfı ve `LocalProfileTransfer` içine `val deletedHistory: List<DeletedHistoryItem> = emptyList()` alanı eklendi.
4. [LocalSyncData.kt](file:///C:/Projeler/Play/app/src/main/java/com/kaan/play/data/sync/local/LocalSyncData.kt): `export()` sırasında mezar taşlarının transfer yüküne dahil edilmesi, `apply(plan)` aşamasında birleştirilmiş mezar taşlarının kaydedilmesi ve Room veritabanından filtrelenen geçmiş kayıtlarının fiziksel silinmesi eklendi.
5. [LocalSyncMerge.kt](file:///C:/Projeler/Play/app/src/main/java/com/kaan/play/data/sync/local/LocalSyncMerge.kt): Saf Kotlin birleştirme mantığı: `pruneDeletedHistory`, `mergeDeletedHistory`, `filterHistoryWithTombstones` ve `plan` entegrasyonu.
6. [LocalSyncMergeTest.kt](file:///C:/Projeler/Play/app/src/test/java/com/kaan/play/data/sync/local/LocalSyncMergeTest.kt): 7 zorunlu senaryoyu doğrulayan kapsamlı birim testleri.
7. [yol-haritasi.md](file:///C:/Projeler/Play/docs/yol-haritasi.md): 2026-11-03 kullanıcı onayı yol haritasına işlendi.

---

## 4. Test Sonuçları

### Birim Testleri:
- **Toplam `testDebugUnitTest` sayısı:** 1216 (taban: 1209, +7 yeni test).
- **Sonuç:** 1216 başarılı, 0 hata, 2 atlandı.

### Doğrulanan 7 Temel Senaryo (`LocalSyncMergeTest`):
1. `tombstoneDeletesOldRecordOnMerge`: A siler, B'de eski kayıt var -> birleşimde ikisinde de OLMAZ (mezar taşı kazanır).
2. `reWatchedAfterDeletionReturns`: B silindikten SONRA tekrar izledi (`lastWatchedAt > deletedAt`) -> kayıt geri GELİR.
3. `seriesTombstoneDeletesAllEpisodesAndNewEpisodeSurvives`: Dizi silindi -> mezar taşı o dizinin TÜM bölümlerini siler; sonradan yeni bölüm izlenirse sadece o bölüm gelir.
4. `tombstoneIsolatedToProfile`: Farklı profil yalıtımı: profil 1'deki mezar taşı profil 2'deki aynı içeriği SİLMEZ.
5. `oldPayloadWithoutDeletedHistoryParses`: Eski yük (`deletedHistory` alanı hiç olmayan JSON) hatasız okunur ve `deletedHistory = []` kabul edilir.
6. `newPayloadReadByOldSchemaWithIgnoreUnknownKeys`: Yeni yük (`deletedHistory` alanı dolu), `ignoreUnknownKeys = true` olan eski şema tarafından çökmeden okunur ve diğer alanlar korunur.
7. `pruningMax500And90Days`: Budama kuralı: 90 günden eski veya profil başına 500'ü aşan mezar taşları temizlenir, en yeniler kalır.

### Entegrasyon Testleri:
- `LocalSyncIntegrationTest` (127.0.0.1 üzerinde gerçek soket uçtan uca Wi-Fi senkronu): 7/7 başarılı.
- `CloudSourcesSyncTest` (Bulut senkron mantığı): 5/5 başarılı.

---

## 5. Cihaz / Emülatör Doğrulaması

> [!NOTE]
> Tek bir emülatör üzerinde iki cihaz senkronizasyonu aynı anda çalıştırılamayacağı için iki cihaz arası canlı senkron akışı **"doğrulanamadı, entegrasyon testiyle kapsandı"** olarak not edilmiştir.

Emülatörde (`127.0.0.1:5555`, Samsung SM-X710N / Android 12) yapılan doğrulamalar:
1. **Uygulama Açılışı:** Debug APK derlenip kuruldu; uygulama çökmeksizin açıldı (`pid 12036`).
2. **Kart Silme & Mezar Taşı Kaydı:** Ana sayfada "İzlemeye devam et" satırındaki kart üzerinden Menü tuşuyla onay penceresi açıldı, "Listeden kaldır" seçildi.
   - Kart satırdan hemen kaldırıldı, odak komşu karta geçti.
   - DataStore doğrudan `run-as com.kaan.play` ile kontrol edildi ve mezar taşının başarıyla yazıldığı teyit edildi:
     ```text
     /data/data/com.kaan.play/files/datastore/profile_sync.preferences_pb:
     sync_deleted_history -> [{"userId":"legacy-user","profileId":"3506fdba-aa30-4151-b88f-7d88d957313e","tmdbId":550,"type":"movie","deletedAt":1793732765990}]
     ```
3. **Kütüphane Geçmişi:** Kütüphane -> Geçmiş sekmesi açıldı, silinen kartın listeden kalktığı ve ekranın hatasız/çökmesiz çalıştığı görüldü.

---

## 6. Release APK Doğrulaması

- **Gradle Görevi:** `.\gradlew :app:assembleRelease`
- **Derleme Durumu:** `BUILD SUCCESSFUL in 2m 49s`
- **Dosya Adı:** `Play-1.2.4.apk`
- **Dosya Boyutu:** `48640618` bayt
- **SHA-256 Özeti:** `6afa54488fd8c3e39f5d2ed3ec72a72f3937b2c896d753e8528496d3027b0521`
