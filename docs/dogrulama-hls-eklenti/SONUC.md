# HDFilmCehennemi / DiziBOX: "bazen bulmuyor" ve Joker 3002, kök neden (2026-10-30)

Dal: `duzeltme/hls-liste-ve-eklenti-suresi`. Karşılaştırma: NuvioMedia/NuvioTV ve NuvioMobile (GPL-3.0; kod kopyalanmadı,
yalnızca davranış okundu).

## Önemli düzeltme: hangi eklenti?
Senin Play'inde HDFilmCehennemi ve DiziBOX **Nuvio JS eklentisi** olarak çalışıyor (`PlayPlugin`/`PlayPluginNet` günlükleri;
`PlayCs: 0 açık CS eklentisi`). Kaynaklar: `kratoskrator/hdfilmcehennemi-nuvio`, `kratoskrator/dizibox-nuvio`.
Gemini'nin değiştirdiği `.cs3` bu yola hiç girmiyor, sonucun değişmemesi bundan.

## Kök neden 1: oynatma listesi Play'in kendi kodunda kesiliyordu (Joker 3002)
`PlaybackController.TsSniffingDataSource` adresinde `/hls` geçen HER yanıtı TS parçası sayıp ilk uygun 0x47 baytına
(ASCII büyük "G") kadar kesiyordu. HDFilmCehennemi listeleri `…/hls/<id>/txt/master.txt` olduğundan liste de kesiliyor,
`#EXTM3U` ile başlamayınca ExoPlayer 0,4 sn içinde `ERROR_CODE_PARSING_MANIFEST_MALFORMED (3002)` veriyordu.
- Eklenti aynı listeyi kendisi indirip doğruluyordu (200, `application/vnd.apple.mpegurl`, 360 bayt): sunucu sağlam, bozan Play.
- İki dilli (TR+EN) listelerde ikinci ses satırının `GROUP-ID` kısmı kesme noktası oluyor (birim testte gösterildi). Tek dilli
  listede uygun "G" yoksa film oynuyor (Örümcek-Adam). "Bazen" görünmesinin nedeni bu.
- Düzeltme: `ui/player/TsSniff.kt` (Android'siz, birim testli). Liste ve manifest (içerik türü mpegurl/dash ya da `#EXT`/`<?xml`/`<MPD`
  ile başlayan gövde) asla kesilmiyor. Sahte resim başlıklı TS parçalarında eski kırpma davranışı birebir korunuyor.

## Kök neden 2: eklenti yarıda öldürülüyordu ("bazen bulmuyor")
İki eklenti de 45 sn iç bütçeyle yazılmış: istek başına ≤12 sn ve bir yeniden deneme (`session(){deadline: now+45000}`).
Play, eklenti çalıştırmasını kaynak zaman aşımı ayarıyla kesiyordu (varsayılan **15 sn**). Site yönlendirmesi
(.nl → .mobi) ya da tek bir yavaş yanıt bunu aşınca "yanıt vermedi" çıkıyordu. Nuvio aynı eklentilere 60 sn veriyor.
- Düzeltme: `PluginStreamProvider.runBudget` = max(ayar, 25 sn) (önce 50 sn denenmişti, "kimse 50 sn beklemez" itirazıyla 25 sn yapıldı; 15 sn üstü başarılı bir yanıt ölçülmedi). Sonuçlar hazır oldukça aktığı için hızlı kaynaklar beklemez.
  Bedeli: hiçbir eklenti bulamazsa "kaynak bulunamadı" ekranı en geç ~25 sn'de gelir (önce 15 sn).

## Aynı türden CloudStream hatası
`CsStreamProvider` arama, detay ve bağlantı adımlarının hepsine toplam 30 sn veriyordu. `CsRuntime.loadLinksDirect` süre
dolunca o ana kadar gelen bağlantıları da atıyordu. Nuvio toplamda 120 sn veriyor ve gelen bağlantıları koruyor.
- Düzeltme: toplam bütçe 90 sn (adım başına 30 sn aynı), `loadLinks` süresi dolarsa gelen bağlantılar kullanılıyor.

