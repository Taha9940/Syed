package com.example.ui.screens

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.Group
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.InsertDriveFile
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material.icons.filled.NearMe
import androidx.compose.material.icons.filled.PlayCircle
import androidx.compose.material.icons.filled.QrCode
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import coil.compose.AsyncImage
import com.example.R
import com.example.data.model.FileCategory
import com.example.data.model.SharedFile
import com.example.data.model.formatFileSize
import com.example.ui.components.SyedTopBar
import com.example.ui.theme.SyedBlue
import com.example.ui.theme.SyedCyan
import com.example.ui.theme.SyedError
import com.example.ui.theme.SyedSuccess
import com.example.ui.theme.SyedTeal

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SendFileScreen(
    deviceFiles: List<SharedFile>,
    selectedFiles: List<SharedFile>,
    selectedCategory: FileCategory,
    searchQuery: String,
    onBackClick: () -> Unit,
    onCategorySelected: (FileCategory) -> Unit,
    onSearchQueryChange: (String) -> Unit,
    onFileToggled: (SharedFile) -> Unit,
    onFilesAddedFromPicker: (List<Uri>) -> Unit,
    onStartQrConnection: () -> Unit,
    onStartNearbyConnection: () -> Unit,
    onStartGroupShare: () -> Unit,
    onStartCreateConnection: () -> Unit = onStartNearbyConnection,
    onRefreshMedia: () -> Unit = {},
    onRemoveFile: (SharedFile) -> Unit = onFileToggled,
    onClearSelected: () -> Unit = {}
) {
    val context = LocalContext.current
    var showConnectionSheet by remember { mutableStateOf(false) }
    val sheetState = rememberModalBottomSheetState()

    // Permission state check
    fun checkHasMediaPermission(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val imgGranted = ContextCompat.checkSelfPermission(
                context, Manifest.permission.READ_MEDIA_IMAGES
            ) == PackageManager.PERMISSION_GRANTED
            val vidGranted = ContextCompat.checkSelfPermission(
                context, Manifest.permission.READ_MEDIA_VIDEO
            ) == PackageManager.PERMISSION_GRANTED
            when (selectedCategory) {
                FileCategory.PHOTOS -> imgGranted
                FileCategory.VIDEOS -> vidGranted
                else -> imgGranted || vidGranted
            }
        } else {
            ContextCompat.checkSelfPermission(
                context, Manifest.permission.READ_EXTERNAL_STORAGE
            ) == PackageManager.PERMISSION_GRANTED
        }
    }

    var hasMediaAccess by remember(selectedCategory) { mutableStateOf(checkHasMediaPermission()) }

    // Modern permission launcher
    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val anyGranted = permissions.values.any { it }
        hasMediaAccess = checkHasMediaPermission() || anyGranted
        if (hasMediaAccess) {
            onRefreshMedia()
        }
    }

    // Photo/Media Picker launcher (Zero-permission Android Photo Picker for modern devices)
    val mediaPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickMultipleVisualMedia()
    ) { uris: List<Uri> ->
        if (uris.isNotEmpty()) {
            onFilesAddedFromPicker(uris)
        }
    }

    // Generic document picker fallback for documents / APKs / all files
    val documentPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenMultipleDocuments()
    ) { uris: List<Uri> ->
        if (uris.isNotEmpty()) {
            onFilesAddedFromPicker(uris)
        }
    }

    // Launch appropriate browse action when user taps [ Browse from Device ]
    fun launchBrowseFromDevice() {
        when (selectedCategory) {
            FileCategory.PHOTOS -> {
                mediaPickerLauncher.launch(
                    PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                )
            }
            FileCategory.VIDEOS -> {
                mediaPickerLauncher.launch(
                    PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.VideoOnly)
                )
            }
            else -> {
                mediaPickerLauncher.launch(
                    PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageAndVideo)
                )
            }
        }
    }

    fun requestMediaAccess() {
        val perms = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            arrayOf(
                Manifest.permission.READ_MEDIA_IMAGES,
                Manifest.permission.READ_MEDIA_VIDEO,
                Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED
            )
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            arrayOf(
                Manifest.permission.READ_MEDIA_IMAGES,
                Manifest.permission.READ_MEDIA_VIDEO
            )
        } else {
            arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE)
        }
        permissionLauncher.launch(perms)
    }

    // Filtered files
    val filteredFiles by remember(deviceFiles, selectedCategory, searchQuery) {
        derivedStateOf {
            deviceFiles.filter { file ->
                val matchesCategory = selectedCategory == FileCategory.ALL || file.category == selectedCategory
                val matchesSearch = searchQuery.isBlank() || file.name.contains(searchQuery, ignoreCase = true)
                matchesCategory && matchesSearch
            }
        }
    }

    val totalSelectedSize by remember(selectedFiles) {
        derivedStateOf { selectedFiles.sumOf { it.size } }
    }

    Scaffold(
        topBar = {
            SyedTopBar(
                title = stringResource(id = R.string.select_files),
                onBackClick = onBackClick
            )
        },
        bottomBar = {
            if (selectedFiles.isNotEmpty()) {
                Surface(
                    color = MaterialTheme.colorScheme.surface,
                    shadowElevation = 8.dp,
                    modifier = Modifier.testTag("send_bottom_bar")
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            IconButton(
                                onClick = onClearSelected,
                                modifier = Modifier.size(32.dp).testTag("clear_selection_button")
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Close,
                                    contentDescription = "Clear selected",
                                    tint = SyedError,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                            Spacer(modifier = Modifier.width(8.dp))
                            Column {
                                Text(
                                    text = "${selectedFiles.size} selected",
                                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Text(
                                    text = formatFileSize(totalSelectedSize),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = SyedCyan
                                )
                            }
                        }

                        Button(
                            onClick = { showConnectionSheet = true },
                            colors = ButtonDefaults.buttonColors(containerColor = SyedBlue),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier
                                .height(46.dp)
                                .testTag("continue_send_button")
                        ) {
                            Icon(imageVector = Icons.Default.Send, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = stringResource(id = R.string.action_send),
                                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                                color = Color.White
                            )
                        }
                    }
                }
            }
        },
        containerColor = MaterialTheme.colorScheme.background,
        modifier = Modifier.testTag("send_file_screen")
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            // Search Bar
            OutlinedTextField(
                value = searchQuery,
                onValueChange = onSearchQueryChange,
                placeholder = { Text("Search files…") },
                leadingIcon = {
                    Icon(imageVector = Icons.Default.Search, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                },
                trailingIcon = {
                    if (searchQuery.isNotEmpty()) {
                        IconButton(onClick = { onSearchQueryChange("") }) {
                            Icon(imageVector = Icons.Default.Clear, contentDescription = "Clear")
                        }
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 6.dp)
                    .testTag("file_search_input"),
                shape = RoundedCornerShape(14.dp),
                singleLine = true
            )

            // Category Chips: All, Photos, Videos, Audio, Documents, APKs, Archives
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                FileCategory.values().forEach { category ->
                    val isSelected = category == selectedCategory
                    FilterChip(
                        selected = isSelected,
                        onClick = {
                            onCategorySelected(category)
                            hasMediaAccess = checkHasMediaPermission()
                        },
                        label = { Text(category.displayName) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = SyedBlue,
                            selectedLabelColor = Color.White
                        ),
                        shape = RoundedCornerShape(20.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(4.dp))

            // Main Media Action: [ Browse from Device ]
            // Shown prominently at the top of the section (no folders / SD card / USB clutter)
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 6.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .clickable { launchBrowseFromDevice() }
                    .testTag("browse_from_device_button"),
                colors = CardDefaults.cardColors(containerColor = SyedBlue.copy(alpha = 0.09f)),
                elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center
                ) {
                    Icon(
                        imageVector = if (selectedCategory == FileCategory.VIDEOS) Icons.Default.Videocam
                        else if (selectedCategory == FileCategory.PHOTOS) Icons.Default.Image
                        else Icons.Default.FolderOpen,
                        contentDescription = null,
                        tint = SyedBlue,
                        modifier = Modifier.size(22.dp)
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(
                        text = stringResource(id = R.string.browse_from_device),
                        style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Bold),
                        color = SyedBlue
                    )
                }
            }

            // Grant Access Banner if permission is not yet granted for Photos / Videos
            if (!hasMediaAccess && (selectedCategory == FileCategory.PHOTOS || selectedCategory == FileCategory.VIDEOS || selectedCategory == FileCategory.ALL)) {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 6.dp)
                        .clip(RoundedCornerShape(14.dp)),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.weight(1f)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(36.dp)
                                    .clip(CircleShape)
                                    .background(SyedCyan.copy(alpha = 0.2f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.LockOpen,
                                    contentDescription = null,
                                    tint = SyedBlue,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                            Spacer(modifier = Modifier.width(10.dp))
                            Text(
                                text = "Grant permission to directly load device media",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }

                        Button(
                            onClick = { requestMediaAccess() },
                            colors = ButtonDefaults.buttonColors(containerColor = SyedBlue),
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.testTag("grant_access_button")
                        ) {
                            Text(
                                text = stringResource(id = R.string.grant_access),
                                style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold)
                            )
                        }
                    }
                }
            }

            // Media Grid / List Content
            if (filteredFiles.isEmpty()) {
                // Only show empty state message if media permission is granted and there is genuinely nothing found
                if (hasMediaAccess) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier.padding(24.dp)
                        ) {
                            Icon(
                                imageVector = if (selectedCategory == FileCategory.PHOTOS) Icons.Default.Image
                                else if (selectedCategory == FileCategory.VIDEOS) Icons.Default.Videocam
                                else Icons.Default.InsertDriveFile,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
                                modifier = Modifier.size(48.dp)
                            )
                            Spacer(modifier = Modifier.height(10.dp))
                            Text(
                                text = "Tap \"Browse from Device\" above to pick files",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                } else {
                    Spacer(modifier = Modifier.weight(1f))
                }
            } else if (selectedCategory == FileCategory.PHOTOS) {
                // Photos View: Clean visual 3-column media grid
                LazyVerticalGrid(
                    columns = GridCells.Fixed(3),
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 6.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(filteredFiles, key = { it.id }) { file ->
                        val isSelected = selectedFiles.any { it.id == file.id }
                        PhotoMediaCard(
                            file = file,
                            isSelected = isSelected,
                            onToggle = { onFileToggled(file) },
                            onRemove = { onRemoveFile(file) }
                        )
                    }
                }
            } else {
                // List View for Videos, Audio, Documents, APKs, All
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 6.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(filteredFiles, key = { it.id }) { file ->
                        val isSelected = selectedFiles.any { it.id == file.id }
                        MediaListItemCard(
                            file = file,
                            isSelected = isSelected,
                            onToggle = { onFileToggled(file) },
                            onRemove = { onRemoveFile(file) }
                        )
                    }
                }
            }
        }
    }

    // Connection Options Bottom Sheet (Method 1 — Create & Join vs Method 2 — Send & Receive QR)
    if (showConnectionSheet) {
        ModalBottomSheet(
            onDismissRequest = { showConnectionSheet = false },
            sheetState = sheetState,
            containerColor = MaterialTheme.colorScheme.surface
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp, vertical = 16.dp)
            ) {
                Text(
                    text = stringResource(id = R.string.choose_connection_method),
                    style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(modifier = Modifier.height(16.dp))

                ConnectionOptionItem(
                    icon = Icons.Default.NearMe,
                    title = stringResource(id = R.string.method1_title),
                    desc = stringResource(id = R.string.method1_desc),
                    tag = "choose_create_join_connection",
                    onClick = {
                        showConnectionSheet = false
                        onStartCreateConnection()
                    }
                )

                Spacer(modifier = Modifier.height(12.dp))

                ConnectionOptionItem(
                    icon = Icons.Default.QrCode,
                    title = stringResource(id = R.string.method2_title),
                    desc = stringResource(id = R.string.method2_desc),
                    tag = "choose_qr_connection",
                    onClick = {
                        showConnectionSheet = false
                        onStartQrConnection()
                    }
                )

                Spacer(modifier = Modifier.height(12.dp))

                ConnectionOptionItem(
                    icon = Icons.Default.Group,
                    title = stringResource(id = R.string.group_sharing),
                    desc = stringResource(id = R.string.group_sharing_desc),
                    tag = "choose_group_sharing",
                    onClick = {
                        showConnectionSheet = false
                        onStartGroupShare()
                    }
                )

                Spacer(modifier = Modifier.height(32.dp))
            }
        }
    }
}

