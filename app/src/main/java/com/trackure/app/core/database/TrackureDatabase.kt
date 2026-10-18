package com.trackure.app.core.database

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.sqlite.db.SupportSQLiteDatabase
import com.trackure.app.core.database.dao.TrackureDao
import com.trackure.app.core.database.entity.*
import java.time.LocalDate

@Database(entities = [UserProfileEntity::class, ActivityEntity::class, ActivityLogEntity::class], version = 1, exportSchema = false)
@TypeConverters(Converters::class)
abstract class TrackureDatabase : RoomDatabase() {
    abstract fun trackureDao(): TrackureDao

    companion object {
        @Volatile private var instance: TrackureDatabase? = null
        fun getInstance(context: Context): TrackureDatabase = instance ?: synchronized(this) {
            instance ?: Room.databaseBuilder(context.applicationContext, TrackureDatabase::class.java, "trackure.db")
                .addCallback(SeedCallback).build().also { instance = it }
        }

        // onCreate runs inside SQLite's creation transaction, before the first query is served.
        // No asynchronous seed job, recursive singleton access, or reseeding after user deletion.
        internal object SeedCallback : Callback() {
            override fun onCreate(db: SupportSQLiteDatabase) {
                val now = System.currentTimeMillis()
                val today = LocalDate.now().toString()
                db.execSQL("INSERT INTO user_profile (id,name,profileUrlOrEmail,motto,avatarUrl,updatedAt) VALUES (?,?,?,?,?,?)",
                    arrayOf<Any?>("local_user", "Muhib Mdn", "https://github.com/muhibmdn", "One Sure Step Everyday", null, now))
                val titles = listOf("Morning Gym Session", "Algoritma & Struktur Data", "Easy 5K Recovery Run", "Review Catatan Kuliah & Prep", "Mobility & Sleep Routine")
                val categories = listOf("Strength", "Academics", "Cardio", "Deep Work", "Wellness")
                val times = listOf("07:00", "09:00", "16:30", "20:00", "22:00")
                val completed = listOf("07:12", "09:05", "17:15")
                val notes = listOf("Upper body push & pull. Core stability.", "Binary search tree & recursion review completed.", "5.02 km recovery run. Felt smooth.")
                titles.indices.forEach { i ->
                    val id = "initial_activity_${i + 1}"
                    db.execSQL("""INSERT INTO activities (id,title,category,scheduledTime,fullScreenLockscreenTrigger,loopActivity,
                        frequency,repeatDaysOfWeekCsv,specificDateIso,intervalCount,endConditionType,endDate,endOccurrences,syncStatus,updatedAt,startDateIso,repeatOnSpecificDate)
                        VALUES (?,?,?,?,0,1,'DAILY','',NULL,1,'NEVER',NULL,NULL,'PENDING_UPLOAD',?,?,0)""",
                        arrayOf<Any?>(id, titles[i], categories[i], times[i], now, today))
                    db.execSQL("""INSERT INTO activity_logs (id,activityId,activityTitle,category,scheduledTime,dateIso,isCompleted,
                        completedHour,completedMinute,note,syncStatus,updatedAt) VALUES (?,?,?,?,?,?,?,?,?,?,'PENDING_UPLOAD',?)""",
                        arrayOf<Any?>("initial_log_${i + 1}", id, titles[i], categories[i], times[i], today, if (i < 3) 1 else 0,
                            completed.getOrNull(i)?.substringBefore(':'), completed.getOrNull(i)?.substringAfter(':'), notes.getOrNull(i), now))
                }
            }
        }
    }
}
