package tv.medusa.app.ui.components.sort

import android.view.KeyEvent
import android.view.SoundEffectConstants
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Sort
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.tv.material3.Button
import androidx.tv.material3.ButtonDefaults
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.Icon
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import kotlinx.coroutines.delay
import tv.medusa.app.R
import tv.medusa.app.ui.theme.TvAccent
import tv.medusa.app.ui.util.handleTouchClick

/**
 * Botón y selector elegante de ordenación para vistas de catálogo en cuadrícula ("muro").
 * Sigue al 100% la estética Leanback y Anti-Slop de Medusa TV.
 */
@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun CatalogSortButton(
    currentSort: CatalogSortOption,
    onSortSelected: (CatalogSortOption) -> Unit,
    accentColorKey: String,
    isMonochrome: Boolean,
    focusRequester: FocusRequester? = null,
    modifier: Modifier = Modifier
) {
    var showDialog by remember { mutableStateOf(false) }
    var isFocused by remember { mutableStateOf(false) }
    val defaultButtonFocusRequester = remember { FocusRequester() }
    val effectiveFocusRequester = focusRequester ?: defaultButtonFocusRequester
    var shouldRefocusOnDismiss by remember { mutableStateOf(false) }

    LaunchedEffect(showDialog) {
        if (!showDialog && shouldRefocusOnDismiss) {
            shouldRefocusOnDismiss = false
            delay(50)
            runCatching { effectiveFocusRequester.requestFocus() }
            delay(100)
            if (!isFocused) {
                runCatching { effectiveFocusRequester.requestFocus() }
            }
        }
    }

    val focusColor = if (isMonochrome) Color.White else TvAccent.getColor(accentColorKey)
    val focusContent = if (isMonochrome) tv.medusa.app.ui.theme.TvPalette.FocusInk else TvAccent.getFocusedContentColor(accentColorKey)
    val isCustomSort = currentSort != CatalogSortOption.DEFAULT

    val contentColor = when {
        isFocused -> focusContent
        isCustomSort -> if (isMonochrome) Color.White else focusColor
        else -> tv.medusa.app.ui.theme.TvPalette.TextSoft
    }

    Button(
        onClick = {
            shouldRefocusOnDismiss = true
            showDialog = true
        },
        contentPadding = tv.medusa.app.ui.theme.TvChipDefaults.ContentPadding,
        scale = tv.medusa.app.ui.theme.TvChipDefaults.buttonScale(),
        colors = ButtonDefaults.colors(
            containerColor = if (isCustomSort) {
                if (isMonochrome) Color(0x30FFFFFF) else focusColor.copy(alpha = 0.22f)
            } else tv.medusa.app.ui.theme.TvPalette.GlassIdle,
            contentColor = contentColor,
            focusedContainerColor = focusColor,
            focusedContentColor = focusContent
        ),
        shape = ButtonDefaults.shape(tv.medusa.app.ui.theme.TvChipDefaults.Shape),
        border = tv.medusa.app.ui.theme.TvChipDefaults.buttonBorder(),
        modifier = modifier
            .focusRequester(effectiveFocusRequester)
            .handleTouchClick {
                shouldRefocusOnDismiss = true
                showDialog = true
            }
            .onFocusChanged { isFocused = it.isFocused }
            .height(tv.medusa.app.ui.theme.TvChipDefaults.Height)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            Icon(
                imageVector = Icons.AutoMirrored.Rounded.Sort,
                contentDescription = stringResource(R.string.sort_dialog_title),
                tint = contentColor,
                modifier = Modifier.size(14.dp)
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = stringResource(currentSort.shortLabelRes),
                color = contentColor,
                style = tv.medusa.app.ui.theme.TvChipDefaults.textStyle
            )
            Spacer(modifier = Modifier.width(4.dp))
            Icon(
                imageVector = Icons.Rounded.KeyboardArrowDown,
                contentDescription = null,
                tint = contentColor.copy(alpha = if (isFocused) 1f else 0.75f),
                modifier = Modifier.size(14.dp)
            )
        }
    }

    if (showDialog) {
        CatalogSortDialog(
            currentSort = currentSort,
            onSortSelected = {
                onSortSelected(it)
                showDialog = false
            },
            onDismiss = { showDialog = false },
            accentColorKey = accentColorKey,
            isMonochrome = isMonochrome
        )
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun CatalogSortDialog(
    currentSort: CatalogSortOption,
    onSortSelected: (CatalogSortOption) -> Unit,
    onDismiss: () -> Unit,
    accentColorKey: String,
    isMonochrome: Boolean
) {
    val view = LocalView.current
    val focusColor = if (isMonochrome) Color.White else TvAccent.getColor(accentColorKey)
    val focusContent = if (isMonochrome) tv.medusa.app.ui.theme.TvPalette.FocusInk else TvAccent.getFocusedContentColor(accentColorKey)
    val selectedFocusRequester = remember { FocusRequester() }

    LaunchedEffect(Unit) {
        delay(60)
        runCatching {
            selectedFocusRequester.requestFocus()
        }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            dismissOnBackPress = true,
            dismissOnClickOutside = true,
            usePlatformDefaultWidth = false
        )
    ) {
        BackHandler {
            onDismiss()
        }

        Box(
            modifier = Modifier
                .fillMaxSize()
                .onPreviewKeyEvent { keyEvent ->
                    val native = keyEvent.nativeKeyEvent
                    if (native.keyCode == KeyEvent.KEYCODE_BACK || native.keyCode == KeyEvent.KEYCODE_ESCAPE) {
                        if (native.action == KeyEvent.ACTION_UP) {
                            onDismiss()
                        }
                        return@onPreviewKeyEvent true
                    }
                    false
                }
                .background(Color(0xCC06080E))
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null
                ) { onDismiss() },
            contentAlignment = Alignment.Center
        ) {
            Box(
                modifier = Modifier
                    .width(420.dp)
                    .clip(RoundedCornerShape(18.dp))
                    .background(tv.medusa.app.ui.theme.AbyssVoid.copy(alpha = 0.95f))
                    .border(
                        border = BorderStroke(0.5.dp, tv.medusa.app.ui.theme.BorderSubtle),
                        shape = RoundedCornerShape(18.dp)
                    )
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null
                    ) { /* Consumir clicks en la tarjeta */ }
                    .padding(24.dp)
            ) {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    // Cabecera elegante
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Rounded.Sort,
                            contentDescription = null,
                            tint = focusColor,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Column {
                            Text(
                                text = stringResource(R.string.sort_dialog_title),
                                style = MaterialTheme.typography.titleMedium,
                                color = Color.White,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = stringResource(R.string.sort_dialog_subtitle),
                                style = MaterialTheme.typography.bodySmall,
                                color = tv.medusa.app.ui.theme.TvPalette.TextSecondaryCool
                            )
                        }
                    }

                    // Opciones de ordenación
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        CatalogSortOption.entries.forEach { option ->
                            val isSelected = option == currentSort
                            var isItemFocused by remember { mutableStateOf(false) }

                            val itemContentColor = when {
                                isItemFocused -> focusContent
                                isSelected -> if (isMonochrome) Color.White else focusColor
                                else -> tv.medusa.app.ui.theme.TvPalette.TextSoft
                            }

                            Button(
                                onClick = {
                                    view.playSoundEffect(SoundEffectConstants.CLICK)
                                    onSortSelected(option)
                                },
                                contentPadding = PaddingValues(horizontal = 14.dp, vertical = 10.dp),
                                scale = ButtonDefaults.scale(scale = 1.0f, focusedScale = 1.0f),
                                colors = ButtonDefaults.colors(
                                    containerColor = if (isSelected) (if (isMonochrome) tv.medusa.app.ui.theme.TvPalette.GlassSelected else focusColor.copy(alpha = 0.18f)) else Color(0x10FFFFFF),
                                    contentColor = itemContentColor,
                                    focusedContainerColor = focusColor,
                                    focusedContentColor = focusContent
                                ),
                                border = ButtonDefaults.border(
                                    border = androidx.tv.material3.Border.None,
                                    focusedBorder = androidx.tv.material3.Border.None
                                ),
                                shape = ButtonDefaults.shape(RoundedCornerShape(10.dp)),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .onFocusChanged { isItemFocused = it.isFocused }
                                    .handleTouchClick {
                                        view.playSoundEffect(SoundEffectConstants.CLICK)
                                        onSortSelected(option)
                                    }
                                    .then(if (isSelected) Modifier.focusRequester(selectedFocusRequester) else Modifier)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text(
                                        text = stringResource(option.labelRes),
                                        fontSize = 13.sp,
                                        fontWeight = if (isSelected || isItemFocused) FontWeight.Bold else FontWeight.Medium,
                                        color = itemContentColor
                                    )
                                    if (isSelected) {
                                        Icon(
                                            imageVector = Icons.Rounded.Check,
                                            contentDescription = null,
                                            tint = if (isItemFocused) focusContent else (if (isMonochrome) Color.White else focusColor),
                                            modifier = Modifier.size(16.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
