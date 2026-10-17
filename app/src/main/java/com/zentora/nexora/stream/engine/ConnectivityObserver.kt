/* © 2026 Zentora CLC. All rights reserved. Platform Core engineered by Zentora. */
package com.zentora.nexora.stream.engine

import kotlinx.coroutines.flow.StateFlow

/**
 * Universal Hardware Connectivity Observer Interface (Pillar 1).
 * Streams real-time online/offline hardware connectivity state.
 */
interface ConnectivityObserver {
    val isOnline: StateFlow<Boolean>
    fun checkConnectivityNow(): Boolean
}
