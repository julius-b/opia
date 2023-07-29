package app.opia.common.api.endpoint

import app.opia.common.api.NetworkResponse
import app.opia.common.api.RetrofitClient
import app.opia.common.api.model.Authorization
import app.opia.common.api.model.RefreshToken
import app.opia.common.di.ServiceLocator
import kotlinx.coroutines.runBlocking
import okhttp3.Interceptor
import java.time.Instant
import java.time.ZonedDateTime
import java.time.temporal.ChronoUnit
import java.util.Base64

internal class AuthInterceptor : Interceptor {
    // CoroutineScope(Dispatchers.IO).launch - should already be on a bg thread
    override fun intercept(chain: Interceptor.Chain) = runBlocking {
        val r = chain.request()
        if (r.header(RefreshToken) != null || r.header(Authorization) != null)
            return@runBlocking chain.proceed(r)

        val req = r.newBuilder()

        // no request when no authSession exists -> don't hinder login/registration process
        var authSession =
            if (!ServiceLocator.isAuthenticated()) null else ServiceLocator.database.sessionQueries.getLatest()
                .executeAsOneOrNull()
        println("[*] AuthInterceptor > current: ${authSession?.created_at}")
        if (authSession != null) {
            val jwt = authSession.access_token.split('.')
            val jwtBody = Base64.getDecoder().decode(jwt[1])
            val jwtBodyS = String(jwtBody, Charsets.UTF_8)
            //val moshi = Moshi.Builder().build()
            val accessToken = RetrofitClient.accessTokenAdapter.fromJson(jwtBodyS)!!
            val exp = Instant.ofEpochMilli(accessToken.exp * 1000)
            val now = Instant.now()
            println("[*] AuthInterceptor > exp: $exp / $now")
            // has to work for exp far in future and exp far in past
            // exp - 5 < now: refresh TODO reset to 5
            if (exp.minus(1, ChronoUnit.MINUTES).isBefore(now)) {
                println("[~] AuthInterceptor > refreshing...")
                val res =
                    ServiceLocator.authRepo.api.refreshAuthSession("Bearer ${authSession.refresh_token}")
                when (res) {
                    is NetworkResponse.ApiSuccess -> {
                        authSession = res.body.data
                        // id, created_at, etc. stay the same
                        ServiceLocator.database.sessionQueries.insert(authSession)
                    }

                    is NetworkResponse.ApiError -> {
                        println("[-] AuthIntercept > deleting auth sessions: $res")
                        ServiceLocator.database.sessionQueries.deleteAll(ZonedDateTime.now())
                        authSession = null
                    }

                    else -> {
                        println("[-] AuthIntercept > unknown response: $res")
                        authSession = null
                    }
                }
            }
            if (authSession != null) req.header(Authorization, "Bearer ${authSession.access_token}")
        }

        // test doing network stuff here on android (which thread are we on?)

        val res = chain.proceed(req.build())
        if (res.code == 401) {
            // eg. {"code":"unauthenticated","errors":{"token":[{"code":"signature"}]}}
            println("[!] AuthIntercept > logout > expected token to be valid but got 'unauthenticated'...")
            if (ServiceLocator.isAuthenticated()) ServiceLocator.authCtx.logout()
        }

        res
    }
}
