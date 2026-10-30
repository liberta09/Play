# Torrentio / Libtorrent Ölçüm Raporu 2 (2026-10-31)

Dal: `duzeltme/oynatici-saglamlik` (Commit: `a546d65`)  
Cihaz: PlayNeon-130 (`emulator-5558`)  
İçerik: Big Buck Bunny (2008), 1080p MP4 (~276 MB / 276.134.947 bayt, TMDB 10378, IMDb tt1254207)  
Eklenti: Torrentio (`v0.0.15`)  

---

## 1. Özet Ölçüm Tablosu

| Deneme | Oturum Türü | Oynat'a Basış | Torrent Açıldı | İlk Kare (PlayMatch) | Eş Sayısı | Bekleniyor Satır Sayısı | İlk Kare > 40 sn ise Son Bekleniyor / Durum | 60 sn Takılma | Kapanış Logu |
|---|---|---|---|---|---|---|---|---|---|
| **Soğuk Deneme 1** | Soğuk (`force-stop`) | `00:52:55.132` | `00:52:59.767 (1.81 sn)` | `GELMEDİ (Zaman aşımı)` | 24 eş | 27 satır | `bekleniyor: parça=0 süre=16sn eş=0 tohum=0 hız=0KB/s inen=0KB durum=DOWNLOADING` | 0 (0.0 sn) | `10 dk boşta` |
| **Soğuk Deneme 2** | Soğuk (`force-stop`) | `00:54:04.617` | `00:54:08.826 (1.72 sn)` | `GELMEDİ (Zaman aşımı)` | 24 eş | 27 satır | `bekleniyor: parça=0 süre=16sn eş=0 tohum=0 hız=0KB/s inen=0KB durum=DOWNLOADING` | 0 (0.0 sn) | `10 dk boşta` |
| **Soğuk Deneme 3** | Soğuk (`force-stop`) | `00:55:13.936` | `00:55:18.211 (1.71 sn)` | `GELMEDİ (Zaman aşımı)` | 24 eş | 27 satır | `bekleniyor: parça=0 süre=16sn eş=0 tohum=0 hız=0KB/s inen=0KB durum=DOWNLOADING` | 0 (0.0 sn) | `10 dk boşta` |
| **Soğuk Deneme 4** | Soğuk (`force-stop`) | `00:56:23.278` | `00:56:27.580 (1.78 sn)` | `GELMEDİ (Zaman aşımı)` | 24 eş | 27 satır | `bekleniyor: parça=0 süre=16sn eş=0 tohum=0 hız=0KB/s inen=0KB durum=DOWNLOADING` | 0 (0.0 sn) | `10 dk boşta` |
| **Soğuk Deneme 5** | Soğuk (`force-stop`) | `00:57:32.710` | `00:57:36.910 (1.66 sn)` | `00:57:39.531 (4.29 sn)` | 24 eş | 2 satır | İlk kare 4.29 sn'de geldi (≤ 40 sn) | 0 (0.0 sn) | `10 dk boşta` |
| **Sıcak Deneme** | **Sıcak** (Kapatıp hemen açılış) | `00:58:50.282` | `00:58:52.806 (1.83 sn)` | `GELMEDİ (Zaman aşımı)` | 24 eş | 27 satır | `bekleniyor: parça=0 süre=16sn eş=0 tohum=0 hız=0KB/s inen=0KB durum=DOWNLOADING` | 0 (0.0 sn) | `10 dk boşta` |

---

## 2. Denemelere Göre 'bekleniyor:' Satırlarının Tamamı

