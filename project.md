# Kratkoza — Project Specification

## 1. Cel projektu

**Kratkoza** to nowoczesna gra Sudoku na Androida.

Priorytety:

1. świetny, intuicyjny UX,
2. zgodność z klasycznymi zasadami Sudoku,
3. bardzo wysoka czytelność interfejsu,
4. perfekcyjne sterowanie dotykowe,
5. brak sztucznego blokowania funkcji gry,
6. szybkie i niezawodne działanie offline,
7. możliwość gry solo oraz uczciwej, lekkiej rywalizacji rankingowej.

Aplikacja ma być szybka, czysta, elegancka i spokojna wizualnie. Plansza Sudoku jest zawsze najważniejszym elementem ekranu.

---

## 2. Platforma

- Platforma: Android.
- `minSdk = 29`.
- `targetSdk = Android 16 / API 36`.
- `compileSdk = najnowsze stabilne SDK dostępne podczas implementacji`.
- Orientacja aplikacji w pierwszej wersji: **portrait only**.
- Tablety i landscape mogą zostać opracowane później.
- Cała podstawowa funkcjonalność Sudoku musi działać **bez połączenia z Internetem**.
- Internet może być wymagany wyłącznie przez funkcje online, np. Daily Sudoku i publiczny ranking.

---

## 3. Stos technologiczny

Projekt ma być tworzony jako nowoczesna aplikacja Android.

### Język

- **Kotlin** jako podstawowy język projektu.
- Nie tworzyć nowego kodu aplikacji w Javie bez konkretnego powodu.

### UI

- **Jetpack Compose**.
- **Material 3** — najnowsza stabilna wersja dostępna w momencie implementacji.
- Nie używać wersji alpha/beta/preview bibliotek, chyba że zostanie to później świadomie zatwierdzone.

### Architektura

Preferowana prosta i czytelna architektura:

- UI: Jetpack Compose,
- `ViewModel`,
- jednokierunkowy przepływ danych / UDF,
- immutable UI state,
- warstwa logiki gry niezależna od UI,
- repository dla danych lokalnych i późniejszych danych sieciowych.

Logika Sudoku, generator, solver, walidacja i scoring nie mogą być zaszyte bezpośrednio w komponentach UI.

### Dane lokalne

- **Room**:
  - zapisane rozgrywki,
  - historia rozgrywek,
  - plansze,
  - statystyki,
  - Daily pobrane lokalnie,
  - dane potrzebne do wznowienia gry.
- **DataStore**:
  - ustawienia użytkownika,
  - motyw,
  - opcje rozgrywki,
  - wybrana paleta,
  - preferencje UI.

### Asynchroniczność

- Kotlin Coroutines / Flow tam, gdzie są potrzebne.

### Dependency Injection

- Na początku **manual dependency injection**.
- Nie dodawać Hilt, Koin ani podobnego frameworka bez realnej potrzeby.

### Biblioteki zewnętrzne

**Minimalizować użycie bibliotek zewnętrznych.**

Jeżeli funkcję można rozsądnie i bezpiecznie wykonać za pomocą:

- Android SDK,
- Kotlin,
- Jetpack,
- Material,

należy preferować rozwiązanie platformowe.

Nową bibliotekę zewnętrzną dodawać tylko wtedy, gdy daje wyraźną korzyść i niepotrzebnie nie zwiększa złożoności, zależności ani ryzyka utrzymania projektu.

---

## 4. Internationalization / i18n

Obsługa i18n musi istnieć **od pierwszej wersji projektu**.

Na start wymagane języki:

- polski,
- angielski.

Wymagania:

- żadnych hardkodowanych tekstów widocznych dla użytkownika,
- wszystkie teksty użytkowe przechowywać w zasobach,
- architektura UI musi umożliwiać łatwe dodanie kolejnych języków,
- formatowanie liczb, dat i czasu powinno uwzględniać locale tam, gdzie ma to znaczenie.

---

## 5. Podstawowe zasady Sudoku

Kratkoza implementuje klasyczne Sudoku 9×9 zgodnie ze standardowymi zasadami gry papierowej.

Plansza:

- 9 wierszy,
- 9 kolumn,
- 9 bloków 3×3.

Każda cyfra od 1 do 9 może wystąpić dokładnie raz w każdym:

- wierszu,
- kolumnie,
- bloku 3×3.

