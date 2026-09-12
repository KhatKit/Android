package heizige.kk.khatkit.hub

import heizige.kk.khatkit.card.CardManifest
import heizige.kk.khatkit.engine.EngineKind
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LibResolverTest {

    private class FakeProvider(private val libs: Map<String, ResolvedLib>) : LibSourceProvider {
        var calls = 0
        override suspend fun resolve(name: String, lang: String, versionRange: String): ResolvedLib? {
            calls++
            return libs["$name:$lang"]
        }
    }

    private fun requirement(name: String, lang: String) = CardManifest.LibRequirement(name, lang)

    @Test
    fun resolvesMatchingLanguage() = runBlocking {
        val bundled = FakeProvider(
            mapOf("@khatkit/std:js" to ResolvedLib("@khatkit/std", "js", "1.0.0", "module.exports = {}"))
        )
        val resolver = LibResolver(listOf(bundled))
        val resolved = resolver.resolve(
            listOf(requirement("@khatkit/std", "js"), requirement("@khatkit/std", "lua")),
            EngineKind.JS,
        )
        assertEquals(1, resolved.size)
        assertEquals("@khatkit/std", resolved.first().name)
    }

    @Test
    fun fallsBackToNextProvider() = runBlocking {
        val empty = FakeProvider(emptyMap())
        val hub = FakeProvider(
            mapOf("remote:lua" to ResolvedLib("remote", "lua", "2.0.0", "return {}"))
        )
        val resolver = LibResolver(listOf(empty, hub))
        val resolved = resolver.resolve(listOf(requirement("remote", "lua")), EngineKind.LUA)
        assertEquals("2.0.0", resolved.first().version)
        assertEquals(1, empty.calls)
        assertEquals(1, hub.calls)
    }

    @Test
    fun unresolvedIsDropped() = runBlocking {
        val resolver = LibResolver(listOf(FakeProvider(emptyMap())))
        val resolved = resolver.resolve(listOf(requirement("missing", "js")), EngineKind.JS)
        assertTrue(resolved.isEmpty())
    }

    @Test
    fun commandEngineResolvesNothing() = runBlocking {
        val provider = FakeProvider(mapOf("x:js" to ResolvedLib("x", "js", "1.0.0", "")))
        val resolver = LibResolver(listOf(provider))
        assertTrue(resolver.resolve(listOf(requirement("x", "js")), EngineKind.COMMAND).isEmpty())
        assertEquals(0, provider.calls)
    }
}
