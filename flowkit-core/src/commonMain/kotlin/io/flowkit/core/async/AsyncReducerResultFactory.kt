package io.flowkit.core.async

import io.flowkit.core.MviSideEffect
import io.flowkit.core.MviState

/**
 * Factory functions for creating AsyncReducerResult instances
 */
object AsyncReducerResultFactory {

    /**
     * Create immediate result
     */
    fun <State : MviState, SideEffect : MviSideEffect, AsyncResult> immediate(
        state: State,
        vararg sideEffects: SideEffect
    ): AsyncReducerResult.Immediate<State, SideEffect, AsyncResult> {
        return AsyncReducerResult.Immediate(state, sideEffects.toList())
    }

    /**
     * Create async result with builder pattern
     */
    fun <State : MviState, SideEffect : MviSideEffect, AsyncResult> async(
        loadingState: State,
        block: AsyncOperationBuilder<State, SideEffect, AsyncResult>.() -> Unit
    ): AsyncReducerResult.Async<State, SideEffect, AsyncResult> {
        return AsyncOperationBuilder<State, SideEffect, AsyncResult>(loadingState).apply(block).build()
    }
}

/**
 * DSL functions for cleaner syntax
 */
inline fun <State : MviState, SideEffect : MviSideEffect, AsyncResult> immediateResult(
    state: State,
    vararg sideEffects: SideEffect
): AsyncReducerResult.Immediate<State, SideEffect, AsyncResult> {
    return AsyncReducerResultFactory.immediate(state, *sideEffects)
}

inline fun <State : MviState, SideEffect : MviSideEffect, AsyncResult> asyncResult(
    loadingState: State,
    noinline block: AsyncOperationBuilder<State, SideEffect, AsyncResult>.() -> Unit
): AsyncReducerResult.Async<State, SideEffect, AsyncResult> {
    return AsyncReducerResultFactory.async(loadingState, block)
}