# ADIM 0: FİZİBİLİTE KANITI - Blok Düzeyinde Okuma ve Servis

**Tarih:** 2 Kasım 2026  
**Dal:** `ozellik/torrent-blok`  
**Test Cihazı:** LDPlayer 9 Emülatörü (`emulator-5554`, Samsung SM-X710N, Android 9 / API 28)  
**Kütüphane:** `libtorrent4j 2.1.0-39` (x86_64 JNI / POSIX Disk I/O)  
**Test İçeriği:** Captain America: Civil War (Torrentio FLUX 19.9 GB 2160p, `cf5290d3f602bbeb0de9cb4419251c98c0e6db70`)  
**Parça Boyutu:** 8 MiB (8.388.608 bayt = 512 adet 16 KiB blok)  

---

## SONUÇ: EVET (YAPILABİLİYOR)

libtorrent4j 2.1.0-39 ile blok düzeyi bilgi tam olarak alınabilmekte, `setPosixDiskIO()` ile inen 16 KiB bloklar parça henüz doğrulanmamışken (`havePiece == false`) diskteki dosyadan **HEMEN VE EKSİKSİZ OKUNABİLMEKTEDİR**. Doğrulanan parçalardaki blokların CRC32 karşılaştırmasında **%100 uyum (0 uyuşmazlık)** elde edilmiştir.

---

## 1. DÖRT KRİTİK UNSURUN İNCELENMESİ VE KANITI

### 1.1 `TorrentHandle.getDownloadQueue()` (PartialPieceInfo ve Blok Durumları)
- **Durum:** **BAŞARILI**
- `handle.getDownloadQueue()` çağrısı, o anda indirilmekte olan tamamlanmamış parçaların `ArrayList<PartialPieceInfo>` listesini döndürmektedir.
- `PartialPieceInfo` nesnesi şu metotlarla anlık durumu vermektedir:
  - `pieceIndex()`: Parça indeksi (ör. 0, 789, 828).
  - `blocksInPiece()`: Parçadaki toplam 16 KiB blok sayısı (Civil War için **512**).
  - `finished()`: Diske yazılan/tamamlanan blok sayısı (1..512 arası artan sayaç).
  - `writing()`: Diske yazılma aşamasındaki blok sayısı.
  - `requested()`: Eşlerden talep edilmiş bekleyen blok sayısı.
- **Ham JSON Kanıtı (`ADIM0_proof.json`):**
  ```json
  {
    "pieceIndex": 0,
    "blocksInPiece": 512,
    "finished": 1,
    "writing": 0,
    "requested": 5
  },
  {
    "pieceIndex": 0,
    "blocksInPiece": 512,
    "finished": 2,
    "writing": 0,
    "requested": 6
  },
  {
    "pieceIndex": 0,
    "blocksInPiece": 512,
    "finished": 3,
    "writing": 0,
    "requested": 7
  }
  ```

---

### 1.2 `BlockFinishedAlert` (`block_finished_alert`)
- **Durum:** **BAŞARILI**
- `AlertListener` üzerinden dinlenen `BlockFinishedAlert`, bir 16 KiB blok eşten inip diske yazıldığında gerçek zamanlı olarak tetiklenmektedir.
- `alert.pieceIndex()` ve `alert.blockIndex()` metodları doğrudan ilgili parçayı ve blok numarasını vermektedir.
- **Ham Logcat Kanıtı:**
  ```text
  11-02 18:22:31.747  4610  4712 I ADIM0_FEASIBILITY: BLOK_OKUNDU: parca=0 blok=0 havePiece=false ofset=0 nonZero=6143 crc=3960567314 queueFin=1
  11-02 18:22:31.752  4610  4712 I ADIM0_FEASIBILITY: BLOK_OKUNDU: parca=0 blok=1 havePiece=false ofset=16384 nonZero=9795 crc=3101466350 queueFin=2
  11-02 18:22:31.829  4610  4712 I ADIM0_FEASIBILITY: BLOK_OKUNDU: parca=0 blok=2 havePiece=false ofset=32768 nonZero=10201 crc=2878280388 queueFin=3
  11-02 18:22:31.845  4610  4712 I ADIM0_FEASIBILITY: BLOK_OKUNDU: parca=0 blok=3 havePiece=false ofset=49152 nonZero=9868 crc=1054206897 queueFin=4
  11-02 18:22:31.859  4610  4712 I ADIM0_FEASIBILITY: BLOK_OKUNDU: parca=0 blok=4 havePiece=false ofset=65536 nonZero=10029 crc=1263473176 queueFin=5
  ```

