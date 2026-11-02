# Doğrulama Raporu: Blok Düzeyinde Torrent Açılış Hızlandırması

**Tarih:** 2 Kasım 2026  
**Hedef Dal:** `ozellik/torrent-blok`  
**Test Ortamı:** LDPlayer 9 Emülatörü (`emulator-5554`, Samsung SM-X710N profili), Android 9 / API 28  
**Durum:** ARA RAPOR (ADIM 0 Tamamlandı, ADIM 1 ve ADIM 2 Uygulanıyor)

---

## ADIM 0 (FİZİBİLİTE KANITI) ÖZETİ

- **Karar:** **EVET** (Blok düzeyinde okuma ve servis fizibıldır).
- **libtorrent4j API Bulguları:**
  - `TorrentHandle.getDownloadQueue()` -> Her parçanın `blocksInPiece()`, `finished()`, `writing()`, `requested()` durumlarını verir.
  - `BlockFinishedAlert` -> İnen her 16 KiB bloğu `pieceIndex()` ve `blockIndex()` ile anında bildirir.
  - `torrent_status` parça bit alanı -> Parça tam doğrulanana dek `havePiece == false` ve `bit == false` kalır.
  - `setPosixDiskIO()` -> Parça henüz doğrulanmamışken (`havePiece == false`), inen 16 KiB bloklar diskteki dosyadan `RandomAccessFile` ile hemen okunabilmektedir.
  - **Bütünlük Testi:** Deneyde okunan **731 blok**tan daha sonra parçası tamamlanan **505 blok** tekrar okunup karşılaştırılmış, **%100 CRC32 eşleşmesi (0 uyuşmazlık)** elde edilmiştir.
  - Ayrıntılı kanıtlar ve ham loglar: [`docs/dogrulama-torrent-blok/ADIM0.md`](ADIM0.md) ve [`docs/dogrulama-torrent-blok/ADIM0_proof.json`](ADIM0_proof.json).

---

## SONRAKİ ADIMLAR

1. **ADIM 1:** Blok düzeyinde servis (`TorrentEngine` / `TorrentBlockTracker` / `TorrentHttpServer`).
   - 16 KiB blok aralığı kontrolü ve servis edilebilirlik mantığı (Android'den bağımsız, birim testli).
   - Servis edilen doğrulanmamış bloklar için CRC32 bütünlük kaydı ve parça bittiğinde kontrolü (`BLOK_UYUMSUZ` durumu).
   - Blok modunu tek bayrakla (`BLOCK_MODE_ENABLED`) kapatılabilirlik.
2. **ADIM 2:** Paralel önceliklendirme (başlık, kuyruk ve ExoPlayer video başlangıç ofseti için eşzamanlı deadline).
3. **ADIM 3:** Civil War FLUX (19.9 GB 4K) ve küçük parça boyutlu torrent ile 5 soğuk koşu benchmark ölçümleri.
