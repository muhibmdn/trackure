package com.trackure.app.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.trackure.app.domain.repository.TrackureRepository
import com.trackure.app.presentation.home.HomeController
import com.trackure.app.presentation.tracker.TrackerController
import com.trackure.app.presentation.add.AddActivityController
import com.trackure.app.presentation.history.HistoryController
import com.trackure.app.presentation.notes.NotesController
import java.time.LocalDate

/** The sole lifecycle owner for all five screens; feature controllers share its scope and data. */
class TrackureViewModel(repository: TrackureRepository, today: LocalDate) : ViewModel() {
    val userProfile = repository.userProfile
    val home = HomeController(repository, today, viewModelScope)
    val tracker = TrackerController(repository, today, viewModelScope)
    val add = AddActivityController(repository, viewModelScope)
    val history = HistoryController(repository, today, viewModelScope)
    val notes = NotesController(repository, today, viewModelScope)
}
