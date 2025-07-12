package io.flowkit.storage

import io.flowkit.core.UiState
import io.flowkit.core.dataOrNull
import io.flowkit.core.isError
import io.flowkit.core.isLoading
import io.flowkit.core.isSuccess
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.asFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.minutes

@OptIn(ExperimentalCoroutinesApi::class)
class StorageTest {

    private lateinit var storage: SimpleMemoryStorage

    @BeforeTest
    fun setup() {
        storage = SimpleMemoryStorage()
    }

    @Test
    fun `should store and retrieve data`() = runTest {
        val key = "test_key"
        val value = "test_value"

        val putResult = storage.put(key, value)
        assertTrue(putResult.isSuccess)

        val getResult = storage.get<String>(key)
        assertTrue(getResult.isSuccess)
        assertEquals(value, getResult.dataOrNull())
    }

    @Test
    fun `should return null for non-existent keys`() = runTest {
        val result = storage.get<String>("non_existent_key")
        assertTrue(result.isSuccess)
        assertNull(result.dataOrNull())
    }

    @Test
    fun `should handle different data types`() = runTest {
        // String
        storage.putString("string_key", "test")
        val stringResult = storage.getString("string_key")
        assertEquals("test", stringResult.dataOrNull())

        // Int
        storage.putInt("int_key", 42)
        val intResult = storage.getInt("int_key")
        assertEquals(42, intResult.dataOrNull())

        // Boolean
        storage.putBoolean("bool_key", true)
        val boolResult = storage.getBoolean("bool_key")
        assertEquals(true, boolResult.dataOrNull())

        // Custom object
        data class TestData(val name: String, val value: Int)

        val customData = TestData("test", 123)
        storage.put("custom_key", customData)
        val customResult = storage.get<TestData>("custom_key")
        assertEquals(customData, customResult.dataOrNull())
    }

    @Test
    fun `should support expiration`() = runTest {
        val key = "expiring_key"
        val value = "expiring_value"
        val config = StorageConfig(expirationTime = 50.milliseconds)

        storage.put(key, value, config)

        // Should be available immediately
        val immediateResult = storage.get<String>(key)
        assertEquals(value, immediateResult.dataOrNull())

        // Wait for expiration
        kotlinx.coroutines.delay(100.milliseconds)

        // Should be expired now
        val expiredResult = storage.get<String>(key)
        assertNull(expiredResult.dataOrNull())
    }

    @Test
    fun `should emit change events`() = runTest {
        val key = "change_key"
        val events = mutableListOf<StorageChangeEvent<Any?>>()

        val job = launch {
            storage.observeChanges<Any?>().take(3).toList(events)
        }

        // Insert
        storage.put(key, "value1")

        // Update
        storage.put(key, "value2")

        // Delete
        storage.remove(key)

        job.join()

        assertEquals(3, events.size)
        assertEquals(StorageOperation.INSERT, events[0].operation)
        assertEquals(StorageOperation.UPDATE, events[1].operation)
        assertEquals(StorageOperation.DELETE, events[2].operation)
    }

    @Test
    fun `should observe key changes`() = runTest {
        val key = "observe_key"
        val values = mutableListOf<String?>()

        val job = launch {
            storage.observe<String>(key).take(3).collect { result ->
                values.add(result.dataOrNull())
            }
        }

        kotlinx.coroutines.delay(10) // Let observer start

        storage.put(key, "value1")
        storage.put(key, "value2")

        job.join()

        assertEquals(3, values.size)
        assertNull(values[0]) // Initial null value
        assertEquals("value1", values[1])
        assertEquals("value2", values[2])
    }

    @Test
    fun `should handle contains operation`() = runTest {
        val key = "contains_key"

        // Should not contain initially
        val initialResult = storage.contains(key)
        assertFalse(initialResult.dataOrNull() ?: true)

        // Should contain after putting
        storage.put(key, "value")
        val afterPutResult = storage.contains(key)
        assertTrue(afterPutResult.dataOrNull() ?: false)

        // Should not contain after removal
        storage.remove(key)
        val afterRemoveResult = storage.contains(key)
        assertFalse(afterRemoveResult.dataOrNull() ?: true)
    }

    @Test
    fun `should get all keys`() = runTest {
        storage.put("key1", "value1")
        storage.put("key2", "value2")
        storage.put("key3", "value3")

        val keysResult = storage.getAllKeys()
        val keys = keysResult.dataOrNull()

        assertNotNull(keys)
        assertEquals(3, keys.size)
        assertTrue(keys.contains("key1"))
        assertTrue(keys.contains("key2"))
        assertTrue(keys.contains("key3"))
    }

