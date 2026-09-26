package com.example

import com.example.data.model.SessionFileMeta
import com.example.data.model.TransferSession
import com.example.data.model.formatFileSize
import com.example.network.NetworkUtils
import com.example.qr.QrCodeGenerator
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class SyedUnitTest {

    @Test
    fun testFileSizeFormatting() {
        assertEquals("0 B", formatFileSize(0))
        assertEquals("1.0 KB", formatFileSize(1024))
        assertEquals("1.5 MB", formatFileSize(1572864))
        assertEquals("2.0 GB", formatFileSize(2147483648L))
    }

    @Test
    fun testSpeedFormatting() {
        assertEquals("0 KB/s", NetworkUtils.formatSpeed(0))
        assertEquals("500 KB/s", NetworkUtils.formatSpeed(512000))
        assertEquals("10.0 MB/s", NetworkUtils.formatSpeed(10485760))
    }

    @Test
    fun testQrSessionSerializationAndParsing() {
        val session = TransferSession(
            sessionId = "test-session-123",
            token = "tok-xyz-456",
            host = "192.168.1.100",
            port = 8888,
            senderName = "Syed Taha",
            files = listOf(
                SessionFileMeta(
                    id = "file-1",
                    name = "presentation.pdf",
                    size = 5242880,
                    mimeType = "application/pdf",
                    checksum = "abcd1234efgh"
                )
            ),
            expiresAt = 1800000000000L,
            isGroup = false
        )

        val json = QrCodeGenerator.generateSessionJson(session)
        assertNotNull(json)

        val parsed = QrCodeGenerator.parseSessionJson(json)
        assertNotNull(parsed)
        assertEquals("test-session-123", parsed?.sessionId)
        assertEquals("tok-xyz-456", parsed?.token)
        assertEquals("192.168.1.100", parsed?.host)
        assertEquals(8888, parsed?.port)
        assertEquals("Syed Taha", parsed?.senderName)
        assertEquals(1, parsed?.files?.size)
        assertEquals("presentation.pdf", parsed?.files?.first()?.name)
    }
}
