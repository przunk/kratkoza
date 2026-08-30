# Task: archive of solved Solo boards

> Follow `codex_instructions.md`. This task was refined and approved by the owner on 2026-08-30.

**Branch:** `feature/solved-board-history`, rebased onto the current assumption-tools work so the
two independently developed Room changes have a single migration sequence.

## Product contract

- Archive every successfully completed `SOLO` game, including generated and imported/shared boards.
- Never archive Daily games or unfinished attempts.
- Keep one archive entry per successful completion. Replaying and completing the same `puzzleId`
  creates another entry, and the UI reports how many times that board has been solved.
- A statistics reset must preserve the archive. Archived entries are a read-only record and must not
  affect statistics, XP, Daily eligibility, scores, or public rankings.
- Show the initial board (`givens`), not the filled result, in the thumbnail and detail preview.
- A history row shows the thumbnail, difficulty, completion date/time, elapsed time, mistakes, XP,
  and completion count for that unique `puzzleId`.
- Offer quick Share directly from the list.
- Tapping a row opens a dedicated detail/preview screen.
- The detail screen offers Share and an icon-led "Solve again" action. If the same puzzle already
  has a save, the action becomes "Continue" and resumes it instead of replacing progress.
- Open History from an icon-led button at the top of Statistics. Do not change the Home menu or the
  placement/styling of Daily in this task.
- Prefer icon plus short caption for actions. Do not introduce text-only action buttons where an
  established icon-led pattern fits.

## Storage design

- Add a dedicated Room archive table with its own generated completion id and a foreign key to
  `puzzles`.
- Store immutable completion metadata: `puzzleId`, completion timestamp, elapsed time, mistakes,
  XP, and optional score for forward compatibility.
- Keep assumption tools as schema 5, then add a 5→6 migration that creates the table and backfills
  existing completed `SOLO` rows from `game_history` whose `finishedAt` is present.
- Statistics reset continues to clear `game_history`; it never touches the archive table.
- Archive insertion happens only on a successful Solo completion.

## Files in scope

- `codex.md`
- `app/src/main/kotlin/com/przunk/kratkoza/data/local/`
- `app/src/main/kotlin/com/przunk/kratkoza/data/repository/GameRepository.kt`
- `app/src/main/kotlin/com/przunk/kratkoza/di/AppContainer.kt`
- `app/src/main/kotlin/com/przunk/kratkoza/domain/game/`
- `app/src/main/kotlin/com/przunk/kratkoza/navigation/`
- `app/src/main/kotlin/com/przunk/kratkoza/ui/history/` (new)
- `app/src/main/kotlin/com/przunk/kratkoza/ui/statistics/`
- `app/src/main/res/drawable/` for local vector icons
- `app/src/main/res/values*/strings.xml`
- focused tests and exported Room schemas required by the change

## Verification

- Focused domain/repository/DAO/migration/ViewModel/Compose tests while implementing.
- `scripts/test-kratkoza.sh`
- `scripts/lint-kratkoza.sh`
- `scripts/build-kratkoza-release.sh`
- Copy the final APK to `/mnt/workspace/Kratkoza/dist/`.
- Device verification remains the owner's responsibility; explicitly call out the History list,
  detail layout, icons, Share chooser, and Solve again/Continue behavior for manual review.
