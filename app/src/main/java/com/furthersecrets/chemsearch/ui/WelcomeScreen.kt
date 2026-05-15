package com.furthersecrets.chemsearch.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BookmarkBorder
import androidx.compose.material.icons.filled.Calculate
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.SmartToy
import androidx.compose.material.icons.filled.ViewInAr
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.furthersecrets.chemsearch.R
import com.furthersecrets.chemsearch.data.AppColorScheme
import com.furthersecrets.chemsearch.data.DescSource

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun WelcomeScreen(
    isDark: Boolean,
    colorScheme: AppColorScheme,
    defaultDescSource: DescSource,
    onSetDarkTheme: (Boolean) -> Unit,
    onSetColorScheme: (AppColorScheme) -> Unit,
    onSetDefaultDesc: (DescSource) -> Unit,
    onConfigureAiProvider: () -> Unit,
    onContinue: () -> Unit
) {
    val compact = LocalCompactMode.current
    val scrollState = rememberScrollState()
    var stage by remember { mutableIntStateOf(0) }
    val stageCount = 3
    val spacing = if (compact) 10.dp else 14.dp
    val horizontalPadding = if (compact) 16.dp else 22.dp
    val logoFrame = if (MaterialTheme.colorScheme.background.luminance() < 0.5f) {
        Color(0xFF111827)
    } else {
        Color(0xFF1F2933)
    }

    Surface(
        modifier = Modifier
            .fillMaxSize()
            .safeDrawingPadding(),
        color = MaterialTheme.colorScheme.background
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(scrollState)
                .padding(horizontal = horizontalPadding, vertical = if (compact) 14.dp else 22.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .widthIn(max = 540.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(spacing)
            ) {
                WelcomeProgress(stage = stage, stageCount = stageCount)

                when (stage) {
                    0 -> WelcomeIntroStage(logoFrame = logoFrame)
                    1 -> WelcomeAppearanceStage(
                        isDark = isDark,
                        colorScheme = colorScheme,
                        onSetDarkTheme = onSetDarkTheme,
                        onSetColorScheme = onSetColorScheme
                    )
                    2 -> WelcomeDescriptionStage(
                        defaultDescSource = defaultDescSource,
                        onSetDefaultDesc = onSetDefaultDesc,
                        onConfigureAiProvider = onConfigureAiProvider
                    )
                }

                WelcomeNavigation(
                    stage = stage,
                    stageCount = stageCount,
                    onBack = { stage = (stage - 1).coerceAtLeast(0) },
                    onNext = {
                        if (stage == stageCount - 1) onContinue()
                        else stage = (stage + 1).coerceAtMost(stageCount - 1)
                    },
                    onSkip = onContinue
                )
            }
        }
    }
}

@Composable
private fun WelcomeProgress(stage: Int, stageCount: Int) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            repeat(stageCount) { index ->
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .height(4.dp)
                        .background(
                            if (index <= stage) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.outline.copy(alpha = 0.18f),
                            RoundedCornerShape(999.dp)
                        )
                )
            }
        }
        Text(
            text = "Step ${stage + 1} of $stageCount",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.45f),
            fontWeight = FontWeight.SemiBold
        )
    }
}

