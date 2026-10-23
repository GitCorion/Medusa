package tv.medusa.app.ui.components

import androidx.compose.ui.graphics.Brush
import tv.medusa.app.ui.theme.LocalAccentColor
import tv.medusa.app.ui.theme.LocalIsMonochromeTheme
import tv.medusa.app.ui.theme.TvAccent
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
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
        val accentColor = TvAccent.getColor(accentKey)

        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = 48.dp, vertical = 6.dp)
        ) {
            if (!isMonochrome) {
                Box(
                    modifier = Modifier
                        .width(3.5.dp)
                        .height(18.dp)
                        .background(
                            brush = Brush.verticalGradient(
                                listOf(
                                    accentColor,
                                    accentColor.copy(alpha = 0.35f)
                                )
                            ),
                            shape = RoundedCornerShape(2.dp)
                        )
                )
                Spacer(modifier = Modifier.width(10.dp))
            }

            if (isMonochrome) {
                Text(
                    text = stringResource(R.string.genre_favorites_title),
                    style = MaterialTheme.typography.titleLarge,
                    color = Color.White,
                    fontWeight = FontWeight.Bold
                )
            } else {
                Text(
                    text = stringResource(R.string.genre_favorites_title),
                    style = MaterialTheme.typography.titleLarge.copy(
                        brush = Brush.horizontalGradient(
                            listOf(
                                Color(0xFFFFFFFF),
                                Color(0xFFE2E8F0),
                                accentColor.copy(alpha = 0.85f)
                            )
                        )
                    ),
                    fontWeight = FontWeight.Bold
                )
            }
        }

        LazyRow(
            contentPadding = PaddingValues(horizontal = 48.dp, vertical = 6.dp),
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
    val accentColor = TvAccent.getColor(accentKey)
    val focusContent = if (isMonochrome) Color(0xFF0F172A) else TvAccent.getFocusedContentColor(accentKey)

    Surface(
        onClick = onClick,
        shape = ClickableSurfaceDefaults.shape(shape = RoundedCornerShape(10.dp)),
        colors = ClickableSurfaceDefaults.colors(
            containerColor = Color(0x10FFFFFF),
            focusedContainerColor = if (isMonochrome) Color.White else accentColor,
            contentColor = Color(0xFFF1F5F9),
            focusedContentColor = focusContent
        ),
        border = ClickableSurfaceDefaults.border(
            border = Border(border = BorderStroke(0.75.dp, Color(0x18FFFFFF))),
            focusedBorder = Border.None
        ),
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1.0f)
    ) {
        Box(
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = genreName,
                fontWeight = FontWeight.SemiBold,
                fontSize = 13.sp,
                letterSpacing = 0.5.sp
            )
        }
    }
}
