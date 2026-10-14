# 工作区模板 + 工作区级备份/恢复（G9 最小可用切片设计）

> 任务：t31（G9 需求与设计）。本文件**只做设计**，不含任何代码改动。
>
> 依据：`docs/beyond-operit2-source-gap-analysis.md` 的 G9 节（:34 / :53 / :61 / :217 / :337）与
> §8「建议的下一步」第 3 条（**`:347`** 原文：「**工作区三件套**（P1）：模板 / 备份 / 同步按此顺序做，
> 前两个不需要多设备」）、§5 优先级表（`:292` = 模板 + 备份 列为 P0′、`:294` = 同步 / 回滚 列为 P1）。
>
> 对照对象：`AAswordman/Operit2`，`git rev-parse HEAD` = **`9ae1e558a66f96d81ff95a499bd39bda7da6c122`**
> （只读副本 `/tmp/operit2`）。
>
> **许可与署名口径（按 captain 对 t31 的修订；本文件为修订后的 attempt 2）**
>
> 1. 本仓根 `LICENSE:1` = `GNU AFFERO GENERAL PUBLIC LICENSE Version 3`，`README.md:85-87`
>    自述 AGPL-3.0 ⇒ **与 Operit2 同许可**。故任务描述里原「**不得**复制代码、资源、字符串或注释」
>    的禁令**已作废**：在本项目内复用/改写 O 的代码或资源没有许可障碍。
> 2. 保留的**唯一硬义务是署名**：凡复用或改写 O 的代码/资源，必须**逐处**注明来源仓库 +
>    SHA `9ae1e558a66f96d81ff95a499bd39bda7da6c122` + 「原样复用 / 改写」（AGPL §5 要求标记改动）。
>    这正是 gap 分析 `:350-351` 自己立的习惯；⚠️ 该段末尾仍留有一句修订前的「不得复制代码/资源」，
>    那句同样已被本次修订作废 —— 但该文件不在本任务 inScope（本任务只写本文件），故只在此备注，不代改。
> 3. **本设计的实际形态**：O 侧是本机 Rust + Dart/Flutter，本仓是 Kotlin/Compose，逐条对照后
>    **没有任何一处需要原样复用**：§3 的 6 条全部是**机制参照 + 本仓自撰**，每条已标注性质
>    （取值域 `参照` / `改写` / `原样复用`，本文件当前只用了前两类）。
>    若下游实现阶段改为原样复用（例如整段移植 O 的某个 manifest 字段表），**必须**在 §3 表内
>    新增一行并写明「仓库 + SHA + 原样复用 + 改动点」，否则不得合入。
>
> 复核方式：第 11 节给了一组可一键复现的命令；本文档每条「已有 / 部分 / 没有」判定都带
> file:line，reviewer 可逐条独立核对。

---

## 0. 结论速览

**做两件（两个独立切片，各自可单独编译、单独验收）：**

| 切片 | 一句话 | 现状完成度 | 量级 |
|---|---|---|---|
| **A. 工作区模板** | 新建工作区时从「模板」起手：把一组预置文件（含内置 assets 模板与用户模板）落到该工作区的 `files/` | 概念 **0/6**；可复用底层 **6/6** | ~370–550 行 |
| **B. 工作区级备份/恢复** | 把**单个工作区**打成一个 `.zip` 导出、再从该格式导入恢复（含工作区元数据） | 工作区级行为 **0/8**；应用级备份机制可复用 **7/8** | ~550–750 行 |

**不做（明确划界，见第 9 节）**：多设备文件同步、Space/绑定概念、O 侧那种「每条 AI 消息自动快照 +
按时间戳回滚」的改动回滚（那是 P1，gap 分析 §5 :294 已把「文件同步 / 改动回滚」拆出去）。

**最重要的三条盘点结论**（都是「不要重复造」）：

1. **本仓已有一套成熟的应用级备份/恢复**（`BackupManager` + `PendingRestore` + WebDAV/S3 + SAF 本地导出），
   打包、暂存、原子替换、journal 续跑、失败回滚、跨版本迁移**全都写完了**；它**完全不覆盖工作区**
   （`BackupManager.kt` 全文 0 处 `workspace`）。切片 B 应当**只在它旁边补一个工作区作用域的归档器**，
   而不是再写一套。
2. **「assets 随 APK 发布 → 首次启动解压到 filesDir → 用户同名优先」这套模板存储形态本仓已经实现过一遍**
   （`BuiltinSkills.kt`，用于内置技能）。切片 A 直接照这个形态做即可。
3. **一个隐性风险**：系统级自动备份的两份规则口径不一致 —— `backup_rules.xml`（API<31）只 include
   `upload/`，而 `data_extraction_rules.xml`（API≥31）的 `<cloud-backup>` 是空的。按 Android 文档语义，
   后者默认包含应用私有目录 ⇒ 在 API≥31 上 `filesDir/workspaces/**`（含 rootfs）**可能已被系统云备份
   悄悄纳入**，并且大概率超 Auto Backup 的默认配额（25MB，属 Android 平台文档口径，非本仓证据）。
   （**标注：此条为规则推断，未实机验证**，验证步骤见 §7-R6。）
   建议随之明确：既然要做显式的工作区备份，就**显式把 workspaces 从系统自动备份排除**。

---

## 1. 术语与本文档的口径

- **工作区（workspace）**：`workspaces` 表一行（`WorkspaceEntity.kt:11-38`）+ 磁盘目录
  `filesDir/workspaces/<root>/`（`RepositoryHiltModule.kt:118`）。目录内固定三段：
  `files/`（用户文件区）、`linux/`（PRoot rootfs）、`tmp/`（`WorkspaceManager.kt:37-41` + :246-248）。
- **`root`**：磁盘目录名，创建时就是 UUID（`WorkspaceRepository.kt:60/69`），受
  `ROOT_NAME_REGEX = [A-Za-z0-9._-]+` 约束（`WorkspaceManager.kt:257`、`requireValidRoot` :221-225）。
  **`name` 与 `root` 解耦** ⇒ 重命名工作区不影响任何按 root 定位的落地动作。
- **「工作区级」vs「应用级」**：应用级 = 整个 App（设置 + 数据库 + upload/skills/fonts）；
  工作区级 = 单个工作区（该工作区的文件 + 该行元数据）。
- **判定三态**：**已有**（能直接用）、**部分**（能力在但缺一环）、**没有**（0 命中）。
- 所有行号基于本仓当前工作区状态（`git rev-parse HEAD` = `2505cf7a1`；注意 `SkillManager.kt` 在
  t22 有未提交改动，本文引用的是**改动后**的行号）。

---

## 2. 我方现状盘点（逐条 file:line）

### 2.1 验收要求的九项

