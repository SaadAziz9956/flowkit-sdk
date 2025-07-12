package io.flowkit.compose

import androidx.compose.runtime.*
import io.flowkit.core.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow

/**
 * Remember MVI container with lifecycle
 */
@Composable
inline fun <reified State : MviState, reified Intent : MviIntent, reified SideEffect : MviSideEffect> rememberMviContainer(
    initialState: State,
    reducer: MviReducer<State, Intent, SideEffect>,
    enableLogging: Boolean = false
): MviContainer<State, Intent, SideEffect> {
    val scope = rememberCoroutineScope()

    return remember(initialState, reducer) {
        scope.mviContainer(
            initialState = initialState,
            reducer = reducer,
            enableLogging = enableLogging
        )
    }
}

/**
 * Basic MVI integration
 */
@Composable
fun <State : MviState, Intent : MviIntent, SideEffect : MviSideEffect> MviCompose(
    container: MviContainer<State, Intent, SideEffect>,
    sideEffectHandler: @Composable (Flow<SideEffect>) -> Unit = {},
    content: @Composable (state: State, onIntent: (Intent) -> Unit) -> Unit
) {
    val state by container.state.collectAsLifecycleState()

    // Side Effects
    sideEffectHandler(container.sideEffects)

    // Intent Channel for one-shot events
    val intentChannel = remember { Channel<Intent>(Channel.UNLIMITED) }

    // Handle intent side effects in LaunchedEffect (outside of recompositions)
    LaunchedEffect(Unit) {
        for (intent in intentChannel) {
            // Handle intent here, e.g., send to container
            container.processIntent(intent)
        }
    }

    // Provide state and onIntent dispatcher
    content(state) { intent ->
        // Directly send intent to the channel (no LaunchedEffect inside lambda)
        intentChannel.trySend(intent)
    }
}

/**
 * Collect side effects with lifecycle awareness
 */
@Composable
fun <SideEffect : MviSideEffect> Flow<SideEffect>.CollectSideEffects(
    onSideEffect: (SideEffect) -> Unit
) {
    LaunchedEffect(Unit) {
        collect(onSideEffect)
    }
}

/**
 * Generic MVI screen builder
 */
@Composable
inline fun <reified State : MviState, reified Intent : MviIntent, reified SideEffect : MviSideEffect> Screen(
    initialState: State,
    reducer: MviReducer<State, Intent, SideEffect>,
    noinline sideEffectHandler: @Composable (Flow<SideEffect>) -> Unit = {},
    noinline content: @Composable (state: State, onIntent: (Intent) -> Unit) -> Unit
) {
    val container = rememberMviContainer(
        initialState = initialState,
        reducer = reducer
    )

    MviCompose(
        container = container,
        sideEffectHandler = sideEffectHandler,
        content = content
    )
}