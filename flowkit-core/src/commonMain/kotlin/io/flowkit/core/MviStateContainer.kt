package io.flowkit.core

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.onFailure
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.receiveAsFlow

/**
 * Default implementation of MviContainer
 * Optimized for performance and memory efficiency
 */
internal class DefaultMviContainer<State : MviState, Intent : MviIntent, SideEffect : MviSideEffect>(
    initialState: State,
    private val reducer: MviReducer<State, Intent, SideEffect>,
    private val scope: CoroutineScope,
    private val enableLogging: Boolean = false
) : MviContainer<State, Intent, SideEffect> {

    // State management - optimized for UI
    private val _state = MutableStateFlow(initialState)
    override val state: StateFlow<State> = _state.asStateFlow()

    // Side effects - optimized for one-time events
    private val _sideEffects = MutableSharedFlow<SideEffect>(
        replay = 0,
        extraBufferCapacity = 64,
        onBufferOverflow = BufferOverflow.DROP_OLDEST
    )
    override val sideEffects: Flow<SideEffect> = _sideEffects.asSharedFlow()

    // Intent processing - unbounded channel for UI events
    private val intentChannel = Channel<Intent>(Channel.UNLIMITED)

    init {
        // Process intents in order
        intentChannel.receiveAsFlow()
            .onEach { intent ->
                if (enableLogging) {
                    logIntent(intent, _state.value)
                }
                processIntentInternal(intent)
            }
            .catch { throwable ->
                // Handle any unexpected errors in intent processing
                logError("Error processing intent", throwable)
            }
            .launchIn(scope)
    }

    override suspend fun processIntent(intent: Intent) {
        intentChannel.trySend(intent).onFailure { throwable ->
            logError("Failed to send intent: $intent", throwable)
        }
    }

    override fun currentState(): State = _state.value

    private suspend fun processIntentInternal(intent: Intent) {
        try {
            val currentState = _state.value
            val result = reducer.reduce(currentState, intent)

            // Update state if it changed
            if (result.newState != currentState) {
                _state.value = result.newState

                if (enableLogging) {
                    logStateChange(currentState, result.newState, intent)
                }
            }

            // Emit side effects
            result.sideEffects.forEach { sideEffect ->
                _sideEffects.tryEmit(sideEffect)

                if (enableLogging) {
                    logSideEffect(sideEffect, intent)
                }
            }

        } catch (throwable: Throwable) {
            logError("Error in reducer for intent: $intent", throwable)
            // Don't crash the app, continue processing other intents
        }
    }

    private fun logIntent(intent: Intent, currentState: State) {
        println("🎯 FlowKit: Processing intent: ${intent::class.simpleName}")
        println("   Current state: ${currentState::class.simpleName}")
    }

    private fun logStateChange(oldState: State, newState: State, intent: Intent) {
        println("🔄 FlowKit: State changed")
        println("   Intent: ${intent::class.simpleName}")
        println("   ${oldState::class.simpleName} -> ${newState::class.simpleName}")
    }

    private fun logSideEffect(sideEffect: SideEffect, intent: Intent) {
        println("⚡ FlowKit: Side effect emitted")
        println("   Effect: ${sideEffect::class.simpleName}")
        println("   From intent: ${intent::class.simpleName}")
    }

    private fun logError(message: String, throwable: Throwable?) {
        println("❌ FlowKit Error: $message")
        throwable?.printStackTrace()
    }
}

/**
 * Builder implementation for MVI containers
 */
class DefaultMviContainerBuilder<State : MviState, Intent : MviIntent, SideEffect : MviSideEffect>(
    private val scope: CoroutineScope
) : MviContainerBuilder<State, Intent, SideEffect> {

    private var initialState: State? = null
    private var reducer: MviReducer<State, Intent, SideEffect>? = null
    private var enableLogging: Boolean = false

    override fun initialState(state: State): MviContainerBuilder<State, Intent, SideEffect> {
        this.initialState = state
        return this
    }

    override fun reducer(reducer: MviReducer<State, Intent, SideEffect>): MviContainerBuilder<State, Intent, SideEffect> {
        this.reducer = reducer
        return this
    }

    override fun enableLogging(enabled: Boolean): MviContainerBuilder<State, Intent, SideEffect> {
        this.enableLogging = enabled
        return this
    }

    override fun build(): MviContainer<State, Intent, SideEffect> {
        val state = initialState ?: throw IllegalStateException("Initial state must be set")
        val reducerInstance = reducer ?: throw IllegalStateException("Reducer must be set")

        return DefaultMviContainer(
            initialState = state,
            reducer = reducerInstance,
            scope = scope,
            enableLogging = enableLogging
        )
    }
}

/**
 * DSL function to create MVI containers with a fluent API
 */
fun <State : MviState, Intent : MviIntent, SideEffect : MviSideEffect>
        CoroutineScope.mviContainer(): MviContainerBuilder<State, Intent, SideEffect> {
    return DefaultMviContainerBuilder(this)
}

/**
 * Convenience function for creating MVI containers with initial state and reducer
 */
fun <State : MviState, Intent : MviIntent, SideEffect : MviSideEffect>
        CoroutineScope.mviContainer(
    initialState: State,
    reducer: MviReducer<State, Intent, SideEffect>,
    enableLogging: Boolean = false
): MviContainer<State, Intent, SideEffect> {
    return mviContainer<State, Intent, SideEffect>()
        .initialState(initialState)
        .reducer(reducer)
        .enableLogging(enableLogging)
        .build()
}