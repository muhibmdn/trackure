package com.trackure.app.presentation.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.trackure.app.R

/** Shared lockup: the launcher mark and an athletic, forward-leaning wordmark. */
@Composable
fun TrackureBrand(modifier: Modifier = Modifier, prominent: Boolean = false) {
    Row(modifier, verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(if (prominent) 14.dp else 10.dp)) {
        Box(Modifier.size(if (prominent) 52.dp else 40.dp)
            .clip(RoundedCornerShape(if (prominent) 16.dp else 12.dp))
            .background(Color(0xFF161D26)), contentAlignment = Alignment.Center) {
            // Match the adaptive icon's 72-unit mask viewport without duplicating its geometry.
            Image(painterResource(R.drawable.ic_launcher_foreground), contentDescription = null,
                modifier = Modifier.fillMaxSize().graphicsLayer { scaleX = 1.5f; scaleY = 1.5f })
        }
        Text("TRACKURE", color = Color(0xFFF5F7FA),
            fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.Black,
            fontStyle = FontStyle.Italic, fontSize = if (prominent) 30.sp else 22.sp,
            letterSpacing = 1.sp, maxLines = 1)
    }
}
