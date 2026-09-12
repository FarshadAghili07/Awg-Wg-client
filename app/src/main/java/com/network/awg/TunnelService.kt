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
import java.io.FileInputStream
import java.io.FileOutputStream
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetSocketAddress

class TunnelService : VpnService() {

    private var vpnInterface: ParcelFileDescriptor? = null
    private val serviceScope = CoroutineScope(Dispatchers.IO + Job())
    private var trafficJob: Job? = null
    private var tunnelJob: Job? = null
    private var udpSocket: DatagramSocket? = null

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

            val mtu = if (config.mtu in 1200..1500) config.mtu else 1280
            val builder = Builder()
                .setSession("AWG Tunnel")
                .setMtu(mtu)
                .setBlocking(false)

            // آی‌پی اینترفیس مجازی
            var hasAddress = false
            if (config.address.isNotBlank()) {
                config.address.split(",").forEach { addrStr ->
                    val part = addrStr.trim()
                    if (part.isNotEmpty()) {
                        val ipParts = part.split("/")
                        val ip = ipParts[0].trim()
                        val prefix = if (ipParts.size > 1) ipParts[1].trim().toIntOrNull() ?: 24 else 24
                        try {
                            builder.addAddress(ip, prefix)
                            hasAddress = true
                        } catch (_: Exception) {}
                    }
                }
            }
            if (!hasAddress) {
                builder.addAddress("10.66.66.2", 24)
            }

            // دی‌ان‌اس معتبر
            var hasDns = false
            if (config.dns.isNotBlank()) {
                config.dns.split(",").forEach { dnsStr ->
                    val dns = dnsStr.trim()
                    if (dns.isNotEmpty()) {
                        try {
                            builder.addDnsServer(dns)
                            hasDns = true
                        } catch (_: Exception) {}
                    }
                }
            }
            if (!hasDns) {
                builder.addDnsServer("1.1.1.1")
                builder.addDnsServer("8.8.8.8")
            }

            // هدایت تمامی ترافیک به وی‌پی‌ان
            builder.addRoute("0.0.0.0", 0)
            try {
                builder.addRoute("::", 0)
            } catch (_: Exception) {}

            // برنامه‌های مستثنی شده (Split Tunneling)
            disallowedApps.forEach { pkg ->
                try {
                    builder.addDisallowedApplication(pkg)
                } catch (_: Exception) {}
            }

            vpnInterface = builder.establish()

            if (vpnInterface != null) {
                _isRunning.value = true
                startForeground(1, createNotification("متصل به تونل AmneziaWG"))
                startPacketForwarding(config)
                startSpeedMonitoring()
            } else {
                stopTunnel()
            }
        } catch (e: Exception) {
            stopTunnel()
        }
    }

    private fun startPacketForwarding(config: AwgConfig) {
        tunnelJob?.cancel()
        tunnelJob = serviceScope.launch {
            val pfd = vpnInterface ?: return@launch
            val epParts = config.endpoint.split(":")
            if (epParts.isEmpty() || epParts[0].isBlank()) return@launch

            val host = epParts[0].trim()
            val port = if (epParts.size > 1) epParts[1].trim().toIntOrNull() ?: 51820 else 51820

            try {
                val socket = DatagramSocket()
                protect(socket)
                socket.connect(InetSocketAddress(host, port))
                udpSocket = socket

                val vpnIn = FileInputStream(pfd.fileDescriptor)
                val vpnOut = FileOutputStream(pfd.fileDescriptor)

                val upJob = launch {
                    val buffer = ByteArray(32767)
                    while (isActive) {
                        val len = vpnIn.read(buffer)
                        if (len > 0) {
                            val packet = DatagramPacket(buffer, len)
                            socket.send(packet)
                        }
                    }
                }

                val downJob = launch {
                    val buffer = ByteArray(32767)
                    val packet = DatagramPacket(buffer, buffer.size)
                    while (isActive) {
                        socket.receive(packet)
                        if (packet.length > 0) {
                            vpnOut.write(packet.data, 0, packet.length)
                        }
                    }
                }

                upJob.join()
                downJob.join()
            } catch (_: Exception) {}
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
        tunnelJob?.cancel()

        try {
            udpSocket?.close()
            udpSocket = null
        } catch (_: Exception) {}

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
