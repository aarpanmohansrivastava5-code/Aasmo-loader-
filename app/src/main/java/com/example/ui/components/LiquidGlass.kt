package com.example.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.Hub
import androidx.compose.material.icons.outlined.Language
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.Hub
import androidx.compose.material.icons.rounded.Language
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.GlassBase
import com.example.ui.theme.GlassEdge
import com.example.ui.theme.GlassEdgeLo
import com.example.ui.theme.GlassHi
import com.example.ui.theme.LocalPrismColors
import com.example.ui.theme.TextMuted
import com.example.ui.theme.TextPrimary

fun Modifier.liquidGlass(
    shape: Shape = RoundedCornerShape(28.dp),
    intensity: Float = 1f,
    borderColor: Color = GlassEdgeLo,
    topHighlightAlpha: Float = 0.28f,
): Modifier {
    val hiAlpha = (0.14f * intensity).coerceIn(0.06f, 0.24f)
    val baseAlpha = (0.06f * intensity).coerceIn(0.03f, 0.14f)
    return this
        .clip(shape)
        .background(
            brush = Brush.linearGradient(
                colors = listOf(
                    Color.White.copy(alpha = hiAlpha),
                    Color.White.copy(alpha = baseAlpha),
                    Color(0xFF0A0B14).copy(alpha = (0.55f * intensity).coerceIn(0.25f, 0.72f)),
                ),
                start = Offset.Zero,
                end = Offset(800f, 1000f),
            ),
            shape = shape,
        )
        .border(
            width = 1.dp,
            brush = Brush.verticalGradient(
                colors = listOf(
                    Color.White.copy(alpha = topHighlightAlpha),
                    borderColor,
                )
            ),
            shape = shape,
        )
}

@Composable
fun LiquidGlassSurface(
    modifier: Modifier = Modifier,
    shape: Shape = RoundedCornerShape(28.dp),
    borderColor: Color = GlassEdgeLo,
    content: @Composable BoxScope.() -> Unit,
) {
    val glassIntensity = LocalPrismColors.current.glassAlphaMultiplier
    Box(
        modifier = modifier.liquidGlass(
            shape = shape,
            intensity = glassIntensity,
            borderColor = borderColor,
        ),
        content = content,
    )
}

@Composable
fun PrismGradientButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    icon: ImageVector? = null,
    height: Dp = 44.dp,
    contentPadding: PaddingValues = PaddingValues(horizontal = 20.dp),
) {
    val accent = LocalPrismColors.current
    val gradient = if (enabled) {
        Brush.linearGradient(listOf(accent.primary, accent.secondary))
    } else {
        Brush.linearGradient(listOf(GlassHi, GlassBase))
    }

    Box(
        modifier = modifier
            .defaultMinSize(minHeight = height, minWidth = 48.dp)
            .clip(CircleShape)
            .background(gradient, CircleShape)
            .border(
                width = 1.dp,
                brush = Brush.verticalGradient(
                    listOf(Color.White.copy(alpha = 0.50f), Color.Transparent)
                ),
                shape = CircleShape,
            )
            .clickable(enabled = enabled, onClick = onClick)
            .padding(contentPadding),
        contentAlignment = Alignment.Center,
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center,
        ) {
            if (icon != null) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(18.dp),
                )
                Spacer(Modifier.width(6.dp))
            }
            Text(
                text = text,
                color = if (enabled) Color.White else TextMuted,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

enum class AppSection(
    val id: String,
    val label: String,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector,
) {
    BROWSER("browser", "Browser", Icons.Rounded.Language, Icons.Outlined.Language),
    LOADER("loader", "Loader", Icons.Rounded.Download, Icons.Outlined.Download),
    HUBS("hubs", "Hubs", Icons.Rounded.Hub, Icons.Outlined.Hub),
    AI_MORE("ai", "AI & More", Icons.Rounded.AutoAwesome, Icons.Outlined.AutoAwesome),
}

@Composable
fun PrismBottomDock(
    currentSection: AppSection,
    activeDownloadCount: Int,
    onSelectSection: (AppSection) -> Unit,
    modifier: Modifier = Modifier,
) {
    val accent = LocalPrismColors.current

    Box(
        modifier = modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(horizontal = 12.dp, vertical = 8.dp),
        contentAlignment = Alignment.Center,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .liquidGlass(
                    shape = RoundedCornerShape(26.dp),
                    intensity = 1.05f,
                    borderColor = GlassEdgeLo,
                    topHighlightAlpha = 0.34f,
                )
                .padding(horizontal = 6.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            AppSection.entries.forEach { section ->
                val selected = section == currentSection
                val itemTint by animateColorAsState(
                    targetValue = if (selected) TextPrimary else TextMuted,
                    label = "dock_tint_${section.id}",
                )

                Box(
                    modifier = Modifier
                        .weight(1f)
                        .defaultMinSize(minHeight = 48.dp)
                        .clip(RoundedCornerShape(20.dp))
                        .then(
                            if (selected) {
                                Modifier
                                    .background(
                                        Brush.linearGradient(
                                            listOf(
                                                accent.primary.copy(alpha = 0.30f),
                                                accent.secondary.copy(alpha = 0.22f),
                                            )
                                        ),
                                        RoundedCornerShape(20.dp),
                                    )
                                    .drawBehind {
                                        drawRoundRect(
                                            brush = Brush.horizontalGradient(
                                                listOf(accent.primary, accent.secondary)
                                            ),
                                            cornerRadius = CornerRadius(20.dp.toPx()),
                                            style = Stroke(width = 1.dp.toPx()),
                                        )
                                    }
                            } else {
                                Modifier
                            }
                        )
                        .clickable { onSelectSection(section) }
                        .testTag("nav_${section.id}")
                        .padding(vertical = 6.dp, horizontal = 4.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center,
                    ) {
                        Box {
                            Icon(
                                imageVector = if (selected) section.selectedIcon else section.unselectedIcon,
                                contentDescription = section.label,
                                tint = itemTint,
                                modifier = Modifier.size(21.dp),
                            )
                            if (section == AppSection.LOADER && activeDownloadCount > 0) {
                                Box(
                                    modifier = Modifier
                                        .align(Alignment.TopEnd)
                                        .size(8.dp)
                                        .clip(CircleShape)
                                        .background(accent.orb3),
                                )
                            }
                        }
                        Spacer(Modifier.height(2.dp))
                        Text(
                            text = section.label,
                            color = itemTint,
                            fontSize = 11.sp,
                            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }
        }
    }
}
