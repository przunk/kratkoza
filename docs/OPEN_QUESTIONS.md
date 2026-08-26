# Kratkoza — otwarte decyzje

Rzeczy, które implementacja musiała rozstrzygnąć, żeby ruszyć dalej, a których `project.md`
świadomie nie rozstrzyga (sekcja 39: „do ustalenia"). Każda pozycja opisuje: co zostało przyjęte,
dlaczego, oraz jakie są alternatywy.

| # | Rzecz | Stan |
|---|---|---|
| 1 | Podział pięciu poziomów trudności między techniki | 🟡 **otwarte** — do weryfikacji w grze |
| 2 | Auto-usuwanie kandydatów po błędnej cyfrze | 🟢 **ustalone** — nie usuwa |
| 3 | Zakres przycisku „Wymaż" | 🟡 tymczasowo OK — rozstrzygnie test w grze |
| 4 | Ponowne wpisanie tej samej cyfry | 🟢 **ustalone** — brak akcji |
| 5 | Domyślne wartości dwóch opcji z §35 | 🟢 **ustalone** — zostaje ON |
| 6 | „Portrait only" na dużych ekranach | 🟢 **ustalone** — zostaje wariant A |
| 7 | Edycja poprawnie wpisanej cyfry | 🟢 **ustalone** — zablokowana, cofa tylko Undo |
| 8 | Co odbiera Daily kwalifikację rankingową | 🟢 **ustalone** — minimalizacja nie, podpowiedź tak |

🟢 = zatwierdzone, kod jest zgodny, pozycja zostaje tylko jako zapis powodu.
🟡 = przyjęte tymczasowo, czeka na decyzję.

Status na podstawie ustaleń z 2026-08-25.

---

## 1. Klasyfikacja trudności — jak rozdzielić 5 poziomów

**Stan: 🟡 otwarte.** Czeka na ocenę w realnej grze, czy MASTER jest wystarczająco trudny.

**Wymaganie źródłowe:** `project.md` §5 — poziom trudności musi wynikać z technik logicznych
potrzebnych do rozwiązania, a nie wyłącznie z liczby pustych pól. §7 — pięć poziomów.
§39 — dokładny algorytm klasyfikacji i konkretne techniki dla poziomów: do ustalenia.

**Problem.** Solver logiczny (`LogicalSolver`) zna dziesięć technik ustawionych w drabinę
trudności. Pierwsza wersja mapowała każdy poziom na inny szczebel tej drabiny. Pomiar na
75 wygenerowanych planszach pokazał, że rozkład jest bardzo nierówny:

| najtrudniejsza wymagana technika | liczba plansz |
|---|---|
| naked / hidden single | 55 |
| locked candidates | 11 |
| naked / hidden pair | 5 |
| naked / hidden triple, X-Wing | 1 |
| XY-Wing, Swordfish | 3 |

Dwie trzecie plansz — nawet wykopanych maksymalnie, do ~24 podpowiedzi — daje się rozwiązać
samymi singlami. Trójki i X-Wing są w praktyce prawie nigdy *wymagane*. Drabina „jeden poziom =
jeden szczebel technik" dawała więc jeden przeludniony poziom na dole i jeden pusty w środku:
generator nie potrafił wyprodukować planszy EXPERT w 60 próbach.

**Przyjęte teraz** (`Difficulty`, `DifficultyEvaluator`):

| poziom | wymaga | dodatkowo |
|---|---|---|
| EASY | tylko single | co najmniej 36 podpowiedzi |
| NORMAL | tylko single | wykopana maksymalnie (~24) |
| HARD | locked candidates | — |
| EXPERT | naked / hidden pair | — |
| MASTER | trójka, X-Wing, XY-Wing lub Swordfish | — |

Technika pozostaje osią klasyfikacji. Liczba podpowiedzi rozróżnia wyłącznie EASY od NORMAL,
czyli działa jako rozstrzygnięcie *wewnątrz* jednego szczebla technik — nie jako kryterium
główne. Generacja trafia w każdy poziom w kilkanaście milisekund.

**Warianty do rozważenia:**

- **A (obecny)** — zostawić jak jest. Zaleta: działa, jest szybkie, wszystkie poziomy realnie
  osiągalne. Wada: MASTER to „potrzeba czegoś ponad pary", co dla bardzo doświadczonego gracza
  może być za mało — `project.md` §7 chce, żeby najwyższy poziom był naprawdę trudny.
- **B** — dołożyć solverowi techniki (Simple Colouring / Single's Chains, XYZ-Wing, W-Wing,
  Jellyfish, remote pairs). Więcej szczebli = gęstsza drabina, każdy poziom może wrócić do
  definicji czysto technicznej, a MASTER staje się realnie ciężki. Koszt: sporo nowego kodu
  solvera i testów.
- **C** — punktowy wskaźnik trudności (suma kosztów wszystkich zastosowanych kroków, nie tylko
  najtrudniejszego) i progi punktowe zamiast szczebli. Zaleta: ciągła skala, łatwo dostroić.
  Wada: progi są arbitralne i trudniej wytłumaczyć graczowi.

Rekomendacja: **A teraz, B kiedy będziemy dostrajać najwyższy poziom.** Sama drabina technik
i progi siedzą w `Difficulty`, więc zmiana to jedna edycja + przeliczenie testów.

---

## 2. Automatyczne usuwanie kandydatów po *błędnej* cyfrze

**Stan: 🟢 ustalone 2026-08-25.** Zostaje wariant przyjęty — po błędnej cyfrze kandydaci sąsiadów nie są ruszani.

**Wymaganie źródłowe:** `project.md` §15 — „Po **poprawnym** wpisaniu cyfry aplikacja
automatycznie usuwa tę cyfrę z kandydatów w polach, w których nie może już wystąpić".

**Problem.** „Poprawne wpisanie" można czytać dwojako: (a) wpisanie cyfry zgodnej z rozwiązaniem,
(b) każde udane wpisanie cyfry.

**Przyjęte teraz:** wariant (a) — czyszczenie kandydatów u sąsiadów tylko dla cyfry zgodnej
z rozwiązaniem. Powody: to dosłowne brzmienie §15; oraz wariant (b) przy pomyłce kasowałby
poprawne notatki w maksymalnie 20 polach, których gracz nie odzyska inaczej niż przez Undo.
Nie zdradza to niczego, bo §16 i tak wymaga natychmiastowego oznaczenia błędu.

**Wariant alternatywny:** (b), jeśli uznasz, że zachowanie ma być identyczne niezależnie od
poprawności cyfry. Zmiana to jeden warunek w `GameEngine.enterDigit`.

---

## 3. Co dokładnie robi przycisk „Wymaż"

**Stan: 🟡 tymczasowo OK.** Zostaje jak jest; rozstrzygnie granie na urządzeniu.

**Wymaganie źródłowe:** `project.md` §11 wymienia przycisk Wymaż, §12 wymaga cofalności
„wymazania cyfry". Nie doprecyzowano, czy Wymaż kasuje też kandydatów.

**Przyjęte teraz:** Wymaż opróżnia pole całkowicie — cyfrę i kandydatów. Pole nigdy nie pokazuje
obu naraz (§11: wpisanie cyfry kasuje kandydatów w tym polu), więc „opróżnij to pole" ma jedno
jednoznaczne znaczenie.

**Wariant alternatywny:** Wymaż działa dwustopniowo (najpierw cyfra, potem kandydaci).

---

## 4. Ponowne wpisanie tej samej cyfry w to samo pole

**Stan: 🟢 ustalone 2026-08-25.** Ponowne wpisanie tej samej cyfry nie robi nic.

**Wymaganie źródłowe:** `project.md` §16 — każde zatwierdzenie cyfry innej niż poprawna zwiększa
licznik błędów o 1. Nie doprecyzowano, czy powtórzenie tej samej cyfry to nowe zdarzenie.

**Przyjęte teraz:** jeśli w polu jest już ta cyfra, akcja nie robi nic — brak ruchu na stosie
Undo, brak nowego błędu. Nic nie zostaje zatwierdzone, bo nic się nie zmienia.

**Wariant alternatywny:** ponowne dotknięcie tej samej cyfry działa jak Wymaż (spotykane
w innych aplikacjach Sudoku). To zmiana UX, nie tylko techniczna.

---

## 5. Wartości domyślne dwóch opcji z §35

**Stan: 🟢 ustalone 2026-08-25.** Zostaje ON w obu przypadkach, potwierdzone w grze.

**Wymaganie źródłowe:** `project.md` §35 wymienia „podświetlanie takich samych cyfr: ON/OFF"
(§10) oraz „licznik pozostałych cyfr: ON/OFF" (§19), ale — inaczej niż przy auto-usuwaniu
kandydatów (domyślnie ON) i podpowiedzi 8/9 (domyślnie OFF) — nie podaje wartości domyślnej.

**Przyjęte teraz:** obie **ON**. Obie są pomocami w czytelności, a `project.md` §8 stawia
czytelność bardzo wysoko; obie da się wyłączyć jednym przełącznikiem w Ustawieniach.

**Wariant alternatywny:** OFF dla obu, jeśli domyślny ekran ma być maksymalnie surowy i bliższy
grze papierowej. Zmiana to dwie wartości w `GameSettings`.

---

## 6. „Portrait only" nie jest już egzekwowalne na dużych ekranach

**Stan: 🟢 ustalone 2026-08-25 — wariant A.** Zostaje jak jest; wrócimy do tego przy pracach nad tabletami.

**Wymaganie źródłowe:** `project.md` §2 — „Orientacja aplikacji w pierwszej wersji: portrait only.
Tablety i landscape mogą zostać opracowane później."

**Fakt techniczny, nie decyzja.** Od Androida 16 (API 36 — czyli dokładnie nasz `targetSdk`) system
**ignoruje** `android:screenOrientation` na urządzeniach o najmniejszej szerokości ≥ 600dp. Android
Lint zgłasza to jako `DiscouragedApi`. Na telefonach wymuszenie portretu nadal działa, więc dla
docelowego urządzenia pierwszej wersji wymaganie jest spełnione. Na tablecie aplikacja pokaże się
w landscape niezależnie od manifestu.

**Stan teraz:** manifest deklaruje `portrait`, ostrzeżenie lintu wyciszone z komentarzem
(`app/lint.xml`). Layout ekranu gry jest kolumną — w landscape plansza zajmie całą szerokość
i panel cyfr zjedzie poza ekran. Nie testowano tego, bo tablety są poza zakresem etapu.

**Do decyzji, kiedy dojdziemy do tabletów:**

- **A** — zostawić jak jest do czasu prac nad tabletami; na telefonie problem nie występuje.
- **B** — już teraz zrobić ekran gry przewijalnym w pionie, żeby landscape na tablecie był brzydki,
  ale używalny. Mały koszt, usuwa najgorszy przypadek.
- **C** — pełny layout landscape (plansza obok panelu cyfr). To już praca z §2 „tablety
  i landscape później".

---

## 7. Czy poprawnie wpisana cyfra powinna stać się nieedytowalna

**Stan: 🟢 ustalone 2026-08-26 — tak, blokowana.** Poprawnie wpisanej cyfry nie da się zastąpić
ani wymazać; jedyną drogą wstecz jest Undo. Blokada jest wyprowadzana z porównania z rozwiązaniem,
a nie zapisywana, więc obowiązuje też natychmiast po wznowieniu zapisanej gry.

**To jest zmiana wymagania, nie tylko implementacja.** `project.md` §5 mówi: „Gracz może edytować
wyłącznie pola, które były puste w planszy początkowej" — czyli **każde** takie pole. Po tej decyzji
§5 należy czytać z zastrzeżeniem: pole puste na starcie pozostaje edytowalne dopóty, dopóki nie
przyjmie cyfry zgodnej z rozwiązaniem.

Wymazywanie jest zablokowane razem ze zmianą, bo inaczej blokada byłaby atrapą — wystarczyłoby
wymazać i wpisać co innego.

**Zgłoszone:** 2026-08-26.

**Pomysł:** po wpisaniu cyfry zgodnej z rozwiązaniem zablokować możliwość zastąpienia jej inną
cyfrą. Chroniłoby to gracza przed przypadkowym zamienieniem poprawnego ruchu na błąd.

**Konsekwencje do rozważenia:**

- trzeba zdecydować, czy poprawną cyfrę nadal można wymazać oraz co dokładnie robi wtedy Undo,
- blokada upraszcza grę i ujawnia trwałym zachowaniem, że wpis był poprawny,
- obecne zasady pozwalają edytować każde pole, które było puste w planszy początkowej
  (`project.md` §5), więc pełna nieedytowalność może wymagać świadomej zmiany wymagania,
- alternatywą jest tylko ostrzeżenie lub prośba o potwierdzenie przed zastąpieniem poprawnej cyfry.

---

## 8. Co odbiera Daily kwalifikację do rankingu

**Stan: 🟢 ustalone 2026-08-26.** Minimalizacja aplikacji **nie** odbiera kwalifikacji. Zegar biegnie
dalej, także gdy aplikacja jest w tle albo została ubita.

**To jest odstępstwo od `project.md` §28**, który wymienia minimalizację, przejście w tło,
zablokowanie ekranu i przerwanie gry jako zdarzenia odbierające kwalifikację.

**Dlaczego zmiana.** §28 chroni przed darmowym czasem do namysłu — pauzą, która zatrzymuje zegar,
a pozwala studiować planszę. Ten cel realizuje **biegnący zegar**, nie flaga: skoro czas leci także
w tle, zminimalizowanie aplikacji nic nie kupuje, tylko kosztuje. Odbieranie za to całej
kwalifikacji karało odebranie telefonu wykluczeniem z dnia, a §31 mówi wprost, że ranking ma być
„competitive-casual", nie e-sportowy.

**Jak to jest liczone.** Czas gry rankingowej to różnica zegara ściennego od momentu otwarcia
planszy. Licznik wewnątrz aplikacji zatrzymuje się razem z aplikacją, więc gracz, który zamknął ją
na godzinę, wróciłby z godziną darmowego namysłu. Arytmetyki na zegarze ściennym nie da się w ten
sposób obejść, a przy braku pauzy nie ma czego odejmować.

**Co nadal odbiera kwalifikację:** nic — na razie. Uzgodnione, że odbierze ją **użycie podpowiedzi**,
gdy powstanie (patrz „Podpowiedź dla gracza" w [STATUS.md](STATUS.md)). Metoda `loseRanking`
w `GameViewModel` już istnieje i przełącza liczenie czasu na zwykłe, żeby w tym momencie licznik
nie skoczył wstecz.

**Czego to nie broni.** Gracz nadal może zrobić zrzut planszy i rozwiązać ją gdzie indziej — ale
zegar przez ten czas biegnie, więc nic na tym nie zyskuje. `project.md` §31 uznaje wprost, że
całkowite zabezpieczenie Sudoku przed drugim urządzeniem nie istnieje.