# Ara ekranı tür çipleri — sonuç

Tarih: 2026-10-28 · Dal: `ozellik/eksikler-4` · Yayın yok, sürüm değişmedi. Kullanıcı kararı 2026-10-28: Ara'da Tümü / Filmler / Diziler çipleri (eski "filtre/çip yok" kuralı bu kararla değişti).

## Ne değişti

- `app/src/main/java/com/kaan/play/data/tmdb/TmdbService.kt` — `search(query, language, page, kind)`; `kind` = `multi` | `movie` | `tv` (`search/$kind`). Dil, `include_adult=false`, hata yönetimi aynı; api_key/URL loglanmıyor.
- `data/tmdb/TmdbRepository.kt` — `search(query, page, type: MediaType? = null)`: `null` → `search/multi` (kişiler ayıklanır); `Movie`/`Tv` → `search/movie` / `search/tv`. Bu uç noktaların sonuçlarında `media_type` yok; tür uç noktadan gelir (`toPage(defaultType = type)`). İstemcide süzme yok, sayfalama TMDB'den.
- `ui/search/SearchViewModel.kt` — `SearchFilter` (All/Movies/Series) durumu; `onFilterChange`: aynı sorgu yeni türle aranır, sayfalama sıfırlanır (`page=0`, ızgara başa döner), sorgu boşsa istek yok (seçilen tür sonra yazılan sorguya uygulanır); `loadMore` seçili türle; tür değişince gelen bayat sonuçlar yok sayılır. Test için arama işlevi enjekte edilebilir (ikincil kurucu `TmdbRepository` alır).
- `ui/search/SearchScreen.kt` — arama kutusunun altında mevcut `FilterPill` (yeni bileşen yazılmadı; seçili: turkuaz kenarlık + `primaryContainer` dolgu + turkuaz metin, seçili değil: nötr kenarlık), `FlowRow` ile büyük yazıda/dar ekranda sarar. Kutudan Aşağı → seçili çip (`focusProperties { down }`).
- `app/src/main/res/values/strings.xml`, `values-tr/strings.xml` — `search_filter_all|movies|series` (All/Movies/Series, Tümü/Filmler/Diziler).
- `CLAUDE.md` — Ekranlar > Ara açıklaması ve kural 5 notu.

## Testler

- `SearchTypeTest` (4, MockWebServer): Tümü → `search/multi` (kişi ayıklanır), Filmler → `search/movie` (query/page/`language=tr-TR`/`include_adult=false`, tür uç noktadan), Diziler → `search/tv`, boş sorguda hiç istek yok.
- `SearchViewModelTest` (4): varsayılan Tümü; çip değişince aynı sorgu doğru türle aranır ve sayfalama sıfırlanır (6 öğe/sayfa 2 → 3 öğe/sayfa 1), `loadMore` seçili türle; boş sorguda çip değişimi istek yapmaz; aynı çipe tekrar basmak istek yapmaz.
- `.\gradlew testDebugUnitTest`: **1064 test, 0 başarısız, 0 hata, 2 atlanan** (`StringResourcesTest` dahil). `assembleDebug`: başarılı.

## Cihaz (LDPlayer, 1600×900, Türkçe)

`adb install -r -t` → Success (kaldırma yok), **`lastUpdateTime=2026-10-28 12:32:48`**.

1. **Boş arama ekranı, üç çip:** `01-bos-ara-uc-cip.png` — "Tümü" seçili (turkuaz kenarlık + koyu dolgu + turkuaz metin), "Filmler"/"Diziler" nötr, "Aramak için bir şey yaz." metni aynı. ✔
2. **Film adı + Filmler:** `02-film-filmler-cipi.png` — "inception" → "Filmler" (D-pad Sağ + Tamam ile seçildi); önceki "Tümü" sonuçlarındaki dizi ("The Cruise · Dizi") gitti, hepsi "Film". ✔
3. **Dizi adı + Diziler:** `03-dizi-diziler-cipi.png` — "breaking bad" → "Diziler"; Breaking Bad dahil tüm sonuçlar "Dizi". ✔
4. **D-pad ile çiplere odak:** `04-dpad-cipe-odak.png` — arama kutusundan Aşağı → "Tümü" çipi odak halkasıyla; `04b-filmler-odakta.png` — Sağ ile "Filmler"; `05-cipten-izgaraya.png` — çipten Aşağı → ızgaradaki ilk sonuç (odak halkalı). ✔

**Cihazda bulunan ve düzeltilen hata:** ilk kurulumda kutudan Aşağı, çiplerin üstünden atlayıp doğrudan ızgaradaki en yakın karta gidiyordu (odak araması çipleri seçmiyordu). Kutuya `focusProperties { down = seçili çip }` eklendi (commit "fix(search): kutudan Asagi ok tur ciplerine gitsin"), yukarıdaki dört kanıt bu düzeltmeden sonraki derlemeyle alındı.

Sınırlar: büyük yazı ölçeğinde çiplerin sarmasını cihazda ayrıca görüntülemedim (`FlowRow` ile yapısal olarak sağlanıyor; üç çip 1600 dp genişlikte tek satır). Kumanda yolu LDPlayer'da klavye tuşlarıyla denendi (gerçek TV kumandası değil). LDPlayer'ın `adb input text` yazması bu uygulamada güvenilmez olduğundan metinler tek tek tuş olaylarıyla yazıldı.
