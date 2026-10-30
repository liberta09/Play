# HDFilmCehennemi MobLand S2 B7 ve S1 B1 Oynatıcı Sayfaları Analizi (2026-10-31)

Bu araştırma, MobLand S2 B7 içeriğinde HDFilmCehennemi eklentisinin neden kaynak bulamadığını (ve S1 B1'de nasıl davrandığını) tespit etmek amacıyla yapılmıştır.

---

## 1. MobLand S2 B7 (https://www.hdfilmcehennemi.nl/dizi/mobland-70/sezon-2/bolum-7/)

* **HTTP Yanıt Kodu:** `200 OK` (143.030 bayt, `bolum.html`)
* **Iframe Sayısı:** 1 adet
* **Iframe Etiketi:**
  ```html
  <iframe class="rapidrame" data-src="https://www.hdfilmcehennemi.nl/rplayer/9fjl4m3rtxi4/" scrolling="no" allowfullscreen="true"></iframe>
  ```
* **Iframe Türü:** `/rplayer/`
* **Alternatif Kaynaklar (`alternative-link`):**
  ```html
  <div class="alternative-links" data-active="1" data-lang="en">
    <button class="alternative-link" data-active="1" data-video="328332">
      Rapidrame
    </button>
  </div>
  ```
  *(Bu bölümde yalnızca Rapidrame alternatifi bulunmakta ve doğrudan `/rplayer/` iframe'ini yüklemektedir).*

---

## 2. MobLand S1 B1 (https://www.hdfilmcehennemi.nl/dizi/mobland-70/sezon-1/bolum-1/)

* **HTTP Yanıt Kodu:** `200 OK` (yönlendirme: `https://www.hdfilmcehennemi.nl/dizi/mobland/sezon-1/bolum-1-hd87/`, 151.796 bayt, `bolum-s1e1.html`)
* **Iframe Sayısı:** 1 adet
* **Iframe Etiketi:**
  ```html
  <iframe class="close" data-src="https://hdfilmcehennemi.mobi/video/embed/sMvfL9k4fcz/?rapidrame_id=cyeb9wh66pzp" scrolling="no" allowfullscreen="true"></iframe>
  ```
* **Iframe Türü:** `/embed/` (`/video/embed/`)
* **Alternatif Kaynaklar (`alternative-link`):**
  ```html
  <div class="alternative-links" data-active="1" data-lang="dual">
    <button class="alternative-link" data-active="1" data-video="215333">
      Close
    </button>
    <button class="alternative-link" data-active="" data-video="196997">
      Rapidrame
    </button>
  </div>
  ```

---

## 3. Karşılaştırma: Nuvio Eklentisi Neden S2 B7'yi Bulamıyor?

1. **S1 B1:** Birincil oynatıcı kaynağı `Close` alternatifidir ve iframe adresi `https://hdfilmcehennemi.mobi/video/embed/...` yapısındadır. Nuvio JS eklentisi (`1.js`) filtre olarak `/(?:video\/)?embed\//` desenini aradığından bu bağlantıyı yakalayabilmektedir.
2. **S2 B7:** Sitede yalnızca tek bir kaynak vardır: `Rapidrame`. Bu kaynak `https://www.hdfilmcehennemi.nl/rplayer/9fjl4m3rtxi4/` adresini kullanmaktadır. Eklenti deseni `/rplayer/` yolunu içermediği için bu iframe'i atlamakta ve sonuçta akış çıkaramamaktadır.

---

## 4. `rplayer.html` ve Şifrelenmiş Akış (`packed.js`)

* **Adres:** `https://www.hdfilmcehennemi.nl/rplayer/9fjl4m3rtxi4/`
* **Referer:** `https://www.hdfilmcehennemi.nl/dizi/mobland-70/sezon-2/bolum-7/`
* **HTTP Yanıt Kodu:** `200 OK` (36.874 bayt, ilk denemede doğrudan başarıyla alındı).
* **Anahtar İpuçları (`rplayer-ipuclari.txt`):**
  * `Satır 324`: `var sources = [];`
  * `Satır 344`: `eval(function(p,a,c,k,e,d){...` (Dean Edwards JavaScript packer bloğu, 3.532 karakter)
  * `Satır 347`: `//sources: [{file:atob(file_link)}],`
  * `Satır 348`: `sources: [{file:zb8}],`
* **Çözümleme:** `packed.js` içindeki Dean Edwards bloğu çözüldüğünde:
  ```javascript
  var zb8 = u8ao("T8mnHlyNWUnq5ATToJ#bzVo1XCn#AuCXmRXa#Eq47hknR#...".split("#"));
  ```
  satırı görülmektedir. Buradaki `zb8` değişkeni, şifreli dizinin çözülmüş video akış (m3u8) bağlantısıdır ve doğrudan `sources: [{file: zb8}]` üzerinden oynatıcıya verilmektedir.

---

## 5. Dizin İçeriği (`docs/dogrulama-mobland/`)

* `bolum.html`: S2 B7 ham HTML kaynağı
* `iframes.txt`: S2 B7 iframe ve alternative-link dökümü
* `rplayer.html`: S2 B7 Rapidrame oynatıcı sayfası
* `rplayer-ipuclari.txt`: `m3u8`, `eval`, `atob`, `file`, `sources` içeren satırlar
* `packed.js`: `rplayer.html` içindeki şifreli packer bloğu
* `bolum-s1e1.html`: S1 B1 ham HTML kaynağı
* `iframes-s1e1.txt`: S1 B1 iframe ve alternative-link dökümü (tür: `/embed/`)
* `cookies.txt`: Curl oturum çerezleri
