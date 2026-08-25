# Kratkoza — stan realizacji

Stan na koniec pierwszego etapu: fundament projektu i w pełni działający tryb Sudoku solo.

## Gotowe

### Projekt
- [x] Projekt Android, Kotlin, Jetpack Compose, Material 3 (`project.md` §3)
- [x] `minSdk 29`, `targetSdk 36`, `compileSdk 37`, portrait only (§2)
- [x] Jeden moduł `:app`, podział przez pakiety (`technical_requirements.md` §10)
- [x] Ręczne DI przez `AppContainer` (§8)
- [x] Zero bibliotek spoza Androida / Kotlina / Jetpacka / Material (§9)
- [x] i18n polski + angielski od pierwszego commita (§12)
- [x] Skrypty budowania debug i podpisanego release

### Logika Sudoku
- [x] Model planszy, walidator reguł (§5)
- [x] Solver mechaniczny z propagacją ograniczeń
- [x] Sprawdzanie unikalności rozwiązania (§5)
- [x] Solver logiczny — dziesięć technik ludzkich
- [x] Generator plansz, deterministyczny z ziarna
- [x] Klasyfikacja trudności po wymaganych technikach (§5, §7)
- [x] Trwałe `puzzleId` z treści planszy (§6)

### Rozgrywka
- [x] Wyświetlanie planszy, wyraźne granice bloków 3×3 (§8)
- [x] Zaznaczanie pola z podświetleniem wiersza, kolumny i bloku (§8)
- [x] Podświetlanie takich samych cyfr, opcjonalne (§10)
- [x] Panel cyfr 1–9 jako podstawowy input (§11)
- [x] Kandydaci (§14)
- [x] Automatyczne usuwanie kandydatów, domyślnie ON (§15)
- [x] Wykrywanie i oznaczanie błędów, bez systemu żyć (§16)
- [x] Undo obejmujące wszystkie rodzaje ruchów, bez cofania błędu (§12)
- [x] Wymazywanie (§11)
- [x] Licznik pozostałych cyfr, opcjonalny (§19)
- [x] Timer, pauza ręczna i przy przejściu w tło, zasłonięcie planszy (§20)
- [x] Automatyczny zapis po każdym istotnym ruchu (§21)

### Dane
- [x] Zapisywanie i wznawianie rozgrywek, lista z podglądem planszy (§22)
- [x] Szybkie wznowienie ostatniej gry na ekranie głównym (§22)
- [x] Historia zakończonych gier w Room (§23)
- [x] Statystyki lokalne (§33)
- [x] Ustawienia w DataStore (§35)
- [x] Motyw jasny / ciemny / systemowy (§9)
- [x] Wybór języka w aplikacji: systemowy / EN / PL (§4)
- [x] Reset statystyk (gry w toku liczą się dalej)

### Jakość
- [x] Logika domenowa testowalna bez Androida (§38)
- [x] 151 testów jednostkowych: walidacja, solver, unikalność, generator, klasyfikacja trudności,
      kandydaci, Undo, wykrywanie błędów, zapis/odtworzenie sesji, repozytorium, ViewModele
- [x] Android Lint czysty przy `abortOnError` i `warningsAsErrors`

## Czego *nie* zweryfikowano

Aplikacja nie została uruchomiona na urządzeniu ani emulatorze — w tym środowisku WSL nie ma
emulatora, a `.tooling/android-sdk` nie zawiera obrazów systemu. Zweryfikowane są: kompilacja,
lint, testy jednostkowe całej logiki i obu ViewModeli oraz zbudowanie podpisanego APK.
Zachowanie samego UI na ekranie — układ, czytelność planszy, dotyk — wymaga wgrania APK
i sprawdzenia ręcznie.

Testy instrumentacyjne Compose (`technical_requirements.md` §15) też czekają na urządzenie.

## Świadomie poza pierwszym etapem

Wymagania oznaczone w `project.md` jako „do ustalenia", „później" albo „nice to have", oraz
funkcje wyłączone z zakresu etapu:

- Daily Sudoku (§26), publiczny ranking (§27–§29), Challenge (§30), backend
- XP i osobisty score (§25, §32) — kolumny w bazie są, algorytmy czekają na ustalenie (§39)
- Podpowiedź 8/9 (§17), pełne podpowiedzi (§18)
- Palety kolorystyczne i tryb wysokiego kontrastu (§9, §35)
- Radial / 3×3 menu cyfr przy długim przytrzymaniu (§13, nice to have)
- Animacje wpisania cyfry i ukończenia wiersza / kolumny / bloku (§36)
- Tablety i landscape (§2)

Architektura żadnej z nich nie blokuje — patrz [ARCHITECTURE.md](ARCHITECTURE.md) §5 i §8.

## Wymaga decyzji

Sześć rzeczy, których `project.md` świadomie nie rozstrzyga. Pełny opis wariantów w
[OPEN_QUESTIONS.md](OPEN_QUESTIONS.md); poniżej sam stan.

Ustalone 2026-08-25 — kod jest zgodny, wracamy tylko jeśli coś się zmieni:

- auto-usuwanie kandydatów **nie** rusza notatek sąsiadów po błędnej cyfrze
- ponowne wpisanie tej samej cyfry w to samo pole **nie robi nic**
- „portrait only" zostaje jak jest, mimo że Android 16 ignoruje je na ekranach ≥ 600dp

Czeka na granie na urządzeniu:

- **podział 5 poziomów trudności** — czy MASTER jest realnie wystarczająco trudny (§7)
- **zakres przycisku „Wymaż"** — na razie kasuje cyfrę i kandydatów naraz
- **domyślne ON** dla podświetlania takich samych cyfr (§10) i licznika pozostałych cyfr (§19)
