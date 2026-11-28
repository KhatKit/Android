package heizige.kk.khatkit.app.feature.backup.di

import android.content.Context
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import heizige.kk.khatkit.app.core.data.sync.BackupManager
import heizige.kk.khatkit.app.feature.backup.data.remote.S3Sync
import heizige.kk.khatkit.app.feature.backup.data.remote.webdav.WebDavSync
import io.ktor.client.HttpClient
import javax.inject.Singleton

/** 备份远端同步的单 feature 装配；备份 schema/Manager 仍留 core。 */
@Module
@InstallIn(SingletonComponent::class)
object BackupSyncHiltModule {
    @Provides
    @Singleton
    fun provideWebDavSync(
        backupManager: BackupManager,
        @ApplicationContext context: Context,
        httpClient: HttpClient,
    ): WebDavSync = WebDavSync(
        backupManager = backupManager,
        context = context,
        httpClient = httpClient,
    )

    @Provides
    @Singleton
    fun provideS3Sync(
        backupManager: BackupManager,
        @ApplicationContext context: Context,
        httpClient: HttpClient,
    ): S3Sync = S3Sync(
        backupManager = backupManager,
        context = context,
        httpClient = httpClient,
    )
}