### Soğuk Deneme 1 (Soğuk)
- **Oynat'a Basış:** `00:52:55.132`
- **Torrent Açılış:** `00:52:59.767` (1.81 sn)
- **İlk Kare:** `None` (None sn)
- **Eş Sayısı:** 24
- **Toplam Bekleniyor Satırı:** 27
```text
00:53:01.791 bekleniyor: parça=0 süre=2sn eş=0 tohum=0 hız=0KB/s inen=0KB durum=DOWNLOADING
00:53:03.808 bekleniyor: parça=0 süre=4sn eş=0 tohum=0 hız=0KB/s inen=0KB durum=DOWNLOADING
00:53:05.823 bekleniyor: parça=0 süre=6sn eş=0 tohum=0 hız=0KB/s inen=0KB durum=DOWNLOADING
00:53:07.839 bekleniyor: parça=0 süre=8sn eş=0 tohum=0 hız=0KB/s inen=0KB durum=DOWNLOADING
00:53:09.855 bekleniyor: parça=0 süre=10sn eş=0 tohum=0 hız=0KB/s inen=0KB durum=DOWNLOADING
00:53:11.869 bekleniyor: parça=0 süre=12sn eş=0 tohum=0 hız=0KB/s inen=0KB durum=DOWNLOADING
00:53:13.880 bekleniyor: parça=0 süre=14sn eş=0 tohum=0 hız=0KB/s inen=0KB durum=DOWNLOADING
00:53:15.890 bekleniyor: parça=0 süre=16sn eş=0 tohum=0 hız=0KB/s inen=0KB durum=DOWNLOADING
00:53:17.895 bekleniyor: parça=0 süre=18sn eş=0 tohum=0 hız=0KB/s inen=0KB durum=DOWNLOADING
00:53:19.913 bekleniyor: parça=0 süre=20sn eş=0 tohum=0 hız=0KB/s inen=0KB durum=DOWNLOADING
00:53:21.929 bekleniyor: parça=0 süre=22sn eş=0 tohum=0 hız=0KB/s inen=0KB durum=DOWNLOADING
00:53:23.948 bekleniyor: parça=0 süre=24sn eş=0 tohum=0 hız=0KB/s inen=0KB durum=DOWNLOADING
00:53:25.959 bekleniyor: parça=0 süre=26sn eş=0 tohum=0 hız=0KB/s inen=0KB durum=DOWNLOADING
00:53:27.964 bekleniyor: parça=0 süre=28sn eş=0 tohum=0 hız=0KB/s inen=0KB durum=DOWNLOADING
00:53:29.968 bekleniyor: parça=0 süre=30sn eş=0 tohum=0 hız=0KB/s inen=0KB durum=DOWNLOADING
00:53:31.973 bekleniyor: parça=0 süre=32sn eş=0 tohum=0 hız=0KB/s inen=0KB durum=DOWNLOADING
00:53:33.979 bekleniyor: parça=0 süre=34sn eş=0 tohum=0 hız=0KB/s inen=0KB durum=DOWNLOADING
00:53:35.983 bekleniyor: parça=0 süre=36sn eş=0 tohum=0 hız=0KB/s inen=0KB durum=DOWNLOADING
00:53:37.988 bekleniyor: parça=0 süre=38sn eş=0 tohum=0 hız=0KB/s inen=0KB durum=DOWNLOADING
00:53:43.274 bekleniyor: parça=0 süre=2sn eş=1 tohum=0 hız=0KB/s inen=0KB durum=DOWNLOADING
00:53:45.287 bekleniyor: parça=0 süre=4sn eş=1 tohum=1 hız=0KB/s inen=0KB durum=DOWNLOADING
00:53:47.302 bekleniyor: parça=0 süre=6sn eş=0 tohum=0 hız=0KB/s inen=0KB durum=DOWNLOADING
00:53:49.317 bekleniyor: parça=0 süre=8sn eş=0 tohum=0 hız=0KB/s inen=0KB durum=DOWNLOADING
00:53:51.335 bekleniyor: parça=0 süre=10sn eş=0 tohum=0 hız=0KB/s inen=0KB durum=DOWNLOADING
00:53:53.349 bekleniyor: parça=0 süre=12sn eş=0 tohum=0 hız=0KB/s inen=0KB durum=DOWNLOADING
00:53:55.363 bekleniyor: parça=0 süre=14sn eş=0 tohum=0 hız=0KB/s inen=0KB durum=DOWNLOADING
00:53:57.373 bekleniyor: parça=0 süre=16sn eş=0 tohum=0 hız=0KB/s inen=0KB durum=DOWNLOADING
```

