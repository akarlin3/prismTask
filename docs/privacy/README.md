# Privacy Policy — Hosting Setup

This folder is the publishable privacy policy for PrismTask. The Play Console store-listing form requires a public privacy-policy URL, and this folder is shaped to be served by GitHub Pages from `/docs`.

The policy text lives at `index.md`. The expected URL once GitHub Pages is enabled:

```
https://akarlin3.github.io/prismTask/privacy/
```

## GitHub Pages enablement (one-time)

1. Go to [repo Settings → Pages](https://github.com/akarlin3/prismTask/settings/pages).
2. **Source:** "Deploy from a branch."
3. **Branch:** `main`.
4. **Folder:** `/docs`.
5. Click **Save**. GitHub Pages enables and the site becomes available at `https://akarlin3.github.io/prismTask/` within ~1 minute. The privacy policy lands at `/privacy/`.

The repo is public (verified via `gh repo view akarlin3/prismTask --json isPrivate` → `false`), so GitHub Pages is free — no Pro / Team tier required.

## Plugging into Play Console

Play Console → Store presence → Store listing → **Privacy policy**: paste `https://akarlin3.github.io/prismTask/privacy/` and save.

Play Console will validate the URL is reachable. If it is a 404 at the time of paste, GitHub Pages either hasn't finished building or the source / branch / folder is misconfigured — wait a minute, refresh the URL in a browser, then retry.

## Optional: custom domain

If you later want the policy under `privacy.prismtask.app`:

1. In GoDaddy (or wherever `prismtask.app` is registered), add a DNS CNAME from `privacy` to `akarlin3.github.io`.
2. Create a `CNAME` file at `docs/CNAME` containing the single line `privacy.prismtask.app`.
3. In repo Settings → Pages, set the custom domain to `privacy.prismtask.app` and enable "Enforce HTTPS" once the TLS cert provisions.

The custom-domain path is optional — the github.io URL is enough for Play Console.

## Jekyll config (`_config.yml`)

A minimal `_config.yml` lives in this folder for sane Jekyll defaults. If you also want a top-level Pages site (not just `/privacy/`), move `_config.yml` to `docs/_config.yml` so it covers the whole `/docs` tree. For privacy-only, this folder-local config is fine — GitHub Pages renders Markdown with the configured theme.

## Keeping the policy in sync with the Data Safety form

**Load-bearing invariant:** this privacy policy and `../store-listing/compliance/data-safety-form.md` must agree on what data is collected, shared, and retained. Phase 3 verification cross-checks them. When you change one, update the other in the same commit.