Każda plansza musi:

- posiadać dokładnie jedno rozwiązanie,
- być możliwa do logicznego rozwiązania,
- odpowiadać zadeklarowanemu poziomowi trudności.

Poziom trudności nie może zależeć wyłącznie od liczby pustych pól. Powinien uwzględniać techniki logiczne wymagane do rozwiązania planszy.

Cyfry startowe są zawsze nieedytowalne.

Gracz może edytować wyłącznie pola, które były puste w planszy początkowej.

---

## 6. Identyfikacja planszy

Każda plansza musi mieć trwałą identyfikację.

Przechowywać co najmniej:

- `puzzleId`,
- wersję generatora / algorytmu,
- poziom trudności,
- początkowy układ planszy,
- poprawne rozwiązanie,
- opcjonalny seed, jeżeli generator jest deterministyczny,
- typ planszy:
  - solo,
  - Daily,
  - przyszły Challenge.

Identyfikacja musi umożliwiać jednoznaczne powiązanie:

- save'a,
- historii,
- statystyk,
- Daily,
- wyniku rankingowego.

---

## 7. Poziomy trudności

Wszystkie poziomy trudności są dostępne od początku.

Docelowo przewidzieć 5 poziomów:

1. Łatwy
2. Normalny
3. Trudny
4. Ekspert
5. Master / Morderczy

Ostateczne nazwy mogą zostać zmienione.

Najwyższy poziom ma być rzeczywiście bardzo trudny również dla doświadczonego gracza.

---

## 8. Plansza i czytelność

Interfejs musi być przejrzysty, elegancki i dobrze czytelny również dla osób o słabszym wzroku.

Wymagania:

- duże i wyraźne cyfry,
- wysoki, ale komfortowy kontrast,
- interfejs nie może „palić w oczy”,
- wyraźne granice bloków 3×3,
- brak nadmiaru dekoracji,
- rozróżnienie cyfr startowych od cyfr wpisanych przez gracza,
- czytelne przedstawienie kandydatów,
- czytelne oznaczenie błędów,
- stan elementu nie może być komunikowany wyłącznie kolorem,
- odpowiednio duże touch targety,
- obsługa TalkBack / content descriptions dla elementów sterujących,
- sensowne zachowanie przy zwiększonej systemowej wielkości tekstu tam, gdzie nie niszczy geometrii planszy.

Po zaznaczeniu pola podświetlać:

- aktualne pole,
- jego wiersz,
- jego kolumnę,
- jego blok 3×3.

---

## 9. Motyw i palety kolorów

Aplikacja wspiera:

- tryb jasny,
- tryb ciemny,
- tryb systemowy.

Dodatkowo przewidzieć możliwość wyboru palety kolorystycznej.

Palety powinny być eleganckie i spokojne, a nie agresywne wizualnie.

Przewidzieć również opcjonalny:

**Tryb wysokiego kontrastu**

który poprawia czytelność planszy i elementów UI, ale nadal powinien wyglądać estetycznie.

Dokładne palety zostaną ustalone podczas projektowania UI.

---

## 10. Podświetlanie takich samych cyfr

Opcja ON/OFF.

Po zaznaczeniu pola zawierającego cyfrę aplikacja może subtelnie podświetlić wszystkie pola zawierające tę samą cyfrę.

Nie może to utrudniać odczytania:

- bieżącego pola,
- wiersza,
- kolumny,
- bloku 3×3,
- błędów.

---

## 11. Wprowadzanie cyfr

Podstawowa metoda sterowania:

1. użytkownik dotyka pola,
2. pole zostaje zaznaczone,
3. użytkownik wybiera cyfrę 1–9 z panelu pod planszą / przy planszy,
4. cyfra trafia do zaznaczonego pola.

Panel cyfr 1–9 jest podstawową metodą inputu.

Dodatkowe podstawowe przyciski:

- Cofnij,
- Wymaż,
- Kandydaci.

Nie należy automatycznie przesuwać zaznaczenia na następne pole po wpisaniu cyfry.

Wpisanie normalnej cyfry do pola usuwa kandydatów zapisanych w tym konkretnym polu.

---

## 12. Undo / Cofnij

Cofanie musi działać przewidywalnie i obejmować:

