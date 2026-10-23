package com.trackure.app.core.network

import com.trackure.app.core.database.entity.*
import kotlinx.serialization.json.*
import org.junit.Assert.*
import org.junit.Test

class CloudDtoTest {
    private val json = Json { encodeDefaults = true }
    private val owner = "0a1e12be-f058-413c-a239-3d1a797c3f70"

    @Test fun profileUsesAuthUuidAndOnlyFlatColumns() {
        val row = UserProfileEntity(owner, "Athlete", "athlete@example.com", "One step", null, 1000)
        val wire = json.encodeToJsonElement(row.toCloud()).jsonObject
        assertEquals(setOf("id", "name", "profile_url_or_email", "motto", "avatar_url", "updated_at"), wire.keys)
        assertEquals(owner, wire.getValue("id").jsonPrimitive.content)
        assertEquals("1970-01-01T00:00:01Z", wire.getValue("updated_at").jsonPrimitive.content)
        assertEquals(JsonNull, wire["avatar_url"])
        assertEquals(row.copy(syncStatus = "SYNCED"), json.decodeFromJsonElement<ProfileDto>(wire).toEntity())
    }

    @Test fun activityMapsEveryColumnWithoutRoomBookkeeping() {
        val row = ActivityEntity("ics-id", "Run", "Cardio", "07:00", true, true, "DAILY", "1,3", null,
            2, "NEVER", null, null, "PENDING_UPLOAD", 1000, ownerId = owner)
        val wire = json.encodeToJsonElement(row.toCloud()).jsonObject
        assertEquals(setOf("id", "user_id", "title", "category", "scheduled_time", "full_screen_lockscreen_trigger",
            "loop_activity", "frequency", "repeat_days_of_week_csv", "specific_date_iso", "interval_count",
            "end_condition_type", "end_date", "end_occurrences", "updated_at"), wire.keys)
        assertEquals(owner, wire.getValue("user_id").jsonPrimitive.content)
        val restored = json.decodeFromJsonElement<ActivityDto>(wire).toEntity()
        assertEquals(row.copy(syncStatus = "SYNCED", startDateIso = restored.startDateIso), restored)
    }

    @Test fun logsPreserveGeneralNotesAndNullableCompletionValues() {
        val row = ActivityLogEntity("log", "general", "Daily Note", null, "", "2026-10-23", false,
            null, null, null, "PENDING_UPLOAD", 1000, owner)
        val wire = json.encodeToJsonElement(row.toCloud()).jsonObject
        assertEquals(setOf("id", "user_id", "activity_id", "activity_title", "category", "scheduled_time", "date_iso",
            "is_completed", "completed_hour", "completed_minute", "note", "updated_at"), wire.keys)
        assertEquals(JsonNull, wire["note"])
        assertEquals(row.copy(syncStatus = "SYNCED"), json.decodeFromJsonElement<ActivityLogDto>(wire).toEntity())
    }
}
