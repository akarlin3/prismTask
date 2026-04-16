# CI Logs

Auto-populated branch containing failure logs from GitHub Actions workflow runs. Do not commit to this branch manually.

Latest failure per workflow:

- Android:    `ci-logs/android-ci/latest.log`
- Backend:    `ci-logs/backend-ci/latest.log`
- Web:        `ci-logs/web-ci/latest.log`
- Release:    `ci-logs/release/latest.log`
- Auto-merge: `ci-logs/auto-merge/latest.log`

Historical failures live at: `ci-logs/<workflow-slug>/<timestamp>-<run-id>.log` on the same branch.

Fetch without authentication (public repo):

```
https://raw.githubusercontent.com/Akarlin3/PrismTask/ci-logs/ci-logs/<workflow-slug>/latest.log
```
