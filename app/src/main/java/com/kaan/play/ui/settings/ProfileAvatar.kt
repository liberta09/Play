package com.kaan.play.ui.settings

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp

/** Özgün çizim avatar kataloğu. */
val profileAvatarOptions: List<String> = listOf(
    "space_astro",
    "scifi_cyber",
    "hero_crimson",
    "ninja_shadow",
    "retro_gamer",
    "magic_wizard",
    "cute_bot",
    "pirate_gold",
    "viking_warrior",
    "nature_spirit",
    "detective_noir",
    "alien_star",
)

/** Eski profil avatar anahtarlarını yeni modern avatar kataloğuna eşler. */
fun normalizeAvatarKey(key: String): String = when (key) {
    "aurora" -> "space_astro"
    "ocean" -> "scifi_cyber"
    "berry" -> "hero_crimson"
    "sunset" -> "retro_gamer"
    "forest" -> "nature_spirit"
    "moon" -> "ninja_shadow"
    in profileAvatarOptions -> key
    else -> "space_astro"
}

@Composable
fun ProfileAvatar(
    avatar: String,
    modifier: Modifier = Modifier,
    selected: Boolean = false,
    size: Int = 56,
) {
    val normalized = normalizeAvatarKey(avatar)
    val activeBorder = if (selected) {
        Modifier.border(3.dp, MaterialTheme.colorScheme.primary, CircleShape)
    } else {
        Modifier.border(1.dp, Color(0xFF1E3A5F).copy(alpha = 0.5f), CircleShape)
    }

    Box(
        modifier = modifier
            .size(size.dp)
            .shadow(if (selected) 8.dp else 2.dp, CircleShape)
            .clip(CircleShape)
            .then(activeBorder),
        contentAlignment = Alignment.Center,
    ) {
        Canvas(modifier = Modifier.size(size.dp)) {
            val w = this.size.width
            val h = this.size.height

            when (normalized) {
                "space_astro" -> drawSpaceAstro(w, h)
                "scifi_cyber" -> drawScifiCyber(w, h)
                "hero_crimson" -> drawHeroCrimson(w, h)
                "ninja_shadow" -> drawNinjaShadow(w, h)
                "retro_gamer" -> drawRetroGamer(w, h)
                "magic_wizard" -> drawMagicWizard(w, h)
                "cute_bot" -> drawCuteBot(w, h)
                "pirate_gold" -> drawPirateGold(w, h)
                "viking_warrior" -> drawVikingWarrior(w, h)
                "nature_spirit" -> drawNatureSpirit(w, h)
                "detective_noir" -> drawDetectiveNoir(w, h)
                "alien_star" -> drawAlienStar(w, h)
                else -> drawSpaceAstro(w, h)
            }
        }
    }
}

private fun DrawScope.drawSpaceAstro(w: Float, h: Float) {
    // Arka plan: Derin uzay mor/lacivert gradyan
    drawRect(
        brush = Brush.verticalGradient(listOf(Color(0xFF1E2640), Color(0xFF090C17))),
        size = Size(w, h),
    )
    // Yıldızlar
    drawCircle(Color.White.copy(alpha = 0.8f), radius = w * 0.02f, center = Offset(w * 0.2f, h * 0.22f))
    drawCircle(Color(0xFF00E5FF).copy(alpha = 0.8f), radius = w * 0.025f, center = Offset(w * 0.82f, h * 0.28f))
    drawCircle(Color.White.copy(alpha = 0.6f), radius = w * 0.015f, center = Offset(w * 0.72f, h * 0.15f))

    // Kask dış çeper
    drawCircle(
        color = Color(0xFFECEFF1),
        radius = w * 0.36f,
        center = Offset(w * 0.5f, h * 0.52f),
    )
    // Kask alt yaka
    drawRoundRect(
        color = Color(0xFF78909C),
        topLeft = Offset(w * 0.32f, h * 0.8f),
        size = Size(w * 0.36f, h * 0.14f),
        cornerRadius = CornerRadius(w * 0.06f),
    )
    // Vizör (Altın yansımalı ayna)
    drawRoundRect(
        brush = Brush.verticalGradient(listOf(Color(0xFFFFCA28), Color(0xFFFF6F00))),
        topLeft = Offset(w * 0.24f, h * 0.35f),
        size = Size(w * 0.52f, h * 0.32f),
        cornerRadius = CornerRadius(w * 0.14f),
    )
    // Vizör parıltısı (specular streak)
    val highlightPath = Path().apply {
        moveTo(w * 0.3f, h * 0.42f)
        lineTo(w * 0.46f, h * 0.39f)
        lineTo(w * 0.4f, h * 0.47f)
        lineTo(w * 0.28f, h * 0.48f)
        close()
    }
    drawPath(highlightPath, color = Color.White.copy(alpha = 0.7f))
}

