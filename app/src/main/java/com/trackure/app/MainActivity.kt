package com.trackure.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontFamily
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
    var currentTab by remember { mutableStateOf(TrackureTab.HOME) }
    var showProfileSheet by remember { mutableStateOf(false) }

    Scaffold(
        containerColor = BackgroundCanvas,
        topBar = {
            if (currentTab == TrackureTab.HOME) {
                TopAppBar(
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = BackgroundCanvas),
                    title = {
                        Column {
                            Text(
                                text = "TRACKURE",
                                fontWeight = FontWeight.ExtraBold,
                                fontSize = 20.sp,
                                letterSpacing = 1.5.sp,
                                color = TextPrimary
                            )
                            Text(
                                text = "One Sure Step Everyday",
                                fontSize = 11.sp,
                                color = SportyBluePrimary,
                                fontFamily = FontFamily.Monospace
                            )
                        }
                    },
                    actions = {
                        // Tombol Profil & Settings di Pojok Kanan Atas Home
                        Box(
                            modifier = Modifier
                                .padding(end = 16.dp)
                                .size(38.dp)
                                .clip(CircleShape)
                                .background(SurfaceCard)
                                .border(2.dp, StravaOrangeHighlight, CircleShape)
                                .clickable { showProfileSheet = true },
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Person,
                                contentDescription = "Profile & Settings",
                                tint = TextPrimary,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                )
            }
        },
        bottomBar = {
            TrackureBottomBar(
                selectedTab = currentTab,
                onTabSelected = { currentTab = it }
            )
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(16.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "Halaman Aktif: ${currentTab.label}\n(Siap diisi komponen ${currentTab.name})",
                color = SportyGreenPrimary,
                fontFamily = FontFamily.Monospace
            )
        }

        // Modal Profil & Settings (Hanya Profil, Login/Logout, dan Switch Account)
        if (showProfileSheet) {
            ModalBottomSheet(
                onDismissRequest = { showProfileSheet = false },
                containerColor = SurfaceCard
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 24.dp, vertical = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        text = "Profil & Akun",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = TextPrimary
                    )
                    Text(
                        text = "muhibmdn • One Sure Step Everyday",
                        fontSize = 13.sp,
                        color = SportyBluePrimary,
                        fontFamily = FontFamily.Monospace
                    )
                    HorizontalDivider(color = SurfaceBorder, modifier = Modifier.padding(vertical = 4.dp))

                    // Tombol Switch Account
                    OutlinedButton(
                        onClick = { /* Trigger Google Credential Manager Switch Account */ },
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = SportyBluePrimary)
                    ) {
                        Icon(Icons.Default.SwitchAccount, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text("Ganti Akun (Switch Account)")
                    }

                    // Tombol Login / Logout
                    Button(
                        onClick = { showProfileSheet = false },
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(containerColor = StravaOrangeHighlight)
                    ) {
                        Icon(Icons.AutoMirrored.Filled.Logout, contentDescription = null, tint = Color.White)
                        Spacer(Modifier.width(8.dp))
                        Text("Logout / Keluar", color = Color.White, fontWeight = FontWeight.Bold)
                    }
                    Spacer(Modifier.height(16.dp))
                }
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
                        indicatorColor = SurfaceBorder
                    )
                )
            }
        }
    }
}