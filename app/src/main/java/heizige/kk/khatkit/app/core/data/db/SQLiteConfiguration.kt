package heizige.kk.khatkit.app.core.data.db

import android.content.Context
import io.requery.android.database.sqlite.RequerySQLiteOpenHelperFactory
import io.requery.android.database.sqlite.SQLiteCustomExtension
import io.requery.android.database.sqlite.SQLiteDatabaseConfiguration

/** Use the same SQLite extensions for the live database and backup validation. */
internal object SQLiteConfiguration {
    const val DATABASE_NAME = "rikka_hub"

    fun configure(context: Context, configuration: SQLiteDatabaseConfiguration): SQLiteDatabaseConfiguration {
        configuration.customExtensions.add(
            SQLiteCustomExtension(context.applicationInfo.nativeLibraryDir + "/libsimple", null)
        )
        // A2 记忆：sqlite-vector 向量检索扩展。路径按 libsimple 同约定解析。
        // 文件缺失时跳过注册，检索层自动退回 Kotlin 暴力余弦。
        if (MemoryVectorIndex.hasNativeExtension(context)) {
            configuration.customExtensions.add(
                SQLiteCustomExtension(MemoryVectorIndex.nativeExtensionPath(context), null)
            )
        }
        return configuration
    }

    fun openHelperFactory(context: Context) = RequerySQLiteOpenHelperFactory(
        listOf(RequerySQLiteOpenHelperFactory.ConfigurationOptions { configure(context, it) })
    )
}
