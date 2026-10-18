package heizige.kk.khatkit.app.core.ui.components.ui

import android.net.Uri
import android.Manifest
import android.content.pm.PackageManager
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.ui.platform.LocalContext
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import heizige.kk.khatkit.app.core.ui.components.ui.PrimaryBottomSheet // 项目内转发，按风格分流：Miuix 走 KedgePrimaryBottomSheet
import androidx.compose.material3.Text
import heizige.kk.kedge.components.KedgeButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import java.security.MessageDigest
import kotlin.math.abs
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.core.net.toUri
import coil3.compose.AsyncImage
import heizige.kk.khatkit.common.android.appTempFolder
import heizige.kk.khatkit.app.R
import heizige.kk.khatkit.app.core.data.files.FilesManager
import heizige.kk.khatkit.app.core.data.model.Avatar
import heizige.kk.khatkit.app.core.ui.components.ai.useCropLauncher
import heizige.kk.khatkit.mediapicker.domain.AllowedMedia
import heizige.kk.khatkit.mediapicker.ui.KhatKitMediaPicker
import heizige.kk.khatkit.app.core.ui.hooks.rememberAvatarShape
import heizige.kk.khatkit.app.core.di.rememberAppEntryPoint
import java.io.File
import heizige.kk.khatkit.app.core.ui.icons.editNote
import heizige.kk.khatkit.app.core.ui.icons.palette
import heizige.kk.kedge.components.KedgeSurface
import heizige.kk.khatkit.app.core.util.computeAIIconByName

@Composable
fun TextAvatar(
    text: String,
    modifier: Modifier = Modifier,
    loading: Boolean = false,
    color: Color = MaterialTheme.colorScheme.secondaryContainer
) {
    Box(
        modifier = modifier
            .then(Modifier.size(32.dp))
            .clip(shape = rememberAvatarShape(loading))
            .background(color),
        contentAlignment = Alignment.Center
    ) {
        // 字形先渲进位图再贴出来（[LetterGlyphImage]）：度量只发生一次，
        // 之后换色只换 tint，不再重组一次就重排一次字。
        LetterGlyphImage(
            glyph = text.take(1).uppercase(),
            color = LocalContentColor.current,
            modifier = Modifier.fillMaxSize(),
        )
    }
}

