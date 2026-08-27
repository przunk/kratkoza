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
- [x] Poprawnie wpisana cyfra jest zablokowana; cofa ją tylko Undo (decyzja z 2026-08-26)
- [x] Wymazywanie (§11)
- [x] Licznik pozostałych cyfr, opcjonalny (§19)
- [x] Podpowiedź 8/9, opcjonalna, domyślnie OFF (§17)
- [x] Subtelne animacje wpisania cyfry, ukończenia wiersza / kolumny / bloku oraz błędu (§36)
- [x] Obracająca się grafika 3×3 na ekranie głównym
- [x] Timer, pauza ręczna i przy przejściu w tło, zasłonięcie planszy (§20)
- [x] Automatyczny zapis po każdym istotnym ruchu (§21)

### Dane
- [x] Zapisywanie i wznawianie rozgrywek, lista z podglądem planszy (§22)
- [x] Udostępnianie planszy kodem i wczytywanie cudzej (poza specyfikacją)
- [x] Szybkie wznowienie ostatniej gry na ekranie głównym (§22)
- [x] Historia zakończonych gier w Room (§23)
- [x] Statystyki lokalne (§33), z liczbą gier w toku i stopniem ich wypełnienia
- [x] XP i poziomy gracza — rosnący koszt kolejnych poziomów, bez kary za błędy (§32)
- [x] Ustawienia w DataStore (§35)
- [x] Motyw jasny / ciemny / systemowy (§9)
- [x] Cztery palety kolorystyczne z podglądem i tryb wysokiego kontrastu (§9, §35)
- [x] Wybór języka w aplikacji: systemowy / EN / PL (§4)
- [x] Reset statystyk — wszystkie liczniki do zera, gry w toku zostają grywalne

### Jakość
- [x] Logika domenowa testowalna bez Androida (§38)
- [x] 309 testów jednostkowych: walidacja, solver, unikalność, generator, klasyfikacja trudności,
      kandydaci, Undo, wykrywanie błędów, zapis/odtworzenie sesji, repozytorium, ViewModele
- [x] Android Lint czysty przy `abortOnError` i `warningsAsErrors`

## Czego *nie* zweryfikowano

Aplikacja nie została uruchomiona na urządzeniu ani emulatorze — w tym środowisku WSL nie ma
emulatora, a `.tooling/android-sdk` nie zawiera obrazów systemu. Zweryfikowane są: kompilacja,
lint, testy jednostkowe całej logiki, warstwy danych i ViewModeli oraz zbudowanie podpisanego APK.
Zachowanie samego UI na ekranie — układ, czytelność planszy, dotyk — wymaga wgrania APK
i sprawdzenia ręcznie.

Testy Compose UI **już są** i chodzą na JVM przez Robolectrica: Ustawienia, Statystyki, Zapisane gry
i Nowa gra. Bez pokrycia zostają ekrany, które zmieniały się najpóźniej — gra, Daily i ekran
główny.

Warstwa Roomu **nie jest już** taką dziurą. Zapytania agregujące, transakcja resetu, JOIN listy
zapisanych gier i kaskada kluczy obcych chodzą w testach po prawdziwym SQLite na Robolectriku.

## Świadomie poza pierwszym etapem

Wymagania oznaczone w `project.md` jako „do ustalenia", „później" albo „nice to have", oraz
funkcje wyłączone z zakresu etapu:

- Daily Sudoku (§26), publiczny ranking (§27–§29), Challenge (§30), backend
- Osobisty score (§25) — kolumna w bazie jest, algorytm czeka na ustalenie (§39)
- Pełne podpowiedzi (§18)
- Radial / 3×3 menu cyfr przy długim przytrzymaniu (§13, nice to have)
- Tablety i landscape (§2)

Architektura żadnej z nich nie blokuje — patrz [ARCHITECTURE.md](ARCHITECTURE.md) §5 i §8.

## Znane usterki

Zgłoszone z urządzenia, jeszcze nienaprawione. Nie są to pomysły — to rzeczy, które nie działają
tak, jak miały działać, więc nie mieszkają w [WISHLIST.md](WISHLIST.md).

### Podwójne kliknięcie „Start" otwiera dwie plansze Daily

**Zgłoszone:** 2026-08-27. Testowane na buildzie 0.16.0 (26).

Szybkie dwukrotne naciśnięcie „Start" na karcie Daily uruchamia grę dwa razy. Druga plansza zostaje
w tle i po ukończeniu pierwszej gracz widzi ją od nowa — wygląda to tak, jakby Daily dało się
rozegrać drugi raz.

