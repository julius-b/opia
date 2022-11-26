package app.opia.common.ui.chats.store

import app.opia.common.db.Actor
import app.opia.common.di.ServiceLocator
import app.opia.common.ui.chats.ChatsItem
import app.opia.common.ui.chats.store.ChatsStore.*
import com.arkivanov.mvikotlin.core.store.Reducer
import com.arkivanov.mvikotlin.core.store.SimpleBootstrapper
import com.arkivanov.mvikotlin.core.store.Store
import com.arkivanov.mvikotlin.core.store.StoreFactory
import com.arkivanov.mvikotlin.extensions.coroutines.CoroutineExecutor
import com.squareup.sqldelight.runtime.coroutines.asFlow
import com.squareup.sqldelight.runtime.coroutines.mapToList
import com.squareup.sqldelight.runtime.coroutines.mapToOne
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import mainDispatcher
import java.util.*

private fun Actor.toItem(): ChatsItem = ChatsItem(
    id = id,
    name = name,
    text = handle, // TODO latest msg
)

internal class ChatsStoreProvider (
    private val storeFactory: StoreFactory,
    private val di: ServiceLocator
) {
    fun provide(): ChatsStore =
        object : ChatsStore, Store<Intent, State, Label> by storeFactory.create(
            name = "OpiaChatsStore",
            initialState = State(),
            bootstrapper = SimpleBootstrapper(Unit),
            executorFactory = ::ExecutorImpl,
            reducer = ReducerImpl
        ) {}

    private sealed class Msg {
        data class ItemsLoaded(val items: List<ChatsItem>) : Msg()
        data class ItemDeleted(val id: UUID) : Msg()
    }

    private inner class ExecutorImpl : CoroutineExecutor<Intent, Unit, State, Msg, Label>(mainDispatcher()) {
        override fun executeAction(action: Unit, getState: () -> State) {
            scope.launch {
                di.database.actorQueries.listAll().asFlow().mapToList().collect {
                    dispatch(Msg.ItemsLoaded(it.map { it.toItem() }))
                }
            }
            scope.launch {
                println("querying user info...")
                val authSession = di.database.sessionQueries.getLatest().asFlow().mapToOne().first()
                println("authSession: $authSession")
                val actor = di.database.actorQueries.getById(authSession.actor_id).asFlow().mapToOne().first()
                println("actor: $actor")
                val ownedFields = di.database.owned_fieldQueries.listByActor(actor.id).asFlow().mapToList().first()
                println("ownedFields: $ownedFields")
            }
        }

        override fun executeIntent(intent: Intent, getState: () -> State): Unit =
            when (intent) {
                is Intent.Logout -> logout()
                is Intent.DeleteItem -> deleteItem(intent.id)
                //is Intent.AddItem -> addItem(getState())
            }

        private fun logout() {
            scope.launch {
                di.actorRepo.logout()
                publish(Label.LoggedOut)
            }
        }

        private fun deleteItem(id: UUID) {
            dispatch(Msg.ItemDeleted(id = id))
            //database.delete(id = id).subscribeScoped()
        }

        private fun addItem(state: State) {
            if (state.text.isNotEmpty()) {
                //dispatch(Msg.TextChanged(text = ""))
                //database.add(text = state.text).subscribeScoped()
            }
        }
    }

    private object ReducerImpl : Reducer<State, Msg> {
        override fun State.reduce(msg: Msg): State =
            when (msg) {
                is Msg.ItemsLoaded -> copy(items = msg.items) //copy(items = msg.items.sorted())
                is Msg.ItemDeleted -> copy(items = items.filterNot { it.id == msg.id })
            }

        private inline fun State.update(id: UUID, func: ChatsItem.() -> ChatsItem): ChatsStore.State {
            val item = items.find { it.id == id } ?: return this

            return put(item.func())
        }

        private fun State.put(item: ChatsItem): State {
            val oldItems = items.associateByTo(mutableMapOf(), ChatsItem::id)
            val oldItem: ChatsItem? = oldItems.put(item.id, item)

            return copy(items = oldItems.values.toList())
            //return copy(items = if (oldItem?.order == item.order) oldItems.values.toList() else oldItems.values.sorted())
        }

        //private fun Iterable<ChatsItem>.sorted(): List<ChatsItem> = sortedByDescending(ChatsItem::order)
    }
}
