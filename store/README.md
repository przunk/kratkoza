# Google Play Store Package

Prepared 2026-09-18 for Kratkoza v0.32.0 (versionCode 51).

This directory contains copy-ready text, visual assets, and verification documentation for the Google Play Store.

## Contents

| Path | Purpose |
| --- | --- |
| `listing/en-US/` | English title, short/full descriptions, and release notes |
| `listing/pl-PL/` | Polish title, short/full descriptions, and release notes |
| `graphics/` | 512×512 icon, 1024×500 feature graphic, and Play-compliant screenshots |
| `privacy-policy.md` | Bilingual (EN/PL) privacy policy ready for static web hosting |
| `play-console/data-safety.md` | Data safety form declaration answers |
| `play-console/app-content.md` | Policy & programs → App content answers (ratings, category, audience) |
| `play-console/release-checklist.md` | Step-by-step checklist for release hand-off |
| `play-console/releases.md` | Release evidence tying artifacts and tags to exact commits |

## Validation

Run store metadata validation to verify Google Play character limits and locale file symmetry:

```bash
./scripts/check-store-metadata.sh
```
