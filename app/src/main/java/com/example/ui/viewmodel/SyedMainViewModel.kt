package com.example.ui.viewmodel

import android.app.Application
import android.content.ContentUris
import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.util.Log
import androidx.core.content.FileProvider
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.local.AppDatabase
import com.example.data.local.TransferDirection
import com.example.data.local.TransferEntity
import com.example.data.local.TransferStatus
import com.example.data.model.AppSettings
import com.example.data.model.FileCategory
import com.example.data.model.PeerDevice
import com.example.data.model.SessionFileMeta
import com.example.data.model.SharedFile
import com.example.data.model.ThemeMode
import com.example.data.model.TransferSession
import com.example.data.model.UserProfile
import com.example.data.repository.ProfileRepository
import com.example.data.repository.SettingsRepository
import com.example.data.repository.TransferHistoryRepository
import com.example.network.NetworkUtils
import com.example.network.UdpDiscoveryManager
import com.example.network.p2p.ClientTransferProgress
import com.example.network.p2p.DuplicatePolicy
import com.example.network.p2p.ReceiverProgress
import com.example.network.p2p.SyedTransferClient
import com.example.network.p2p.SyedTransferServer
import com.example.qr.QrCodeGenerator
import com.example.service.TransferForegroundService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.util.UUID

sealed class Screen {
    object Splash : Screen()
    object Onboarding : Screen()
    object Home : Screen()
    object Send : Screen()
    object QrConnect : Screen()
    object QrScan : Screen()
    object Receive : Screen()
    object Nearby : Screen()
    object GroupShare : Screen()
    object ActiveTransfer : Screen()
    object History : Screen()
    object FileManager : Screen()
    object Settings : Screen()
}

data class IncomingTransferRequest(
    val host: String,
    val port: Int,
    val token: String,
    val senderName: String,
    val files: List<SessionFileMeta>
)

class SyedMainViewModel(application: Application) : AndroidViewModel(application) {

    private val profileRepository = ProfileRepository(application)
    private val settingsRepository = SettingsRepository(application)
    private val historyRepository = TransferHistoryRepository(
        AppDatabase.getDatabase(application).transferDao()
    )

    private val udpDiscovery = UdpDiscoveryManager(application)
    private val transferServer = SyedTransferServer(application)
    private val transferClient = SyedTransferClient(application, historyRepository)

    // Navigation & Screen Stack
    private val _currentScreen = MutableStateFlow<Screen>(Screen.Splash)
    val currentScreen: StateFlow<Screen> = _currentScreen.asStateFlow()

    private val navigationBackStack = mutableListOf<Screen>()

    // Profile & Settings
    val userProfile: StateFlow<UserProfile> = profileRepository.userProfile.stateIn(
        scope = viewModelScope,
        started = SharingStarted.Eagerly,
        initialValue = UserProfile()
    )

    val appSettings: StateFlow<AppSettings> = settingsRepository.settings.stateIn(
        scope = viewModelScope,
        started = SharingStarted.Eagerly,
        initialValue = AppSettings()
    )

    val isOnboardingCompleted: StateFlow<Boolean> = profileRepository.isOnboardingCompleted.stateIn(
        scope = viewModelScope,
        started = SharingStarted.Eagerly,
        initialValue = false
    )

