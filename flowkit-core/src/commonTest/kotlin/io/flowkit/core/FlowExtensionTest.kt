package io.flowkit.core

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.asFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.milliseconds

@OptIn(ExperimentalCoroutinesApi::class)
class FlowExtensionsTest {

    @Test
    fun `asUiState should emit Loading then Success`() = runTest {
        val sourceFlow = flowOf("test data")
        val uiStates = sourceFlow.asUiState().toList()

        assertEquals(2, uiStates.size)
        assertTrue(uiStates[0] is UiState.Loading)
        assertTrue(uiStates[1] is UiState.Success)
        assertEquals("test data", (uiStates[1] as UiState.Success).data)
    }

    @Test
    fun `asUiState should emit Loading then Error on exception`() = runTest {
        val sourceFlow = flow<String> {
            throw RuntimeException("Test error")
        }
        val uiStates = sourceFlow.asUiState().toList()

        assertEquals(2, uiStates.size)
        assertTrue(uiStates[0] is UiState.Loading)
        assertTrue(uiStates[1] is UiState.Error)
        assertEquals("Test error", (uiStates[1] as UiState.Error).message)
    }

    @Test
    fun `mapData should transform Success data`() = runTest {
        val sourceFlow = flowOf(UiState.Success(5))
        val mappedFlow = sourceFlow.mapData { it * 2 }
        val result = mappedFlow.first()

        assertTrue(result is UiState.Success)
        assertEquals(10, result.data)
    }

    @Test
    fun `mapData should preserve Loading and Error states`() = runTest {
        val states = listOf<UiState<Int>>(
            UiState.Loading,
            UiState.Error(RuntimeException("Test")),
            UiState.Success(5)
        )

        val mappedStates = states.asFlow()
            .mapData { it * 2 }
            .toList()

        assertEquals(3, mappedStates.size)
        assertTrue(mappedStates[0] is UiState.Loading)
        assertTrue(mappedStates[1] is UiState.Error)
        assertTrue(mappedStates[2] is UiState.Success)
        assertEquals(10, (mappedStates[2] as UiState.Success).data)
    }

    @Test
    fun `onSuccess should only trigger for Success states`() = runTest {
        var successCount = 0
        val states = listOf<UiState<String>>(
            UiState.Loading,
            UiState.Success("data1"),
            UiState.Error(RuntimeException()),
            UiState.Success("data2")
        )

        states.asFlow()
            .onSuccess { successCount++ }
            .toList()

        assertEquals(2, successCount)
    }

    @Test
    fun `onError should only trigger for Error states`() = runTest {
        var errorCount = 0
        val states = listOf<UiState<String>>(
            UiState.Loading,
            UiState.Success("data"),
            UiState.Error(RuntimeException("error1")),
            UiState.Error(RuntimeException("error2"))
        )

        states.asFlow()
            .onError { errorCount++ }
            .toList()

        assertEquals(2, errorCount)
    }

    @Test
    fun `retryWithBackoff should retry on failure`() = runTest {
        var attemptCount = 0
        val flow = flow {
            attemptCount++
            if (attemptCount < 3) {
                throw RuntimeException("Attempt $attemptCount failed")
            }
            emit("Success on attempt $attemptCount")
        }

        val result = flow
            .retryWithBackoff(
                maxAttempts = 3,
                initialDelay = 10.milliseconds
            )
            .first()

        assertEquals("Success on attempt 3", result)
        assertEquals(3, attemptCount)
    }

    @Test
    fun `retryWithBackoff should stop after maxAttempts`() = runTest {
        var attemptCount = 0
        val flow = flow<String> {
            attemptCount++
            throw RuntimeException("Always fails")
        }

        try {
            flow.retryWithBackoff(
                maxAttempts = 2,
                initialDelay = 10.milliseconds
            ).first()
        } catch (e: RuntimeException) {
            assertEquals("Always fails", e.message)
        }

        assertEquals(3, attemptCount) // Initial attempt + 2 retries
    }

    @Test
    fun `flowBuilder should create flow with all configurations`() = runTest {
        var sourceCallCount = 0
        var errorHandlerCalled = false

        val flow = flowBuilder<String> {
            source {
                sourceCallCount++
                if (sourceCallCount == 1) throw RuntimeException("First attempt fails")
                "Success"
            }
            retryOnFailure(maxAttempts = 2, initialDelay = 10.milliseconds)
            onError { errorHandlerCalled = true }
            ignoreRepeatedValues()
        }

        val result = flow.first()
        assertEquals("Success", result)
        assertEquals(2, sourceCallCount) // First failed, second succeeded
    }

    @Test
    fun `flowBuilder should throw when source not set`() = runTest {
        try {
            flowBuilder<String> {
                retryOnFailure(maxAttempts = 2)
            }.first()
            kotlin.test.fail("Should have thrown IllegalStateException")
        } catch (e: IllegalStateException) {
            assertTrue(e.message?.contains("Source flow must be set") == true)
        }
    }

    @Test
    fun `ignoreRepeatedValues should work like distinctUntilChanged`() = runTest {
        val sourceValues = listOf(1, 1, 2, 2, 2, 3, 1)
        val distinctValues = sourceValues.asFlow()
            .ignoreRepeatedValues()
            .toList()

        assertEquals(listOf(1, 2, 3, 1), distinctValues)
    }

    @Test
    fun `safeCollect should handle errors gracefully`() = runTest {
        var errorCaught: Throwable? = null
        val values = mutableListOf<String>()

        val flow = flow {
            emit("value1")
            emit("value2")
            throw RuntimeException("Test error")
        }

        flow.safeCollect(
            onError = { errorCaught = it },
            onEach = { values.add(it) }
        )

        assertEquals(listOf("value1", "value2"), values)
        assertTrue(errorCaught is RuntimeException)
        assertEquals("Test error", errorCaught?.message)
    }
}