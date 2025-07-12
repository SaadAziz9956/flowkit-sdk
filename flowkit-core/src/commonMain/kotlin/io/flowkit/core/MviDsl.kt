package io.flowkit.core

import kotlinx.coroutines.CoroutineScope
import kotlin.reflect.KClass

/**
 * Enhanced MVI DSL - builds on your existing mviContainer function
 * Makes MVI state management read like natural language
 *
 * Usage:
 * val container = viewModelScope.stateManager<State, Intent, SideEffect> {
 *     startsWith(State.Loading)
 *     handleIntents {
 *         on<LoadUser> { state, intent -> ... }
 *         on<UpdateProfile> { state, intent -> ... }
 *     }
 *     withLogging()
 * }
 */
fun <State : MviState, Intent : MviIntent, SideEffect : MviSideEffect>
        CoroutineScope.stateManager(
    block: MviDslBuilder<State, Intent, SideEffect>.() -> Unit
): MviContainer<State, Intent, SideEffect> {
    return MviDslBuilder<State, Intent, SideEffect>(this).apply(block).build()
}

/**
 * MVI DSL Builder - creates state containers with natural language
 */
class MviDslBuilder<State : MviState, Intent : MviIntent, SideEffect : MviSideEffect>(
    private val scope: CoroutineScope
) {
    private var initialState: State? = null
    private var reducer: MviReducer<State, Intent, SideEffect>? = null
    private var logging = false

    // ============================================================================
    // 🎯 NATURAL LANGUAGE MVI SETUP
    // ============================================================================

    /**
     * Set initial state with natural language
     */
    fun startsWith(state: State) = apply { initialState = state }

    /**
     * Alternative natural language for initial state
     */
    fun beginsWith(state: State) = apply { initialState = state }

    /**
     * Alternative natural language for initial state
     */
    fun initiallyShows(state: State) = apply { initialState = state }

    /**
     * Set reducer with existing reducer instance
     */
    fun handlesIntentsWith(reducer: MviReducer<State, Intent, SideEffect>) = apply {
        this.reducer = reducer
    }

    /**
     * Alternative natural language for reducer
     */
    fun processesIntentsWith(reducer: MviReducer<State, Intent, SideEffect>) = apply {
        this.reducer = reducer
    }

    /**
     * Enable logging with natural language
     */
    fun withLogging() = apply { logging = true }

    /**
     * Alternative natural language for logging
     */
    fun withDebugMode() = apply { logging = true }

    /**
     * Alternative natural language for logging
     */
    fun logged() = apply { logging = true }

    /**
     * DSL for building reducers inline with natural language
     */
    fun handleIntents(block: IntentHandlerDsl<State, Intent, SideEffect>.() -> Unit) = apply {
        this.reducer = IntentHandlerDsl<State, Intent, SideEffect>().apply(block).build()
    }

    /**
     * Alternative natural language for intent handling
     */
    fun processIntents(block: IntentHandlerDsl<State, Intent, SideEffect>.() -> Unit) = apply {
        this.reducer = IntentHandlerDsl<State, Intent, SideEffect>().apply(block).build()
    }

    /**
     * Alternative natural language for intent handling
     */
    fun whenIntentReceived(block: IntentHandlerDsl<State, Intent, SideEffect>.() -> Unit) = apply {
        this.reducer = IntentHandlerDsl<State, Intent, SideEffect>().apply(block).build()
    }

    /**
     * Build the MVI container using your existing infrastructure
     */
    fun build(): MviContainer<State, Intent, SideEffect> {
        val state = initialState ?: throw IllegalStateException("Initial state must be set with startsWith()")
        val reducerInstance = reducer ?: throw IllegalStateException("Reducer must be set")

        // Uses your existing mviContainer function
        return scope.mviContainer(state, reducerInstance, logging)
    }
}

/**
 * Intent handler DSL for inline reducer creation
 * Makes intent handling read like a when statement but more powerful
 */
class IntentHandlerDsl<State : MviState, Intent : MviIntent, SideEffect : MviSideEffect> {
    val handlers = mutableMapOf<String, suspend (State, Intent) -> ReducerResult<State, SideEffect>>()

    /**
     * Handle specific intent type with natural language
     */
    inline fun <reified T : Intent> on(
        noinline handler: suspend (State, T) -> ReducerResult<State, SideEffect>
    ) {
        val key = T::class.simpleName ?: "Unknown"
        @Suppress("UNCHECKED_CAST")
        handlers[key] = { state, intent ->
            handler(state, intent as T)
        }
    }

    /**
     * Alternative natural language for intent handling
     */
    inline fun <reified T : Intent> when_(
        noinline handler: suspend (State, T) -> ReducerResult<State, SideEffect>
    ) = on<T>(handler)

    /**
     * Alternative natural language for intent handling
     */
    inline fun <reified T : Intent> handle(
        noinline handler: suspend (State, T) -> ReducerResult<State, SideEffect>
    ) = on<T>(handler)

    /**
     * Alternative natural language for intent handling
     */
    inline fun <reified T : Intent> process(
        noinline handler: suspend (State, T) -> ReducerResult<State, SideEffect>
    ) = on<T>(handler)

    /**
     * Handle intent with just state change (no side effects)
     */
    inline fun <reified T : Intent> onJustState(
        noinline handler: suspend (State, T) -> State
    ) {
        on<T> { state, intent ->
            ReducerResult(handler(state, intent), emptyList())
        }
    }

