package io.flowkit.core

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow

/**
 * Base interface for MVI State
 * All UI states should implement this interface
 */
interface MviState

/**
 * Base interface for MVI Intent
 * All user intents/actions should implement this interface
 */
interface MviIntent

/**
 * Base interface for MVI Side Effects
 * All one-time UI events should implement this interface
 */
interface MviSideEffect

/**
 * Result of a state reduction operation
 */
data class ReducerResult<State : MviState, SideEffect : MviSideEffect>(
    val newState: State,
    val sideEffects: List<SideEffect> = emptyList()
)

/**
 * Core reducer interface for MVI pattern
 * Handles state transitions based on intents
 */
interface MviReducer<State : MviState, Intent : MviIntent, SideEffect : MviSideEffect> {
    /**
     * Reduces the current state and intent to a new state and optional side effects
     * This function should be pure (no side effects) and deterministic
     */
    suspend fun reduce(
        currentState: State,
        intent: Intent
    ): ReducerResult<State, SideEffect>
}

/**
 * Container interface for MVI state management
 * Provides reactive streams for state and side effects
 */
interface MviContainer<State : MviState, Intent : MviIntent, SideEffect : MviSideEffect> {
    /**
     * Current state as a StateFlow
     * UI observes this for state changes
     */
    val state: StateFlow<State>

    /**
     * Side effects as a Flow
     * UI observes this for one-time events like navigation, toasts, etc.
     */
    val sideEffects: Flow<SideEffect>

    /**
     * Process an intent and update state accordingly
     * This is the single entry point for state changes
     */
    suspend fun processIntent(intent: Intent)

    /**
     * Get current state value synchronously
     * Useful for testing and synchronous operations
     */
    fun currentState(): State
}

/**
 * Builder interface for configuring MVI containers
 */
interface MviContainerBuilder<State : MviState, Intent : MviIntent, SideEffect : MviSideEffect> {
    /**
     * Set the initial state
     */
    fun initialState(state: State): MviContainerBuilder<State, Intent, SideEffect>

    /**
     * Set the reducer for handling state transitions
     */
    fun reducer(reducer: MviReducer<State, Intent, SideEffect>): MviContainerBuilder<State, Intent, SideEffect>

    /**
     * Enable debug logging (optional)
     */
    fun enableLogging(enabled: Boolean = true): MviContainerBuilder<State, Intent, SideEffect>

    /**
     * Build the MVI container
     */
    fun build(): MviContainer<State, Intent, SideEffect>
}