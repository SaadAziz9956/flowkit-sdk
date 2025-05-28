package io.flowkit.network

import io.flowkit.core.UiState
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

/**
 * DSL builder for creating network flows with all the bells and whistles
 */
class NetworkFlowBuilder<T> {
    private var sourceRequest: (suspend () -> T)? = null
    private var cacheConfig: CacheConfig? = null
    private var retryConfig: RetryConfig? = null
    private var timeoutDuration: Duration? = null
    private var debounceTime: Duration? = null
    private var authRequired: Boolean = false
    private var offlineSupport: Boolean = false
    private var loggingEnabled: Boolean = false
    private var logTag: String = "NetworkFlow"

    // Dependencies (will be injected)
    private var cache: NetworkCache? = null
    private var connectivityMonitor: NetworkConnectivityMonitor? = null
    private var authProvider: AuthTokenProvider? = null

    /**
     * Set the network request source
     */
    fun source(request: suspend () -> T): NetworkFlowBuilder<T> {
        sourceRequest = request
        return this
    }

    /**
     * Enable caching with strategy
     */
    fun cache(
        key: String,
        strategy: CacheStrategy = CacheStrategy.CACHE_FIRST,
        ttl: Duration = 5.minutes
    ): NetworkFlowBuilder<T> {
        cacheConfig = CacheConfig(key, strategy, ttl)
        return this
    }

    /**
     * Configure retry behavior
     */
    fun retryOnFailure(
        maxAttempts: Int = 3,
        initialDelay: Duration = 1.seconds,
        shouldRetry: (NetworkError) -> Boolean = { true }
    ): NetworkFlowBuilder<T> {
        retryConfig = RetryConfig(maxAttempts, initialDelay, shouldRetry)
        return this
    }

    /**
     * Set request timeout
     */
    fun timeout(duration: Duration): NetworkFlowBuilder<T> {
        timeoutDuration = duration
        return this
    }

    /**
     * Add debouncing (useful for search)
     */
    fun debounce(duration: Duration): NetworkFlowBuilder<T> {
        debounceTime = duration
        return this
    }

    /**
     * Require authentication for this request
     */
    fun requireAuth(): NetworkFlowBuilder<T> {
        authRequired = true
        return this
    }

    /**
     * Enable offline support with caching fallback
     */
    fun offlineFirst(): NetworkFlowBuilder<T> {
        offlineSupport = true
        return this
    }

    /**
     * Enable debug logging
     */
    fun enableLogging(tag: String = "NetworkFlow"): NetworkFlowBuilder<T> {
        loggingEnabled = true
        logTag = tag
        return this
    }

    /**
     * Inject dependencies (usually done by DI framework)
     */
    fun withDependencies(
        cache: NetworkCache? = null,
        connectivityMonitor: NetworkConnectivityMonitor? = null,
        authProvider: AuthTokenProvider? = null
    ): NetworkFlowBuilder<T> {
        this.cache = cache
        this.connectivityMonitor = connectivityMonitor
        this.authProvider = authProvider
        return this
    }