private fun DrawScope.drawScifiCyber(w: Float, h: Float) {
    // Cyberpunk Neon degrade
    drawRect(
        brush = Brush.linearGradient(
            colors = listOf(Color(0xFF4A148C), Color(0xFF0D47A1)),
            start = Offset(0f, 0f),
            end = Offset(w, h),
        ),
        size = Size(w, h),
    )
    // Yüz silueti
    drawRoundRect(
        color = Color(0xFF1E212B),
        topLeft = Offset(w * 0.26f, h * 0.26f),
        size = Size(w * 0.48f, h * 0.54f),
        cornerRadius = CornerRadius(w * 0.16f),
    )
    // Neon Cyber Vizör
    drawRoundRect(
        brush = Brush.horizontalGradient(listOf(Color(0xFF00E5FF), Color(0xFF18FFFF))),
        topLeft = Offset(w * 0.22f, h * 0.42f),
        size = Size(w * 0.56f, h * 0.13f),
        cornerRadius = CornerRadius(w * 0.06f),
    )
    // Kulak neon soketleri
    drawCircle(Color(0xFFFF4081), radius = w * 0.045f, center = Offset(w * 0.22f, h * 0.48f))
    drawCircle(Color(0xFFFF4081), radius = w * 0.045f, center = Offset(w * 0.78f, h * 0.48f))
    // Ağız çizgisi
    drawLine(
        color = Color(0xFF00E5FF).copy(alpha = 0.8f),
        start = Offset(w * 0.38f, h * 0.68f),
        end = Offset(w * 0.62f, h * 0.68f),
        strokeWidth = w * 0.03f,
        cap = StrokeCap.Round,
    )
}

private fun DrawScope.drawHeroCrimson(w: Float, h: Float) {
    // Kahraman kırmızı gradyanı
    drawRect(
        brush = Brush.verticalGradient(listOf(Color(0xFFD32F2F), Color(0xFF3E0509))),
        size = Size(w, h),
    )
    // Maske / Başlık
    val cowl = Path().apply {
        moveTo(w * 0.5f, h * 0.18f)
        cubicTo(w * 0.8f, h * 0.2f, w * 0.85f, h * 0.65f, w * 0.75f, h * 0.85f)
        lineTo(w * 0.25f, h * 0.85f)
        cubicTo(w * 0.15f, h * 0.65f, w * 0.2f, h * 0.2f, w * 0.5f, h * 0.18f)
        close()
    }
    drawPath(cowl, color = Color(0xFF880E4F))

    // Alın arması (Altın yıldız / amblem)
    drawCircle(Color(0xFFFFD700), radius = w * 0.07f, center = Offset(w * 0.5f, h * 0.34f))

    // Açılı kahraman gözleri (Keskin beyaz)
    val leftEye = Path().apply {
        moveTo(w * 0.3f, h * 0.48f)
        lineTo(w * 0.44f, h * 0.54f)
        lineTo(w * 0.34f, h * 0.57f)
        close()
    }
    val rightEye = Path().apply {
        moveTo(w * 0.7f, h * 0.48f)
        lineTo(w * 0.56f, h * 0.54f)
        lineTo(w * 0.66f, h * 0.57f)
        close()
    }
    drawPath(leftEye, color = Color.White)
    drawPath(rightEye, color = Color.White)
}

