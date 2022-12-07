package app.opia.common.ui

import app.opia.common.db.Actor
import app.opia.common.di.ServiceLocator
import app.opia.common.ui.auth.AuthComponent
import app.opia.common.ui.auth.OpiaAuth
import app.opia.common.ui.auth.registration.OpiaRegistration
import app.opia.common.ui.auth.registration.RegistrationComponent
import app.opia.common.ui.chats.ChatsComponent
import app.opia.common.ui.chats.OpiaChats
import app.opia.common.ui.chats.chat.ChatComponent
import app.opia.common.ui.chats.chat.OpiaChat
import app.opia.common.ui.splash.OpiaSplash
import app.opia.common.ui.splash.SplashComponent
import app.opia.common.utils.Consumer
import com.arkivanov.decompose.ComponentContext
import com.arkivanov.decompose.router.stack.*
import com.arkivanov.decompose.value.Value
import com.arkivanov.essenty.parcelable.Parcelable
import com.arkivanov.essenty.parcelable.Parcelize
import com.arkivanov.mvikotlin.core.store.StoreFactory
import com.badoo.reaktive.base.Consumer
import java.util.*

class OpiaRootComponent internal constructor(
    componentContext: ComponentContext,
    private val splash: (ComponentContext, Consumer<OpiaSplash.Output>) -> OpiaSplash,
    private val auth: (ComponentContext, Consumer<OpiaAuth.Output>) -> OpiaAuth,
    private val registration: (ComponentContext, Consumer<OpiaRegistration.Output>) -> OpiaRegistration,
    private val chats: (ComponentContext, selfId: UUID, Consumer<OpiaChats.Output>) -> OpiaChats,
    private val chat: (ComponentContext, selfId: UUID, peerId: UUID, Consumer<OpiaChat.Output>) -> OpiaChat
) : OpiaRoot, ComponentContext by componentContext {

    constructor(
        componentContext: ComponentContext,
        storeFactory: StoreFactory,
        di: ServiceLocator,
    ) : this(componentContext = componentContext, splash = { childContext, output ->
        SplashComponent(
            componentContext = childContext, storeFactory = storeFactory, di = di, output = output
        )
    }, auth = { childContext, output ->
        AuthComponent(
            componentContext = childContext, storeFactory = storeFactory, di = di, output = output
        )
    }, registration = { childContext, output ->
        RegistrationComponent(
            componentContext = childContext, storeFactory = storeFactory, di = di, output = output
        )
    }, chats = { childContext, selfId, output ->
        ChatsComponent(
            componentContext = childContext,
            storeFactory = storeFactory,
            di = di,
            selfId = selfId,
            output = output
        )
    }, chat = { childContext, selfId, peerId, output ->
        ChatComponent(
            componentContext = childContext,
            storeFactory = storeFactory,
            di = di,
            selfId = selfId,
            peerId = peerId,
            output = output
        )
    })

    private val navigation = StackNavigation<Configuration>()

    private val stack = childStack(
        source = navigation,
        initialConfiguration = Configuration.Splash,
        handleBackButton = true,
        childFactory = ::createChild
    )

    override val childStack: Value<ChildStack<*, OpiaRoot.Child>> = stack

    private fun createChild(
        configuration: Configuration, componentContext: ComponentContext
    ): OpiaRoot.Child = when (configuration) {
        is Configuration.Splash -> OpiaRoot.Child.Splash(
            splash(componentContext, Consumer(::onSplashOutput))
        )
        is Configuration.Auth -> OpiaRoot.Child.Auth(
            auth(componentContext, Consumer(::onAuthOutput))
        )
        is Configuration.Registration -> OpiaRoot.Child.Registration(
            registration(componentContext, Consumer(::onRegistrationOutput))
        )
        is Configuration.Chats -> OpiaRoot.Child.Chats(
            chats(componentContext, configuration.selfId, Consumer(::onChatsOutput))
        )
        is Configuration.Chat -> OpiaRoot.Child.Chat(
            chat(componentContext, configuration.selfId, configuration.peerId, Consumer(::onChatOutput))
        )
    }

    // reset splash state by replacing it - on logout, it will requery the db again
    private fun onSplashOutput(output: OpiaSplash.Output) = when (output) {
        is OpiaSplash.Output.Auth -> navigation.replaceCurrent(Configuration.Auth)
        is OpiaSplash.Output.Main -> navigation.replaceCurrent(Configuration.Chats(output.selfId))
    }

    private fun onAuthOutput(output: OpiaAuth.Output) = when (output) {
        // keep Auth
        is OpiaAuth.Output.Register -> navigation.push(Configuration.Registration)
        is OpiaAuth.Output.ContinueWithProvider -> {
            println("[+] onAuthOutput > navigating to provider screen: ${output.provider}")
        }
        is OpiaAuth.Output.Authenticated -> navigation.replaceCurrent(Configuration.Chats(output.selfId))
    }

    private fun onRegistrationOutput(output: OpiaRegistration.Output) = when (output) {
        is OpiaRegistration.Output.Authenticated -> {
            // pop Registration
            navigation.pop()
            // replace Auth
            navigation.replaceCurrent(Configuration.Chats(output.selfId))
        }
        is OpiaRegistration.Output.BackToAuth -> navigation.pop()
    }

    private fun onChatsOutput(output: OpiaChats.Output): Unit = when (output) {
        is OpiaChats.Output.Back -> navigation.replaceCurrent(Configuration.Splash)
        is OpiaChats.Output.Selected -> navigation.push(Configuration.Chat(output.selfId, output.peerId))
    }

    private fun onChatOutput(output: OpiaChat.Output) = when (output) {
        is OpiaChat.Output.Back -> navigation.pop()
    }

    private sealed class Configuration : Parcelable {
        @Parcelize
        object Splash : Configuration()

        @Parcelize
        object Auth : Configuration()

        @Parcelize
        object Registration : Configuration()

        @Parcelize
        data class Chats(val selfId: UUID) : Configuration()

        @Parcelize
        data class Chat(val selfId: UUID, val peerId: UUID) : Configuration()
    }
}
