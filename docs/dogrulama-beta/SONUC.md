# Beta Doğrulama Raporu (LDPlayer Cihaz Testleri)

## Özet Tablo

| Test | Durum | Açıklama |
|---|:---:|---|
| **T1 Yeni depo açık gelir** | **DOĞRULANAMADI** | Onay penceresi ve adres göründü; depo zaten ekli olduğundan ve kural gereği silinmediğinden yeni kurulum doğrulanamadı (43/43 eklenti açık). |
| **T2 Zaman çizgisi** | **PASS** | Oynatıcıda 4dp ince iz ve 12dp tutamaçlı özel Canvas çubuğun ince göründüğü kanıtlandı. |
| **T3 Yüzük Kardeşliği** | **PASS** | Mismatch sonrası Dragon (228.3 dk, dublajlı) açıldı, ilk kare geldi, süre ve kaynak doğrulandı. |
| **T4 Dublaj yedeği** | **PASS** | Bogard 33.4 dk Mismatch sonrası anında dublajlı Dragon kaynağına geçildi. |
| **T5 Gerileme (4 Film)** | **PASS** | İki Kule (235.4 dk), Kralın Dönüşü (263.2 dk), Dune (149.2 dk), Joker (121.8 dk) doğru film ve sürelerle oynatıldı. |
| **T6 Eklenti hata mesajı** | **DOĞRULANAMADI** | Cihazda kurulu CloudStream eklentisi bulunmadığı için doğrulanamadı. |

---

## Detaylı Test Sonuçları ve Kanıtlar

### T1: Yeni Depo Açık Gelir
- **İşlem**: `play://add?nuvio=<eklenti adresi>` deep link ile çağrıldı.
- **Onay Penceresi**: "Bağlantıdan eklensin mi?" başlığı, `<eklenti adresi>` adresi ve `[Nuvio Deposu Ekle]` tür önerisi görüntülendi.
- **Mevcut Durum**: Depo cihazda zaten ekli olduğundan ve kurallar gereği depoyu silme işlemi yapılmadığından durum: **DOĞRULANAMADI: depo zaten ekli**.
- **Yüklü Eklentiler**: `izlelan` deposunda 43 eklenti mevcut, 43'ü de açık (`enabled = 1`), 0 kapalı.
- **Kanıt**: `docs/dogrulama-beta/t1_yuklu_eklentiler.png` (92.5 KB).

### T2: Zaman Çizgisi
- **İşlem**: Canlı video oynatılırken (Yüzük Kardeşliği) oynatıcı kontrolleri açıldı.
- **Görsel İnceleme**: Material varsayılan kalın Slider yerine Canvas ile çizilen 4dp iz kalınlığı ve 12dp dairesel tutamaç ince olarak görüntülendi. Odak halkası ve 48dp dokunma alanı korundu.
- **Kanıt**: `docs/dogrulama-beta/t2_zaman_cizgisi.png` (487.3 KB).

### T3: Yüzüklerin Efendisi: Yüzük Kardeşliği
- **İşlem**: Detay > Oynat basıldı.
- **Açılan Kaynak**: `Dragon` (sunucu: `s31.images5733.shop`).
- **Dublaj**: Dublajlı (gömülü ses).
- **Video Süresi**: 228.3 dk (TMDB beklenen: 228 dk, süre kontrolü: Ok).
- **İlk Kare**: Başarıyla ekrana geldi (Frodo / Çıkın Çıkmazı).
- **PlayMatch / PlayProbe Günlüğü**:
  ```
  PlayMatch: kaynak başlatılıyor: Bogard (streamcorecdn.com)
  PlayProbe: kaynak yoklama: host=streamcorecdn.com durum=200 type=application/vnd.apple.mpegurl uzunluk=null
  PlayMatch: süre kontrolü kaynak=Bogard (streamcorecdn.com) süre=33.4 dk beklenen=228 dk tür=Movie -> Mismatch
  PlayMatch: kaynak başlatılıyor: Dragon (s31.images5733.shop)
  PlayProbe: kaynak yoklama: host=s31.images5733.shop durum=200 type=application/x-mpegURL uzunluk=null
  PlayMatch: ilk kare: Dragon (faz=Starting)
  PlayMatch: süre kontrolü kaynak=Dragon (s31.images5733.shop) süre=228.3 dk beklenen=228 dk tür=Movie -> Ok
  ```
