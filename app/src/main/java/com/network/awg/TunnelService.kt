package com.network.awg

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Intent
import android.net.TrafficStats
import android.net.VpnService
import android.os.Build
import androidx.core.app.NotificationCompat
import com.wireguard.android.backend.GoBackend
import com.wireguard.android.backend.Tunnel
import com.wireguard.config.Config
import com.wireguard.config.Interface
import com.wireguard.config.Peer
import com.wireguard.crypto.Key
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.ByteArrayInputStream
import java.net.InetAddress

class TunnelService : VpnService() {

    private val serviceScope = CoroutineScope(Dispatchers.IO + Job())
    private var trafficJob: Job? = null
    private var backend: GoBackend? = null

    // آبجکت تونل برای مدیریت وضعیت بک‌اند
    private val awgTunnel = object : Tunnel {
        override fun getName(): String = "awg0"
        override fun onStateChange(newState: Tunnel.State) {
            _isRunning.value = (newState == Tunnel.State.UP)
        }
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
        backend = GoBackend(applicationContext)
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
                startForeground(1, createNotification("در حال برقراری تونل امن..."))

                // ساخت کانفیگ استاندارد WireGuard برای موتور GoBackend
                val confBuilder = StringBuilder()
                confBuilder.append("[Interface]\n")
                confBuilder.append("PrivateKey = ${awgConfig.privateKey.trim()}\n")
                confBuilder.append("Address = ${awgConfig.address.trim().ifEmpty { "10.66.66.2/24" }}\n")
                confBuilder.append("DNS = ${awgConfig.dns.trim().ifEmpty { "1.1.1.1, 8.8.8.8" }}\n")
                confBuilder.append("MTU = ${if (awgConfig.mtu in 1200..1500) awgConfig.mtu else 1280}\n")

                confBuilder.append("\n[Peer]\n")
                confBuilder.append("PublicKey = ${awgConfig.publicKey.trim()}\n")
                confBuilder.append("Endpoint = ${awgConfig.endpoint.trim()}\n")
                confBuilder.append("AllowedIPs = ${awgConfig.allowedIps.trim().ifEmpty { "0.0.0.0/0, ::/0" }}\n")
                confBuilder.append("PersistentKeepalive = 25\n")

                val wgConfig = Config.parse(ByteArrayInputStream(confBuilder.toString().toByteArray()))

                // استارت هسته شبکه بومی برای هدایت و رمزنگاری پکت‌ها
                backend?.setState(awgTunnel, Tunnel.State.UP, wgConfig)

                _isRunning.value = true
                startForeground(1, createNotification("متصل به تونل WireGuard"))
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
