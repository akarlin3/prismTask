#!/usr/bin/env bash
# Audit item #11 — one-shot setup script that configures branch protection
# on `main` to require the CI checks that today's audit identified as
# load-bearing (lint-and-test + Integration CI's connected-tests + Backend CI
# + Web CI).
#
# Branch protection is a GitHub repository setting, not something that lives
# in a file in the repo, so the canonical setup is a single `gh api` call.
# Running this script is idempotent — `gh api PUT` replaces the current
# protection config with the one encoded below. Re-run after upgrading the
# required-check list (e.g. adding a new workflow).
#
# Usage:
#   bash scripts/setup-branch-protection.sh
#
# Requires:
#   - `gh` CLI authenticated as a repo admin
#   - The required check names below must match the `name:` values in
#     the corresponding workflow files (GitHub matches by job display name,
#     not file path).
#
# Why this matters: today's session landed PRs #689-#702 solely because
# feature PRs without the `ci:integration` label bypassed Android
# Integration CI and their breakage surfaced only post-merge on main.
# Together with `.github/workflows/label-integration-ci.yml` (audit #1),
# requiring `connected-tests` via branch protection closes that gap
# authoritatively: even if the auto-label fails, the merge is blocked
# until the check runs.

set -euo pipefail

REPO="${REPO:-averycorp/prismTask}"
BRANCH="${BRANCH:-main}"

REQUIRED_CHECKS=(
  "lint-and-test"       # Android CI
  "connected-tests"     # Android Integration CI — matches PR label auto-apply
  "test"                # Backend CI
  "web-lint-and-test"   # Web CI (renamed from lint-and-test to avoid collision with Android CI)
)

# Build the JSON payload. `strict: true` requires branches to be up-to-date
# before merging — together with the `update-branch` retry in auto-merge.yml
# (audit #4), this means stale PRs get rebased automatically instead of
# merging a stale-base commit.
CHECKS_JSON=$(printf '"%s",' "${REQUIRED_CHECKS[@]}" | sed 's/,$//')
PAYLOAD=$(cat <<EOF
{
  "required_status_checks": {
    "strict": true,
    "contexts": [${CHECKS_JSON}]
  },
  "enforce_admins": false,
  "required_pull_request_reviews": null,
  "restrictions": null,
  "required_linear_history": true,
  "allow_force_pushes": false,
  "allow_deletions": false,
  "required_conversation_resolution": false,
  "lock_branch": false,
  "allow_fork_syncing": false
}
EOF
)

echo "Applying branch protection to $REPO:$BRANCH …"
echo "Required checks: ${REQUIRED_CHECKS[*]}"
echo

echo "$PAYLOAD" | gh api --method PUT \
  "repos/$REPO/branches/$BRANCH/protection" \
  --input -

echo
echo "Done. Verify at:"
echo "  https://github.com/$REPO/settings/branches"
