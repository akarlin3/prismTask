#!/usr/bin/env python3
"""
Read-only Firestore state check for the Phase 3 / Fix D cleanup.

Streams per-collection doc counts under users/<uid>/ and reports whether
Firestore is still at the pre-Fix-D corrupted state (~8,500+ docs), has
been cleaned to the canonical post-Fix-D state (~150 docs), or lies in
between. Also verifies that tags/projects dedup by natural key and that
nothing has written new docs since Fix D.

This script performs NO WRITES. It is safe to run on production.

Usage:
    python firestore_state_check_fix_d.py <key.json> [uid] [--database <id>]

Examples:
    python firestore_state_check_fix_d.py sa.json
    python firestore_state_check_fix_d.py sa.json <firebase-auth-uid>
"""

import sys
import os
import io
import argparse
from datetime import datetime, timezone, timedelta

try:
    sys.stdout.reconfigure(encoding="utf-8")
except Exception:
    sys.stdout = io.TextIOWrapper(sys.stdout.buffer, encoding="utf-8", errors="replace")

from google.cloud import firestore as gcloud_firestore
from google.cloud.firestore_v1.base_query import FieldFilter
from google.oauth2 import service_account

PROJECT_ID = "averytask-50dc5"

COLLECTIONS = [
    "tasks", "tags", "projects", "habits",
    "task_completions", "task_templates",
    "habit_completions", "habit_logs",
    "milestones", "courses", "course_completions",
    "leisure_logs", "self_care_logs", "self_care_steps",
    "bug_reports", "settings",
]

# Baseline from the 2026-04-21 pre-cleanup audit (corrupted state).
BASELINE = {
    "tasks": 4843, "tags": 2165, "projects": 16, "habits": 14,
    "task_completions": 1170, "task_templates": 34,
    "habit_completions": 7, "habit_logs": 137,
    "courses": 4, "course_completions": 6,
    "leisure_logs": 6, "self_care_logs": 38,
    "milestones": None, "self_care_steps": None,
    "bug_reports": None, "settings": None,
}

# Post-Fix-D canonical target (matches .fixd-workspace/live.fixd.db).
CANONICAL_TARGET = {
    "tasks": 19, "tags": 3, "projects": 2, "habits": 3,
    "task_completions": 12, "task_templates": 8,
}


def make_db(key_path, database_id="(default)"):
    creds = service_account.Credentials.from_service_account_file(
        key_path, scopes=["https://www.googleapis.com/auth/cloud-platform"]
    )
    return gcloud_firestore.Client(
        project=PROJECT_ID, credentials=creds, database=database_id
    )


def count_collection(col_ref):
    try:
        agg = col_ref.count().get()
        return int(agg[0][0].value)
    except Exception as e:
        return f"ERROR: {e}"


