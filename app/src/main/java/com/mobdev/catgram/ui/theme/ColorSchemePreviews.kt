package com.mobdev.catgram.ui.theme

import android.content.res.Configuration
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ModeComment
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Pets
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp

private data class ColorSchemeCandidate(
    val name: String,
    val description: String,
    val light: ColorScheme,
    val dark: ColorScheme,
    val favourite: Color,
)

private val CalicoCream = ColorSchemeCandidate(
    name = "Calico Cream",
    description = "Warm, friendly, distinctive",
    light = lightColorScheme(
        primary = Color(0xFF714B3A),
        onPrimary = Color.White,
        primaryContainer = Color(0xFFFFDBCB),
        onPrimaryContainer = Color(0xFF2A150D),
        secondary = Color(0xFF76536B),
        onSecondary = Color.White,
        secondaryContainer = Color(0xFFFFD7ED),
        onSecondaryContainer = Color(0xFF2D1126),
        tertiary = Color(0xFF316B65),
        onTertiary = Color.White,
        tertiaryContainer = Color(0xFFB5EFE7),
        onTertiaryContainer = Color(0xFF00201D),
        background = Color(0xFFFFF8F3),
        onBackground = Color(0xFF211A17),
        surface = Color(0xFFFFF8F3),
        onSurface = Color(0xFF211A17),
        surfaceVariant = Color(0xFFF1DED6),
        onSurfaceVariant = Color(0xFF50443F),
        outline = Color(0xFF82736C),
    ),
    dark = darkColorScheme(
        primary = Color(0xFFE4BFAE),
        onPrimary = Color(0xFF402014),
        primaryContainer = Color(0xFF573527),
        onPrimaryContainer = Color(0xFFFFDBCB),
        secondary = Color(0xFFE5BAD2),
        onSecondary = Color(0xFF44263B),
        secondaryContainer = Color(0xFF5C3C53),
        onSecondaryContainer = Color(0xFFFFD7ED),
        tertiary = Color(0xFF9DD3CB),
        onTertiary = Color(0xFF003733),
        tertiaryContainer = Color(0xFF14534E),
        onTertiaryContainer = Color(0xFFB5EFE7),
        background = Color(0xFF181210),
        onBackground = Color(0xFFEDDDD7),
        surface = Color(0xFF181210),
        onSurface = Color(0xFFEDDDD7),
        surfaceVariant = Color(0xFF50443F),
        onSurfaceVariant = Color(0xFFD4C2BA),
        outline = Color(0xFF9D8D86),
    ),
    favourite = Color(0xFFE7A526),
)

private val BerryAndSky = ColorSchemeCandidate(
    name = "Berry & Sky",
    description = "A modern evolution of the current theme",
    light = lightColorScheme(
        primary = Color(0xFF5D4A9E),
        onPrimary = Color.White,
        primaryContainer = Color(0xFFE7DEFF),
        onPrimaryContainer = Color(0xFF211059),
        secondary = Color(0xFF41658A),
        onSecondary = Color.White,
        secondaryContainer = Color(0xFFD1E4FF),
        onSecondaryContainer = Color(0xFF001D35),
        tertiary = Color(0xFF9C4D69),
        onTertiary = Color.White,
        tertiaryContainer = Color(0xFFFFD9E2),
        onTertiaryContainer = Color(0xFF3E001F),
        background = Color(0xFFFAF8FF),
        onBackground = Color(0xFF1C1B20),
        surface = Color(0xFFFAF8FF),
        onSurface = Color(0xFF1C1B20),
        surfaceVariant = Color(0xFFE7E0ED),
        onSurfaceVariant = Color(0xFF49454E),
        outline = Color(0xFF7A757F),
    ),
    dark = darkColorScheme(
        primary = Color(0xFFC9BFFF),
        onPrimary = Color(0xFF30206C),
        primaryContainer = Color(0xFF463586),
        onPrimaryContainer = Color(0xFFE7DEFF),
        secondary = Color(0xFFA9C8EE),
        onSecondary = Color(0xFF0D3658),
        secondaryContainer = Color(0xFF294D70),
        onSecondaryContainer = Color(0xFFD1E4FF),
        tertiary = Color(0xFFFFB0C8),
        onTertiary = Color(0xFF5F1138),
        tertiaryContainer = Color(0xFF7E3650),
        onTertiaryContainer = Color(0xFFFFD9E2),
        background = Color(0xFF131318),
        onBackground = Color(0xFFE5E1E9),
        surface = Color(0xFF131318),
        onSurface = Color(0xFFE5E1E9),
        surfaceVariant = Color(0xFF49454E),
        onSurfaceVariant = Color(0xFFCAC4CF),
        outline = Color(0xFF948F99),
    ),
    favourite = Color(0xFFF6B73C),
)

