package io.flowkit.network

import io.flowkit.core.UiState
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.milliseconds

@OptIn(ExperimentalCoroutinesApi::class)
class NetworkFlowTest {

    @Test
    fun `networkFlow should emit Loading then Success`() = runTest {
        val flow = networkFlow<String> {
            source { "test data" }
        }

        val results = flow.toList()

        assertEquals(2, results.size)
        assertTrue(results[0].isLoading)
        assertTrue(results[1].isSuccess)
        assertEquals("test data", results[1].dataOrNull())
    }

    @Test
    fun `networkFlow should emit Loading then Error on exception`() = runTest {
        val flow = networkFlow<String> {
            source { throw RuntimeException("Test error") }
        }

        val results = flow.toList()

        assertEquals(2, results.size)
        assertTrue(results[0].isLoading)
        assertTrue(results[1].isError)

        val error = results[1].errorOrNull()
        assertTrue(error is NetworkError.UnknownError)
        assertEquals("Test error", error.message)
    }

    @Test
    fun `networkFlow should handle NetworkError correctly`() = runTest {
        val flow = networkFlow<String> {
            source { throw NetworkError.NoInternetConnection }
        }

        val results = flow.toList()

        assertEquals(2, results.size)
        assertTrue(results[0].isLoading)
        assertTrue(results[1].isError)

        val error = results[1].errorOrNull()
        assertTrue(error is NetworkError.NoInternetConnection)
    }

    @Test
    fun `networkFlow with retry should retry on failure`() = runTest {
        var attemptCount = 0
        val flow = networkFlow<String> {
            source {
                attemptCount++
                if (attemptCount < 3) {
                    throw NetworkError.TimeoutError("Timeout on attempt $attemptCount")
                }
                "Success on attempt $attemptCount"
            }
            retryOnFailure(maxAttempts = 3, initialDelay = 10.milliseconds)
        }

        val results = flow.toList()

        // Should have multiple loading states due to retries
        assertTrue(results.any { it is NetworkResult.Loading })
        assertTrue(results.last() is NetworkResult.Success)
        assertEquals("Success on attempt 3", (results.last() as NetworkResult.Success).data)
        assertEquals(3, attemptCount)
    }

    @Test
    fun `networkFlow with cache should use cached data`() = runTest {
        val cache = SimpleNetworkCache()
        val cacheKey = "test_key"

        // Pre-populate cache
        cache.put(cacheKey, "cached data")

        val flow = networkFlow<String> {
            source { "fresh data" }
            cache(cacheKey, CacheStrategy.STALE_WHILE_REVALIDATE)
            withDependencies(cache = cache)
        }

        val results = flow.toList()

        // Should get both cached and fresh data
        assertTrue(results.any { it is NetworkResult.Success })
        val successResults = results.filterIsInstance<NetworkResult.Success<String>>()
        assertTrue(successResults.any { it.data == "cached data" })
    }

    @Test
    fun `asUiState should convert NetworkResult to UiState`() = runTest {
        val networkFlow = flowOf(
            NetworkResult.Loading,
            NetworkResult.Success("test data"),
            NetworkResult.Error(NetworkError.NoInternetConnection)
        )

        val uiStates = networkFlow.asUiState().toList()

        assertEquals(3, uiStates.size)
        assertTrue(uiStates[0] is UiState.Loading)
        assertTrue(uiStates[1] is UiState.Success)
        assertTrue(uiStates[2] is UiState.Error)

        assertEquals("test data", (uiStates[1] as UiState.Success).data)
    }

    @Test
    fun `onNetworkSuccess should trigger on successful results`() = runTest {
        var successCount = 0
        val flow = flowOf(
            NetworkResult.Loading,
            NetworkResult.Success("data1"),
            NetworkResult.Error(NetworkError.NoInternetConnection),
            NetworkResult.Success("data2")
        )

        flow.onNetworkSuccess { successCount++ }.toList()

        assertEquals(2, successCount)
    }

    @Test
    fun `onNetworkError should trigger on error results`() = runTest {
        var errorCount = 0
        val flow = flowOf(
            NetworkResult.Loading,
            NetworkResult.Success("data"),
            NetworkResult.Error(NetworkError.NoInternetConnection),
            NetworkResult.Error(NetworkError.TimeoutError())
        )

        flow.onNetworkError { errorCount++ }.toList()

        assertEquals(2, errorCount)
    }

