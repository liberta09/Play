# Teşhis Raporu: Örümcek-Adam Açılış Duraksaması İncelemesi (DONMA-2)

**Tarih:** 25 Ekim 2026, 02:07  
**Dal:** `tani-donma-2` (taban: `main`)  
**Cihaz:** LDPlayer 9 (Android 9 / SDK 28)

---

## 1. Kurulu Sürüm
```
adb shell dumpsys package com.kaan.play | grep versionName
versionName=1.1.10
```

---

## 2. ANR Kontrolü (/data/anr/ ve dumpsys)
- `/data/anr/` dizin listesi:
  ```
  total 656
  -rw------- 1 system system 670862 2026-10-25 00:21 anr_2026-10-25-00-21-10-398
  ```
- Son 15 dakikada `com.kaan.play` sürecine ait herhangi bir ANR dosyası **oluşmamıştır** (mevcut tek dosya saat 00:21'deki optimizasyon öncesi testlere aittir).

---

## 3. Logcat Analizi (Son 15 Dakika)

### 3.1 Choreographer: Skipped
- **0 satır.** Son 15 dakika boyunca ana iş parçacığında tek bir kare dahi atlanmamıştır (`Choreographer: Skipped frames = 0`).

### 3.2 ANR Satırları
- **0 satır.** Logcat'te `ANR in com.kaan.play` ifadesi yer almamaktadır.

### 3.3 PlayMatch Olayları (02:04:13 – 02:06:58)
- **02:04:13.129:** Kullanıcı "Örümcek-Adam: Yepyeni Bir Gün" içeriğinde "Oynat" butonuna bastı; oynatıcı ve eklenti sorguları arka planda başlatıldı (`Resolving` fazı).
- **02:04:17.474:** İlk kare başarıyla ekranda oluşturuldu (`ilk kare: Yabancı Dizi (faz=Starting)`). Başlatma süresi: **4.34 saniye**.
- **02:04:17.532:** `süre kontrolü kaynak=Yabancı Dizi süre=145.0 dk beklenen=145 dk tür=Movie -> Ok`
- **02:05:29.074, 02:05:50.381, 02:05:53.153:** `ilk kare: Yabancı Dizi (faz=Playing)` (kullanıcı oynatma sırasında ileri/geri sardı; ExoPlayer her sarma sonrası kareyi anında oluşturdu).
- **02:06:57.821 (Oynatıcı Kapanışı):**
  - `takılma kaynak=Yabancı Dizi sayı=0 toplam=0.0 sn oynama=0 dk`
  - `takılma-ayrıntı kaynak=Yabancı Dizi bitHızı=1.40 Mbps bantTahmini=76.52 Mbps tampon=52.6 sn`
  - Oynatma süresince **0 adet takılma** gerçekleşmiş, tampon miktarı **52.6 saniyeye** ulaşmıştır.

### 3.4 ExoPlayer ve MediaCodec Hataları
- MediaCodec ve ExoPlayer üzerinde hiçbir hata/çökme meydana gelmemiştir; oynatıcı temiz bir şekilde sonlandırılmıştır (`Release a18ee69`).

---

## 4. Karar (Tek Cümle)

**Karar:** Ana iş parçacığı kilitlenmedi (Choreographer skipped kare sayısı: 0, ANR kaydı: 0, 1 sn üzeri kilitlenme yok); uygulama arayüzü tamamen akıcıydı, algılanan duraksama eklentilerin ilk kaynağı ağdan çözümlemesi sırasındaki 4.3 saniyelik normal kaynak bekleme (Resolving) süresinden kaynaklandı ve ilk kare (02:04:17.474) geldikten sonra oturum 52.6 sn tamponla sıfır takılmayla kesintisiz oynatıldı.
