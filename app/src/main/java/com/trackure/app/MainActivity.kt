package com.trackure.app

import android.os.Bundle
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
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            TrackureTheme {
                TrackureMainAppShell()
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TrackureMainAppShell() {
    var currentTab by androidx.compose.runtime.saveable.rememberSaveable { mutableStateOf(TrackureTab.HOME) }
    val viewModel: com.trackure.app.presentation.TrackureViewModel = androidx.lifecycle.viewmodel.compose.viewModel(
        factory = androidx.lifecycle.viewmodel.viewModelFactory {
            initializer {
                com.trackure.app.presentation.TrackureViewModel(
                    com.trackure.app.data.repository.InMemoryTrackureRepository.shared,
                    com.trackure.app.data.repository.InMemoryTrackureRepository.demoDate,
                    com.trackure.app.core.util.DeferredLockscreenAlarmScheduler,
                )
            }
        }
    )
    val profile by viewModel.userProfile.collectAsStateWithLifecycle()
    val savingProfile by viewModel.home.isSaving.collectAsStateWithLifecycle()
    var showProfileSheet by rememberSaveable { mutableStateOf(false) }
    var showEditProfile by rememberSaveable { mutableStateOf(false) }
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
            screenState.SaveableStateProvider(currentTab.name) {
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
    if (showProfileSheet) {
        ModalBottomSheet(
            onDismissRequest = { showProfileSheet = false },
            containerColor = SurfaceCard,
            modifier = Modifier.border(1.dp, SurfaceBorder, RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp)),
        ) {
            ProfileSheet(profile,
                onEdit = { showProfileSheet = false; showEditProfile = true },
                onSwitchAccount = { showProfileSheet = false; viewModel.home.switchAccount() },
                onLogout = { showProfileSheet = false; viewModel.home.logout() },
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
