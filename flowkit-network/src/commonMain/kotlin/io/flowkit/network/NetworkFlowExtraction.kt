@file:OptIn(kotlinx.coroutines.FlowPreview::class)

package io.flowkit.network

import io.flowkit.core.Result
import io.flowkit.core.UiState
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onCompletion
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.retryWhen
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.takeWhile
import kotlinx.coroutines.flow.timeout
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

/**
 * Convert NetworkResult to UiState for easy UI consumption
 */
fun <T> Flow<NetworkResponse<T>>.asUiState(): Flow<UiState<T>> = map { result ->
    result.toUiState()
}

/**
 * Handle network-specific errors with custom logic
 */
fun <T> Flow<NetworkResponse<T>>.onNetworkError(
    action: suspend (NetworkError) -> Unit
): Flow<NetworkResponse<T>> = onEach { result ->
    result.onFailure { error -> action(error) }
}

/**
 * Handle successful network responses
 */
fun <T> Flow<NetworkResponse<T>>.onNetworkSuccess(
    action: suspend (T) -> Unit
): Flow<NetworkResponse<T>> = onEach { result ->
    result.onSuccess { data -> action(data) }
}

/**
 * Handle loading state
 */
fun <T> Flow<NetworkResponse<T>>.onNetworkLoading(
    action: suspend () -> Unit
): Flow<NetworkResponse<T>> = onEach { result ->
    result.onLoading { action() }
}

/**
 * Transform successful network data
 */
fun <T, R> Flow<NetworkResponse<T>>.mapNetworkData(
    transform: suspend (T) -> R
): Flow<NetworkResponse<R>> = map { result ->
    result.map { transform(it) }
}

/**
 * Filter network results based on success data
 */
fun <T> Flow<NetworkResult<T>>.filterNetworkSuccess(
    predicate: suspend (T) -> Boolean
): Flow<NetworkResult<T>> = filter { result ->
    when (result) {
        is Result.Success -> predicate(result.data)
        else -> true // Pass through Loading and Error states
    }
}

/**
 * Combine multiple network flows with loading state management
 */
fun <T1, T2, R> combineNetworkFlows(
    flow1: Flow<NetworkResult<T1>>,
    flow2: Flow<NetworkResult<T2>>,
    transform: suspend (T1, T2) -> R
): Flow<NetworkResult<R>> = flow1.combine(flow2) { result1, result2 ->
    when {
        result1 is Result.Loading || result2 is Result.Loading -> NetworkResultFactory.loading()
        result1 is Result.Error -> NetworkResultFactory.failure(result1.error)
        result2 is Result.Error -> NetworkResultFactory.failure(result2.error)
        result1 is Result.Success && result2 is Result.Success -> {
            NetworkResultFactory.success(transform(result1.data, result2.data))
        }

        else -> NetworkResultFactory.loading()
    }
}

/**
 * Retry network requests with exponential backoff for specific errors
 */
fun <T> Flow<NetworkResponse<T>>.retryNetworkRequest(
    maxAttempts: Int = 3,
    initialDelay: Duration = 1.seconds,
    shouldRetry: (NetworkError) -> Boolean = { true }
): Flow<NetworkResponse<T>> = retryWhen { cause, attempt ->
    if (attempt < maxAttempts && cause is NetworkError && shouldRetry(cause)) {
        delay(initialDelay * (attempt + 1).toInt())
        true
    } else {
        false
    }
}

/**
 * Add timeout to network requests
 */
fun <T> Flow<NetworkResponse<T>>.timeoutNetwork(
    duration: Duration
): Flow<NetworkResponse<T>> = timeout(duration).catch { exception ->
    emit(NetworkResultFactory.failure(NetworkError.TimeoutError("Request timed out after $duration")))
}

/**
 * Debounce network requests (useful for search)
 */
fun <T> Flow<NetworkResponse<T>>.debounceNetwork(
    duration: Duration
): Flow<NetworkResponse<T>> = debounce(duration)

/**
 * Cache network responses with stale-while-revalidate strategy
 */
fun <T> Flow<NetworkResponse<T>>.cacheStaleWhileRevalidate(
    cache: NetworkCache,
    key: String,
    ttl: Duration = 5.seconds
): Flow<NetworkResponse<T>> = flow {
// Emit cached data immediately if available
    val cachedData = cache.get<T>(key)
    if (cachedData != null && cache.isValid(key)) {
        emit(NetworkResultFactory.success(cachedData))
    }

// Fetch fresh data in background
    collect { result ->
        result.onSuccess { data ->
            cache.put(key, data, ttl)
        }
        emit(result)
    }
}

/**
 * Handle offline scenarios with cached fallback
 */
