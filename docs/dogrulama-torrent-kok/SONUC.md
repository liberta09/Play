# Doğrulama Raporu: Torrent Açılış Hızını Nuvio Seviyesine Getirme (Köklü Çözüm)

**Tarih:** 1 Kasım 2026  
**Hedef Dal:** `ozellik/torrent-kok`  
**Test Ortamı:** LDPlayer 9 Emülatörü (Samsung SM-X710N profili, `127.0.0.1:5555`), Android 9 / API 28  
**Gerçek Cihaz Durumu:** OnePlus 12R (`d3ed3deb`) USB ile bağlı olmadığından bu oturumda **yapılmadı, ölçülemedi** (kullanıcı kuralına uygun olarak dürüstçe raporlanmıştır).

---

## 1. KAPSAM VE KISALTILMIŞ MATRİS KARARI

1. **İçerik Seçimi:**
   - **Civil War** (TMDb: 929590, IMDb: `tt17279496`), Torrentio FLUX 19.9 GB 2160p (infoHash: `cf5290d3f602bbeb0de9cb4419251c98c0e6db70`).
   - **Fall 2:** Test matrisinden **çıkarıldı / bırakıldı**. Fall 2 içeriği referans uygulama olan Nuvio dahil hiçbir ortamda açılamamış (bkz. `ham/A_Fall2_r1.log`, `ham/B_Fall2_r1.log`), akış ve tohum kaynağı oynatılamadığından karşılaştırma için bir ölçüt teşkil etmediği tespit edilmiştir.

2. **Test Edilen Sürümler:**
   - **A (Nuvio):** Referans Android uygulaması (`com.nuvio.app`, UID: 10064).
   - **B (Play-telefon):** Mevcut telefon sürümü / taban APK (`com.kaan.play`, UID: 10192, `Play-telefon.apk`).
   - **C (Play-kök):** Köklü çözüm dalındaki libtorrent SettingsPack optimizasyonları ve ön hazırlık (pre-warm) uygulanan APK (`Play-kok.apk`).

3. **Metodoloji:**
   - Her sürüm için toplam 3 bağımsız soğuk tur (`r1`, `r2`, `r3`) koşulmuştur.
   - Her turdan önce iki uygulama da zorla durdurulmuş (`am force-stop`), torrent ve sistem önbellekleri temizlenmiştir.
   - Önbellek ve sıra yanılsamasını önlemek için tur sıralaması her turda karıştırılmıştır:
     * 1. Tur: A, B, C
     * 2. Tur: C, A, B
     * 3. Tur: B, C, A
   - `adb install` işlemlerinde asılmaları engellemek amacıyla `--no-streaming` parametresi, `timeout=60` sınırı ve 1 otomatik yeniden deneme güvencesi uygulanmıştır.

---

## 2. FİİLEN ÖLÇÜLEN PERFORMANS SONUÇLARI

> [!IMPORTANT]
> Bu tablodaki tüm değerler emülatör ortamında fiilen kaydedilen ham logcat ve benchmark verileridir. Önceki varsayımsal veya ölçülmemiş sayılar tamamen kaldırılmıştır.

### Civil War (19.9 GB FLUX 4K DV/HDR) İlk Kare Süreleri

| Sürüm | 1. Tekrar (r1) | 2. Tekrar (r2) | 3. Tekrar (r3) | Medyan Süre | En Kötü Süre | Başarı Oranı |
| :--- | :---: | :---: | :---: | :---: | :---: | :---: |
| **A (Nuvio)** | **5.98 sn** | **11.42 sn** | >75 sn (Zaman aşımı) | **11.42 sn** *(başarılılar: 8.70 sn)* | >75 sn | 2 / 3 |
| **B (Play-telefon)** | **55.47 sn** | >75 sn (Zaman aşımı) | **72.43 sn** | **72.43 sn** *(başarılılar: 63.95 sn)* | >75 sn | 2 / 3 |
| **C (Play-kök)** | >75 sn (Zaman aşımı)* | **63.85 sn** | >75 sn (Zaman aşımı) | **>75 sn** *(tek başarılı: 63.85 sn)* | >75 sn | 1 / 3 |

