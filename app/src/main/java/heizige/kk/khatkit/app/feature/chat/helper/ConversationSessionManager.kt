package heizige.kk.khatkit.app.feature.chat.helper

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import heizige.kk.khatkit.app.core.data.model.Conversation
import kotlin.uuid.Uuid
import heizige.kk.khatkit.app.feature.chat.data.MessageQueueState


/** Owns runtime sessions; loading, persistence and message dispatch belong to ChatManager. */
class ConversationSessionManager(
    private val scope: CoroutineScope,
    private val createInitialConversation: (Uuid) -> Conversation,
    private val onGenerationFinished: (ConversationRuntimeManager, Throwable?) -> Unit,
) {
    private val lock = Any()
    private val sessions = MutableStateFlow<Map<Uuid, ConversationRuntimeManager>>(emptyMap())

    fun get(conversationId: Uuid): ConversationRuntimeManager? = sessions.value[conversationId]

    fun snapshot(): List<ConversationRuntimeManager> = sessions.value.values.toList()

    fun getOrCreate(conversationId: Uuid): ConversationRuntimeManager = synchronized(lock) {
        get(conversationId)?.let { return it }
        lateinit var session: ConversationRuntimeManager
        session = ConversationRuntimeManager(
            id = conversationId,
            initial = createInitialConversation(conversationId),
            scope = scope,
            onIdle = { removeIfIdle(session) },
            onGenerationFinished = { id, cause ->
                if (get(id) === session) onGenerationFinished(session, cause)
            },
        )
        // Publish the actual registry, so collectors never observe a version before insertion.
        sessions.value = sessions.value + (conversationId to session)
        session
    }

    fun acquire(conversationId: Uuid): ConversationRuntimeManager = synchronized(lock) {
        getOrCreate(conversationId).also { it.acquire() }
    }

    fun release(conversationId: Uuid) {
        get(conversationId)?.release()
    }

    suspend fun <T> withSession(
        conversationId: Uuid,
        block: suspend (ConversationRuntimeManager) -> T,
    ): T {
        val session = acquire(conversationId)
        try {
            return block(session)
        } finally {
            session.release()
        }
    }

    fun launchWithSession(
        conversationId: Uuid,
        block: suspend (ConversationRuntimeManager) -> Unit,
    ): Job = scope.launch { withSession(conversationId, block) }

    internal fun removeIfIdle(session: ConversationRuntimeManager) {
        val removed = synchronized(lock) {
            if (get(session.id) !== session || session.isInUse) return
            sessions.value = sessions.value - session.id
            session
        }
        // Job completion can call back into the registry; never hold its lock during cleanup.
        removed.cleanup()
    }

    fun cleanup() {
        val removed = synchronized(lock) {
            snapshot().also { sessions.value = emptyMap() }
        }
        removed.forEach { it.cleanup() }
    }

    fun getConversationFlow(conversationId: Uuid): StateFlow<Conversation> =
        getOrCreate(conversationId).state

    fun getProcessingStatusFlow(conversationId: Uuid): StateFlow<String?> =
        getOrCreate(conversationId).processingStatus

    fun getMessageQueueFlow(conversationId: Uuid): StateFlow<MessageQueueState> =
        getOrCreate(conversationId).messageQueue.state

    fun getGenerationJobStateFlow(conversationId: Uuid): Flow<Job?> =
        sessions.map { it[conversationId] }.distinctUntilChanged().flatMapLatest { session ->
            session?.generationJob ?: flowOf(null)
        }

    fun getConversationJobs(): Flow<Map<Uuid, Job?>> = sessions.flatMapLatest { current ->
        if (current.isEmpty()) {
            flowOf(emptyMap())
        } else {
            combine(current.values.map { session ->
                session.generationJob.map { session.id to it }
            }) { pairs ->
                pairs.filter { it.second != null }.toMap()
            }
        }
    }
}
