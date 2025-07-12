package io.flowkit.core

import kotlinx.coroutines.flow.*
import kotlinx.coroutines.delay
import kotlin.time.Clock
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds
import kotlin.time.ExperimentalTime

/**
 * Enhanced flow pipeline DSL
 * Extends your existing flow extensions with more natural syntax
 *
 * Usage:
 * someFlow.pipeline {
 *     waitFor(300.milliseconds)
 *     onlyNewValues()
 *     retryUpTo(3)
 *     logAs("MyFlow")
 * }
 */
fun <T> Flow<T>.pipeline(block: FlowPipelineBuilder<T>.() -> Unit): Flow<T> {
    return FlowPipelineBuilder(this).apply(block).build()
}

/**
 * Flow pipeline builder for chainable operations
 * Makes complex flow transformations read like English
 */
class FlowPipelineBuilder<T>(private val source: Flow<T>) {
    private val operations = mutableListOf<Flow<T>.() -> Flow<T>>()

    /**
     * Debounce with natural language - "wait for user to stop typing"
     */
    fun waitFor(duration: Duration) = add { debounce(duration) }

    /**
     * Distinct with natural language - "only emit new values"
     */
    fun onlyNewValues() = add { distinctUntilChanged() }

    /**
     * Only unique values ever (stronger than distinctUntilChanged)
     */
    fun onlyUniqueValues() = add { distinct() }

    /**
     * Retry with natural language - "retry up to N times"
     */
    fun retryUpTo(attempts: Int) = add {
        retryWhen { cause, attempt ->
            attempt < attempts.toLong()
        }
    }

    /**
     * Take only first N items with natural language
     */
    fun takeFirst(count: Int) = add { take(count) }

    /**
     * Take items while condition is true
     */
    fun takeWhile(condition: (T) -> Boolean) = add { takeWhile(condition) }

    /**
     * Skip first N items with natural language
     */
    fun skipFirst(count: Int) = add { drop(count) }

    /**
     * Skip items while condition is true
     */
    fun skipWhile(condition: (T) -> Boolean) = add { dropWhile(condition) }

    /**
     * Transform values with natural language
     */
    fun transformWith(transform: (T) -> T) = add { map(transform) }

    /**
     * Filter values with natural language
     */
    fun onlyWhen(predicate: (T) -> Boolean) = add { filter(predicate) }

    /**
     * Log emissions with natural language
     */
    fun logAs(tag: String) = add {
        onEach { value ->
            println("🔄 [$tag] Emitted: $value")
        }
    }

    /**
     * Log with custom format
     */
    fun logWith(tag: String, formatter: (T) -> String) = add {
        onEach { value ->
            println("🔄 [$tag] ${formatter(value)}")
        }
    }

    /**
     * Handle errors gracefully
     */
    fun onErrorJust(fallback: T) = add {
        catch { emit(fallback) }
    }

    /**
     * Handle errors with custom logic
     */
    fun onErrorDo(handler: suspend (Throwable) -> T) = add {
        catch { throwable -> emit(handler(throwable)) }
    }

    /**
     * Add side effects without changing the stream
     */
    fun alsoDo(action: suspend (T) -> Unit) = add {
        onEach { action(it) }
    }

    /**
     * Throttle emissions (different from debounce)
     */
    @OptIn(ExperimentalTime::class)
    fun throttle(duration: Duration) = add {
        // Simple throttle implementation
        var lastEmissionTime = 0L
        filter {
            val currentTime = Clock.System.now().toEpochMilliseconds()
            val shouldEmit = currentTime - lastEmissionTime >= duration.inWholeMilliseconds
            if (shouldEmit) {
                lastEmissionTime = currentTime
            }
            shouldEmit
        }
    }

    /**
     * Sample values at regular intervals
     */
    fun sampleEvery(duration: Duration) = add {
        sample(duration)
    }

    /**
     * Add delays between emissions
     */
    fun withDelay(duration: Duration) = add {
        onEach { delay(duration) }
    }

    /**
     * Share emissions among multiple subscribers
     */
    fun shareResults() = add { shareIn(kotlinx.coroutines.GlobalScope, SharingStarted.Lazily) }

    /**
     * Cache latest value for new subscribers
     */
    fun cacheLatest() = add {
        shareIn(kotlinx.coroutines.GlobalScope, SharingStarted.Lazily, replay = 1)
    }

    // ============================================================================
    // 🔧 BUILDER INTERNALS
    // ============================================================================

    private fun add(operation: Flow<T>.() -> Flow<T>) = apply {
        operations.add(operation)
    }

