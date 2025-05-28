package io.flowkit.core

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

// Test MVI classes
sealed class TestState : MviState {
    data object Loading : TestState()
    data class Success(val data: String) : TestState()
    data class Error(val message: String) : TestState()
}

sealed class TestIntent : MviIntent {
    data object LoadData : TestIntent()
    data class UpdateData(val newData: String) : TestIntent()
    data object TriggerError : TestIntent()
}

sealed class TestSideEffect : MviSideEffect {
    data class ShowToast(val message: String) : TestSideEffect()
    data object NavigateBack : TestSideEffect()
}

class TestReducer : MviReducer<TestState, TestIntent, TestSideEffect> {
    override suspend fun reduce(
        currentState: TestState,
        intent: TestIntent
    ): ReducerResult<TestState, TestSideEffect> {
        return when (intent) {
            is TestIntent.LoadData -> {
                when (currentState) {
                    is TestState.Loading -> ReducerResult(
                        newState = TestState.Success("loaded data"),
                        sideEffects = listOf(TestSideEffect.ShowToast("Data loaded"))
                    )
                    else -> ReducerResult(
                        newState = TestState.Loading,
                        sideEffects = emptyList()
                    )
                }
            }
            is TestIntent.UpdateData -> {
                ReducerResult(
                    newState = TestState.Success(intent.newData),
                    sideEffects = listOf(TestSideEffect.ShowToast("Data updated"))
                )
            }
            is TestIntent.TriggerError -> {
                ReducerResult(
                    newState = TestState.Error("Something went wrong"),
                    sideEffects = listOf(TestSideEffect.NavigateBack)
                )
            }
        }
    }
}

@OptIn(ExperimentalCoroutinesApi::class)
class MviStateContainerTest {

    private val testDispatcher = StandardTestDispatcher()
    private val testScope = TestScope(testDispatcher)

    @Test
    fun `container should have initial state`() = runTest {
        val container = testScope.mviContainer(
            initialState = TestState.Loading,
            reducer = TestReducer()
        )

        assertEquals(TestState.Loading, container.currentState())
        assertEquals(TestState.Loading, container.state.first())
    }

    @Test
    fun `container should process intents and update state`() = runTest {
        val container = testScope.mviContainer(
            initialState = TestState.Loading,
            reducer = TestReducer()
        )

        // Process intent
        container.processIntent(TestIntent.LoadData)
        testDispatcher.scheduler.advanceUntilIdle()

        // Verify state changed
        assertEquals(TestState.Success("loaded data"), container.currentState())
    }

    @Test
    fun `container should emit side effects`() = runTest {
        val container = testScope.mviContainer(
            initialState = TestState.Loading,
            reducer = TestReducer()
        )

        val sideEffects = mutableListOf<TestSideEffect>()
        val job = launch {
            container.sideEffects.take(1).toList(sideEffects)
        }

        // Process intent that generates side effect
        container.processIntent(TestIntent.LoadData)
        testDispatcher.scheduler.advanceUntilIdle()

        job.cancel()

        // Verify side effect was emitted
        assertEquals(1, sideEffects.size)
        assertTrue(sideEffects.first() is TestSideEffect.ShowToast)
    }

    @Test
    fun `container should handle multiple intents in sequence`() = runTest {
        val container = testScope.mviContainer(
            initialState = TestState.Loading,
            reducer = TestReducer()
        )

        // Process multiple intents
        container.processIntent(TestIntent.LoadData)
        testDispatcher.scheduler.advanceUntilIdle()

        container.processIntent(TestIntent.UpdateData("new data"))
        testDispatcher.scheduler.advanceUntilIdle()

        // Verify final state
        assertEquals(TestState.Success("new data"), container.currentState())
    }

    @Test
    fun `container builder should work with DSL`() = runTest {
        val container = testScope.mviContainer<TestState, TestIntent, TestSideEffect>()
            .initialState(TestState.Loading)
            .reducer(TestReducer())
            .enableLogging(true)
            .build()

        assertEquals(TestState.Loading, container.currentState())
    }

    @Test
    fun `builder should throw when initial state not set`() = runTest {
        val builder = testScope.mviContainer<TestState, TestIntent, TestSideEffect>()
            .reducer(TestReducer())

        try {
            builder.build()
            kotlin.test.fail("Should have thrown IllegalStateException")
        } catch (e: IllegalStateException) {
            assertTrue(e.message?.contains("Initial state must be set") == true)
        }
    }

    @Test
    fun `builder should throw when reducer not set`() = runTest {
        val builder = testScope.mviContainer<TestState, TestIntent, TestSideEffect>()
            .initialState(TestState.Loading)

        try {
            builder.build()
            kotlin.test.fail("Should have thrown IllegalStateException")
        } catch (e: IllegalStateException) {
            assertTrue(e.message?.contains("Reducer must be set") == true)
        }
    }
}