- wpisanie cyfry,
- zmianę cyfry,
- wymazanie cyfry,
- dodanie kandydata,
- usunięcie kandydata,
- automatyczne zmiany kandydatów wynikające z ruchu.

Undo odtwarza stan planszy, ale **nie usuwa z historii już popełnionego błędu rankingowego**.

Nie wolno umożliwić cofnięcia kary rankingowej przez Undo.

---

## 13. Nice to have — szybki input

Opcjonalny sposób wpisywania cyfr:

- długie przytrzymanie pola,
- pojawia się radial menu / menu 3×3 z cyframi 1–9,
- użytkownik wybiera cyfrę bez korzystania z dolnego panelu.

Funkcja jest oznaczona jako **nice to have** i nie jest wymagana dla pierwszej wersji.

---

## 14. Kandydaci

Gra wspiera klasyczne notatki Sudoku nazywane **Kandydatami**.

Dla jednego pola użytkownik może zaznaczyć wiele potencjalnych cyfr.

Kandydaci:

- są wyświetlani jako małe cyfry,
- nie są odpowiedzią ostateczną,
- nie są traktowani jako błąd,
- nie wpływają negatywnie na ranking,
- są traktowani jako normalna technika i umiejętność gracza.

Tryb Kandydatów musi być łatwy do włączenia i wyłączenia.

---

## 15. Automatyczne usuwanie kandydatów

Opcja:

**Automatycznie usuwaj kandydatów**

Domyślnie: **ON**

Po poprawnym wpisaniu cyfry aplikacja automatycznie usuwa tę cyfrę z kandydatów w polach, w których nie może już wystąpić ze względu na:

- wiersz,
- kolumnę,
- blok 3×3.

Opcję można wyłączyć.

---

## 16. Błędy

Błąd oznacza:

> zatwierdzenie w polu cyfry innej niż poprawna cyfra wynikająca z rozwiązania planszy.

Każde takie zdarzenie zwiększa licznik błędów o 1.

Błędy:

- są zawsze wykrywane,
- są wyraźnie oznaczane,
- nie kończą gry,
- nie powodują utraty „życia”,
- nie blokują dalszej rozgrywki.

Gra nie posiada systemu żyć.

Poprawienie błędnej cyfry później ani użycie Undo nie usuwa popełnionego błędu ze statystyk tej rozgrywki ani z kary rankingowej.

Gra nie może zostać uznana za ukończoną, dopóki plansza nie jest poprawnie rozwiązana.

---

## 17. Podpowiedź przy 8/9

Opcjonalna funkcja pomocnicza.

Jeżeli w:

- wierszu,
- kolumnie lub
- bloku 3×3

jest prawidłowo wypełnionych 8 z 9 cyfr, aplikacja może pokazać brakującą cyfrę jako subtelną, półprzezroczystą podpowiedź.

Ustawienie:

- ON/OFF,
- domyślnie OFF.

Podpowiedź musi być jednoznacznie odróżniona od wartości wpisanej przez użytkownika.

### Ranking publiczny

Funkcja 8/9 jest niedostępna w publicznie rankingowanych rozgrywkach Daily.

---

## 18. Pełne podpowiedzi

Do późniejszego ustalenia pozostaje funkcja typu:

> „Pokaż następną poprawną cyfrę”.

Jeżeli powstanie:

- może być dostępna w trybie solo,
- nie może być używana w aktywnej rozgrywce kwalifikującej się do publicznego rankingu,
- jej użycie powinno automatycznie oznaczyć rozgrywkę jako nierankingową, jeżeli taki przypadek będzie możliwy.

Kandydaci nie są traktowani jako podpowiedź.

---

## 19. Licznik pozostałych cyfr

Opcja ON/OFF.

Przy cyfrze 1–9 aplikacja może pokazywać, ile poprawnych wystąpień danej cyfry pozostało do uzupełnienia planszy.

Po poprawnym wykorzystaniu wszystkich 9 wystąpień cyfry jej przycisk może zostać wizualnie wygaszony.

Funkcja nie wpływa na ranking.

---

## 20. Czas gry solo

Każda rozgrywka mierzy czas.

W grze solo czas jest zatrzymywany, gdy:

- użytkownik ręcznie włączy pauzę,
- aplikacja trafia do tła,
- użytkownik minimalizuje aplikację.

Minimalizacja domyślnie uruchamia pauzę.

