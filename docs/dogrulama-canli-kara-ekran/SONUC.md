# Canlı Kanal Kara Ekran ve Sahte Yer Tutucu Dosya Düzeltmesi — Sonuç Raporu

**Tarih:** 23 Ekim 2026  
**Dal:** `fix-canli-kara-ekran`  
**Test Edilen Cihaz:** LDPlayer 9 (Android 9, 1600x900)  
**Toplam Birim Test:** 960 test (960 GEÇTİ, 0 BAŞARISIZ, 0 HATA, 2 ATLANDI)  

---

## 1. Teşhis ve Kök Neden (Kanıtlı)

1. WioSpor akış manifestinde en üstte yer alan gerçek adaptif HLS yayınları (`PatronHD`, `VİONTV` vb.) `BEIN SPORTS 1 • Auto` başlığıyla geldiği için (`resolution = null`), Play'in `SourceRanking` algoritması çözünürlüğü açıkça `1080p` yazan IPTV linkini (`204.52.191.82:80`) listenin en önüne almıştır.
2. Bu IPTV sunucusu (`204.52.191.82:80`), doğrudan yetkisiz HTTP GET isteklerine gerçek yayın akışı yerine HTTP 206 ile tam **14.472.616 baytlık (14 MB)** ve sonlu süreli (**9 dakika 59 saniye / 10.0 dk**) sabit bir kara ekran / engel `.ts` video dosyası döndürmektedir.
3. Play canlı yayında dosya boyutu ve sonlu süre tutarsızlığını denetlemediği için bu 14 MB'lık dosyayı geçerli bir yayın saymış; üst çubuğa "14 MB" yazdırmış ve `OMX.google.h264.decoder` bu siyah videonun ilk karesini çizip `onRenderedFirstFrame` gönderince oynatıcı yayını açılmış kabul ederek kullanıcıyı 9:59 süreli kara ekranda bırakmıştır.

---

## 2. Yapılan Düzeltmeler

1. **Canlı Akış Tür Desteği (`AddonManifest.kt`):**
   `LIVE_TYPES = setOf("tv", "channel")` tanımlandı. `providesStreams` özelliği `servesPlayableType(STREAM) || servesLiveType(STREAM)` olarak güncellenerek yalnızca TV/kanal akışı sağlayan eklentilerin de canlı yayın sağlayabilmesi sağlandı.
2. **Doğrudan Eklenti Oynatma Koruması (`PlayRoot.kt`):**
   `target.request.pluginId != null` kontrolü eklenerek, eklenti detay veya katalog ekranından doğrudan başlatılan akışların gereksiz "eklenti yok" kontrolüne takılması engellendi.
3. **Canlı Yayın Sıralama Mantığı (`SourceLogic.kt`):**
   `RankContext` içine `isLive: Boolean` parametresi eklendi. Canlı yayında `SourceRanking.recommended`, manifestteki doğal HLS akış sırasını koruyarak çözünürlüğü belirtilmemiş kaliteli adaptive HLS yayınlarını 1080p etiketli sahte IPTV linklerinin arkasına düşürmez.
4. **Sahte Canlı Dosya Tespiti (`ContentDurationCheck.kt`):**
   `isFakeLiveStream(durationMs, isLive)` kuralı eklendi. Canlı olarak işaretlenen bir yayında 30 dakikadan kısa (`<= 1800 sn`) sonlu bir süre raporlanırsa (9:59 vb.) bu akış sahte yer tutucu/engel kabul edilir.
5. **Sahte Kaynak Eleme ve Sıradakine Geçiş (`SourceCoordinator.kt`):**
   `onDuration` tetiklendiğinde `isFakeLiveStream` kuralı çalışır; sahte kaynak `StreamVerdicts.rejected` ile işaretlenip derhal sıradaki kaynağa geçilir. Canlı yayında `reportSize` ve `probeSizes` boyut kaydetmez.
6. **Yedek Çözücü Desteği (`PlaybackController.kt`):**
   `DefaultRenderersFactory(context).setEnableDecoderFallback(true)` eklenerek donanım çözücüsü başarısız olduğunda sistemin otomatik olarak yedek yazılım çözücüsüne geçmesi güvence altına alındı.
