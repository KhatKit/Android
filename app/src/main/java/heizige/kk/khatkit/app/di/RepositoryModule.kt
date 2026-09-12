package heizige.kk.khatkit.app.di

import android.content.Context
import heizige.kk.khatkit.app.data.files.FileFolders
import heizige.kk.khatkit.app.data.files.FilesManager
import heizige.kk.khatkit.app.data.files.SkillManager
import heizige.kk.khatkit.app.data.repository.ConversationRepository
import heizige.kk.khatkit.app.data.repository.FavoriteRepository
import heizige.kk.khatkit.app.data.repository.FolderRepository
import heizige.kk.khatkit.app.data.repository.FilesRepository
import heizige.kk.khatkit.app.data.repository.GenMediaRepository
import heizige.kk.khatkit.app.data.repository.MemoryRepository
import heizige.kk.khatkit.app.data.repository.WorkspaceRepository
import heizige.kk.khatkit.workspace.ProotShellRunner
import heizige.kk.khatkit.workspace.RootfsInstaller
import heizige.kk.khatkit.workspace.WorkspaceBindMount
import heizige.kk.khatkit.workspace.WorkspaceManager
import org.koin.dsl.module
import java.io.File

val repositoryModule = module {
    single {
        ConversationRepository(get(), get(), get(), get(), get(), get())
    }

    single {
        FolderRepository(get(), get())
    }

    single {
        MemoryRepository(get())
    }

    single {
        GenMediaRepository(get())
    }

    single {
        FilesRepository(get())
    }

    single {
        FavoriteRepository(get())
    }

    single {
        val context: Context = get()
        WorkspaceManager(
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
    }

    single {
        RootfsInstaller(get())
    }

    single {
        WorkspaceRepository(get(), get(), get(), get())
    }

    single {
        FilesManager(get(), get(), get())
    }

    single {
        SkillManager(get(), get())
    }
}
