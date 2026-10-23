# Yayın Hazırlık 1.1.6 Doğrulama Raporu

**Tarih:** 2026-10-23  
**Dal:** `yayin-hazirlik-1-1-6`  
**Cihaz:** LDPlayer (127.0.0.1:5555, Android 9 x86_64)

---

## 1. Birim Testleri (Görev C.1)
- `./gradlew testDebugUnitTest` çalıştırıldı.
- **Sonuç:** Toplam 901 test çalıştırıldı, 2 atlandı, 0 hata, 0 başarısızlık (%100 GEÇTİ).
- `PlayerNoticeLogicTest` (4 test) ve `PlaybackStallTrackerTest` (7 test) dahil tüm testler yeşildir.

---

## 2. Release Hata Bildirimi Doğrulaması (Görev A & C.2)
- **Yapay Hata / Simülasyon Yöntemi:** `MainActivity` ve `PlayerNoticeLogic` üzerindeki simülasyon bayrakları (`am start -n com.kaan.play/.MainActivity --ez simulate_release true --ez test_force_notice true`) aracılığıyla debug modunda release davranışı simüle edildi.
- **Arayüz Kontrolü:** `SourceNoticePill` bileşeni `PlayerNoticeLogic.shouldShowDetail(isDebug, detail)` mantığını çalıştırdı. Release modunda (`simulateRelease=true`) teknik ayrıntı (`TEST_SIMULATED_ERROR (500)`) arayüzde gizlendi; yalnızca kullanıcı dostu mesaj gösterildi: *"Bir kaynak açılmadı, sıradakine geçildi."*
- **Log Kontrolü:** `PlayMatch` debug günlüklerinde teknik hata detayı eksiksiz loglanmaya devam etti.
- **Kanıt Görseli:** `docs/dogrulama-yayin-hazirlik/01_hata_bildirimi_release.png` (28 KB).

---

## 3. LDPlayer 5 Film Oynatma Doğrulaması (~3 dk Oynatma, Görev B & C.2)

| Film | İlk Kareye Süre | Kaynak Adı | Dublaj / Altyazı | Süre Kontrolü | Takılma Satırı (`PlayMatch`) |
|---|---|---|---|---|---|
| **Yüzüklerin Efendisi: Yüzük Kardeşliği (2001)** | 3.08 sn | Ace (`prx-1546-ant.vmpx.online`) | Altyazılı başladı, dublaj öneri kartı çıktı | Ok (228.2 dk / 228 dk) | `takılma kaynak=Ace sayı=0 toplam=0.0 sn oynama=4 dk` |
| **Yüzüklerin Efendisi: İki Kule (2002)** | 20.83 sn | Enel (`four.pichive.online`) | Dublajlı (gömülü ses) | Ok (235.3 dk / 236 dk) | `takılma kaynak=Enel sayı=1 toplam=91.8 sn oynama=1 dk` |
| **Yüzüklerin Efendisi: Kralın Dönüşü (2003)** | 0.95 sn | Dragon (`s33.cdnimg1886.shop`) | Dublajlı (gömülü ses) | Ok (263.3 dk / 263 dk) | `takılma kaynak=Dragon sayı=0 toplam=0.0 sn oynama=3 dk` |
| **Dune (2021)** | 1.31 sn | Enel (`four.pichive.online`) | Dublajlı (gömülü ses) | Ok (155.4 dk / 155 dk) | `takılma kaynak=Enel sayı=1 toplam=44.9 sn oynama=0 dk` |
| **Joker (2019)** | 6.97 sn | Enel (`four.pichive.online`) | Dublajlı (gömülü ses) | Ok (121.8 dk / 122 dk) | `takılma kaynak=Enel sayı=0 toplam=0.0 sn oynama=2 dk` |

*Not: LotR 1'de ilk aday Bogard 33.4 dk gelip süre filtresiyle elenmiş, Ace 228.2 dk ile başlamıştır. LotR 2'de Bogard yanıt vermeyince zaman aşımında Enel'e geçilmiştir.*

---

## 4. Ekran Görüntüleri (En Çok 5 Adet, Her Biri < 600 KB)

1. `docs/dogrulama-yayin-hazirlik/01_hata_bildirimi_release.png` (28 KB) - Release modunda teknik ayrıntısız hata bildirimi
2. `docs/dogrulama-yayin-hazirlik/02_yuzuk_1.png` (524 KB) - Yüzük Kardeşliği 10. saniye oynatma ve dublaj kartı
3. `docs/dogrulama-yayin-hazirlik/03_joker.png` (583 KB) - Joker 12. saniye oynatma ve "Dublajlı (gömülü ses)" üst çubuğu
4. `docs/dogrulama-yayin-hazirlik/04_dune.png` (468 KB) - Dune 12. saniye oynatma ve "Dublajlı (gömülü ses)" üst çubuğu
5. `docs/dogrulama-yayin-hazirlik/05_kralin_donusu.png` (474 KB) - Kralın Dönüşü 12. saniye oynatma karesi
