# Neon + kumanda taşıması: doğrulama sonucu

Dal: `ozellik/neon-dpad-1.2.1` (taban `d247b96`).

## Doğrulananlar (derleme/test, 2026-10-29)
- `.\gradlew testDebugUnitTest assembleDebug`: BUILD SUCCESSFUL.
- `git diff --stat d247b96..HEAD -- app/src/main/java/com/kaan/play/data`: boş (data/** değişmedi).
- `build.gradle.kts`, `versionCode` / `versionName` değişmedi.

## Gerçek cihazda GÖRÜLMEDİ (hiçbiri yapılmadı)
Cihaz doğrulaması engellendi, bu yüzden aşağıdakilerin hepsi **doğrulanmadı**:
- Ekran görüntüleri (Ana sayfa, Ayarlar, Kurulu tüm depolar, Depo Kataloğu, Profil seçimi, Ara, Kütüphane): yok.
- Ana sayfa D-pad akışı (AŞAĞI düz iner, YUKARI üst menüye çıkar, profil simgesinde SAĞ, Geri): denenmedi.
- Mobland / Örümcek-Adam kaynak araması (HDFilmCehennemi, DiziBox), `logcat -s PlayCs PlayMatch`: yapılmadı.
- Wi-Fi senkron kartı ve intro atlama ayarının ekranda görülmesi: yapılmadı (kodları bu dalda değişmedi).

## Engel
LDPlayer'daki kurulu uygulama `versionCode=54` (imzasız-debug değil, `1.2.0`); bu dalın derlemesi `versionCode=27`.
`adb install -r -t` ve `-r -d -t`: `INSTALL_FAILED_VERSION_DOWNGRADE`. Kaldırma yasak olduğu için kurulum yapılmadı.
