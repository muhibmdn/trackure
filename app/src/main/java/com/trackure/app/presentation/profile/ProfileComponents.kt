package com.trackure.app.presentation.profile

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

@Composable
internal fun ProfileSheet(profile: UserProfile, onEdit: () -> Unit, onSwitchAccount: () -> Unit, onLogout: () -> Unit, onLogin: () -> Unit = onSwitchAccount, syncStatus: String = "Local Mode", syncing: Boolean = false, onSyncNow: () -> Unit = {}) {
    val uriHandler = LocalUriHandler.current
    var linkError by remember { mutableStateOf(false) }
    Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("Profile & Settings", color = TextPrimary, fontWeight = FontWeight.Bold, fontSize = 18.sp)
        Surface(color = BackgroundCanvas, shape = RoundedCornerShape(12.dp), border = BorderStroke(1.dp, SurfaceBorder)) {
            Row(Modifier.fillMaxWidth().padding(16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                com.trackure.app.presentation.components.ProfileAvatar(profile, 56.dp)
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
        Text(syncStatus, color = if (syncStatus == "Cloud Synced (Supabase Active)") Color(0xFF00E676) else Color(0xFF94A3B8), fontSize = 12.sp)
        if (!profile.id.startsWith("local_")) {
            OutlinedButton(onSyncNow, Modifier.fillMaxWidth(), enabled = !syncing) { Text(if (syncing) "Syncing..." else "Sync Now") }
        }
        if (profile.id.startsWith("local_")) {
            Text("Local profile · available offline", color = TextSecondary, fontSize = 12.sp)
            OutlinedButton(onLogin, Modifier.fillMaxWidth()) { Text("Sign In / Register") }
        }
        listOf(Triple("Edit Profile", TextPrimary, onEdit), Triple("Switch Account", SportyBluePrimary, onSwitchAccount), Triple("Log Out", StravaOrangeHighlight, onLogout)).forEach { (label, color, action) ->
            OutlinedButton(action, Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp), border = BorderStroke(1.dp, SurfaceBorder), colors = ButtonDefaults.outlinedButtonColors(containerColor = BackgroundCanvas, contentColor = color)) { Text(label, Modifier.fillMaxWidth().padding(vertical = 6.dp)) }
        }
    }
}

@Composable
internal fun EditProfileDialog(profile: UserProfile, saving: Boolean, onDismiss: () -> Unit, title: String = "Edit Profile", requireLink: Boolean = false, onSave: (String, String, String) -> Unit) {
    var name by rememberSaveable(profile.id) { mutableStateOf(profile.name) }
    var link by rememberSaveable(profile.id) { mutableStateOf(profile.profileUrlOrEmail) }
    var motto by rememberSaveable(profile.id) { mutableStateOf(profile.motto) }
    Dialog(onDismissRequest = { if (!saving) onDismiss() }) {
        Surface(color = SurfaceCard, shape = RoundedCornerShape(16.dp), border = BorderStroke(1.dp, SurfaceBorder)) {
            Column(Modifier.fillMaxWidth().imePadding().verticalScroll(rememberScrollState()).padding(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Text(title, color = TextPrimary, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                if (requireLink) Text("Use a local profile now. Login with Google later to connect to cloud sync.", color = TextSecondary)
                OutlinedTextField(name, { name = it }, label = { Text("NAME") }, singleLine = true, enabled = !saving, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(link, { link = it }, label = { Text("PROFILE LINK / EMAIL / SOCIAL URL", fontSize = 10.sp) }, singleLine = true, enabled = !saving, textStyle = TextStyle(color = SportyBluePrimary, fontFamily = FontFamily.Monospace), modifier = Modifier.fillMaxWidth())
                OutlinedTextField(motto, { motto = it }, label = { Text("MOTTO") }, enabled = !saving, modifier = Modifier.fillMaxWidth())
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    TextButton(onDismiss, enabled = !saving) { Text("Cancel", color = TextSecondary) }
                    TextButton({ onSave(name, link, motto) }, enabled = !saving && name.isNotBlank() && (!requireLink || link.isNotBlank()), colors = ButtonDefaults.textButtonColors(contentColor = StravaOrangeHighlight)) { Text(if (saving) "Saving..." else "Save Changes") }
                }
            }
        }
    }
}
