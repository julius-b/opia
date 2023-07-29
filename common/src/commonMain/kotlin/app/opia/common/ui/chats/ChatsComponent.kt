package app.opia.common.ui.chats

import OpiaDispatchers
import app.opia.common.ui.chats.OpiaChats.Event
import app.opia.common.ui.chats.OpiaChats.Model
import app.opia.common.ui.chats.OpiaChats.Output
import app.opia.common.ui.chats.store.ChatsStore.Intent
import app.opia.common.ui.chats.store.ChatsStore.Label
import app.opia.common.ui.chats.store.ChatsStore.State
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
import java.util.UUID

class ChatsComponent(
    componentContext: ComponentContext,
    storeFactory: StoreFactory,
    dispatchers: OpiaDispatchers,
    private val output: (Output) -> Unit
) : OpiaChats, ComponentContext by componentContext {
    private val store = instanceKeeper.getStore {
        ChatsStoreProvider(
            storeFactory = storeFactory,
            dispatchers = dispatchers
        ).provide()
    }

    override val models: Value<Model> = store.asValue().map(stateToModel)

    override val events: Flow<Event> = store.labels.map(transform = labelToEvent)

    override fun onChatClicked(peerId: UUID) {
        store.accept(Intent.OpenChat(peerId))
    }

    override fun continueToChat(peerId: UUID) {
        output(Output.Selected(peerId))
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
        chats = it.chats, searchQuery = it.searchQuery, searchError = it.searchError
    )
}

internal val labelToEvent: (Label) -> Event = {
    when (it) {
        is Label.SearchFinished -> Event.SearchFinished
        is Label.ChatOpened -> Event.ChatOpened(it.peerId)
    }
}