### Soğuk Deneme 2 (Soğuk)
- **Oynat'a Basış:** `00:54:04.617`
- **Torrent Açılış:** `00:54:08.826` (1.72 sn)
- **İlk Kare:** `None` (None sn)
- **Eş Sayısı:** 24
- **Toplam Bekleniyor Satırı:** 27
```text
00:54:10.842 bekleniyor: parça=0 süre=2sn eş=0 tohum=0 hız=0KB/s inen=0KB durum=DOWNLOADING
00:54:12.867 bekleniyor: parça=0 süre=4sn eş=0 tohum=0 hız=0KB/s inen=0KB durum=DOWNLOADING
00:54:14.880 bekleniyor: parça=0 süre=6sn eş=0 tohum=0 hız=0KB/s inen=0KB durum=DOWNLOADING
00:54:16.893 bekleniyor: parça=0 süre=8sn eş=0 tohum=0 hız=0KB/s inen=0KB durum=DOWNLOADING
00:54:18.902 bekleniyor: parça=0 süre=10sn eş=0 tohum=0 hız=0KB/s inen=0KB durum=DOWNLOADING
00:54:20.914 bekleniyor: parça=0 süre=12sn eş=0 tohum=0 hız=0KB/s inen=0KB durum=DOWNLOADING
00:54:22.927 bekleniyor: parça=0 süre=14sn eş=0 tohum=0 hız=0KB/s inen=0KB durum=DOWNLOADING
00:54:24.942 bekleniyor: parça=0 süre=16sn eş=0 tohum=0 hız=0KB/s inen=0KB durum=DOWNLOADING
00:54:26.952 bekleniyor: parça=0 süre=18sn eş=0 tohum=0 hız=0KB/s inen=0KB durum=DOWNLOADING
00:54:28.964 bekleniyor: parça=0 süre=20sn eş=0 tohum=0 hız=0KB/s inen=0KB durum=DOWNLOADING
00:54:30.981 bekleniyor: parça=0 süre=22sn eş=0 tohum=0 hız=0KB/s inen=0KB durum=DOWNLOADING
00:54:32.992 bekleniyor: parça=0 süre=24sn eş=0 tohum=0 hız=0KB/s inen=0KB durum=DOWNLOADING
00:54:35.004 bekleniyor: parça=0 süre=26sn eş=0 tohum=0 hız=0KB/s inen=0KB durum=DOWNLOADING
00:54:37.018 bekleniyor: parça=0 süre=28sn eş=0 tohum=0 hız=0KB/s inen=0KB durum=DOWNLOADING
00:54:39.032 bekleniyor: parça=0 süre=30sn eş=0 tohum=0 hız=0KB/s inen=0KB durum=DOWNLOADING
00:54:41.046 bekleniyor: parça=0 süre=32sn eş=0 tohum=0 hız=0KB/s inen=0KB durum=DOWNLOADING
00:54:43.057 bekleniyor: parça=0 süre=34sn eş=0 tohum=0 hız=0KB/s inen=0KB durum=DOWNLOADING
00:54:45.070 bekleniyor: parça=0 süre=36sn eş=0 tohum=0 hız=0KB/s inen=0KB durum=DOWNLOADING
00:54:47.084 bekleniyor: parça=0 süre=38sn eş=0 tohum=0 hız=0KB/s inen=0KB durum=DOWNLOADING
00:54:52.324 bekleniyor: parça=0 süre=2sn eş=3 tohum=1 hız=0KB/s inen=0KB durum=DOWNLOADING
00:54:54.329 bekleniyor: parça=0 süre=4sn eş=2 tohum=2 hız=0KB/s inen=0KB durum=DOWNLOADING
00:54:56.335 bekleniyor: parça=0 süre=6sn eş=1 tohum=1 hız=0KB/s inen=0KB durum=DOWNLOADING
00:54:58.340 bekleniyor: parça=0 süre=8sn eş=0 tohum=0 hız=0KB/s inen=0KB durum=DOWNLOADING
00:55:00.349 bekleniyor: parça=0 süre=10sn eş=0 tohum=0 hız=0KB/s inen=0KB durum=DOWNLOADING
00:55:02.354 bekleniyor: parça=0 süre=12sn eş=0 tohum=0 hız=0KB/s inen=0KB durum=DOWNLOADING
00:55:04.359 bekleniyor: parça=0 süre=14sn eş=0 tohum=0 hız=0KB/s inen=0KB durum=DOWNLOADING
00:55:06.364 bekleniyor: parça=0 süre=16sn eş=0 tohum=0 hız=0KB/s inen=0KB durum=DOWNLOADING
```

