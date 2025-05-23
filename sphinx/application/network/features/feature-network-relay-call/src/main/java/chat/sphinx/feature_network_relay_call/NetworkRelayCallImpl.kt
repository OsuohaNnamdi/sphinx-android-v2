package chat.sphinx.feature_network_relay_call

import chat.sphinx.concept_network_call.buildRequest
import chat.sphinx.concept_network_client.NetworkClient
import chat.sphinx.concept_network_client.NetworkClientClearedListener
import chat.sphinx.concept_network_relay_call.NetworkRelayCall
import chat.sphinx.concept_relay.CustomException
import chat.sphinx.concept_relay.RelayDataHandler
import chat.sphinx.kotlin_response.LoadResponse
import chat.sphinx.kotlin_response.Response
import chat.sphinx.kotlin_response.ResponseError
import chat.sphinx.logger.SphinxLogger
import chat.sphinx.logger.e
import chat.sphinx.wrapper_relay.AuthorizationToken
import chat.sphinx.wrapper_relay.RelayUrl
import chat.sphinx.wrapper_relay.RequestSignature
import chat.sphinx.wrapper_relay.TransportToken
import com.squareup.moshi.Moshi
import com.squareup.moshi.Types
import io.matthewnelson.concept_coroutines.CoroutineDispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.internal.EMPTY_REQUEST
import java.io.IOException
import java.util.concurrent.TimeUnit

@Suppress("NOTHING_TO_INLINE")
private inline fun NetworkRelayCallImpl.mapRelayHeaders(
    relayData: Triple<Pair<AuthorizationToken, TransportToken?>, RequestSignature?, RelayUrl>,
    additionalHeaders: Map<String, String>?
): Map<String, String> {

    val map: MutableMap<String, String> = mutableMapOf(
        relayData.first.second?.let { transportToken ->
            Pair(TransportToken.TRANSPORT_TOKEN_HEADER, transportToken.value)
        } ?: Pair(AuthorizationToken.AUTHORIZATION_HEADER, relayData.first.first.value)
    )

    relayData.second?.let { signedRequest ->
        map.put(
            RequestSignature.REQUEST_SIGNATURE_HEADER,
            signedRequest.value
        )
    }

    additionalHeaders?.let {
        map.putAll(it)
    }

    return map
}

@Suppress("NOTHING_TO_INLINE")
private inline fun NetworkRelayCallImpl.handleException(
    LOG: SphinxLogger,
    callMethod: String,
    url: String,
    e: Exception
): Response.Error<ResponseError> {
    val msg = "$callMethod Request failure for: $url"
    LOG.e(NetworkRelayCallImpl.TAG, msg, e)
    return Response.Error(ResponseError(msg, e))
}

@Suppress("NOTHING_TO_INLINE")
@Throws(AssertionError::class, NullPointerException::class)
private suspend inline fun<RequestBody: Any> Moshi.requestBodyToJson(
    dispatchers: CoroutineDispatchers,
    requestBodyJsonAdapter: Class<RequestBody>,
    requestBody: RequestBody
): String =
    withContext(dispatchers.default) {
        adapter(requestBodyJsonAdapter).toJson(requestBody)
    } ?: throw NullPointerException(
        "Failed to convert RequestBody ${requestBodyJsonAdapter.simpleName} to Json"
    )

