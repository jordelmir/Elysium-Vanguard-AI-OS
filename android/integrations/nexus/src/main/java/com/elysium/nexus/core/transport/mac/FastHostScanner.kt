package com.elysium.nexus.core.transport.mac

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.wifi.WifiManager
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.withContext
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.NetworkInterface
import java.net.Socket
import java.util.Collections

/**
 * Fast Zero-Touch Host Scanner & Network Gateway Probe.
 *
 * Discovers the Mac over network interfaces only. ADB reverse exposes the
 * same 127.0.0.1 endpoint over both USB and wireless ADB, so it cannot be
 * used to classify a Wi-Fi connection as USB-C. The USB-C screen owns that
 * explicit reverse route; this scanner checks a remembered network address,
 * network gateways, then the local Wi-Fi subnet.
 */
object FastHostScanner {

    private const val TAG = "FastHostScanner"
    private const val DEFAULT_PORT = 7878

    private var lastKnownWorkingIp: String? = null

    fun saveWorkingIp(ip: String) {
        if (ip != "127.0.0.1" && ip.isNotBlank()) {
            lastKnownWorkingIp = ip
        }
    }

    /**
     * Probes all available connection channels in parallel and returns
     * the first active DiscoveredHost on port 7878 within [timeoutMs].
     */
    suspend fun findFirstActiveHost(
        context: Context,
        preferredHostIp: String? = null,
        timeoutMs: Long = 2500
    ): DiscoveredHost? = withContext(Dispatchers.IO) {
        val scanStartedAt = android.os.SystemClock.elapsedRealtime()
        val wifiIp = getWifiIpAddress(context)
        val localAddresses = getLocalIpv4Addresses().toMutableSet().apply {
            wifiIp?.let(::add)
        }
        val priorityTargets = mutableListOf<String>()

        // 1. Preferred / Last known working network IP
        val saved = preferredHostIp ?: lastKnownWorkingIp
        if (!saved.isNullOrBlank() && isRemoteCandidate(saved, localAddresses) && saved !in priorityTargets) {
            priorityTargets.add(saved)
        } else if (!saved.isNullOrBlank()) {
            Log.i(TAG, "DISCOVERY_SKIP_LOCAL source=preferred address=$saved")
        }

        // 2. Network interface gateways (including USB tethering/NCM)
        val gateways = getNetworkGateways(context)
        for (gw in gateways) {
            if (!isRemoteCandidate(gw, localAddresses)) {
                Log.d(TAG, "DISCOVERY_SKIP_LOCAL source=gateway address=$gw")
            } else if (gw !in priorityTargets) {
                priorityTargets.add(gw)
            }
        }
        Log.i(TAG, "DISCOVERY_BEGIN port=$DEFAULT_PORT preferredHost=${preferredHostIp ?: "none"} priorityTargets=${priorityTargets.size} localAddressCount=${localAddresses.size} wifiAddress=${wifiIp ?: "unavailable"}")

        // Check high priority targets first with fast 350ms connect timeout
        for (ip in priorityTargets) {
            val open = isPortOpen(ip, DEFAULT_PORT, 350)
            Log.d(TAG, "DISCOVERY_PROBE route=priority target=$ip:$DEFAULT_PORT open=$open timeoutMs=350")
            if (open) {
                Log.i(TAG, "DISCOVERY_RESULT route=priority host=$ip:$DEFAULT_PORT elapsedMs=${android.os.SystemClock.elapsedRealtime() - scanStartedAt}")
                saveWorkingIp(ip)
                return@withContext DiscoveredHost(
                    name = "Mac ($ip)",
                    host = ip,
                    port = DEFAULT_PORT,
                    model = "Mac",
                    osVersion = "macOS",
                    publicKeyB64 = null
                )
            }
        }

        // 3. Parallel LAN subnet sweep on local Wi-Fi
        if (!wifiIp.isNullOrBlank() && wifiIp.contains(".")) {
            val prefix = wifiIp.substringBeforeLast(".")
            val candidateCount = (1..254).count { isRemoteCandidate("$prefix.$it", localAddresses) }
            Log.i(TAG, "DISCOVERY_SUBNET_BEGIN prefix=$prefix.0/24 candidateCount=$candidateCount excludedLocalCount=${254 - candidateCount} timeoutPerHostMs=500 budgetMs=$timeoutMs")
            val scannedIp = scanSubnetParallel(prefix, DEFAULT_PORT, localAddresses, timeoutMs = 500)
            if (!scannedIp.isNullOrBlank()) {
                Log.i(TAG, "DISCOVERY_RESULT route=subnet host=$scannedIp:$DEFAULT_PORT elapsedMs=${android.os.SystemClock.elapsedRealtime() - scanStartedAt}")
                saveWorkingIp(scannedIp)
                return@withContext DiscoveredHost(
                    name = "Mac (Wi-Fi Auto)",
                    host = scannedIp,
                    port = DEFAULT_PORT,
                    model = "Mac",
                    osVersion = "macOS",
                    publicKeyB64 = null
                )
            }
            Log.w(TAG, "DISCOVERY_SUBNET_RESULT activeHost=none elapsedMs=${android.os.SystemClock.elapsedRealtime() - scanStartedAt}")
        } else {
            Log.w(TAG, "DISCOVERY_SUBNET_SKIPPED reason=wifi_ipv4_unavailable")
        }

        Log.w(TAG, "DISCOVERY_RESULT host=none priorityTargets=${priorityTargets.size} elapsedMs=${android.os.SystemClock.elapsedRealtime() - scanStartedAt}")
        return@withContext null
    }