    // History
    val recentTransfers: StateFlow<List<TransferEntity>> = historyRepository.recentTransfers.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList()
    )

    val allTransfers: StateFlow<List<TransferEntity>> = historyRepository.allTransfers.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList()
    )

    // File Selection (for Sending)
    private val _selectedFiles = MutableStateFlow<List<SharedFile>>(emptyList())
    val selectedFiles: StateFlow<List<SharedFile>> = _selectedFiles.asStateFlow()

    private val _deviceFiles = MutableStateFlow<List<SharedFile>>(emptyList())
    val deviceFiles: StateFlow<List<SharedFile>> = _deviceFiles.asStateFlow()

    private val _selectedCategory = MutableStateFlow(FileCategory.ALL)
    val selectedCategory: StateFlow<FileCategory> = _selectedCategory.asStateFlow()

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    // Active Transfer Sessions
    private val _activeSession = MutableStateFlow<TransferSession?>(null)
    val activeSession: StateFlow<TransferSession?> = _activeSession.asStateFlow()

    private val _sessionExpirySeconds = MutableStateFlow(600)
    val sessionExpirySeconds: StateFlow<Int> = _sessionExpirySeconds.asStateFlow()
    private var expiryCountdownJob: Job? = null

    // Server State
    val receiverProgressMap: StateFlow<Map<String, ReceiverProgress>> = transferServer.receiverProgressMap

    // Client State
    val clientTransferProgress: StateFlow<ClientTransferProgress?> = transferClient.transferProgress

    // Incoming Transfer Dialog
    private val _incomingRequest = MutableStateFlow<IncomingTransferRequest?>(null)
    val incomingRequest: StateFlow<IncomingTransferRequest?> = _incomingRequest.asStateFlow()

    // Duplicate Dialog State
    private val _duplicatePrompt = MutableStateFlow<String?>(null)
    val duplicatePrompt: StateFlow<String?> = _duplicatePrompt.asStateFlow()

    // Nearby Devices
    val nearbyDevices: StateFlow<List<PeerDevice>> = udpDiscovery.nearbyDevices

    // Status / Toast Banner
    private val _statusMessage = MutableStateFlow<String?>(null)
    val statusMessage: StateFlow<String?> = _statusMessage.asStateFlow()

    init {
        // Load initial files
        loadDeviceFiles()
    }

    // Navigation
    fun navigateTo(screen: Screen) {
        if (_currentScreen.value != screen) {
            navigationBackStack.add(_currentScreen.value)
            _currentScreen.value = screen
        }
    }

    fun navigateBack(): Boolean {
        if (navigationBackStack.isNotEmpty()) {
            val prev = navigationBackStack.removeAt(navigationBackStack.size - 1)
            _currentScreen.value = prev
            return true
        }
        return false
    }

    fun completeSplash() {
        if (isOnboardingCompleted.value) {
            _currentScreen.value = Screen.Home
        } else {
            _currentScreen.value = Screen.Onboarding
        }
    }

    fun completeOnboarding(name: String, photoUri: String?) {
        viewModelScope.launch {
            val device = userProfile.value.deviceName
            profileRepository.saveProfile(name.ifBlank { "Syed User" }, photoUri, device)
            profileRepository.completeOnboarding()
            _currentScreen.value = Screen.Home
        }
    }

    // Profile & Settings
    fun updateProfile(name: String, photoUri: String?, deviceName: String) {
        viewModelScope.launch {
            profileRepository.saveProfile(name, photoUri, deviceName)
            showStatus("Profile updated")
        }
    }

    fun updateSettings(settings: AppSettings) {
        viewModelScope.launch {
            settingsRepository.updateSettings(settings)
            showStatus("Settings saved")
        }
    }

    // File Selection
    fun toggleFileSelection(file: SharedFile) {
        val current = _selectedFiles.value.toMutableList()
        val index = current.indexOfFirst { it.id == file.id }
        if (index >= 0) {
            current.removeAt(index)
        } else {
            current.add(file)
        }
        _selectedFiles.value = current
    }

    fun addFilesFromUris(uris: List<Uri>) {
        viewModelScope.launch(Dispatchers.IO) {
            val added = mutableListOf<SharedFile>()
            val cr = getApplication<Application>().contentResolver

            for (uri in uris) {
                try {
                    var name = "file_${System.currentTimeMillis()}"
                    var size = 0L
                    var mime = cr.getType(uri) ?: "application/octet-stream"

                    cr.query(uri, null, null, null, null)?.use { cursor ->
                        val nameIndex = cursor.getColumnIndex(MediaStore.MediaColumns.DISPLAY_NAME)
                        val sizeIndex = cursor.getColumnIndex(MediaStore.MediaColumns.SIZE)
                        if (cursor.moveToFirst()) {
                            if (nameIndex >= 0) name = cursor.getString(nameIndex) ?: name
                            if (sizeIndex >= 0) size = cursor.getLong(sizeIndex)
                        }
                    }

                    val cat = detectCategory(name, mime)
                    val isApk = name.endsWith(".apk", ignoreCase = true)

                    added.add(
                        SharedFile(
                            id = UUID.randomUUID().toString(),
                            name = name,
                            size = size,
                            mimeType = mime,
                            uriString = uri.toString(),
                            category = cat,
                            isApk = isApk
                        )
                    )
                } catch (e: Exception) {
                    Log.e("SyedViewModel", "Error resolving URI $uri", e)
                }
            }

            val current = _selectedFiles.value.toMutableList()
            current.addAll(added)
            _selectedFiles.value = current
            showStatus("${added.size} files added")
        }
    }

    fun removeSelectedFile(file: SharedFile) {
        _selectedFiles.value = _selectedFiles.value.filter { it.id != file.id }
    }

    fun clearSelectedFiles() {
        _selectedFiles.value = emptyList()
    }

    fun setFileCategory(category: FileCategory) {
        _selectedCategory.value = category
    }

    fun setSearchQuery(query: String) {
        _searchQuery.value = query
    }

    // Query Local Files (MediaStore / Installed APKs / Internal)
    fun loadDeviceFiles() {
        viewModelScope.launch(Dispatchers.IO) {
            val list = mutableListOf<SharedFile>()
            val context = getApplication<Application>()

            try {
                // Query MediaStore Files
                val projection = arrayOf(
                    MediaStore.Files.FileColumns._ID,
                    MediaStore.Files.FileColumns.DISPLAY_NAME,
                    MediaStore.Files.FileColumns.SIZE,
                    MediaStore.Files.FileColumns.MIME_TYPE,
                    MediaStore.Files.FileColumns.DATE_MODIFIED
                )

                val uri = MediaStore.Files.getContentUri("external")
                context.contentResolver.query(
                    uri,
                    projection,
                    null,
                    null,
                    "${MediaStore.Files.FileColumns.DATE_MODIFIED} DESC LIMIT 150"
                )?.use { cursor ->
                    val idCol = cursor.getColumnIndexOrThrow(MediaStore.Files.FileColumns._ID)
                    val nameCol = cursor.getColumnIndexOrThrow(MediaStore.Files.FileColumns.DISPLAY_NAME)
                    val sizeCol = cursor.getColumnIndexOrThrow(MediaStore.Files.FileColumns.SIZE)
                    val mimeCol = cursor.getColumnIndexOrThrow(MediaStore.Files.FileColumns.MIME_TYPE)

                    while (cursor.moveToNext()) {
                        val id = cursor.getLong(idCol)
                        val name = cursor.getString(nameCol) ?: "file_$id"
                        val size = cursor.getLong(sizeCol)
                        val mime = cursor.getString(mimeCol) ?: "application/octet-stream"
                        val contentUri = ContentUris.withAppendedId(uri, id)

                        list.add(
                            SharedFile(
                                id = id.toString(),
                                name = name,
                                size = size,
                                mimeType = mime,
                                uriString = contentUri.toString(),
                                category = detectCategory(name, mime),
                                isApk = name.endsWith(".apk", ignoreCase = true)
                            )
                        )
                    }
                }
            } catch (e: Exception) {
                Log.e("SyedViewModel", "Error loading MediaStore files", e)
            }

            // Also load user-installed non-system APKs for APK Sharing
            try {
                val pm = context.packageManager
                val packages = pm.getInstalledApplications(0)
                for (app in packages) {
                    if ((app.flags and ApplicationInfo.FLAG_SYSTEM) == 0) {
                        val apkFile = File(app.sourceDir)
                        if (apkFile.exists() && apkFile.length() > 0) {
                            val label = pm.getApplicationLabel(app).toString()
                            list.add(
                                SharedFile(
                                    id = "apk_${app.packageName}",
                                    name = "$label.apk",
                                    size = apkFile.length(),
                                    mimeType = "application/vnd.android.package-archive",
                                    uriString = Uri.fromFile(apkFile).toString(),
                                    category = FileCategory.APKS,
                                    isApk = true,
                                    apkVersion = pm.getPackageInfo(app.packageName, 0).versionName
                                )
                            )
                        }
                    }
                }
            } catch (_: Exception) {}

            _deviceFiles.value = list
        }
    }

    private fun detectCategory(name: String, mime: String): FileCategory {
        val lower = name.lowercase()
        return when {
            mime.startsWith("image/") || lower.endsWith(".jpg") || lower.endsWith(".png") || lower.endsWith(".jpeg") || lower.endsWith(".webp") || lower.endsWith(".gif") -> FileCategory.PHOTOS
            mime.startsWith("video/") || lower.endsWith(".mp4") || lower.endsWith(".mkv") || lower.endsWith(".mov") || lower.endsWith(".avi") -> FileCategory.VIDEOS
            mime.startsWith("audio/") || lower.endsWith(".mp3") || lower.endsWith(".wav") || lower.endsWith(".m4a") || lower.endsWith(".aac") -> FileCategory.AUDIO
            mime == "application/pdf" || lower.endsWith(".pdf") || lower.endsWith(".doc") || lower.endsWith(".docx") || lower.endsWith(".txt") || lower.endsWith(".xlsx") || lower.endsWith(".pptx") -> FileCategory.DOCUMENTS
            lower.endsWith(".apk") || mime == "application/vnd.android.package-archive" -> FileCategory.APKS
            lower.endsWith(".zip") || lower.endsWith(".rar") || lower.endsWith(".7z") || lower.endsWith(".tar") || lower.endsWith(".gz") -> FileCategory.ARCHIVES
            else -> FileCategory.OTHER
        }
    }

    // QR Sender Session Setup
    fun startSenderSession(isGroup: Boolean = false, groupName: String = "SYED Group") {
        val files = _selectedFiles.value
        if (files.isEmpty()) {
            showStatus("Please select at least one file to send")
            return
        }

        viewModelScope.launch(Dispatchers.IO) {
            val context = getApplication<Application>()
            val hostIp = NetworkUtils.getLocalIpAddress(context)
            val sessionId = UUID.randomUUID().toString()
            val token = UUID.randomUUID().toString().take(12)

            val session = TransferSession(
                sessionId = sessionId,
                token = token,
                host = hostIp,
                port = SyedTransferServer.DEFAULT_PORT,
                senderName = userProfile.value.displayName,
                files = files.map {
                    SessionFileMeta(
                        id = it.id,
                        name = it.name,
                        size = it.size,
                        mimeType = it.mimeType,
                        checksum = "" // Computed on stream or pre-computed
                    )
                },
                expiresAt = System.currentTimeMillis() + (10 * 60 * 1000),
                isGroup = isGroup,
                groupName = if (isGroup) groupName else ""
            )

            val actualPort = transferServer.start(viewModelScope, session, files) { receiverName ->
                showStatus("$receiverName connected!")
            }

            val finalSession = session.copy(port = actualPort)
            _activeSession.value = finalSession

            // Start UDP discovery beacon
            udpDiscovery.startBroadcasting(
                scope = viewModelScope,
                deviceId = sessionId,
                displayName = userProfile.value.displayName,
                serverPort = actualPort,
                token = token,
                status = if (isGroup) "Group: $groupName" else "Ready to send ${files.size} files"
            )

            // Start expiry countdown
            startExpiryCountdown()

            withContext(Dispatchers.Main) {
                if (isGroup) {
                    navigateTo(Screen.GroupShare)
                } else {
                    navigateTo(Screen.QrConnect)
                }
            }
        }
    }

    private fun startExpiryCountdown() {
        expiryCountdownJob?.cancel()
        _sessionExpirySeconds.value = 600
        expiryCountdownJob = viewModelScope.launch {
            while (_sessionExpirySeconds.value > 0) {
                delay(1000)
                _sessionExpirySeconds.value -= 1
            }
            stopSenderSession()
            showStatus("Transfer session expired")
        }
    }

    fun stopSenderSession() {
        expiryCountdownJob?.cancel()
        transferServer.stop()
        udpDiscovery.stopBroadcasting()
        _activeSession.value = null
    }

    // QR Scanning / Receiver Connection
    fun onQrScanned(qrRawContent: String) {
        val session = QrCodeGenerator.parseSessionJson(qrRawContent)
        if (session != null) {
            connectToSession(session.host, session.port, session.token)
        } else {
            showStatus("Invalid SYED QR code")
        }
    }

    fun connectToSession(host: String, port: Int, token: String) {
        viewModelScope.launch(Dispatchers.IO) {
            val receiverName = userProfile.value.displayName
            val manifest = transferClient.fetchManifest(host, port, token, receiverName)
            if (manifest != null) {
                val (senderName, files) = manifest
                _incomingRequest.value = IncomingTransferRequest(
                    host = host,
                    port = port,
                    token = token,
                    senderName = senderName,
                    files = files
                )
            } else {
                showStatus("Could not connect to sender. Check local Wi-Fi / Hotspot.")
            }
        }
    }

    fun acceptIncomingTransfer(policy: DuplicatePolicy = DuplicatePolicy.KEEP_BOTH) {
        val req = _incomingRequest.value ?: return
        _incomingRequest.value = null

        viewModelScope.launch(Dispatchers.IO) {
            val context = getApplication<Application>()
            TransferForegroundService.startService(context, req.files.firstOrNull()?.name ?: "Transfer")

            withContext(Dispatchers.Main) {
                navigateTo(Screen.ActiveTransfer)
            }

            val success = transferClient.downloadFiles(
                host = req.host,
                port = req.port,
                token = req.token,
                senderName = req.senderName,
                receiverName = userProfile.value.displayName,
                files = req.files,
                duplicatePolicy = policy,
                onFileDownloaded = { fileName, file ->
                    showStatus("Received: $fileName")
                }
            )

            TransferForegroundService.stopService(context)
            if (success) {
                showStatus("All files received successfully!")
            } else {
                showStatus("Transfer finished with warnings or cancellation")
            }
        }
    }

    fun declineIncomingTransfer() {
        _incomingRequest.value = null
        showStatus("Transfer declined")
    }

    fun pauseTransfer() {
        transferClient.pause()
    }

    fun resumeTransfer() {
        transferClient.resume()
    }

    fun cancelTransfer() {
        transferClient.cancel()
        val context = getApplication<Application>()
        TransferForegroundService.stopService(context)
        showStatus("Transfer cancelled")
    }

    // Nearby Discovery
    fun startNearbyDiscovery() {
        udpDiscovery.startListening(viewModelScope)
    }

    fun stopNearbyDiscovery() {
        udpDiscovery.stopListening()
    }

    // Transfer History Management
    fun deleteHistoryRecord(id: Long) {
        viewModelScope.launch {
            historyRepository.deleteTransfer(id)
            showStatus("History record deleted")
        }
    }

    fun clearAllHistory() {
        viewModelScope.launch {
            historyRepository.clearAll()
            showStatus("History cleared")
        }
    }

    fun openFile(filePath: String?) {
        if (filePath.isNullOrBlank()) return
        val context = getApplication<Application>()
        try {
            val file = File(filePath)
            if (!file.exists()) {
                showStatus("File does not exist on storage")
                return
            }
            val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
            val intent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, context.contentResolver.getType(uri) ?: "*/*")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
        } catch (e: Exception) {
            showStatus("Cannot open file: ${e.localizedMessage}")
        }
    }

    fun shareFileOutside(filePath: String?) {
        if (filePath.isNullOrBlank()) return
        val context = getApplication<Application>()
        try {
            val file = File(filePath)
            if (!file.exists()) {
                showStatus("File not found")
                return
            }
            val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
            val intent = Intent(Intent.ACTION_SEND).apply {
                type = context.contentResolver.getType(uri) ?: "*/*"
                putExtra(Intent.EXTRA_STREAM, uri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            val chooser = Intent.createChooser(intent, "Share via").apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(chooser)
        } catch (e: Exception) {
            showStatus("Cannot share: ${e.localizedMessage}")
        }
    }

    fun showStatus(msg: String) {
        viewModelScope.launch {
            _statusMessage.value = msg
            delay(3500)
            if (_statusMessage.value == msg) {
                _statusMessage.value = null
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
        stopSenderSession()
        stopNearbyDiscovery()
    }
}
