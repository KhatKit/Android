package heizige.kk.khatkit.app.feature.settings

import heizige.kk.khatkit.app.feature.settings.data.*
import heizige.kk.khatkit.app.feature.settings.helper.DonationLoadManager
import kotlinx.coroutines.*
import org.junit.Assert.*
import org.junit.Test
import java.io.IOException

class DonationLoadManagerTest {
    private val snapshot = DonationSnapshot(
        DonationProfile(name = "KhatKit", projectUrl = "https://github.com/KhatKit/Android", websiteUrl = "https://heizige.top"),
        emptyList(), emptyList(), 0,
    )

    @Test fun acknowledgementEndpointCanRecoverIndependentlyOfOverviewFailure() {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        try {
            val loader = DonationLoadManager(scope, { "https://hub.example" }, { snapshot }, { throw IOException("overview offline") }, { _, offset ->
                DonationSupporterPage(listOf(DonationSupporter("more", "Supporter")), 1, 50, offset)
            })
            loader.refresh()
            assertTrue(loader.state.value.failed)
            assertEquals(snapshot, loader.state.value.snapshot)
            loader.loadSupporters(retry = true)
            assertTrue(loader.state.value.wallLoaded)
            assertEquals("more", loader.state.value.supporters.single().id)
        } finally { scope.cancel() }
    }

    @Test fun switchingHubDropsCacheAndIgnoresLateOldResponse() = runBlocking {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        val late = CompletableDeferred<DonationSnapshot>()
        var hub = "https://old.example"
        try {
            val loader = DonationLoadManager(scope, { hub }, { base -> snapshot.takeIf { base.contains("old") } }, { base ->
                if (base.contains("old")) withContext(NonCancellable) { late.await() } else snapshot.copy(profile = snapshot.profile.copy(name = "New Hub"))
            }, { _, offset -> DonationSupporterPage(emptyList(), 0, 50, offset) })
            loader.refresh()
            hub = "https://new.example"
            loader.refresh()
            assertEquals("New Hub", loader.state.value.snapshot?.profile?.name)
            late.complete(snapshot)
            yield()
            assertEquals("New Hub", loader.state.value.snapshot?.profile?.name)
        } finally { scope.cancel() }
    }

    @Test fun refreshDiscardsInFlightPageAndRapidClicksIssueOnlyOneRequest() = runBlocking {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        val late = CompletableDeferred<DonationSupporterPage>()
        var requests = 0
        try {
            val loader = DonationLoadManager(scope, { "https://hub.example" }, { null }, { snapshot }, { _, _ ->
                requests++
                withContext(NonCancellable) { late.await() }
            })
            loader.refresh()
            loader.loadSupporters()
            loader.loadSupporters()
            assertEquals(1, requests)
            loader.refresh()
            late.complete(DonationSupporterPage(listOf(DonationSupporter("stale", "Old supporter")), 1, 50, 0))
            yield()
            assertFalse(loader.state.value.wallLoaded)
            assertTrue(loader.state.value.supporters.isEmpty())
        } finally { scope.cancel() }
    }

    @Test fun failedPageRetryKeepsCursorAndRetainsAlreadyLoadedRows() {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        var failNext = true
        val cursors = mutableListOf<Int>()
        try {
            val loader = DonationLoadManager(scope, { "https://hub.example" }, { null }, { snapshot }, { _, offset ->
                cursors += offset
                if (offset > 0 && failNext) { failNext = false; throw IOException("page failed") }
                DonationSupporterPage(listOf(DonationSupporter("s-$offset", "Supporter")), 2, 50, offset)
            })
            loader.refresh()
            loader.loadSupporters()
            loader.loadSupporters()
            assertTrue(loader.state.value.wallFailed)
            assertEquals(1, loader.state.value.supporters.size)
            loader.loadSupporters(retry = true)
            assertEquals(listOf(0, 1, 1), cursors)
            assertEquals(2, loader.state.value.supporters.size)
            loader.loadSupporters()
            assertEquals(3, cursors.size)
        } finally { scope.cancel() }
    }
}
