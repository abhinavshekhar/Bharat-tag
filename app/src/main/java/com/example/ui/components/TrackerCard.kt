package com.example.ui.components

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CellTower
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.Tag
import androidx.compose.material3.Badge
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.model.BleDeviceItem
import com.example.ui.theme.CyanAccent
import com.example.ui.theme.CyanAccentDark
import com.example.ui.theme.RingOrange
import com.example.ui.theme.StopRed
import com.example.ui.theme.SuccessGreen

@Composable
fun TrackerCard(
    isConnected: Boolean,
    isCharacteristicReady: Boolean,
    device: BleDeviceItem?,
    lastCommandSent: String?,
    modifier: Modifier = Modifier
) {
    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = if (isConnected) 1.15f else 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(1200, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulseScale"
    )

    Card(
        modifier = modifier
            .fillMaxWidth()
            .testTag("tracker_hero_card"),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 3.dp)
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Visual Tag Emblem with Animated Pulse
            Box(
                modifier = Modifier
                    .size(110.dp)
                    .padding(8.dp),
                contentAlignment = Alignment.Center
            ) {
                if (isConnected) {
                    Box(
                        modifier = Modifier
                            .size(96.dp)
                            .scale(pulseScale)
                            .clip(CircleShape)
                            .background(
                                Brush.radialGradient(
                                    listOf(
                                        SuccessGreen.copy(alpha = 0.25f),
                                        Color.Transparent
                                    )
                                )
                            )
                    )
                }

                // Center circular tag badge
                Box(
                    modifier = Modifier
                        .size(80.dp)
                        .clip(CircleShape)
                        .background(
                            Brush.linearGradient(
                                if (isConnected) {
                                    listOf(CyanAccent, CyanAccentDark)
                                } else {
                                    listOf(Color(0xFF64748B), Color(0xFF334155))
                                }
                            )
                        )
                        .border(
                            width = 3.dp,
                            color = if (isConnected) SuccessGreen else Color.LightGray.copy(alpha = 0.5f),
                            shape = CircleShape
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = if (isConnected) Icons.Default.NotificationsActive else Icons.Default.Tag,
                        contentDescription = "Tag Icon",
                        tint = Color.White,
                        modifier = Modifier.size(38.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Device Name
            Text(
                text = device?.displayName ?: "BharatTag",
                style = MaterialTheme.typography.headlineSmall.copy(
                    fontWeight = FontWeight.Bold,
                    fontSize = 22.sp
                ),
                color = MaterialTheme.colorScheme.onSurface
            )

            Text(
                text = "Arduino UNO R4 BLE Item Finder",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(modifier = Modifier.height(14.dp))

            // Connection Status Pill
            Surface(
                shape = RoundedCornerShape(20.dp),
                color = if (isConnected && isCharacteristicReady) {
                    SuccessGreen.copy(alpha = 0.15f)
                } else {
                    StopRed.copy(alpha = 0.12f)
                },
                modifier = Modifier.testTag("connection_status_label")
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(10.dp)
                            .clip(CircleShape)
                            .background(
                                if (isConnected && isCharacteristicReady) SuccessGreen else StopRed
                            )
                    )
                    Text(
                        text = if (isConnected && isCharacteristicReady) {
                            "CONNECTED & READY"
                        } else if (isConnected) {
                            "DISCOVERING GATT…"
                        } else {
                            "NOT CONNECTED"
                        },
                        style = MaterialTheme.typography.labelMedium.copy(
                            fontWeight = FontWeight.Bold,
                            fontSize = 12.sp,
                            letterSpacing = 1.sp
                        ),
                        color = if (isConnected && isCharacteristicReady) SuccessGreen else StopRed
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Details info row (MAC Address & RSSI if connected)
            if (device != null) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                        .padding(horizontal = 14.dp, vertical = 10.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = "MAC ADDRESS",
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        )
                        Text(
                            text = device.address,
                            style = MaterialTheme.typography.bodyMedium.copy(
                                fontFamily = FontFamily.Monospace,
                                fontWeight = FontWeight.Medium
                            ),
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }

                    if (device.rssi != 0) {
                        Column(horizontalAlignment = Alignment.End) {
                            Text(
                                text = "SIGNAL",
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontWeight = FontWeight.SemiBold,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            )
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.CellTower,
                                    contentDescription = null,
                                    tint = CyanAccent,
                                    modifier = Modifier.size(16.dp)
                                )
                                Text(
                                    text = "${device.rssi} dBm",
                                    style = MaterialTheme.typography.bodyMedium.copy(
                                        fontWeight = FontWeight.Bold
                                    ),
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