| # | 对象 | 判定 | 证据（file:line） | 说明 |
|---|---|---|---|---|
| 1 | **WorkspacePage** | **已有** | `WorkspacePage.kt:64` 页面；`:72` `KedgeSettingsPageScaffold`；`:76` FAB 建工作区；`:103-114` 创建对话框调用；`:260-303` `EditWorkspaceDialog`（`:275-286` 名称输入 + `:269` 同名检测 + `:288-301` 保存/取消）；`:81-100` 列表；`:172-257` `WorkspaceCard` 菜单（重命名/删除） | 创建/重命名/删除/进入，全在。**创建入口只有一个**（`:110` `vm.create(name)`） |
| 2 | **WorkspaceDetailPage** | **已有** | `WorkspaceDetailPage.kt:111` 页面；`:118` `rememberPagerState { 2 }`；`:124-136` 导入文件 launcher（`OpenDocument`）；`:138-145` 导出单文件 launcher（`CreateDocument("*/*")`）；`:146-150` 批量导出目录 launcher（`OpenDocumentTree`）；`:156-177` 顶栏 actions（导入/刷新/终端）；`:178-195` 底部两 tab；`:362` `WorkspaceBasicPage`；`:468` `WorkspaceToolApprovalCard`；`:602` `WorkspaceFilesPage`；`:713` `WorkspaceAreaSelector` | **已具备 SAF 三种 launcher 的既有用法** ⇒ 切片 B 的导入/导出直接照抄接线模式，不引入新机制 |
| 3 | **WorkspaceDetailViewModel** | **已有** | `WorkspaceDetailViewModel.kt:50-55` `prepareBatchExport`；`:61-112` `exportFilesToDirectory`（`:70-72` tree→document，`:80` `createDocument`，`:93` 失败即删半成品）；`:147` `open`；`:189` `delete`；`:206` `importFile`；`:224` `exportFile`；`:248` `exportToCacheFile`；`:268` `setShellCompatibilityMode`；`:282` `setToolApproval`；`:290` `installRootfs`；`:316` `executeTerminalCommand`；`:369-380` `WorkspaceDetailState` | 导出**单文件/批量到目录**已经会做；缺的是「工作区整包」 |
| 4 | **WorkspaceFileEditorPage** | **已有** | `WorkspaceFileEditorPage.kt:53` 页面；`:69` `remember(id, area, path) { TextFieldState() }`（不把全文塞 saved state，见 t20）；`:172` `WorkspaceWebPreview` | 与模板/备份无耦合，仅作为「文件写入口」提示：模板落地后立刻可在此编辑 |
| 5 | **WorkspaceTerminalPage + Session(+Manager)** | **已有** | `WorkspaceTerminalPage.kt:77`（`WorkspaceDetailViewModel` assisted 工厂）；`WorkspaceTerminalSession.kt:96-107` `prepareWorkspaceTerminalSession`（建 files/tmp、挂载 skills、`RootfsPatcher`）；`:109-112` `workspaceRootfsReady`；`:114` `WorkspaceTerminalSessionClient`（gap 分析 :53 引用的就是这里）；`WorkspaceTerminalSessionManager.kt:38` `observeWorkspace`、`:43` `ensureSession`、`:47` `createTab`、`:88` `closeWorkspace` | **恢复工作区前必须 `closeWorkspace`**：`:88` 是现成的关门把手 |
| 6 | **FilesManager** | **部分** | `FilesManager.kt:513-519` `FileFolders`（`UPLOAD` / `SKILLS` / `BUILTIN_SKILLS` / `FONTS` / `TOOL_OUTPUTS`）；`:38` `saveManagedFromUri`、`:60` `saveManagedFromBytes`、`:76` `saveManagedText`；`:326` `syncFolder` | 它管的是**应用级**受管文件（≠ 工作区）。**没有** `WORKSPACES` 常量、也**不碰** `filesDir/workspaces`（0 命中，命令见 §2.4） |
| 7 | **`workspace` 模块** | **已有（且相当完整）** | `WorkspaceManager.kt:24` `ensureWorkspace`、`:32` `workspaceDir`、`:37` `filesDir`、`:39` `linuxDir`、`:41` `tempDir`、`:43` `hasRootfs`、`:45` `deleteWorkspace`、`:47` `listFiles`、`:54` `readText`、`:60` `writeText`、`:68` `importFile`、`:102` `exportFile`、`:121` `resolveRootfsPath`、`:167` `deleteFile`、`:175` `moveFile`、`:178` `glob`、`:181` `grep`、`:191` `executeCommand`、`:232` `cleanupAllTempDirs`；`:245-258` 常量（含 `ROOTFS_WORKSPACE_DIR = "/workspace"` :252）；`Workspace.kt:3-11` `Workspace`、`:20-23` `WorkspaceStorageArea{FILES,LINUX}`、`:39-44` `WorkspaceConfig`；`WorkspaceFileSystem.kt:37-54` `writeText`（**带 `maxWriteBytes` 上限** :45-47 与越界守卫 :48）、`:56-62` `importBytes`（同名走 `resolveConflict` :59）、`:64-71` `resolveConflict`（`name (1).ext`）、`:174-191` `resolvePath`（越界断言：targetPath 必须等于 rootPath 或以 rootPath + 分隔符 开头，:187-189）、`:193` `resolve` 公开；`RootfsInstaller.kt:19` `install(root,url,onProgress)`、`:101` `extractTar`、`:388` `ArchiveFormat`；`ProotShellRunner.kt` / `WorkspaceShellRunner.kt` / `RootfsPatcher.kt` | **没有**任何「模板」「归档」「备份」概念（§2.4 的 0 命中） |
| 8 | **WorkspaceDocumentsProvider** | **已有（且只暴露 files/）** | `WorkspaceDocumentsProvider.kt:20-34` 类文档（明确「每个 workspace 虚拟目录，对应 `files/` 目录」）；`:52-67` `queryRoots`（`:61` `FLAG_LOCAL_ONLY or FLAG_SUPPORTS_IS_CHILD or FLAG_SUPPORTS_CREATE`）；`:90-114` `queryChildDocuments`（顶层列 workspace，`root` 目录取 `manager().filesDir(ws.root)` :100）；`:116-125` `openDocument`；`:127-145` `createDocument`；`:147-154` `deleteDocument`；`:156-165` `renameDocument`；`:167-183` `copyDocument`；`:185-212` `moveDocument`；`:220-228` `isChildDocument`；manifest 注册：`app/src/main/AndroidManifest.xml:242-250`（authority `${applicationId}.documents`、`exported="true"`、`grantUriPermissions="true"`、`permission=MANAGE_DOCUMENTS`） | **只映射 `files/`**；`linux/`、`tmp/`、以及 `workspaces` 表的元数据都**不经** provider 暴露 ⇒ 见 §5.6 |
| 9 | **RepositoryHiltModule 的 bind mount 表** | **已有（4 条）** | `RepositoryHiltModule.kt:117-141`：`:118` `baseDir = File(context.filesDir, "workspaces")`；`:122` 注释「同一份挂载表既用于 PRoot 的 `-b` 参数，也用于文件工具的路径解析，避免两处漂移」；`:124-127` `/skills`；`:128-131` `/builtin_skills`；`:132-135` `/tool_outputs`；`:136-139` `/upload` | 挂载表与工作区**正交**：挂载点是跨工作区共享的应用目录，**不属于任何单个工作区** ⇒ 切片 B 不打包它们（见 §5.2） |

### 2.2 现有「备份/恢复」入口（钉死）——三套并存，都不覆盖工作区

**（1）应用级 zip 归档（本地 / WebDAV / S3 共用同一格式与生命周期）**

| 事实 | 证据 |
|---|---|
| 打包：`createBackup(includeDatabase, includeFiles): File` | `BackupManager.kt:37-73` |
| 产物落盘：`cacheDir` 下的临时文件 `backup_<yyyyMMdd_HHmmss>_*.zip` | `BackupManager.kt:38-39` |
| 归档内容：`settings.json` / `database/<db>`（+ wal、shm）/ `upload/**`、`skills/**`、`fonts/**` | `BackupManager.kt:44-46`（settings）、`:47-51`（DB 快照 `DatabaseBackup.createSnapshot`）、`:52-64`（三个文件夹，`:53` 是唯一的文件夹清单） |
| **工作区不在其中** | `BackupManager.kt` 全文 `workspace` **0 命中**（§2.4） |
| 恢复入口：`stageRestore(archive, includeDatabase, includeFiles)`，只接受 `settings.json`、`database/*`、以及 `upload` / `skills` / `fonts` 三种前缀的条目 | `BackupManager.kt:75-139`；条目前缀白名单 `isAttachment` :141-148；重复条目拒绝 :99；每条 `fd.sync()` :106；空归档拒绝 :112；DB 先规范化再跑 Room 迁移校验 :114-124；settings 走 `SettingsJsonMigrator.migrate` :126-132 |
| 恢复在**任何消费者初始化之前**安装（Koin/Room/SettingsRepository 之前） | `BackupManager.kt:163-168` `applyPendingRestore`；唯一调用点 `KhatKitApp.kt:97` |
| 暂存/发布/续跑/回滚：`createStagingDirectory` / `publish` / `apply(journal)` / `rollback` | `PendingRestore.kt:23-27`、`:29-32`、`:34-87`（journal :41-53、逐条安装 :54-66、失败回滚 :69-79、提交 :81-85）、`:89-105` `buildEntries`、`:107-115` `rollback`、`:117-132` `targetFile`（**只接受 `database/` 与 `files/` 两种前缀**）、`:138-148` `resolveInside`（越界守卫）、`:150-157` `writeDurably`（fsync+原子 move）、`:159-163` `move`（`ATOMIC_MOVE`+`REPLACE_EXISTING`） |
| 条目选择是**持久化的枚举**：`DATABASE` / `FILES` | `SettingsRepository.kt:712-715`（`WebDavConfig.BackupItem`）、`S3Config.kt:36-40`（`S3Config.BackupItem`）；UI 侧 `BackupViewModel.kt:49` `localBackupItems = …entries.toList()`、`:62-64` `updateLocalBackupItems` |
| 远端与本地都只是它的壳 | `WebDavSync.kt:105-113`（`prepareBackupFile` / `restoreFromBackupFile` → `createBackup` / `stageRestore`）、`S3Sync.kt:97-105`（同构）；`BackupViewModel.kt:87-113`（`backup` / `exportToFile` / `restoreFromLocalFile`） |
| 本地导出/导入走 SAF | `ImportExportTab.kt:73-75` `ActivityResultContracts.CreateDocument("application/zip")`、`:110-112` `OpenDocument()`、`:84-88` 拷贝到用户选的位置、`:120-133` 先落临时文件再恢复 |
| 入口页面 | `RouteActivity.kt:449` `entry<Screen.Backup>`；`Screen.Backup` 定义 `:729`；页面 `app/src/main/java/heizige/kk/khatkit/app/feature/backup/BackupPage.kt` |

**（2）系统级自动备份（Android Auto Backup）**

| 事实 | 证据 |
|---|---|
| 开启 | `app/src/main/AndroidManifest.xml:66` `android:allowBackup="true"` |
| API<31 规则：**只** include `file` 域的 `upload/` | `app/src/main/res/xml/backup_rules.xml:8-12` |
| API≥31 规则：`<cloud-backup>` 块**空**（无 include/exclude） | `app/src/main/res/xml/data_extraction_rules.xml:6-12`（`<device-transfer>` 整段被注释掉 :13-18） |
| 两份规则口径不一致 ⇒ **API≥31 上 `filesDir/workspaces/**` 可能落在系统云备份的默认集合内** | **推断**（未实机验证）；依据是上面两行 + Android 对 `<cloud-backup>` 的默认语义。验证步骤见 §7-R6 |

**（3）数据库快照 helper**：`DatabaseBackup.kt`（`createSnapshot` / `normalize` / `checkpoint` / `removeSidecars` / `ARCHIVE_DATABASE` / `WAL` / `SHM`），被 `BackupManager.kt:49/50/92-94/115/119/123` 使用。相关测试 `app/src/androidTest/java/heizige/kk/khatkit/app/core/data/sync/{BackupManagerTest,DatabaseBackupTest}.kt`（**设备侧**）。

### 2.3 「不得重复实现」清单（切片 B 必须复用，不得另起）

