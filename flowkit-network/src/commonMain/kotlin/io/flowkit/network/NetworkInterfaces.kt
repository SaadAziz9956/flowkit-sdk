package io.flowkit.network

import kotlinx.coroutines.flow.Flow
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes

/**
 * Interface for network request execution
 * Pure abstraction - no implementation details
 */
interface NetworkExecutor {
    /**
     * Execute a network request and return a Flow of results
     */
    suspend fun <T> execute(
        request: suspend () -> T,
        config: NetworkConfig = NetworkConfig()
    ): Flow<NetworkResponse<T>>

    /**
     * Execute a network request with caching support
     */
    suspend fun <T> executeWithCache(
        key: String,
        request: suspend () -> T,
        strategy: CacheStrategy = CacheStrategy.CACHE_FIRST,
        config: NetworkConfig = NetworkConfig()
    ): Flow<NetworkResponse<T>>
}

/**
 * Interface for network connectivity monitoring
 * Platform-specific implementations will handle actual connectivity detection
 */
interface NetworkConnectivityMonitor {
    /**
     * Flow that emits connectivity status changes
     */
    val isConnected: Flow<Boolean>

    /**
     * Get current connectivity status
     */
    suspend fun isCurrentlyConnected(): Boolean
}

/**
 * Interface for network caching
 * Implementations can use in-memory, disk, or remote caching
 */
interface NetworkCache {
    /**
     * Get cached data for a key
     */
    suspend fun <T> get(key: String): T?

    /**
     * Store data in cache with TTL
     */
    suspend fun <T> put(key: String, data: T, ttl: Duration = 5.minutes)

    /**
     * Remove cached data for a key
     */
    suspend fun remove(key: String)

    /**
     * Clear all cached data
     */
    suspend fun clear()

    /**
     * Check if cache has valid data for a key
     */
    suspend fun isValid(key: String): Boolean
}

/**
 * Interface for authentication token management
 * Implementations handle token storage, refresh, and validation
 */
interface AuthTokenProvider {
    /**
     * Get current auth token
     */
    suspend fun getToken(): String?

    /**
     * Refresh auth token
     */
    suspend fun refreshToken(): String?

    /**
     * Clear stored token
     */
    suspend fun clearToken()

    /**
     * Check if token is expired
     */
    suspend fun isTokenExpired(): Boolean
}

/**
 * Network request interceptor interface
 * For adding headers, logging, authentication, etc.
 */
interface NetworkInterceptor {
    /**
     * Intercept and potentially modify network requests
     */
    suspend fun <T> intercept(
        request: suspend () -> T,
        config: NetworkConfig
    ): T
}

/**
 * Common HTTP methods
 */
enum class HttpMethod {
    GET, POST, PUT, DELETE, PATCH, HEAD, OPTIONS
}

/**
 * HTTP request representation
 */
data class HttpRequest(
    val url: String,
    val method: HttpMethod = HttpMethod.GET,
    val headers: Map<String, String> = emptyMap(),
    val body: Any? = null,
    val config: NetworkConfig = NetworkConfig()
)

/**
 * HTTP response representation
 */
data class HttpResponse<T>(
    val data: T,
    val statusCode: Int,
    val headers: Map<String, String> = emptyMap(),
    val fromCache: Boolean = false
)