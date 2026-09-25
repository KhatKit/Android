package heizige.kk.khatkit.app.di

import android.content.Context
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton
import heizige.kk.khatkit.app.AppScope
import heizige.kk.khatkit.app.data.db.AppDatabase
import heizige.kk.khatkit.app.data.db.dao.ConversationDAO
import heizige.kk.khatkit.app.data.db.dao.FolderDAO
import heizige.kk.khatkit.app.data.db.dao.GenMediaDAO
import heizige.kk.khatkit.app.data.db.dao.ManagedFileDAO
import heizige.kk.khatkit.app.data.db.dao.MemoryDAO
import heizige.kk.khatkit.app.data.db.dao.MessageNodeDAO
import heizige.kk.khatkit.app.data.db.dao.WorkspaceDAO
import heizige.kk.khatkit.app.data.db.fts.MessageFtsManager
import heizige.kk.khatkit.app.data.datastore.SettingsStore
import heizige.kk.khatkit.app.data.files.FileFolders
import heizige.kk.khatkit.app.data.files.FilesManager
import heizige.kk.khatkit.app.data.files.SkillManager
import heizige.kk.khatkit.app.data.repository.ConversationRepository
import heizige.kk.khatkit.app.data.repository.FolderRepository
import heizige.kk.khatkit.app.data.repository.FilesRepository
import heizige.kk.khatkit.app.data.repository.GenMediaRepository
import heizige.kk.khatkit.app.data.repository.MemoryRepository
import heizige.kk.khatkit.app.data.repository.WorkspaceRepository
import heizige.kk.khatkit.workspace.ProotShellRunner
import heizige.kk.khatkit.workspace.RootfsInstaller
import heizige.kk.khatkit.workspace.WorkspaceBindMount
import heizige.kk.khatkit.workspace.WorkspaceManager
import java.io.File

@Module
@InstallIn(SingletonComponent::class)
object RepositoryHiltModule {
    @Provides
    @Singleton
    fun provideConversationRepository(
        conversationDAO: ConversationDAO,
        messageNodeDAO: MessageNodeDAO,
        database: AppDatabase,
        filesManager: FilesManager,
        messageFtsManager: MessageFtsManager,
    ): ConversationRepository = ConversationRepository(
        conversationDAO,
        messageNodeDAO,
        database,
        filesManager,
        messageFtsManager,
    )

    @Provides
    @Singleton
    fun provideFolderRepository(
        folderDAO: FolderDAO,
        conversationDAO: ConversationDAO,
    ): FolderRepository = FolderRepository(folderDAO, conversationDAO)

    @Provides
    @Singleton
    fun provideMemoryRepository(memoryDAO: MemoryDAO): MemoryRepository = MemoryRepository(memoryDAO)

    @Provides
    @Singleton
    fun provideGenMediaRepository(genMediaDAO: GenMediaDAO): GenMediaRepository = GenMediaRepository(genMediaDAO)

    @Provides
    @Singleton
    fun provideFilesRepository(managedFileDAO: ManagedFileDAO): FilesRepository = FilesRepository(managedFileDAO)

    @Provides
    @Singleton
    fun provideWorkspaceManager(@ApplicationContext context: Context): WorkspaceManager = WorkspaceManager(
        baseDir = File(context.filesDir, "workspaces"),
        shellRunner = ProotShellRunner(
            nativeLibraryDir = File(context.applicationInfo.nativeLibraryDir),
        ),
        // 同一份挂载表既用于 PRoot 的 -b 参数, 也用于文件工具的路径解析, 避免两处漂移
        bindMounts = listOf(
            WorkspaceBindMount(
                source = File(context.filesDir, FileFolders.SKILLS).apply { mkdirs() },
                target = "/skills",
            ),
            WorkspaceBindMount(
                source = File(context.filesDir, FileFolders.TOOL_OUTPUTS).apply { mkdirs() },
                target = "/tool_outputs",
            ),
            WorkspaceBindMount(
                source = File(context.filesDir, FileFolders.UPLOAD).apply { mkdirs() },
                target = "/upload",
            ),
        ),
    )

    @Provides
    @Singleton
    fun provideRootfsInstaller(workspaceManager: WorkspaceManager): RootfsInstaller = RootfsInstaller(workspaceManager)

    @Provides
    @Singleton
    fun provideWorkspaceRepository(
        workspaceDAO: WorkspaceDAO,
        workspaceManager: WorkspaceManager,
        rootfsInstaller: RootfsInstaller,
        settingsStore: SettingsStore,
    ): WorkspaceRepository = WorkspaceRepository(workspaceDAO, workspaceManager, rootfsInstaller, settingsStore)

    @Provides
    @Singleton
    fun provideFilesManager(
        @ApplicationContext context: Context,
        filesRepository: FilesRepository,
        appScope: AppScope,
    ): FilesManager = FilesManager(context, filesRepository, appScope)

    @Provides
    @Singleton
    fun provideSkillManager(
        @ApplicationContext context: Context,
        settingsStore: SettingsStore,
    ): SkillManager = SkillManager(context, settingsStore)
}
