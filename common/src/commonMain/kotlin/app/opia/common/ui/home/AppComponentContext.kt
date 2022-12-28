package app.opia.common.ui.home

import app.opia.common.api.RetrofitClient
import app.opia.common.api.repository.ActorRepo
import app.opia.common.api.repository.InstallationRepo
import app.opia.common.api.repository.KeyRepo
import app.opia.common.api.repository.MessagingRepo
import app.opia.common.di.ServiceLocator
import com.arkivanov.decompose.ComponentContext
import com.arkivanov.decompose.router.stack.ChildStack
import com.arkivanov.decompose.router.stack.StackNavigationSource
import com.arkivanov.decompose.router.stack.childStack
import com.arkivanov.decompose.value.Value
import com.arkivanov.essenty.parcelable.Parcelable
import retrofit2.Retrofit
import kotlin.reflect.KClass

// documentation: https://arkivanov.github.io/Decompose/component/custom-component-context/
interface AppComponentContext : ComponentContext {

    val logout: suspend () -> Unit

    /**
     * An authenticated client for making http requests with Retrofit.
     */
    val retrofitClient: Retrofit

    val installationRepo: InstallationRepo
    val keyRepo: KeyRepo
    val actorRepo: ActorRepo
    val messagingRepo: MessagingRepo
}

class DefaultAppComponentContext(
    componentContext: ComponentContext,
    di: ServiceLocator,
    override val logout: suspend () -> Unit
) : AppComponentContext, ComponentContext by componentContext {
    override val retrofitClient = RetrofitClient.newRetrofitClient(
        RetrofitClient.newOkHttpClient(di, logout)
    )

    override val installationRepo = InstallationRepo(
        di.database.installationQueries, RetrofitClient.newInstallationService(retrofitClient)
    )

    override val keyRepo =
        KeyRepo(di.database.keyPairQueries, RetrofitClient.newKeyService(retrofitClient))

    override val actorRepo = ActorRepo(di.database, RetrofitClient.newActorService(retrofitClient))

    override val messagingRepo =
        MessagingRepo(di.database, RetrofitClient.newMessagingService(retrofitClient))
}

fun <C : Parcelable, T : Any> AppComponentContext.appChildStack(
    di: ServiceLocator,
    logout: suspend () -> Unit,
    source: StackNavigationSource<C>,
    initialStack: () -> List<C>,
    configurationClass: KClass<out C>,
    key: String = "DefaultStack",
    handleBackButton: Boolean = false,
    childFactory: (configuration: C, AppComponentContext) -> T
): Value<ChildStack<C, T>> = childStack(
    source = source,
    initialStack = initialStack,
    configurationClass = configurationClass,
    key = key,
    handleBackButton = handleBackButton
) { configuration, componentContext ->
    childFactory(
        configuration, DefaultAppComponentContext(
            componentContext = componentContext,
            di = di,
            logout = logout
        )
    )
}

inline fun <reified C : Parcelable, T : Any> AppComponentContext.appChildStack(
    di: ServiceLocator,
    noinline logout: suspend () -> Unit,
    source: StackNavigationSource<C>,
    noinline initialStack: () -> List<C>,
    key: String = "DefaultStack",
    handleBackButton: Boolean = false,
    noinline childFactory: (configuration: C, AppComponentContext) -> T
): Value<ChildStack<C, T>> = appChildStack(
    di = di,
    logout = logout,
    source = source,
    initialStack = initialStack,
    configurationClass = C::class,
    key = key,
    handleBackButton = handleBackButton,
    childFactory = childFactory,
)

inline fun <reified C : Parcelable, T : Any> AppComponentContext.appChildStack(
    di: ServiceLocator,
    noinline logout: suspend () -> Unit,
    source: StackNavigationSource<C>,
    initialConfiguration: C,
    key: String = "DefaultStack",
    handleBackButton: Boolean = false,
    noinline childFactory: (configuration: C, AppComponentContext) -> T
): Value<ChildStack<C, T>> = appChildStack(
    di = di,
    logout = logout,
    source = source,
    initialStack = { listOf(initialConfiguration) },
    configurationClass = C::class,
    key = key,
    handleBackButton = handleBackButton,
    childFactory = childFactory,
)