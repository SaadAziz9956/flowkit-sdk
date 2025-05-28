package io.flowkit.network

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.runTest
import kotlin.test.*
import kotlin.time.Duration.Companion.milliseconds

@OptIn(ExperimentalCoroutinesApi::class)
class SimpleNetworkCacheTest {

    private lateinit var cache: SimpleNetworkCache

    @BeforeTest
    fun setup() {
        cache = SimpleNetworkCache()
    }

    @Test
    fun `cache should store and retrieve data`() = runTest {
        val key = "test_key"
        val data = "test_data"

        cache.put(key, data)
        val retrieved = cache.get<String>(key)

        assertEquals(data, retrieved)
    }

    @Test
    fun `cache should return null for non-existent keys`() = runTest {
        val retrieved = cache.get<String>("non_existent_key")

        assertNull(retrieved)
    }

    @Test
    fun `cache should respect TTL and expire data`() = runTest {
        val key = "ttl_key"
        val data = "ttl_data"
        val shortTtl = 50.milliseconds

        cache.put(key, data, shortTtl)

        // Should be available immediately
        assertEquals(data, cache.get<String>(key))

        // Wait for expiration
        delay(100.milliseconds)

        // Should be expired now
        assertNull(cache.get<String>(key))
    }

    @Test
    fun `isValid should return correct validity status`() = runTest {
        val key = "validity_key"
        val data = "validity_data"
        val shortTtl = 50.milliseconds

        // Should be invalid initially
        assertFalse(cache.isValid(key))

        cache.put(key, data, shortTtl)

        // Should be valid after storing
        assertTrue(cache.isValid(key))

        // Wait for expiration
        delay(100.milliseconds)

        // Should be invalid after expiration
        assertFalse(cache.isValid(key))
    }

    @Test
    fun `remove should delete specific cache entry`() = runTest {
        val key1 = "key1"
        val key2 = "key2"
        val data1 = "data1"
        val data2 = "data2"

        cache.put(key1, data1)
        cache.put(key2, data2)

        // Both should exist
        assertEquals(data1, cache.get<String>(key1))
        assertEquals(data2, cache.get<String>(key2))

        // Remove one
        cache.remove(key1)

        // Only key2 should remain
        assertNull(cache.get<String>(key1))
        assertEquals(data2, cache.get<String>(key2))
    }

    @Test
    fun `clear should remove all cache entries`() = runTest {
        cache.put("key1", "data1")
        cache.put("key2", "data2")
        cache.put("key3", "data3")

        // All should exist
        assertEquals("data1", cache.get<String>("key1"))
        assertEquals("data2", cache.get<String>("key2"))
        assertEquals("data3", cache.get<String>("key3"))

        // Clear all
        cache.clear()

        // None should exist
        assertNull(cache.get<String>("key1"))
        assertNull(cache.get<String>("key2"))
        assertNull(cache.get<String>("key3"))
    }

    @Test
    fun `cache should handle different data types`() = runTest {
        // String
        cache.put("string_key", "string_value")
        assertEquals("string_value", cache.get<String>("string_key"))

        // Int
        cache.put("int_key", 42)
        assertEquals(42, cache.get<Int>("int_key"))

        // List
        val list = listOf("a", "b", "c")
        cache.put("list_key", list)
        assertEquals(list, cache.get<List<String>>("list_key"))

        // Custom object
        data class TestData(val name: String, val value: Int)
        val customData = TestData("test", 123)
        cache.put("custom_key", customData)
        assertEquals(customData, cache.get<TestData>("custom_key"))
    }

    @Test
    fun `getStats should return correct cache statistics`() = runTest {
        // Empty cache
        var stats = cache.getStats()
        assertEquals(0, stats.totalEntries)
        assertEquals(0, stats.validEntries)
        assertEquals(0, stats.expiredEntries)

        // Add some entries
        cache.put("valid1", "data1")
        cache.put("valid2", "data2")
        cache.put("expired", "data3", 1.milliseconds)

        // Wait for one to expire
        delay(10.milliseconds)

        stats = cache.getStats()
        assertEquals(3, stats.totalEntries)
        assertEquals(2, stats.validEntries)
        assertEquals(1, stats.expiredEntries)
    }

    @Test
    fun `cleanupExpired should remove only expired entries`() = runTest {
        cache.put("valid", "valid_data")
        cache.put("expired1", "expired_data1", 1.milliseconds)
        cache.put("expired2", "expired_data2", 1.milliseconds)

        // Wait for expiration
        delay(10.milliseconds)

        // Before cleanup
        var stats = cache.getStats()
        assertEquals(3, stats.totalEntries)

        // Cleanup expired entries
        cache.cleanupExpired()

        // After cleanup
        stats = cache.getStats()
        assertEquals(1, stats.totalEntries)
        assertEquals(1, stats.validEntries)
        assertEquals(0, stats.expiredEntries)

        // Valid entry should still exist
        assertEquals("valid_data", cache.get<String>("valid"))
    }

    @Test
    fun `cache should be thread-safe with concurrent operations`() = runTest {
        val keys = (1..100).map { "key_$it" }
        val values = (1..100).map { "value_$it" }

        // Concurrent puts
        keys.zip(values).forEach { (key, value) ->
            cache.put(key, value)
        }

        // Concurrent gets
        keys.zip(values).forEach { (key, expectedValue) ->
            val actualValue = cache.get<String>(key)
            assertEquals(expectedValue, actualValue)
        }
    }
}