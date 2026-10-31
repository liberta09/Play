# Doğrulama Raporu: Android TV Geçmiş Listesi Çarpı (X) Düğmesi Kumanda/D-pad Düzeltmesi

- **Tarih:** 2026-11-01
- **Dal:** `fix/tv-gecmis-carpi` (main dalından oluşturuldu)
- **Cihaz / Emülatör:** `emulator-5554` (model: `SM_X710N`, Android 9 / API 28)
- **Dokunulmayan Cihazlar:** Fiziksel telefon (`d3ed3deb` - resmi sürüm yüklü, debug imzası farklı; dokunulmadı/kaldırılmadı), LDPlayer örnek 0 ve PlayNeon cihazlarına dokunulmadı.

---

## 1. Kök Neden ve Kanıt

### Kök Neden
`app/src/main/java/com/kaan/play/ui/library/LibraryScreen.kt` içindeki `HistoryRow` bileşeni, tüm satırı kapsayan tek bir tıklanabilir `androidx.compose.material3.Surface(onClick = onClick)` içinde yer almaktaydı (`bounds: [50, 427, 1550, 580]`). Satırın en sağındaki geçmiş kaydını silme çarpı düğmesi olan `IconButton(onClick = onRemove)` ise bu tıklanabilir `Surface`'in bir alt öğesi (çocuğu) olarak konumlanmıştı (`bounds: [1460, 468, 1532, 540]`).

Compose'un 2D uzamsal odak arama mekanizması (`FocusDirection.Right`), mevcut odaklı bileşenin geometrik sınırlarının dışındaki (`rect.right` ötesindeki) odak hedeflerini arar. Çarpı düğmesi ise `Surface`'in geometrik sınırlarının **içinde** kaldığından, kullanıcı D-pad ile sağa bastığında Compose sağ tarafta odaklanacak bir hedef bulamaz ve odak satırdan çıkamaz.

### Kanıt (Düzeltme Öncesi Emülatör Deneyi)
1. **D-pad SAĞ (`KEYCODE_DPAD_RIGHT`):**
   - Satır odaktayken (`bounds: [50, 427, 1550, 580]`) `adb shell input keyevent KEYCODE_DPAD_RIGHT` gönderildi.
   - `uiautomator dump` ile kontrol edildiğinde odağın satırdan ayrılmadığı ve hâlâ `[50, 427, 1550, 580]` üzerinde kaldığı görüldü.
   - Odak çarpıya hiç geçmediği için çarpı üzerinde odak halkası oluşmadı.
2. **D-pad TAMAM (`KEYCODE_DPAD_CENTER`):**
   - Odak satırda kaldığı için `adb shell input keyevent KEYCODE_DPAD_CENTER` gönderildiğinde `Surface.onClick` tetiklendi ve `PlayerActivity` açılarak film oynatılmaya başlandı (`mFocusedApp=...PlayerActivity`).
   - `onRemove` geri çağrımı **asla çağrılmadı**, geçmiş kaydı silinemedi.

---

## 2. Düzeltme ve Mimari

Projenin `ui/tv/PlayFocus.kt` dosyasında tam da tıklanabilir container'lar içindeki denetimlere geçiş için hazırlanmış olan `RowControls` (`rememberRowControls`, `rowControlsHost`, `rowControl`) altyapısı kullanıldı:

1. **`HistoryRow` İçinde Satır ↔ Çarpı Odak Zinciri:**
   - `val rowControls = rememberRowControls(1)` tanımlandı.
   - Dıştaki `Surface`'e `.rowControlsHost(rowControls)` verilerek SAĞ yönün (`FocusProperties.right`) ilk denetim olan çarpıya gitmesi sağlandı.
   - Çarpı düğmesi olan `IconButton`'a `modifier = Modifier.rowControl(rowControls, 0)` bağlandı; böylece SOL yön (`FocusProperties.left`) doğrudan satırın kendisine döndürüldü.
2. **Odak Halkası ve Görsel Deneyim (`tvFocusableScaleGlow`):**
   - `Surface`'e `isRowFocused` durumuna göre `tvFocusableScaleGlow(shape = shape, isFocused = isRowFocused, zIndex = 5f)` ve `.onFocusChanged { isRowFocused = it.isFocused }` bağlandı.
   - `it.isFocused` kontrolü sayesinde satır doğrudan odaktayken turkuaz neon parlama çerçevesi aktiftir. Odak alt öğe olan çarpı düğmesine geçtiğinde `it.isFocused == false` olur ve satırın parlaması söner.
   - Çarpı düğmesi `com.kaan.play.ui.components.IconButton` (`PlayButtons.kt`) sarmalayıcısıdır ve varsayılan olarak `.focusRing(CircleShape, scale = 1.10f)` uygular. Çarpı odaklandığında etrafında belirgin turkuaz daire neon halka belirir ve öğe 1.10 kat büyür.
