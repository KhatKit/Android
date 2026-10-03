package heizige.kk.khatkit.ai.util

/**
 * Priority groups, then round-robin inside the highest group that still has a live key.
 * 429/5xx uses 5s, 10s, 20s… capped at 5 minutes. After the wait, one half-open probe
 * is allowed; success returns the key to the pool.
 *
 * Behavior follows the client route spec. It is not a copy of HalfEmptyDrum/Key-Carousel (MIT).
 */
class KeyPool {
    private val failures = mutableMapOf<String, Failure>()
    private val cursors = mutableMapOf<Int, Int>()
    private val probing = mutableSetOf<String>()
    private val lastUsed = mutableMapOf<String, String>()

    @Synchronized
    fun choose(providerId: String, accounts: List<ProviderAccount>, nowMillis: Long): ProviderAccount? {
        val groups = accounts.filter { it.apiKey.isNotBlank() }
            .groupBy { it.priority }
            .toSortedMap(compareByDescending { it })
        for ((priority, group) in groups) {
            val open = group.filter { !isCooling(it.apiKey, nowMillis) }
            if (open.isNotEmpty()) {
                val cursor = cursors.getOrDefault(priority, 0)
                val selected = open[cursor % open.size]
                cursors[priority] = cursor + 1
                lastUsed[providerId] = selected.apiKey
                return selected
            }
            val probe = group.firstOrNull { canProbe(it.apiKey, nowMillis) } ?: continue
            probing.add(probe.apiKey)
            lastUsed[providerId] = probe.apiKey
            return probe
        }
        return null
    }

    @Synchronized
    fun reportFailure(key: String, status: Int, nowMillis: Long) {
        if (status != 429 && status !in 500..599) return
        val previous = failures[key]
        val count = (previous?.count ?: 0) + 1
        val delay = (5_000L shl (count - 1).coerceAtMost(16)).coerceAtMost(300_000L)
        failures[key] = Failure(count = count, untilMillis = nowMillis + delay)
        probing.remove(key)
    }

    @Synchronized
    fun reportSuccess(key: String) {
        failures.remove(key)
        probing.remove(key)
    }

    @Synchronized
    fun failLast(providerId: String, status: Int, nowMillis: Long) {
        val key = lastUsed[providerId] ?: return
        reportFailure(key, status, nowMillis)
    }

    @Synchronized
    fun coolingUntil(key: String): Long? = failures[key]?.untilMillis

    private fun isCooling(key: String, nowMillis: Long): Boolean {
        val until = failures[key]?.untilMillis ?: return false
        return nowMillis < until || key in probing
    }

    private fun canProbe(key: String, nowMillis: Long): Boolean {
        val until = failures[key]?.untilMillis ?: return false
        return nowMillis >= until && key !in probing
    }

    private data class Failure(val count: Int, val untilMillis: Long)

    companion object {
        val shared = KeyPool()
    }
}

data class ProviderAccount(
    val apiKey: String,
    val priority: Int = 0,
    val weight: Int = 1,
)