---

### 1.3 `torrent_status` Parça Bit Alanı (`PieceIndexBitfield`)
- **Durum:** **BAŞARILI**
- `handle.status(TorrentHandle.QUERY_PIECES).pieces()` ile parçaların tamamlanma bit alanı (`PieceIndexBitfield`) sorgulanabilmektedir.
- `handle.havePiece(p)` ve `pieces.getBit(p)` metodları, parça tüm 512 bloğu inip SHA-1 hash kontrolünden geçene kadar `false` dönmektedir.
- **Ham JSON Kanıtı:**
  ```json
  "bitfield": {
    "size": 2358,
    "count": 1,
    "bit0": false,
    "havePiece0": false
  }
  ```

---

### 1.4 `setPosixDiskIO()` ile Biten Blokların Doğrulanmamış Parçada Hemen Okunabilirliği
- **Durum:** **BAŞARILI VE KANITLANDI**
- `SessionParams(settings).apply { setPosixDiskIO() }` kullanıldığında, libtorrent POSIX dosya tanıtıcısına doğrudan `pwrite()` ile blokları anında yazar.
- Deney sırasında:
  - Toplam **731 blok**, parçaları henüz doğrulanmamışken (`havePiece == false`) `RandomAccessFile` ile diskten doğrudan okundu.
  - Okunan her blokta gerçek veri (boş olmayan baytlar) tespit edildi (ör. `parca=0 blok=0 ofset=0 nonZero=6143`).
  - Daha sonra bu parçalar tamamlanıp `PieceFinishedAlert` geldiğinde (`havePiece == true`), doğrulanmış parçadaki aynı bloklar tekrar okunup CRC32 hesaplandı.
  - Karşılaştırılan **505 bloğun tamamında (%100)**:
    $$\text{oncekiCrc} == \text{dogrulanmisCrc} \implies \text{eslesiyor} = \text{true}$$
  - **Uyuşmazlık Sayısı: 0**
- **Ham Logcat Kanıtı (Örnek Karşılaştırma Satırları):**
  ```text
  11-02 18:19:00.251  4252  4306 I ADIM0_FEASIBILITY: PARCA_DOGRULANDI: parca=789 havePiece=true
  11-02 18:19:00.251  4252  4306 I ADIM0_FEASIBILITY: CRC_KARSILASTIRMA: parca=789 blok=413 oncekiCrc=2240671412 dogrulanmisCrc=2240671412 eslesiyor=true
  11-02 18:19:00.251  4252  4306 I ADIM0_FEASIBILITY: CRC_KARSILASTIRMA: parca=789 blok=414 oncekiCrc=750258752 dogrulanmisCrc=750258752 eslesiyor=true
  11-02 18:19:00.251  4252  4306 I ADIM0_FEASIBILITY: CRC_KARSILASTIRMA: parca=789 blok=415 oncekiCrc=2255255599 dogrulanmisCrc=2255255599 eslesiyor=true
  11-02 18:19:00.251  4252  4306 I ADIM0_FEASIBILITY: CRC_KARSILASTIRMA: parca=789 blok=416 oncekiCrc=2482840119 dogrulanmisCrc=2482840119 eslesiyor=true
  11-02 18:19:00.299  4252  4306 I ADIM0_FEASIBILITY: CRC_KARSILASTIRMA: parca=789 blok=0 oncekiCrc=3801570761 dogrulanmisCrc=3801570761 eslesiyor=true
  11-02 18:19:00.299  4252  4306 I ADIM0_FEASIBILITY: CRC_KARSILASTIRMA: parca=789 blok=1 oncekiCrc=1978817850 dogrulanmisCrc=1978817850 eslesiyor=true
  11-02 18:19:00.299  4252  4306 I ADIM0_FEASIBILITY: CRC_KARSILASTIRMA: parca=789 blok=2 oncekiCrc=2164952265 dogrulanmisCrc=2164952265 eslesiyor=true
  ```

---

## 2. ADIM 1'E GEÇİŞ KARARI

Fizibilite kanıtı **EVET** olarak kesinleştiği için:
1. `TorrentEngine` ve HTTP servis katmanında blok düzeyinde okuma mekanizması (ADIM 1) uygulanacaktır.
2. Servis edilen doğrulanmamış aralıkların CRC32 bütünlük takibi yapılacak, parça tamamlandığında doğrulanmış veriyle uyuşmazlık olursa loglanıp parça düzeyine dönülecektir.
3. Paralel önceliklendirme (ADIM 2) ile açılış ofsetleri eşzamanlı talep edilecektir.
