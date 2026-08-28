# Kratkoza — wishlista

Pomysły zgłoszone w trakcie pracy, które **nie są** wymaganiami z `project.md` i nie mają
przypisanego etapu. Nic stąd nie trafia do kodu bez osobnej decyzji.

Rzeczy, które `project.md` §40 nazywa „nice to have", są tam — nie tutaj. Ta lista zbiera to,
czego w specyfikacji w ogóle nie ma.

---

## 1. Grafika albo powolna animacja nad menu

**Zrobione 2026-08-26 — wariant B.** Obracający się w przestrzeni kwadrat 3×3 z cyframi,
które pojawiają się i znikają. Zostaje niżej jako zapis rozważanych wariantów.

**Rozwinięte 2026-08-28.** Kratka, cyfry i iskierki są osobnymi warstwami paralaksy: w spoczynku
zachowują płynny sinusoidalny wobble, pod palcem reagują na położenie, a po puszczeniu wracają do
aktualnego ruchu automatycznego. Scena korzysta bezpośrednio z tła ekranu, plansza jest jaśniejszą,
nieprzezroczystą płaszczyzną, a cienie cyfr są rzutowane na nią z osobnej warstwy. Iskry parują od
dołu ku górze i dzielą się na lecące za planszą, między planszą i cyframi oraz przed cyframi. Efekt
pozostaje czystą dekoracją Compose, bez sensorów i nowych zależności.

**Zgłoszone:** 2026-08-25.

Ekran główny jest czysty, ale pusty nad przyciskami. Pomysł: wstawić tam grafikę albo
minimalistyczną, powolną animację — na przykład pole 3×3, w którym cyfry same się układają.

**Dlaczego to nie jest oczywiste:**

- `project.md` §36 wymaga, żeby animacje były subtelne i szybkie i nie odciągały nadmiernie
  uwagi. Dotyczy to ekranu gry, ale duch tego samego wymagania stosuje się do menu.
- Animacja w tle na ekranie głównym chodzi, dopóki ktoś na niego patrzy. Trzeba ją zatrzymać,
  gdy aplikacja idzie w tło, inaczej pali baterię za nic.
- Musi wyglądać dobrze w obu motywach i nie walczyć o uwagę z przyciskiem „Kontynuuj", który
  jest tam najważniejszy.

**Warianty:**

- **A — statyczna grafika.** Najtaniej, zero ryzyka, żadnego kosztu wydajności. Wymaga
  narysowania czegoś ładnego; obecny zestaw ikon daje punkt wyjścia dla stylu.
- **B — powolna animacja pola 3×3.** Cyfry pojawiają się i znikają w rytmie kilku sekund,
  bardzo niski kontrast, żeby nie krzyczała. Da się zrobić samym Compose, bez zależności.
- **C — plansza z ostatniej rozgrywki jako tło.** Powiązane z treścią, ale zaśmieca ekran
  i dubluje miniaturę z listy zapisanych gier.

Rekomendacja: **B**, jeśli ma być ruch — z twardym wymogiem zatrzymania animacji, gdy ekran
nie jest widoczny. **A**, jeśli priorytetem jest spokój.

---

## 2. Subtelne potrząśnięcie planszą przy błędzie

**Zrobione 2026-08-26 — wariant A.** Drgnięcie samej komórki, nie całej planszy.

**Zgłoszone:** 2026-08-25.

Dziś błędnie wpisana cyfra rozświetla na czerwono swój wiersz, kolumnę i blok. Pomysł: dołożyć do
tego krótkie, delikatne drgnięcie całej planszy — sygnał, który czuje się kątem oka, nie patrząc
wprost na pole.

**Dlaczego to nie jest oczywiste:**

- `project.md` §36 wymienia animacje dla wpisania cyfry i ukończenia sekcji. Potrząśnięcia tam nie
  ma, a §36 zabrania animacjom przeszkadzać w szybkim wpisywaniu — ruch całej planszy w trakcie
  serii wpisów łatwo przekracza tę granicę.
- Ruch całego ekranu bywa problemem dla osób wrażliwych na animacje (choroba lokomocyjna,
  przedsionkowe zaburzenia równowagi). Android ma systemowe ograniczenie animacji i Compose je
  respektuje, więc trzeba się upewnić, że drgnięcie też pod nie podlega.
