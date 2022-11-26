@file:OptIn(ExperimentalUnsignedTypes::class)

package app.opia.common.api

import app.opia.common.api.endpoint.*
import com.squareup.moshi.FromJson
import com.squareup.moshi.Moshi
import com.squareup.moshi.ToJson
import com.squareup.moshi.adapters.Rfc3339DateJsonAdapter
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.Response
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.util.Base64
import java.util.UUID
import java.util.concurrent.TimeUnit

object RetrofitClient {

    data class RunConfig(
        val host: String
    )

    enum class Mode(val config: RunConfig) {
        LocalDebug(RunConfig(host = "http://localhost:8080/api/v1/")),
        NetworkDebug(RunConfig(host = "http://192.168.43.55:8080/api/v1/")),
        StagingDebug(RunConfig(host = "https://staging.opia.app/api/v1/")),
        Prod(RunConfig(host = "https://opia.app/api/v1/"));
    }

    private val mode: Mode = Mode.NetworkDebug

    private val loggingInterceptor = HttpLoggingInterceptor().apply {
        setLevel(HttpLoggingInterceptor.Level.BODY)
    }

    private val okHttpClient: OkHttpClient =
        OkHttpClient().newBuilder().addInterceptor(RequestInterceptor)
            //.addInterceptor(AuthorizationInterceptor)
            .addInterceptor(loggingInterceptor)
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(20, TimeUnit.SECONDS).build()

    val moshi = Moshi.Builder()
        .add(KotlinJsonAdapterFactory()) // TODO remove reflection
        .add(object : Any() {
            @ToJson
            fun toJson(dt: ZonedDateTime?): String? {
                return dt?.format(DateTimeFormatter.ISO_DATE_TIME)
            }

            @FromJson
            fun fromJson(s: String?): ZonedDateTime? {
                return s?.let { ZonedDateTime.parse(it) }
            }
        })
        .add(object : Any() {
            @ToJson
            fun toJson(uuid: UUID?): String? {
                return uuid?.toString()
            }

            @FromJson
            fun fromJson(s: String?): UUID? {
                return s?.let { UUID.fromString(it) }
            }
        })
        .add(object : Any() {
            @ToJson
            fun toJson(byteArray: ByteArray): String {
                return byteArray.let { Base64.getEncoder().encodeToString(it) }
            }

            @FromJson
            fun fromJson(s: String): ByteArray {
                return s.let { Base64.getDecoder().decode(it) }
            }
        })
        .add(object : Any() {
            @ToJson
            fun toJson(ubyteArray: UByteArray): String {
                return ubyteArray.let { Base64.getEncoder().encodeToString(it.toByteArray()) }
            }

            @FromJson
            fun fromJson(s: String): UByteArray {
                return s.let { Base64.getDecoder().decode(it).toUByteArray() }
            }
        })
        //.add(Date::class.java, Rfc3339DateJsonAdapter())
        .build()

    private val retrofitClient by lazy {
        Retrofit.Builder().client(okHttpClient).baseUrl(mode.config.host)
            .addConverterFactory(MoshiConverterFactory.create(moshi))
            .addCallAdapterFactory(NetworkResponseAdapterFactory())
            .build()
    }

    val installationService: InstallationApi by lazy {
        retrofitClient.create(InstallationApi::class.java)
    }
    val actorService: ActorApi by lazy {
        retrofitClient.create(ActorApi::class.java)
    }
}

// TODO read from db
object AuthorizationInterceptor : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val requestWithHeader =
            chain.request().newBuilder().header("Authorization", UUID.randomUUID().toString()).build()
        return chain.proceed(requestWithHeader)
    }
}

object RequestInterceptor : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        println("[*] http > req: ${request.method} ${request.url}")
        return chain.proceed(request)
    }
}
