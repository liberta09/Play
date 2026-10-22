# Nuvio eklenti deposu (JS kazıyıcı) formatı — araştırma notu

Tarih: 2026-10-12. Amaç: Faz 2'de (Nuvio plugin'leri) formatı tahminle değil, gerçek dosyalardan ve resmî uygulamanın çalışma ortamı kaynağından bilmek. Bu belge **yalnızca teknik biçimi** anlatır; eklentilerin hangi sitelere bağlandığı konusunda bir değerlendirme yapılmaz. Uygulama koduna dokunulmadı.

Etiketler: **[DOĞRULANDI]** gerçek dosya veya resmî uygulama kaynağından doğrudan okundu; **[VARSAYIM]** dosyalardan çıkarılan ya da doğrulanamayan.

---

## 0. Kaynaklar ve erişilemeyenler

Okunanlar (hepsi bu oturumda indirildi/okundu):

| Kaynak | Ne için |
|---|---|
| `eclipsia/nuvio-plugin` deposu (Codeberg raw erişimi çok kararsızdı; **gitea.com/eclipsia/nuvio-plugin** aynası ile tamamlandı). `manifest.json` + 43 provider dosyasının hepsi | Depo manifest'i ve provider örnekleri. Lisans: **GPL-3.0** (depodaki `LICENSE`, README de aynısını söylüyor) |
| `Wekmed/nuvio` (16 provider) ve `liberta09/CloudStreamRepoManager/nuvio/manifest.json` ("mooncrown", 32 giriş), `manitux-app/nuvio-plugins` (6 giriş) manifest'leri | Farklı yazarların manifest ve provider alışkanlıkları |
| **Resmî uygulama kaynağı** (GPL-3.0): `NuvioMedia/NuvioTV` (`app/src/full/.../core/plugin/PluginRuntime.kt`, `PluginManager.kt`, `domain/model/Plugin.kt` …) ve `NuvioMedia/NuvioMobile` (`composeApp/src/fullCommonMain/.../plugins/runtime/js/JsBindings.kt`, `host/HostFunctions.kt`, `plugins/PluginModels.kt`, `PluginSettingsDialog.kt` …) | Çalışma ortamının **gerçek tanımı** (hangi global'ler, imza, sonuç ayrıştırma, zaman aşımları) |
| `liberta09/CloudStreamRepoManager` içindeki `central_repos.json` | "nuvio" türü 17 girişin hepsi canlı indirilip sınıflandırıldı (14 Stremio addon, 3 eklenti deposu) |
| `dokar3/quickjs-kt` (GitHub API: lisans, sürüm, minSdk, README) | Çalıştırma önerisi |

**Erişilemeyenler / doğrulanamayanlar**
- `https://nuvio-wiki.duckdns.org/integrations/` ve `https://eclipsia.codeberg.page/` : bağlantı reddedildi (ECONNREFUSED). Arama sonuçlarında göründükleri için varlıkları biliniyor, **içerikleri okunamadı**.
- `codeberg.org` raw uçları aralıklı 504/zaman aşımı verdi; aynı depo gitea.com aynasından alındı (aynı `manifest.json` boyutu 19822 bayt, aynı dosya adları).
- "Resmî bir eklenti yazım belgesi" **bulunamadı**: NuvioMedia organizasyonunda belge/wiki deposu yok (11 depodan 10'u listelendi; hiçbiri belge değil). Aşağıdaki sözleşme, uygulamanın kendi çalışma ortamı kodundan çıkarıldı; bu da resmî belgeden daha güvenilir ama **sürümden sürüme değişebilir**.
- Bir topluluk deposunda ("1Anime/nuvio-src") `docs/API_NOTES.md` olduğu belirtiliyordu; açılamadı, kullanılmadı. (Aynı depo kendi README'sinde "çalışmıyor/WIP" diyor.)
- Mobil uygulamada `WasmBridge.kt` dosyası var ama içeriği okunmadı; taranan 59 eklentinin hiçbiri `WebAssembly` kullanmıyor.

Önemli lisans notu: Nuvio uygulamaları **GPL-3.0**. Çalışma ortamının kaynak kodunu (polyfill'ler, köprüler) **kopyalamayız**; yalnızca davranış sözleşmesi (arayüz) bilgisini kullanıp kendi gerçeklememizi sıfırdan yazarız (CLAUDE.md "kopyalama yok" kuralı).

---

## 1. Depo manifest'i (`manifest.json`)

**[DOĞRULANDI]** — Eclipsia manifest'inin başı:

```json
{
  "name": "Eclipsia",
  "version": "11.5.0",
  "scrapers": [
    {
      "id": "brellor",
      "name": "Brellor.",
      "description": "Internet Archive • Classic/Vintage",
      "version": "1.1.0",
      "author": "Eclipsia",
      "supportedTypes": ["movie", "tv"],
      "filename": "providers/brellor.js",
      "enabled": true,
      "formats": ["m3u8"],
      "logo": "",
      "contentLanguage": ["en"]
    }
  ]
}
```
(biçim kısaltıldı; asıl dosyada diziler satır satır yazılı.)

### 1.1 Üst düzey alanlar
Resmî uygulama modeli (`PluginManifest`, hem TV hem Mobil): `name` (zorunlu), `version` (zorunlu), `description?`, `author?`, `scrapers` (liste). Gerçek manifest'lerde **fazladan** üst alanlar da var (ör. manitux manifest'inde `id`, `logo`); uygulama bilinmeyenleri yok sayar.
- TV'de `scrapers` boş olamaz (Moshi non-null); **Mobil'de varsayılan `emptyList()`** → Stremio addon manifest'i Mobil'de "boş eklenti deposu" gibi ayrışabilir (bkz. §5).

### 1.2 Kazıyıcı girişi (`scrapers[]`) alanları
Eclipsia'nın 43 girişindeki alan sayımı ve resmî modelle (`ScraperManifestInfo` / `PluginManifestScraper`) birleşik tablo:

| Alan | Tür | Zorunlu? | Not |
|---|---|---|---|
| `id` | string | evet | Depo içinde benzersiz. (mooncrown manifest'inde `mooncrown__wekmed__filmmodu` gibi önek içeren kimlikler var.) |
| `name` | string | evet | Gösterim adı. |
| `version` | string | evet | Semver benzeri; güncelleme karşılaştırması için. |
| `filename` | string | evet | **Göreli** (`providers/x.js`, manifest'in bulunduğu dizine göre) **ya da mutlak URL** (`https://raw.githubusercontent.com/.../x.js`; `startsWith("http")` ise olduğu gibi kullanılıyor, mooncrown böyle) |
| `description` | string | hayır | Eclipsia'da hep var; manitux'ta yok. |
| `author` | string | hayır (modelde yok) | Eclipsia/Wekmed'de var. |
| `supportedTypes` | string[] | hayır, varsayılan `["movie","tv"]` | Değerler: `movie`, `tv`, ve **`series`** (Eclipsia'da 2 girişte). Uygulama `series/show/other` → `tv` olarak normalleştirir. |
| `enabled` | bool | hayır, varsayılan `true` | Manifest'in varsayılanı; kullanıcı ayrıca açıp kapatır. |
| `logo` | string | hayır | Çoğu boş `""`; manitux'ta `%size%` yer tutuculu favicon URL'si (iç içe şablon, yorumlayan yok varsayıldı). |
| `contentLanguage` | string[] | hayır | Gerçek değerler: `en,bn,hi,ja,ko,zh,te,ta,tr,it,jp,es,pt,ar,kr,cs,fr,de,hu,pl` (**tutarsız**: `ja` ve `jp`, `ko` ve `kr`). Serbest etiket olarak ele alınmalı. |
| `formats` | string[] | hayır | `m3u8, mp4, mkv, mpd`. Ek: modelde `supportedFormats` eşanlamlısı var. |
| `hasSettings` | bool | hayır, varsayılan `false` | Eclipsia'da 9 giriş `true`. Provider'ın `onSettings` dışa aktardığını söyler (bkz. §2.4). |
| `disabledPlatforms` / `supportedPlatforms` | string[] | hayır | Örn. `["ios"]` (Eclipsia'da 1 giriş). Modelde var; mooncrown'da çoğu `[]`. |
| `limited` | bool | hayır | Modelde var, mooncrown'da yaygın; anlamı **[VARSAYIM]** (kısıtlı/sınırlı kaynak işareti). |
| `supportsExternalPlayer` | bool | hayır | Modelde var, mooncrown'da 4 giriş; anlamı **[VARSAYIM]**. |

**Gerçek manifest örnekleri arasında fark:** manitux girişleri yalnızca `id, name, logo, version, filename, supportedTypes, contentLanguage` içerir (`enabled`, `description` yok) → ayrıştırıcı tüm isteğe bağlı alanları hoşgörüyle okumalı.

### 1.3 Nasıl indirilir **[DOĞRULANDI, TV `PluginManager`]**
- Kullanıcı URL girer; `/manifest.json` yoksa eklenir (`canonicalizeManifestUrl`), sondaki `/` atılır.
- Provider kodu: `filename` mutlaksa o URL, değilse `{manifest'in dizini}/{filename}`. Önce `HEAD` ile `Content-Length` bakılır (üst sınır **5 MB**), sonra GET. Hata veren giriş atlanır, diğerleri sürer.
- Kod, uygulamada saklanır; `id` uygulama içinde `{depoId}:{scraperId}` olur.
- Mobil: depo manifest'i her **6 saatte** yenilenir (`PLUGIN_REPOSITORY_REFRESH_INTERVAL_MS`).
- Depo bir kısayol kodu ("short code") da olabiliyor (TV'de `isShortCode`/`resolveShortCode`); kullanımı **[VARSAYIM]**, ayrıntısı okunmadı.

### 1.4 Aynı depodaki ek manifest'ler
`eclipsia/nuvio-plugin` içinde kökte `manifest.json` dışında `english/manifest.json` ve `stable/manifest.json` (ikisi 19126 bayt, birbirinin aynı boyutta) var: aynı şemada başka "kanal" manifest'leri **[VARSAYIM]**. İçerikleri karşılaştırılmadı.

---

## 2. Bir provider `.js` dosyasının yapısı

### 2.1 Dışa aktarma ve çağrı imzası **[DOĞRULANDI]**
59 dosyanın (Eclipsia 43 + Wekmed 16) **hepsi** `module.exports` kullanıyor; hiçbiri ES modülü (`import/export`) kullanmıyor.

```js
async function getStreams(tmdbId, mediaType, season, episode) { ... return [ {...}, ... ]; }
module.exports = { getStreams };            // ayarlı olanlarda:  { getStreams, onSettings }
```

Çalışma ortamının çağrı kodu (resmî, TV `getStaticCallCode`):
- `var getStreams = module.exports.getStreams || globalThis.getStreams;` → `module.exports` ya da global `getStreams` kabul edilir.
- `await getStreams(args.tmdbId, args.mediaType, args.season, args.episode)`; sonuç `JSON.stringify(result || [])` ile geri alınır. Fonksiyon bulunamazsa ya da hata atarsa **boş liste** döner (hata yutulur).

Parametreler:
| Parametre | Değer |
|---|---|
| `tmdbId` | **string** (TMDB sayısal kimliği, örn. `"27205"`). Uygulama `tmdb:` önekini/`:sezon:bölüm` sonekini temizler (`pluginContentId`). Provider'lar `parseInt(tmdbId, 10)` yapıyor. |
| `mediaType` | `"movie"` ya da `"tv"`. **İstisna:** manifest'te `supportedTypes` içinde `"series"` varsa ve sezon+bölüm verilmişse `"series"` gönderilir (`jsPluginMediaType`). Provider'lar bu yüzden `mediaType === "tv"` ve `"series"` ikisini de karşılamalı; biz de aynı kuralı uygularız. |
| `season`, `episode` | Dizide tam sayı; filmde `null` (TV sürümü) ya da `undefined` (Mobil sürümü `null`'ı `undefined`'a çevirir). Provider'lar `season == null` ile ikisini de yakalıyor. Anime tracker kimliklerinde (`kitsu/mal/anilist/anidb`) `season = null`, `episode` = mutlak bölüm no. (TV `resolvePluginSeasonEpisode`) — bizim kapsamımızda (TMDB kimliği) gerekmez. |

Çıktı bir **dizidir**; `await` edilebilir bir Promise döner. Provider'ların çoğu `try/catch` ile `[]` döner.

### 2.2 Dönüş (akış) nesnesi **[DOĞRULANDI]**
Uygulamanın ayrıştırıcısı (`parseJsonResults`) şu alanları okur:

| Alan | Okunuşu |
|---|---|
| `url` | **Zorunlu**: dize (ya da `{url: "..."}` nesnesi). Boş veya `[object` içeriyorsa kayıt atılır. `http(s)` dışı doğrulama yok (HLS `.m3u8`, `.mp4`, `.mkv` hepsi geliyor). |
| `title` | Yoksa `name`, o da yoksa `"Unknown"`. |
| `name` | Satırın kaynak adı (uygulama gösterirken `name`'i, kalite yoksa ` - {kalite}` ile birleştirir). |
| `quality` | Serbest dize. Gerçek değerler (sayım): `"Auto"` (en yaygın, ~25), `"1080p"`, `"720p"`, `"2160p"`, `"1080P"` (büyük harf), `"1080p • MKV"`, `"1080p • HLS"`. **Normalleştirme şart.** |
| `size` | İsteğe bağlı dize (`"2.1 GB"`; 7 dosyada). |
| `language` | İsteğe bağlı dize (11 dosyada). |
| `provider`, `type`, `seeders`, `peers`, `infoHash` | Okunuyor; gerçek dosyalarda `provider` 1, `type` 18 (anlamı **[VARSAYIM]**: çoğunlukla `"hls"`/`"mp4"` gibi biçim etiketi), `infoHash`/`seeders` **0** (torrent yok). |
| `headers` | `{ "User-Agent": ..., "Referer": ..., ... }` — **58/59 dosya** döndürüyor. Yalnızca dize→dize çiftleri alınır. Uygulama bunu Stremio'daki `behaviorHints.proxyHeaders.request` gibi **oynatma isteklerine** ekler. Bizim `PlayableStream.headers` ile birebir örtüşür. |
| `subtitles` | İsteğe bağlı dizi: `{ url, language, name?, headers?, id? }` (6 dosya). `language` yoksa `lang` da kabul edilir; ikisi de yoksa `"Unknown"`. |
| `behaviorHints` | 2 dosya kendi içinde kullanıyor (svartok: `proxyHeaders.request`'i `headers`'a katıyor); ana ayrıştırıcı **okumaz**. |

**Sıralama hilesi [DOĞRULANDI]:** `evyrith.js` (ve muhtemelen başkaları) `name` başına görünmez karakterlerden (`U+FEFF`/`U+200B`) oluşan bir "ters sıralama etiketi" ekliyor (`invertedSortTag`). Amaç, uygulamanın ada göre sıralamasında kendi sırasını korumak. Bizde bu karakterleri **temizleyip** sıralamayı kendimiz yapmalıyız (aksi halde ad görünmez karakterle başlar).

### 2.3 Kapsam
- Tek dosya, paketlenmiş değil, derleme yok; dosya boyutu 3–20 KB (en büyük örnek 20 KB).
- 44/59 dosya kendi TMDB isteğini atar: `fetch(\`https://api.themoviedb.org/3/${endpoint}/${tmdbId}?api_key=${TMDB_API_KEY}\`)`. Yani **TMDB anahtarını global `TMDB_API_KEY`'den okur** (bkz. §3.3).

### 2.3.1 Üst düzey durum
Dosyalar sabitleri (BASE_URL, UA listeleri, AES tabloları) üst düzeyde `const` ile tanımlar. Çalışma ortamı kodu bir IIFE içine sarar, bu yüzden provider'ın kendi `const TMDB_API_KEY = ...` tanımı (bir dosyada var) global ile çakışmaz. Oturum/çağrı arası kalıcı durum **[VARSAYIM]** garanti yok: her çağrı yeni bir JS bağlamında koşar (bkz. §3.5).

### 2.4 Ayarlar: `onSettings()` **[DOĞRULANDI]**
`hasSettings: true` olan provider'lar ikinci bir fonksiyon dışa aktarır: `async function onSettings()` → bir **arayüz düzeni dizisi** döndürür. Uygulama bunu ayrı bir çağrıyla (`staticSettingsCallCode`) alır ve ayar penceresini çizer. Gerçek öğe türleri (hexion + fjoral + `PluginSettingsDialog` kaynağı):

```js
{ type: "header", label: "Resolution" }
{ type: "info",   label: "Max streams shown per resolution\nDefault: Unlimited" }
{ type: "toggle", key: "enable4K", label: "4K / 2160p", defaultValue: true, description: "..." }
{ type: "select", key: "max4K", label: "4K", defaultValue: "unlimited",
  options: [ { value: "1", label: "1" }, { value: "unlimited", label: "Unlimited" } ] }
{ type: "text",   key: "cinemaTvToken", isPassword: true, placeholder: "Paste cookie/token here...", description: "..." }
```
Kullanıcı değerleri saklanır (`scraper_settings`: `{scraperId → {anahtar → değer}}`) ve her çağrıda **global `SCRAPER_SETTINGS`** nesnesi olarak enjekte edilir; provider `SCRAPER_SETTINGS.enable4K !== false` gibi okur. `select` değerleri dize, `toggle` bool'dur. Ayar olmadan çalışmayanlar var: örn. `fjoral` bir çerez/belirteç (`cinemaTvToken`) ister ("Requires Setup" açıklamasında yazıyor). Faz 2'de ayar ekranı **yok sayılsa** bile bu provider'lar boş döner; bu bir Ayarlar maddesi (CLAUDE.md "Ayarlar ≤ 5") sorusu doğurur: kural 5 ("olmadan çalışır mı?") gereği **ilk sürümde `hasSettings` olanları yok sayıp varsayılanlarla çalıştırmak** önerilir.

---

## 3. Çalışma ortamı (resmî uygulamanın sağladığı)

Hepsi **[DOĞRULANDI]** (NuvioTV `PluginRuntime.kt` + NuvioMobile `JsBindings.kt`/`HostFunctions.kt`). İki sürümde küçük farklar var; farklar belirtildi.

### 3.1 Motor
QuickJS (Kotlin bağlayıcısı `com.dokar.quickjs`; iki uygulamada da `quickjs-kt-android-1.0.5-nuvio.aar`, yani **Nuvio'nun yamalı kopyası**). `async/await`, Promise, `const/let`, ok işlevleri, şablon dizeleri, spread, `?.`, `??`, opsiyonel `catch {}` bağı, düzenli ifadeler, `Uint8Array/Int32Array`, `BigInt`, `Date`, `JSON` motordan gelir (örnek dosyalarda hepsi kullanılıyor).
- Yok: `import/export` (ESM yok), Node/DOM API'leri.
- Eksik `Array.prototype.flat/flatMap`, `Object.entries/fromEntries`, `String.prototype.replaceAll` için **polyfill** enjekte edilir (eski QuickJS sürümü kaygısı).

### 3.2 Global'ler / modüller
| Global | Durum | Not |
|---|---|---|
| `module`, `exports`, `require(...)` | var | `require` yalnızca **`cheerio`** (ve `cheerio-without-node-native`, `react-native-cheerio` takma adları) ile **`crypto-js`**'i tanır; başka her modül için `Error("Module 'x' is not available")` atar. Taramada `require` kullanımı: `cheerio` 10 dosya, `crypto-js` 2 dosya, başka 1 dosya yalnızca bir yorum satırında `require('crypto')`'tan söz ediyor. |
| `fetch(url, options)` | var, **özel gerçekleme** | Seçenekler: `method`, `headers`, `body` (dize/ArrayBuffer/typed array; nesneler `String(body)`), `redirect: 'manual'` (yönlendirme izleme kapanır; 2 dosya kullanıyor), `signal` (AbortSignal; yalnızca istek öncesi/sonrası kontrol). Yanıt nesnesi: `ok, status, statusText, url, headers.get(ad), text(), json(), arrayBuffer()`. `json()` ayrıştırma hatasında **`null` döner** (atmaz). `headers.get` küçük harfli anahtar arar. Yok: `clone()`, `body` akışı, `headers.entries/forEach`, `Headers/Request/Response/FormData/Blob` sınıfları. |
| `AbortController`, `AbortSignal` | var (minimal) | |
| `URL`, `URLSearchParams` | var (minimal, kendi yazımları) | `new URL(rel, base)` göreli çözümü basit. |
| `atob`, `btoa` | var | |
| `TextEncoder`, `TextDecoder` | **Mobil'de var**; TV sürümünün okunan polyfill'inde **bulunmadı** (ama `evyrith.js` kullanıyor → motor/yama sağlıyor olabilir) — **[VARSAYIM]/belirsiz**, kendi gerçeklememizde sağlayalım. |
| `setTimeout/clearTimeout/setInterval/clearInterval` | **Mobil'de var** (korutin gecikmesine bağlı); TV polyfill'inde bulunmadı. 3/59 dosya `setTimeout` kullanıyor (zaman aşımı sarmalayıcısı). Sağlayalım. |
| `console.log/info/warn/error/debug` | var (günlüğe) | 14/59 dosya kullanıyor. |
| `CryptoJS` (global) + `require("crypto-js")` | var | Gerçek `crypto-js 4.2.0` (WebJars) TV'de motora yükleniyor; Mobil'de yerleşik bir `CryptoJS` benzeri (`AES/CBC/ECB...`, `enc.Base64/Utf8/Hex`, `lib.WordArray`, EVP KDF) JS+yerel köprü ile yazılmış. Kullanım: AES (karnis, pynvix…). |
| `crypto.subtle`, `crypto.getRandomValues` | **Yalnızca Mobil'de** (`digest`, `importKey`, `exportKey`, AES, HMAC…) | 1 dosya `crypto.subtle` (webteizle), 2 dosya `getRandomValues` kullanıyor. TV sürümünde yok. |
| `cheerio` (global) + `require("cheerio")` | var, **gerçek cheerio değil**: yerel jsoup'a köprü | `cheerio.load(html)` → `$` ; `$(selector[, context])`, `.find .each .map .filter .first .last .eq .get .text .html .attr .next .prev .children .toArray`; `$.html(el)`. **`parent()` TV sürümünde her zaman boş döner** (eksik; Mobil sürümü ayrıca denetlenmedi). `:contains("x")` jsoup biçimine çevriliyor. Tam cheerio API'si beklenmemeli. |
| `TMDB_API_KEY` | var (dize) | Kullanıcının ya da uygulamanın TMDB anahtarı (TV: `BuildConfig.TMDB_API_KEY`; Mobil: `TmdbSettingsRepository.effectiveApiKey()`). Provider kendi `const`'ını tanımlamışsa o geçerli. |
| `SCRAPER_ID`, `SCRAPER_SETTINGS` | var | Bkz. §2.4. |
| `global`, `window`, `self` | var (`globalThis`'a takma) | Örnek dosyalarda gerçek `window.`/`document.` kullanımı yok (iki eşleşme yorum/alan adı). |
| `Buffer` | **yok**; 4 Wekmed dosyası `typeof atob === 'function' ? atob(..) : Buffer.from(..)` gibi **korumalı** kullanıyor | Biz de sağlamak zorunda değiliz. |
| `XMLHttpRequest`, `localStorage`, `process`, `DOMParser`, `WebAssembly` | **yok** (Mobil'de `WebAssembly` boş yer tutucu: `instantiate` uyarı verip sahte `exports: {}` döndürür) | `XMLHttpRequest` geçen 9 eşleşmenin hepsi `"X-Requested-With": "XMLHttpRequest"` başlık değeri (API yok). |

### 3.3 TMDB anahtarı
- Eklentiler **TMDB anahtarını kendileri istemez**; ev sahibi (uygulama) global `TMDB_API_KEY` olarak verir ve ~44/59 eklenti kendi TMDB isteğini (başlık/yıl/IMDb kimliği için) atar.
- Eclipsia README'si: "*You MUST set TMDB API Key in Settings → Integrations → TMDB Enrichment, otherwise plugins will not work reliably.*" Bu, Nuvio'nun **kendi** ayarına gönderme (Mobil `TmdbSettingsRepository.effectiveApiKey()` = kullanıcı girdiyse o, değilse yerleşik). Bizim uygulamada anahtar `BuildConfig`'te; **aynı değeri `TMDB_API_KEY` olarak enjekte etmek yeterli** (fazladan Ayarlar maddesi gerekmez).
- Gizlilik/güvenlik notu: anahtar üçüncü taraf JS'e verilmiş olur (zaten APK'ya gömülü, kişisel kullanım için kabul edilmişti — README). Provider anahtarı başka yere gönderebilir; bunu "kabul edilen risk" olarak yazmak ya da yalnızca `api.themoviedb.org` alan adına giden isteklerde anahtarı biz ekleyip global'i boş/sahte bırakmak (**yalnızca öneri**; provider `?api_key=${TMDB_API_KEY}` kuruyorsa bir köprü yeniden yazımı gerekir) seçenekleri var.
- `fjoral` kendi gömülü TMDB anahtarını kullanıyor (örnek dosyalara bu anahtar alınmadı).

### 3.4 Ağ davranışı (resmî uygulama, ev sahibi tarafı) **[DOĞRULANDI, TV]**
OkHttp; bağlantı/okuma/yazma zaman aşımı 30 sn; yönlendirme izlenir (`redirect:'manual'` değilse); **sistem proxy'si kapalı** (`Proxy.NO_PROXY`); IPv4 öncelikli DNS; varsayılan `User-Agent` `Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36`; kullanıcı `Accept-Encoding` verirse silinir (OkHttp kendisi gzip çözsün); gzip/deflate elle de çözülüyor; **yanıt gövdesi en çok 1 MiB** (kesilirse `truncated` işareti, metin 1M karakter); POST'ta `Content-Type` verilmemişse `application/x-www-form-urlencoded`, PUT/PATCH'te `application/json`; `Content-Type`'a `; charset` eklenmez (HMAC imzaları için). Hata durumunda `fetch` **atmaz**, `ok:false, status:0` içeren bir yanıt döner. Plugin bitince/iptalinde havadaki HTTP çağrıları iptal edilir.

### 3.5 Yürütme modeli ve sınırlar
- Her çağrıda **yeni bir QuickJS örneği**: polyfill → (crypto-js) → `var module = {exports:{}}; var exports = module.exports; (function(){ <kod> })();` → çağrı betiği. Çağrılar arasında JS durumu kalmaz.
- Zaman aşımı: eklenti başına **60 sn** (Mobil `PLUGIN_TIMEOUT_MS`; TV'de `executePlugin` 60 sn, üstte `PluginManager` 120 sn). En çok **10** eşzamanlı eklenti; düşük öncelikli iş parçacığı havuzu (oynatıcı/arayüz aç kalmasın).
- Sonuçlar eklenti bitince **akarak** gelir (TV `PluginManager` "streaming execution"): bizim `Flow<StreamResult>` modeliyle birebir uyumlu.
- Kayıtlar eklenti adıyla etiketlenir (`provider = scraper.name`).

---

## 4. Addon ile eklenti deposu manifest'ini ayırt etmek **[DOĞRULANDI]**

Nuvio açık kaynak kodları (`NuvioTV` ve `NuvioMobile`) ve topluluk depoları (`central_repos.json` içindeki 57 depo: 31 CloudStream, 26 Nuvio) incelendiğinde depolama kuralları şu şekildedir:

### 4.1 URL Hazırlama ve Normalleştirme
- `stremio://` ve `cloudstreamrepo://` şemaları doğrudan `https://` şemasına dönüştürülür.
- Adres bir `.json` ya da `.cs3` dosyası ile bitmiyorsa, sondaki `/` atılarak `/manifest.json` eklenir (`AddonUrl.normalize`).

### 4.2 Sınıflandırma ve Öncelik Sırası (Nuvio Mantığı)
Nuvio manifest'leri indirirken türü katı şema zorlamasıyla değil, anahtar alanların varlığına göre belirler:
1. **Nuvio Plugin Deposu (`scrapers` dizisi):**
   - Manifest içinde nesne içeren bir `scrapers` dizisi varsa (veya boş `scrapers: []` ve `resources` yoksa) depo **kesinlikle Nuvio Plugin Deposu**dur.
   - Dosyada `resources` (`["stream", "subtitles"]`), `types` veya Stremio alanlarının bulunması (örn. `jasoleys`, `HANS.MEGA`, `BRONZE CLOUD`, `MANİTUX`) bu sonucu değiştirmez; `scrapers` her zaman önceliklidir.
   - Bilinmeyen üst alanlar (`description`, `author`, `logo`, `id`, `types`, `resources` vb.) yok sayılır.
2. **CloudStream Harici Depo (`pluginLists` veya `manifestVersion` + `plugins`):**
   - Manifest'te `pluginLists` URL dizisi varsa veya `manifestVersion` ile birlikte `plugins` dizisi yer alıyorsa CloudStream deposudur.
3. **Nuvio Addon (`id` + `resources`):**
   - Manifest'te metin formatında `id` ve `resources` dizisi var ve `scrapers` alanı yoksa **Addon** olarak sınıflandırılır.
   - `catalogs` dizisi içeren addon'larda `types: ["tv"]` olsa dahi (canlı TV/IPTV/spor katalogları) katalog sunulduğu için geçerli kabul edilir.

### 4.3 Hoşgörülü Ayrıştırıcı Kuralları (Tolerans)
- **Tür ve Dil Normalleştirmesi:** Kazıyıcılardaki `supportedTypes` ve `contentLanguage` alanları gerçek depolarda hem JSON dizisi (`["movie", "tv"]`) hem de boşluk veya virgülle ayrılmış düz metin (`"series tv"`, `"tr en"`) olarak gelebilmektedir. Ayrıştırıcı iki biçimi de kabul edip diziye dönüştürür.
- **Ayar Bayrağı:** `hasSettings` alanı boolean (`true/false`) haricinde metin `"true"` değerini de boolean true olarak yorumlar.
- **Tek Dokunuşla Akıllı Öneri:** Kullanıcı depoyu yanlış bir kutuya girerse (örn. Nuvio Plugin kutusuna Addon veya CloudStream adresi verilirse), reddetmek yerine `"Bu bir <tür>. <tür> olarak ekleyeyim mi?"` (TR+EN) önerisi çıkar ve tek dokunuşla doğru türe geçip ekleme tamamlanır.

---

## 5. Hızlı karşılaştırma: bizim Faz 1 addon modeli ↔ eklenti sonucu

| Bizim `PlayableStream` | Eklenti çıktısı | Dönüşüm |
|---|---|---|
| `url` | `url` | doğrudan (http/https dışını at) |
| `resolution` (Int) | `quality` (serbest metin: `Auto`, `1080p`, `1080P`, `1080p • HLS`, `2160p`) | `StreamLabels.resolution` benzeri ayrıştırma; `Auto` → bilinmiyor |
| `sizeBytes` | `size` (dize, nadir) | mevcut boyut ayrıştırıcısı |
| `headers` | `headers` | doğrudan (satır sonu içerenleri at; mevcut kural) |
| `subtitles` | `subtitles[{url, language|lang}]` | doğrudan |
| `language` (10. adım) | `language` (nadir) / ad metni | mevcut `StreamLabels.language` |
| addon adı | `provider` / eklenti adı | `StreamSource(addonId, name)` |
| — | `name`, `title` (görünmez karakterler!) | görünmez karakterleri temizle |
| `infoHash`/`seeders` | yok | zaten yok sayıyoruz |

---

## 6. Android'de çalıştırma önerisi (SADECE öneri, kod yok)

### 6.1 Motor: **QuickJS (dokar3/quickjs-kt)**, WebView değil
- **[DOĞRULANDI]** Nuvio'nun iki Android uygulaması da QuickJS kullanıyor; eklentiler bu sözleşmeye göre yazıldığı için aynı motorun davranışına en yakın seçenek bu.
- `io.github.dokar3:quickjs-kt` — Apache-2.0, aktif (son sürüm `v1.0.15`, 2026-09-03; son itme 2026-10-08), Android `minSdk 23` (bizim 24 ile uyumlu), ABI'ler `arm64-v8a, armeabi-v7a, x86, x86_64`. Özellikler (README): `define`/`function`/`asyncFunction` ile Kotlin köprüleri, `evaluate`, **`compile` → bayt kodu önbelleği**, `evaluationTimeoutMillis`, ES modülleri, kendi korutin dispatcher'ı. APK'ya native `.so` ekler (~birkaç MB; ölçülmedi).
- Neden WebView değil: WebView ana iş parçacığına bağlı, arka planda/başsız çalıştırması zahmetli, her çağrı için yeni izole bağlam pahalı, **gerçek tarayıcı API'leri (DOM, `XMLHttpRequest`, çerez, CORS, `localStorage`) açığa çıkar** — eklentiler bunları beklemiyor ama kötü niyetli JS'in saldırı yüzeyi büyür. QuickJS'te yalnızca bizim enjekte ettiğimiz şey vardır.
- Neden `app.cash.quickjs`: arşivlenmiş/yerini Zipline'a bırakmış bir proje; `quickjs-kt` Kotlin/korutin dostu **[VARSAYIM: depo sayfası okunmadı, `cashapp/quickjs` depo araması 404 döndü]**.
- Dosya: motor sürümü `libs.versions.toml`'a sabit sürümle eklenir (kural). Test: Android birim testlerinde yerel kütüphane yüklenmez (README "Android unit tests" uyarısı), motor testleri `androidTest` altında olmalı.

### 6.2 Eksik global'ler ve köprüler (kendi gerçeklememiz)
Aşağıdakileri **sıfırdan** yazmalıyız (Nuvio kaynağı GPL-3.0, kopyalanamaz); sözleşme §3.2'de.
1. **`fetch`** — Kotlin `asyncFunction("__fetch")` → mevcut OkHttp istemcisi. Bizim `AddonStreamProvider`'daki gibi: istek başına zaman aşımı, 1 MiB gövde sınırı, `Accept-Encoding` düşürme, `ok/status/url/headers.get/text/json/arrayBuffer`. Hata → atmadan `ok:false,status:0`. İptal (Flow iptal olunca `Call.cancel()`).
2. **HTML ayrıştırma (cheerio benzeri)** — en çok iş. Seçenek A (Nuvio gibi): jsoup'u Kotlin tarafında tutup JS'e küçük bir `cheerio` kabuğu (`load/find/each/text/html/attr/first/eq/next/prev/children/filter/map/get/toArray`) vermek. Seçenek B: gerçek `cheerio`'yu paketleyip QuickJS'te çalıştırmak (büyük, `htmlparser2`/`parse5` bağımlılığı, performans düşük). **Öneri A**; ancak jsoup'u bağımlılık olarak eklemek "tek JSON kütüphanesi / yığın" kararını genişletir (yeni bağımlılık notu CLAUDE.md'ye).
3. **`crypto`/`CryptoJS`** — AES(CBC/ECB/GCM?), MD5/SHA, HMAC, Base64/Hex/Utf8, `WordArray`. Seçenek: Kotlin'de `javax.crypto` ile yerel köprü + JS'te ince bir CryptoJS arayüzü; ya da gerçek `crypto-js` (MIT) dosyasını paketleyip QuickJS'e yüklemek (TV böyle yapıyor; ~30–50 KB bayt kodu önbellekli). **Öneri:** `crypto-js`'i paketle (olgun, sözleşme birebir), `crypto.subtle` ve `getRandomValues`'ı ilk sürümde **atla** (taramada 59 dosyanın 1'i/2'si).
4. **Küçükler:** `atob/btoa`, `URL/URLSearchParams`, `TextEncoder/TextDecoder`, `AbortController`, `setTimeout/clearTimeout` (korutin `delay` ile), `console`, `global/window/self`, `module/exports`, `require` (yalnızca `cheerio` ve `crypto-js`). Dört `Array/Object/String` polyfill'i motor sürümüne göre.
5. **Enjekte değerler:** `TMDB_API_KEY` (BuildConfig), `SCRAPER_ID`, `SCRAPER_SETTINGS` (ilk sürümde `{}`).
- Her çağrı için **yeni `QuickJs` örneği**, polyfill'i **bayt kodu olarak bir kez derle** (Nuvio'nun yaptığı gibi) → başlangıç maliyeti düşer.

### 6.3 Mimariye oturma (CLAUDE.md ile)
- `StreamProvider` arayüzü zaten uygun: bir `PluginStreamProvider(repo, scraper)` → `Flow<StreamResult>`; eklenti başına ayrı zaman aşımı ve "yanıt vermedi" bilgisi mevcut `FailureReason`'la örtüşür (60 sn çok uzun: **bizim varsayılan 15 sn**, ayarlanabilir; Nuvio'nunki 60–120 sn).
- Veri: `addons` tablosundaki `NUVIO_PLUGIN_REPO` ve `plugins` tablosu (depo → plugin) zaten ayrılmıştı; şema değişmeden kullanılabilir. JS kodu dosya olarak (`filesDir/plugins/{repoId}/{id}.js`) ya da tabloda saklanabilir.
- Arayüz: "Eklenti ekle" akışı manifest türünü §4 kuralıyla ayırıp addon/eklenti deposu olarak yönlendirir (kullanıcıya "bu bir depo" ayrımı göstermeden). Kural 5 gereği önce **depo ekle + varsayılan açık** çalışsın; kazıyıcı bazında aç/kapat mevcut "Yüklü eklentiler" listesinin uzantısı.

### 6.4 Güvenlik (eklenti JS'inin neye erişmemesi gerektiği)
QuickJS **kendiliğinden** dosya sistemi/uygulama verisine erişmez; erişim yalnızca bizim enjekte ettiğimiz köprülerle olur. Bu yüzden asıl yüzey **köprü fonksiyonlarıdır**:
- **Dosya sistemi, SharedPreferences/Room/DataStore, Intent, ağ dışı Android API'leri: hiç köprülenmez.** `require` yalnızca izin listesindeki iki modülü verir.
- **`fetch` köprüsü** tek dış kapıdır: yalnızca `http`/`https` (çıkış: `file:`, `content:`, `data:`, `ftp:`, `intent:` yok); **yerel/özel ağ adreslerini engelle** (127.0.0.0/8, 10/8, 172.16/12, 192.168/16, 169.254/16, `localhost`, IPv6 `::1/fc00::/7`): bir eklenti kullanıcının LAN'ındaki cihazlara (yönlendirici, NAS, yerel addon) istek atamasın (SSRF). DNS yeniden bağlanmasına karşı IP kontrolü bağlantıdan **sonra** yapılmalı. Yönlendirmelerde her adım yeniden denetlenir.
- Gövde/yanıt sınırı (1 MiB), toplam bayt/istek sayısı bütçesi, eşzamanlı istek sınırı, **duvar saati zaman aşımı** (`evaluationTimeoutMillis` + korutin `withTimeout`), bellek ve yığın sınırı (QuickJS `memoryLimit`/`maxStackSize` var mı **[VARSAYIM: README'de belge okunmadı, API doğrulanmadı]**).
- **Çerezler/oturum:** eklenti istekleri uygulamanın WebView/OkHttp çerez deposundan **ayrı** olmalı (çerez kavanozu yok ya da eklenti başına geçici). Kullanıcının Web sitesi görünümü çerezleriyle karışmamalı.
- **Sonuç temizliği:** dönen `url`'yi yalnızca http(s) kabul et; `headers` içinde satır sonu (`\r\n\0`) atılır (mevcut kural); metin alanları arayüzde kaçışlanır (Compose zaten); görünmez sıralama karakterleri temizlenir; çok uzun/çok sayıda sonuç kırpılır.
- **TMDB anahtarı** §3.3'teki gibi: ya kabul edilen risk ya da yalnızca TMDB alan adına yönelik köprü.
- **Günlük:** eklenti `console` çıktısı ve istek URL'leri (özellikle `api_key=` içerenler) günlüğe **yazılmaz** ya da maskelenir (CLAUDE.md: URL asla loglanmaz).
- **Kaynak bütünlüğü:** indirilen kodun SHA-256'sı saklanıp güncellemede değişim kullanıcıya bildirilebilir (Nuvio yalnızca günlüğe yazıyor); depo manifest'i yalnızca https'ten alınmalı (düz `http` depo reddedilebilir ya da uyarılabilir; mevcut addon yolunda LAN `http` serbest bırakılmıştı — bu bilinçli bir fark olur).
- **Kod boyutu:** Nuvio 5 MB üst sınır koyuyor; biz 1 MB bile yeterli (en büyük örnek 20 KB).
- Tehdit modeli notu: eklenti kodu **güvenilmeyen** koddur ve kullanıcının kendi eklediği bir URL'den gelir; "gömülü varsayılan depo yok" kuralı (CLAUDE.md) aynen sürer.

---

## 7. Doğrulanmış özet / Belirsizlikler

**Doğrulanmış**
- Depo manifest şeması (`name`, `version`, `scrapers[]` ve girdi alanları), `filename` göreli/mutlak, `supportedTypes` içinde `series`, `hasSettings`.
- Sözleşme: `getStreams(tmdbId: string, mediaType: "movie"|"tv"(|"series"), season?, episode?)` → Promise<dizi>; `module.exports`; hata/boş → `[]`.
- Sonuç alanları ve `headers`/`subtitles` biçimi; `quality` serbest metin.
- Ortam global'leri: `fetch` (özel), `cheerio`/`require`, `CryptoJS`, `URL/URLSearchParams`, `atob/btoa`, `AbortController`, `console`, `TMDB_API_KEY`, `SCRAPER_SETTINGS`, `SCRAPER_ID`.
- `onSettings()` düzen öğeleri: `header`, `info`, `toggle`, `select`, `text`(+`isPassword`).
- Addon ↔ depo ayrımı: `scrapers` ↔ `resources`, ve MANİTUX'un yanlış etiketi.

**Belirsiz / sonraya**
- `TextDecoder`/`setTimeout`'un TV sürümünde nereden geldiği; `limited`, `supportsExternalPlayer`, `type` alanlarının tam anlamı; `english/` ve `stable/` manifest'lerinin amacı; kısayol kodu (`short code`) çözümü; QuickJS bellek/yığın sınırı API'si; `quickjs-kt` APK boyut etkisi; Nuvio'nun eklenti ayarlarında `select` dışında başka türlerin (ör. sayı) olup olmadığı (taranan dosyalarda yok).
- Resmî belge olmadığı için sözleşme **çalışma ortamı kaynağından** türetildi; Nuvio güncellenince alanlar/polyfill'ler değişebilir (ör. TV ↔ Mobil farkları zaten var). Faz 2 başlarken tekrar kısa bir doğrulama turu (güncel `JsBindings.kt` farkı) önerilir.

**Örnek dosyalar** (`docs/ornekler/`): [`brellor.js`](ornekler/brellor.js) (en sade: yalnızca `fetch`, `module.exports = { getStreams }`) ve [`hexion.js`](ornekler/hexion.js) (`require("cheerio")` + `onSettings` + `SCRAPER_SETTINGS`). İkisi de `eclipsia/nuvio-plugin` (gitea.com aynasından, 2026-10-12) — **lisans GPL-3.0**; yalnızca belge amaçlı örnek olarak saklanır, uygulamaya **derlenmez/gömülmez/kopyalanmaz**. SHA-256: brellor `a0bb5c46…895e34`, hexion `681a06e6…7fc`. (Bkz. `docs/ornekler/KAYNAK.md`.)

## Ek (2026-10-22): `format` alanı ve doğrulama
- Akış nesnesindeki `format` (`"m3u8"`, `"hls"`, `"mpd"`, `"dash"`, `"mp4"`…) medya türünü belirler. Uzantısız HLS adresleri (`…/play?id=…`) bu ipucu olmadan ExoPlayer'da `3003 (container desteklenmiyor)` verir; Play `m3u8/hls` → HLS, `mpd/dash` → DASH olarak okur, bilinmeyen biçimde adresten çıkarır.
- Eklentiye giden çağrı `PluginCallRules` ile denetlenir: `tmdbId` pozitif sayı; film → `movie`, sezon/bölüm yok; dizi → `tv`/`series`, geçerli sezon ve bölüm. Tutarsız çağrı eklentiye gönderilmez.
- Eklenti içeriğin kendisini doğrulamaz; doğru kimlikle yanlış video dönebilir (Bogard, TMDB 120 için 33 dakikalık video). Bu yüzden oynatıcı süreyi TMDB süresiyle karşılaştırır (bkz. CLAUDE.md "İçerik doğrulama").