- Musi być bardzo krótkie i o małej amplitudzie, inaczej stanie się karą, a §16 mówi wprost, że
  błąd nie kończy gry i nie blokuje rozgrywki.

**Warianty:**

- **A** — drgnięcie samej komórki, nie całej planszy. Najmniej inwazyjne, nie rusza układu.
- **B** — drgnięcie całej planszy, 2–3 wychylenia po kilka pikseli, poniżej 200 ms.
- **C** — bez ruchu, zamiast tego krótka wibracja (haptyka). Nie przeszkadza wzrokowo i działa
  także wtedy, gdy gracz patrzy na panel cyfr, a nie na planszę.

Rekomendacja: **A albo C.** Potrząśnięcie całą planszą wygląda efektownie na filmiku i męczy przy
setnej pomyłce.

---

## 3. Kandydaci ograniczeni do cyfr dozwolonych w polu

**Zgłoszone:** 2026-08-26.

W trybie kandydatów pozwalać dodać tylko cyfrę, która nie występuje już w wierszu, kolumnie ani
bloku 3×3 wybranego pola. Panel cyfr powinien od razu pokazywać, których kandydatów nie można
dodać, zamiast przyjmować notatkę sprzeczną z aktualnym stanem planszy.

Do decyzji pozostaje zachowanie istniejących kandydatów po późniejszym wpisaniu błędnej cyfry;
auto-usuwanie po cyfrze poprawnej jest opisane osobno poniżej i w `project.md` §15.

**Pułapka, którą trzeba tu rozstrzygnąć.** „Dozwolona" liczy się względem **aktualnej** planszy,
a ta może zawierać błędną cyfrę gracza. Wtedy aplikacja zabroniłaby zanotować kandydata, który
w rzeczywistości jest poprawny — i to dokładnie wtedy, gdy gracz próbuje rozplątać własną pomyłkę.
To ten sam wzorzec, na który wpadliśmy przy blokadzie klawiszy panelu: licznik liczył wszystkie
wystąpienia cyfry zamiast poprawnych, więc jedna pomyłka odcinała gracza od cyfry, której plansza
wciąż potrzebowała.

Bezpieczny wariant: liczyć dostępność wyłącznie względem cyfr startowych i cyfr poprawnych, albo
w ogóle nie blokować, tylko wyszarzać — kandydaci są narzędziem rozumowania gracza (§14), a nie
odpowiedzią, więc aplikacja nie musi mieć w tej sprawie ostatniego słowa.

---

## 4. Usuwanie kandydatów, które przestały być możliwe

**Zgłoszone:** 2026-08-26.

Po wpisaniu przez gracza poprawnej cyfry automatycznie usuwać tę cyfrę z kandydatów wszystkich pól
w tym samym wierszu, kolumnie i bloku 3×3.

To zachowanie jest już wymagane przez `project.md` §15 i deklarowane jako zaimplementowane. Wpis
pozostaje jako punkt do świadomej weryfikacji na urządzeniu oraz ochrony testem regresyjnym.

---

## 5. Czyszczenie wszystkich kandydatów

**Zgłoszone:** 2026-08-26.

Dodać dostępną z ekranu gry akcję usuwającą wszystkich kandydatów z całej planszy. Operacja powinna
wymagać potwierdzenia i dać się cofnąć jako jedna akcja Undo, żeby przypadkowe dotknięcie nie niszczyło
notatek gracza.

---

## 6. Udostępnianie planszy innemu graczowi

**Zrobione 2026-08-26.** Kod niesie układ planszy, nie ziarno; poziom trudności jest przeliczany po
stronie odbiorcy. Uzasadnienie obu decyzji w `BoardCode`. Udostępnianie z listy zapisanych gier,
wczytywanie z ekranu nowej gry.

Udostępnianie jest w dwóch miejscach: w pasku ekranu gry, gdzie gracz najpierw sięgnie, i na
liście zapisanych gier — dla plansz, których akurat nie ma otwartych.

**Zgłoszone:** 2026-08-26.

Umożliwić przekazanie kodu planszy, aby inny gracz mógł rozegrać dokładnie ten sam układ. Kod może
opierać się na `puzzleId`/GUID, ale musi jednoznacznie wskazywać także dane potrzebne do odtworzenia
planszy — np. wersję generatora i seed — albo zawierać zakodowany układ. Sam losowy GUID bez wspólnej
bazy lub deterministycznego mapowania nie wystarczy do wygenerowania tej samej planszy.

