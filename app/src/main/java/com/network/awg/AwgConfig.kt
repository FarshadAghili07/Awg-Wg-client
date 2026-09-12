package com.network.awg

data class AwgConfig(
    val privateKey: String = "",
    val address: String = "",
    val dns: String = "1.1.1.1",
    val mtu: Int = 1420,
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
        fun parse(rawText: String): AwgConfig {
            var privateKey = ""
            var address = ""
            var dns = "1.1.1.1"
            var mtu = 1420
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

            rawText.lines().forEach { line ->
                val clean = line.trim()
                if (clean.contains("=") && !clean.startsWith("#")) {
                    val parts = clean.split("=", limit = 2)
                    val key = parts[0].trim().lowercase()
                    val value = parts[1].trim()

                    when (key) {
                        "privatekey" -> privateKey = value
                        "address" -> address = value
                        "dns" -> dns = value
                        "mtu" -> mtu = value.toIntOrNull() ?: 1420
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
                address = address,
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

