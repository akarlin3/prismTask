# Point git at the version-controlled hooks directory. Idempotent —
# rerunning is a no-op, and edits to scripts/hooks/* take effect on the
# next git command without a re-install (was a copy-on-install before;
# see CI_AUDIT_2026-04-30 F2).
$ErrorActionPreference = "Stop"
git config core.hooksPath scripts/hooks
Write-Host "Configured core.hooksPath = scripts/hooks"
Write-Host "Active hooks: pre-push, post-commit"
