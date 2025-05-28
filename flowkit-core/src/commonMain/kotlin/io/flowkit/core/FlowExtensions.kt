@file:OptIn(FlowPreview::class)

package io.flowkit.core

import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.delay
import kotlin.math.pow
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds

/**
 * Common UI states that most apps need
 */
sealed class UiState<out T> : MviState {
    data object Loading : UiState<Nothing>()
    data class Success<T>(val data: T) : UiState<T>()
    data class Error(val throwable: Throwable, val message: String = throwable.message ?: "Unknown error") : UiState<Nothing>()
}

/**
 * Extension to safely convert any Flow to UiState
 */
fun <T> Flow<T>.asUiState(): Flow<UiState<T>> = flow {
    emit(UiState.Loading)
    try {
        collect { data ->
            emit(UiState.Success(data))
        }
    } catch (throwable: Throwable) {
        emit(UiState.Error(throwable))
    }
}

/**
 * Extension to map UiState data while preserving state type
 */
inline fun <T, R> Flow<UiState<T>>.mapData(
    crossinline transform: suspend (T) -> R
): Flow<UiState<R>> = map { state ->
    when (state) {
        is UiState.Loading -> UiState.Loading
        is UiState.Success -> UiState.Success(transform(state.data))
        is UiState.Error -> state
    }
}

/**
 * Extension to handle errors in UiState flows
 */
fun <T> Flow<UiState<T>>.onError(
    action: suspend (Throwable) -> Unit
): Flow<UiState<T>> = onEach { state ->
    if (state is UiState.Error) {
        action(state.throwable)
    }
}

/**
 * Extension to handle successful data in UiState flows
 */
fun <T> Flow<UiState<T>>.onSuccess(
    action: suspend (T) -> Unit
): Flow<UiState<T>> = onEach { state ->
    if (state is UiState.Success) {
        action(state.data)
    }
}

/**
 * Extension to handle loading state in UiState flows
 */
fun <T> Flow<UiState<T>>.onLoading(
    action: suspend () -> Unit
): Flow<UiState<T>> = onEach { state ->
    if (state is UiState.Loading) {
        action()
    }
}

/**
 * Debounce extension with Duration support
 */
fun <T> Flow<T>.debounce(duration: Duration): Flow<T> =
    debounce(duration.inWholeMilliseconds)

/**
 * Retry with exponential backoff
 */
fun <T> Flow<T>.retryWithBackoff(
    maxAttempts: Int = 3,
    initialDelay: Duration = 1000.milliseconds,
    maxDelay: Duration = 10_000.milliseconds,
    backoffMultiplier: Double = 2.0,
    retryCondition: (Throwable) -> Boolean = { true }
): Flow<T> = retryWhen { cause, attempt ->
    if (attempt < maxAttempts && retryCondition(cause)) {
        // Calculate delay with exponential backoff
        val delayMs = (initialDelay.inWholeMilliseconds * backoffMultiplier.pow(attempt.toDouble()))
            .toLong()
            .coerceAtMost(maxDelay.inWholeMilliseconds)
        delay(delayMs)
        true
    } else {
        false
    }
}

/**
 * Extension to ignore repeated values (more readable than distinctUntilChanged)
 */
fun <T> Flow<T>.ignoreRepeatedValues(): Flow<T> = distinctUntilChanged()

/**
 * Extension to safely collect with error handling
 */
suspend inline fun <T> Flow<T>.safeCollect(
    crossinline onError: suspend (Throwable) -> Unit,
    crossinline onEach: suspend (T) -> Unit
) {
    catch { throwable ->
        onError(throwable)
    }.collect { value ->
        try {
            onEach(value)
        } catch (throwable: Throwable) {
            onError(throwable)
        }
    }
}

/**
 * Builder for creating flows with common patterns
 */
class FlowBuilder<T> {
    private var sourceFlow: Flow<T>? = null
    private var errorHandler: (suspend (Throwable) -> Unit)? = null
    private var debounceTime: Duration? = null
    private var retryConfig: RetryConfig? = null
    private var distinctValues: Boolean = false

    fun source(flow: Flow<T>): FlowBuilder<T> {
        sourceFlow = flow
        return this
    }

    fun source(block: suspend () -> T): FlowBuilder<T> {
        sourceFlow = flow { emit(block()) }
        return this
    }

    fun onError(handler: suspend (Throwable) -> Unit): FlowBuilder<T> {
        errorHandler = handler
        return this
    }

    fun debounce(duration: Duration): FlowBuilder<T> {
        debounceTime = duration
        return this
    }

    fun retryOnFailure(
        maxAttempts: Int = 3,
        initialDelay: Duration = 1000.milliseconds,
        condition: (Throwable) -> Boolean = { true }
    ): FlowBuilder<T> {
        retryConfig = RetryConfig(maxAttempts, initialDelay, condition)
        return this
    }

    fun ignoreRepeatedValues(): FlowBuilder<T> {
        distinctValues = true
        return this
    }

    fun build(): Flow<T> {
        var flow = sourceFlow ?: throw IllegalStateException("Source flow must be set")

        // Apply transformations in order
        if (debounceTime != null) {
            flow = flow.debounce(debounceTime!!)
        }

        if (distinctValues) {
            flow = flow.distinctUntilChanged()
        }

        if (retryConfig != null) {
            flow = flow.retryWithBackoff(
                maxAttempts = retryConfig!!.maxAttempts,
                initialDelay = retryConfig!!.initialDelay,
                retryCondition = retryConfig!!.condition
            )
        }

        if (errorHandler != null) {
            flow = flow.catch { throwable ->
                errorHandler!!(throwable)
            }
        }

        return flow
    }

    private data class RetryConfig(
        val maxAttempts: Int,
        val initialDelay: Duration,
        val condition: (Throwable) -> Boolean
    )
}

/**
 * DSL function for building flows with common patterns
 */
fun <T> flowBuilder(block: FlowBuilder<T>.() -> Unit): Flow<T> {
    return FlowBuilder<T>().apply(block).build()
}