3. **D-pad Yolları:**
   - **Satır ↔ Çarpı:** Satır odaktayken SAĞ → Çarpı; Çarpı odaktayken SOL → Satır.
   - **Yukarı / Aşağı:** Satırdayken YUKARI/AŞAĞI satırlar arası geçiş yapar. Çarpıdayken YUKARI bir üst satıra (veya ilk satırdaysa dikey hizasındaki "Tümünü temizle" düğmesine), AŞAĞI bir alt satıra geçer.
   - **Geri Menüye:** En üst satırdayken YUKARI basıldığında üstteki yüzen menüye (`topBarFocusRequester`) sorunsuz geçilir.
   - **Tamam (DPAD_CENTER):** Satırdayken oynatır (`onClick`), Çarpıdayken siler (`onRemove`).
   - **Dokunmatik:** Satıra dokunulunca oynatır, çarpıya dokunulunca siler; dokunmatik davranışta hiçbir değişiklik olmamıştır.
   - **Erişilebilirlik:** `contentDescription = stringResource(R.string.library_remove_history)` aynen korunmuştur.

---

## 3. Favori ve İndirilenler Düğmelerinin İncelenmesi (Kod Değiştirilmedi)

Görev 3. maddesi gereğince Favori kartı ve İndirilenler satırındaki düğmeler incelenmiştir:
1. **Favori Kartı (`PosterCard`):**
   - `PosterCard`, dıştan `Column.clickable` ile tıklanabilir ve odaklanabilir bir bileşendir.
   - Üzerindeki kalp simgesi (`IconButton`), `PosterCard`'ın `overlay` parametresi içinde, afişin (`Box`) içine yerleştirilmektedir.
   - Tıpkı `HistoryRow`'un ilk halindeki gibi, kalp düğmesi dış tıklanabilir bileşenin geometrik sınırları içinde kaldığından Compose 2D uzamsal odak araması kalp düğmesini bulamaz. Kumandayla kalp düğmesine **ulaşılamaz ve odak görünmez**.
2. **İndirilenler Satırı (`DownloadRow`):**
   - `DownloadRow`, `HistoryRow` ile birebir aynı mimariye sahiptir: Dışta `Surface(onClick = onClick)` tüm satırı kaplar; içinde ise duraklat/sürdür `IconButton` ve iptal/sil `IconButton` yer alır.
   - Bu düğmeler de dış `Surface`'in sınırları içinde kaldığı için kumandayla bu butonlara **ulaşılamaz ve odak görünmez**.
3. **Karar:** Kullanıcı talimatına titizlikle uyularak bu iki bileşende **HİÇBİR KOD DEĞİŞİKLİĞİ YAPILMAMIŞTIR**.

---

## 4. Değişen Satırlar

SADECE `app/src/main/java/com/kaan/play/ui/library/LibraryScreen.kt` dosyasında `HistoryRow` ve gerekli TV odak helper importları değiştirilmiştir:

```diff
@@ -62,6 +62,11 @@ import androidx.compose.ui.focus.focusProperties
 import androidx.compose.ui.focus.focusRequester
 import com.kaan.play.ui.tv.focusMemory
 import com.kaan.play.ui.tv.rememberFocusMemory
+import com.kaan.play.ui.tv.rememberRowControls
+import com.kaan.play.ui.tv.rowControl
+import com.kaan.play.ui.tv.rowControlsHost
+import com.kaan.play.ui.tv.tvFocusableScaleGlow
+import androidx.compose.ui.focus.onFocusChanged
 
 private val CardMinWidth = 132.dp
 private val Gutter = 20.dp
@@ -295,10 +300,21 @@ private fun HistoryRow(entry: WatchHistoryEntity, onClick: () -> Unit, onRemove:
         else -> stringResource(R.string.continue_left_movie, minutesLeft)
     }
     val progress = (entry.positionMs.toFloat() / entry.durationMs.toFloat()).coerceIn(0f, 1f)
+    val rowControls = rememberRowControls(1)
+    val shape = androidx.compose.foundation.shape.RoundedCornerShape(14.dp)
+    var isRowFocused by remember { mutableStateOf(false) }
 
     androidx.compose.material3.Surface(
         onClick = onClick,
-        modifier = modifier.fillMaxWidth(),
+        modifier = modifier
+            .rowControlsHost(rowControls)
+            .tvFocusableScaleGlow(
+                shape = shape,
+                isFocused = isRowFocused,
+                zIndex = 5f,
+            )
+            .onFocusChanged { isRowFocused = it.isFocused }
+            .fillMaxWidth(),
         shape = androidx.compose.foundation.shape.RoundedCornerShape(14.dp),
         color = MaterialTheme.colorScheme.surfaceVariant,
         border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
@@ -334,7 +350,7 @@ private fun HistoryRow(entry: WatchHistoryEntity, onClick: () -> Unit, onRemove:
                     trackColor = MaterialTheme.colorScheme.outline,
                 )
             }
-            IconButton(onClick = onRemove) {
+            IconButton(onClick = onRemove, modifier = Modifier.rowControl(rowControls, 0)) {
                 Icon(
                     PlayIcons.Close,
                     contentDescription = stringResource(R.string.library_remove_history),
```

