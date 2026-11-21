package tv.medusa.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import tv.medusa.app.domain.model.HomeSection
import tv.medusa.app.domain.model.HomeSectionKind
import tv.medusa.app.domain.model.HomeSectionLayout
import tv.medusa.app.domain.model.MediaItem
import tv.medusa.app.ui.theme.CinematicTypography
import tv.medusa.app.ui.theme.TextPrimary
import tv.medusa.app.ui.theme.LocalAccentColor
import tv.medusa.app.ui.theme.LocalIsMonochromeTheme
import tv.medusa.app.ui.theme.TvAccent
import tv.medusa.app.ui.theme.TvPill
import tv.medusa.app.ui.theme.TvSpacing

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun MediaRow(
    section: HomeSection,
    onItemClick: (MediaItem) -> Unit,
    onItemFocus: (MediaItem) -> Unit,
    onItemLongClick: ((MediaItem) -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    var isRowFocused by remember { mutableStateOf(false) }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(bottom = TvSpacing.ShelfBottomPadding)
    ) {
        val isMonochrome = LocalIsMonochromeTheme.current
        val accentKey = LocalAccentColor.current
        val focusColor = if (isMonochrome) Color.White else TvAccent.getColor(accentKey)

        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = TvSpacing.ScreenSafeHorizontal)
        ) {
            val badgeText = localizedSectionBadge(section.badge)
            if (!badgeText.isNullOrBlank()) {
                tv.medusa.app.ui.components.cinema.CinemaFormatBadge(
                    text = badgeText,
                    isHighlighted = !isMonochrome,
                    highlightColor = focusColor
                )
                Spacer(modifier = Modifier.width(10.dp))
            }

            Text(
                text = localizedSectionTitle(section),
                style = CinematicTypography.SectionTitle,
                color = TextPrimary
            )
        }

        val uniqueItems = remember(section.items) {
            section.items.distinctBy { it.id }
        }

        val currentOnItemClick by rememberUpdatedState(onItemClick)
        val currentOnItemFocus by rememberUpdatedState(onItemFocus)
        val currentOnLongClick by rememberUpdatedState(onItemLongClick)

        val isContinueWatching = section.kind == HomeSectionKind.CONTINUE_WATCHING ||
                section.title.equals("Continuar Viendo", ignoreCase = true)

        val isRankedSection = section.isRanked ||
                section.layout == HomeSectionLayout.RANKED ||
                section.kind == HomeSectionKind.TRENDING ||
                section.title.contains("Top 10", ignoreCase = true)

        val isSagasSection = section.layout == HomeSectionLayout.LANDSCAPE ||
                section.kind == HomeSectionKind.FRANCHISES ||
                section.title.contains("Sagas", ignoreCase = true) ||
                section.title.contains("Coleccion", ignoreCase = true)

        LazyRow(
            contentPadding = PaddingValues(
                start = TvSpacing.ScreenSafeHorizontal,
                end = TvSpacing.ScreenSafeHorizontal,
                top = TvSpacing.TitleToShelfItems,
                bottom = 24.dp
            ),
            horizontalArrangement = Arrangement.spacedBy(TvSpacing.ShelfItemSpacing),
            modifier = Modifier
                .fillMaxWidth()
                .onFocusChanged { isRowFocused = it.hasFocus }
        ) {
            itemsIndexed(
                items = uniqueItems,
                key = { _, it -> it.id },
                contentType = { _, _ ->
                    when {
                        isContinueWatching -> "continue_watching_card"
                        isRankedSection -> "ranked_media_card"
                        isSagasSection -> "landscape_media_card"
                        else -> "media_card"
                    }
                }
            ) { index, item ->
                val displayItem = remember(item, isRankedSection, index) {
                    if (isRankedSection && item.rank == null) {
                        item.copy(rank = index + 1)
                    } else {
                        item
                    }
                }
                val view = androidx.compose.ui.platform.LocalView.current
                val onClickLambda = remember(displayItem.id) { { currentOnItemClick(displayItem) } }
                val onFocusLambda = remember(displayItem.id) { { currentOnItemFocus(displayItem) } }
                val onLongClickLambda: (() -> Unit)? = remember(displayItem.id, currentOnLongClick) {
                    if (currentOnLongClick != null) {
                        {
                            view.playSoundEffect(android.view.SoundEffectConstants.CLICK)
                            currentOnLongClick?.invoke(displayItem)
                        }
                    } else null
                }

                when {
                    isContinueWatching -> {
                        ContinueWatchingCard(
                            item = displayItem,
                            onClick = onClickLambda,
                            onFocus = onFocusLambda,
                            onLongClick = onLongClickLambda,
                            isRowFocused = isRowFocused
                        )
                    }
                    isRankedSection -> {
                        RankedMediaCard(
                            item = displayItem,
                            rank = displayItem.rank ?: (index + 1),
                            onClick = onClickLambda,
                            onFocus = onFocusLambda,
                            onLongClick = onLongClickLambda,
                            isRowFocused = isRowFocused
                        )
                    }
                    isSagasSection -> {
                        LandscapeMediaCard(
                            item = displayItem,
                            onClick = onClickLambda,
                            onFocus = onFocusLambda,
                            onLongClick = onLongClickLambda,
                            isRowFocused = isRowFocused
                        )
                    }
                    else -> {
                        MediaCard(
                            item = displayItem,
                            onClick = onClickLambda,
                            onFocus = onFocusLambda,
                            onLongClick = onLongClickLambda,
                            isRowFocused = isRowFocused
                        )
                    }
                }
            }
        }
    }
}