| 能力 | 已有实现（复用点） | 切片 B 的用法 |
|---|---|---|
| zip 打包/解包 | `BackupManager.kt:150-154` `addFile`、`:43-65` `ZipOutputStream` 用法 | 同构复用写法（工作区作用域自己一份小 helper） |
| 越界路径守卫 | `PendingRestore.kt:138-148` `resolveInside`（JVM 已被 `PendingRestoreTest.kt` 覆盖，9 个 `@Test`） | **直接调用**，不要重写一份 |
| 暂存 + 原子替换 + 失败回滚 | `PendingRestore.kt:23-32/107-115/159-163`；`SkillManager.kt:166-209`（staging 目录 :169、`writeBytes` :176、`renameTo` :183/:186、catch 内 backup 回滚 :197-199） | 参照同一形状实现工作区目录的「staging → 换入 → 失败回滚」 |
| 大小/条数上限 | `WorkspaceConfig.maxWriteBytes/maxReadBytes`（`Workspace.kt:39-44`）+ `WorkspaceFileSystem.kt:45-47` 强制；`WorkspaceRepository.kt:354` `MAX_PREVIEW_BYTES = 512KB` | 归档器自己定上限（见 §5.5） |
| 同名冲突命名 | `WorkspaceFileSystem.kt:64-71` `resolveConflict`（`name (1).ext`） | 恢复时若选择「不覆盖」，沿用同一命名法 |
| SAF 三件套 | `ImportExportTab.kt:73-75/110-112`；`WorkspaceDetailPage.kt:124-150` | 只加 launcher，不引入新库 |
| 名字唯一性 | `WorkspaceRepository.kt:95-98` `isNameTaken` + `:63-65` `require` | 导入工作区时的重名处理（§5.4） |
| 元数据行读写 | `WorkspaceDAO.kt:11-32`、`WorkspaceEntity.kt:18-38` | 导入时 `upsert`，需要时重写 `id`/`root` |
| 恢复后自愈 | `WorkspaceRepository.kt:34-55` `checkIntegrity`（目录缺失→`BROKEN` :42-44；rootfs 缺失→`DISABLED` :48-52） | **不打包 rootfs 的兜底就靠它** |

### 2.4 「没有」的证据（0 命中，可复现）

| 断言 | 命令 | 结果 |
|---|---|---|
| 工作区代码里没有任何模板概念 | `grep -rn "template" app/src/main/java/heizige/kk/khatkit/app/feature/workspace/ workspace/src/main/java/` | 0 命中 |
| 工作区代码里没有任何备份/同步/回滚概念 | `grep -rniE "\b(backup\|restore\|snapshot\|rollback\|sync)\b" app/src/main/java/heizige/kk/khatkit/app/feature/workspace/ workspace/src/main/java/` | **0 命中（加词边界才对）**。⚠️ 不加 `\b` 会得 4 处**假阳性**：`WorkspaceDetailPage.kt:67/:838` 的 `AsyncImage` 与 `WorkspaceShellRunner.kt:120/:143` 的 `synchronized` 都含子串 `sync`，与备份无关。t31 初次盘点时用的就是无边界版，特此更正 |
| 应用级备份完全不覆盖工作区 | `grep -rn "workspace\|Workspace" app/src/main/java/heizige/kk/khatkit/app/core/data/sync/BackupManager.kt` | 0 命中 |
| `FileFolders` 没有 WORKSPACES | `grep -n "WORKSPACE" app/src/main/java/heizige/kk/khatkit/app/core/data/files/FilesManager.kt` | 0 命中（`FileFolders` 块 `:513-519` 只有 5 个常量：UPLOAD / SKILLS / BUILTIN_SKILLS / FONTS / TOOL_OUTPUTS；文件里另有 1 个 `private const val TAG` 在 `:35`，与目录常量无关） |
| 备份条目枚举没有 WORKSPACES | `grep -rn "enum class BackupItem" -A 4 app/src/main/java/heizige/kk/khatkit/app/core/data/datastore/SettingsRepository.kt app/src/main/java/heizige/kk/khatkit/app/core/data/sync/s3/S3Config.kt` | 两处枚举（`SettingsRepository.kt:712-715`、`S3Config.kt:37-40`）各只有 `DATABASE` / `FILES`（只 grep `core/data/sync/` 会漏掉前一处） |
| 没有 workspace/backup 专用的 Miuix 变体文件 | `git ls-files \| grep -i miuix \| grep -iE "workspace\|backup"` | 0 命中（双风格是**运行时**决的，见 §4.5） |
| 没有工作区设计文档 | `ls docs/ \| grep -iE "workspace\|template\|backup" \| grep -v workspace-templates-and-backup-design.md` | 0 命中（本文件是第一个；不过滤就会命中本文件自身） |
| 模板/归档 JVM 测试不存在 | `git ls-files \| grep -iE "test.*(workspace\|Template\|Archive)"` | 实际输出 5 个文件：`workspace/src/test/…/{ExampleUnitTest,RootfsInstallerTest,RootfsPathResolutionTest}.kt`、`workspace/src/androidTest/…/ExampleInstrumentedTest.kt`、`app/src/androidTest/…/browser/BrowserArchiveTest.kt`（最后一个是被 `Archive` 命中的**浏览器**归档测试，与工作区无关）。⇒ 工作区模块**没有**模板/归档测试 |

---

## 3. 与 Operit2 的设计对照（逐处署名）

> 来源：`AAswordman/Operit2` @ `9ae1e558a66f96d81ff95a499bd39bda7da6c122`（`/tmp/operit2`）。
> **性质列取值域**：`原样复用`（O 的代码/资源逐字进本仓）/ `改写`（取机制，重写为 Kotlin/Compose）/
> `参照`（只读行为与交互，不产生衍生代码）。**本表 6 条当前全部为 `参照` + 我方 `改写` 或自撰，
> 「原样复用」为 0 处**；下游若产生原样复用，必须在此表新增一行（仓库 + SHA + 性质 + 改动点）。

| # | O 侧事实（file:line，只读核对） | 性质 | 我方采纳 |
|---|---|---|---|
| 1 | 内置模板以构建期资源随二进制发布：`core/crates/runtime/application/assets/workspace_templates/` 下 **7 个**模板目录（`go`/`java`/`node`/`office`/`python`/`typescript`/`web`，共 29 个文件、约 120K）；`core/crates/runtime/application/build.rs:231-248` 生成 `WORKSPACE_TEMPLATE_ASSETS` 静态表；`core/crates/runtime/application/src/ui/features/chat/webview/workspace/WorkspaceTemplateAssets.rs:1` 只是 `include!` | **参照**（此表由 t31 只读统计得出） | **改写**：用 Android assets + 解压（本仓已有同形实现，见 §4.2）。不引入构建期代码生成 |
| 2 | 模板落地 = 把 `<template>/**` 逐个写入工作区，并把名为 `gitignore` 的段改写为 `.gitignore`（`.../chat/webview/workspace/WorkspaceUtils.rs:40-41`、`:118-140`、`:146-158` `templateDestinationPath`） | **参照** | **改写**：同样的「aapt 不吃点开头文件」约束**本仓已独立记录在** `BuiltinSkills.kt:14`（注释：「aapt 默认会忽略以 `.` 开头的文件和以 `_` 开头的目录」）。故本仓模板里点文件统一存成 `gitignore`、落地时改名 `.gitignore`（见 §4.3 `renameOnExtract`） |
| 3 | 模板选择**不是用户挑选**，而是按项目类型自动映射的**调用点**在 `WorkspaceUtils.rs:36-42`（`createDefaultWorkspace` 里 `projectType.templateName()` → `copyTemplateFiles`），映射表 `ProjectType::templateName` 在 `:84-98`（`:96` `ProjectType::BLANK => None` ⇒ BLANK 不套模板） | **参照** | **部分采纳 + 反转**：MVP 做**显式选择**（用户挑），并把「按内容自动推荐模板」列为下一步（O 的自动映射需要一套项目类型判定，MVP 不值当） |
| 4 | O 的 `WorkspaceBackupManager` 是**消息时间戳作用域**的快照/回滚：`BackupManifest { timestamp, files, fileStats }`（`.../chat/webview/workspace/WorkspaceBackupManager.rs:42-47`）、按 `{messageTimestamp}.json` 落 manifest（`:207-208/281-283`）、内容对象目录 `objectsDir`、`AIToolHook` 在每次 AI 工具调用前挂钩（`:736-`）、并提供 `previewChanges` / `previewChangesForRewind`（`:135/145`）与 `WorkspaceFileChange/ChangeType` 差异模型（`:49-59`） | **参照** | **本切片不做**（那是「改动回滚/文件同步」= gap 分析 §5 :294 的 P1）。**仅采纳**「归档带 manifest + 版本号」这一点 → §5.3。**这是对 gap 分析映射的一处订正**：§5 :292 把 `WorkspaceBackupManager.rs` 挂在「工作区模板 + 备份」这位上，行为层面它其实属于 P1 那一族 |
| 5 | O 的 workspace 设置面板只做「已绑定/未绑定工作区列表 + 删除未绑定」，**没有模板 UI、也没有备份 UI**（`apps/flutter/app/lib/ui/features/settings/workspace/WorkspaceSettingsPanel.dart`，502 行；对 template / backup / restore / export 四词做**词边界** grep = **0 命中**，完整命令见 §11 第 9 条 —— 模式里**不能**带 `import`：该文件 7 处 `import` 全是 Dart import 语句，会假阳性） | **参照** | 我方入口**自行设计**（§4.4 / §5.4）：不背 O 的 UI 结构包袱 |
| 6 | O 的 workspace 创建/重置入口在 Rust 侧（`.../chat/webview/workspace/WorkspaceUtils.rs:22-27/48-54`），且与「每会话一个工作区」绑定（`getWorkspaceRelativePath(chatId)` :59-61） | **参照** | **不采纳**：本仓工作区是**应用级资源**（`workspaces` 表 + 助手可绑定 `assistant.workspaceId`，见 `WorkspaceRepository.kt:318-330`），不是每会话一个 |

