package heizige.kk.khatkit.app.core.di

import android.content.Context
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton
import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO
import io.ktor.client.plugins.HttpTimeout
import io.ktor.http.HttpHeaders
import io.pebbletemplates.pebble.PebbleEngine
import kotlinx.serialization.json.Json
import heizige.kk.khatkit.ai.provider.ProviderManager
import heizige.kk.khatkit.common.http.AcceptLanguageBuilder
import heizige.kk.khatkit.app.BuildConfig
import heizige.kk.khatkit.app.AppScope
import heizige.kk.khatkit.app.core.data.ai.AIRequestInterceptor
import heizige.kk.khatkit.app.core.data.ai.RequestLoggingInterceptor
import heizige.kk.khatkit.app.core.data.ai.transformers.AssistantTemplateLoader
import heizige.kk.khatkit.app.core.data.ai.GenerationLoop
import heizige.kk.khatkit.app.core.data.ai.TranslationHandler
import heizige.kk.khatkit.app.core.data.ai.transformers.TemplateTransformer
import heizige.kk.khatkit.app.core.data.api.SponsorAPI
import heizige.kk.khatkit.app.core.data.datastore.SettingsRepository
import heizige.kk.khatkit.app.core.data.sync.BackupManager
import heizige.kk.khatkit.app.core.data.db.AppDatabaseFactory
import heizige.kk.khatkit.app.core.data.db.AppDatabase
import heizige.kk.khatkit.app.core.data.db.dao.ConversationDAO
import heizige.kk.khatkit.app.core.data.db.dao.FolderDAO
import heizige.kk.khatkit.app.core.data.db.dao.GenMediaDAO
import heizige.kk.khatkit.app.core.data.db.dao.ManagedFileDAO
import heizige.kk.khatkit.app.core.data.db.dao.MemoryDAO
import heizige.kk.khatkit.app.core.data.db.dao.MessageNodeDAO
import heizige.kk.khatkit.app.core.data.db.dao.WorkspaceDAO
import heizige.kk.khatkit.app.core.data.db.fts.MessageFtsManager
import heizige.kk.khatkit.app.core.data.ai.mcp.McpManager
import heizige.kk.khatkit.app.core.data.network.SettingsProxySelector
import heizige.kk.khatkit.app.core.data.network.SettingsProxyAuthenticator
import heizige.kk.khatkit.app.core.data.network.SettingsSocks5Authenticator
import heizige.kk.khatkit.app.core.data.files.FilesManager
import heizige.kk.khatkit.app.core.data.sync.webdav.WebDavSync
import heizige.kk.khatkit.search.SearchService
import heizige.kk.khatkit.app.core.data.sync.S3Sync
import heizige.kk.khatkit.common.http.okhttp.OkHttpClient
import heizige.kk.khatkit.common.http.okhttp.logging.HttpLoggingInterceptor
import java.util.Locale
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference

@Module
@InstallIn(SingletonComponent::class)
object DataSourceHiltModule {
    @Provides
    @Singleton
    fun provideAppDatabase(@ApplicationContext context: Context): AppDatabase =
        AppDatabaseFactory.create(context)

    @Provides
    @Singleton
    fun provideAssistantTemplateLoader(settingsStore: SettingsRepository): AssistantTemplateLoader =
        AssistantTemplateLoader(settingsStore = settingsStore)

    @Provides
    @Singleton
    fun providePebbleEngine(loader: AssistantTemplateLoader): PebbleEngine =
        PebbleEngine.Builder()
            .loader(loader)
            .defaultLocale(Locale.getDefault())
            .autoEscaping(false)
            .build()

    @Provides
    @Singleton
    fun provideTemplateTransformer(
        engine: PebbleEngine,
        settingsStore: SettingsRepository,
    ): TemplateTransformer = TemplateTransformer(engine = engine, settingsStore = settingsStore)

    @Provides
    @Singleton
    fun provideConversationDao(database: AppDatabase): ConversationDAO = database.conversationDao()

    @Provides
    @Singleton
    fun provideMemoryDao(database: AppDatabase): MemoryDAO = database.memoryDao()

    @Provides
    @Singleton
    fun provideGenMediaDao(database: AppDatabase): GenMediaDAO = database.genMediaDao()

    @Provides
    @Singleton
    fun provideMessageNodeDao(database: AppDatabase): MessageNodeDAO = database.messageNodeDao()

    @Provides
    @Singleton
    fun provideManagedFileDao(database: AppDatabase): ManagedFileDAO = database.managedFileDao()

    @Provides
    @Singleton
    fun provideWorkspaceDao(database: AppDatabase): WorkspaceDAO = database.workspaceDao()

    @Provides
    @Singleton
    fun provideFolderDao(database: AppDatabase): FolderDAO = database.folderDao()