    /**
     * Build the network flow with all configurations applied
     */
    fun build(): Flow<NetworkResponse<T>> {
        val request = sourceRequest ?: throw IllegalStateException("Source request must be set")

        // Start with basic network flow
        var flow = flow {
            emit(NetworkResultFactory.loading<T>())

            // Retry logic
            val maxRetries = retryConfig?.maxAttempts ?: 0
            var attempt = 0
            var lastException: Throwable? = null

            while (attempt <= maxRetries) {
                try {
                    val result = request()
                    emit(NetworkResultFactory.success(result))
                    return@flow // Success, exit retry loop
                } catch (exception: Throwable) {
                    lastException = exception
                    val networkError = when (exception) {
                        is NetworkError -> exception
                        else -> NetworkError.UnknownError(
                            message = exception.message ?: "Network request failed",
                            cause = exception
                        )
                    }

                    // Check if we should retry
                    if (attempt < maxRetries && retryConfig?.shouldRetry?.invoke(networkError) == true) {
                        attempt++
                        // Emit loading state for retry
                        emit(NetworkResultFactory.loading<T>())
                        // Wait before retry
                        delay(retryConfig!!.initialDelay * attempt)
                    } else {
                        // No more retries, emit final error
                        emit(NetworkResultFactory.failure<T>(networkError))
                        return@flow
                    }
                }
            }
        }

        // Apply timeout if configured
        timeoutDuration?.let { duration ->
            flow = flow.timeoutNetwork(duration)
        }

        // Apply debouncing if configured
        debounceTime?.let { duration ->
            flow = flow.debounceNetwork(duration)
        }

        // Apply retry configuration
        retryConfig?.let { config ->
            flow = flow.retryNetworkRequest(
                maxAttempts = config.maxAttempts,
                initialDelay = config.initialDelay,
                shouldRetry = config.shouldRetry
            )
        }

        // Apply caching if configured
        cacheConfig?.let { config ->
            cache?.let { cacheInstance ->
                when (config.strategy) {
                    CacheStrategy.STALE_WHILE_REVALIDATE -> {
                        flow = flow.cacheStaleWhileRevalidate(
                            cache = cacheInstance,
                            key = config.key,
                            ttl = config.ttl
                        )
                    }
                    // Other cache strategies can be implemented here
                    else -> {
                        // Default caching behavior
                    }
                }
            }
        }

        // Apply offline support if enabled
        if (offlineSupport && cache != null && connectivityMonitor != null) {
            val cacheKey = cacheConfig?.key ?: "default_cache_key"
            flow = flow.offlineFallback(
                cache = cache!!,
                key = cacheKey,
                connectivityMonitor = connectivityMonitor!!
            )
        }

        // Apply authentication if required
        if (authRequired && authProvider != null) {
            flow = flow.withAuthentication(authProvider!!)
        }

        // Apply logging if enabled
        if (loggingEnabled) {
            flow = flow.logNetwork(logTag)
        }

        return flow
    }

    /**
     * Build and convert to UiState flow for direct UI consumption
     */
    fun buildAsUiState(): Flow<UiState<T>> = build().asUiState()

    // Configuration data classes
    private data class CacheConfig(
        val key: String,
        val strategy: CacheStrategy,
        val ttl: Duration
    )

    private data class RetryConfig(
        val maxAttempts: Int,
        val initialDelay: Duration,
        val shouldRetry: (NetworkError) -> Boolean
    )
}

/**
 * DSL function to create network flows with fluent configuration
 */
fun <T> networkFlow(block: NetworkFlowBuilder<T>.() -> Unit): Flow<NetworkResponse<T>> {
    return NetworkFlowBuilder<T>().apply(block).build()
}

/**
 * DSL function to create network flows that return UiState directly
 */
fun <T> networkUiFlow(block: NetworkFlowBuilder<T>.() -> Unit): Flow<UiState<T>> {
    return NetworkFlowBuilder<T>().apply(block).buildAsUiState()
}

/**
 * Convenience function for simple network requests
 */
fun <T> simpleNetworkFlow(request: suspend () -> T): Flow<NetworkResponse<T>> {
    return networkFlow { source(request) }
}

/**
 * Convenience function for cached network requests
 */
fun <T> cachedNetworkFlow(
    key: String,
    request: suspend () -> T,
    ttl: Duration = 5.minutes
): Flow<NetworkResponse<T>> {
    return networkFlow {
        source(request)
        cache(key, CacheStrategy.STALE_WHILE_REVALIDATE, ttl)
    }
}

/**
 * Convenience function for authenticated network requests
 */
fun <T> authenticatedNetworkFlow(
    request: suspend () -> T
): Flow<NetworkResponse<T>> {
    return networkFlow {
        source(request)
        requireAuth()
        retryOnFailure(maxAttempts = 2) // Retry once on auth failure
    }
}

/**
 * Convenience function for search requests with debouncing
 */
fun <T> searchNetworkFlow(
    request: suspend () -> T,
    debounceTime: Duration = 300.seconds
): Flow<NetworkResponse<T>> {
    return networkFlow {
        source(request)
        debounce(debounceTime)
        retryOnFailure(maxAttempts = 2)
    }
}