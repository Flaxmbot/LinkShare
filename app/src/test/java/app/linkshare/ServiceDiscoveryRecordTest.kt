package app.linkshare

import app.linkshare.model.DiscoveryTxtRecord
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ServiceDiscoveryRecordTest {

    @Test
    fun testTxtRecordEncodingAndDecoding() {
        val original = DiscoveryTxtRecord(
            deviceName = "Pixel_8_Owner",
            appVersion = "1.0.0",
            supportsF2 = true,
            supportsF3 = true,
            ftpActive = true,
            port = 8888
        )

        val map = original.toMap()
        val decoded = DiscoveryTxtRecord.fromMap(map)

        assertEquals("Pixel_8_Owner", decoded.deviceName)
        assertEquals("1.0.0", decoded.appVersion)
        assertTrue(decoded.supportsF2)
        assertTrue(decoded.supportsF3)
        assertTrue(decoded.ftpActive)
        assertEquals(8888, decoded.port)
    }
}
