# Firebase Emulator Suite (Local Sync Testing)

Debug builds of PrismTask default to talking to a local Firebase Emulator
Suite instead of production Firestore/Auth. This lets you exercise two-device
sync scenarios against a throwaway local instance — no Google account, no
production data touched.

## What's Emulated

Only the services PrismTask actually uses:

- **Firestore** on port `8080`
- **Auth** on port `9099`
- **Emulator UI** on port `4000` (inspect docs + auth users)

Functions, Storage, and Realtime Database are intentionally out of scope.

## Prerequisites

1. **firebase-tools** (the Firebase CLI):
   ```bash
   npm install -g firebase-tools
   # or, without a global install:
   npx firebase-tools --version
   ```
2. **Java 11+** — required by the emulator JVM. Most Android dev setups
   already satisfy this. Verify with `java -version`.

No sign-in with `firebase login` is required to run emulators locally.

## Start / Stop

From the repo root:

```bash
firebase emulators:start \
  --import=./firebase-emulator-data \
  --export-on-exit=./firebase-emulator-data
```

`--import` loads the last snapshot if present; `--export-on-exit` writes a
fresh snapshot to the same directory on Ctrl+C. The directory is gitignored.

Stop with **Ctrl+C**. Data is auto-exported on clean exit. Ungraceful
shutdowns (`kill -9`, OS crash) discard any writes since the last export.

## Reset State

```bash
rm -rf firebase-emulator-data
```

Then restart. A fresh run starts empty.

## Connecting the Android App

Debug builds have `BuildConfig.USE_FIREBASE_EMULATOR = true` and auto-route
Firestore + Auth to the emulator. See `app/build.gradle.kts`.

### Android Emulator (AVD)

Works out of the box. The app detects the emulator via `Build.FINGERPRINT`
and connects to the special `10.0.2.2` loopback alias that the AVD exposes
for the host machine.

### Physical Device

The app connects to `localhost` on the device, which needs ADB port
forwarding to hit your laptop:

```bash
adb reverse tcp:8080 tcp:8080
adb reverse tcp:9099 tcp:9099
```

**Rerun these after every disconnect / reconnect** of the device or an `adb
kill-server`. This is deliberately not automated — per the setup decision,
the developer owns when to wire the device at local services.

### Two-Device Setup

Any combination works:

- **AVD + AVD** — both auto-detect and use `10.0.2.2`. Easiest.
- **AVD + physical** — physical device needs `adb reverse`.
- **Two physical** — each needs its own `adb reverse` (ADB tracks per-device).

Sign in on each device via `EmulatorAuthHelper.signInAsTestUser()` (debug
build only; see `app/src/debug/...`). Passing no arguments uses the same
default account (`test@prismtask.local`) on both, which is usually what you
want for sync tests.

## Emulator UI

Open <http://localhost:4000> once the emulator is running:

- **Firestore tab** — browse and edit docs, watch writes in real time.
- **Auth tab** — see test accounts the app has created.
- **Logs tab** — see every RPC the SDK has made.

Invaluable for confirming "did device A's write arrive" without instrumenting
the app.

## Switching Between Emulator and Production

There's no runtime toggle. To point a debug APK at production Firestore/Auth
(e.g. to reproduce a prod-only bug):

1. Open `app/build.gradle.kts`.
2. In the `debug { ... }` block, change
   `buildConfigField("boolean", "USE_FIREBASE_EMULATOR", "true")`
   to `"false"`.
3. Rebuild.

Release builds are always pinned to `false`. This is guarded by the separate
`release { ... }` block.

## Known Caveats

- **Initialization ordering matters.** `FirebaseFirestore.useEmulator` throws
  if the Firestore client has already run a query. PrismTask calls it from
  `PrismTaskApplication.onCreate()`, before any repository dispatches work.
  Don't move Firestore access earlier in startup without re-verifying.
- **Auth state listener is registered before `useEmulator`.** `AuthManager`
  attaches a `FirebaseAuth.AuthStateListener` during Hilt injection, which
  runs inside `super.onCreate()` — strictly before our `useEmulator` call.
  The Auth SDK tolerates this: `useEmulator` only requires that no sign-in
  RPC has been dispatched, and sign-in only happens in response to user
  action. If a future change triggers sign-in during startup, revisit this.
- **Persistence is disabled in debug builds.** Firestore's offline cache is
  turned off when the emulator flag is on so the emulator is always the
  source of truth. Consequences: offline edits will NOT surface, and you
  cannot test offline-cache-specific behaviour while pointed at the emulator.
- **`firestore.rules` in this repo is a stub.** The production rules live
  only in the Firebase console. The stubbed file allows any authenticated
  user to read/write everything — it is NOT a mirror of production and must
  never be deployed. If you import real rules later, swap the file and keep
  `firebase.json` pointing at it.
- **Silent fallback to production.** If `useEmulator` fails (e.g. because
  Firestore was used before the call), the SDK silently continues talking
  to production. `configureFirebaseEmulator` logs any exception; watch
  `adb logcat -s PrismTaskApp` to catch this.
- **Emulator data is per-directory.** Snapshots live under
  `./firebase-emulator-data/`. If you start the emulator from a different
  working directory, a different snapshot is used.

## Troubleshooting

- **"No data showing up in the app."** You probably forgot `adb reverse` on a
  physical device. Also check the UI at <http://localhost:4000> to confirm
  the emulator is actually receiving traffic.
- **"useEmulator() after instance has already been initialized" exception.**
  Something in startup performed a Firestore read before
  `PrismTaskApplication.onCreate`. File a bug — don't paper over it.
- **`adb: more than one device/emulator`.** Target a specific device:
  `adb -s <serial> reverse tcp:8080 tcp:8080`. Find serials with `adb
  devices`.
- **Port already in use.** Another emulator instance is still running.
  `lsof -i :8080` / `lsof -i :9099` to find it, then kill it.
