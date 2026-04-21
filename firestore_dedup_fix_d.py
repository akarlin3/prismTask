#!/usr/bin/env python3
"""
Firestore dedup using a Room DB's cloud_id set as the keep-set.

For each Firestore subcollection under users/<uid>/, keep docs whose
doc-ID is in the keep-set (extracted from --keep-db), and delete
everything else. bug_reports and settings are never touched.

Default mode is dry-run. Pass --execute to actually delete.

Usage:
    python firestore_dedup_fix_d.py <key.json> <uid> --keep-db <path> [--execute]
    python firestore_dedup_fix_d.py sa.json 2Bg... --keep-db live.fixd.db
    python firestore_dedup_fix_d.py sa.json 2Bg... --keep-db live.fixd.db --execute
"""

import sys
import os
import io
import json
import argparse
import sqlite3
import time
from datetime import datetime, timezone

try:
    sys.stdout.reconfigure(encoding="utf-8")
except Exception:
    sys.stdout = io.TextIOWrapper(sys.stdout.buffer, encoding="utf-8", errors="replace")

from google.cloud import firestore as gcloud_firestore
from google.oauth2 import service_account

PROJECT_ID = "averytask-50dc5"

# Collections to dedup. bug_reports and settings are intentionally excluded.
COLLECTIONS = [
    "tasks", "tags", "projects", "habits",
    "task_completions", "task_templates",
    "habit_completions", "habit_logs",
    "milestones", "courses", "course_completions",
    "leisure_logs", "self_care_logs", "self_care_steps",
]

# Firestore's hard batch limit is 500; keep headroom.
BATCH_SIZE = 450


def load_keep_from_db(db_path):
    """Extract cloud_id keep-set from a Room SQLite DB."""
    if not os.path.exists(db_path):
        sys.exit(f"ERROR: keep-db not found at {db_path}")
    conn = sqlite3.connect(db_path)
    c = conn.cursor()
    keep = {}
    for coll in COLLECTIONS:
        c.execute(f"PRAGMA table_info({coll})")
        cols = [r[1] for r in c.fetchall()]
        if "cloud_id" not in cols:
            keep[coll] = set()
            continue
        c.execute(f"SELECT cloud_id FROM {coll} WHERE cloud_id IS NOT NULL AND cloud_id != ''")
        keep[coll] = {r[0] for r in c.fetchall()}
    conn.close()
    return keep