Podczas pauzy plansza musi zostać zasłonięta, aby nie można było jej analizować przy zatrzymanym czasie.

---

## 21. Automatyczny zapis

Stan gry jest automatycznie zapisywany po każdym istotnym ruchu.

Restart aplikacji, ubicie procesu lub restart telefonu nie może powodować utraty ostatniego poprawnie zapisanego stanu.

---

## 22. Zapisywanie i wznawianie rozgrywek

Użytkownik może mieć zapisane niedokończone rozgrywki i wracać do nich później.

Dla save'a przechowywać co najmniej:

- `puzzleId`,
- poziom trudności,
- datę rozpoczęcia,
- datę i godzinę ostatniej aktywności,
- aktywny czas gry,
- aktualny stan planszy,
- kandydatów,
- historię potrzebną do Undo,
- liczbę błędów,
- typ gry,
- informację o kwalifikacji rankingowej.

Lista zapisanych gier powinna pokazywać:

- datę i godzinę,
- poziom trudności,
- czas,
- mały podgląd aktualnego stanu planszy.

Na ekranie głównym należy umożliwić szybkie wznowienie ostatniej rozgrywki.

Przykład:

**Kontynuuj — Ekspert — 12:43**

---

## 23. Historia i statystyki w bazie danych

Room powinien przechowywać historię zakończonych gier oraz dane potrzebne do statystyk.

Dla zakończonej rozgrywki przewidzieć co najmniej:

- `puzzleId`,
- typ rozgrywki,
- poziom trudności,
- czas rozpoczęcia,
- czas zakończenia,
- aktywny czas gry,
- liczbę błędów,
- wynik / score, jeżeli dotyczy,
- zdobyte XP,
- informację o ukończeniu,
- informację czy wynik był publicznie rankingowany,
- ustawienia pomocnicze istotne dla interpretacji wyniku,
- wersję generatora / ruleset, jeżeli ma znaczenie.

Dane powinny umożliwiać późniejsze wyliczanie statystyk bez utraty informacji historycznych.

---

## 24. Gra solo

Zwykłe generowane Sudoku jest trybem **solo**.

Dla zwykłych gier:

- brak publicznego leaderboardu,
- można pauzować,
- można zapisywać i wznawiać,
- wynik trafia do statystyk osobistych,
- liczy się czas,
- liczą się błędy,
- można obliczać osobisty score / skuteczność,
- można zdobywać XP.

---

## 25. Osobisty ranking / skuteczność

Zwykłe gry solo mogą służyć do obliczania osobistej skuteczności użytkownika.

Statystyka może uwzględniać:

- poziom trudności,
- czas,
- liczbę błędów,
- ukończenie.

Nie jest to publiczny leaderboard.

Dokładny algorytm osobistego score / skuteczności zostanie ustalony później.

---

## 26. Daily Sudoku

Każdego dnia dostępne są **3 plansze Daily Sudoku**.

Wszyscy gracze otrzymują tego dnia dokładnie te same trzy plansze.

Daily Sudoku jest podstawowym trybem publicznej rywalizacji.

Każda Daily ma własne:

- `puzzleId`,
- poziom trudności,
- ranking,
- wynik użytkownika.

Dokładny dobór trzech poziomów trudności zostanie ustalony później.

Przykładowo mogą to być trzy różne stopnie trudności, ale nie należy tego implementować jako niezmiennej reguły bez dalszej decyzji.

Daily powinno być możliwe do pobrania i zapisania lokalnie.

---

## 27. Publiczny ranking Daily

Publiczny ranking dotyczy w pierwszej kolejności **Daily Sudoku**.

Możliwe zakresy:

- globalny,
- krajowy,
- regionalny / wojewódzki.

Udział w rankingu jest opcjonalny i musi zostać świadomie włączony przez użytkownika.

Nie wymagać GPS ani permissionu lokalizacji.

Region użytkownik wybiera ręcznie.

Do późniejszego ustalenia:

- sposób identyfikacji użytkownika,
- backend,
- logowanie,
- synchronizacja,
- model przechowywania wyników,
- prywatność,
- dokładne zabezpieczenia requestów.

---

## 28. Daily — brak pauzy w grze rankingowej

Publicznie rankingowana rozgrywka Daily nie może korzystać z pauzy.

Jeżeli podczas aktywnej gry rankingowej:

