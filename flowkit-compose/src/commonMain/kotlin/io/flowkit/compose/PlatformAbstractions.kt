package io.flowkit.compose

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import io.flowkit.core.MviSideEffect
import kotlinx.coroutines.flow.Flow

/**
 * 🌍 FlowKit Compose - Platform Abstractions
 * Generic platform service interfaces
 */

/**
 * Generic platform action interface
 */
interface PlatformAction {
    suspend fun execute()
}

/**
 * Message display abstraction
 */
interface MessageDisplayer {
    fun showMessage(message: String, type: MessageType)
}

enum class MessageType { Info, Success, Warning, Error }

/**
 * Navigation abstraction
 */
interface Navigator {
    fun navigate(destination: String)
    fun goBack(): Boolean
    fun getCurrentDestination(): String?
}

/**
 * Haptic feedback abstraction
 */
interface HapticProvider {
    fun performHaptic(type: HapticType)
}

enum class HapticType { Light, Medium, Heavy }

/**
 * Dialog display abstraction
 */
interface DialogProvider {
    fun showDialog(
        title: String,
        message: String,
        onConfirm: (() -> Unit)? = null,
        onDismiss: (() -> Unit)? = null
    )
}

/**
 * Loading display abstraction
 */
interface LoadingProvider {
    fun showLoading(message: String? = null)
    fun hideLoading()
}

/**
 * Platform services provider - all platform capabilities in one place
 */
interface PlatformServices {
    val messageDisplayer: MessageDisplayer
    val navigator: Navigator
    val hapticProvider: HapticProvider
    val dialogProvider: DialogProvider
    val loadingProvider: LoadingProvider
}

/**
 * Platform-specific implementations (expect/actual)
 */
@Composable
expect fun rememberPlatformServices(): PlatformServices

/**
 * Generic platform action executor
 */
@Composable
fun HandlePlatformActions(
    actionsFlow: Flow<PlatformAction>
) {
    LaunchedEffect(Unit) {
        actionsFlow.collect { action ->
            try {
                action.execute()
            } catch (e: Exception) {
                // Handle gracefully - this is generic SDK behavior
                println("FlowKit: Platform action failed: ${e.message}")
            }
        }
    }
}

/**
 * Side effect to platform action converter
 */
interface SideEffectToPlatformActionConverter<SideEffect : MviSideEffect> {
    fun convert(sideEffect: SideEffect, platformServices: PlatformServices): PlatformAction?
}

/**
 * Generic side effect handler using platform services
 */
@Composable
fun <SideEffect : MviSideEffect> HandleSideEffectsWithPlatform(
    sideEffectsFlow: Flow<SideEffect>,
    converter: SideEffectToPlatformActionConverter<SideEffect>
) {
    val platformServices = rememberPlatformServices()

    LaunchedEffect(Unit) {
        sideEffectsFlow.collect { sideEffect ->
            converter.convert(sideEffect, platformServices)?.execute()
        }
    }
}