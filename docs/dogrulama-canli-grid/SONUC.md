# Canlı TV kompakt ızgara + kategori alt menüsü — yayın APK'si doğrulaması (2026-11-13)

Sürüm **1.3.2 (versionCode 49)**, imzalı R8 yayın APK'si (`Play-1.3.2.apk`), LDPlayer Android 9, 1600x900, 45→47→48→49 güncellemeleri (`install -r`, veri korundu).
SHA-256: `8a11030ec7e8c5905bd6ceee3832745609a370516901c7b5580b8d052b019eda`

Not: 04–14 ve 19–26 numaralı görüntüler 48'de çekildi. 48→49 farkı yalnızca ızgara sütun sayısının yazı ölçüsü > 1.0'da azalması; 1.0'daki düzen aynıdır (18 numara 49 ile yeniden çekildi, aynı düzen). Logcat taraması 49 oturumu içindir (11:42'den sonra).

## Bulunan ve düzeltilen
1. "2 kayn…" kesiliyordu ve "BeIN Sports 1 …" gibi adlar 2 satırda kesiliyordu → ⋯ düğmesi görselin köşesine taşındı (etiket satırı tam genişlik), ad en çok 3 satır (versionCode 48).
2. Yazı ölçüsü 2.0'da "CAN…" kesiliyordu → sütun sayısı yazı ölçüsüyle azalır (`TvScreenRules.calculateGridColumns(width, fontScale)`; 1067 dp: 1.0→8, 1.3→6, 2.0→4), birim testli (49).

## Sonuç tablosu
| Madde | Sonuç | Kanıt |
|---|---|---|
| a) Kompakt ızgara, ad tam okunuyor, etiket kesilmiyor (1.0) | GEÇTİ (en uzun ad "BeIN Sports Max 1 HD" 3 satırda tam; "2 kaynak" tam) | 18_font_1_0_final.png |
| b) Kategori alt menüsü açılır / süzer / çip X kaldırır / Geri menüyü kapatır | GEÇTİ (Haber: 15 kanal) | 04, 05, 06, 07 |
| c) Kompakt karoda favori, listeye ekle, sıralama (dokunmatik + kumanda Tamam) | GEÇTİ (favori, Manuel sıra'da Aşağı taşı); "Listeye ekle" yalnızca menüde görüldü, ekleme penceresi bu turda denenmedi | 08–12, 20–21 |
| d) Arama sayısı, tek kanal, sonuç yok metni TR + EN | GEÇTİ ("1 canlı kanal"/"1 live channel", "Bu aramayla eşleşen kanal yok."/"No channels match this search.") | 13, 14, 29, 30 |
| e) D-pad: ızgara/üst menü/Kategori arası, odak halkası, Tamam kanal açar, Geri | GEÇTİ (aşağıdaki not) | 19_dpad_a–e, 22, 23, 25, 26 |
| f) Yazı ölçüsü 1.0 / 1.3 / 2.0, TR + EN | GEÇTİ (1.3'te 1 satırlık "Sports Max 1 …" sorunu 49'da sütun azalmasıyla çözüldü; 2.0 TR+EN kesilme yok) | 15, 16, 17, 31 |
| g) Canlı kanal 60 sn + Geri, kara ekran yok | GEÇTİ (Bengü Türk, 62. sn canlı görüntü; Geri listeye döndü, odak aynı kartta) | 23_kanal_3s, 23_kanal_12s, 24, 25 |
| h) Regresyon | GEÇTİ: Criminal Minds Bölümler paneli açıldı, B8 seçildi (eski bölüm arama sırasında oynadı, sonra B8 açıldı), panelde Geri paneli kapattı oynatıcıdan çıkmadı; Joker'de Bölümler düğmesi yok; Örümcek-Adam oynuyor; HDFilmCehennemi kaynağı açıldı (Joker, Örümcek-Adam, VisionQuest); Kütüphane ve Ayarlar açıldı | 33–43 |
| i) logcat `VerifyError|FATAL|ClassNotFound|NoSuchMethod` | 0 (3201 satır, 49 oturumu) | — |

## Notlar / bilinen sınırlar
- ⋯ düğmesi logonun sağ alt köşesine biner (yarı saydam koyu daire); küçük logolarda köşeyi örter.
- D-pad: Kategori'den Aşağı doğrudan ızgaraya (en sağ sütun) gider, kaynak çipleri (Tümü/Nuvio) arama kutusundan Aşağı ile ulaşılır. Karttan Sağ ile ⋯ düğmesine, oradan Tamam ile seçenekler açılır.
- "Bu sürümde neler yeni?" penceresi (`whats_new_message`) 1.3.2'de gösteriliyor ama eski sürüm maddelerini listeliyor; Canlı TV değişikliğini içermiyor (bu turda değiştirilmedi).
- Test sırasında cihaz durumu: favori/sıralama/dil/yazı ölçüsü eski haline getirildi. Criminal Minds geçmişi S1 B8'e, Joker/Örümcek-Adam/VisionQuest konumları birkaç dakika ilerledi.
- Çift altyazı görüntüsü (video içi yakılmış + bizim altyazı) Criminal Minds'ta görüldü; bu işle ilgisiz.
