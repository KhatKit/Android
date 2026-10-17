package heizige.kk.khatkit.app.core.data.repository

import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.nio.file.Files
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import kotlin.uuid.Uuid

class RoleMemoryRepositoryTest {
    @get:Rule val temp = TemporaryFolder()
    private val role = Uuid.random()
    private val clock = Clock.fixed(Instant.parse("2026-10-17T08:00:00Z"), ZoneOffset.UTC)
    private fun repository() = RoleMemoryRepository(temp.newFolder(), clock)

    @Test fun `facts persist across repository instances but never across roles`() = runBlocking {
        val repo = repository()
        repo.remember(role, "User prefers brief replies")
        assertTrue(RoleMemoryRepository(repo.root, clock).read(role).content.contains("brief replies"))
        assertEquals("", repo.read(Uuid.random()).content)
        assertTrue(repo.search(Uuid.random(), "brief").isEmpty())
    }

    @Test fun `duplicate facts are not appended`() = runBlocking {
        val repo = repository()
        repo.remember(role, "  User prefers   brief replies\n")
        repo.remember(role, "User prefers brief replies")
        assertEquals(1, repo.read(role).content.lines().count { it.startsWith("- ") })
    }

    @Test fun `changed facts retain a dated struck out predecessor`() = runBlocking {
        val repo = repository()
        repo.remember(role, "Invoices on the 1st")
        repo.remember(role, "Invoices on the 5th", replaces = "Invoices on the 1st")
        val text = repo.read(role).content
        assertTrue(text.contains("- ~~[2026-10-17] Invoices on the 1st~~ (superseded 2026-10-17)"))
        assertTrue(text.contains("- [2026-10-17] Invoices on the 5th"))
    }

    @Test fun `ambiguous corrections do not mutate any facts`() = runBlocking {
        val repo = repository()
        repo.remember(role, "Deploy production needs ticket")
        repo.remember(role, "Deploy staging needs ticket")
        val old = repo.read(role)
        assertTrue(runCatching { repo.remember(role, "Changed", replaces = "needs ticket") }.isFailure)
        assertEquals(old, repo.read(role))
    }

    @Test fun `forget preserves the dated change trail`() = runBlocking {
        val repo = repository()
        repo.remember(role, "Old preference")
        repo.forget(role, "Old preference")
        assertTrue(repo.read(role).content.contains("~~ (forgotten 2026-10-17)"))
        assertTrue(runCatching { repo.forget(role, "") }.isFailure)
    }

    @Test fun `stale editor receives the current version without overwriting it`() = runBlocking {
        val repo = repository()
        val editor = repo.read(role)
        repo.remember(role, "Background update")
        val result = runCatching { repo.save(role, "MEMORY.md", "my edit", editor.version) }
        val conflict = result.exceptionOrNull() as RoleMemoryConflict
        assertTrue(conflict.current.content.contains("Background update"))
        assertEquals(conflict.current, repo.read(role))
        repo.save(role, "MEMORY.md", "my edit", editor.version, overwrite = true)
        assertEquals("my edit", repo.read(role).content)
    }

    @Test fun `parallel role turns do not lose appended facts`() = runBlocking {
        val repo = repository()
        (1..40).map { n -> async { repo.remember(role, "Fact $n") } }.awaitAll()
        assertEquals(40, repo.read(role).content.lines().count { it.startsWith("- ") })
    }

    @Test fun `journals are idempotent per completed message`() = runBlocking {
        val repo = repository()
        val chat = Uuid.random()
        val message = Uuid.random()
        repo.appendTurn(role, chat, message, "Completed deployment")
        repo.appendTurn(role, chat, message, "Duplicate completion")
        val hits = repo.search(role, "deployment")
        assertEquals(1, hits.size)
        assertEquals("daily/2026-10-17.md", hits.single().path)
        assertTrue(repo.search(role, "Duplicate").isEmpty())
    }

