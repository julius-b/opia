package app.opia.common.api.endpoint

import app.opia.common.api.NetworkResponse
import app.opia.common.api.RetrofitClient
import app.opia.common.api.model.Authorization
import app.opia.common.api.model.RefreshToken
import app.opia.common.di.AuthStatus
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
        // TODO do via SL.auth -> get auth data from there, update it if necessary - also refresh
        var authSession = ServiceLocator.getAuth()
        println("[*] AuthInterceptor > current: ${(authSession as? AuthStatus.Authenticated)?.sessCreatedAt}")
        if (authSession is AuthStatus.Authenticated) {
            val jwt = authSession.accessToken.split('.')
            val jwtBody = Base64.getDecoder().decode(jwt[1])
            val jwtBodyS = String(jwtBody, Charsets.UTF_8)
            //val moshi = Moshi.Builder().build()
            val accessToken = RetrofitClient.accessTokenAdapter.fromJson(jwtBodyS)!!
            val exp = Instant.ofEpochMilli(accessToken.exp * 1000)
            val now = Instant.now()
            println("[*] AuthInterceptor > exp: $exp (now: $now)")
            // has to work for exp far in future and exp far in past
            // exp - 5 < now: refresh TODO reset to 5
            if (exp.minus(1, ChronoUnit.MINUTES).isBefore(now)) {
                println("[~] AuthInterceptor > refreshing...")
                val res =
                    ServiceLocator.authRepo.api.refreshAuthSession("Bearer ${authSession.refreshToken}")
                when (res) {
                    is NetworkResponse.ApiSuccess -> {
                        val sess = res.body.data
                        // id, created_at, etc. stay the same
                        ServiceLocator.database.sessionQueries.insert(sess)
                        authSession = ServiceLocator.refresh(sess)
                    }

                    is NetworkResponse.ApiError -> {
                        println("[-] AuthIntercept > deleting auth sessions: $res")
                        ServiceLocator.database.sessionQueries.deleteAll(ZonedDateTime.now())
                        authSession = AuthStatus.Unauthenticated
                    }

                    else -> {
                        println("[-] AuthIntercept > unknown response: $res")
                        authSession = AuthStatus.Unauthenticated
                    }
                }
            }
            if (authSession is AuthStatus.Authenticated) req.header(
                Authorization, "Bearer ${authSession.accessToken}"
            )
        }

        // test doing network stuff here on android (which thread are we on?)

        val res = chain.proceed(req.build())
        if (res.code == 401) {
            // eg. {"code":"unauthenticated","errors":{"token":[{"code":"signature"}]}}
            println("[!] AuthIntercept > logout > expected token to be valid but got 'unauthenticated'...")
            // TODO just call .logout, it does mutex & check if auth'd
            if (ServiceLocator.isAuthenticated()) ServiceLocator.logout()
        }

        res
    }
}
