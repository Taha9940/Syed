package com.example.network

import android.content.Context
import android.net.wifi.WifiManager
import android.util.Log
import com.example.data.model.PeerDevice
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import org.json.JSONObject
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress

class UdpDiscoveryManager(private val context: Context) {
    companion object {
        private const val TAG = "UdpDiscovery"
        const val DISCOVERY_PORT = 8889
        private const val BROADCAST_INTERVAL_MS = 2000L
    }

    private val _nearbyDevices = MutableStateFlow<List<PeerDevice>>(emptyList())
    val nearbyDevices: StateFlow<List<PeerDevice>> = _nearbyDevices.asStateFlow()

    private var broadcastJob: Job? = null
    private var listenJob: Job? = null
    private var multicastLock: WifiManager.MulticastLock? = null
    private var socket: DatagramSocket? = null

    fun startListening(scope: CoroutineScope) {
        stopListening()
        acquireMulticastLock()

        listenJob = scope.launch(Dispatchers.IO) {
            try {
                val s = DatagramSocket(DISCOVERY_PORT).apply {
                    broadcast = true
                    reuseAddress = true
                }
                socket = s
                val buffer = ByteArray(2048)

                while (isActive && !s.isClosed) {
                    try {
                        val packet = DatagramPacket(buffer, buffer.size)
                        s.receive(packet)
                        val text = String(packet.data, 0, packet.length)
                        val senderIp = packet.address.hostAddress ?: ""
                        parseBeacon(text, senderIp)
                    } catch (e: Exception) {
                        if (!isActive) break
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error starting UDP listener", e)
            }
        }
    }

    private fun parseBeacon(jsonString: String, senderIp: String) {
        try {
            val json = JSONObject(jsonString)
            val type = json.optString("type")
            if (type == "TAHA_BEACON" || type == "SYED_BEACON") {
                val id = json.optString("id")
                val name = json.optString("name")
                val port = json.optInt("port", 8888)
                val token = json.optString("token")
                val status = json.optString("status", "Available")
                val host = if (json.has("host") && json.getString("host").isNotBlank()) {
                    json.getString("host")
                } else senderIp

                val device = PeerDevice(
                    id = id,
                    name = name,
                    host = host,
                    port = port,
                    token = token,
                    status = status,
                    lastSeen = System.currentTimeMillis()
                )

                val current = _nearbyDevices.value.toMutableList()
                val idx = current.indexOfFirst { it.id == id || (it.host == host && it.port == port) }
                if (idx >= 0) {
                    current[idx] = device
                } else {
                    current.add(device)
                }
                // Filter out stale devices older than 8 seconds
                val now = System.currentTimeMillis()
                _nearbyDevices.value = current.filter { now - it.lastSeen < 8000 }
            }
        } catch (_: Exception) {
        }
    }

    fun startBroadcasting(
        scope: CoroutineScope,
        deviceId: String,
        displayName: String,
        serverPort: Int,
        token: String,
        status: String = "Ready to transfer"
    ) {
        stopBroadcasting()
        broadcastJob = scope.launch(Dispatchers.IO) {
            var broadcastSocket: DatagramSocket? = null
            try {
                broadcastSocket = DatagramSocket().apply { broadcast = true }
                val broadcastAddr = InetAddress.getByName("255.255.255.255")
                val myIp = NetworkUtils.getLocalIpAddress(context)

                while (isActive) {
                    val json = JSONObject().apply {
                        put("type", "TAHA_BEACON")
                        put("id", deviceId)
                        put("name", displayName)
                        put("host", myIp)
                        put("port", serverPort)
                        put("token", token)
                        put("status", status)
                        put("time", System.currentTimeMillis())
                    }
                    val data = json.toString().toByteArray()
                    val packet = DatagramPacket(data, data.size, broadcastAddr, DISCOVERY_PORT)
                    broadcastSocket.send(packet)
                    delay(BROADCAST_INTERVAL_MS)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error in broadcast loop", e)
            } finally {
                broadcastSocket?.close()
            }
        }
    }

    fun stopBroadcasting() {
        broadcastJob?.cancel()
        broadcastJob = null
    }

    fun stopListening() {
        listenJob?.cancel()
        listenJob = null
        try {
            socket?.close()
        } catch (_: Exception) {}
        socket = null
        releaseMulticastLock()
    }

    fun clearDevices() {
        _nearbyDevices.value = emptyList()
    }

    private fun acquireMulticastLock() {
        try {
            val wifi = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as? WifiManager
            multicastLock = wifi?.createMulticastLock("syed_multicast_lock")?.apply {
                setReferenceCounted(true)
                acquire()
            }
        } catch (e: Exception) {
            Log.w(TAG, "Could not acquire multicast lock", e)
        }
    }

    private fun releaseMulticastLock() {
        try {
            if (multicastLock?.isHeld == true) {
                multicastLock?.release()
            }
        } catch (_: Exception) {}
        multicastLock = null
    }
}
