package com.example.network.p2p

import android.content.Context
import android.net.Uri
import android.util.Log
import com.example.data.model.SessionFileMeta
import com.example.data.model.SharedFile
import com.example.data.model.TransferSession
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.OutputStream
import java.net.ServerSocket
import java.net.Socket
import java.net.URLDecoder
import java.util.concurrent.ConcurrentHashMap

data class ReceiverProgress(
    val receiverId: String,
    val receiverName: String,
    val fileId: String,
    val fileName: String,
    val bytesTransferred: Long,
    val totalBytes: Long,
    val progressPercent: Int,
    val speedBytesPerSec: Long,
    val isComplete: Boolean = false,
    val hasError: Boolean = false,
    val errorMessage: String? = null
)

class SyedTransferServer(private val context: Context) {
    companion object {
        private const val TAG = "SyedTransferServer"
        const val DEFAULT_PORT = 8888
    }

    private var serverSocket: ServerSocket? = null
    private var serverJob: Job? = null

    private val _receiverProgressMap = MutableStateFlow<Map<String, ReceiverProgress>>(emptyMap())
    val receiverProgressMap: StateFlow<Map<String, ReceiverProgress>> = _receiverProgressMap.asStateFlow()

    private val activeStreams = ConcurrentHashMap<String, Boolean>()

    fun start(
        scope: CoroutineScope,
        session: TransferSession,
        files: List<SharedFile>,
        onReceiverConnected: ((receiverName: String) -> Unit)? = null
    ): Int {
        stop()

        val sSocket = try {
            ServerSocket(session.port)
        } catch (_: Exception) {
            ServerSocket(0) // bind to any available port
        }
        serverSocket = sSocket
        val actualPort = sSocket.localPort

        serverJob = scope.launch(Dispatchers.IO) {
            while (isActive && !sSocket.isClosed) {
                try {
                    val clientSocket = sSocket.accept()
                    launch(Dispatchers.IO) {
                        handleClient(clientSocket, session, files, onReceiverConnected)
                    }
                } catch (e: Exception) {
                    if (!isActive) break
                }
            }
        }
        return actualPort
    }

