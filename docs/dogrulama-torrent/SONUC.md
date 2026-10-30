# Torrentio ve Yerleşik Torrent Motoru Ölçüm Sonuçları (2026-10-31)

Dal: `duzeltme/oynatici-saglamlik`
Cihaz: PlayNeon-130 (`emulator-5558`)
Eklenti: Torrentio (`v0.0.15` - NUVIO_ADDON / Stremio uyumlu)
Test İçeriği: Big Buck Bunny (2008), 1080p MP4 (Dosya boyutu: ~276 MB / 276.134.947 bayt, TMDB 10378, IMDb tt1254207)

---

## 1. Torrent Ölçüm Tablosu

| Deneme | Oturum Türü | Oynat'a Basış | Torrentten Önce Kaynak Denendi mi? | "Torrent Açıldı" Süresi | İlk Parça Süresi | İlk Kare Süresi | Eş Sayısı (Peers) | 60 sn Takılma Sayısı | Hata / Zaman Aşımı | Oynatıcı Kapanışı Log Satırı |
|---|---|---|---|---|---|---|---|---|---|---|
| **1** | Soğuk (`force-stop`) | 00:24:29.786 | Evet (HDFilmCehennemi 0.78 sn denendi, yok) | 1.82 sn | 1.82 sn | - | 27 | 0 | 40 sn zaman aşımı (sıradaki akışa geçildi) | `oynatıcı kapandı: oturum 10 dk boşta açık kalacak` |
| **2** | Soğuk (`force-stop`) | 00:25:38.448 | Evet (HDFilmCehennemi 0.88 sn denendi, yok) | 3.43 sn | 3.43 sn | - | 28 | 0 | 40 sn zaman aşımı (sıradaki akışa geçildi) | - |
| **3** | Soğuk (`force-stop`) | 00:26:47.089 | Evet (HDFilmCehennemi 0.76 sn denendi, yok) | 1.79 sn | 1.79 sn | **8.42 sn** | 29 | **0 (0.0 sn takılma)**, tampon: 22.8 sn | YOK | `oynatıcı kapandı: oturum 10 dk boşta açık kalacak` |
| **4** | **Sıcak (14 sn sonra)** | 00:28:02.161 | Evet (HDFilmCehennemi 0.75 sn denendi, yok) | 2.78 sn (istekten 2.02 sn) | 2.78 sn (istekten 2.02 sn) | **23.97 sn** | 27 | 2 (toplam 12.2 sn), tampon: 3.6 sn | YOK | `oynatıcı kapandı: oturum 10 dk boşta açık kalacak` |

---

## 2. Sıcak Oturum Doğrulaması (Torrent Oturumu 10 dk Boşta Açık)

