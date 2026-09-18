# Data safety declaration

Prepared 2026-09-18 for Kratkoza v0.32.0. The developer is responsible for the final form in Play
Console; this document records the factual evidence and recommended conservative answers.

## Recommended top-level answer

**Does your app collect or share any of the required user data types? — Yes.**

While Kratkoza operates entirely offline by default with no advertising or telemetry SDKs, it offers
optional integration with **Google Play Games Services v2** for Daily Sudoku public leaderboards.
When a user explicitly chooses to sign in to Google Play Games, Google Play Games SDK transmits
the user's gamer account ID and scores to Google servers. Under Google Play definitions, off-device
transmission via an SDK counts as data collection.

If Google Play Games were completely removed, the answer would be "No".

## Declared data types

### User info → User IDs

| Question | Answer | Evidence |
| --- | --- | --- |
| Collected | **Yes** | Transmitted by Google Play Games Services SDK upon sign-in |
| Shared | **No** | Not shared with third-party advertisers or external data brokers |
| Processed ephemerally | **No** | Retained by Google Play Games for persistent leaderboard rankings |
| Required or optional | **Optional** | Core gameplay, generation, and statistics work completely offline |
| Purpose | **App functionality** | Identifying the player on public Daily Sudoku leaderboards |
| Encryption in transit | **Yes** | Google Play Games Services communicates strictly over HTTPS |

## Transfers that do not add a declared data type

- Puzzles, game history, candidate notes, and statistics are stored locally in Room database on the device.
- User preferences (theme, language, error assists) are stored locally in DataStore.
- **Share puzzle**: Uses Android's system share sheet to send a raw puzzle code string directly to another app chosen by the user.
- **Android Backup**: System backup of app data handled by the OS under the device owner's Google account settings.

## Security and deletion answers

- **Is collected data encrypted in transit? — Yes.**
- **Can users request deletion? — Not applicable to first-party records.** The developer holds no
  user database or server accounts. Play Games data is managed through Google Play Games / Google Account.
- **Does the app provide account creation? — No.** (No custom in-app account system).
- **Independent security review? — No.**

## Shipped SDKs inspected

- AndroidX libraries (Room, Compose, DataStore, Lifecycle, Navigation)
- `com.google.android.gms:play-services-games-v2`
- Kotlin coroutines
- **No** Firebase, Google Analytics, Facebook SDK, AdMob, Unity, or crash-reporting libraries.
