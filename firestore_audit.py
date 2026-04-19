#!/usr/bin/env python3
"""
Firestore FK audit script for PrismTask / averytask-50dc5.

Usage:
    python3 firestore_audit.py <key.json> [uid] [--database <db-id>]

Examples:
    python3 firestore_audit.py prismtask-sa.json
    python3 firestore_audit.py prismtask-sa.json abc123uid
    python3 firestore_audit.py prismtask-sa.json abc123uid --database prismtask
"""

import re
import sys
import os
import argparse
from datetime import datetime, timezone
from collections import defaultdict

import firebase_admin
from firebase_admin import credentials, firestore
from google.cloud import firestore as gcloud_firestore
from google.oauth2 import service_account

PROJECT_ID = "averytask-50dc5"
MAX_SAMPLE = 20

# (collection_name, [field_names_to_audit])
AUDIT_PLAN = [
    ("tasks",            ["projectId", "parentTaskId", "sourceHabitId", "tags"]),
    ("task_templates",   ["templateProjectId"]),
    ("milestones",       ["projectCloudId", "projectId"]),
    ("habit_completions",None),   # None = discover FK fields from docs
    ("habit_logs",       None),
    ("task_completions", None),
]

CLOUD_ID_RE = re.compile(r'^[A-Za-z0-9]{20,}$')

def classify(value):
    """Return ('cloud', value), ('local', value), or ('null', None)."""
    if value is None:
        return 'null', value
    if isinstance(value, (int, float)):
        return 'local', value
    if isinstance(value, str):
        if not value:
            return 'null', value
        if CLOUD_ID_RE.match(value):
            return 'cloud', value
        # Short numeric-looking string
        try:
            int(value)
            return 'local', value
        except ValueError:
            pass
        if len(value) < 10:
            return 'local', value
        return 'cloud', value
    if isinstance(value, list):
        return 'array', value
    return 'null', value


def looks_like_fk(field_name):
    return (field_name.endswith('Id') or field_name.endswith('_id') or
            field_name.endswith('CloudId') or field_name.endswith('cloud_id'))


def audit_collection(col_ref, collection_name, audit_fields):
    """Return (field_stats, surprises, raw_examples)."""
    # Sort by updatedAt desc if available, otherwise just limit
    try:
        docs = list(col_ref.order_by('updatedAt', direction=firestore.Query.DESCENDING).limit(MAX_SAMPLE).stream())
    except Exception:
        docs = list(col_ref.limit(MAX_SAMPLE).stream())

    if not docs:
        return {}, [], []

    # Determine fields to audit
    if audit_fields is None:
        # Discover from first doc
        sample_data = docs[0].to_dict() or {}
        audit_fields = [f for f in sample_data if looks_like_fk(f)]

    # Also detect surprise FK fields not in audit_fields
    all_seen_fk_fields = set()
    for doc in docs:
        data = doc.to_dict() or {}
        for f in data:
            if looks_like_fk(f):
                all_seen_fk_fields.add(f)
    surprises = sorted(all_seen_fk_fields - set(audit_fields))

    # Per-field stats
    field_stats = {}
    for field in audit_fields:
        counts = {'cloud': 0, 'local': 0, 'null': 0, 'mixed_array': 0}
        examples = {'local': [], 'mixed_array': []}
        missing = 0
        for doc in docs:
            data = doc.to_dict() or {}
            if field not in data:
                missing += 1
                counts['null'] += 1
                continue
            val = data[field]
            kind, v = classify(val)
            if kind == 'array':
                arr = val
                if not arr:
                    counts['null'] += 1
                else:
                    kinds = [classify(e)[0] for e in arr]
                    if all(k == 'cloud' for k in kinds):
                        counts['cloud'] += 1
                    elif all(k == 'local' for k in kinds):
                        counts['local'] += 1
                        if len(examples['local']) < 3:
                            examples['local'].append((doc.id, val))
                    else:
                        counts['mixed_array'] += 1
                        if len(examples['mixed_array']) < 3:
                            examples['mixed_array'].append((doc.id, val))
            elif kind == 'local':
                counts['local'] += 1
                if len(examples['local']) < 3:
                    examples['local'].append((doc.id, val))
            elif kind == 'cloud':
                counts['cloud'] += 1
            else:
                counts['null'] += 1
        field_stats[field] = {
            'sampled': len(docs),
            'missing_field': missing,
            'counts': counts,
            'examples': examples,
        }

    return field_stats, surprises, docs[:3]


