# Zadanie: testy Compose UI dla ekranów pomocniczych

> Zasady współpracy — worktree, gałęzie, konwencje, kto scala — są w `codex_instructions.md`.
> Przeczytaj je najpierw. Ten plik opisuje wyłącznie bieżące zadanie.

**Gałąź:** `test/compose-ui-screens` z `develop`.

**Pliki, których dotyczy zadanie — same testy:**
- `app/src/test/kotlin/com/przunk/kratkoza/ui/settings/`
- `app/src/test/kotlin/com/przunk/kratkoza/ui/statistics/`
- `app/src/test/kotlin/com/przunk/kratkoza/ui/saves/`
- `app/src/test/kotlin/com/przunk/kratkoza/ui/newgame/`

**Nie zmieniaj kodu produkcyjnego.** Jeśli test czegoś nie da się napisać bez zmiany w `app/src/main`,
to jest wynik sam w sobie — zapisz co i dlaczego, i zostaw resztę zadania zrobioną.

Równolegle trwa praca nad Daily, która dotyka `ui/game/*`, `ui/daily/*`, `ui/home/*`,
`domain/daily/*`, `data/*` i `navigation/*`. **Tych ekranów nie testuj w tym zadaniu** — zmieniają
się pod ręką i testy do nich trzeba będzie pisać po scaleniu.

---

## Dlaczego to zadanie

`technical_requirements.md` §15 wymaga testów Compose dla najważniejszych interakcji, a projekt nie
ma ich wcale. `docs/STATUS.md` mówi wprost: **żadna zmiana wizualna nie jest wyłapywana maszynowo.**
To ostatnia duża dziura w pokryciu — logika, warstwa danych i ViewModele są przetestowane.

**Infrastruktura jest już gotowa i sprawdzona.** Testy Compose działają w tym projekcie na JVM przez
Robolectrica, bez urządzenia — patrz `ComposeSmokeTest`, który to potwierdza i celowo został jako
osobny plik. Nie musisz nic konfigurować; `createComposeRule()` i `@RunWith(AndroidJUnit4::class)`
wystarczą.

## Co ma być pokryte

Ekrany, których dotyczy zadanie, mają ViewModele o prostych zależnościach — użyj istniejących
atrap z `app/src/test/kotlin/com/przunk/kratkoza/data/FakeDaos.kt`, tak jak robią to testy
ViewModeli obok.

**Ustawienia** (`SettingsScreen`)
- każda sekcja jest widoczna: język, paleta, wygląd, rozgrywka, o aplikacji,
- przełącznik faktycznie zmienia stan — kliknięcie „Automatycznie usuwaj kandydatów" odbija się
  w `GameSettings`,
- wybór palety zaznacza właściwą pozycję i pokazuje podgląd dla każdej z czterech,
- wersja z `BuildConfig` jest wypisana.

**Statystyki** (`StatisticsScreen`)
- pusty dorobek nie pokazuje „—" tam, gdzie powinna być liczba, ani odwrotnie,
- poziom i pasek XP zgadzają się z `Progress`,
- wiersz „W toku" pojawia się tylko dla poziomów, które faktycznie coś mają,
- przycisk resetu jest nieaktywny przy pustym dorobku i pyta o potwierdzenie przy niepustym.

**Zapisane gry** (`SavedGamesScreen`)
- pusta lista pokazuje komunikat, a nie pustą kartę,
- wiersz pokazuje poziom, czas, procent wypełnienia i liczbę błędów,
- **dwustopniowe usuwanie**: kosz → „USUŃ" → dopiero drugie kliknięcie usuwa; po 3 s wraca kosz,
- uzbrojenie jednego wiersza rozbraja poprzedni.

**Nowa gra** (`NewGameScreen`)
- pięć poziomów, każdy klikalny,
- okno wklejania kodu przyjmuje tekst i zamyka się,
- trzy komunikaty błędu importu pojawiają się dla trzech różnych przyczyn.

## Na co uważać

- **Testuj to, co widzi gracz, nie strukturę.** Wyszukiwanie po tekście z zasobów jest odporne na
  przestawienie układu; wyszukiwanie po pozycji w drzewie nie jest.
- Teksty biorą się z zasobów, a testy chodzą po angielsku — pobieraj je przez kontekst zamiast
  wpisywać na sztywno, inaczej test przestanie przechodzić przy pierwszej korekcie tłumaczenia.
- Dwustopniowe usuwanie ma **odliczanie 3 s**. `ComposeTestRule` domyślnie sam przesuwa zegar; jeśli
  będzie z tym kłopot, `mainClock.advanceTimeBy` daje kontrolę.
- Jeżeli test przechodzi, ale nie umiałbyś powiedzieć, co by go zepsuło — usuń go. Test, który nie
  potrafi zawieść, kosztuje czas budowania i niczego nie chroni.

## Weryfikacja

```bash
cd <twój worktree>
./scripts/test-kratkoza.sh
./scripts/lint-kratkoza.sh
```

Wersji **nie podbijaj** — robi to scalający.

W opisie commita napisz, czego **nie** udało się przetestować i dlaczego. To cenniejsze niż lista
tego, co przeszło.
