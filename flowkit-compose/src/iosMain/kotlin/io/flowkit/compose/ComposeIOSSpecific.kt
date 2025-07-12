package io.flowkit.compose

import androidx.compose.runtime.*
import platform.UIKit.*

/**
 * 🍎 iOS Platform Services Implementation
 */

/**
 * iOS message displayer using alerts
 */
class IOSMessageDisplayer : MessageDisplayer {
    override fun showMessage(message: String, type: MessageType) {
        val viewController = UIApplication.sharedApplication.keyWindow?.rootViewController

        if (viewController != null) {
            val alertTitle = when (type) {
                MessageType.Info -> "Info"
                MessageType.Success -> "Success"
                MessageType.Warning -> "Warning"
                MessageType.Error -> "Error"
            }

            val alert = UIAlertController.alertControllerWithTitle(
                title = alertTitle,
                message = message,
                preferredStyle = UIAlertControllerStyleAlert
            )

            val okAction = UIAlertAction.actionWithTitle(
                title = "OK",
                style = UIAlertActionStyleDefault,
                handler = null
            )
            alert.addAction(okAction)

            viewController.presentViewController(alert, animated = true, completion = null)
        } else {
            // Fallback to console
            println("iOS Message ($type): $message")
        }
    }
}

/**
 * iOS navigator (simple implementation)
 */
class IOSNavigator : Navigator {
    private val navigationStack = mutableListOf<String>()
    private var currentDestination: String? = null

    override fun navigate(destination: String) {
        currentDestination?.let { navigationStack.add(it) }
        currentDestination = destination
        println("iOS Navigation: -> $destination")
    }

    override fun goBack(): Boolean {
        return if (navigationStack.isNotEmpty()) {
            currentDestination = navigationStack.removeLastOrNull()
            println("iOS Navigation: <- $currentDestination")
            true
        } else {
            false
        }
    }

    override fun getCurrentDestination(): String? = currentDestination
}

/**
 * iOS haptic provider using UIImpactFeedbackGenerator
 */
class IOSHapticProvider : HapticProvider {
    override fun performHaptic(type: HapticType) {
        when (type) {
            HapticType.Light -> {
                val generator = UIImpactFeedbackGenerator(UIImpactFeedbackStyle.UIImpactFeedbackStyleLight)
                generator.prepare()
                generator.impactOccurred()
            }
            HapticType.Medium -> {
                val generator = UIImpactFeedbackGenerator(UIImpactFeedbackStyle.UIImpactFeedbackStyleMedium)
                generator.prepare()
                generator.impactOccurred()
            }
            HapticType.Heavy -> {
                val generator = UIImpactFeedbackGenerator(UIImpactFeedbackStyle.UIImpactFeedbackStyleHeavy)
                generator.prepare()
                generator.impactOccurred()
            }
        }
    }
}

/**
 * iOS dialog provider using UIAlertController
 */
class IOSDialogProvider : DialogProvider {
    override fun showDialog(
        title: String,
        message: String,
        onConfirm: (() -> Unit)?,
        onDismiss: (() -> Unit)?
    ) {
        val viewController = UIApplication.sharedApplication.keyWindow?.rootViewController

        if (viewController != null) {
            val alert = UIAlertController.alertControllerWithTitle(
                title = title,
                message = message,
                preferredStyle = UIAlertControllerStyleAlert
            )

            // Confirm action
            if (onConfirm != null) {
                val confirmAction = UIAlertAction.actionWithTitle(
                    title = "OK",
                    style = UIAlertActionStyleDefault
                ) { _ ->
                    onConfirm.invoke()
                }
                alert.addAction(confirmAction)
            }

            // Dismiss action
            if (onDismiss != null) {
                val dismissAction = UIAlertAction.actionWithTitle(
                    title = "Cancel",
                    style = UIAlertActionStyleCancel
                ) { _ ->
                    onDismiss.invoke()
                }
                alert.addAction(dismissAction)
            }

            // If no actions provided, add default OK
            if (onConfirm == null && onDismiss == null) {
                val okAction = UIAlertAction.actionWithTitle(
                    title = "OK",
                    style = UIAlertActionStyleDefault,
                    handler = null
                )
                alert.addAction(okAction)
            }

            viewController.presentViewController(alert, animated = true, completion = null)
        } else {
            println("iOS Dialog: $title - $message")
        }
    }
}

/**
 * iOS loading provider (basic implementation)
 */
class IOSLoadingProvider : LoadingProvider {
    override fun showLoading(message: String?) {
        // In a real implementation, you'd show a loading spinner
        println("iOS Loading: ${message ?: "Loading..."}")
    }

    override fun hideLoading() {
        println("iOS: Hide loading")
    }
}

/**
 * iOS platform services implementation
 */
class IOSPlatformServices(
    override val messageDisplayer: MessageDisplayer,
    override val navigator: Navigator,
    override val hapticProvider: HapticProvider,
    override val dialogProvider: DialogProvider,
    override val loadingProvider: LoadingProvider
) : PlatformServices

/**
 * iOS actual implementation
 */
@Composable
actual fun rememberPlatformServices(): PlatformServices {
    return remember {
        IOSPlatformServices(
            messageDisplayer = IOSMessageDisplayer(),
            navigator = IOSNavigator(),
            hapticProvider = IOSHapticProvider(),
            dialogProvider = IOSDialogProvider(),
            loadingProvider = IOSLoadingProvider()
        )
    }
}