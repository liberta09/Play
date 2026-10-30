# HDFilmCehennemi 1.0.3 (Play deneme düzeltmesi)

Kaynak: kratoskrator/hdfilmcehennemi-nuvio 1.0.2 (MIT, lisans başlığı dosyada duruyor).
Üç küçük değişiklik (MobLand S2 B7 gibi `/rplayer/` sayfalı bölümler için):
1. `players()` yalnızca `/embed/` değil `/rplayer/` iframe'lerini de bulur.
2. `seededProfile` işlem kalıbında `;` isteğe bağlı.
3. `extractPlayer` birden çok script varsa birleştirip `parseSeededDecoders` ile de dener.

Node'da kaydedilmiş sayfalarla doğrulandı: S2 B7 → 1 akış (rapidrame master.m3u8), S1 B1 → önceki gibi 2 oynatıcı.
Cihazda denenmedi.
