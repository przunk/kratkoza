# Zadanie: trzy poprawki czytelności ekranu gry

> Zasady współpracy — worktree, gałęzie, konwencje, weryfikacja — są w `codex_instructions.md`.
> Przeczytaj je najpierw. Ten plik opisuje wyłącznie bieżące zadanie.

**Gałąź:** `fix/game-screen-readability` z `develop`.

**Pliki, których dotyczy zadanie:**
- `app/src/main/kotlin/com/przunk/kratkoza/ui/game/NumberPad.kt`
- `app/src/main/kotlin/com/przunk/kratkoza/ui/game/GameScreen.kt`
- `res/values*/strings.xml` — tylko jeśli zmieniasz teksty, i tylko w sekcji „Gra"
- `docs/STATUS.md` — usuń stamtąd pozycje, które domkniesz

Równolegle ktoś pracuje w `ui/theme/*`, `domain/settings/*`, `data/repository/SettingsRepository.kt`
i `ui/settings/*`. **Tych plików nie ruszaj.**

Wszystkie trzy pozycje pochodzą z `docs/STATUS.md`. Żadna nie czeka na decyzję produktową.

---

## 1. Mocniejsze wyszarzenie niedostępnych cyfr

**Plik:** `NumberPad.kt`

Klawisz cyfry, której wszystkie dziewięć wystąpień jest już poprawnie na planszy, jest wygaszony
(`EXHAUSTED_ALPHA = 0.35f`) i odmawia kliknięcia. Za słabo to widać — stan powinien być czytelny
bez próby dotknięcia.

- Kontrast tekstu musi zostać czytelny w obu motywach. Samo zejście z alfą pogarsza kontrast;
  rozważ zamiast tego zmianę tła i koloru tekstu na role `surfaceVariant` / `onSurfaceVariant`,
  zamiast przezroczystości nałożonej na wszystko.
- **Musi zostać sygnał niekolorystyczny** — dziś jest nim tylko wygaszenie, czyli kolor. Dodaj
  drugi nośnik: przekreślenie, obramowanie zamiast wypełnienia, wyraźnie inna grubość.
- Wygaszenie i odmowa kliknięcia **nie zależą** od opcji „licznik pozostałych cyfr" (§19) — ona
  rządzi wyłącznie małą liczbą pod klawiszem. Nie wiąż tych dwóch rzeczy.
- Zachowaj rozmiar celu dotykowego: klawisze stykają się i mają 64dp wysokości.
- TalkBack powinien rozpoznać klawisz jako niedostępny; dziś ma tylko `contentDescription` z cyfrą.

## 2. Układ napisu trybu kandydatów

**Plik:** `GameScreen.kt` — `CandidateLabel`, `GameActionsRow`

Napis „Kandydaci włączeni" / „Kandydaci wyłączeni" rozjeżdża się w polskiej wersji, szczególnie przy
powiększonej systemowej czcionce. Przycisk jest jednym z trzech w rzędzie o stałej wysokości 52dp.

- Układ ma znieść dłuższe tłumaczenie i powiększoną czcionkę bez ucinania tekstu i bez wypychania
  sąsiadów.
- Nie schodź z celem dotykowym poniżej 52dp.
- Stan musi dalej być czytelny **nie tylko z koloru** — dziś niesie go treść napisu plus wypełnienie
  przycisku. Jeśli skrócisz napis, zadbaj o zastępczy nośnik.
- Rozważ krótszy napis („Kandydaci") plus osobny znacznik stanu, zamiast walki o zmieszczenie dwóch
  słów.

Precedens z tego samego pliku: przycisk pauzy zmieniał szerokość razem z napisem i przeciągał
sąsiada. Rozwiązaniem było zmierzenie obu etykiet i pomalowanie jednej — patrz `PauseButton`. Tam
chodziło o stabilną szerokość, tu o zmieszczenie treści, więc nie kopiuj bezmyślnie.

## 3. Wyśrodkowanie licznika błędów

**Plik:** `GameScreen.kt` — `GameStatusRow`

Licznik błędów nie stoi na środku ekranu. Przyczyna jest już rozpoznana i opisana w `docs/STATUS.md`:
`Arrangement.SpaceBetween` rozkłada **odstępy**, nie elementy, więc środkowy trafia na środek tylko
wtedy, gdy skrajne mają równą szerokość — a „Czas / 4:04" i przycisk pauzy jej nie mają.

- Trzy sloty o równych wagach, treść środkowego wyśrodkowana.
- Czas zostaje przy lewej krawędzi, pauza przy prawej.
- Nie psuj stabilnej szerokości `PauseButton` — to osobna, już naprawiona rzecz.

---

## Na koniec

Wszystkie trzy pozycje są wizualne i **żaden test jednostkowy ich nie złapie** — projekt nie ma
testów Compose UI. W opisie commita napisz wprost, co zweryfikowałeś maszynowo, a co wymaga
obejrzenia na urządzeniu.
