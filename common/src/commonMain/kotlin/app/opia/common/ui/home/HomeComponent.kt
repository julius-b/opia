package app.opia.common.ui.home

import OpiaDispatchers
import app.opia.common.di.ServiceLocator
import app.opia.common.ui.chats.ChatsComponent
import app.opia.common.ui.chats.OpiaChats
import app.opia.common.ui.chats.chat.ChatComponent
import app.opia.common.ui.chats.chat.OpiaChat
import app.opia.common.ui.home.OpiaHome.Child
import app.opia.common.ui.settings.OpiaSettings
import app.opia.common.ui.settings.SettingsComponent
import com.arkivanov.decompose.ComponentContext
import com.arkivanov.decompose.router.stack.*
import com.arkivanov.decompose.value.Value
import com.arkivanov.decompose.value.operator.map
import com.arkivanov.essenty.parcelable.Parcelable
import com.arkivanov.essenty.parcelable.Parcelize
import com.arkivanov.mvikotlin.core.store.StoreFactory
import java.util.*

class HomeComponent(
    componentContext: ComponentContext,
    private val selfId: UUID,
    private val chats: (ComponentContext, (OpiaChats.Output) -> Unit) -> OpiaChats,
    private val chat: (ComponentContext, peerId: UUID, (OpiaChat.Output) -> Unit) -> OpiaChat,
    private val settings: (ComponentContext) -> OpiaSettings
) : OpiaHome, ComponentContext by componentContext {

    constructor(
        componentContext: ComponentContext,
        storeFactory: StoreFactory,
        di: ServiceLocator,
        dispatchers: OpiaDispatchers,
        selfId: UUID
    ) : this(componentContext = componentContext, selfId = selfId,
        chats = { childContext, output ->
            ChatsComponent(
                componentContext = childContext,
                storeFactory = storeFactory,
                di = di,
                dispatchers = dispatchers,
                selfId = selfId,
                output = output
            )
        }, chat = { childContext, peerId, output ->
            ChatComponent(
                componentContext = childContext,
                storeFactory = storeFactory,
                di = di,
                dispatchers = dispatchers,
                selfId = selfId,
                peerId = peerId,
                output = output
            )
        }, settings = { childContext ->
            SettingsComponent(
                componentContext = childContext,
                storeFactory = storeFactory,
                di = di,
                dispatchers = dispatchers,
                selfId = selfId
            )
        }
    )

    private val navigation = StackNavigation<Configuration>()

    private val stack = childStack(
        source = navigation,
        initialConfiguration = Configuration.Chats(selfId),
        handleBackButton = true,
        childFactory = ::createChild
    )

    override val childStack: Value<ChildStack<*, Child>> = stack

    override val activeChildIndex: Value<Int> = childStack.map {
        it.active.instance.index!!
    }

    override fun onBarSelect(index: Int) {
        navigation.replaceCurrent(
            when (index) {
                0 -> Configuration.Chats(selfId)
                1 -> Configuration.Settings(selfId)
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
            settings(componentContext)
        )
    }

    private fun onChatsOutput(output: OpiaChats.Output): Unit = when (output) {
        is OpiaChats.Output.Selected -> navigation.push(
            Configuration.Chat(output.selfId, output.peerId)
        )
    }

    private fun onChatOutput(output: OpiaChat.Output) = when (output) {
        is OpiaChat.Output.Back -> navigation.pop()
    }

    private sealed class Configuration : Parcelable {
        @Parcelize
        data class Chats(val selfId: UUID) : Configuration()

        @Parcelize
        data class Chat(val selfId: UUID, val peerId: UUID) : Configuration()

        @Parcelize
        data class Settings(val selfId: UUID) : Configuration()
    }
}
