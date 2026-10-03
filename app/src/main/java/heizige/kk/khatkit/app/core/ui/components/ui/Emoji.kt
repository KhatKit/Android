package heizige.kk.khatkit.app.core.ui.components.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import heizige.kk.khatkit.app.core.ui.context.NoHeroTransition
import heizige.kk.kedge.components.KedgeCard
import heizige.kk.kedge.components.KedgeSearchBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import kotlinx.coroutines.launch
import heizige.kk.khatkit.app.R
import heizige.kk.khatkit.app.core.util.Emoji
import heizige.kk.khatkit.app.core.util.EmojiCategory
import heizige.kk.khatkit.app.core.util.EmojiData
import heizige.kk.khatkit.app.core.di.rememberAppEntryPoint
import heizige.kk.kedge.theme.KedgeTextStyles
import heizige.kk.kedge.components.KedgeSurface

@Preview
@Composable
fun EmojiPicker(
    modifier: Modifier = Modifier,
    onEmojiSelected: (Emoji) -> Unit = {},
    showSearch: Boolean = true,
    height: Int = 400
) {
    val emojiData = rememberAppEntryPoint().emojiData()
    var searchQuery by remember { mutableStateOf("") }
    var searchExpanded by remember { mutableStateOf(false) }
    var selectedCategoryIndex by remember { mutableIntStateOf(0) }
    var showModifierPicker by remember { mutableStateOf(false) }
    var selectedEmojiForModifier by remember { mutableStateOf<Emoji?>(null) }
    var modifierVariants by remember { mutableStateOf<List<Emoji>>(emptyList()) }

    val lazyListState: LazyGridState = rememberLazyGridState()
    val coroutineScope = rememberCoroutineScope()

    emojiData.let { data ->
        Box(modifier = modifier) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(height.dp)
                    .background(
                        MaterialTheme.colorScheme.surface,
                        RoundedCornerShape(12.dp)
                    )
                    .padding(8.dp)
            ) {
                // KSU 式药丸搜索框（KernelSU SuperSearchBar 同一套逻辑）：点一下折叠药丸，
                // 展开成铺满全屏的搜索页；取消键/返回键收起，关键词同时清空。
                // 展开期间分类栏与表情网格整个抽掉（KSU 的 SearchBox 同款），
                // 只在搜索框的全屏槽里出现一次。
                val emojiVariants = remember(selectedCategoryIndex, data) {
                    data.categories[selectedCategoryIndex].getEmojiVariants()
                }
                val filteredEmojiVariants = remember(searchQuery, emojiVariants) {
                    if (searchQuery.isBlank()) {
                        emojiVariants
                    } else {
                        emojiVariants.filter { (_, variants) ->
                            variants.any { emoji ->
                                emoji.name.contains(searchQuery, ignoreCase = true) ||
                                    emoji.emoji.contains(searchQuery)
                            }
                        }
                    }
                }
                val emojiGrid: @Composable (Modifier) -> Unit = { gridModifier ->
                    EmojiGrid(
                        modifier = gridModifier,
                        variants = filteredEmojiVariants,
                        gridState = lazyListState,
                        onEmojiSelected = onEmojiSelected,
                        onRequestModifierPicker = { baseEmoji, variants ->
                            selectedEmojiForModifier = baseEmoji
                            modifierVariants = variants
                            showModifierPicker = true
                        },
                    )
                }
                val categoryRow: @Composable () -> Unit = {
                    EmojiCategoryRow(
                        categories = data.categories,
                        selectedCategoryIndex = selectedCategoryIndex,
                        gridState = lazyListState,
                        onSelect = { selectedCategoryIndex = it },
                    )
                }

                if (showSearch) {
                    KedgeSearchBar(
                        value = searchQuery,
                        onValueChange = { searchQuery = it },
                        placeholder = stringResource(R.string.emoji_picker_search_placeholder),
                        active = searchExpanded,
                        onActiveChange = { searchExpanded = it },
                        cancelLabel = stringResource(R.string.cancel),
                        modifier = Modifier.fillMaxWidth(),
                        expandedContent = {
                            // 独立窗口里不能有 sharedElement，否则 lookahead 配对跨 ViewRoot 会崩
                            NoHeroTransition {
                                Column(modifier = Modifier.fillMaxSize()) {
                                    categoryRow()
                                    emojiGrid(Modifier.fillMaxSize())
                                }
                            }
                        },
                    )
                }

                if (!searchExpanded) {
                    // Category tabs
                    categoryRow()

                    // Emoji grid
                    emojiGrid(Modifier.fillMaxSize())
                }
            }

            // Modifier picker popup
            if (showModifierPicker && selectedEmojiForModifier != null) {
                EmojiModifierPicker(
                    variants = modifierVariants,
                    onEmojiSelected = { emoji ->
                        onEmojiSelected(emoji)
                        showModifierPicker = false
                        selectedEmojiForModifier = null
                    },
                    onDismiss = {
                        showModifierPicker = false
                        selectedEmojiForModifier = null
                    }
                )
            }
        }
    }
}

