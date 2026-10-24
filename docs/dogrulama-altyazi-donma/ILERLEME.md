# Altyazı Donma ve Takılma Doğrulama İlerlemesi

- Bölüm 1: Criminal Minds S01E21 teşhis tamamlandı; altyazısız 60 sn'de 1 tampon takılması (kaynak yavaşlığı), 0 düşen kare/Choreographer uyarısı; altyazı seçiminde ANR cihazda tekrarlanamadı (ölçülemedi), kod incelemesinde SubtitleTimeline/ayrıştırma/zip seçiminin ana iş parçacığını bloke etme riski ve bölüm eşleştirme eksikliği tespit edildi.
- Bölüm 2: Altyazı indirme, zip açma, ayrıştırma ve SubtitleTimeline işlemleri arka plana (IO/computation) alındı, panelde Apply tıklamasında anında onDismiss sağlandı, SubtitleZip çoklu dosya ve bölüm eşleşmesi (Rank 1-3 + en büyük srt yedeği) uygulandı, 977 birim testinin tamamı başarıyla geçti.
- Bölüm 3: Cihaz doğrulaması başarıyla tamamlandı; Criminal Minds S01E21'de Altyazı Paketi dahil tüm satırlar ANR olmadan çalıştı ve altyazı ekranda görüntülendi, Joker 60 sn regresyonunda Türkçe altyazı otomatik açıldı.
