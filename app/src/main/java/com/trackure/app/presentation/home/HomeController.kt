package com.trackure.app.presentation.home

import kotlinx.coroutines.CoroutineScope
import com.trackure.app.domain.model.*
import com.trackure.app.domain.repository.TrackureRepository
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

data class HomeUiState(
    val profile: UserProfile,
    val matrix: List<StreakDay>,
    val completedCount: Int,
    val totalCount: Int,
    val dateLabel: String,
    val streakCount: Int,
) {
    val completionPercent: Int get() = if (totalCount == 0) 0 else completedCount * 100 / totalCount
}

class HomeController(private val repository: TrackureRepository, private val date: LocalDate, private val scope: CoroutineScope) {
    private fun state(profile: UserProfile, activities: List<ActivityItem>, logs: List<ActivityLog>, matrix: List<StreakDay>): HomeUiState {
        val ids = activities.filter { it.syncStatus != SyncStatus.PENDING_DELETE }.map { it.id }.toSet()
        val completed = logs.filter { it.dateIso == date.toString() && it.isCompleted && it.activityId in ids && it.syncStatus != SyncStatus.PENDING_DELETE }.map { it.activityId }.distinct().size
        val levels = matrix.associate { it.dateIso to it.level }
        var day = if ((levels[date.toString()] ?: 0) > 0) date else date.minusDays(1)
        var streak = 0
        while ((levels[day.toString()] ?: 0) > 0) { streak++; day = day.minusDays(1) }
        return HomeUiState(profile, matrix, completed, ids.size, date.format(DateTimeFormatter.ofPattern("EEEE, MMM d", Locale.ENGLISH)), streak)
    }
    val uiState = combine(repository.userProfile, repository.activities, repository.activityLogs, repository.streakMatrix, ::state)
        .stateIn(scope, SharingStarted.WhileSubscribed(5_000), state(repository.userProfile.value, repository.activities.value, repository.activityLogs.value, repository.streakMatrix.value))
    private val messages = Channel<String>(Channel.BUFFERED)
    val events = messages.receiveAsFlow()
    private val saving = MutableStateFlow(false)
    val isSaving = saving.asStateFlow()
    fun updateProfile(name: String, link: String, motto: String, onSaved: () -> Unit) {
        if (saving.value) return
        saving.value = true
        scope.launch {
            try {
                repository.updateProfile(name, link, motto)
                onSaved()
                messages.send("Profile updated successfully")
            } catch (e: CancellationException) { throw e
            } catch (e: Exception) { messages.send(e.message ?: "Unable to update profile")
            } finally { saving.value = false }
        }
    }
    private fun accountAction(action: suspend () -> Unit) {
        scope.launch {
            try { action() } catch (e: CancellationException) { throw e
            } catch (e: Exception) { messages.send(e.message ?: "Account action failed") }
        }
    }
    fun switchAccount() = accountAction { repository.switchAccount() }
    fun logout() = accountAction { repository.logout() }
}
