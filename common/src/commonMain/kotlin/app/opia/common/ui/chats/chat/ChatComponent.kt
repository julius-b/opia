package app.opia.common.ui.chats.chat

import OpiaDispatchers
import app.opia.common.ui.auth.AuthCtx
import app.opia.common.ui.chats.chat.OpiaChat.Event
import app.opia.common.ui.chats.chat.OpiaChat.Model
import app.opia.common.ui.chats.chat.OpiaChat.Output
import app.opia.common.ui.chats.chat.store.ChatStore.Intent
import app.opia.common.ui.chats.chat.store.ChatStore.Label
import app.opia.common.ui.chats.chat.store.ChatStore.State
import app.opia.common.ui.chats.chat.store.ChatStoreProvider
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

class ChatComponent(
    componentContext: ComponentContext,
    storeFactory: StoreFactory,
    dispatchers: OpiaDispatchers,
    authCtx: AuthCtx,
    peerId: UUID,
    private val output: (Output) -> Unit
) : OpiaChat, ComponentContext by componentContext {
    private val store = instanceKeeper.getStore {
        ChatStoreProvider(
            storeFactory = storeFactory,
            dispatchers = dispatchers,
            authCtx = authCtx,
            peerId = peerId
        ).provide()
    }

    override val models: Value<Model> = store.asValue().map(stateToModel)

    override val events: Flow<Event> = store.labels.map(transform = labelToEvent)

    override fun onBackClicked() {
        output(Output.Back)
    }

    override fun onSendClicked(txt: String) {
        store.accept(Intent.AddMessage(txt))
    }
}

internal val stateToModel: (State) -> Model = {
    Model(
        self = it.self, peer = it.peer, msgs = it.msgs
    )
}

internal val labelToEvent: (Label) -> Event = {
    when (it) {
        is Label.NetworkError -> Event.NetworkError
        is Label.UnknownError -> Event.UnknownError
    }
}