/**
 * 表情分类栏。折叠态与搜索展开态共用一份实现，只有宿主容器不同。
 */
@Composable
private fun EmojiCategoryRow(
    categories: List<EmojiCategory>,
    selectedCategoryIndex: Int,
    gridState: LazyGridState,
    onSelect: (Int) -> Unit,
) {
    val coroutineScope = rememberCoroutineScope()
    LazyRow(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        items(categories.size) { index ->
            val category = categories[index]
            val isSelected = selectedCategoryIndex == index

            KedgeCard(
                modifier = Modifier
                    .clickable {
                        onSelect(index)
                        coroutineScope.launch { gridState.animateScrollToItem(0) }
                    }
                    .clip(RoundedCornerShape(20.dp)),
                colors = CardDefaults.cardColors(
                    containerColor = if (isSelected) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.surfaceVariant
                    }
                )
            ) {
                Text(
                    text = when (category.name) {
                        "Smileys & Emotion" -> "\uD83D\uDE03"
                        "People & Body" -> "\uD83D\uDC64"
                        "Component" -> "\uD83E\uDDF4"
                        "Animals & Nature" -> "\uD83C\uDC3B"
                        "Food & Drink" -> "\uD83C\uDF5B"
                        "Travel & Places" -> "\uD83C\uDF04"
                        "Activities" -> "\uD83C\uDFA3"
                        "Objects" -> "\uD83D\uDCBB"
                        "Symbols" -> "\uD83C\uDF00"
                        "Flags" -> "\uD83D\uDEA9"
                        else -> category.name
                    },
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                    color = if (isSelected) {
                        MaterialTheme.colorScheme.onPrimary
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                    style = KedgeTextStyles.body(),
                )
            }
        }
    }
}

/**
 * 表情网格。折叠态与搜索展开态共用一份实现，只有 [modifier] 不同。
 */
@Composable
private fun EmojiGrid(
    variants: Map<Emoji, List<Emoji>>,
    gridState: LazyGridState,
    onEmojiSelected: (Emoji) -> Unit,
    onRequestModifierPicker: (Emoji, List<Emoji>) -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyVerticalGrid(
        columns = GridCells.Adaptive(minSize = 40.dp),
        state = gridState,
        modifier = modifier,
        contentPadding = PaddingValues(4.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        items(variants.toList(), key = { it.first.emoji }) { (baseEmoji, emojiGroup) ->
            EmojiItem(
                emoji = baseEmoji,
                hasVariants = emojiGroup.size > 1,
                onClick = { onEmojiSelected(baseEmoji) },
                onLongClick = if (emojiGroup.size > 1) {
                    { onRequestModifierPicker(baseEmoji, emojiGroup) }
                } else null
            )
        }
    }
}

@Composable
private fun EmojiItem(
    emoji: Emoji,
    hasVariants: Boolean = false,
    onClick: () -> Unit,
    onLongClick: (() -> Unit)? = null
) {
    Box(
        modifier = Modifier
            .size(40.dp)
            .combinedClickable(
                onClick = onClick,
                onLongClick = onLongClick
            )
            .clip(RoundedCornerShape(8.dp))
            .background(
                MaterialTheme.colorScheme.surfaceVariant.copy(
                    alpha = if (hasVariants) 0.5f else 0.3f
                )
            ),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = emoji.emoji,
            fontSize = 20.sp,
            textAlign = TextAlign.Center
        )
    }
}

@Composable
private fun EmojiModifierPicker(
    variants: List<Emoji>,
    onEmojiSelected: (Emoji) -> Unit,
    onDismiss: () -> Unit
) {
    Popup(
        onDismissRequest = onDismiss,
        properties = PopupProperties(
            focusable = true,
            dismissOnBackPress = true,
            dismissOnClickOutside = true
        )
    ) {
        KedgeSurface(
            modifier = Modifier
                .wrapContentSize()
                .padding(16.dp),
            shape = RoundedCornerShape(12.dp),
            color = MaterialTheme.colorScheme.surface,
            shadowElevation = 8.dp,
            tonalElevation = 8.dp
        ) {
            Column(
                modifier = Modifier.padding(16.dp)
            ) {
                Text(
                    text = stringResource(R.string.emoji_picker_select_skin_tone),
                    style = KedgeTextStyles.title(),
                    modifier = Modifier.padding(bottom = 12.dp)
                )

                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(variants) { variant ->
                        Box(
                            modifier = Modifier
                                .size(48.dp)
                                .clickable { onEmojiSelected(variant) }
                                .clip(RoundedCornerShape(8.dp))
                                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = variant.emoji,
                                fontSize = 24.sp,
                                textAlign = TextAlign.Center
                            )
                        }
                    }
                }
            }
        }
    }
}
