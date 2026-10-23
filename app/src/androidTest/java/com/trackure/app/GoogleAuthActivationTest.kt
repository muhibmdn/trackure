package com.trackure.app

import android.content.Context
import android.content.ContextWrapper
import androidx.room.Room
import androidx.test.platform.app.InstrumentationRegistry
import com.trackure.app.core.database.TrackureDatabase
import com.trackure.app.core.network.TrackureCloudManager
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

class GoogleAuthActivationTest {
    private fun isolatedContext(): Context = object : ContextWrapper(InstrumentationRegistry.getInstrumentation().targetContext) {
        private val suffix = java.util.UUID.randomUUID().toString()
        override fun getSharedPreferences(name: String, mode: Int) = super.getSharedPreferences("test_${suffix}_$name", mode)
    }

    @Test fun cloudFailureKeepsGoogleProfileActiveAndReportsExactMessage() = runBlocking {
        val context = isolatedContext()
        val db = Room.inMemoryDatabaseBuilder(context, TrackureDatabase::class.java).build()
        val dao = db.trackureDao()
        var shown = ""
        var cleared = false
        try {
            val result = TrackureCloudManager.completeGoogleSignIn(context, "Google Athlete", "athlete@example.com", "https://example.com/avatar",
                database = db, requestSync = { fail("No cloud sync without a Supabase identity") },
                clearStaleSession = { cleared = true }, reportCloudError = { shown = it }, exchangeToken = {
                    // The Google profile must be committed before the failing network exchange.
                    assertEquals("Google Athlete", dao.getUserProfileOnce()!!.name)
                    assertEquals("athlete@example.com", dao.getUserProfileOnce()!!.profileUrlOrEmail)
                    throw IllegalStateException("Invalid API key")
                })
            assertTrue(result.isSuccess)
            assertTrue(TrackureCloudManager.isAuthenticatedOrLocalActive.value)
            assertTrue(dao.activeUserId().startsWith("local_"))
            assertEquals("Local Mode", TrackureCloudManager.syncStatus.value)
            assertEquals("Invalid API key", shown)
            assertTrue(cleared)
            assertTrue(context.getSharedPreferences("trackure_auth", Context.MODE_PRIVATE).getBoolean("local_active", false))
        } finally { db.close() }
    }

    @Test fun cloudSuccessPromotesProfileToAuthUuidAndRequestsImmediateSync() = runBlocking {
        val context = isolatedContext()
        val db = Room.inMemoryDatabaseBuilder(context, TrackureDatabase::class.java).build()
        val userId = "ac3df581-a04b-4318-9db2-fd64ba788172"
        var requested = false
        try {
            val result = TrackureCloudManager.completeGoogleSignIn(context, "Google Athlete", "athlete@example.com", null,
                database = db, requestSync = { requested = true }, clearStaleSession = { fail("No cleanup on success") },
                reportCloudError = { fail(it) }, exchangeToken = { userId })
            assertEquals(userId, result.getOrThrow().id)
            assertEquals(userId, db.trackureDao().activeUserId())
            assertTrue(TrackureCloudManager.isAuthenticatedOrLocalActive.value)
            assertTrue(requested)
            assertFalse(context.getSharedPreferences("trackure_auth", Context.MODE_PRIVATE).getBoolean("local_active", true))
        } finally { db.close() }
    }
}
