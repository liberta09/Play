# Eklenti kayan alanları (ana sayfa üst sürgüsü) / Add-on sliders

Ana sayfada bir eklenti seçiliyken, eklentinin adı bilinen bir sitenin adıyla eşleşirse sayfanın en üstünde o siteye
özgü, geçişli bir kayan alan (sürgü) durur. Eşleşme yoksa hiçbir şey gösterilmez; ayar kalemi yoktur.

*EN: When a selected add-on's name matches a known site, Home shows a site-specific animated slider at the top. No match,
no slider; there is no setting.*

## Yapı

| Dosya | Görev |
|---|---|
| `ui/home/slider/PluginSliders.kt` | TEK kayıt: site adı eşleşmesi → stil. Sıra = öncelik. |
| `ui/home/slider/SliderKit.kt` | Ortak davranış: `SliderMath` (döngüsel uzaklık, kaydırma eşiği, dokunma yönü, sayaç), `SliderColors.table` (temadan masa/zemin), `rememberReduceMotion`, `AutoAdvance`, `Modifier.trackTouch`, `SliderDots`, `SliderCounter`, `SliderArrowButton`. |
| `ui/home/slider/<Stil>Carousel.kt` | Her stilin kendi dosyası (ör. `OpenMatchboxCarousel.kt`). |
| `ui/home/HomeScreen.kt` | Tek kanca: `PluginSliders.pick(...)` → `item(key = slider.key) { content(items, openItem) }`. |
| `res/values*/strings_sliders.xml` | Sürgü metinleri (ayrı dosya; `strings.xml` birleşmelerinde çakışmasın). |

İlk üç stil (`FanCarousel.kt`, `WarpCarousel.kt`, `MatchboxCarousel.kt`, `ui/home/` altında) kendi eşleşme işlevini taşır;
kayda bağlıdırlar.

## Kayıt

| Anahtar | Site (eklenti adı) | Stil |
|---|---|---|
| `shortizle_fan` | Shortİzle | yelpaze (`isShortizleProvider`: ad "shortizle" içerir) |
| `dramatr_warp` | DramaTR | 3B halka (`isDramaTrProvider`) |
| `dramavakti_matchbox` | DramaVakti | koyu kibrit kutusu (`isDramaVaktiProvider`) |
| `minidiziizle_open_matchbox` | MiniDiziİzle | açık kibrit kutusu |
| `anyshort_film_strip` | AnyShort | film şeridi |
| `directdrama_cinema_screen` | DirectDrama | sinema perdesi |
| `dramadizilerim_bookshelf` | DramaDizilerim | kitap rafı |
| `dramadunyam_globe` | DramaDünyam | dünya küresi |
| `dramacix_card_deck` | DramaCix | kart destesi |
| `fulldrama_cover_flow` | FullDrama | kapak akışı |
| `izlegec_snapshots` | İzleGeç | saçılmış anlık fotoğraflar |
| `liderdrama_spotlight` | LiderDrama | spot ışığı |
| `lidershort_vertical_feed` | LiderShort | dikey kaydırma |
| `narto_accordion` | Narto | akordeon şeritler |
| `verticalshorts_cube` | VerticalShorts | 3B küp |

Ad eşleşmesi: `PluginSliders.named("site")` eklenti adındaki Türkçe harfleri sadeleştirir, yalnız a–z bırakır ve site
adıyla **birebir** eşleşme ister (`MiniDiziİzle` → `minidiziizle`). Benzer adlar (`DramaVaktiMp4`, `MiniDiziİzle Plus`,
yalın `Cix`) eşleşmez. Eklenti adı değişirse `PluginSlidersTest`'teki listeyi ve kayıt satırını güncelle.

Seçim (`PluginSliders.pick`): kayıtta sırayla, kodu olan ve seçili bir eklentiyle eşleşen ilk giriş. Kartlar o eklentinin
ilk dolu satırının ilk 10 öğesi; eşleşen eklentinin dolu satırı yoksa sıradaki girişe geçilir; kart 3'ten azsa sürgü
gösterilmez.

## Yeni stil ekleme

1. Yeni dosya: `ui/home/slider/<Stil>Carousel.kt`, imza
   `@Composable internal fun XCarousel(items: List<CatalogItem>, onOpen: (CatalogItem) -> Unit, modifier: Modifier = Modifier)`.
   `items.size < 3` ise hiçbir şey çizme.
2. Kayda bir satır ekle: `PluginSlider("site_stil", "Site", "stil adı", named("site")) { i, o -> XCarousel(i, o) }`.
3. Ortak davranışı `SliderKit.kt`'ten al:
   - otomatik ilerleme `AutoAdvance(paused = touching) { go(+1) }` (dokunurken, uygulama arka plandayken ve sürgü ekran
     dışına kayınca durur);
   - `Modifier.trackTouch { touching = it }`;
   - kaydırma bitişi `SliderMath.settleTarget(start, now, velocity)` (öğe payının 1/3'ü ya da fiske);
   - `rememberReduceMotion()` true ise animasyon yerine `snapTo` (sade geçiş);
   - ortadaki öğeye dokununca `onOpen(item)` (detay), yan öğeye dokununca bir adım (`SliderMath.tapStep`);
   - alt gösterge `SliderDots` ya da `SliderCounter` + `SliderArrowButton`.
4. Metinler `res/values/strings_sliders.xml` ve `res/values-tr/strings_sliders.xml`'e.
5. Saf hesapları (geometri, eşik, renk türetme) bir `object` içinde tut ve
   `src/test/java/com/kaan/play/ui/home/slider/` altına birim testi yaz.

## Tema kuralı

Sürgüler altı temanın hepsinde (Koyu, Açık, AMOLED siyah, Okyanus mavisi, Mor gece, Yüksek kontrast) uyumlu olmalı:

- Zemin, nokta, düğme, sayaç ve sürgü dışı yazılar daima `MaterialTheme.colorScheme`'den gelir (etkin nokta `primary`,
  sönük `onSurfaceVariant`, halka ok `onSurface` / basılıyken `primary`, sayaç `onSurface` + `onSurfaceVariant`).
- Masa/zemin rengi `SliderColors.table(surface, surfaceVariant)`: açık temada krem, koyu temalarda temanın tonunu koruyan
  ılık koyu.
- Üst/alt saydam geçişler sabit koyu renge değil `colorScheme.background`'a doğru söner; açık temada koyu şerit kalmaz.
- Sabit renk yalnız stile özgü süs içindir (kibrit kılıfı renkleri, çekmece kâğıdı vb.); süs üstündeki yazı rengi
  kontrasta göre seçilir (`MatchboxGeometry.inkOn`). DramaVakti koyu kibrit kutusu koyu temalarda koyu ceviz masasını
  korur; Açık ve Yüksek kontrast temada masa `SliderColors.table`'dan türer.
- Tema kipi gerekiyorsa `LocalPlayThemeMode.current` (`ui/theme/Theme.kt`).

## Test

```powershell
.\gradlew testPhoneDebugUnitTest --tests "com.kaan.play.ui.home.*"
```

Cihazda: yalnız ilgili eklentiyi seç, ana sayfanın en üstünde sürgüyü gör; en az Koyu, Açık ve bir renkli temada bak;
otomatik ilerleme, kaydırma, oklar ve ortadakine dokununca detay; stilsiz bir eklentide üst alan olmadığını doğrula.
