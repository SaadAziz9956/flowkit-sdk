package io.flowkit.compose.example

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import io.flowkit.compose.AsyncContent
import io.flowkit.compose.Screen
import io.flowkit.compose.HandleSideEffectsWithPlatform
import io.flowkit.compose.HapticType
import io.flowkit.compose.MessageType
import io.flowkit.compose.PlatformAction
import io.flowkit.compose.PlatformServices
import io.flowkit.compose.SideEffectToPlatformActionConverter
import io.flowkit.compose.collectAsNetworkUiState
import io.flowkit.compose.collectAsUiState
import io.flowkit.compose.rememberPlatformServices
import io.flowkit.core.*
import io.flowkit.network.NetworkResult
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf

/**
 * 📋 FlowKit Compose - Usage Examples
 * Shows how to use the generic SDK
 */

/**
 * Example 1: Generic data loading screen
 */
@Composable
fun <T> GenericDataScreen(
    dataFlow: Flow<UiState<T>>,
    loadingContent: @Composable () -> Unit = {
        Box(modifier = Modifier.fillMaxSize()) {
            CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
        }
    },
    errorContent: @Composable (UiState.Error) -> Unit = { error ->
        Box(modifier = Modifier.fillMaxSize()) {
            Text(
                text = "Error: ${error.message}",
                modifier = Modifier.align(Alignment.Center)
            )
        }
    },
    successContent: @Composable (T) -> Unit
) {
    val state by dataFlow.collectAsUiState()

    AsyncContent(
        state = state,
        loadingContent = loadingContent,
        errorContent = errorContent,
        successContent = successContent
    )
}

/**
 * Example 2: Network data screen
 */
@Composable
fun NetworkDataExample() {
    // This would come from your repository
    val networkFlow: Flow<NetworkResult<List<String>>> = flowOf(/* your network flow */)

    val uiState by networkFlow.collectAsNetworkUiState()

    AsyncContent(state = uiState) { data ->
        LazyColumn {
            items(data.size) { index ->
                Text(text = data[index])
            }
        }
    }
}

/**
 * Example 3: Platform services usage
 */
@Composable
fun PlatformServicesExample() {
    val platformServices = rememberPlatformServices()

    Column(
        modifier = Modifier.padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Button(
            onClick = {
                platformServices.messageDisplayer.showMessage(
                    "Hello from FlowKit!",
                    MessageType.Success
                )
            }
        ) {
            Text("Show Success Message")
        }

        Button(
            onClick = {
                platformServices.hapticProvider.performHaptic(HapticType.Light)
            }
        ) {
            Text("Light Haptic")
        }

        Button(
            onClick = {
                platformServices.navigator.navigate("details")
            }
        ) {
            Text("Navigate to Details")
        }

        Button(
            onClick = {
                platformServices.dialogProvider.showDialog(
                    title = "Confirm",
                    message = "Are you sure?",
                    onConfirm = { println("Confirmed") },
                    onDismiss = { println("Dismissed") }
                )
            }
        ) {
            Text("Show Dialog")
        }
    }
}

/**
 * Example 4: MVI with platform integration
 */

// Your app's MVI contracts (not part of SDK)
data class ExampleState(
    val count: Int = 0,
    val isLoading: Boolean = false
) : MviState

sealed class ExampleIntent : MviIntent {
    object Increment : ExampleIntent()
    object Decrement : ExampleIntent()
    object ShowMessage : ExampleIntent()
}

sealed class ExampleSideEffect : MviSideEffect {
    data class ShowSuccess(val message: String) : ExampleSideEffect()
    object TriggerHaptic : ExampleSideEffect()
}

// Your app's reducer (not part of SDK)
class ExampleReducer : MviReducer<ExampleState, ExampleIntent, ExampleSideEffect> {
    override suspend fun reduce(
        currentState: ExampleState,
        intent: ExampleIntent
    ): ReducerResult<ExampleState, ExampleSideEffect> {
        return when (intent) {
            ExampleIntent.Increment -> ReducerResult(
                newState = currentState.copy(count = currentState.count + 1),
                sideEffects = listOf(
                    ExampleSideEffect.ShowSuccess("Count incremented!"),
                    ExampleSideEffect.TriggerHaptic
                )
            )
            ExampleIntent.Decrement -> ReducerResult(
                newState = currentState.copy(count = currentState.count - 1),
                sideEffects = listOf(ExampleSideEffect.TriggerHaptic)
            )
            ExampleIntent.ShowMessage -> ReducerResult(
                newState = currentState,
                sideEffects = listOf(ExampleSideEffect.ShowSuccess("Hello!"))
            )
        }
    }
}

// Your app's side effect converter (not part of SDK)
class ExampleSideEffectConverter : SideEffectToPlatformActionConverter<ExampleSideEffect> {
    override fun convert(
        sideEffect: ExampleSideEffect,
        platformServices: PlatformServices
    ): PlatformAction? {
        return when (sideEffect) {
            is ExampleSideEffect.ShowSuccess -> object : PlatformAction {
                override suspend fun execute() {
                    platformServices.messageDisplayer.showMessage(
                        sideEffect.message,
                        MessageType.Success
                    )
                }
            }
            ExampleSideEffect.TriggerHaptic -> object : PlatformAction {
                override suspend fun execute() {
                    platformServices.hapticProvider.performHaptic(HapticType.Light)
                }
            }
        }
    }
}

/**
 * Example MVI screen using FlowKit SDK
 */
@Composable
fun ExampleMviScreen() {
    Screen(
        initialState = ExampleState(),
        reducer = ExampleReducer(),
        sideEffectHandler = { sideEffectsFlow ->
            HandleSideEffectsWithPlatform(
                sideEffectsFlow = sideEffectsFlow,
                converter = ExampleSideEffectConverter()
            )
        }
    ) { state, onIntent ->

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                text = "Count: ${state.count}",
                style = MaterialTheme.typography.headlineMedium
            )

            Spacer(modifier = Modifier.height(16.dp))

            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                Button(onClick = { onIntent(ExampleIntent.Increment) }) {
                    Text("Increment")
                }

                Button(onClick = { onIntent(ExampleIntent.Decrement) }) {
                    Text("Decrement")
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            Button(onClick = { onIntent(ExampleIntent.ShowMessage) }) {
                Text("Show Message")
            }
        }
    }
}

/**
 * Example 5: Simple usage without MVI
 */
@Composable
fun SimpleUsageExample() {
    // Just using state collection
    val dataFlow: Flow<UiState<String>> = flowOf(UiState.Success("Hello FlowKit!"))
    val state by dataFlow.collectAsUiState()

    AsyncContent(state = state) { data ->
        Text(text = data)
    }
}