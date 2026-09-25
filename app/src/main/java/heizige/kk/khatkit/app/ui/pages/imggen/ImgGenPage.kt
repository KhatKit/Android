package heizige.kk.khatkit.app.ui.pages.imggen

import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import heizige.kk.khatkit.app.ui.components.ui.AppAlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Card
import androidx.compose.material3.CircularWavyProgressIndicator
import androidx.compose.material3.ContainedLoadingIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import heizige.kk.khatkit.app.ui.components.ui.KedgePageTopBar
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.paging.compose.collectAsLazyPagingItems
import androidx.paging.compose.itemContentType
import androidx.paging.compose.itemKey
import coil3.compose.AsyncImage
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import heizige.kk.khromia.components.PrimaryBottomSheet
import heizige.kk.khromia.helper.Toast
import heizige.kk.khatkit.ai.provider.ModelType
import heizige.kk.khatkit.ai.ui.ImageGenSize
import heizige.kk.khatkit.common.android.appTempFolder
import heizige.kk.khatkit.app.R
import heizige.kk.khatkit.app.data.datastore.Settings
import heizige.kk.khatkit.app.data.files.FileUtils
import heizige.kk.khatkit.app.data.files.FilesManager
import heizige.kk.khatkit.app.ui.components.ai.ModelSelector
import heizige.kk.khatkit.app.ui.components.nav.BackButton
import heizige.kk.khatkit.app.ui.components.ui.FormItem
import heizige.kk.khatkit.app.ui.components.ui.ImagePreviewDialog
import heizige.kk.khatkit.app.ui.components.ui.OutlinedNumberInput
import heizige.kk.khatkit.app.ui.context.LocalToaster
import heizige.kk.khatkit.app.utils.ImageUtils
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import heizige.kk.khatkit.app.di.rememberAppEntryPoint
import java.io.File
import kotlin.uuid.Uuid
import heizige.kk.khatkit.app.ui.icons.add
import heizige.kk.khatkit.app.ui.icons.arrowUpward
import heizige.kk.khatkit.app.ui.icons.build
import heizige.kk.khatkit.app.ui.icons.close
import heizige.kk.khatkit.app.ui.icons.contentCopy
import heizige.kk.khatkit.app.ui.icons.delete
import heizige.kk.khatkit.app.ui.icons.palette
import heizige.kk.khatkit.app.ui.icons.photo
import heizige.kk.khatkit.app.ui.icons.save
import heizige.kk.khatkit.app.ui.icons.tune

@Composable
fun ImageGenPage(
    modifier: Modifier = Modifier,
    vm: ImgGenVM = hiltViewModel()
) {
    val pagerState = rememberPagerState { 2 }
    val scope = rememberCoroutineScope()

    val isGenerating by vm.isGenerating.collectAsStateWithLifecycle()
    var showCancelDialog by remember { mutableStateOf(false) }
    BackHandler(isGenerating) {
        showCancelDialog = true
    }
    if (showCancelDialog) {
        CancelDialog(
            onDismiss = { showCancelDialog = false },
            onConfirm = {
                showCancelDialog = false
                vm.cancelGeneration()
            }
        )
    }

    Scaffold(
        topBar = {
            KedgePageTopBar(
                title = stringResource(R.string.imggen_page_title),
                navigationIcon = {
                    BackButton()
                },
                actions = {
                    IconButton(onClick = vm::startNewSession) {
                        Icon(
                            imageVector = add,
                            contentDescription = "New session"
                        )
                    }
                }
            )
        },
        bottomBar = {
            BottomBar(pagerState, scope)
        },
    ) { innerPadding ->
        HorizontalPager(
            state = pagerState,
            modifier = modifier
                .padding(innerPadding)
                .consumeWindowInsets(innerPadding)
        ) { page ->
            when (page) {
                0 -> ImageGenScreen(vm = vm)
                1 -> ImageGalleryScreen(vm = vm, isActive = pagerState.currentPage == 1)
            }
        }
    }
}

