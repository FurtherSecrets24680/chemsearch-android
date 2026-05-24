package com.furthersecrets.chemsearch.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight

val ChemMotionEasing = CubicBezierEasing(0.22f, 1f, 0.36f, 1f)
const val ChemMotionFast = 170
const val ChemMotionMedium = 240

fun Modifier.chemAnimateContentSize(): Modifier =
    animateContentSize(animationSpec = tween(ChemMotionMedium, easing = ChemMotionEasing))

@Composable
fun AnimatedStateIcon(
    selected: Boolean,
    selectedIcon: ImageVector,
    unselectedIcon: ImageVector,
    selectedDescription: String?,
    unselectedDescription: String?,
    tint: Color,
    modifier: Modifier = Modifier
) {
    AnimatedContent(
        targetState = selected,
        transitionSpec = {
            (scaleIn(
                initialScale = 0.82f,
                animationSpec = tween(ChemMotionFast, easing = ChemMotionEasing)
            ) + fadeIn(tween(ChemMotionFast))) togetherWith
                (scaleOut(
                    targetScale = 0.82f,
                    animationSpec = tween(ChemMotionFast, easing = ChemMotionEasing)
                ) + fadeOut(tween(ChemMotionFast)))
        },
        label = "AnimatedStateIcon"
    ) { isSelected ->
        Icon(
            imageVector = if (isSelected) selectedIcon else unselectedIcon,
            contentDescription = if (isSelected) selectedDescription else unselectedDescription,
            tint = tint,
            modifier = modifier
        )
    }
}

@Composable
fun AnimatedStateIcon(
    selected: Boolean,
    selectedIcon: ChemIconSpec,
    unselectedIcon: ChemIconSpec,
    selectedDescription: String?,
    unselectedDescription: String?,
    tint: Color,
    modifier: Modifier = Modifier
) {
    AnimatedContent(
        targetState = selected,
        transitionSpec = {
            (scaleIn(
                initialScale = 0.82f,
                animationSpec = tween(ChemMotionFast, easing = ChemMotionEasing)
            ) + fadeIn(tween(ChemMotionFast))) togetherWith
                (scaleOut(
                    targetScale = 0.82f,
                    animationSpec = tween(ChemMotionFast, easing = ChemMotionEasing)
                ) + fadeOut(tween(ChemMotionFast)))
        },
        label = "AnimatedStateIcon"
    ) { isSelected ->
        ChemIcon(
            icon = if (isSelected) selectedIcon else unselectedIcon,
            contentDescription = if (isSelected) selectedDescription else unselectedDescription,
            tint = tint,
            modifier = modifier
        )
    }
}

@Composable
fun AnimatedActionLabel(
    text: String,
    color: Color,
    modifier: Modifier = Modifier,
    style: TextStyle = MaterialTheme.typography.labelLarge,
    fontWeight: FontWeight = FontWeight.SemiBold
) {
    AnimatedContent(
        targetState = text,
        transitionSpec = {
            (slideInVertically(
                animationSpec = tween(ChemMotionFast, easing = ChemMotionEasing),
                initialOffsetY = { it / 2 }
            ) + fadeIn(tween(ChemMotionFast))) togetherWith
                (slideOutVertically(
                    animationSpec = tween(ChemMotionFast, easing = ChemMotionEasing),
                    targetOffsetY = { -it / 2 }
                ) + fadeOut(tween(ChemMotionFast)))
        },
        label = "AnimatedActionLabel",
        modifier = modifier
    ) { label ->
        Text(label, color = color, fontWeight = fontWeight, style = style)
    }
}
