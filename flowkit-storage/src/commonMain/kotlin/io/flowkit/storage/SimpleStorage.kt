package io.flowkit.storage

import io.flowkit.core.dataOrNull
import io.flowkit.core.map
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlin.time.Clock
import kotlin.time.Duration
import kotlin.time.ExperimentalTime

/**
 * Simple in-memory storage implementation
 * Thread-safe and supports basic reactive operations
 */
class SimpleMemoryStorage : ReactiveStorage, KeyValueStorage {

    private val storage = mutableMapOf<String, Any?>()
    private val expirationTimes = mutableMapOf<String, Long>()
    private val mutex = Mutex()

    // SharedFlow for broadcasting changes
    private val _changes = MutableSharedFlow<StorageChangeEvent<Any?>>()

    @OptIn(ExperimentalTime::class)
    override suspend fun <T> put(
        key: String,
        value: T,
        config: StorageConfig
    ): StorageResult<Unit> {
        return StorageResultFactory.catching {
            mutex.withLock {
                val oldValue = storage[key]
                storage[key] = value

                // Handle expiration
                if (config.expirationTime != null) {
                    expirationTimes[key] = Clock.System.now()
                        .toEpochMilliseconds() + config.expirationTime.inWholeMilliseconds
                }

                // Emit change event
                _changes.tryEmit(
                    StorageChangeEvent(
                        key = key,
                        operation = if (oldValue == null) StorageOperation.INSERT else StorageOperation.UPDATE,
                        oldValue = oldValue,
                        newValue = value
                    )
                )
            }
        }
    }

    @OptIn(ExperimentalTime::class)
    override suspend fun <T> get(key: String): StorageResult<T?> {
        return StorageResultFactory.catching {
            mutex.withLock {
                // Check expiration
                val expirationTime = expirationTimes[key]
                if (expirationTime != null && Clock.System.now()
                        .toEpochMilliseconds() > expirationTime
                ) {
                    storage.remove(key)
                    expirationTimes.remove(key)
                    return@withLock null
                }

                @Suppress("UNCHECKED_CAST")
                storage[key] as? T
            }
        }
    }

    override suspend fun remove(key: String): StorageResult<Unit> {
        return StorageResultFactory.catching {
            mutex.withLock {
                val oldValue = storage.remove(key)
                expirationTimes.remove(key)

                if (oldValue != null) {
                    _changes.tryEmit(
                        StorageChangeEvent(
                            key = key,
                            operation = StorageOperation.DELETE,
                            oldValue = oldValue,
                            newValue = null
                        )
                    )
                }
            }
        }
    }

    @OptIn(ExperimentalTime::class)
    override suspend fun contains(key: String): StorageResult<Boolean> {
        return StorageResultFactory.catching {
            mutex.withLock {
                // Check expiration
                val expirationTime = expirationTimes[key]
                if (expirationTime != null && Clock.System.now()
                        .toEpochMilliseconds() > expirationTime
                ) {
                    storage.remove(key)
                    expirationTimes.remove(key)
                    return@withLock false
                }

                storage.containsKey(key)
            }
        }
    }

    override suspend fun clear(): StorageResult<Unit> {
        return StorageResultFactory.catching {
            mutex.withLock {
                storage.clear()
                expirationTimes.clear()

                _changes.tryEmit(
                    StorageChangeEvent(
                        key = "*",
                        operation = StorageOperation.CLEAR,
                        oldValue = null,
                        newValue = null
                    )
                )
            }
        }
    }

    @OptIn(ExperimentalTime::class)
    override suspend fun getAllKeys(): StorageResult<Set<String>> {
        return StorageResultFactory.catching {
            mutex.withLock {
                // Clean up expired keys first
                val currentTime = Clock.System.now().toEpochMilliseconds()
                val expiredKeys = expirationTimes.filter { (_, expiration) ->
                    currentTime > expiration
                }.keys

                expiredKeys.forEach { key ->
                    storage.remove(key)
                    expirationTimes.remove(key)
                }

                storage.keys.toSet()
            }
        }
    }

    override suspend fun getSize(): StorageResult<Long> {
        return StorageResultFactory.catching {
            mutex.withLock {
                // Rough estimate of memory usage
                storage.values.sumOf { value ->
                    when (value) {
                        is String -> value.length * 2L // UTF-16
                        is ByteArray -> value.size.toLong()
                        else -> 64L // Rough estimate for other objects
                    }
                }
            }
        }
    }

    override fun <T> observe(key: String): Flow<StorageResult<T?>> = flow {
        // Emit current value first
        emit(get<T>(key))

        // Then observe changes
        _changes
            .filter { it.key == key || it.key == "*" }
            .collect {
                emit(get<T>(key))
            }
    }

    override fun <T> observeChanges(): Flow<StorageChangeEvent<T>> = _changes
        .map {
            @Suppress("UNCHECKED_CAST")
            it as StorageChangeEvent<T>
        }

    // KeyValueStorage implementation
    override suspend fun putString(key: String, value: String): StorageResult<Unit> =
        put(key, value)

    override suspend fun putInt(key: String, value: Int): StorageResult<Unit> =
        put(key, value)

    override suspend fun putLong(key: String, value: Long): StorageResult<Unit> =
        put(key, value)

