package com.network.awg

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Intent
import android.net.TrafficStats
import android.net.VpnService
import android.os.Build
import androidx.core.app.NotificationCompat
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.amnezia.awg.backend.Backend
import org.amnezia.awg.backend.GoBackend
import org.amnezia.awg.backend.Tunnel
import org.amnezia.awg.config.Config
import java.io.ByteArrayInputStream

class TunnelService : VpnService() {

    private val serviceScope = CoroutineScope(Dispatchers.IO + Job())
    private var trafficJob: Job? = null
    private var backend: Backend? = null

    private val awgTunnel = object : Tunnel {
        override fun getName(): String = "awg0"
        override fun onStateChange(newState: Tunnel.State) {
            _isRunning.value = (newState == Tunnel.State.UP)
        }
        override fun isIpv4ResolutionPreferred(): Boolean = true
        override fun isMetered(): Boolean = false
    }

    companion object {
        const val ACTION_CONNECT = "com.network.awg.CONNECT"
        const val ACTION_DISCONNECT = "com.network.awg.DISCONNECT"
        const val EXTRA_CONFIG = "extra_config"
        const val EXTRA_DISALLOWED_APPS = "extra_disallowed_apps"

        private val _isRunning = MutableStateFlow(false)
        val isRunning = _isRunning.asStateFlow()

        private val _downloadSpeed = MutableStateFlow(0L)
        val downloadSpeed = _downloadSpeed.asStateFlow()

        private val _uploadSpeed = MutableStateFlow(0L)
        val uploadSpeed = _uploadSpeed.asStateFlow()
    }

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        try {
            backend = GoBackend(applicationContext, null)
        } catch (_: Exception) {
            try {
                val constructor = GoBackend::class.java.constructors.first()
                val args = Array(constructor.parameterTypes.size) { index ->
                    if (index == 0) applicationContext else null
                }
                backend = constructor.newInstance(*args) as Backend
            } catch (_: Exception) {}
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_CONNECT -> {
                val rawConfig = intent.getStringExtra(EXTRA_CONFIG) ?: ""
                val disallowedApps = intent.getStringArrayListExtra(EXTRA_DISALLOWED_APPS) ?: arrayListOf()
                val config = AwgConfig.parse(rawConfig)
                startTunnel(config, disallowedApps)
            }
            ACTION_DISCONNECT -> {
                stopTunnel()
            }
        }
        return START_NOT_STICKY
    }

    private fun startTunnel(awgConfig: AwgConfig, disallowedApps: List<String>) {
        serviceScope.launch {
            try {
                startForeground(1, createNotification("در حال اتصال به هسته AmneziaWG..."))

                val confBuilder = StringBuilder()
                confBuilder.append("[Interface]\n")
                confBuilder.append("PrivateKey = ${awgConfig.privateKey.trim()}\n")
                confBuilder.append("Address = ${awgConfig.address.trim().ifEmpty { "10.66.66.2/24" }}\n")
                confBuilder.append("DNS = ${awgConfig.dns.trim().ifEmpty { "1.1.1.1, 8.8.8.8" }}\n")
                confBuilder.append("MTU = ${if (awgConfig.mtu in 1200..1500) awgConfig.mtu else 1280}\n")

                if (awgConfig.jc > 0) confBuilder.append("Jc = ${awgConfig.jc}\n")
                if (awgConfig.jmin > 0) confBuilder.append("Jmin = ${awgConfig.jmin}\n")
                if (awgConfig.jmax > 0) confBuilder.append("Jmax = ${awgConfig.jmax}\n")
                if (awgConfig.s1 > 0) confBuilder.append("S1 = ${awgConfig.s1}\n")
                if (awgConfig.s2 > 0) confBuilder.append("S2 = ${awgConfig.s2}\n")
                if (awgConfig.h1.isNotBlank()) confBuilder.append("H1 = ${awgConfig.h1.trim()}\n")
                if (awgConfig.h2.isNotBlank()) confBuilder.append("H2 = ${awgConfig.h2.trim()}\n")
                if (awgConfig.h3.isNotBlank()) confBuilder.append("H3 = ${awgConfig.h3.trim()}\n")
                if (awgConfig.h4.isNotBlank()) confBuilder.append("H4 = ${awgConfig.h4.trim()}\n")

                confBuilder.append("\n[Peer]\n")
                confBuilder.append("PublicKey = ${awgConfig.publicKey.trim()}\n")
                confBuilder.append("Endpoint = ${awgConfig.endpoint.trim()}\n")
                confBuilder.append("AllowedIPs = ${awgConfig.allowedIps.trim().ifEmpty { "0.0.0.0/0, ::/0" }}\n")
                confBuilder.append("PersistentKeepalive = 25\n")

                val wgConfig = Config.parse(ByteArrayInputStream(confBuilder.toString().toByteArray()))

                backend?.setState(awgTunnel, Tunnel.State.UP, wgConfig)

                _isRunning.value = true
                startForeground(1, createNotification("متصل به تونل AmneziaWG"))
                startSpeedMonitoring()

            } catch (e: Exception) {
                stopTunnel()
            }
        }
    }

    private fun startSpeedMonitoring() {
        trafficJob?.cancel()
        trafficJob = serviceScope.launch {
            var lastRx = TrafficStats.getTotalRxBytes()
            var lastTx = TrafficStats.getTotalTxBytes()

            while (isActive && _isRunning.value) {
                delay(1000)
                val currentRx = TrafficStats.getTotalRxBytes()
                val currentTx = TrafficStats.getTotalTxBytes()

                val rxSpeed = if (lastRx > 0 && currentRx >= lastRx) currentRx - lastRx else 0L
                val txSpeed = if (lastTx > 0 && currentTx >= lastTx) currentTx - lastTx else 0L

                _downloadSpeed.value = rxSpeed
                _uploadSpeed.value = txSpeed

                lastRx = currentRx
                lastTx = currentTx
            }
        }
    }

    private fun stopTunnel() {
        trafficJob?.cancel()
        serviceScope.launch {
            try {
                backend?.setState(awgTunnel, Tunnel.State.DOWN, null)
            } catch (_: Exception) {}
        }

        _isRunning.value = false
        _downloadSpeed.value = 0L
        _uploadSpeed.value = 0L
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    override fun onDestroy() {
        serviceScope.cancel()
        stopTunnel()
        super.onDestroy()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                "awg_tunnel_channel",
                "AWG Tunnel Status",
                NotificationManager.IMPORTANCE_LOW
            )
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
        }
    }

    private fun createNotification(statusText: String): Notification {
        return NotificationCompat.Builder(this, "awg_tunnel_channel")
            .setContentTitle("AmneziaWG Client")
            .setContentText(statusText)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setOngoing(true)
            .build()
    }
}
