package com.trackure.app.presentation.home

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.automirrored.filled.TrendingUp
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.trackure.app.domain.model.UserProfile
import com.trackure.app.ui.theme.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeRoute(viewModel: HomeViewModel, modifier: Modifier = Modifier) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val saving by viewModel.isSaving.collectAsStateWithLifecycle()
    var showProfileSheet by rememberSaveable { mutableStateOf(false) }
    var showEditProfileDialog by rememberSaveable { mutableStateOf(false) }
    val snackbar = remember { SnackbarHostState() }
    LaunchedEffect(viewModel) { viewModel.events.collect { snackbar.showSnackbar(it) } }
    Box(modifier.fillMaxSize()) {
        HomeScreen(state, { showProfileSheet = true })
        if (showProfileSheet) {
            ModalBottomSheet(
                onDismissRequest = { showProfileSheet = false }, containerColor = SurfaceCard,
                modifier = Modifier.border(1.dp, SurfaceBorder, RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp)),
            ) {
                ProfileSheet(state.profile, onEdit = { showProfileSheet = false; showEditProfileDialog = true },
                    onSwitchAccount = { showProfileSheet = false; viewModel.switchAccount() },
                    onLogout = { showProfileSheet = false; viewModel.logout() })
            }
        }
        if (showEditProfileDialog) {
            EditProfileDialog(state.profile, saving, { showEditProfileDialog = false }) { name, link, motto ->
                viewModel.updateProfile(name, link, motto) { showEditProfileDialog = false }
            }
        }
        SnackbarHost(snackbar, Modifier.align(Alignment.TopCenter).padding(12.dp)) { data ->
            Snackbar(data, containerColor = SurfaceCard, contentColor = TextPrimary)
        }
    }
}

@Composable
fun HomeScreen(state: HomeUiState, onOpenProfileSheet: () -> Unit, modifier: Modifier = Modifier) {
    Column(modifier.fillMaxSize().background(BackgroundCanvas).verticalScroll(rememberScrollState()).padding(horizontal = 20.dp, vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Column(Modifier.weight(1f)) {
                Text(state.dateLabel, fontSize = 12.sp, color = TextSecondary)
                Text("Good Morning, ${state.profile.name.substringBefore(" ")}", fontSize = 24.sp, fontWeight = FontWeight.ExtraBold, color = TextPrimary)
            }
            OutlinedButton(onOpenProfileSheet, shape = RoundedCornerShape(8.dp), border = BorderStroke(1.dp, SurfaceBorder), colors = ButtonDefaults.outlinedButtonColors(containerColor = SurfaceBorder, contentColor = TextPrimary), contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp)) {
                Icon(Icons.Default.Settings, null, Modifier.size(14.dp))
                Spacer(Modifier.width(4.dp))
                Text("Profile", fontSize = 11.sp)
            }
        }
        HomeCard {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text("Sure-Step Matrix", color = TextPrimary, fontSize = 16.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                MonoText("${state.streakCount} Day Streak", StravaOrangeHighlight)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    listOf("M", "T", "W", "T", "F", "S", "S").forEach {
                        Box(Modifier.size(14.dp), contentAlignment = Alignment.Center) { Text(it, fontSize = 10.sp, lineHeight = 12.sp, color = TextSecondary) }
                    }
                }
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                    state.matrix.chunked(7).forEach { week ->
                        Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                            week.forEach { day ->
                                Box(Modifier.size(14.dp).clip(RoundedCornerShape(2.dp)).background(matrixColor(day.level)).semantics { contentDescription = "${day.dateIso}: activity level ${day.level}" })
                            }
                        }
                    }
                }
            }
            HorizontalDivider(color = SurfaceBorder, thickness = 1.dp)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text("Activity Level", fontSize = 10.sp, color = TextSecondary)
                Row(horizontalArrangement = Arrangement.spacedBy(5.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text("Inactive", fontSize = 10.sp, color = TextSecondary)
                    (0..3).forEach { Box(Modifier.size(10.dp).background(matrixColor(it), RoundedCornerShape(2.dp))) }
                    Text("Peak", fontSize = 10.sp, color = TextSecondary)
                }
            }
        }
        HomeCard {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Icon(Icons.AutoMirrored.Filled.TrendingUp, null, Modifier.size(18.dp), tint = SportyGreenPrimary)
                Text("Today's Momentum", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = TextPrimary, modifier = Modifier.weight(1f))
                MonoText("${state.completionPercent}% Done", SportyGreenPrimary)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("${state.completedCount} of ${state.totalCount} Activities Completed", fontSize = 12.sp, color = TextSecondary, modifier = Modifier.weight(1f))
                MonoText("${state.totalCount - state.completedCount} Pending", TextSecondary)
            }
            Box(Modifier.fillMaxWidth().height(8.dp).clip(CircleShape).background(BackgroundCanvas).border(1.dp, SurfaceBorder, CircleShape).semantics { progressBarRangeInfo = ProgressBarRangeInfo(state.completionPercent / 100f, 0f..1f) }) {
                Box(Modifier.fillMaxWidth(state.completionPercent / 100f).fillMaxHeight().background(Brush.horizontalGradient(listOf(SportyBluePrimary, SportyGreenPrimary)), CircleShape))
            }
        }
    }
}

