package io.flowkit.network

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlin.time.Clock
import kotlin.time.Duration
import kotlin.time.ExperimentalTime

/**
 * Simple in-memory cache implementation for network responses
 * Thread-safe and supports TTL (Time To Live)
 */
class SimpleNetworkCache : NetworkCache {

    private val cache = mutableMapOf<String, CacheEntry<*>>()
    private val mutex = Mutex()

    override suspend fun <T> get(key: String): T? {
        return mutex.withLock {
            val entry = cache[key] as? CacheEntry<T>
            if (entry != null && !entry.isExpired()) {
                entry.data
            } else {
                // Remove expired entry
                cache.remove(key)
                null
            }
        }
    }

    @OptIn(ExperimentalTime::class)
    override suspend fun <T> put(key: String, data: T, ttl: Duration) {
        mutex.withLock {
            val expirationTime = Clock.System.now().toEpochMilliseconds() + ttl.inWholeMilliseconds
            cache[key] = CacheEntry(data, expirationTime)
        }
    }

    override suspend fun remove(key: String) {
        mutex.withLock {
            cache.remove(key)
        }
    }

    override suspend fun clear() {
        mutex.withLock {
            cache.clear()
        }
    }

    override suspend fun isValid(key: String): Boolean {
        return mutex.withLock {
            val entry = cache[key]
            entry != null && !entry.isExpired()
        }
    }

    /**
     * Get cache statistics for debugging
     */
    suspend fun getStats(): CacheStats {
        return mutex.withLock {
            val totalEntries = cache.size
            val expiredEntries = cache.values.count { it.isExpired() }
            CacheStats(
                totalEntries = totalEntries,
                validEntries = totalEntries - expiredEntries,
                expiredEntries = expiredEntries
            )
        }
    }

    /**
     * Clean up expired entries
     */
    suspend fun cleanupExpired() {
        mutex.withLock {
            val expiredKeys = cache.filterValues { it.isExpired() }.keys
            expiredKeys.forEach { cache.remove(it) }
        }
    }

    private data class CacheEntry<T>(
        val data: T,
        val expirationTime: Long
    ) {
        @OptIn(ExperimentalTime::class)
        fun isExpired(): Boolean = Clock.System.now().toEpochMilliseconds() > expirationTime
    }

    data class CacheStats(
        val totalEntries: Int,
        val validEntries: Int,
        val expiredEntries: Int
    )
}

/**
 * Default network cache instance
 */
val DefaultNetworkCache: NetworkCache = SimpleNetworkCache()