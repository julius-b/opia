package app.opia.common.ui.chats.store

import OpiaDispatchers
import app.opia.common.api.repository.LinkPerm
import app.opia.common.db.Actor
import app.opia.common.db.Actor_link
import app.opia.common.di.ServiceLocator
import app.opia.common.sync.ChatSync
import app.opia.common.ui.chats.ChatsItem
import app.opia.common.ui.chats.store.ChatsStore.*
import app.opia.common.ui.home.AppComponentContext
import app.opia.common.ui.home.OpiaHome.HomeModel
import com.arkivanov.decompose.value.Value
import com.arkivanov.mvikotlin.core.store.Reducer
import com.arkivanov.mvikotlin.core.store.SimpleBootstrapper
import com.arkivanov.mvikotlin.core.store.Store
import com.arkivanov.mvikotlin.core.store.StoreFactory
import com.arkivanov.mvikotlin.extensions.coroutines.CoroutineExecutor
import com.squareup.sqldelight.runtime.coroutines.asFlow
import com.squareup.sqldelight.runtime.coroutines.mapToList
import com.squareup.sqldelight.runtime.coroutines.mapToOne
import com.squareup.sqldelight.runtime.coroutines.mapToOneOrNull
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import java.time.ZonedDateTime
import java.util.*
import kotlin.system.exitProcess

internal class ChatsStoreProvider(
    componentContext: AppComponentContext,
    private val storeFactory: StoreFactory,
    private val di: ServiceLocator,
    private val dispatchers: OpiaDispatchers,
    private val mainModel: Value<HomeModel>
) {
    private val db = di.database
    private val keyRepo = componentContext.keyRepo
    private val actorRepo = componentContext.actorRepo
    private val msgApi = componentContext.messagingRepo.api

    fun provide(): ChatsStore =
        object : ChatsStore, Store<Intent, State, Label> by storeFactory.create(
            name = "ChatsStore",
            initialState = State(),
            bootstrapper = SimpleBootstrapper(Unit),
            executorFactory = ::ExecutorImpl,
            reducer = ReducerImpl
        ) {}

    private sealed class Msg {
        data class SelfUpdated(val self: Actor) : Msg()
        data class ChatsLoaded(val chats: List<ChatsItem>) : Msg()
        data class ChatDeleted(val id: UUID) : Msg()
        data class SearchQueryChanged(val query: String) : Msg()
        data class SearchErrorChanged(val error: String) : Msg()
    }

    private inner class ExecutorImpl :
        CoroutineExecutor<Intent, Unit, State, Msg, Label>(dispatchers.main) {
        override fun executeAction(action: Unit, getState: () -> State) {
            GlobalScope.launch(Dispatchers.Default + CoroutineExceptionHandler { _, throwable ->
                println("[!] ChatSync > t: $throwable")
                println("[!] ChatSync > t: ${throwable.message}")
                throwable.printStackTrace()
                exitProcess(-1)
            }) {
                val id = UUID.randomUUID()
                val chatSync = withContext(Dispatchers.IO) { ChatSync.init(di, keyRepo, msgApi) }
                while (true) {
                    println("[*] ChatSync [$id] > syncing...")
                    // check if logout occurred...
                    val aS = db.sessionQueries.getLatest().asFlow().mapToOneOrNull().first()
                    if (aS == null) {
                        println("[!] Sync > logged out, exiting...")
                        break
                    }

                    withContext(Dispatchers.IO) { chatSync.sync() }
                    delay(5000L)
                }
            }

            scope.launch {
                // TODO maybe don't poll... idk just an idea
                val self: Actor
                //mainModel.observe()
                while (true) {
                    delay(100L)
                    if (mainModel.value.self == null) return@launch
                    self = mainModel.value.self!!
                    break
                }
                println("[*] ChatsStore > actor: $self")
                dispatch(Msg.SelfUpdated(self))
                val ownedFields = withContext(Dispatchers.IO) {
                    db.ownedFieldQueries.listByActor(self.id).asFlow().mapToList().first()
                }
                println("[*] ChatsStore > ownedFields: $ownedFields")

                actorRepo.listLinks()

                // refresh when db refreshes - maybe listLinks should return a Flow
                db.actorQueries.listLinksForActor(self.id).asFlow().mapToList().collect {
                    val chats = it.map { link ->
                        // TODO get latest msg per chat
                        // actor should exist in db, only time this returns null is if unauthenticated
                        val peer = actorRepo.getActor(link.peer_id)
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
                val peer = actorRepo.getActorByHandle(state.searchQuery)
                if (peer == null) {
                    dispatch(Msg.SearchErrorChanged("User not found :["))
                    return@launch
                }
                db.actorQueries.insertLink(
                    Actor_link(
                        state.self!!.id,
                        peer.id,
                        LinkPerm.isAdmin.ordinal.toLong(),
                        ZonedDateTime.now(),
                        state.self.id,
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
            is Msg.SelfUpdated -> copy(self = msg.self)
            is Msg.ChatsLoaded -> copy(chats = msg.chats) //copy(items = msg.items.sorted())
            is Msg.ChatDeleted -> copy(chats = chats.filterNot { it.id == msg.id })
            is Msg.SearchQueryChanged -> copy(searchQuery = msg.query, searchError = null)
            is Msg.SearchErrorChanged -> copy(searchError = msg.error)
        }
    }
}
