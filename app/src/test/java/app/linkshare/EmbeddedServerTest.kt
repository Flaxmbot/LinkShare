package app.linkshare

import app.linkshare.core.swarm.SwarmPeerHandler
import app.linkshare.model.SwarmManifest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test

class EmbeddedServerTest {

    @Test
    fun testSwarmManifestSerialization() {
        val manifest = SwarmManifest(
            fileId = "id_100",
            fileName = "movie.mp4",
            fileSizeBytes = 10485760L,
            pieceSize = 1048576,
            pieceCount = 10,
            pieceHashes = listOf("hash0", "hash1", "hash2"),
            totalHash = "total_hash_xyz"
        )

        val json = SwarmPeerHandler.manifestToJson(manifest)
        val deserialized = SwarmPeerHandler.jsonToManifest(json)

        assertNotNull(deserialized)
        assertEquals(manifest.fileId, deserialized?.fileId)
        assertEquals(manifest.fileName, deserialized?.fileName)
        assertEquals(manifest.fileSizeBytes, deserialized?.fileSizeBytes)
        assertEquals(manifest.totalHash, deserialized?.totalHash)
        assertEquals(3, deserialized?.pieceHashes?.size)
    }
}
