# Task: show the next correct digit

> The rules of collaboration — worktree, branches, conventions, who merges — are in
> `codex_instructions.md`. Read that first. This file describes only the current task.
>
> Code and git are written in English, commit messages included. Documents under `docs/` stay Polish.

**Branch:** `feature/reveal-digit` from `develop`.

**Files this task covers:**
- `domain/game/` — the reveal itself and what it costs
- `ui/game/` — the control and the state behind it
- `data/repository/GameRepository.kt` — only if a revealed cell has to be recorded
- `res/values*/strings.xml`
- tests: `domain/game/`, `ui/game/`

I am working on the title artwork's colours at the same time — `ui/home/` and `ui/theme/`. **Stay out
of those.**

---

## What the specification decides, and what it leaves open

`project.md` §18 describes this feature but marks it **"do późniejszego ustalenia"**. The owner has
now asked for it, so it is being built — but read §18 before anything else, because three things are
already settled there and are not yours or mine to reopen:

- it **may** be available in solo games,
- it **must not** be usable in an active game that still qualifies for the public ranking,
- using it **must** mark the game as non-ranked, where that case can arise.

Note what that rules out: **reducing the score is not an option.** Eligibility is a yes or no in this
codebase and in the specification; a hint does not make a ranked game worth fewer points, it makes it
not a ranked game.

## How the app already says "this costs you the ranking"

Two patterns exist and you should follow them rather than invent a third.

**A control that would break the rule is absent, not disabled.** The pause button is missing entirely
while a Daily still counts — see `GameStatusRow` and the comment there. A greyed-out control invites
the player to wonder what they did wrong.

**Losing eligibility is a one-way step with a visible consequence.** `GameViewModel.loseRanking`
already exists, banks the elapsed time and flips `rankedEligible`; the game screen shows a message
when it happens, and the Daily card says whether a board still counts.

So the shape is: in a Daily that still counts, no reveal control at all. In a solo game, and in a
Daily that has already lost its place, the control is there.

## What the specification does *not* decide — do not invent these

Ask before building, or build the simplest version and say plainly in the commit that you chose it:

- **How many reveals.** Unlimited, or a budget per game.
- **What it reveals.** The digit for the selected cell, or a cell the app picks.
- **What it costs in a solo game.** Nothing, or XP, or a mark in the statistics.

My own reading is that the smallest useful version is: reveals the selected empty cell, unlimited,
costs nothing in solo. It is the least that can be judged on a device, and every restriction is
easier to add later than to take away. But it is the owner's call, not mine.

## On what a revealed digit *is*

Worth settling before you write it: a revealed digit is correct by construction, so it must not be
enterable as a mistake, must not be undoable into a wrong state, and must not count as the player's
own entry anywhere the statistics distinguish those. `GameEngine` already treats a correct digit as
locked — check whether a reveal can simply go through the same path rather than a new one.

Note also that **candidates are explicitly not a hint** (§18, last line). Whatever you build must not
change how candidates behave.

## Verification

```bash
./scripts/test-kratkoza.sh
./scripts/lint-kratkoza.sh
```

Test the rule, not only the mechanism: a Daily that still counts must have no way to reach a reveal,
and a reveal must leave the game non-ranked wherever it was reachable. Say in the commit what you
could not check by machine.
