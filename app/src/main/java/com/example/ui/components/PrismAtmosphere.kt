package com.example.ui.components

import android.net.Uri
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import coil.compose.AsyncImage
import com.example.R
import com.example.data.UserSettings
import com.example.data.WallpaperMode
import com.example.ui.theme.LocalPrismColors
import com.example.ui.theme.ObsidianBg1
import com.example.ui.theme.ObsidianBg2

@Composable
fun PrismAtmosphere(
    settings: UserSettings,
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit,
) {
    val prismColors = LocalPrismColors.current
    val transition = rememberInfiniteTransition(label = "prism_orbs")

    val drift1 by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 14000, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "orb1",
    )
    val drift2 by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 18000, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "orb2",
    )

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(
                Brush.linearGradient(
                    colors = listOf(ObsidianBg1, ObsidianBg2),
                    start = Offset.Zero,
                    end = Offset(1200f, 2000f),
                )
            )
    ) {
        // Optional custom or bundled background wallpaper
        val wallAlpha = (1.15f - settings.glassIntensity * 0.45f).coerceIn(0.28f, 0.85f)
        when (settings.wallpaperMode) {
            WallpaperMode.WALL_PRISM -> {
                Image(
                    painter = painterResource(id = R.drawable.img_wall_prism),
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    alpha = wallAlpha,
                    modifier = Modifier.fillMaxSize(),
                )
            }
            WallpaperMode.WALL_AURORA -> {
                Image(
                    painter = painterResource(id = R.drawable.img_wall_aurora),
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    alpha = wallAlpha,
                    modifier = Modifier.fillMaxSize(),
                )
            }
            WallpaperMode.CUSTOM_PHOTO -> {
                val uri = settings.customWallpaperUri
                if (!uri.isNullOrBlank()) {
                    AsyncImage(
                        model = Uri.parse(uri),
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        alpha = wallAlpha,
                        modifier = Modifier.fillMaxSize(),
                    )
                }
            }
            WallpaperMode.PRISM_ORBS -> Unit
        }

        // 4 floating Prism blurred radial orbs matching CSS .o1 .. .o4
        Canvas(modifier = Modifier.fillMaxSize()) {
            val w = size.width
            val h = size.height
            val maxDim = maxOf(w, h)
            val orbOpacity = if (settings.wallpaperMode == WallpaperMode.PRISM_ORBS) 0.34f else 0.22f

            // o1: top-left primary indigo
            val c1 = Offset(
                x = w * (-0.08f + 0.14f * drift1),
                y = h * (-0.06f + 0.10f * drift1),
            )
            val r1 = maxDim * 0.48f
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(
                        prismColors.primary.copy(alpha = orbOpacity),
                        prismColors.primary.copy(alpha = orbOpacity * 0.3f),
                        Color.Transparent,
                    ),
                    center = c1,
                    radius = r1,
                ),
                radius = r1,
                center = c1,
            )

            // o2: mid-right pink
            val c2 = Offset(
                x = w * (1.02f - 0.14f * drift2),
                y = h * (0.24f + 0.10f * drift2),
            )
            val r2 = maxDim * 0.42f
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(
                        prismColors.secondary.copy(alpha = orbOpacity),
                        prismColors.secondary.copy(alpha = orbOpacity * 0.28f),
                        Color.Transparent,
                    ),
                    center = c2,
                    radius = r2,
                ),
                radius = r2,
                center = c2,
            )

            // o3: bottom-left teal
            val c3 = Offset(
                x = w * (0.28f + 0.12f * drift2),
                y = h * (0.98f - 0.12f * drift1),
            )
            val r3 = maxDim * 0.40f
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(
                        prismColors.orb3.copy(alpha = orbOpacity),
                        prismColors.orb3.copy(alpha = orbOpacity * 0.25f),
                        Color.Transparent,
                    ),
                    center = c3,
                    radius = r3,
                ),
                radius = r3,
                center = c3,
            )

            // o4: upper-right amber
            val c4 = Offset(
                x = w * (0.72f - 0.10f * drift1),
                y = h * (0.04f + 0.08f * drift2),
            )
            val r4 = maxDim * 0.26f
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(
                        prismColors.orb4.copy(alpha = orbOpacity * 0.9f),
                        prismColors.orb4.copy(alpha = orbOpacity * 0.2f),
                        Color.Transparent,
                    ),
                    center = c4,
                    radius = r4,
                ),
                radius = r4,
                center = c4,
            )
        }

        // Subtle dark readability vignette when custom wallpaper is active
        if (settings.wallpaperMode != WallpaperMode.PRISM_ORBS) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = (settings.glassIntensity * 0.38f).coerceIn(0.15f, 0.45f)))
            )
        }

        content()
    }
}
