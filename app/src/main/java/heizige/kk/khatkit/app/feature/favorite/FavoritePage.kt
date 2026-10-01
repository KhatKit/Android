package heizige.kk.khatkit.app.feature.favorite

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.LargeFlexibleTopAppBar
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.launch
import heizige.kk.khatkit.app.R
import heizige.kk.khatkit.app.core.ui.components.nav.BackButton
import heizige.kk.khatkit.app.core.ui.context.LocalNavController
import heizige.kk.khatkit.app.core.ui.theme.CustomColors
import heizige.kk.khatkit.app.core.util.navigateToChatPage
import heizige.kk.khatkit.app.core.util.plus
import heizige.kk.khatkit.app.core.util.toLocalDateTime
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import java.time.Instant
import heizige.kk.khatkit.app.core.ui.icons.delete
import heizige.kk.khatkit.app.core.ui.components.ui.miuix.KedgeSettingsPageScaffold
import heizige.kk.kedge.overlays.rememberKedgeSnackbarHostState
import heizige.kk.kedge.overlays.KedgeSnackbarHost
import heizige.kk.kedge.overlays.KedgeSnackbarResult
import heizige.kk.kedge.components.KedgeCard
import heizige.kk.kedge.theme.KedgeTextStyles

@Composable
fun FavoritePage(vm: FavoriteVM = hiltViewModel()) {
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    val navController = LocalNavController.current
    val scope = rememberCoroutineScope()
    val snackbarHostState = rememberKedgeSnackbarHostState()
    val favorites = vm.nodeFavorites.collectAsStateWithLifecycle().value
    val favoriteRemovedText = stringResource(R.string.favorite_page_removed)
    val undoText = stringResource(R.string.history_page_undo)

    Box {
        KedgeSettingsPageScaffold(
            title = stringResource(R.string.favorite_page_title),
            scrollBehavior = scrollBehavior,
        ) { innerPadding ->
        if (favorites.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = stringResource(R.string.favorite_page_no_favorites),
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.65f),
                )
            }
            return@KedgeSettingsPageScaffold
        }

        LazyColumn(
            contentPadding = innerPadding + PaddingValues(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.fillMaxSize(),
        ) {
            items(favorites, key = { it.id }) { item ->
                SwipeableFavoriteCard(
                    item = item,
                    onClick = { navigateToChatPage(navController, item.conversationId, nodeId = item.nodeId) },
                    onDelete = {
                        scope.launch {
                            val entity = vm.getEntityByRefKey(item.refKey) ?: return@launch
                            vm.removeFavorite(item.refKey)
                            val result = snackbarHostState.showSnackbar(
                                message = favoriteRemovedText,
                                actionLabel = undoText,
                                withDismissAction = true,
                            )
                            if (result == KedgeSnackbarResult.ActionPerformed) {
                                vm.restoreFavorite(entity)
                            }
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp)
                        .animateItem(),
                )
            }
        }
    }

        KedgeSnackbarHost(state = snackbarHostState)
    }
}

@Composable
private fun SwipeableFavoriteCard(
    item: NodeFavoriteListItem,
    onClick: () -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val dismissState = rememberSwipeToDismissBoxState(
        initialValue = SwipeToDismissBoxValue.Settled,
    )

    // Key on settledValue: currentValue flips to the closest anchor mid-animation,
    // which would restart this effect and cancel it before onDelete() runs.
    LaunchedEffect(dismissState.settledValue) {
        if (dismissState.settledValue == SwipeToDismissBoxValue.EndToStart) {
            onDelete()
        }
    }

    SwipeToDismissBox(
        state = dismissState,
        backgroundContent = {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        MaterialTheme.colorScheme.errorContainer,
                        RoundedCornerShape(12.dp)
                    )
                    .padding(horizontal = 20.dp),
                contentAlignment = Alignment.CenterEnd,
            ) {
                Icon(
                    imageVector = delete,
                    contentDescription = stringResource(R.string.assistant_page_remove),
                    tint = MaterialTheme.colorScheme.onErrorContainer,
                )
            }
        },
        enableDismissFromStartToEnd = false,
        modifier = modifier,
    ) {
        FavoriteCard(
            item = item,
            onClick = onClick,
        )
    }
}

@Composable
private fun FavoriteCard(
    item: NodeFavoriteListItem,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    KedgeCard(
        onClick = onClick,
        modifier = modifier.fillMaxWidth(),
        ) {
        SelectionContainer {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Text(
                    text = item.conversationTitle.ifBlank { stringResource(R.string.favorite_page_untitled_conversation) },
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    style = KedgeTextStyles.title(),
                )
                val dateText = Instant.ofEpochMilli(item.createdAt).toLocalDateTime()
                Text(
                    text = item.preview,
                    maxLines = 4,
                    overflow = TextOverflow.Ellipsis,
                    style = KedgeTextStyles.body(),
                )
                Text(
                    text = dateText,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    style = KedgeTextStyles.body(),
                    color = MaterialTheme.colorScheme.outline,
                )
            }
        }
    }
}
