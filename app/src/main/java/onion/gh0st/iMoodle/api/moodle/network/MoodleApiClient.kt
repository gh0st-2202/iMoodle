package onion.gh0st.iMoodle.api.moodle.network

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import okhttp3.FormBody
import okhttp3.OkHttpClient
import okhttp3.Request
import onion.gh0st.iMoodle.api.moodle.auth.AuthException
import onion.gh0st.iMoodle.core.await
import onion.gh0st.iMoodle.api.moodle.config.MoodleConfig
import java.net.URLEncoder

open class MoodleApiClient(
    private val client: OkHttpClient,
    val config: MoodleConfig,
    private val tokenRefresher: TokenRefresher,
    private val onTokenRefreshed: suspend (String) -> Unit = {},
    private val onInvalidToken: suspend () -> Unit = {},
    initialToken: String,
) {
    private val _token = MutableStateFlow(initialToken)
    val token: StateFlow<String> = _token.asStateFlow()
    private val refreshMutex = Mutex()
    private var invalidated = false

    open suspend fun callREST(
        function: String,
        params: MoodleParams = MoodleParams(),
    ): String = callRESTInternal(function, params.build(), allowRetry = true)

    /**
     * Ejecuta una función REST enviando los parámetros en el cuerpo como `application/x-www-form-urlencoded`.
     * Útil para parámetros con corchetes (`plugindata[...]`) que no deben ser codificados en la URL.
     */
    open suspend fun callRESTWithFormBody(
        function: String,
        params: Map<String, String>,
    ): String = callRESTWithFormBodyInternal(function, params, allowRetry = true)

    private suspend fun callRESTInternal(
        function: String,
        params: Map<String, String>,
        allowRetry: Boolean,
    ): String = withContext(Dispatchers.IO) {
        val usedToken = _token.value
        val responseBody = performRequest(function, params, usedToken)
        if (!responseBody.hasInvalidTokenError()) {
            responseBody.throwIfMoodleException()
            return@withContext responseBody
        }
        if (!allowRetry) return@withContext failInvalid()
        retryWithFreshToken(function, params, usedToken)
    }

    private suspend fun callRESTWithFormBodyInternal(
        function: String,
        params: Map<String, String>,
        allowRetry: Boolean,
    ): String = withContext(Dispatchers.IO) {
        val usedToken = _token.value
        val responseBody = performFormBodyRequest(function, params, usedToken)
        if (!responseBody.hasInvalidTokenError()) {
            responseBody.throwIfMoodleException()
            return@withContext responseBody
        }
        if (!allowRetry) return@withContext failInvalid()
        retryWithFreshTokenWithFormBody(function, params, usedToken)
    }

    private suspend fun performRequest(
        function: String,
        params: Map<String, String>,
        token: String,
    ): String = try {
        client.newCall(
            Request.Builder()
                .url(buildUrl(function, params, token))
                .header("User-Agent", config.userAgent)
                .build(),
        ).await().use { httpResponse ->
            val responseBody = httpResponse.body?.string().orEmpty()
            if (!httpResponse.isSuccessful) {
                throw MoodleApiException("http_${httpResponse.code}", "Error HTTP ${httpResponse.code}")
            }
            responseBody
        }
    } catch (exception: CancellationException) {
        throw exception
    } catch (exception: AuthException) {
        throw exception
    } catch (exception: MoodleApiException) {
        throw exception
    } catch (exception: Exception) {
        throw AuthException.Network(exception)
    }

    private suspend fun performFormBodyRequest(
        function: String,
        params: Map<String, String>,
        token: String,
    ): String = try {
        val formBody = FormBody.Builder().apply {
            add("wstoken", token)
            add("wsfunction", function)
            add("moodlewsrestformat", "json")
            params.forEach { (key, value) -> add(key, value) }
        }.build()

        client.newCall(
            Request.Builder()
                .url("${config.moodleBase}/webservice/rest/server.php")
                .header("User-Agent", config.userAgent)
                .post(formBody)
                .build(),
        ).await().use { httpResponse ->
            val responseBody = httpResponse.body?.string().orEmpty()
            if (!httpResponse.isSuccessful) {
                throw MoodleApiException("http_${httpResponse.code}", "Error HTTP ${httpResponse.code}")
            }
            responseBody
        }
    } catch (exception: CancellationException) {
        throw exception
    } catch (exception: AuthException) {
        throw exception
    } catch (exception: MoodleApiException) {
        throw exception
    } catch (exception: Exception) {
        throw AuthException.Network(exception)
    }

    private suspend fun retryWithFreshToken(
        function: String,
        params: Map<String, String>,
        usedToken: String,
    ): String {
        val refreshedSuccessfully: Boolean = refreshMutex.withLock {
            when {
                invalidated -> false
                _token.value != usedToken -> true
                else -> {
                    val freshToken = tokenRefresher.refresh()
                    if (freshToken == null) false
                    else {
                        _token.value = freshToken
                        onTokenRefreshed(freshToken)
                        true
                    }
                }
            }
        }
        if (!refreshedSuccessfully) failInvalid()
        return callRESTInternal(function, params, allowRetry = false)
    }

    private suspend fun retryWithFreshTokenWithFormBody(
        function: String,
        params: Map<String, String>,
        usedToken: String,
    ): String {
        val refreshedSuccessfully: Boolean = refreshMutex.withLock {
            when {
                invalidated -> false
                _token.value != usedToken -> true
                else -> {
                    val freshToken = tokenRefresher.refresh()
                    if (freshToken == null) false
                    else {
                        _token.value = freshToken
                        onTokenRefreshed(freshToken)
                        true
                    }
                }
            }
        }
        if (!refreshedSuccessfully) failInvalid()
        return callRESTWithFormBodyInternal(function, params, allowRetry = false)
    }

    private suspend fun failInvalid(): Nothing {
        refreshMutex.withLock {
            if (!invalidated) {
                invalidated = true
                onInvalidToken()
            }
        }
        throw AuthException.InvalidToken()
    }

    private fun buildUrl(
        function: String,
        params: Map<String, String>,
        token: String,
    ): String = buildString {
        append("${config.moodleBase}/webservice/rest/server.php")
        append("?wstoken=").append(URLEncoder.encode(token, "UTF-8"))
        append("&wsfunction=").append(URLEncoder.encode(function, "UTF-8"))
        append("&moodlewsrestformat=json")
        params.forEach { (parameterName, parameterValue) ->
            // Moodle no decodifica correctamente los corchetes codificados en la URL.
            // Se envían literales para parámetros anidados como `plugindata[...]`.
            val encodedName = parameterName.replace("[", "%5B").replace("]", "%5D")
            append("&").append(encodedName)
                .append("=").append(URLEncoder.encode(parameterValue, "UTF-8"))
        }
    }
}