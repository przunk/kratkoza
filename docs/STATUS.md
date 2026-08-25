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
- [x] Subtelne animacje wpisania cyfry i ukończenia wiersza / kolumny / bloku (§36)
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
- [x] Reset statystyk — wszystkie liczniki do zera, gry w toku zostają grywalne

### Jakość
- [x] Logika domenowa testowalna bez Androida (§38)
- [x] 165 testów jednostkowych: walidacja, solver, unikalność, generator, klasyfikacja trudności,
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
- Pełne podpowiedzi (§18)
- Palety kolorystyczne i tryb wysokiego kontrastu (§9, §35)
- Radial / 3×3 menu cyfr przy długim przytrzymaniu (§13, nice to have)
- Tablety i landscape (§2)

Architektura żadnej z nich nie blokuje — patrz [ARCHITECTURE.md](ARCHITECTURE.md) §5 i §8.

## Do zrobienia

Uzgodniona kolejność przed wydaniem alpha na Google Play: XP i poziomy → minifikacja.
Skrypt AAB jest gotowy — patrz [BUILD.md](BUILD.md).

Po stronie właściciela projektu, poza kodem: klucz upload (nie ma żadnego), konto Play,
polityka prywatności, formularz Data safety, opis i grafiki w konsoli.

### Podpowiedź 8/9 niedostępna w grach rankingowych

**Zgłoszone:** 2026-08-25. `project.md` §17 kończy się zdaniem: „Funkcja 8/9 jest niedostępna
w publicznie rankingowanych rozgrywkach Daily."

Dziś nie ma czego blokować — nie ma Daily ani rankingu — więc kodu celowo nie ruszono. Do zrobienia
**razem z Daily**, i to strukturalnie, nie przez pamiętanie: podpowiedź ma być liczona wyłącznie
gdy gra nie kwalifikuje się do rankingu. Pole `rankedEligible` istnieje już w save'ie i w bazie,
więc warunek ma jedno miejsce.

To samo dotyczy każdej przyszłej pomocy z §18 („Pokaż następną cyfrę").

### XP i poziomy gracza

**Zgłoszone:** 2026-08-25. `project.md` §32 wymaga XP zależnego co najmniej od poziomu trudności
i ukończenia gry; §39 zostawia otwarty dokładny wzór. Ustalona zasada: **im wyższy poziom gracza,
tym trudniej go podbić.**

Twarde ograniczenie z §32, którego nie wolno naruszyć: XP **nigdy** nie może blokować poziomów
trudności, kandydatów, ustawień ani normalnej gry. Poziom gracza jest odznaką, nie bramką.

**Ustalone 2026-08-25: XP i wynik rankingowy to dwie różne rzeczy i liczą się inaczej.**

| | XP (§32) | Wynik rankingowy (§29) |
|---|---|---|
| za co | granie i ukończenie partii | konkretna plansza Daily |
| błędy | **nie obniżają** | obniżają mocno: `wynik × 0.7^liczba_błędów` |
| po co | postęp gracza, odznaka | uczciwe porównanie na tej samej planszy |

Powód rozdzielenia: §32 zabrania sztucznego grindu, więc XP ma płynąć za samo granie. Kara za
zgadywanie należy do rankingu, gdzie §29 chce jej mocno — trzy błędy to już 34% wyniku.

Kolumny `xpEarned` i `score` w `game_history` czekają puste. Do ustalenia przed implementacją:
dokładna krzywa progów poziomów.

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


### Podpowiedź 8/9 niedostępna w grach rankingowych

**Zgłoszone:** 2026-08-25. `project.md` §17 kończy się zdaniem: „Funkcja 8/9 jest niedostępna
w publicznie rankingowanych rozgrywkach Daily."

Dziś nie ma czego blokować — nie ma Daily ani rankingu — więc kodu celowo nie ruszono. Do zrobienia
**razem z Daily**, i to strukturalnie, nie przez pamiętanie: podpowiedź ma być liczona wyłącznie
gdy gra nie kwalifikuje się do rankingu. Pole `rankedEligible` istnieje już w save'ie i w bazie,
więc warunek ma jedno miejsce.

To samo dotyczy każdej przyszłej pomocy z §18 („Pokaż następną cyfrę").

### XP i poziomy gracza

**Zgłoszone:** 2026-08-25. `project.md` §32 wymaga XP zależnego co najmniej od poziomu trudności
i ukończenia gry; §39 zostawia otwarty dokładny wzór. Ustalona zasada: **im wyższy poziom gracza,
tym trudniej go podbić.**

Twarde ograniczenie z §32, którego nie wolno naruszyć: XP **nigdy** nie może blokować poziomów
trudności, kandydatów, ustawień ani normalnej gry. Poziom gracza jest odznaką, nie bramką.

**Ustalone 2026-08-25: XP i wynik rankingowy to dwie różne rzeczy i liczą się inaczej.**

| | XP (§32) | Wynik rankingowy (§29) |
|---|---|---|
| za co | granie i ukończenie partii | konkretna plansza Daily |
| błędy | **nie obniżają** | obniżają mocno: `wynik × 0.7^liczba_błędów` |
| po co | postęp gracza, odznaka | uczciwe porównanie na tej samej planszy |

Powód rozdzielenia: §32 zabrania sztucznego grindu, więc XP ma płynąć za samo granie. Kara za
zgadywanie należy do rankingu, gdzie §29 chce jej mocno — trzy błędy to już 34% wyniku.

Kolumny `xpEarned` i `score` w `game_history` czekają puste. Do ustalenia przed implementacją:
dokładna krzywa progów poziomów.

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


### Skrypt budujący AAB na Google Play

**Zgłoszone:** 2026-08-25. Przygotowanie do wydania alpha.

Wzorzec: `/mnt/workspace/TaskFlow/scripts/build-play-bundle.sh`. Robi tam:
`:app:bundleRelease` → podpisanie kluczem upload przez `jarsigner` z JDK → artefakt
ze stemplem czasu do katalogu wyjściowego, z interaktywnym pytaniem o ścieżkę keystore
i hasło, gdy nie ma ich w środowisku.

Do zrobienia jako `scripts/build-kratkoza-bundle.sh`, w stylu pozostałych skryptów Kratkozy
(krótki output, szczegóły dopiero przy porażce):

- `:app:bundleRelease`, artefakt do `dist/` jako `kratkoza-<versionName>-<versionCode>.aab`
- odczyt podpisu z **gotowego pliku**, nie z konfiguracji Gradle'a — tak samo jak robi to
  `build-kratkoza-release.sh`; AAB podpisany kluczem debug zostanie odrzucony przy uploadzie,
  a błąd jest inaczej niewidoczny do momentu odrzucenia
- **twarde przerwanie**, gdy podpis nie jest kluczem upload. Przy APK fallback na klucz debug
  ratuje wgrywanie na telefon i ma sens; przy bundlu przeznaczonym na Play jest bezużyteczny
- sprawdzenie, czy `versionCode` jest wyższy niż ostatnio wysłany — Play odrzuca powtórzony

Już gotowe i istotne dla AAB: `bundle { language { enableSplit = false } }` w
`app/build.gradle.kts`. Bez tego Play dzieliłby zasoby po języku i przełącznik języka w
aplikacji byłby atrapą — na telefonie po angielsku polskie stringi nigdy by się nie pobrały.

Do ustalenia przed pierwszym wysłaniem: keystore upload (dziś nie ma żadnego, patrz
[BUILD.md](BUILD.md)), `applicationId`, oraz czy `isMinifyEnabled` ma zostać wyłączone.

---

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
