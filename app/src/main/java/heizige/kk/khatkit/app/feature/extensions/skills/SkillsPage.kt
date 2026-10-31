package heizige.kk.khatkit.app.feature.extensions.skills

import ui.feice.mochi.basic.MochiDropdown
import ui.feice.mochi.basic.MochiItem
import heizige.kk.khatkit.uikit.mochi.Md3OutlinedTextFieldWithSlots
import heizige.kk.khatkit.uikit.mochi.Md3SearchBar
import heizige.kk.khatkit.uikit.mochi.MochiTextButton
import heizige.kk.khatkit.uikit.mochi.MochiIconButton
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed as lazyItemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import heizige.kk.khatkit.app.core.ui.components.ui.AppDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import heizige.kk.khatkit.app.core.ui.components.ui.KedgePageLargeTopBar
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import heizige.kk.khatkit.uikit.mochi.MochiMenuRow
import heizige.kk.khatkit.uikit.mochi.MochiMenuPopup
import heizige.kk.khatkit.uikit.mochi.MochiOptionItem
import heizige.kk.khatkit.uikit.mochi.KkProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import heizige.kk.khatkit.app.core.ui.components.ui.PrimaryBottomSheet // 项目内转发，按风格分流：Miuix 走 MochiPrimaryBottomSheet
import heizige.kk.khatkit.uikit.mochi.Toast
import heizige.kk.khatkit.app.R
import heizige.kk.khatkit.app.core.data.files.InvalidSkillDir
import heizige.kk.khatkit.app.core.data.files.InvalidSkillReason
import heizige.kk.khatkit.app.core.data.files.SkillMetadata
import heizige.kk.khatkit.app.core.data.files.SkillValidationError
import heizige.kk.khatkit.app.core.data.files.SkillValidationException
import heizige.kk.khatkit.app.core.data.files.SkillValidator
import heizige.kk.khatkit.app.Screen
import heizige.kk.khatkit.app.core.ui.components.nav.BackButton
import heizige.kk.khatkit.app.core.ui.components.ui.RikkaConfirmDialog
import heizige.kk.khatkit.app.core.ui.context.LocalNavController
import heizige.kk.khatkit.app.core.ui.context.NoHeroTransition
import heizige.kk.khatkit.app.core.ui.context.LocalToaster
import heizige.kk.khatkit.app.core.ui.theme.listCardStyle
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import heizige.kk.khatkit.app.core.ui.icons.add
import heizige.kk.khatkit.app.core.ui.icons.delete
import heizige.kk.khatkit.app.core.ui.icons.download
import heizige.kk.khatkit.app.core.ui.icons.extension
import heizige.kk.khatkit.app.core.ui.icons.moreVert
import heizige.kk.khatkit.app.core.ui.icons.uploadFile
import heizige.kk.khatkit.app.core.ui.icons.warning
import heizige.kk.khatkit.app.core.ui.components.ui.miuix.KedgeSettingsPageScaffold