@Composable
fun UIAvatar(
    name: String,
    value: Avatar,
    modifier: Modifier = Modifier,
    loading: Boolean = false,
    avatarShape: String = "Circle",
    defaultIconName: String? = null,
    avatarColor: String? = null,
    onUpdate: ((Avatar) -> Unit)? = null,
    onClick: (() -> Unit)? = null
) {
    val filesManager: FilesManager = rememberAppEntryPoint().filesManager()
    val context = LocalContext.current
    // 编辑头像的入口开关：内容与转场都在 AvatarEditSheet 里，这里只控制挂载。
    var showEditSheet by remember { mutableStateOf(false) }
    var showEmojiPicker by remember { mutableStateOf(false) }
    var preCropTempFile by remember { mutableStateOf<File?>(null) }
    // 拍照的落盘文件：TakePicture 需要一个可写的 FileProvider uri；
    // 拍完把文件移交给 preCropTempFile，裁切结束时由 useCropLauncher 的 onCleanup 删掉。
    var cameraOutputFile by remember { mutableStateOf<File?>(null) }
    var cameraOutputUri by remember { mutableStateOf<Uri?>(null) }

    fun saveAvatarImage(uri: Uri) {
        val localUris = filesManager.createChatFilesByContents(listOf(uri))
        localUris.firstOrNull()?.let { localUri ->
            onUpdate?.invoke(Avatar.Image(localUri.toString()))
        }
    }

    // 头像走自研媒体网格选择器（对齐 ImageToolbox），单选
    var showImagePicker by remember { mutableStateOf(false) }
    val (_, launchImageCrop) = useCropLauncher(
        onCroppedImageReady = { croppedUri ->
            saveAvatarImage(croppedUri)
        },
        onCleanup = {
            preCropTempFile?.delete()
            preCropTempFile = null
        },
        aspectRatio = 1f to 1f,
        freeStyleCropEnabled = false
    )
    val iconFilePickerLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { selectedUri ->
        selectedUri ?: return@rememberLauncherForActivityResult
        val mime = context.contentResolver.getType(selectedUri).orEmpty()
        val isSvg = mime.equals("image/svg+xml", ignoreCase = true) ||
            selectedUri.toString().substringBefore('?').endsWith(".svg", ignoreCase = true)
        if (isSvg) {
            saveAvatarImage(selectedUri)
        } else {
            val tempFile = File(context.appTempFolder, "avatar_file_${System.currentTimeMillis()}.jpg")
            runCatching {
                context.contentResolver.openInputStream(selectedUri)?.use { input ->
                    tempFile.outputStream().use { output -> input.copyTo(output) }
                } ?: error("Failed to open input stream for $selectedUri")
                preCropTempFile?.delete()
                preCropTempFile = tempFile
                launchImageCrop(tempFile.toUri())
            }.onFailure {
                tempFile.delete()
                launchImageCrop(selectedUri)
            }
        }
    }

    fun clearCameraTemp() {
        cameraOutputFile?.delete()
        cameraOutputFile = null
        cameraOutputUri = null
    }

    val cameraLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.TakePicture()
    ) { captureSuccessful ->
        val capturedUri = cameraOutputUri
        if (captureSuccessful && capturedUri != null) {
            // 与相册选图后的路径逐字一致：先裁切，onUpdate 只在裁切完成后触发。
            // 落盘文件移交给 preCropTempFile（它由 useCropLauncher 的 onCleanup 负责删）。
            preCropTempFile?.delete()
            preCropTempFile = cameraOutputFile
            cameraOutputFile = null
            cameraOutputUri = null
            launchImageCrop(capturedUri)
        } else {
            // 取消拍摄：把已建的空文件清掉，不留残渣
            clearCameraTemp()
        }
    }

    fun launchCameraCapture() {
        val tempFile = File(context.appTempFolder, "avatar_camera_${System.currentTimeMillis()}.jpg")
        // cache-path 已声明在 res/xml/file_paths.xml，FileProvider 可直接外发该文件
        val uri = runCatching {
            FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", tempFile)
        }.getOrNull()
        if (uri == null) {
            tempFile.delete()
            return
        }
        cameraOutputFile = tempFile
        cameraOutputUri = uri
        cameraLauncher.launch(uri)
    }

    val cameraPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            launchCameraCapture()
        } else {
            Toast.makeText(context, context.getString(R.string.avatar_camera_permission_denied), Toast.LENGTH_SHORT).show()
        }
    }

    fun takePhoto() {
        val granted = ContextCompat.checkSelfPermission(
            context, Manifest.permission.CAMERA
        ) == PackageManager.PERMISSION_GRANTED
        if (granted) {
            launchCameraCapture()
        } else {
            cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
        }
    }

    // 头像组件被移出组合时，别把拍摄落盘的临时图留在 cache 目录里
    DisposableEffect(Unit) {
        onDispose { cameraOutputFile?.delete() }
    }

    val shape = remember(loading, avatarShape) {
        if (loading) null else avatarShape
    }
    Box(modifier = modifier.then(Modifier.size(32.dp))) {
        KedgeSurface(
            shape = if (shape == null) rememberAvatarShape(loading) else roleAvatarShape(shape),
            modifier = Modifier.fillMaxSize(),
            onClick = {
                onClick?.invoke()
                if (onUpdate != null) showEditSheet = true
            },
            tonalElevation = 4.dp,
            color = roleAvatarColor(name, avatarColor),
        ) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier.fillMaxSize()
            ) {
                when (value) {
                    is Avatar.Image -> {
                        AsyncImage(
                            model = value.url,
                            contentDescription = null,
                            modifier = Modifier.fillMaxSize(),
                            contentScale = ContentScale.Crop,
                        )
                    }

                    is Avatar.Emoji -> {
                        Text(
                            text = value.content,
                            autoSize = TextAutoSize.StepBased(
                                minFontSize = 15.sp,
                                maxFontSize = 30.sp,
                            ),
                            lineHeight = 0.8.em,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.padding(5.dp)
                        )
                    }

                    is Avatar.Dummy -> {
                        DefaultRoleAvatar(
                            name = name,
                            iconName = defaultIconName,
                            backgroundColor = roleAvatarColor(name, avatarColor),
                            modifier = Modifier.fillMaxSize()
                        )
                    }
                }
            }
        }

        // Show edit icon when editable
        if (onUpdate != null) {
            Box(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .size(14.dp)
                    .clip(MaterialTheme.shapes.small)
                    .background(MaterialTheme.colorScheme.tertiaryContainer),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = editNote,
                    contentDescription = "Edit",
                    modifier = Modifier
                        .size(10.dp)
                        .padding(1.dp),
                    tint = MaterialTheme.colorScheme.onTertiaryContainer
                )
            }
        }
    }

    if (showEditSheet) {
        AvatarEditSheet(
            onDismissRequest = { showEditSheet = false },
            onAction = { action ->
                when (action) {
                    AvatarEditSheetAction.PickImage -> showImagePicker = true
                    AvatarEditSheetAction.TakePhoto -> takePhoto()
                    AvatarEditSheetAction.PickIconFile ->
                        iconFilePickerLauncher.launch(arrayOf("image/*", "image/svg+xml"))
                    AvatarEditSheetAction.PickEmoji -> showEmojiPicker = true
                    // 重置即清除：Avatar.Dummy 会把图片/emoji 一起清掉，回到默认头像
                    AvatarEditSheetAction.Reset -> onUpdate?.invoke(Avatar.Dummy)
                }
            },
            onSubmitUrl = { url -> onUpdate?.invoke(Avatar.Image(url)) },
        )
    }

    if (showEmojiPicker) {
        PrimaryBottomSheet(
            visible = true,
            title = stringResource(id = R.string.avatar_pick_emoji),
            imageVector = palette,
            onDismiss = {
                showEmojiPicker = false
            },
            scrollable = false,
        ) { dismiss ->
            EmojiPicker(
                onEmojiSelected = { emoji ->
                    onUpdate?.invoke(Avatar.Emoji(content = emoji.emoji))
                    dismiss()
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .wrapContentHeight()
                    .padding(16.dp)
            )
        }
    }


    KhatKitMediaPicker(
        visible = showImagePicker,
        allowedMedia = AllowedMedia.Photos(null),
        allowMultiple = false,
        onDismiss = { showImagePicker = false },
        onPicked = { uris ->
            showImagePicker = false
            uris.firstOrNull()?.let { selectedUri ->
                val tempFile = File(context.appTempFolder, "avatar_pick_${System.currentTimeMillis()}.jpg")
                runCatching {
                    context.contentResolver.openInputStream(selectedUri)?.use { input ->
                        tempFile.outputStream().use { output -> input.copyTo(output) }
                    } ?: error("Failed to open input stream for $selectedUri")
                    preCropTempFile?.delete()
                    preCropTempFile = tempFile
                    launchImageCrop(tempFile.toUri())
                }.onFailure {
                    tempFile.delete()
                    launchImageCrop(selectedUri)
                }
            }
        },
    )
}

