package app.opia.common.ui

import OpiaDispatchers
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
import com.arkivanov.decompose.ComponentContext
import com.arkivanov.decompose.router.stack.*
import com.arkivanov.decompose.value.Value
import com.arkivanov.essenty.parcelable.Parcelable
import com.arkivanov.essenty.parcelable.Parcelize
import com.arkivanov.mvikotlin.core.store.StoreFactory
import kotlinx.coroutines.channels.Channel
import java.util.*

// TODO output func or Flow?? try Channel, but let's see how sync behaves
class OpiaRootComponent internal constructor(
    componentContext: ComponentContext,
    private val splash: (ComponentContext, (OpiaSplash.Output) -> Unit) -> OpiaSplash,
    private val auth: (ComponentContext, (OpiaAuth.Output) -> Unit) -> OpiaAuth,
    private val registration: (ComponentContext, (OpiaRegistration.Output) -> Unit) -> OpiaRegistration,
    private val chats: (ComponentContext, selfId: UUID, (OpiaChats.Output) -> Unit) -> OpiaChats,
    private val chat: (ComponentContext, selfId: UUID, peerId: UUID, (OpiaChat.Output) -> Unit) -> OpiaChat
) : OpiaRoot, ComponentContext by componentContext {

    constructor(
        componentContext: ComponentContext,
        storeFactory: StoreFactory,
        di: ServiceLocator,
        dispatchers: OpiaDispatchers
    ) : this(componentContext = componentContext, splash = { childContext, output ->
        SplashComponent(
            componentContext = childContext,
            storeFactory = storeFactory,
            di = di,
            dispatchers = dispatchers,
            output = output
        )
    }, auth = { childContext, output ->
        AuthComponent(
            componentContext = childContext,
            storeFactory = storeFactory,
            di = di,
            dispatchers = dispatchers,
            output = output
        )
    }, registration = { childContext, output ->
        RegistrationComponent(
            componentContext = childContext,
            storeFactory = storeFactory,
            dispatchers = dispatchers,
            di = di,
            output = output
        )
    }, chats = { childContext, selfId, output ->
        ChatsComponent(
            componentContext = childContext,
            storeFactory = storeFactory,
            di = di,
            dispatchers = dispatchers,
            selfId = selfId,
            output = output
        )
    }, chat = { childContext, selfId, peerId, output ->
        ChatComponent(
            componentContext = childContext,
            storeFactory = storeFactory,
            di = di,
            dispatchers = dispatchers,
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
            splash(componentContext, ::onSplashOutput)
        )
        is Configuration.Auth -> OpiaRoot.Child.Auth(
            auth(componentContext, ::onAuthOutput)
        )
        is Configuration.Registration -> OpiaRoot.Child.Registration(
            registration(componentContext, ::onRegistrationOutput)
        )
        is Configuration.Chats -> OpiaRoot.Child.Chats(
            chats(componentContext, configuration.selfId, ::onChatsOutput)
        )
        is Configuration.Chat -> OpiaRoot.Child.Chat(
            chat(
                componentContext, configuration.selfId, configuration.peerId, ::onChatOutput
            )
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
        is OpiaChats.Output.Selected -> navigation.push(
            Configuration.Chat(output.selfId, output.peerId)
        )
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