@Composable
fun SkillsPage() {
    val navController = LocalNavController.current
    val vm = hiltViewModel<SkillsViewModel>()
    val skills by vm.skills.collectAsStateWithLifecycle()
    val invalidSkillDirs by vm.invalidSkillDirs.collectAsStateWithLifecycle()
    var searchQuery by rememberSaveable { mutableStateOf("") }
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    val toaster = LocalToaster.current
    val context = LocalContext.current

    val skillsPageImportSuccess = stringResource(R.string.skills_page_import_success)
    val skillsPageImportFailed = stringResource(R.string.skills_page_import_failed)
    val skillsPageSaveFailed = stringResource(R.string.skills_page_save_failed)
    var showImportSheet by rememberSaveable { mutableStateOf(false) }
    var showAddDialog by rememberSaveable { mutableStateOf(false) }
    var showImportDialog by rememberSaveable { mutableStateOf(false) }
    var deleteTarget by remember { mutableStateOf<SkillMetadata?>(null) }
    var invalidDeleteTarget by remember { mutableStateOf<InvalidSkillDir?>(null) }
    val fileImportLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        uri ?: return@rememberLauncherForActivityResult
        vm.importSkillFromFile(uri) { success, message ->
            if (success) {
                Toast.show(String.format(skillsPageImportSuccess, message))
            } else {
                Toast.show(String.format(skillsPageImportFailed, message))
            }
        }
    }

    val visibleSkills = skills.filter {
        searchQuery.isBlank() || it.name.contains(searchQuery, true) || it.description.contains(searchQuery, true)
    }
    val visibleInvalidSkillDirs = remember(invalidSkillDirs, searchQuery) {
        invalidSkillDirs.filter { searchQuery.isBlank() || it.dirName.contains(searchQuery, true) }
    }
    var searchExpanded by remember { mutableStateOf(false) }

    // 技能列表：折叠态挂在页面里，展开态搬进搜索框的全屏槽，所以抽成 lambda 复用。
    val skillsList: @Composable (Modifier) -> Unit = { listModifier ->
        // 与助手/提供商列表同款分组：整列看着是一张卡片，圆角按 index/count 逐项算
        // （首项只圆上边、末项只圆下边、中间项直角），项间距取全局列表卡片样式。
        // 不能换成 CardGroup —— 那会把所有项收进一个 Column，就没了「每项一个 lazy item」
        // 和稳定 key。横向留白一律由调用点给（折叠态走 listPadding，展开态自己补 16dp）；
        // LazyColumn 自己不再给 contentPadding，否则会和 listPadding 叠成 32dp。
        val cards = listCardStyle()
        LazyColumn(
            modifier = listModifier,
            verticalArrangement = Arrangement.spacedBy(cards.gap),
        ) {
            if (visibleSkills.isEmpty()) {
                item { SkillsEmptyState() }
            } else {
                lazyItemsIndexed(visibleSkills, key = { _, skill -> skill.skillDir.absolutePath }) { index, skill ->
                    SkillCard(
                        skill = skill,
                        shape = cards.indexedShape(index, visibleSkills.size),
                        onClick = { navController.navigate(Screen.SkillDetail(skill.name)) },
                        onDelete = { deleteTarget = skill },
                    )
                }
            }

            // 空目录、缺少 SKILL.md 等无法识别的目录不会出现在技能列表里，单独列出供清理
            if (visibleInvalidSkillDirs.isNotEmpty()) {
                item(key = "invalid_skill_dirs_title") {
                    Text(
                        text = stringResource(R.string.skills_page_invalid_dirs_title),
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(start = 4.dp, top = cards.gap, bottom = 4.dp),
                    )
                }
                lazyItemsIndexed(
                    visibleInvalidSkillDirs,
                    key = { _, dir -> "invalid_dir_${dir.dirName}" },
                ) { index, dir ->
                    InvalidSkillDirCard(
                        dir = dir,
                        shape = cards.indexedShape(index, visibleInvalidSkillDirs.size),
                        onDelete = { invalidDeleteTarget = dir },
                    )
                }
            }
        }
    }

    KedgeSettingsPageScaffold(
        title = stringResource(R.string.skills_page_title),
        scrollBehavior = scrollBehavior,
    ) { innerPadding ->
        val isMiuix = false
        // Scaffold 的 innerPadding 挂在**整列**上（助手列表 / 提供商列表 / 搜索页同款），
        // 不再塞进列表自己的 padding：以前搜索框留在 y=0 被顶栏压住，列表却整体下移了
        // 一个顶栏高度，于是「搜索框下方到首项」空出一大块（顶栏高 + 16dp）。
        // 现在只留设计留白：top=8dp，与 ProviderList 的 vertical=8dp 同一口径。
        val listPadding = Modifier.padding(
            start = 16.dp,
            end = 16.dp,
            top = 8.dp,
            // 底部 16 + 72 是滚动末端留白，保持原值。
            bottom = 16.dp + 72.dp,
        )

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                // MD3 的 Md3SearchBar 自带 16dp 顶距，Miuix 分支为 0，由调用点补
                // （与 AssistantPage 的补偿同款），两种风格搜索框上方都是 16dp。
                .padding(top = if (isMiuix) 16.dp else 0.dp),
        ) {
            Md3SearchBar(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                placeholder = stringResource(R.string.skills_page_search_placeholder),
                active = searchExpanded,
                onActiveChange = { searchExpanded = it },
                cancelLabel = stringResource(R.string.cancel),
                // MD3 侧留白由组件自己给（跟助手/提供商页同款 16dp），Miuix 侧才要调用点补。
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = if (isMiuix) 16.dp else 0.dp),
                // 不传 showResultsWhenEmpty（默认 false）：点开搜索先空白，输入才出技能列表。
                expandedContent = {
                    // 独立窗口里不能有 sharedElement，否则 lookahead 配对跨 ViewRoot 会崩。
                    // 全屏槽不吃折叠态的 listPadding，横向 16dp 得在这里自己补，
                    // 否则展开态列表会贴到屏幕边上（和折叠态的 16dp 对不齐）。
                    NoHeroTransition {
                        skillsList(
                            Modifier
                                .fillMaxSize()
                                .padding(horizontal = 16.dp),
                        )
                    }
                },
            )

            // 展开期间列表整个抽掉，只在全屏槽里出现一次（其它页同款）。
            if (!searchExpanded) {
                skillsList(
                    Modifier
                        .fillMaxSize()
                        .then(listPadding),
                )
            }
        }
    }

    if (showImportSheet) {
        SkillImportSheet(
            onDismiss = { showImportSheet = false },
            onAddManually = {
                showImportSheet = false
                showAddDialog = true
            },
            onImportFromFile = {
                showImportSheet = false
                fileImportLauncher.launch(
                    arrayOf(
                        "text/*",
                        "application/zip",
                        "application/x-zip-compressed",
                        "application/octet-stream",
                    )
                )
            },
            onImportFromGitHub = {
                showImportSheet = false
                showImportDialog = true
            },
        )
    }

    if (showAddDialog) {
        AddSkillDialog(
            onDismiss = { showAddDialog = false },
            onConfirm = { name, content ->
                vm.saveSkill(name, content) { success ->
                    showAddDialog = false
                    if (!success) {
                        Toast.show(skillsPageSaveFailed)
                    }
                }
            },
        )
    }

    if (showImportDialog) {
        ImportSkillDialog(
            onDismiss = { showImportDialog = false },
            onConfirm = { repoUrl ->
                vm.importSkillFromGitHub(repoUrl) { success, message ->
                    showImportDialog = false
                    if (success) {
                        Toast.show(String.format(skillsPageImportSuccess, message))
                    } else {
                        Toast.show(String.format(skillsPageImportFailed, message))
                    }
                }
            },
        )
    }

    RikkaConfirmDialog(
        show = deleteTarget != null,
        title = stringResource(R.string.skills_page_delete_title),
        confirmText = stringResource(R.string.delete),
        dismissText = stringResource(R.string.cancel),
        onConfirm = {
            deleteTarget?.let { vm.deleteSkill(it) }
            deleteTarget = null
        },
        onDismiss = { deleteTarget = null },
    ) {
        Text(stringResource(R.string.skills_page_delete_message, deleteTarget?.name ?: ""))
    }

    // 异常目录（空目录 / 缺 SKILL.md 等）的删除：删的是整个目录
    RikkaConfirmDialog(
        show = invalidDeleteTarget != null,
        title = stringResource(R.string.skills_page_delete_dir_title),
        confirmText = stringResource(R.string.delete),
        dismissText = stringResource(R.string.cancel),
        onConfirm = {
            invalidDeleteTarget?.let { vm.deleteInvalidSkillDir(it.dirName) }
            invalidDeleteTarget = null
        },
        onDismiss = { invalidDeleteTarget = null },
    ) {
        Text(stringResource(R.string.skills_page_delete_dir_message, invalidDeleteTarget?.dirName.orEmpty()))
    }
}