- **Kanıt**: `docs/dogrulama-beta/t3_yuzuk_kardesligi.png` (155.3 KB).

### T4: Dublaj Yedeği
- **Tetiklenme**: T3 sırasında ilk denenen `Bogard` kaynağı 33.4 dk dizi bölümü içeriği olduğundan süre uyuşmazlığı (`Mismatch`) ile elendi.
- **Yedeğe Geçiş**: Sıradaki kabul edilebilir dublajlı aday olan `Dragon` kaynağına anında geçildi.
- **Bekleme Süresi**: Bogard Mismatch zamanı `01:06:02.586`, Dragon başlatılma zamanı `01:06:02.587` (gecikme ~1 ms; adayın hazır bulunması sebebiyle bekleme yapılmadan doğrudan başlatıldı).
- **Sonuç**: **PASS**.

### T5: Gerileme Testleri (4 Film)
1. **Yüzüklerin Efendisi: İki Kule**
   - Açılan kaynak: `Imu` (ha.vixolity.com)
   - Dublajlı: Evet (Türkçe dublaj)
   - Süre: 235.4 dk (TMDB: 236 dk, Ok)
   - Doğru film: Evet (New Line Cinema açılışı ve Dumanlı Dağlar)
   - Günlük: `Bogard (31.2 dk -> Mismatch)` -> `Clover (31.2 dk -> Mismatch)` -> `Imu (235.4 dk -> Ok)`
   - Kanıt: `docs/dogrulama-beta/t5_iki_kule.png` (365.4 KB).
2. **Yüzüklerin Efendisi: Kralın Dönüşü**
   - Açılan kaynak: `Ace` (prx-1353-ant-23.vmpx.online)
   - Dublajlı: Altyazılı başladı, ardından dublajlı kaynak hazır olunca kart önerisi çıktı ("Dublajlı kaynak bulundu, geçilsin mi?").
   - Süre: 263.2 dk (TMDB: 263 dk, Ok)
   - Doğru film: Evet (Return of the King intro)
   - Günlük: `Ace: ilk kare, süre=263.2 dk beklenen=263 dk -> Ok`
   - Kanıt: `docs/dogrulama-beta/t5_kralin_donusu.png` (312.1 KB).
3. **Dune (2021)**
   - Açılan kaynak: `Ace` (prx-1546-ant.vmpx.online)
   - Dublajlı: Altyazılı başladı, dublaj kartı çıktı.
   - Süre: 149.2 dk (TMDB: 155 dk, Ok)
   - Doğru film: Evet (Açılış cümlesi: "Rüyalar derinlerden gelen haberlerdir.")
   - Günlük: `Ace: ilk kare, süre=149.2 dk beklenen=155 dk -> Ok`
   - Kanıt: `docs/dogrulama-beta/t5_dune.png` (54.5 KB).
4. **Joker (2019)**
   - Açılan kaynak: `Gaban` (nicheauthorityengine.site)
   - Dublajlı: Altyazılı başladı, dublaj kartı çıktı.
   - Süre: 121.8 dk (TMDB: 122 dk, Ok)
   - Doğru film: Evet (Warner Bros logosu ve radyo açılış repliği)
   - Günlük: `Gaban: ilk kare, süre=121.8 dk beklenen=122 dk -> Ok`
   - Kanıt: `docs/dogrulama-beta/t5_joker.png` (108.9 KB).

### T6: CloudStream Eklenti Hata Mesajı
- **Durum**: Cihazdaki veritabanında `CLOUDSTREAM_REPO` kaydı bulunmamaktadır (toplam: 0).
- **Sonuç**: **DOĞRULANAMADI: CloudStream eklentisi yok**.
- **Kanıt**: `docs/dogrulama-beta/t6_cs_durum.png` (203.6 KB).

---
## Ekran Görüntüleri Özeti (En çok 8 görüntü, her biri <= 600 KB)
1. `t1_yuklu_eklentiler.png` (92.5 KB)
2. `t2_zaman_cizgisi.png` (487.3 KB)
3. `t3_yuzuk_kardesligi.png` (155.3 KB)
4. `t5_iki_kule.png` (365.4 KB)
5. `t5_kralin_donusu.png` (312.1 KB)
6. `t5_dune.png` (54.5 KB)
7. `t5_joker.png` (108.9 KB)
8. `t6_cs_durum.png` (203.6 KB)
