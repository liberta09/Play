# Dublaj Bekleme Süresi — Cihaz Doğrulama Raporu (LDPlayer)

Dal: `fix-dublaj-bekleme`. Test cihazı: LDPlayer (127.0.0.1:5555, 1600x900). Tarih: 2026-10-23.
Açılış dublaj beklemesi: `StartPolicy.DUB_WAIT_MS = 8_000L` (8 sn).

## Özet Tablo

| Film | İlk Kareye Süre | Kaynak | Dublajlı mı | Kart Çıktı mı | Süre Kontrolü |
|---|---|---|---|---|---|
| Yüzüklerin Efendisi: Kralın Dönüşü (2003) | 6.5 sn | Ace (prx-1564-ant-q.vmpx.online) | Hayır (Altyazılı) | Evet (27.8 sn sonra) | 263.2 dk / 263 dk -> Ok |
| Dune (2021) | 8.1 sn | Enel (four.pichive.online) | **Evet (Dublajlı)** | Hayır (Gerek kalmadı) | 155.4 dk / 155 dk -> Ok |

---

## 1. Yüzüklerin Efendisi: Kralın Dönüşü (2003)
- **İşlem**: Detay > Oynat (`02:15:16.649`).
- **Kaynak Başlatma & Süre Kontrolü**:
  - İlk denenen kaynak: `Bogard (streamcorecdn.com)` -> Süre: 51.7 dk / beklenen 263 dk -> `Mismatch` (elendi).
  - Yedeğe geçiş: `Ace (prx-1564-ant-q.vmpx.online)` başlatıldı (`02:15:19.758`).
  - İlk kare: `02:15:23.164` (Oynat basıldıktan ilk kareye geçen süre: **6.515 sn**).
  - Süre kontrolü: `263.2 dk beklenen=263 dk -> Ok`.
- **Dublajlı mı Başladı**: Hayır, altyazılı başladı.
- **Dublaj Kartı**: Evet, oynatıcıda 10. saniyede "Dublajlı kaynak bulundu, geçilsin mi? [Geç] [Kapat]" kartı çıktı.
- **Gecikme Analizi**: Dublajlı kaynaklar (`Xebec`: `02:15:44.461`, `Shanks`: `02:15:45.777`) Oynat basıldıktan **27.8 sn** sonra geldi. 8 saniyelik açılış dublaj bekleme süresinden çok daha geç hazır oldukları için altyazılı kaynak başlatılmış ve dublaj sonradan kart önerisi olarak sunulmuştur.
- **Ekran Görüntüsü**: `docs/dogrulama-dublaj-bekleme/kralin_donusu_10s.png` (565 KB, Sméagol açılış sahnesi ve dublaj geçiş kartı).

---

## 2. Dune (2021)
- **İşlem**: Detay > Oynat (`02:22:18.246`).
- **Kaynak Başlatma & Süre Kontrolü**:
  - İlk denenen kaynak: `Clover (cdn2.blueflix.top)` -> `ERROR_CODE_IO_BAD_HTTP_STATUS (2004) http=500` hatası aldı (`02:22:24.070`).
  - Yedeğe geçiş: Dublajlı aday olan `Enel (four.pichive.online)` başlatıldı (`02:22:24.071`).
  - İlk kare: `02:22:26.308` (Oynat basıldıktan ilk kareye geçen süre: **8.062 sn**).
  - Süre kontrolü: `155.4 dk beklenen=155 dk -> Ok`.
- **Dublajlı mı Başladı**: **EVET** (Üst çubuk: `Enel · 1920×804 · Dublajlı (gömülü ses)`).
- **Dublaj Kartı**: Hayır (zaten dublajlı başladığı için gerek kalmadı).
- **İyileşme Kanıtı**: Önceki beta testinde 3 sn sınırı yüzünden altyazılı başlayan Dune, 8 sn kuralı sayesinde dublajlı (`Enel`) başladı.
- **Ekran Görüntüsü**: `docs/dogrulama-dublaj-bekleme/dune_10s.png` (243 KB, çöl açılış sahnesi, "Dublajlı (gömülü ses)" ibaresi).
