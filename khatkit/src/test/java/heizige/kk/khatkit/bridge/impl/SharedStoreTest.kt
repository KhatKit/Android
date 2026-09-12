package heizige.kk.khatkit.bridge.impl

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class SharedStoreTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    private fun store(owner: String) = SharedStore(tempFolder.root, owner)

    @Test
    fun writeAndReadOwnFile() {
        val a = store("card_a")
        a.write("result.txt", "hello")
        assertEquals("hello", a.read("result.txt"))
        assertTrue(a.exists("result.txt"))
    }

    @Test
    fun readAcrossCardsByFileName() {
        store("card_a").write("result.txt", "from-a")
        assertEquals("from-a", store("card_b").read("result.txt"))
    }

    @Test
    fun readOthersByExplicitPath() {
        store("card_a").write("notes/today.md", "# hi")
        assertEquals("# hi", store("card_b").read("card_a/notes/today.md"))
    }

    @Test
    fun deleteOnlyOwnNamespace() {
        store("card_a").write("x.txt", "a")
        assertTrue(store("card_a").delete("x.txt"))
        assertFalse(store("card_a").delete("x.txt"))

        store("card_a").write("y.txt", "a")
        assertFalse("card_b 不能删 card_a 的文件", store("card_b").delete("y.txt"))
        assertEquals("a", store("card_a").read("y.txt"))
    }

    @Test
    fun rejectsPathTraversalOnWrite() {
        assertThrows(IllegalArgumentException::class.java) {
            store("card_a").write("../escape.txt", "bad")
        }
        assertNull(store("card_b").read("../escape.txt"))
    }

    @Test
    fun listShowsOwnerPrefixedPaths() {
        store("card_a").write("result.txt", "a")
        store("card_b").write("notes.md", "b")
        val listed = store("card_a").list()
        assertTrue(listed.contains("card_a/result.txt"))
        assertTrue(listed.contains("card_b/notes.md"))
    }
}
