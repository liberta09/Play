# Oynatıcı "Bölümler" paneli — emülatör doğrulaması (2026-11-13)

Dal `feature/bolumler-paneli`. Cihaz: LDPlayer `127.0.0.1:5555` (1600x900, Türkçe). Eklentiler: DiziBOX TV ve HDFilmCehennemi (ikisi de Nuvio JS eklentisi; CloudStream eklentisi kurulu değil, logda `streams: 0 açık CS eklentisi`).

## Derleme / test
- `.\gradlew assembleDebug testDebugUnitTest`: BUILD SUCCESSFUL, **1528 test, 0 hata**. Düzeltmeden sonra yeniden: 1528 test, 0 hata.

## Bulunan ve düzeltilen tek hata
- **Panel kapanınca kumanda odağı kayboluyordu.** Bölümler açıkken kontroller bileşimden çıkıyor (`showControls` = `!showEpisodes && …`), kapanınca odak etkisi (`PlayerScreen.kt`, "Odak:" `LaunchedEffect`) yeniden çalışmıyordu; hiçbir düğmede halka yoktu ve Sol/Sağ geri/ileri sarmıyordu (Parçalar/Kaynak panelleri kapanınca düğmesine odak veriyor, bunlar tutarsızdı). Düzeltme: etkinin anahtarlarına `showEpisodes` eklendi, kontrol-çubuğu dalı `!showEpisodes` iken çalışıyor; kapanınca odak ilerleme çubuğuna gelir (kumandada kontroller açılınca varsayılan odak ile aynı yer). Önce/sonra: `q1_closed.png`, `q2_left_after_close.png` (önce: halka yok, Sol sarmıyor) → `s2_closed_focus.png`, `s3_right_after_close.png` (sonra: tutamaç odakta, Sağ +10 sn sardı).