fun <T> Flow<NetworkResponse<T>>.offlineFallback(
    cache: NetworkCache,
    key: String,
    connectivityMonitor: NetworkConnectivityMonitor
): Flow<NetworkResponse<T>> = flow {
    if (connectivityMonitor.isCurrentlyConnected()) {
// Online: collect network results and cache success
        collect { result ->
            result.onSuccess { data ->
                cache.put(key, data)
            }
            emit(result)
        }
    } else {
// Offline: try to emit cached data
        val cachedData = cache.get<T>(key)
        if (cachedData != null) {
            emit(NetworkResultFactory.success(cachedData))
        } else {
            emit(NetworkResultFactory.failure(NetworkError.NoInternetConnection))
        }
    }
}

/**
 * Add authentication to network requests
 */
fun <T> Flow<NetworkResult<T>>.withAuthentication(
    authProvider: AuthTokenProvider,
    onUnauthorized: suspend () -> Unit = {}
): Flow<NetworkResult<T>> =
    map { result ->
        when (result) {
            is Result.Error -> {
                val error = result.errorOrNull
                if (error is NetworkError.HttpError && error.code == 401) {
                    onUnauthorized()
                }
                result
            }

            else -> result
        }
    }

/**
 * Log network operations for debugging
 */
fun <T> Flow<NetworkResponse<T>>.logNetwork(
    tag: String = "NetworkFlow"
): Flow<NetworkResponse<T>> = onEach { result ->
    result.fold(
        onFailure = { error -> println("❌ [$tag] Error: ${error.message}") },
        onSuccess = { data -> println("✅ [$tag] Success: $data") },
        onLoading = { println("🔄 [$tag] Loading...") }
    )
}

/**
 * Convert Flow<T> to Flow<NetworkResult<T>> with error handling
 */
fun <T> Flow<T>.asNetworkResult(): Flow<NetworkResponse<T>> = flow {
    emit(NetworkResultFactory.loading<T>())
    try {
        collect { data ->
            emit(NetworkResultFactory.success(data))
        }
    } catch (exception: Throwable) {
        val networkError = when (exception) {
            is NetworkError -> exception
            else -> NetworkError.UnknownError(
                message = exception.message ?: "Unknown network error",
                cause = exception
            )
        }
        emit(NetworkResultFactory.failure<T>(networkError))
    }
}

/**
 * Flatten nested NetworkResult flows
 */
@OptIn(ExperimentalCoroutinesApi::class)
fun <T> Flow<NetworkResponse<Flow<NetworkResponse<T>>>>.flattenNetworkResult(): Flow<NetworkResponse<T>> =
    flatMapLatest { outerResult ->
        outerResult.fold(
            onFailure = { error -> flowOf(NetworkResultFactory.failure(error)) },
            onSuccess = { innerFlow -> innerFlow },
            onLoading = { flowOf(NetworkResultFactory.loading()) }
        )
    }

/**
 * Merge multiple network flows with loading state coordination
 */
fun <T> List<Flow<NetworkResponse<T>>>.mergeNetworkFlows(): Flow<List<NetworkResponse<T>>> =
    combine(this) { results -> results.toList() }

/**
 * Take only the first successful network result
 */
fun <T> Flow<NetworkResponse<T>>.takeFirstSuccess(): Flow<NetworkResponse<T>> =
    this.takeWhile { result -> !result.isSuccess }
        .onCompletion {
            emitAll(this@takeFirstSuccess.filter { it.isSuccess }.take(1))
        }

/**
 * Emit periodic network requests (useful for polling)
 */
fun <T> networkPolling(
    interval: Duration,
    request: suspend () -> T
): Flow<NetworkResponse<T>> = flow {
    while (true) {
        emit(NetworkResultFactory.loading<T>())
        try {
            val result = request()
            emit(NetworkResultFactory.success(result))
        } catch (exception: Throwable) {
            val networkError = when (exception) {
                is NetworkError -> exception
                else -> NetworkError.UnknownError(exception.message ?: "Polling error", exception)
            }
            emit(NetworkResultFactory.failure<T>(networkError))
        }
        delay(interval)
    }
}

/**
 * Recover from network errors with fallback values
 */
fun <T> Flow<NetworkResponse<T>>.recover(
    fallback: suspend (NetworkError) -> T
): Flow<NetworkResponse<T>> = map { result ->
    result.fold(
        onFailure = { error ->
            try {
                NetworkResultFactory.success(fallback(error))
            } catch (e: Exception) {
                NetworkResultFactory.failure(NetworkError.UnknownError("Fallback failed", e))
            }
        },
        onSuccess = { data -> NetworkResultFactory.success(data) },
        onLoading = { NetworkResultFactory.loading() }
    )
}

/**
 * Chain network operations (flatMap for NetworkResult)
 */
fun <T, R> Flow<NetworkResponse<T>>.chain(
    transform: suspend (T) -> Flow<NetworkResponse<R>>
): Flow<NetworkResponse<R>> = flatMapLatest { result ->
    result.fold(
        onFailure = { error -> flowOf(NetworkResultFactory.failure(error)) },
        onSuccess = { data -> transform(data) },
        onLoading = { flowOf(NetworkResultFactory.loading()) }
    )
}