Do ustalenia: format kodu, udostępnianie offline lub przez backend oraz zachowanie po zmianie wersji
generatora.

---

## 7. Wybór regionu z mapki zamiast z listy

**Zgłoszone:** 2026-08-26.

Zamiast wybierać województwo z listy — tapnąć je na mapce Polski.

**Dlaczego to dodatek, a nie zamiennik.** `project.md` §27 przewiduje zakresy globalny, krajowy
i regionalny, a aplikacja jest po polsku i angielsku, więc trafi też poza Polskę — mapa województw
nie ma wtedy czego pokazać. Do tego mapa dotykowa jest trudna dla TalkBacka: szesnaście obszarów
o nieregularnych kształtach, bez tekstu. Lista musi więc istnieć tak czy inaczej, jako ścieżka
dostępności i jako rozwiązanie dla innych krajów.

Mapa ma sens jako **efektowna skrótka do tego samego wyboru**, wywoływana z listy i wracająca do
niej — a nie jako drugi, niezależny mechanizm, który trzeba utrzymywać w dwóch wersjach.

Decyzje funkcjonalne wokół regionu są już podjęte i opisane w [STATUS.md](STATUS.md); ta pozycja
dotyczy wyłącznie formy wyboru.

---

## 8. Podświetlenie cyfry klikniętej na klawiaturze

**Zgłoszone:** 2026-08-28. Pomysł użytkownika, widziany w innej aplikacji do sudoku.

Zgłaszający opisał to tak: gdy zaznaczone jest pole, którego nie da się nadpisać, kliknięcie cyfry
na klawiaturze zaznacza pierwsze jej wystąpienie od góry i podświetla wszystkie takie same na
planszy.

**Połowa tego już działa.** Podświetlanie takich samych cyfr po zaznaczeniu pola jest wymaganiem
`project.md` §10, jest zaimplementowane i domyślnie włączone (`highlightSameDigits`). Nowa jest
wyłącznie część o wywołaniu tego **z klawiatury**, bez zaznaczonego pola z tą cyfrą.

**Dlaczego to nie jest oczywiste:**

- **Ten sam gest zacząłby znaczyć dwie różne rzeczy.** Kliknięcie „5" znaczy „wstaw tu piątkę".
  W propozycji czasem znaczy „zabierz mnie do jakiejś piątki", a decyduje o tym, czy zaznaczone pole
  akurat jest zajęte — stan, którego gracz patrzący na planszę nie śledzi.
- **Przesunięcie zaznaczenia jest efektem ubocznym, o który nikt nie prosił.** Zaznaczenie to miejsce
  gracza na planszy; po przeskoku **następne** kliknięcie działa gdzie indziej, niż zakłada. Pomyłka
  przestaje być nieszkodliwa i zaczyna się kaskadować.
- **„Pierwsze z góry" jest arbitralne.** Gdyby gracz chciał obejrzeć konkretną piątkę, kliknąłby
  w nią.
- **§10 stawia warunek czytelności:** podświetlenie nie może utrudniać odczytania bieżącego pola,
  wiersza, kolumny, bloku i błędów. Podświetlenie wywołane z klawiatury obejmuje więcej pól naraz niż
  to z zaznaczenia, więc kontrast trzeba obejrzeć w obu motywach i w trybie wysokiego kontrastu.

