package com.trackure.app

import android.os.Bundle
import androidx.lifecycle.viewmodel.initializer
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
    val screenState = androidx.compose.runtime.saveable.rememberSaveableStateHolder()
    Scaffold(
        containerColor = BackgroundCanvas,
        topBar = {
            TopAppBar(title = { Text("TRACKURE", fontWeight = FontWeight.ExtraBold, fontSize = 20.sp, color = TextPrimary) }, colors = TopAppBarDefaults.topAppBarColors(containerColor = BackgroundCanvas))
        },
        bottomBar = { TrackureBottomBar(currentTab) { currentTab = it } },
    ) { padding ->
        screenState.SaveableStateProvider(currentTab.name) {
            when (currentTab) {
                TrackureTab.HOME -> com.trackure.app.presentation.home.HomeRoute(viewModel, Modifier.padding(padding))
                TrackureTab.TRACKER -> com.trackure.app.presentation.tracker.TrackerRoute(viewModel, Modifier.padding(padding))
                TrackureTab.ADD -> com.trackure.app.presentation.add.AddActivityScreen(viewModel, Modifier.padding(padding))
                TrackureTab.HISTORY -> com.trackure.app.presentation.history.HistoryRoute(viewModel, Modifier.padding(padding))
                TrackureTab.NOTE -> com.trackure.app.presentation.notes.NoteScreen(viewModel, Modifier.padding(padding))
            }
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
