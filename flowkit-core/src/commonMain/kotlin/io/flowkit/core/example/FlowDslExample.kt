package io.flowkit.core.example

import io.flowkit.core.UiState
import io.flowkit.core.collectSafely
import io.flowkit.core.flowOf
import io.flowkit.core.periodicFlow
import io.flowkit.core.pipeline
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

/**
 * Examples showing how to use the enhanced Flow DSL
 */
object FlowDslExamples {

    /**
     * Search flow with debouncing and error handling
     */
    fun createSearchFlow(searchQueries: Flow<String>): Flow<String> {
        return searchQueries.pipeline {
            waitFor(300.milliseconds)  // Debounce user input
            onlyNewValues()           // Only new search terms
            onlyWhen { it.length >= 2 } // Only search terms with 2+ chars
            transformWith { it.trim().lowercase() } // Clean the input
            retryUpTo(3)              // Retry failed searches
            logAs("SearchFlow")       // Log all searches
        }
    }

    /**
     * Data loading flow with caching and error recovery
     */
    fun createDataFlow(): Flow<String> {
        return flowOf { loadDataFromApi() }.pipeline {
            retryUpTo(3)              // Retry API calls
            onErrorJust("Fallback data") // Provide fallback
            cacheLatest()             // Cache for new subscribers
            logAs("DataFlow")         // Log all emissions
        }
    }

    /**
     * Real-time updates flow
     */
    fun createUpdatesFlow(): Flow<Int> {
        return periodicFlow(5.seconds) {
            fetchLatestCount()
        }.pipeline {
            onlyUniqueValues()             // Only emit when count changes
            throttle(1.seconds)       // Don't emit more than once per second
            logWith("Updates") { "Count: $it" } // Custom log format
        }
    }

    /**
     * Complex data processing pipeline
     */
    fun createProcessingFlow(input: Flow<RawData>): Flow<ProcessedData> {
        return input.pipeline {
            onlyWhen { it.isValid() }     // Filter valid data
            transformWith { it.clean() }  // Clean the data
            waitFor(100.milliseconds)     // Batch processing
            alsoDo { saveToCache(it) }    // Side effect: cache
            onErrorDo { error ->          // Error recovery
                logError(error)
                RawData.empty()
            }
            transformWith { it.process() } // Final processing
            logAs("ProcessingPipeline")   // Log everything
        }.map { it.toProcessedData() }
    }

    /**
     * UI state flow with lifecycle management
     */
    suspend fun collectUiUpdates(uiUpdates: Flow<UiState<String>>) {
        uiUpdates.pipeline {
            onlyNewValues()             // Only when UI state changes
            throttle(16.milliseconds) // Respect 60fps
            logAs("UI")              // Log UI updates
        }.collectSafely(
            onError = { error ->
                println("UI Error: ${error.message}")
            }
        ) { uiState ->
            updateUI(uiState)
        }
    }
}

// Sample data classes for examples
data class RawData(val value: String) {
    fun isValid(): Boolean = value.isNotBlank()
    fun clean(): RawData = copy(value = value.trim())
    fun process(): RawData = copy(value = value.uppercase())
    fun toProcessedData(): ProcessedData = ProcessedData(value)

    companion object {
        fun empty() = RawData("")
    }
}

data class ProcessedData(val result: String)

// Mock functions for examples
fun loadDataFromApi(): String = "API Data"
fun fetchLatestCount(): Int = (1..100).random()
fun saveToCache(data: RawData) { /* Cache implementation */
}

fun logError(error: Throwable) {
    println("Error: ${error.message}")
}

fun updateUI(state: UiState<String>) { /* UI update */
}
