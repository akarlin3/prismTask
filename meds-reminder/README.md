# Next Dose — Standalone Medication Reminder

A tiny, self-contained web app that reminds you when your **next dose is due**,
a set length of time after you tap that you took each medication.

It is a single static file — `index.html` — with **no build step, no server, no
account**. Everything runs in your browser and all data stays on your device.

## How it works

1. Add a medication: name, optional dose (e.g. "400 mg"), and a **remind-after
   interval** (e.g. 6 hr).
2. When you take it, tap **Take Now**. A live countdown starts.
3. When the interval elapses, you get a **browser notification** ("Time for
   Ibuprofen — your next dose is due now"), a chime, and a phone vibration.
4. Tap **Take Now** again to log the next dose and restart the timer, or
   **Snooze 10m** if you're not ready.

The interval is per-medication and independent — each one counts down from the
moment *that* medication was last taken, exactly as requested.

## Running it

Just open `index.html` in any modern browser. For notification permissions to
be granted reliably, serve it over `http://localhost` or `https://` rather than
`file://`:

```bash
cd meds-reminder
python3 -m http.server 8080
# then open http://localhost:8080
```

On a phone, use **Add to Home Screen** for an app-like, full-screen experience.

## Notes & limitations

- **Reminders fire while the page is open.** This is a local-only app with no
  push server, so the tab (or installed PWA) needs to stay alive to alert you.
  When you return to the tab, it immediately reconciles any doses that came due
  while you were away.
- Data is stored in `localStorage` under the key `nextDose.meds.v1`. Clearing
  site data resets the app.
- No network calls, no tracking, no dependencies.

## Files

- `index.html` — the entire app (markup, styles, and logic).
