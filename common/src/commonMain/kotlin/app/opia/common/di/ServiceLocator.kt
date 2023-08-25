package app.opia.common.di

import OpiaDispatchers
import app.opia.common.NotificationRepo
import app.opia.common.api.RetrofitClient
import app.opia.common.api.repository.ActorRepo
import app.opia.common.api.repository.AuthRepo
import app.opia.common.api.repository.InstallationRepo
import app.opia.common.api.repository.KeyRepo
import app.opia.common.api.repository.MessagingRepo
import app.opia.common.sync.Message
import app.opia.common.sync.msgActor
import app.opia.common.ui.auth.AuthCtx
import app.opia.db.OpiaDatabase
import ch.oxc.nikea.initCrypto
import kotlinx.coroutines.CompletableJob
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.channels.SendChannel
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import java.util.LinkedList
import java.util.UUID

data class AuthData(
    var installationId: UUID,
    val ioid: UUID,
    val actorId: UUID,
    private var exitHandlers: MutableList<suspend () -> Unit> = LinkedList(),
    // run all authenticated requests on this coro, cancelled on logout
    val job: CompletableJob = Job()
) {
    private val mutex = Mutex()

    // TODO this should probably be moved
    // but: when okHttpClient doesn't require `logout` to be an init param, the same instance can be used everywhere
    // - no more diff between authenticated (nullable) & unauthenticated repositories (and unauth in unexpected moment always leads to logout)
    // - and when Home may be launched after Service, it has to register in a matter independent of the existing RetrofitClient
    suspend fun addLogoutHandler(handler: suspend () -> Unit) = mutex.withLock {
        exitHandlers.add(handler)
    }

    private suspend fun getAndClearLogoutHandlers(): List<suspend () -> Unit> {
        mutex.withLock {
            val ret = exitHandlers
            exitHandlers = LinkedList()
            return ret
        }
    }

    suspend fun logout() {
        println("[~] SL > auth - cancelling authJob...")
        job.cancelAndJoin()
        getAndClearLogoutHandlers().forEach { it() }
        println("[~] SL > auth - logging out repo...")
        ServiceLocator.authRepo.logout()
        println("[~] SL > auth - logout done")
    }
}

// TODO use Koin
// Only provides unauthenticated repositories
object ServiceLocator {
    // only used after init
    lateinit var authCtx: AuthData

    // only used in contexts where auth status is not known
    fun isAuthenticated() = this::authCtx.isInitialized

    lateinit var dispatchers: OpiaDispatchers
        private set
    lateinit var database: OpiaDatabase
        private set
    lateinit var notificationRepo: NotificationRepo
        private set
    lateinit var msgChan: SendChannel<Message>
        private set

    val installationRepo by lazy {
        InstallationRepo(
            database.installationQueries, RetrofitClient.newInstallationService(retrofitClient)
        )
    }

    val authRepo by lazy {
        AuthRepo(
            database,
            RetrofitClient.newActorService(retrofitClient),
            RetrofitClient.newKeyService(retrofitClient)
        )
    }

    // independent of auth, only one
    lateinit var mainScope: CoroutineScope
        private set

    // authenticated, lazy for lateinit db
    val keyRepo by lazy {
        KeyRepo(database.keyPairQueries, RetrofitClient.newKeyService(retrofitClient))
    }
    val msgRepo by lazy {
        MessagingRepo(database, RetrofitClient.newMessagingService(retrofitClient))
    }
    val actorRepo by lazy { ActorRepo(database, RetrofitClient.newActorService(retrofitClient)) }

    private val okHttpClient: OkHttpClient = RetrofitClient.newOkHttpClient()
    private val retrofitClient: Retrofit = RetrofitClient.newRetrofitClient(okHttpClient)

    fun init(
        dispatchers: OpiaDispatchers, database: OpiaDatabase, notificationRepo: NotificationRepo
    ) {
        this.dispatchers = dispatchers
        this.database = database
        this.notificationRepo = notificationRepo
        mainScope = MainScope()
        mainScope.launch(dispatchers.io) { initCrypto() }
    }

    // globally register the fact that app is now authenticated
    fun initAuth(authCtx: AuthCtx) {
        this.authCtx = AuthData(authCtx.installationId, authCtx.ioid, authCtx.actorId)
        this.msgChan = mainScope.msgActor(this.authCtx.job)
    }
}
