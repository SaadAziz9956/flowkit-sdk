package io.flowkit.network.example

import io.flowkit.network.NetworkError
import io.flowkit.network.fold
import io.flowkit.network.networkFlow
import io.flowkit.network.onNetworkError
import io.flowkit.network.onNetworkSuccess

suspend fun errorHandlingExample() {
    val errorProneFlow = networkFlow<String> {
        source {
            // Simulate different types of errors
            when ((1..4).random()) {
                1 -> throw NetworkError.NoInternetConnection
                2 -> throw NetworkError.TimeoutError("Request timed out")
                3 -> throw NetworkError.HttpError(404, "Not found")
                else -> "Success!"
            }
        }
        retryOnFailure(maxAttempts = 3) { error ->
            // Only retry on timeout and connection errors
            error is NetworkError.TimeoutError || error is NetworkError.NoInternetConnection
        }
        enableLogging("ErrorExample")
    }

    errorProneFlow
        .onNetworkError { error ->
            println("🚨 Network error occurred: ${error.message}")
        }
        .onNetworkSuccess { data ->
            println("🎉 Success: $data")
        }
        .collect { result ->
            result.fold(
                onFailure = { error -> println("💥 Final error: ${error.message}") },
                onSuccess = { data -> println("✅ Final result: $data") },
                onLoading = { println("⏳ Trying...") }
            )
        }
}