private fun matrixColor(level: Int) = when (level) { 1 -> MatrixBlueCell; 2 -> SportyGreenPrimary; 3 -> StravaOrangeHighlight; else -> MatrixEmptyCell }

@Composable
private fun MonoText(text: String, color: Color) = Text(text, fontSize = 12.sp, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold, color = color)

@Composable
private fun HomeCard(content: @Composable ColumnScope.() -> Unit) {
    Surface(color = SurfaceCard, shape = RoundedCornerShape(12.dp), border = BorderStroke(1.dp, SurfaceBorder)) {
        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp), content = content)
    }
}

@Composable
private fun ProfileSheet(profile: UserProfile, onEdit: () -> Unit, onSwitchAccount: () -> Unit, onLogout: () -> Unit) {
    val uriHandler = LocalUriHandler.current
    var linkError by remember { mutableStateOf(false) }
    Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("Profile & Settings", color = TextPrimary, fontWeight = FontWeight.Bold, fontSize = 18.sp)
        Surface(color = BackgroundCanvas, shape = RoundedCornerShape(12.dp), border = BorderStroke(1.dp, SurfaceBorder)) {
            Row(Modifier.fillMaxWidth().padding(16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                // Initials are the offline avatar for the seed profile (avatarUrl = null).
                Box(Modifier.size(56.dp).background(SurfaceBorder, CircleShape), contentAlignment = Alignment.Center) {
                    Text(profile.name.split(" ").filter { it.isNotEmpty() }.take(2).joinToString("") { it.take(1) }, color = TextPrimary, fontWeight = FontWeight.Bold)
                }
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(profile.name, fontSize = 16.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
                    Text(profile.profileUrlOrEmail.removePrefix("https://"), fontFamily = FontFamily.Monospace, fontSize = 12.sp, color = SportyBluePrimary,
                        modifier = Modifier.clickable {
                            val link = profile.profileUrlOrEmail.trim()
                            val uri = when { link.startsWith("https://") || link.startsWith("http://") || link.startsWith("mailto:") -> link; "@" in link && "/" !in link -> "mailto:$link"; else -> "https://$link" }
                            try { uriHandler.openUri(uri) } catch (_: IllegalArgumentException) { linkError = true }
                        })
                    Text(profile.motto, color = TextSecondary, fontSize = 12.sp)
                    if (linkError) Text("Unable to open this link", color = StravaOrangeHighlight, fontSize = 12.sp)
                }
            }
        }
        listOf(Triple("Edit Profile", TextPrimary, onEdit), Triple("Switch Account", SportyBluePrimary, onSwitchAccount), Triple("Log Out", StravaOrangeHighlight, onLogout)).forEach { (label, color, action) ->
            OutlinedButton(action, Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp), border = BorderStroke(1.dp, SurfaceBorder), colors = ButtonDefaults.outlinedButtonColors(containerColor = BackgroundCanvas, contentColor = color)) { Text(label, Modifier.fillMaxWidth().padding(vertical = 6.dp)) }
        }
    }
}

@Composable
private fun EditProfileDialog(profile: UserProfile, saving: Boolean, onDismiss: () -> Unit, onSave: (String, String, String) -> Unit) {
    var name by rememberSaveable(profile.id) { mutableStateOf(profile.name) }
    var link by rememberSaveable(profile.id) { mutableStateOf(profile.profileUrlOrEmail) }
    var motto by rememberSaveable(profile.id) { mutableStateOf(profile.motto) }
    Dialog(onDismissRequest = { if (!saving) onDismiss() }) {
        Surface(color = SurfaceCard, shape = RoundedCornerShape(16.dp), border = BorderStroke(1.dp, SurfaceBorder)) {
            Column(Modifier.fillMaxWidth().imePadding().verticalScroll(rememberScrollState()).padding(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Text("Edit Profile", color = TextPrimary, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                OutlinedTextField(name, { name = it }, label = { Text("NAME") }, singleLine = true, enabled = !saving, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(link, { link = it }, label = { Text("PROFILE LINK / EMAIL / SOCIAL URL", fontSize = 10.sp) }, singleLine = true, enabled = !saving, textStyle = TextStyle(color = SportyBluePrimary, fontFamily = FontFamily.Monospace), modifier = Modifier.fillMaxWidth())
                OutlinedTextField(motto, { motto = it }, label = { Text("MOTTO") }, enabled = !saving, modifier = Modifier.fillMaxWidth())
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    TextButton(onDismiss, enabled = !saving) { Text("Cancel", color = TextSecondary) }
                    TextButton({ onSave(name, link, motto) }, enabled = !saving && name.isNotBlank(), colors = ButtonDefaults.textButtonColors(contentColor = StravaOrangeHighlight)) { Text(if (saving) "Saving..." else "Save Changes") }
                }
            }
        }
    }
}
