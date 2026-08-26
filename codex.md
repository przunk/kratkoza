# Zadanie: blokada poprawnie wpisanej cyfry

> Zadanie przyjęte przez właściciela 2026-08-26. Nie realizować go na gałęzi
> `fix/game-screen-readability`. Rozpocząć dopiero po scaleniu tej gałęzi i utworzeniu nowej gałęzi
> z aktualnego `develop`.

**Proponowana gałąź:** `fix/lock-correct-player-digits` z aktualnego `develop`.

## Cel

Gdy gracz wpisze do edytowalnego pola cyfrę zgodną z rozwiązaniem planszy, nie może później
zastąpić jej inną, błędną cyfrą.

Blokada jest regułą domenową, a nie zabezpieczeniem pojedynczego przycisku. Każda droga wywołująca
zmianę planszy ma zachowywać się tak samo.

## Ustalone zachowanie

- Cyfry startowe pozostają nieedytowalne tak jak dotychczas.
- Niepoprawna cyfra wpisana przez gracza nadal może zostać zastąpiona inną cyfrą, poprawiona albo
  wymazana.
- Poprawna cyfra wpisana przez gracza staje się zablokowana:
  - próba wpisania w jej miejsce innej cyfry niczego nie zmienia,
  - przycisk „Wymaż” jej nie usuwa,
  - próba nie dopisuje ruchu do historii, nie uruchamia zapisu, nie zwiększa licznika błędów i nie
    wywołuje animacji błędu.
- „Wymaż” również musi respektować blokadę. Pozostawienie możliwości wymazania byłoby prostym
  obejściem: gracz mógłby usunąć poprawną cyfrę, a potem wpisać błędną.
- **Cofnij jest jedynym wyjątkiem.** Bezpośrednio po wpisaniu poprawnej cyfry Undo nadal odtwarza
  poprzednią wartość pola oraz wszystkie zmiany kandydatów. Licznik wcześniej popełnionych błędów
  nadal nie maleje, zgodnie z `project.md` §12 i §16.
- Blokada ma wynikać z bieżącego stanu planszy i rozwiązania (`board[index] == solution[index]`),
  bez nowej flagi w bazie. Dzięki temu działa również po zapisaniu i ponownym otwarciu gry, bez
  migracji danych.
- Tryb kandydatów nie wymaga osobnego wyjątku: zajęte pole już teraz nie przyjmuje kandydatów.
- Nie dodajemy podpowiedzi ani nowego wizualnego oznaczenia poprawności. Reguła nie może ujawniać
  rozwiązania pustych pól.

## Miejsce implementacji

Podstawowa kontrola powinna znaleźć się w `GameEngine`, ponieważ jest to reguła gry, a nie UI.
`GameViewModel` może dalej delegować ruchy do silnika. Zwrócenie tej samej instancji `GameState`
zachowa istniejący kontrakt „brak zmiany = brak zapisu”.

Przewidywany zakres plików:

- `app/src/main/kotlin/com/przunk/kratkoza/domain/game/GameEngine.kt`
- `app/src/test/kotlin/com/przunk/kratkoza/domain/game/GameEngineTest.kt`
- `app/src/test/kotlin/com/przunk/kratkoza/domain/game/UndoTest.kt` — tylko jeśli istniejące testy
  Undo nie pokrywają nowego przypadku
- `docs/STATUS.md` — usunięcie rozstrzygniętego punktu „blokowanie poprawnie wpisanej cyfry”

Jeśli podczas implementacji okaże się, że potrzebna jest zmiana UI lub pliku spoza tej listy,
najpierw rozszerzyć zakres jawnie, zamiast dodawać ją przy okazji.

## Kryteria akceptacji i testy

1. `poprawna → błędna` zwraca niezmieniony stan.
2. `poprawna → Wymaż` zwraca niezmieniony stan.
3. Zablokowana próba zachowuje planszę, kandydatów, licznik błędów i stos Undo.
4. `błędna → inna błędna` nadal działa i nalicza kolejne zdarzenie błędu.
5. `błędna → poprawna` nadal działa, nie usuwa wcześniejszej kary i blokuje pole od tej chwili.
6. Undo poprawnego ruchu nadal przywraca poprzednią cyfrę lub puste pole oraz automatycznie usunięte
   kandydaty.
7. Stan wczytany z zapisu blokuje poprawne cyfry bez dodatkowych danych trwałych.
8. Uruchomić celowane testy `GameEngineTest` i `UndoTest`, a na końcu pełną weryfikację wymaganą
   przez `codex_instructions.md`.
