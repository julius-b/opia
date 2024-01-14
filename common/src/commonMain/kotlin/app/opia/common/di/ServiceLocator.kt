package app.opia.common.di

import OpiaDispatchers
import app.opia.common.NotificationRepo
import app.opia.common.api.RetrofitClient
import app.opia.common.api.repository.ActorRepo
import app.opia.common.api.repository.AuthRepo
import app.opia.common.api.repository.InstallationRepo
import app.opia.common.api.repository.KeyRepo
import app.opia.common.api.repository.MessagingRepo
import app.opia.common.db.Auth_session
import app.opia.common.sync.MsgSync
import app.opia.common.sync.Notifier
import app.opia.common.ui.auth.AuthCtx
import app.opia.db.OpiaDatabase
import ch.oxc.nikea.initCrypto
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import java.time.ZonedDateTime
import java.util.UUID

sealed class AuthStatus {
    data object Unauthenticated : AuthStatus()

    // TODO consider `val sess: Auth_session`
    data class Authenticated(
        val actorId: UUID,
        val ioid: UUID,
        val secretUpdateId: UUID,
        val refreshToken: String,
        val accessToken: String,
        val sessCreatedAt: ZonedDateTime
    ) : AuthStatus()
}

// TODO use Koin
object ServiceLocator {

    private val mutex = Mutex()

    private var auth: AuthStatus = AuthStatus.Unauthenticated

    // independent of auth, only one
    // TODO remove this? start actor in another way
    lateinit var mainScope: CoroutineScope
        private set

    private val okHttpClient: OkHttpClient = RetrofitClient.newOkHttpClient()
    private val retrofitClient: Retrofit = RetrofitClient.newRetrofitClient(okHttpClient)

    lateinit var dispatchers: OpiaDispatchers
        private set
    lateinit var database: OpiaDatabase
        private set
    lateinit var notificationRepo: NotificationRepo
        private set
    private lateinit var bgSync: MsgSync

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

    // authenticated through AuthInterceptor, lazy for lateinit db
    val keyRepo by lazy {
        KeyRepo(database.keyPairQueries, RetrofitClient.newKeyService(retrofitClient))
    }
    val msgRepo by lazy {
        MessagingRepo(database, RetrofitClient.newMessagingService(retrofitClient))
    }
    val actorRepo by lazy { ActorRepo(database, RetrofitClient.newActorService(retrofitClient)) }

    //fun isAuthenticated() = this::authCtx.isInitialized
    suspend fun isAuthenticated(): Boolean {
        mutex.withLock { return auth is AuthStatus.Authenticated }
    }

    suspend fun getAuth(): AuthStatus {
        mutex.withLock { return auth }
    }

    suspend fun init(
        dispatchers: OpiaDispatchers,
        database: OpiaDatabase,
        notificationRepo: NotificationRepo,
        notifier: Notifier
    ) {
        mutex.withLock {
            if (this::dispatchers.isInitialized) {
                println("[~] SL: duplicate init call detected")
                return
            }
            this.dispatchers = dispatchers
            this.database = database
            this.notificationRepo = notificationRepo
            mainScope = MainScope() // TODO replace by auth job on which to launch MsgSync (a supervisor)
            // wait for completion
            withContext(Dispatchers.Default) {
                initCrypto()
            }
            bgSync = MsgSync(notifier)
        }
    }

    // globally register the fact that app is now authenticated
    suspend fun login(authCtx: AuthCtx) {
        mutex.withLock {
            auth = AuthStatus.Authenticated(
                authCtx.actorId,
                authCtx.ioid,
                authCtx.secretUpdateId,
                authCtx.refreshToken,
                authCtx.accessToken,
                authCtx.sessCreatedAt
            )
            bgSync.registerActor(authCtx.actorId, authCtx.ioid)
        }
        //this.authCtx = AuthData(authCtx.ioid, authCtx.actorId)
        //this.msgChan = mainScope.msgActor(this.authCtx.job)
    }

    suspend fun refresh(sess: Auth_session): AuthStatus {
        mutex.withLock {
            auth = AuthStatus.Authenticated(
                sess.actor_id,
                sess.ioid,
                sess.secret_update_id,
                sess.refresh_token,
                sess.access_token,
                sess.created_at
            )
            return auth
        }
    }

    suspend fun logout() {
        mutex.withLock {
            println("[+] SL/logout - stopping bg sync...")
            bgSync.unregisterAll()

            println("[+] SL/logout - logging out repo...")
            authRepo.logout()
            auth = AuthStatus.Unauthenticated
        }
    }
}
