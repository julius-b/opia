@file:OptIn(ExperimentalUnsignedTypes::class)

package app.opia.common.api

import app.opia.common.api.endpoint.ActorApi
import app.opia.common.api.endpoint.AuthInterceptor
import app.opia.common.api.endpoint.InstallationApi
import app.opia.common.api.endpoint.KeyApi
import app.opia.common.api.endpoint.MessagingApi
import app.opia.common.api.model.AccessToken
import com.squareup.moshi.FromJson
import com.squareup.moshi.JsonAdapter
import com.squareup.moshi.Moshi
import com.squareup.moshi.ToJson
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
        LocalDebug(RunConfig(host = "http://localhost:8080/")),
        NetworkDebug(RunConfig(host = "http://192.168.1.25:8080/")),
        StagingDebug(RunConfig(host = "https://staging.opia.app/")),
        Prod(RunConfig(host = "https://opia.app/"));
    }

    val mode: Mode = Mode.StagingDebug

    private val loggingInterceptor = HttpLoggingInterceptor().apply {
        setLevel(HttpLoggingInterceptor.Level.BODY)
    }

    fun newOkHttpClient() =
        OkHttpClient().newBuilder().addInterceptor(RequestInterceptor)
            .addInterceptor(AuthInterceptor())
            .addInterceptor(loggingInterceptor)
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(20, TimeUnit.SECONDS).build()

    val moshi = Moshi.Builder()
        .add(KotlinJsonAdapterFactory()) // TODO remove reflection
        .add(ZonedDateTimeAdapter).add(UUIDAdapter).add(ByteArrayAdapter).add(UByteArrayAdapter)
        //.add(Date::class.java, Rfc3339DateJsonAdapter())
        .build()

    val accessTokenAdapter: JsonAdapter<AccessToken> = moshi.adapter(AccessToken::class.java)

    fun newRetrofitClient(okHttpClient: OkHttpClient): Retrofit =
        Retrofit.Builder().client(okHttpClient).baseUrl(mode.config.host + "api/v1/")
            .addConverterFactory(MoshiConverterFactory.create(moshi))
            .addCallAdapterFactory(NetworkResponseAdapterFactory()).build()

    fun newInstallationService(retrofit: Retrofit) = retrofit.create(InstallationApi::class.java)

    fun newActorService(retrofit: Retrofit) = retrofit.create(ActorApi::class.java)

    fun newKeyService(retrofit: Retrofit) = retrofit.create(KeyApi::class.java)

    fun newMessagingService(retrofit: Retrofit) = retrofit.create(MessagingApi::class.java)
}

object RequestInterceptor : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        println("[*] http > req: ${request.method} ${request.url}")
        return chain.proceed(request)
    }
}


object UUIDAdapter {
    @ToJson
    fun toJson(uuid: UUID?): String? {
        return uuid?.toString()
    }

    @FromJson
    fun fromJson(s: String?): UUID? {
        return s?.let { UUID.fromString(it) }
    }
}

object ByteArrayAdapter {
    @ToJson
    fun toJson(byteArray: ByteArray): String {
        return byteArray.let { Base64.getEncoder().encodeToString(it) }
    }

    @FromJson
    fun fromJson(s: String): ByteArray {
        return s.let { Base64.getDecoder().decode(it) }
    }
}

object UByteArrayAdapter {
    @ToJson
    fun toJson(ubyteArray: UByteArray): String {
        return ubyteArray.let { Base64.getEncoder().encodeToString(it.toByteArray()) }
    }

    @FromJson
    fun fromJson(s: String): UByteArray {
        return s.let { Base64.getDecoder().decode(it).toUByteArray() }
    }
}

object ZonedDateTimeAdapter {
    @ToJson
    fun toJson(dt: ZonedDateTime?): String? {
        return dt?.format(DateTimeFormatter.ISO_OFFSET_DATE_TIME)
    }

    @FromJson
    fun fromJson(s: String?): ZonedDateTime? {
        return s?.let { ZonedDateTime.parse(it) }
    }
}
