# Node.js vs Play Eklenti Karşılaştırma Raporu

**Tarih / Saat:** 2026-10-31 02:52:00 (TSİ)  
**Ortam:** Node.js v24.21.0 (Windows 11)  
**Hedef Dal:** `duzeltme/oynatici-saglamlik`  
**Kural Uyumu:** KOD DEĞİŞTİRİLMEDİ (0 satır `.kt` değişikliği), emülatöre DOKUNULMADI, TMDB anahtarı loglanmadı/yazılmadı.

---

## 1. Eklenti Dosyaları ve Sürüm Kontrolü

İki eklenti dosyası ayrı klasörlere indirilmiştir:
1. **HDFilmCehennemi 1.0.3:**
   - URL: `https://raw.githubusercontent.com/liberta09/hdfilmcehennemi-nuvio/main/hdfilmcehennemi.js`
   - İndirilen yer: `docs/dogrulama-toplu/eklentiler/hdfilmcehennemi/hdfilmcehennemi.js` (69,538 bayt)
   - Sürüm: `1.0.3` (rplayer ve paketli şifre çözücü yaması içeren sürüm).
2. **DiziBOX TV:**
   - URL: `https://raw.githubusercontent.com/kratoskrator/dizibox-nuvio/main/dizibox.js`
   - Manifest URL: `https://raw.githubusercontent.com/kratoskrator/dizibox-nuvio/main/manifest.json`
   - İndirilen yer: `docs/dogrulama-toplu/eklentiler/dizibox/dizibox.js` (43,084 bayt)
   - **Sürüm Doğrulaması:**
     - `manifest.json` içeriğinde `"version": "1.0.1"` ve scraper bloğunda `"version": "1.0.1"`.
     - `dizibox.js` başlığında: `/* DiziBOX TV for Nuvio v1.0.1 ... */`.
     - **Sonuç:** Emülatörde kurulu olan `v1.0.1` ile **BİREBİR AYNIDIR**.

---

## 2. Node ile Çalıştırma ve Eksik Global Tespiti

Komut şablonu:
```bash
node -e "global.TMDB_API_KEY='<anahtar>'; global.SCRAPER_SETTINGS={}; require('<dosya>').getStreams(<tmdbId>,'<movie|tv>',<sezon>,<bölüm>).then(r=>console.log(JSON.stringify(r.map(x=>({name:x.name,title:x.title,quality:x.quality,type:x.type})))))"
```

### Karşılaşılan Durumlar ve Nedenleri:
1. **HDFilmCehennemi (`hdfilmcehennemi.js`):**
   - Node 18+ yerleşik `fetch` API'si ile ek bir global/kütüphane gerektirmeden doğrudan çalıştı.
   - Örn. Kara Şövalye (155) için doğrudan 1 akış döndürdü.
2. **DiziBOX TV (`dizibox.js`):**
   - Arama aşamasında ek global gerekmemektedir.
   - Ancak arama sonucu bulunup şifreli bir oynatıcı sayfası (Molystream / DBX Pro) çözülmek istendiğinde (`dizibox.js` satır 592-598), koddaki `cryptoLibrary()` işlevi `globalThis.CryptoJS` veya `require('crypto-js')` aramaktadır:
     ```javascript
     function cryptoLibrary() {
       if (cryptoCache) return cryptoCache;
       if (typeof globalThis !== 'undefined' && globalThis.CryptoJS) cryptoCache = globalThis.CryptoJS;
       else {
         try { cryptoCache = require('crypto-js'); } catch (ignored) {}
       }
       if (!cryptoCache || !cryptoCache.AES || !cryptoCache.enc || !cryptoCache.enc.Utf8) throw new Error('Nuvio CryptoJS support is required for this player');
       return cryptoCache;
     }
     ```
   - Ham Node komutunda `global.CryptoJS` tanımlı olmadığı ve `crypto-js` paketi bulunmadığı için scraper:
     `[DiziBOX TV] Nuvio CryptoJS support is required for this player`
     uyarısı basarak akış listesini `[]` (0 akış) olarak sonlandırmaktadır.
   - **Play Android Uygulamasındaki Durum:** Play uygulaması `PluginPrelude.kt` içerisinde QuickJS motoruna JVM tabanlı `CryptoJS` polyfill'i enjekte etmektedir. Bu sayede Android'de `Criminal Minds` için 1 akış çözülebilmektedir.
   - **Doğrulama:** Node tarafında Play'in sağladığına denk minimal bir `CryptoJS.AES` shim'i tanımlandığında, Node da Android Play ile birebir aynı akışı (`Criminal Minds S1E1 | DBX Pro`, `720p`, `hls`) başarıyla çözmüştür.

---

## 3. DiziBOX Ana Sayfası İndirme Sonucu (`dizibox-anasayfa.html`)

