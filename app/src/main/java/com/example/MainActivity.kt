package com.example

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.data.model.ThemeMode
import com.example.data.model.formatFileSize
import com.example.network.p2p.DuplicatePolicy
import com.example.ui.screens.FileManagerScreen
import com.example.ui.screens.GroupShareScreen
import com.example.ui.screens.HistoryScreen
import com.example.ui.screens.HomeScreen
import com.example.ui.screens.NearbyScreen
import com.example.ui.screens.OnboardingScreen
import com.example.ui.screens.QrConnectScreen
import com.example.ui.screens.QrScanScreen
import com.example.ui.screens.ReceiveScreen
import com.example.ui.screens.SendFileScreen
import com.example.ui.screens.SettingsScreen
import com.example.ui.screens.SplashScreen
import com.example.ui.screens.TransferActiveScreen
import com.example.ui.theme.SyedBlue
import com.example.ui.theme.SyedCyan
import com.example.ui.theme.SyedNavy
import com.example.ui.theme.SyedTheme
import com.example.ui.viewmodel.Screen
import com.example.ui.viewmodel.SyedMainViewModel

class MainActivity : ComponentActivity() {

    private var viewModelInstance: SyedMainViewModel? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent {
            val viewModel: SyedMainViewModel = viewModel()
            viewModelInstance = viewModel

            // Handle incoming shared files from other apps
            LaunchedEffect(intent) {
                handleIncomingShareIntent(intent, viewModel)
            }

            val appSettings by viewModel.appSettings.collectAsState()
            val isDark = when (appSettings.themeMode) {
                ThemeMode.LIGHT -> false
                ThemeMode.DARK -> true
                ThemeMode.SYSTEM -> isSystemInDarkTheme()
            }

            SyedTheme(darkTheme = isDark) {
                SyedAppRoot(viewModel)
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        viewModelInstance?.let { handleIncomingShareIntent(intent, it) }
    }

    private fun handleIncomingShareIntent(intent: Intent?, vm: SyedMainViewModel) {
        if (intent == null) return
        when (intent.action) {
            Intent.ACTION_SEND -> {
                val uri = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    intent.getParcelableExtra(Intent.EXTRA_STREAM, Uri::class.java)
                } else {
                    @Suppress("DEPRECATION")
                    intent.getParcelableExtra(Intent.EXTRA_STREAM)
                }
                if (uri != null) {
                    vm.addFilesFromUris(listOf(uri))
                    vm.navigateTo(Screen.Send)
                }
            }
            Intent.ACTION_SEND_MULTIPLE -> {
                val uris = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    intent.getParcelableArrayListExtra(Intent.EXTRA_STREAM, Uri::class.java)
                } else {
                    @Suppress("DEPRECATION")
                    intent.getParcelableArrayListExtra(Intent.EXTRA_STREAM)
                }
                if (!uris.isNullOrEmpty()) {
                    vm.addFilesFromUris(uris)
                    vm.navigateTo(Screen.Send)
                }
            }
        }
    }
}

