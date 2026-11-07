package tv.medusa.app.ui.components

import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Brush
import tv.medusa.app.ui.theme.LocalAccentColor
import tv.medusa.app.ui.theme.LocalIsMonochromeTheme
import tv.medusa.app.ui.theme.TvAccent
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
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import tv.medusa.app.domain.model.HomeSection
import tv.medusa.app.domain.model.MediaItem
import tv.medusa.app.ui.theme.TvPill

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun MediaRow(
    section: HomeSection,
    onItemClick: (MediaItem) -> Unit,
    onItemFocus: (MediaItem) -> Unit,
    onItemLongClick: ((MediaItem) -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 12.dp)
    ) {
        val isMonochrome = LocalIsMonochromeTheme.current
        val accentKey = LocalAccentColor.current
        val focusColor = if (isMonochrome) Color.White else TvAccent.getColor(accentKey)

        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = 48.dp, vertical = 8.dp)
        ) {
            val badgeText = localizedSectionBadge(section.badge)
            if (!badgeText.isNullOrBlank()) {
                TvPill(
                    text = badgeText,
                    containerColor = if (isMonochrome) Color(0x18FFFFFF) else focusColor.copy(alpha = 0.16f),
                    textColor = if (isMonochrome) Color(0xFFCBD5E1) else Color(0xFF7DD3FC)
                )
                Spacer(modifier = Modifier.width(10.dp))
            }

            Text(
                text = localizedSectionTitle(section),
                style = MaterialTheme.typography.titleLarge,
                color = Color.White,
                fontWeight = FontWeight.SemiBold
            )
        }

        val uniqueItems = remember(section.items) {
            section.items.distinctBy { it.id }
        }

        val currentOnItemClick by androidx.compose.runtime.rememberUpdatedState(onItemClick)
        val currentOnItemFocus by androidx.compose.runtime.rememberUpdatedState(onItemFocus)
        val currentOnLongClick by androidx.compose.runtime.rememberUpdatedState(onItemLongClick)

        LazyRow(
            contentPadding = PaddingValues(horizontal = 48.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            itemsIndexed(
                items = uniqueItems,
                key = { _, it -> it.id },
                contentType = { _, _ -> "media_card" }
            ) { index, item ->
                val displayItem = remember(item, section.isRanked, index) {
                    if (section.isRanked && item.rank == null) {
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

                if (section.layout == tv.medusa.app.domain.model.HomeSectionLayout.LANDSCAPE) {
                    LandscapeMediaCard(
                        item = displayItem,
                        onClick = onClickLambda,
                        onFocus = onFocusLambda,
                        onLongClick = onLongClickLambda
                    )
                } else {
                    MediaCard(
                        item = displayItem,
                        onClick = onClickLambda,
                        onFocus = onFocusLambda,
                        onLongClick = onLongClickLambda
                    )
                }
            }
        }
    }
}
