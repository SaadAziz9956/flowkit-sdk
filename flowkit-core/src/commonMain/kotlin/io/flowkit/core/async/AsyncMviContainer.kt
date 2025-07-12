package io.flowkit.core.async

import io.flowkit.core.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.onFailure
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * 🚀 Enhanced MVI Container with automatic async operation handling
 * Generic over AsyncResult type to eliminate circular dependencies
 */
internal class EnhancedMviContainer<State : MviState, Intent : MviIntent, SideEffect : MviSideEffect, AsyncResult>(
    initialState: State,
    private val reducer: AsyncMviReducer<State, Intent, SideEffect, AsyncResult>,
    private val scope: CoroutineScope,
    private val enableLogging: Boolean = false,
    private val enableAutoSideEffects: Boolean = true
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

    // Track running async operations to prevent duplicates
    private val runningOperations = mutableSetOf<String>()
    private val operationsMutex = Mutex()

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

    override suspend fun updateState(transform: (State) -> State) {
        val currentState = _state.value
        val newState = transform(currentState)

        if (newState != currentState) {
            _state.value = newState

            if (enableLogging) {
                println("🔄 FlowKit Enhanced: Direct state update")
                println("   ${currentState::class.simpleName} -> ${newState::class.simpleName}")
            }
        }
    }

    private suspend fun processIntentInternal(intent: Intent) {
        try {
            val currentState = _state.value
            val result = reducer.reduceAsync(currentState, intent)

            when (result) {
                is AsyncReducerResult.Immediate -> {
                    handleImmediateResult(result, intent, currentState)
                }

                is AsyncReducerResult.Async -> {
                    handleAsyncResult(result, intent, currentState)
                }
            }

        } catch (throwable: Throwable) {
            logError("Error in async reducer for intent: $intent", throwable)
        }
    }

    private suspend fun handleImmediateResult(
        result: AsyncReducerResult.Immediate<State, SideEffect, AsyncResult>,
        intent: Intent,
        currentState: State
    ) {
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
    }

    private suspend fun handleAsyncResult(
        result: AsyncReducerResult.Async<State, SideEffect, AsyncResult>,
        intent: Intent,
        currentState: State
    ) {
        // Check if operation is already running
        val isRunning = operationsMutex.withLock {
            if (runningOperations.contains(result.operationId)) {
                true
            } else {
                runningOperations.add(result.operationId)
                false
            }
        }

        if (isRunning) {
            if (enableLogging) {
                logDuplicateOperation(result.operationId, intent)
            }
            return
        }

        // Set loading state immediately
        if (result.loadingState != currentState) {
            _state.value = result.loadingState

            if (enableLogging) {
                logStateChange(currentState, result.loadingState, intent)
            }
        }

        // Emit immediate side effects
        result.sideEffects.forEach { sideEffect ->
            _sideEffects.tryEmit(sideEffect)
        }

        // Emit auto-generated side effect for operation start
        if (enableAutoSideEffects) {
            @Suppress("UNCHECKED_CAST")
            _sideEffects.tryEmit(
                AsyncSideEffect.AsyncOperationStarted(result.operationId) as SideEffect
            )
        }

        // 🚀 Execute async operation automatically
        scope.launch {
            try {
                if (enableLogging) {
                    logAsyncOperationStart(result.operationId, intent)
                }

                result.operation().collect { asyncResult ->
                    val newState = result.onResult(_state.value, asyncResult)
                    _state.value = newState

                    if (enableLogging) {
                        logAsyncResult(intent, result.operationId, asyncResult)
                    }
                }

                // Emit success side effect
                if (enableAutoSideEffects) {
                    @Suppress("UNCHECKED_CAST")
                    _sideEffects.tryEmit(
                        AsyncSideEffect.AsyncOperationCompleted(result.operationId) as SideEffect
                    )
                }

            } catch (throwable: Throwable) {
                logError("Async operation failed: ${result.operationId}", throwable)

                // Emit failure side effect
                if (enableAutoSideEffects) {
                    @Suppress("UNCHECKED_CAST")
                    _sideEffects.tryEmit(
                        AsyncSideEffect.AsyncOperationFailed(
                            result.operationId,
                            throwable.message ?: "Unknown error"
                        ) as SideEffect
                    )
                }
            } finally {
                // Remove operation from running set
                operationsMutex.withLock {
                    runningOperations.remove(result.operationId)
                }
            }
        }
    }

    // Logging functions
    private fun logIntent(intent: Intent, currentState: State) {
        println("🎯 FlowKit Enhanced: Processing intent: ${intent::class.simpleName}")
        println("   Current state: ${currentState::class.simpleName}")
    }

    private fun logStateChange(oldState: State, newState: State, intent: Intent) {
        println("🔄 FlowKit Enhanced: State changed")
        println("   Intent: ${intent::class.simpleName}")
        println("   ${oldState::class.simpleName} -> ${newState::class.simpleName}")
    }

    private fun logSideEffect(sideEffect: SideEffect, intent: Intent) {
        println("⚡ FlowKit Enhanced: Side effect emitted")
        println("   Effect: ${sideEffect::class.simpleName}")
        println("   From intent: ${intent::class.simpleName}")
    }

    private fun logAsyncOperationStart(operationId: String, intent: Intent) {
        println("🚀 FlowKit Enhanced: Async operation started")
        println("   Operation ID: $operationId")
        println("   Intent: ${intent::class.simpleName}")
    }

    private fun logAsyncResult(intent: Intent, operationId: String, result: Any?) {
        println("✅ FlowKit Enhanced: Async operation result")
        println("   Operation ID: $operationId")
        println("   Intent: ${intent::class.simpleName}")
        println("   Result type: ${result?.let { it::class.simpleName } ?: "null"}")
    }

    private fun logDuplicateOperation(operationId: String, intent: Intent) {
        println("⚠️ FlowKit Enhanced: Duplicate operation prevented")
        println("   Operation ID: $operationId")
        println("   Intent: ${intent::class.simpleName}")
    }

    private fun logError(message: String, throwable: Throwable?) {
        println("❌ FlowKit Enhanced Error: $message")
        throwable?.printStackTrace()
    }
}