- aplikacja zostanie zminimalizowana,
- trafi do tła,
- ekran zostanie zablokowany,
- gra zostanie przerwana,

rozgrywka traci kwalifikację do publicznego rankingu.

Stan może zostać zachowany, a użytkownik może ją później dokończyć jako grę nierankingową.

Taka gra może nadal:

- trafić do osobistych statystyk,
- przyznać XP,
- zostać zapisana w historii.

---

## 29. Wynik rankingowy

Publiczny wynik Daily powinien uwzględniać:

- konkretną planszę,
- czas rozwiązania,
- liczbę błędów.

Błędy mają być bardzo mocno karane, aby zgadywanie było rankingowo nieopłacalne.

Wstępny model kary:

`wynik końcowy = wynik bazowy × 0.7 ^ liczba_błędów`

Przykład wpływu błędów:

- 0 błędów → 100%,
- 1 błąd → 70%,
- 2 błędy → 49%,
- 3 błędy → 34.3%,
- 4 błędy → 24.01%.

Dokładny algorytm wyniku bazowego zależnego od czasu zostanie ustalony później.

Ponieważ wszyscy uczestnicy danego Daily rozwiązują tę samą planszę, ranking może bezpośrednio porównywać wyniki dla konkretnego `puzzleId`.

---

## 30. Challenge

W przyszłości może powstać osobny tryb:

**Challenge**

Na tym etapie **nie projektować szczegółów i nie implementować jego zasad na podstawie domysłów**.

Agent powinien traktować Challenge jako funkcję do późniejszego wspólnego opracowania.

Architektura nie powinna jednak niepotrzebnie uniemożliwiać dodania kolejnego typu rozgrywki.

---

## 31. Ograniczenia anty-cheat

Nie istnieje praktyczny sposób całkowitego zabezpieczenia Sudoku przed użyciem:

- drugiego telefonu,
- zdjęcia planszy,
- zewnętrznego solvera,
- AI.

Publiczny ranking należy traktować jako rywalizację **competitive-casual**, a nie system e-sportowy.

Pierwsza wersja nie powinna posiadać przesadnie skomplikowanego anty-cheat.

W przyszłości można rozważyć:

- plansze Daily dostarczane przez backend,
- pomiar czasu również po stronie serwera,
- Google Play Integrity,
- walidację requestów,
- wykrywanie nierealnych wyników,
- oznaczanie wyników zweryfikowanych.

---

## 32. XP i postęp

Użytkownik zdobywa doświadczenie za ukończone rozgrywki.

XP powinno zależeć co najmniej od:

- poziomu trudności,
- ukończenia gry.

W przyszłości XP może mieć dodatkowe zastosowanie.

### Twarde wymaganie

XP nigdy nie może blokować:

- poziomów trudności,
- podstawowych funkcji,
- kandydatów,
- ustawień,
- możliwości normalnej gry.

Nie tworzyć sztucznego grindu wymaganego do odblokowania normalnej funkcjonalności Sudoku.

---

## 33. Statystyki lokalne

Aplikacja prowadzi osobiste statystyki użytkownika.

Przewidzieć co najmniej:

- liczbę rozpoczętych gier,
- liczbę ukończonych gier,
- procent ukończeń,
- najlepszy czas dla każdego poziomu,
- średni czas dla każdego poziomu,
- łączną liczbę błędów,
- średnią liczbę błędów,
- osobistą skuteczność / score,
- zdobyte XP,
- poziom / rangę użytkownika, jeżeli system poziomów zostanie wprowadzony,
- statystyki Daily.

Do późniejszego rozważenia:

- seria ukończonych gier,
- seria dni aktywności.

---

## 34. Informacje wokół planszy

Interfejs powinien pokazywać najważniejsze informacje o aktualnej rozgrywce w czystej formie.

Do ustalenia podczas projektowania UI:

- czas,
- poziom trudności,
- liczba błędów,
- status rankingu,
- ewentualny licznik pozostałych cyfr,
- XP / wynik.

Nie wyświetlać wszystkich możliwych danych jednocześnie kosztem czytelności planszy.

---

## 35. Opcje

Aplikacja ma posiadać system ustawień wpływających na wygląd i zachowanie gry.

Obecnie przewidziane:

### Wygląd

- motyw:
  - systemowy,
  - jasny,
  - ciemny,