## Nuvio oynatıcısında olup Play'de olmayanlar (bu dala EKLENMEDİ, onay gerekir: kural 5)
1. Ayrıştırma hatasında (3001–3004) aynı kaynağı türünü yeniden yoklayıp bir kez daha deneme. Play hemen sıradaki kaynağa geçiyor.
2. Geçici ağ/G-Ç hatalarında aynı kaynakta 2 otomatik yeniden deneme (403/404 hariç).
3. ExoPlayer açamazsa ikinci oynatıcı motoru (MPV) ile deneme. Büyük iş, APK'yı büyütür.
4. Takılma bekçisi (tamponlanmış bölgenin ötesine küçük atlama) ve ilk kare bekçisinde kod çözücü kurtarma.
5. Kare hızı eşleme (AFR), Dolby Vision geri düşme, paralel aralıklı indirme. TV'ye özgü cila.

## Doğrulama durumu
- Kural testleri: `TsSniffTest` (7), `PluginRunBudgetTest` (2). Beklenen değerler kuralın birebir Python karşılığıyla
  doğrulandı. Bu oturumda Android SDK/Maven erişimi yok, Gradle derlemesini ve cihaz denemesini ajan yapacak.
- Cihaz: (ajan dolduracak) Joker, MobLand S2 B7, Örümcek-Adam, Criminal Minds, bir canlı kanal. Her biri 60 sn, Geri, çökme günlüğü.

## Dal 2: `duzeltme/oynatici-saglamlik` (Nuvio karşılaştırmasından, 2026-10-30)
Nuvio'da olup Play'de olmayan 4 madde, dublaj/kaynak sıralama dosyalarına (SourceLogic, SourceCoordinator, SourceMemory) DOKUNMADAN:
1. **IPv4 öncelikli DNS** (`data/net/Ipv4FirstDns.kt`): yalnızca oynatma istemcisinde; IPv6 yedek olarak kalır, adres silinmez.
2. **Ayrıştırma hatasında tür yoklaması** (`ui/player/MimeSniff.kt`, `PlaybackController`): 3001–3004 hatasında, bir kez, ilk 2 KB'a bakılır (HLS/DASH/düz dosya); tür tahminden farklıysa doğru türle yeniden denenir, değilse eski davranış (sıradaki kaynak).
3. **Geçici çözücü hatasında yeniden hazırlama**: DECODER_INIT_FAILED/DECODING_FAILED için kaynak atılmadan önce bir kez `prepare()`.
4. **Torrent oturumu boşta açık** (`TorrentIdleRules`, `TorrentEngine`): torrent verisi hemen silinir, oturum 10 dk bekler.
Eklenmeyenler (onay bekliyor): ikinci oynatıcı motoru (MPV), paralel aralıklı indirme.
Doğrulama: kurallar Python karşılığıyla denendi; Gradle derlemesi ve cihaz testi bu oturumda yapılamadı (SDK/Maven erişimi yok), ajan yapacak.

## Cihaz sonuçları (PlayNeon-130 / emulator-5558)

Derleme ve birim testler: `testDebugUnitTest assembleDebug` 1120 test (1118 geçti, 0 hata, 2 atlandı).