private fun DrawScope.drawNinjaShadow(w: Float, h: Float) {
    // Gece gölgesi zemin
    drawRect(
        brush = Brush.verticalGradient(listOf(Color(0xFF37474F), Color(0xFF0F1416))),
        size = Size(w, h),
    )
    // Ninja kapüşon
    drawCircle(Color(0xFF1E272C), radius = w * 0.35f, center = Offset(w * 0.5f, h * 0.5f))

    // Alın bandı
    drawRoundRect(
        color = Color(0xFF263238),
        topLeft = Offset(w * 0.2f, h * 0.34f),
        size = Size(w * 0.6f, h * 0.14f),
        cornerRadius = CornerRadius(w * 0.04f),
    )
    // Metal plaka
    drawRoundRect(
        color = Color(0xFF78909C),
        topLeft = Offset(w * 0.36f, h * 0.36f),
        size = Size(w * 0.28f, h * 0.09f),
        cornerRadius = CornerRadius(w * 0.02f),
    )

    // Göz aralığı ve parlayan gözler
    drawRoundRect(
        color = Color(0xFF12171A),
        topLeft = Offset(w * 0.26f, h * 0.49f),
        size = Size(w * 0.48f, h * 0.12f),
        cornerRadius = CornerRadius(w * 0.04f),
    )
    // Parlayan zümrüt bakışlar
    drawRoundRect(
        color = Color(0xFF00E676),
        topLeft = Offset(w * 0.32f, h * 0.52f),
        size = Size(w * 0.12f, h * 0.05f),
        cornerRadius = CornerRadius(w * 0.02f),
    )
    drawRoundRect(
        color = Color(0xFF00E676),
        topLeft = Offset(w * 0.56f, h * 0.52f),
        size = Size(w * 0.12f, h * 0.05f),
        cornerRadius = CornerRadius(w * 0.02f),
    )
}

private fun DrawScope.drawRetroGamer(w: Float, h: Float) {
    // 80s Retro mor/indigo gradyan
    drawRect(
        brush = Brush.verticalGradient(listOf(Color(0xFF6A1B9A), Color(0xFF1A237E))),
        size = Size(w, h),
    )
    // Altın piksel taç (3 tepe)
    val crown = Path().apply {
        moveTo(w * 0.25f, h * 0.45f)
        lineTo(w * 0.25f, h * 0.26f)
        lineTo(w * 0.38f, h * 0.35f)
        lineTo(w * 0.5f, h * 0.2f)
        lineTo(w * 0.62f, h * 0.35f)
        lineTo(w * 0.75f, h * 0.26f)
        lineTo(w * 0.75f, h * 0.45f)
        close()
    }
    drawPath(crown, color = Color(0xFFFFD54F))

    // Taç tabanı
    drawRect(
        color = Color(0xFFFFB300),
        topLeft = Offset(w * 0.25f, h * 0.43f),
        size = Size(w * 0.5f, h * 0.06f),
    )

    // Retro 8-bit gözlük
    drawRoundRect(
        color = Color(0xFF00E5FF),
        topLeft = Offset(w * 0.22f, h * 0.54f),
        size = Size(w * 0.24f, h * 0.16f),
        cornerRadius = CornerRadius(w * 0.02f),
    )
    drawRoundRect(
        color = Color(0xFF00E5FF),
        topLeft = Offset(w * 0.54f, h * 0.54f),
        size = Size(w * 0.24f, h * 0.16f),
        cornerRadius = CornerRadius(w * 0.02f),
    )
    drawRect(
        color = Color(0xFF00E5FF),
        topLeft = Offset(w * 0.44f, h * 0.58f),
        size = Size(w * 0.12f, h * 0.05f),
    )
}

private fun DrawScope.drawMagicWizard(w: Float, h: Float) {
    // Büyücü zümrüt/mistik gradyan
    drawRect(
        brush = Brush.verticalGradient(listOf(Color(0xFF004D40), Color(0xFF001F18))),
        size = Size(w, h),
    )
    // Sivri uçlu büyücü kukuletası
    val hood = Path().apply {
        moveTo(w * 0.5f, h * 0.14f)
        lineTo(w * 0.82f, h * 0.84f)
        lineTo(w * 0.18f, h * 0.84f)
        close()
    }
    drawPath(hood, color = Color(0xFF00796B))

    // İç gölge
    drawCircle(Color(0xFF00241C), radius = w * 0.24f, center = Offset(w * 0.5f, h * 0.58f))

    // Parlayan mistik kristal / göz
    drawCircle(Color(0xFF64FFDA), radius = w * 0.07f, center = Offset(w * 0.5f, h * 0.55f))
    drawCircle(Color.White, radius = w * 0.03f, center = Offset(w * 0.5f, h * 0.55f))

    // Büyü kıvılcımları
    drawCircle(Color(0xFFE0F2F1), radius = w * 0.015f, center = Offset(w * 0.25f, h * 0.35f))
    drawCircle(Color(0xFF80CBC4), radius = w * 0.02f, center = Offset(w * 0.76f, h * 0.4f))
}

