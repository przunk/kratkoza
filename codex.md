# Zadanie: jedno podejście do Daily, nie do obejścia

> Zasady współpracy — worktree, gałęzie, konwencje, kto scala — są w `codex_instructions.md`.
> Przeczytaj je najpierw. Ten plik opisuje wyłącznie bieżące zadanie.

**Gałąź:** `fix/daily-single-attempt` z `develop`.

**Pliki, których dotyczy zadanie:**
- `data/repository/GameRepository.kt` — `startDaily` i to, co ją broni
- `data/local/Daos.kt`, `data/local/KratkozaDatabase.kt` — jeśli reguła wymaga zapytania lub migracji
- `ui/daily/DailyViewModel.kt` — `open`
- testy: `data/`, `data/local/`, `ui/daily/`

Równolegle przebudowuję moduł tablic wyników — dotykam `domain/ranking/`, `data/ranking/` oraz
`res/values/play_games_ids.xml`. **Tam nie wchodź.** W szczególności **nie edytuj**
`GameRepository.dailyTotals` ani niczego w `data/ranking/`. Reszta `GameRepository` jest Twoja.

---

## Usterka

Szybkie dwukrotne naciśnięcie „Start" na karcie Daily uruchamia grę **dwa razy**. Druga plansza
zostaje w tle; po ukończeniu pierwszej gracz widzi ją od nowa i wygląda to tak, jakby Daily dało się
rozegrać drugi raz. Zgłoszone z urządzenia 2026-08-27, build 0.16.0 (26). Opis jest też w
`docs/STATUS.md` w sekcji „Znane usterki" — zaktualizuj go, gdy skończysz.

## Dlaczego to nie jest usterka UI

`DailyViewModel.open` startuje korutynę na każde naciśnięcie. Dwie korutyny wchodzą do
`GameRepository.startDaily` **równolegle** i obie czytają stan, zanim którakolwiek cokolwiek zapisze
— więc obie widzą planszę jako nierozegraną i obie ją zakładają.

**Zablokowanie przycisku nie jest naprawą.** Zamyka najczęstszą drogę i nic poza tym: ta sama luka
zostaje otwarta dla każdego innego wywołania, dla powrotu z nawigacji i dla wznowienia procesu.
Reguła „jedno podejście dziennie" jest regułą domeny i ma być nie do obejścia **w repozytorium**.
Blokada w UI jest mile widziana, ale jako uprzejmość, nie jako mechanizm.

## Co ma być prawdą po naprawie

- Dwa równoległe wywołania `startDaily(date, difficulty)` dają **jedną** grę. Drugie dostaje tę
  samą, którą założyło pierwsze — albo `null`, jeśli plansza jest już ukończona. Nigdy dwie.
- Sekwencyjne wywołanie po ukończeniu dalej zwraca `null`, tak jak dziś.
- Wznowienie rozpoczętej gry działa bez zmian.
- To samo sprawdź dla `startNewGame` i dla ścieżki „Kontynuuj". Jeśli tam luka nie istnieje,
  napisz w commicie **dlaczego** — to informacja warta tyle samo co poprawka.

## Wskazówki, nie rozstrzygnięcia

Sedno to atomowość: sprawdzenie i zajęcie planszy muszą być jednym krokiem, którego drugi wątek nie
przecina. Do rozważenia — Room `@Transaction` na metodzie, która sprawdza i wstawia; unikalny indeks
na `saved_games`/`game_history`, który po prostu nie pozwoli powstać drugiemu wierszowi; mutex w
repozytorium. Każde ma inny koszt i inny zasięg:

- **Mutex** jest najprostszy i broni tylko tego procesu — tu wystarczy, bo baza jest lokalna
  i jednoprocesowa, ale warto to napisać w komentarzu, żeby ktoś kiedyś nie założył więcej.
- **Indeks unikalny** jest najmocniejszy i przeżywa każdą przyszłą ścieżkę wywołania, ale to
  **migracja schematu** — wersja bazy w górę i ręcznie napisana migracja, jak dwie poprzednie.
  Jeśli wybierzesz tę drogę, dopisz test migracji.

Wybierz sam i **uzasadnij wybór w commicie**. Nie ma tu jednej dobrej odpowiedzi, jest kilka
z różnymi kosztami.

## Na co uważać

- **Test musi naprawdę być równoległy.** Test, który woła `startDaily` dwa razy pod rząd, przejdzie
  także na niepoprawionym kodzie i niczego nie udowodni. Potrzebne są dwie korutyny startujące
  razem — `async` + `awaitAll`, albo `coroutineScope` z dwoma `launch`. Sprawdź, że **bez** poprawki
  ten test pada; jeśli przechodzi na obu wersjach, testuje coś innego, niż myślisz.
- `FakeDaos` reimplementuje SQL w Kotlinie, więc **sam nie udowodni** niczego o atomowości zapytania.
  Jeśli reguła siada w SQL-u albo w indeksie, potrzebny jest test na prawdziwej bazie Room —
  wzorzec jest w `GameHistoryDaoTest`.
- Nie podbijaj wersji na gałęzi zadaniowej.

## Weryfikacja

```bash
./scripts/test-kratkoza.sh
./scripts/lint-kratkoza.sh
```

W opisie commita napisz, czego **nie** udało się sprawdzić maszynowo — w szczególności to, że nie
sprawdzałeś podwójnego kliknięcia na urządzeniu.