@Composable
private fun CancelDialog(
    onDismiss: () -> Unit,
    onConfirm: () -> Unit
) {
    AppAlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.imggen_page_cancel_generation_title)) },
        text = { Text(stringResource(R.string.imggen_page_cancel_generation_message)) },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text(stringResource(R.string.imggen_page_confirm))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.imggen_page_cancel))
            }
        }
    )
}

@Composable
private fun BottomBar(
    pagerState: PagerState,
    scope: CoroutineScope
) {
    NavigationBar {
        NavigationBarItem(
            selected = 0 == pagerState.currentPage,
            label = {
                Text(stringResource(R.string.imggen_page_title))
            },
            icon = {
                Icon(palette, null)
            },
            onClick = {
                scope.launch {
                    pagerState.animateScrollToPage(0)
                }
            }
        )

        NavigationBarItem(
            selected = 1 == pagerState.currentPage,
            label = {
                Text(stringResource(R.string.imggen_page_gallery))
            },
            icon = {
                Icon(photo, null)
            },
            onClick = {
                scope.launch {
                    pagerState.animateScrollToPage(1)
                }
            }
        )
    }
}

@Composable
private fun ImageGenScreen(
    vm: ImgGenVM,
) {
    val prompt by vm.prompt.collectAsStateWithLifecycle()
    val numberOfImages by vm.numberOfImages.collectAsStateWithLifecycle()
    val size by vm.size.collectAsStateWithLifecycle()
    val isGenerating by vm.isGenerating.collectAsStateWithLifecycle()
    val currentGeneratedImages by vm.currentGeneratedImages.collectAsStateWithLifecycle()
    val referenceImages by vm.referenceImages.collectAsStateWithLifecycle()
    val error by vm.error.collectAsStateWithLifecycle()
    val settings by vm.settingsStore.settingsFlow.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    val toaster = LocalToaster.current
    var showSettingsSheet by remember { mutableStateOf(false) }

    LaunchedEffect(error) {
        error?.let { errorMessage ->
            Toast.show(message = errorMessage, isError = true)
            vm.clearError()
        }
    }

    Column(
        verticalArrangement = Arrangement.spacedBy(16.dp),
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
            .imePadding()
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .verticalScroll(rememberScrollState()),
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                (0 until minOf(2, currentGeneratedImages.size)).forEach { index ->
                    val image = currentGeneratedImages[index]
                    var showPreview by remember { mutableStateOf(false) }
                    AsyncImage(
                        model = File(image.filePath),
                        contentDescription = null,
                        modifier = Modifier
                            .weight(1f)
                            .aspectRatio(1f)
                            .clip(RoundedCornerShape(8.dp))
                            .clickable { showPreview = true },
                        contentScale = ContentScale.Crop
                    )

                    if (showPreview) {
                        ImagePreviewDialog(
                            images = listOf(image.filePath),
                            onDismissRequest = { showPreview = false },
                        )
                    }
                }
            }
            if (isGenerating) {
                ContainedLoadingIndicator(
                    modifier = Modifier.align(Alignment.Center)
                )
            }
        }
        InputBar(
            prompt = prompt,
            vm = vm,
            isGenerating = isGenerating,
            referenceImages = referenceImages,
            settings = settings,
            onShowSettings = { showSettingsSheet = true },
            modifier = Modifier
        )
    }

    if (showSettingsSheet) {
        SettingsBottomSheet(
            vm = vm,
            numberOfImages = numberOfImages,
            size = size,
            scope = scope,
            onDismiss = { showSettingsSheet = false }
        )
    }
}