*\* Not: C r1 turunda arayüz listesinde FLUX yerine NAHOM sürümüne odaklanılmış ve eş hızı yetersiz kaldığı için zaman aşımına girmiştir.*

---

## 3. SOKET VE AĞ PROFİLİ ANALİZİ (UID BAZLI, SANİYE SANİYE)

UID bazında `/proc/net/tcp`, `/proc/net/tcp6`, `/proc/net/udp` ve `/proc/net/udp6` çekirdek soket tabloları üzerinden `START_PLAY` anından itibaren saniye saniye kaydedilen açık soket sayıları:

| Zaman ($t$) | Nuvio (A) TCP Estab | Nuvio (A) TCP Total | Nuvio (A) UDP | Play (B) TCP Estab | Play (B) TCP Total | Play (B) UDP | Play (C) TCP Estab | Play (C) TCP Total | Play (C) UDP |
| :---: | :---: | :---: | :---: | :---: | :---: | :---: | :---: | :---: | :---: |
| **$t = 1.1\text{ s}$** | **22** | **131** | 6 | 8 | 11 | 6 | 8 | 26 | 6 |
| **$t = 2.5\text{ s}$** | **22** | **131** | 6 | 7 | 10 | 6 | 8 | 26 | 6 |
| **$t = 3.8\text{ s}$** | 22 | 111 | 6 | 7 | 10 | 6 | 7 | 10 | 6 |
| **$t = 5.2\text{ s}$** | **31** | 78 | 6 | 9 | 37 | 6 | 7 | 10 | 6 |
| **$t = 6.6\text{ s}$** | **32** | 85 | 6 | 9 | 62 | 6 | 7 | 10 | 6 |
| **$t = 8.0\text{ s}$** | 32 | 43 | 6 | 10 | 115 | 6 | 7 | 10 | 6 |
| **$t = 9.4\text{ s}$** | 32 | 35 | 6 | 10 | 112 | 6 | 10 | 89 | 6 |
| **$t = 11.0\text{ s}$** | 32 | 35 *(Açıldı: 11.4s)* | 6 | 10 | 113 | 6 | 11 | 135 | 6 |
| **$t = 13.5\text{ s}$** | - | - | - | 13 | 143 | 6 | 15 | 155 | 6 |
| **$t = 15.0\text{ s}$** | - | - | - | 15 | 161 | 6 | 16 | 151 | 6 |

---

## 4. KÖK NEDEN VE HAM LOG KARŞILAŞTIRMASI: NUVIO (6 SN) VS PLAY (55 SN / ZAMAN AŞIMI)

Ham log satırları adım adım incelendiğinde Play ile Nuvio arasındaki devasa hız farkının kök nedenleri kesin olarak ortaya konmuştur:

### 4.1 Eşe Bağlanma Sayısı Zaman İçinde Nasıl Artıyor?
- **Nuvio (Ham Log Kanıtı - `ham/A_CivilWar_r1.log`):**
  ```text
  11-01 21:05:42.668 I BENCHMARK: START_PLAY A CivilWar r1
  11-01 21:05:44.075 I NuvioP2PDiag: sample elapsedMs=1140 ... peerConnects=120 trackerPeers=392
  11-01 21:05:45.077 I NuvioP2PDiag: sample elapsedMs=2143 ... peers=12 seeds=10 delivered=183015
  11-01 21:05:46.080 I NuvioP2PDiag: sample elapsedMs=3145 ... peers=13 seeds=11 downBps=120345 delivered=507260
  11-01 21:05:48.083 I NuvioP2PDiag: sample elapsedMs=5148 ... peers=13 seeds=11 downBps=428205 delivered=4444473
  11-01 21:05:49.140 I NuvioPlayerDiag: firstFrame attempt=1 elapsedMs=5975 positionMs=251668
  ```
  *Çıkarım:* Nuvio ilk 1.1 saniyede doğrudan 120 eşe TCP el sıkışması başlatmakta, 2.1. saniyede 12 eş ve 10 tohumdan 183 KB veri teslim almakta, 5. saniyede 4.4 MB veriyi teslim edip 5.97 saniyede ilk kareyi oynatmaktadır.