    override suspend fun putFloat(key: String, value: Float): StorageResult<Unit> =
        put(key, value)

    override suspend fun putDouble(key: String, value: Double): StorageResult<Unit> =
        put(key, value)

    override suspend fun putBoolean(key: String, value: Boolean): StorageResult<Unit> =
        put(key, value)

    override suspend fun getString(key: String, defaultValue: String): StorageResult<String> {
        val result = get<String>(key)
        return result.map { it ?: defaultValue }
    }

    override suspend fun getInt(key: String, defaultValue: Int): StorageResult<Int> {
        val result = get<Int>(key)
        return result.map { it ?: defaultValue }
    }

    override suspend fun getLong(key: String, defaultValue: Long): StorageResult<Long> {
        val result = get<Long>(key)
        return result.map { it ?: defaultValue }
    }

    override suspend fun getFloat(key: String, defaultValue: Float): StorageResult<Float> {
        val result = get<Float>(key)
        return result.map { it ?: defaultValue }
    }

    override suspend fun getDouble(key: String, defaultValue: Double): StorageResult<Double> {
        val result = get<Double>(key)
        return result.map { it ?: defaultValue }
    }

    override suspend fun getBoolean(key: String, defaultValue: Boolean): StorageResult<Boolean> {
        val result = get<Boolean>(key)
        return result.map { it ?: defaultValue }
    }

    override fun observeString(key: String, defaultValue: String): Flow<String> =
        observeKeySafe(key, defaultValue)

    override fun observeInt(key: String, defaultValue: Int): Flow<Int> =
        observeKeySafe(key, defaultValue)

    override fun observeLong(key: String, defaultValue: Long): Flow<Long> =
        observeKeySafe(key, defaultValue)

    override fun observeFloat(key: String, defaultValue: Float): Flow<Float> =
        observeKeySafe(key, defaultValue)

    override fun observeDouble(key: String, defaultValue: Double): Flow<Double> =
        observeKeySafe(key, defaultValue)

    override fun observeBoolean(key: String, defaultValue: Boolean): Flow<Boolean> =
        observeKeySafe(key, defaultValue)

    /**
     * Clean up expired entries manually
     */
    @OptIn(ExperimentalTime::class)
    suspend fun cleanupExpired(): StorageResult<Int> {
        return StorageResultFactory.catching {
            mutex.withLock {
                val currentTime = Clock.System.now().toEpochMilliseconds()
                val expiredKeys = expirationTimes.filter { (_, expiration) ->
                    currentTime > expiration
                }.keys

                expiredKeys.forEach { key ->
                    storage.remove(key)
                    expirationTimes.remove(key)
                }

                expiredKeys.size
            }
        }
    }

    /**
     * Get storage statistics
     */
    @OptIn(ExperimentalTime::class)
    suspend fun getStats(): StorageStats {
        return mutex.withLock {
            StorageStats(
                totalKeys = storage.size,
                totalSize = getSize().dataOrNull() ?: 0L,
                expiredKeys = expirationTimes.count { (_, expiration) ->
                    Clock.System.now().toEpochMilliseconds() > expiration
                }
            )
        }
    }
}

/**
 * Storage statistics data class
 */
data class StorageStats(
    val totalKeys: Int,
    val totalSize: Long,
    val expiredKeys: Int
)

/**
 * Cache storage implementation with TTL support
 */
class SimpleCacheStorage : CacheStorage {

    private val storage = SimpleMemoryStorage()

    override suspend fun <T> put(
        key: String,
        value: T,
        config: StorageConfig
    ): StorageResult<Unit> =
        storage.put(key, value, config)

    override suspend fun <T> get(key: String): StorageResult<T?> = storage.get(key)

    override suspend fun remove(key: String): StorageResult<Unit> = storage.remove(key)

    override suspend fun contains(key: String): StorageResult<Boolean> = storage.contains(key)

    override suspend fun clear(): StorageResult<Unit> = storage.clear()

    override suspend fun getAllKeys(): StorageResult<Set<String>> = storage.getAllKeys()

    override suspend fun getSize(): StorageResult<Long> = storage.getSize()

    override fun <T> observe(key: String): Flow<StorageResult<T?>> = storage.observe(key)

    override fun <T> observeChanges(): Flow<StorageChangeEvent<T>> = storage.observeChanges()

    override suspend fun <T> putWithTtl(key: String, value: T, ttl: Duration): StorageResult<Unit> =
        storage.put(key, value, StorageConfig(expirationTime = ttl))

    override suspend fun <T> getIfValid(key: String): StorageResult<T?> = storage.get(key)

    override suspend fun isExpired(key: String): StorageResult<Boolean> {
        val containsResult = storage.contains(key)
        return containsResult.map { !it } // If key doesn't exist, it's "expired"
    }

    override suspend fun cleanupExpired(): StorageResult<Int> = storage.cleanupExpired()

    override suspend fun setGlobalTtl(ttl: Duration): StorageResult<Unit> {
        // For simplicity, this implementation doesn't support global TTL
        // In a real implementation, you'd store this and apply to all new entries
        return StorageResultFactory.success(Unit)
    }
}

/**
 * Default storage instances
 */
val DefaultMemoryStorage: ReactiveStorage = SimpleMemoryStorage()
val DefaultCacheStorage: CacheStorage = SimpleCacheStorage()