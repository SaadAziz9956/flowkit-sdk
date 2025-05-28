package io.flowkit.network

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Simple connectivity monitor implementation
 * Platform-specific implementations should extend this
 */
open class SimpleConnectivityMonitor : NetworkConnectivityMonitor {

    private val _isConnected = MutableStateFlow(true) // Assume connected by default
    override val isConnected: Flow<Boolean> = _isConnected.asStateFlow()

    override suspend fun isCurrentlyConnected(): Boolean {
        return _isConnected.value
    }

    /**
     * Update connectivity status (to be called by platform-specific implementations)
     */
    protected fun updateConnectivity(connected: Boolean) {
        _isConnected.value = connected
    }

    /**
     * Simulate connectivity changes for testing
     */
    fun simulateConnectivityChange(connected: Boolean) {
        updateConnectivity(connected)
    }
}

/**
 * Mock connectivity monitor for testing
 */
class MockConnectivityMonitor(initialState: Boolean = true) : SimpleConnectivityMonitor() {

    init {
        updateConnectivity(initialState)
    }

    fun setConnected(connected: Boolean) {
        updateConnectivity(connected)
    }

    fun simulateConnectionLoss() {
        updateConnectivity(false)
    }

    fun simulateConnectionRestored() {
        updateConnectivity(true)
    }
}

/**
 * Default connectivity monitor instance
 */
val DefaultConnectivityMonitor: NetworkConnectivityMonitor = SimpleConnectivityMonitor()