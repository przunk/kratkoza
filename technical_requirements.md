# Kratkoza — Technical Requirements

## 1. Platforma

- Platforma: Android.
- `minSdk = 29`.
- `targetSdk = Android 16 / API 36`.
- `compileSdk = najnowsze stabilne SDK dostępne podczas implementacji`.
- Pierwsza wersja aplikacji działa wyłącznie w orientacji **portrait**.
- Podstawowa funkcjonalność Sudoku musi działać całkowicie offline.
- Funkcje wymagające Internetu mają być odseparowane od podstawowej rozgrywki.

---

## 2. Język

- Główny język projektu: **Kotlin**.
- Nowy kod aplikacji powinien być pisany w Kotlinie.
- Java może być użyta wyłącznie wtedy, gdy istnieje konkretne techniczne uzasadnienie.

---

## 3. UI

- UI: **Jetpack Compose**.
- Design system: **Material 3**.
- Używać najnowszych stabilnych wersji bibliotek dostępnych podczas implementacji.
- Nie używać wersji alpha, beta ani preview bez świadomej decyzji.
- Preferować komponenty Android SDK, Jetpack i Material zamiast zewnętrznych bibliotek.

---

## 4. Architektura

Projekt powinien używać prostej architektury:

`Compose UI -> ViewModel -> Domain / Repository -> Room / DataStore`

Założenia:

- jedna główna `Activity`,
- ekranami zarządza Navigation Compose,
- UI nie zawiera logiki Sudoku,
- `ViewModel` odpowiada za stan i obsługę zdarzeń ekranu,
- używać jednokierunkowego przepływu danych (UDF),
- stan UI powinien być immutable,
- stan udostępniać jako `StateFlow`,
- logika domenowa musi być niezależna od Android UI.

Nie wprowadzać dodatkowych warstw architektonicznych bez realnej potrzeby.

---

## 5. Logika Sudoku

Logika Sudoku ma być napisana jako możliwie czysty Kotlin, niezależny od Androida.

Przewidzieć osobne odpowiedzialności dla:

- modelu planszy,
- generatora,
- solvera,
- walidatora,
- sprawdzania unikalności rozwiązania,
- klasyfikacji trudności,
- kandydatów,
- historii ruchów / Undo,
- sesji gry,
- obliczania wyniku,
- zasad kwalifikacji rankingowej.

Przykładowe klasy / komponenty:

- `SudokuBoard`
- `SudokuGenerator`
- `SudokuSolver`
- `SudokuValidator`
- `DifficultyEvaluator`
- `CandidateManager`
- `GameSession`
- `ScoreCalculator`

Nazwy mogą się zmienić, ale odpowiedzialności powinny pozostać rozdzielone.

---

## 6. Dane lokalne

### Room

Room służy do przechowywania danych strukturalnych:

- aktywnych zapisów gier,
- historii zakończonych gier,
- plansz,
- Daily Sudoku,
- wyników,
- XP,
- statystyk,
- danych potrzebnych do wznowienia rozgrywki,
- danych potrzebnych do odtworzenia historii ruchów.

Każda plansza powinna mieć trwałe `puzzleId`.

Dane historyczne powinny umożliwiać późniejsze ponowne wyliczenie statystyk.

### DataStore

DataStore służy do ustawień użytkownika, m.in.:

- motyw,
- paleta kolorów,
- tryb wysokiego kontrastu,
- opcje kandydatów,
- opcje podpowiedzi,
- ustawienia sposobu wyświetlania planszy,
- pozostałe preferencje UI i rozgrywki.

Nie przechowywać złożonych danych rozgrywki w DataStore.

---

## 7. Asynchroniczność

- Używać Kotlin Coroutines.
- Używać `Flow` / `StateFlow` tam, gdzie ma to sens.
- Operacje bazodanowe i przyszłe sieciowe nie mogą blokować głównego wątku.
- Nie wprowadzać RxJava.

---

## 8. Dependency Injection

Na początku używać **manual dependency injection**.

Preferowany prosty `AppContainer` lub równoważne rozwiązanie.

Nie dodawać:

- Hilt,
- Dagger,
- Koin,

chyba że później pojawi się realna potrzeba wynikająca ze skali projektu.

---

## 9. Biblioteki zewnętrzne

Minimalizować liczbę bibliotek zewnętrznych.

Jeżeli daną funkcję można rozsądnie zaimplementować przy użyciu:

- Android SDK,
- Kotlin,
- Jetpack,
- Material,

