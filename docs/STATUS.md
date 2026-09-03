# Kratkoza — stan realizacji

**Wersja kodu 0.32.0 (51); na Play w kanale wewnętrznym pozostaje 0.31.0 (50).** Gra solo i Daily
z publicznym rankingiem działają na urządzeniu; 398 testów, lint czysty.

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
- [x] Podpowiedź odsłaniająca zaznaczoną cyfrę, niedostępna w grach rankingowych (§18)
- [x] Wyczyszczenie wszystkich kandydatów jednym długim przyciśnięciem

### Daily
- [x] Jedna plansza dzienna na poziom, deterministyczna z daty i `RULESET_VERSION`
- [x] Trzy poziomy: Łatwy, Trudny, Ekspert — drabinka wyrównana pomiarem stu plansz na poziom
- [x] Zmiana dnia o 07:00 UTC (08:00/09:00 w Warszawie), jedno podejście do planszy
- [x] Punktacja addytywna: stała za rozwiązanie plus premia za czas, kara mnożnikowa za błędy (§29)
- [x] Kwalifikacja rankingowa — zachowana przy minimalizacji, wygasa razem z cyklem planszy
- [x] Wygasłe Daily pozostaje grywalne jako nierankingowe i odzyskuje zwykłe funkcje pomocy

### Ranking publiczny
- [x] Play Games Services v2: logowanie, nakładka z tablicami, wysyłanie wyników
- [x] Pięć tablic: trzy dzienne po poziomach, suma dzienna, suma wszech czasów
- [x] Ponowienie przy `CLIENT_RECONNECT_REQUIRED`, potem wysyłka bezzwrotna jako zapas
- [x] Wysyłka na zakresie aplikacji, więc przeżywa opuszczenie ekranu gry
- [x] Dodanie tablicy wymusza uzupełnienie w trzech miejscach — inaczej kod się nie kompiluje

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
- [x] Archiwum rozwiązanych plansz, trzymane osobno od statystyk, żeby reset go nie zabierał
- [x] Baza z wersji wyższej niż bieżąca jest kasowana, a nie blokuje uruchomienia

### Ekran główny i sklep
- [x] Napis „Kratkoza" rysowany w scenie jako kształty, nie czcionką
- [x] Grafika 3×3 z przechyłem, iskrami i cieniem cyfr, czytelna w obu motywach
- [x] Ikona i grafika promocyjna generowane skryptem, zrzuty ekranu renderowane z testów

### Jakość
- [x] Logika domenowa testowalna bez Androida (§38)
- [x] 393 testy: walidacja, solver, unikalność, generator, klasyfikacja trudności, kandydaci,
      Undo, wykrywanie błędów, zapis/odtworzenie sesji, migracje po prawdziwym SQLite,
      repozytoria, ViewModele, ekrany Compose na Robolectriku
- [x] Android Lint czysty przy `abortOnError` i `warningsAsErrors`

## Czego *nie* zweryfikowano

Aplikacja **jest** sprawdzana na urządzeniu — właściciel gra na buildach z tej gałęzi, a wersja
0.31.0 (50) stoi na Play w kanale wewnętrznym. To, czego nie da się zamknąć tutaj, to emulator: w
tym środowisku WSL go nie ma, więc każde zachowanie na ekranie potwierdza dopiero wgrany plik.

Praktyczny wniosek z tej sesji: **wstrzykiwany dotyk nie dociera w testach do klawiszy klawiatury
cyfr** — zwykłe kliknięcie też nie ląduje — więc gesty na niej sprawdza się przez akcje semantyki.
Test, który „potwierdzał" gest dotykiem, potwierdzał tylko własną bezsilność.

Pokryte testami Compose: Ustawienia, Statystyki, Zapisane gry, Nowa gra, Gra, Archiwum plansz.
Warstwa Roomu chodzi po prawdziwym SQLite, razem z każdą migracją od wersji 1.

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

**Nic otwartego.** Wszystko zgłoszone z urządzenia jest naprawione; jedyna niewykreślona pozycja
niżej opisuje zachowanie, które łatwo wziąć za usterkę, a nią nie jest.

