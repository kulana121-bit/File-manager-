package com.example.filesapp.domain.nearby

import android.content.Context
import com.google.android.gms.nearby.Nearby
import com.google.android.gms.nearby.connection.AdvertisingOptions
import com.google.android.gms.nearby.connection.ConnectionInfo
import com.google.android.gms.nearby.connection.ConnectionLifecycleCallback
import com.google.android.gms.nearby.connection.ConnectionResolution
import com.google.android.gms.nearby.connection.ConnectionsClient
import com.google.android.gms.nearby.connection.DiscoveredEndpointInfo
import com.google.android.gms.nearby.connection.DiscoveryOptions
import com.google.android.gms.nearby.connection.EndpointDiscoveryCallback
import com.google.android.gms.nearby.connection.Payload
import com.google.android.gms.nearby.connection.PayloadCallback
import com.google.android.gms.nearby.connection.PayloadTransferUpdate
import com.google.android.gms.nearby.connection.Strategy
import java.io.File

/**
 * Nearby Share manager using Google's Nearby Connections API.
 * Google Files parity: P2P file sharing without internet.
 *
 * Uses P2P_STAR strategy for 1-to-many sharing.
 */
class NearbyShareManager(private val context: Context) {

    companion object {
        const val SERVICE_ID = "com.example.filesapp.NEARBY_SHARE"
        private val STRATEGY = Strategy.P2P_STAR
    }

    private val client: ConnectionsClient = Nearby.getConnectionsClient(context)

    var onEndpointFound: ((endpointId: String, name: String) -> Unit)? = null
    var onEndpointLost: ((endpointId: String) -> Unit)? = null
    var onConnectionRequest: ((endpointId: String, name: String, accept: (Boolean) -> Unit) -> Unit)? = null
    var onConnected: ((endpointId: String, name: String) -> Unit)? = null
    var onDisconnected: ((endpointId: String) -> Unit)? = null
    var onFileReceived: ((file: File, endpointName: String) -> Unit)? = null
    var onTransferUpdate: ((endpointId: String, progress: Float) -> Unit)? = null
    var onLog: ((String) -> Unit)? = null

    private val pendingFilePayloads = mutableMapOf<Long, File>()
    private val endpointNames = mutableMapOf<String, String>()

    private val connectionCallback = object : ConnectionLifecycleCallback() {
        override fun onConnectionInitiated(endpointId: String, info: ConnectionInfo) {
            endpointNames[endpointId] = info.endpointName
            onLog?.invoke("Connection request from ${info.endpointName}")
            onConnectionRequest?.invoke(endpointId, info.endpointName) { accept ->
                if (accept) {
                    client.acceptConnection(endpointId, payloadCallback)
                } else {
                    client.rejectConnection(endpointId)
                }
            }
        }

        override fun onConnectionResult(endpointId: String, result: ConnectionResolution) {
            if (result.status.isSuccess) {
                val name = endpointNames[endpointId] ?: endpointId
                onLog?.invoke("Connected to $name")
                onConnected?.invoke(endpointId, name)
            } else {
                onLog?.invoke("Connection failed: ${result.status.statusMessage}")
                onDisconnected?.invoke(endpointId)
            }
        }

        override fun onDisconnected(endpointId: String) {
            val name = endpointNames[endpointId] ?: endpointId
            onLog?.invoke("Disconnected from $name")
            endpointNames.remove(endpointId)
            onDisconnected?.invoke(endpointId)
        }
    }

