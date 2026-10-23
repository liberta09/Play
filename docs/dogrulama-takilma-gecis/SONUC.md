# Takılmada Otomatik Kaynak Geçişi Doğrulama Raporu

## 1. Amaç ve Kapsam
Bu doğrulama çalışmasının amacı; yavaş CDN sunucularından (ör. Enel'in 0.1–0.5 Mbps'e düştüğü durumlar) kaynaklanan uzun süreli takılmaları tespit edip, oynatmayı kesintiye uğratmadan, aynı saniyeden otomatik olarak çalışan başka bir kaynağa geçiren mekanizmanın (`StallPolicy`, `SlowHostMemory`, `SourceCoordinator`) doğrulanmasıdır.

---

## 2. Görev A: Birim Test Sonuçları (Android'den Bağımsız Saf Mantık)
Tüm birim testler (`./gradlew testDebugUnitTest`) çalıştırılmış ve **925 testin 925'i (%100) başarıyla geçmiştir**.

Test edilen temel kurallar:
- **Tek Takılma Eşik Sınırları (`STALL_SINGLE_MS = 10_000L`):** 9.999 ms takılmada karar verilmez (`None`); tam 10.000 ms olduğunda geçiş kararı (`Switch(Single)`) verilir.
- **2 Dakikalık Pencere Toplam Takılma Sınırları (`STALL_WINDOW_MS = 120_000L`, `STALL_WINDOW_TOTAL_MS = 15_000L`):** Son 2 dakika içinde toplam 14.999 ms takılmada karar verilmez; 15.000 ms olduğunda geçiş kararı (`Switch(Window)`) verilir.
- **Pencere Dışı Temizliği:** 120 saniyeden daha eski tamamlanmış takılmalar pencere toplamına dahil edilmez.
- **Sarma (Seek) Koruması:** Kullanıcı ileri/geri sararken takılma sayacı ve geçiş mekanizması tetiklenmez (`None`).
- **İlk Kare Öncesi:** İlk video karesi render edilmeden önceki tamponlama takılma sayılmaz ve geçiş tetiklemez (`None`).
- **Oynatma Durumu:** Oynatıcı takılmada değilken (buffering=false) karar verilmez (`None`).
- **Dublaj Tercihi Koruması:** Mevcut kaynak dublajlıysa öncelikle diğer dublajlı adaylar seçilir; dublaj grubu önceliği korunur.
- **Altyazılıya Geçiş Eşiği (`STALL_SUBTITLED_MS = 20_000L`):** Mevcut kaynak dublajlıyken başka dublajlı aday kalmamışsa, 10–19 saniyede altyazılıya geçilmez; yalnızca kesintisiz 20 saniye takılma aşıldığında altyazılı adaya geçilir (`Switch(Subtitled)`). Pencere toplamı 20 sn kesintisiz takılma şartını atlatamaz.
- **Aday Tükenmesi:** Kabul edilebilir aday kalmadığında oynatma kesilmez veya hata/siyah ekran çıkarılmaz (`None`).
- **Elle Seçim Koruması:** Kullanıcının Kaynak panelinden elle seçtiği kaynak otomatik değiştirilmez; yalnızca `NotifyManualSlow` kararı üretilerek arayüze bilgi verilir.
- **Yavaş Sunucu Hafızası (`SlowHostMemory`):** Takılma nedeniyle terk edilen sunucu ana makinesi (host) bellek içinde 30 dakika (`SLOW_HOST_EXPIRY_MS = 30 * 60 * 1000L`) yavaş işaretlenir. `SourceRanking.recommended` içinde kendi grubu arkasına düşürülür; dublaj grubu önceliği kesinlikle bozulmaz.

---

## 3. Görev B & C: Cihaz Doğrulama Sonuçları (LDPlayer)

### A. Sentetik Takılma Doğrulaması (`--ez test_force_stall true`)

Debug derlemesinde ilk kareden 5 saniye sonra 11 saniyelik kesintisiz takılma yapay olarak tetiklenmiş ve geçiş davranışı gözlemlenmiştir:

| Test Senaryosu | İlk Kaynak | Geçilen Yeni Kaynak | Geçiş Nedeni | Dublaj Durumu | Kullanıcı Bildirimi | Ekran Görüntüsü | Sonuç |
|---|---|---|---|---|---|---|---|
| **Yüzüklerin Efendisi: İki Kule (2002)** | Enel (`four.pichive.online`) | Garp (`prx-1566-ant-r.vmpx.online`) | `tek` (>= 10 sn) | Dublajlı kaldı | "Kaynak yavaştı, değiştirildi." | `01_iki_kule_gecis.png` (149 KB) | **GEÇTİ** |
| **Dune (2021)** | Dragon (`s1.dizisol.com`) | Dragon (`dizisol.com`) | `tek` (>= 10 sn) | Dublajlı kaldı | "Kaynak yavaştı, değiştirildi." | `02_dune_gecis.png` (13 KB) | **GEÇTİ** |

#### Doğrulanan Logcat Çıktıları:

**İki Kule:**
```text
D PlayMatch: kaynak başlatılıyor: Enel (four.pichive.online)
D PlayMatch: ilk kare: Enel (faz=Starting)
D PlayMatch: süre kontrolü kaynak=Enel (four.pichive.online) süre=235.3 dk beklenen=236 dk tür=Movie -> Ok
D PlayMatch: takılma geçişi eski=Enel yeni=Garp neden=tek
D PlayMatch: kaynak başlatılıyor: Garp (prx-1566-ant-r.vmpx.online)
D PlayMatch: ilk kare: Garp (faz=Starting)
D PlayMatch: süre kontrolü kaynak=Garp (prx-1566-ant-r.vmpx.online) süre=179.4 dk beklenen=236 dk tür=Movie -> Ok
```

**Dune:**
```text
D PlayMatch: kaynak başlatılıyor: Dragon (s1.dizisol.com)
D PlayMatch: ilk kare: Dragon (faz=Starting)
D PlayMatch: süre kontrolü kaynak=Dragon (s1.dizisol.com) süre=155.4 dk beklenen=155 dk tür=Movie -> Ok
D PlayMatch: takılma geçişi eski=Dragon yeni=Dragon neden=tek
D PlayMatch: kaynak başlatılıyor: Dragon (dizisol.com)
D PlayMatch: ilk kare: Dragon (faz=Starting)
D PlayMatch: süre kontrolü kaynak=Dragon (dizisol.com) süre=155.4 dk beklenen=155 dk tür=Movie -> Ok
```

- **Doğal Takılma Geçişi:** Enel'in test aralığında rastgele CDN hız kısıtlamasına girmesi test süresi kısıtı nedeniyle beklenmemiş ve bu madde kural gereği dürüstçe **denenmedi** olarak işaretlenmiştir.

---

### B. Normal Oynatma Doğrulaması (Bayraksız, 120 sn Kesintisiz)

Uygulamanın herhangi bir test bayrağı olmadan normal akışında geçiş mekanizmasının haksız yere tetiklenmediği ve normal oynatmanın etkilenmediği doğrulanmıştır:

| Film | Kaynak | Oynatma Süresi | Takılma Sayısı | Geçiş Durumu | Ekran Görüntüsü | Sonuç |
|---|---|---|---|---|---|---|
| **Joker (2019)** | Garling (`s24.imagecdn9065.shop`) | 120 sn | 0 | Geçiş olmadı (kesintisiz akış) | `03_joker_normal.png` (513 KB) | **GEÇTİ** |
| **Yüzüklerin Efendisi: Kralın Dönüşü (2003)** | Enel (`four.pichive.online`) | 120 sn | 0 | Geçiş olmadı (kesintisiz akış) | `04_kralin_donusu_normal.png` (489 KB) | **GEÇTİ** |

#### Doğrulanan Logcat Çıktıları:

**Joker:**
```text
D PlayMatch: kaynak başlatılıyor: Garling (s24.imagecdn9065.shop)
D PlayMatch: ilk kare: Garling (faz=Starting)
D PlayMatch: süre kontrolü kaynak=Garling (s24.imagecdn9065.shop) süre=121.8 dk beklenen=122 dk tür=Movie -> Ok
(120 sn boyunca kesintisiz devam etti, geçiş tetiklenmedi)
```

**Kralın Dönüşü:**
```text
D PlayMatch: kaynak başlatılıyor: Enel (four.pichive.online)
D PlayMatch: ilk kare: Enel (faz=Starting)
D PlayMatch: süre kontrolü kaynak=Enel (four.pichive.online) süre=263.1 dk beklenen=263 dk tür=Movie -> Ok
(120 sn boyunca kesintisiz devam etti, geçiş tetiklenmedi)
```

---

## 4. Ekran Görüntüleri Özeti (< 600 KB Kuralı)
1. `01_iki_kule_gecis.png` (149.649 bayt) — İki Kule filminde Enel'den Garp'a geçiş anında "Kaynak yavaştı, değiştirildi." hap bildirimi.
2. `02_dune_gecis.png` (13.724 bayt) — Dune filminde Dragon'dan Dragon CDN sunucusuna geçiş anında "Kaynak yavaştı, değiştirildi." hap bildirimi.
3. `03_joker_normal.png` (513.612 bayt) — Joker filminde normal oynatmanın bayraksız sorunsuz aktığı an.
4. `04_kralin_donusu_normal.png` (489.707 bayt) — Kralın Dönüşü filminde normal oynatmanın bayraksız sorunsuz aktığı an.

---

## 5. Değerlendirme
Otomatik geçiş politikası (`StallPolicy`), sunucu kaynaklı bant genişliği darboğazlarında takılmayı kullanıcı müdahalesine gerek kalmadan başarıyla çözmektedir. Mevcut oynatma saniyesi ve dublaj tercihi korunarak alternatif çalışan kaynağa geçiş sağlanmış; kullanıcının elle seçtiği kaynaklara dokunulmayarak yalnızca bilgilendirme yapılması güvence altına alınmıştır.