/** 技能列表为空时的占位（图标 + 标题 + 提示），折叠态与展开态共用。 */
@Composable
private fun SkillsEmptyState() {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 48.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Icon(
            imageVector = extension,
            contentDescription = null,
            modifier = Modifier.size(48.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = stringResource(R.string.skills_page_empty_title),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = stringResource(R.string.skills_page_empty_hint),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun SkillCard(
    skill: SkillMetadata,
    shape: Shape,
    onClick: () -> Unit,
    onDelete: () -> Unit,
) {
    var menuExpanded by remember { mutableStateOf(false) }

    // 与助手/提供商列表同一套口径：底色、内边距、按压反馈全交给 MochiOptionItem，
    // 行内容只走插槽（leading 图标 / 标题 / 摘要 / 尾部徽标或更多菜单）。
    MochiOptionItem(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        shape = shape,
        leadingContent = {
            Icon(
                imageVector = extension,
                contentDescription = null,
                modifier = Modifier.size(20.dp),
                tint = MaterialTheme.colorScheme.primary,
            )
        },
        titleContent = {
            Text(
                text = skill.name,
                style = MaterialTheme.typography.titleMedium,
            )
        },
        supportingContent = {
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    text = skill.description,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                )
                if (!skill.compatibility.isNullOrBlank()) {
                    Text(
                        text = skill.compatibility,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.tertiary,
                    )
                }
            }
        },
        trailingContent = {
            if (skill.builtin) {
                Text(
                    text = stringResource(R.string.skills_page_builtin_badge),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.secondary,
                )
            } else {
                // 内置技能只读，不提供删除
                Box {
                    MochiDropdown(
                        expanded = menuExpanded,
                        onExpandedChange = { menuExpanded = it },
                        items = kotlinx.collections.immutable.persistentListOf(
                            MochiItem(
                                label = stringResource(R.string.delete),
                                destructive = true,
                                icon = { color -> Icon(delete, contentDescription = null, tint = color) },
                            ),
                        ),
                        onItemClick = {
                            menuExpanded = false
                            onDelete()
                        },
                        onClick = { menuExpanded = !menuExpanded },
                        buttonContent = {
                            Icon(
                                imageVector = moreVert,
                                contentDescription = stringResource(R.string.skills_page_more_actions),
                            )
                        },
                    )
                }
            }
        },
    )
}

@Composable
private fun invalidSkillReasonText(reason: InvalidSkillReason): String = stringResource(
    when (reason) {
        InvalidSkillReason.MissingSkillMd -> R.string.skills_page_invalid_reason_missing_skill_md
        InvalidSkillReason.MissingName -> R.string.skills_page_invalid_reason_missing_name
        InvalidSkillReason.MissingDescription -> R.string.skills_page_invalid_reason_missing_description
        InvalidSkillReason.ReadFailed -> R.string.skills_page_invalid_reason_read_failed
    }
)

// 页面和 SkillsViewModel 的导入提示共用
@get:StringRes
internal val SkillValidationError.messageRes: Int
    get() = when (this) {
        SkillValidationError.MissingName -> R.string.skills_page_name_error
        SkillValidationError.NameHasSurroundingWhitespace -> R.string.skills_page_name_whitespace_error
        SkillValidationError.NameTooLong -> R.string.skills_page_name_too_long_error
        SkillValidationError.NameStartsWithDot -> R.string.skills_page_name_dot_error
        SkillValidationError.NameHasInvalidChars -> R.string.skills_page_name_invalid_chars_error
        SkillValidationError.MissingDescription -> R.string.skills_page_description_error
    }

internal val SkillValidationError.formatArgs: Array<Any>
    get() = if (this == SkillValidationError.NameTooLong) {
        arrayOf(SkillValidator.MAX_NAME_LENGTH)
    } else {
        emptyArray()
    }

/** 无法识别为技能的目录（空目录 / 缺 SKILL.md 等）一行：显示目录名与原因，行尾可删。 */
@Composable
private fun InvalidSkillDirCard(
    dir: InvalidSkillDir,
    shape: Shape,
    onDelete: () -> Unit,
) {
    MochiOptionItem(
        onClick = onDelete,
        modifier = Modifier.fillMaxWidth(),
        shape = shape,
        leadingContent = {
            Icon(
                imageVector = warning,
                contentDescription = null,
                modifier = Modifier.size(20.dp),
                tint = MaterialTheme.colorScheme.error,
            )
        },
        titleContent = {
            Text(
                text = dir.dirName,
                style = MaterialTheme.typography.titleMedium,
                maxLines = 1,
            )
        },
        supportingContent = {
            Text(
                text = invalidSkillReasonText(dir.reason),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
            )
        },
        trailingContent = {
            Icon(
                imageVector = delete,
                contentDescription = stringResource(R.string.delete),
                modifier = Modifier.size(20.dp),
                tint = MaterialTheme.colorScheme.error,
            )
        },
    )
}

@Composable
private fun SkillImportSheet(
    onDismiss: () -> Unit,
    onAddManually: () -> Unit,
    onImportFromFile: () -> Unit,
    onImportFromGitHub: () -> Unit,
) {
    PrimaryBottomSheet(
        visible = true,
        title = stringResource(R.string.skills_page_add_title),
        imageVector = extension,
        onDismiss = onDismiss,
    ) { dismiss ->
        // 弹窗里这三个入口同属一组「选项行」：项间距与逐项圆角走全局列表卡片样式
        // （首项只圆上边、末项只圆下边），与页面里的技能卡片同一套口径。
        val cards = listCardStyle()
        val count = 3
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 16.dp, end = 16.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(cards.gap),
        ) {
            SkillImportSheetItem(
                shape = cards.indexedShape(0, count),
                icon = { Icon(add, contentDescription = null) },
                text = stringResource(R.string.skills_page_add_manually),
                onClick = {
                    dismiss()
                    onAddManually()
                },
            )
            SkillImportSheetItem(
                shape = cards.indexedShape(1, count),
                icon = { Icon(uploadFile, contentDescription = null) },
                text = stringResource(R.string.skills_page_import_from_file),
                onClick = {
                    dismiss()
                    onImportFromFile()
                },
            )
            SkillImportSheetItem(
                shape = cards.indexedShape(2, count),
                icon = { Icon(download, contentDescription = null) },
                text = stringResource(R.string.skills_page_import_from_github),
                onClick = {
                    dismiss()
                    onImportFromGitHub()
                },
            )
        }
    }
}