/**
 * Thumbnail Grid Card for Photos
 */
@Composable
fun PhotoMediaCard(
    file: SharedFile,
    isSelected: Boolean,
    onToggle: () -> Unit,
    onRemove: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(1f)
            .clip(RoundedCornerShape(12.dp))
            .clickable { onToggle() }
            .testTag("photo_item_${file.id}"),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isSelected) SyedBlue.copy(alpha = 0.15f) else MaterialTheme.colorScheme.surfaceVariant
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = if (isSelected) 3.dp else 1.dp)
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            AsyncImage(
                model = file.uriString,
                contentDescription = file.name,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )

            // Top-right selection / deselect cross
            if (isSelected) {
                Box(
                    modifier = Modifier
                        .padding(6.dp)
                        .size(24.dp)
                        .clip(CircleShape)
                        .background(SyedBlue)
                        .clickable { onRemove() }
                        .align(Alignment.TopEnd),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Check,
                        contentDescription = "Selected",
                        tint = Color.White,
                        modifier = Modifier.size(15.dp)
                    )
                }
            }

            // Bottom name/size overlay
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color.Black.copy(alpha = 0.55f))
                    .padding(horizontal = 4.dp, vertical = 2.dp)
                    .align(Alignment.BottomCenter)
            ) {
                Text(
                    text = formatFileSize(file.size),
                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                    color = Color.White,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

/**
 * List Item Card with Thumbnail & Quick Deselect/X option
 */
@Composable
fun MediaListItemCard(
    file: SharedFile,
    isSelected: Boolean,
    onToggle: () -> Unit,
    onRemove: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .clickable { onToggle() }
            .testTag("file_item_${file.id}"),
        colors = CardDefaults.cardColors(
            containerColor = if (isSelected) SyedBlue.copy(alpha = 0.08f) else MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(46.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(
                        if (file.category == FileCategory.VIDEOS) SyedCyan.copy(alpha = 0.15f)
                        else if (file.isApk) SyedTeal.copy(alpha = 0.15f)
                        else SyedBlue.copy(alpha = 0.12f)
                    ),
                contentAlignment = Alignment.Center
            ) {
                if (file.category == FileCategory.PHOTOS || file.category == FileCategory.VIDEOS) {
                    AsyncImage(
                        model = file.uriString,
                        contentDescription = file.name,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                } else {
                    Icon(
                        imageVector = if (file.isApk) Icons.Default.InsertDriveFile
                        else if (file.category == FileCategory.VIDEOS) Icons.Default.Videocam
                        else Icons.Default.InsertDriveFile,
                        contentDescription = null,
                        tint = if (file.isApk) SyedTeal else SyedBlue,
                        modifier = Modifier.size(24.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.width(14.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = file.name,
                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(3.dp))
                Text(
                    text = "${file.category.displayName} • ${formatFileSize(file.size)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Spacer(modifier = Modifier.width(10.dp))

            if (isSelected) {
                // If selected: show X/cross to remove accidentally selected item
                IconButton(
                    onClick = onRemove,
                    modifier = Modifier.size(32.dp).testTag("deselect_${file.id}")
                ) {
                    Box(
                        modifier = Modifier
                            .size(24.dp)
                            .clip(CircleShape)
                            .background(SyedBlue),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Remove selected",
                            tint = Color.White,
                            modifier = Modifier.size(14.dp)
                        )
                    }
                }
            } else {
                // Unselected indicator
                Box(
                    modifier = Modifier
                        .size(24.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.surfaceVariant)
                )
            }
        }
    }
}

/**
 * Backward compatibility alias
 */
@Composable
fun FileSelectionCard(
    file: SharedFile,
    isSelected: Boolean,
    onToggle: () -> Unit
) {
    MediaListItemCard(
        file = file,
        isSelected = isSelected,
        onToggle = onToggle,
        onRemove = onToggle
    )
}

@Composable
private fun ConnectionOptionItem(
    icon: ImageVector,
    title: String,
    desc: String,
    tag: String,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .clickable { onClick() }
            .testTag(tag),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(CircleShape)
                    .background(SyedBlue.copy(alpha = 0.15f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = SyedBlue,
                    modifier = Modifier.size(24.dp)
                )
            }

            Spacer(modifier = Modifier.width(16.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = desc,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}
