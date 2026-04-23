package com.averycorp.prismtask.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * One row per affected entity in a single Approve action. A batch is the
 * group of rows sharing the same [batchId]; the row carries enough state
 * (`pre_state_json`) to reverse the mutation within the 24-hour undo
 * window.
 *
 * **Device-local by design.** The table intentionally has no `cloud_id`
 * column and is not registered with `SyncMapper` / `CloudIdOrphanHealer`.
 * Cross-device undo would race two devices undoing the same batch
 * simultaneously, and the per-entity sync path already propagates the
 * mutated entities themselves. If Avery later wants cross-device batch
 * history, adding a `cloud_id` column + sync hooks is a follow-up — the
 * row shape is forward-compatible.
 *
 * Indexes:
 * - `batch_id` — list all entries for a batch (undo path, history detail)
 * - `created_at` — recent batches first (history list)
 * - `(expires_at, undone_at)` — sweep worker's "expired or already-undone" filter
 */
@Entity(
    tableName = "batch_undo_log",
    indices = [
        Index(value = ["batch_id"]),
        Index(value = ["created_at"]),
        Index(value = ["expires_at", "undone_at"])
    ]
)
data class BatchUndoLogEntry(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    /** UUID grouping every entry from one Approve action. */
    @ColumnInfo(name = "batch_id")
    val batchId: String,
    /** The original natural-language command the user typed. */
    @ColumnInfo(name = "batch_command_text")
    val batchCommandText: String,
    /** [com.averycorp.prismtask.domain.model.BatchEntityType] name. */
    @ColumnInfo(name = "entity_type")
    val entityType: String,
    /** Local FK into the entity's table. NULL only if the entity was
     *  hard-deleted by a later batch and we couldn't preserve identity. */
    @ColumnInfo(name = "entity_id")
    val entityId: Long?,
    /** The entity's `cloud_id` at the time of capture. Belt-and-braces for
     *  the rare case where a sync churns the local id between mutate and undo. */
    @ColumnInfo(name = "entity_cloud_id")
    val entityCloudId: String?,
    /** Full pre-mutation state of the entity, JSON-encoded. The undo path
     *  decodes this back into the matching entity class and writes it. For
     *  hard-deletes, this also captures relations (subtasks, tags) needed
     *  to recreate the row. */
    @ColumnInfo(name = "pre_state_json")
    val preStateJson: String,
    /** [com.averycorp.prismtask.domain.model.BatchMutationType] name. */
    @ColumnInfo(name = "mutation_type")
    val mutationType: String,
    @ColumnInfo(name = "created_at")
    val createdAt: Long,
    /** Wall-clock millis when undo executed; NULL until then. */
    @ColumnInfo(name = "undone_at")
    val undoneAt: Long? = null,
    /** `created_at + 24h`. Sweep worker drops rows past this. */
    @ColumnInfo(name = "expires_at")
    val expiresAt: Long
)
