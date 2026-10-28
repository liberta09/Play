# Bulut senkronizasyonunda kaynaklar — sonuç

Tarih: 2026-10-28 · Dal: `ozellik/eksikler-4` · Yayın yok, sürüm değişmedi. Kullanıcı kararı 2026-10-28: aynı hesapla giriş yapan cihazlar eklentilerini de paylaşır; Wi-Fi senkronizasyonu kalır.

## Ne değişti

- `app/src/main/java/com/kaan/play/data/sync/SupabaseCloudSync.kt` — yükü artık Wi-Fi ile ortak `LocalSyncData.export()` kurar (profiller PIN'siz, geçmiş/favoriler Room'dan, tüm kaynaklar + durumları); birleştirme `LocalSyncMerge.plan` + `LocalSyncData.apply`; eksik kaynaklar `pendingSources`'a düşer, `confirmSources` ile `LocalSyncData.install` (mevcut `SourceInstaller`/`WebSiteRepository`) kurar; onay bekleyenler buluta geri yazılır; yazımda `return=representation` ile sunucunun `updated_at`'i yerel damga olarak saklanır.
  - **Önceki yolda bir sızıntı vardı:** `profiles.exportTransfer()` profil listesini `pinHash` ve `syncCode` ile birlikte yüklüyordu. Bu sürüm yazdığı anda buluttaki yük PIN'siz hale gelir; eski sürümdeki bir cihaz eşitlerse yeniden yazabilir.
- `data/sync/local/LocalSyncMerge.kt` — `snapshot` (ortak yük; PIN/kod çıkarılır), `withPending`, `remoteIsNewer`.
- `data/sync/local/CloudStamp.kt` (yeni) — `timestamptz` → mikrosaniye (minSdk 24, `java.time` yok).
- `data/sync/local/LocalSyncData.kt` — `export` ortak `snapshot` üzerinden.
- `AppContainer.kt` — `localSyncData` tek örnek (Wi-Fi denetleyicisi ve bulut paylaşır).
- `ui/settings/PendingSourcesList.kt` (yeni, Wi-Fi kartından taşındı) — tek "Şunlar eklensin mi?" bileşeni; `WifiSyncCard.kt` ve `ProfileChooserScreen.kt` (`AccountAccessPanel`) bunu kullanır. Hesap sekmesi kayar. Panel açıklaması string resource `cloud_sync_scope` (iki dilde). Ayarlar'a yeni kalem yok.
- `CLAUDE.md` ("Bulut hesabı senkronizasyonu: kaynaklar da"), `docs/yol-haritasi.md` (karar satırı).
- `supabase_schema.sql` değişmedi: `payload` jsonb, `updated_at` tetikleyicisi ve SELECT politikası yeterli. **Supabase'de yapılacak bir şey yok.**

Ortak kaynakta fark kuralı: bulut kaydının `updated_at`'i bu cihazın son yazdığı `updated_at`'ten yeniyse bulut kazanır, değilse yerel; bulut damgasızsa yerel korunur; cihaz hiç yazmamışsa damgalı bulut kazanır. Damga kaynak başına değil kayıt başınadır (iki cihaz arasında yazılmamış yerel değişiklik, başka cihazın daha yeni yazımına yenilir).

Bilinen sınır: bir cihazda silinen kaynak bulutta kalır (onay bekleyen kaynakları korumak için birleşim yazılır) ve o cihaza sonraki eşitlemede yeniden sorulur.

## Testler

`CloudSourcesSyncTest` (5): yükte PIN özeti ve senkron kodu yok; yük modelinde yalnızca adres + durum alanları var, eklenti kodu/özet/dosya yok, tüm kaynak türleri + ayar + kapalı katalog taşınıyor; eksik kaynak onaya düşüyor ve buluttan silinmiyor; ortak kaynakta daha yeni kazanıyor (damgasız bulut → yerel); `CloudStamp` çözümü.
`.\gradlew testDebugUnitTest`: **1056 test, 0 başarısız, 0 hata, 2 atlanan** (StringResourcesTest dahil). `assembleDebug`: başarılı.

## Cihaz (LDPlayer)

- `adb install -r -t` → Success, `lastUpdateTime=2026-10-28 09:54:46`.
- Bulut Hesabı sekmesi: **oturum açık değil** (`01-bulut-hesabi.png`). Bu Supabase projesi için test hesabı/şifresi elimde olmadığından giriş, yükle/indir akışı, Ayarlar > Eklentiler ve onay listesi görüntüleri **henüz alınmadı**. Giriş bilgisi gelince bu bölüm güncellenecek.
