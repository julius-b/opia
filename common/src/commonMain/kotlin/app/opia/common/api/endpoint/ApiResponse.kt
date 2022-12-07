package app.opia.common.api

import com.squareup.moshi.JsonDataException
import okhttp3.Request
import okhttp3.ResponseBody
import okio.Timeout
import retrofit2.*
import java.io.IOException
import java.lang.reflect.ParameterizedType
import java.lang.reflect.Type

enum class Code {
    ok, created,
    schema, constraint, conflict, required, reference, unauthenticated, forbidden, expired, signature,
    internal
}

data class Status(
    val code: Code,
    val raw: String? = null,
    val parsed: String? = null,
    val constraints: Map<String, Any>? = null,
    val error: String? = null
)


// simplify code from `NetworkResponse<ApiResponse.ApiSuccess<Actor>>`
typealias PlainApiSuccess<T> = NetworkResponse<ApiResponse.ApiSuccess<T, Any>>

typealias HintedApiSuccess<T, S> = NetworkResponse<ApiResponse.ApiSuccess<T, S>>

typealias Errors = Map<String, Array<Status>>

fun Errors?.hasErr(key: String, code: Code) = this?.get(key)?.any { it.code == code } == true

sealed interface ApiResponse<out T : Any, out S : Any> {
    val code: String
    //val hints: S

    data class ApiSuccess<T : Any, S : Any>(
        override val code: String,
        val count: Int? = null,
        val data: T,
        val hints: S? // TODO nullable S?
    ) : ApiResponse<T, S>

    // TODO add support for generic errorBodyConverter with `hints: S`
    data class ApiError(
        override val code: String,
        val errors: Errors?,
        val hints: Map<String, Any>?
    ) : ApiResponse<Nothing, Nothing>
}

sealed class NetworkResponse<out T : ApiResponse<Any, Any>>(
    open val httpCode: Int? = null
) {
    data class ApiSuccess<T : ApiResponse.ApiSuccess<Any, Any>>(
        override val httpCode: Int, val body: T
    ) : NetworkResponse<T>()

    data class ApiError(
        override val httpCode: Int, val body: ApiResponse.ApiError
    ) : NetworkResponse<Nothing>()

    data class NetworkError(val error: IOException) : NetworkResponse<Nothing>()

    // api parsing or retrofit internal/incorrect usage
    data class UnknownError(
        val error: Throwable? = null, override val httpCode: Int? = null
    ) : NetworkResponse<Nothing>()
}

class NetworkResponseAdapter<S : ApiResponse.ApiSuccess<Any, Any>> constructor(
    private val successType: Type,
    private val errorBodyConverter: Converter<ResponseBody, ApiResponse.ApiError>
) : CallAdapter<S, Call<NetworkResponse<S>>> {
    override fun responseType(): Type = successType

    override fun adapt(call: Call<S>): Call<NetworkResponse<S>> =
        NetworkResponseCall(call, errorBodyConverter)

    internal class NetworkResponseCall<S : ApiResponse.ApiSuccess<Any, Any>>(
        private val delegate: Call<S>,
        private val errorConverter: Converter<ResponseBody, ApiResponse.ApiError>
    ) : Call<NetworkResponse<S>> {
        override fun clone(): Call<NetworkResponse<S>> =
            NetworkResponseCall(delegate.clone(), errorConverter)

        override fun execute(): Response<NetworkResponse<S>> =
            throw UnsupportedOperationException("NetworkResponseCall doesn't support execute")

        override fun enqueue(callback: Callback<NetworkResponse<S>>) {
            return delegate.enqueue(object : Callback<S> {
                override fun onResponse(call: Call<S>, response: Response<S>) {
                    val body = response.body()
                    val code = response.code()
                    val error = response.errorBody()

                    println("[*] NetworkResponseCall > onResponse > [$code] body: $body, error: $error")
                    if (response.isSuccessful) {
                        if (body != null) {
                            callback.onResponse(
                                this@NetworkResponseCall, Response.success(
                                    NetworkResponse.ApiSuccess(code, body)
                                )
                            )
                        } else {
                            // response is successful but the body is null
                            callback.onResponse(
                                this@NetworkResponseCall, Response.success(
                                    NetworkResponse.UnknownError(null, code)
                                )
                            )
                        }
                    } else {
                        val errorBody = when {
                            error == null -> null
                            error.contentLength() == 0L -> null
                            else -> try {
                                println("[*] NetworkResponseCall > onResponse > converting error...")
                                errorConverter.convert(error)
                            } catch (e: Exception) {
                                println("[!] NetworkResponseCall > onResponse > unexpected errorConverter ex: $e")
                                null
                            }
                        }
                        if (errorBody != null) {
                            callback.onResponse(
                                this@NetworkResponseCall, Response.success(
                                    NetworkResponse.ApiError(code, errorBody)
                                )
                            )
                        } else {
                            callback.onResponse(
                                this@NetworkResponseCall, Response.success(
                                    NetworkResponse.UnknownError(null, code)
                                )
                            )
                        }
                    }
                }

                override fun onFailure(call: Call<S>, t: Throwable) {
                    println("[!] NetworkResponseCall > onFailure > t: $t")
                    // IOException encompasses UnknownHostException, etc. - but not everything relevant (DNS lookup?)
                    val networkResponse = when (t) {
                        is JsonDataException -> NetworkResponse.UnknownError(t)
                        is IOException -> NetworkResponse.NetworkError(t)
                        else -> NetworkResponse.UnknownError(t)
                    }
                    callback.onResponse(this@NetworkResponseCall, Response.success(networkResponse))
                }
            })
        }

        override fun isExecuted(): Boolean = delegate.isExecuted

        override fun cancel(): Unit = delegate.cancel()

        override fun isCanceled(): Boolean = delegate.isCanceled

        override fun request(): Request = delegate.request()

        override fun timeout(): Timeout = delegate.timeout()
    }
}

// NOTE: exceptions here aren't handled correctly but they shouldn't occur
class NetworkResponseAdapterFactory : CallAdapter.Factory() {

    override fun get(
        returnType: Type, annotations: Array<Annotation>, retrofit: Retrofit
    ): CallAdapter<*, *>? {
        // suspend functions wrap the response type in `Call`
        if (Call::class.java != getRawType(returnType)) {
            return null
        }

        check(returnType is ParameterizedType) {
            "return type must be parameterized as Call<NetworkResponse<Foo>> or Call<NetworkResponse<out Foo>>"
        }

        // get the response type inside the `Call` type
        val responseType = getParameterUpperBound(0, returnType)
        // if the response type is not NetworkResponse then we can't handle this type, so we return null
        if (getRawType(responseType) != NetworkResponse::class.java) {
            return null
        }

        // the response type is NetworkResponse and should be parameterized
        check(responseType is ParameterizedType) { "Response must be parameterized as NetworkResponse<Foo> or NetworkResponse<out Foo>" }

        val successBodyType = getParameterUpperBound(0, responseType)

        // TODO const
        val errorBodyConverter = retrofit.nextResponseBodyConverter<ApiResponse.ApiError>(
            null, ApiResponse.ApiError::class.java, annotations
        )

        return NetworkResponseAdapter<ApiResponse.ApiSuccess<Any, Any>>(
            successBodyType, errorBodyConverter
        )
    }
}