- **Kural:** `TorrentIdleRules.IDLE_MS = 600_000L` (10 dakika). Oynatıcı kapatıldığında torrent verisi silinir, ancak libtorrent oturumu DHT ve eş havuzunu sıcak tutmak için 10 dakika boyunca arka planda açık tutulur.
- **Doğrulama:**
  - Deneme 3 sonunda saat `00:27:48.246`'da geri tuşuna basılarak oynatıcı kapatıldı. Günlükte şu satır görüldü:
    ```text
    10-31 00:27:48.246 D/PlayTorrent: oynatıcı kapandı: oturum 10 dk boşta açık kalacak
    ```
  - Uygulama sonlandırılmadı (`force-stop` yapılmadı, PID 13979 korundu).
  - 14 saniye sonra (30 sn'lik sınırın çok altında), saat `00:28:02.161`'de aynı film yeniden başlatıldı.
  - Günlükte `libtorrent oturumu başlıyor` satırı **yazılmadı** (oturumun baştan kurulmadığı, açık olan oturumun doğrudan kullanıldığı kanıtlandı).
  - Anında 27 eşle bağlantı sağlandı (`received peers: 27`), torrent 2.02 sn içinde açıldı ve ilk kare 23.97 sn'de oynatıcıya geldi.

---

## 3. İlgili Dosyalar Git Geçmişi

Komut:
`git log --since=2026-10-24 --format="%h %ad %s" --date=short -- app/src/main/java/com/kaan/play/data/torrent app/src/main/java/com/kaan/play/ui/player/PlaybackController.kt app/src/main/java/com/kaan/play/ui/player/SourceCoordinator.kt`

Çıktı:
```text
7ccb5b7 2026-10-30 feat(oynatici): IPv4 oncelikli DNS, tur yoklamasi, gecici cozucu yeniden denemesi, torrent oturumu bosta acik
53291d0 2026-10-30 fix(oynatici): HLS listesi TS sanilip kesilmesin (Joker 3002); eklentilere 50 sn butce; CS bulunan baglantilari korusun
9df6f43 2026-10-30 Revert "feat/fix: Premium UI neon glow entegrasyonu, kaba çizgilerin temizlenmesi ve v1.2.0 üzerine içerik yönlendirme hotfix'i"
517469a 2026-10-29 feat/fix: Premium UI neon glow entegrasyonu, kaba çizgilerin temizlenmesi ve v1.2.0 üzerine içerik yönlendirme hotfix'i
d82b8e6 2026-10-28 v1.1.20 - Multi-Profile, Supabase Cloud Sync, Speed Optimizations & Window Crash Armor
ed2fbfd 2026-10-27 release: v1.1.19 - HDFilmCehennemi ve DiziBOX eklenti akis cozumu ve oynatma iyilestirmeleri
6e918ad 2026-10-26 release: v1.1.18 - Trakt kalinti temizligi, DataStore kalkani ve LazyColumn key duzeltmesi
7508be4 2026-10-26 fix: v1.1.16 - CloudStream DataStore uyumlulugu ve PlayerActivity baslatma zırhlama
6747d04 2026-10-26 feat: release v1.1.15 with cold-start hardening, live TV, player gestures, trailer embed, and mod enhancements
9053b90 2026-10-25 fix(sources): configure Semaphore(6) and 8s timeout guard on plugin providers and SourceCoordinator
b7dab38 2026-10-25 fix(tvbox): prevent crashes, OOM and seek stalls on Android TV boxes
845b6cf 2026-10-25 fix(player): execute source onChange on main dispatcher to prevent wrong thread crashes
6b0825e 2026-10-25 feat(player): remove timeline focus ring and add playback speed selection
25650fc 2026-10-25 fix(player): normalize line endings to LF and remove extraneous blank lines
c606017 2026-10-25 fix(player): resolve source ranking ANR with caching, precalculation and debounced onChange
```

---

## 4. Kaydedilen Günlükler ve Ekran Görüntüleri

Tüm log dosyalarında adresler, magnet bağlantıları ve belirteçler kurallara uygun olarak maskelenmiştir:
- `docs/dogrulama-torrent/torrent-1.log`
- `docs/dogrulama-torrent/torrent-2.log`
- `docs/dogrulama-torrent/torrent-3.log`
- `docs/dogrulama-torrent/torrent-sicak.log`
- `docs/dogrulama-torrent/torrent_summary.json`
- Ekran görüntüleri:
  - `torrent-1-10sn.png`, `torrent-1-60sn.png`, `torrent-1-60sn-controls.png`, `torrent-1-geri.png`
  - `torrent-2-10sn.png`, `torrent-2-60sn.png`, `torrent-2-60sn-controls.png`, `torrent-2-geri.png`
  - `torrent-3-10sn.png`, `torrent-3-60sn.png`, `torrent-3-60sn-controls.png`, `torrent-3-geri.png`
  - `torrent-sicak-10sn.png`, `torrent-sicak-60sn.png`, `torrent-sicak-60sn-controls.png`, `torrent-sicak-geri.png`
