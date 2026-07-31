package app.linkshare.core.swarm

import android.net.Uri
import android.util.Log
import app.linkshare.core.storage.RealFileManager
import app.linkshare.core.transport.NetworkUtils
import app.linkshare.model.PieceBitset
import app.linkshare.model.SwarmManifest
import app.linkshare.model.TransferState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.net.ServerSocket
import java.net.Socket
import java.util.concurrent.ConcurrentHashMap

/**
 * Real TCP ServerSocket and Socket transport service for Swarm Transfers (F3).
 */
class SwarmNetworkService(
    private val realFileManager: RealFileManager,
    private val port: Int = 8888
) {
    private val TAG = "SwarmNetworkService"
    private val scope = CoroutineScope(Dispatchers.IO + Job())

    private var serverSocket: ServerSocket? = null
    private var isListening = false

    private val connectedPeersMap = ConcurrentHashMap<String, SwarmPeerHandler>()
    private val peerBitsetsMap = ConcurrentHashMap<String, PieceBitset>()

    private val _transferState = MutableStateFlow<TransferState>(TransferState.Idle)
    val transferState: StateFlow<TransferState> = _transferState.asStateFlow()

    private var activeManifest: SwarmManifest? = null
    private var activeSourceUri: Uri? = null
    private var activeOutputFile: File? = null
    private var localBitset: PieceBitset? = null
    private var startTimeMs: Long = 0L

    /**
     * Start background TCP ServerSocket listener to accept incoming connections from swarm peers.
     */
    fun startServerSocket() {
        if (isListening) return
        isListening = true

        scope.launch {
            try {
                serverSocket = ServerSocket(port)
                Log.d(TAG, "Swarm ServerSocket listening on port $port")

                while (isListening) {
                    val socket = serverSocket?.accept() ?: break
                    launch {
                        handleIncomingConnection(socket)
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "ServerSocket error: ${e.message}")
            }
        }
    }

    /**
     * Connect outgoing socket to a peer device IP.
     */
    fun connectToPeerSocket(peerId: String, ipAddress: String) {
        scope.launch {
            try {
                Log.d(TAG, "Connecting socket to peer $peerId at $ipAddress:$port")
                NetworkUtils.registerClientIp(ipAddress)
                val socket = Socket(ipAddress, port)
                val handler = SwarmPeerHandler(peerId, socket)
                connectedPeersMap[peerId] = handler

                // Exchange bitset or manifest
                localBitset?.let { bs ->
                    handler.sendBitsetUpdate(bs)
                }
                activeManifest?.let { m ->
                    handler.sendManifest(m)
                }

                listenToPeerMessages(handler)
            } catch (e: Exception) {
                Log.e(TAG, "Failed outgoing socket to $peerId: ${e.message}")
            }
        }
    }

    /**
     * Initiate real file broadcast as sender.
     */
    suspend fun startSendingFile(sourceUri: Uri, targetPeers: List<String>) = withContext(Dispatchers.IO) {
        val manifest = realFileManager.createManifestFromUri(sourceUri) ?: return@withContext false
        activeManifest = manifest
        activeSourceUri = sourceUri
        localBitset = PieceBitset.allOwned(manifest.pieceCount)
        startTimeMs = System.currentTimeMillis()

        _transferState.value = TransferState.Transferring(
            fileName = manifest.fileName,
            bytesTransferred = manifest.fileSizeBytes,
            totalBytes = manifest.fileSizeBytes,
            speedBytesPerSec = 0,
            isDualLinkActive = false,
            isSwarmActive = targetPeers.size > 1,
            activePeers = targetPeers.size,
            pieceBitset = localBitset
        )

        // Send manifest to all connected peers
        connectedPeersMap.values.forEach { handler ->
            try {
                handler.sendManifest(manifest)
                localBitset?.let { bs -> handler.sendBitsetUpdate(bs) }
            } catch (_: Exception) {}
        }
        true
    }

    /**
     * Initiate file download as recipient.
     */
    fun startReceivingFile(manifest: SwarmManifest) {
        activeManifest = manifest
        val targetFile = realFileManager.getDownloadsTargetFile(manifest.fileName)
        activeOutputFile = targetFile
        localBitset = PieceBitset(manifest.pieceCount)
        startTimeMs = System.currentTimeMillis()

        _transferState.value = TransferState.Transferring(
            fileName = manifest.fileName,
            bytesTransferred = 0L,
            totalBytes = manifest.fileSizeBytes,
            speedBytesPerSec = 0,
            isDualLinkActive = false,
            isSwarmActive = connectedPeersMap.size > 1,
            activePeers = connectedPeersMap.size,
            pieceBitset = localBitset
        )

        scheduleNextPieceRequests()
    }

    private suspend fun handleIncomingConnection(socket: Socket) = withContext(Dispatchers.IO) {
        val peerIp = socket.inetAddress?.hostAddress ?: ""
        if (peerIp.isNotEmpty()) {
            NetworkUtils.registerClientIp(peerIp)
        }
        val peerId = socket.remoteSocketAddress.toString()
        val handler = SwarmPeerHandler(peerId, socket)
        connectedPeersMap[peerId] = handler

        // Send local bitset and manifest if available
        localBitset?.let { bs -> handler.sendBitsetUpdate(bs) }
        activeManifest?.let { m -> handler.sendManifest(m) }

        listenToPeerMessages(handler)
    }

    private suspend fun listenToPeerMessages(handler: SwarmPeerHandler) = withContext(Dispatchers.IO) {
        try {
            val dis = handler.inputStream
            while (isListening) {
                val msgType = dis.readUTF()
                when (msgType) {
                    "MANIFEST_REQ" -> {
                        activeManifest?.let { m -> handler.sendManifest(m) }
                    }
                    "MANIFEST_RESP" -> {
                        val json = dis.readUTF()
                        val manifest = SwarmPeerHandler.jsonToManifest(json)
                        if (manifest != null && activeManifest == null) {
                            startReceivingFile(manifest)
                        }
                    }
                    "BITSET_UPDATE" -> {
                        val totalPieces = dis.readInt()
                        val hex = dis.readUTF()
                        val bs = PieceBitset.fromHexString(hex, totalPieces)
                        peerBitsetsMap[handler.peerId] = bs
                        scheduleNextPieceRequests()
                    }
                    "PIECE_REQ" -> {
                        val pieceIndex = dis.readInt()
                        val uri = activeSourceUri
                        val manifest = activeManifest
                        if (uri != null && manifest != null) {
                            val data = realFileManager.readPieceFromUri(uri, pieceIndex, manifest.pieceSize)
                            if (data != null) {
                                handler.sendPieceData(pieceIndex, data)
                            }
                        }
                    }
                    "PIECE_RESP" -> {
                        val pieceIndex = dis.readInt()
                        val dataLength = dis.readInt()
                        val data = ByteArray(dataLength)
                        dis.readFully(data)

                        onPieceDataReceived(handler.peerId, pieceIndex, data)
                    }
                }
            }
        } catch (e: Exception) {
            Log.d(TAG, "Peer ${handler.peerId} disconnected: ${e.message}")
        } finally {
            connectedPeersMap.remove(handler.peerId)
            peerBitsetsMap.remove(handler.peerId)
        }
    }

    private fun scheduleNextPieceRequests() {
        val bitset = localBitset ?: return
        if (bitset.isComplete()) return

        val request = RarestFirstScheduler.selectNextPiece(bitset, peerBitsetsMap) ?: return
        val handler = connectedPeersMap[request.targetPeerId]
        handler?.let {
            scope.launch {
                try {
                    it.sendPieceRequest(request.pieceIndex)
                } catch (_: Exception) {}
            }
        }
    }

    private suspend fun onPieceDataReceived(peerId: String, pieceIndex: Int, data: ByteArray) {
        val manifest = activeManifest ?: return
        val bitset = localBitset ?: return
        val outputFile = activeOutputFile ?: return

        // 1. SHA-256 Hash Verification (FR3.5)
        val isValid = PieceVerifier.verifyPiece(manifest, pieceIndex, data)
        if (!isValid) {
            Log.w(TAG, "Corrupt piece $pieceIndex from $peerId! Rejecting and re-requesting (FR3.6)")
            scheduleNextPieceRequests()
            return
        }

        // 2. Write piece to disk at exact offset (pieceIndex * pieceSize)
        realFileManager.writePieceToFile(outputFile, pieceIndex, manifest.pieceSize, data)
        bitset.setPiece(pieceIndex, true)

        // Broadcast updated bitset to peers (FR3.2)
        connectedPeersMap.values.forEach { h ->
            try { h.sendBitsetUpdate(bitset) } catch (_: Exception) {}
        }

        val bytesTransferred = bitset.countOwned().toLong() * manifest.pieceSize
        val elapsedSec = maxOf(1L, (System.currentTimeMillis() - startTimeMs) / 1000)
        val speed = bytesTransferred / elapsedSec

        if (bitset.isComplete()) {
            _transferState.value = TransferState.Completed(
                fileName = manifest.fileName,
                totalBytes = manifest.fileSizeBytes,
                elapsedTimeMs = System.currentTimeMillis() - startTimeMs,
                averageSpeedBytesPerSec = speed
            )
        } else {
            _transferState.value = TransferState.Transferring(
                fileName = manifest.fileName,
                bytesTransferred = minOf(bytesTransferred, manifest.fileSizeBytes),
                totalBytes = manifest.fileSizeBytes,
                speedBytesPerSec = speed,
                isDualLinkActive = false,
                isSwarmActive = connectedPeersMap.size > 1,
                activePeers = connectedPeersMap.size,
                pieceBitset = bitset
            )
            scheduleNextPieceRequests()
        }
    }

    fun stop() {
        isListening = false
        try { serverSocket?.close() } catch (_: Exception) {}
        connectedPeersMap.values.forEach { it.close() }
        connectedPeersMap.clear()
        peerBitsetsMap.clear()
        _transferState.value = TransferState.Idle
    }
}
