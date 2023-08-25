package app.opia.common.ui

import app.opia.common.di.ServiceLocator
import app.opia.common.ui.auth.AuthComponent
import app.opia.common.ui.auth.AuthCtx
import app.opia.common.ui.auth.OpiaAuth
import app.opia.common.ui.auth.registration.OpiaRegistration
import app.opia.common.ui.auth.registration.RegistrationComponent
import app.opia.common.ui.home.HomeComponent
import app.opia.common.ui.home.OpiaHome
import app.opia.common.ui.splash.OpiaSplash
import app.opia.common.ui.splash.SplashComponent
import com.arkivanov.decompose.ComponentContext
import com.arkivanov.decompose.router.stack.ChildStack
import com.arkivanov.decompose.router.stack.StackNavigation
import com.arkivanov.decompose.router.stack.childStack
import com.arkivanov.decompose.router.stack.pop
import com.arkivanov.decompose.router.stack.push
import com.arkivanov.decompose.router.stack.replaceAll
import com.arkivanov.decompose.value.Value
import com.arkivanov.essenty.parcelable.Parcelable
import com.arkivanov.essenty.parcelable.Parcelize
import com.arkivanov.mvikotlin.core.store.StoreFactory
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.launch

class OpiaRootComponent internal constructor(
    componentContext: ComponentContext,
    private val splash: (ComponentContext, (OpiaSplash.Output) -> Unit) -> OpiaSplash,
    private val auth: (ComponentContext, (OpiaAuth.Output) -> Unit) -> OpiaAuth,
    private val registration: (ComponentContext, (OpiaRegistration.Output) -> Unit) -> OpiaRegistration,
    private val home: (ComponentContext, authCtx: AuthCtx, (OpiaHome.Output) -> Unit) -> OpiaHome
) : OpiaRoot, ComponentContext by componentContext {

    constructor(
        componentContext: ComponentContext,
        storeFactory: StoreFactory
    ) : this(componentContext = componentContext,
        splash = { childContext, output ->
            SplashComponent(
                componentContext = childContext,
                storeFactory = storeFactory,
                output = output
            )
        },
        auth = { childContext, output ->
            AuthComponent(
                componentContext = childContext,
                storeFactory = storeFactory,
                output = output
            )
        },
        registration = { childContext, output ->
            RegistrationComponent(
                componentContext = childContext,
                storeFactory = storeFactory,
                output = output
            )
        },
        home = { childContext, authCtx, output ->
            HomeComponent(
                componentContext = childContext,
                storeFactory = storeFactory,
                authCtx = authCtx,
                homeOutput = output
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

        is Configuration.Home -> OpiaRoot.Child.Home(
            home(componentContext, configuration.authCtx, ::onHomeOutput)
        )
    }

    // reset splash state by replacing it - on logout, it will requery the db again
    private fun onSplashOutput(output: OpiaSplash.Output) = when (output) {
        is OpiaSplash.Output.Auth -> navigation.replaceAll(Configuration.Auth)
        is OpiaSplash.Output.Main -> {
            ServiceLocator.initAuth(output.authCtx)
            navigation.replaceAll(Configuration.Home(output.authCtx))
        }
    }

    private fun onAuthOutput(output: OpiaAuth.Output) = when (output) {
        // keep Auth
        is OpiaAuth.Output.Register -> navigation.push(Configuration.Registration)
        is OpiaAuth.Output.ContinueWithProvider -> {
            println("[+] Root > onAuthOutput > navigating to provider screen: ${output.provider}")
        }

        is OpiaAuth.Output.Authenticated -> {
            ServiceLocator.initAuth(output.authCtx)
            navigation.replaceAll(Configuration.Home(output.authCtx))
        }
    }

    private fun onRegistrationOutput(output: OpiaRegistration.Output) = when (output) {
        is OpiaRegistration.Output.Authenticated -> {
            ServiceLocator.initAuth(output.authCtx)
            navigation.replaceAll(Configuration.Home(output.authCtx))
        }

        is OpiaRegistration.Output.BackToAuth -> navigation.pop()
    }

    private fun onHomeOutput(output: OpiaHome.Output) = when (output) {
        is OpiaHome.Output.Logout -> {
            println("[~] Root > Home > logout - clearing db & returning to splash/auth...")
            MainScope().launch {
                ServiceLocator.authCtx.logout()
                navigation.replaceAll(Configuration.Splash)
            }
        }
    }

    private sealed class Configuration : Parcelable {
        @Parcelize
        object Splash : Configuration()

        @Parcelize
        object Auth : Configuration()

        @Parcelize
        object Registration : Configuration()

        @Parcelize
        data class Home(val authCtx: AuthCtx) : Configuration()
    }
}
