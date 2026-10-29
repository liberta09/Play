package com.kaan.play.ui.nav

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp

/** Alt menü için çizgi stilinde, kendi çizdiğimiz sade ikonlar (24dp tuval, 1.8 kalınlık). */
object PlayIcons {

    private fun icon(name: String, block: androidx.compose.ui.graphics.vector.ImageVector.Builder.() -> Unit) =
        ImageVector.Builder(name, 24.dp, 24.dp, 24f, 24f).apply(block).build()

    private fun ImageVector.Builder.line(d: androidx.compose.ui.graphics.vector.PathBuilder.() -> Unit) =
        path(
            fill = null,
            stroke = SolidColor(Color.Black),
            strokeLineWidth = 1.8f,
            strokeLineCap = StrokeCap.Round,
            strokeLineJoin = StrokeJoin.Round,
            pathBuilder = d,
        )

    val Home: ImageVector by lazy {
        icon("Home") {
            line {
                moveTo(4f, 11f); lineTo(12f, 4.5f); lineTo(20f, 11f)
                moveTo(6f, 9.5f); verticalLineTo(19f); horizontalLineTo(18f); verticalLineTo(9.5f)
                moveTo(10f, 19f); verticalLineTo(14f); horizontalLineTo(14f); verticalLineTo(19f)
            }
        }
    }

    val Tv: ImageVector by lazy {
        icon("Tv") {
            line {
                moveTo(4f, 6f); horizontalLineTo(20f)
                arcToRelative(1.5f, 1.5f, 0f, false, true, 1.5f, 1.5f)
                verticalLineTo(17f)
                arcToRelative(1.5f, 1.5f, 0f, false, true, -1.5f, 1.5f)
                horizontalLineTo(4f)
                arcToRelative(1.5f, 1.5f, 0f, false, true, -1.5f, -1.5f)
                verticalLineTo(7.5f)
                arcToRelative(1.5f, 1.5f, 0f, false, true, 1.5f, -1.5f)
                moveTo(8f, 3.5f); lineTo(12f, 6f); lineTo(16f, 3.5f)
            }
        }
    }

    val Search: ImageVector by lazy {
        icon("Search") {
            line {
                moveTo(10.5f, 4.5f)
                arcToRelative(6f, 6f, 0f, true, true, 0f, 12f)
                arcToRelative(6f, 6f, 0f, true, true, 0f, -12f)
                moveTo(15f, 15f); lineTo(19.5f, 19.5f)
            }
        }
    }

    /** İstif halinde iki kart: kütüphane. */
    val Library: ImageVector by lazy {
        icon("Library") {
            line {
                moveTo(7f, 6f); horizontalLineTo(17f)
                moveTo(5f, 9.5f); horizontalLineTo(19f)
                moveTo(5f, 12f)
                arcToRelative(1.5f, 1.5f, 0f, false, true, 1.5f, -1.5f)
                horizontalLineTo(17.5f)
                arcToRelative(1.5f, 1.5f, 0f, false, true, 1.5f, 1.5f)
                verticalLineTo(18f)
                arcToRelative(1.5f, 1.5f, 0f, false, true, -1.5f, 1.5f)
                horizontalLineTo(6.5f)
                arcToRelative(1.5f, 1.5f, 0f, false, true, -1.5f, -1.5f)
                close()
            }
        }
    }

    val History: ImageVector by lazy {
        icon("History") {
            line {
                moveTo(3f, 12f)
                arcToRelative(9f, 9f, 0f, true, true, 2.64f, 6.36f)
                moveTo(3f, 5f); verticalLineTo(12f); horizontalLineTo(10f)
                moveTo(12f, 7f); verticalLineTo(12f); lineTo(16f, 14f)
            }
        }
    }

    private fun ImageVector.Builder.solid(d: androidx.compose.ui.graphics.vector.PathBuilder.() -> Unit) =
        path(fill = SolidColor(Color.Black), pathBuilder = d)

    private fun androidx.compose.ui.graphics.vector.PathBuilder.heart() {
        moveTo(12f, 20f)
        curveTo(5.5f, 15f, 3f, 11.5f, 3f, 8.5f)
        curveTo(3f, 5.5f, 5.2f, 4f, 7.3f, 4f)
        curveTo(9.2f, 4f, 11f, 5.2f, 12f, 6.8f)
        curveTo(13f, 5.2f, 14.8f, 4f, 16.7f, 4f)
        curveTo(18.8f, 4f, 21f, 5.5f, 21f, 8.5f)
        curveTo(21f, 11.5f, 18.5f, 15f, 12f, 20f)
        close()
    }