    /**
     * Probes all available connection channels in parallel and returns
     * ALL active DiscoveredHosts on port 7878 within [timeoutMs].
     */
    suspend fun findAllActiveHosts(
        context: Context,
        timeoutMs: Long = 2000
    ): List<DiscoveredHost> = withContext(Dispatchers.IO) {
        val hosts = mutableListOf<DiscoveredHost>()
        val seenIps = mutableSetOf<String>()
        val wifiIp = getWifiIpAddress(context)
        val localAddresses = getLocalIpv4Addresses().toMutableSet().apply {
            wifiIp?.let(::add)
        }

        val priorityTargets = mutableListOf<String>()
        lastKnownWorkingIp?.takeIf { it.isNotBlank() && isRemoteCandidate(it, localAddresses) }
            ?.let { priorityTargets.add(it) }
        val gateways = getNetworkGateways(context)
        for (gw in gateways) {
            if (isRemoteCandidate(gw, localAddresses) && gw !in priorityTargets) {
                priorityTargets.add(gw)
            }
        }

        for (ip in priorityTargets) {
            if (isPortOpen(ip, DEFAULT_PORT, 350)) {
                if (seenIps.add(ip)) {
                    saveWorkingIp(ip)
                    hosts.add(
                        DiscoveredHost(
                            name = "Mac ($ip)",
                            host = ip,
                            port = DEFAULT_PORT,
                            model = "Mac",
                            osVersion = "macOS",
                            publicKeyB64 = null
                        )
                    )
                }
            }
        }

        // Subnet sweep on local Wi-Fi
        if (!wifiIp.isNullOrBlank() && wifiIp.contains(".")) {
            val prefix = wifiIp.substringBeforeLast(".")
            val candidateCount = (1..254).count { isRemoteCandidate("$prefix.$it", localAddresses) }
            Log.i(TAG, "DISCOVERY_SUBNET_BEGIN mode=all prefix=$prefix.0/24 candidateCount=$candidateCount excludedLocalCount=${254 - candidateCount} timeoutPerHostMs=600 budgetMs=$timeoutMs")
            val scannedIps = scanSubnetParallelAll(prefix, DEFAULT_PORT, localAddresses, timeoutMs = 600)
            for (scannedIp in scannedIps) {
                if (seenIps.add(scannedIp)) {
                    saveWorkingIp(scannedIp)
                    hosts.add(
                        DiscoveredHost(
                            name = "Mac (Wi-Fi $scannedIp)",
                            host = scannedIp,
                            port = DEFAULT_PORT,
                            model = "Mac",
                            osVersion = "macOS",
                            publicKeyB64 = null
                        )
                    )
                }
            }
        }

        return@withContext hosts
    }

    /**
     * Scans a /24 subnet (e.g. 192.168.1.1 to 192.168.1.254) in parallel using coroutines.
     */
    private suspend fun scanSubnetParallel(
        subnetPrefix: String,
        port: Int,
        localAddresses: Set<String>,
        timeoutMs: Int
    ): String? = withContext(Dispatchers.IO) {
        val jobs = (1..254).mapNotNull { i ->
            val targetIp = "$subnetPrefix.$i"
            if (!isRemoteCandidate(targetIp, localAddresses)) {
                Log.d(TAG, "DISCOVERY_SKIP_LOCAL source=subnet address=$targetIp")
                return@mapNotNull null
            }
            async {
                if (isPortOpen(targetIp, port, timeoutMs)) targetIp else null
            }
        }
        val results = jobs.awaitAll()
        return@withContext results.firstOrNull { it != null }
    }