Zgłoszone z urządzenia, jeszcze nienaprawione. Nie są to pomysły — to rzeczy, które nie działają
tak, jak miały działać, więc nie mieszkają w [WISHLIST.md](WISHLIST.md).

### ~~Grafika tytułowa jest zrobiona pod ciemny motyw~~ — naprawione 2026-08-28

Naprawione przez Codexa w `71211a2` dokładnie w kierunku opisanym niżej: rozjaśnianie ku bieli
zastąpione przesunięciem **od tła** (`oppositeInk`), więc jedna stała działa w obu motywach i nie
walczy z wysokim kontrastem. Analiza zostaje, bo tłumaczy, dlaczego tryb wysokiego kontrastu
wypadał *gorzej*, a nie lepiej.

**Zgłoszone:** 2026-08-28 z urządzenia. W ciemnym motywie wygląda dobrze, w jasnym źle, a w trybie
wysokiego kontrastu jeszcze gorzej.

**Trop, i jest jednoznaczny.** Wszystkie trzy kolory sceny są rozjaśniane **w stronę bieli**:

```kotlin
val digitColor = lerp(colors.enteredDigit, Color.White, DIGIT_COLOR_LIFT)   // 0.62
val gridColor  = lerp(colors.blockLine,    Color.White, GRID_COLOR_LIFT)    // 0.48
val boardColor = lerp(background,          Color.White, BOARD_BACKGROUND_LIFT) // 0.13
```

Na ciemnym tle rozjaśnianie oddala od tła i scena świeci. Na jasnym robi dokładnie odwrotnie —
**zbliża wszystko do tła** i wypłukuje kontrast. Stała ma jeden znak, a potrzebuje dwóch kierunków.

**Dlaczego wysoki kontrast wypada gorzej, a nie lepiej.** Ten tryb istnieje po to, żeby zwiększać
rozdzielenie. Rozjaśnianie w stronę bieli działa wtedy przeciwko niemu — im mocniej tryb rozsuwa
kolory, tym mocniej scena je z powrotem ściąga.

**Kierunek naprawy.** Rozjaśnianie zastąpić przesunięciem **od tła**, nie ku bieli: w jasnym motywie
w stronę czerni, w ciemnym w stronę bieli. Wtedy jedna stała opisuje „o ile odsunąć od tła" zamiast
„o ile zbielić", i działa w obu motywach oraz nie walczy z wysokim kontrastem.

**Uwaga o zakresie.** Codex pracuje równolegle nad kontrastem palet w `ui/theme/`. To jest osobna
usterka, w `ui/home/`, i dotyczy wyłącznie rozjaśnień samej grafiki — ale obie zmiany spotkają się
na ekranie, więc druga z nich powinna zostać obejrzana po scaleniu pierwszej.

### ~~Dwa kliknięcia „wstecz" w Ustawieniach zostawiają pusty ekran~~ — naprawione 2026-08-28

**Zgłoszone:** 2026-08-28 z urządzenia, **100% powtarzalności**. Aplikacja wraca na ekran główny,
natychmiast go opuszcza i zostaje pusta karta, z której nie da się nic zrobić.

**Trop.** Ekran główny nie ma strzałki wstecz, Ustawienia mają — i wołają `navController::popBackStack`
bez żadnego zabezpieczenia (`KratkozaNavHost`, wszystkie ekrany podrzędne robią to tak samo). Dwa
szybkie kliknięcia to dwa wywołania: pierwsze zdejmuje Ustawienia, drugie zdejmuje **ekran główny**,
bo nikt nie sprawdza, czy jest jeszcze co zdejmować. Zostaje `NavHost` z pustym stosem — nie ma czego
narysować, a Activity się nie kończy, więc nie ma też wyjścia.

**To ta sama klasa błędu co podwójny „Start" w Daily**: dwa zdarzenia wchodzą, zanim pierwsze zdąży
zmienić stan. Tamto rozwiązanie — blokada w warstwie, która naprawdę pilnuje reguły — jest tu
wskazówką, ale nie da się przepisać wprost: to nawigacja, nie baza.