def make_db(key_path, database_id="(default)"):
    creds = service_account.Credentials.from_service_account_file(
        key_path, scopes=["https://www.googleapis.com/auth/cloud-platform"]
    )
    return gcloud_firestore.Client(
        project=PROJECT_ID, credentials=creds, database=database_id
    )


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("key", help="Path to service-account JSON key")
    ap.add_argument("uid", nargs="?", default=os.environ.get("PRISMTASK_UID"),
                    help="Firebase Auth UID (or set $PRISMTASK_UID)")
    ap.add_argument("--keep-db", required=True,
                    help="Path to Room SQLite DB whose cloud_ids define the keep-set")
    ap.add_argument("--database", default="(default)",
                    help='Named Firestore database (default: "(default)")')
    ap.add_argument("--execute", action="store_true",
                    help="Actually delete. Default is dry-run.")
    ap.add_argument("--log-dir", default=".",
                    help="Directory for deletion log output (default: cwd)")
    args = ap.parse_args()

    if not os.path.exists(args.key):
        sys.exit(f"ERROR: key not found at {args.key}")
    if not args.uid:
        sys.exit("ERROR: uid required (positional arg or $PRISMTASK_UID)")

    keep_sets = load_keep_from_db(args.keep_db)

    mode = "EXECUTE" if args.execute else "DRY-RUN"
    ts = datetime.now(timezone.utc).strftime("%Y%m%dT%H%M%SZ")
    print(f"\n{'='*70}\nFirestore dedup — mode={mode} — ts={ts}\n{'='*70}")
    print(f"UID: {args.uid}")
    print(f"Keep-set source: {args.keep_db}")
    total_keep_ids = sum(len(v) for v in keep_sets.values())
    print(f"Keep-set totals: {total_keep_ids} cloud_ids across {len(keep_sets)} collections")
    print()

    db = make_db(args.key, args.database)
    user_ref = db.collection("users").document(args.uid)

    log_path = os.path.join(os.path.abspath(args.log_dir),
                            f"dedup_{'exec' if args.execute else 'dryrun'}_{ts}.log")
    log_f = open(log_path, "w", encoding="utf-8")
    def log(s=""):
        print(s)
        log_f.write(s + "\n")

    log(f"Mode: {mode}")
    log(f"Timestamp: {ts}")
    log(f"UID: {args.uid}")
    log(f"Keep-db: {args.keep_db}")
    log("")

    log(f"{'collection':22s} {'fs_total':>8s} {'to_keep':>8s} {'to_del':>8s} {'missing':>8s} verdict")
    log("-" * 85)

    total_to_delete = 0
    total_to_keep = 0
    plans = {}

    for coll_name in COLLECTIONS:
        col_ref = user_ref.collection(coll_name)
        keep = keep_sets[coll_name]

        fs_ids = set()
        for doc in col_ref.stream():
            fs_ids.add(doc.id)

        to_keep_set = fs_ids & keep
        to_delete_set = fs_ids - keep
        missing = keep - fs_ids

        plans[coll_name] = {
            "fs_total": len(fs_ids),
            "keep_expected": len(keep),
            "to_keep": sorted(to_keep_set),
            "to_delete": sorted(to_delete_set),
            "missing_expected": sorted(missing),
        }

        total_to_delete += len(to_delete_set)
        total_to_keep += len(to_keep_set)

        verdict = "OK"
        if missing:
            verdict = f"{len(missing)} expected cloud_ids not in FS"
        if len(fs_ids) == 0 and len(keep) == 0:
            verdict = "both empty (no-op)"

        log(f"{coll_name:22s} {len(fs_ids):>8d} {len(to_keep_set):>8d} {len(to_delete_set):>8d} {len(missing):>8d} {verdict}")

    log("-" * 85)
    log(f"{'TOTAL':22s} {sum(p['fs_total'] for p in plans.values()):>8d} {total_to_keep:>8d} {total_to_delete:>8d}")
    log("")

    for coll, plan in plans.items():
        if plan["missing_expected"]:
            log(f"[WARN] {coll}: {len(plan['missing_expected'])} cloud_ids in keep-set have NO matching doc in FS")
            for cid in plan["missing_expected"][:20]:
                log(f"       missing: {cid}")
            if len(plan["missing_expected"]) > 20:
                log(f"       ... and {len(plan['missing_expected'])-20} more")
    log("")
    log(f"Log: {log_path}")

    if not args.execute:
        log("")
        log("Dry-run complete. No deletions performed. Pass --execute to delete.")
        log_f.close()
        return

    log("")
    log("=" * 70)
    log(f"EXECUTING DELETIONS: {total_to_delete} docs across {len(COLLECTIONS)} collections")
    log("=" * 70)

    deleted_total = 0
    t0 = time.time()
    for coll_name in COLLECTIONS:
        plan = plans[coll_name]
        to_del = plan["to_delete"]
        if not to_del:
            continue

        col_ref = user_ref.collection(coll_name)
        log(f"\n{coll_name}: deleting {len(to_del)} docs in batches of {BATCH_SIZE}")

        n = 0
        batch = db.batch()
        batch_count = 0
        for doc_id in to_del:
            batch.delete(col_ref.document(doc_id))
            batch_count += 1
            n += 1
            if batch_count >= BATCH_SIZE:
                batch.commit()
                log(f"  committed {batch_count} (progress: {n}/{len(to_del)})")
                batch = db.batch()
                batch_count = 0
        if batch_count > 0:
            batch.commit()
            log(f"  committed {batch_count} (progress: {n}/{len(to_del)})")

        deleted_total += n
        log(f"  {coll_name}: deleted {n} docs")

    elapsed = time.time() - t0
    log("")
    log(f"Total deleted: {deleted_total} docs in {elapsed:.1f}s")
    log_f.close()


if __name__ == "__main__":
    main()
