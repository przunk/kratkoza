# Zadanie: dopracowanie głębi i ruchu grafiki tytułowej

> Zasady współpracy — worktree, gałęzie, konwencje i scalanie — są w
> `codex_instructions.md`. Prace wykonujemy na `feature/home-board-parallax` w osobnym worktree.

## Cel

Grafika 3×3 nad menu ma wyglądać jak spójna część ekranu, z czytelną głębią i płynnym ruchem w
stylu efektów demonstracyjnych Atari/Amiga. Nie jest to obrót planszy dookoła osi, lecz ciągły,
łagodny wobble wynikający z kilku złożonych fal.

## Zakres

### 1. Miejsce na grafikę

- Lekko przesunąć menu w dół, bez spychania go pod dolną krawędź na małych ekranach.
- Wycentrować planszę w dostępnym obszarze nad menu.
- Rozmiar planszy musi uwzględniać pionowy margines bezpieczeństwa i maksymalne wychylenie, aby
  grafika nie była przycinana od góry ani od boków.

### 2. Tło sceny

- Nie rysować osobnego, ciemniejszego prostokąta pod animacją.
- Tło obszaru grafiki ma być przezroczyste i pokazywać dokładnie tło ekranu z `MaterialTheme`.
- Sama plansza pozostaje w pełni nieprzezroczysta i wyraźnie oddzielona tonalnie od ekranu.

### 3. Czytelna fizyczna głębia

- Zachować kolejność: tło ekranu → plansza → cyfry.
- Cyfry mają być jasne i czytelne, bez efektu prześwitywania spod planszy.
- Cień cyfr ma być osobną projekcją na płaszczyźnie planszy, a nie cieniem przyklejonym do warstwy
  cyfr. Przy wobble lub sterowaniu palcem położenie cyfry i jej cienia ma zmieniać się względem
  siebie zgodnie z różnicą głębokości oraz stałym kierunkiem światła.
- Dodać subtelne oddzielenie planszy od tła bez używania `shadowElevation`, które wcześniej dawało
  prostokątne artefakty GPU.

### 4. Wobble bez przeskoku

- Przyspieszyć ruch względem dotychczasowego cyklu 42 s.
- Złożyć przechył z kilku sinusoid o całkowitych harmonicznych, aby ruch był organiczny, ale jego
  początek i koniec pętli miały identyczne położenie oraz prędkość.
- Plansza nie może wykonywać pełnego obrotu. Zakres pozostaje niewielkim przechyłem w osiach X/Y.
- Sterowanie palcem i sprężysty powrót do aktualnej pozycji idle pozostają bez zmian.

### 5. Parujące cząsteczki

- Każda cząsteczka zaczyna przy dolnej krawędzi sceny i powoli płynie ku górze.
- W trakcie drogi lekko dryfuje na boki, może mieć subtelny glow i zanika przed górną krawędzią.
- Zachować trzy pasma głębokości: za planszą, między planszą i cyframi oraz przed cyframi.
- Głębokość wpływa na rozmiar, jasność, dryf i reakcję paralaksy.
- Odrodzenie cząsteczki na dole nie może powodować wspólnego, widocznego przeskoku całej animacji.

## Testy i weryfikacja

- Test jednostkowy ciągłości wobble na granicy cyklu.
- Test profilu widoczności cząsteczki: niewidoczna na starcie, widoczna w locie, wygaszona na końcu.
- Zachować testy mapowania dotyku, powrotu i pasm głębokości.
- Uruchomić testy celowane dla `RotatingBoardArtTest`, pełne testy i lint skryptami projektu.
- Po każdej ukończonej zmianie zbudować debug i release skryptami z `scripts/`.
- Skopiować APK do `/mnt/workspace/Kratkoza/dist`.
- Nie scalać gałęzi bez akceptacji użytkownika. Wygląd musi zostać oceniony na urządzeniu.