@Suppress("BlockingMethodInNonBlockingContext")
class NetworkRelayCallImpl(
    private val dispatchers: CoroutineDispatchers,
    private val moshi: Moshi,
    private val networkClient: NetworkClient,
    private val relayDataHandler: RelayDataHandler,
    private val LOG: SphinxLogger,
) : NetworkRelayCall(),
    NetworkClientClearedListener,
    CoroutineDispatchers by dispatchers
{

    companion object {
        const val TAG = "NetworkRelayCallImpl"

        private const val GET = "GET"
        private const val PUT = "PUT"
        private const val POST = "POST"
        private const val DELETE = "DELETE"
    }

    ///////////////////
    /// NetworkCall ///
    ///////////////////
    override fun <T: Any> get(
        url: String,
        responseJsonClass: Class<T>,
        headers: Map<String, String>?,
        useExtendedNetworkCallClient: Boolean
    ): Flow<LoadResponse<T, ResponseError>> = flow {

        emit(LoadResponse.Loading)

        var response: T?

        try {
            val requestBuilder = buildRequest(url, headers)

            response = call(responseJsonClass, requestBuilder.build(), useExtendedNetworkCallClient)

            emit(Response.Success(response))
        } catch (e: Exception) {
            emit(handleException(LOG, GET, url, e))
        }
    }

    override suspend fun getWithoutJson(
        url: String,
        headers: Map<String, String>?
    ): Flow<LoadResponse<String, ResponseError>> = flow {

        emit(LoadResponse.Loading)

        try {
            val networkResponse = withContext(io) {
                networkClient.getClient()
                    .newCall(buildRequest(url, headers).build())
                    .execute()
            }

            if(networkResponse.isSuccessful) {
                emit(Response.Success(""))
            } else {
                throw Exception(networkResponse.message)
            }
        } catch (e: Exception) {
            emit(handleException(LOG, GET, url, e))
        }
    }

    override fun <T: Any> getList(
        url: String,
        responseJsonClass: Class<T>,
        headers: Map<String, String>?,
        useExtendedNetworkCallClient: Boolean,
    ): Flow<LoadResponse<List<T>, ResponseError>> = flow {

        emit(LoadResponse.Loading)

        try {
            val requestBuilder = buildRequest(url, headers)

            val response = callList(responseJsonClass, requestBuilder.build(), useExtendedNetworkCallClient)

            emit(Response.Success(response))
        } catch (e: Exception) {
            emit(handleException(LOG, GET, url, e))
        }
    }

    override fun getRawJson(
        url: String,
        headers: Map<String, String>?,
        useExtendedNetworkCallClient: Boolean
    ): Flow<LoadResponse<String, ResponseError>> = flow {

        emit(LoadResponse.Loading)

        try {
            val requestBuilder = buildRequest(url, headers)

            val client = if (useExtendedNetworkCallClient) {
                extendedClientLock.withLock {
                    extendedNetworkCallClient ?: networkClient.getClient().newBuilder()
                        .connectTimeout(120, TimeUnit.SECONDS)
                        .readTimeout(45, TimeUnit.SECONDS)
                        .writeTimeout(45, TimeUnit.SECONDS)
                        .build()
                        .also { extendedNetworkCallClient = it }
                }
            } else {
                networkClient.getClient()
            }

            val networkResponse = withContext(io) {
                client.newCall(requestBuilder.build()).execute()
            }

            val body = networkResponse.body ?: throw NullPointerException(
                """
                NetworkResponse.body returned null
                NetworkResponse: $networkResponse
            """.trimIndent()
            )

            if (!networkResponse.isSuccessful) {
                val response = try {
                    moshi.adapter(NetworkRelayErrorResponseDto::class.java).fromJson(body.source())
                } catch (e: Exception) {
                    body.close()
                    throw IOException(networkResponse.toString())
                }

                body.close()
                throw IOException(response?.error ?: networkResponse.toString())
            }

            val rawJson = withContext(default) {
                body.string()
            }

            emit(Response.Success(rawJson))
        } catch (e: Exception) {
            emit(handleException(LOG, GET, url, e))
        }
    }

    override fun <T: Any, V: Any> put(
        url: String,
        responseJsonClass: Class<T>,
        requestBodyJsonClass: Class<V>?,
        requestBody: V?,
        mediaType: String?,
        headers: Map<String, String>?
    ): Flow<LoadResponse<T, ResponseError>> = flow {

        emit(LoadResponse.Loading)

        try {
            val requestBuilder = buildRequest(url, headers)

            val requestBodyJson: String? = if (requestBody == null || requestBodyJsonClass == null) {
                null
            } else {
                moshi.requestBodyToJson(dispatchers, requestBodyJsonClass, requestBody)
            }

            val reqBody = requestBodyJson?.toRequestBody(mediaType?.toMediaType())

            val response = call(
                responseJsonClass,
                requestBuilder.put(reqBody ?: EMPTY_REQUEST).build()
            )

            emit(Response.Success(response))
        } catch (e: Exception) {
            emit(handleException(LOG, PUT, url, e))
        }

    }

    override fun <T: Any, V: Any> post(
        url: String,
        responseJsonClass: Class<T>,
        requestBodyJsonClass: Class<V>,
        requestBody: V,
        mediaType: String?,
        headers: Map<String, String>?,
        accept400AsSuccess: Boolean
    ): Flow<LoadResponse<T, ResponseError>> = flow {

        emit(LoadResponse.Loading)

        try {
            val requestBuilder = buildRequest(url, headers)

            val requestBodyJson: String = moshi
                .requestBodyToJson(dispatchers, requestBodyJsonClass, requestBody)

            val reqBody = requestBodyJson.toRequestBody(mediaType?.toMediaType())

            val response = call(responseJsonClass, requestBuilder.post(reqBody).build(), accept400AsSuccess)

            emit(Response.Success(response))
        } catch (e: CustomException) {
            emit(Response.Error(ResponseError(e.message ?: "Unknown error", e, e.code?.toLong())))
        } catch (e: IOException) {
            emit(Response.Error(ResponseError("Network error: ${e.message}", e)))
        } catch (e: Exception) {
            emit(Response.Error(ResponseError("Unexpected error: ${e.message}", e)))
        }
    }

    override fun <T: Any, V: Any> postList(
        url: String,
        responseJsonClass: Class<T>,
        requestBodyJsonClass: Class<V>,
        requestBody: V,
        mediaType: String?,
        headers: Map<String, String>?
    ): Flow<LoadResponse<List<T>, ResponseError>> = flow {

        emit(LoadResponse.Loading)

        try {
            val requestBuilder = buildRequest(url, headers)

            val requestBodyJson: String = moshi
                .requestBodyToJson(dispatchers, requestBodyJsonClass, requestBody)

            val reqBody = requestBodyJson.toRequestBody(mediaType?.toMediaType())

            val response = callList(responseJsonClass, requestBuilder.post(reqBody).build())

            emit(Response.Success(response))
        } catch (e: Exception) {
            emit(handleException(LOG, POST, url, e))
        }
    }

    override fun <T: Any, V: Any> delete(
        url: String,
        responseJsonClass: Class<T>,
        requestBodyJsonClass: Class<V>?,
        requestBody: V?,
        mediaType: String?,
        headers: Map<String, String>?
    ): Flow<LoadResponse<T, ResponseError>> = flow {

        emit(LoadResponse.Loading)

        try {
            val requestBuilder = buildRequest(url, headers)

            val requestBodyJson: String? =
                if (requestBody == null || requestBodyJsonClass == null) {
                    null
                } else {
                    moshi.requestBodyToJson(dispatchers, requestBodyJsonClass, requestBody)
                }

            val reqBody: RequestBody? = requestBodyJson?.toRequestBody(mediaType?.toMediaType())

            val response = call(
                responseJsonClass,
                requestBuilder.delete(reqBody ?: EMPTY_REQUEST).build()
            )

            emit(Response.Success(response))
        } catch (e: Exception) {
            emit(handleException(LOG, DELETE, url, e))
        }

    }

    @Volatile
    private var extendedNetworkCallClient: OkHttpClient? = null
    private val extendedClientLock = Mutex()

    override fun networkClientCleared() {
        extendedNetworkCallClient = null
    }

    init {
        networkClient.addListener(this)
    }

    @Throws(NullPointerException::class, CustomException::class)
    override suspend fun <T: Any> call(
        responseJsonClass: Class<T>,
        request: Request,
        useExtendedNetworkCallClient: Boolean,
        accept400AsSuccess: Boolean
    ): T {

        val client = if (useExtendedNetworkCallClient) {
            extendedClientLock.withLock {
                extendedNetworkCallClient ?: networkClient.getClient().newBuilder()
                    .connectTimeout(120, TimeUnit.SECONDS)
                    .readTimeout(45, TimeUnit.SECONDS)
                    .writeTimeout(45, TimeUnit.SECONDS)
                    .build()
                    .also { extendedNetworkCallClient = it }
            }
        } else {
            networkClient.getClient()
        }

        val networkResponse = withContext(io) {
            client.newCall(request).execute()
        }

        val body = networkResponse.body ?: throw NullPointerException(
            "NetworkResponse.body returned null\nNetworkResponse: $networkResponse"
        )

        val acceptableErrorCodes = if (accept400AsSuccess) listOf(400) else emptyList()

        return withContext(default) {
            try {
                moshi.adapter(responseJsonClass).fromJson(body.source())
            } catch (e: Exception) {
                throw CustomException(
                    "Failed to convert Json to ${responseJsonClass.simpleName}\nNetworkResponse: $networkResponse",
                    networkResponse.code
                )
            } finally {
                body.close()
            }
        } ?: throw CustomException(
            "Failed to convert Json to ${responseJsonClass.simpleName}\nNetworkResponse: $networkResponse",
            networkResponse.code
        ).takeUnless { networkResponse.code in acceptableErrorCodes } ?: throw IOException("Unacceptable error: ${networkResponse}")
    }


    @Throws(NullPointerException::class, IOException::class)
    override suspend fun <T: Any> callList(
        responseJsonClass: Class<T>,
        request: Request,
        useExtendedNetworkCallClient: Boolean
    ): List<T> {
        // TODO: Make less horrible. Needed for the `/contacts` endpoint for users who
        //  have a large number of contacts as Relay needs more time than the default
        //  client's settings. Replace once the `aa/contacts` endpoint gets pagination.
        val client = if (useExtendedNetworkCallClient) {
            extendedClientLock.withLock {
                extendedNetworkCallClient ?: networkClient.getClient().newBuilder()
                    .connectTimeout(120, TimeUnit.SECONDS)
                    .readTimeout(45, TimeUnit.SECONDS)
                    .writeTimeout(45, TimeUnit.SECONDS)
                    .build()
                    .also { extendedNetworkCallClient = it }
            }
        } else {
            networkClient.getClient()
        }

        val networkResponse = withContext(io) {
            client.newCall(request).execute()
        }

        if (!networkResponse.isSuccessful) {
            networkResponse.body?.close()
            throw IOException(networkResponse.toString())
        }

        val body = networkResponse.body ?: throw NullPointerException(
            """
                NetworkResponse.body returned null
                NetworkResponse: $networkResponse
            """.trimIndent()
        )

        val listMyData = Types.newParameterizedType(List::class.java, responseJsonClass)

        return withContext(default) {
            try{
                moshi.adapter<List<T>>(listMyData).fromJson(body.source())
            } catch (e: Exception) {
                throw CustomException(
                    """
                Failed to convert Json to ${responseJsonClass.simpleName}
                NetworkResponse: $networkResponse
            """.trimIndent(),
                    networkResponse.code
                )
            }
        } ?: throw IOException(
            """
                Failed to convert Json to ${responseJsonClass.simpleName}
                NetworkResponse: $networkResponse
            """.trimIndent()
        )
    }
}
