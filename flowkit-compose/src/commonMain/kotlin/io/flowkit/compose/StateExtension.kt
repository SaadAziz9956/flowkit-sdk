package io.flowkit.compose

import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import io.flowkit.core.*
import io.flowkit.network.NetworkResult
import io.flowkit.storage.StorageResult
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map

/**
 * 🎯 FlowKit Compose - State Extensions
 * Generic state collection utilities for reactive UI
 */

/**
 * Collect UiState with lifecycle awareness
 */
@Composable
fun <T> Flow<UiState<T>>.collectAsUiState(): State<UiState<T>> {
    return collectAsState(initial = UiState.Loading)
}

/**
 * Collect Result with lifecycle awareness
 */
@Composable
fun <E, T> Flow<Result<E, T>>.collectAsResult(): State<Result<E, T>> {
    return collectAsState(initial = Result.Loading)
}

/**
 * Collect StateFlow with lifecycle awareness
 */
@Composable
fun <T> StateFlow<T>.collectAsLifecycleState(): State<T> {
    return collectAsState()
}

/**
 * Generic async content handler for UiState
 */
@Composable
fun <T> AsyncContent(
    state: UiState<T>,
    loadingContent: @Composable () -> Unit = { CircularProgressIndicator() },
    errorContent: @Composable (UiState.Error) -> Unit = { Text("Error: ${it.message}") },
    successContent: @Composable (T) -> Unit
) {
    when (state) {
        is UiState.Loading -> loadingContent()
        is UiState.Error -> errorContent(state)
        is UiState.Success -> successContent(state.data)
    }
}

/**
 * Generic async content handler for Result
 */
@Composable
fun <E, T> AsyncResult(
    result: Result<E, T>,
    loadingContent: @Composable () -> Unit = { CircularProgressIndicator() },
    errorContent: @Composable (E) -> Unit = { Text("Error: $it") },
    successContent: @Composable (T) -> Unit
) {
    when (result) {
        is Result.Loading -> loadingContent()
        is Result.Error -> errorContent(result.error)
        is Result.Success -> successContent(result.data)
    }
}

/**
 * Network flow UI integration
 */
@Composable
fun <T> Flow<NetworkResult<T>>.collectAsNetworkUiState(): State<UiState<T>> {
    return this.map { networkResult ->
        networkResult.toUiState()
    }.collectAsUiState()
}

/**
 * Storage flow UI integration
 */
@Composable
fun <T> Flow<StorageResult<T>>.collectAsStorageUiState(): State<UiState<T>> {
    return this.map { storageResult ->
        storageResult.toUiState()
    }.collectAsUiState()
}

/**
 * Convert NetworkResult to UiState
 */
fun <T> NetworkResult<T>.toUiState(): UiState<T> = when (this) {
    is Result.Loading -> UiState.Loading
    is Result.Success -> UiState.Success(data)
    is Result.Error -> UiState.Error(error, error.message ?: "Network error")
}

/**
 * Convert StorageResult to UiState
 */
fun <T> StorageResult<T>.toUiState(): UiState<T> = when (this) {
    is Result.Loading -> UiState.Loading
    is Result.Success -> UiState.Success(data)
    is Result.Error -> UiState.Error(error, error.message ?: "Storage error")
}