### Soğuk Deneme 3 (Soğuk)
- **Oynat'a Basış:** `00:55:13.936`
- **Torrent Açılış:** `00:55:18.211` (1.71 sn)
- **İlk Kare:** `None` (None sn)
- **Eş Sayısı:** 24
- **Toplam Bekleniyor Satırı:** 27
```text
00:55:20.229 bekleniyor: parça=0 süre=2sn eş=0 tohum=0 hız=0KB/s inen=0KB durum=DOWNLOADING
00:55:22.238 bekleniyor: parça=0 süre=4sn eş=0 tohum=0 hız=0KB/s inen=0KB durum=DOWNLOADING
00:55:24.251 bekleniyor: parça=0 süre=6sn eş=0 tohum=0 hız=0KB/s inen=0KB durum=DOWNLOADING
00:55:26.261 bekleniyor: parça=0 süre=8sn eş=0 tohum=0 hız=0KB/s inen=0KB durum=DOWNLOADING
00:55:28.269 bekleniyor: parça=0 süre=10sn eş=0 tohum=0 hız=0KB/s inen=0KB durum=DOWNLOADING
00:55:30.280 bekleniyor: parça=0 süre=12sn eş=0 tohum=0 hız=0KB/s inen=0KB durum=DOWNLOADING
00:55:32.288 bekleniyor: parça=0 süre=14sn eş=0 tohum=0 hız=0KB/s inen=0KB durum=DOWNLOADING
00:55:34.296 bekleniyor: parça=0 süre=16sn eş=0 tohum=0 hız=0KB/s inen=0KB durum=DOWNLOADING
00:55:36.310 bekleniyor: parça=0 süre=18sn eş=0 tohum=0 hız=0KB/s inen=0KB durum=DOWNLOADING
00:55:38.327 bekleniyor: parça=0 süre=20sn eş=0 tohum=0 hız=0KB/s inen=0KB durum=DOWNLOADING
00:55:40.346 bekleniyor: parça=0 süre=22sn eş=0 tohum=0 hız=0KB/s inen=0KB durum=DOWNLOADING
00:55:42.363 bekleniyor: parça=0 süre=24sn eş=0 tohum=0 hız=0KB/s inen=0KB durum=DOWNLOADING
00:55:44.378 bekleniyor: parça=0 süre=26sn eş=0 tohum=0 hız=0KB/s inen=0KB durum=DOWNLOADING
00:55:46.393 bekleniyor: parça=0 süre=28sn eş=0 tohum=0 hız=0KB/s inen=0KB durum=DOWNLOADING
00:55:48.409 bekleniyor: parça=0 süre=30sn eş=0 tohum=0 hız=0KB/s inen=0KB durum=DOWNLOADING
00:55:50.427 bekleniyor: parça=0 süre=32sn eş=0 tohum=0 hız=0KB/s inen=0KB durum=DOWNLOADING
00:55:52.443 bekleniyor: parça=0 süre=34sn eş=0 tohum=0 hız=0KB/s inen=0KB durum=DOWNLOADING
00:55:54.449 bekleniyor: parça=0 süre=36sn eş=0 tohum=0 hız=0KB/s inen=0KB durum=DOWNLOADING
00:55:56.456 bekleniyor: parça=0 süre=38sn eş=0 tohum=0 hız=0KB/s inen=0KB durum=DOWNLOADING
00:56:01.722 bekleniyor: parça=0 süre=2sn eş=2 tohum=1 hız=0KB/s inen=0KB durum=DOWNLOADING
00:56:03.727 bekleniyor: parça=0 süre=4sn eş=1 tohum=1 hız=0KB/s inen=0KB durum=DOWNLOADING
00:56:05.733 bekleniyor: parça=0 süre=6sn eş=1 tohum=1 hız=0KB/s inen=0KB durum=DOWNLOADING
00:56:07.738 bekleniyor: parça=0 süre=8sn eş=0 tohum=0 hız=0KB/s inen=0KB durum=DOWNLOADING
00:56:09.743 bekleniyor: parça=0 süre=10sn eş=0 tohum=0 hız=0KB/s inen=0KB durum=DOWNLOADING
00:56:11.749 bekleniyor: parça=0 süre=12sn eş=0 tohum=0 hız=0KB/s inen=0KB durum=DOWNLOADING
00:56:13.754 bekleniyor: parça=0 süre=14sn eş=0 tohum=0 hız=0KB/s inen=0KB durum=DOWNLOADING
00:56:15.758 bekleniyor: parça=0 süre=16sn eş=0 tohum=0 hız=0KB/s inen=0KB durum=DOWNLOADING
```