    val Heart: ImageVector by lazy { icon("Heart") { line { heart() } } }

    val HeartFilled: ImageVector by lazy { icon("HeartFilled") { solid { heart() } } }

    val ChevronDown: ImageVector by lazy {
        icon("ChevronDown") { line { moveTo(6f, 9f); lineTo(12f, 15f); lineTo(18f, 9f) } }
    }

    val Close: ImageVector by lazy {
        icon("Close") {
            line {
                moveTo(6f, 6f); lineTo(18f, 18f)
                moveTo(18f, 6f); lineTo(6f, 18f)
            }
        }
    }

    /** Yuvarlatılmış oynat üçgeni (launcher işaretiyle aynı biçim). */
    val PlayTriangle: ImageVector by lazy {
        icon("PlayTriangle") {
            solid {
                moveTo(8f, 5.5f)
                curveTo(8f, 4.4f, 8.9f, 3.9f, 9.8f, 4.5f)
                lineTo(19f, 10.6f)
                curveTo(19.9f, 11.2f, 19.9f, 12.8f, 19f, 13.4f)
                lineTo(9.8f, 19.5f)
                curveTo(8.9f, 20.1f, 8f, 19.6f, 8f, 18.5f)
                close()
            }
        }
    }

    val Pause: ImageVector by lazy {
        icon("Pause") {
            solid {
                moveTo(7f, 5.5f); horizontalLineTo(10.5f); verticalLineTo(18.5f); horizontalLineTo(7f); close()
                moveTo(13.5f, 5.5f); horizontalLineTo(17f); verticalLineTo(18.5f); horizontalLineTo(13.5f); close()
            }
        }
    }

    /** Çift ok sola / sağa: 10 sn geri / ileri (yanına "10" yazılır). */
    val SeekBack: ImageVector by lazy {
        icon("SeekBack") {
            line {
                moveTo(12f, 6f); lineTo(6f, 12f); lineTo(12f, 18f)
                moveTo(18f, 6f); lineTo(12f, 12f); lineTo(18f, 18f)
            }
        }
    }

    val SeekForward: ImageVector by lazy {
        icon("SeekForward") {
            line {
                moveTo(6f, 6f); lineTo(12f, 12f); lineTo(6f, 18f)
                moveTo(12f, 6f); lineTo(18f, 12f); lineTo(12f, 18f)
            }
        }
    }

    /** Altyazı: konuşma kutusu içinde iki satır. */
    val Subtitles: ImageVector by lazy {
        icon("Subtitles") {
            line {
                moveTo(5.5f, 5.5f); horizontalLineTo(18.5f)
                arcToRelative(1.5f, 1.5f, 0f, false, true, 1.5f, 1.5f)
                verticalLineTo(17f)
                arcToRelative(1.5f, 1.5f, 0f, false, true, -1.5f, 1.5f)
                horizontalLineTo(5.5f)
                arcToRelative(1.5f, 1.5f, 0f, false, true, -1.5f, -1.5f)
                verticalLineTo(7f)
                arcToRelative(1.5f, 1.5f, 0f, false, true, 1.5f, -1.5f)
                close()
                moveTo(7.5f, 11f); horizontalLineTo(10.5f)
                moveTo(13f, 11f); horizontalLineTo(16.5f)
                moveTo(7.5f, 14.5f); horizontalLineTo(11f)
                moveTo(13.5f, 14.5f); horizontalLineTo(16.5f)
            }
        }
    }

    /** Hoparlör ve iki ses dalgası: ses düzeyi göstergesi. */
    val Volume: ImageVector by lazy {
        icon("Volume") {
            line {
                moveTo(4f, 9.5f); horizontalLineTo(7f); lineTo(11f, 5.5f); verticalLineTo(18.5f); lineTo(7f, 14.5f); horizontalLineTo(4f); close()
                moveTo(14.5f, 9f); arcToRelative(4f, 4f, 0f, false, true, 0f, 6f)
                moveTo(17.5f, 6.5f); arcToRelative(7.5f, 7.5f, 0f, false, true, 0f, 11f)
            }
        }
    }

