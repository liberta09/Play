# Doğrulama: Yedeğe Geçişte Dublaj Önceliği

- **KIRMIZI Test Çıktısı**: `SourceCoordinatorTest > fallbackWaitsForDubbedSourceWhenNextCandidateIsSubtitled FAILED: java.lang.AssertionError: expected:<[https://a/dub, https://c/dub]> but was:<[https://a/dub, https://b/sub]>`
- **Düzeltme**: `preferDub` açıkken `startFailed` anında sıradaki aday dublajlı değilse ve kaynak arama bitmediyse (`!finished`), `SourceCoordinator` `FALLBACK_DUB_WAIT_MS` (6 sn) boyunca `Resolving` fazında dublajlı kaynak bekler. Dublajlı kaynak gelince hemen açar; süre dolunca ya da arama bitince altyazılıya geçer. `preferDub=false` veya aday dublajlıysa beklemez.
- **Ek Testler**: (a) 6 sn zaman aşımında altyazılıya geçiş, (b) arama bitince (`finished=true`) anında altyazılıya geçiş, (c) `preferDub=false` iken beklemeden geçiş, (d) denenmiş dublajlı kaynağın tekrar aday olmaması.
- **Test Sonucu**: `.\gradlew testDebugUnitTest` tüm testler hatasız tamamlandı (SourceCoordinatorTest: 25/25 başarılı).