---

## 5. Denenen Tuşlar ve Doğrulama Akışı

Emülatörde (`emulator-5554`) gerçek veritabanı kayıtlarıyla aşağıdaki tuş dizileri sırayla icra edilmiştir:
1. `KEYCODE_DPAD_DOWN`: Menüden Kütüphane içerisindeki 1. geçmiş satırına inildi (`bounds: [5,422][1580,585]`, CivilWar). Satırda turkuaz neon odak halkası görüldü.
2. `KEYCODE_DPAD_RIGHT`: Odak satırdan doğrudan sağdaki çarpı (X) düğmesine geçti (`bounds: [1456,464][1536,544]`). Çarpı etrafında turkuaz neon daire belirdi, satırın halkası söndü.
3. `KEYCODE_DPAD_LEFT`: Odak çarpıdan tekrar solundaki satıra döndü (`bounds: [5,422][1580,585]`).
4. `KEYCODE_DPAD_RIGHT`: Tekrar çarpı düğmesine geçildi.
5. `KEYCODE_DPAD_CENTER`: Çarpı düğmesi üzerindeyken basıldı. `onRemove` başarıyla tetiklendi. CivilWar kaydı ekrandan kaldırıldı ve SQLite veritabanındaki `watch_history` tablosundan silindi. Liste başında sıradaki kayıt olan "Fall 2" yer aldı.
6. `KEYCODE_DPAD_UP`: Çarpı düğmesindeyken yukarı basıldı; dikey hizada yer alan "Tümünü temizle" düğmesine odaklandı (`bounds: [1332,333][1560,413]`).
7. `KEYCODE_DPAD_CENTER`: "Tümünü temizle" düğmesine basılarak "Geçmiş temizlensin mi?" onay penceresi açıldı. Odak "İptal" üzerindeydi.
8. `KEYCODE_DPAD_RIGHT`: Onay penceresinde "Temizle" düğmesine geçildi (`bounds: [930,490][1063,570]`).
9. `KEYCODE_BACK`: Onay penceresi kapatıldı ve Kütüphane ekranına dönüldü.

---

## 6. Kanıt Ekran Görüntüleri

Tüm ekran görüntüleri `docs/dogrulama-tv-gecmis-carpi/` klasöründe yer almaktadır:
- `01-odak-satirda.png`: D-pad ile odak geçmiş satırındayken (CivilWar satırı etrafında turkuaz neon odak halkası).
- `02-odak-carpida.png`: D-pad SAĞ sonrası odak satırın sağındaki çarpı (X) düğmesindeyken (çarpı etrafında turkuaz neon daire, satırın halkası sönmüş).
- `03-silme-sonrasi.png`: D-pad TAMAM (DPAD_CENTER) ile silme sonrası (CivilWar silindi, ilk sıraya Fall 2 geldi).
- `04-tumunu-temizle-dialog.png`: Çarpıdan YUKARI basılarak ulaşılan "Tümünü temizle" butonundan açılan kumanda uyumlu onay penceresi.

---

## 7. Derleme ve Test Sayıları

- **Birim Testleri (`.\gradlew testDebugUnitTest`):**
  - Toplam Test: **1116**
  - Başarılı: **1116**
  - Başarısız: **0**
  - Atlanan / Ignored: **2**
  - Başarı Oranı: **%100**
- **Derleme (`.\gradlew assembleDebug`):**
  - Durum: **BUILD SUCCESSFUL** (Sorunsuz derlendi)

---

## 8. Denenemeyenler

- **Yoktur.** İlgili tüm davranışlar `emulator-5554` üzerinde D-pad tuş olayları, ekran görüntüleri ve SQLite veri denetimleri ile uçtan uca doğrulanmıştır.