/**
 * Enhanced builder for creating MVI containers with async support
 * Generic over AsyncResult type to eliminate circular dependencies
 */
class EnhancedMviContainerBuilder<State : MviState, Intent : MviIntent, SideEffect : MviSideEffect, AsyncResult>(
    private val scope: CoroutineScope
) : MviContainerBuilder<State, Intent, SideEffect> {

    private var initialState: State? = null
    private var asyncReducer: AsyncMviReducer<State, Intent, SideEffect, AsyncResult>? = null
    private var enableLogging: Boolean = false
    private var enableAutoSideEffects: Boolean = true

    override fun initialState(state: State): EnhancedMviContainerBuilder<State, Intent, SideEffect, AsyncResult> {
        this.initialState = state
        return this
    }

    override fun reducer(reducer: MviReducer<State, Intent, SideEffect>): EnhancedMviContainerBuilder<State, Intent, SideEffect, AsyncResult> {
        // Convert regular reducer to async reducer if needed
        @Suppress("UNCHECKED_CAST")
        this.asyncReducer = if (reducer is AsyncMviReducer<*, *, *, *>) {
            reducer as AsyncMviReducer<State, Intent, SideEffect, AsyncResult>
        } else {
            LegacyReducerWrapper(reducer)
        }
        return this
    }

    /**
     * Set async reducer directly
     */
    fun asyncReducer(reducer: AsyncMviReducer<State, Intent, SideEffect, AsyncResult>): EnhancedMviContainerBuilder<State, Intent, SideEffect, AsyncResult> {
        this.asyncReducer = reducer
        return this
    }

    override fun enableLogging(enabled: Boolean): EnhancedMviContainerBuilder<State, Intent, SideEffect, AsyncResult> {
        this.enableLogging = enabled
        return this
    }

    /**
     * Enable/disable automatic side effects for async operations
     */
    fun enableAutoSideEffects(enabled: Boolean): EnhancedMviContainerBuilder<State, Intent, SideEffect, AsyncResult> {
        this.enableAutoSideEffects = enabled
        return this
    }

    override fun build(): MviContainer<State, Intent, SideEffect> {
        val state = initialState ?: throw IllegalStateException("Initial state must be set")
        val reducerInstance = asyncReducer ?: throw IllegalStateException("Reducer must be set")

        return EnhancedMviContainer(
            initialState = state,
            reducer = reducerInstance,
            scope = scope,
            enableLogging = enableLogging,
            enableAutoSideEffects = enableAutoSideEffects
        )
    }
}

/**
 * Wrapper to make legacy reducers work with enhanced container
 */
private class LegacyReducerWrapper<State : MviState, Intent : MviIntent, SideEffect : MviSideEffect, AsyncResult>(
    private val legacyReducer: MviReducer<State, Intent, SideEffect>
) : AsyncMviReducer<State, Intent, SideEffect, AsyncResult> {

    override suspend fun reduceAsync(
        currentState: State,
        intent: Intent
    ): AsyncReducerResult<State, SideEffect, AsyncResult> {
        val result = legacyReducer.reduce(currentState, intent)
        return AsyncReducerResult.Immediate(
            newState = result.newState,
            sideEffects = result.sideEffects
        )
    }
}

/**
 * 🚀 Enhanced DSL function to create async MVI containers
 * Generic over AsyncResult type
 */
inline fun <reified State : MviState, reified Intent : MviIntent, reified SideEffect : MviSideEffect, reified AsyncResult>
        CoroutineScope.enhancedMviContainer(): EnhancedMviContainerBuilder<State, Intent, SideEffect, AsyncResult> {
    return EnhancedMviContainerBuilder(this)
}

/**
 * 🚀 Convenience function for creating enhanced containers with async reducer
 */
inline fun <reified State : MviState, reified Intent : MviIntent, reified SideEffect : MviSideEffect, reified AsyncResult>
        CoroutineScope.enhancedMviContainer(
    initialState: State,
    asyncReducer: AsyncMviReducer<State, Intent, SideEffect, AsyncResult>,
    enableLogging: Boolean = false,
    enableAutoSideEffects: Boolean = true
): MviContainer<State, Intent, SideEffect> {
    return enhancedMviContainer<State, Intent, SideEffect, AsyncResult>()
        .initialState(initialState)
        .asyncReducer(asyncReducer)
        .enableLogging(enableLogging)
        .enableAutoSideEffects(enableAutoSideEffects)
        .build()
}