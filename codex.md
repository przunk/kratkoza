# Zadanie dla Codexa: trzy poprawki czytelności ekranu gry

**Gałąź:** utwórz `fix/game-screen-readability` z `develop`.
**Pliki:** wyłącznie `app/src/main/kotlin/com/przunk/kratkoza/ui/game/NumberPad.kt`
i `app/src/main/kotlin/com/przunk/kratkoza/ui/game/GameScreen.kt`.

> **Praca równoległa.** Równolegle powstaje gałąź z paletami kolorystycznymi, która dotyka
> `ui/theme/*`, `domain/settings/*`, `data/repository/SettingsRepository.kt`
> i `ui/settings/*`. **Nie ruszaj tych plików.** Jedyny wspólny plik to `res/values*/strings.xml` —
> jeśli musisz dodać napis, dopisz go w sekcji „Gra" / „Gra (dostępność)", nie w sekcji „Ustawienia”.

Wszystkie trzy pozycje pochodzą z `docs/STATUS.md`, sekcja „Do zrobienia". Żadna nie czeka na
decyzję produktową — jeśli mimo to na jakąś trafisz, zapisz ją w `docs/OPEN_QUESTIONS.md`
z wariantami zamiast rozstrzygać po cichu.

---

## 1. Mocniejsze wyszarzenie niedostępnych cyfr

**Plik:** `NumberPad.kt`

Klawisz cyfry, której wszystkie dziewięć wystąpień jest już poprawnie na planszy, jest wygaszony
(`EXHAUSTED_ALPHA = 0.35f`) i odmawia kliknięcia. Za słabo to widać — stan powinien być czytelny
bez próby dotknięcia.

**Wymagania:**

- Kontrast tekstu na klawiszu musi zostać czytelny w obu motywach. Samo zejście z alfą w dół
  pogarsza kontrast; rozważ zamiast tego zmianę tła klawisza i koloru tekstu na role
  `surfaceVariant` / `onSurfaceVariant` albo `outline`, zamiast przezroczystości nałożonej na
  wszystko.
- **Musi zostać sygnał niekolorystyczny.** `project.md` §8 wprost zabrania komunikowania stanu
  samym kolorem. Dziś jest nim tylko wygaszenie, co jest kolorem. Dodaj drugi nośnik — np.
  przekreślenie cyfry, obramowanie zamiast wypełnienia, albo wyraźnie inny krój/grubość.
- Wygaszenie i odmowa kliknięcia **nie zależą** od opcji „licznik pozostałych cyfr" (§19) — ona
  rządzi wyłącznie małą liczbą pod klawiszem. Nie wiąż tych dwóch rzeczy ze sobą.
- Zachowaj rozmiar celu dotykowego. Klawisze stykają się i mają 64dp wysokości; nie zmniejszaj.
- TalkBack: klawisz niedostępny powinien być rozpoznawalny jako niedostępny. Dziś ma tylko
  `contentDescription` z cyfrą.

---

## 2. Układ napisu trybu kandydatów

**Plik:** `GameScreen.kt`, funkcja `CandidateLabel` i `GameActionsRow`

Napis „Kandydaci włączeni" / „Kandydaci wyłączeni" rozjeżdża się w polskiej wersji, szczególnie
przy powiększonej systemowej czcionce. Przycisk jest jednym z trzech w rzędzie o stałej wysokości
52dp i dzieli szerokość po równo z „Cofnij" i „Wymaż".

**Wymagania:**

- Układ ma znieść dłuższe tłumaczenie i powiększoną czcionkę bez ucinania tekstu i bez wypychania
  sąsiednich przycisków.
- Nie zmniejszaj celu dotykowego poniżej obecnych 52dp.
- Stan (włączony/wyłączony) musi dalej być czytelny **nie tylko z koloru** — dziś niesie go treść
  napisu plus wypełnienie przycisku. Jeśli skrócisz napis, zadbaj o zastępczy nośnik.
- Rozważ, czy rozwiązaniem nie jest krótszy napis („Kandydaci") plus osobny znacznik stanu, zamiast
  walki o zmieszczenie dwóch słów. Jeśli zmienisz teksty, zaktualizuj **oba** pliki `strings.xml`
  i zachowaj parzystość kluczy.

Uwaga na precedens z tego samego ekranu: przycisk pauzy zmieniał szerokość razem z napisem i
przeciągał sąsiada. Rozwiązaniem było zmierzenie obu etykiet i pomalowanie jednej — patrz
`PauseButton` w tym samym pliku. Ten sam trik może się przydać, ale nie kopiuj go bezmyślnie:
tam chodziło o stabilną szerokość, tu o zmieszczenie treści.

---

## 3. Wyśrodkowanie licznika błędów

**Plik:** `GameScreen.kt`, funkcja `GameStatusRow`

Licznik błędów nie stoi na środku ekranu. Przyczyna jest już rozpoznana i zapisana w
`docs/STATUS.md`: `Arrangement.SpaceBetween` rozkłada **odstępy**, nie elementy, więc środkowy
trafia na środek tylko wtedy, gdy skrajne mają równą szerokość — a „Czas / 4:04" i przycisk pauzy
jej nie mają.

**Wymagania:**

- Trzy sloty o równych wagach, treść środkowego wyśrodkowana. Wtedy pozycja przestaje zależeć od
  długości sąsiadów w jakimkolwiek języku.
- Czas zostaje przy lewej krawędzi, pauza przy prawej.
- Nie psuj stabilnej szerokości przycisku pauzy (`PauseButton`) — to osobna, już naprawiona rzecz.
- Po poprawce usuń tę pozycję z `docs/STATUS.md`.

---

## Weryfikacja

```bash
./scripts/test-kratkoza.sh      # 185 testów musi przechodzić
./scripts/lint-kratkoza.sh      # lint musi być czysty
./scripts/build-kratkoza-debug.sh
```

Lint ma włączone `abortOnError` i `warningsAsErrors`, więc ostrzeżenie wywraca build. Jeśli dodasz
napis, sprawdź parzystość kluczy między `values/` i `values-pl/`.

Podbij `versionCode` i `versionName` w `app/build.gradle.kts` — wersja jest pokazywana
w Ustawieniach i podbijamy ją przy każdej zatwierdzonej zmianie.

**Czego testy nie sprawdzą:** wszystkie trzy pozycje są wizualne i żaden test jednostkowy ich nie
złapie — projekt nie ma testów Compose UI. Napisz w opisie commita wprost, co zostało zweryfikowane
maszynowo, a co wymaga obejrzenia na urządzeniu.
