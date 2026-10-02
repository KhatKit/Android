package heizige.kk.khatkit.bridge.impl

import android.content.Context
import android.database.Cursor
import android.util.Base64
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.SupportSQLiteOpenHelper
import androidx.sqlite.db.SupportSQLiteStatement
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import heizige.kk.khatkit.common.android.Logging
import java.io.File

/**
 * 卡片自己的 SQLite 库（`store.sql` 的落地）。
 *
 * 每张卡片一个文件：`databases/cards/<cardName>.db`，与宿主聊天库完全隔离；
 * 卡片卸载时由宿主 `deleteDatabase` 删掉。跨库语句（`ATTACH` / `VACUUM` / 扩展加载）
 * 由 [SqlGuard] 在执行前挡住。
 *
 * 引擎选型：androidx.sqlite 的 `FrameworkSQLiteOpenHelperFactory`（平台 SQLite），
 * **不用 Room** —— 卡片要的是运行期 `CREATE TABLE`，注解处理器帮不上忙，而且 Room 会把
 * 卡片库和宿主聊天库耦在一起（决策记录见 docs/bridge-expansion-spec.md T2.1）。
 *
 * FTS5 依系统 SQLite 构建而定，探测结果见 [fts5Supported]，卡片可先用
 * `host.health().fts5` 判断，不支持时退化成普通表 + `LIKE`。
 */