    /** Daire ve sekiz ışın: parlaklık göstergesi. */
    val Brightness: ImageVector by lazy {
        icon("Brightness") {
            line {
                moveTo(8.2f, 12f); arcToRelative(3.8f, 3.8f, 0f, true, true, 7.6f, 0f); arcToRelative(3.8f, 3.8f, 0f, true, true, -7.6f, 0f)
                moveTo(18.5f, 12f); lineTo(20.5f, 12f)
                moveTo(16.6f, 16.6f); lineTo(18f, 18f)
                moveTo(12f, 18.5f); lineTo(12f, 20.5f)
                moveTo(7.4f, 16.6f); lineTo(6f, 18f)
                moveTo(5.5f, 12f); lineTo(3.5f, 12f)
                moveTo(7.4f, 7.4f); lineTo(6f, 6f)
                moveTo(12f, 5.5f); lineTo(12f, 3.5f)
                moveTo(16.6f, 7.4f); lineTo(18f, 6f)
            }
        }
    }

    /** Karşılıklı iki ok: kaynağı değiştir. */
    val SwitchSource: ImageVector by lazy {
        icon("SwitchSource") {
            line {
                moveTo(5f, 8f); horizontalLineTo(19f)
                moveTo(15f, 4.5f); lineTo(19f, 8f); lineTo(15f, 11.5f)
                moveTo(19f, 16f); horizontalLineTo(5f)
                moveTo(9f, 12.5f); lineTo(5f, 16f); lineTo(9f, 19.5f)
            }
        }
    }

    val Check: ImageVector by lazy {
        icon("Check") { line { moveTo(5f, 12.5f); lineTo(10f, 17.5f); lineTo(19f, 7f) } }
    }

    /** Aşağı ok + tepsi çizgisi: indir. */
    val Download: ImageVector by lazy {
        icon("Download") {
            line {
                moveTo(12f, 4.5f); verticalLineTo(14.5f)
                moveTo(7.5f, 10.5f); lineTo(12f, 15f); lineTo(16.5f, 10.5f)
                moveTo(5f, 19.5f); horizontalLineTo(19f)
            }
        }
    }

    /** Tepsi çizgisinin üstünde onay işareti: indirildi. */
    val DownloadDone: ImageVector by lazy {
        icon("DownloadDone") {
            line {
                moveTo(7f, 10.5f); lineTo(11f, 14.5f); lineTo(17.5f, 6.5f)
                moveTo(5f, 19.5f); horizontalLineTo(19f)
            }
        }
    }

    val Back: ImageVector by lazy {
        icon("Back") {
            line {
                moveTo(15f, 5f); lineTo(8f, 12f); lineTo(15f, 19f)
            }
        }
    }

    val Forward: ImageVector by lazy {
        icon("Forward") {
            line {
                moveTo(9f, 5f); lineTo(16f, 12f); lineTo(9f, 19f)
            }
        }
    }

    /** Neredeyse tam daire biçiminde ok: sayfayı yenile. */
    val Refresh: ImageVector by lazy {
        icon("Refresh") {
            line {
                moveTo(17.4f, 7.5f)
                arcTo(7f, 7f, 0f, true, true, 13.8f, 5.2f)
                moveTo(12.05f, 2.15f); lineTo(13.8f, 5.2f); lineTo(10.75f, 6.95f)
            }
        }
    }

    val Plus: ImageVector by lazy {
        icon("Plus") {
            line {
                moveTo(12f, 5f); verticalLineTo(19f)
                moveTo(5f, 12f); horizontalLineTo(19f)
            }
        }
    }

    val Trash: ImageVector by lazy {
        icon("Trash") {
            line {
                moveTo(4.5f, 7f); horizontalLineTo(19.5f)
                moveTo(9.5f, 7f); verticalLineTo(4.5f); horizontalLineTo(14.5f); verticalLineTo(7f)
                moveTo(6.5f, 7f); lineTo(7.5f, 19.5f); horizontalLineTo(16.5f); lineTo(17.5f, 7f)
                moveTo(10f, 11f); verticalLineTo(16f)
                moveTo(14f, 11f); verticalLineTo(16f)
            }
        }
    }

    /** Üç nokta: "daha fazla". */
    val More: ImageVector by lazy {
        icon("More") {
            solid {
                for (cx in floatArrayOf(5.5f, 12f, 18.5f)) {
                    moveTo(cx - 1.7f, 12f)
                    arcToRelative(1.7f, 1.7f, 0f, true, true, 3.4f, 0f)
                    arcToRelative(1.7f, 1.7f, 0f, true, true, -3.4f, 0f)
                    close()
                }
            }
        }
    }

