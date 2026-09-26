package com.example.qr

import android.graphics.Bitmap
import android.graphics.Color
import com.example.data.model.TransferSession
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.qrcode.QRCodeWriter
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel
import org.json.JSONArray
import org.json.JSONObject

object QrCodeGenerator {

    fun generateSessionJson(session: TransferSession): String {
        return JSONObject().apply {
            put("v", 1)
            put("app", "SYED")
            put("sid", session.sessionId)
            put("tok", session.token)
            put("host", session.host)
            put("port", session.port)
            put("sender", session.senderName)
            put("exp", session.expiresAt)
            put("isGroup", session.isGroup)
            if (session.groupName.isNotBlank()) {
                put("grp", session.groupName)
            }
            val filesArr = JSONArray()
            session.files.forEach { file ->
                filesArr.put(
                    JSONObject().apply {
                        put("id", file.id)
                        put("name", file.name)
                        put("size", file.size)
                        put("mime", file.mimeType)
                        if (file.checksum.isNotBlank()) put("sha", file.checksum)
                    }
                )
            }
            put("files", filesArr)
        }.toString()
    }

    fun parseSessionJson(jsonStr: String): TransferSession? {
        return try {
            val json = JSONObject(jsonStr)
            if (json.optString("app") != "SYED" && !json.has("sid")) return null

            val filesArr = json.optJSONArray("files") ?: JSONArray()
            val files = mutableListOf<com.example.data.model.SessionFileMeta>()
            for (i in 0 until filesArr.length()) {
                val f = filesArr.getJSONObject(i)
                files.add(
                    com.example.data.model.SessionFileMeta(
                        id = f.optString("id", i.toString()),
                        name = f.optString("name", "Unknown File"),
                        size = f.optLong("size", 0L),
                        mimeType = f.optString("mime", "application/octet-stream"),
                        checksum = f.optString("sha", "")
                    )
                )
            }

            TransferSession(
                sessionId = json.getString("sid"),
                token = json.getString("tok"),
                host = json.getString("host"),
                port = json.getInt("port"),
                senderName = json.optString("sender", "Syed User"),
                files = files,
                expiresAt = json.optLong("exp", System.currentTimeMillis() + 600000),
                isGroup = json.optBoolean("isGroup", false),
                groupName = json.optString("grp", "")
            )
        } catch (e: Exception) {
            null
        }
    }

    fun generateQrBitmap(content: String, sizePx: Int = 600): Bitmap? {
        return try {
            val hints = mapOf(
                EncodeHintType.ERROR_CORRECTION to ErrorCorrectionLevel.M,
                EncodeHintType.MARGIN to 2
            )
            val matrix = QRCodeWriter().encode(content, BarcodeFormat.QR_CODE, sizePx, sizePx, hints)
            val width = matrix.width
            val height = matrix.height
            val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)

            // Deep Navy / Dark Charcoal foreground (#0F172A), Pure White background (#FFFFFF)
            val colorDark = Color.rgb(15, 23, 42)
            val colorLight = Color.WHITE

            for (x in 0 until width) {
                for (y in 0 until height) {
                    bitmap.setPixel(x, y, if (matrix[x, y]) colorDark else colorLight)
                }
            }
            bitmap
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }
}
