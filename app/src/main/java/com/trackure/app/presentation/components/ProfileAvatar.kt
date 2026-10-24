package com.trackure.app.presentation.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.SubcomposeAsyncImage
import com.trackure.app.domain.model.UserProfile
import com.trackure.app.ui.theme.SurfaceCard
import com.trackure.app.ui.theme.TextPrimary

@Composable
fun ProfileAvatar(profile: UserProfile, size: Dp = 36.dp) {
    val initials = profile.name.trim().split(Regex("\\s+")).filter { it.isNotBlank() }
        .take(2).joinToString("") { it.take(1).uppercase() }.ifBlank { "T" }
    val fallback: @Composable () -> Unit = {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text(initials, color = TextPrimary, fontWeight = FontWeight.Bold,
                fontSize = (size.value / 3).sp)
        }
    }
    key(profile.id, profile.avatarUrl) {
        SubcomposeAsyncImage(model = profile.avatarUrl?.takeIf { it.isNotBlank() },
            contentDescription = null, contentScale = ContentScale.Crop,
            modifier = Modifier.size(size).clip(CircleShape).background(SurfaceCard),
            loading = { fallback() }, error = { fallback() })
    }
}