class CardSqlStore(
    context: Context,
    private val cardName: String,
    private val quota: StoreQuota,
    /** 卡片其他存储（files/ + JSONL）当前占用，用于合计校验配额。 */
    private val externalUsage: () -> Long,
) {

    private val appContext = context.applicationContext

    /** 库文件路径；`getDatabasePath` 保证 `databases/` 目录存在。 */
    val dbFile: File = File(appContext.getDatabasePath(CARD_DB_DIR).apply { mkdirs() }, "$cardName.db")

    private val helper: SupportSQLiteOpenHelper by lazy {
        FrameworkSQLiteOpenHelperFactory().create(
            SupportSQLiteOpenHelper.Configuration.builder(appContext)
                .name(dbFile.absolutePath)
                .callback(object : SupportSQLiteOpenHelper.Callback(DB_VERSION) {
                    override fun onCreate(db: SupportSQLiteDatabase) = tune(db)
                    override fun onUpgrade(db: SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) = Unit
                    override fun onConfigure(db: SupportSQLiteDatabase) = tune(db)
                })
                .build(),
        )
    }

    /**
     * 执行一条 SQL。`SELECT` / `PRAGMA` / `WITH` / `VALUES` 返回行表（最多 [MAX_ROWS] 行，
     * 超出请自己加 `LIMIT`），其余语句返回空表。
     */
    fun exec(sql: String, args: List<Any?>): List<Map<String, Any?>> {
        SqlGuard.check(sql)
        val db = helper.writableDatabase
        return try {
            if (SqlGuard.returnsRows(sql)) readRows(db, sql, args) else runWrite(db, sql, args)
        } catch (e: IllegalArgumentException) {
            throw e
        } catch (e: Exception) {
            throw IllegalStateException("SQL 执行失败：${e.message ?: e.javaClass.simpleName}")
        }
    }

    /** 库文件 + WAL/SHM 占用字节。 */
    fun ownBytes(): Long = listOf(dbFile, File(dbFile.path + "-wal"), File(dbFile.path + "-shm"))
        .filter { it.exists() }
        .sumOf { it.length() }

    private fun readRows(db: SupportSQLiteDatabase, sql: String, args: List<Any?>): List<Map<String, Any?>> {
        val cursor = db.query(sql, args.toTypedArray())
        cursor.use {
            val rows = ArrayList<Map<String, Any?>>(minOf(cursor.count, MAX_ROWS))
            while (cursor.moveToNext() && rows.size < MAX_ROWS) {
                val row = LinkedHashMap<String, Any?>(cursor.columnCount)
                for (index in 0 until cursor.columnCount) {
                    row[cursor.getColumnName(index)] = readValue(cursor, index)
                }
                rows += row
            }
            return rows
        }
    }

    private fun runWrite(db: SupportSQLiteDatabase, sql: String, args: List<Any?>): List<Map<String, Any?>> {
        quota.enforce(externalUsage() + ownBytes())
        db.beginTransactionNonExclusive()
        try {
            val affected = compile(db, sql, args).executeUpdateDelete()
            // -1 表示这条语句本身返回结果集（例如 WITH … VALUES），补一次查询
            val result = if (affected >= 0) emptyList() else readRows(db, sql, args)
            db.setTransactionSuccessful()
            return result
        } finally {
            db.endTransaction()
            quota.enforce(externalUsage() + ownBytes())
        }
    }

    private fun compile(db: SupportSQLiteDatabase, sql: String, args: List<Any?>): SupportSQLiteStatement {
        val statement = db.compileStatement(sql)
        args.forEachIndexed { index, arg ->
            val position = index + 1
            when (arg) {
                null -> statement.bindNull(position)
                is Boolean -> statement.bindLong(position, if (arg) 1L else 0L)
                is Int -> statement.bindLong(position, arg.toLong())
                is Long -> statement.bindLong(position, arg)
                is Float -> statement.bindDouble(position, arg.toDouble())
                is Double -> statement.bindDouble(position, arg)
                else -> statement.bindString(position, arg.toString())
            }
        }
        return statement
    }

    private fun readValue(cursor: Cursor, index: Int): Any? = when (cursor.getType(index)) {
        Cursor.FIELD_TYPE_NULL -> null
        Cursor.FIELD_TYPE_INTEGER -> cursor.getLong(index)
        Cursor.FIELD_TYPE_FLOAT -> cursor.getDouble(index)
        Cursor.FIELD_TYPE_BLOB -> Base64.encodeToString(cursor.getBlob(index), Base64.NO_WRAP)
        else -> cursor.getString(index)
    }

    /** 建库时统一收紧：关掉 schema 里的非可信函数，页数按配额硬顶。 */
    private fun tune(db: SupportSQLiteDatabase) {
        runCatching { db.execSQL("PRAGMA trusted_schema = OFF") }
        runCatching { db.execSQL("PRAGMA max_page_count = ${quota.maxPageCount}") }
    }

    companion object {
        /** 库文件所在子目录（相对 `databases/`）。 */
        const val CARD_DB_DIR = "cards"

        private const val DB_VERSION = 1

        /** 单次查询最多返回行数；超出请在 SQL 里自己加 `LIMIT`。 */
        const val MAX_ROWS = 1000

        @Volatile
        private var fts5Probe: Boolean? = null

        /**
         * 平台 SQLite 是否带 FTS5。首次调用时建一张临时虚拟表探测（结果缓存），
         * 卡片可据此决定用 FTS5 还是 `LIKE`。
         */
        fun fts5Supported(context: Context): Boolean {
            fts5Probe?.let { return it }
            val probe = probeFts5(context.applicationContext)
            fts5Probe = probe
            if (!probe) {
                Logging.log("CardSqlStore", "平台 SQLite 不支持 FTS5，卡片全文检索需退化为 LIKE")
            }
            return probe
        }

        private fun probeFts5(context: Context): Boolean = runCatching {
            val file = File(context.cacheDir, "khatkit-fts5-probe.db")
            file.delete()
            val db = FrameworkSQLiteOpenHelperFactory().create(
                SupportSQLiteOpenHelper.Configuration.builder(context)
                    .name(file.absolutePath)
                    .callback(object : SupportSQLiteOpenHelper.Callback(1) {
                        override fun onCreate(db: SupportSQLiteDatabase) {
                            db.execSQL("CREATE VIRTUAL TABLE probe USING fts5(text, tokenize='simple')")
                            db.execSQL("INSERT INTO probe(text) VALUES ('hello')")
                        }

                        override fun onUpgrade(db: SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) = Unit
                    })
                    .build(),
            )
            val ok = runCatching {
                db.writableDatabase.query("SELECT count(*) FROM probe").use { it.moveToFirst() }
            }.isSuccess
            db.close()
            runCatching { context.deleteDatabase(file.absolutePath) }
            file.delete()
            ok
        }.getOrDefault(false)
    }
}