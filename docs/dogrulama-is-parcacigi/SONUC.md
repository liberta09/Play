# Do?rulama Raporu: Oynat?c? ?? Par?ac??? D?zeltmesi (fix-yanlis-is-parcacigi)

## 1. K?k Neden Analizi (Neden)
1.1.10 s?r?m? sonras?nda kullan?c?lardan gelen "film ba?lad?ktan sonra kasma, donma, uygulamadan atma" ve Mi Box cihazlar?nda "geri d?n?nce program reset at?yor, yeniden kaynak ar?yor" ?ikayetlerinin kayna?? incelendi:
- `SourceCoordinator.handleStreamResult` i?inde birle?tirme (debounce) i?i `pendingOnChangeJob = scope.launch(rankingDispatcher) { ... onChange() }` ?eklinde do?rudan `Dispatchers.Default` (rankingDispatcher) ?zerinde ba?lat?l?yordu.
- Bu coroutine i?indeki `onChange()` ?a?r?s? `begin()` ve `load()` fonksiyonlar?n? tetikleyerek ExoPlayer oynat?c?s?na ve Compose `mutableStateOf` de?i?kenlerine arka plan i? par?ac???ndan (`DefaultDispatcher-worker-*`) eri?iyordu.
- ExoPlayer yaln?zca olu?turuldu?u ana i? par?ac???ndan (Main/UI thread) eri?ilmesini ?art ko?tu?u i?in bu durum "Player is accessed on the wrong thread" hatas?yla ??kmelere, ANR'lere ve Compose state tutars?zl?klar?na (reset/yeniden ba?latma) yol a??yordu.
- Bu hata `25650fc` ("sat?r sonu temizli?i") commit'i s?ras?nda olu?mu?tu; ?ncesinde `c606017` commit'inde d?? launch ana scope'un kendi dispatcher'?nda (Main) ko?uyor ve yaln?zca s?ralama `withContext(rankingDispatcher)` i?inde yap?l?yordu.

## 2. De?i?en Dosyalar
- `app/src/main/java/com/kaan/play/ui/player/SourceCoordinator.kt`:
  - `scope.launch(rankingDispatcher)` ifadesi `scope.launch` olarak d?zeltildi.
  - Sadece a??r `SourceRanking.recommended(currentStreams, currentCtx)` hesaplamas? `withContext(rankingDispatcher)` i?inde tutuldu.
  - `currentStreams` ve `currentCtx` yerel de?i?kenlere al?nd?; hesaplama bitti?inde `streams === currentStreams` kontrol?yle eski/bayat s?ralama sonucunun ?nbelle?e (`rankedCache`) yaz?lmas? engellendi.
  - `rankedCache` `@Volatile` yap?ld?.
  - `matchesPreference` yard?mc? fonksiyonu c606017'deki gibi tan?mland?.
  - `onChange()`, `rankedCache`, saya? atamas?, `lastOnChangeClock` ve t?m Compose/ExoPlayer state yaz?mlar? ana i? par?ac???nda garantilendi.
- `app/src/test/java/com/kaan/play/ui/player/SourceCoordinatorTest.kt`:
  - 200 h?zl? ard???k sonu?la oynatma ba?lat?ld???nda `load` lambdas?n?n her zaman scope'un ana i? par?ac???nda ?a?r?ld???n? ve asla `rankingDispatcher` thread'inde ?a?r?lmad???n? do?rulayan birim testi eklendi (`loadIsAlwaysCalledOnScopeDispatcherNeverOnRankingDispatcher`).

