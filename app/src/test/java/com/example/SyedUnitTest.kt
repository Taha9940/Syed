package com.example

import com.example.data.model.SessionFileMeta
import com.example.data.model.TransferSession
import com.example.data.model.formatFileSize
import com.example.network.NetworkUtils
import com.example.network.p2p.DuplicatePolicy
import com.example.network.p2p.FileTypeCategory
import com.example.network.p2p.PublicMediaStorageHelper
import com.example.qr.QrCodeGenerator
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import java.io.File

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
    fun testFileCategorizationAndMimeTypes() {
        // Photos
        assertEquals(FileTypeCategory.PHOTO, PublicMediaStorageHelper.categorizeFile("vacation.jpg", ""))
        assertEquals(FileTypeCategory.PHOTO, PublicMediaStorageHelper.categorizeFile("photo.PNG", ""))
        assertEquals(FileTypeCategory.PHOTO, PublicMediaStorageHelper.categorizeFile("image.webp", "image/webp"))
        assertEquals("image/jpeg", PublicMediaStorageHelper.resolveMimeType("vacation.jpg", ""))
        assertEquals("image/png", PublicMediaStorageHelper.resolveMimeType("photo.png", ""))

        // Videos
        assertEquals(FileTypeCategory.VIDEO, PublicMediaStorageHelper.categorizeFile("movie.mp4", ""))
        assertEquals(FileTypeCategory.VIDEO, PublicMediaStorageHelper.categorizeFile("clip.MKV", ""))
        assertEquals("video/mp4", PublicMediaStorageHelper.resolveMimeType("movie.mp4", ""))
        assertEquals("video/x-matroska", PublicMediaStorageHelper.resolveMimeType("clip.mkv", ""))

        // Documents, APKs, ZIPs
        assertEquals(FileTypeCategory.DOCUMENT_OR_OTHER, PublicMediaStorageHelper.categorizeFile("app-release.apk", ""))
        assertEquals(FileTypeCategory.DOCUMENT_OR_OTHER, PublicMediaStorageHelper.categorizeFile("backup.zip", ""))
        assertEquals(FileTypeCategory.DOCUMENT_OR_OTHER, PublicMediaStorageHelper.categorizeFile("invoice.pdf", ""))

        assertEquals("application/vnd.android.package-archive", PublicMediaStorageHelper.resolveMimeType("app-release.apk", "application/octet-stream"))
        assertEquals("application/zip", PublicMediaStorageHelper.resolveMimeType("backup.zip", "application/octet-stream"))
        assertEquals("application/pdf", PublicMediaStorageHelper.resolveMimeType("invoice.pdf", ""))
    }

    @Test
    fun testPendingDestinationLifecycleAndSafety() {
        val context = RuntimeEnvironment.getApplication()

        // 1. Open pending destination for a photo
        val photoDest = PublicMediaStorageHelper.openPendingDestination(
            context = context,
            originalName = "test_photo.jpg",
            rawMime = "image/jpeg",
            duplicatePolicy = DuplicatePolicy.KEEP_BOTH
        )
        assertNotNull(photoDest)
        photoDest!!.outputStream.write("dummy photo data".toByteArray())

        // Discard incomplete file (simulating transfer failure)
        PublicMediaStorageHelper.discardPendingFile(context, photoDest)

        // 2. Open pending destination for an APK, write and finalize
        val apkDest = PublicMediaStorageHelper.openPendingDestination(
            context = context,
            originalName = "test_app.apk",
            rawMime = "",
            duplicatePolicy = DuplicatePolicy.KEEP_BOTH
        )
        assertNotNull(apkDest)
        assertEquals("application/vnd.android.package-archive", apkDest!!.mimeType)
        apkDest.outputStream.write("dummy apk bytes".toByteArray())

        val published = PublicMediaStorageHelper.finalizeAndPublish(context, apkDest)
        assertNotNull(published)
        assertTrue(published!!.filePath.isNotBlank())
    }

    @Test
    fun testQrSessionSerializationAndParsing() {
        val session = TransferSession(
            sessionId = "test-session-123",
            token = "tok-xyz-456",
            host = "192.168.1.100",
            port = 8888,
            senderName = "Taha User",
            files = emptyList(),
            expiresAt = 1800000000000L,
            isGroup = false
        )

        val qrPayload = QrCodeGenerator.generateSessionJson(session)
        assertNotNull(qrPayload)
        assertEquals("taha://192.168.1.100:8888/tok-xyz-456", qrPayload)

        val parsed = QrCodeGenerator.parseSessionJson(qrPayload)
        assertNotNull(parsed)
        assertEquals("tok-xyz-456", parsed?.token)
        assertEquals("192.168.1.100", parsed?.host)
        assertEquals(8888, parsed?.port)

        // Also verify JSON payload parsing
        val jsonPayload = """{"h":"192.168.1.100","p":8888,"t":"tok-xyz-456","sender":"Taha User"}"""
        val parsedJson = QrCodeGenerator.parseSessionJson(jsonPayload)
        assertNotNull(parsedJson)
        assertEquals("tok-xyz-456", parsedJson?.token)
        assertEquals("192.168.1.100", parsedJson?.host)
        assertEquals(8888, parsedJson?.port)
        assertEquals("Taha User", parsedJson?.senderName)
    }
}
