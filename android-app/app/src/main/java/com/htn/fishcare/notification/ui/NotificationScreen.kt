package com.htn.fishcare.notification.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.DoneAll
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.htn.fishcare.notification.model.AppNotification
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private val GREEN = Color(0xFF0E5A2A)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NotificationRoute(
    onBackClick: () -> Unit,
    viewModel: NotificationViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(listOf(Color(0xFFF4FBF7), Color(0xFFE8F1FF))))
    ) {
        TopAppBar(
            title = {
                Text(
                    "Thông báo",
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
            },
            navigationIcon = {
                IconButton(onClick = onBackClick) {
                    Icon(Icons.Default.ArrowBack, "Quay lại", tint = Color.White)
                }
            },
            actions = {
                if (uiState.unreadCount > 0) {
                    IconButton(onClick = viewModel::markAllAsRead) {
                        Icon(Icons.Default.DoneAll, "Đánh dấu tất cả đã đọc", tint = Color.White)
                    }
                }
            },
            colors = TopAppBarDefaults.topAppBarColors(containerColor = GREEN)
        )

        if (uiState.isLoading) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = GREEN)
            }
            return@Column
        }

        if (uiState.notifications.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("🔔", fontSize = 48.sp)
                    Spacer(Modifier.height(12.dp))
                    Text(
                        "Chưa có thông báo nào",
                        color = Color.Gray,
                        fontWeight = FontWeight.Medium
                    )
                }
            }
            return@Column
        }

        // Unread count banner
        if (uiState.unreadCount > 0) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color(0xFFE8F5E9))
                    .padding(horizontal = 16.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(24.dp)
                        .clip(CircleShape)
                        .background(GREEN),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        "${uiState.unreadCount}",
                        color = Color.White,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
                Spacer(Modifier.width(8.dp))
                Text(
                    "${uiState.unreadCount} thông báo chưa đọc",
                    color = GREEN,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 13.sp
                )
            }
        }

        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(horizontal = 12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            item { Spacer(Modifier.height(8.dp)) }

            items(uiState.notifications, key = { it.id }) { notif ->
                NotificationCard(
                    notif = notif,
                    onClick = { if (!notif.read) viewModel.markAsRead(notif.id) }
                )
            }

            item { Spacer(Modifier.height(24.dp)) }
        }
    }
}

@Composable
private fun NotificationCard(notif: AppNotification, onClick: () -> Unit) {
    val bgColor = if (notif.read) Color.White else Color(0xFFE8F5E9)
    val accentColor = when (notif.type) {
        "water_quality" -> Color(0xFF1565C0)
        "temperature"   -> Color(0xFFE65100)
        "feed"          -> GREEN
        else            -> Color(0xFF6A1B9A)
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() },
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = bgColor),
        elevation = CardDefaults.cardElevation(if (notif.read) 1.dp else 3.dp)
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            verticalAlignment = Alignment.Top,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Icon bubble
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(CircleShape)
                    .background(accentColor.copy(alpha = 0.12f)),
                contentAlignment = Alignment.Center
            ) {
                Text(notif.icon, fontSize = 22.sp)
            }

            Column(modifier = Modifier.weight(1f)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = notif.title,
                        fontWeight = if (notif.read) FontWeight.Normal else FontWeight.Bold,
                        fontSize = 14.sp,
                        color = accentColor,
                        modifier = Modifier.weight(1f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    if (!notif.read) {
                        Box(
                            modifier = Modifier
                                .size(8.dp)
                                .clip(CircleShape)
                                .background(GREEN)
                        )
                    }
                }
                Spacer(Modifier.height(4.dp))
                Text(
                    text = notif.message,
                    fontSize = 13.sp,
                    color = Color(0xFF444444),
                    lineHeight = 19.sp
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    text = formatTimestamp(notif.timestamp),
                    fontSize = 11.sp,
                    color = Color.Gray
                )
            }
        }
    }
}

private fun formatTimestamp(ts: Long): String {
    if (ts == 0L) return ""
    val sdf = SimpleDateFormat("HH:mm – dd/MM/yyyy", Locale("vi", "VN"))
    return sdf.format(Date(ts))
}
