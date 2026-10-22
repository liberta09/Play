# Yeni Depo Eklentileri Otomatik Açık Doğrulama Raporu
- Karar (2026-10-23): Yeni eklenen depolarda (Nuvio ve CloudStream) eklentiler varsayılan enabled=true kurulur.
- RepoInstallPolicy.kt: Android'den bağımsız ilk kurulum açık kuralı tanımlandı ve birim testlendi.
- PluginRepository.kt & CsRepository.kt: Yeni kurulumlarda RepoInstallPolicy.initialPluginEnabled(...) bağlandı.
- Güncelleme ve mevcut kayıtlar: Var olan eklentilerin açık/kapalı durumu aynen korunur; şema/göç değişmedi.
- Deep link: play://add akışı korundu; kullanıcı onayı olmadan hiçbir depo veya eklenti eklenmez.
- CLAUDE.md: 3 konumdaki varsayılan kapalı ifadeleri yeni kararla güncellendi.
- RepoAutoEnabledTest.kt & PluginRepositoryTest.kt: 4 senaryo (yeni Nuvio, yeni CS, güncelleme koruma, deep link red) doğrulandı.
- testDebugUnitTest: 889 test çalıştı, 0 hata, 0 başarısız (tümü yeşil).