@Composable
private fun DefaultRoleAvatar(
    name: String,
    iconName: String?,
    backgroundColor: Color,
    modifier: Modifier = Modifier,
) {
    val iconPath = remember(iconName) { iconName?.let(::computeAIIconByName) }
    Box(
        modifier = modifier.background(backgroundColor),
        contentAlignment = Alignment.Center,
    ) {
        if (iconPath != null) {
            val context = LocalContext.current
            val model = remember(iconPath, context) {
                coil3.request.ImageRequest.Builder(context)
                    .data("file:///android_asset/icons/$iconPath")
                    .build()
            }
            AsyncImage(
                model = model,
                contentDescription = null,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(8.dp),
                colorFilter = androidx.compose.ui.graphics.ColorFilter.tint(Color.White),
            )
        } else {
            // 与原来 `Text(fontSize = 18.sp)` 视觉字号逐点对齐：显式字号不参与自适应，
            // 只按位图/盒子的像素比缩放（见 [LetterGlyphImage] 的 `fontSize`）。
            LetterGlyphImage(
                glyph = name.trim().take(1).uppercase().ifBlank { "?" },
                color = Color.White,
                fontSize = 18.sp,
                modifier = Modifier.fillMaxSize(),
            )
        }
    }
}

@Composable
private fun ProceduralAvatar(name: String, modifier: Modifier = Modifier) {
    val (fromColor, toColor) = remember(name) {
        vercelAvatarColors(name.ifBlank { "?" })
    }
    Canvas(modifier = modifier) {
        drawRect(
            brush = Brush.linearGradient(
                colors = listOf(fromColor, toColor),
                start = Offset(0f, 0f),
                end = Offset(size.width, size.height)
            )
        )
    }
}

private fun vercelAvatarColors(name: String): Pair<Color, Color> {
    val bytes = MessageDigest.getInstance("SHA-1").digest(name.toByteArray(Charsets.UTF_8))
    val sum = bytes.fold(0) { acc, b -> acc + (b.toInt() and 0xFF) }
    val hue = (sum % 360).toFloat()
    return Pair(
        hslToColor(hue, 0.65f, 0.55f),
        hslToColor((hue + 120f) % 360f, 0.65f, 0.55f)
    )
}

private fun hslToColor(h: Float, s: Float, l: Float): Color {
    val c = (1f - abs(2f * l - 1f)) * s
    val hPrime = h / 60f
    val x = c * (1f - abs(hPrime % 2f - 1f))
    val (r1, g1, b1) = when {
        hPrime < 1f -> Triple(c, x, 0f)
        hPrime < 2f -> Triple(x, c, 0f)
        hPrime < 3f -> Triple(0f, c, x)
        hPrime < 4f -> Triple(0f, x, c)
        hPrime < 5f -> Triple(x, 0f, c)
        else        -> Triple(c, 0f, x)
    }
    val m = l - c / 2f
    return Color(r1 + m, g1 + m, b1 + m)
}

@Preview(showBackground = true)
@Composable
private fun PreviewUIAvatar() {
    var loading by remember { mutableStateOf(true) }
    Column(
        modifier = Modifier.padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        UIAvatar(
            name = "John Doe",
            value = Avatar.Dummy,
            loading = false
        )

        UIAvatar(
            name = "John Doe",
            value = Avatar.Dummy,
            loading = loading,
        )

        KedgeButton(
            onClick = {
                loading = !loading
            },
            shapes = ButtonDefaults.shapes(),
        ) {
            Text("Toggle Loading")
        }
    }
}