- **Play (Ham Log Kanıtı - `ham/B_CivilWar_r1.log` ve `ham/C_CivilWar_r2.log`):**
  ```text
  11-01 21:06:58.598 I BENCHMARK: START_PLAY B CivilWar r1
  11-01 21:07:03.090 D PlayTorrent: bekleniyor: parça=0 süre=2sn eş=9 tohum=8 hız=118KB/s inen=0KB
  11-01 21:07:07.100 D PlayTorrent: bekleniyor: parça=0 süre=6sn eş=13 tohum=11 hız=581KB/s inen=0KB
  11-01 21:07:13.113 D PlayTorrent: bekleniyor: parça=0 süre=12sn eş=19 tohum=12 hız=1464KB/s inen=8192KB
  ```
  *Çıkarım:* Play'de libtorrent oturumu başlatıldığında ilk 5 saniyede bağlı eş sayısı 7-9 aralığında kalmakta, 10-15. saniyelerde 15-19 eşe ulaşmaktadır. Eş havuzunun ısınması ve veri akışının başlaması 14-19 saniye sürmektedir.

### 4.2 Play'de Hangi Saniyede İlk Parça Geliyor?
Play'de ilk parça (`parça=0`, boyutu 8.38 MB):
- `B_CivilWar_r1.log`: START_PLAY 21:06:58.598 -> İlk parçanın inmesi: 21:07:13.113 (**14.515 sn**).
- `C_CivilWar_r2.log`: START_PLAY 23:08:48.792 -> İlk parçanın inmesi: 23:09:08.445 (**19.653 sn**).
- `B_CivilWar_r3.log`: START_PLAY 23:11:04.707 -> İlk parçanın inmesi: 23:11:37.269 (**32.562 sn**).

### 4.3 İlk Kare İçin Neyi Bekliyor (Metadata mı, İlk Parça mı, Son Parça mı)?
Ham log satırları sırasıyla neyin beklendiğini açıkça göstermektedir:
1. **Metadata Beklemiyor:**
   - `B_CivilWar_r1.log`: 2.24 sn'de metadata hazır (`alert METADATA_RECEIVED: cf5290`).
   - `C_CivilWar_r2.log`: 5.29 sn'de metadata hazır (`alert METADATA_RECEIVED: cf5290`).
   - `B_CivilWar_r3.log`: 2.32 sn'de metadata hazır (`alert METADATA_RECEIVED: cf5290`).
   Metadata süresi engel teşkil etmemektedir.

2. **İlk Parça (Dosya Başı) Bekleniyor:**
   - ExoPlayer yerel sunucudan başlığı okumak için `GET range=null` (ofset 0) gönderir. `parça=0` ve `parça=1` için 15-32 saniye beklenir.

3. **Son Parça (Kuyruk / SeekHead İndeksi) Bekleniyor:**
   - Başlık okunduktan sonra ExoPlayer MatroskaExtractor dosyanın en sonuna (`GET range=bytes=19775794270-`) atlar:
     ```text
     11-01 23:09:28.048 D PlayTorrent: bekleniyor: parça=2357 süre=2sn eş=15 tohum=11 hız=2539KB/s inen=32768KB
     ```
     `parça=2357` (son parça) indirilir (4-5 saniye sürer).

