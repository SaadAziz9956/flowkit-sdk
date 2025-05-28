package io.flowkit.storage

import io.flowkit.core.UiState
import io.flowkit.core.dataOrNull
import io.flowkit.core.isSuccess
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlin.math.pow
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes

/**
 * DSL builder for storage operations
 */
class StorageFlowBuilder<T> {
    private var sourceOperation: (suspend () -> T)? = null
    private var cacheKey: String? = null
    private var cacheTtl: Duration? = null
    private var retryAttempts: Int = 0
    private var fallbackValue: T? = null
    private var enableLogging: Boolean = false
    private var logTag: String = "StorageFlow"

    // Dependencies
    private var storage: ReactiveStorage? = null
    private var cacheStorage: CacheStorage? = null

    /**
     * Set the storage operation source
     */
    fun source(operation: suspend () -> T): StorageFlowBuilder<T> {
        sourceOperation = operation
        return this
    }

    /**
     * Enable caching with TTL
     */
    fun cache(key: String, ttl: Duration = 5.minutes): StorageFlowBuilder<T> {
        cacheKey = key
        cacheTtl = ttl
        return this
    }

    /**
     * Configure retry on failure
     */
    fun retryOnFailure(maxAttempts: Int = 3): StorageFlowBuilder<T> {
        retryAttempts = maxAttempts
        return this
    }

    /**
     * Set fallback value on error
     */
    fun fallbackTo(value: T): StorageFlowBuilder<T> {
        fallbackValue = value
        return this
    }

    /**
     * Enable debug logging
     */
    fun enableLogging(tag: String = "StorageFlow"): StorageFlowBuilder<T> {
        enableLogging = true
        logTag = tag
        return this
    }

    /**
     * Inject storage dependencies
     */
    fun withStorage(
        storage: ReactiveStorage? = null,
        cacheStorage: CacheStorage? = null
    ): StorageFlowBuilder<T> {
        this.storage = storage
        this.cacheStorage = cacheStorage
        return this
    }

    /**
     * Build the storage flow
     */
    fun build(): Flow<StorageResult<T>> {
        val operation =
            sourceOperation ?: throw IllegalStateException("Source operation must be set")

        return flow {
            emit(StorageResultFactory.loading<T>())

            // Try to get from cache first
            if (cacheKey != null && cacheStorage != null) {
                if (enableLogging) {
                    println("🔍 [$logTag] Checking cache for key: $cacheKey")
                }

                val cached = cacheStorage!!.getIfValid<T>(cacheKey!!)
                if (cached.isSuccess && cached.dataOrNull() != null) {
                    if (enableLogging) {
                        println("✅ [$logTag] Cache hit for key: $cacheKey")
                    }
                    emit(StorageResultFactory.success(cached.dataOrNull()!!))
                    return@flow
                }
            }

            // Execute operation with retry logic
            var attempt = 0
            var lastError: StorageError? = null

            while (attempt <= retryAttempts) {
                try {
                    if (enableLogging && attempt > 0) {
                        println("🔄 [$logTag] Retry attempt $attempt")
                    }

                    val result = operation()

                    if (enableLogging) {
                        println("✅ [$logTag] Operation successful: $result")
                    }

                    // Cache the result
                    if (cacheKey != null && cacheStorage != null && cacheTtl != null) {
                        cacheStorage!!.putWithTtl(cacheKey!!, result, cacheTtl!!)
                        if (enableLogging) {
                            println("💾 [$logTag] Cached result with TTL: ${cacheTtl}")
                        }
                    }

                    emit(StorageResultFactory.success(result))
                    return@flow

                } catch (e: StorageError) {
                    lastError = e
                    if (enableLogging) {
                        println("❌ [$logTag] Storage error on attempt $attempt: ${e.message}")
                    }
                } catch (e: Exception) {
                    lastError = StorageError.UnknownStorageError(e.message ?: "Unknown error", e)
                    if (enableLogging) {
                        println("❌ [$logTag] Unknown error on attempt $attempt: ${e.message}")
                    }
                }

                attempt++

                if (attempt <= retryAttempts) {
                    val baseDelay = 1000L // 1 second
                    delay(baseDelay * (2.0.pow(attempt - 1)).toLong())
                }
            }

            // All retries failed, try fallback
            if (fallbackValue != null) {
                if (enableLogging) {
                    println("🔄 [$logTag] Using fallback value: $fallbackValue")
                }
                emit(StorageResultFactory.success(fallbackValue!!))
            } else {
                emit(StorageResultFactory.failure(lastError!!))
            }
        }
    }

    /**
     * Build and convert to UiState
     */
    fun buildAsUiState(): Flow<UiState<T>> = build().asUiState()
}

/**
 * DSL function to create storage flows
 */
fun <T> storageFlow(block: StorageFlowBuilder<T>.() -> Unit): Flow<StorageResult<T>> {
    return StorageFlowBuilder<T>().apply(block).build()
}

/**
 * DSL function to create storage flows that return UiState
 */
fun <T> storageUiFlow(block: StorageFlowBuilder<T>.() -> Unit): Flow<UiState<T>> {
    return StorageFlowBuilder<T>().apply(block).buildAsUiState()
}