@Composable
fun SyedAppRoot(viewModel: SyedMainViewModel) {
    val currentScreen by viewModel.currentScreen.collectAsState()
    val userProfile by viewModel.userProfile.collectAsState()
    val appSettings by viewModel.appSettings.collectAsState()
    val recentTransfers by viewModel.recentTransfers.collectAsState()
    val allTransfers by viewModel.allTransfers.collectAsState()
    val selectedFiles by viewModel.selectedFiles.collectAsState()
    val deviceFiles by viewModel.deviceFiles.collectAsState()
    val selectedCategory by viewModel.selectedCategory.collectAsState()
    val searchQuery by viewModel.searchQuery.collectAsState()
    val activeSession by viewModel.activeSession.collectAsState()
    val sessionExpirySeconds by viewModel.sessionExpirySeconds.collectAsState()
    val receiverProgressMap by viewModel.receiverProgressMap.collectAsState()
    val clientTransferProgress by viewModel.clientTransferProgress.collectAsState()
    val nearbyDevices by viewModel.nearbyDevices.collectAsState()
    val incomingRequest by viewModel.incomingRequest.collectAsState()
    val statusMessage by viewModel.statusMessage.collectAsState()

    // Handle Android system back gesture for custom screen navigation
    if (currentScreen != Screen.Home && currentScreen != Screen.Splash && currentScreen != Screen.Onboarding) {
        BackHandler {
            viewModel.navigateBack()
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        when (currentScreen) {
            Screen.Splash -> {
                SplashScreen(onTimeout = { viewModel.completeSplash() })
            }

            Screen.Onboarding -> {
                OnboardingScreen(
                    onComplete = { name, photoUri ->
                        viewModel.completeOnboarding(name, photoUri)
                    }
                )
            }

            Screen.Home -> {
                HomeScreen(
                    userProfile = userProfile,
                    recentTransfers = recentTransfers,
                    onSendClick = { viewModel.navigateTo(Screen.Send) },
                    onReceiveClick = { viewModel.navigateTo(Screen.Receive) },
                    onScanQrClick = { viewModel.navigateTo(Screen.QrScan) },
                    onNearbyClick = { viewModel.navigateTo(Screen.Nearby) },
                    onFileManagerClick = { viewModel.navigateTo(Screen.FileManager) },
                    onHistoryClick = { viewModel.navigateTo(Screen.History) },
                    onSettingsClick = { viewModel.navigateTo(Screen.Settings) },
                    onTransferItemClick = { item -> viewModel.openFile(item.filePath) }
                )
            }

            Screen.Send -> {
                SendFileScreen(
                    deviceFiles = deviceFiles,
                    selectedFiles = selectedFiles,
                    selectedCategory = selectedCategory,
                    searchQuery = searchQuery,
                    onBackClick = { viewModel.navigateBack() },
                    onCategorySelected = { viewModel.setFileCategory(it) },
                    onSearchQueryChange = { viewModel.setSearchQuery(it) },
                    onFileToggled = { viewModel.toggleFileSelection(it) },
                    onFilesAddedFromPicker = { viewModel.addFilesFromUris(it) },
                    onStartQrConnection = { viewModel.startSenderSession(isGroup = false) },
                    onStartNearbyConnection = { viewModel.navigateTo(Screen.Nearby) },
                    onStartGroupShare = { viewModel.startSenderSession(isGroup = true) }
                )
            }

            Screen.QrConnect -> {
                QrConnectScreen(
                    session = activeSession,
                    expirySeconds = sessionExpirySeconds,
                    receiverProgressMap = receiverProgressMap,
                    onBackClick = {
                        viewModel.stopSenderSession()
                        viewModel.navigateBack()
                    },
                    onCancelSession = {
                        viewModel.stopSenderSession()
                        viewModel.navigateBack()
                    }
                )
            }

            Screen.QrScan -> {
                QrScanScreen(
                    onBackClick = { viewModel.navigateBack() },
                    onQrScanned = { qrText ->
                        viewModel.onQrScanned(qrText)
                    },
                    onManualConnect = { host, port, token ->
                        viewModel.connectToSession(host, port, token)
                    }
                )
            }

            Screen.Receive -> {
                ReceiveScreen(
                    userProfile = userProfile,
                    onBackClick = { viewModel.navigateBack() },
                    onScanQrClick = { viewModel.navigateTo(Screen.QrScan) },
                    onNearbyClick = { viewModel.navigateTo(Screen.Nearby) },
                    onStartDiscovery = { viewModel.startNearbyDiscovery() },
                    onStopDiscovery = { viewModel.stopNearbyDiscovery() }
                )
            }

            Screen.Nearby -> {
                NearbyScreen(
                    nearbyDevices = nearbyDevices,
                    onBackClick = { viewModel.navigateBack() },
                    onStartDiscovery = { viewModel.startNearbyDiscovery() },
                    onStopDiscovery = { viewModel.stopNearbyDiscovery() },
                    onDeviceSelected = { device ->
                        if (device.token.isNotBlank()) {
                            viewModel.connectToSession(device.host, device.port, device.token)
                        } else {
                            viewModel.showStatus("Device ${device.name} is available")
                        }
                    }
                )
            }

            Screen.GroupShare -> {
                GroupShareScreen(
                    session = activeSession,
                    receiverProgressMap = receiverProgressMap,
                    onBackClick = {
                        viewModel.stopSenderSession()
                        viewModel.navigateBack()
                    },
                    onCancelGroup = {
                        viewModel.stopSenderSession()
                        viewModel.navigateBack()
                    }
                )
            }

            Screen.ActiveTransfer -> {
                TransferActiveScreen(
                    progress = clientTransferProgress,
                    onBackClick = { viewModel.navigateTo(Screen.Home) },
                    onPause = { viewModel.pauseTransfer() },
                    onResume = { viewModel.resumeTransfer() },
                    onCancel = { viewModel.cancelTransfer() },
                    onHomeClick = { viewModel.navigateTo(Screen.Home) }
                )
            }

            Screen.History -> {
                HistoryScreen(
                    transfers = allTransfers,
                    onBackClick = { viewModel.navigateBack() },
                    onOpenFile = { filePath -> viewModel.openFile(filePath) },
                    onShareFile = { filePath -> viewModel.shareFileOutside(filePath) },
                    onDeleteRecord = { id -> viewModel.deleteHistoryRecord(id) },
                    onClearAll = { viewModel.clearAllHistory() }
                )
            }

            Screen.FileManager -> {
                FileManagerScreen(
                    files = deviceFiles,
                    onBackClick = { viewModel.navigateBack() },
                    onSendFile = { file ->
                        viewModel.clearSelectedFiles()
                        viewModel.toggleFileSelection(file)
                        viewModel.navigateTo(Screen.Send)
                    },
                    onOpenFile = { file ->
                        viewModel.openFile(file.uriString)
                    }
                )
            }

            Screen.Settings -> {
                SettingsScreen(
                    userProfile = userProfile,
                    appSettings = appSettings,
                    onBackClick = { viewModel.navigateBack() },
                    onSaveProfile = { name, photo, device ->
                        viewModel.updateProfile(name, photo, device)
                    },
                    onSaveSettings = { settings ->
                        viewModel.updateSettings(settings)
                    }
                )
            }
        }

        // Global Incoming Transfer Dialog
        if (incomingRequest != null) {
            val req = incomingRequest!!
            val totalSize = req.files.sumOf { it.size }

            AlertDialog(
                onDismissRequest = { viewModel.declineIncomingTransfer() },
                title = {
                    Text(
                        text = "Incoming Transfer",
                        style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold)
                    )
                },
                text = {
                    Column {
                        Text(
                            text = "${req.senderName} wants to send you:",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Spacer(modifier = Modifier.height(10.dp))
                        Card(
                            shape = RoundedCornerShape(10.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                        ) {
                            Column(modifier = Modifier.padding(12.dp)) {
                                Text(
                                    text = "${req.files.size} files (${formatFileSize(totalSize)})",
                                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                                    color = SyedBlue
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = req.files.joinToString(", ") { it.name }.take(80) + if (req.files.size > 2) "…" else "",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                },
                confirmButton = {
                    Button(
                        onClick = { viewModel.acceptIncomingTransfer() },
                        colors = ButtonDefaults.buttonColors(containerColor = SyedBlue),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Text("Accept")
                    }
                },
                dismissButton = {
                    TextButton(onClick = { viewModel.declineIncomingTransfer() }) {
                        Text("Decline", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            )
        }

        // Bottom Status Floating Banner
        AnimatedVisibility(
            visible = statusMessage != null,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 24.dp)
        ) {
            Card(
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(containerColor = SyedNavy),
                elevation = CardDefaults.cardElevation(defaultElevation = 6.dp),
                modifier = Modifier.padding(horizontal = 24.dp)
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.Info,
                        contentDescription = null,
                        tint = SyedCyan,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = statusMessage ?: "",
                        style = MaterialTheme.typography.bodyMedium,
                        color = Color.White
                    )
                }
            }
        }
    }
}
