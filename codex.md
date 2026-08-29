# Task: the title artwork in the light theme

> The rules of collaboration — worktree, branches, conventions, who merges — are in
> `codex_instructions.md`. Read that first. This file describes only the current task.
>
> Code and git are written in English, commit messages included. Documents under `docs/` stay Polish.

**Branch:** `fix/artwork-light-theme` from `develop`.

**Files this task covers:**
- `ui/home/RotatingBoardArt.kt` — where the scene picks its colours
- `ui/theme/` — only if the fix belongs there rather than in the scene

I am not writing code while you do this, so the area is yours. Note that `ui/home/` also holds the
wordmark and the motion; **the geometry and the timing are not part of this task** and should come
out unchanged.

---

## The report

From the owner, on a device: the artwork looks good in the dark theme, bad in the light one, and
**worse again in high contrast**.

## The cause, which is already found

The scene lightens all three of its colours **towards white**:

```kotlin
val digitColor = lerp(colors.enteredDigit, Color.White, DIGIT_COLOR_LIFT)   // 0.62
val gridColor  = lerp(colors.blockLine,    Color.White, GRID_COLOR_LIFT)    // 0.48
val boardColor = lerp(background,          Color.White, BOARD_BACKGROUND_LIFT) // 0.13
```

Against a dark background that moves them away from it and the scene glows. Against a light one it
does the opposite — it pushes everything **towards** the background and washes the contrast out. The
constant has one sign and the problem has two directions.

**That also explains why high contrast comes out worse rather than better.** That mode exists to widen
separation; a lift towards white pulls it back. The harder the mode works, the harder the scene works
against it.

## The direction, not the answer

Displace **from the background** rather than towards white: in a light theme towards black, in a dark
one towards white. One constant then means "how far from the background" and holds in both themes and
in high contrast, instead of meaning "how much whiter".

Whether that belongs in the scene or in `BoardColors` is yours to judge — you have just been through
those files for the palette work and are better placed than I am to say.

## What to be careful of

**Your own high-contrast work is in here.** You separated the palette tiers at distances visible on a
device; make sure a scene that now displaces from the background does not overshoot in that mode and
turn the artwork into hard poster colours.

**The three lift constants were tuned by eye against a dark theme.** Reused as displacement distances
they will not mean the same thing. Expect to re-choose them rather than reinterpret them.

**No machine can tell you whether this worked.** There is no assertion for "looks right"; the owner
judges it on a device, in both themes and in high contrast. Say plainly in the commit that you could
not check the appearance, and what you would want looked at.

## Verification

```bash
./scripts/test-kratkoza.sh
./scripts/lint-kratkoza.sh
```

Then build a debug APK and hand it over. The store screenshots regenerate on every test run, so a
change to the artwork's colours will show up as modified files in `store/screenshots/` — commit them
with the change rather than leaving the assets disagreeing with the app.