    @Test
    fun `should clear all data`() = runTest {
        storage.put("key1", "value1")
        storage.put("key2", "value2")

        val clearResult = storage.clear()
        assertTrue(clearResult.isSuccess)

        val keysResult = storage.getAllKeys()
        val keys = keysResult.dataOrNull()
        assertNotNull(keys)
        assertTrue(keys.isEmpty())
    }

    @Test
    fun `should calculate storage size`() = runTest {
        val sizeResult = storage.getSize()
        assertTrue(sizeResult.isSuccess)

        val initialSize = sizeResult.dataOrNull() ?: 0L

        storage.put("size_key", "test_value")

        val newSizeResult = storage.getSize()
        val newSize = newSizeResult.dataOrNull() ?: 0L

        assertTrue(newSize > initialSize)
    }

    @Test
    fun `should clean up expired entries`() = runTest {
        val config = StorageConfig(expirationTime = 50.milliseconds)

        storage.put("expiring1", "value1", config)
        storage.put("expiring2", "value2", config)
        storage.put("persistent", "value3") // No expiration

        // Wait for expiration
        kotlinx.coroutines.delay(100.milliseconds)

        val cleanupResult = storage.cleanupExpired()
        assertEquals(2, cleanupResult.dataOrNull()) // Should have cleaned 2 entries

        val keysResult = storage.getAllKeys()
        val keys = keysResult.dataOrNull()
        assertEquals(1, keys?.size) // Only persistent key should remain
        assertTrue(keys?.contains("persistent") ?: false)
    }
}

@OptIn(ExperimentalCoroutinesApi::class)
class StorageFlowTest {

    @Test
    fun `storageFlow should work with simple operations`() = runTest {
        val flow = storageFlow<String> {
            source { "test_data" }
        }

        val results = flow.toList()

        assertEquals(2, results.size)
        assertTrue(results[0].isLoading)
        assertTrue(results[1].isSuccess)
        assertEquals("test_data", results[1].dataOrNull())
    }

    @Test
    fun `storageFlow should handle errors with fallback`() = runTest {
        val flow = storageFlow<String> {
            source { throw StorageError.KeyNotFound("test_key") }
            fallbackTo("fallback_value")
        }

        val results = flow.toList()

        assertTrue(results.last().isSuccess)
        assertEquals("fallback_value", results.last().dataOrNull())
    }

    @Test
    fun `storageFlow should retry on failure`() = runTest {
        var attemptCount = 0
        val flow = storageFlow<String> {
            source {
                attemptCount++
                if (attemptCount < 3) {
                    throw StorageError.DatabaseError("Attempt $attemptCount failed")
                }
                "Success on attempt $attemptCount"
            }
            retryOnFailure(maxAttempts = 3)
        }

        val results = flow.toList()

        assertTrue(results.last().isSuccess)
        assertEquals("Success on attempt 3", results.last().dataOrNull())
        assertEquals(3, attemptCount)
    }

    @Test
    fun `cachedStorageFlow should use cache`() = runTest {
        val cacheStorage = SimpleCacheStorage()
        val cacheKey = "cache_test"

        // Pre-populate cache
        cacheStorage.putWithTtl(cacheKey, "cached_data", 1.minutes)

        val flow = cachedStorageFlow(
            key = cacheKey,
            operation = { "fresh_data" },
            cacheStorage = cacheStorage
        )

        val result = flow.first()

        assertTrue(result.isSuccess)
        assertEquals("cached_data", result.dataOrNull())
    }

    @Test
    fun `storageUiFlow should convert to UiState`() = runTest {
        val flow = storageUiFlow<String> {
            source { "ui_data" }
        }

        val results = flow.toList()

        assertEquals(2, results.size)
        assertTrue(results[0] is UiState.Loading)
        assertTrue(results[1] is UiState.Success)
        assertEquals("ui_data", (results[1] as UiState.Success).data)
    }
}

@OptIn(ExperimentalCoroutinesApi::class)
class StorageExtensionsTest {

    @Test
    fun `should map storage data correctly`() = runTest {
        val flow = flowOf(StorageResultFactory.success(5))

        val mapped = flow.mapStorageData { it * 2 }
        val result = mapped.first()

        assertTrue(result.isSuccess)
        assertEquals(10, result.dataOrNull())
    }

    @Test
    fun `should combine storage flows correctly`() = runTest {
        val flow1 = flowOf(StorageResultFactory.success("Hello"))
        val flow2 = flowOf(StorageResultFactory.success("World"))

        val combined = combineStorageFlows(flow1, flow2) { data1, data2 ->
            "$data1 $data2"
        }

        val result = combined.first()
        assertTrue(result.isSuccess)
        assertEquals("Hello World", result.dataOrNull())
    }

