/* © 2026 Zentora CLC. All rights reserved. Platform Core engineered by Zentora. */
package com.zentora.nexora.stream.engine

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.os.Build
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * Real-time Hardware Network Monitor Service (ConnectivityManager.NetworkCallback).
 * Continuously observes NET_CAPABILITY_INTERNET and NET_CAPABILITY_VALIDATED in real time
 * across Wi-Fi, Cellular, and Ethernet transports.
 */
class NetworkConnectivityManager(context: Context) : ConnectivityObserver {

    private val connectivityManager =
        context.applicationContext.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager

    private val scope = CoroutineScope(Dispatchers.IO)
    private val _isOnline = MutableStateFlow(isHardwareConnected())
    override val isOnline: StateFlow<Boolean> = _isOnline.asStateFlow()

    private val networkCallback = object : ConnectivityManager.NetworkCallback() {
        override fun onAvailable(network: Network) {
            updateNetworkState()
        }

        override fun onCapabilitiesChanged(network: Network, networkCapabilities: NetworkCapabilities) {
            val hasInternet = networkCapabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
            val isValidated = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                networkCapabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
            } else true

            if (hasInternet && isValidated) {
                _isOnline.value = true
            } else {
                updateNetworkState()
            }
        }

        override fun onLost(network: Network) {
            updateNetworkState()
        }

        override fun onUnavailable() {
            _isOnline.value = false
        }
    }

    init {
        registerCallback()
    }

    private fun registerCallback() {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                connectivityManager.registerDefaultNetworkCallback(networkCallback)
            } else {
                val request = NetworkRequest.Builder()
                    .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
                    .build()
                connectivityManager.registerNetworkCallback(request, networkCallback)
            }
        } catch (_: Exception) {
            updateNetworkState()
        }
    }

    private fun updateNetworkState() {
        scope.launch {
            _isOnline.value = isHardwareConnected()
        }
    }

    override fun checkConnectivityNow(): Boolean {
        val currentStatus = isHardwareConnected()
        _isOnline.value = currentStatus
        return currentStatus
    }

    private fun isHardwareConnected(): Boolean {
        val activeNetwork = connectivityManager.activeNetwork ?: return false
        val capabilities = connectivityManager.getNetworkCapabilities(activeNetwork) ?: return false

        val hasInternetCapability = capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
        val isNotSuspended = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_SUSPENDED)
        } else true

        val isTransportValid = capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) ||
                capabilities.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) ||
                capabilities.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET)

        return hasInternetCapability && isNotSuspended && isTransportValid
    }
}