    @Test fun `journals and topics stay out of the automatically loaded context`() = runBlocking {
        val repo = repository()
        repo.remember(role, "Short reply preference")
        repo.appendTurn(role, Uuid.random(), Uuid.random(), "JOURNAL_ONLY")
        val topic = repo.read(role, "topics/deploy.md")
        repo.save(role, topic.path, "TOPIC_ONLY", topic.version)
        val context = repo.context(role)
        assertTrue(context.contains("Short reply preference"))
        assertFalse(context.contains("JOURNAL_ONLY"))
        assertFalse(context.contains("TOPIC_ONLY"))
    }

    @Test fun `curated context obeys a stable head budget`() = runBlocking {
        val repo = repository()
        val initial = repo.read(role)
        repo.save(role, initial.path, "a".repeat(RoleMemoryRepository.CURATED_BUDGET) + "TAIL_NOT_LOADED", initial.version)
        assertFalse(repo.context(role).contains("TAIL_NOT_LOADED"))
        assertTrue(repo.read(role).content.endsWith("TAIL_NOT_LOADED"))
    }

    @Test fun `keyword and inclusive date filters work for facts and journals`() = runBlocking {
        val repo = repository()
        repo.remember(role, "Deployment needs ticket")
        repo.appendEvent(role, "Deployment started")
        assertEquals(2, repo.search(role, "Deployment", LocalDate.parse("2026-10-17"), LocalDate.parse("2026-10-17")).size)
        assertTrue(repo.search(role, "Deployment", until = LocalDate.parse("2026-10-16")).isEmpty())
        assertTrue(repo.search(role, "Deployment ticket").all { it.text.contains("ticket") })
        assertEquals(1, repo.search(role, "", limit = 1).size)
        assertTrue(runCatching {
            repo.search(role, "", LocalDate.parse("2026-10-18"), LocalDate.parse("2026-10-17"))
        }.isFailure)
    }

    @Test fun `path traversal and arbitrary files are rejected`() = runBlocking {
        val repo = repository()
        listOf("../other/MEMORY.md", "/etc/passwd", "topics/../../MEMORY.md", "topics/a/b.md", "daily/now.md").forEach {
            assertTrue(it, runCatching { repo.read(role, it) }.isFailure)
        }
    }

    @Test fun `symlink cannot escape a role directory`() = runBlocking {
        val repo = repository()
        repo.read(role)
        val external = temp.newFolder()
        Files.createSymbolicLink(File(repo.root, "$role/topics").toPath(), external.toPath())
        assertTrue(runCatching { repo.read(role, "topics/escape.md") }.isFailure)
    }

    @Test fun `oversized edits are rejected without truncating the old file`() = runBlocking {
        val repo = repository()
        val old = repo.remember(role, "Keep")
        assertTrue(runCatching {
            repo.save(role, "MEMORY.md", "x".repeat(RoleMemoryRepository.MAX_FILE_CHARS + 1), old.version)
        }.isFailure)
        assertEquals(old, repo.read(role))
    }

    @Test fun `legacy import is idempotent and global memory is never adopted`() = runBlocking {
        val repo = repository()
        repo.importLegacy(role, role.toString(), listOf("Previous personal fact"))
        repo.importLegacy(role, role.toString(), listOf("Should not reimport"))
        assertFalse(repo.read(role).content.contains("Should not reimport"))
        assertTrue(repo.read(role).content.contains("Previous personal fact"))
        assertTrue(runCatching { repo.importLegacy(role, MemoryRepository.GLOBAL_MEMORY_ID, listOf("Other role")) }.isFailure)
    }

    @Test fun `every memory write strips credentials before touching disk`() = runBlocking {
        val repo = repository()
        val secret = "sk-abcdefghijklmnopqrstuvwxyz"
        repo.remember(role, "Deploy with $secret password: hunter22")
        repo.appendTurn(role, Uuid.random(), Uuid.random(), "token=private-token-value")
        val note = repo.read(role, "topics/config.md")
        repo.save(role, note.path, "API_KEY=\"private-api-key\"", note.version)
        repo.importLegacy(role, "legacy", listOf("密钥：private-secret"))
        val all = File(repo.root, role.toString()).walkTopDown().filter { it.isFile && it.extension == "md" }.joinToString { it.readText() }
        listOf(secret, "hunter22", "private-token-value", "private-api-key", "private-secret").forEach {
            assertFalse(it, all.contains(it))
        }
        assertTrue(all.contains("[REDACTED]"))
    }