    @Test
    fun `mapNetworkData should transform successful data`() = runTest {
        val flow = flowOf(
            NetworkResult.Loading,
            NetworkResult.Success(5),
            NetworkResult.Error(NetworkError.NoInternetConnection)
        )

        val mappedFlow = flow.mapNetworkData { it * 2 }
        val results = mappedFlow.toList()

        assertEquals(3, results.size)
        assertTrue(results[0] is NetworkResult.Loading)
        assertTrue(results[1] is NetworkResult.Success)
        assertTrue(results[2] is NetworkResult.Error)

        assertEquals(10, (results[1] as NetworkResult.Success).data)
    }

    @Test
    fun `combineNetworkFlows should combine successful results`() = runTest {
        val flow1 = flowOf(NetworkResult.Success("Hello"))
        val flow2 = flowOf(NetworkResult.Success("World"))

        val combined = combineNetworkFlows(flow1, flow2) { data1, data2 ->
            "$data1 $data2"
        }

        val result = combined.first()
        assertTrue(result is NetworkResult.Success)
        assertEquals("Hello World", result.data)
    }

    @Test
    fun `combineNetworkFlows should emit Loading if any flow is loading`() = runTest {
        val flow1 = flowOf(NetworkResult.Loading)
        val flow2 = flowOf(NetworkResult.Success("World"))

        val combined = combineNetworkFlows(flow1, flow2) { data1, data2 ->
            "$data1 $data2"
        }

        val result = combined.first()
        assertTrue(result is NetworkResult.Loading)
    }

    @Test
    fun `combineNetworkFlows should emit Error if any flow has error`() = runTest {
        val flow1 = flowOf(NetworkResult.Error(NetworkError.NoInternetConnection))
        val flow2 = flowOf(NetworkResult.Success("World"))

        val combined = combineNetworkFlows(flow1, flow2) { data1, data2 ->
            "$data1 $data2"
        }

        val result = combined.first()
        assertTrue(result is NetworkResult.Error)
        assertTrue(result.error is NetworkError.NoInternetConnection)
    }

    @Test
    fun `simpleNetworkFlow should work as convenience function`() = runTest {
        val flow = simpleNetworkFlow { "simple data" }
        val results = flow.toList()

        assertEquals(2, results.size)
        assertTrue(results[0] is NetworkResult.Loading)
        assertTrue(results[1] is NetworkResult.Success)
        assertEquals("simple data", (results[1] as NetworkResult.Success).data)
    }

    @Test
    fun `networkUiFlow should return UiState directly`() = runTest {
        val flow = networkUiFlow<String> {
            source { "ui data" }
        }

        val results = flow.toList()

        assertEquals(2, results.size)
        assertTrue(results[0] is UiState.Loading)
        assertTrue(results[1] is UiState.Success)
        assertEquals("ui data", (results[1] as UiState.Success).data)
    }

    @Test
    fun `networkFlow builder should throw when source not set`() = runTest {
        assertFailsWith<IllegalStateException> {
            networkFlow<String> {
                // No source set
                retryOnFailure(maxAttempts = 2)
            }.first()
        }
    }

    @Test
    fun `offlineFallback should use cached data when offline`() = runTest {
        val cache = SimpleNetworkCache()
        val connectivityMonitor = MockConnectivityMonitor(initialState = false) // Offline
        val cacheKey = "offline_key"

        // Pre-populate cache
        cache.put(cacheKey, "cached offline data")

        val flow = flowOf(NetworkResult.Success("online data"))
            .offlineFallback(cache, cacheKey, connectivityMonitor)

        val result = flow.first()
        assertTrue(result is NetworkResult.Success)
        assertEquals("cached offline data", result.data)
    }

    @Test
    fun `offlineFallback should fetch fresh data when online`() = runTest {
        val cache = SimpleNetworkCache()
        val connectivityMonitor = MockConnectivityMonitor(initialState = true) // Online
        val cacheKey = "online_key"

        val flow = flowOf(NetworkResult.Success("fresh online data"))
            .offlineFallback(cache, cacheKey, connectivityMonitor)

        val result = flow.first()
        assertTrue(result is NetworkResult.Success)
        assertEquals("fresh online data", result.data)
    }
}