**Rozwiązanie.** Jedno miejsce, `navigateBackOnce`, przez które przechodzą wszystkie sześć powrotów.
Blokadą jest cykl życia wpisu, z którego przyszło naciśnięcie: ekran schodzący ze stosu nie jest już
RESUMED, więc drugie wywołanie nie ma na czym zadziałać. W przeciwieństwie do flagi w każdym ekranie
nie ma tu stanu do wyzerowania i nie da się o tym zapomnieć przy siódmym ekranie.

Sprawdzone w obie strony: bez blokady test pada na „the stack must not be empty", a pojedyncze
naciśnięcie dalej wraca — blokada nie zamieniła się w odmowę.

### ~~Podwójne kliknięcie „Start" otwiera dwie plansze Daily~~ — naprawione 2026-08-28

**Potwierdzone na urządzeniu 2026-08-28** przez zgłaszającego.

**Zgłoszone:** 2026-08-27. Testowane na buildzie 0.16.0 (26).

Przyczyną były dwa równoległe wywołania `startDaily`, które odczytywały brak zapisu, zanim
którekolwiek zdążyło utworzyć podejście. `GameRepository` serializuje teraz sprawdzenie i zajęcie
planszy mutexem, a `DailyViewModel` dodatkowo odrzuca kolejne naciśnięcie podczas otwierania.

Test z dwiema naprawdę równoległymi korutynami odtwarzał błąd na starym kodzie (powstawały dwa
podejścia), a po naprawie potwierdza jeden wpis historii i jeden zapis. Nadal do sprawdzenia na
urządzeniu: szybkie dwukrotne naciśnięcie i przejście do gry.

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

### ~~Tablica Daily ma otwierać się zawsze na „dziś"~~ — zrobione 2026-08-29

**Zgłoszone:** 2026-08-29. Nakładka Play Games zapamiętuje ostatnio wybrany zakres czasu, więc gracz,
który raz przełączył na „wszech czasów", widzi go już zawsze — także przy tablicy dziennej, gdzie
sens ma wyłącznie dzisiejszy dzień.

**Da się to wymusić i wiadomo czym.** `LeaderboardsClient` ma przeciążenie
`getLeaderboardIntent(id, timeSpan)`, a `LeaderboardVariant.TIME_SPAN_DAILY` to zakres dzienny.
`PlayGamesRanking.show` woła dziś wersję jednoargumentową, która zostawia wybór nakładce.

**Do rozstrzygnięcia przy okazji:** czy wymuszać zakres także dla tablic poziomów i dla sumy wszech
czasów, czy tylko dla dziennej. Dla sumy wszech czasów zakres dzienny nie miałby sensu.

### ~~Porządki w dokumentacji i skryptach~~ — zrobione 2026-08-29

**Zebrane 2026-08-29.** Drobne, tanie, wszystkie o tym samym: pliki opisują stan, którego już nie ma.

- `test-kratkoza.sh` wypisuje `✅ 0 tests passed`, gdy testy w ogóle nie ruszą. Zielony znaczek przy
  zerze testów to dokładnie ta cisza, przed którą zabezpiecza `ARCHITECTURE.md` §9 — skrypt powinien
  traktować brak wyników jako błąd.
- `ARCHITECTURE.md` §10 „Czego jeszcze nie ma" wymienia jako brakujące Daily, ranking, XP, score,
  podpowiedź 8/9, palety i tryb wysokiego kontrastu. Wszystkie istnieją.
- Checkboxy w `PLAY_CONSOLE.md` pokazują jako niezrobione konto dewelopera, aplikację, ikonę,
  grafikę, identyfikatory tablic i publikację Play Games.
- Wishlist 9 (tytuł wtopiony w animację) jest zrobiona i nieoznaczona.
- Pozycja „Usunąć kartę Ranking (debug)" jest nieaktualna — zapadła decyzja, że log zostaje
  w Ustawieniach.

Wszystkie odhaczone. `PLAY_CONSOLE.md` ma jeszcze osiem pozycji otwartych i wszystkie są prawdziwe:
kopia klucza upload, cztery formularze, opisy, zrzuty ekranu i `mapping.txt` przy wysyłce.

## Gałęzie