    @Test fun `credential scrubber is idempotent and preserves ordinary prose`() {
        val text = "The token budget is 20k; short replies are preferred."
        assertEquals(text, redactMemoryCredentials(text))
        val redacted = redactMemoryCredentials("Authorization: Bearer abcdefghijklmnopqrstuvwxyz")
        assertEquals(redacted, redactMemoryCredentials(redacted))
        listOf("ghp_abcdefghijklmnopqrstuvwx", "eyJabcdefghijk.abcdefghijk.abcdefghijk",
            "AKIAABCDEFGHIJKLMNOP", "xoxb-abcdefghijklm", "pwd: abcdef", "secret_key=abcdef").forEach {
            assertFalse(it, redactMemoryCredentials(it).contains(it))
        }
    }

    @Test fun `precompression archive preserves redacted searchable history without loading it`() = runBlocking {
        val repo = repository()
        val message = heizige.kk.khatkit.ai.ui.UIMessage.user("Deploy requires ticket ABC123 password: hunter22")
        val chat = Uuid.random()
        repo.archiveBeforeCompression(role, chat, listOf(message))
        repo.archiveBeforeCompression(role, chat, listOf(message))
        val path = repo.list(role).single()
        assertTrue(path.startsWith("topics/history-"))
        assertTrue(repo.search(role, "ABC123").isNotEmpty())
        assertFalse(repo.read(role, path).content.contains("hunter22"))
        assertFalse(repo.context(role).contains("ABC123"))
        assertTrue(repo.search(Uuid.random(), "ABC123").isEmpty())
    }

    @Test fun `explicit profile copy excludes daily logs and archived chat history`() = runBlocking {
        val repo = repository()
        val clone = Uuid.random()
        repo.remember(role, "Preference")
        repo.appendEvent(role, "Daily event")
        val topic = repo.read(role, "topics/deploy.md")
        repo.save(role, topic.path, "Deployment checklist", topic.version)
        repo.archiveBeforeCompression(role, Uuid.random(), listOf(heizige.kk.khatkit.ai.ui.UIMessage.user("Private transcript")))
        repo.copyProfile(role, clone)
        assertEquals(listOf("MEMORY.md", "topics/deploy.md"), repo.list(clone))
        assertTrue(repo.read(clone).content.contains("Preference"))
        assertTrue(repo.search(clone, "Daily event").isEmpty())
        assertTrue(repo.search(clone, "Private transcript").isEmpty())
    }

    @Test fun `manual memory deletion is confined to exactly one role`() = runBlocking {
        val repo = repository()
        val other = Uuid.random()
        repo.remember(role, "A")
        repo.remember(other, "B")
        repo.deleteRoleMemory(role)
        assertEquals("", repo.read(role).content)
        assertTrue(repo.read(other).content.contains("B"))
    }

    @Test fun `deleting a role removes symlinks without traversing their targets`() = runBlocking {
        val repo = repository()
        repo.remember(role, "A")
        val external = temp.newFolder()
        val keep = File(external, "keep.md").also { it.writeText("Keep external data") }
        Files.createSymbolicLink(File(repo.root, "$role/topics").toPath(), external.toPath())
        repo.deleteRoleMemory(role)
        assertEquals("Keep external data", keep.readText())
    }

    @Test fun `large legacy migration keeps overflow searchable instead of blocking generation`() = runBlocking {
        val repo = repository()
        repo.importLegacy(role, role.toString(), (1..100).map { "Old fact $it " + "x".repeat(2_000) })
        assertTrue(repo.list(role).any { it.startsWith("topics/legacy-") })
        assertTrue(repo.search(role, "Old fact 100").isNotEmpty())
        assertTrue(repo.read(role).content.length <= RoleMemoryRepository.MAX_FILE_CHARS)
    }
}
