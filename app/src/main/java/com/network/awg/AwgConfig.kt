package com.network.awg

import android.net.Uri
import java.net.URLDecoder

data class AwgConfig(
    val privateKey: String = "",
    val address: String = "",
    val dns: String = "1.1.1.1",
    val mtu: Int = 1280,
    val publicKey: String = "",
    val endpoint: String = "",
    val allowedIps: String = "0.0.0.0/0, ::/0",
    val jc: Int = 0,
    val jmin: Int = 0,
    val jmax: Int = 0,
    val s1: Int = 0,
    val s2: Int = 0,
    val h1: String = "",
    val h2: String = "",
    val h3: String = "",
    val h4: String = ""
) {
    companion object {
        fun parse(rawInput: String): AwgConfig {
            val text = rawInput.trim()

            // اگر کانفیگ به صورت لینک باشد (wg:// یا awg:// یا wireguard://)
            if (text.startsWith("wg://", ignoreCase = true) || 
                text.startsWith("awg://", ignoreCase = true) ||
                text.startsWith("wireguard://", ignoreCase = true)) {
                return parseUri(text)
            }

            // در غیر این صورت به عنوان فایل استاندارد .conf پارس شود
            return parseConf(text)
        }

        private fun parseUri(uriString: String): AwgConfig {
            return try {
                val uri = Uri.parse(uriString)
                val endpoint = if (uri.port != -1) "${uri.host}:${uri.port}" else (uri.host ?: "")

                fun getParam(vararg keys: String): String {
                    for (k in keys) {
                        val v = uri.getQueryParameter(k)
                        if (!v.isNullOrEmpty()) return URLDecoder.decode(v, "UTF-8")
                    }
                    return ""
                }

                AwgConfig(
                    endpoint = endpoint,
                    privateKey = getParam("private_key", "privkey", "privatekey"),
                    publicKey = getParam("public_key", "pubkey", "publickey"),
                    address = getParam("local_address", "address", "ip").ifEmpty { "10.66.66.2/24" },
                    dns = getParam("dns").ifEmpty { "1.1.1.1" },
                    mtu = getParam("mtu").toIntOrNull() ?: 1280,
                    allowedIps = getParam("allowed_ips", "allowedips").ifEmpty { "0.0.0.0/0, ::/0" },
                    jc = getParam("jc").toIntOrNull() ?: 0,
                    jmin = getParam("jmin").toIntOrNull() ?: 0,
                    jmax = getParam("jmax").toIntOrNull() ?: 0,
                    s1 = getParam("s1").toIntOrNull() ?: 0,
                    s2 = getParam("s2").toIntOrNull() ?: 0,
                    h1 = getParam("h1"),
                    h2 = getParam("h2"),
                    h3 = getParam("h3"),
                    h4 = getParam("h4")
                )
            } catch (e: Exception) {
                AwgConfig()
            }
        }

        private fun parseConf(confText: String): AwgConfig {
            var privateKey = ""
            var address = ""
            var dns = "1.1.1.1"
            var mtu = 1280
            var publicKey = ""
            var endpoint = ""
            var allowedIps = "0.0.0.0/0, ::/0"
            var jc = 0
            var jmin = 0
            var jmax = 0
            var s1 = 0
            var s2 = 0
            var h1 = ""
            var h2 = ""
            var h3 = ""
            var h4 = ""

            confText.lines().forEach { line ->
                val clean = line.trim()
                if (clean.contains("=") && !clean.startsWith("#")) {
                    val parts = clean.split("=", limit = 2)
                    val key = parts[0].trim().lowercase()
                    val value = parts[1].trim()

                    when (key) {
                        "privatekey" -> privateKey = value
                        "address" -> address = value
                        "dns" -> dns = value
                        "mtu" -> mtu = value.toIntOrNull() ?: 1280
                        "publickey" -> publicKey = value
                        "endpoint" -> endpoint = value
                        "allowedips" -> allowedIps = value
                        "jc" -> jc = value.toIntOrNull() ?: 0
                        "jmin" -> jmin = value.toIntOrNull() ?: 0
                        "jmax" -> jmax = value.toIntOrNull() ?: 0
                        "s1" -> s1 = value.toIntOrNull() ?: 0
                        "s2" -> s2 = value.toIntOrNull() ?: 0
                        "h1" -> h1 = value
                        "h2" -> h2 = value
                        "h3" -> h3 = value
                        "h4" -> h4 = value
                    }
                }
            }
            return AwgConfig(
                privateKey = privateKey,
                address = address.ifEmpty { "10.66.66.2/24" },
                dns = dns,
                mtu = mtu,
                publicKey = publicKey,
                endpoint = endpoint,
                allowedIps = allowedIps,
                jc = jc,
                jmin = jmin,
                jmax = jmax,
                s1 = s1,
                s2 = s2,
                h1 = h1,
                h2 = h2,
                h3 = h3,
                h4 = h4
            )
        }
    }
}