@Composable
private fun WelcomeIntroStage(logoFrame: Color) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Surface(
            modifier = Modifier.size(88.dp),
            shape = RoundedCornerShape(22.dp),
            color = logoFrame,
            border = BorderStroke(1.dp, Color(0xFF3A3A3A)),
            shadowElevation = 2.dp
        ) {
            Image(
                painter = painterResource(id = R.drawable.chemsearch),
                contentDescription = "ChemSearch",
                modifier = Modifier.padding(5.dp),
                contentScale = ContentScale.Fit
            )
        }

        WelcomeTitle(
            title = "ChemSearch",
            subtitle = "CHEMISTRY SIMPLIFIED",
            body = "Search compounds, inspect structures, save useful results, and use practical chemistry tools in one native Android app."
        )

        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            WelcomeFeatureRow(Icons.Default.Search, "Compound search", "Look up PubChem data by name, formula, CAS-style identifier, or CID.")
            WelcomeFeatureRow(Icons.Default.ViewInAr, "2D and 3D structures", "View structure images, rotate 3D models, and save structure files.")
            WelcomeFeatureRow(Icons.Default.Calculate, "Chemistry tools", "Balance reactions, calculate molar mass, solve stoichiometry, and more.")
            WelcomeFeatureRow(Icons.Default.BookmarkBorder, "Local workflow", "Keep favorites, recent searches, cached results, and offline reference data.")
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun WelcomeAppearanceStage(
    isDark: Boolean,
    colorScheme: AppColorScheme,
    onSetDarkTheme: (Boolean) -> Unit,
    onSetColorScheme: (AppColorScheme) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(14.dp), modifier = Modifier.fillMaxWidth()) {
        WelcomeTitle(
            title = "Set your look",
            subtitle = "APPEARANCE",
            body = "Choose the theme and accent color ChemSearch should use across search, tools, and settings."
        )

        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
            WelcomeChoiceCard(
                selected = !isDark,
                icon = Icons.Default.LightMode,
                title = "Light",
                description = "Bright surfaces",
                modifier = Modifier.weight(1f),
                onClick = { onSetDarkTheme(false) }
            )
            WelcomeChoiceCard(
                selected = isDark,
                icon = Icons.Default.DarkMode,
                title = "Dark",
                description = "Low-light UI",
                modifier = Modifier.weight(1f),
                onClick = { onSetDarkTheme(true) }
            )
        }

        Text(
            "Color scheme",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.48f),
            fontWeight = FontWeight.Bold
        )
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            AppColorScheme.entries.forEach { scheme ->
                val selected = colorScheme == scheme
                Surface(
                    onClick = { onSetColorScheme(scheme) },
                    shape = RoundedCornerShape(999.dp),
                    color = if (selected) scheme.previewColor().copy(alpha = 0.14f) else MaterialTheme.colorScheme.surface,
                    border = BorderStroke(
                        1.dp,
                        if (selected) scheme.previewColor().copy(alpha = 0.72f)
                        else MaterialTheme.colorScheme.outline.copy(alpha = 0.22f)
                    )
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(7.dp)
                    ) {
                        Box(Modifier.size(12.dp).background(scheme.previewColor(), CircleShape))
                        Text(scheme.label(), style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold)
                        if (selected) Icon(Icons.Default.Check, null, tint = scheme.previewColor(), modifier = Modifier.size(14.dp))
                    }
                }
            }
        }
    }
}

@Composable
private fun WelcomeDescriptionStage(
    defaultDescSource: DescSource,
    onSetDefaultDesc: (DescSource) -> Unit,
    onConfigureAiProvider: () -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(14.dp), modifier = Modifier.fillMaxWidth()) {
        WelcomeTitle(
            title = "Pick descriptions",
            subtitle = "COMPOUND SUMMARIES",
            body = "Choose which source should open by default after a compound search."
        )

        listOf(
            DescSource.PUBCHEM to Triple(Icons.Default.Description, "PubChem", "Scientific summaries from PubChem records."),
            DescSource.WIKI to Triple(Icons.Default.Search, "Wikipedia", "General readable summaries when a page exists."),
            DescSource.AI to Triple(Icons.Default.SmartToy, "AI", "Generated explanations from your selected AI provider.")
        ).forEach { (source, item) ->
            WelcomeChoiceCard(
                selected = defaultDescSource == source,
                icon = item.first,
                title = item.second,
                description = item.third,
                onClick = { onSetDefaultDesc(source) }
            )
        }

        if (defaultDescSource == DescSource.AI) {
            OutlinedButton(
                onClick = onConfigureAiProvider,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                contentPadding = PaddingValues(vertical = 12.dp)
            ) {
                Icon(Icons.Default.SmartToy, null, modifier = Modifier.size(17.dp))
                Spacer(Modifier.width(8.dp))
                Text("Configure AI provider", fontWeight = FontWeight.SemiBold)
            }
        }
    }
}

