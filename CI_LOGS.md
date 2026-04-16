# CI Failure Logs

Whenever a workflow fails, the failing job's log is auto-committed to the `ci-logs` branch of this public repo. You can fetch the latest failure for any workflow without authentication:

- Android:    https://raw.githubusercontent.com/Akarlin3/PrismTask/ci-logs/ci-logs/android-ci/latest.log
- Backend:    https://raw.githubusercontent.com/Akarlin3/PrismTask/ci-logs/ci-logs/backend-ci/latest.log
- Web:        https://raw.githubusercontent.com/Akarlin3/PrismTask/ci-logs/ci-logs/web-ci/latest.log
- Release:    https://raw.githubusercontent.com/Akarlin3/PrismTask/ci-logs/ci-logs/release/latest.log
- Auto-merge: https://raw.githubusercontent.com/Akarlin3/PrismTask/ci-logs/ci-logs/auto-merge/latest.log

Historical failures live at: `ci-logs/<workflow-slug>/<timestamp>-<run-id>.log` on the same branch.

## For Claude Code

When the user reports a CI failure, fetch the relevant `latest.log` URL above before asking for logs. Do not ask the user to paste CI output — it's already accessible.
