package tv.medusa.app.ui.components

import androidx.compose.ui.graphics.Brush
import tv.medusa.app.ui.theme.CinematicTypography
import tv.medusa.app.ui.theme.TextPrimary
import tv.medusa.app.ui.theme.TextSecondary
import tv.medusa.app.ui.theme.LocalAccentColor
import tv.medusa.app.ui.theme.LocalIsMonochromeTheme
import tv.medusa.app.ui.theme.TvAccent
import tv.medusa.app.ui.theme.TvChipDefaults
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
        val focusColor = if (isMonochrome) TextPrimary else TvAccent.getColor(accentKey)

        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = TvSpacing.ScreenSafeHorizontal, vertical = 6.dp)
        ) {
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
    val focusColor = if (isMonochrome) TextPrimary else TvAccent.getColor(accentKey)
    val focusContent = if (isMonochrome) tv.medusa.app.ui.theme.TvPalette.FocusInk else TvAccent.getFocusedContentColor(accentKey)

    Surface(
        onClick = onClick,
        modifier = Modifier.handleTouchClick { onClick() },
        shape = ClickableSurfaceDefaults.shape(shape = TvChipDefaults.Shape),
        scale = TvChipDefaults.scale(),
        colors = TvChipDefaults.colors(
            isSelected = false,
            isMonochrome = isMonochrome,
            focusColor = focusColor,
            focusContent = focusContent
        ),
        border = TvChipDefaults.border()
    ) {
        Box(
            modifier = Modifier
                .height(TvChipDefaults.Height)
                .padding(horizontal = TvChipDefaults.HorizontalPadding),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = genreName,
                style = TvChipDefaults.textStyle.copy(
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    letterSpacing = 0.2.sp
                )
            )
        }
    }
}