należy preferować te rozwiązania.

Każda nowa biblioteka zewnętrzna powinna mieć konkretne uzasadnienie.

Nie dodawać bibliotek wyłącznie dla wygody, jeśli zwiększają:

- złożoność,
- rozmiar projektu,
- ryzyko utrzymania,
- liczbę zależności,
- trudność aktualizacji.

Agent nie powinien samodzielnie dodawać frameworków architektonicznych „bo są popularne”.

---

## 10. Moduły projektu

Na początku projekt powinien posiadać **jeden moduł aplikacji `app`**.

Nie dzielić projektu od razu na wiele modułów Gradle.

Preferować logiczny podział przez pakiety, np.:

```text
kratkoza/
  data/
    local/
    repository/
  domain/
    sudoku/
    scoring/
    model/
  ui/
    home/
    game/
    daily/
    saves/
    statistics/
    settings/
  navigation/
  util/
```

Jeżeli projekt znacząco urośnie, modularizacja może zostać wykonana później.

---

## 11. Nawigacja

Używać Navigation Compose.

Przewidywane główne ekrany:

- Home
- New Game
- Game
- Daily
- Saved Games
- Statistics
- Settings

Dokładny flow ekranów zostanie ustalony w wymaganiach UI.

---

## 12. i18n

Internationalization ma być poprawnie przygotowane od początku.

Na start:

- English,
- Polish.

Wymagania:

- brak hardkodowanych tekstów użytkowych,
- wszystkie teksty w zasobach,
- łatwa możliwość dodania kolejnych języków,
- poprawne formatowanie dat, czasu i liczb zależnie od locale.

---

## 13. Motywy i dostępność

Wspierać:

- light,
- dark,
- system,
- wybór palety kolorystycznej,
- opcjonalny tryb wysokiego kontrastu.

Wysoki kontrast ma poprawiać czytelność, ale UI nadal powinno wyglądać elegancko i nie być agresywne wizualnie.

Stan elementów nie może być sygnalizowany wyłącznie kolorem.

Uwzględnić:

- odpowiednie touch targety,
- TalkBack,
- content descriptions,
- zwiększony systemowy font tam, gdzie nie niszczy planszy.

---

## 14. Zapis stanu

Stan aktywnej gry powinien być automatycznie utrwalany po każdym istotnym ruchu.

Aplikacja musi poprawnie odtworzyć grę po:

- zamknięciu,
- ubiciu procesu,
- ponownym uruchomieniu,
- restarcie telefonu.

Nie można opierać trwałego stanu wyłącznie na `ViewModel`.

---

## 15. Testy

Logika domenowa musi być testowalna bez uruchamiania Android UI.

Testy jednostkowe powinny obejmować co najmniej:

- walidację planszy,
- solver,
- generator,
- unikalność rozwiązania,
- wykrywanie błędów,
- kandydatów,
- Undo,
- zapis / odtworzenie sesji,
- scoring,
- zasady rankingu,
- klasyfikację trudności po jej zdefiniowaniu.

Preferować zwykłe fake'i zamiast ciężkich bibliotek mockingowych.

Compose UI tests stosować dla najważniejszych interakcji i ekranów.

---

## 16. Jakość kodu

Kod powinien być:

- prosty,
- czytelny,
- przewidywalny,
- łatwy do testowania,
- łatwy do rozszerzenia,
- pozbawiony zbędnej abstrakcji.

Nie stosować overengineeringu.

Nie dodawać warstw, interfejsów, wrapperów ani frameworków, jeżeli nie rozwiązują konkretnego problemu.

Logika domenowa nie może zależeć od konkretnych `Composable`.

---

## 17. Zasada implementacyjna dla agenta

Agent powinien:

1. preferować prostą implementację nad sprytną,
2. nie dodawać zależności bez uzasadnienia,
3. nie implementować funkcji oznaczonych w `project.md` jako „do ustalenia”,
4. nie wymyślać samodzielnie zachowania produktu tam, gdzie wymagania są świadomie otwarte,
5. utrzymywać logikę Sudoku niezależnie od UI,
6. pisać testy dla krytycznej logiki domenowej,
7. zachowywać zgodność z `project.md` jako nadrzędną specyfikacją funkcjonalną.

Jeżeli istnieje konflikt między własnym pomysłem implementacyjnym agenta a zatwierdzonym wymaganiem projektu, pierwszeństwo ma wymaganie projektu.
