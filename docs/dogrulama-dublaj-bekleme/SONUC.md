# Doğrulama Raporu — fix-dublaj-bekleme

- **Kırmızı Test Çıktısı:** `SourceCoordinatorTest > openingDubWaitPrefersDubbedArrivingAtFiveSeconds FAILED` (`AssertionError: 3.5 sn'de altyazılı başlamamalı, dublaj beklenmeli`).
- **Düzeltme:** `StartPolicy.DUB_WAIT_MS` 3_000L yerine 8_000L (8 sn) yapıldı. 8 sn dolunca altyazılı başlar; dublaj gelince veya toplama bitince beklemeden başlar.
- **Kural & Test Güncellemesi:** CLAUDE.md güncellendi, testlerdeki 3 sn varsayımları `StartPolicy.DUB_WAIT_MS` sabitine bağlandı. Kök `SONUC.md` silindi.
- **Birim Testleri:** 890 test tamamlandı (0 failure, 0 error, 2 skipped, %100 başarılı).
- **Sonuç:** BAŞARILI
