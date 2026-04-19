package com.htn.fishcare.notification.ui

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.htn.fishcare.R
import com.htn.fishcare.notification.data.NotificationRepository
import com.htn.fishcare.notification.model.AppNotification
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

data class NotificationUiState(
    val notifications: List<AppNotification> = emptyList(),
    val unreadCount: Int = 0,
    val isLoading: Boolean = true
)

@HiltViewModel
class NotificationViewModel @Inject constructor(
    application: Application,
    private val repo: NotificationRepository
) : AndroidViewModel(application) {

    private val CHANNEL_ID = "fishcare_alerts"

    private val _uiState = MutableStateFlow(NotificationUiState())
    val uiState: StateFlow<NotificationUiState> = _uiState.asStateFlow()

    // Tracks IDs already shown as Android notifications (avoids re-firing on re-collect)
    private val shownIds = mutableSetOf<String>()

    init {
        createNotificationChannel()
        observeNotifications()
    }

    private fun observeNotifications() {
        viewModelScope.launch {
            repo.observeNotifications().collect { list ->
                val unread = list.count { !it.read }
                _uiState.value = NotificationUiState(
                    notifications = list,
                    unreadCount   = unread,
                    isLoading     = false
                )
                // Hiện local Android notification cho các entry mới chưa đọc
                list.filter { !it.read && it.id !in shownIds }.forEach { notif ->
                    shownIds.add(notif.id)
                    showAndroidNotification(notif)
                }
            }
        }
    }

    fun markAsRead(id: String) {
        viewModelScope.launch { runCatching { repo.markAsRead(id) } }
    }

    fun markAllAsRead() {
        val ids = _uiState.value.notifications.filter { !it.read }.map { it.id }
        if (ids.isEmpty()) return
        viewModelScope.launch { runCatching { repo.markAllAsRead(ids) } }
    }

    // ── Android local notification ─────────────────────────────────────────
    private fun showAndroidNotification(notif: AppNotification) {
        val ctx = getApplication<Application>()
        val manager = NotificationManagerCompat.from(ctx)

        // Kiểm tra quyền (Android 13+)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (!manager.areNotificationsEnabled()) return
        }

        val androidNotif = NotificationCompat.Builder(ctx, CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle(notif.title)
            .setContentText(notif.message)
            .setStyle(NotificationCompat.BigTextStyle().bigText(notif.message))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .build()

        try {
            manager.notify(notif.id.hashCode(), androidNotif)
        } catch (e: SecurityException) {
            // Quyền POST_NOTIFICATIONS chưa được cấp - bỏ qua
        }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Cảnh báo hồ cá",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Thông báo về nhiệt độ, chất lượng nước và lịch cho ăn"
            }
            val mgr = getApplication<Application>()
                .getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            mgr.createNotificationChannel(channel)
        }
    }
}
