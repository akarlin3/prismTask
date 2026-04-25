package com.averycorp.prismtask.data.local.database

import android.content.Context
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.averycorp.prismtask.data.diagnostics.MigrationInstrumentor

/**
 * Database file name used for [MigrationInstrumentor.dbSizeBytes]
 * and any external diagnostic that needs to size the file. Mirrors
 * the literal in [com.averycorp.prismtask.di.DatabaseModule].
 */
const val DATABASE_FILE_NAME = "averytask.db"

/**
 * Returns [ALL_MIGRATIONS] wrapped so each `migrate()` call is
 * timed by [MigrationInstrumentor]. Wiring point:
 *
 * ```
 * .addMigrations(*instrumentedMigrations(context))
 * ```
 *
 * inside [com.averycorp.prismtask.di.DatabaseModule.provideDatabase].
 *
 * Calling this also primes [MigrationInstrumentor] with the
 * application [FirebaseAnalytics] instance — events that fire
 * before Hilt realizes the migration call site stay buffered.
 */
fun instrumentedMigrations(context: Context): Array<Migration> {
    MigrationInstrumentor.bindAnalytics(context)
    val dbSize = MigrationInstrumentor.dbSizeBytes(context, DATABASE_FILE_NAME)
    return ALL_MIGRATIONS.map { wrap(it, dbSize) }.toTypedArray()
}

private fun wrap(real: Migration, dbSizeBytes: Long): Migration =
    object : Migration(real.startVersion, real.endVersion) {
        override fun migrate(db: SupportSQLiteDatabase) {
            MigrationInstrumentor.run(real.startVersion, real.endVersion, dbSizeBytes) {
                real.migrate(db)
            }
        }
    }