| İçerik | Deneme | Eklenti / Kaynak | İstek → Akış | İlk Kare | Hata Kodu | 60 sn Konum | Dublaj | Geri / Odak | FATAL |
|---|---|---|---|---|---|---|---|---|---|
| Joker (2019) | 1 | HDFilmCehennemi (`srv12.cdnimages1159.shop`) | 1.95 sn | 2.88 sn | YOK (3002 yok) | 0:58 (58.9 sn) | Altyazılı | MainActivity | 0 |
| Joker (2019) | 2 | HDFilmCehennemi (`srv12.cdnimages1159.shop`) | 1.83 sn | 2.64 sn | YOK (3002 yok) | 0:58 (58.7 sn) | Altyazılı | MainActivity | 0 |
| Joker (2019) | 3 | HDFilmCehennemi (`srv12.cdnimages1159.shop`) | 1.51 sn | 2.47 sn | YOK (3002 yok) | 0:58 (58.9 sn) | Altyazılı | MainActivity | 0 |
| MobLand S2 B7 | 1 | Kaynak bulunamadı (HDFilmCehennemi / DiziBOX) | - | - | YOK (Oynatıcı hatası yok) | - | - | MainActivity | 0 |
| Örümcek-Adam: Yepyeni Bir Gün | 1 | HDFilmCehennemi (`srv9.cdnimages2426.cyou`) | 1.77 sn | 2.85 sn | YOK (3002 yok) | 1 dk (58.8 sn) | Altyazılı | MainActivity | 0 |
| Criminal Minds S1 B2 | 1 | DiziBOX TV (`dbx.molystream.org`) | 3.81 sn | 4.81 sn | YOK (3002 yok) | 1 dk (59.1 sn) | Altyazılı | MainActivity | 0 |
| TRT 1 HD (Canlı TV) | 1 | Anthology — Canlı TV (`trt.daioncdn.net`) | 0.01 sn | 0.88 sn | YOK (3002 yok) | Canlı (1 dk) | Orijinal (TR) | MainActivity | 0 |

### Gözlemler ve Teşhis Notları:
1. **Joker (2019) 3002 Hatası:** 3 denemenin 3'ünde de `ERROR_CODE_PARSING_MANIFEST_MALFORMED (3002)` tamamen kayboldu (0/3 hata). İlk kare 2.47–2.88 sn aralığında geldi, 60 sn boyunca kesintisiz (0 sn takılma) oynadı, süre kontrolü (121.8 dk / 122 dk) doğrulandı.
2. **MobLand S2 B7:** HDFilmCehennemi bölüm sayfasını (`https://www.hdfilmcehennemi.nl/dizi/mobland-70/sezon-2/bolum-7/`) 200 ile indirdi ancak sitedeki oynatıcı iframe'i `/rplayer/` URL yapısında olduğundan JS eklentisi (yalnızca `/embed/` eşlediği için) akış çıkaramadı. DiziBOX ise `Network request failed` verdi. Bütçe aşımı ("yanıt vermedi") çıkmadı. Ekranda "Bu içerik için kaynak bulunamadı." bildirildi, çökme veya oynatıcı hatası oluşmadı.
3. **Örümcek-Adam:** HDFilmCehennemi üzerinden 1.77 sn akış süresi ve 2.85 sn ilk kare süresi ile sorunsuz oynadı, 60 sn kesintisiz (0 sn takılma, tampon 29.7 sn) devam etti. Regresyon yok.
4. **Criminal Minds S1 B2:** DiziBOX TV üzerinden 3.81 sn akış süresi ve 4.81 sn ilk kare süresi ile sorunsuz açıldı, 60 sn kesintisiz (0 sn takılma, tampon 32.5 sn) oynadı.
5. **Canlı TV (TRT 1 HD):** Anthology — Canlı TV eklentisiyle HLS m3u8 akışı 0.88 sn'de ilk kareye ulaştı. 60 sn boyunca 0 takılma, 21.0 sn tampon ve 86.95 Mbps bant genişliği ile kesintisiz oynadı.
6. **Oynatıcı Sağlamlığı:** Hiçbir denemede "tür yoklaması" veya "çözücü hatası" satırı tetiklenmedi (akışlar doğrudan düzgün MIME ile çözüldü). Geri tuşu tüm denemelerde `MainActivity`'ye sorunsuz döndürdü, arka planda çökme (FATAL EXCEPTION) 0.