## 3. B?l?m 1 Madde 2 ?ncelemesi (`c606017` ile `HEAD` Kar??la?t?rmas?)
`git diff c606017 HEAD -- app/src/main/java/com/kaan/play/ui/player/SourceCoordinator.kt app/src/main/java/com/kaan/play/ui/player/AutoNext.kt` incelendi:
- `AutoNext.kt`: Sat?r sonu temizli?i s?ras?nda hi?bir davran?? veya kod de?i?ikli?i olmam??t?r (yaln?zca bo? sat?rlar temizlenmi?tir).
- `SourceCoordinator.kt`:
  1. `handleStreamResult` i?inde `scope.launch` yerine `scope.launch(rankingDispatcher)` yaz?lm??t? (bu durum ana i? par?ac??? yerine arka planda ?al??mas?na ve ??kmeye yol a?t?; c606017 davran???na d?nd?r?ld?).
  2. `val currentStreams = streams` yerel de?i?keni ve `streams === currentStreams` kontrol? kald?r?lm??t?; bu nedenle arka planda s?ralan?rken yeni kaynak gelirse eski listenin ?nbelle?e yaz?lma riski olu?mu?tu (c606017'deki g?venli kontrol geri getirildi).
  3. `canStartImmediately` i?indeki `matchesPreference(stream, preference)` ?a?r?s? sat?r i?ine g?m?lm??t? (davran?? ayn?yd?, c606017 d?zenine d?nd?r?ld?).
  4. ?nbellek de?i?keni `@Volatile` i?aret?isi ve bellek g?r?n?rl??? g?vencesi geri kazand?r?ld?.

## 4. T?m Projede Benzer launch Kullan?mlar? Taramas?
`grep -rn "launch(.*Dispatchers\|launch(rankingDispatcher\|launch(io\|launch(computation" app/src/main/java` ?al??t?r?ld?:
- Yaln?zca `DownloadCenter.kt` i?inde `scope.launch(Dispatchers.Main)` kullan?ld??? g?r?ld? (do?ru kullan?m).
- `SourceCoordinator.kt:345` haricinde hi?bir yerde arka plan dispatcher'? ile oynat?c? veya UI state'ine dokunan uygunsuz bir launch bulunmad?.

## 5. Birim Testleri
- Toplam Test Say?s?: **988 test**
- Ba?ar?s?z (Failures): **0**
- Hata (Errors): **0**
- Atlanan (Skipped): **2** (sahte SubDL endpoint testleri)
- Yeni eklenen i? par?ac??? testi (`loadIsAlwaysCalledOnScopeDispatcherNeverOnRankingDispatcher`) ba?ar?yla ge?ti (0.019 sn).

## 6. ?nce / Sonra ve Cihaz Testi (LDPlayer, T?m Sa?lay?c?lar A??k)
- **?nceki S?r?m (1.1.12):** S?ralama gecikmeli/birle?tirilmi? sonu?larda arka plan dispatcher'?nda (`Dispatchers.Default`) `onChange() -> begin() -> load()` ?a?r?ld??? i?in ExoPlayer wrong thread riski ve bellek yar?? durumu mevcuttu.
- **D?zeltilmi? S?r?m (1.1.12 + fix):**
  - Release imzal? APK derlendi (Sertifika SHA-256: `5c7cd2bd91946586d125e59a848544f7db10b42ab0c0d3d6dd30c5b8c4e9f44f`).
  - Uygulama silinmeden `pm install -r -t /data/local/tmp/play.apk` ile g?ncellendi.
  - ?? i?erik s?rayla test edildi:

| ??erik | A??l?? Y?ntemi | ?lk Kare | 60 sn Ak?c? Oynatma | Geri ile D?n?? (Uygulama Reset Yok) | Ekran G?r?nt?s? | Durum |
|---|---|---|---|---|---|---|
| (a) ?r?mcek-Adam: Yepyeni Bir G?n | Ana sayfa raf?ndan | ? 4.1 sn (Dragon, 145.0 dk do?ruland?) | ? 65 sn kesintisiz | ? Ana sayfaya d?n?ld?, PID 4947 korundu | `orumcek_adam.png` (738 KB, 1600x900) | ? GE?T? |
| (b) Joker (2019) | Ara ekran?ndan | ? 1.5 sn (SelcukFlix, 121.8 dk do?ruland?) | ? 60 sn kesintisiz | ? Joker Detay ekran?na d?n?ld?, PID 4947 korundu | `joker.png` (32 KB, 1600x900) | ? GE?T? |
| (c) Criminal Minds S01E21 | Ara -> Detay -> B?l?m 21 | ? 1.2 sn (Ace, 43.7 dk do?ruland?) | ? 60 sn kesintisiz | ? Detay ekran?na d?n?ld?, PID 4947 korundu | `criminal_minds.png` (1.06 MB, 1600x900) | ? GE?T? |

## 7. Sistem G?nl??? (Logcat) ve ANR Denetimi
- `adb logcat -d | grep -E "FATAL EXCEPTION|wrong thread|ANR in com.kaan.play"`: **BO?** (0 e?le?me).
- `adb shell dumpsys activity anr`: `com.kaan.play` i?in hi?bir ANR kayd? yok.
- Uygulama PID'si test boyunca `4947` olarak sabit kald?, ??kme veya yeniden ba?lama ya?anmad?.