    val Settings: ImageVector by lazy {
        icon("Settings") {
            line {
                moveTo(5f, 7f); horizontalLineTo(19f)
                moveTo(5f, 12f); horizontalLineTo(19f)
                moveTo(5f, 17f); horizontalLineTo(19f)
                moveTo(9f, 5f); verticalLineTo(9f)
                moveTo(15f, 10f); verticalLineTo(14f)
                moveTo(8f, 15f); verticalLineTo(19f)
            }
        }
    }
    /** Oynatıcı: ekranı kilitle / kilidi aç. */
    val Lock: ImageVector by lazy {
        icon("Lock") {
            line {
                moveTo(6.5f, 11f); horizontalLineTo(17.5f); verticalLineTo(19.5f); horizontalLineTo(6.5f); close()
                moveTo(8.5f, 11f); verticalLineTo(8f)
                arcTo(3.5f, 3.5f, 0f, false, true, 15.5f, 8f)
                verticalLineTo(11f)
                moveTo(12f, 14.5f); verticalLineTo(16.5f)
            }
        }
    }

    val Unlock: ImageVector by lazy {
        icon("Unlock") {
            line {
                moveTo(6.5f, 11f); horizontalLineTo(17.5f); verticalLineTo(19.5f); horizontalLineTo(6.5f); close()
                moveTo(8.5f, 11f); verticalLineTo(7.5f)
                arcTo(3.5f, 3.5f, 0f, false, true, 15.5f, 7.5f)
                moveTo(12f, 14.5f); verticalLineTo(16.5f)
            }
        }
    }

    /** Oynatıcı: yeniden boyutlandır (köşe işaretleri + iç dikdörtgen). */
    val Resize: ImageVector by lazy {
        icon("Resize") {
            line {
                moveTo(4f, 9f); verticalLineTo(4f); horizontalLineTo(9f)
                moveTo(15f, 4f); horizontalLineTo(20f); verticalLineTo(9f)
                moveTo(20f, 15f); verticalLineTo(20f); horizontalLineTo(15f)
                moveTo(9f, 20f); horizontalLineTo(4f); verticalLineTo(15f)
                moveTo(8.5f, 9f); horizontalLineTo(15.5f); verticalLineTo(15f); horizontalLineTo(8.5f); close()
            }
        }
    }

    /** Oynatıcı: altyazı senkronu (saat). */
    val SubtitleSync: ImageVector by lazy {
        icon("SubtitleSync") {
            line {
                moveTo(4f, 12f)
                arcToRelative(8f, 8f, 0f, true, true, 16f, 0f)
                arcToRelative(8f, 8f, 0f, true, true, -16f, 0f)
                moveTo(12f, 7.5f); verticalLineTo(12f); lineTo(15f, 14f)
            }
        }
    }

    /** Oynatıcı: parçalar (video/ses seçenekleri; düzey çubukları). */
    val Tracks: ImageVector by lazy {
        icon("Tracks") {
            line {
                moveTo(6f, 11f); verticalLineTo(19f)
                moveTo(12f, 5f); verticalLineTo(19f)
                moveTo(18f, 8f); verticalLineTo(19f)
            }
        }
    }

    /** Oynatıcı: sonraki bölüm (sağa ok). */
    val NextEpisode: ImageVector by lazy {
        icon("NextEpisode") {
            line {
                moveTo(5f, 12f); horizontalLineTo(18.5f)
                moveTo(13.5f, 7f); lineTo(18.5f, 12f); lineTo(13.5f, 17f)
            }
        }
    }

    /** Sağlayıcı seçici: iğneleme / sabitleme ikonu (çizgi stilinde). */
    val Pin: ImageVector by lazy {
        icon("Pin") {
            line {
                moveTo(9f, 4f); horizontalLineTo(15f)
                moveTo(10f, 4f); verticalLineTo(7f)
                moveTo(14f, 4f); verticalLineTo(7f)
                moveTo(10f, 7f); lineTo(7f, 13f); horizontalLineTo(17f); lineTo(14f, 7f)
                moveTo(12f, 13f); verticalLineTo(20f)
            }
        }
    }

    /** Sağlayıcı seçici: sabitlenmiş (dolu gövdeli) iğne ikonu. */
    val PinFilled: ImageVector by lazy {
        icon("PinFilled") {
            line {
                moveTo(9f, 4f); horizontalLineTo(15f)
                moveTo(12f, 13f); verticalLineTo(20f)
            }
            solid {
                moveTo(10f, 4f); verticalLineTo(7f); lineTo(7f, 13f); horizontalLineTo(17f); lineTo(14f, 7f); verticalLineTo(4f); close()
            }
        }
    }