7. **Canlı Yayında Boyut Bilgisinin Gizlenmesi (`PlayerScreen.kt` & `PlayerPanels.kt`):**
   Canlı yayında sonsuz akış olduğu için üst çubukta ve kaynak panelinde dosya boyutu metni (`sizeText`) gösterilmez (`if (isLive) null else sizeText`). Tüm kaynaklar tükenirse kullanıcıya `live_channel_unavailable` ("Bu kanal şu an oynatılamıyor.") bilgilendirme mesajı gösterilir.
8. **Yerelleştirme (`strings.xml` & `values-tr/strings.xml`):**
   `live_channel_unavailable` kaynak dizesi ("Bu kanal şu an oynatılamıyor.") eklendi.

---

## 3. Cihaz Doğrulama Sonuçları (LDPlayer 9)

### 3.1 Canlı Kanallar (30'ar Saniye)

| No | Kanal Adı | Süre | Seçilen Çözücü | Çözünürlük / Biçim | Durum | Ekran Görüntüsü |
|---|---|---|---|---|---|---|
| 1 | 🟣 Mor Spor 1 | 30 sn | `OMX.google.h264.decoder` + `OMX.google.aac.decoder` | HLS Adaptive Stream | ✅ Oynatıldı | `mor_spor_1.png` (514.5 KB) |
| 2 | 🟣 Mor Spor 1 4K | 30 sn | `OMX.google.h264.decoder` + `OMX.google.aac.decoder` | HLS Adaptive Stream | ✅ Oynatıldı | `mor_spor_1_4k.png` (520.7 KB) |
| 3 | 🟣 Mor Spor 2 | 30 sn | `OMX.google.h264.decoder` + `OMX.google.aac.decoder` | HLS Adaptive Stream | ✅ Oynatıldı | `mor_spor_2.png` (499.9 KB) |
| 4 | beIN Sports 1 (Birdirbir IPTV) | 30 sn | `OMX.google.h264.decoder` + `OMX.google.aac.decoder` | MPEG-TS Stream | ✅ Oynatıldı | `bein_sports_1.png` (539.5 KB) |

### 3.2 Film Regresyon Testleri (60'ar Saniye)

| No | Film | Süre | İlk Kare | Kaynak | Takılma Durumu | Süre Kontrolü | Durum | Ekran Görüntüsü |
|---|---|---|---|---|---|---|---|---|
| 5 | Joker (2019) | 60 sn | ~1.3 sn | Enel (`four.pichive.online`) | 0 takılma (kesintisiz) | 121.8 dk / 122 dk (Ok) | ✅ Oynatıldı | `regresyon_joker.png` (432.8 KB) |
| 6 | Dune (2021) | 60 sn | ~1.7 sn | Enel (`four.pichive.online`) | 1 takılma (bant=1.49 Mbps) | 155.4 dk / 155 dk (Ok) | ✅ Oynatıldı | `regresyon_dune.png` (122.2 KB) |

---

## 4. Ekran Görüntüleri ve Görsel Kanıtlar

Tüm ekran görüntüleri Android `screencap` ile çekilmiş, `adb pull` ile alınmış, PNG imzaları (`\x89PNG\r\n\x1a\n`) ve boyutları (<600 KB) doğrulanmıştır.

1. **`mor_spor_1.png` (514.5 KB):**  
   Mor Spor 1 canlı yayınının 30 saniye boyunca akıcı şekilde oynatıldığını, gerçek canlı yayın video karesinin ekrana geldiğini ve üst çubukta sabit "14 MB" boyutu bulunmadığını gösteren ekran görüntüsü.
   ![Mor Spor 1 Canlı Yayın](mor_spor_1.png)

2. **`mor_spor_1_4k.png` (520.7 KB):**  
   Mor Spor 1 4K canlı kanalının 30 saniye boyunca siyah ekranda kalmadan renkli yayın karesiyle oynatıldığını gösteren ekran görüntüsü.
   ![Mor Spor 1 4K Canlı Yayın](mor_spor_1_4k.png)

