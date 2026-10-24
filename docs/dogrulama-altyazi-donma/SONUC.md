# Altyazı Donma ve Takılma Doğrulama Sonuç Raporu

## 1. Sorunun Nedeni
1. **Ana İş Parçacığı Yükü:** `SubtitleController.loadExternal` akışında `SubtitleTimeline` oluşturma ve zaman çizgisi indeksleme ana iş parçacığında çalışıyordu. `SubtitleTimeline.activeAt` içinde `longest` satır süresi üst sınırı bulunmadığından geriye dönük 256 adımlık tarama, her 50 ms'lik saat döngüsünde ana iş parçacığını ve Choreographer'ı tüketerek donmaya/ANR'ye yol açıyordu.
2. **Panel Kapanma Gecikmesi:** `SourcePanel.onApply` tıklandığında `onDismiss()` seçimin ardına bırakılmıştı; panel anında kapatılmadığından altyazı indirme ve yükleme tamamlanana kadar arayüz yanıt vermiyordu.
3. **Zip Arşivlerinde Bölüm Seçimi Eksikliği:** `SubtitleZip` çok dosyalı paketlerde bölüm numarasını eşleştirmeksizin ilk geçerli uzantılı dosyayı alıyordu (bölüm 21 için bölüm 1'i seçebiliyordu) ve arşiv boyutu/girdi sınırlamaları esneklik sunmuyordu.

## 2. Değiştirilen Dosyalar
- `app/src/main/java/com/kaan/play/data/subtitle/SubtitleModels.kt`: `SubtitleTrack` modeline `season` ve `episode` alanları eklendi.
- `app/src/main/java/com/kaan/play/data/subtitle/SubtitleZip.kt`: Giriş baytları, uncompressed toplam bayt ve en çok 128 girdi sınırları getirildi. Rank 1 (`S01E21` / `1x21`), Rank 2 (`E21` / `ep21`), Rank 3 (bağımsız `21`) eşleşme algoritması ve en büyük `.srt` yedeği eklendi.
- `app/src/main/java/com/kaan/play/data/subtitle/SubtitleLoader.kt`: Zip çıkarma adımına `track.season` ve `track.episode` parametreleri aktarıldı.
- `app/src/main/java/com/kaan/play/data/subtitle/SubtitleSelection.kt`: `SubtitleTimeline` içinde `longest` arama penceresi 30 saniye ve `MAX_LOOKBACK` 32 ile sınırlandırıldı.
- `app/src/main/java/com/kaan/play/ui/player/SubtitleController.kt`: `SubtitleTimeline` oluşturma `computation` dispatcher'ına, `memory.offset` ve `firstUsableOnline` `io` dispatcher'ına alındı; gelen parçalara bölüm ve sezon bilgisi işlendi.
- `app/src/main/java/com/kaan/play/ui/player/PlayerPanels.kt`: `SourcePanel.onApply` başında `onDismiss()` çağrılarak panelin anında kapanması sağlandı.
- `app/src/test/java/com/kaan/play/data/subtitle/SubtitleZipTest.kt`: Çoklu dosya, bölüm eşleşmesi (Rank 1-3), yıl/bölüm ayrımı ve limit testleri eklendi.
- `app/src/test/java/com/kaan/play/ui/player/SubtitleControllerTest.kt`: Test ortamı dispatcher uyumu sağlandı (977 birim testinin tamamı geçti).

## 3. Cihaz Doğrulama Tablosu

| Test İçeriği | Seçilen Satır / İşlem | ANR / Donma | Oynatma Akışı | Altyazı Görünümü | Sonuç |
| :--- | :--- | :---: | :---: | :---: | :---: |
| Criminal Minds S01E21 | Altyazı yok | Yok | Sorunsuz | Kapalı | ✅ |
| Criminal Minds S01E21 | Türkçe · videonun içinde | Yok | Sorunsuz | Senkron | ✅ |
| Criminal Minds S01E21 | Türkçe · AltyazıDB (S01E21 • Altyazı Paketi...) | Yok | Sorunsuz | Ekranda göründü | ✅ |
| Criminal Minds S01E21 | Türkçe · AltyazıDB (S01E21 • quattro • LOL •...) | Yok | Sorunsuz | Ekranda göründü | ✅ |
| Criminal Minds S01E21 | Diğer · videonun içinde | Yok | Sorunsuz | Senkron | ✅ |
| Joker (2019) 60 sn | Türkçe altyazı otomatik seçim | Yok | Sorunsuz | 739 satır yüklendi, otomatik açık | ✅ |

## 4. Ekran Görüntüleri (Kanıt)
- `cm_altyazi_paketi_kanit.png`: "Altyazı Paketi" seçilerek açılan S01E21 bölümünde altyazı satırı ("Onu asla bulamayacaksın.") ekranda doğrulanmıştır.
- `cm_quattro_kanit.png`: "quattro • LOL" satırı seçilerek açılan S01E21 bölümünde altyazı satırı ekranda doğrulanmıştır.
- `joker_kanit.png`: Joker (2019) 60 saniyelik oynatmasında Türkçe altyazının otomatik devreye girdiği ve akışın kesintisiz devam ettiği doğrulanmıştır.