private val SageAndSand = ColorSchemeCandidate(
    name = "Sage & Sand",
    description = "Calm, organic, and soft",
    light = lightColorScheme(
        primary = Color(0xFF3F6758),
        onPrimary = Color.White,
        primaryContainer = Color(0xFFC1EBD9),
        onPrimaryContainer = Color(0xFF002118),
        secondary = Color(0xFF6C5E45),
        onSecondary = Color.White,
        secondaryContainer = Color(0xFFF5E1BE),
        onSecondaryContainer = Color(0xFF251A05),
        tertiary = Color(0xFF7A5978),
        onTertiary = Color.White,
        tertiaryContainer = Color(0xFFFFD7FA),
        onTertiaryContainer = Color(0xFF30132F),
        background = Color(0xFFF7FAF6),
        onBackground = Color(0xFF191C1A),
        surface = Color(0xFFF7FAF6),
        onSurface = Color(0xFF191C1A),
        surfaceVariant = Color(0xFFDDE5DF),
        onSurfaceVariant = Color(0xFF414945),
        outline = Color(0xFF717A74),
    ),
    dark = darkColorScheme(
        primary = Color(0xFFA5D2BF),
        onPrimary = Color(0xFF0A372B),
        primaryContainer = Color(0xFF274F41),
        onPrimaryContainer = Color(0xFFC1EBD9),
        secondary = Color(0xFFD8C5A3),
        onSecondary = Color(0xFF3B2F17),
        secondaryContainer = Color(0xFF53462E),
        onSecondaryContainer = Color(0xFFF5E1BE),
        tertiary = Color(0xFFEAB8E5),
        onTertiary = Color(0xFF482746),
        tertiaryContainer = Color(0xFF613F5F),
        onTertiaryContainer = Color(0xFFFFD7FA),
        background = Color(0xFF101412),
        onBackground = Color(0xFFE0E3E0),
        surface = Color(0xFF101412),
        onSurface = Color(0xFFE0E3E0),
        surfaceVariant = Color(0xFF414945),
        onSurfaceVariant = Color(0xFFC1C9C3),
        outline = Color(0xFF8B938E),
    ),
    favourite = Color(0xFFE5A62D),
)

private val InkAndCoral = ColorSchemeCandidate(
    name = "Ink & Coral",
    description = "Clean, premium, and photo-first",
    light = lightColorScheme(
        primary = Color(0xFF40516C),
        onPrimary = Color.White,
        primaryContainer = Color(0xFFD8E2FF),
        onPrimaryContainer = Color(0xFF001A41),
        secondary = Color(0xFF667085),
        onSecondary = Color.White,
        secondaryContainer = Color(0xFFE1E5EE),
        onSecondaryContainer = Color(0xFF1E2530),
        tertiary = Color(0xFFA44A62),
        onTertiary = Color.White,
        tertiaryContainer = Color(0xFFFFD9E1),
        onTertiaryContainer = Color(0xFF3F001D),
        background = Color(0xFFF6F7F9),
        onBackground = Color(0xFF191C20),
        surface = Color(0xFFF6F7F9),
        onSurface = Color(0xFF191C20),
        surfaceVariant = Color(0xFFE1E2E8),
        onSurfaceVariant = Color(0xFF44474F),
        outline = Color(0xFF74777F),
    ),
    dark = darkColorScheme(
        primary = Color(0xFFBAC7E4),
        onPrimary = Color(0xFF243550),
        primaryContainer = Color(0xFF384963),
        onPrimaryContainer = Color(0xFFD8E2FF),
        secondary = Color(0xFFC5CAD6),
        onSecondary = Color(0xFF303846),
        secondaryContainer = Color(0xFF474F5D),
        onSecondaryContainer = Color(0xFFE1E5EE),
        tertiary = Color(0xFFFFB1C3),
        onTertiary = Color(0xFF611331),
        tertiaryContainer = Color(0xFF84334A),
        onTertiaryContainer = Color(0xFFFFD9E1),
        background = Color(0xFF111318),
        onBackground = Color(0xFFE2E2E9),
        surface = Color(0xFF111318),
        onSurface = Color(0xFFE2E2E9),
        surfaceVariant = Color(0xFF44474F),
        onSurfaceVariant = Color(0xFFC4C6D0),
        outline = Color(0xFF8E9099),
    ),
    favourite = Color(0xFFE8A317),
)

