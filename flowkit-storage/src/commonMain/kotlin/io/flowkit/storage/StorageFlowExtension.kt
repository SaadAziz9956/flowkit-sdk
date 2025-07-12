@file:OptIn(FlowPreview::class)
package io.flowkit.storage

import io.flowkit.core.Result
import io.flowkit.core.UiState
import io.flowkit.core.dataOrNull
import io.flowkit.core.errorOrNull
import io.flowkit.core.fold
import io.flowkit.core.isError
import io.flowkit.core.isLoading
import io.flowkit.core.isSuccess
import io.flowkit.core.map
import io.flowkit.core.onFailure
import io.flowkit.core.onSuccess
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.retryWhen
import kotlin.time.Duration

/**
 * Convert StorageResult to UiState for UI consumption
 */
fun <T> Flow<StorageResult<T>>.asUiState(): Flow<UiState<T>> = map { result ->
    result.toUiState()
}

/**
 * Handle storage-specific errors
 */
fun <T> Flow<StorageResult<T>>.onStorageError(
    action: suspend (StorageError) -> Unit
): Flow<StorageResult<T>> = onEach { result ->
    result.onFailure { error -> action(error) }
}

/**
 * Handle successful storage operations
 */
fun <T> Flow<StorageResult<T>>.onStorageSuccess(
    action: suspend (T) -> Unit
): Flow<StorageResult<T>> = onEach { result ->
    result.onSuccess { data -> action(data) }
}

/**
 * Transform storage data
 */
fun <T, R> Flow<StorageResult<T>>.mapStorageData(
    transform: suspend (T) -> R
): Flow<StorageResult<R>> = map { result ->
    result.map { transform(it) }
}

/**
 * Filter storage results
 */
fun <T> Flow<StorageResult<T>>.filterStorageSuccess(
    predicate: suspend (T) -> Boolean
): Flow<StorageResult<T>> = filter { result ->
    when {
        result.isSuccess -> result.dataOrNull()?.let { predicate(it) } ?: false
        else -> true // Pass through loading and error states
    }
}

/**
 * Provide fallback values for storage errors
 */
fun <T> Flow<StorageResult<T>>.storageOrElse(
    fallback: suspend (StorageError) -> T
): Flow<StorageResult<T>> = map { result ->
    result.fold(
        onFailure = { error ->
            try {
                StorageResultFactory.success(fallback(error))
            } catch (e: Exception) {
                StorageResultFactory.failure(StorageError.UnknownStorageError("Fallback failed", e))
            }
        },
        onSuccess = { data -> StorageResultFactory.success(data) },
        onLoading = { StorageResultFactory.loading() }
    )
}

/**
 * Cache flow results in storage
 */
fun <T> Flow<T>.cacheInStorage(
    storage: ReactiveStorage,
    key: String,
    ttl: Duration? = null
): Flow<T> = onEach { data ->
    val config = StorageConfig(expirationTime = ttl)
    storage.put(key, data, config)
}

/**
 * Load from storage with fallback to flow
 */
fun <T> Flow<T>.loadFromStorageOrElse(
    storage: ReactiveStorage,
    key: String
): Flow<T> = flow {
    // Try to load from storage first
    val cached = storage.get<T>(key)
    if (cached.isSuccess && cached.dataOrNull() != null) {
        emit(cached.dataOrNull()!!)
    }

    // Then emit from original flow and cache
    collect { data ->
        storage.put(key, data)
        emit(data)
    }
}

/**
 * Observe storage key with automatic error handling
 */
fun <T> ReactiveStorage.observeKeySafe(
    key: String,
    defaultValue: T
): Flow<T> = observe<T>(key)
    .map { result ->
        result.fold(
            onFailure = { defaultValue },
            onSuccess = { it ?: defaultValue },
            onLoading = { defaultValue }
        )
    }
    .distinctUntilChanged()

/**
 * Create a storage-backed StateFlow
 */
fun <T> ReactiveStorage.createStorageStateFlow(
    key: String,
    initialValue: T
): Flow<T> = flow {
    // Emit initial value
    emit(initialValue)

    // Load from storage
    val stored = get<T>(key)
    if (stored.isSuccess && stored.dataOrNull() != null) {
        emit(stored.dataOrNull()!!)
    }

    // Observe future changes
    observeKeySafe(key, initialValue).collect { emit(it) }
}