/**
 * Convenience function for simple storage read operations
 */
fun <T> simpleStorageRead(
    storage: ReactiveStorage,
    key: String,
    defaultValue: T? = null
): Flow<StorageResult<T?>> {
    return storageFlow {
        source { storage.get<T>(key).dataOrNull() ?: defaultValue }
        withStorage(storage = storage)
    }
}

/**
 * Convenience function for cached storage operations
 */
fun <T> cachedStorageFlow(
    key: String,
    operation: suspend () -> T,
    ttl: Duration = 5.minutes,
    cacheStorage: CacheStorage = DefaultCacheStorage
): Flow<StorageResult<T>> {
    return storageFlow {
        source(operation)
        cache(key, ttl)
        withStorage(cacheStorage = cacheStorage)
    }
}

/**
 * Convenience function for storage operations with fallback
 */
fun <T> storageWithFallback(
    operation: suspend () -> T,
    fallback: T,
    retryAttempts: Int = 3
): Flow<StorageResult<T>> {
    return storageFlow {
        source(operation)
        retryOnFailure(retryAttempts)
        fallbackTo(fallback)
    }
}

/**
 * Storage preference builder for type-safe preferences
 */
class StoragePreferenceBuilder(private val storage: KeyValueStorage) {

    fun stringPreference(key: String, defaultValue: String = ""): StoragePreference<String> =
        StoragePreference(
            storage, key, defaultValue,
            getter = { storage.getString(key, defaultValue) },
            setter = { storage.putString(key, it) },
            observer = { storage.observeString(key, defaultValue) }
        )

    fun intPreference(key: String, defaultValue: Int = 0): StoragePreference<Int> =
        StoragePreference(
            storage, key, defaultValue,
            getter = { storage.getInt(key, defaultValue) },
            setter = { storage.putInt(key, it) },
            observer = { storage.observeInt(key, defaultValue) }
        )

    fun booleanPreference(key: String, defaultValue: Boolean = false): StoragePreference<Boolean> =
        StoragePreference(
            storage, key, defaultValue,
            getter = { storage.getBoolean(key, defaultValue) },
            setter = { storage.putBoolean(key, it) },
            observer = { storage.observeBoolean(key, defaultValue) }
        )

    fun longPreference(key: String, defaultValue: Long = 0L): StoragePreference<Long> =
        StoragePreference(
            storage, key, defaultValue,
            getter = { storage.getLong(key, defaultValue) },
            setter = { storage.putLong(key, it) },
            observer = { storage.observeLong(key, defaultValue) }
        )

    fun floatPreference(key: String, defaultValue: Float = 0f): StoragePreference<Float> =
        StoragePreference(
            storage, key, defaultValue,
            getter = { storage.getFloat(key, defaultValue) },
            setter = { storage.putFloat(key, it) },
            observer = { storage.observeFloat(key, defaultValue) }
        )

    fun doublePreference(key: String, defaultValue: Double = 0.0): StoragePreference<Double> =
        StoragePreference(
            storage, key, defaultValue,
            getter = { storage.getDouble(key, defaultValue) },
            setter = { storage.putDouble(key, it) },
            observer = { storage.observeDouble(key, defaultValue) }
        )
}

/**
 * Type-safe storage preference wrapper
 */
class StoragePreference<T>(
    private val storage: KeyValueStorage,
    private val key: String,
    private val defaultValue: T,
    private val getter: suspend () -> StorageResult<T>,
    private val setter: suspend (T) -> StorageResult<Unit>,
    private val observer: () -> Flow<T>
) {
    suspend fun get(): T = getter().dataOrNull() ?: defaultValue

    suspend fun set(value: T): StorageResult<Unit> = setter(value)

    fun observe(): Flow<T> = observer()

    suspend fun remove(): StorageResult<Unit> = storage.remove(key)

    suspend fun exists(): Boolean = storage.contains(key).dataOrNull() ?: false
}

/**
 * DSL function to create preferences
 */
fun KeyValueStorage.preferences(block: StoragePreferenceBuilder.() -> Unit): StoragePreferenceBuilder {
    return StoragePreferenceBuilder(this).apply(block)
}

/**
 * Storage repository pattern builder
 */
abstract class StorageRepository(
    protected val storage: ReactiveStorage,
    protected val cacheStorage: CacheStorage = DefaultCacheStorage
) {

    /**
     * Load data with caching
     */
    protected fun <T> loadCached(
        key: String,
        loader: suspend () -> T,
        ttl: Duration = 5.minutes
    ): Flow<UiState<T>> {
        return cachedStorageFlow(key, loader, ttl, cacheStorage).asUiState()
    }

    /**
     * Save data to storage
     */
    protected suspend fun <T> save(key: String, data: T): StorageResult<Unit> {
        return storage.put(key, data)
    }

    /**
     * Observe data changes
     */
    protected fun <T> observe(key: String, defaultValue: T): Flow<T> {
        return storage.observeKeySafe(key, defaultValue)
    }

    /**
     * Clear all data
     */
    protected suspend fun clearAll(): StorageResult<Unit> {
        return storage.clear()
    }
}