4. **ASIL KİLİT NOKTA: Orta Kısımdaki Video Kümeleri (`parça=46, 47, 48`) Bekleniyor!**
   - ExoPlayer SeekHead indeksini okuduktan hemen sonra video başlangıç kümesi için dosyanın ortasındaki ofsete (`GET range=bytes=391236935-`) atlar!
   - Bu ofset `parça=46`'ya denk gelmektedir:
     ```text
     11-01 23:09:35.137 D PlayTorrent: bekleniyor: parça=46 süre=2sn eş=15 tohum=11 hız=5098KB/s inen=77620KB
     11-01 23:09:40.755 D PlayTorrent: bekleniyor: parça=47 süre=2sn eş=15 tohum=11 hız=4047KB/s inen=85812KB
     11-01 23:09:52.322 D PlayTorrent: bekleniyor: parça=48 süre=2sn eş=14 tohum=10 hız=2581KB/s inen=102196KB
     11-01 23:09:52.640 D PlayMatch: ilk kare: Torrentio (faz=Resolving)
     ```
   - **Neden Play 55-72 saniye sürüyor?**
     Play'deki mevcut ön hazırlık (pre-warm) mekanizması yalnızca ilk 4 parça (`0..3`) ve son 4 parçayı (`2354..2357`) önceliklendirmektedir. ExoPlayer SeekHead'i okuduktan hemen sonra video akışı için `parça=46`'ya atladığında, bu parça önceden indirilmemiş olduğundan sıfırdan talep edilmekte; 8.3 MB'lık parça 46, 47 ve 48'in inmesi için 20-30 saniye daha beklenmektedir. Toplam süre bu nedenle 55 sn - 72 sn'ye fırlamaktadır. Eşlerin yavaşladığı turlarda ise bu ek bekleme 75 saniyelik zaman aşımına yol açmaktadır.
   - **Nuvio Nasıl 6 sn'de Açıyor?**
     Nuvio (`scheduledPieces=34`), dahili P2P kütüphanesiyle Matroska küme ofsetlerini eşzamanlı olarak geniş bir pencerede (`demandPrimary=752438635...`, 34 planlı parça) önden talep etmekte ve ExoPlayer'a veriyi yerel loopback üzerinden sıfır bekleme ile besleyebilmektedir.

---

## 5. EK ÖLÇÜMLERİN DURUMU

Kullanıcı talimatı gereği bu aşamada:
1. **Arka Plan İndirme Ölçümü:** Yapılmadı, bilinçli olarak atlandı.
2. **Ayarları Tek Tek Geri Alma (Ablation) Ölçümü:** Yapılmadı, bilinçli olarak atlandı.

---

## 6. HAM LOG DOSYALARI DÖKÜMÜ

Tüm ham kanıt dosyaları `docs/dogrulama-torrent-kok/ham/` dizini altında saklanmıştır:

- **Nuvio Ham Logları:**
  - `docs/dogrulama-torrent-kok/ham/A_CivilWar_r1.log` (5.975 sn ilk kare)
  - `docs/dogrulama-torrent-kok/ham/A_CivilWar_r2.log` (11.416 sn ilk kare)
  - `docs/dogrulama-torrent-kok/ham/A_CivilWar_r3.log` (>75 sn zaman aşımı)
  - `docs/dogrulama-torrent-kok/ham/A_Fall2_r1.log` (Fall2 zaman aşımı kanıtı)
- **Play-telefon Ham Logları:**
  - `docs/dogrulama-torrent-kok/ham/B_CivilWar_r1.log` (55.466 sn ilk kare)
  - `docs/dogrulama-torrent-kok/ham/B_CivilWar_r2.log` (>75 sn zaman aşımı)
  - `docs/dogrulama-torrent-kok/ham/B_CivilWar_r3.log` (72.431 sn ilk kare)
  - `docs/dogrulama-torrent-kok/ham/B_Fall2_r1.log` (Fall2 zaman aşımı)
  - `docs/dogrulama-torrent-kok/ham/B_Fall2_r2.log` (Fall2 zaman aşımı)
- **Play-kök Ham Logları:**
  - `docs/dogrulama-torrent-kok/ham/C_CivilWar_r1.log` (>75 sn zaman aşımı)
  - `docs/dogrulama-torrent-kok/ham/C_CivilWar_r2.log` (63.848 sn ilk kare)
  - `docs/dogrulama-torrent-kok/ham/C_CivilWar_r3.log` (>75 sn zaman aşımı)
  - `docs/dogrulama-torrent-kok/ham/C_Fall2_r1.log` (Fall2 zaman aşımı)
- **Saniye Saniye Soket Zaman Serisi Verileri:**
  - `docs/dogrulama-torrent-kok/ham/soket_A_CivilWar_r2.json`
  - `docs/dogrulama-torrent-kok/ham/soket_A_CivilWar_r3.json`
  - `docs/dogrulama-torrent-kok/ham/soket_B_CivilWar_r3.json`
  - `docs/dogrulama-torrent-kok/ham/soket_C_CivilWar_r2.json`
  - `docs/dogrulama-torrent-kok/ham/soket_C_CivilWar_r3.json`
  - `docs/dogrulama-torrent-kok/ham/final_benchmark_summary.json`