def status_label(counts, sampled, field_name):
    local = counts.get('local', 0) + counts.get('mixed_array', 0)
    cloud = counts.get('cloud', 0)
    null = counts.get('null', 0)
    if local == 0 and cloud == 0:
        return 'MISSING'
    if local == 0:
        return 'OK'
    if cloud == 0:
        return 'BROKEN'
    return 'MIXED'


def make_db(key_path, database_id='(default)'):
    """Return a google-cloud-firestore Client using a service account key file."""
    sa_creds = service_account.Credentials.from_service_account_file(
        key_path,
        scopes=["https://www.googleapis.com/auth/cloud-platform"]
    )
    return gcloud_firestore.Client(
        project=PROJECT_ID,
        credentials=sa_creds,
        database=database_id
    )


def main():
    parser = argparse.ArgumentParser(description='Firestore FK audit for PrismTask')
    parser.add_argument('key', help='Path to service account JSON key')
    parser.add_argument('uid', nargs='?', default=None,
                        help='Firebase Auth UID to audit (skip auto-discovery)')
    parser.add_argument('--database', default=None,
                        help='Named Firestore database (default: tries (default) then common names)')
    args = parser.parse_args()

    key_path = args.key
    if not os.path.exists(key_path):
        print(f"ERROR: Key file not found: {key_path}")
        sys.exit(1)

    print(f"Using credentials: {key_path}")

    # Determine which database(s) to try
    databases_to_try = [args.database] if args.database else ['(default)', 'prismtask', 'averytask']

    db = None
    used_database = None
    for db_id in databases_to_try:
        print(f"Connecting to Firestore database: {db_id!r} ...")
        try:
            candidate = make_db(key_path, db_id)
            # Try a quick probe — list top-level collections
            cols = list(candidate.collections())
            col_names = [c.id for c in cols]
            print(f"  Top-level collections: {col_names}")
            if col_names:  # found something
                db = candidate
                used_database = db_id
                break
            else:
                print(f"  (empty — trying next)")
        except Exception as e:
            print(f"  Error connecting to {db_id!r}: {e}")

    if db is None:
        print("\nERROR: Could not connect to any Firestore database.")
        print("  Try: python3 firestore_audit.py key.json --database YOUR_DB_NAME")
        print("  Find the database name in Firebase Console → Firestore → database dropdown.")
        sys.exit(1)

    # Locate user document
    user_ref = None
    uid = args.uid

    if uid:
        user_ref = db.collection('users').document(uid)
        print(f"Using provided UID: {uid}")
    else:
        print("Auto-discovering user UID...")
        AUDIT_COLLECTIONS = {p[0] for p in AUDIT_PLAN}

        # Strategy 1: stream /users/ directly
        try:
            user_docs = list(db.collection('users').limit(10).stream())
        except Exception as e:
            user_docs = []
            print(f"  WARNING: streaming /users/ raised {e}")

        if user_docs:
            if len(user_docs) == 1:
                uid = user_docs[0].id
                user_ref = db.collection('users').document(uid)
                print(f"  Found 1 user under /users/: {uid}")
            else:
                print("  Multiple users found:")
                for i, d in enumerate(user_docs):
                    print(f"    [{i}] {d.id}")
                idx = int(input("  Enter index: "))
                uid = user_docs[idx].id
                user_ref = db.collection('users').document(uid)
        else:
            # Strategy 2: probe all top-level collections for sub-collection matches
            print("  /users/ stream returned nothing. Probing all collections...")
            for col in db.collections():
                try:
                    sample = list(col.limit(5).stream())
                except Exception as e:
                    print(f"  /{col.id}/: stream error — {e}")
                    continue
                if not sample:
                    print(f"  /{col.id}/: (no documents)")
                    continue
                for doc in sample:
                    try:
                        sub_cols = [s.id for s in doc.reference.collections()]
                    except Exception:
                        sub_cols = []
                    print(f"  /{col.id}/{doc.id}/ → sub-collections: {sub_cols}")
                    if set(sub_cols) & AUDIT_COLLECTIONS:
                        uid = doc.id
                        user_ref = doc.reference
                        print(f"  ✓ Matched! Using /{col.id}/{uid}/")
                        break
                if user_ref:
                    break

    if user_ref is None:
        print("\nERROR: Could not find user data.")
        print("  Options:")
        print("  A) Pass your UID directly:")
        print("     python3 firestore_audit.py key.json YOUR_FIREBASE_UID")
        print("     (Find UID in Firebase Console → Authentication → Users)")
        print("  B) Pass a named database:")
        print("     python3 firestore_audit.py key.json --database YOUR_DB_NAME")
        print("     (Find in Firebase Console → Firestore → database name dropdown)")
        sys.exit(1)
    now_str = datetime.now(timezone.utc).strftime('%Y-%m-%d %H:%M:%S UTC')

    # Run audit
    all_results = {}
    for collection_name, audit_fields in AUDIT_PLAN:
        print(f"  Auditing {collection_name}...", end='', flush=True)
        col_ref = user_ref.collection(collection_name)
        field_stats, surprises, raw_sample = audit_collection(col_ref, collection_name, audit_fields)
        if not field_stats and not surprises:
            print(" (empty or not found)")
        else:
            print(f" {sum(next(iter(v.values())) for v in field_stats.values() if 'sampled' in v)} docs")
        all_results[collection_name] = {
            'field_stats': field_stats,
            'surprises': surprises,
            'raw_sample': raw_sample,
            'audit_fields': audit_fields or list(field_stats.keys()),
        }

    # Build report
    lines = []
    lines.append(f"# Firestore FK Audit — {now_str}\n")
    lines.append(f"Project: `{PROJECT_ID}` | User UID: `{uid}`\n")

    # Summary table
    lines.append("## Summary\n")
    lines.append("| Collection | Field | Sampled | Cloud | Local | Null | Status |")
    lines.append("|---|---|---|---|---|---|---|")
    for collection_name, result in all_results.items():
        field_stats = result['field_stats']
        if not field_stats:
            lines.append(f"| {collection_name} | — | 0 | — | — | — | EMPTY |")
            continue
        for field, stats in field_stats.items():
            sampled = stats['sampled']
            c = stats['counts']
            cloud = c.get('cloud', 0)
            local = c.get('local', 0) + c.get('mixed_array', 0)
            null = c.get('null', 0)
            status = status_label(c, sampled, field)
            lines.append(f"| {collection_name} | {field} | {sampled} | {cloud} | {local} | {null} | {status} |")

    lines.append("")

    # Detailed sections
    lines.append("## Details\n")
    for collection_name, result in all_results.items():
        field_stats = result['field_stats']
        surprises = result['surprises']

        for field, stats in field_stats.items():
            sampled = stats['sampled']
            c = stats['counts']
            cloud = c.get('cloud', 0)
            local = c.get('local', 0) + c.get('mixed_array', 0)
            null = c.get('null', 0)
            status = status_label(c, sampled, field)
            missing = stats['missing_field']

            lines.append(f"### {collection_name}.{field} ({status})\n")
            lines.append(f"Sampled {sampled} docs. Cloud-format: {cloud} | Local-format: {local} | Null/missing: {null}")
            if missing:
                lines.append(f"  - Field absent from {missing} doc(s) entirely")

            # Examples of broken values
            for ex_kind in ('local', 'mixed_array'):
                exs = stats['examples'].get(ex_kind, [])
                if exs:
                    lines.append(f"\nExample docs with {ex_kind} values:")
                    for doc_id, val in exs:
                        lines.append(f"  - `{doc_id}`: `{field}={repr(val)}`")

            lines.append("")

        if surprises:
            lines.append(f"### {collection_name} — Unexpected FK-looking fields\n")
            lines.append("Fields found in documents that look like FKs but were not in the audit plan:\n")
            for f in surprises:
                lines.append(f"  - `{f}`")
            lines.append("")

        if not field_stats and not surprises:
            lines.append(f"### {collection_name}\n")
            lines.append("Collection was empty or not found.\n")

    # Field name surprises across all collections
    all_surprises = {k: v['surprises'] for k, v in all_results.items() if v['surprises']}
    if all_surprises:
        lines.append("## Field Name Surprises\n")
        for col, fields in all_surprises.items():
            lines.append(f"**{col}**: {', '.join(f'`{f}`' for f in fields)}\n")
    else:
        lines.append("## Field Name Surprises\n\nNone detected.\n")

    report = '\n'.join(lines)
    out_path = os.path.join(os.path.dirname(__file__), 'firestore_audit.md')
    with open(out_path, 'w') as f:
        f.write(report)
    print(f"\nReport written to: {out_path}")

    # Print summary table to stdout for convenience
    print("\n" + '\n'.join(lines[lines.index("## Summary\n"):lines.index("")+1] if "## Summary\n" in lines else []))


if __name__ == '__main__':
    main()
