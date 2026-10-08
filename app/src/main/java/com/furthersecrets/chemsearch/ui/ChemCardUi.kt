package com.furthersecrets.chemsearch.ui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardColors
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * The shared card design language for the whole app. Every selectable /
 * tappable card (search results, isomers, advanced results, structure hits,
 * database entries, tools, library) renders through this surface so all of
 * them share the same radius scale, border strength, elevation and accent
 * treatment on every screen size and theme (including AMOLED).
 *
 * Visual spec:
 *  - Radius: 16dp (compact) / 18dp (regular) via [ChemCardRadius]
 *  - Container: theme surface; slight tint toward [accent] when set
 *  - Border: 1dp outline (14%); brightens to the accent when [selected]
 *  - Elevation: 1dp resting, 3dp when selected, so the active card lifts
 *  - Accent edge: an optional 3dp leading bar in the accent color for
 *    category-coded cards (tools, database groups)
 */
object ChemCardStyle {
    fun radius(compact: Boolean): Dp = if (compact) 16.dp else 18.dp

    @Composable
    fun outlineColor(): Color = MaterialTheme.colorScheme.outline.copy(alpha = 0.14f)

    @Composable
    fun selectedColor(): Color = MaterialTheme.colorScheme.primary.copy(alpha = 0.62f)
}

/**
 * Flat-mode-aware building blocks for the few raw Card/Surface call sites that
 * predate [ChemCardSurface]. When the Cards setting is off they collapse to a
 * chrome-less rectangle so those screens join the unified flat page.
 */
@Composable
internal fun chemCardShape(radius: Dp): Shape =
    if (LocalCardsEnabled.current) RoundedCornerShape(radius) else RectangleShape

@Composable
internal fun chemCardColor(color: Color): Color =
    if (LocalCardsEnabled.current) color else Color.Transparent

@Composable
internal fun chemCardBorder(color: Color): BorderStroke? =
    if (LocalCardsEnabled.current) BorderStroke(1.dp, color) else null

@Composable
internal fun chemCardElevation(elevation: Dp): Dp =
    if (LocalCardsEnabled.current) elevation else 0.dp

/**
 * Hairline drawn above a section when the Cards setting is off, so flattened
 * screens keep a faint structure without returning to boxed cards. Card mode
 * renders nothing.
 */
@Composable
internal fun ChemFlatDivider(modifier: Modifier = Modifier) {
    if (!LocalCardsEnabled.current) {
        HorizontalDivider(
            modifier = modifier,
            thickness = 0.5.dp,
            color = MaterialTheme.colorScheme.outline.copy(alpha = 0.16f)
        )
    }
}

/**
 * Flat-mode tap target for callers that previously stacked a clickable inside
 * a card container. Keeps the whole area tappable, preserves the disabled
 * dimming, and clips the ripple to the compact/regular card radius so the
 * feedback reads identically to card mode.
 */
@Composable
internal fun ChemFlatTap(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    content: @Composable () -> Unit
) {
    val compact = LocalCompactMode.current
    Box(
        modifier = modifier
            .alpha(if (enabled) 1f else 0.45f)
            .clip(RoundedCornerShape(ChemCardStyle.radius(compact)))
            .clickable(enabled = enabled, onClick = onClick)
    ) {
        content()
    }
}

/**
 * Standard card surface.
 *
 * @param accent optional category/accent color: tints the container a touch,
 *        colors the selection border, and (with [showAccentEdge]) draws a
 *        leading color bar.
 * @param selected lifts + highlights the card (result pickers, compare, etc.)
 * @param showAccentEdge draws a 3dp rounded accent bar at the start edge.
 */