private fun DrawScope.drawCuteBot(w: Float, h: Float) {
    // Sıcak turuncu/lacivert gradyan
    drawRect(
        brush = Brush.verticalGradient(listOf(Color(0xFFE65100), Color(0xFF263238))),
        size = Size(w, h),
    )
    // Anten
    drawLine(
        color = Color(0xFFCFD8DC),
        start = Offset(w * 0.5f, h * 0.25f),
        end = Offset(w * 0.5f, h * 0.15f),
        strokeWidth = w * 0.04f,
    )
    drawCircle(Color(0xFFFFD54F), radius = w * 0.05f, center = Offset(w * 0.5f, h * 0.14f))

    // Robot kafası
    drawRoundRect(
        color = Color(0xFFECEFF1),
        topLeft = Offset(w * 0.22f, h * 0.26f),
        size = Size(w * 0.56f, h * 0.54f),
        cornerRadius = CornerRadius(w * 0.14f),
    )
    // Ekran vizörü
    drawRoundRect(
        color = Color(0xFF1E272C),
        topLeft = Offset(w * 0.28f, h * 0.34f),
        size = Size(w * 0.44f, h * 0.32f),
        cornerRadius = CornerRadius(w * 0.08f),
    )
    // Mavi sevimli optik gözler
    drawCircle(Color(0xFF00E5FF), radius = w * 0.065f, center = Offset(w * 0.4f, h * 0.49f))
    drawCircle(Color.White, radius = w * 0.025f, center = Offset(w * 0.38f, h * 0.47f))
    drawCircle(Color(0xFF00E5FF), radius = w * 0.065f, center = Offset(w * 0.6f, h * 0.49f))
    drawCircle(Color.White, radius = w * 0.025f, center = Offset(w * 0.58f, h * 0.47f))

    // Gülümseme çizgisi
    drawLine(
        color = Color(0xFF00E5FF),
        start = Offset(w * 0.42f, h * 0.68f),
        end = Offset(w * 0.58f, h * 0.68f),
        strokeWidth = w * 0.03f,
        cap = StrokeCap.Round,
    )
}

private fun DrawScope.drawPirateGold(w: Float, h: Float) {
    // Denizci okyanus lacivert gradyan
    drawRect(
        brush = Brush.verticalGradient(listOf(Color(0xFF0D47A1), Color(0xFF1A1A24))),
        size = Size(w, h),
    )
    // Yüz
    drawCircle(Color(0xFFFFCC80), radius = w * 0.28f, center = Offset(w * 0.5f, h * 0.56f))

    // Kaptan şapkası (Tricorn)
    val hat = Path().apply {
        moveTo(w * 0.12f, h * 0.42f)
        cubicTo(w * 0.3f, h * 0.22f, w * 0.7f, h * 0.22f, w * 0.88f, h * 0.42f)
        lineTo(w * 0.5f, h * 0.18f)
        close()
    }
    drawPath(hat, color = Color(0xFF212121))
    // Şapka altın süsü
    drawCircle(Color(0xFFFFD54F), radius = w * 0.05f, center = Offset(w * 0.5f, h * 0.3f))

    // Göz bandı (sağ göz)
    drawLine(
        color = Color(0xFF212121),
        start = Offset(w * 0.32f, h * 0.44f),
        end = Offset(w * 0.72f, h * 0.6f),
        strokeWidth = w * 0.035f,
    )
    drawCircle(Color(0xFF212121), radius = w * 0.065f, center = Offset(w * 0.6f, h * 0.54f))

    // Sol göz
    drawCircle(Color(0xFF212121), radius = w * 0.045f, center = Offset(w * 0.4f, h * 0.54f))
    drawCircle(Color.White, radius = w * 0.015f, center = Offset(w * 0.38f, h * 0.52f))

    // Bıyık
    val stache = Path().apply {
        moveTo(w * 0.36f, h * 0.68f)
        quadraticTo(w * 0.5f, h * 0.64f, w * 0.64f, h * 0.68f)
    }
    drawPath(stache, color = Color(0xFF424242), style = Stroke(width = w * 0.04f, cap = StrokeCap.Round))
}

