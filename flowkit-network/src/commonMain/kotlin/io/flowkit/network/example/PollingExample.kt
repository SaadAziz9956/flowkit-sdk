package io.flowkit.network.example

import io.flowkit.network.fold
import io.flowkit.network.networkPolling
import io.flowkit.network.onNetworkSuccess
import kotlin.time.Duration.Companion.seconds

suspend fun pollingExample() {
    val pollingFlow = networkPolling(
        interval = 5.seconds,
        request = {
            // Simulate periodic data fetch (like notifications)
            "Notification count: ${(1..10).random()}"
        }
    )

    pollingFlow
        .onNetworkSuccess { data ->
            println("🔔 $data")
        }
        .collect { result ->
            result.fold(
                onFailure = { error -> println("❌ Polling failed: ${error.message}") },
                onSuccess = { /* Success handled above */ },
                onLoading = { println("🔄 Checking for updates...") }
            )
        }
}
