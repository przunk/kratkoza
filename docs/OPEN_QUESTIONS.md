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
| 9 | Wzór na wynik Daily i czasy odniesienia | 🟡 **wstępnie** — wzór przyjęty, czasy do dostrojenia |

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

**Stan: 🟢 ustalone 2026-09-02.** Minimalizacja aplikacji **nie** odbiera kwalifikacji. Zegar biegnie
dalej, także gdy aplikacja jest w tle albo została ubita. Kwalifikacja wygasa natomiast wraz z
cyklem konkretnej planszy, przy następnej zmianie Daily o 07:00 UTC.

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

**Co odbiera kwalifikację:** koniec cyklu planszy. Gra pozostaje dostępna, ale od tej chwili jest
zwykłą rozgrywką nierankingową. Czas naliczony przed granicą nie znika, natomiast po konwersji
zaczynają obowiązywać zwykłe zasady pauzy. Podpowiedź nie jest sposobem rezygnacji z rankingu:
w grze nadal kwalifikującej się do rankingu przycisk nie istnieje i wywołanie jest odrzucane także
przez `GameViewModel`; pojawia się dopiero po wygaśnięciu cyklu.

**Czego to nie broni.** Gracz nadal może zrobić zrzut planszy i rozwiązać ją gdzie indziej — ale
zegar przez ten czas biegnie, więc nic na tym nie zyskuje. `project.md` §31 uznaje wprost, że
całkowite zabezpieczenie Sudoku przed drugim urządzeniem nie istnieje.

---

## 9. ~~Wzór na wynik Daily~~ — rozstrzygnięte 2026-08-29

**Wynik = (punkty za rozwiązanie + bonus za czas) × 0.7 ^ błędy**, gdzie bonus wynosi ⅔ punktów za
rozwiązanie i **połowi się co czas docelowy** poziomu.

Poprzedni wzór **mnożył** przez czas, więc zegar mógł zjeść wszystko: na Łatwym rozpiętość między
szybkim a bardzo wolnym ukończeniem wynosiła **44,6×**. Zgłoszone z urządzenia jako rozwiązana plansza
warta 12 punktów — wzór policzył poprawnie 4,6 godziny na liczniku.

To była wada kształtu, nie stałych. Zegar Daily biegnie od otwarcia i nie da się go zatrzymać (§28),
więc „wolno" najczęściej zapisuje **odejście od telefonu**, a nie długie myślenie. Odejście nie
powinno unieważniać rozwiązanej planszy.

**Co daje nowy kształt:**

| | natychmiast | czas docelowy | dwukrotność | doba |
|---|---|---|---|---|
| Łatwy | 833 | 667 | 583 | 500 |
| Trudny | 1667 | 1333 | 1167 | 1000 |

Rozpiętość szybki/wolny to teraz **5:3**, a nie kilkadziesiąt razy. Bonus nigdy nie znika całkiem, więc
nie ma progu, po którym szybkość przestaje się liczyć — i nie ma widocznego limitu czasu, co było
warunkiem: gracz ma robić sudoku na luzie.

**Kara za błędy bez zmian** — §29 ustala ją wprost i tylko wynik bazowy zależny od czasu był otwarty.

**Konsekwencja dla tablic wyników.** Wyniki zapisane starym wzorem nie są porównywalne z nowymi:
wolne partie są teraz warte znacznie więcej. Tablice i tak mają zostać wymienione na świeże przed
premierą, więc rozwiązuje się to samo — ale gdyby ta decyzja upadła, stare wyniki trzeba by uznać za
nieporównywalne.

**Czasy odniesienia poziomów** (5/9/15/20/25 minut) zostają i mają teraz inne znaczenie: nie są parą
ani terminem, tylko czasem, w którym bonus spada o połowę.

## 9a. Czasy odniesienia poziomów — nadal wybrane z wyczucia

