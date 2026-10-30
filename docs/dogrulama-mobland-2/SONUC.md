# HDFilmCehennemi v1.0.3 Deneme Sonuçları (2026-10-31)

Dal: `duzeltme/oynatici-saglamlik` (Commit: `50c36d1`)  
Cihaz: PlayNeon-130 (`emulator-5558`)  
Eklenti: HDFilmCehennemi (`v1.0.3` - Nuvio JS Plugin)  

---

## 1. Test Sonuç Tablosu

| İçerik | İstek → Akış (sn) | İlk Görüntü (sn) | Sonuç | Notlar |
|---|---|---|---|---|
| **MobLand S2 B7** | 1.61 sn | **3.55 sn** | BAŞARILI | Sağlayıcı: HDFilmCehennemi; Kaynak: HDFilmCehennemi; rapidrame mevcut; Süre: kaynak=HDFilmCehennemi (s231.rapidrame.com) süre=53.5 dk beklenen=54 dk tür=Tv -> Ok; 60s konum=0:56, takılma=0 |
| **MobLand S1 B1** | 1.57 sn | **4.15 sn** | BAŞARILI | Sağlayıcı: HDFilmCehennemi; Kaynak: HDFilmCehennemi; Süre: kaynak=HDFilmCehennemi (srv12.cdnimages3606.cyou) süre=56.8 dk beklenen=60 dk tür=Tv -> Ok; 60s konum=0, takılma=0 |
| **Joker (2019)** | 1.5 sn | **2.34 sn** | BAŞARILI | Sağlayıcı: HDFilmCehennemi; Kaynak: HDFilmCehennemi; Süre: kaynak=HDFilmCehennemi (srv12.cdnimages1157.shop) süre=121.8 dk beklenen=122 dk tür=Movie -> Ok; 60s konum=1:01, takılma=0 |
| **Örümcek-Adam: Yepyeni Bir Gün** | 1.4 sn | **2.31 sn** | BAŞARILI | Sağlayıcı: HDFilmCehennemi; Kaynak: HDFilmCehennemi; Süre: kaynak=HDFilmCehennemi (srv12.cdnimages2845.cyou) süre=148.2 dk beklenen=148 dk tür=Movie -> Ok; 60s konum=0, takılma=0 |
| **Criminal Minds S1 B2** | 3.21 sn | **4.07 sn** | BAŞARILI | Sağlayıcı: DiziBOX TV; Kaynak: DiziBOX TV; Süre: kaynak=DiziBOX TV (dbx.molystream.org) süre=44.4 dk beklenen=43 dk tür=Tv -> Ok; 60s konum=0, takılma=0 |

---

## 2. Ayrıntılı Değerlendirme ve Analiz

### a) MobLand S2 B7 (Asıl Hedef)
- **Oynatma Durumu:** BAŞARILI
- **İstek → Akış Süresi:** 1.61 sn
- **İlk Kare Süresi:** 3.55 sn
- **Kullanılan Sağlayıcı:** HDFilmCehennemi
- **Kullanılan Kaynak:** HDFilmCehennemi
- **Oynatıcı Sayfası (rplayer):** Bulundu ve başarıyla indirildi (www.hdfilmcehennemi.nl /rplayer/... len=37389)
- **Akış Listeye Girdi mi:** EVET (rapidrame master.m3u8 akışı listeye girdi)
- **rapidrame CDN Referer Başlıkları:** Doğru biçimde eklendi (Referer, Origin, User-Agent)
- **Doğrulama (verifyStream / İçerik Süresi Kontrolü):** kaynak=HDFilmCehennemi (s231.rapidrame.com) süre=53.5 dk beklenen=54 dk tür=Tv -> Ok
- **60 sn Oynatma:** Konum: `0:56`, Takılma: 0 kez (0.0 sn)
- **Geri Tuşu:** Sorunsuz çalıştı, ana ekrana dönüldü
- **Çökme Günlüğü:** Çökme YOK (0)

### b) MobLand S1 B1 (Eski Fonksiyon Kontrolü)
- **Oynatma Durumu:** BAŞARILI
- **İstek → Akış Süresi:** 1.57 sn
- **İlk Kare Süresi:** 4.15 sn
- **Kullanılan Sağlayıcı:** HDFilmCehennemi
- **Kullanılan Kaynak:** HDFilmCehennemi
- **Geri Tuşu:** Sorunsuz çalıştı
- **Çökme Günlüğü:** Çökme YOK (0)

### c) Gerileme Kontrolleri (Joker, Örümcek-Adam, Criminal Minds)
- **Joker (2019):** İlk Kare: 2.34 sn | Sağlayıcı: HDFilmCehennemi | Sonuç: BAŞARILI
- **Örümcek-Adam: Yepyeni Bir Gün:** İlk Kare: 2.31 sn | Sağlayıcı: HDFilmCehennemi | Sonuç: BAŞARILI
- **Criminal Minds S1 B2:** İlk Kare: 4.07 sn | Sağlayıcı: DiziBOX TV | Sonuç: BAŞARILI

---

## 3. Günlük Dosyaları

Tüm günlükler hassas veri kurallarına uygun olarak çerez, token ve tam akış adresleri maskelenerek kaydedilmiştir:
- `mobland-s2e7.log` (Ham logcat dökümü)
- `mobland-s2e7-filtered.log` (PlayPlugin, PlayPluginNet, PlayMatch filtreli günlük)
- Ekran görüntüleri: `mobland-s2e7-10sn.png`, `mobland-s2e7-60sn.png`, `mobland-s2e7-60sn-controls.png`, `mobland-s2e7-geri.png`
- `mobland-s1e1.log` (Ham logcat dökümü)
- `mobland-s1e1-filtered.log` (PlayPlugin, PlayPluginNet, PlayMatch filtreli günlük)
- Ekran görüntüleri: `mobland-s1e1-10sn.png`, `mobland-s1e1-60sn.png`, `mobland-s1e1-60sn-controls.png`, `mobland-s1e1-geri.png`
- `joker-2019.log` (Ham logcat dökümü)
- `joker-2019-filtered.log` (PlayPlugin, PlayPluginNet, PlayMatch filtreli günlük)
- Ekran görüntüleri: `joker-2019-10sn.png`, `joker-2019-60sn.png`, `joker-2019-60sn-controls.png`, `joker-2019-geri.png`
- `spiderman.log` (Ham logcat dökümü)
- `spiderman-filtered.log` (PlayPlugin, PlayPluginNet, PlayMatch filtreli günlük)
- Ekran görüntüleri: `spiderman-10sn.png`, `spiderman-60sn.png`, `spiderman-60sn-controls.png`, `spiderman-geri.png`
- `criminal-s1e2.log` (Ham logcat dökümü)
- `criminal-s1e2-filtered.log` (PlayPlugin, PlayPluginNet, PlayMatch filtreli günlük)
- Ekran görüntüleri: `criminal-s1e2-10sn.png`, `criminal-s1e2-60sn.png`, `criminal-s1e2-60sn-controls.png`, `criminal-s1e2-geri.png`
