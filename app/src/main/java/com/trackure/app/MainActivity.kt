package com.trackure.app

import android.os.Bundle
import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import com.trackure.app.data.repository.PersistentTrackureRepository
import com.trackure.app.core.network.TrackureSyncWorker
import kotlinx.coroutines.launch
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import com.trackure.app.presentation.profile.ProfileSheet
import com.trackure.app.presentation.profile.EditProfileDialog
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.trackure.app.ui.theme.*

enum class TrackureTab(val label: String, val icon: ImageVector, val isCenterAdd: Boolean = false) {
    HOME("Home", Icons.Default.Home),
    TRACKER("Tracker", Icons.Default.CheckCircle),
    ADD("Add", Icons.Default.Add, isCenterAdd = true),
    HISTORY("History", Icons.Default.BarChart),
    NOTE("Note", Icons.Default.EditNote)
}

class MainActivity : ComponentActivity() {
    companion object { const val EXTRA_OPEN_TAB = "EXTRA_OPEN_TAB" }
    private var requestedTab by mutableStateOf<String?>(null)

    override fun onNewIntent(intent: android.content.Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        requestedTab = intent.getStringExtra(EXTRA_OPEN_TAB)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        requestedTab = intent.getStringExtra(EXTRA_OPEN_TAB)
        enableEdgeToEdge()
        setContent {
            TrackureTheme {
                val repository = remember { PersistentTrackureRepository.getInstance(applicationContext) }
                val active by repository.isAuthenticatedOrLocalActive.collectAsStateWithLifecycle()
                if (active) TrackureMainAppShell(requestedTab) {
                    requestedTab = null
                    intent.removeExtra(EXTRA_OPEN_TAB)
                }
                else com.trackure.app.presentation.auth.AuthScreen(repository)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TrackureMainAppShell(requestedTab: String? = null, onTabOpened: () -> Unit = {}) {
    val activityContext = androidx.compose.ui.platform.LocalContext.current
    val context = activityContext.applicationContext
    val repository = remember(context) { PersistentTrackureRepository.getInstance(context) }
    val notificationPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) com.trackure.app.core.util.enqueueAlarmRecovery(context)
    }
    LaunchedEffect(Unit) {
        com.trackure.app.core.util.enqueueAlarmRecovery(context)
        TrackureSyncWorker.enqueueOneTimeSync(context)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }
    val profile by repository.userProfile.collectAsStateWithLifecycle()
    val syncStatus by repository.syncStatus.collectAsStateWithLifecycle()
    var syncing by remember { mutableStateOf(false) }
    var currentTab by androidx.compose.runtime.saveable.rememberSaveable { mutableStateOf(TrackureTab.HOME) }
    LaunchedEffect(requestedTab) {
        if (requestedTab == "TRACKER") {
            currentTab = TrackureTab.TRACKER
            onTabOpened()
        }
    }
    val viewModel: com.trackure.app.presentation.TrackureViewModel = androidx.lifecycle.viewmodel.compose.viewModel(
        key = profile.id,
        factory = androidx.lifecycle.viewmodel.viewModelFactory {
            initializer {
                com.trackure.app.presentation.TrackureViewModel(
                    repository,
                    java.time.LocalDate.now(),
                )
            }
        }
    )
    val savingProfile by viewModel.home.isSaving.collectAsStateWithLifecycle()
    var showProfileSheet by rememberSaveable { mutableStateOf(false) }
    var showEditProfile by rememberSaveable { mutableStateOf(false) }
    var accountBusy by remember { mutableStateOf(false) }
    val accountScope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }
    LaunchedEffect(viewModel) {
        viewModel.home.events.collect { snackbar.showSnackbar(it) }
    }
    val screenState = androidx.compose.runtime.saveable.rememberSaveableStateHolder()
    Scaffold(
        containerColor = BackgroundCanvas,
        topBar = {
            TopAppBar(
                title = { Text("TRACKURE", fontWeight = FontWeight.ExtraBold, fontSize = 20.sp, color = TextPrimary) },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = BackgroundCanvas),
                actions = {
                    IconButton(
                        onClick = { showProfileSheet = true },
                        modifier = Modifier.padding(end = 8.dp).semantics { contentDescription = "Open Profile & Settings" },
                    ) {
                        Box(Modifier.size(36.dp).clip(CircleShape).background(SurfaceCard).border(1.dp, SurfaceBorder, CircleShape), contentAlignment = Alignment.Center) {
                            Text(profile.name.split(" ").filter { it.isNotBlank() }.take(2).joinToString("") { it.take(1).uppercase() },
                                color = TextPrimary, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                        }
                    }
                },
            )
        },
        bottomBar = { TrackureBottomBar(currentTab) { currentTab = it } },
    ) { padding ->
        Box(Modifier.fillMaxSize()) {
            screenState.SaveableStateProvider(profile.id + currentTab.name) {
                when (currentTab) {
                    TrackureTab.HOME -> com.trackure.app.presentation.home.HomeRoute(viewModel, { showProfileSheet = true }, Modifier.padding(padding))
                    TrackureTab.TRACKER -> com.trackure.app.presentation.tracker.TrackerRoute(viewModel, Modifier.padding(padding))
                    TrackureTab.ADD -> com.trackure.app.presentation.add.AddActivityScreen(viewModel, Modifier.padding(padding))
                    TrackureTab.HISTORY -> com.trackure.app.presentation.history.HistoryRoute(viewModel, Modifier.padding(padding))
                    TrackureTab.NOTE -> com.trackure.app.presentation.notes.NoteScreen(viewModel, Modifier.padding(padding))
                }
            }
            SnackbarHost(snackbar, Modifier.align(Alignment.TopCenter).padding(padding).padding(12.dp)) { data ->
                Snackbar(data, containerColor = SurfaceCard, contentColor = TextPrimary)
            }
        }
    }
    if (accountBusy) LinearProgressIndicator(Modifier.fillMaxWidth())
    if (showProfileSheet && !accountBusy) {
        ModalBottomSheet(
            onDismissRequest = { showProfileSheet = false },
            containerColor = SurfaceCard,
            modifier = Modifier.border(1.dp, SurfaceBorder, RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp)),
        ) {
            ProfileSheet(profile,
                syncStatus = syncStatus,
                syncing = syncing,
                onSyncNow = {
                    syncing = true
                    accountScope.launch {
                        try {
                            val success = repository.syncNow()
                            com.trackure.app.core.util.enqueueAlarmRecovery(context)
                            snackbar.showSnackbar(if (success) "Sync complete" else "Sync unavailable. Your changes are saved locally.")
                        } finally { syncing = false }
                    }
                },
                onEdit = { showProfileSheet = false; showEditProfile = true },
                onSwitchAccount = { accountScope.launch { repository.logout() } },
                onLogin = { accountScope.launch { repository.logout() } },
                onLogout = {
                    showProfileSheet = false
                    accountBusy = true
                    accountScope.launch {
                        try {
                            repository.logout()
                            Toast.makeText(context, "Logged out.", Toast.LENGTH_SHORT).show()
                        } catch (e: kotlinx.coroutines.CancellationException) { throw e
                        } catch (_: Exception) { snackbar.showSnackbar("Sign-out cleanup could not finish. Please try again.")
                        } finally { accountBusy = false }
                    }
                },
            )
        }
    }
    if (showEditProfile) {
        EditProfileDialog(profile, savingProfile, { showEditProfile = false }) { name, link, motto ->
            viewModel.home.updateProfile(name, link, motto) { showEditProfile = false }
        }
    }

}
@Composable
fun TrackureBottomBar(
    selectedTab: TrackureTab,
    onTabSelected: (TrackureTab) -> Unit
) {
    NavigationBar(
        containerColor = SurfaceCard,
        tonalElevation = 8.dp,
        modifier = Modifier.border(0.5.dp, SurfaceBorder, RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp))
    ) {
        TrackureTab.entries.forEach { tab ->
            val isSelected = selectedTab == tab
            if (tab.isCenterAdd) {
                NavigationBarItem(
                    selected = isSelected,
                    onClick = { onTabSelected(tab) },
                    icon = {
                        Box(
                            modifier = Modifier
                                .size(48.dp)
                                .clip(CircleShape)
                                .background(StravaOrangeHighlight),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = tab.icon,
                                contentDescription = "Add Activity",
                                tint = Color.White,
                                modifier = Modifier.size(28.dp)
                            )
                        }
                    },
                    label = { Text("Add", color = StravaOrangeHighlight, fontWeight = FontWeight.Bold) },
                    colors = NavigationBarItemDefaults.colors(indicatorColor = Color.Transparent)
                )
            } else {
                NavigationBarItem(
                    selected = isSelected,
                    onClick = { onTabSelected(tab) },
                    icon = {
                        Icon(
                            imageVector = tab.icon,
                            contentDescription = tab.label,
                            tint = if (isSelected) SportyGreenPrimary else TextSecondary
                        )
                    },
                    label = {
                        Text(
                            text = tab.label,
                            color = if (isSelected) SportyGreenPrimary else TextSecondary,
                            fontSize = 11.sp
                        )
                    },
                    colors = NavigationBarItemDefaults.colors(
                        indicatorColor = Color.Transparent
                    )
                )
            }
        }
    }
}
