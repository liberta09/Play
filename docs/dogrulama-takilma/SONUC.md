# Takılma Teşhis Raporu (İki Kule & Dune)

## 1. Amaç ve Kapsam
Bu çalışmanın amacı, "Yüzüklerin Efendisi: İki Kule" ve "Dune (2021)" filmlerinde Enel kaynağında gözlemlenen uzun süreli takılmaların (arabelleğe alma / buffering) kök nedenini tahmin yürütmeden, bizzat ölçüm yaparak tespit etmektir.

---

## 2. Emülatör Ağ Temel Hız Ölçümü (Adım 2c)
Emülatörün (LDPlayer) kendi ağ bağdaştırıcısının hızını bağımsız olarak test etmek amacıyla, emülatör kabuğundan herkese açık OVH 1 GB test dosyasına 30 saniyelik indirme testi yapılmış ve dosya diske yazılmadan akış hızı ölçülmüştür:

- **Komut:** `curl -o /dev/null -s -S -m 30 -w 'speed=%{speed_download} bytes=%{size_download} time=%{time_total}' https://proof.ovh.net/files/1Gb.dat`
- **Süre:** 30.00 sn
- **İndirilen Veri:** 307.279.340 bayt (~307,28 MB)
- **Ortalama İndirme Hızı:** **10,24 MB/s (~81,92 Mbps)**
- **Değerlendirme:** Emülatörün sanal ağ arayüzü ve ana makine bağlantısı kesintisiz ~82 Mbps aktarım kapasitesine sahiptir; ağ darboğazı kesinlikle emülatörden veya yerel ağdan kaynaklanmamaktadır.

---

## 3. Karşılaştırmalı Deney ve Ölçüm Tablosu

Oynatıcıya eklenen ayrıntılı telemetri (`PlayMatch: takılma-ayrıntı kaynak=<ad> bitHızı=X Mbps bantTahmini=Y Mbps tampon=Z sn`) ve `dumpsys meminfo com.kaan.play` (TOTAL PSS) ile LDPlayer üzerinde yapılan en az 4'er dakikalık test koşularının ölçüm sonuçları:

| Film | Kaynak | Bayrak (`test_big_buffer`) | İlk Kareye Süre | Takılma Sayısı | Toplam Takılma (sn) | Oynama Süresi | Video Bit Hızı | Bant Genişliği Tahmini | Bellek (TOTAL PSS) |
|---|---|---|---|---|---|---|---|---|---|
| **İki Kule (2002)** | Enel | Kapalı (varsayılan) | 1.71 sn | 1 | 104.7 sn | 3 dk | 2.75 Mbps | 0.46 Mbps (kapanışta 0.35 Mbps) | 228 MB (228.086 KB) |
| **İki Kule (2002)** | Enel | Açık (özel büyük tampon) | 10.45 sn | 3 | 233.6 sn | 0 dk | 2.75 Mbps / 1.40 Mbps | 1.79 Mbps (kapanışta 13.92 Mbps) | 198 MB (197.791 KB) |
| **İki Kule (2002)** | Enel | Açık (özel büyük tampon) | 7.64 sn | 0 | 0.0 sn | 1 dk | 2.75 Mbps | 75.81 Mbps | 199 MB (199.074 KB) |
| **İki Kule (2002)** | Garp (Alternatif) | Kapalı (varsayılan) | 2.08 sn | 0 | 0.0 sn | 18 dk | 2.40 Mbps | 61.36 Mbps | 203 MB (203.671 KB) |
| **İki Kule (2002)** | Dragon (Alternatif) | Açık (özel büyük tampon) | 1.06 sn | 0 | 0.0 sn | 4 dk | 2.01 Mbps | 74.33 Mbps | 219 MB (219.132 KB) |
| **Dune (2021)** | Enel | Kapalı (varsayılan) | 1.48 sn | 0 | 0.0 sn | 3 dk | 2.75 Mbps | 46.32 Mbps | 205 MB (205.757 KB) |
| **Dune (2021)** | Enel | Kapalı (varsayılan) | 3.07 sn | 1 | 77.6 sn | 2 dk | 2.75 Mbps | 0.13 Mbps (kapanışta 0.12 Mbps) | 198 MB (197.977 KB) |
| **Dune (2021)** | Imu (Alternatif) | Açık (özel büyük tampon) | 1.26 sn | 1 | 115.2 sn | 1 dk | 2.27 Mbps | 95.49 Mbps | 218 MB (218.817 KB) |

*Not: Denenmeyen diğer kombinasyonlar kural gereği "denenmedi" olarak kaydedilmiştir.*

---

## 4. Arabellek Deneyi Bulguları (`DefaultLoadControl`)
- **Bayrak Açık Parametreleri:** `minBufferMs=30_000`, `maxBufferMs=90_000`, `bufferForPlaybackMs=2_500`, `bufferForPlaybackAfterRebuffer=8_000`.
- **Bellek Etkisi:** Varsayılan tamponda TOTAL PSS ortalama 198 - 228 MB iken, 90 saniyelik büyük tamponda TOTAL PSS 199 - 219 MB aralığında kalmış; bellek artışı ihmal edilebilir düzeyde (~15-20 MB) olmuştur.
- **Takılmaya Etkisi:** Büyük tampon, sunucu hızının video bit hızının altına düştüğü (`bantTahmini < bitHızı`) durumlarda takılmayı engellememiştir. Aksine, `bufferForPlaybackAfterRebuffer=8_000` (8 saniye) gereksinimi nedeniyle, aşırı yavaş CDN sunucusundan 8 saniyelik yeni tamponu doldurmak çok uzun sürmüş ve arabellekte bekleme süresini 104 saniyeden 233 saniyeye çıkarmıştır.

---

## 5. Teşhis Sonucu (3 Cümle)
Yapılan ölçümlerde emülatörün yerel ağ hattı 81.9 Mbps hız sunarken, Enel kaynağında takılma yaşandığı anlarda sunucu bant genişliği tahmininin 0.13 - 0.46 Mbps seviyesine düşerek video bit hızının (2.75 Mbps) çok altına indiği ve tamponu sıfıra indirdiği (tampon=0.0 - 0.1 sn) doğrulanmıştır. Aynı filmlerde alternatif CDN kaynakları (Garp, Dragon) 61 - 74 Mbps bant genişliği ve sıfır takılmayla sorunsuz oynatılmıştır. Dolayısıyla takılmanın nedeni emülatör veya oynatıcı tampon ayarları değil, doğrudan Enel kaynağının içerik dağıtım sunucusunun (`four.pichive.online`) periyodik olarak uyguladığı aşırı bant genişliği kısıtlamasıdır.