    @Test
    fun `should filter storage success results`() = runTest {
        val flows = listOf(
            StorageResultFactory.success(1),
            StorageResultFactory.success(5),
            StorageResultFactory.success(3),
            StorageResultFactory.failure<Int>(StorageError.KeyNotFound("test"))
        )

        val filtered = flows.asFlow().filterStorageSuccess { it > 3 }
        val results = filtered.toList()

        // Should pass through the success with 5 and the error
        assertEquals(2, results.size)
        assertTrue(results[0].isSuccess)
        assertEquals(5, results[0].dataOrNull())
        assertTrue(results[1].isError)
    }

    @Test
    fun `should observe key safely with default value`() = runTest {
        val storage = SimpleMemoryStorage()
        val defaultValue = "default"

        val values = mutableListOf<String>()
        val job = launch {
            storage.observeKeySafe("test_key", defaultValue).take(3).toList(values)
        }

        kotlinx.coroutines.delay(10) // Let observer start
        storage.put("test_key", "value1")
        storage.put("test_key", "value2")

        job.join()

        assertEquals(3, values.size)
        assertEquals(defaultValue, values[0]) // Initial default
        assertEquals("value1", values[1])
        assertEquals("value2", values[2])
    }
}

@OptIn(ExperimentalCoroutinesApi::class)
class StoragePreferencesTest {

    @Test
    fun `should create and use string preference`() = runTest {
        val storage = SimpleMemoryStorage()
        val prefs = storage.preferences { }

        val stringPref = prefs.stringPreference("test_string", "default")

        // Should return default initially
        assertEquals("default", stringPref.get())

        // Should store and retrieve value
        stringPref.set("new_value")
        assertEquals("new_value", stringPref.get())

        // Should observe changes
        val values = mutableListOf<String>()
        val job = launch {
            stringPref.observe().take(2).toList(values)
        }

        kotlinx.coroutines.delay(10)
        stringPref.set("observed_value")

        job.join()
        assertEquals(2, values.size)
        assertEquals("observed_value", values[1])
    }

    @Test
    fun `should create and use int preference`() = runTest {
        val storage = SimpleMemoryStorage()
        val prefs = storage.preferences { }

        val intPref = prefs.intPreference("test_int", 0)

        assertEquals(0, intPref.get())

        intPref.set(42)
        assertEquals(42, intPref.get())

        assertTrue(intPref.exists())

        intPref.remove()
        assertFalse(intPref.exists())
        assertEquals(0, intPref.get()) // Should return default after removal
    }

    @Test
    fun `should create and use boolean preference`() = runTest {
        val storage = SimpleMemoryStorage()
        val prefs = storage.preferences { }

        val boolPref = prefs.booleanPreference("test_bool", false)

        assertFalse(boolPref.get())

        boolPref.set(true)
        assertTrue(boolPref.get())
    }

    @Test
    fun `should handle all primitive preference types`() = runTest {
        val storage = SimpleMemoryStorage()
        val prefs = storage.preferences { }

        // Test all types
        val stringPref = prefs.stringPreference("string", "default")
        val intPref = prefs.intPreference("int", 0)
        val longPref = prefs.longPreference("long", 0L)
        val floatPref = prefs.floatPreference("float", 0f)
        val doublePref = prefs.doublePreference("double", 0.0)
        val boolPref = prefs.booleanPreference("bool", false)

        // Set values
        stringPref.set("test")
        intPref.set(42)
        longPref.set(123L)
        floatPref.set(3.14f)
        doublePref.set(2.718)
        boolPref.set(true)

        // Verify values
        assertEquals("test", stringPref.get())
        assertEquals(42, intPref.get())
        assertEquals(123L, longPref.get())
        assertEquals(3.14f, floatPref.get())
        assertEquals(2.718, doublePref.get())
        assertTrue(boolPref.get())
    }
}

@OptIn(ExperimentalCoroutinesApi::class)
class StorageBindingTest {

    @Test
    fun `should create two-way binding`() = runTest {
        val storage = SimpleMemoryStorage()
        val binding = storage.createBinding("binding_key", "initial")

        // Should have initial value
        assertEquals("initial", binding.value.value)

        // Should update when setValue is called
        binding.setValue("new_value")
        assertEquals("new_value", binding.value.value)

        // Should observe external changes
        storage.put("binding_key", "external_value")

        val observedValues = mutableListOf<String>()
        val job = launch {
            binding.observeValue().take(2).toList(observedValues)
        }

        kotlinx.coroutines.delay(10)
        storage.put("binding_key", "another_external")

        job.join()
        assertTrue(observedValues.contains("another_external"))
    }
}

