# Localization template

Play Console supports per-locale overrides for store listing copy. English (en-US) is the fallback when a locale has no translation.

## Adding a new locale

1. Copy the `_localization-template/` folder and rename it to the target Play Console locale code. Common ones:
   - `es-419` — Spanish (Latin America)
   - `es-ES` — Spanish (Spain)
   - `de-DE` — German
   - `fr-FR` — French
   - `pt-BR` — Portuguese (Brazil)
   - `ja-JP` — Japanese
   - `zh-CN` — Chinese (simplified)
   - `zh-TW` — Chinese (traditional)
2. Translate each file. Preserve the character limits noted in each `<!-- TRANSLATE: ... -->` comment.
3. Preserve the release-notes structure — they are per-locale on Play Console too.
4. Paste contents into the matching Play Console fields under Store Listing → Custom listing → Locale.

## Character limits (enforced by Play Console)

| File | Limit |
|---|---|
| `app-title.txt` | 30 characters |
| `short-description.txt` | 80 characters |
| `full-description.txt` | 4000 characters |
| `release-notes/*.txt` | 500 characters per locale per release |

## Tone guidance for translators

PrismTask copy is deliberately plain and slightly understated — no superlatives ("best", "#1"), no emojis in body text, no marketing fluff. Prefer short sentences. Translate meaning, not words. If the English line sounds dry, the translation should sound dry too.