    @Provides
    @Singleton
    fun provideMessageFtsManager(database: AppDatabase): MessageFtsManager = MessageFtsManager(database)

    @Provides
    @Singleton
    fun provideMcpManager(
        settingsStore: SettingsRepository,
        appScope: AppScope,
        filesManager: FilesManager,
    ): McpManager = McpManager(settingsStore = settingsStore, appScope = appScope, filesManager = filesManager)

    @Provides
    @Singleton
    fun provideGenerationLoop(
        @ApplicationContext context: Context,
        providerManager: ProviderManager,
        json: Json,
    ): GenerationLoop = GenerationLoop(
        context = context,
        providerManager = providerManager,
        json = json,
    )

    @Provides
    @Singleton
    fun provideTranslationHandler(providerManager: ProviderManager): TranslationHandler =
        TranslationHandler(providerManager = providerManager)

    @Provides
    @Singleton
    fun provideOkHttpClient(
        @ApplicationContext context: Context,
        settingsStore: SettingsRepository,
    ): OkHttpClient {
        val acceptLang = AcceptLanguageBuilder.fromAndroid(context)
            .build()
        java.net.Authenticator.setDefault(SettingsSocks5Authenticator(settingsStore))
        val initialNetworkSetting = settingsStore.settingsFlow.value.networkSetting
        val appliedProxySetting = AtomicReference(
            Triple(
                initialNetworkSetting.proxyUrl,
                initialNetworkSetting.proxyUsername,
                initialNetworkSetting.proxyPassword,
            )
        )
        lateinit var client: OkHttpClient
        client = OkHttpClient.Builder()
            .proxySelector(SettingsProxySelector(settingsStore))
            .proxyAuthenticator(SettingsProxyAuthenticator(settingsStore))
            .connectTimeout(20, TimeUnit.SECONDS)
            .readTimeout(10, TimeUnit.MINUTES)
            .writeTimeout(120, TimeUnit.SECONDS)
            .followSslRedirects(true)
            .followRedirects(true)
            .retryOnConnectionFailure(true)
            .addInterceptor { chain ->
                val networkSetting = settingsStore.settingsFlow.value.networkSetting
                val currentProxySetting = Triple(
                    networkSetting.proxyUrl,
                    networkSetting.proxyUsername,
                    networkSetting.proxyPassword,
                )
                if (appliedProxySetting.getAndSet(currentProxySetting) != currentProxySetting) {
                    client.connectionPool.evictAll()
                }

                val originalRequest = chain.request()
                val requestBuilder = originalRequest.newBuilder()
                    .addHeader(HttpHeaders.AcceptLanguage, acceptLang)

                if (originalRequest.header(HttpHeaders.UserAgent) == null) {
                    val userAgent = settingsStore.settingsFlow.value.networkSetting.userAgent
                        .trim()
                        .ifEmpty { "KhatKit-Android/${BuildConfig.VERSION_NAME}" }
                    requestBuilder.addHeader(HttpHeaders.UserAgent, userAgent)
                }

                chain.proceed(requestBuilder.build())
            }
            .addNetworkInterceptor { chain ->
                val request = chain.request()
                val contentTypeHeader = request.header("Content-Type")
                if (
                    contentTypeHeader != null &&
                    contentTypeHeader.contains(";") &&
                    contentTypeHeader.substringBefore(";").trim().equals("application/json", ignoreCase = true)
                ) {
                    chain.proceed(
                        request.newBuilder()
                            .header("Content-Type", contentTypeHeader.substringBefore(";").trim())
                            .build()
                    )
                } else {
                    chain.proceed(request)
                }
            }
            .addNetworkInterceptor(RequestLoggingInterceptor())
            .addInterceptor(AIRequestInterceptor())
            .addInterceptor(HttpLoggingInterceptor().apply {
                redactHeader("Proxy-Authorization")
                level = HttpLoggingInterceptor.Level.HEADERS
            })
            .build()
        SearchService.init(client, context)
        return client
    }

    @Provides
    @Singleton
    fun provideSponsorAPI(client: OkHttpClient): SponsorAPI = SponsorAPI.create(client)

    @Provides
    @Singleton
    fun provideProviderManager(
        client: OkHttpClient,
        @ApplicationContext context: Context,
    ): ProviderManager = ProviderManager(client = client, context = context)

    @Provides
    @Singleton
    fun provideBackupManager(
        @ApplicationContext context: Context,
        database: AppDatabase,
        settingsStore: SettingsRepository,
        json: Json,
    ): BackupManager = BackupManager(
        context = context,
        database = database,
        settingsStore = settingsStore,
        json = json,
    )

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
    fun provideKtorHttpClient(): HttpClient = HttpClient(CIO) {
        expectSuccess = false
        install(HttpTimeout) {
            connectTimeoutMillis = 20_000
            socketTimeoutMillis = 10 * 60_000
        }
    }

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