---

## 4. 切片 A：工作区模板（MVP）

### 4.1 做什么 / 不做什么

**做**

- A1 定义模板格式（`template.json` + `files/**` 载荷）与目录约定。
- A2 内置模板：以 `app/src/main/assets/workspace_templates/<id>/` 随 APK 发布，首次启动解压到
  `filesDir/workspace_templates/<id>/`（形态照 `BuiltinSkills.kt:25-48`）。
- A3 用户模板：`filesDir/workspace_templates_user/<id>/`，与内置**同名 id 时用户优先**
  （覆盖规则照 `BuiltinSkills.kt:81-87` `mergeWithBuiltinSkills`）。
- A4 模板变量：仅支持白名单两个 —— `${workspace_name}`、`${date}`，作用域限**文本文件内容**。
- A5 新建工作区时选模板（默认「空白」），落地到该工作区 `files/`。
- A6 落地失败即回滚（删目录、不写 `workspaces` 行）。

**不做**

- 不做「套用到已有工作区」（MVP 只在新工作区创建时应用 ⇒ **同名冲突在 MVP 里不存在**，见 §4.6）。
- 不做二进制模板文件（MVP 模板只允许文本；见 §4.3 的 `textOnly` 约束与理由）。
- 不做模板市场/远端下载、不做模板版本升级迁移、不做「把现有工作区另存为模板」。
- 不做项目类型自动识别（§3-#3）。
- 不做模板内的 rootfs 捆绑；`rootfsUrl` 只允许指向既有安装流程（§4.3）。

### 4.2 存储形态（三处 + 一处镜像约束）

| 位置 | 路径 | 角色 |
|---|---|---|
| 内置源（只读，随 APK） | `app/src/main/assets/workspace_templates/<id>/…` | 发布形态。**先例**：`app/src/main/assets/builtin_skills/skill-creator/` |
| 内置落地（可读，启动解压） | `filesDir/workspace_templates/<id>/…` | 与 `BuiltinSkills` 的 `filesDir/builtin_skills/` 同形（`SkillManager.kt:24` `getBuiltinSkillsDir`、`KhatKitApp.kt:131` + `:206-211` 启动调用、`SkillManager.kt:38-47` 幂等 + 时间戳版本文件） |
| 用户模板（可写） | `filesDir/workspace_templates_user/<id>/…` | 与内置合并时**用户优先**（`BuiltinSkills.kt:81-87` 的规则） |

**为什么是「解压到 filesDir」而不是「直接用 AssetManager」**：模板落地要写文件、要被用户模板覆盖、
要在 `filesDir` 里可枚举；`BuiltinSkills.kt:25-48` 已经把「时间戳版本文件 + staging 目录 + rename 换入」
的坑走过一遍（`:34-47`），照抄这个形状最省事、也最稳。

**幂等与版本**：`.version` 文件写 `packageManager.getPackageInfo(...).lastUpdateTime`
（`BuiltinSkills.kt:26-31`）。模板内容变了但时间戳没变的情况（同一次安装内改 assets）在开发期不会出现，
发布后也不会 —— 与技能一致。

### 4.3 模板定义格式

```
filesDir/workspace_templates/<id>/
├── template.json          # 元数据
└── files/                 # 载荷：按相对路径原样落地到工作区的 files/
    ├── README.md
    ├── gitignore          # 落地时改名 .gitignore（见 renameOnExtract）
    └── src/main.py
```

`template.json`（`kotlinx.serialization`，用本仓既有的 `JsonInstant`；`ignoreUnknownKeys` +
`encodeDefaults` 口径见 `docs/architecture-map.md` 记录的 `core/util/Json.kt`）：

| 字段 | 类型 | 必填 | 说明 |
|---|---|---|---|
| `schemaVersion` | Int | ✔ | 从 1 起。读到更大值 ⇒ 拒绝该模板（不猜） |
| `id` | String | ✔ | `[a-z0-9][a-z0-9-]*`，与目录名一致 |
| `name` | String | ✔ | 展示名（UI 直接显示，**不做 7 语言翻译**：模板名属内容不属界面文案） |
| `description` | String? | ✖ | 展示用一句话 |
| `renameOnExtract` | Map<String,String> | ✖ | 默认 `{"gitignore": ".gitignore"}`；段级匹配（照 §3-#2 的行为） |
| `variables` | List<String> | ✖ | 允许的变量名子集，取值只能在 `workspace_name` / `date` 内；缺省 `[]` |
| `rootfsUrl` | String? | ✖ | 非空则创建后走既有 `WorkspaceRepository.installRootfs`（`WorkspaceRepository.kt:116-145`）。**MVP 内置模板一律为 null** |
| `shellCompatibilityMode` | Boolean | ✖ | 默认 false；落地后写该工作区行（`WorkspaceDAO.kt:27-28`） |

约束：

- **`textOnly`（安全换便利）**：载荷只允许 UTF-8 文本，单文件 ≤ `WorkspaceConfig.maxWriteBytes`
  （默认 2MiB，`Workspace.kt:39-44`）。落地走 `WorkspaceManager.writeText`（`WorkspaceManager.kt:60-66`），
  于是**白拿**两件事：越界守卫（`WorkspaceFileSystem.kt:187-189`）与大小上限（`:45-47`）。二进制模板
  留待以后（那时需要一条绕过 `writeText` 的、带同样守卫的字节通道）。
- 复用 `WorkspaceFileSystem.kt:59` 的**同名不覆盖**语义：MVP 在空目录里落地，正常不会撞名；若撞上
  （用户模板自带重名），按 `overwrite = false` 处理并**报错回滚**，不静默改名。

### 4.4 新建工作区的入口与交互（双风格）

**流程**：`WorkspacePage` FAB（`WorkspacePage.kt:76`）→ `EditWorkspaceDialog`（`:260-303`）→ 新增一个
「模板」选择行 → 确认 → `vm.create(name, templateId)` → `WorkspaceRepository.create`。

| 改动点 | 文件 / 符号 | 双风格影响 |
|---|---|---|
| 对话框加模板行 | `WorkspacePage.kt:260` `EditWorkspaceDialog`（在 `:275-286` 的名称输入下方） | **零风格分支**：用 `KedgeOutlinedTextFieldWithSlots`（现用）或 `KedgeSingleChoiceSegmentedRow`（`ImportExportTab.kt:46` 已在用）。`AppAlertDialog`（`WorkspacePage.kt:271`）本身已双风格 |
| 创建调用带模板 | `WorkspacePage.kt:110` `vm.create(name)` → `WorkspaceViewModel.kt:27-31` → `WorkspaceRepository.kt:59` `create` | 无 UI |
| 模板列表来源 | 新 `WorkspaceTemplateCatalog`（见 §8-Step1） | 无 UI |

**双风格结论（硬约束 #3 的回答）**：本仓的工作区/备份页面**没有 MD3 与 Miuix 两份文件**
（§2.4 命令：`git ls-files | grep -i miuix | grep -iE "workspace|backup"` = 0 命中）。风格是**运行时**由
`LocalKedgeStyle.current`（`KedgeStyle.MD3Exp` / `KedgeStyle.Miuix`）在共享组件内部决定，证据：

- `app/src/main/java/heizige/kk/khatkit/app/core/ui/components/ui/miuix/KedgeSettingsPageScaffold.kt:61-67`
  （只给 MD3 补 `nestedScroll`，注释写明 Miuix 下会抢折叠增量）
- 同目录 `KedgeForm.kt:40-50`（`when (LocalKedgeStyle.current)` 起，Miuix 分支 `:50`）、`:113`（另一行两分支）
- 同目录 `KedgeTabRow.kt:40-64`（`:41` Miuix、`:64` MD3 两分支）
- `app/src/main/java/heizige/kk/khatkit/app/core/ui/components/nav/BackButton.kt:34-42`
  （注意它在 **`nav/`** 包，不在 `ui/miuix/`；`:34` `when`、`:35` Miuix、`:42` MD3）

⇒ **切片 A 的两套风格接线点就是「同一个改动点」**：只要新 UI 用 `KedgeCard` / `KedgeForm` 系行 /
`AppAlertDialog` / `KedgeTextButton`，MD3 与 Miuix 自动各得一套；**唯一要避免**的是自己手写 `Row` +
`Surface` 拼一行（那样只有一个风格）。模板选择行若用分段控件，需确认 Miuix 分支存在
（`KedgeSingleChoiceSegmentedRow` 已在 `ImportExportTab.kt:46` 用过，可复用）。

### 4.5 与现有目录约定的关系（不变量）