    /**
     * Scans a /24 subnet and returns ALL IPs where port [port] is open.
     */
    private suspend fun scanSubnetParallelAll(
        subnetPrefix: String,
        port: Int,
        localAddresses: Set<String>,
        timeoutMs: Int
    ): List<String> = withContext(Dispatchers.IO) {
        val jobs = (1..254).mapNotNull { i ->
            val targetIp = "$subnetPrefix.$i"
            if (!isRemoteCandidate(targetIp, localAddresses)) {
                Log.d(TAG, "DISCOVERY_SKIP_LOCAL source=subnet address=$targetIp")
                return@mapNotNull null
            }
            async {
                if (isPortOpen(targetIp, port, timeoutMs)) targetIp else null
            }
        }
        val results = jobs.awaitAll()
        return@withContext results.filterNotNull()
    }

    /** True only for a remote endpoint, never an address owned by this Android device. */
    internal fun isRemoteCandidate(candidateIp: String, localAddresses: Set<String>): Boolean {
        val normalized = candidateIp.trim().removePrefix("::ffff:")
        return normalized.isNotEmpty() &&
            !normalized.equals("localhost", ignoreCase = true) &&
            normalized != "127.0.0.1" &&
            normalized != "::1" &&
            normalized !in localAddresses
    }

    private fun getLocalIpv4Addresses(): Set<String> {
        return try {
            Collections.list(NetworkInterface.getNetworkInterfaces())
                .flatMap { intf ->
                    Collections.list(intf.inetAddresses)
                        .filterIsInstance<java.net.Inet4Address>()
                        .mapNotNull { it.hostAddress }
                }
                .toSet()
        } catch (e: Throwable) {
            Log.w(TAG, "DISCOVERY_LOCAL_ADDRESSES_FAILED error=${e.message}")
            setOf("127.0.0.1")
        }
    }

    /**
     * Checks if a TCP port is open at [ip]:[port] with [timeoutMs] connect timeout.
     */
    fun isPortOpen(ip: String, port: Int, timeoutMs: Int): Boolean {
        return try {
            Socket().use { socket ->
                socket.connect(InetSocketAddress(ip, port), timeoutMs)
                true
            }
        } catch (_: Throwable) {
            false
        }
    }

    /**
     * Returns a list of default gateway IPs across active network interfaces
     * (especially USB RNDIS/NCM tethering interfaces).
     */
    private fun getNetworkGateways(context: Context): List<String> {
        val gateways = mutableListOf<String>()
        try {
            val interfaces = Collections.list(NetworkInterface.getNetworkInterfaces())
            for (intf in interfaces) {
                if (!intf.isUp || intf.isLoopback) continue
                val addrs = Collections.list(intf.inetAddresses)
                for (addr in addrs) {
                    if (!addr.isLoopbackAddress && addr is java.net.Inet4Address) {
                        val hostAddr = addr.hostAddress ?: continue
                        if (hostAddr.contains(".")) {
                            val prefix = hostAddr.substringBeforeLast(".")
                            // Common gateway address on USB tethering subnets (e.g. .1 or .129 or .254)
                            gateways.add("$prefix.1")
                            gateways.add("$prefix.129")
                            gateways.add("$prefix.254")
                        }
                    }
                }
            }
        } catch (e: Throwable) {
            Log.d(TAG, "Error getting network gateways: ${e.message}")
        }
        return gateways.distinct()
    }

    /**
     * Retrieves the IPv4 address of the active Wi-Fi interface.
     */
    private fun getWifiIpAddress(context: Context): String? {
        try {
            val wifiManager = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as? WifiManager
            val ipInt = wifiManager?.connectionInfo?.ipAddress ?: 0
            if (ipInt != 0) {
                return String.format(
                    "%d.%d.%d.%d",
                    ipInt and 0xff,
                    ipInt shr 8 and 0xff,
                    ipInt shr 16 and 0xff,
                    ipInt shr 24 and 0xff
                )
            }
            // Fallback via NetworkInterfaces
            val interfaces = Collections.list(NetworkInterface.getNetworkInterfaces())
            for (intf in interfaces) {
                if (!intf.isUp || intf.isLoopback) continue
                if (intf.name.startsWith("wlan") || intf.name.startsWith("swlan")) {
                    val addrs = Collections.list(intf.inetAddresses)
                    for (addr in addrs) {
                        if (!addr.isLoopbackAddress && addr is java.net.Inet4Address) {
                            return addr.hostAddress
                        }
                    }
                }
            }
        } catch (e: Throwable) {
            Log.d(TAG, "Error getting Wi-Fi IP: ${e.message}")
        }
        return null
    }
}