**Co jest podejrzane w kodzie.** `DailyViewModel.open` startuje korutynę na każde naciśnięcie i nic
nie pilnuje, żeby druga nie weszła, zanim pierwsza skończy `startDaily`. Sama reguła „jedno podejście
dziennie" jest po stronie repozytorium (`startDaily` zwraca `null`, gdy gra jest ukończona), ale
dwa równoległe wywołania zdążą ją minąć — obie widzą stan sprzed zapisania czegokolwiek.

**Czego naprawa musi dotknąć.** Nie wystarczy zablokować przycisku w UI: to zamyka najczęstszą drogę,
ale nie samą lukę. Reguła powinna być nieprzekraczalna w repozytorium, a przycisk ma być tylko
uprzejmością. Warto przy okazji sprawdzić tę samą ścieżkę przy „Kontynuuj".

### ~~Suma wszech czasów nie zawsze dociera~~ — naprawione 2026-08-27

Trzy partie dały: Łatwy 1 wysyłkę z 3, Trudny 2 z 3, Morderczy 2 z 3. Liczby, które doszły, były
co do jednego poprawne (2405 i 6402 zgadzały się z sumą wyników), a brakowało zawsze **ogona
sekwencji** — co jest sygnaturą anulowania, nie błędu w rozgałęzieniu.

Przyczyna: wysyłka szła w `viewModelScope` ekranu gry. Trzy wysyłki to trzy okrążenia po sieci,
a gracz w tym czasie wraca do listy Daily — co czyści ten ViewModel i anuluje wszystko, co jeszcze
robił. Wynik zależał od tego, jak długo ktoś patrzył na rozwiązaną planszę.

Naprawa: `RankingSubmitter` na zakresie aplikacji, oddzielony od ekranu.

### Reset statystyk nie czyści tablic wyników

**Zgłoszone:** 2026-08-27. Nie jest to usterka, tylko rzecz, którą łatwo wziąć za usterkę — i mająca
jedną nieprzyjemną konsekwencję.

Play Games trzyma **najlepszy** wynik gracza, nie ostatni. Wysłanie gorszego wyniku kończy się
sukcesem i nie zmienia tablicy. Reset statystyk w aplikacji czyści wyłącznie lokalną historię —
tablice żyją po stronie Google i nic o tym resecie nie wiedzą.

**Konsekwencja, o której trzeba pamiętać.** Suma wszech czasów jest przeliczana z lokalnej historii.
Po resecie statystyk zaczyna od zera, więc kolejne wysyłki są **niższe niż to, co tablica już ma**,
i będą ignorowane, dopóki nowa suma nie przekroczy starej. Lokalny rekord i publiczny rozjeżdżają
się wtedy na stałe.

**Co z tym zrobić przed premierą.** Albo zresetować wyniki testerów w Play Console, albo — pewniej —
utworzyć świeże tablice i podmienić identyfikatory w `res/values/play_games_ids.xml`. Plik jest
osobny właśnie po to.

**Czy zmieniać zachowanie?** Do decyzji. Można by liczyć sumę wszech czasów niezależnie od resetu
statystyk, ale wtedy „reset statystyk" przestaje znaczyć to, co mówi. Na razie zostaje tak, jak jest,
a log rankingu mówi wprost, kiedy wynik został przyjęty, ale niczego nie zmienił.

## Do zrobienia

Kod jest gotowy do wydania alpha. Zostało to, co poza kodem — patrz niżej.
Skrypt AAB jest gotowy — patrz [BUILD.md](BUILD.md).

Po stronie właściciela projektu, poza kodem: klucz upload (nie ma żadnego), konto Play,
polityka prywatności, formularz Data safety, opis i grafiki w konsoli.

### Tabela wyników w Daily

**Zgłoszone:** 2026-08-26. Na dole ekranu Daily — własne wyniki obok regionalnych, z zaznaczeniem
najlepszego czasu z uwzględnieniem błędów.

Część da się zrobić już teraz, bez sieci: **własna historia Daily** jest w bazie i wystarczy ją
pokazać. Porównanie z innymi graczami należy do etapu 2 i czeka na tablice Play Games.

Do ustalenia: jak wyliczyć „najlepszy" przy różnej liczbie błędów. `project.md` §29 podaje karę
mnożnikową `wynik × 0.7^błędy`, ale wzoru na wynik bazowy zależny od czasu jeszcze nie ma — bez
niego „najlepszy czas uwzględniający błędy" nie ma definicji.

### Podpowiedź 8/9 niedostępna w grach rankingowych

**Zgłoszone:** 2026-08-25. `project.md` §17 kończy się zdaniem: „Funkcja 8/9 jest niedostępna
w publicznie rankingowanych rozgrywkach Daily."

