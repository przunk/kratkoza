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
- [x] Skrypty budowania: debug, podpisany release, bundle AAB na Play
- [x] Minifikacja R8 z regułami chroniącymi format zapisu (25 MB → 2,3 MB)

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
- [x] Podpowiedź 8/9, opcjonalna, domyślnie OFF (§17)
- [x] Subtelne animacje wpisania cyfry, ukończenia wiersza / kolumny / bloku oraz błędu (§36)
- [x] Obracająca się grafika 3×3 na ekranie głównym
- [x] Timer, pauza ręczna i przy przejściu w tło, zasłonięcie planszy (§20)
- [x] Automatyczny zapis po każdym istotnym ruchu (§21)

### Dane
- [x] Zapisywanie i wznawianie rozgrywek, lista z podglądem planszy (§22)
- [x] Szybkie wznowienie ostatniej gry na ekranie głównym (§22)
- [x] Historia zakończonych gier w Room (§23)
- [x] Statystyki lokalne (§33)
- [x] XP i poziomy gracza — rosnący koszt kolejnych poziomów, bez kary za błędy (§32)
- [x] Ustawienia w DataStore (§35)
- [x] Motyw jasny / ciemny / systemowy (§9)
- [x] Wybór języka w aplikacji: systemowy / EN / PL (§4)
- [x] Reset statystyk — wszystkie liczniki do zera, gry w toku zostają grywalne

### Jakość
- [x] Logika domenowa testowalna bez Androida (§38)
- [x] 185 testów jednostkowych: walidacja, solver, unikalność, generator, klasyfikacja trudności,
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
- Osobisty score (§25) — kolumna w bazie jest, algorytm czeka na ustalenie (§39)
- Pełne podpowiedzi (§18)
- Palety kolorystyczne i tryb wysokiego kontrastu (§9, §35)
- Radial / 3×3 menu cyfr przy długim przytrzymaniu (§13, nice to have)
- Tablety i landscape (§2)

Architektura żadnej z nich nie blokuje — patrz [ARCHITECTURE.md](ARCHITECTURE.md) §5 i §8.

## Do zrobienia

Kod jest gotowy do wydania alpha. Zostało to, co poza kodem — patrz niżej.
Skrypt AAB jest gotowy — patrz [BUILD.md](BUILD.md).

Po stronie właściciela projektu, poza kodem: klucz upload (nie ma żadnego), konto Play,
polityka prywatności, formularz Data safety, opis i grafiki w konsoli.

### Drobne: licznik błędów nie stoi na środku ekranu

**Zgłoszone i odłożone:** 2026-08-26.

Pasek nad planszą rozkłada trzy elementy przez `Arrangement.SpaceBetween`: czas po lewej, błędy
w środku, pauza po prawej. SpaceBetween rozkłada *odstępy*, nie elementy — środkowy trafia na
środek tylko wtedy, gdy skrajne mają tę samą szerokość, a „Czas / 4:04" i przycisk pauzy jej nie
mają. Skakanie zostało już naprawione (przycisk ma stałą szerokość), zostało samo przesunięcie.

Poprawka to nadanie trzem slotom równych wag i wyśrodkowanie treści w środkowym; wtedy pozycja
przestaje zależeć od długości sąsiadów w jakimkolwiek języku.

### Podpowiedź 8/9 niedostępna w grach rankingowych

**Zgłoszone:** 2026-08-25. `project.md` §17 kończy się zdaniem: „Funkcja 8/9 jest niedostępna
w publicznie rankingowanych rozgrywkach Daily."

Dziś nie ma czego blokować — nie ma Daily ani rankingu — więc kodu celowo nie ruszono. Do zrobienia
**razem z Daily**, i to strukturalnie, nie przez pamiętanie: podpowiedź ma być liczona wyłącznie
gdy gra nie kwalifikuje się do rankingu. Pole `rankedEligible` istnieje już w save'ie i w bazie,
więc warunek ma jedno miejsce.

To samo dotyczy każdej przyszłej pomocy z §18 („Pokaż następną cyfrę").

### Palety kolorystyczne i tryb wysokiego kontrastu

**Zgłoszone:** 2026-08-25. `project.md` §9 i §35 wymagają wyboru palety oraz opcjonalnego trybu
wysokiego kontrastu; §39 zostawia *zestaw* palet do ustalenia.

Infrastruktura jest już gotowa i to nie przypadek: `BoardColors` siedzi celowo **poza**
`ColorScheme` Material 3, więc przemalowanie planszy i przemalowanie reszty UI to niezależne
decyzje. Do zrobienia: przełącznik palety w Ustawieniach, wartość w DataStore i same palety.

Tryb wysokiego kontrastu to ta sama warstwa i naturalne miejsce na jedną konkretną decyzję:
podświetlanie takich samych cyfr działa dziś przez **kolor cyfry**, a barwy niebieskiej i morskiej
część osób nie odróżni. W trybie wysokiego kontrastu powinno wracać na **tło**, które czyta się
jasnością.


### Wynik rankingowy Daily

**Do zrobienia razem z Daily.** `project.md` §29 opisuje karę za błędy wprost:
`wynik × 0.7^liczba_błędów` — trzy błędy to już 34% wyniku. Wzór na sam wynik bazowy zależny od
czasu jest w §39 otwarty.

To druga połowa decyzji podjętej przy XP. Warto trzymać obie obok siebie, bo łatwo je pomylić:

| | XP (§32) — **zrobione** | Wynik rankingowy (§29) — **do zrobienia** |
|---|---|---|
| za co | granie i ukończenie partii | konkretna plansza Daily |
| błędy | **nie obniżają** | obniżają mocno: `× 0.7^błędy` |
| po co | postęp gracza, odznaka | uczciwe porównanie na tej samej planszy |

Kolumna `score` w `game_history` czeka pusta.

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
