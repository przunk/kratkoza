# Kratkoza — otwarte decyzje

Rzeczy, które implementacja musiała rozstrzygnąć, żeby ruszyć dalej, a których `project.md`
świadomie nie rozstrzyga (sekcja 39: „do ustalenia"). Każda pozycja opisuje: co zostało przyjęte
teraz, dlaczego, oraz jakie są alternatywy do wspólnej decyzji.

Nic z tej listy nie jest traktowane jako zatwierdzone wymaganie.

---

## 1. Klasyfikacja trudności — jak rozdzielić 5 poziomów

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

**Wymaganie źródłowe:** `project.md` §11 wymienia przycisk Wymaż, §12 wymaga cofalności
„wymazania cyfry". Nie doprecyzowano, czy Wymaż kasuje też kandydatów.

**Przyjęte teraz:** Wymaż opróżnia pole całkowicie — cyfrę i kandydatów. Pole nigdy nie pokazuje
obu naraz (§11: wpisanie cyfry kasuje kandydatów w tym polu), więc „opróżnij to pole" ma jedno
jednoznaczne znaczenie.

**Wariant alternatywny:** Wymaż działa dwustopniowo (najpierw cyfra, potem kandydaci).

---

## 4. Ponowne wpisanie tej samej cyfry w to samo pole

**Wymaganie źródłowe:** `project.md` §16 — każde zatwierdzenie cyfry innej niż poprawna zwiększa
licznik błędów o 1. Nie doprecyzowano, czy powtórzenie tej samej cyfry to nowe zdarzenie.

**Przyjęte teraz:** jeśli w polu jest już ta cyfra, akcja nie robi nic — brak ruchu na stosie
Undo, brak nowego błędu. Nic nie zostaje zatwierdzone, bo nic się nie zmienia.

**Wariant alternatywny:** ponowne dotknięcie tej samej cyfry działa jak Wymaż (spotykane
w innych aplikacjach Sudoku). To zmiana UX, nie tylko techniczna.
