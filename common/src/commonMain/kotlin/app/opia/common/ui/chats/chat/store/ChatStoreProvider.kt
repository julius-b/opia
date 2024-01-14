package app.opia.common.ui.chats.chat.store

import app.cash.sqldelight.coroutines.asFlow
import app.cash.sqldelight.coroutines.mapToList
import app.opia.common.db.Actor
import app.opia.common.db.Msg_payload
import app.opia.common.di.ServiceLocator
import app.opia.common.ui.auth.AuthCtx
import app.opia.common.ui.chats.chat.MessageItem
import app.opia.common.ui.chats.chat.store.ChatStore.Intent
import app.opia.common.ui.chats.chat.store.ChatStore.Label
import app.opia.common.ui.chats.chat.store.ChatStore.State
import com.arkivanov.mvikotlin.core.store.Reducer
import com.arkivanov.mvikotlin.core.store.SimpleBootstrapper
import com.arkivanov.mvikotlin.core.store.Store
import com.arkivanov.mvikotlin.core.store.StoreFactory
import com.arkivanov.mvikotlin.extensions.coroutines.CoroutineExecutor
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.ZonedDateTime
import java.util.UUID

internal class ChatStoreProvider(
    private val storeFactory: StoreFactory, private val authCtx: AuthCtx, private val peerId: UUID
) {
    private val dispatchers = ServiceLocator.dispatchers
    private val db = ServiceLocator.database

    fun provide(): ChatStore =
        object : ChatStore, Store<Intent, State, Label> by storeFactory.create(
            name = "ChatStore",
            initialState = State(),
            bootstrapper = SimpleBootstrapper(Action.Start),
            executorFactory = ::ExecutorImpl,
            reducer = ReducerImpl
        ) {}

    private sealed interface Action {
        data object Start : Action
    }

    private sealed class Msg {
        data class SelfUpdated(val self: Actor) : Msg()
        data class PeerUpdated(val peer: Actor) : Msg()
        data class MsgsUpdated(val msgs: List<MessageItem>) : Msg()
    }

    // TODO how to get actor parameter into initial state
    private inner class ExecutorImpl :
        CoroutineExecutor<Intent, Action, State, Msg, Label>(dispatchers.main) {
        override fun executeAction(action: Action, getState: () -> State) = when (action) {
            is Action.Start -> loadStateFromDb(getState())
        }

        override fun executeIntent(intent: Intent, getState: () -> State) = when (intent) {
            is Intent.AddMessage -> addMessage(intent.txt, getState())
        }

        private fun loadStateFromDb(state: State) {
            scope.launch {
                val self = withContext(dispatchers.io) {
                    db.actorQueries.getById(authCtx.actorId).executeAsOne()
                }
                val peer = withContext(dispatchers.io) {
                    db.actorQueries.getById(peerId).executeAsOne()
                }
                dispatch(Msg.SelfUpdated(self))
                dispatch(Msg.PeerUpdated(peer))
                db.msgQueries.listAll(peerId, authCtx.actorId).asFlow().mapToList(coroutineContext)
                    .collectLatest {
                        dispatch(Msg.MsgsUpdated(it.map { msg ->
                            // from may be any actor in a group, self is unique
                            val from = if (msg.from_id == authCtx.actorId) null else peer.name
                            MessageItem(from, msg.payload, msg.timestamp)
                        }))
                    }
            }
        }

        private fun addMessage(txt: String, state: State) {
            println("[*] addMsg > peer: ${state.peer!!.handle}, txt: $txt")
            val msgPayload = Msg_payload(
                UUID.randomUUID(), state.self!!.id, state.peer.id, txt, ZonedDateTime.now(), null
            )
            db.msgQueries.insertPayload(msgPayload)
        }
    }

    private object ReducerImpl : Reducer<State, Msg> {
        override fun State.reduce(msg: Msg) = when (msg) {
            is Msg.SelfUpdated -> copy(self = msg.self)
            is Msg.PeerUpdated -> copy(peer = msg.peer)
            is Msg.MsgsUpdated -> copy(msgs = msg.msgs.sortedBy { it.created_at })
        }
    }
}