**Wariant proponowany zamiast oryginalnego:** kliknięcie cyfry, której **nie da się wpisać**,
podświetla wszystkie jej wystąpienia i **nie rusza zaznaczenia**. Ta sama potrzeba („pokaż mi, gdzie
są piątki"), efekt czysto wizualny i odwracalny.

Ma dwie zalety, których wariant z przeskokiem nie ma:

- **Ożywia martwy przycisk.** Dziś, gdy cyfra jest już rozdystrybuowana po całej planszy,
  `GameViewModel.enterDigit` po prostu wychodzi i kliknięcie nie robi **nic**. Podświetlenie daje
  temu klawiszowi sens dokładnie wtedy, gdy stracił poprzedni.
- **Zgadza się z §19**, które przewiduje wizualne wygaszenie przycisku po wykorzystaniu wszystkich
  dziewięciu wystąpień. Wygaszony przycisk, który dalej umie pokazać, gdzie te dziewiątki są, to
  spójna całość, a nie wyjątek.

**Czego wymaga przed realizacją:**

- rozstrzygnięcia, **jak się z tego wychodzi** — kliknięcie w pole, ponowne kliknięcie tej samej
  cyfry, czy jedno i drugie,
- rozstrzygnięcia **pierwszeństwa**, gdy naraz jest zaznaczone pole z cyfrą i „przypięta" cyfra
  z klawiatury; bez tego powstanie druga cicha reguła,
- decyzji, czy to osobna opcja w ustawieniach, czy zachowanie idące za istniejącym
  `highlightSameDigits`.

---

## 9. Tytuł wtopiony w animację zamiast nagłówka po lewej

**Zgłoszone:** 2026-08-28.

Dziś ekran główny ma zwykły pasek z tytułem „Kratkoza" wyrównanym do lewej, a pod nim osobny obszar
z animacją. Pomysł: **usunąć pasek** i oddać całą górną część ekranu animacji, a nazwę narysować
w jej obrębie — u góry, nad kratką, grubymi literami na całą szerokość.

**Dlaczego to nie jest tylko przesunięcie napisu:**

- Pasek to `KratkozaScaffold`, wspólny dla wszystkich ekranów. Ekran główny musiałby przestać go
  używać albo dostać wariant bez paska — a wtedy trzeba zdecydować, co z górnym wcięciem systemowym,
  które dziś załatwia `TopAppBar`.
- Napis na całą szerokość, grubymi literami, przestaje być tekstem interfejsu i staje się elementem
  grafiki. Musi więc znieść oba motywy, tryb wysokiego kontrastu i powiększoną czcionkę systemową —
  a `project.md` §8 pozwala jej nie respektować tylko tam, gdzie niszczy geometrię planszy.
- Animacja porusza się i przechyla pod palcem. Trzeba rozstrzygnąć, czy napis **należy do sceny**
  i przechyla się razem z nią, czy stoi nieruchomo nad nią. To dwie różne rzeczy wizualnie i tylko
  pierwsza wymaga wciągnięcia go w warstwy paralaksy.
- Ekran główny jest jedynym miejscem, gdzie widać nazwę aplikacji. Po usunięciu paska tekst musi
  zostać dostępny dla TalkBacka, a dziś grafika jest przed nim celowo ukryta
  (`clearAndSetSemantics`).

---

## 10. Cyfry w grafice tytułowej przechodzące jedna w drugą

**Zgłoszone:** 2026-08-28.

Dziś cyfra znika i pojawia się następna — zmiana trwa jedną klatkę. Pomysł: żeby jedna cyfra
**przelewała się** w drugą, plastycznie, tak jak zmienia się kształt z gliny.

**Czy to możliwe: tak, ale nie przez przenikanie.** Przenikanie dwóch cyfr daje moment, w którym obie
są widoczne naraz i półprzezroczyste — czyta się to jako duch, nie jako zmiana kształtu. To jest
tanie i można zrobić od ręki, ale nie o to chodzi w zgłoszeniu.

**Prawdziwe morfowanie wymaga, żeby obie cyfry były tym samym rodzajem opisu.** Interpolacja między
konturami z kroju pisma jest trudna: kontury mają różną liczbę punktów, różną liczbę konturów (8 ma
dwa prześwity, 1 żadnego) i nie ma naturalnego przyporządkowania punktu do punktu. Bez tego wychodzi
szarpanie, a nie przelewanie.

**Droga, która to otwiera:** narysować cyfry jako **własną geometrię na wspólnym szkielecie** — tak
jak zrobiony jest już napis „Kratkoza". Jeśli każda cyfra jest złożona z tego samego zestawu kresek,
a różni się tylko ich położeniem, długością i tym, które są widoczne, to morfowanie sprowadza się do
interpolacji kilkunastu liczb. Przelewanie wychodzi wtedy samo i jest tanie w każdej klatce.

**Czego to kosztuje.** Zaprojektowania dziesięciu cyfr na jednym szkielecie, co jest robotą
rysunkową, a nie programistyczną — i decyzji, czy taki „techniczny" krój cyfr pasuje do reszty
grafiki, czy zaczyna wyglądać jak wyświetlacz kalkulatora.

**Uwaga o zasięgu.** Dotyczy wyłącznie cyfr w grafice tytułowej. Cyfry na planszy zostają zwykłym
tekstem: tam liczy się czytelność i respektowanie ustawień systemowych, a nie efekt.

