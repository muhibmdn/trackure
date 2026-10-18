package com.trackure.app.domain.model

/** A journal entry has no scheduled activity and never contributes to completion analytics. */
object GeneralDailyNote {
    const val ACTIVITY_ID = "trackure:general-daily-note"
    const val TITLE = "General Daily Note"
}
