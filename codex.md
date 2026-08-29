# Task: clear every candidate at once

> The rules of collaboration — worktree, branches, conventions, who merges — are in
> `codex_instructions.md`. Read that first. This file describes only the current task.
>
> Code and git are written in English, commit messages included. Documents under `docs/` stay Polish.

**Branch:** `feature/clear-candidates` from `develop`.

**Files this task covers:**
- `domain/game/` — the operation and how it is undone
- `ui/game/` — the control and its confirmation
- `res/values*/strings.xml`
- tests: `domain/game/`, `ui/game/`

I am working on the title artwork's colours and on rendering screenshots from tests — `ui/home/`,
`ui/theme/` and test infrastructure. **Stay out of those.**

---

## What is wanted

An action on the game screen that removes every candidate from the whole board.

This is `docs/WISHLIST.md` item 5, not a requirement from `project.md` — so it exists because the
owner asked for it, and the two conditions he attached are the point of the task rather than
decoration:

- **it must ask for confirmation**, and
- **it must undo as a single action**, so a mistaken tap does not cost a player their notes.

## The undo machinery already does the hard part

`Move.previousCandidates` is a `Map<Int, Set<Int>>` — cell to the candidates it held before. One move
can therefore carry the whole board's marks, and `GameEngine.undo` already walks that map and puts
them back. Clearing everything is one `Move` with a full map, not a new kind of undo.

Look at how `enterDigit` builds its move through `changedCandidates` before writing anything of your
own; the shape you need is probably already there.

## What not to invent

**This costs nothing in the ranking.** `project.md` §18 says plainly that candidates are not a hint.
Clearing your own notes makes the board harder, not easier, so it must not touch `rankedEligible`.

**The confirmation already has a pattern.** Statistics reset asks before it destroys — reuse that
shape rather than inventing a second kind of dialogue.

**An empty board must not offer the action.** If there are no candidates to clear, the control
belongs absent or disabled, not live and silent — `ARCHITECTURE.md` §9. The number pad and the eraser
were fixed for exactly this a day ago; look at `GameUiState.canEnterDigit` for how that reads.

## On where the action goes

The actions row is already carrying undo, the eraser and the candidate toggle, and it has just gained
a reveal. Adding a fifth control to that row may be the wrong answer — say what you chose and why. If
it crowds, that is worth reporting rather than shipping tight.

## Verification

```bash
./scripts/test-kratkoza.sh
./scripts/lint-kratkoza.sh
```

Test the two conditions, not only the clearing: that a single undo restores every cell's marks
exactly, and that the action cannot be reached without confirming. Say in the commit what you could
not check by machine.