private fun DrawScope.drawVikingWarrior(w: Float, h: Float) {
    // İskandinav çelik/bronz gradyan
    drawRect(
        brush = Brush.verticalGradient(listOf(Color(0xFF455A64), Color(0xFF1C252B))),
        size = Size(w, h),
    )
    // Boynuzlar (Sol & Sağ)
    val leftHorn = Path().apply {
        moveTo(w * 0.28f, h * 0.4f)
        quadraticTo(w * 0.12f, h * 0.25f, w * 0.15f, h * 0.14f)
        quadraticTo(w * 0.26f, h * 0.28f, w * 0.32f, h * 0.36f)
        close()
    }
    val rightHorn = Path().apply {
        moveTo(w * 0.72f, h * 0.4f)
        quadraticTo(w * 0.88f, h * 0.25f, w * 0.85f, h * 0.14f)
        quadraticTo(w * 0.74f, h * 0.28f, w * 0.68f, h * 0.36f)
        close()
    }
    drawPath(leftHorn, color = Color(0xFFFFECB3))
    drawPath(rightHorn, color = Color(0xFFFFECB3))

    // Demir miğfer
    drawCircle(Color(0xFF78909C), radius = w * 0.28f, center = Offset(w * 0.5f, h * 0.46f))
    drawRect(
        color = Color(0xFF546E7A),
        topLeft = Offset(w * 0.46f, h * 0.42f),
        size = Size(w * 0.08f, h * 0.18f),
    )

    // Sakal (Kızıl-kahve)
    val beard = Path().apply {
        moveTo(w * 0.3f, h * 0.58f)
        lineTo(w * 0.5f, h * 0.86f)
        lineTo(w * 0.7f, h * 0.58f)
        close()
    }
    drawPath(beard, color = Color(0xFFFF8F00))
}

private fun DrawScope.drawNatureSpirit(w: Float, h: Float) {
    // Doğal zümrüt/orman gradyanı
    drawRect(
        brush = Brush.verticalGradient(listOf(Color(0xFF2E7D32), Color(0xFF00330C))),
        size = Size(w, h),
    )
    // Tilki / Ruh kulakları
    val leftEar = Path().apply {
        moveTo(w * 0.25f, h * 0.4f)
        lineTo(w * 0.28f, h * 0.16f)
        lineTo(w * 0.44f, h * 0.36f)
        close()
    }
    val rightEar = Path().apply {
        moveTo(w * 0.75f, h * 0.4f)
        lineTo(w * 0.72f, h * 0.16f)
        lineTo(w * 0.56f, h * 0.36f)
        close()
    }
    drawPath(leftEar, color = Color(0xFF81C784))
    drawPath(rightEar, color = Color(0xFF81C784))

    // Yüz
    drawCircle(Color(0xFFE8F5E9), radius = w * 0.3f, center = Offset(w * 0.5f, h * 0.54f))

    // Büyük tatlı gözler
    drawCircle(Color(0xFF1B5E20), radius = w * 0.06f, center = Offset(w * 0.4f, h * 0.52f))
    drawCircle(Color.White, radius = w * 0.025f, center = Offset(w * 0.38f, h * 0.5f))
    drawCircle(Color(0xFF1B5E20), radius = w * 0.06f, center = Offset(w * 0.6f, h * 0.52f))
    drawCircle(Color.White, radius = w * 0.025f, center = Offset(w * 0.58f, h * 0.5f))

    // Burun
    drawCircle(Color(0xFF81C784), radius = w * 0.035f, center = Offset(w * 0.5f, h * 0.63f))
}