## Adımlar
| Adım | Sonuç | Kanıt |
|---|---|---|
| (a) Dizide alt çubukta "Bölümler", "Sonraki bölüm"ün solunda | GEÇTİ (VisionQuest HDFilmCehennemi, Criminal Minds DiziBOX). Geçmişten devam ettiği için VisionQuest **B4**'ten açıldı (B1 değil). | `s4_controls.png`, `k0_cm_player.png` |
| (a) Filmde (Joker, Örümcek-Adam) ve canlı kanalda (A2 TV HD) düğme yok | GEÇTİ | `z5_joker_bar.png`, `hh2_spider_player.png`, `aa1_live_20s.png` |
| (b) Kumandayla panel: sağda, arka plansız, düğmeler/üst çubuk/saat gizli, odak "Şimdi oynuyor"da, video akıyor | GEÇTİ. Panel açılırken saat çubuğu ~1 sn görünüp gizleniyor (`d1_immediate.png`, sonra `d2_2s.png` gizli). | `d0_focus.png`, `d1_immediate.png`, `d2_2s.png` (iki karede video ilerlemiş) |
| (c) DOWN×3 + CENTER: "kaynak aranıyor", eski bölüm oynuyor, yeni bölüm açılıyor | GEÇTİ (B4 → B7). İleti `S1 B7 için kaynak aranıyor…`, sırasında B4 10:52'de oynuyordu | `e0_before_select.png`, `e1_searching.png`, `e3_title.png` |
| (c) Eski bölüm "izlendi" sayılmıyor | GEÇTİ. Kütüphane > Geçmiş: B4 "40 dk kaldı", B7 "63 dk kaldı", B6 "24 dk kaldı" (hiçbiri izlendi değil); panelde B4'te onay işareti yok | `i0_library.png`, `g2_after_tap2.png` |
| (d) Çok sezonlu dizide (Criminal Minds) Sağ/Sol sezon, başlık güncelleniyor, Geri paneli kapatıyor | GEÇTİ: Sağ → "Sezon 2", Sol×2 → "Sezon 1" (Şimdi oynuyor), Geri → oynatıcı açık (2:38 oynuyor) | `p1_panel.png`, `p2_season2.png`, `p4_back_s1.png`, `p5_after_back.png` |
| (d) Kapanınca odak | Düzeltme öncesi: **hiçbir yerde** (hata, yukarıda). Sonrası: ilerleme çubuğunda | `q1_closed.png` → `s2_closed_focus.png` |
| (e) Dokunmatik: satıra dokunmak, sezon okları, videoya dokunmak | GEÇTİ: düğmeye dokunma paneli açtı, satıra dokunma `S1 B6/B4 için kaynak aranıyor…` + açılış, sağ ok "Sezon 2", sol ok geri, sol tarafta videoya dokunma paneli kapattı. (Kontroller gizliyken ilk dokunuş yalnızca kontrolleri açar; ikinci dokunuş düğmeye basar.) | `g2_after_tap2.png`, `h0_after_video_tap.png`, `h2_row6_tap.png`, `h3_after.png`, `u2_touch_s2.png`, `u4_touch_row3.png`, `u5_title.png` |
| (f) DiziBOX ile (b)–(e) | GEÇTİ (Criminal Minds S1 B1 → B2 kumandayla, → B4 dokunmatikle; sezonlar, Geri) | `t1_toast.png`, `t2_title.png`, `u2_touch_s2.png`, `u4_touch_row3.png`, `u5_title.png` |
| (f) Liste kaynağı | **Eklentinin kendi listesi yolu denenemedi.** `CsNextEpisode` bir günlük etiketi değil (koda yalnızca sınıf adı olarak geçer, logcat'te satır yok). Her iki eklenti de Nuvio JS, CloudStream eklentisi kurulu değil; istekte `pluginId/contentUrl` yok, `PlayerActivity.loadEpisodes` TMDB yoluna düşer (`tmdb.season`). Bu yol çalıştı; eklenti listesi yolu bu cihazda kanıtlanamadı. | kod: `PlayerActivity.kt` `loadEpisodes` |
| (g) Yazı ölçeği 1.3 | GEÇTİ: alt çubuk ("Yeniden boyutlandır" iki satıra sarar) ve panel taşmıyor. Not: ölçek değişimi oynatıcıyı yeniden oluşturdu (B4 0:02'den başladı); bu panelle ilgili değil, ayarın genel etkisi. Sonra 1.0'a döndü. | `v1_font13_bar.png`, `v2_font13_panel.png` |
| (g) Türkçe ve İngilizce arayüz | GEÇTİ: İngilizce'de "Episodes", "Season 1", "Episode 4", "Now playing", "Searching source for S1 E5…" taşmadan. Dil Ayarlar > Dil'den değiştirildi ve **Sistem**'e (özgün değer; cihaz Türkçe) geri alındı. | `y2_en_bar.png`, `y3_en_panel.png`, `y4_en_toast.png`, `z1_lang_restored.png` |
| (h) "Sonraki bölüm" düğmesi | GEÇTİ (B4 → B5) | `ee0_next_focus.png`, `ee1_after_next.png` |
| (h) Otomatik sonraki bölüm kartı | GEÇTİ: sona sarınca "Sonraki bölüm: S1 B6 · 6 sn (Şimdi oynat / İptal)", süre bitince B6 açıldı | `ff0_card.png`, `ff1_after_countdown.png`, `gg0_autonext_result.png` |
| (h) Kaynak ve Parçalar panelleri | GEÇTİ: açılıp Geri/İptal ile kapandı, oynatıcı açık kaldı | `m0_after_back_source.png`, `r1_tracks_open.png`, `r2_tracks_closed.png` |
| (h) Örümcek-Adam, Joker, Criminal Minds oynuyor; canlı kanal 60 sn + Geri | GEÇTİ (canlı: A2 TV HD, ~60 sn sonra Geri → Canlı TV listesi) | `hh2_spider_player.png`, `z5_joker_bar.png`, `k0_cm_player.png`, `aa2_live_60s.png`, `aa3_live_after_back.png` |
| (h) Çökme | GEÇTİ: tüm oturumda logcat `FATAL EXCEPTION` 0, `ANR in` 0, süreç ölümü 0 | logcat |

## Notlar / sınırlar
- Kontroller birkaç sn sonra gizlendiği için kumanda dizisi (Yukarı, Aşağı×2, Sağ×5–6, Tamam) tek komut grubunda verilmeli; gizliyken Tamam oynat/duraklat, Aşağı Kaynak menüsünü açar (`PlayerKeys.kt`). Bunlar yeni davranış değil.
- Tüm ekran görüntüleri bu klasördedir; yanlış ekran olduğu anlaşılanlar silindi.
- `docs/dogrulama-bolumler/` dışındaki kök PNG'ler commit'lenmedi.