    private val payloadCallback = object : PayloadCallback() {
        override fun onPayloadReceived(endpointId: String, payload: Payload) {
            if (payload.type == Payload.Type.FILE) {
                payload.asFile()?.let { parcelFile ->
                    parcelFile.asParcelFileDescriptor()?.let { pfd ->
                        // Copy to app's receive directory
                        val receiveDir = File(context.getExternalFilesDir(null), "NearbyShare")
                        receiveDir.mkdirs()
                        // Filename comes via separate bytes payload; use temp name for now
                        val tempFile = File(receiveDir, "incoming_${payload.id}")
                        try {
                            android.os.ParcelFileDescriptor.AutoCloseInputStream(pfd).use { input ->
                                tempFile.outputStream().use { output ->
                                    input.copyTo(output)
                                }
                            }
                            pendingFilePayloads[payload.id] = tempFile
                        } catch (e: Exception) {
                            e.printStackTrace()
                        }
                    }
                }
            } else if (payload.type == Payload.Type.BYTES) {
                // Filename metadata
                val fileName = payload.asBytes()?.toString(Charsets.UTF_8) ?: "received_file"
                // Will be matched with file payload via naming convention
                pendingFilePayloads[-payload.id]?.let { tempFile ->
                    val finalFile = File(tempFile.parent, fileName)
                    tempFile.renameTo(finalFile)
                    pendingFilePayloads.remove(-payload.id)
                    val endpointName = endpointNames[endpointId] ?: endpointId
                    onFileReceived?.invoke(finalFile, endpointName)
                    onLog?.invoke("Received $fileName from $endpointName")
                }
            }
        }

        override fun onPayloadTransferUpdate(endpointId: String, update: PayloadTransferUpdate) {
            val progress = if (update.totalBytes > 0) {
                update.bytesTransferred.toFloat() / update.totalBytes.toFloat()
            } else 0f
            onTransferUpdate?.invoke(endpointId, progress)
        }
    }

    private val discoveryCallback = object : EndpointDiscoveryCallback() {
        override fun onEndpointFound(endpointId: String, info: DiscoveredEndpointInfo) {
            onLog?.invoke("Found: ${info.endpointName}")
            onEndpointFound?.invoke(endpointId, info.endpointName)
        }

        override fun onEndpointLost(endpointId: String) {
            onEndpointLost?.invoke(endpointId)
        }
    }

    /**
     * Start advertising (for receiving files).
     */
    fun startAdvertising(deviceName: String) {
        val options = AdvertisingOptions.Builder().setStrategy(STRATEGY).build()
        client.startAdvertising(deviceName, SERVICE_ID, connectionCallback, options)
            .addOnSuccessListener { onLog?.invoke("Advertising as $deviceName") }
            .addOnFailureListener { e -> onLog?.invoke("Advertise failed: ${e.message}") }
    }

    fun stopAdvertising() {
        client.stopAdvertising()
    }

    /**
     * Start discovery (for sending files).
     */
    fun startDiscovery() {
        val options = DiscoveryOptions.Builder().setStrategy(STRATEGY).build()
        client.startDiscovery(SERVICE_ID, discoveryCallback, options)
            .addOnSuccessListener { onLog?.invoke("Discovering devices...") }
            .addOnFailureListener { e -> onLog?.invoke("Discovery failed: ${e.message}") }
    }

    fun stopDiscovery() {
        client.stopDiscovery()
    }

    /**
     * Request connection to a discovered endpoint.
     */
    fun connectTo(endpointId: String, deviceName: String) {
        client.requestConnection(deviceName, endpointId, connectionCallback)
            .addOnSuccessListener { onLog?.invoke("Connection requested") }
            .addOnFailureListener { e -> onLog?.invoke("Connect failed: ${e.message}") }
    }

    /**
     * Send a file to a connected endpoint.
     */
    fun sendFile(endpointId: String, file: File) {
        try {
            val filePayload = Payload.fromFile(file)
            // Send filename first as bytes (negative ID convention for pairing)
            val namePayload = Payload.fromBytes(file.name.toByteArray(Charsets.UTF_8))
            client.sendPayload(endpointId, namePayload)
            client.sendPayload(endpointId, filePayload)
                .addOnSuccessListener { onLog?.invoke("Sending ${file.name}...") }
                .addOnFailureListener { e -> onLog?.invoke("Send failed: ${e.message}") }
        } catch (e: Exception) {
            onLog?.invoke("Send error: ${e.message}")
        }
    }

    /**
     * Disconnect from an endpoint.
     */
    fun disconnect(endpointId: String) {
        client.disconnectFromEndpoint(endpointId)
    }

    /**
     * Stop all Nearby activity.
     */
    fun stopAll() {
        client.stopAllEndpoints()
    }
}