@Composable
private fun SkillImportSheetItem(
    shape: Shape,
    icon: @Composable () -> Unit,
    text: String,
    onClick: () -> Unit,
) {
    // 与技能卡片同款：底色、内边距、按压反馈都交给 MochiOptionItem，行内容只走插槽；
    // 圆角由调用点按 index/count 算好传进来（本组件不猜自己在组里的位置）。
    MochiOptionItem(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        shape = shape,
        leadingContent = icon,
        titleContent = { Text(text) },
    )
}

@Composable
private fun AddSkillDialog(
    onDismiss: () -> Unit,
    onConfirm: (name: String, content: String) -> Unit,
) {
    var content by rememberSaveable { mutableStateOf("") }

    // 目录名直接取 name，所以这里用 SkillValidator 校验它能不能原样当目录名
    val validation = remember(content) { SkillValidator.validate(content) }
    val name = validation.getOrNull()
    val validationError = (validation.exceptionOrNull() as? SkillValidationException)?.error
    val nameError = content.isNotBlank() && name == null

    AppDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.skills_page_add_title)) },
        text = {
            Md3OutlinedTextFieldWithSlots(
                value = content,
                onValueChange = { content = it },
                label = { Text(stringResource(R.string.skills_page_skill_content_label)) },
                placeholder = {
                    Text(
                        "---\nname: my-skill\ndescription: \"...\"\n---\n\n" +
                            stringResource(R.string.skills_page_skill_body_placeholder),
                        fontFamily = FontFamily.Monospace,
                    )
                },
                supportingText = {
                    if (nameError) Text(
                        validationError?.let { stringResource(it.messageRes, *it.formatArgs) }
                            ?: stringResource(R.string.skills_page_name_error),
                        color = MaterialTheme.colorScheme.error
                    )
                    else if (name != null) Text(stringResource(R.string.skills_page_skill_name, name))
                    else Text(stringResource(R.string.skills_page_paste_hint))
                },
                isError = nameError,
                minLines = 8,
                maxLines = 14,
                textStyle = MaterialTheme.typography.bodyMedium.copy(fontFamily = FontFamily.Monospace),
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp)
            )
        },
        confirmButton = {
            MochiTextButton(
                onClick = { name?.let { onConfirm(it, content) } },
                enabled = name != null,
                shapes = ButtonDefaults.shapes(),
            ) {
                Text(stringResource(R.string.skills_page_save))
            }
        },
        dismissButton = {
            MochiTextButton(onClick = onDismiss, shapes = ButtonDefaults.shapes()) { Text(stringResource(R.string.cancel)) }
        },
    )
}

