# Task: the palettes look low-contrast

> The rules of collaboration — worktree, branches, conventions, who merges — are in
> `codex_instructions.md`. Read that first. This file describes only the current task.
>
> Note the rule that changed on 2026-08-28: **code and git are written in English now**, commit
> messages included. Documents under `docs/` stay Polish.

**Branch:** `fix/palette-contrast` from `develop`.

**Files this task covers:**
- `ui/theme/Palettes.kt`, `ui/theme/Color.kt`, `ui/theme/BoardColors.kt`, `ui/theme/Theme.kt`
- `app/src/test/kotlin/.../ui/theme/PalettesTest.kt`

I am working on the navigation back-stack defect at the same time — `navigation/` and the screens'
back handling. **Stay out of those.** Our areas do not otherwise touch.

---

## The report

From the owner, on a device: the colours themselves are fine. **The contrast looks bad.**

That is worth reading carefully, because it rules out the obvious answer. This is not a request for
different hues. It is that the board does not separate into legible layers when you look at it.

## Where the rule is already written down, and where it is too weak

`PalettesTest` holds five rules for every palette in both themes. They pass. That they pass while
the board looks flat is the interesting part, and two gaps explain it.

**The tiers are only ordered, not spaced.** The test asserts `sameDigit > peer` and
`selected > sameDigit` — measured as distance in CIE L\* from the surface. Ordering says nothing
about how far apart they are. Three tiers at 5.1, 5.2 and 5.3 satisfy it completely and are
indistinguishable to a human eye.

**Only the faintest tier is checked against the surface**, and `MINIMUM_TIER_SEPARATION` is `5f` —
five units on a scale that runs 0 to 100. Digits get `MINIMUM_DIGIT_SEPARATION = 25f`, which is a
different order of magnitude. The tier threshold was set to be *a* floor, not a good one.

So the likely shape of the defect: highlight tiers technically distinct, practically one wash.

## What to do

Strengthen the rules first, then move the palettes until they pass. Doing it the other way round
produces colours tuned to a device and a mood, and the next palette repeats the same mistake.

At minimum the rules should gain a **minimum gap between adjacent tiers**, not only against the
surface. Whether the surface floor of 5 is also too low is yours to judge — but if you raise it,
raise it because you can say what the number means, not to make a specific palette pass.

**Beware of the ceiling.** `project.md` §10 says same-digit highlighting must not make it harder to
read the current cell, the row, the column, the block or mistakes. Contrast pushed too far breaks
that from the other side, and no test will tell you — this one needs eyes.

**High contrast mode has its own rule** (`high contrast never reduces separation`) and an open item
in `docs/STATUS.md` about refining it. If your change makes the normal mode close to what high
contrast used to give, say so in the commit; that changes what that mode is still for.

## Verification

```bash
./scripts/test-kratkoza.sh
./scripts/lint-kratkoza.sh
```

Build a debug APK and look at all three palettes in both themes before you call it done. Say in the
commit what you could not check by machine — and expect the owner to judge the result on a device,
because the report that started this was visual and so is the answer.
