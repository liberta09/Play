# Dublaj Bekleme Süresi — Cihaz Doğrulama Raporu (LDPlayer)

Dal: `fix-dublaj-bekleme`. Test cihazı: LDPlayer (127.0.0.1:5555, 1600x900). Tarih: 2026-10-23.  
Açılış Türkçe dublaj beklemesi: `StartPolicy.DUB_WAIT_MS = 8_000L` (3 sn yerine 8 sn, Kullanıcı kararı 2026-10-23).

## Özet Tablo

| Film | İlk Kareye Süre | Kaynak | Dublajlı mı | Kart Çıktı mı | Süre Kontrolü |
|---|---|---|---|---|---|
| Yüzüklerin Efendisi: Kralın Dönüşü (2003) | 6.5 sn | Ace (prx-1564-ant-q.vmpx.online) | Hayır (Altyazılı) | Evet (27.8 sn'de dublaj gelince) | 263.2 dk / 263 dk -> Ok |
| Dune (2021) | 8.1 sn | Enel (four.pichive.online) | **Evet (Dublajlı)** | Hayır (Gerek kalmadı) | 155.4 dk / 155 dk -> Ok |
| Joker (2019) | 1.1 sn | Dragon (s24.imagecdn9065.shop) | **Evet (Dublajlı)** | Hayır (Gerek kalmadı) | 121.8 dk / 122 dk -> Ok |

---

## Karşılaştırma Analizi (Beta Testi vs. 8 sn Dublaj Beklemesi)

- **Beta Testi Sonucu (`docs/dogrulama-beta/SONUC.md`)**:
  3 sn bekleme sınırında 43 eklenti açıkken her 3 film de altyazılı başlamış ve dublajlı kaynaklar sonradan gelerek "Dublajlı kaynak bulundu, geçilsin mi?" kartına dönüşmüştü.
- **Bu Testin Sonucu (`StartPolicy.DUB_WAIT_MS = 8_000L`)**:
  - **Dune (2021)**: Önceki testte altyazılı başlarken, uzatılan bekleme ve yedeğe geçiş sayesinde **dublajlı (`Enel`) başladı**.
  - **Joker (2019)**: Önceki testte altyazılı başlarken, dublaj önceliğiyle doğrudan **dublajlı (`Dragon`) başladı** (1.1 sn).
  - **Kralın Dönüşü (2003)**: Eklentiler bu filmde ilk 8 sn içinde dublajlı akış veremedi; altyazılı `Ace` kaynağıyla açıldı. Dublajlı kaynaklar (`Xebec` ve `Shanks`) **27.8 sn sonra** geldiğinde oynatıcıda köşe kartı çıktı (kullanıcı deneyimi kesilmedi, siyah ekranda kalınmadı).

---

## Detaylı Film Sonuçları ve Kanıtlar

### 1. Yüzüklerin Efendisi: Kralın Dönüşü (2003)
- **İşlem**: Detay > Oynat (`02:15:16.649`).
- **Kaynak Başlatma & Süre Kontrolü**:
  - İlk denenen kaynak: `Bogard (streamcorecdn.com)` -> Süre: 51.7 dk / beklenen 263 dk -> `Mismatch` (yanlış video elendi).
  - Yedeğe geçiş: `Ace (prx-1564-ant-q.vmpx.online)` başlatıldı (`02:15:19.758`).
  - İlk kare: `02:15:23.164` (Oynat basıldıktan ilk kareye geçen süre: **6.515 sn**).
  - Süre kontrolü: `263.2 dk beklenen=263 dk -> Ok`.
- **Dublajlı mı Başladı**: Hayır, altyazılı başladı (üst çubukta `Ace · 1280x528`).
- **Dublaj Kartı**: Evet, 10. saniyede "Dublajlı kaynak bulundu, geçilsin mi? [Geç] [Kapat]" kartı çıktı.
- **Gecikme Analizi**: Dublajlı kaynaklar (`Xebec`: `02:15:44.461`, `Shanks`: `02:15:45.777`) Oynat basıldıktan **27.8 sn** sonra geldi. 8 saniyelik açılış dublaj bekleme süresinden çok daha geç hazır oldukları için altyazılı kaynak başlatılmış ve dublaj sonradan kart önerisi olarak sunulmuştur.
- **Ekran Görüntüsü**: `docs/dogrulama-dublaj-bekleme/kralin_donusu_10s.png` (565 KB, Sméagol açılış sahnesi ve dublaj geçiş kartı).
- **PlayMatch / PlayProbe Günlüğü**:
  ```
  PlayMatch: kaynak başlatılıyor: Bogard (streamcorecdn.com)
  PlayProbe: kaynak yoklama: host=streamcorecdn.com durum=200 type=application/vnd.apple.mpegurl uzunluk=null
  PlayMatch: ilk kare: Bogard (faz=Starting)
  PlayMatch: süre kontrolü kaynak=Bogard (streamcorecdn.com) süre=51.7 dk beklenen=263 dk tür=Movie -> Mismatch
  PlayMatch: kaynak başlatılıyor: Ace (prx-1564-ant-q.vmpx.online)
  PlayProbe: kaynak yoklama: host=prx-1564-ant-q.vmpx.online durum=200 type=application/vnd.apple.mpegurl uzunluk=null
  PlayMatch: ilk kare: Ace (faz=Starting)
  PlayMatch: süre kontrolü kaynak=Ace (prx-1564-ant-q.vmpx.online) süre=263.2 dk beklenen=263 dk tür=Movie -> Ok
  ```

### 2. Dune (2021)
- **İşlem**: Detay > Oynat (`02:22:18.246`).
- **Kaynak Başlatma & Süre Kontrolü**:
  - İlk denenen kaynak: `Clover (cdn2.blueflix.top)` -> `ERROR_CODE_IO_BAD_HTTP_STATUS (2004) http=500` hatası aldı (`02:22:24.070`).
  - Yedeğe geçiş: Dublajlı aday olan `Enel (four.pichive.online)` başlatıldı (`02:22:24.071`).
  - İlk kare: `02:22:26.308` (Oynat basıldıktan ilk kareye geçen süre: **8.062 sn**).
  - Süre kontrolü: `155.4 dk beklenen=155 dk -> Ok`.
- **Dublajlı mı Başladı**: **EVET** (Üst çubuk: `Enel · 1920×804 · Dublajlı (gömülü ses)`).
- **Dublaj Kartı**: Hayır (zaten dublajlı başladığı için gerek kalmadı).
- **Ekran Görüntüsü**: `docs/dogrulama-dublaj-bekleme/dune_10s.png` (243 KB, çöl açılış sahnesi, üst çubuk "Dublajlı (gömülü ses)").
- **PlayMatch / PlayProbe Günlüğü**:
  ```
  PlayMatch: kaynak başlatılıyor: Clover (cdn2.blueflix.top)
  PlayProbe: kaynak yoklama: host=cdn2.blueflix.top durum=200 type=application/vnd.apple.mpegurl uzunluk=null
  PlayMatch: oynatıcı hatası: Clover (faz=Starting) ERROR_CODE_IO_BAD_HTTP_STATUS (2004) http=500
  PlayMatch: kaynak başlatılıyor: Enel (four.pichive.online)
  PlayProbe: kaynak yoklama: host=four.pichive.online durum=200 type=text/html;charset=UTF-8 uzunluk=null
  PlayMatch: ilk kare: Enel (faz=Starting)
  PlayMatch: süre kontrolü kaynak=Enel (four.pichive.online) süre=155.4 dk beklenen=155 dk tür=Movie -> Ok
  ```

### 3. Joker (2019)
- **İşlem**: Detay > Oynat (`02:25:25.023`).
- **Kaynak Başlatma & Süre Kontrolü**:
  - İlk denenen kaynak: `Dragon (s24.imagecdn9065.shop)`.
  - İlk kare: `02:25:26.142` (Oynat basıldıktan ilk kareye geçen süre: **1.119 sn**).
  - Süre kontrolü: `121.8 dk beklenen=122 dk -> Ok`.
- **Dublajlı mı Başladı**: **EVET** (Üst çubuk: `Dragon · 1280×692 · Dublajlı (gömülü ses)`).
- **Dublaj Kartı**: Hayır (zaten dublajlı başladığı için gerek kalmadı).
- **Ekran Görüntüsü**: `docs/dogrulama-dublaj-bekleme/joker_10s.png` (507 KB, Arthur Fleck soyunma odası aynası sahnesi).
- **PlayMatch / PlayProbe Günlüğü**:
  ```
  PlayMatch: kaynak başlatılıyor: Dragon (s24.imagecdn9065.shop)
  PlayProbe: kaynak yoklama: host=s24.imagecdn9065.shop durum=200 type=application/x-mpegURL uzunluk=null
  PlayMatch: ilk kare: Dragon (faz=Starting)
  PlayMatch: süre kontrolü kaynak=Dragon (s24.imagecdn9065.shop) süre=121.8 dk beklenen=122 dk tür=Movie -> Ok
  ```

---

## Ekran Görüntüleri (En çok 3 görüntü, her biri < 600 KB)
1. `docs/dogrulama-dublaj-bekleme/kralin_donusu_10s.png` (565 KB)
2. `docs/dogrulama-dublaj-bekleme/dune_10s.png` (243 KB)
3. `docs/dogrulama-dublaj-bekleme/joker_10s.png` (507 KB)
