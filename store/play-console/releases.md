# Release evidence

One section per artifact handed to Google Play. Ties each published `versionCode` back to the exact source commit and tag. Record a build here when uploading it.

---

## 0.32.0 — versionCode 51

Closed testing release. First release prepared for Google Play Closed Testing track.

| Property | Value |
| --- | --- |
| Artifact | `dist/kratkoza-0.32.0-51.aab` |
| Size | 4,422,307 bytes (4.3M) |
| SHA-256 | `31572b8ae6700f7813010d565af0932c0142ed092cf5f1c60c2ea7fe02937df2` |
| versionCode | 51 |
| versionName | 0.32.0 |
| Commit | `2411c62` |
| Tag | `v0.32.0` |
| Target SDK | 36 (Android 16) |
| Min SDK | 29 (Android 10) |
| Built | 2026-09-18 |

**Signer** (upload key, printed by `build-kratkoza-bundle.sh` from the bundle itself):

```
Owner:   CN=Przunk, OU=Przunk, O=Przunk, L=Wejherowo, ST=Pomorskie, C=PL
Valid:   2026-08-25 to 2054-01-10
SHA-256: 71:5C:8F:D5:BE:65:8F:9C:02:DE:2F:B3:1C:EB:B4:BF:38:CA:42:E6:F3:82:96:BA:CF:E1:85:52:8E:B8:46:4E
Algorithm: SHA256withRSA
```

### What the release contains

- **Pure 9×9 Sudoku logic**: 5 difficulty levels (Easy, Normal, Hard, Expert, Master) graded by logical human techniques.
- **Daily Sudoku**: Deterministic daily puzzle across 3 levels with Play Games Services v2 leaderboards.
- **Diagnostics**: Play Games sign-in diagnostics in Settings to verify authentication state.
- **Comfort**: Candidate pencil marks with auto-removal, full Undo, error detection, pause, auto-save.
- **Stats & Progression**: Detailed local statistics, player XP and levels, solved board archive.
- **Visuals**: Light, Dark, System themes, 4 custom palettes, dedicated High Contrast mode.
- **i18n**: English and Polish, switchable in-app.
- **Zero ads, 100% offline-first**.
