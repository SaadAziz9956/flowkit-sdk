package io.flowkit.compose

import android.content.Context
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.widget.Toast
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import androidx.navigation.NavController
import androidx.navigation.compose.rememberNavController

/**
 * 🤖 Android Platform Services Implementation
 */

/**
 * Android message displayer using Toast
 */
class AndroidMessageDisplayer(private val context: Context) : MessageDisplayer {
    override fun showMessage(message: String, type: MessageType) {
        val duration = Toast.LENGTH_SHORT
        Toast.makeText(context, message, duration).show()
    }
}

/**
 * Android navigator using Navigation Component
 */
class AndroidNavigator(private val navController: NavController) : Navigator {
    override fun navigate(destination: String) {
        try {
            navController.navigate(destination)
        } catch (e: Exception) {
            println("Navigation failed: ${e.message}")
        }
    }

    override fun goBack(): Boolean {
        return try {
            navController.popBackStack()
        } catch (e: Exception) {
            false
        }
    }

    override fun getCurrentDestination(): String? {
        return navController.currentDestination?.route
    }
}

/**
 * Android haptic provider using Vibrator
 */
class AndroidHapticProvider(private val context: Context) : HapticProvider {
    private val vibrator = context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator

    override fun performHaptic(type: HapticType) {
        vibrator?.let { vib ->
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val effect = when (type) {
                    HapticType.Light -> VibrationEffect.createOneShot(50, VibrationEffect.DEFAULT_AMPLITUDE)
                    HapticType.Medium -> VibrationEffect.createOneShot(100, VibrationEffect.DEFAULT_AMPLITUDE)
                    HapticType.Heavy -> VibrationEffect.createOneShot(200, VibrationEffect.DEFAULT_AMPLITUDE)
                }
                vib.vibrate(effect)
            } else {
                @Suppress("DEPRECATION")
                val duration = when (type) {
                    HapticType.Light -> 50L
                    HapticType.Medium -> 100L
                    HapticType.Heavy -> 200L
                }
                vib.vibrate(duration)
            }
        }
    }
}

/**
 * Android dialog provider (basic implementation)
 */
class AndroidDialogProvider : DialogProvider {
    override fun showDialog(
        title: String,
        message: String,
        onConfirm: (() -> Unit)?,
        onDismiss: (() -> Unit)?
    ) {
        // In a real implementation, you'd use AlertDialog
        // For now, we'll just log - apps should provide their own dialog implementation
        println("Android Dialog: $title - $message")
        onConfirm?.invoke()
    }
}

/**
 * Android loading provider (basic implementation)
 */
class AndroidLoadingProvider : LoadingProvider {
    override fun showLoading(message: String?) {
        // In a real implementation, you'd show a loading dialog
        println("Android Loading: ${message ?: "Loading..."}")
    }

    override fun hideLoading() {
        println("Android: Hide loading")
    }
}

/**
 * Android platform services implementation
 */
class AndroidPlatformServices(
    override val messageDisplayer: MessageDisplayer,
    override val navigator: Navigator,
    override val hapticProvider: HapticProvider,
    override val dialogProvider: DialogProvider,
    override val loadingProvider: LoadingProvider
) : PlatformServices

/**
 * Android actual implementation
 */
@Composable
actual fun rememberPlatformServices(): PlatformServices {
    val context = LocalContext.current
    val navController = rememberNavController()

    return remember(context, navController) {
        AndroidPlatformServices(
            messageDisplayer = AndroidMessageDisplayer(context),
            navigator = AndroidNavigator(navController),
            hapticProvider = AndroidHapticProvider(context),
            dialogProvider = AndroidDialogProvider(),
            loadingProvider = AndroidLoadingProvider()
        )
    }
}