package app.linkshare.core.swarm

import app.linkshare.model.PieceBitset
import app.linkshare.model.SwarmManifest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.DataInputStream
import java.io.DataOutputStream
import java.net.Socket

/**
 * Protocol handler for network communication with a single Swarm Peer.
 */
class SwarmPeerHandler(
    val peerId: String,
    private val socket: Socket
) {
    val inputStream = DataInputStream(socket.getInputStream())
    val outputStream = DataOutputStream(socket.getOutputStream())

    suspend fun sendManifest(manifest: SwarmManifest) = withContext(Dispatchers.IO) {
        val json = manifestToJson(manifest)
        outputStream.writeUTF("MANIFEST_RESP")
        outputStream.writeUTF(json)
        outputStream.flush()
    }

    suspend fun requestManifest(): SwarmManifest? = withContext(Dispatchers.IO) {
        outputStream.writeUTF("MANIFEST_REQ")
        outputStream.flush()

        val type = inputStream.readUTF()
        if (type == "MANIFEST_RESP") {
            val json = inputStream.readUTF()
            return@withContext jsonToManifest(json)
        }
        null
    }

    suspend fun sendBitsetUpdate(bitset: PieceBitset) = withContext(Dispatchers.IO) {
        outputStream.writeUTF("BITSET_UPDATE")
        outputStream.writeInt(bitset.totalPieces)
        outputStream.writeUTF(bitset.toHexString())
        outputStream.flush()
    }

    suspend fun sendPieceRequest(pieceIndex: Int) = withContext(Dispatchers.IO) {
        outputStream.writeUTF("PIECE_REQ")
        outputStream.writeInt(pieceIndex)
        outputStream.flush()
    }

    suspend fun sendPieceData(pieceIndex: Int, data: ByteArray) = withContext(Dispatchers.IO) {
        outputStream.writeUTF("PIECE_RESP")
        outputStream.writeInt(pieceIndex)
        outputStream.writeInt(data.size)
        outputStream.write(data)
        outputStream.flush()
    }

    fun close() {
        try {
            socket.close()
        } catch (_: Exception) {}
    }

    companion object {
        private val gson = com.google.gson.Gson()

        fun manifestToJson(manifest: SwarmManifest): String {
            return gson.toJson(manifest)
        }

        fun jsonToManifest(json: String): SwarmManifest? {
            return try {
                gson.fromJson(json, SwarmManifest::class.java)
            } catch (_: Exception) {
                null
            }
        }
    }
}
