package com.example.qr

import android.graphics.Bitmap
import android.graphics.Color
import android.net.Uri
import com.example.data.model.SessionFileMeta
import com.example.data.model.TransferSession
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.qrcode.QRCodeWriter
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel
import org.json.JSONArray
import org.json.JSONObject

object QrCodeGenerator {

    /**
     * Generates a minimal, ultra-compact QR payload: "taha://host:port/token".
     * Keeps the QR module matrix as minimal as possible (under 35 chars) so weak,
     * low-resolution, or soft phone cameras can detect and scan the QR code instantly.
     */
    fun generateSessionJson(session: TransferSession): String {
        return "taha://${session.host}:${session.port}/${session.token}"
    }

    /**
     * Parses the QR code payload.
     * Supports:
     * 1. Ultra-compact URI scheme: taha://host:port/token (and legacy syed://)
     * 2. Compact JSON: {"h":"host","p":port,"t":"token"}
     * 3. Legacy full JSON format
     */
    fun parseSessionJson(content: String): TransferSession? {
        val trimmed = content.trim()
        return try {
            if (trimmed.startsWith("taha://", ignoreCase = true) || trimmed.startsWith("syed://", ignoreCase = true)) {
                val uri = Uri.parse(trimmed)
                val host = uri.host ?: return null
                val port = if (uri.port > 0) uri.port else 8888
                val token = uri.path?.removePrefix("/") ?: return null
                val sender = uri.getQueryParameter("s") ?: "Taha User"

                TransferSession(
                    sessionId = token,
                    token = token,
                    host = host,
                    port = port,
                    senderName = sender,
                    files = emptyList(),
                    expiresAt = System.currentTimeMillis() + 600000
                )
            } else if (trimmed.startsWith("{")) {
                val json = JSONObject(trimmed)
                val host = json.optString("h", json.optString("host"))
                val port = json.optInt("p", json.optInt("port", 8888))
                val token = json.optString("t", json.optString("tok", json.optString("token")))
                val sid = json.optString("sid", token)
                val sender = json.optString("sender", "Taha User")

                if (host.isBlank() || token.isBlank()) return null

                val filesArr = json.optJSONArray("files") ?: JSONArray()
                val files = mutableListOf<SessionFileMeta>()
                for (i in 0 until filesArr.length()) {
                    val f = filesArr.getJSONObject(i)
                    files.add(
                        SessionFileMeta(
                            id = f.optString("id", i.toString()),
                            name = f.optString("name", "File"),
                            size = f.optLong("size", 0L),
                            mimeType = f.optString("mime", "application/octet-stream"),
                            checksum = f.optString("sha", "")
                        )
                    )
                }

                TransferSession(
                    sessionId = sid,
                    token = token,
                    host = host,
                    port = port,
                    senderName = sender,
                    files = files,
                    expiresAt = json.optLong("exp", System.currentTimeMillis() + 600000),
                    isGroup = json.optBoolean("isGroup", false),
                    groupName = json.optString("grp", "")
                )
            } else {
                null
            }
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    /**
     * Generates a high-contrast, clean black-and-white QR code bitmap.
     * Uses ErrorCorrectionLevel.L (7% error correction) to minimize module count and maximize
     * block size, making scanning effortless for lower quality / softer phone cameras.
     * Quiet zone margin is set to 4 modules conforming to QR standards.
     * Zero gradients, zero logos, zero decorative styling inside the QR pattern.
     */
    fun generateQrBitmap(content: String, sizePx: Int = 800): Bitmap? {
        return try {
            val hints = mapOf(
                EncodeHintType.ERROR_CORRECTION to ErrorCorrectionLevel.L,
                EncodeHintType.MARGIN to 4, // ISO standard 4-module quiet zone
                EncodeHintType.CHARACTER_SET to "UTF-8"
            )
            val matrix = QRCodeWriter().encode(content, BarcodeFormat.QR_CODE, sizePx, sizePx, hints)
            val width = matrix.width
            val height = matrix.height
            val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)

            // Pure high-contrast black and pure white
            val colorBlack = Color.BLACK
            val colorWhite = Color.WHITE

            for (x in 0 until width) {
                for (y in 0 until height) {
                    bitmap.setPixel(x, y, if (matrix[x, y]) colorBlack else colorWhite)
                }
            }
            bitmap
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }
}
