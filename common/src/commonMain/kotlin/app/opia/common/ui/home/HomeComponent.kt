package app.opia.common.ui.home

import OpiaDispatchers
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
import com.arkivanov.decompose.ComponentContext
import com.arkivanov.decompose.router.stack.ChildStack
import com.arkivanov.decompose.router.stack.StackNavigation
import com.arkivanov.decompose.router.stack.childStack
import com.arkivanov.decompose.router.stack.pop
import com.arkivanov.decompose.router.stack.push
import com.arkivanov.decompose.router.stack.replaceCurrent
import com.arkivanov.decompose.value.Value
import com.arkivanov.decompose.value.operator.map
import com.arkivanov.essenty.parcelable.Parcelable
import com.arkivanov.essenty.parcelable.Parcelize
import com.arkivanov.mvikotlin.core.store.StoreFactory
import java.util.UUID

class HomeComponent(
    componentContext: ComponentContext,
    storeFactory: StoreFactory,
    dispatchers: OpiaDispatchers,
    private val authCtx: AuthCtx,
    private val homeOutput: (OpiaHome.Output) -> Unit,
    private val chats: (ComponentContext, (OpiaChats.Output) -> Unit) -> OpiaChats,
    private val chat: (ComponentContext, peerId: UUID, (OpiaChat.Output) -> Unit) -> OpiaChat,
    private val settings: (ComponentContext, (OpiaSettings.Output) -> Unit) -> OpiaSettings
) : OpiaHome, ComponentContext by componentContext {

    constructor(
        componentContext: ComponentContext,
        storeFactory: StoreFactory,
        dispatchers: OpiaDispatchers,
        authCtx: AuthCtx,
        homeOutput: (OpiaHome.Output) -> Unit
    ) : this(componentContext,
        storeFactory,
        dispatchers,
        authCtx,
        homeOutput,
        chats = { childContext, output ->
            ChatsComponent(
                componentContext = childContext,
                storeFactory = storeFactory,
                dispatchers = dispatchers,
                output = output
            )
        },
        chat = { childContext, peerId, output ->
            ChatComponent(
                componentContext = childContext,
                storeFactory = storeFactory,
                dispatchers = dispatchers,
                authCtx = authCtx,
                peerId = peerId,
                output = output
            )
        },
        settings = { childContext, output ->
            SettingsComponent(
                componentContext = childContext,
                storeFactory = storeFactory,
                dispatchers = dispatchers,
                authCtx = authCtx,
                output = output
            )
        })

    private val store = instanceKeeper.getStore {
        HomeStoreProvider(
            storeFactory = storeFactory, dispatchers = dispatchers, authCtx = authCtx
        ) { homeOutput(OpiaHome.Output.Logout) }.provide()
    }

    override val models: Value<HomeModel> = store.asValue().map(stateToModel)

    private val navigation = StackNavigation<Configuration>()

    private val stack = childStack(
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
        configuration: Configuration, componentContext: ComponentContext
    ): Child = when (configuration) {
        is Configuration.Chats -> Child.Chats(
            chats(componentContext, ::onChatsOutput)
        )

        is Configuration.Chat -> Child.Chat(
            chat(componentContext, configuration.peerId, ::onChatOutput)
        )

        is Configuration.Settings -> Child.Settings(
            settings(componentContext, ::onSettingsOutput)
        )
    }

    private fun onChatsOutput(output: OpiaChats.Output): Unit = when (output) {
        is OpiaChats.Output.Selected -> navigation.push(Configuration.Chat(authCtx, output.peerId))
    }

    private fun onChatOutput(output: OpiaChat.Output) = when (output) {
        is OpiaChat.Output.Back -> navigation.pop()
    }

    private fun onSettingsOutput(output: OpiaSettings.Output) = when (output) {
        is OpiaSettings.Output.Logout -> homeOutput(OpiaHome.Output.Logout)
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

internal val stateToModel: (HomeStore.State) -> HomeModel = { HomeModel(self = it.self) }
