package app.opia.common.ui

import app.opia.common.di.ServiceLocator
import app.opia.common.ui.auth.OpiaAuth
import app.opia.common.ui.auth.integration.AuthComponent
import app.opia.common.ui.auth.registration.OpiaRegistration
import app.opia.common.ui.auth.registration.integration.RegistrationComponent
import app.opia.common.ui.chats.OpiaChats
import app.opia.common.ui.chats.integration.ChatsComponent
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

class OpiaRootComponent internal constructor(
    componentContext: ComponentContext,
    private val splash: (ComponentContext, Consumer<OpiaSplash.Output>) -> OpiaSplash,
    private val auth: (ComponentContext, Consumer<OpiaAuth.Output>) -> OpiaAuth,
    private val registration: (ComponentContext, Consumer<OpiaRegistration.Output>) -> OpiaRegistration,
    private val chats: (ComponentContext, Consumer<OpiaChats.Output>) -> OpiaChats
) : OpiaRoot, ComponentContext by componentContext {

    constructor(
        componentContext: ComponentContext,
        storeFactory: StoreFactory,
        di: ServiceLocator,
    ) : this(
        componentContext = componentContext,
        splash = { childContext, output ->
            SplashComponent(
                componentContext = childContext,
                storeFactory = storeFactory,
                di = di,
                output = output
            )
        },
        auth = { childContext, output ->
            AuthComponent(
                componentContext = childContext,
                storeFactory = storeFactory,
                di = di,
                output = output
            )
        },
        registration = { childContext, output ->
            RegistrationComponent(
                componentContext = childContext,
                storeFactory = storeFactory,
                di = di,
                output = output
            )
        },
        chats = { childContext, output ->
            ChatsComponent(
                componentContext = childContext,
                storeFactory = storeFactory,
                di = di,
                output = output
            )
        }
    )

    private val navigation = StackNavigation<Configuration>()

    private val stack =
        childStack(
            source = navigation,
            initialConfiguration = Configuration.Splash,
            handleBackButton = true,
            childFactory = ::createChild
        )

    override val childStack: Value<ChildStack<*, OpiaRoot.Child>> = stack

    private fun createChild(configuration: Configuration, componentContext: ComponentContext): OpiaRoot.Child =
        when (configuration) {
            is Configuration.Splash -> OpiaRoot.Child.Splash(splash(componentContext, Consumer(::onSplashOutput)))
            is Configuration.Auth -> OpiaRoot.Child.Auth(auth(componentContext, Consumer(::onAuthOutput)))
            is Configuration.Registration -> OpiaRoot.Child.Registration(registration(componentContext, Consumer(::onRegistrationOutput)))
            is Configuration.Chats -> OpiaRoot.Child.Chats(chats(componentContext, Consumer(::onChatsOutput)))
        }

    // reset splash state by replacing it - on logout, it will requery the db again
    private fun onSplashOutput(output: OpiaSplash.Output): Unit =
        when (output) {
            is OpiaSplash.Output.Auth -> navigation.replaceCurrent(Configuration.Auth)
            is OpiaSplash.Output.Main -> navigation.replaceCurrent(Configuration.Chats)
        }

    private fun onAuthOutput(output: OpiaAuth.Output): Unit =
        when (output) {
            // keep Auth
            is OpiaAuth.Output.Register -> navigation.push(Configuration.Registration)
            is OpiaAuth.Output.ContinueWithProvider -> {
                println("[+] onAuthOutput > navigating to provider screen: ${output.provider}")
            }
            is OpiaAuth.Output.Authenticated -> navigation.replaceCurrent(Configuration.Chats)
        }

    private fun onRegistrationOutput(output: OpiaRegistration.Output): Unit =
        when (output) {
            is OpiaRegistration.Output.Authenticated -> {
                // pop Registration
                navigation.pop()
                // replace Auth
                navigation.replaceCurrent(Configuration.Chats)
            }
            is OpiaRegistration.Output.BackToAuth -> navigation.pop()
        }

    private fun onChatsOutput(output: OpiaChats.Output): Unit =
        when (output) {
            is OpiaChats.Output.Back -> navigation.replaceCurrent(Configuration.Splash)
            is OpiaChats.Output.Selected -> navigation.push(Configuration.Chats)
        }

    private sealed class Configuration : Parcelable {
        @Parcelize
        object Splash : Configuration()

        @Parcelize
        object Auth : Configuration()

        @Parcelize
        object Registration : Configuration()

        @Parcelize
        object Chats : Configuration()
    }
}
