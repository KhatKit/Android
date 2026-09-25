package heizige.kk.khatkit.app.feature.chat

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.paging.compose.collectAsLazyPagingItems
import heizige.kk.khatkit.app.R
import heizige.kk.khatkit.app.Screen
import heizige.kk.khatkit.app.core.ui.context.LocalNavController
import heizige.kk.khatkit.app.core.ui.icons.arrowBack

/**
 * 文件夹二级页：顶栏显示文件夹名，左侧为返回箭头；内容为该文件夹下的会话列表。
 */
@Composable
fun FolderDetailPage(
    folderId: String,
    vm: FolderDetailViewModel = hiltViewModel<FolderDetailViewModel, FolderDetailViewModel.Factory>(
        creationCallback = { it.create(folderId) },
    ),
) {
    val navController = LocalNavController.current
    val folder by vm.folder.collectAsStateWithLifecycle()
    val conversations = vm.conversations.collectAsLazyPagingItems()

    Scaffold(
        topBar = {
            TopAppBar(
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainer,
                ),
                navigationIcon = {
                    IconButton(
                        onClick = { navController.popBackStack() },
                        shapes = IconButtonDefaults.shapes(),
                    ) {
                        Icon(arrowBack, contentDescription = stringResource(R.string.back))
                    }
                },
                title = { Text(folder?.name.orEmpty()) },
            )
        },
        containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(8.dp),
        ) {
            ConversationList(
                conversations = conversations,
                currentId = null,
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                onClick = { conversation ->
                    navController.navigate(Screen.Chat(id = conversation.id.toString()))
                },
                onDelete = { vm.deleteConversation(it) },
                onRegenerateTitle = { vm.generateTitle(it) },
                onPin = { vm.togglePinned(it) },
                onRemoveFromFolder = { vm.removeFromFolder(it) },
            )
        }
    }
}
