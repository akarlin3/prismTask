# Phase 3 — Firestore cleanup runbook

Operational procedure for the Firestore-side cleanup that follows a
successful local Fix D run (Room collapse). Executed on 2026-04-21;
record this procedure for future post-migration cleanups.

## Prereqs

- Fix D has already run against the live Room DB on every signed-in
  device (Room is at canonical state — see `PHASE_3_FIX_D_PLAN.md`).
- You have a known-good post-Fix-D Room DB snapshot locally at
  `.fixd-workspace/live.fixd.db` (or equivalent). Every surviving row
  has a non-null `cloud_id`.
- Service-account key with Firestore admin access is on the host.
- `firebase-admin` + `google-cloud-firestore` installed in Python.
- `adb` works for every device signed into the Firebase account.

## The five steps, in order

Any step skipped or reordered causes re-corruption on next app launch.

### 1. Confirm no app is running

```bash
for D in <device-id-1> <device-id-2>; do
  adb -s "$D" shell "am force-stop com.averycorp.prismtask"
  adb -s "$D" shell "cmd jobscheduler cancel com.averycorp.prismtask"
done
```

Verify with `pidof com.averycorp.prismtask` on each (must be empty).

### 2. Firestore server-side dedup

Dry-run first, then execute.

```bash
python firestore_dedup_fix_d.py <key.json> <uid> \
    --keep-db .fixd-workspace/live.fixd.db

python firestore_dedup_fix_d.py <key.json> <uid> \
    --keep-db .fixd-workspace/live.fixd.db --execute
```

Verify with `firestore_state_check_fix_d.py <key.json> <uid>`.

### 3. Restore Room on every device

Push the same `live.fixd.db` to every device so every Room and server
agree on the same cloud_ids. Clear WAL/SHM so no stale transaction
logs apply on open.

```bash
for D in <device-id-1> <device-id-2>; do
  adb -s "$D" push .fixd-workspace/live.fixd.db /data/local/tmp/averytask.db
  adb -s "$D" shell "run-as com.averycorp.prismtask \
    cp /data/local/tmp/averytask.db \
       /data/data/com.averycorp.prismtask/databases/averytask.db"
  adb -s "$D" shell "run-as com.averycorp.prismtask rm -f \
    /data/data/com.averycorp.prismtask/databases/averytask.db-shm \
    /data/data/com.averycorp.prismtask/databases/averytask.db-wal"
  adb -s "$D" shell "rm /data/local/tmp/averytask.db"
done
```

Verify with an `exec-out cat` pull and an MD5 match against
`live.fixd.db`.

### 4. ⚠ Clear Firestore SDK offline cache on every device

**Do not skip this step.** The Firestore SDK keeps a per-device
offline cache of pending writes (`firestore.[DEFAULT].<project>.(default)`,
typically ~13 MB). A write queued while the device was offline or
between sync cycles will be **replayed on next app open**, resurrecting
deleted docs on the server. This is exactly what happened on
2026-04-21: a "mark complete" that had been queued at 07:46 UTC
resurrected a deleted doc when the app was opened at 18:21 UTC, six
minutes after the cleanup "finished."

```bash
FS="firestore.%5BDEFAULT%5D.averytask-50dc5.%28default%29"
for D in <device-id-1> <device-id-2>; do
  adb -s "$D" shell "run-as com.averycorp.prismtask \
    rm -f /data/data/com.averycorp.prismtask/databases/$FS \
          /data/data/com.averycorp.prismtask/databases/${FS}-journal"
done
```

Trade-off: any unsynced offline write is discarded. This is consistent
with "Room is canonical" — the only state we preserve post-cleanup is
what Room already had in `live.fixd.db`.

Globs do not expand under Android's `run-as` shell — pass both
filenames literally or the `*` stays a literal character.

### 5. Final verification

Before any relaunch:

```bash
python firestore_state_check_fix_d.py <key.json> <uid>
```

All six Fix-D-managed collection counts must match the canonical
target exactly:

| tasks | tags | projects | habits | task_completions | task_templates |
|---|---|---|---|---|---|
| 19 | 3 | 2 | 3 | 12 | 8 |

Most-recent `createdAt` on tasks/tags must be older than the time the
cleanup finished. A more recent timestamp means a pending write has
already replayed — go back to step 4 on the device that did it.

Pull each device's `averytask.db` and MD5-compare to `live.fixd.db`;
all must match.

## Windows / Git Bash gotchas

- Set `export MSYS_NO_PATHCONV=1` before any `adb push /data/...` or
  `adb shell /data/...` — MSYS otherwise translates unix paths to
  Windows paths and the push fails with `remote secure_mkdirs() failed`.
- Use `adb exec-out "run-as <pkg> cat ..."` to pull DBs, not
  `adb shell "run-as <pkg> cat ..."`. The shell variant applies
  CRLF translation on Windows and corrupts binary SQLite files.
- File globs (`*`) in `rm -f <glob>` under `run-as` do not expand.
  Pass every filename literally.

## What NOT to do

- Do not delete the Room DB (`averytask.db`) without immediately
  restoring from `live.fixd.db`. An empty or missing DB triggers the
  Firestore listener to treat every remote doc as a fresh insert and
  hydrate a new-but-empty Room with the cleaned server state — which
  is actually fine, but it bypasses the benefit of keeping Room's
  pre-existing row IDs stable for local foreign keys.
- Do not run the dedup against Firestore while the app is running on
  any device. Live listeners will observe deletions and can issue
  their own compensating writes depending on mapper logic.
- Do not skip step 4. This is the step that is easy to forget and
  silently re-corrupts the server within seconds of the next app open.
