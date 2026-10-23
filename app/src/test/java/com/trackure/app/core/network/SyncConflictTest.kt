package com.trackure.app.core.network

import org.junit.Assert.*
import org.junit.Test

class SyncConflictTest {
    @Test fun remoteRowsNeverReplacePendingEditsOrDeletes() {
        assertFalse(TrackureSyncWorker.canRestore("PENDING_UPLOAD", 10, 20))
        assertFalse(TrackureSyncWorker.canRestore("PENDING_DELETE", 10, 20))
    }
    @Test fun restoreNewRowsAndOnlyNewerAcknowledgedRows() {
        assertTrue(TrackureSyncWorker.canRestore(null, null, 10))
        assertTrue(TrackureSyncWorker.canRestore("SYNCED", 10, 20))
        assertFalse(TrackureSyncWorker.canRestore("SYNCED", 20, 10))
        assertFalse(TrackureSyncWorker.canRestore("DELETED", 20, 10))
    }
}
