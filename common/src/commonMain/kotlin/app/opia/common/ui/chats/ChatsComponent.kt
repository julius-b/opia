package app.opia.common.ui.chats

import OpiaDispatchers
import app.opia.common.di.ServiceLocator
import app.opia.common.ui.chats.OpiaChats.*
import app.opia.common.ui.chats.store.ChatsStore.*
import app.opia.common.ui.chats.store.ChatsStoreProvider
import app.opia.common.utils.asValue
import app.opia.common.utils.getStore
import com.arkivanov.decompose.ComponentContext
import com.arkivanov.decompose.value.Value
import com.arkivanov.decompose.value.operator.map
import com.arkivanov.mvikotlin.core.store.StoreFactory
import com.arkivanov.mvikotlin.extensions.coroutines.labels
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.util.*

class ChatsComponent(
    componentContext: ComponentContext,
    storeFactory: StoreFactory,
    di: ServiceLocator,
    dispatchers: OpiaDispatchers,
    private val selfId: UUID,
    private val output: (Output) -> Unit
) : OpiaChats, ComponentContext by componentContext {
    private val store = instanceKeeper.getStore {
        ChatsStoreProvider(
            storeFactory = storeFactory, di = di, dispatchers = dispatchers, selfId = selfId
        ).provide()
    }

    override val models: Value<Model> = store.asValue().map(stateToModel)

    override val events: Flow<Event> = store.labels.map(transform = labelToEvent)

    override fun logout() {
        store.accept(Intent.Logout)
    }

    override fun onBackClicked() {
        output(Output.Back)
    }

    override fun onChatClicked(peerId: UUID) {
        store.accept(Intent.OpenChat(peerId))
    }

    override fun continueToChat(selfId: UUID, peerId: UUID) {
        output(Output.Selected(selfId, peerId))
    }

    override fun onSearchUpdated(query: String) {
        store.accept(Intent.SetSearchQuery(query))
    }

    override fun onSearchClicked() {
        store.accept(Intent.Search)
    }
}

internal val stateToModel: (State) -> Model = {
    Model(
        self = it.self, chats = it.chats, searchQuery = it.searchQuery, searchError = it.searchError
    )
}

internal val labelToEvent: (Label) -> Event = {
    when (it) {
        is Label.LoggedOut -> Event.LoggedOut
        is Label.SearchFinished -> Event.SearchFinished
        is Label.ChatOpened -> Event.ChatOpened(it.selfId, it.peerId)
    }
}