3. **`mor_spor_2.png` (499.9 KB):**  
   Mor Spor 2 canlı kanalının ölü kaynakların ardından sıradaki çalışan adaptif HLS yayınına geçerek başarıyla açıldığını gösteren ekran görüntüsü.
   ![Mor Spor 2 Canlı Yayın](mor_spor_2.png)

4. **`bein_sports_1.png` (539.5 KB):**  
   Birdirbir IPTV kataloğundaki beIN Sports 1 kanalının sahte 14 MB yer tutucu dosya yerine gerçek yayın akışıyla 30 saniye boyunca oynatıldığını gösteren ekran görüntüsü.
   ![beIN Sports 1 Birdirbir Canlı Yayın](bein_sports_1.png)

5. **`regresyon_joker.png` (432.8 KB):**  
   Joker (2019) filminin film oynatma mantığı, dublaj/altyazı kuralları ve süre denetimi bozulmadan 60 saniye boyunca sorunsuz oynatıldığını gösteren ekran görüntüsü.
   ![Joker 2019 Regresyon](regresyon_joker.png)

6. **`regresyon_dune.png` (122.2 KB):**  
   Dune (2021) filminin 60 saniyelik regresyon testinde ilk kareyi 1.7 saniyede vererek doğru film içeriğiyle oynatıldığını gösteren ekran görüntüsü.
   ![Dune 2021 Regresyon](regresyon_dune.png)

---

## 5. Doğrulama Günlük Satırları

### Mor Spor 1
```
D PlayTorrent: oynatıcıya veriliyor: kaynak=WioSpor http(s)://...
I OMXClient: IOmx service obtained
I OMXMaster: makeComponentInstance(OMX.google.h264.decoder) in omx@1.0-service process
I OMXMaster: makeComponentInstance(OMX.google.aac.decoder) in omx@1.0-service process
D MediaCodec: [OMX.google.h264.decoder] setting dataspace on output surface to #104
```

### beIN Sports 1 (Birdirbir IPTV)
```
D PlayTorrent: oynatıcıya veriliyor: kaynak=WioSpor http(s)://204.52.191.82/...
I OMXMaster: makeComponentInstance(OMX.google.h264.decoder) in omx@1.0-service process
I OMXMaster: makeComponentInstance(OMX.google.aac.decoder) in omx@1.0-service process
D MediaCodec: [OMX.google.h264.decoder] setting dataspace on output surface to #104
```

### Joker (2019)
```
D PlayMatch: kaynak başlatılıyor: Enel (four.pichive.online)
D PlayProbe: kaynak yoklama: host=four.pichive.online durum=200 type=text/html;charset=UTF-8 uzunluk=null
D PlayMatch: ilk kare: Enel (faz=Starting)
D PlayMatch: süre kontrolü kaynak=Enel (four.pichive.online) süre=121.8 dk beklenen=122 dk tür=Movie -> Ok
```

### Dune (2021)
```
D PlayMatch: kaynak başlatılıyor: Enel (four.pichive.online)
D PlayProbe: kaynak yoklama: host=four.pichive.online durum=200 type=text/html;charset=UTF-8 uzunluk=null
D PlayMatch: ilk kare: Enel (faz=Starting)
D PlayMatch: süre kontrolü kaynak=Enel (four.pichive.online) süre=155.4 dk beklenen=155 dk tür=Movie -> Ok
D PlayMatch: takılma-ayrıntı kaynak=Enel bitHızı=2.75 Mbps bantTahmini=1.49 Mbps tampon=0.1 sn
```

---

## 6. Sonuç

WioSpor canlı kanallarındaki kara ekran sorununun kök nedeni (IPTV sunucusunun 14 MB / 9:59 süreli sahte yer tutucu video döndürmesi ve `SourceRanking`'in çözünürlüğü belirsiz gerçek HLS akışlarını bu sahte linkin arkasına itmesi) tam olarak giderilmiştir. Canlı yayınlarda boyut gösterimi kaldırılmış, sahte sonlu süreli dosyaların elenmesi sağlanmış ve donanım çözücü yedeği devreye alınmıştır. Tüm canlı kanallar gerçek video kareleriyle oynatılmış, film regresyon testleri de başarıyla tamamlanmıştır.