    val Cloud: ImageVector by lazy {
        icon("Cloud") {
            line {
                moveTo(6.5f, 18f); horizontalLineTo(17.5f)
                arcTo(3.5f, 3.5f, 0f, false, false, 17.5f, 11f)
                arcTo(0.5f, 0.5f, 0f, false, false, 17f, 10.7f)
                arcTo(5f, 5f, 0f, false, false, 7.3f, 12.3f)
                arcTo(3.5f, 3.5f, 0f, false, false, 6.5f, 18f)
                close()
            }
        }
    }

    val Extension: ImageVector by lazy {
        icon("Extension") {
            line {
                moveTo(10f, 4f); horizontalLineTo(6f)
                arcToRelative(2f, 2f, 0f, false, false, -2f, 2f)
                verticalLineToRelative(4f)
                arcToRelative(2f, 2f, 0f, false, true, 2f, 2f)
                arcToRelative(2f, 2f, 0f, false, true, -2f, 2f)
                verticalLineToRelative(4f)
                arcToRelative(2f, 2f, 0f, false, false, 2f, 2f)
                horizontalLineToRelative(4f)
                arcToRelative(2f, 2f, 0f, false, true, 2f, -2f)
                arcToRelative(2f, 2f, 0f, false, true, 2f, 2f)
                horizontalLineToRelative(4f)
                arcToRelative(2f, 2f, 0f, false, false, 2f, -2f)
                verticalLineToRelative(-4f)
                arcToRelative(2f, 2f, 0f, false, true, -2f, -2f)
                arcToRelative(2f, 2f, 0f, false, true, 2f, -2f)
                verticalLineToRelative(-4f)
                arcToRelative(2f, 2f, 0f, false, false, -2f, -2f)
                horizontalLineToRelative(-4f)
                arcToRelative(2f, 2f, 0f, false, true, -2f, 2f)
                arcToRelative(2f, 2f, 0f, false, true, -2f, -2f)
                close()
            }
        }
    }

    val ContentPaste: ImageVector by lazy {
        icon("ContentPaste") {
            line {
                moveTo(19f, 5f); horizontalLineTo(15f)
                moveTo(9f, 5f); horizontalLineTo(5f)
                arcToRelative(2f, 2f, 0f, false, false, -2f, 2f)
                verticalLineToRelative(12f)
                arcToRelative(2f, 2f, 0f, false, false, 2f, 2f)
                horizontalLineToRelative(14f)
                arcToRelative(2f, 2f, 0f, false, false, 2f, -2f)
                verticalLineTo(7f)
                arcToRelative(2f, 2f, 0f, false, false, -2f, -2f)
                moveTo(9f, 4f)
                arcToRelative(1f, 1f, 0f, false, true, 1f, -1f)
                horizontalLineToRelative(4f)
                arcToRelative(1f, 1f, 0f, false, true, 1f, 1f)
                verticalLineToRelative(2f)
                arcToRelative(1f, 1f, 0f, false, true, -1f, 1f)
                horizontalLineTo(10f)
                arcToRelative(1f, 1f, 0f, false, true, -1f, -1f)
                close()
            }
        }
    }

    val ChevronRight: ImageVector by lazy {
        icon("ChevronRight") {
            line {
                moveTo(9f, 6f); lineTo(15f, 12f); lineTo(9f, 18f)
            }
        }
    }

    val Nuvio: ImageVector by lazy {
        icon("Nuvio") {
            line {
                moveTo(6f, 4f); horizontalLineTo(18f)
                arcToRelative(2f, 2f, 0f, false, true, 2f, 2f)
                verticalLineTo(18f)
                arcToRelative(2f, 2f, 0f, false, true, -2f, 2f)
                horizontalLineTo(6f)
                arcToRelative(2f, 2f, 0f, false, true, -2f, -2f)
                verticalLineTo(6f)
                arcToRelative(2f, 2f, 0f, false, true, 2f, -2f)
                close()
                moveTo(8.5f, 16.5f); verticalLineTo(7.5f); lineTo(15.5f, 16.5f); verticalLineTo(7.5f)
            }
        }
    }

    val AddCircle: ImageVector by lazy {
        icon("AddCircle") {
            line {
                moveTo(12f, 3f)
                arcToRelative(9f, 9f, 0f, true, true, 0f, 18f)
                arcToRelative(9f, 9f, 0f, true, true, 0f, -18f)
                moveTo(12f, 7.5f); verticalLineTo(16.5f)
                moveTo(7.5f, 12f); horizontalLineTo(16.5f)
            }
        }
    }

