package app.opia.common.ui.chats.store

import OpiaDispatchers
import app.opia.common.api.repository.LinkPerm
import app.opia.common.db.Actor_link
import app.opia.common.di.ServiceLocator
import app.opia.common.ui.chats.ChatsItem
import app.opia.common.ui.chats.store.ChatsStore.Intent
import app.opia.common.ui.chats.store.ChatsStore.Label
import app.opia.common.ui.chats.store.ChatsStore.State
import com.arkivanov.mvikotlin.core.store.Reducer
import com.arkivanov.mvikotlin.core.store.SimpleBootstrapper
import com.arkivanov.mvikotlin.core.store.Store
import com.arkivanov.mvikotlin.core.store.StoreFactory
import com.arkivanov.mvikotlin.extensions.coroutines.CoroutineExecutor
import com.squareup.sqldelight.runtime.coroutines.asFlow
import com.squareup.sqldelight.runtime.coroutines.mapToList
import com.squareup.sqldelight.runtime.coroutines.mapToOne
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.ZonedDateTime
import java.util.UUID

internal class ChatsStoreProvider(
    private val storeFactory: StoreFactory, private val dispatchers: OpiaDispatchers
) {
    private val db = ServiceLocator.database

    fun provide(): ChatsStore =
        object : ChatsStore, Store<Intent, State, Label> by storeFactory.create(
            name = "ChatsStore",
            initialState = State(),
            bootstrapper = SimpleBootstrapper(Unit),
            executorFactory = ::ExecutorImpl,
            reducer = ReducerImpl
        ) {}

    private sealed class Msg {
        data class ChatsLoaded(val chats: List<ChatsItem>) : Msg()
        data class ChatDeleted(val id: UUID) : Msg()
        data class SearchQueryChanged(val query: String) : Msg()
        data class SearchErrorChanged(val error: String) : Msg()
    }

    private inner class ExecutorImpl :
        CoroutineExecutor<Intent, Unit, State, Msg, Label>(dispatchers.main) {
        override fun executeAction(action: Unit, getState: () -> State) {
            if (!ServiceLocator.isAuthenticated()) {
                println("[~] ChatsStore - not authenticated, expecting logout...")
                return
            }
            scope.launch {
                val self = withContext(ServiceLocator.dispatchers.io) {
                    ServiceLocator.actorRepo.getActor(ServiceLocator.authCtx.actorId)!!
                }
                println("[*] ChatsStore > actor: $self")
                val ownedFields = withContext(Dispatchers.IO) {
                    db.ownedFieldQueries.listByActor(self.id).executeAsList()
                }
                println("[*] ChatsStore > ownedFields: $ownedFields")

                ServiceLocator.actorRepo.listLinks()

                // refresh when db refreshes - maybe listLinks should return a Flow
                db.actorQueries.listLinksForActor(self.id).asFlow().mapToList().collect {
                    val chats = it.map { link ->
                        // TODO get latest msg per chat
                        // actor should exist in db, only time this returns null is if unauthenticated
                        val peer = ServiceLocator.actorRepo.getActor(link.peer_id)
                        if (peer == null) {
                            println("[!] ChatsStore > failed to query peer: ${link.peer_id}")
                            return@collect
                        }
                        ChatsItem(link.peer_id, peer.name, "@${peer.handle} / ${peer.desc}")
                    }
                    dispatch(Msg.ChatsLoaded(chats))
                }
            }
        }

        override fun executeIntent(intent: Intent, getState: () -> State) = when (intent) {
            is Intent.SetSearchQuery -> dispatch(Msg.SearchQueryChanged(intent.query))
            is Intent.Search -> search(getState())
            is Intent.OpenChat -> openChat(intent.id)
            is Intent.DeleteItem -> deleteItem(intent.id)
        }

        private fun search(state: State) {
            scope.launch {
                val peer = ServiceLocator.actorRepo.getActorByHandle(state.searchQuery)
                if (peer == null) {
                    dispatch(Msg.SearchErrorChanged("User not found :["))
                    return@launch
                }
                db.actorQueries.insertLink(
                    Actor_link(
                        ServiceLocator.authCtx.actorId,
                        peer.id,
                        LinkPerm.isAdmin.ordinal.toLong(),
                        ZonedDateTime.now(),
                        ServiceLocator.authCtx.actorId,
                        null,
                        null,
                        null
                    )
                )
                dispatch(Msg.SearchQueryChanged(""))
                publish(Label.SearchFinished)
            }
        }

        private fun openChat(id: UUID) {
            scope.launch {
                val peer = withContext(Dispatchers.IO) {
                    db.actorQueries.getById(id).asFlow().mapToOne().first()
                }
                publish(Label.ChatOpened(peer.id))
            }
        }

        private fun deleteItem(id: UUID) {
            dispatch(Msg.ChatDeleted(id))
            //database.delete(id = id).subscribeScoped()
        }
    }

    private object ReducerImpl : Reducer<State, Msg> {
        override fun State.reduce(msg: Msg) = when (msg) {
            is Msg.ChatsLoaded -> copy(chats = msg.chats) //copy(items = msg.items.sorted())
            is Msg.ChatDeleted -> copy(chats = chats.filterNot { it.id == msg.id })
            is Msg.SearchQueryChanged -> copy(searchQuery = msg.query, searchError = null)
            is Msg.SearchErrorChanged -> copy(searchError = msg.error)
        }
    }
}
