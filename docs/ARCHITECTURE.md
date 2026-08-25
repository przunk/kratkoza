# Kratkoza — architektura

Mapa kodu i uzasadnienia decyzji, których nie widać z samych plików.
Nadrzędne są `project.md` i `technical_requirements.md`; ten dokument tylko opisuje, jak zostały
zrealizowane.

---

## 1. Przepływ

```
Compose UI  ->  ViewModel  ->  Repository  ->  Room / DataStore
                    |
                    +-------->  Domain (czysty Kotlin, zero Androida)
```

Jednokierunkowy przepływ danych: ekran wystawia zdarzenia, ViewModel produkuje niezmienny stan,
UI go rysuje. Stan udostępniany jest jako `StateFlow`.

Jedna `Activity` (`MainActivity`), ekrany to cele Navigation Compose.

## 2. Pakiety

```
com.przunk.kratkoza/
  data/
    local/        Room: encje, DAO, baza, kodek historii ruchów
    repository/   GameRepository, SettingsRepository
  di/             AppContainer — ręczne DI
  domain/
    game/         stan gry, silnik ruchów, kandydaci, zapisane gry
    model/        Difficulty, GameType, Puzzle
    settings/     GameSettings, ThemeMode
    stats/        Statistics
    sudoku/       plansza, walidator, solver, solver logiczny, generator, ocena trudności
  navigation/     cele i NavHost
  ui/
    common/       wspólny scaffold, etykiety poziomów
    game/         ekran gry, plansza, panel cyfr, ViewModel
    home/ newgame/ saves/ settings/ statistics/
  util/           formatowanie czasu i daty
```

Jeden moduł Gradle `:app`, podział przez pakiety — `technical_requirements.md` §10.

## 3. Warstwa domenowa

Cała w czystym Kotlinie. Nie importuje niczego z Androida, więc testuje się bez emulatora
i bez Robolectrica.

| Klasa | Odpowiedzialność |
|---|---|
| `Sudoku` | geometria 9×9, prekalkulowane wiersze / kolumny / bloki / sąsiedzi |
| `SudokuBoard` | niezmienna plansza 81 pól + format zapisu |
| `SudokuValidator` | spójność z regułami, konflikty, dozwolone cyfry |
| `SudokuSolver` | backtracking + propagacja; liczenie rozwiązań z limitem |
| `LogicalSolver` | dziesięć technik ludzkich, zawsze od najtańszej |
| `DifficultyEvaluator` | ocena planszy → poziom |
| `SudokuGenerator` | pełne rozwiązanie → wykopywanie podpowiedzi |
| `GameState` / `GameEngine` | stan rozgrywki i wszystkie ruchy gracza |
| `CandidateGrid` | notatki gracza, maski bitowe |

### Dlaczego niezmienność

`SudokuBoard` i `CandidateGrid` są niezmienne. Dzięki temu Undo przechowuje stan sprzed ruchu
zamiast odtwarzać go z reguł, a automatyczny zapis może wziąć migawkę bez ryzyka, że zmieni się
pod nim w trakcie zapisu.

### Solver mechaniczny vs logiczny

To dwa różne pytania i dwie różne klasy:

- `SudokuSolver` — *czy rozwiązanie istnieje i czy jest jedyne*. Szybki, zgaduje.
- `LogicalSolver` — *czy człowiek to rozwiąże i jak trudno*. Nigdy nie zgaduje.

Generator potrzebuje obu: pierwszego przy każdej usuwanej podpowiedzi (unikalność), drugiego przy
ocenie trudności.

## 4. Trudność

Poziom wynika z najtrudniejszej techniki, jakiej plansza *wymaga* — `project.md` §5.
`EASY` i `NORMAL` dzieli dodatkowo próg liczby podpowiedzi.

Uzasadnienie, pomiary i warianty do dalszej decyzji: [OPEN_QUESTIONS.md](OPEN_QUESTIONS.md) §1.

## 5. Trwałość

Room trzyma trzy tabele:

- `puzzles` — plansza raz zapisana, wskazywana przez resztę
- `saved_games` — jedna gra w toku na planszę
- `game_history` — jeden wiersz na rozegraną grę

Wiersz historii powstaje przy **starcie** gry. Inaczej `project.md` §33 („liczba rozpoczętych gier",
„procent ukończeń") nie ma czego liczyć. Ukończenie zamyka wiersz i kasuje save; porzucenie kasuje
save i zostawia wiersz otwarty.

Statystyki są liczone z historii przy każdym odczycie, nie trzymane jako sumy bieżące — `project.md`
§23 chce, żeby dało się je przeliczyć później, a rozjechanej sumy nie da się naprawić.

Schemat v1 zawiera już kolumny dla Daily i rankingu (`gameType`, `rankedEligible`, `score`,
`xpEarned`, `rulesetVersion`). Migracja nie umiałaby zgadnąć typu gry wiersza zapisanego przed
istnieniem kolumny.

DataStore trzyma ustawienia. Każda wartość ma default, więc brak klucza nie jest błędem, a dodanie
opcji nie wymaga migracji.

## 6. Zależności

Poza Androidem, Kotlinem, Jetpackiem i Material 3 projekt nie ma żadnej biblioteki
(`technical_requirements.md` §9). Miejsca, gdzie łatwo byłoby jedną dołożyć, i czym zostały
zastąpione:

| Zamiast | Jest | Dlaczego |
|---|---|---|
| JSON do historii Undo | `MoveHistoryCodec` | format to trzy liczby i mała mapa; aplikacja nie ma innego zastosowania dla JSON |
| Hilt / Koin | `AppContainer` | cztery obiekty i jedno spięcie, widoczne w dwudziestu liniach |
| type-safe routes | zwykłe stringi | wymuszałyby kotlinx-serialization dla jednego argumentu |
| material-icons-extended | glif tekstowy w przycisku wstecz | jedna strzałka nie jest warta całego pakietu ikon |

## 7. Dostępność

- Każde pole planszy to osobny cel dotykowy z własnym opisem dla TalkBacka
  („wiersz 3, kolumna 5, 7, cyfra startowa").
- Stan nigdy nie jest komunikowany wyłącznie kolorem (`project.md` §8): cyfra startowa ma inną
  grubość, błąd ma podkreślenie, tryb kandydatów ma inny napis na przycisku.
- Kolory planszy żyją w `BoardColors`, poza `ColorScheme` Material 3 — przemalowanie planszy i reszty
  UI to niezależne decyzje.
- Tekst na planszy skaluje się od rozmiaru pola, nie od systemowego rozmiaru czcionki. `project.md`
  §8 prosi o respektowanie powiększonej czcionki „tam, gdzie nie niszczy geometrii planszy" —
  wewnątrz 1/9 szerokości ekranu niszczy.

## 8. Czego jeszcze nie ma

Zgodnie z zakresem pierwszego etapu: Daily, publiczny ranking, Challenge, backend, XP, score,
podpowiedź 8/9, palety kolorystyczne, tryb wysokiego kontrastu, radial menu.

Architektura ich nie blokuje — patrz kolumny bazy w §5 i `GameType` w domenie.
