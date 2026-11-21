package tv.medusa.app.ui.components

import androidx.compose.ui.graphics.Brush
import tv.medusa.app.ui.theme.CinematicTypography
import tv.medusa.app.ui.theme.GoldNitrate
import tv.medusa.app.ui.theme.TextPrimary
import tv.medusa.app.ui.theme.TextSecondary
import tv.medusa.app.ui.theme.LocalAccentColor
import tv.medusa.app.ui.theme.LocalIsMonochromeTheme
import tv.medusa.app.ui.theme.TvAccent
import tv.medusa.app.ui.theme.TvCornerRadius
import tv.medusa.app.ui.theme.TvSpacing
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Border
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import tv.medusa.app.ui.util.handleTouchClick
import tv.medusa.app.R

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun GenreRow(
    genres: List<String>,
    onSelectGenre: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    if (genres.isEmpty()) return

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 10.dp)
    ) {
        val isMonochrome = LocalIsMonochromeTheme.current
        val accentKey = LocalAccentColor.current
        val focusColor = if (isMonochrome) Color.White else TvAccent.getColor(accentKey)

        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = TvSpacing.ScreenSafeHorizontal, vertical = 6.dp)
        ) {
            if (!isMonochrome) {
                Box(
                    modifier = Modifier
                        .size(width = 3.dp, height = 14.dp)
                        .background(
                            brush = Brush.verticalGradient(listOf(GoldNitrate, focusColor)),
                            shape = RoundedCornerShape(1.5.dp)
                        )
                )
                Spacer(modifier = Modifier.width(10.dp))
            }
            Text(
                text = stringResource(R.string.genre_favorites_title),
                style = CinematicTypography.SectionTitle,
                color = TextPrimary
            )
        }

        LazyRow(
            contentPadding = PaddingValues(horizontal = TvSpacing.ScreenSafeHorizontal, vertical = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            items(
                items = genres,
                key = { it },
                contentType = { "genre_chip" }
            ) { genre ->
                GenreChip(
                    genreName = genre,
                    onClick = { onSelectGenre(genre) }
                )
            }
        }
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun GenreChip(
    genreName: String,
    onClick: () -> Unit
) {
    val isMonochrome = LocalIsMonochromeTheme.current
    val accentKey = LocalAccentColor.current
    val focusColor = if (isMonochrome) Color.White else TvAccent.getColor(accentKey)
    val focusContent = if (isMonochrome) Color(0xFF0F172A) else TvAccent.getFocusedContentColor(accentKey)
    var isFocused by remember { mutableStateOf(false) }

    val chipShape = RoundedCornerShape(8.dp)

    Surface(
        onClick = onClick,
        modifier = Modifier
            .onFocusChanged { isFocused = it.isFocused }
            .handleTouchClick { onClick() },
        shape = ClickableSurfaceDefaults.shape(shape = chipShape),
        colors = ClickableSurfaceDefaults.colors(
            containerColor = Color(0xFF11141C),
            focusedContainerColor = focusColor,
            contentColor = TextSecondary,
            focusedContentColor = focusContent
        ),
        border = ClickableSurfaceDefaults.border(
            border = Border(
                border = BorderStroke(0.75.dp, Color(0x22FFFFFF)),
                shape = chipShape
            ),
            focusedBorder = Border(
                border = BorderStroke(1.5.dp, Color.White),
                shape = chipShape
            )
        ),
        scale = ClickableSurfaceDefaults.scale(scale = 1.0f, focusedScale = 1.04f)
    ) {
        Box(
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 11.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = genreName.uppercase(),
                color = if (isFocused) focusContent else TextPrimary,
                fontWeight = FontWeight.Bold,
                fontSize = 11.sp,
                letterSpacing = 1.2.sp
            )
        }
    }
}
