# Neon görünüm ve kumanda taşıması: neyi aldık, neyi almadık

Dal: `ozellik/neon-dpad-1.2.1` (taban: `d247b96`, v1.2.0). Kaynak: `517469a` yalnızca `git show` ile okundu; merge/cherry-pick yok.
Kural: `data/**` değişmedi, yalnızca `ui/**` ve iki satır string kaynağı.

## Alınanlar (yalnızca görünüm + kumanda)

| Dosya | Ne alındı |
|---|---|
| `ui/theme/Color.kt` | Koyu tema renkleri (`#06070B`, `#0A0D14`, `#101420`, `#0E1A2B`, `#1A2838`) |
| `ui/tv/PlayFocus.kt` | `tvFocusableScaleGlow`, `categoryHeaderFocusable`, yeni `focusRing` (büyüme + turkuaz parlama), `isTelevisionDevice`, `DismissImeOnBack`, `dismissImeOnDoneOrBack`, `rememberFocusMemory(forceOnTouch, resetKey)`. **`RowControls / rowControlsHost / rowControl / rememberRowControls` korundu** (517469a'da silinmişti). |
| `ui/components/PlayButtons.kt` | Düğmelerde `focusRing(..., scale = 1.10f)`. Bizdeki özel köşe/iç boşluklu `Button` aşırı yüklemesi korundu. |
| `ui/components/PlayCards.kt`, `SettingsRow.kt`, `ScreenHeader.kt` | Çerçeve (`BorderStroke`) kaldırıldı, `focusRing` ölçeği; `ScreenHeader`'a `backFocusRequester` / `backModifier`. |
| `ui/components/PosterCard.kt` | Odakta `tvFocusableScaleGlow` + `zIndex`. `onLongClick` **alınmadı** (özellik). |
| `ui/settings/ProfileAvatar.kt` | Çizimle avatar kataloğu (eski anahtarlar `normalizeAvatarKey` ile eşlenir). |
| `ui/nav/PlayRoot.kt` | Yüzen hap **üst menü** (`PlayTopBar`): Ana sayfa, Ara, TV, Kütüphane, Ayarlar, profil. Alt menü ve yan menü kaldırıldı. Sekme başına `contentFocusRequester`, ortak `topBarFocusRequester`. Alt sayfalarda (detay, kategori, `settings/*`) üst menü gizli. |
| `ui/home/HomeScreen.kt`, `HeroCarousel.kt` | Kategori başlığı odak parlaması, kapakta YUKARI = üst menü, ilk "devam et" / ilk trend kartında `contentFocusRequester`, hap düğmede çerçeve yok, radyal arka plan. |
| `ui/tv/TvScreen.kt`, `ui/search/SearchScreen.kt`, `ui/library/LibraryScreen.kt`, `ui/settings/SettingsScreen.kt` | Yalnızca `contentFocusRequester` / `topBarFocusRequester` bağlantıları. |
| `ui/detail/DetailScreen.kt` | Geri düğmesi ve bölüm kartında çerçeve kaldırıldı. |
| `ui/settings/ProfileChooserScreen.kt`, `ProfileSetupScreen.kt` | `ui/components` düğmeleri (odak halkalı), sekme/profil kartı `focusRing`, ilk profil başlangıç odağı, profil seçicide koyu bordo arka plan. |
| `res/values*/strings.xml` | `tab_profile` (İngilizce "Profile", Türkçe "Profil"), üst menüdeki profil simgesinin erişilebilirlik adı. |

## Bilerek alınmayanlar

- **`data/**` tamamı** (cloudstream, match, stream, sync, provider): kaynak bulma d247b96'daki gibi.
- `ContinueSortControl.kt` ve sıralama (`ContinueSortMode`): özellik, `data/library` gerektirir.
- Geçmiş eylemleri (uzun basınca favori / izlendi / sil), `PosterCard.onLongClick`.
- Çocuk profili filtreleri, PIN diyaloğu ve profil kapısını kaldıran açılış mantığı (PlayRoot'ta 517469a'nın profil akışı alınmadı; bizdeki `profileGate` akışı olduğu gibi). Bu akışta gömülü Türkçe metinler vardı.
- `SkipController`, `WifiSyncCard`, `PendingSourcesList`, Wi-Fi senkron: dokunulmadı (517469a'da silinmişti).
- `CrashReporter.kt` (gömülü bot belirteci), `SpeedTestScreen`, `SyncScreen`, `ProfileScreen` yeniden yazımları.
- `RepoManagerCatalogScreen.kt`, `CsRepos.kt` (alt repo kataloğu), `PluginRepos.kt`, `AddonsScreen.kt`, `AddonDetailScreen.kt`, `WebSites.kt`: 517469a bunlarda `rowControls`'u silip odağı yeniden kurmuş ve eski sürüme dönmüştü. Bizdeki sürümler korundu; yeni `focusRing` sayesinde satırlar, anahtarlar ve çöp kutuları zaten büyüme + parlama alır.
- `ProviderSelectorSheet.kt`, `ProviderDetailScreen.kt`, `SourcesSheet.kt`, `PlayerActivity` ve diğer oynatıcı dosyaları: yalnızca mantık farkı, görsel fark yok.
- `LibraryScreen` içindeki "Geçmiş" bölümü: 517469a kaldırmıştı, bizde kaldı.
- `versionCode` / `versionName`, `build.gradle`: değişmedi.

## Ayarlar kalemleri

517469a'nın ek kalemleri (Hız testi, Poster URL, Profil seçimi, Son profili hatırla, Bulut hesabı, Hakkında) **zaten d247b96'da var**; hiçbiri eklenmedi ya da silinmedi. Ayarlar'a yeni kalem yok.

## Bilinen açıklar

- `RingNode` (`PlayIndication`) artık odakta halka **çizmez** (517469a'daki gibi). Odak halkası yalnızca `focusRing` / `tvFocusableScaleGlow` kullanan öğelerde görünür. `focusRing` almayan, yalnızca `clickable` olan bir öğe kumandayla odaklanınca ripple dışında belirti vermez; cihaz taramasında bulunanlar buraya eklenecek.
- Profil ekranlarındaki gömülü Türkçe metinler (önceden vardı) bu işte çevrilmedi.
