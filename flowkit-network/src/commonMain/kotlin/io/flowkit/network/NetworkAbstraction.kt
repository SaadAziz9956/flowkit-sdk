package io.flowkit.network

import io.flowkit.core.Result
import io.flowkit.core.UiState
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

// Typealias reusing core AppResult
typealias NetworkResult<T> = Result<NetworkError, T>

/**
 * Type aliases for common network result patterns
 */
typealias ApiResult<T> = Result<NetworkError, T>
typealias NetworkResponse<T> = Result<NetworkError, T>

/**
 * Network-specific error types
 */
sealed class NetworkError : Exception() {
    object NoInternetConnection : NetworkError() {
        override val message = "No internet connection"
    }
    data class HttpError(val code: Int, override val message: String) : NetworkError()
    data class TimeoutError(override val message: String = "Request timed out") : NetworkError()
    data class SerializationError(override val message: String, override val cause: Throwable? = null) : NetworkError()
    data class UnknownError(override val message: String, override val cause: Throwable? = null) : NetworkError()
}
/**
 * Extension properties for NetworkResult
 */
val <T> NetworkResult<T>.isError: Boolean
    get() = this is Result.Error

val <T> NetworkResult<T>.isSuccess: Boolean
    get() = this is Result.Success

val <T> NetworkResult<T>.isLoading: Boolean
    get() = this is Result.Loading


/**
 * Safe getters for NetworkResult values
 */
val <T> NetworkResult<T>.errorOrNull: NetworkError?
    get() = (this as? Result.Error)?.error

val <T> NetworkResult<T>.dataOrNull: T?
    get() = (this as? Result.Success)?.data

/**
 * Fold function for NetworkResult
 */
inline fun <T, R> NetworkResult<T>.fold(
    onFailure: (NetworkError) -> R,
    onSuccess: (T) -> R,
    onLoading: () -> R
): R = when (this) {
    is Result.Error -> onFailure(error)
    is Result.Success -> onSuccess(data)
    is Result.Loading -> onLoading()
}

/**
 * Map function for transforming success values
 */
inline fun <T, R> NetworkResult<T>.map(
    transform: (T) -> R
): NetworkResult<R> = when (this) {
    is Result.Error -> this
    is Result.Success -> Result.Success(transform(data))
    is Result.Loading -> Result.Loading
}

/**
 * Map function for transforming error values
 */
inline fun <T, R> NetworkResult<T>.mapError(
    transform: (NetworkError) -> R
): Result<R, T> = when (this) {
    is Result.Error -> Result.Error(transform(error))
    is Result.Success -> this
    is Result.Loading -> Result.Loading
}


/**
 * FlatMap function for chaining operations
 */
inline fun <T, R> NetworkResult<T>.flatMap(
    transform: (T) -> NetworkResult<R>
): NetworkResult<R> = when (this) {
    is Result.Error -> this
    is Result.Success -> transform(data)
    is Result.Loading -> Result.Loading
}

/**
 * Get value or return default
 */
fun <T> NetworkResult<T>.getOrElse(default: T): T = when (this) {
    is Result.Success -> data
    else -> default
}

/**
 * Get value or compute default
 */
inline fun <T> NetworkResult<T>.getOrElse(default: () -> T): T = when (this) {
    is Result.Success -> data
    else -> default()
}

/**
 * Extension functions for side effects
 */
inline fun <T> NetworkResult<T>.onSuccess(action: (T) -> Unit): NetworkResult<T> {
    if (this is Result.Success) action(data)
    return this
}

inline fun NetworkResult<*>.onFailure(action: (NetworkError) -> Unit): NetworkResult<*> {
    if (this is Result.Error) action(error)
    return this
}

inline fun NetworkResult<*>.onLoading(action: () -> Unit): NetworkResult<*> {
    if (this is Result.Loading) action()
    return this
}

/**
 * Factory functions for creating NetworkResult instances
 */
object NetworkResultFactory {
    fun <T> success(data: T): NetworkResult<T> = Result.Success(data)
    fun <T> failure(error: NetworkError): NetworkResult<T> = Result.Error(error)
    fun <T> loading(): NetworkResult<T> = Result.Loading

    /**
     * Catch exceptions and convert to NetworkResult
     */
    inline fun <T> catching(block: () -> T): NetworkResult<T> = try {
        success(block())
    } catch (e: NetworkError) {
        failure(e)
    } catch (e: Exception) {
        failure(NetworkError.UnknownError(e.message ?: "Unknown error", e))
    }
}

/**
 * Convert NetworkResult to UiState
 */
fun <T> NetworkResult<T>.toUiState(): UiState<T> = when (this) {
    is Result.Loading -> UiState.Loading
    is Result.Success -> UiState.Success(data)
    is Result.Error -> UiState.Error(error, error.message ?: "")
}

/**
 * Convert UiState to NetworkResult
 */
fun <T> UiState<T>.toNetworkResult(): NetworkResponse<T> = when (this) {
    is UiState.Loading -> NetworkResultFactory.loading()
    is UiState.Success -> NetworkResultFactory.success(data)
    is UiState.Error -> NetworkResultFactory.failure(
        NetworkError.UnknownError(message, throwable)
    )
}

/**
 * Configuration for network requests
 */
data class NetworkConfig(
    val timeout: Duration = 30.seconds,
    val retryAttempts: Int = 3,
    val retryDelay: Duration = 1.seconds,
    val cacheEnabled: Boolean = false,
    val cacheTtl: Duration = 5.minutes,
    val requiresAuth: Boolean = false
)

/**
 * Cache strategy for network requests
 */
enum class CacheStrategy {
    NETWORK_ONLY,
    CACHE_FIRST,
    NETWORK_FIRST,
    STALE_WHILE_REVALIDATE,
    CACHE_ONLY
}