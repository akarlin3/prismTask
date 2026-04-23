package com.averycorp.prismtask.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.averycorp.prismtask.data.local.entity.BatchUndoLogEntry
import kotlinx.coroutines.flow.Flow

/**
 * DAO for [BatchUndoLogEntry]. The table is append-only at write time
 * (one INSERT per affected entity in a batch); the sweep worker is the
 * only path that issues DELETE.
 */
@Dao
interface BatchUndoLogDao {
    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(entry: BatchUndoLogEntry): Long

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertAll(entries: List<BatchUndoLogEntry>): List<Long>

    /**
     * Distinct batch IDs ordered most-recent-first. Backs the Settings
     * "Batch command history" list. The 24h window is enforced by the
     * sweep worker, so consumers don't need to filter on `expires_at`.
     */
    @Query(
        """
        SELECT DISTINCT batch_id FROM batch_undo_log
        ORDER BY created_at DESC
        """
    )
    fun observeBatchIds(): Flow<List<String>>

    /**
     * Per-entry rows for one batch, in insertion order. The history detail
     * screen + undo path both consume this.
     */
    @Query("SELECT * FROM batch_undo_log WHERE batch_id = :batchId ORDER BY id ASC")
    suspend fun getEntriesForBatchOnce(batchId: String): List<BatchUndoLogEntry>

    @Query("SELECT * FROM batch_undo_log WHERE batch_id = :batchId ORDER BY id ASC")
    fun observeEntriesForBatch(batchId: String): Flow<List<BatchUndoLogEntry>>

    /**
     * Most recent batch_id, regardless of undone state. Used by the post-
     * commit Snackbar's `Undo` action to reverse "the batch I just made"
     * without the caller needing to remember the UUID.
     */
    @Query(
        """
        SELECT batch_id FROM batch_undo_log
        ORDER BY created_at DESC LIMIT 1
        """
    )
    suspend fun getMostRecentBatchIdOnce(): String?

    @Query(
        """
        UPDATE batch_undo_log SET undone_at = :now
        WHERE batch_id = :batchId AND undone_at IS NULL
        """
    )
    suspend fun markBatchUndone(batchId: String, now: Long): Int

    /**
     * Sweep: expired and never-undone, OR already-undone (we don't keep
     * undone history forever — undone rows hang around for a short tail
     * so the Settings UI can show "undone X minutes ago" before the row
     * disappears).
     */
    @Query(
        """
        DELETE FROM batch_undo_log
        WHERE (expires_at < :now AND undone_at IS NULL)
           OR (undone_at IS NOT NULL AND undone_at < :undoneCutoff)
        """
    )
    suspend fun sweep(now: Long, undoneCutoff: Long): Int

    /** All rows — test-only debug helper. */
    @Query("SELECT * FROM batch_undo_log ORDER BY id ASC")
    suspend fun getAllOnce(): List<BatchUndoLogEntry>

    @Query("SELECT COUNT(*) FROM batch_undo_log")
    suspend fun count(): Int
}