/**
 * Combine multiple storage flows
 */
fun <T1, T2, R> combineStorageFlows(
    flow1: Flow<StorageResult<T1>>,
    flow2: Flow<StorageResult<T2>>,
    transform: suspend (T1, T2) -> R
): Flow<StorageResult<R>> = flow1.combine(flow2) { result1, result2 ->
    when {
        result1.isLoading || result2.isLoading -> StorageResultFactory.loading()
        result1.isError -> StorageResultFactory.failure(result1.errorOrNull()!!)
        result2.isError -> StorageResultFactory.failure(result2.errorOrNull()!!)
        result1.isSuccess && result2.isSuccess -> {
            StorageResultFactory.success(transform(result1.dataOrNull()!!, result2.dataOrNull()!!))
        }
        else -> StorageResultFactory.loading()
    }
}

/**
 * Retry storage operations with exponential backoff
 */
fun <T> Flow<StorageResult<T>>.retryStorageOperation(
    maxAttempts: Int = 3,
    shouldRetry: (StorageError) -> Boolean = { true }
): Flow<StorageResult<T>> = retryWhen { cause, attempt ->
    if (attempt < maxAttempts && cause is StorageError && shouldRetry(cause)) {
        kotlinx.coroutines.delay(1000 * (attempt + 1))
        true
    } else {
        false
    }
}

/**
 * Debounce storage writes to prevent excessive I/O
 */
fun <T> Flow<T>.debounceStorage(
    duration: kotlin.time.Duration,
    storage: ReactiveStorage,
    key: String
): Flow<T> = debounce(duration)
    .onEach { data -> storage.put(key, data) }

/**
 * Sync storage data across multiple storages
 */
fun <T> Flow<StorageResult<T>>.syncWith(
    targetStorage: ReactiveStorage,
    key: String
): Flow<StorageResult<T>> = onEach { result ->
    result.onSuccess { data ->
        targetStorage.put(key, data)
    }
}

/**
 * Create a two-way binding between storage and UI state
 */
fun <T> ReactiveStorage.createBinding(
    key: String,
    initialValue: T
): StorageBinding<T> = StorageBinding(this, key, initialValue)

/**
 * Storage binding for two-way data binding
 */
class StorageBinding<T>(
    private val storage: ReactiveStorage,
    private val key: String,
    private val initialValue: T
) {
    private val _value = MutableStateFlow(initialValue)

    val value: StateFlow<T> = _value.asStateFlow()

    suspend fun setValue(newValue: T) {
        _value.value = newValue
        storage.put(key, newValue)
    }

    fun observeValue(): Flow<T> = storage.observeKeySafe(key, initialValue)
        .onEach { _value.value = it }
}

/**
 * Factory functions for StorageResult
 */
object StorageResultFactory {

    fun <T> success(data: T): StorageResult<T> =
        Result.Success(data)

    fun <T> failure(error: StorageError): StorageResult<T> =
        Result.Error(error)

    fun <T> loading(): StorageResult<T> = 
        Result.Loading

    /**
     * Catch exceptions and convert to StorageResult
     */
    inline fun <T> catching(block: () -> T): StorageResult<T> = try {
        success(block())
    } catch (e: StorageError) {
        failure(e)
    } catch (e: Exception) {
        failure(StorageError.UnknownStorageError(e.message ?: "Unknown storage error", e))
    }
}

/**
 * Extension to convert StorageResult to UiState
 */
fun <T> StorageResult<T>.toUiState(): UiState<T> = when (this) {
    is Result.Loading -> UiState.Loading
    is Result.Success -> UiState.Success(data)
    is Result.Error -> UiState.Error(error, error.message ?: "")
}

/**
 * Extension to convert UiState to StorageResult
 */
fun <T> UiState<T>.toStorageResult(): StorageResult<T> = when (this) {
    is UiState.Loading -> StorageResultFactory.loading()
    is UiState.Success -> StorageResultFactory.success(data)
    is UiState.Error -> {
        val storageError = when (val throwable = this.throwable) {
            is StorageError -> throwable
            else -> StorageError.UnknownStorageError(
                message = throwable.message ?: "Unknown error",
                cause = throwable
            )
        }
        StorageResultFactory.failure(storageError)
    }
}