    val Database: ImageVector by lazy {
        icon("Database") {
            line {
                moveTo(5f, 6.5f)
                arcToRelative(7f, 2.5f, 0f, true, false, 14f, 0f)
                arcToRelative(7f, 2.5f, 0f, true, false, -14f, 0f)
                moveTo(5f, 6.5f); verticalLineTo(11.5f)
                arcToRelative(7f, 2.5f, 0f, false, false, 14f, 0f)
                verticalLineTo(6.5f)
                moveTo(5f, 11.5f); verticalLineTo(16.5f)
                arcToRelative(7f, 2.5f, 0f, false, false, 14f, 0f)
                verticalLineTo(11.5f)
            }
        }
    }

    val NuvioLogo: ImageVector by lazy {
        icon("NuvioLogo") {
            solid {
                moveTo(6.5f, 5.5f)
                curveTo(5.7f, 5.5f, 5f, 6.2f, 5f, 7f)
                verticalLineTo(17f)
                curveTo(5f, 17.8f, 5.7f, 18.5f, 6.5f, 18.5f)
                curveTo(7.3f, 18.5f, 8f, 17.8f, 8f, 17f)
                verticalLineTo(10.5f)
                lineTo(15.8f, 18.2f)
                curveTo(16.3f, 18.7f, 17.1f, 18.7f, 17.6f, 18.3f)
                curveTo(18.5f, 17.6f, 19f, 16.8f, 19f, 15.5f)
                verticalLineTo(7f)
                curveTo(19f, 6.2f, 18.3f, 5.5f, 17.5f, 5.5f)
                curveTo(16.7f, 5.5f, 16f, 6.2f, 16f, 7f)
                verticalLineTo(13.5f)
                lineTo(8.2f, 5.8f)
                curveTo(7.8f, 5.4f, 7.1f, 5.3f, 6.5f, 5.5f)
                close()
            }
        }
    }

    val Code: ImageVector by lazy {
        icon("Code") {
            line {
                moveTo(8.5f, 7.5f); lineTo(4f, 12f); lineTo(8.5f, 16.5f)
                moveTo(15.5f, 7.5f); lineTo(20f, 12f); lineTo(15.5f, 16.5f)
                moveTo(13.5f, 5.5f); lineTo(10.5f, 18.5f)
            }
        }
    }

    val Globe: ImageVector by lazy {
        icon("Globe") {
            line {
                moveTo(12f, 3.5f)
                arcToRelative(8.5f, 8.5f, 0f, true, true, 0f, 17f)
                arcToRelative(8.5f, 8.5f, 0f, true, true, 0f, -17f)
                moveTo(3.5f, 12f); horizontalLineTo(20.5f)
                moveTo(12f, 3.5f)
                arcToRelative(4.5f, 8.5f, 0f, false, false, 0f, 17f)
                arcToRelative(4.5f, 8.5f, 0f, false, false, 0f, -17f)
            }
        }
    }

    val Clock: ImageVector by lazy {
        icon("Clock") {
            line {
                moveTo(12f, 3.5f)
                arcToRelative(8.5f, 8.5f, 0f, true, true, 0f, 17f)
                arcToRelative(8.5f, 8.5f, 0f, true, true, 0f, -17f)
                moveTo(12f, 7.5f); verticalLineTo(12f); lineTo(15.5f, 14f)
            }
        }
    }

    val Server: ImageVector by lazy {
        icon("Server") {
            line {
                moveTo(4.5f, 5f); horizontalLineTo(19.5f)
                arcToRelative(1.5f, 1.5f, 0f, false, true, 1.5f, 1.5f)
                verticalLineTo(8.5f)
                arcToRelative(1.5f, 1.5f, 0f, false, true, -1.5f, 1.5f)
                horizontalLineTo(4.5f)
                arcToRelative(1.5f, 1.5f, 0f, false, true, -1.5f, -1.5f)
                verticalLineTo(6.5f)
                arcToRelative(1.5f, 1.5f, 0f, false, true, 1.5f, -1.5f)
                close()
                moveTo(7f, 7.5f); horizontalLineTo(9f)

                moveTo(4.5f, 10.5f); horizontalLineTo(19.5f)
                arcToRelative(1.5f, 1.5f, 0f, false, true, 1.5f, 1.5f)
                verticalLineTo(14f)
                arcToRelative(1.5f, 1.5f, 0f, false, true, -1.5f, 1.5f)
                horizontalLineTo(4.5f)
                arcToRelative(1.5f, 1.5f, 0f, false, true, -1.5f, -1.5f)
                verticalLineTo(12f)
                arcToRelative(1.5f, 1.5f, 0f, false, true, 1.5f, -1.5f)
                close()
                moveTo(7f, 13f); horizontalLineTo(9f)

                moveTo(4.5f, 16f); horizontalLineTo(19.5f)
                arcToRelative(1.5f, 1.5f, 0f, false, true, 1.5f, 1.5f)
                verticalLineTo(19.5f)
                arcToRelative(1.5f, 1.5f, 0f, false, true, -1.5f, 1.5f)
                horizontalLineTo(4.5f)
                arcToRelative(1.5f, 1.5f, 0f, false, true, -1.5f, -1.5f)
                verticalLineTo(17.5f)
                arcToRelative(1.5f, 1.5f, 0f, false, true, 1.5f, -1.5f)
                close()
                moveTo(7f, 18.5f); horizontalLineTo(9f)
            }
        }
    }