    private suspend fun handleClient(
        socket: Socket,
        session: TransferSession,
        files: List<SharedFile>,
        onReceiverConnected: ((receiverName: String) -> Unit)?
    ) {
        try {
            socket.soTimeout = 30000
            val reader = BufferedReader(InputStreamReader(socket.getInputStream()))
            val out = socket.getOutputStream()

            val requestLine = reader.readLine() ?: return
            val parts = requestLine.split(" ")
            if (parts.size < 2) return

            val method = parts[0]
            val fullPath = parts[1]

            val headers = mutableMapOf<String, String>()
            var line: String?
            while (reader.readLine().also { line = it } != null) {
                if (line!!.isBlank()) break
                val headerParts = line!!.split(": ", limit = 2)
                if (headerParts.size == 2) {
                    headers[headerParts[0].lowercase()] = headerParts[1]
                }
            }

            val path = fullPath.substringBefore("?")
            val queryParams = parseQueryParams(fullPath.substringAfter("?", ""))

            val clientToken = queryParams["token"]
            val receiverName = queryParams["receiver"] ?: "Nearby Syed"
            val receiverId = queryParams["receiver_id"] ?: socket.inetAddress.hostAddress ?: "peer"

            // Validate token
            if (clientToken != session.token) {
                sendResponse(out, 403, "text/plain", "Forbidden: Invalid session token".toByteArray())
                return
            }

            withContext(Dispatchers.Main) {
                onReceiverConnected?.invoke(receiverName)
            }

            when (path) {
                "/manifest" -> {
                    val json = JSONObject().apply {
                        put("sessionId", session.sessionId)
                        put("senderName", session.senderName)
                        put("expiresAt", session.expiresAt)
                        val filesArr = JSONArray()
                        files.forEach { file ->
                            val fileJson = JSONObject().apply {
                                put("id", file.id)
                                put("name", file.name)
                                put("size", file.size)
                                put("mimeType", file.mimeType)
                                put("checksum", file.checksum)
                            }
                            filesArr.put(fileJson)
                        }
                        put("files", filesArr)
                    }
                    sendResponse(out, 200, "application/json", json.toString().toByteArray())
                }

                "/download" -> {
                    val fileId = queryParams["fileId"] ?: ""
                    val targetFile = files.find { it.id == fileId }
                    if (targetFile == null) {
                        sendResponse(out, 404, "text/plain", "File Not Found".toByteArray())
                        return
                    }

                    // Check Range header for resume
                    val rangeHeader = headers["range"]
                    var startOffset = 0L
                    if (rangeHeader != null && rangeHeader.startsWith("bytes=")) {
                        val rangeSpec = rangeHeader.removePrefix("bytes=").substringBefore("-")
                        startOffset = rangeSpec.toLongOrNull() ?: 0L
                    }

                    streamFile(out, targetFile, startOffset, receiverId, receiverName)
                }

                "/cancel" -> {
                    activeStreams[receiverId] = false
                    sendResponse(out, 200, "text/plain", "Cancelled".toByteArray())
                }

                else -> {
                    sendResponse(out, 404, "text/plain", "Not Found".toByteArray())
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Client handling error", e)
        } finally {
            try {
                socket.close()
            } catch (_: Exception) {}
        }
    }

    private fun streamFile(
        out: OutputStream,
        file: SharedFile,
        startOffset: Long,
        receiverId: String,
        receiverName: String
    ) {
        val totalSize = file.size
        val contentLength = totalSize - startOffset
        val isPartial = startOffset > 0

        val statusCode = if (isPartial) 206 else 200
        val statusText = if (isPartial) "Partial Content" else "OK"

        val headerBuilder = StringBuilder()
        headerBuilder.append("HTTP/1.1 $statusCode $statusText\r\n")
        headerBuilder.append("Content-Type: ${file.mimeType}\r\n")
        headerBuilder.append("Content-Length: $contentLength\r\n")
        headerBuilder.append("Accept-Ranges: bytes\r\n")
        if (isPartial) {
            headerBuilder.append("Content-Range: bytes $startOffset-${totalSize - 1}/$totalSize\r\n")
        }
        headerBuilder.append("Connection: close\r\n\r\n")

        out.write(headerBuilder.toString().toByteArray())
        out.flush()

        activeStreams[receiverId] = true

        val inputStream = try {
            val uri = Uri.parse(file.uriString)
            context.contentResolver.openInputStream(uri)
        } catch (e: Exception) {
            Log.e(TAG, "Cannot open stream for ${file.name}", e)
            return
        } ?: return

        try {
            if (startOffset > 0) {
                var skipped = 0L
                while (skipped < startOffset) {
                    val s = inputStream.skip(startOffset - skipped)
                    if (s <= 0) break
                    skipped += s
                }
            }

            val buffer = ByteArray(64 * 1024)
            var bytesTransferred = startOffset
            var lastSampleTime = System.currentTimeMillis()
            var lastSampleBytes = bytesTransferred
            var speedBytesPerSec = 0L

            var bytesRead = 0
            while (activeStreams[receiverId] == true && inputStream.read(buffer).also { bytesRead = it } != -1) {
                out.write(buffer, 0, bytesRead)
                bytesTransferred += bytesRead

                val now = System.currentTimeMillis()
                val delta = now - lastSampleTime
                if (delta >= 500) {
                    speedBytesPerSec = ((bytesTransferred - lastSampleBytes) * 1000) / delta
                    lastSampleTime = now
                    lastSampleBytes = bytesTransferred

                    val percent = if (totalSize > 0) ((bytesTransferred * 100) / totalSize).toInt() else 0
                    updateReceiverProgress(
                        ReceiverProgress(
                            receiverId = receiverId,
                            receiverName = receiverName,
                            fileId = file.id,
                            fileName = file.name,
                            bytesTransferred = bytesTransferred,
                            totalBytes = totalSize,
                            progressPercent = percent,
                            speedBytesPerSec = speedBytesPerSec,
                            isComplete = bytesTransferred >= totalSize
                        )
                    )
                }
            }
            out.flush()

            val isComplete = bytesTransferred >= totalSize
            updateReceiverProgress(
                ReceiverProgress(
                    receiverId = receiverId,
                    receiverName = receiverName,
                    fileId = file.id,
                    fileName = file.name,
                    bytesTransferred = bytesTransferred,
                    totalBytes = totalSize,
                    progressPercent = if (totalSize > 0) ((bytesTransferred * 100) / totalSize).toInt() else 100,
                    speedBytesPerSec = speedBytesPerSec,
                    isComplete = isComplete
                )
            )
        } catch (e: Exception) {
            Log.e(TAG, "Streaming error for ${file.name}", e)
            updateReceiverProgress(
                ReceiverProgress(
                    receiverId = receiverId,
                    receiverName = receiverName,
                    fileId = file.id,
                    fileName = file.name,
                    bytesTransferred = 0,
                    totalBytes = totalSize,
                    progressPercent = 0,
                    speedBytesPerSec = 0,
                    isComplete = false,
                    hasError = true,
                    errorMessage = e.message
                )
            )
        } finally {
            inputStream.close()
            activeStreams.remove(receiverId)
        }
    }

    private fun updateReceiverProgress(progress: ReceiverProgress) {
        val current = _receiverProgressMap.value.toMutableMap()
        current[progress.receiverId] = progress
        _receiverProgressMap.value = current
    }

    private fun sendResponse(out: OutputStream, code: Int, contentType: String, body: ByteArray) {
        val msg = when (code) {
            200 -> "OK"
            403 -> "Forbidden"
            404 -> "Not Found"
            else -> "Error"
        }
        val headers = "HTTP/1.1 $code $msg\r\n" +
                "Content-Type: $contentType\r\n" +
                "Content-Length: ${body.size}\r\n" +
                "Connection: close\r\n\r\n"
        out.write(headers.toByteArray())
        out.write(body)
        out.flush()
    }

    private fun parseQueryParams(queryString: String): Map<String, String> {
        val params = mutableMapOf<String, String>()
        if (queryString.isBlank()) return params
        val pairs = queryString.split("&")
        for (pair in pairs) {
            val idx = pair.indexOf("=")
            if (idx > 0) {
                val key = URLDecoder.decode(pair.substring(0, idx), "UTF-8")
                val value = URLDecoder.decode(pair.substring(idx + 1), "UTF-8")
                params[key] = value
            }
        }
        return params
    }

    fun stop() {
        serverJob?.cancel()
        serverJob = null
        try {
            serverSocket?.close()
        } catch (_: Exception) {}
        serverSocket = null
        _receiverProgressMap.value = emptyMap()
        activeStreams.clear()
    }
}