- paleta kolorystyczna,
- tryb wysokiego kontrastu.

### Rozgrywka

- automatyczne usuwanie kandydatów:
  - ON/OFF,
  - domyślnie ON,
- podświetlanie takich samych cyfr:
  - ON/OFF,
- podpowiedź 8/9:
  - ON/OFF,
  - domyślnie OFF,
- licznik pozostałych cyfr:
  - ON/OFF.

Lista opcji będzie rozwijana.

---

## 36. Animacje

Animacje mają być subtelne i szybkie.

Można stosować delikatne animacje dla:

- wpisania cyfry,
- ukończenia wiersza,
- ukończenia kolumny,
- ukończenia bloku 3×3,
- ukończenia całej planszy.

Animacje nie mogą:

- przeszkadzać w szybkim inputcie,
- opóźniać interakcji,
- zasłaniać informacji,
- nadmiernie odciągać uwagi.

---

## 37. Główne zasady UX

1. Plansza jest najważniejszym elementem ekranu gry.
2. Najczęstsze działania wymagają minimalnej liczby dotknięć.
3. Sterowanie ma być naturalne na telefonie w orientacji portrait.
4. Funkcje dodatkowe nie mogą utrudniać klasycznej gry Sudoku.
5. Brak blokowania funkcji przez XP.
6. Brak systemu żyć.
7. Gracz może popełnić błąd i normalnie kontynuować grę.
8. Publiczny ranking ma bardzo mocno karać zgadywanie.
9. Kandydaci są normalnym narzędziem gracza.
10. Gra solo pozwala grać we własnym tempie.
11. UI musi pozostać eleganckie również przy zwiększonym kontraście.
12. Najważniejsze operacje na planszy powinny dawać natychmiastową reakcję wizualną.

---

## 38. Testowalność i jakość

Logika domenowa Sudoku musi być testowalna niezależnie od Android UI.

Testami jednostkowymi należy objąć co najmniej:

- walidację planszy,
- sprawdzanie poprawności ruchu,
- wykrywanie błędów,
- solver,
- sprawdzanie unikalności rozwiązania,
- generator,
- klasyfikację trudności, gdy zostanie zdefiniowana,
- kandydatów,
- Undo,
- scoring,
- zasady kwalifikacji do rankingu,
- zapis/odtworzenie stanu gry.

Kod nie powinien opierać krytycznej logiki na zachowaniu konkretnego composable.

---

## 39. Rzeczy do dalszego ustalenia

- ostateczne nazwy poziomów,
- dokładny algorytm klasyfikacji trudności,
- dokładny generator Sudoku,
- konkretne techniki logiczne dla poszczególnych poziomów,
- dokładny algorytm publicznego score,
- dokładny algorytm osobistej skuteczności,
- zależność XP od poziomu i wyniku,
- przyszłe zastosowanie XP,
- dokładny projekt ekranów,
- dokładny wygląd oznaczenia błędów,
- zestaw palet kolorystycznych,
- pełna lista opcji,
- funkcja „Pokaż następną cyfrę”,
- backend,
- konto / identyfikacja użytkownika,
- zasady wyboru regionu,
- trzy konkretne poziomy Daily,
- synchronizacja Daily,
- szczegóły Challenge,
- zabezpieczenia anty-cheat,
- system poziomu / rangi użytkownika.

---

## 40. Nice to have

Elementy pożądane, ale nieobowiązkowe dla pierwszej wersji:

- radial / 3×3 menu cyfr wywoływane długim przytrzymaniem pola,
- bardziej rozbudowane zabezpieczenia rankingowe,
- system oznaczania zweryfikowanych wyników,
- dodatkowe statystyki i serie,
- dalsze wykorzystanie XP.

---

## 41. Zasada dla agenta implementującego

Jeżeli wymaganie jest oznaczone jako:

- „do ustalenia”,
- „później”,
- „nice to have”,

agent nie powinien samodzielnie wymyślać ostatecznej funkcjonalności i traktować jej jako zatwierdzonego wymagania.

W przypadku nieustalonej decyzji architektonicznej lub produktowej należy pozostawić kod gotowy do rozszerzenia, ale nie implementować spekulacyjnej funkcjonalności.

Priorytetem jest poprawna, prosta i utrzymywalna implementacja zatwierdzonych wymagań.