Dziś nie ma czego blokować — nie ma Daily ani rankingu — więc kodu celowo nie ruszono. Do zrobienia
**razem z Daily**, i to strukturalnie, nie przez pamiętanie: podpowiedź ma być liczona wyłącznie
gdy gra nie kwalifikuje się do rankingu. Pole `rankedEligible` istnieje już w save'ie i w bazie,
więc warunek ma jedno miejsce.

To samo dotyczy każdej przyszłej pomocy z §18 („Pokaż następną cyfrę").

### Dopracowanie trybu wysokiego kontrastu

**Zgłoszone:** 2026-08-26, do weryfikacji w grze.

Pierwsza wersja kontrastuje bardzo mocno i przez to spłaszcza różnice między paletami — w tym
trybie wybór palety niewiele zmienia. To wynik świadomej decyzji: stopnie podświetlenia są
przebudowywane jako ustalone ułamki drogi od tła do atramentu, więc trzymają równe odstępy, ale
gubią po drodze charakter palety.

Do rozważenia przy strojeniu:

- zejść z `SURFACE_PUSH` i `DIGIT_PUSH` w `Palettes.kt`, żeby tło i tekst nie szły tak blisko
  bieli i czerni,
- zachować odcień palety przy przeliczaniu stopni zamiast mieszać wprost do atramentu — np.
  domieszać trochę koloru akcentu,
- albo uznać spłaszczenie za cechę, nie wadę: kto włącza wysoki kontrast, prawdopodobnie chce
  czytelności, a nie charakteru.

Czego nie wolno przy tym zepsuć: `PalettesTest` pilnuje, że stopnie rozdziela jasność w rosnącej
kolejności, że wysoki kontrast nigdy nie *zmniejsza* rozdzielenia i że podpowiedź 8/9 zostaje
cichsza od cyfry gracza (§17). Progi są w L*, nie w luminancji — patrz komentarz w teście.

### Daily Sudoku — etap 2: ranking publiczny

**Zrobiona lokalna połowa** (2026-08-27): wyliczanie wyniku według §29 i tabela własnych wyników
dnia. Zostaje porównanie z innymi graczami.

Uzgodnione: **Google Play Games Services**. Darmowe, niesie tożsamość gracza i nie wymaga serwera.
Koszt: zależność `play-services-games-v2`, wbrew `technical_requirements.md` §9, ale alternatywą
jest własny backend.

**Ustalone fakty o Play Games** (sprawdzone w dokumentacji, nie założone):

- **maksimum 70 tablic wyników na grę** — regiony jako osobne tablice to 16 województw × 3 poziomy
  = 48, plus 3 globalne; mieści się, ale wszystkie trzeba utworzyć ręcznie w konsoli,
- **dzienne i tygodniowe wersje każdej tablicy powstają same**, nic nie trzeba dla nich zakładać,
- **dzienne tablice resetują się o stałej godzinie UTC-7** — dlatego wymiana Daily została przypięta
  do stałego 07:00 UTC (wariant A), bo godzina zegarowa rozjeżdżałaby się z resetem przez pół roku,
- **nie ma filtrowania po regionie ani kraju** — Play Games zna tylko zakres publiczny i znajomych.

**Czego nie da się zrobić bez właściciela projektu:** identyfikatory tablic powstają w Play Console,
a logowanie wymaga zarejestrowanego SHA-1 klucza podpisującego. Do tego czasu integracja byłaby
kodem przeciw identyfikatorom, które nie istnieją — czyli spekulacją, przed którą ostrzega
`project.md` §41.

Do zrobienia, gdy aplikacja będzie w Play Console:

- opcjonalne logowanie Play Games — udział w rankingu ma być świadomym wyborem (§27),
- wysyłanie wyniku po ukończeniu Daily,
- wybór regionu z listy; decyzje funkcjonalne są podjęte, patrz „Wybór regionu do rankingu".

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

### Wybór regionu do rankingu

**Ustalone 2026-08-26.** `project.md` §27 wymaga, żeby region wybierał użytkownik i żeby nie
sięgać po GPS ani po permission lokalizacji. Do tego dochodzą trzy decyzje:

- **Region wolno zmieniać w dowolnym momencie.** Bez weryfikacji i bez okresu karencji.
- **Każdy region ma osobny ranking**, a nie jeden ranking z przypisaną etykietą. Gracz, który grał
  w dwóch regionach, ma dwa niezależne dorobki.
- **Na start lista regionów**, nie mapa. Wystarczy funkcjonalnie, działa poza Polską, jest dostępna
  dla czytnika ekranu i nie wymaga rysowania szesnastu nieregularnych kształtów. Mapa może dojść
  później jako nakładka na ten sam wybór — patrz [WISHLIST.md](WISHLIST.md).

Konsekwencja osobnych rankingów jest taka, że zmiana regionu przestaje być czymś, na czym da się
ugrać: nie przenosi dorobku i nie miesza wyników, więc nie ma potrzeby jej blokować. To rozwiązuje
problem „zmieniam region po zobaczeniu wyników" bez żadnej dodatkowej reguły.

### Statystyki nieukończonych gier

**Zgłoszone:** 2026-08-26.

Rozszerzyć statystyki o liczbę nieukończonych gier na każdym poziomie trudności oraz informację,
w jakim stopniu są wypełnione. Trzeba najpierw ustalić, czy „stopień ukończenia” oznacza odsetek
poprawnie wypełnionych pól, wszystkich wypełnionych pól, czy inną miarę postępu.

### Podpowiedź dla gracza

**Zgłoszone:** 2026-08-26. `project.md` §18 zostawia tę funkcję do ustalenia.

Podpowiedź wstawia **następną poprawną cyfrę w wolne pole**. Limit 1–3 użyć na partię — dokładna
liczba do ustalenia.

Ustalone przy okazji decyzji o kwalifikacji rankingowej (patrz [OPEN_QUESTIONS.md](OPEN_QUESTIONS.md)
§8): **użycie podpowiedzi odbiera Daily kwalifikację do rankingu** albo zabiera punkty — to jedyne
zdarzenie, które ma to robić, skoro minimalizacja już tego nie robi. `GameViewModel.loseRanking()`
czeka gotowa na wywołanie i przełącza liczenie czasu z zegara ściennego na zwykły, żeby licznik nie
skoczył wstecz.

Do ustalenia poza liczbą użyć:

- czy podpowiedź odbiera kwalifikację, czy tylko obniża wynik (§29 ma już karę mnożnikową za błędy),
- czy w grze solo działa bez ograniczeń (§18 dopuszcza),
- czy pole wstawione podpowiedzią jest oznaczone i czy liczy się do XP.

### ~~Wymiana tablic wyników i reużywalność modułu rankingu~~ — zrobione 2026-08-28

Deklaracja tablicy jest teraz w jednym miejscu: enum `domain/ranking/Leaderboard`. Dodanie tablicy to
stała plus odpowiedź na dwa `when`; kompilator dopomina się wtedy o identyfikator w
`PlayGamesConfig`, a wysyłka podchwytuje nową pozycję sama, bo `RankingSubmitter` przechodzi po
`Leaderboard.entries` zamiast trzymać własną listę.

Sprawdzone doświadczalnie, nie założone: dopisanie `WEEKLY_TOTAL` daje trzy odmowy kompilacji —
dwie w `Leaderboard`, trzecią w `PlayGamesConfig`. Wcześniej submitter milczał.

**Wymiana identyfikatorów** przed premierą to edycja wyłącznie `res/values/play_games_ids.xml`;
procedura jest opisana w nagłówku tego pliku.

**Zostaje do rozstrzygnięcia:** czy suma wszech czasów ma dalej iść za resetem statystyk.

## Wymaga decyzji

Siedem rzeczy, których `project.md` świadomie nie rozstrzyga. Pełny opis wariantów w
[OPEN_QUESTIONS.md](OPEN_QUESTIONS.md); poniżej sam stan.

Ustalone — kod jest zgodny, wracamy tylko jeśli coś się zmieni:

- auto-usuwanie kandydatów **nie** rusza notatek sąsiadów po błędnej cyfrze
- ponowne wpisanie tej samej cyfry w to samo pole **nie robi nic**
- „portrait only" zostaje jak jest, mimo że Android 16 ignoruje je na ekranach ≥ 600dp
- **poprawnie wpisana cyfra jest zablokowana** — cofa ją wyłącznie Undo, a `project.md` §5 należy
  odtąd czytać z tym zastrzeżeniem
- **minimalizacja nie odbiera Daily kwalifikacji rankingowej** — zegar biegnie dalej, co realizuje
  cel `project.md` §28 bez karania odebrania telefonu

Czeka na granie na urządzeniu:

- **podział 5 poziomów trudności** — czy MASTER jest realnie wystarczająco trudny (§7)
- **zakres przycisku „Wymaż"** — na razie kasuje cyfrę i kandydatów naraz
- **domyślne ON** dla podświetlania takich samych cyfr (§10) i licznika pozostałych cyfr (§19)
