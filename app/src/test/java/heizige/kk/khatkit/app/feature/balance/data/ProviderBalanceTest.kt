package heizige.kk.khatkit.app.feature.balance.data

import heizige.kk.khatkit.ai.provider.BalanceOption
import heizige.kk.khatkit.common.http.getByKey
import heizige.kk.khatkit.common.http.getByKeyStrict
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import org.junit.Assert.*
import org.junit.Test
import kotlin.uuid.Uuid
import heizige.kk.khatkit.app.feature.balance.helper.LowBalanceEpisodes
import heizige.kk.khatkit.app.feature.balance.helper.BalanceQuery
import heizige.kk.khatkit.app.feature.balance.helper.isLow

class ProviderBalanceTest {
    private val options = BalanceOption(enabled = true, resultPath = "data.remaining", lowBalanceAlert = true)
    private fun eval(json: String, expr: String = options.resultPath) = Json.parseToJsonElement(json).jsonObject.getByKeyStrict(expr)

    @Test fun oldOptionsStayOptInAndThresholdRoundTrips() {
        val old = Json.decodeFromString<BalanceOption>("""{"enabled":true,"apiPath":"/v1/credits","resultPath":"data.remaining"}""")
        assertFalse(old.lowBalanceAlert)
        assertNull(old.isLow("0"))
        val custom = old.copy(lowBalanceAlert = true, lowBalanceThreshold = "0.125")
        assertEquals(custom, Json.decodeFromString<BalanceOption>(Json.encodeToString(custom)))
    }

    @Test fun thresholdUsesUnroundedDecimalAndStrictBoundary() {
        assertEquals(true, options.isLow(eval("""{"data":{"remaining":4.9999999999999999}}""")))
        assertEquals(false, options.isLow(eval("""{"data":{"remaining":"5.000"}}""")))
        assertEquals(false, options.isLow("5.001"))
        assertEquals(true, options.isLow("-0.1"))
        assertEquals(true, options.copy(lowBalanceThreshold = "0,01").isLow("0.009"))
    }

    @Test fun unknownSamplesThresholdsAndUsageAreNotLow() {
        for (raw in listOf(null, "", "null", "unlimited", "NaN", "Infinity")) assertNull(options.isLow(raw))
        for (threshold in listOf("", "n/a", "0", "-1")) assertNull(options.copy(lowBalanceThreshold = threshold).isLow("0"))
        assertNull(options.copy(enabled = false).isLow("0"))
        assertNull(options.copy(resultPath = " data . total_usage ").isLow("0"))
        assertNull(options.isLow(eval("""{"data":{}}""")))
    }

    @Test fun remainingFormulaPreservesDecimalsAndRejectsMissingOperands() {
        val formula = "data.total_credits - data.total_usage"
        val amount = eval("""{"data":{"total_credits":5.1,"total_usage":"0.1001"}}""", formula)
        assertEquals("4.9999", amount)
        assertEquals(true, options.copy(resultPath = formula).isLow(amount))
        for (bad in listOf("{}", """{"data":{"total_usage":2}}""", """{"data":{"total_credits":5,"total_usage":"unlimited"}}""")) {
            assertThrows(RuntimeException::class.java) { eval(bad, formula) }
        }
        assertEquals("1", Json.parseToJsonElement("{}").jsonObject.getByKey("data.missing + 1"))
        assertThrows(RuntimeException::class.java) {
            Json.parseToJsonElement("{}").jsonObject.getByKeyStrict("data.missing + 1")
        }
    }

    @Test fun lowEpisodeSurvivesReopenUnknownResultsAndOtherProviders() {
        val stored = mutableSetOf<Uuid>()
        fun reopen() = LowBalanceEpisodes(stored::contains) { id, low -> if (low) stored.add(id) else stored.remove(id) }
        val a = Uuid.random()
        val b = Uuid.random()
        val first = reopen()
        assertFalse(first.enteredLow(a, false))
        assertTrue(first.enteredLow(a, true))
        assertFalse(first.enteredLow(a, null))
        val next = reopen()
        assertFalse(next.enteredLow(a, true))
        assertTrue(next.enteredLow(b, true))
        assertFalse(next.enteredLow(a, false))
        assertTrue(next.enteredLow(a, true))
        assertFalse(next.enteredLow(b, true))
    }

    @Test fun concurrentConsumersShareAQueryAndCanRefreshOrRetry() = runBlocking {
        var clock = 0L
        val q = BalanceQuery { clock }
        assertEquals(BalanceReading.NotQueried, q.reading.value)
        val release = CompletableDeferred<Unit>()
        val entered = CompletableDeferred<Unit>()
        val a = async { q.refresh { entered.complete(Unit); release.await(); "4" } }
        entered.await()
        val b = async { q.refresh { throw AssertionError("duplicate HTTP query") } }
        release.complete(Unit)
        a.await()
        b.await()
        assertEquals(BalanceReading.Value("4"), q.reading.value)
        q.refresh(force = true) { error("HTTP 503") }
        assertEquals(BalanceReading.Unavailable, q.reading.value)
        q.refresh { "" }
        assertEquals(BalanceReading.Unavailable, q.reading.value)
        q.refresh { "5" }
        clock = 119_999L
        q.refresh { throw AssertionError("unexpired cache") }
        clock++
        q.refresh { "3" }
        assertEquals(BalanceReading.Value("3"), q.reading.value)
        assertThrows(CancellationException::class.java) { runBlocking { q.refresh(true) { throw CancellationException() } } }
    }.let { Unit }
}