def count_with_filter(col_ref, field, op, value):
    try:
        q = col_ref.where(filter=FieldFilter(field, op, value))
        agg = q.count().get()
        return int(agg[0][0].value)
    except Exception as e:
        return f"ERROR: {e}"


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("key", help="Path to service-account JSON key")
    ap.add_argument("uid", nargs="?", default=os.environ.get("PRISMTASK_UID"),
                    help="Firebase Auth UID (or set $PRISMTASK_UID)")
    ap.add_argument("--database", default="(default)",
                    help='Named Firestore database (default: "(default)")')
    args = ap.parse_args()

    if not os.path.exists(args.key):
        sys.exit(f"ERROR: key not found at {args.key}")
    if not args.uid:
        sys.exit("ERROR: uid required (positional arg or $PRISMTASK_UID)")

    print(f"Connecting to {PROJECT_ID} (db={args.database}) ...")
    db = make_db(args.key, args.database)
    user_ref = db.collection("users").document(args.uid)

    report = []
    def out(s=""):
        print(s)
        report.append(s)

    now_utc = datetime.now(timezone.utc)
    out(f"# Firestore state check (read-only) — {now_utc.isoformat()}")
    out(f"UID: {args.uid}")
    out("")

    out("## 1. Per-collection doc counts")
    out("")
    counts = {}
    for name in COLLECTIONS:
        col = user_ref.collection(name)
        c = count_collection(col)
        counts[name] = c
        out(f"  {name:22s} = {c}")
    out("")

    out("## 2. Comparison vs earlier baseline audit")
    out("")
    out("| Collection | Earlier audit | Current count | Delta | Verdict |")
    out("|---|---|---|---|---|")

    def verdict(name, cur, base):
        if isinstance(cur, str):
            return "ERROR"
        if base is None:
            return "no baseline"
        if cur == base:
            return "unchanged (matches baseline)"
        tgt = CANONICAL_TARGET.get(name)
        if tgt is not None and cur == tgt:
            return "CLEAN (matches canonical target)"
        if tgt is not None and abs(cur - tgt) <= max(3, int(tgt * 0.2)):
            return f"near canonical ({tgt})"
        if cur < base * 0.5:
            return "reduced (major)"
        if cur < base:
            return "reduced"
        if cur > base * 1.1:
            return "inflated further"
        return "similar to baseline"

    for name in COLLECTIONS:
        cur = counts[name]
        base = BASELINE.get(name)
        base_s = "n/a" if base is None else str(base)
        if isinstance(cur, int) and isinstance(base, int):
            delta_s = f"{cur - base:+d}"
        else:
            delta_s = "n/a"
        out(f"| {name} | {base_s} | {cur} | {delta_s} | {verdict(name, cur, base)} |")
    out("")

    out("## 3. Tasks isCompleted distribution")
    out("")
    tasks = user_ref.collection("tasks")
    total_tasks = counts["tasks"] if isinstance(counts["tasks"], int) else None
    true_count  = count_with_filter(tasks, "isCompleted", "==", True)
    false_count = count_with_filter(tasks, "isCompleted", "==", False)
    missing_count = "skipped (total too large to stream)"
    wrong_type_count = "skipped (total too large to stream)"
    if isinstance(total_tasks, int) and total_tasks <= 500:
        out("  (streaming all tasks since total is small)")
        mc = wt = tc = fc = 0
        for d in tasks.stream():
            data = d.to_dict() or {}
            if "isCompleted" not in data:
                mc += 1
            else:
                v = data["isCompleted"]
                if v is True: tc += 1
                elif v is False: fc += 1
                else: wt += 1
        missing_count, wrong_type_count = mc, wt
        out(f"  Stream-based true  = {tc} (agg count = {true_count})")
        out(f"  Stream-based false = {fc} (agg count = {false_count})")
        true_count, false_count = tc, fc

    out(f"  true         = {true_count}")
    out(f"  false        = {false_count}")
    out(f"  missing      = {missing_count}")
    out(f"  wrong-type   = {wrong_type_count}")
    if isinstance(total_tasks, int) and isinstance(true_count, int) and isinstance(false_count, int):
        accounted = true_count + false_count
        if isinstance(missing_count, int): accounted += missing_count
        if isinstance(wrong_type_count, int): accounted += wrong_type_count
        out(f"  (total tasks = {total_tasks}, accounted = {accounted})")
    out("")

    out("## 4. Duplicate-group spot check (tags, projects)")
    out("")

    out("### tags — group by lower(trim(name))")
    tag_total = 0
    tag_groups = {}
    tag_no_name = 0
    for d in user_ref.collection("tags").stream():
        tag_total += 1
        data = d.to_dict() or {}
        name = data.get("name")
        if not isinstance(name, str):
            tag_no_name += 1
            continue
        key = name.strip().lower()
        tag_groups[key] = tag_groups.get(key, 0) + 1
    out(f"  Total tag docs (streamed): {tag_total}")
    out(f"  Distinct lower(trim(name)) values: {len(tag_groups)}")
    out(f"  Docs with no/invalid 'name' field: {tag_no_name}")
    if tag_groups:
        top = sorted(tag_groups.items(), key=lambda x: -x[1])[:10]
        out("  Top 10 groups (name → count):")
        for k, v in top:
            out(f"    {repr(k):40s} × {v}")
    out("")

    out("### projects — group by trim(name)")
    proj_total = 0
    proj_groups = {}
    proj_no_name = 0
    for d in user_ref.collection("projects").stream():
        proj_total += 1
        data = d.to_dict() or {}
        name = data.get("name")
        if not isinstance(name, str):
            proj_no_name += 1
            continue
        key = name.strip()
        proj_groups[key] = proj_groups.get(key, 0) + 1
    out(f"  Total project docs (streamed): {proj_total}")
    out(f"  Distinct trim(name) values: {len(proj_groups)}")
    out(f"  Docs with no/invalid 'name' field: {proj_no_name}")
    if proj_groups:
        top = sorted(proj_groups.items(), key=lambda x: -x[1])[:10]
        out("  Top 10 groups (name → count):")
        for k, v in top:
            out(f"    {repr(k):40s} × {v}")
    out("")

    if isinstance(counts["tags"], int) and counts["tags"] != tag_total:
        out(f"  NOTE: tag count() agg = {counts['tags']}, stream count = {tag_total} — using stream as authoritative")
    if isinstance(counts["projects"], int) and counts["projects"] != proj_total:
        out(f"  NOTE: project count() agg = {counts['projects']}, stream count = {proj_total} — using stream as authoritative")
    out("")

    out("## 5. Most-recent createdAt")
    out("")

    def most_recent(col_ref, field="createdAt"):
        try:
            docs = list(
                col_ref.order_by(field, direction=gcloud_firestore.Query.DESCENDING)
                       .limit(1).stream()
            )
            if not docs:
                return None, None
            d = docs[0]
            data = d.to_dict() or {}
            return d.id, data.get(field)
        except Exception as e:
            return None, f"ERROR: {e}"

    for coll in ("tasks", "tags"):
        doc_id, ts = most_recent(user_ref.collection(coll))
        out(f"  {coll}: doc={doc_id} createdAt={ts}")
        if isinstance(ts, datetime):
            age = now_utc - ts if ts.tzinfo else now_utc.replace(tzinfo=None) - ts
            out(f"    age = {age}")
            if age < timedelta(hours=6):
                out(f"    WARNING: newer than 6h — something may have written recently")
    out("")

    out("## 6. Verdict")
    out("")

    baseline_match_count = 0
    canonical_match_count = 0
    for k, tgt in CANONICAL_TARGET.items():
        if isinstance(counts.get(k), int):
            base = BASELINE.get(k)
            if counts[k] == base:
                baseline_match_count += 1
            if abs(counts[k] - tgt) <= max(3, int(tgt * 0.25)):
                canonical_match_count += 1

    out(f"Baseline-matching collections (out of {len(CANONICAL_TARGET)}): {baseline_match_count}")
    out(f"Canonical-target-matching collections (out of {len(CANONICAL_TARGET)}): {canonical_match_count}")
    total_docs = sum(v for v in counts.values() if isinstance(v, int))
    out(f"Total docs across checked collections: {total_docs}")
    out("")

    if canonical_match_count >= 5:
        verdict_txt = "CLEAN — Firestore matches the canonical target. Safe to launch app: YES."
    elif baseline_match_count >= 4:
        verdict_txt = (f"STILL CORRUPTED — Firestore matches pre-Fix-D audit baseline "
                       f"(saw {total_docs} total). Safe to launch app: NO.")
    elif baseline_match_count >= 2 and canonical_match_count >= 2:
        verdict_txt = "PARTIAL — some collections cleaned, others not. Safe to launch app: NO."
    else:
        verdict_txt = "UNEXPECTED — counts differ from both baseline and canonical target. Safe to launch app: NO."
    out(f"**{verdict_txt}**")
    out("")


if __name__ == "__main__":
    main()