    /**
     * Handle intent with just side effects (no state change)
     */
    inline fun <reified T : Intent> onJustEffect(
        noinline handler: suspend (State, T) -> List<SideEffect>
    ) {
        on<T> { state, intent ->
            ReducerResult(state, handler(state, intent))
        }
    }

    /**
     * Handle intent with single side effect
     */
    inline fun <reified T : Intent> onSingleEffect(
        noinline handler: suspend (State, T) -> SideEffect
    ) {
        on<T> { state, intent ->
            ReducerResult(state, listOf(handler(state, intent)))
        }
    }

    /**
     * Ignore specific intent type
     */
    inline fun <reified T : Intent> ignore() {
        on<T> { state, _ -> ReducerResult(state, emptyList()) }
    }

    /**
     * Build the reducer using your existing infrastructure
     */
    fun build(): MviReducer<State, Intent, SideEffect> = object : MviReducer<State, Intent, SideEffect> {
        override suspend fun reduce(currentState: State, intent: Intent): ReducerResult<State, SideEffect> {
            val key = intent::class.simpleName ?: "Unknown"
            val handler = handlers[key]
                ?: return ReducerResult(currentState, emptyList()) // Unhandled intent
            return handler(currentState, intent)
        }
    }
}

// ============================================================================
// 🎯 ENHANCED MVI HELPERS
// ============================================================================

/**
 * Create state with natural language
 */
fun <State : MviState, SideEffect : MviSideEffect> just(
    newState: State
): ReducerResult<State, SideEffect> = ReducerResult(newState, emptyList())

/**
 * Create state with single side effect
 */
fun <State : MviState, SideEffect : MviSideEffect> stateWith(
    newState: State,
    sideEffect: SideEffect
): ReducerResult<State, SideEffect> = ReducerResult(newState, listOf(sideEffect))

/**
 * Create state with multiple side effects
 */
fun <State : MviState, SideEffect : MviSideEffect> stateWith(
    newState: State,
    sideEffects: List<SideEffect>
): ReducerResult<State, SideEffect> = ReducerResult(newState, sideEffects)

/**
 * Keep current state but emit side effect
 */
fun <State : MviState, SideEffect : MviSideEffect> sameStateWith(
    currentState: State,
    sideEffect: SideEffect
): ReducerResult<State, SideEffect> = ReducerResult(currentState, listOf(sideEffect))

/**
 * Keep current state but emit multiple side effects
 */
fun <State : MviState, SideEffect : MviSideEffect> sameStateWith(
    currentState: State,
    sideEffects: List<SideEffect>
): ReducerResult<State, SideEffect> = ReducerResult(currentState, sideEffects)

/**
 * No change - keep current state, no side effects
 */
fun <State : MviState, SideEffect : MviSideEffect> noChange(
    currentState: State
): ReducerResult<State, SideEffect> = ReducerResult(currentState, emptyList())

// ============================================================================
// 🎯 QUICK MVI SETUP FUNCTIONS
// ============================================================================

/**
 * Quick MVI setup for simple state management
 */
fun <State : MviState, Intent : MviIntent> CoroutineScope.simpleState(
    initialState: State,
    block: SimpleStateDsl<State, Intent>.() -> Unit
): MviContainer<State, Intent, Nothing> {
    return SimpleStateDsl<State, Intent>(this, initialState).apply(block).build()
}

/**
 * Simple state DSL for basic MVI without side effects
 */
class SimpleStateDsl<State : MviState, Intent : MviIntent>(
    private val scope: CoroutineScope,
    private val initialState: State
) {
    val handlers = mutableMapOf<String, suspend (State, Intent) -> State>()
    private var logging = false

    /**
     * Handle intent with just state change
     */
    inline fun <reified T : Intent> on(
        noinline handler: suspend (State, T) -> State
    ) {
        val key = T::class.simpleName ?: "Unknown"
        @Suppress("UNCHECKED_CAST")
        handlers[key] = { state, intent ->
            handler(state, intent as T)
        }
    }

    /**
     * Enable logging
     */
    fun withLogging() = apply { logging = true }

    /**
     * Build simple MVI container
     */
    fun build(): MviContainer<State, Intent, Nothing> {
        val reducer = object : MviReducer<State, Intent, Nothing> {
            override suspend fun reduce(currentState: State, intent: Intent): ReducerResult<State, Nothing> {
                val key = intent::class.simpleName ?: "Unknown"
                val handler = handlers[key] ?: return ReducerResult(currentState, emptyList())
                val newState = handler(currentState, intent)
                return ReducerResult(newState, emptyList())
            }
        }

        return scope.mviContainer(initialState, reducer, logging)
    }
}

// ============================================================================
// 🎯 EXTENSION FUNCTIONS FOR EASIER MVI USAGE
// ============================================================================

/**
 * Extension to process intents more naturally
 */
suspend fun <State : MviState, Intent : MviIntent, SideEffect : MviSideEffect>
        MviContainer<State, Intent, SideEffect>.send(intent: Intent) {
    processIntent(intent)
}

/**
 * Extension to get current state more naturally
 */
fun <State : MviState, Intent : MviIntent, SideEffect : MviSideEffect>
        MviContainer<State, Intent, SideEffect>.current(): State {
    return currentState()
}

/**
 * Extension to observe state changes with natural language
 */
fun <State : MviState, Intent : MviIntent, SideEffect : MviSideEffect>
        MviContainer<State, Intent, SideEffect>.watchState() = state

/**
 * Extension to observe side effects with natural language
 */
fun <State : MviState, Intent : MviIntent, SideEffect : MviSideEffect>
        MviContainer<State, Intent, SideEffect>.watchSideEffects() = sideEffects