private fun DrawScope.drawDetectiveNoir(w: Float, h: Float) {
    // Klasik Noir kömür/gece mavisi gradyan
    drawRect(
        brush = Brush.verticalGradient(listOf(Color(0xFF424242), Color(0xFF121212))),
        size = Size(w, h),
    )
    // Fötr Şapka tepe
    drawRoundRect(
        color = Color(0xFF212121),
        topLeft = Offset(w * 0.3f, h * 0.22f),
        size = Size(w * 0.4f, h * 0.26f),
        cornerRadius = CornerRadius(w * 0.08f),
    )
    // Şapka şeridi
    drawRect(
        color = Color(0xFFB71C1C),
        topLeft = Offset(w * 0.3f, h * 0.42f),
        size = Size(w * 0.4f, h * 0.06f),
    )
    // Şapka eğik siperliği
    val brim = Path().apply {
        moveTo(w * 0.12f, h * 0.48f)
        quadraticTo(w * 0.5f, h * 0.42f, w * 0.88f, h * 0.54f)
        quadraticTo(w * 0.5f, h * 0.5f, w * 0.12f, h * 0.48f)
        close()
    }
    drawPath(brim, color = Color(0xFF1A1A1A))

    // Gölge yüz & Altın monokl
    drawCircle(
        color = Color(0xFFFFD54F),
        radius = w * 0.075f,
        center = Offset(w * 0.42f, h * 0.62f),
        style = Stroke(width = w * 0.03f),
    )
    drawCircle(Color(0xFFFFECB3).copy(alpha = 0.5f), radius = w * 0.06f, center = Offset(w * 0.42f, h * 0.62f))
}

private fun DrawScope.drawAlienStar(w: Float, h: Float) {
    // Galaksi kozmik mor/turkuaz gradyan
    drawRect(
        brush = Brush.verticalGradient(listOf(Color(0xFF4A148C), Color(0xFF00695C))),
        size = Size(w, h),
    )
    // Kozmik antenler
    drawLine(
        color = Color(0xFF80CBC4),
        start = Offset(w * 0.4f, h * 0.3f),
        end = Offset(w * 0.28f, h * 0.14f),
        strokeWidth = w * 0.035f,
    )
    drawCircle(Color(0xFF00E5FF), radius = w * 0.045f, center = Offset(w * 0.28f, h * 0.14f))

    drawLine(
        color = Color(0xFF80CBC4),
        start = Offset(w * 0.6f, h * 0.3f),
        end = Offset(w * 0.72f, h * 0.14f),
        strokeWidth = w * 0.035f,
    )
    drawCircle(Color(0xFF00E5FF), radius = w * 0.045f, center = Offset(w * 0.72f, h * 0.14f))

    // Uzaylı kafası (Geniş üst, dar çene)
    val head = Path().apply {
        moveTo(w * 0.5f, h * 0.22f)
        cubicTo(w * 0.85f, h * 0.24f, w * 0.78f, h * 0.65f, w * 0.5f, h * 0.82f)
        cubicTo(w * 0.22f, h * 0.65f, w * 0.15f, h * 0.24f, w * 0.5f, h * 0.22f)
        close()
    }
    drawPath(head, color = Color(0xFF80CBC4))

    // Büyük badem biçimli kozmik gözler
    val leftEye = Path().apply {
        moveTo(w * 0.32f, h * 0.44f)
        quadraticTo(w * 0.42f, h * 0.36f, w * 0.44f, h * 0.52f)
        quadraticTo(w * 0.38f, h * 0.56f, w * 0.32f, h * 0.44f)
        close()
    }
    val rightEye = Path().apply {
        moveTo(w * 0.68f, h * 0.44f)
        quadraticTo(w * 0.58f, h * 0.36f, w * 0.56f, h * 0.52f)
        quadraticTo(w * 0.62f, h * 0.56f, w * 0.68f, h * 0.44f)
        close()
    }
    drawPath(leftEye, color = Color(0xFF1A237E))
    drawPath(rightEye, color = Color(0xFF1A237E))
    drawCircle(Color.White, radius = w * 0.02f, center = Offset(w * 0.38f, h * 0.46f))
    drawCircle(Color.White, radius = w * 0.02f, center = Offset(w * 0.62f, h * 0.46f))
}