- **`master`** — `b1c0d29`, równy developowi, 0.31.0 (50). Aktualizowany rzadko, decyzją właściciela.
- **`develop`** — gałąź główna, wszystko wchodzi tutaj i stąd wychodzi.
- **`feature/hypothesis-tools`** — **odłożona**. Narzędzia do symulacji założeń; trzy podejścia
  wróciły z urządzenia zepsute, model właściwy opisuje pozycja 14 w [WISHLIST.md](WISHLIST.md).
  Jej porzucony schemat 6 nie może już wrócić pod tym numerem; oficjalna migracja 6→7 służy wyłącznie
  odzyskaniu telefonów, które zdążyły go uruchomić.
- **`feature/daily-expiry-and-hint-tracking`** — bieżące zadanie Codexa, odbite od `develop`.
- **`docs/full-code-review`**, **`measure/difficulty-ladder`** — stare, niescalone.

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

### ~~Podpowiedź 8/9 niedostępna w grach rankingowych~~ — zrobione

Wstrzymywana w rankingowych Daily i wracająca, gdy plansza straci kwalifikację. Pilnują tego dwa
testy w `GameViewModelTest`.

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

### ~~Daily Sudoku — etap 2: ranking publiczny~~ — zrobione 2026-08-28

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

### ~~Wynik rankingowy Daily~~ — zrobione 2026-08-27

`DailyScore` liczy wynik z wagą poziomu, czasu i błędów. Wzór i czasy odniesienia opisuje
[OPEN_QUESTIONS.md](OPEN_QUESTIONS.md) §9.

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

### ~~Podpowiedź dla gracza i licznik użyć~~ — zrobione 2026-09-02

**Zgłoszone:** 2026-08-26. `project.md` §18 zostawia tę funkcję do ustalenia.

Podpowiedź wstawia poprawną cyfrę w zaznaczone wolne pole. Każde skuteczne odsłonięcie zwiększa
licznik próby; kliknięcie bez zaznaczenia, na wypełnionym polu albo w grze rankingowej nie jest
liczone. Licznik przeżywa ponowne uruchomienie, nie cofa się z Undo i trafia do historii gry oraz
trwałego archiwum rozwiązanych plansz Solo. Bieżąca wartość jest widoczna przy pustej żarówce na
ekranie gry, archiwum pokazuje ją na liście i w szczegółach, a Statystyki sumują ją dla ukończonych
gier.

Gra rankingowa nie oferuje podpowiedzi i `GameViewModel` odrzuca również bezpośrednie wywołanie.
Po końcu cyklu Daily staje się nierankingowe i odzyskuje tę funkcję.

Nadal do ustalenia:

- czy w grze solo wprowadzić limit użyć (§18 dopuszcza 1–3; obecnie limitu nie ma),
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

**Zostaje do rozstrzygnięcia:** czy suma wszech czasów ma dalej iść za resetem statystyk —
przeniesione do [OPEN_QUESTIONS.md](OPEN_QUESTIONS.md).

### ~~Usunąć kartę „Ranking (debug)"~~ — rozstrzygnięte inaczej 2026-08-28

Karta zniknęła z ekranu Daily, ale sam log **został** i przeniósł się do Ustawień, pod numer wersji,
wyłącznie w buildach debug. Natrętna była karta na ekranie, na którym się gra, a nie sam zapis — to on
rozwiązał śledztwo nad wysyłką wyników po tym, jak Toasty zawiodły dwa razy.

### ~~Rozciąć `GameRepository`~~ — zrobione 2026-08-28

571 linii rozcięte na trzy: `GameRepository` (368) trzyma cykl życia gry i udostępnianie planszy,
`DailyRepository` (182) kalendarz i podejścia Daily, `StatisticsRepository` (97) rekord gracza.
Otwieranie i wczytywanie gry zostało w pierwszym; pozostałe dwa proszą go o to i mają tylko swoje.

**Zagrożenie, które zapisałem tu wcześniej, zostało sprawdzone, a nie założone.** Mutex chroniący
regułę „jedno podejście dziennie" przeniósł się do `DailyRepository`. Żeby potwierdzić, że dalej
czegokolwiek broni, zamieniłem go tymczasowo na mutex tworzony przy każdym wywołaniu — czyli
dokładnie na opisany tu przypadek „rozmnożony na dwie instancje". Test wyścigu wykrył to
natychmiast, tym samym błędem co pierwotna usterka: `expected:<1> but was:<2>`.

