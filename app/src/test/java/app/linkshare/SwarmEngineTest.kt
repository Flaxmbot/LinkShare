package app.linkshare

import app.linkshare.core.swarm.ManifestGenerator
import app.linkshare.core.swarm.PieceVerifier
import app.linkshare.core.swarm.RarestFirstScheduler
import app.linkshare.core.swarm.SwarmEngine
import app.linkshare.model.PeerDevice
import app.linkshare.model.PieceBitset
import app.linkshare.model.TransferState
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class SwarmEngineTest {

    @Test
    fun testManifestGenerationAndPieceVerification() {
        val testData = "LinkShare Offline Swarm Share Test Content Line 1 Line 2 Line 3".toByteArray()
        val manifest = ManifestGenerator.generateManifestFromBytes(
            fileId = "file_123",
            fileName = "test.txt",
            bytes = testData,
            pieceSize = 16 // 16 bytes per piece
        )

        assertTrue(manifest.pieceCount > 1)
        assertEquals(testData.size.toLong(), manifest.fileSizeBytes)

        // Verify correct piece
        val piece0 = testData.copyOfRange(0, 16)
        val isPiece0Valid = PieceVerifier.verifyPiece(manifest, 0, piece0)
        assertTrue(isPiece0Valid)

        // Verify corrupted piece fails hash check (FR3.5 & FR3.6)
        val corruptedPiece0 = piece0.copyOf()
        corruptedPiece0[0] = (corruptedPiece0[0].toInt() xor 0xFF).toByte()
        val isCorruptedValid = PieceVerifier.verifyPiece(manifest, 0, corruptedPiece0)
        assertFalse("Corrupted piece must be rejected by hash check", isCorruptedValid)
    }

    @Test
    fun testRarestFirstScheduler() {
        val localBitset = PieceBitset(5) // missing all 5 pieces
        
        val peer1Bitset = PieceBitset(5).apply {
            setPiece(0, true)
            setPiece(1, true)
            setPiece(2, true)
        }

        val peer2Bitset = PieceBitset(5).apply {
            setPiece(0, true)
            setPiece(1, true)
            setPiece(2, true)
            setPiece(3, true) // piece 3 is owned ONLY by peer 2 (1 copy vs 2 copies for 0,1,2)
        }

        val peerBitsets = mapOf("peer1" to peer1Bitset, "peer2" to peer2Bitset)

        val request = RarestFirstScheduler.selectNextPiece(localBitset, peerBitsets)
        assertNotNull(request)
        assertEquals(3, request?.pieceIndex) // piece 3 is uniquely rarest (1 copy)
        assertEquals("peer2", request?.targetPeerId)
    }

    @Test
    fun testSwarmEngineFullFlowAndCorruptPieceRejection() = runTest {
        val swarmEngine = SwarmEngine(this)
        val testData = "0123456789ABCDEF0123456789ABCDEF".toByteArray() // 32 bytes
        val manifest = ManifestGenerator.generateManifestFromBytes(
            fileId = "test_flow",
            fileName = "data.bin",
            bytes = testData,
            pieceSize = 16
        )

        val peers = listOf(
            PeerDevice(id = "peer1", name = "Peer 1"),
            PeerDevice(id = "peer2", name = "Peer 2")
        )

        swarmEngine.startRecipientSwarm(manifest, peers)

        val piece0Data = testData.copyOfRange(0, 16)
        val piece1Data = testData.copyOfRange(16, 32)

        // Corrupt piece submission attempt
        val corruptPiece = piece0Data.copyOf()
        corruptPiece[0] = 0x00
        val acceptedCorrupt = swarmEngine.onPieceReceived("peer1", 0, corruptPiece)
        assertFalse("Corrupt piece must be rejected", acceptedCorrupt)
        assertFalse(swarmEngine.transferState.value is TransferState.Completed)

        // Valid piece submissions
        val accepted0 = swarmEngine.onPieceReceived("peer1", 0, piece0Data)
        assertTrue(accepted0)

        val accepted1 = swarmEngine.onPieceReceived("peer2", 1, piece1Data)
        assertTrue(accepted1)

        assertTrue("Transfer must complete when all pieces are verified", swarmEngine.transferState.value is TransferState.Completed)
    }
}