| 不变量 | 依据 |
|---|---|
| 模板只写 `filesDir/workspaces/<root>/files/**`，绝不碰 `linux/`、`tmp/` | `WorkspaceManager.kt:37-41` 区分三区；rootfs 由 `RootfsInstaller` 独占（`:24-30`） |
| 落地即等于在 PRoot 里可见为 `/workspace/**` | `WorkspaceManager.kt:133-138`（`ROOTFS_WORKSPACE_DIR = "/workspace"` 映射到 `filesDir(root)`），常量 `:252` |
| 模板不得写 bind mount 目标（`/skills` `/builtin_skills` `/tool_outputs` `/upload`） | 那些是**应用级**共享目录（`RepositoryHiltModule.kt:124-139`），与工作区正交。落地的根是 `filesDir(root)`，天然写不到它们 |
| 模板落地不改变 bind mount 表 | 挂载表只在 DI 里构造一次（`RepositoryHiltModule.kt:117-141`），切片 A 不动它 |

### 4.6 同名冲突与覆盖规则

| 场景 | 规则 | 依据 |
|---|---|---|
| 模板 id：内置 vs 用户 | 用户优先，内置同名者**隐藏**（不是合并） | 照 `BuiltinSkills.kt:81-87` |
| 模板落地时目录内已有同名文件 | `overwrite = false` ⇒ 报错并整体回滚（MVP 目标是空目录，正常不触发） | `WorkspaceFileSystem.kt:49`、`WorkspaceFileSystem.kt:59` |
| 工作区名重复 | 沿用现状：`require(!isNameTaken(...))` 抛错 + UI 侧禁用确认按钮 | `WorkspaceRepository.kt:63-65` / `:95-98`；`WorkspacePage.kt:269/291` |
| 模板未找到 / 载荷非法 | 创建失败并**不留下半成品工作区**：删掉刚建的目录，不写 `workspaces` 行 | 见 §8-Step2 的顺序约束（先落地、后 `dao.upsert`） |

### 4.7 现有代码完成度判定

| 项 | 状态 |
|---|---|
| 概念（模板定义/目录/变量/选择/落地/覆盖规则） | **0 / 6** |
| 可复用底层（assets 解压模式、用户优先合并、原子写+越界守卫、创建入口与对话框、分段控件、幂等版本戳） | **6 / 6**（§2.3 + §4.2 已逐条给 file:line） |

⇒ 切片 A 是**纯新增**，不涉及任何既有行为回归；风险集中在「新代码路径校验」而不在「改动老逻辑」。

---

## 5. 切片 B：工作区级备份 / 恢复（MVP）

### 5.1 做什么 / 不做什么

**做**

- B1 定义工作区归档格式（`.zip` + `manifest.json` + `files/**` 载荷）。
- B2 导出：把**一个**工作区的 `files/**` + 该工作区的元数据打成 zip，写到用户通过 SAF 选的位置。
- B3 导入：从用户选的 zip 恢复成**新**工作区（MVP 不允许覆盖同名工作区，见 §5.4）。
- B4 原子性：staging + 换入 + 失败回滚，绝不留半个工作区。
- B5 安全：越界/重复/超限/zip 炸弹防护；解包只在目标工作区目录内落文件。
- B6 跨版本：`manifest.schemaVersion` + 拒绝未来版本 + 旧版本迁移钩子。

**不做**

- 不覆盖既有工作区（MVP 只「恢复为新工作区」）。
- 不打包 rootfs（`linux/`）、不打包 `tmp/`、不打包 bind mount 目录（§5.2）。
- 不做 O 那种「按消息时间戳自动快照 + 回退」（§3-#4）。
- 不做多设备同步 / Space / 绑定（§9）。
- 不改系统自动备份规则（**建议但需拍板**，见 §7-R6 与 §10-P7）。
- 不做增量/去重/加密（zip 明文，用户可自行加密外层）。

### 5.2 备份范围（含明确排除项与理由）

| 范围 | 内容 | 证据 |
|---|---|---|
| 打包 | `workspaces/<root>/files/**`（递归，含空目录标记） | `WorkspaceManager.kt:37` `filesDir` |
| 打包 | 元数据：`name`、`root`、`shellCompatibilityMode`、`toolApprovals`、`createdAt`、`updatedAt`、`lastAccessAt` | `WorkspaceEntity.kt:18-38` |
| **不打包** | `linux/**`（rootfs） | 可由 URL 重下（`RootfsInstaller.kt:19`）；体积大（tar.xz 展开）；且恢复后有兜底：`checkIntegrity` 会把 rootfs 缺失的 `READY/INSTALLING` 打回 `DISABLED`（`WorkspaceRepository.kt:48-52`） |
| **不打包** | `tmp/**` | 纯临时（`WorkspaceManager.kt:232-243` 就是清它的），不该进归档 |
| **不打包** | `/skills` `/builtin_skills` `/tool_outputs` `/upload` | 是**应用级**挂载点（`RepositoryHiltModule.kt:124-139`），不属任何单个工作区；其中 `upload`/`skills`/`fonts` 已由应用级备份覆盖（`BackupManager.kt:53`） |
| **不打包** | `workspaces` 表的其它工作区 | 工作区作用域 |
| 明确写进 manifest 的「未打包项」 | `excluded: ["linux", "tmp", "bind_mounts"]` | 让用户/后续版本能看出归档**故意**不含 rootfs，避免误判为坏包 |

### 5.3 产物格式与落盘位置

```
<用户选定的 zip>
├── manifest.json
└── files/                      # 载荷根；相对路径 = 工作区 files/ 下的相对路径
    ├── README.md
    └── src/main.py
```

`manifest.json`：

| 字段 | 类型 | 必填 | 说明 |
|---|---|---|---|
| `schemaVersion` | Int | ✔ | 从 1 起 |
| `kind` | String | ✔ | 固定 `"khatkit.workspace"`；**导入时校验**，避免拿应用级备份 zip 来导入 |
| `createdAt` | Long | ✔ | 导出时刻 |
| `appVersionName` / `appVersionCode` | String / Int | ✔ | 诊断与兼容判断用（`app/build.gradle.kts:28-29` 是现值来源） |
| `workspace.name` | String | ✔ | 导入时的候选名（重名则加后缀，§5.4） |
| `workspace.root` | String? | ✖ | 原 root；**仅记录**，导入不沿用（新工作区新 UUID） |
| `workspace.shellCompatibilityMode` | Boolean | ✔ | 导入时写回（`WorkspaceDAO.kt:27-28`） |
| `workspace.toolApprovals` | Map<String,Boolean> | ✖ | 现为 JSON 串（`WorkspaceEntity.kt:34`）；归档里用**结构化 map**，读写各做一次转换 |
| `workspace.createdAt / updatedAt / lastAccessAt` | Long? | ✖ | 尽量保留；`createdAt` 缺失则用导入时刻 |
| `excluded` | List<String> | ✔ | 见 §5.2 |
| `fileCount` / `totalBytes` | Int / Long | ✔ | 导入前预检（配合 §5.5 的上限） |

**为什么 `files/` 前缀**：与 `PendingRestore.targetFile`（`PendingRestore.kt:117-132`）的 `files/`
约定同形，便于把工作区归档**将来**并进应用级归档时少一层转换（§8-Step4-可选）。

**落盘位置**：导出时先写 `context.cacheDir` 临时文件（照 `BackupManager.kt:38-40` 的做法），
SAF 写成功后删除临时文件（照 `ImportExportTab.kt:84-92`）。导入时同样先落 `cacheDir` 临时文件
（照 `ImportExportTab.kt:120-133`）。

### 5.4 导入导出路径（复用现有选择器）

| 动作 | 复用 | 证据 |
|---|---|---|
| 导出 | `ActivityResultContracts.CreateDocument("application/zip")` | `ImportExportTab.kt:73-75` 已在用；`WorkspaceDetailPage.kt:138-145` 的 `CreateDocument` 是同一模式的既有接线 |
| 导入 | `ActivityResultContracts.OpenDocument()`（或带 `arrayOf("application/zip")` 过滤） | `ImportExportTab.kt:110-112`；`WorkspaceDetailPage.kt:124-136` 已有 `OpenDocument` 接线 |
| 是否用 `WorkspaceDocumentsProvider` | **不用** | provider 只映射 `files/`（`WorkspaceDocumentsProvider.kt:20-34/90-114`），无法表达元数据与整包语义；用它当导入源会引入「用户先得在系统文件管理器里找到我们的包」的绕路（§5.6） |
| 重名处理 | 复用 `isNameTaken`（`WorkspaceRepository.kt:95-98`）：重名则 `name (2)` 递增直到可用（命名法与 `WorkspaceFileSystem.kt:64-71` 一致） | — |
| 恢复顺序 | ① 解析+校验 manifest → ② 解到 staging → ③ `closeWorkspace`（`WorkspaceTerminalSessionManager.kt:88`）→ ④ 换入目录 → ⑤ `dao.upsert` 新行 | 与 `WorkspaceRepository.create`（`:59-77`）的「先文件后行」一致；失败则回滚到 ③ |

### 5.5 写入原子性、失败回滚、上限

**形状**（结构参照 `SkillManager.kt:166-209` 与 `PendingRestore.kt:23-32/107-115/159-163`）：

1. 解包到 `filesDir/workspaces/.staging-<uuid>/`（同分区，保证 `renameTo`/`ATOMIC_MOVE` 有效）。
2. 每条目：`resolveInside(stagingFilesDir, rel)`（**复用** `PendingRestore.kt:138-148`）⇒ 越界立即失败。
3. 全部落地 + `manifest` 校验通过后，把 staging 换入 `workspaces/<newRoot>/`；
   换入用 `File.renameTo`（同分区；`WorkspaceDocumentsProvider.kt:202-207` 证明了工作区在应用私有目录内
   `renameTo` 可行，失败才退化为复制+删除）。