@Composable
fun ChemCardSurface(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    accent: Color? = null,
    selected: Boolean = false,
    showAccentEdge: Boolean = false,
    enabled: Boolean = true,
    containerColor: Color = MaterialTheme.colorScheme.surface,
    content: @Composable () -> Unit
) {
    val compact = LocalCompactMode.current
    if (!LocalCardsEnabled.current) {
        // Flat mode: unified page. A hairline keeps sections separated, the
        // tap target stays via [ChemFlatTap] (with disabled dimming and
        // selection feedback), and selected rows keep the accent edge.
        Column {
            ChemFlatDivider()
            if (selected) {
                Box {
                    AccentEdge(accent ?: MaterialTheme.colorScheme.primary, compact)
                    ChemFlatTap(onClick, Modifier.fillMaxWidth(), enabled) { content() }
                }
            } else {
                ChemFlatTap(onClick, Modifier.fillMaxWidth(), enabled) { content() }
            }
        }
        return
    }
    val accentColor = accent ?: MaterialTheme.colorScheme.primary
    val isLight = MaterialTheme.colorScheme.background.luminance() > 0.5f
    val accentTint = if (accent != null) {
        // A whisper of accent in the container: stronger in dark mode where
        // tints read better, fainter in light mode to avoid pastel mush.
        accentColor.copy(alpha = if (isLight) 0.045f else 0.075f)
    } else {
        Color.Transparent
    }
    val borderColor = when {
        selected -> ChemCardStyle.selectedColor()
        accent != null -> accentColor.copy(alpha = if (isLight) 0.30f else 0.24f)
        else -> ChemCardStyle.outlineColor()
    }
    val elevation by animateFloatAsState(
        targetValue = if (selected) 3f else 1f,
        animationSpec = tween(durationMillis = 180),
        label = "chemCardElevation"
    )

    Card(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier,
        shape = RoundedCornerShape(ChemCardStyle.radius(compact)),
        colors = CardDefaults.cardColors(
            containerColor = if (accentTint == Color.Transparent) {
                containerColor
            } else {
                // Blend manually so AMOLED's pure black stays pure.
                if (containerColor == MaterialTheme.colorScheme.surface) {
                    blendTint(containerColor, accentTint)
                } else {
                    containerColor
                }
            }
        ),
        border = BorderStroke(1.dp, borderColor),
        elevation = CardDefaults.cardElevation(defaultElevation = elevation.dp)
    ) {
        if (showAccentEdge && accent != null) {
            Box {
                AccentEdge(accentColor, compact)
                content()
            }
        } else {
            content()
        }
    }
}

/** Non-clickable variant for static info cards. */
@Composable
fun ChemCardSurfaceStatic(
    modifier: Modifier = Modifier,
    accent: Color? = null,
    containerColor: Color = MaterialTheme.colorScheme.surface,
    content: @Composable () -> Unit
) {
    val compact = LocalCompactMode.current
    if (!LocalCardsEnabled.current) {
        // Flat mode: unified page with a hairline above each former card.
        Column {
            ChemFlatDivider()
            content()
        }
        return
    }
    val isLight = MaterialTheme.colorScheme.background.luminance() > 0.5f
    val accentColor = accent ?: MaterialTheme.colorScheme.primary
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(ChemCardStyle.radius(compact)),
        colors = CardDefaults.cardColors(containerColor = containerColor),
        border = BorderStroke(
            1.dp,
            if (accent != null) accentColor.copy(alpha = if (isLight) 0.30f else 0.24f)
            else ChemCardStyle.outlineColor()
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        content()
    }
}

@Composable
private fun AccentEdge(accent: Color, compact: Boolean) {
    Box(
        modifier = Modifier
            .padding(start = 0.dp)
            .size(width = 3.dp, height = Dp.Infinity)
            .background(
                accent.copy(alpha = 0.85f),
                RoundedCornerShape(
                    topStart = ChemCardStyle.radius(compact),
                    bottomStart = ChemCardStyle.radius(compact),
                    topEnd = 0.dp,
                    bottomEnd = 0.dp
                )
            )
    )
}

/** Manual alpha blend that preserves dark/AMOLED surfaces. */
private fun blendTint(base: Color, tint: Color): Color {
    val a = tint.alpha
    return Color(
        red = base.red * (1 - a) + tint.red * a,
        green = base.green * (1 - a) + tint.green * a,
        blue = base.blue * (1 - a) + tint.blue * a,
        alpha = base.alpha
    )
}

/**
 * Standard rounded icon badge used inside cards: accent-tinted square with
 * the icon centered. Sizes follow the compact-mode scale.
 */
@Composable
fun ChemIconBadge(
    icon: ChemIconSpec,
    accent: Color,
    modifier: Modifier = Modifier,
    iconTint: Color = accent,
    badgeSize: Dp? = null,
    iconSize: Dp? = null
) {
    val compact = LocalCompactMode.current
    val size = badgeSize ?: (if (compact) 42.dp else 48.dp)
    val inner = iconSize ?: (if (compact) 22.dp else 25.dp)
    Surface(
        shape = RoundedCornerShape(if (compact) 12.dp else 13.dp),
        color = accent.copy(alpha = 0.10f),
        modifier = modifier.size(size)
    ) {
        Box(contentAlignment = Alignment.Center) {
            ChemIcon(
                icon,
                contentDescription = null,
                tint = iconTint,
                modifier = Modifier.size(inner)
            )
        }
    }
}

/**
 * Small accent pill for meta text (category labels, CIDs, counts). Replaces
 * the several one-off pill implementations across screens.
 */
@Composable
fun ChemAccentPill(
    text: String,
    accent: Color,
    modifier: Modifier = Modifier,
    filled: Boolean = false
) {
    Surface(
        shape = RoundedCornerShape(999.dp),
        color = if (filled) accent.copy(alpha = 0.16f) else accent.copy(alpha = 0.10f),
        border = BorderStroke(1.dp, accent.copy(alpha = 0.26f)),
        modifier = modifier
    ) {
        Text(
            text,
            modifier = Modifier.padding(horizontal = 9.dp, vertical = 3.dp),
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.SemiBold,
            color = accent
        )
    }
}