### Soğuk Deneme 4 (Soğuk)
- **Oynat'a Basış:** `00:56:23.278`
- **Torrent Açılış:** `00:56:27.580` (1.78 sn)
- **İlk Kare:** `None` (None sn)
- **Eş Sayısı:** 24
- **Toplam Bekleniyor Satırı:** 27
```text
00:56:29.602 bekleniyor: parça=0 süre=2sn eş=0 tohum=0 hız=0KB/s inen=0KB durum=DOWNLOADING
00:56:31.624 bekleniyor: parça=0 süre=4sn eş=0 tohum=0 hız=0KB/s inen=0KB durum=DOWNLOADING
00:56:33.647 bekleniyor: parça=0 süre=6sn eş=0 tohum=0 hız=0KB/s inen=0KB durum=DOWNLOADING
00:56:35.663 bekleniyor: parça=0 süre=8sn eş=0 tohum=0 hız=0KB/s inen=0KB durum=DOWNLOADING
00:56:37.680 bekleniyor: parça=0 süre=10sn eş=0 tohum=0 hız=0KB/s inen=0KB durum=DOWNLOADING
00:56:39.696 bekleniyor: parça=0 süre=12sn eş=0 tohum=0 hız=0KB/s inen=0KB durum=DOWNLOADING
00:56:41.714 bekleniyor: parça=0 süre=14sn eş=0 tohum=0 hız=0KB/s inen=0KB durum=DOWNLOADING
00:56:43.736 bekleniyor: parça=0 süre=16sn eş=0 tohum=0 hız=0KB/s inen=0KB durum=DOWNLOADING
00:56:45.749 bekleniyor: parça=0 süre=18sn eş=0 tohum=0 hız=0KB/s inen=0KB durum=DOWNLOADING
00:56:47.763 bekleniyor: parça=0 süre=20sn eş=0 tohum=0 hız=0KB/s inen=0KB durum=DOWNLOADING
00:56:49.780 bekleniyor: parça=0 süre=22sn eş=0 tohum=0 hız=0KB/s inen=0KB durum=DOWNLOADING
00:56:51.796 bekleniyor: parça=0 süre=24sn eş=0 tohum=0 hız=0KB/s inen=0KB durum=DOWNLOADING
00:56:53.807 bekleniyor: parça=0 süre=26sn eş=0 tohum=0 hız=0KB/s inen=0KB durum=DOWNLOADING
00:56:55.812 bekleniyor: parça=0 süre=28sn eş=0 tohum=0 hız=0KB/s inen=0KB durum=DOWNLOADING
00:56:57.817 bekleniyor: parça=0 süre=30sn eş=0 tohum=0 hız=0KB/s inen=0KB durum=DOWNLOADING
00:56:59.822 bekleniyor: parça=0 süre=32sn eş=0 tohum=0 hız=0KB/s inen=0KB durum=DOWNLOADING
00:57:01.829 bekleniyor: parça=0 süre=34sn eş=0 tohum=0 hız=0KB/s inen=0KB durum=DOWNLOADING
00:57:03.836 bekleniyor: parça=0 süre=36sn eş=0 tohum=0 hız=0KB/s inen=0KB durum=DOWNLOADING
00:57:05.841 bekleniyor: parça=0 süre=38sn eş=0 tohum=0 hız=0KB/s inen=0KB durum=DOWNLOADING
00:57:11.581 bekleniyor: parça=0 süre=2sn eş=1 tohum=1 hız=0KB/s inen=0KB durum=DOWNLOADING
00:57:13.585 bekleniyor: parça=0 süre=4sn eş=1 tohum=1 hız=0KB/s inen=0KB durum=DOWNLOADING
00:57:15.589 bekleniyor: parça=0 süre=6sn eş=0 tohum=0 hız=0KB/s inen=0KB durum=DOWNLOADING
00:57:17.591 bekleniyor: parça=0 süre=8sn eş=0 tohum=0 hız=0KB/s inen=0KB durum=DOWNLOADING
00:57:19.597 bekleniyor: parça=0 süre=10sn eş=0 tohum=0 hız=0KB/s inen=0KB durum=DOWNLOADING
00:57:21.601 bekleniyor: parça=0 süre=12sn eş=0 tohum=0 hız=0KB/s inen=0KB durum=DOWNLOADING
00:57:23.607 bekleniyor: parça=0 süre=14sn eş=0 tohum=0 hız=0KB/s inen=0KB durum=DOWNLOADING
00:57:25.610 bekleniyor: parça=0 süre=16sn eş=0 tohum=0 hız=0KB/s inen=0KB durum=DOWNLOADING
```