    fun build(): Flow<T> = operations.fold(source) { flow, operation ->
        flow.operation()
    }
}

// ============================================================================
// 🎯 ENHANCED COLLECTION HELPERS
// ============================================================================

/**
 * Collect safely with natural language error handling
 * Builds on your existing safeCollect but with better naming
 */
suspend fun <T> Flow<T>.collectSafely(
    onError: suspend (Throwable) -> Unit = { it.printStackTrace() },
    onEach: suspend (T) -> Unit
) {
    return safeCollect(onError, onEach) // Uses your existing function
}

/**
 * Collect with lifecycle awareness (useful for UI)
 */
suspend fun <T> Flow<T>.collectWhile(
    condition: () -> Boolean,
    onEach: suspend (T) -> Unit
) {
    takeWhile { condition() }.collect(onEach)
}

/**
 * Collect only the first emission
 */
suspend fun <T> Flow<T>.collectFirst(action: suspend (T) -> Unit) {
    first().let { action(it) }
}

/**
 * Collect only the latest emission (useful for UI updates)
 */
suspend fun <T> Flow<T>.collectLatest(action: suspend (T) -> Unit) {
    collectLatest(action)
}

// ============================================================================
// 🎯 ENHANCED FLOW BUILDERS
// ============================================================================

/**
 * Create flows with natural language builders
 */
fun <T> createFlow(block: suspend FlowCollector<T>.() -> Unit): Flow<T> = flow(block)

/**
 * Create flows from suspending functions
 */
fun <T> flowOf(producer: suspend () -> T): Flow<T> = flow { emit(producer()) }

/**
 * Create flows from multiple suspending functions
 */
fun <T> flowOfAll(vararg producers: suspend () -> T): Flow<T> = flow {
    producers.forEach { producer ->
        emit(producer())
    }
}

/**
 * Create periodic flows with natural language
 */
fun <T> periodicFlow(
    interval: Duration,
    producer: suspend () -> T
): Flow<T> = flow {
    while (true) {
        emit(producer())
        delay(interval)
    }
}

/**
 * Create timer flows
 */
fun timerFlow(interval: Duration): Flow<Long> = flow {
    var count = 0L
    while (true) {
        emit(count++)
        delay(interval)
    }
}

/**
 * Create countdown flows
 */
fun countdownFlow(from: Int, interval: Duration = 1.seconds): Flow<Int> = flow {
    for (i in from downTo 0) {
        emit(i)
        if (i > 0) delay(interval)
    }
}

// ============================================================================
// 🎯 ENHANCED FLOW COMBINATORS
// ============================================================================

/**
 * Combine flows with natural language
 */
fun <T1, T2, R> combineFlows(
    flow1: Flow<T1>,
    flow2: Flow<T2>,
    transform: (T1, T2) -> R
): Flow<R> = flow1.combine(flow2, transform)

/**
 * Combine three flows
 */
fun <T1, T2, T3, R> combineFlows(
    flow1: Flow<T1>,
    flow2: Flow<T2>,
    flow3: Flow<T3>,
    transform: (T1, T2, T3) -> R
): Flow<R> = combine(flow1, flow2, flow3, transform)

/**
 * Merge flows with natural language
 */
fun <T> mergeFlows(vararg flows: Flow<T>): Flow<T> = merge(*flows)

/**
 * Zip flows with natural language
 */
fun <T1, T2, R> zipFlows(
    flow1: Flow<T1>,
    flow2: Flow<T2>,
    transform: (T1, T2) -> R
): Flow<R> = flow1.zip(flow2, transform)

// ============================================================================
// 🎯 FLOW STATE MANAGEMENT
// ============================================================================

/**
 * Create stateful flows that remember previous emissions
 */
fun <T> Flow<T>.stateful(): Flow<Pair<T?, T>> = scan(null as T? to null as T?) { (_, previous), current ->
    previous to current
}.drop(1).map { (previous, current) -> previous to current!! }

/**
 * Create flows that emit differences between consecutive values
 */
fun <T> Flow<T>.changes(): Flow<Pair<T?, T>> = stateful()

/**
 * Create flows that only emit when value actually changes
 */
fun <T> Flow<T>.onlyChanges(): Flow<T> = distinctUntilChanged()

/**
 * Create flows with buffering
 */
fun <T> Flow<T>.buffered(size: Int): Flow<List<T>> =
    buffer(size).chunked(size)


fun <T> Flow<T>.distinct(): Flow<T> = flow {
    val emitted = HashSet<T>() // HashSet for O(1) contains/add
    collect { value ->
        if (emitted.add(value)) {
            emit(value)
        }
    }
}
