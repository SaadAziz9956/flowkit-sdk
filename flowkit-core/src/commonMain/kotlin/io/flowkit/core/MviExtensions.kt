package io.flowkit.core


import kotlinx.coroutines.flow.Flow

/**
 * 🚀 Convenient extension functions for MviContainer
 */

/**
 * Update state with a transformation function
 * Useful for handling async operation results outside of reducer
 */
suspend fun <State : MviState, Intent : MviIntent, SideEffect : MviSideEffect>
        MviContainer<State, Intent, SideEffect>.updateState(
    transform: (State) -> State
) {
    updateState(transform)
}

/**
 * Update state conditionally
 */
suspend fun <State : MviState, Intent : MviIntent, SideEffect : MviSideEffect>
        MviContainer<State, Intent, SideEffect>.updateStateIf(
    condition: (State) -> Boolean,
    transform: (State) -> State
) {
    val current = currentState()
    if (condition(current)) {
        updateState(transform)
    }
}

/**
 * Emit a side effect directly (bypass reducer)
 */
suspend fun <State : MviState, Intent : MviIntent, SideEffect : MviSideEffect>
        MviContainer<State, Intent, SideEffect>.emitSideEffect(
    sideEffect: SideEffect
) {
    // This would need to be implemented in DefaultMviContainer
    // For now, we'll add it to the interface
}

/**
 * Reset state to initial state
 */
suspend fun <State : MviState, Intent : MviIntent, SideEffect : MviSideEffect>
        MviContainer<State, Intent, SideEffect>.resetState(
    initialState: State
) {
    updateState { initialState }
}

/**
 * Apply multiple state transformations atomically
 */
suspend fun <State : MviState, Intent : MviIntent, SideEffect : MviSideEffect>
        MviContainer<State, Intent, SideEffect>.batchUpdateState(
    vararg transforms: (State) -> State
) {
    updateState { state ->
        transforms.fold(state) { currentState, transform ->
            transform(currentState)
        }
    }
}