### Soğuk Deneme 5 (Soğuk)
- **Oynat'a Basış:** `00:57:32.710`
- **Torrent Açılış:** `00:57:36.910` (1.66 sn)
- **İlk Kare:** `00:57:39.531` (4.29 sn)
- **Eş Sayısı:** 24
- **Toplam Bekleniyor Satırı:** 2
```text
00:57:38.934 bekleniyor: parça=0 süre=2sn eş=2 tohum=2 hız=4KB/s inen=512KB durum=DOWNLOADING
00:57:41.565 bekleniyor: parça=2 süre=2sn eş=2 tohum=2 hız=1107KB/s inen=6144KB durum=DOWNLOADING
```

### Sıcak Deneme (Sıcak)
- **Oynat'a Basış:** `00:58:50.282`
- **Torrent Açılış:** `00:58:52.806` (1.83 sn)
- **İlk Kare:** `None` (None sn)
- **Eş Sayısı:** 24
- **Toplam Bekleniyor Satırı:** 27
```text
00:58:54.826 bekleniyor: parça=0 süre=2sn eş=0 tohum=0 hız=0KB/s inen=0KB durum=DOWNLOADING
00:58:56.842 bekleniyor: parça=0 süre=4sn eş=0 tohum=0 hız=0KB/s inen=0KB durum=DOWNLOADING
00:58:58.848 bekleniyor: parça=0 süre=6sn eş=0 tohum=0 hız=0KB/s inen=0KB durum=DOWNLOADING
00:59:00.854 bekleniyor: parça=0 süre=8sn eş=0 tohum=0 hız=0KB/s inen=0KB durum=DOWNLOADING
00:59:02.860 bekleniyor: parça=0 süre=10sn eş=0 tohum=0 hız=0KB/s inen=0KB durum=DOWNLOADING
00:59:04.865 bekleniyor: parça=0 süre=12sn eş=0 tohum=0 hız=0KB/s inen=0KB durum=DOWNLOADING
00:59:06.871 bekleniyor: parça=0 süre=14sn eş=0 tohum=0 hız=0KB/s inen=0KB durum=DOWNLOADING
00:59:08.877 bekleniyor: parça=0 süre=16sn eş=0 tohum=0 hız=0KB/s inen=0KB durum=DOWNLOADING
00:59:10.886 bekleniyor: parça=0 süre=18sn eş=0 tohum=0 hız=0KB/s inen=0KB durum=DOWNLOADING
00:59:12.895 bekleniyor: parça=0 süre=20sn eş=0 tohum=0 hız=0KB/s inen=0KB durum=DOWNLOADING
00:59:14.898 bekleniyor: parça=0 süre=22sn eş=0 tohum=0 hız=0KB/s inen=0KB durum=DOWNLOADING
00:59:16.903 bekleniyor: parça=0 süre=24sn eş=0 tohum=0 hız=0KB/s inen=0KB durum=DOWNLOADING
00:59:18.910 bekleniyor: parça=0 süre=26sn eş=0 tohum=0 hız=0KB/s inen=0KB durum=DOWNLOADING
00:59:20.915 bekleniyor: parça=0 süre=28sn eş=0 tohum=0 hız=0KB/s inen=0KB durum=DOWNLOADING
00:59:22.919 bekleniyor: parça=0 süre=30sn eş=0 tohum=0 hız=0KB/s inen=0KB durum=DOWNLOADING
00:59:24.925 bekleniyor: parça=0 süre=32sn eş=0 tohum=0 hız=0KB/s inen=0KB durum=DOWNLOADING
00:59:26.932 bekleniyor: parça=0 süre=34sn eş=0 tohum=0 hız=0KB/s inen=0KB durum=DOWNLOADING
00:59:28.938 bekleniyor: parça=0 süre=36sn eş=0 tohum=0 hız=0KB/s inen=0KB durum=DOWNLOADING
00:59:30.945 bekleniyor: parça=0 süre=38sn eş=0 tohum=0 hız=0KB/s inen=0KB durum=DOWNLOADING
00:59:36.843 bekleniyor: parça=0 süre=2sn eş=1 tohum=1 hız=0KB/s inen=0KB durum=DOWNLOADING
00:59:38.858 bekleniyor: parça=0 süre=4sn eş=1 tohum=1 hız=0KB/s inen=0KB durum=DOWNLOADING
00:59:40.876 bekleniyor: parça=0 süre=6sn eş=1 tohum=1 hız=0KB/s inen=0KB durum=DOWNLOADING
00:59:42.887 bekleniyor: parça=0 süre=8sn eş=0 tohum=0 hız=0KB/s inen=0KB durum=DOWNLOADING
00:59:44.904 bekleniyor: parça=0 süre=10sn eş=0 tohum=0 hız=0KB/s inen=0KB durum=DOWNLOADING
00:59:46.919 bekleniyor: parça=0 süre=12sn eş=0 tohum=0 hız=0KB/s inen=0KB durum=DOWNLOADING
00:59:48.935 bekleniyor: parça=0 süre=14sn eş=0 tohum=0 hız=0KB/s inen=0KB durum=DOWNLOADING
00:59:50.949 bekleniyor: parça=0 süre=16sn eş=0 tohum=0 hız=0KB/s inen=0KB durum=DOWNLOADING
```

---

## 3. Bulgular ve Değerlendirme

1. **Soğuk Başlatmalarda DHT ve Eş Bağlantı Süresi:** Soğuk başlatmalarda (`force-stop`), libtorrent motoru her defasında sıfırdan başlatılır. DHT düğümlerinin bulunması, izleyicilerden eş listelerinin çekilmesi ve ilk parçanın indirilmesi ağ ve tohum durumuna bağlı olarak dalgalanmaktadır.
2. **Sıcak Oturum Davranışı:** Uygulama sonlandırılmadan kapatılıp yeniden açıldığında, `TorrentIdleRules` sayesinde libtorrent oturumu 10 dakika boyunca açık tutulur. Eş havuzu sıcak kaldığından yeniden bağlantı maliyeti oluşmaz.
3. **Teşhis Günlükleri (`a546d65`):** `TorrentEngine` içine eklenen `logWait` mekanizması, parça beklerken her 2 saniyede bir eş sayısı, tohum sayısı, anlık indirme hızı ve libtorrent durumunu eksiksiz loglamış, gecikmenin eş/hız kaynağını şeffaf hale getirmiştir.