@Composable
private fun InputBar(
    prompt: String,
    vm: ImgGenVM,
    isGenerating: Boolean,
    referenceImages: List<String>,
    settings: Settings,
    onShowSettings: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val imagePickerLauncher =
        rememberLauncherForActivityResult(ActivityResultContracts.GetMultipleContents()) { selectedUris ->
            if (selectedUris.isNotEmpty()) {
                scope.launch {
                    val paths = selectedUris.mapNotNull { uri ->
                        withContext(Dispatchers.IO) {
                            runCatching {
                                val bitmap = ImageUtils.loadOptimizedBitmap(context, uri, maxSize = 2048)
                                    ?: error("Failed to decode image")
                                val pngBytes = FileUtils.compressBitmapToPng(bitmap)
                                bitmap.recycle()
                                val file = File(context.appTempFolder, "imggen_ref_${Uuid.random()}.png")
                                file.writeBytes(pngBytes)
                                file.absolutePath
                            }.getOrNull()
                        }
                    }
                    vm.addReferenceImages(paths)
                }
            }
        }

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        if (referenceImages.isNotEmpty()) {
            ReferenceImagesRow(
                images = referenceImages,
                onRemove = vm::removeReferenceImage
            )
        }

        OutlinedTextField(
            value = prompt,
            onValueChange = vm::updatePrompt,
            placeholder = { Text(stringResource(R.string.imggen_page_prompt_placeholder)) },
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = 140.dp),
            minLines = 1,
            maxLines = 5,
            shape = MaterialTheme.shapes.large,
            textStyle = MaterialTheme.typography.bodySmall,
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            ModelSelector(
                modelId = settings.imageGenerationModelId,
                providers = settings.providers,
                type = ModelType.IMAGE,
                onlyIcon = true,
                onSelect = { model ->
                    scope.launch {
                        vm.settingsStore.update { oldSettings ->
                            oldSettings.copy(imageGenerationModelId = model.id)
                        }
                    }
                }
            )

            IconButton(
                onClick = onShowSettings
            ) {
                Icon(build, null)
            }

            IconButton(
                onClick = { imagePickerLauncher.launch("image/*") }
            ) {
                Icon(
                    imageVector = add,
                    contentDescription = "Add reference image"
                )
            }

            Spacer(modifier = Modifier.weight(1f))

            val canSend = prompt.isNotBlank()
            Surface(
                onClick = {
                    if (!isGenerating) {
                        if (referenceImages.isEmpty()) {
                            vm.generateImage()
                        } else {
                            vm.editImage()
                        }
                    } else {
                        vm.cancelGeneration()
                    }
                },
                enabled = isGenerating || canSend,
                modifier = Modifier.size(40.dp),
                shape = CircleShape,
                color = when {
                    isGenerating -> MaterialTheme.colorScheme.errorContainer
                    !canSend -> MaterialTheme.colorScheme.surfaceContainerHigh
                    else -> MaterialTheme.colorScheme.primary
                },
            ) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = if (isGenerating) close else arrowUpward,
                        contentDescription = stringResource(R.string.imggen_page_generate_image),
                        tint = when {
                            isGenerating -> MaterialTheme.colorScheme.onErrorContainer
                            !canSend -> MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
                            else -> MaterialTheme.colorScheme.onPrimary
                        },
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun ReferenceImagesRow(
    images: List<String>,
    onRemove: (String) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        images.forEach { image ->
            Surface(
                modifier = Modifier.size(56.dp),
                shape = RoundedCornerShape(12.dp),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f)),
                color = MaterialTheme.colorScheme.surfaceContainerHigh,
            ) {
                Box {
                    AsyncImage(
                        model = File(image),
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )

                    Surface(
                        onClick = { onRemove(image) },
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(3.dp)
                            .size(20.dp),
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.scrim.copy(alpha = 0.55f),
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = delete,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.inverseOnSurface,
                                modifier = Modifier.size(12.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ImageGalleryScreen(
    vm: ImgGenVM,
    isActive: Boolean,
) {
    val generatedImages = vm.generatedImages.collectAsLazyPagingItems()
    val context = LocalContext.current
    val filesManager: FilesManager = rememberAppEntryPoint().filesManager()
    val clipboardManager = LocalClipboardManager.current
    val scope = rememberCoroutineScope()
    val toaster = LocalToaster.current
    val pullToRefreshState = rememberPullToRefreshState()
    var selectionMode by remember { mutableStateOf(false) }
    var selectedImages by remember { mutableStateOf<Map<Int, GeneratedImage>>(emptyMap()) }
    var showDeleteDialog by remember { mutableStateOf(false) }
    var isDeleting by remember { mutableStateOf(false) }

    fun clearSelection() {
        selectionMode = false
        selectedImages = emptyMap()
        showDeleteDialog = false
    }

    fun toggleSelection(image: GeneratedImage) {
        if (!isDeleting) {
            selectedImages = if (image.id in selectedImages) {
                selectedImages - image.id
            } else {
                selectedImages + (image.id to image)
            }
        }
    }

    BackHandler(enabled = isActive && selectionMode) {
        if (!isDeleting) clearSelection()
    }
    LaunchedEffect(isActive) {
        if (!isActive && !isDeleting) clearSelection()
    }

    if (showDeleteDialog) {
        AppAlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            title = { Text(stringResource(R.string.imggen_page_delete_images_title)) },
            text = { Text(stringResource(R.string.imggen_page_delete_images_message, selectedImages.size)) },
            confirmButton = {
                TextButton(onClick = {
                    showDeleteDialog = false
                    isDeleting = true
                    val images = selectedImages.values.toList()
                    scope.launch {
                        try {
                            val failed = vm.deleteImages(images)
                            selectedImages = failed.associateBy { it.id }
                            selectionMode = failed.isNotEmpty()
                            Toast.show(
                                message = if (failed.isEmpty()) context.getString(R.string.imggen_page_delete_images_success, images.size)
                                else context.getString(
                                    R.string.imggen_page_delete_images_failed,
                                    images.size - failed.size,
                                    failed.size
                                ),
                                isError = failed.isNotEmpty(),
                            )
                        } finally {
                            isDeleting = false
                        }
                    }
                }) { Text(stringResource(R.string.imggen_page_delete)) }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteDialog = false }) { Text(stringResource(R.string.imggen_page_cancel)) }
            }
        )
    }

    Column(modifier = Modifier.fillMaxSize()) {
        if (selectionMode) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                TextButton(onClick = { clearSelection() }, enabled = !isDeleting) {
                    Text(stringResource(R.string.imggen_page_cancel))
                }
                Text(
                    if (isDeleting) stringResource(R.string.imggen_page_deleting)
                    else stringResource(R.string.imggen_page_selected_count, selectedImages.size)
                )
                TextButton(
                    onClick = { showDeleteDialog = true },
                    enabled = selectedImages.isNotEmpty() && !isDeleting
                ) { Text(stringResource(R.string.imggen_page_delete)) }
            }
        }

        PullToRefreshBox(
            isRefreshing = false,
            onRefresh = { generatedImages.refresh() },
            state = pullToRefreshState
        ) {
            if (generatedImages.itemCount == 0) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Icon(
                            imageVector = photo,
                            contentDescription = null,
                            modifier = Modifier.size(64.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = stringResource(R.string.imggen_page_no_generated_images),
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center
                        )
                    }
                }
            } else {
                LazyVerticalGrid(
                    columns = GridCells.Fixed(2),
                    contentPadding = PaddingValues(16.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxSize(),
                ) {
                    items(
                        count = generatedImages.itemCount,
                        key = generatedImages.itemKey { it.id },
                        contentType = generatedImages.itemContentType { "GeneratedImage" }
                    ) { index ->
                        val image = generatedImages[index]
                        image?.let {
                            var showPreview by remember { mutableStateOf(false) }

                            Card(
                                modifier = Modifier.fillMaxWidth().combinedClickable(
                                    onClick = {
                                        if (selectionMode) toggleSelection(it) else showPreview = true
                                    },
                                    onLongClick = {
                                        if (!isDeleting) {
                                            selectionMode = true
                                            selectedImages = selectedImages + (it.id to it)
                                        }
                                    },
                                    onLongClickLabel = stringResource(R.string.imggen_page_select_image)
                                ),
                                border = if (it.id in selectedImages) {
                                    BorderStroke(2.dp, MaterialTheme.colorScheme.primary)
                                } else null
                            ) {
                                Column {
                                    Box {
                                        AsyncImage(
                                            model = File(it.filePath),
                                            contentDescription = null,
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .aspectRatio(1f),
                                            contentScale = ContentScale.Crop
                                        )
                                        if (selectionMode) {
                                            Checkbox(
                                                checked = it.id in selectedImages,
                                                onCheckedChange = { _ -> toggleSelection(it) },
                                                enabled = !isDeleting,
                                                modifier = Modifier.align(Alignment.TopEnd)
                                            )
                                        }
                                    }

                                    Column(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(8.dp),
                                        verticalArrangement = Arrangement.spacedBy(4.dp)
                                    ) {
                                        Column {
                                            Text(
                                                text = it.model,
                                                style = MaterialTheme.typography.labelSmall,
                                                color = MaterialTheme.colorScheme.primary
                                            )
                                            Text(
                                                text = it.prompt.take(20) + if (it.prompt.length > 20) "..." else "",
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                maxLines = 2
                                            )
                                        }

                                        if (!selectionMode) Row {
                                            IconButton(
                                                onClick = {
                                                    clipboardManager.setText(AnnotatedString(it.prompt))
                                                    Toast.show(
                                                        message = "Prompt copied to clipboard",
                                                        isError = false
                                                    )
                                                },
                                                modifier = Modifier.size(32.dp)
                                            ) {
                                                Icon(
                                                    imageVector = contentCopy,
                                                    contentDescription = "Copy prompt",
                                                    modifier = Modifier.size(16.dp)
                                                )
                                            }

                                            IconButton(
                                                onClick = {
                                                    scope.launch {
                                                        try {
                                                            filesManager.saveMessageImage(context, "file://${it.filePath}")
                                                            Toast.show(
                                                                message = context.getString(R.string.imggen_page_image_saved_success),
                                                                isError = false
                                                            )
                                                        } catch (e: Exception) {
                                                            Toast.show(
                                                                message = context.getString(
                                                                    R.string.imggen_page_save_failed,
                                                                    e.message
                                                                ),
                                                                isError = true
                                                            )
                                                        }
                                                    }
                                                },
                                                modifier = Modifier.size(32.dp)
                                            ) {
                                                Icon(
                                                    imageVector = save,
                                                    contentDescription = stringResource(R.string.imggen_page_save),
                                                    modifier = Modifier.size(16.dp)
                                                )
                                            }

                                            IconButton(
                                                onClick = { vm.deleteImage(it) },
                                                modifier = Modifier.size(32.dp)
                                            ) {
                                                Icon(
                                                    imageVector = delete,
                                                    contentDescription = stringResource(R.string.imggen_page_delete),
                                                    modifier = Modifier.size(16.dp),
                                                    tint = MaterialTheme.colorScheme.error
                                                )
                                            }
                                        }
                                    }
                                }
                            }

                            if (showPreview) {
                                ImagePreviewDialog(
                                    images = listOf(it.filePath),
                                    onDismissRequest = { showPreview = false }
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SettingsBottomSheet(
    vm: ImgGenVM,
    numberOfImages: Int,
    size: String,
    scope: CoroutineScope,
    onDismiss: () -> Unit
) {
    PrimaryBottomSheet(
        visible = true,
        title = stringResource(R.string.imggen_page_settings_title),
        imageVector = tune,
        onDismiss = onDismiss,
        scrollable = false,
    ) { _ ->
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp)
                .imePadding(),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            FormItem(
                label = { Text(stringResource(R.string.imggen_page_generation_count)) },
                description = { Text(stringResource(R.string.imggen_page_generation_count_desc)) }
            ) {
                OutlinedNumberInput(
                    value = numberOfImages,
                    onValueChange = vm::updateNumberOfImages,
                    modifier = Modifier.width(120.dp)
                )
            }

            FormItem(
                label = { Text("Image Size") }
            ) {
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    ImageGenSize.entries.forEach { sizeOption ->
                        FilterChip(
                            selected = size == sizeOption.value,
                            onClick = { vm.updateSize(sizeOption.value) },
                            label = { Text(sizeOption.value) }
                        )
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                OutlinedTextField(
                    value = size,
                    onValueChange = vm::updateSize,
                    label = { Text("Custom size") },
                    placeholder = { Text("e.g. 1024x1024") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    textStyle = MaterialTheme.typography.bodySmall,
                )
            }

            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}
