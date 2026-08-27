# Zadanie: statystyki Daily

> Zasady współpracy — worktree, gałęzie, konwencje, kto scala — są w `codex_instructions.md`.
> Przeczytaj je najpierw. Ten plik opisuje wyłącznie bieżące zadanie.

**Gałąź:** `feature/daily-statistics` z `develop`.

**Pliki, których dotyczy zadanie:**
- `data/local/Daos.kt` — nowe zapytania
- `domain/stats/Statistics.kt` — model
- `data/repository/GameRepository.kt` — złożenie
- `ui/statistics/` — ekran i ViewModel
- `res/values*/strings.xml` — sekcja „Statystyki"
- testy: `data/`, `data/local/`, `ui/statistics/`

Równolegle piszę testy Compose dla ekranów gry, Daily i ekranu głównego — dotykam
`app/src/test/kotlin/.../ui/{game,daily,home}/`. **Tam nie wchodź.** Poza tym nasze zakresy się nie
przecinają.

---

## Dlaczego to zadanie

`project.md` §33 wymienia **statystyki Daily** wśród wymaganych statystyk lokalnych i jest to
jedyna pozycja z tej listy, której nie ma. Daily działa od wczoraj, więc dane już się zbierają —
tylko nikt ich nie pokazuje.

## Co ma być widać

Sekcja „Daily" na ekranie statystyk, obok tego, co już tam jest:

- **ile Daily ukończono** — łącznie i z podziałem na poziomy,
- **suma zdobytych punktów** oraz **najlepszy pojedynczy wynik**,
- **średni wynik** na ukończone Daily,
- **ile Daily rozegrano bez utraty kwalifikacji rankingowej** — kolumna `rankedEligible`
  w `game_history` już to niesie.

Sekcja pojawia się **tylko wtedy, gdy jest co pokazać**. Zero wierszy z zerami na koncie kogoś, kto
nie grał jeszcze w Daily — reszta ekranu robi to samo i warto zachować spójność.

## Skąd brać dane

`game_history` ma wszystko: `gameType = 'DAILY'`, `completed`, `score`, `rankedEligible`,
`difficulty`. Wynik jest liczony przy kończeniu gry przez `DailyScore` i zapisywany — **nie licz go
ponownie w zapytaniu ani w UI.** Gdyby wzór się zmienił, wiersze historii mają dalej mówić, ile
partia była warta wtedy; to ta sama zasada, dla której istnieje `rulesetVersion`.

`score` jest `NULL` dla gier solo i dla nieukończonych. `NULL` to „nie punktowana", a nie zero —
`AVG` i `SUM` same je pomijają, ale `COUNT(*)` już nie, więc uważaj, co liczysz.

## Na co uważać

- **Zapytania agregujące pisz z testem na prawdziwej bazie**, nie tylko na atrapie. `FakeDaos`
  reimplementuje SQL w Kotlinie i tak właśnie przeszedł kiedyś zepsuty reset statystyk — sprawdzał
  imitację, nie kod. Wzorzec jest w `GameHistoryDaoTest`.
- **Reset statystyk musi czyścić też te liczby.** Skoro liczysz z `game_history`, dostaniesz to za
  darmo — ale sprawdź testem, bo to jest dokładnie ta rzecz, którą łatwo przeoczyć.
- Liczby formatuj przez `NumberFormat`, jak reszta ekranu (`project.md` §4).
- Puste wartości pokazuj jako „—", nie jako zero. „Brak najlepszego wyniku" i „najlepszy wynik
  zero" to dwa różne zdania.
- Dopisz testy Compose dla nowej sekcji — infrastruktura działa, a Twoje testy z poprzedniego
  zadania są dobrym wzorem.

## Weryfikacja

```bash
./scripts/test-kratkoza.sh
./scripts/lint-kratkoza.sh
```

Wersji nie podbijaj, jeśli nie scalasz. W opisie commita napisz, czego **nie** udało się sprawdzić
maszynowo.