    val PlayCircle: ImageVector by lazy {
        icon("PlayCircle") {
            line {
                moveTo(12f, 3.5f)
                arcToRelative(8.5f, 8.5f, 0f, true, true, 0f, 17f)
                arcToRelative(8.5f, 8.5f, 0f, true, true, 0f, -17f)
            }
            solid {
                moveTo(10f, 8.5f)
                curveTo(9.5f, 8.2f, 9f, 8.5f, 9f, 9.1f)
                verticalLineTo(14.9f)
                curveTo(9f, 15.5f, 9.5f, 15.8f, 10f, 15.5f)
                lineTo(15.2f, 12.6f)
                curveTo(15.7f, 12.3f, 15.7f, 11.7f, 15.2f, 11.4f)
                close()
            }
        }
    }

    val Palette: ImageVector by lazy {
        icon("Palette") {
            line {
                moveTo(12f, 3f)
                curveTo(7f, 3f, 3f, 7f, 3f, 12f)
                curveTo(3f, 14.5f, 4f, 16.5f, 6f, 17.5f)
                curveTo(6.5f, 17.8f, 7f, 17.4f, 7f, 16.8f)
                curveTo(7f, 16.3f, 6.7f, 15.8f, 6.7f, 15.3f)
                curveTo(6.7f, 14.2f, 7.6f, 13.3f, 8.7f, 13.3f)
                horizontalLineTo(10f)
                curveTo(12.2f, 13.3f, 14f, 15.1f, 14f, 17.3f)
                verticalLineTo(18.5f)
                curveTo(14f, 19.9f, 15.1f, 21f, 16.5f, 21f)
                curveTo(19f, 21f, 21f, 17f, 21f, 12f)
                curveTo(21f, 7f, 17f, 3f, 12f, 3f)
                close()
            }
            solid {
                moveTo(6.5f, 10f)
                arcToRelative(1.5f, 1.5f, 0f, true, true, 3f, 0f)
                arcToRelative(1.5f, 1.5f, 0f, true, true, -3f, 0f)
                moveTo(10.5f, 6.5f)
                arcToRelative(1.5f, 1.5f, 0f, true, true, 3f, 0f)
                arcToRelative(1.5f, 1.5f, 0f, true, true, -3f, 0f)
                moveTo(14.5f, 9.5f)
                arcToRelative(1.5f, 1.5f, 0f, true, true, 3f, 0f)
                arcToRelative(1.5f, 1.5f, 0f, true, true, -3f, 0f)
            }
        }
    }

    val InfoCircle: ImageVector by lazy {
        icon("InfoCircle") {
            line {
                moveTo(12f, 3f)
                arcToRelative(9f, 9f, 0f, true, true, 0f, 18f)
                arcToRelative(9f, 9f, 0f, true, true, 0f, -18f)
                moveTo(12f, 10.5f); verticalLineTo(16.5f)
            }
            solid {
                moveTo(11f, 7.5f)
                arcToRelative(1f, 1f, 0f, true, true, 2f, 0f)
                arcToRelative(1f, 1f, 0f, true, true, -2f, 0f)
            }
        }
    }

    val Folder: ImageVector by lazy {
        icon("Folder") {
            line {
                moveTo(3.5f, 6.5f)
                arcToRelative(2f, 2f, 0f, false, true, 2f, -2f)
                horizontalLineTo(9f)
                lineTo(11f, 6.5f)
                horizontalLineTo(18.5f)
                arcToRelative(2f, 2f, 0f, false, true, 2f, 2f)
                verticalLineTo(17.5f)
                arcToRelative(2f, 2f, 0f, false, true, -2f, 2f)
                horizontalLineTo(5.5f)
                arcToRelative(2f, 2f, 0f, false, true, -2f, -2f)
                close()
            }
        }
    }