Liczba testów po rozcięciu bez zmian (344), więc nic nie zginęło po drodze.

**Czego nie ruszyłem:** `importSharedBoard` zostało w `GameRepository`. Jest małe i stabilne, a
osobna klasa na jedną metodę dołożyłaby plik, nie zmniejszając pola konfliktów.

### ~~Tablica wszech czasów nieosiągalna, dopóki nie zagra się dzisiaj~~ — zrobione 2026-08-28

**Zgłoszone 2026-08-28 z urządzenia.** Oba przyciski tablic siedzą w karcie „Twoje dzisiejsze
wyniki", która renderuje się tylko przy `entries.any { it.score != null }`. Bez rozegranego dziś
Daily nie ma karty, a razem z nią znikają oba przyciski.

Dla sumy dnia to ma sens. Dla **sumy wszech czasów nie ma żadnego** — ona nie jest o dzisiaj, więc
gracz z dorobkiem z zeszłego tygodnia nie widzi własnej tablicy, dopóki czegoś nie rozwiąże.

Kierunek: wyjąć przyciski z karty wyników do własnej sekcji pokazywanej, gdy gracz jest zalogowany,
niezależnie od tego, co zrobił dzisiaj. Tablica jest warta obejrzenia także wtedy, gdy nie ma się na
niej wyniku.

### ~~Mocniejsze wypalanie iskier~~ — zrobione 2026-08-28

**Zgłoszone 2026-08-28.** Iskry już maleją idąc ku górze (`SPARK_SHRINK = 0.22f`, czyli 22% ubytku
promienia), ale efekt jest za słaby. Docelowo **60%**.

### ~~Symetryzacja wobble w osi yaw~~ — zrobione 2026-08-28

**Zgłoszone 2026-08-28**, po zmierzeniu obu osi przez pełny cykl.

Pitch jest praktycznie symetryczny (−7,45° … +7,27°). **Yaw nie jest: −15,46° … +10,23°** — wychył
w jedną stronę o połowę większy niż w drugą, i to właśnie czyta się jako ruch jednokierunkowy.

Przyczyna nie jest przypadkowa. Suma harmonicznych ma symetryczne skrajności dokładnie wtedy, gdy
faza każdej składowej jest odpowiednią wielokrotnością fazy podstawowej — wtedy cały przebieg jest
tylko przesunięciem w czasie przebiegu o zerowych fazach. W yaw fazy (0; 0,75; 2,20) tego warunku nie
spełniają, w pitch spełniają go dwie z trzech (1,30; 2,60; **0,30** zamiast 5,20).

Po zblokowaniu faz obie osie wychodzą symetryczne co do zera, przy zachowanym przesunięciu pitch
względem yaw, które daje ruchowi charakter figury zamiast przekątnej. Zmierzone po zmianie:
yaw ±12,79°, pitch ±6,33°, asymetria 0,000° na obu osiach, 50/50 czasu po każdej stronie. Szczyt
w „większą" stronę jest mniejszy niż był (12,79° wobec 15,46°) — to cena symetrii, warta obejrzenia
na urządzeniu.

### ~~Matematyka animacji do osobnego pliku~~ — zrobione 2026-08-28

**Zgłoszone 2026-08-28.** `RotatingBoardArt.kt` ma 651 linii, 68 stałych i 18 deklaracji. Szew jest
już wycięty i przetestowany: siedem funkcji `internal` to czysta matematyka, a testy są ułożone
dokładnie wzdłuż tej granicy. Wyniesienie ich zostawia w pliku samo rysowanie.

### ~~Wstrzykiwalne ziarno animacji~~ — zrobione 2026-08-28

**Zgłoszone 2026-08-28.** Cyfry i iskry są zasiewane z `System.nanoTime()` w dwóch miejscach, więc
każde uruchomienie daje inny obrazek. Wizualnie bez znaczenia — ale blokuje taśmę klatek do oceny
animacji (patrz niżej) i każde porównanie „przed/po".

### ~~Generowanie zrzutów ekranu z testów Compose~~ — zrobione 2026-08-29

**Zapisane 2026-08-28 jako pomysł wart sprawdzenia.**

