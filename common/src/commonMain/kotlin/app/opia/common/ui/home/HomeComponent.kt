package app.opia.common.ui.home

import OpiaDispatchers
import app.opia.common.di.ServiceLocator
import app.opia.common.ui.auth.AuthCtx
import app.opia.common.ui.chats.ChatsComponent
import app.opia.common.ui.chats.OpiaChats
import app.opia.common.ui.chats.chat.ChatComponent
import app.opia.common.ui.chats.chat.OpiaChat
import app.opia.common.ui.home.OpiaHome.Child
import app.opia.common.ui.home.OpiaHome.HomeModel
import app.opia.common.ui.home.store.HomeStore
import app.opia.common.ui.home.store.HomeStoreProvider
import app.opia.common.ui.settings.OpiaSettings
import app.opia.common.ui.settings.SettingsComponent
import app.opia.common.utils.asValue
import app.opia.common.utils.getStore
import com.arkivanov.decompose.router.stack.*
import com.arkivanov.decompose.value.Value
import com.arkivanov.decompose.value.operator.map
import com.arkivanov.essenty.parcelable.Parcelable
import com.arkivanov.essenty.parcelable.Parcelize
import com.arkivanov.mvikotlin.core.store.StoreFactory
import java.util.*

class HomeComponent(
    componentContext: AppComponentContext,
    storeFactory: StoreFactory,
    di: ServiceLocator,
    dispatchers: OpiaDispatchers,
    private val authCtx: AuthCtx,
    private val chats: (AppComponentContext, Value<HomeModel>, (OpiaChats.Output) -> Unit) -> OpiaChats,
    private val chat: (AppComponentContext, peerId: UUID, (OpiaChat.Output) -> Unit) -> OpiaChat,
    private val settings: (AppComponentContext) -> OpiaSettings
) : OpiaHome, AppComponentContext by componentContext {

    constructor(
        componentContext: AppComponentContext,
        storeFactory: StoreFactory,
        di: ServiceLocator,
        dispatchers: OpiaDispatchers,
        authCtx: AuthCtx
    ) : this(componentContext,
        storeFactory,
        di,
        dispatchers,
        authCtx = authCtx,
        chats = { childContext, models, output ->
            ChatsComponent(
                componentContext = childContext,
                storeFactory = storeFactory,
                di = di,
                dispatchers = dispatchers,
                mainModel = models,
                output = output
            )
        },
        chat = { childContext, peerId, output ->
            ChatComponent(
                componentContext = childContext,
                storeFactory = storeFactory,
                di = di,
                dispatchers = dispatchers,
                authCtx = authCtx,
                peerId = peerId,
                output = output
            )
        },
        settings = { childContext ->
            SettingsComponent(
                componentContext = childContext,
                storeFactory = storeFactory,
                di = di,
                dispatchers = dispatchers,
                authCtx = authCtx
            )
        })

    private val store = instanceKeeper.getStore {
        HomeStoreProvider(
            storeFactory = storeFactory, di = di, dispatchers = dispatchers, authCtx = authCtx
        ).provide()
    }

    override val models: Value<HomeModel> = store.asValue().map(stateToModel)

    private val navigation = StackNavigation<Configuration>()

    private val stack = appChildStack(
        di = di,
        logout = logout,
        source = navigation,
        initialConfiguration = Configuration.Chats(authCtx),
        handleBackButton = true,
        childFactory = ::createChild
    )

    override val childStack: Value<ChildStack<*, Child>> = stack

    override val activeChild: Value<HomeChild> = childStack.map {
        it.active.instance.meta
    }

    override fun onBarSelect(index: Int) {
        navigation.replaceCurrent(
            when (index) {
                HomeChild.Chats.navIndex -> Configuration.Chats(authCtx)
                HomeChild.Settings.navIndex -> Configuration.Settings(authCtx)
                else -> error("index: $index")
            }
        )
    }

    private fun createChild(
        configuration: Configuration, componentContext: AppComponentContext
    ): Child = when (configuration) {
        is Configuration.Chats -> Child.Chats(
            chats(componentContext, models, ::onChatsOutput)
        )
        is Configuration.Chat -> Child.Chat(
            chat(componentContext, configuration.peerId, ::onChatOutput)
        )
        is Configuration.Settings -> Child.Settings(
            settings(componentContext)
        )
    }

    private fun onChatsOutput(output: OpiaChats.Output): Unit = when (output) {
        is OpiaChats.Output.Selected -> navigation.push(Configuration.Chat(authCtx, output.peerId))
    }

    private fun onChatOutput(output: OpiaChat.Output) = when (output) {
        is OpiaChat.Output.Back -> navigation.pop()
    }

    private sealed class Configuration : Parcelable {
        @Parcelize
        data class Chats(val authCtx: AuthCtx) : Configuration()

        @Parcelize
        data class Chat(val authCtx: AuthCtx, val peerId: UUID) : Configuration()

        @Parcelize
        data class Settings(val authCtx: AuthCtx) : Configuration()
    }
}

internal val stateToModel: (HomeStore.State) -> HomeModel = {
    HomeModel(
        self = it.self
    )
}
