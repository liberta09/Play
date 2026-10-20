# PLAY Özel Katalog (Nuvio / Stremio Addon)

Bu servis, TMDB'den canlı katalog verisi üreten Stremio Addon protokolünü uygular. Nuvio ve Stremio uyumlu istemcilerde kullanılmak üzere tasarlanmıştır.

## Kataloglar
1. PLAY – Yeni Eklenen Filmler: son 90 günde yayınlanan filmler, yayın tarihine göre azalan.
2. PLAY – Yeni Eklenen Diziler: son 90 günde yayınlanan diziler, ilk yayın tarihine göre azalan.
3. PLAY – Haftanın Filmleri: son 7 günde yayınlanan filmler.
4. PLAY – Yerli Filmler: Türkiye menşeli filmler.
5. PLAY – Yerli Diziler: Türkiye menşeli diziler.

Yalnızca gerçek TMDB sonuçları kullanılır; afişi veya IMDb kimliği bulunmayan kayıtlar atlanır. Böylece katalog kimlikleri oynatma kaynaklarının beklediği IMDb `tt...` biçiminde kalır.

## Vercel dağıtımı
- Vercel projesinin Root Directory değerini `catalog-addon` yap.
- Environment Variables bölümüne `TMDB_API_KEY` ekle (Production, Preview ve Development hedeflerine gerektiği şekilde).
- Dağıtımdan sonra `https://<proje-adresi>/manifest.json` adresi addon manifestini döndürmelidir.

TMDB API anahtarını kaynak koda veya Git'e ekleme. TMDB verisi içerik akışı/izleme URL'si sağlamaz; yalnızca metadata ve katalog listeleri sunar.

## Play uyumluluğu notu
Play'in mevcut sürümündeki Nuvio addon doğrulayıcısı yalnızca `stream` veya `subtitles` kaynaklarını kabul ediyor. Bu katalog eklentisinin Play içindeki Eklentiler bölümünden kurulabilmesi ve ana sayfa satırları olarak gösterilebilmesi için Play uygulamasında ayrıca `catalog` kaynağını tanıyıp katalog yanıtlarını işleyen entegrasyon gerekir. Nuvio tarafı bu servisi Stremio katalog Addon'u olarak kullanabilir.