    val Gear: ImageVector by lazy {
        icon("Gear") {
            solid {
                moveTo(19.14f, 12.94f)
                curveTo(19.18f, 12.63f, 19.2f, 12.32f, 19.2f, 12f)
                curveTo(19.2f, 11.68f, 19.18f, 11.37f, 19.14f, 11.06f)
                lineTo(21.41f, 9.29f)
                curveTo(21.62f, 9.13f, 21.67f, 8.84f, 21.54f, 8.6f)
                lineTo(19.4f, 4.89f)
                curveTo(19.27f, 4.66f, 18.99f, 4.57f, 18.74f, 4.67f)
                lineTo(16.07f, 5.75f)
                curveTo(15.52f, 5.32f, 14.92f, 4.98f, 14.26f, 4.73f)
                lineTo(13.86f, 1.88f)
                curveTo(13.82f, 1.62f, 13.6f, 1.43f, 13.34f, 1.43f)
                horizontalLineTo(10.66f)
                curveTo(10.4f, 1.43f, 10.18f, 1.62f, 10.14f, 1.88f)
                lineTo(9.74f, 4.73f)
                curveTo(9.08f, 4.98f, 8.48f, 5.32f, 7.93f, 5.75f)
                lineTo(5.26f, 4.67f)
                curveTo(5.01f, 4.57f, 4.73f, 4.66f, 4.6f, 4.89f)
                lineTo(2.46f, 8.6f)
                curveTo(2.33f, 8.84f, 2.38f, 9.13f, 2.59f, 9.29f)
                lineTo(4.86f, 11.06f)
                curveTo(4.82f, 11.37f, 4.8f, 11.69f, 4.8f, 12f)
                curveTo(4.8f, 12.31f, 4.82f, 12.63f, 4.86f, 12.94f)
                lineTo(2.59f, 14.71f)
                curveTo(2.38f, 14.87f, 2.33f, 15.16f, 2.46f, 15.4f)
                lineTo(4.6f, 19.11f)
                curveTo(4.73f, 19.34f, 5.01f, 19.43f, 5.26f, 19.33f)
                lineTo(7.93f, 18.25f)
                curveTo(8.48f, 18.68f, 9.08f, 19.02f, 9.74f, 19.27f)
                lineTo(10.14f, 22.12f)
                curveTo(10.18f, 22.38f, 10.4f, 22.57f, 10.66f, 22.57f)
                horizontalLineTo(13.34f)
                curveTo(13.6f, 22.57f, 13.82f, 22.38f, 13.86f, 22.12f)
                lineTo(14.26f, 19.27f)
                curveTo(14.92f, 19.02f, 15.52f, 18.68f, 16.07f, 18.25f)
                lineTo(18.74f, 19.33f)
                curveTo(18.99f, 19.43f, 19.27f, 19.34f, 19.4f, 19.11f)
                lineTo(21.54f, 15.4f)
                curveTo(21.67f, 15.16f, 21.62f, 14.87f, 21.41f, 14.71f)
                lineTo(19.14f, 12.94f)
                close()
                moveTo(12f, 15.5f)
                curveTo(10.07f, 15.5f, 8.5f, 13.93f, 8.5f, 12f)
                curveTo(8.5f, 10.07f, 10.07f, 8.5f, 12f, 8.5f)
                curveTo(13.93f, 8.5f, 15.5f, 10.07f, 15.5f, 12f)
                curveTo(15.5f, 13.93f, 13.93f, 15.5f, 12f, 15.5f)
                close()
            }
        }
    }

    val TelegramPlane: ImageVector by lazy {
        ImageVector.Builder("TelegramPlane", 48.dp, 48.dp, 48f, 48f).apply {
            solid {
                moveTo(33.95f, 15f)
                lineTo(30.21f, 32.65f)
                curveTo(29.93f, 33.9f, 29.19f, 34.21f, 28.15f, 33.62f)
                lineTo(22.44f, 29.41f)
                lineTo(19.69f, 32.06f)
                curveTo(19.39f, 32.36f, 19.13f, 32.62f, 18.55f, 32.62f)
                lineTo(18.96f, 26.81f)
                lineTo(29.54f, 17.25f)
                curveTo(30.0f, 16.84f, 29.44f, 16.61f, 28.83f, 17.02f)
                lineTo(15.75f, 25.25f)
                lineTo(10.12f, 23.49f)
                curveTo(8.9f, 23.11f, 8.87f, 22.27f, 10.38f, 21.68f)
                lineTo(32.4f, 13.2f)
                curveTo(33.42f, 12.82f, 34.31f, 13.43f, 33.95f, 15f)
                close()
            }
        }.build()
    }
}