@Preview(
    name = "Light",
    group = "Color scheme candidates",
    widthDp = 390,
    heightDp = 760,
    showBackground = true,
)
@Preview(
    name = "Dark",
    group = "Color scheme candidates",
    widthDp = 390,
    heightDp = 760,
    showBackground = true,
    uiMode = Configuration.UI_MODE_NIGHT_YES,
)
private annotation class ThemeModePreviews

@ThemeModePreviews
@Composable
private fun CalicoCreamPreview() = ColorSchemeCandidatePreview(CalicoCream)

@ThemeModePreviews
@Composable
private fun BerryAndSkyPreview() = ColorSchemeCandidatePreview(BerryAndSky)

@ThemeModePreviews
@Composable
private fun SageAndSandPreview() = ColorSchemeCandidatePreview(SageAndSand)

@ThemeModePreviews
@Composable
private fun InkAndCoralPreview() = ColorSchemeCandidatePreview(InkAndCoral)

@Composable
private fun ColorSchemeCandidatePreview(candidate: ColorSchemeCandidate) {
    MaterialTheme(
        colorScheme = if (isSystemInDarkTheme()) candidate.dark else candidate.light,
        typography = Typography,
    ) {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = MaterialTheme.colorScheme.background,
        ) {
            Column {
                PreviewAppBar()
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Column {
                        Text(
                            text = candidate.name,
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.Bold,
                        )
                        Text(
                            text = candidate.description,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    }
                    PreviewPostCard(candidate.favourite)
                    Text(
                        text = "Recent activity",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                    )
                    PreviewActivityCard()
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(onClick = {}) { Text("Follow") }
                        TextButton(onClick = {}) { Text("View profile") }
                    }
                }
            }
        }
    }
}

@Composable
private fun PreviewAppBar() {
    Surface(tonalElevation = 2.dp) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(64.dp)
                .padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = Icons.Default.Pets,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
            )
            Spacer(Modifier.width(10.dp))
            Text(
                text = "Catgram",
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
            )
            Icon(
                imageVector = Icons.Default.Notifications,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
            )
        }
    }
}

@Composable
private fun PreviewPostCard(favouriteColor: Color) {
    Card(
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer,
        ),
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(34.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primary),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.Default.Pets,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onPrimary,
                    modifier = Modifier.size(20.dp),
                )
            }
            Spacer(Modifier.width(10.dp))
            Column {
                Text("Milo", fontWeight = FontWeight.SemiBold)
                Text(
                    text = "2 minutes ago",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(210.dp)
                .background(MaterialTheme.colorScheme.tertiaryContainer),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = Icons.Default.Pets,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.tertiary,
                modifier = Modifier.size(84.dp),
            )
        }
        Text(
            text = "Found the warmest spot in the house.",
            modifier = Modifier.padding(start = 12.dp, top = 12.dp, end = 12.dp),
            style = MaterialTheme.typography.bodyMedium,
        )
        Row(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TextButton(onClick = {}) {
                Icon(
                    imageVector = Icons.Default.Star,
                    contentDescription = null,
                    tint = favouriteColor,
                )
                Spacer(Modifier.width(4.dp))
                Text("24")
            }
            TextButton(onClick = {}) {
                Icon(imageVector = Icons.Default.ModeComment, contentDescription = null)
                Spacer(Modifier.width(6.dp))
                Text("Comments")
            }
        }
    }
}

@Composable
private fun PreviewActivityCard() {
    Card(
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.secondaryContainer,
        ),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.secondary),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.Default.Pets,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSecondary,
                    modifier = Modifier.size(22.dp),
                )
            }
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text("Luna commented on your post", fontWeight = FontWeight.SemiBold)
                Text(
                    text = "Such a beautiful cat!",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
            Box(
                modifier = Modifier
                    .size(10.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primary),
            )
        }
    }
}