@Composable
private fun WelcomeTitle(title: String, subtitle: String, body: String) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(5.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Text(
            text = subtitle,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.primary,
            fontWeight = FontWeight.Bold,
            letterSpacing = 1.5.sp,
            textAlign = TextAlign.Center
        )
        Text(
            text = title,
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.ExtraBold,
            color = MaterialTheme.colorScheme.onBackground,
            textAlign = TextAlign.Center
        )
        Text(
            text = body,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.68f),
            textAlign = TextAlign.Center
        )
    }
}

@Composable
private fun WelcomeNavigation(
    stage: Int,
    stageCount: Int,
    onBack: () -> Unit,
    onNext: () -> Unit,
    onSkip: () -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
        Button(
            onClick = onNext,
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp),
            contentPadding = PaddingValues(vertical = 14.dp)
        ) {
            Text(if (stage == stageCount - 1) "Start using ChemSearch" else "Next", fontWeight = FontWeight.Bold)
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            TextButton(onClick = onBack, enabled = stage > 0) {
                Text("Back")
            }
            OutlinedButton(
                onClick = onSkip,
                shape = RoundedCornerShape(10.dp),
                contentPadding = PaddingValues(horizontal = 18.dp, vertical = 8.dp)
            ) {
                Text("Skip")
            }
        }
    }
}

@Composable
private fun WelcomeFeatureRow(
    icon: ImageVector,
    title: String,
    description: String
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.18f)),
        tonalElevation = 0.dp
    ) {
        Row(
            modifier = Modifier.padding(13.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            WelcomeIconBox(icon)
            Spacer(Modifier.width(12.dp))
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                Text(title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                Text(
                    description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.62f)
                )
            }
        }
    }
}

@Composable
private fun WelcomeChoiceCard(
    selected: Boolean,
    icon: ImageVector,
    title: String,
    description: String,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Surface(
        onClick = onClick,
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        color = if (selected) MaterialTheme.colorScheme.primary.copy(alpha = 0.1f) else MaterialTheme.colorScheme.surface,
        border = BorderStroke(
            1.dp,
            if (selected) MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)
            else MaterialTheme.colorScheme.outline.copy(alpha = 0.2f)
        )
    ) {
        Row(
            modifier = Modifier.padding(13.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            WelcomeIconBox(icon, selected = selected)
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                Text(
                    description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.64f)
                )
            }
            if (selected) {
                Icon(
                    Icons.Default.Check,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(18.dp)
                )
            }
        }
    }
}

@Composable
private fun WelcomeIconBox(icon: ImageVector, selected: Boolean = false) {
    Surface(
        modifier = Modifier.size(38.dp),
        shape = RoundedCornerShape(10.dp),
        color = if (selected) MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)
        else MaterialTheme.colorScheme.surfaceVariant,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.12f))
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(21.dp)
            )
        }
    }
}

private fun AppColorScheme.label(): String = when (this) {
    AppColorScheme.BLUE -> "Blue"
    AppColorScheme.VIOLET -> "Violet"
    AppColorScheme.EMERALD -> "Emerald"
    AppColorScheme.ROSE -> "Rose"
    AppColorScheme.AMBER -> "Amber"
}

private fun AppColorScheme.previewColor(): Color = when (this) {
    AppColorScheme.BLUE -> Color(0xFF2563EB)
    AppColorScheme.VIOLET -> Color(0xFF7C3AED)
    AppColorScheme.EMERALD -> Color(0xFF059669)
    AppColorScheme.ROSE -> Color(0xFFE11D48)
    AppColorScheme.AMBER -> Color(0xFFD97706)
}