**Stan: 🟡 wstępnie przyjęte 2026-08-27.** `project.md` §39 zostawia wzór na wynik bazowy otwarty;
§29 ustala jedynie karę za błędy.

    wynik = 2 × baza(poziom) × cel(poziom) / (cel + czas) × 0.7 ^ błędy

| poziom | baza | cel |
|---|---|---|
| Łatwy | 500 | 5 min |
| Normalny | 700 | 9 min |
| Trudny | 1000 | 15 min |
| Ekspert | 1400 | 20 min |
| Morderczy | 2000 | 25 min |

**Co robi każdy człon.** Baza niesie wagę poziomu — pierwsza wersja punktowała wyłącznie osiągi
względem poziomu, więc sam poziom znikał z odpowiedzi i bezbłędny Łatwy bił dobrego Morderczego.
Człon czasowy wynosi dokładnie 1 przy czasie równym celowi, więc **cel jest miarą, nie limitem**:
trafiony daje bazę, pobity podnosi mnożnik ku dwóm, przekroczony opada gładko i nigdy nie sięga
zera. Ostatni człon to §29 dosłownie.

**Dlaczego nie zerujemy przy trzech błędach**, mimo propozycji. Po pierwsze §29 podaje wprost
mnożnik i tabelę procentów. Po drugie — i ważniejsze — wynik zerowy kończyłby grę we wszystkim poza
literą: gracz z trzema błędami nie miałby o co grać, a `project.md` §16 mówi, że gra nie ma systemu
żyć i błąd nie blokuje rozgrywki. Sedno zgłoszenia („błędy za mało bolą") załatwia natomiast waga
poziomu: **jeden błąd na Morderczym kosztuje 600 punktów, czyli więcej, niż wart jest bezbłędny
Łatwy.** Wcześniej mnożnik działał na wynik, który i tak nie zależał od poziomu.

**Czasy odniesienia dobrane oceną.** Próba wyprowadzenia ich z solvera się nie udała i warto to
zapisać: kroków logicznego rozwiązania jest 45 / 58 / 63 dla ŁATWY / TRUDNY / MORDERCZY — rozrzut
niespełna półtorakrotny, gdy ludzki jest kilkukrotny. O trudności decyduje **znalezienie** techniki,
a nie jej zastosowanie, i żadna liczba zastosowań tego nie widzi. Dostroić, gdy będą prawdziwe czasy.

---

## Czy reset statystyk ma zerować sumę wysyłaną do rankingu?

**Otwarte od 2026-08-28.** Właściciel projektu chce najpierw pożyć z tym trochę i zdecydować
z doświadczenia, a nie z rozumowania — więc pytanie czeka, a zachowanie zostaje takie, jak jest.

**Jak jest teraz.** Suma wszech czasów jest przeliczana z lokalnej historii, którą reset statystyk
czyści. Tablica po stronie Google trzyma najlepszy wynik i nic o resecie nie wie, więc po resecie
wysyłane sumy są niższe niż to, co tablica już ma, i są ignorowane, dopóki nowa suma nie przekroczy
starej. Lokalny rekord i publiczny rozjeżdżają się wtedy na stałe.

**Co przemawia za zostawieniem.** „Reset statystyk" znaczy dokładnie to, co mówi: zerujesz swój
lokalny rekord i wszystko, co się z niego liczy. Wyjątek dla jednej liczby byłby regułą, o której
nikt się nie dowie, dopóki go nie zauważy.

**Co przemawia za zmianą.** Suma wszech czasów jest rekordem publicznym, a nie prywatnym. Gracz,
który czyści swoje statystyki, prawdopodobnie chce zacząć od nowa u siebie, a nie wypaść z rankingu,
w którym już jest — i przez pewien czas widzieć, że jego wyniki „nie działają".

**Czego nie da się zrobić — sprawdzone w SDK 2026-08-28.** Skasować wyniku gracza z aplikacji.
Całe API `LeaderboardsClient` w `play-services-games-v2:20.1.2` to odczyt i wysyłka:
`loadTopScores`, `loadPlayerCenteredScores`, `loadCurrentPlayerLeaderboardScore`,
`loadLeaderboardMetadata`, `loadMoreScores`, `getLeaderboardIntent`, `getAllLeaderboardsIntent`,
`submitScore`, `submitScoreImmediate`. **Żadnej metody kasującej ani zerującej** — i nic takiego nie
występuje w całym SDK w klasach tablic, wyników i graczy.

Kasowanie istnieje wyłącznie w Management API, po stronie serwera, na koncie usługi — i tylko dla
kont testerów, dopóki gra nie jest opublikowana. Aplikacja nie ma tam wstępu.

Padło pytanie, czy reset statystyk mógłby wyczyścić także ranking gracza, za dodatkowym
potwierdzeniem. **Nie może.** Dialog obiecywałby coś, czego nie da się dotrzymać, a tablica trzyma
najlepszy wynik, więc nie da się go nawet obniżyć przez wysłanie zera.

**Co zostaje do wyboru, skoro kasowanie odpada:**

1. **Zostawić jak jest.** Po resecie wysyłane sumy są niższe od tego, co tablica trzyma, i są
   ignorowane, dopóki nie przebiją starej wartości. Gracz przez pewien czas widzi, że jego wyniki
   „nie działają", i nie ma jak się dowiedzieć dlaczego.
2. **Liczyć sumę wszech czasów z osobnego licznika, którego reset nie dotyka**, i napisać w dialogu
   resetu, że rankingu publicznego to nie obejmuje. Tablica pozostaje spójna, znika efekt martwych
   wyników, a „reset statystyk" dalej znaczy „zeruję swój lokalny rekord" — bo suma publiczna nigdy
   nie była lokalnym rekordem.

Wariant 2 jest bliższy temu, o co pytano, i usuwa jedyną realną szkodę. Kosztuje licznik, który
przeżywa reset, i jedno zdanie w dialogu.

---

## Zmierzona drabinka trudności (2026-08-29)

Sto plansz na poziom, każda oceniona przez `LogicalSolver`. Mierzona jest **ranga najtrudniejszej
techniki, której plansza naprawdę wymaga** — nie pułap, który poziom ma dozwolony.

| poziom | pułap | średnia wymagana ranga | maks. | kroki | podpowiedzi |
|---|---|---|---|---|---|
| Łatwy | 2 | 1,15 | 2 | 45,0 | 36,0 |
| Normalny | 2 | 1,99 | 2 | 56,5 | 24,5 |
| Trudny | 3 | 3,00 | 3 | 59,4 | 24,5 |
| Ekspert | 5 | 4,35 | 5 | 61,8 | 24,6 |
| Morderczy | 10 | 8,72 | 9 | 61,9 | 24,9 |

### Pięć rzeczy, które z tego wynikają

**Pełna drabinka jest równa na czterech szczeblach i pęka na ostatnim:** +0,84, +1,01, +1,35, a potem
**+4,37**. Ekspert kończy się na ukrytej parze, Morderczy w 87% wymaga XY-Wing.

**Daily nie używa pełnej drabinki.** Bierze Łatwy, Trudny i Morderczy, więc jego skoki to **+1,85
i +5,72** — mniej więcej dwa i cztery razy więcej niż zwykły szczebel.

*Sprostowanie (2026-08-29):* pomiar powstał po tym, jak właściciel odpuścił Trudnego w Daily — ale
z braku czasu, nie dlatego, że był nieosiągalny. Zapisałem to wtedy jako „za trudny do ukończenia"
i było to nadinterpretacją. Nierówność drabinki jest zmierzona i broni się sama; powód, dla którego
zaczęliśmy mierzyć, był łagodniejszy.

**Skok Łatwy → Trudny jest podwójny.** Rośnie nie tylko technika, ale i liczba podpowiedzi: **36 → 24,5**.
Łatwy jest jedynym poziomem z gęstą planszą; wszystkie pozostałe mają około 24,5. Gracz przechodzący
w Daily z Łatwego na Trudny dostaje jednocześnie nową technikę i jedenaście podpowiedzi mniej.

**Łatwy jest bardzo łatwy.** 85 na 100 plansz da się rozwiązać samymi nagimi singlami; ukryty singiel
bywa potrzebny w 15 przypadkach.

**Pułap Morderczego jest tylko nominalny.** Dozwolony jest Swordfish (ranga 10), ale **żadna ze stu
plansz go nie wymagała** — najwyższa zaobserwowana ranga to 9. Morderczy jest w praktyce poziomem
XY-Wing, a Swordfish nie ma zastosowania.

### Co to znaczy dla czasów odniesienia w punktacji

Liczba kroków logicznych rośnie od Normalnego do Morderczego o **10%** (56,5 → 61,9), a czasy
odniesienia zakładają rozpiętość **pięciokrotną** (5 → 25 minut). Cała różnica musi więc leżeć w czasie
*wypatrzenia* techniki, a nie w ilości pracy. To potwierdza notatkę przy `targetMinutesFor`, że próba
wyprowadzenia czasów z licznika kroków zawiodła — ale też pokazuje, że pięciokrotność jest
oszacowaniem z wyczucia i nic jej nie potwierdza.

### Co z tym zrobiono (2026-08-29)

**Daily bierze teraz Łatwy / Trudny / Ekspert**, a Łatwy został rozrzedzony z 36 do 30 podpowiedzi.
Zmierzone ponownie po zmianie:

| | przed | po |
|---|---|---|
| podpowiedzi w Łatwym | 36,0 | 30,0 |
| średnia wymagana ranga | 1,15 | 1,71 |
| kroki | 45,0 | 51,0 |
| plansz na samych nagich singlach | 85% | 29% |

Rozrzedzenie zmieniło Łatwy **w rodzaju, nie tylko w długości**: ukryty singiel jest teraz potrzebny
w 71 planszach na 100 zamiast w 15. Pułap się nie zmienił, więc poziom dalej jest „na singlach" — po
prostu przestał być planszą, którą wypełnia się bez patrzenia.

**Skoki w Daily po obu zmianach:**

| | przed | po |
|---|---|---|
| pierwszy szczebel | +1,85 | **+1,29** |
| drugi szczebel | +5,72 | **+1,35** |

Drabinka dnia jest teraz równa i mieści się w rozstępie zwykłego szczebla pełnej drabinki
(+0,84 … +1,35). Przepaść przed najtrudniejszym poziomem zniknęła.

**`RULESET_VERSION` podbite do 2**, bo zestaw poziomów jest częścią przepisu: wiersze zapisane pod
jedynką opisują dzień, którego najtrudniejszą planszą był Morderczy, i są pomijane zamiast używane.

**Morderczy zostaje jako poziom gry solo.** Nie jest już poziomem Daily, ale nadal istnieje — i nadal
nigdy nie wymaga Swordfisha.

### Do decyzji

Skok Trudny → Morderczy w Daily jest ponad czterokrotnie większy niż zwykły szczebel drabinki.
Możliwe kierunki, żadnego nie wybieram sam:

- **Daily bierze Łatwy / Trudny / Ekspert** — skoki +1,85 i +1,35, czyli górny szczebel przestaje być
  przepaścią, a wejście dla mniej wprawnych zostaje.
- **Daily bierze Łatwy / Normalny / Trudny** — najłagodniejsze, ale najtrudniejszy poziom dnia wymaga
  wtedy tylko locked candidates, co dla wprawnych będzie za mało.
- **Zostaje jak jest**, ze świadomością, że najtrudniejszy poziom Daily jest dla większości graczy
  nieosiągalny.

Osobno warto rozważyć **dogęszczenie Łatwego albo rozrzedzenie go do 30 podpowiedzi**, żeby przejście
do kolejnego poziomu nie było podwójne.
