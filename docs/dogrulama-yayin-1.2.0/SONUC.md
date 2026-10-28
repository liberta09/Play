# Yayın duman testi — 1.2.0

Cihaz: LDPlayer (127.0.0.1:5555). Release APK `adb install -r` ile mevcut kurulumun üstüne kuruldu (uninstall yok, imza uyumlu).

- Sürüm: `versionName=1.2.0`, `versionCode=27` (önceki: 1.1.20); lastUpdateTime 2026-10-28 20:17:31
- APK SHA-256: d612ea38ee08c27b73dd615c82da0cda728dfdea4496d0f0d8bfca9ad865c37f (SHA256.txt ile aynı)
- Sertifika SHA-256: 5c7cd2bd91946586d125e59a848544f7db10b42ab0c0d3d6dd30c5b8c4e9f44f (beklenenle aynı)

| Kontrol | Sonuç |
|---|---|
| Uygulama açılıyor (profil ekranı, Wi-Fi senkronizasyonu kartı) | Geçti — 01-acilis.png, 02-ana.png |
| Joker oynatma, 60 sn+ (2:56'dan ilerledi) | Geçti — 04, 05 |
| Geri tuşu oynatıcıdan ana sayfaya dönüyor | Geçti — 06-joker-geri.png |
| Ayarlar açılıyor | Geçti — 07-ayarlar.png |
| `logcat -b crash` | Boş; süreç canlı |
