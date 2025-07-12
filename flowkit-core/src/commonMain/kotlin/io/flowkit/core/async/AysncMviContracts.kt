package io.flowkit.core.async

import io.flowkit.core.*
import kotlinx.coroutines.flow.Flow
import kotlin.time.Clock
import kotlin.time.ExperimentalTime

/**
 * 🚀 Enhanced MVI Reducer with built-in async handling - Generic over result types
 * Eliminates circular dependency between core and network modules
 */
interface AsyncMviReducer<State : MviState, Intent : MviIntent, SideEffect : MviSideEffect, AsyncResult> :
    MviReducer<State, Intent, SideEffect> {

    /**
     * Handle async operations directly in reducer with automatic state management
     * FlowKit automatically handles loading states, async operations, and error handling
     *
     * @param AsyncResult The type of async operation result (e.g., NetworkResult<T>, DatabaseResult<T>)
     */
    suspend fun reduceAsync(
        currentState: State,
        intent: Intent
    ): AsyncReducerResult<State, SideEffect, AsyncResult>

    /**
     * Legacy support - delegates to reduceAsync by default
     * Most implementations should override reduceAsync instead
     */
    override suspend fun reduce(
        currentState: State,
        intent: Intent
    ): ReducerResult<State, SideEffect> {
        val asyncResult = reduceAsync(currentState, intent)
        return when (asyncResult) {
            is AsyncReducerResult.Immediate -> ReducerResult(
                newState = asyncResult.newState,
                sideEffects = asyncResult.sideEffects
            )
            is AsyncReducerResult.Async -> ReducerResult(
                newState = asyncResult.loadingState,
                sideEffects = asyncResult.sideEffects
            )
        }
    }
}

/**
 * Enhanced result type that supports both immediate and async operations
 * Generic over AsyncResult type to avoid circular dependencies
 */
sealed class AsyncReducerResult<State : MviState, SideEffect : MviSideEffect, AsyncResult> {

    /**
     * Immediate state change - no async operation needed
     * Use for simple state updates, validation errors, etc.
     */
    data class Immediate<State : MviState, SideEffect : MviSideEffect, AsyncResult>(
        val newState: State,
        val sideEffects: List<SideEffect> = emptyList()
    ) : AsyncReducerResult<State, SideEffect, AsyncResult>()

    /**
     * Async operation with automatic state management
     * FlowKit handles loading→success/error transitions automatically
     */
    data class Async<State : MviState, SideEffect : MviSideEffect, AsyncResult>(
        val loadingState: State,
        val operation: suspend () -> Flow<AsyncResult>,
        val onResult: (State, AsyncResult) -> State,
        val sideEffects: List<SideEffect> = emptyList(),
        val operationId: String = generateOperationId()
    ) : AsyncReducerResult<State, SideEffect, AsyncResult>()
}

/**
 * Auto-generated side effects for async operations
 * FlowKit creates these automatically - no boilerplate needed
 */
sealed class AsyncSideEffect : MviSideEffect {
    data class AsyncOperationStarted(val operationId: String) : AsyncSideEffect()
    data class AsyncOperationCompleted(val operationId: String) : AsyncSideEffect()
    data class AsyncOperationFailed(val operationId: String, val error: String) : AsyncSideEffect()
}

/**
 * Builder for creating AsyncReducerResult.Async with fluent API
 */
class AsyncOperationBuilder<State : MviState, SideEffect : MviSideEffect, AsyncResult>(
    private val loadingState: State
) {
    private var operation: (suspend () -> Flow<AsyncResult>)? = null
    private var onResult: ((State, AsyncResult) -> State)? = null
    private var sideEffects: List<SideEffect> = emptyList()
    private var operationId: String = generateOperationId()

    /**
     * Set the async operation to execute
     */
    fun operation(op: suspend () -> Flow<AsyncResult>): AsyncOperationBuilder<State, SideEffect, AsyncResult> {
        this.operation = op
        return this
    }

    /**
     * Define how to handle the operation result
     */
    fun onResult(handler: (State, AsyncResult) -> State): AsyncOperationBuilder<State, SideEffect, AsyncResult> {
        this.onResult = handler
        return this
    }

    /**
     * Add side effects to emit during operation
     */
    fun withSideEffects(vararg effects: SideEffect): AsyncOperationBuilder<State, SideEffect, AsyncResult> {
        this.sideEffects = effects.toList()
        return this
    }

    /**
     * Set custom operation ID for tracking
     */
    fun withOperationId(id: String): AsyncOperationBuilder<State, SideEffect, AsyncResult> {
        this.operationId = id
        return this
    }

    /**
     * Build the async result
     */
    fun build(): AsyncReducerResult.Async<State, SideEffect, AsyncResult> {
        require(operation != null) { "Operation must be set" }
        require(onResult != null) { "Result handler must be set" }

        return AsyncReducerResult.Async(
            loadingState = loadingState,
            operation = operation!!,
            onResult = onResult!!,
            sideEffects = sideEffects,
            operationId = operationId
        )
    }
}


@OptIn(ExperimentalTime::class)
private fun generateOperationId(): String {
    return "op_${Clock.System.now().toEpochMilliseconds()}_${(1000..9999).random()}"
}