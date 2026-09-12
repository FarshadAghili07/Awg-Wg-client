package com.network.awg

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Intent
import android.net.TrafficStats
import android.net.VpnService
import android.os.Build
import android.os.ParcelFileDescriptor
import androidx.core.app.NotificationCompat
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.net.InetAddress

class TunnelService : VpnService() {

    private var vpnInterface: ParcelFileDescriptor? = null
    private val serviceScope = CoroutineScope(Dispatchers.IO + Job())
    private var trafficJob: Job? = null

    companion object {
        const val ACTION_CONNECT = "com.network.awg.CONNECT"
        const val ACTION_DISCONNECT = "com.network.awg.DISCONNECT"
        const val EXTRA_CONFIG = "extra_config"
        const val EXTRA_DISALLOWED_APPS = "extra_disallowed_apps"

        private val _isRunning = MutableStateFlow(false)
        val isRunning = _isRunning.asStateFlow()

        private val _downloadSpeed = MutableStateFlow(0L) // Bytes per second
        val downloadSpeed = _downloadSpeed.asStateFlow()

        private val _uploadSpeed = MutableStateFlow(0L) // Bytes per second
        val uploadSpeed = _uploadSpeed.asStateFlow()
    }

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
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

    private fun startTunnel(config: AwgConfig, disallowedApps: List<String>) {
        try {
            startForeground(1, createNotification("در حال اتصال..."))

            val builder = Builder()
                .setSession("AWG Tunnel")
                .setMtu(config.mtu)

            // اعمال آدرس IP
            config.address.split(",").forEach { addrStr ->
                val part = addrStr.trim()
                if (part.isNotEmpty()) {
                    val ipParts = part.split("/")
                    val ip = ipParts[0]
                    val prefix = if (ipParts.size > 1) ipParts[1].toIntOrNull() ?: 32 else 32
                    builder.addAddress(ip, prefix)
                }
            }

            // اعمال DNS
            config.dns.split(",").forEach { dnsStr ->
                val dns = dnsStr.trim()
                if (dns.isNotEmpty()) {
                    builder.addDnsServer(dns)
                }
            }

            // اعمال مسیردهی ترافیک (AllowedIPs)
            config.allowedIps.split(",").forEach { routeStr ->
                val route = routeStr.trim()
                if (route.isNotEmpty()) {
                    val parts = route.split("/")
                    val ip = parts[0]
                    val prefix = if (parts.size > 1) parts[1].toIntOrNull() ?: 0 else 0
                    try {
                        builder.addRoute(ip, prefix)
                    } catch (_: Exception) {}
                }
            }

            // اعمال Split Tunneling (استثنا کردن اپلیکیشن‌های انتخاب شده)
            disallowedApps.forEach { pkg ->
                try {
                    builder.addDisallowedApplication(pkg)
                } catch (_: Exception) {}
            }

            // احراز شرایط تونل و باز کردن سوکت
            vpnInterface = builder.establish()

            if (vpnInterface != null) {
                _isRunning.value = true
                startForeground(1, createNotification("متصل به تونل AmneziaWG"))
                startSpeedMonitoring()
            } else {
                stopTunnel()
            }
        } catch (e: Exception) {
            stopTunnel()
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
        try {
            vpnInterface?.close()
            vpnInterface = null
        } catch (_: Exception) {}

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