4. 任一步失败：删 staging，**不写** `workspaces` 行（目录与行都不留痕）。
5. 若目标工作区已存在（不该发生，作为不变量断言）：把原目录移到 `*.bak-<ts>`，换入成功后再删 `.bak`；
   换入失败则把 `.bak` 移回（照 `SkillManager.kt:182-201` 的 backup 回滚）。

**上限与防护**（MVP 取值，可调）：

| 防护 | 取值 | 依据/先例 |
|---|---|---|
| 单文件大小（导出侧不做特殊处理；导入侧落盘前检查） | ≤ `WorkspaceConfig.maxWriteBytes`（2MiB） | `Workspace.kt:39-44` |
| 归档条目数 | ≤ 5000 | 先例：`WorkspaceConfig.maxListEntries = 500`（`Workspace.kt:42`）是「一次列出」的上限，归档另设更宽松值 |
| 解压总字节 | ≤ 512MiB 且 ≤ `manifest.totalBytes` 的 1.1 倍 | zip 炸弹防护；`RootfsInstaller.kt:274` `copyExactly` 是「按声明长度精确拷贝」的先例 |
| 条目名 | 拒绝绝对路径、`..`、`\`、空段、重复条目 | **复用** `PendingRestore.resolveInside`（`:138-148`）+ 重复条目拒绝先例 `BackupManager.kt:99` |
| `kind` 校验 | 必须 = `khatkit.workspace` | 与 `BackupManager.kt:90-98` 的前缀白名单同构 |

### 5.6 与 `WorkspaceDocumentsProvider` 权限模型的关系

| 事实 | 影响 |
|---|---|
| provider 只映射每个工作区的 `files/`（`WorkspaceDocumentsProvider.kt:90-114`，`:100` 取 `filesDir`） | 工作区**文件**其实已经可以从系统文件管理器读写；备份切片的价值在「整包 + 元数据 + 原子恢复」，不在「拿到文件」 |
| provider 声明 `FLAG_SUPPORTS_CREATE`（`:61`）、`exported="true"` + `grantUriPermissions`（`app/src/main/AndroidManifest.xml:244-245`） | 获授权的第三方应用**能写**工作区文件 ⇒ 它们写的文件**会进归档**（因为归档就是读 `files/`）。这是既有事实，本切片不改变、也不收紧 |
| 本切片**不**扩大 provider 面 | 不新增 root（不暴露 `linux/`、`tmp/`）、不暴露 `workspaces` 表元数据、不新增 provider 方法 |
| 导出/导入**不经过** provider | 见 §5.4；`MANAGE_DOCUMENTS` 权限与 provider 授权模型均保持原样 |

### 5.7 跨版本兼容

| 情形 | 策略 | 先例 |
|---|---|---|
| 归档版本更旧 | 走一个显式的 `WorkspaceArchiveMigrator`（MVP：v1 无历史，只留钩子与测试位） | `BackupManager.kt:128` 用 `SettingsJsonMigrator.migrate` 处理旧 settings |
| 归档版本更新 | **拒绝**并给出明确错误（不猜字段语义） | Room 迁移/DB 校验失败即拒绝：`BackupManager.kt:114-124` |
| `kind` 不匹配 | 拒绝 | `BackupManager.kt:90-98` |
| rootfs 未随包 | 允许：导入后 `shellStatus = DISABLED`，由用户按需重装（UI 入口：`WorkspaceDetailPage.kt:300` 调 `:557` `InstallRootfsDialog` → `WorkspaceDetailViewModel.kt:290` `installRootfs` → `WorkspaceRepository.kt:116-145`） | `WorkspaceRepository.kt:48-52`（rootfs 缺失→`DISABLED`） |
| 元数据字段缺失 | 用默认值补（`toolApprovals` 缺省 `{}`：`WorkspaceEntity.kt:34-35`） | 同上 |

### 5.8 现有代码完成度判定

| 项 | 状态 |
|---|---|
| 工作区级产品行为（归档格式 / 导出 / 导入 / 恢复 / UI 入口 / 重名 / 跨版本 / 权限口径） | **0 / 8** |
| 底层机制可复用 | **7 / 8**：zip 打包（`BackupManager.kt:150-154`）、越界守卫（`PendingRestore.kt:138-148`，JVM 已覆盖）、暂存+原子换入+回滚（`PendingRestore.kt`/`SkillManager.kt:166-209`）、SAF 三件套（`ImportExportTab.kt`/`WorkspaceDetailPage.kt:124-150`）、元数据读写（`WorkspaceDAO`/`WorkspaceEntity`）、最后手段自愈（`WorkspaceRepository.kt:34-55`）、终端关门（`WorkspaceTerminalSessionManager.kt:88`）。缺的第 8 项 = 「把上面这些编排成工作区作用域」的那层胶水 |

---

## 6. 测试策略（JVM 可测 vs 必须真机）

### 6.1 JVM 单测（不需要设备，能覆盖绝大部分风险）

放 `app/src/test/java/heizige/kk/khatkit/app/...`（先例：`app/src/test/…/PendingRestoreTest.kt`，9 个 `@Test`）与
`workspace/src/test/...`（先例：`RootfsInstallerTest.kt`、`RootfsPathResolutionTest.kt`）。

| 测试 | 覆盖 |
|---|---|
| `WorkspaceTemplateCatalogTest` | 内置/用户合并与「用户优先」（§4.6）、`template.json` 反序列化、`schemaVersion` 过大即拒、`id` 合法性、缺 `name` 即拒 |
| `WorkspaceTemplateVariablesTest` | 变量白名单：允许 `${workspace_name}`/`${date}`；`${任意}` / `${` 未闭合 / `$${}` 不被替换；`variables` 未声明时一律不替换 |
| `WorkspaceTemplateRenameTest` | `gitignore` → `.gitignore` 段级改名（含嵌套目录段） |
| `WorkspaceTemplateApplyTest` | 落地到临时目录：目录/文件/空目录、`overwrite=false` 撞名报错、失败后目录被清理 |
| `WorkspaceArchiveFormatTest` | manifest 往返、`kind` 校验、`schemaVersion` 拒绝、`excluded` 存在性 |
| `WorkspaceArchiveRoundTripTest` | pack → unpack 字节级相等（含嵌套目录、空目录、UTF-8 与非 ASCII 文件名、CRLF 保留） |
| `WorkspaceArchiveGuardTest` | `..`、绝对路径、`\`、空段、重复条目、超条数/超字节、`totalBytes` 不匹配 ⇒ 全部拒绝且**不落任何文件** |
| `WorkspaceArchiveRestoreTest` | 在临时目录上模拟 换入/回滚：中途注入失败 ⇒ 原目录完好；重名 ⇒ `name (2)` |

### 6.2 必须真机（JVM 覆盖不到）

| 项 | 为什么必须真机 | 建议步骤 |
|---|---|---|
| SAF 导出（`CreateDocument`） | 依赖系统 DocumentsUI 与用户选点 | 详情页 →「导出工作区」→ 选目录 → 完成后用系统文件管理器打开 zip 确认存在且可解 |
| SAF 导入（`OpenDocument`） | 同上 + 权限授予 | 选上一步的 zip → 确认新工作区出现、文件数与内容一致 |
| `WorkspaceDocumentsProvider` 可见性 | 第三方 app 与系统文件管理器行为 | 系统「文件」应用 → 找到本 app 的 provider root → 确认只看到 `files/`（看不到 `linux/`） |
| 恢复时终端正在运行 | 文件句柄/PRoot 挂载 | 打开终端 tab → 在该工作区执行导出/导入 → 断言终端被关闭且恢复成功 |
| 大工作区（>100MiB） | 内存/耗时/ANR | 预置大目录 → 导出 → 观察内存与耗时（不要 OOM） |
| rootfs 缺失兜底 | 需要真实 `linux/` 目录 | 把一个有 rootfs 的工作区「导出→删掉→导入」→ 断言 `shellStatus` 变 `DISABLED` 且可按 `WorkspaceDetailPage.kt:557` 的对话框重装 |
| 应用级备份回归 | 真机 `BackupManagerTest`/`DatabaseBackupTest` 是 androidTest | 跑 `:app:connectedDebugAndroidTest`（或至少手工跑一次本地备份导出/导入） |
| 系统自动备份口径（§7-R6） | 需 `bmgr`/系统设置 | `adb shell bmgr backupnow <pkg>` + `adb shell dumpsys backup` 观察 `workspaces/` 是否在集合内；再决定是否显式 exclude |

### 6.3 门禁（每步都跑，不新增工具链）

```
./gradlew --offline --max-workers=1 :app:compileDebugKotlin
./gradlew --offline --max-workers=1 :app:testDebugUnitTest
./gradlew --offline --max-workers=1 :workspace:testDebugUnitTest
```

（本仓 16GB 机器的既有约定：串行 + `flock /tmp/kk-gradle.lock` + `--max-workers=1`；见团队运维口径。）

---

## 7. 风险与缓解

| id | 风险 | 影响 | 缓解 |
|---|---|---|---|
| R1 | 模板二进制文件被禁用后，用户仍想放图片/字体 | 功能边界落差 | §10-P2 拍板；后续加一条「带守卫的字节落地通道」 |
| R2 | 模板变量替换被当成通用模板引擎 | 注入/意外替换 | **白名单两变量** + 只在声明了 `variables` 时替换（§4.3）；单测钉死 `$` 边界 |
| R3 | 大工作区归档 OOM | 崩溃 | 全程流式（`copyTo`），绝不整份读进内存；导入前用 `manifest.totalBytes` 预检；上限见 §5.5 |
| R4 | 恢复时终端持句柄 | 换入失败/数据不一致 | 换入前 `closeWorkspace`（`WorkspaceTerminalSessionManager.kt:88`）；失败即回滚 |
| R5 | 半途中断留下半个工作区 | 用户数据观感损坏 | staging + 原子换入 + 回滚（§5.5）；MVP 不做跨重启续跑（应用级那条有 journal，工作区级先不做：单工作区体积小、重试成本低）——**如需一致体验再加** |
| R6 | 系统云备份**已经**包含 `workspaces/**`（API≥31） | 可能超 Auto Backup 默认配额（25MB，平台文档口径）导致**整个系统备份失败**；也与「显式备份」重复 | 先按 §6.2 验证；若成立，则在 `data_extraction_rules.xml` 的 `<cloud-backup>` 里加 `<exclude domain="file" path="workspaces/"/>`（同时确认 `backup_rules.xml` 的口径）——**需拍板**（§10-P7） |
| R7 | 用户拿应用级备份 zip 来导入工作区 | 报错不明确 | `kind` 校验 + 明确错误文案（§5.3/§5.7） |
| R8 | 工作区 `root` 是 UUID，用户看不懂 | UI 观感 | 归档 manifest 里带 `name`；导入时以 `name` 为候选名（§5.4） |
| R9 | 第三方通过 provider 写入的文件被一起归档 | 归档里混入非用户预期内容 | 这是既有事实（§5.6）；manifest 里记 `fileCount/totalBytes` 并在 UI 导出前展示，让用户可判断 |

---

## 8. 实施顺序（4 步，每步可独立编译 + 独立验证）

> 每步的「自证」= 该步自己就能跑的门禁 + 手工/真机步骤；不把验证攒到最后。

### Step 1 — 模板核心 + 内置资产（纯新增，JVM 可测）

| 项 | 内容 |
|---|---|
| 新增文件 | `app/src/main/java/heizige/kk/khatkit/app/core/data/files/WorkspaceTemplateCatalog.kt`（模板模型/解析/合并/落地；名字按 AGENTS.md 的 `Catalog`≈纯函数+无状态，若最终带 IO 则改叫 `WorkspaceTemplateManager`）<br>`app/src/main/java/heizige/kk/khatkit/app/core/data/files/WorkspaceTemplateApplier.kt`（或并入上一个文件）<br>`app/src/main/assets/workspace_templates/{blank,python,web}/template.json` + `files/**`（**自撰**，每个 ≤5 个文件）<br>`app/src/test/java/heizige/kk/khatkit/app/core/data/files/WorkspaceTemplateCatalogTest.kt` + `WorkspaceTemplateVariablesTest.kt` + `WorkspaceTemplateApplyTest.kt` |
| 改动文件 | `app/src/main/java/heizige/kk/khatkit/app/KhatKitApp.kt`（在 `:206-211` `extractBuiltinSkills` 旁边加 `extractBuiltinTemplates()`，`:131` 处调用） |
| 自证 | `:app:testDebugUnitTest` 三个新测试类全绿；`grep -c` 断言内置模板目录数 = 3 |
| 量级 | **~250–350 行**（含 3 个模板 ~15 个文件） |

### Step 2 — 创建时选择模板（UI 接线，双风格）

| 项 | 内容 |
|---|---|
| 改动文件 | `WorkspacePage.kt:260-303`（`EditWorkspaceDialog` 加模板选择行）、`:110`（`vm.create(name, templateId)`）<br>`WorkspaceViewModel.kt:27-31`（`create(name, templateId)`）<br>`WorkspaceRepository.kt:59-77`（`create` 先 `ensureWorkspace` → 落地模板 → 成功后才 `dao.upsert`；失败 `deleteWorkspace` 回滚）<br>`app/src/main/res/values*/strings.xml`（7 个 locale：`values`、`values-zh`、`values-zh-rTW`、`values-ja`、`values-ko-rKR`、`values-ru`、`values-ar`） |
| 自证 | 编译 + `:app:testDebugUnitTest`；真机：分别用「空白 / python / web」新建三个工作区 → 进 Files tab 看到预期文件 → 进终端 `ls /workspace` 看到同一批文件（验证 §4.5 的映射不变量） |
| 量级 | **~120–200 行**（含 7 语言 ~6 个键） |

### Step 3 — 归档核心（纯逻辑，JVM 可测）

| 项 | 内容 |
|---|---|
| 新增文件 | `app/src/main/java/heizige/kk/khatkit/app/core/data/workspace/WorkspaceArchive.kt`（`pack(workspaceDir, manifest): Unit` / `unpack(zip, targetDir): Manifest`）<br>`app/src/main/java/heizige/kk/khatkit/app/core/data/workspace/WorkspaceArchiveManifest.kt`（`@Serializable`）<br>`app/src/test/java/heizige/kk/khatkit/app/core/data/workspace/{WorkspaceArchiveFormatTest,WorkspaceArchiveRoundTripTest,WorkspaceArchiveGuardTest,WorkspaceArchiveRestoreTest}.kt` |
| 复用 | `PendingRestore.resolveInside`（`:138-148`）——**直接调用**，不复制 |
| 自证 | 四个测试类全绿（round-trip 字节相等 + 8 类恶意条目全拒 + 回滚后原目录完好） |
| 量级 | **~350–450 行** |

### Step 4 — 详情页导出/导入接线（真机验证）

| 项 | 内容 |
|---|---|
| 改动文件 | `WorkspaceDetailPage.kt`（`WorkspaceBasicPage` :362 里加一张 `WorkspaceBackupCard`：导出/导入两个按钮；按 `:146-150` 的 `OpenDocumentTree`/`CreateDocument` 模式加 launcher）<br>`WorkspaceDetailViewModel.kt`（加 `exportWorkspaceArchive(uri, resolver)` / `importWorkspaceArchive(uri, resolver)`；换入前 `terminalSessionManager.closeWorkspace(root)`）<br>`WorkspaceRepository.kt`（加 `createFromArchive(...)`，编排 §5.4 的五步）<br>strings（7 语言，~8 个键） |
| 自证 | 编译 + 单测；真机按 §6.2 的 7 条走（尤其「恢复时终端正在运行」与「rootfs 缺失兜底」） |
| 量级 | **~200–300 行** |

**（不排期，仅登记）Step 5 可选**：把工作区并进应用级备份 —— 扩展 `WebDavConfig.BackupItem`（`SettingsRepository.kt:712-715`）
与 `S3Config.BackupItem`（`S3Config.kt:36-40`）加 `WORKSPACES`、`BackupManager.kt:52-64` 的文件夹清单与
`:141-148` `isAttachment` 同步扩展、`PendingRestore.targetFile`（`:117-132`）加 `workspaces/` 前缀分支。
**风险点是旧归档兼容与配额**，所以单列、不在 MVP。

---

## 9. 明确不做项

| 不做 | 依据 |
|---|---|
| 多设备文件同步 | gap 分析 §8 第 3 条（「前两个不需要多设备」）、§5 :294（同步/回滚是 P1） |
| Space / 绑定（`CoreNode`/`Space`/`Binding`） | gap 分析 §0 第 1 条指出那条轴不在 G1–G14 矩阵内；§8 第 5 条（P2，只做文档对齐） |
| 「每条消息自动快照 + 按时间戳回退」 | §3-#4：O 的 `WorkspaceBackupManager` 属 P1 那一族；本切片只要「用户显式整包导出/导入」 |
| 覆盖既有工作区 | 覆盖语义涉及不可逆数据替换；MVP 只「恢复为新工作区」 |
| 打包 rootfs | §5.2（可重下 + `checkIntegrity` 兜底） |
| 二进制模板 / 模板引擎 / 模板市场 | §4.1（安全与范围控制） |
| 改 `WorkspaceDocumentsProvider` 暴露面 | §5.6 |
| 无署名的搬运 | 许可上已无障碍（本仓同 AGPL-3.0），**唯一保留的硬约束是署名**：§3 逐处标注了性质；若将来出现「原样复用」，按 §3 表头的规则新增一行（仓库 + SHA + 性质 + 改动点）。本设计当前 0 处原样复用 |

---

## 10. 必须由用户拍板的产品问题

| id | 问题 | 我的建议 | 影响面 |
|---|---|---|---|
| P1 | 内置模板集合：照 O 的 7 个语言模板，还是只做 3 个（空白/Python/Web）？ | **3 个**（`blank`/`python`/`web`）：覆盖面最大的三种起手，且自撰成本低 | Step 1 资产量与后续维护 |
| P2 | 模板是否允许二进制文件？ | **先不允许**（走 `writeText` 白拿守卫与上限） | 模板能力边界（R1） |
| P3 | 模板变量要不要？ | **要，但只两个**（`${workspace_name}` / `${date}`） | 模板作者体验 vs 注入面（R2） |
| P4 | 是否要「把现有工作区另存为模板」？ | **下一步**，不进 MVP | 需要「工作区→模板」的反向导出 + 脱敏考虑 |
| P5 | 备份是否包含 rootfs？ | **不包含** | 归档体积/耗时 vs 「恢复即可用终端」的体验（R5/§5.7） |
| P6 | 导入是否允许覆盖同名工作区？ | **不允许**（重名自动加 `(2)`） | 数据安全 |
| P7 | 是否显式把 `workspaces/` 从系统自动备份排除？ | **建议排除**（`data_extraction_rules.xml` 加 `<exclude>`），先按 §6.2 验证再改 | 会动到 `res/xml/` 既有行为，故必须拍板（R6） |
| P8 | UI 文案走 7 语言键，还是按 AGENTS.md「不做本地化优先」直接用中文常量？ | **走 7 语言键**：现有 `workspace_*` 键就是 7 语言齐的，保持一致 | Step 2/4 的字符串工作量 |

---

## 11. 附录：复核命令（一键复现本文所有「已有 / 没有」判定）

```bash
cd /home/heizige/文档/Android/Project/KhatKit

# 0) 版本与许可前提（§0 段的许可/署名口径）
git rev-parse HEAD                       # 本文基于 2505cf7a1
head -1 LICENSE                          # GNU AFFERO GENERAL PUBLIC LICENSE …（与 Operit2 同许可）
sed -n '85,87p' README.md                # 自述 AGPL-3.0
grep -n "不得复制代码/资源\|注明来源仓库与 SHA" docs/beyond-operit2-source-gap-analysis.md
                                         # §8 第 6 条 = :350-351；该段里的「不得复制」已被 t31 修订作废（署名义务保留），该文件不在本任务 inScope

# 1) 工作区 UI/VM 存在性
grep -n "fun WorkspacePage" app/src/main/java/heizige/kk/khatkit/app/feature/workspace/WorkspacePage.kt
grep -n "EditWorkspaceDialog\|vm.create(name)" app/src/main/java/heizige/kk/khatkit/app/feature/workspace/WorkspacePage.kt
grep -n "fun create" app/src/main/java/heizige/kk/khatkit/app/feature/workspace/WorkspaceViewModel.kt
grep -n "fun create\|fun checkIntegrity\|isNameTaken\|MAX_PREVIEW_BYTES" app/src/main/java/heizige/kk/khatkit/app/core/data/repository/WorkspaceRepository.kt
grep -n "prepareBatchExport\|exportFilesToDirectory" app/src/main/java/heizige/kk/khatkit/app/feature/workspace/WorkspaceDetailViewModel.kt

# 2) 三区目录 + 越界守卫 + 原子写先例
grep -n "FILES_DIR\|LINUX_DIR\|TEMP_DIR\|ROOTFS_WORKSPACE_DIR\|ROOT_NAME_REGEX" workspace/src/main/java/heizige/kk/khatkit/workspace/WorkspaceManager.kt
grep -n "Path escapes workspace root\|private fun resolveConflict" workspace/src/main/java/heizige/kk/khatkit/workspace/WorkspaceFileSystem.kt
grep -n "fun saveSkillFileBytesAtomically\|stagingDir\|writeBytes" app/src/main/java/heizige/kk/khatkit/app/core/data/files/SkillManager.kt

# 3) 应用级备份（三处入口）
grep -n "fun createBackup\|if (includeFiles)\|fun stageRestore\|isAttachment\|applyPendingRestore" app/src/main/java/heizige/kk/khatkit/app/core/data/sync/BackupManager.kt
grep -n "resolveInside\|writeDurably\|ATOMIC_MOVE\|targetFile" app/src/main/java/heizige/kk/khatkit/app/core/data/sync/PendingRestore.kt
grep -n "CreateDocument\|OpenDocument" app/src/main/java/heizige/kk/khatkit/app/feature/backup/tabs/ImportExportTab.kt
grep -n "enum class BackupItem" -A 4 app/src/main/java/heizige/kk/khatkit/app/core/data/datastore/SettingsRepository.kt app/src/main/java/heizige/kk/khatkit/app/core/data/sync/s3/S3Config.kt
grep -n "applyPendingRestore" app/src/main/java/heizige/kk/khatkit/app/KhatKitApp.kt

# 4) 系统自动备份规则不一致（R6 的证据）
grep -n "allowBackup\|dataExtractionRules\|fullBackupContent" app/src/main/AndroidManifest.xml
cat app/src/main/res/xml/backup_rules.xml
cat app/src/main/res/xml/data_extraction_rules.xml

# 5) DocumentsProvider 只暴露 files/ + 挂载表
sed -n '20,34p;90,114p' app/src/main/java/heizige/kk/khatkit/app/feature/workspace/data/WorkspaceDocumentsProvider.kt
sed -n '242,250p' app/src/main/AndroidManifest.xml
sed -n '117,141p' app/src/main/java/heizige/kk/khatkit/app/core/di/RepositoryHiltModule.kt

# 6) 双风格是运行时的（不是两份文件）
git ls-files | grep -i miuix | grep -iE "workspace|backup"   # 0 命中
grep -rn "LocalKedgeStyle.current" app/src/main/java/heizige/kk/khatkit/app/core/ui/components/ui/miuix/ app/src/main/java/heizige/kk/khatkit/app/core/ui/components/nav/BackButton.kt
sed -n '61,67p' app/src/main/java/heizige/kk/khatkit/app/core/ui/components/ui/miuix/KedgeSettingsPageScaffold.kt   # :62 MD3Exp
sed -n '40,50p' app/src/main/java/heizige/kk/khatkit/app/core/ui/components/ui/miuix/KedgeForm.kt                    # :40 when / :50 Miuix
sed -n '34,42p' app/src/main/java/heizige/kk/khatkit/app/core/ui/components/nav/BackButton.kt                        # :34 when / :35 Miuix / :42 MD3

# 7) 「没有」的 0 命中证据
grep -rn "template" app/src/main/java/heizige/kk/khatkit/app/feature/workspace/ workspace/src/main/java/          # 0
grep -rniE "\b(backup|restore|snapshot|rollback|sync)\b" app/src/main/java/heizige/kk/khatkit/app/feature/workspace/ workspace/src/main/java/   # 0（必须带 \b）
grep -rniE "backup|restore|snapshot|rollback|sync" app/src/main/java/heizige/kk/khatkit/app/feature/workspace/ workspace/src/main/java/        # 4：AsyncImage×2 + synchronized×2，假阳性
grep -rn "workspace" app/src/main/java/heizige/kk/khatkit/app/core/data/sync/BackupManager.kt                    # 0
grep -n "WORKSPACE" app/src/main/java/heizige/kk/khatkit/app/core/data/files/FilesManager.kt                     # 0
grep -rn "enum class BackupItem" -A 4 app/src/main/java/heizige/kk/khatkit/app/core/data/datastore/SettingsRepository.kt app/src/main/java/heizige/kk/khatkit/app/core/data/sync/s3/S3Config.kt
ls docs/ | grep -iE "workspace|template|backup" | grep -v workspace-templates-and-backup-design.md                # 0（不过滤会命中本文件自身）
git ls-files | grep -iE "test.*(workspace|Template|Archive)"                                                     # 5 个（含无关的 BrowserArchiveTest）

# 8) 可复用件的既有测试（JVM 先例）
grep -c "@Test" app/src/test/java/heizige/kk/khatkit/app/core/data/sync/PendingRestoreTest.kt

# 9) O 侧对照（只读副本；性质见 §3）
cd /tmp/operit2 && git rev-parse HEAD                                            # 9ae1e558a6…
ls -1 core/crates/runtime/application/assets/workspace_templates/                # 7 个模板
grep -n "templateName\|copyTemplateFiles" core/crates/runtime/application/src/ui/features/chat/webview/workspace/WorkspaceUtils.rs
grep -n "pub struct BackupManifest\|previewChangesForRewind" core/crates/runtime/application/src/ui/features/chat/webview/workspace/WorkspaceBackupManager.rs
grep -niE "\b(template|backup|restore|export)\b" apps/flutter/app/lib/ui/features/settings/workspace/WorkspaceSettingsPanel.dart   # 0 命中 ⇒ O 无可抄的入口 UI（模式别带 import：那 7 处是 Dart import）
```

---

## 12. 变更记录

| 日期 | 变更 |
|---|---|
| 2026-10-14 | 初版（t31 attempt 1）。许可口径按 captain 的 t31 修订：本仓同 AGPL-3.0 ⇒ 取消「禁止复制」禁令，保留逐处署名；本文档实际全部为机制参照 + 自撰，未复制 O 侧代码/资源/字符串/注释 |
| 2026-10-14 | 记录一处对 gap 分析的映射订正：`WorkspaceBackupManager.rs` 在行为层属「改动回滚/文件同步」（P1），不属本切片的「整包导出/导入」（§3-#4） |
| 2026-10-14 | **attempt 2：全量复核后订正 4 处**（均可在 HEAD `2505cf7a1` + 当前工作树上复现）。① 依据引用补精确行号：gap 分析 §8 第 3 条 = `:347`（并补 G9 判定行 `:217`、优先级表 `:292`/`:294`）；② §3-#3 的 O 侧自动映射**调用点**由 `:28-31`（那是 `workspaceSyncStore`，与映射无关）改为 `:36-42`，映射表改 `:84-98`；③ §3-#6 的 `getWorkspaceRelativePath` 由 `:60-62` 改为 `:59-61`；④ §3-#5 的 grep 模式去掉 `import`——原写法在该 Dart 文件上得 **7 处假阳性**（全是 `import` 语句），正确命令见 §11-#9 |
| 2026-10-14 | attempt 2 另补三处口径：§0 许可段改成「禁令作废 + 署名义务保留 + 本设计 0 处原样复用 + 若原样复用必须登记」四段式，并标注 gap 分析 `:350-351` 里那句旧禁令已被本次修订作废（该文件不在本任务 inScope，只备注不代改）；§3 表头写明性质取值域；§4.4 双风格证据补**完整路径**（`BackButton.kt` 在 `core/ui/components/nav/`，不在 `ui/miuix/`） |