Powiedziałem wcześniej, że zrzuty do sklepu muszą pochodzić z działającej aplikacji na urządzeniu.
To prawdopodobnie nieprawda: Robolectric w trybie natywnej grafiki renderuje realne piksele Compose,
a infrastruktura testów Compose już stoi. Dałoby to zrzuty planszy, Daily i statystyk — w obu
motywach, odtwarzalne po każdej zmianie UI.

**Ciekawsze zastosowanie niż sklep:** animacja nad menu (`RotatingBoardArt`). Chodzi na
`rememberInfiniteTransition`, czyli na zegarze Compose, który w teście da się zatrzymać
(`mainClock.autoAdvance = false`) i przesuwać o dokładne milisekundy. Zamiast jednego szczęśliwego
kadru z telefonu można wyrenderować **taśmę klatek przez cały cykl** i zobaczyć, czy plansza
w którejś fazie się nie spłaszcza, nie ucieka poza kadr albo nie gubi kontrastu. Telefonem tego nie
złapiesz, bo nie zatrzymasz animacji w wybranej fazie.

**Czego nie wiadomo, a trzeba sprawdzić najpierw.** Obrót robi `graphicsLayer { rotationX; rotationY;
cameraDistance }` — transformacja RenderNode, nie rysowanie po Canvasie. Rysowanie 2D (siatka, iskry)
narysuje się na pewno; czy Robolectric honoruje kamerę i obrót w trzech osiach — nie wiadomo. Jeśli
nie honoruje, plansza wyjdzie **płaska**, co wygląda jak zepsuta animacja i może sprowokować
„naprawianie" czegoś, co działa. **Pierwszym krokiem jest jedna klatka porównana z telefonem.**
Reszta jest warta tyle, ile ta weryfikacja.

**Dwie pułapki.** Zawartość animacji jest zasiewana z `System.nanoTime()`, więc każdy przebieg da
inny obrazek — do oceny „czy ładnie" bez znaczenia, do porównań przed/po bezużyteczne; ziarno
musiałoby być wstrzykiwalne. I `waitForIdle()` przy nieskończonej animacji **nigdy nie wraca** —
`autoAdvance` trzeba wyłączyć przed pierwszą kompozycją. Ta sama klasa zawieszenia, którą suite
złapał już dwa razy.

### ~~Wynik Daily zjadany przez zegar ścienny~~ — rozstrzygnięte 2026-08-29

**Zgłoszone:** 2026-08-29 — „zrobiłem easy z jednym błędem i dostałem 12 punktów".

**To nie jest wada wzoru, tylko skutek reguły czasu.** Policzone dla obecnych stałych:

| czas gry | 0 błędów | 1 błąd |
|---|---|---|
| 2 min | 714 | 500 |
| 5 min | 500 | 350 |
| 20 min | 200 | 140 |
| 3 h | 27 | 19 |

12 punktów przy jednym błędzie odpowiada **około 4,6 godziny na liczniku**. Wzór zachował się
poprawnie; policzył czas, który naprawdę upłynął.

**Skąd on się bierze.** Rankingowe Daily mierzy czas zegarem ściennym od otwarcia planszy i nie da
się go zatrzymać (`project.md` §28). Zrobiliśmy to celowo: licznik w aplikacji zatrzymuje się razem
z procesem, więc gracz, który schowałby aplikację na godzinę, dostałby godzinę darmowego myślenia.

**Napięcie, które to tworzy.** Ta sama reguła karze zachowanie zupełnie zwyczajne — otworzyć
planszę, odejść, wrócić wieczorem. Dziś kosztuje to cały wynik, bez żadnego ostrzeżenia.

**Co postanowiono.** Żadne z powyższych. Zamiast ograniczać czas albo odbierać kwalifikację — jedno
i drugie odrzucone jako sprzeczne z założeniem, że gracz robi sudoku na luzie — zmieniono kształt
wzoru: czas **dodaje** do wyniku, zamiast go mnożyć. Rozwiązana plansza jest zawsze warta punktów za
rozwiązanie, a szybkość dokłada bonus, który połowi się co czas docelowy. Szczegóły i liczby
w [OPEN_QUESTIONS.md](OPEN_QUESTIONS.md) §9.

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