@OptIn(ExperimentalCoroutinesApi::class)
class CacheStorageTest {

    @Test
    fun `should handle TTL correctly`() = runTest {
        val cache = SimpleCacheStorage()
        val key = "ttl_key"
        val value = "ttl_value"
        val shortTtl = 50.milliseconds

        // Put with TTL
        val putResult = cache.putWithTtl(key, value, shortTtl)
        assertTrue(putResult.isSuccess)

        // Should be available immediately
        val immediateResult = cache.getIfValid<String>(key)
        assertEquals(value, immediateResult.dataOrNull())

        // Should not be expired yet
        val notExpiredResult = cache.isExpired(key)
        assertFalse(notExpiredResult.dataOrNull() ?: true)

        // Wait for expiration
        kotlinx.coroutines.delay(100.milliseconds)

        // Should be expired now
        val expiredResult = cache.isExpired(key)
        assertTrue(expiredResult.dataOrNull() ?: false)

        // Should return null for expired data
        val expiredGetResult = cache.getIfValid<String>(key)
        assertNull(expiredGetResult.dataOrNull())
    }

    @Test
    fun `should cleanup expired entries`() = runTest {
        val cache = SimpleCacheStorage()
        val shortTtl = 50.milliseconds

        // Add some entries with short TTL
        cache.putWithTtl("expired1", "value1", shortTtl)
        cache.putWithTtl("expired2", "value2", shortTtl)
        cache.put("persistent", "value3") // No TTL

        // Wait for expiration
        kotlinx.coroutines.delay(100.milliseconds)

        // Cleanup expired
        val cleanupResult = cache.cleanupExpired()
        val cleanedCount = cleanupResult.dataOrNull() ?: 0

        assertTrue(cleanedCount >= 2) // At least 2 should be cleaned

        // Persistent should still exist
        val persistentResult = cache.get<String>("persistent")
        assertEquals("value3", persistentResult.dataOrNull())
    }
}

@OptIn(ExperimentalCoroutinesApi::class)
class StorageRepositoryTest {

    private class TestRepository(storage: ReactiveStorage) : StorageRepository(storage) {

        fun loadUser(userId: String): Flow<UiState<String>> {
            return loadCached("user_$userId", {
                // Simulate API call
                "User data for $userId"
            })
        }

        suspend fun saveUser(userId: String, userData: String): StorageResult<Unit> {
            return save("user_$userId", userData)
        }

        fun observeUser(userId: String): Flow<String> {
            return observe("user_$userId", "Default User")
        }

        suspend fun clearAllUsers(): StorageResult<Unit> {
            return clearAll()
        }
    }

    @Test
    fun `repository should load and cache data`() = runTest {
        val storage = SimpleMemoryStorage()
        val repository = TestRepository(storage)

        val userFlow = repository.loadUser("123")
        val result = userFlow.first()

        assertTrue(result is UiState.Success)
        assertEquals("User data for 123", result.data)
    }

    @Test
    fun `repository should save data`() = runTest {
        val storage = SimpleMemoryStorage()
        val repository = TestRepository(storage)

        val saveResult = repository.saveUser("123", "John Doe")
        assertTrue(saveResult.isSuccess)

        // Verify it was saved
        val getResult = storage.get<String>("user_123")
        assertEquals("John Doe", getResult.dataOrNull())
    }

    @Test
    fun `repository should observe changes`() = runTest {
        val storage = SimpleMemoryStorage()
        val repository = TestRepository(storage)

        val values = mutableListOf<String>()
        val job = launch {
            repository.observeUser("123").take(3).toList(values)
        }

        kotlinx.coroutines.delay(10)

        repository.saveUser("123", "John")
        repository.saveUser("123", "Jane")

        job.join()

        assertEquals(3, values.size)
        assertEquals("Default User", values[0]) // Initial default
        assertEquals("John", values[1])
        assertEquals("Jane", values[2])
    }

    @Test
    fun `repository should clear all data`() = runTest {
        val storage = SimpleMemoryStorage()
        val repository = TestRepository(storage)

        // Add some data
        repository.saveUser("123", "John")
        repository.saveUser("456", "Jane")

        // Clear all
        val clearResult = repository.clearAllUsers()
        assertTrue(clearResult.isSuccess)

        // Verify cleared
        val keysResult = storage.getAllKeys()
        val keys = keysResult.dataOrNull()
        assertTrue(keys?.isEmpty() ?: false)
    }
}