@Composable
private fun ImportSkillDialog(
    onDismiss: () -> Unit,
    onConfirm: (repoUrl: String) -> Unit,
) {
    var url by rememberSaveable { mutableStateOf("") }
    var loading by remember { mutableStateOf(false) }

    AppDialog(
        onDismissRequest = { if (!loading) onDismiss() },
        title = { Text(stringResource(R.string.skills_page_import_from_github)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    text = stringResource(R.string.skills_page_import_description),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Md3OutlinedTextFieldWithSlots(
                    value = url,
                    onValueChange = { url = it },
                    label = { Text(stringResource(R.string.skills_page_repo_url_label)) },
                    placeholder = { Text("https://github.com/owner/repo", fontFamily = FontFamily.Monospace) },
                    supportingText = { Text(stringResource(R.string.skills_page_repo_url_hint)) },
                    singleLine = true,
                    enabled = !loading,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp)
                )
                if (loading) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        KkProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                        Text(
                            stringResource(R.string.skills_page_downloading),
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                }
            }
        },
        confirmButton = {
            MochiTextButton(
                onClick = {
                    loading = true
                    onConfirm(url)
                },
                enabled = url.isNotBlank() && !loading,
                shapes = ButtonDefaults.shapes(),
            ) {
                Text(stringResource(R.string.skills_page_import_confirm))
            }
        },
        dismissButton = {
            MochiTextButton(onClick = onDismiss, enabled = !loading, shapes = ButtonDefaults.shapes()) { Text(stringResource(R.string.cancel)) }
        },
    )
}