- **Komut:** `curl.exe -s -L -A "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36" -o docs/dogrulama-toplu/dizibox-anasayfa.html https://www.dizibox.live/`
- **Dosya Yolu:** `docs/dogrulama-toplu/dizibox-anasayfa.html` (5,549 bayt).
- **Sayfa Durumu:** Cloudflare Antibot koruması (`<title>Just a moment...</title>`, `Enable JavaScript and cookies to continue`) dönmüştür.
- **"tulsa" Araması:** Sayfa içinde "tulsa" kelimesi **GEÇMEMEKTEDİR** (Cloudflare engeli nedeniyle gövdede dizi listesi bulunmamaktadır).

---

## 4. Karşılaştırma Tablosu

| İçerik | Tür | Eklenti | Node Sonucu | Play Sonucu (Toplu Ölçüm) | Fark Var mı? | Açıklama / Detay |
|---|---|---|---|---|---|---|
| **Tulsa King** (153312) | TV (S1E1) | HDFilmCehennemi | 0 akış | 0 akış | **YOK** | Sağlayıcı arama sorgusu boş döndü. |
| | | DiziBOX TV | 0 akış | 0 akış | **YOK** | Sağlayıcı arama sorgusu boş döndü (hem ham hem shim ile 0). |
| **Law & Order: SVU** (2734) | TV (S1E1) | HDFilmCehennemi | 0 akış | 0 akış | **YOK** | Sağlayıcı arama sorgusu boş döndü. |
| | | DiziBOX TV | 0 akış | 0 akış | **YOK** | Sağlayıcı arama sorgusu boş döndü. |
| **Family Guy** (1434) | TV (S1E1) | HDFilmCehennemi | 0 akış | 0 akış | **YOK** | Sağlayıcı arama sorgusu boş döndü. |
| | | DiziBOX TV | 0 akış | 0 akış | **YOK** | Sağlayıcı arama sorgusu boş döndü. |
| **Simpsonlar** (456) | TV (S1E1) | HDFilmCehennemi | 0 akış | 0 akış | **YOK** | Sağlayıcı arama sorgusu boş döndü. |
| | | DiziBOX TV | 0 akış | 0 akış | **YOK** | Sağlayıcı arama sorgusu boş döndü. |
| **Grey's Anatomy** (1416) | TV (S1E1) | HDFilmCehennemi | 0 akış | 0 akış | **YOK** | Sağlayıcı arama sorgusu boş döndü. |
| | | DiziBOX TV | 0 akış | 0 akış | **YOK** | Sağlayıcı arama sorgusu boş döndü. |
| **Vahşi Orman** (791523) | MOVIE | HDFilmCehennemi | 0 akış | 0 akış | **YOK** | Sağlayıcı arama sorgusu boş döndü. |
| | | DiziBOX TV | 0 akış | 0 akış | **YOK** | DiziBOX TV film türünü desteklemez (`tv` dışındakilere doğrudan `[]` döner). |
| **Umut** (1058424) | MOVIE | HDFilmCehennemi | 0 akış | 0 akış | **YOK** | Sağlayıcı arama sorgusu boş döndü. |
| | | DiziBOX TV | 0 akış | 0 akış | **YOK** | DiziBOX TV film türünü desteklemez. |
| **Criminal Minds** (4057) *(Kontrol)* | TV (S1E1) | HDFilmCehennemi | 0 akış | 0 akış | **YOK** | HDFilmCehennemi diziyi listelemiyor. |
| | | DiziBOX TV | **0 akış** (Ham Node)<br>**1 akış** (CryptoJS ile) | **1 akış** | **VAR\*** | \*Ham Node'da `CryptoJS` eksikliği nedeniyle `[DiziBOX TV] Nuvio CryptoJS support is required for this player` uyarısıyla 0 akışa düştü. Play'in yerleşik Prelude polyfill'i (CryptoJS) sağlandığında Node da birebir **1 akış** (`DBX Pro`, `720p`, `hls`) döndü. |
| **Kara Şövalye** (155) *(Kontrol)* | MOVIE | HDFilmCehennemi | **1 akış** | **1 akış** | **YOK** | `Kara Şövalye \| Close \| TR / original audio when available` (1080p, hls). |
| | | DiziBOX TV | 0 akış | 0 akış | **YOK** | DiziBOX TV film türünü desteklemez. |

---

## 5. Değerlendirme ve Sonuç

1. **Tulsa King ve Açılmayan İçerikler:**
   - Tulsa King dahil olmak üzere toplu testte açılamayan 7 içeriğin tamamı, Node üzerinde bağımsız olarak çalıştırıldığında da **0 akış** döndürmüştür.
   - Bu durum, içeriklerin Play oynatıcı uygulamasındaki bir hata/tıkanma nedeniyle değil, kaynak sitelerin (hdfilmcehennemi.nl ve dizibox.live) arama kataloglarında bulunmamasından kaynaklandığını kesin olarak kanıtlamaktadır.
2. **Kontrol Grubu:**
   - Kara Şövalye ve Criminal Minds kontrol testlerinde eklentilerin kaynak bulma ve oynatıcı çözme yeteneği doğrulanmıştır.
   - `dizibox.js`'in çalışabilmesi için QuickJS/Play ortamında sunulan `CryptoJS` ortam değişkeninin gerekliliği tespit edilmiş ve bu köprü sağlandığında Node ile Play sonuçlarının %100 örtüştüğü gösterilmiştir.
