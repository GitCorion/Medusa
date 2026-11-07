package tv.medusa.app.ui.components

import androidx.compose.ui.text.font.FontWeight
import androidx.tv.material3.Text
import tv.medusa.app.data.local.INDICATOR_THEME_MONOCHROME
import tv.medusa.app.data.local.PLATFORM_LOGO_COLOR
import tv.medusa.app.data.local.PLATFORM_LOGO_MONOCHROME
import tv.medusa.app.data.model.StreamPlatform
import tv.medusa.app.ui.theme.LocalAccentColor
import tv.medusa.app.ui.theme.LocalIndicatorTheme
import tv.medusa.app.ui.theme.LocalIsMonochromeTheme
import tv.medusa.app.ui.theme.LocalPlatformLogoStyle
import tv.medusa.app.ui.theme.TvAccent
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Card
import androidx.tv.material3.CardDefaults
import androidx.tv.material3.ExperimentalTvMaterial3Api
import tv.medusa.app.ui.util.handleTouchClick
import tv.medusa.app.R
import tv.medusa.app.data.model.SUPPORTED_PLATFORMS

@Composable
fun BrandTileRow(
    onSelectBrand: (String) -> Unit,
    modifier: Modifier = Modifier,
    visiblePlatforms: Set<String>? = null,
    watchRegion: String? = null,
    isProUser: Boolean = true
) {
    val platforms = remember(visiblePlatforms, watchRegion) {
        val base = if (visiblePlatforms != null) {
            SUPPORTED_PLATFORMS.filter { it.id in visiblePlatforms }
        } else {
            SUPPORTED_PLATFORMS
        }
        val region = watchRegion?.takeIf { it.isNotBlank() } ?: tv.medusa.app.data.remote.ApiConfig.TMDB_WATCH_REGION
        base.filter { it.isAvailableIn(region) }
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 12.dp)
    ) {
        LazyRow(
            modifier = Modifier.fillMaxWidth(),
            contentPadding = PaddingValues(horizontal = 48.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterHorizontally)
        ) {
            items(
                items = platforms,
                key = { it.id },
                contentType = { "brand_card" }
            ) { platform ->
                BrandCard(
                    brand = platform,
                    isLocked = !isProUser,
                    onClick = { onSelectBrand(platform.id) }
                )
            }
        }
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun BrandCard(
    brand: StreamPlatform,
    isLocked: Boolean = false,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    var isFocused by remember { mutableStateOf(false) }
    val logoStyle = LocalPlatformLogoStyle.current
    val indicatorTheme = LocalIndicatorTheme.current
    val isMonochrome = LocalIsMonochromeTheme.current ||
            logoStyle == PLATFORM_LOGO_MONOCHROME ||
            (logoStyle == PLATFORM_LOGO_COLOR && indicatorTheme == INDICATOR_THEME_MONOCHROME)

    val iconRes = if (isMonochrome) brand.iconMonoRes else brand.iconRes

    val accentKey = LocalAccentColor.current
    val accentColor = TvAccent.getColor(accentKey)
    val focusColor = if (isMonochrome) Color.White else accentColor

    Card(
        onClick = onClick,
        colors = CardDefaults.colors(
            containerColor = Color(0xFF0C1017),
            focusedContainerColor = Color(0xFF141B26)
        ),
        scale = CardDefaults.scale(focusedScale = 1.08f),
        modifier = modifier
            .handleTouchClick { onClick() }
            .width(132.dp)
            .height(64.dp)
            .onFocusChanged { isFocused = it.isFocused }
            .clip(RoundedCornerShape(10.dp))
            .then(
                if (isFocused) {
                    Modifier.border(2.dp, focusColor, RoundedCornerShape(10.dp))
                } else Modifier
            )
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(if (isFocused) Color(0xFF181F2C) else Color(0xFF0C1017))
                .padding(horizontal = 14.dp, vertical = 8.dp)
        ) {
            Image(
                painter = painterResource(id = iconRes),
                contentDescription = brand.name,
                contentScale = ContentScale.Fit,
                modifier = Modifier
                    .fillMaxSize()
                    .align(Alignment.Center)
            )

            if (isLocked) {
                Box(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .background(Color(0xE6D97706), RoundedCornerShape(4.dp))
                        .padding(horizontal = 5.dp, vertical = 2.dp)
                ) {
                    Text(
                        text = stringResource(R.string.pro_badge),
                        color = Color.White,
                        fontSize = 8.sp,
                        fontWeight = FontWeight.Black
                    )
                }
            }
        }
    }
}
