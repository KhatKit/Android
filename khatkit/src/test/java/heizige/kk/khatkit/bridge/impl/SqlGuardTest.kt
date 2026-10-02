package heizige.kk.khatkit.bridge.impl

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class SqlGuardTest {

    private fun check(sql: String) = SqlGuard.check(sql)

    private fun errorOf(sql: String): String = try {
        check(sql)
        fail("应当拒绝：$sql")
        ""
    } catch (e: IllegalArgumentException) {
        e.message.orEmpty()
    }

    @Test
    fun `plain ddl and dml pass`() {
        check("CREATE TABLE notes(id INTEGER PRIMARY KEY, body TEXT)")
        check("INSERT INTO notes(body) VALUES (?)")
        check("SELECT * FROM notes WHERE id = ?")
        check("select body from notes limit 10;")
        check("UPDATE notes SET body = ? WHERE id = 1")
        check("DELETE FROM notes WHERE id < 100")
    }

    @Test
    fun `attach and detach rejected`() {
        assertTrue(errorOf("ATTACH DATABASE '/data/data/heizige.kk.khatkit.app/databases/rikkahub.db' AS x").contains("ATTACH"))
        assertTrue(errorOf("attach database ':memory:' as m").contains("ATTACH"))
        assertTrue(errorOf("DETACH DATABASE main").contains("DETACH"))
    }

    @Test
    fun `vacuum and extension loading rejected`() {
        assertTrue(errorOf("VACUUM").contains("VACUUM"))
        assertTrue(errorOf("VACUUM INTO '/sdcard/stolen.db'").contains("VACUUM"))
        assertTrue(errorOf("SELECT load_extension('x.so')").contains("LOAD_EXTENSION"))
    }

    @Test
    fun `multi statement batches rejected`() {
        assertTrue(errorOf("DROP TABLE notes; DROP TABLE kv").contains("一条语句"))
        assertTrue(errorOf("INSERT INTO t VALUES (1);; DELETE FROM t").contains("一条语句"))
    }

    @Test
    fun `blank statement rejected`() {
        assertTrue(errorOf("   ").contains("不能为空"))
    }

    @Test
    fun `keywords inside literals or identifiers are not false positives`() {
        check("SELECT 'attach database' AS note")
        check("SELECT * FROM attachments WHERE kind = 'vacuum'")
        check("SELECT attach_count FROM stats")
        check("""SELECT "detach" FROM logs""")
    }

    @Test
    fun `row returning keywords detected`() {
        assertTrue(SqlGuard.returnsRows("SELECT 1"))
        assertTrue(SqlGuard.returnsRows("  with t as (select 1) select * from t"))
        assertTrue(SqlGuard.returnsRows("PRAGMA table_info(notes)"))
        assertTrue(SqlGuard.returnsRows("explain select 1"))
        assertTrue(SqlGuard.returnsRows("VALUES (1)"))
        assertFalse(SqlGuard.returnsRows("INSERT INTO notes(body) VALUES ('x')"))
        assertFalse(SqlGuard.returnsRows("CREATE TABLE t(a)"))
        assertFalse(SqlGuard.returnsRows("DELETE FROM notes"))
    }

    @Test
    fun `comment tail does not count as second statement`() {
        check("SELECT 1 -- 说明")
        check("SELECT 1; -- 说明")
        check("SELECT 1 /* 一次只允许一条；别想在注释里塞语句 */")
    }

    @Test
    fun `second statement hidden after a comment is rejected`() {
        assertTrue(errorOf("SELECT 1; DROP TABLE notes -- 清理").contains("一条语句"))
        assertTrue(errorOf("SELECT 1 /* ; */ ; DROP TABLE notes").contains("一条语句"))
    }
}