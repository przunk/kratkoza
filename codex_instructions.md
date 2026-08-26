# Kratkoza — zasady współpracy dla Codexa

Dokument stały. Opisuje **jak** pracujemy; konkretne zadanie dostajesz osobno w `codex.md`.

Nad projektem pracują równolegle dwa agenty — Codex i Claude — oraz właściciel projektu, który jako
jedyny uruchamia aplikację na urządzeniu. Wszystko poniżej wynika z tego układu.

---

## 1. Źródło prawdy

Nadrzędne są dwa pliki w katalogu głównym:

- `project.md` — wymagania funkcjonalne
- `technical_requirements.md` — wymagania techniczne

**Nie edytujesz ich.** To specyfikacja właściciela projektu. Jeśli uważasz, że wymaganie jest złe
albo sprzeczne, napisz to w opisie commita i w `docs/OPEN_QUESTIONS.md` — nie obchodź go po cichu.

Gdy Twój pomysł implementacyjny kłóci się z wymaganiem, wygrywa wymaganie
(`technical_requirements.md` §17).

## 2. Gdzie pracujesz

Każdy agent ma **własny katalog roboczy i własną gałąź**. Jedna kopia repozytorium może mieć
wybraną tylko jedną gałąź, więc dzielenie katalogu oznaczałoby, że przełączenie gałęzi przez jednego
przestawia ją drugiemu w trakcie pracy.

Twój katalog powstaje jako git worktree — wspólny `.git`, osobne pliki:

```bash
cd /mnt/workspace/Kratkoza
git worktree add /mnt/workspace/Kratkoza-codex -b <nazwa-gałęzi> develop
```

Od tego momentu pracujesz **wyłącznie** w `/mnt/workspace/Kratkoza-codex`.

**Czego nie robisz nigdy:**

- nie przełączasz gałęzi w `/mnt/workspace/Kratkoza` — tam pracuje ktoś inny,
- nie commitujesz na cudzej gałęzi ani na `develop`,
- nie robisz `rebase`, `reset --hard` ani `push --force` na niczym, co jest już wypchnięte,
- nie wypychasz nic na GitHuba — robi to właściciel projektu, bo token jest tylko u niego.

Historia jest wspólna, więc commity drugiego agenta widzisz bez pobierania czegokolwiek. Jeśli
potrzebujesz jego zmian, `git merge develop` **do siebie** — nigdy odwrotnie.

## 3. Gałęzie

- `master` — baza,
- `develop` — gałąź integracyjna, **nikt nie commituje na nią wprost**,
- `<typ>/<opis>` — gałąź zadania, np. `fix/game-screen-readability`.

Scalenie do `develop` następuje, gdy zadanie jest skończone i zweryfikowane, i robi je ten, kogo
właściciel o to poprosi. Sam nie scalasz cudzej pracy.

## 4. Granice plików

`codex.md` wymienia pliki, których dotyczy zadanie. **Trzymaj się ich.** Worktree usuwa przeszkodę
techniczną, ale nie zapobiega konfliktowi, gdy dwie osoby edytują ten sam plik — jedynym realnym
zabezpieczeniem jest rozdział plików.

Jeśli zadania nie da się wykonać bez ruszenia pliku spoza listy, **zatrzymaj się i napisz o tym**,
zamiast rozszerzać zakres.

Jedyny plik, który regularnie dotyczy wszystkich, to `res/values*/strings.xml`. Dopisuj w sekcji
odpowiadającej Twojemu ekranowi i nigdy nie przestawiaj istniejących wpisów — konflikt tekstowy
w jednym miejscu jest do rozwiązania, a przemieszany plik nie.

## 5. Decyzje, których nie wolno podejmować po cichu

Jeżeli trafisz na pytanie, którego `project.md` nie rozstrzyga, a bez odpowiedzi nie da się iść
dalej — **nie wymyślaj jej**. Zapisz problem i warianty w odpowiednim pliku:

| Plik | Co tam trafia |
|---|---|
| `docs/OPEN_QUESTIONS.md` | decyzje, które implementacja musiała przyjąć, a specyfikacja ich nie rozstrzyga; z wariantami i uzasadnieniem |
| `docs/STATUS.md` | co gotowe, czego nie ma, co do zrobienia, czego nie zweryfikowano |
| `docs/WISHLIST.md` | pomysły spoza specyfikacji, bez przypisanego etapu |

Rzeczy oznaczone w `project.md` jako „do ustalenia", „później" albo „nice to have" **nie są
zatwierdzonymi wymaganiami** — zostaw kod gotowy na rozszerzenie, ale nie implementuj domysłów
(`project.md` §41).

## 6. Konwencje kodu

- **Kotlin**, Jetpack Compose, Material 3. Nowego kodu w Javie nie piszemy.
- **Komentarze i KDoc po angielsku.** Opisy commitów i dokumentacja w `docs/` — po polsku.
- Komentarz ma tłumaczyć **dlaczego**, nie powtarzać, co widać w kodzie. Szczególnie cenne jest
  zapisanie, czego dana decyzja unika.
- **Żadnych nowych zależności** bez wyraźnego uzasadnienia (`technical_requirements.md` §9).
  Projekt celowo nie ma nic poza Androidem, Kotlinem, Jetpackiem i Material 3. Ikony są rysowane
  na miejscu, kodek historii ruchów jest własny, DI jest ręczne — to nie przeoczenia.
- Logika domenowa nie zależy od Androida ani od żadnego `Composable`.
- **Stan nigdy nie może być komunikowany samym kolorem** (`project.md` §8). Przy każdej zmianie
  wyglądu sprawdź, czy zostaje drugi nośnik: grubość, kształt, treść, ikona.

### Pułapki, które już raz kosztowały

- **Nazwy stałych enum są formatem zapisu.** `Difficulty`, `GameType`, `ThemeMode`, `AppLanguage`,
  `BoardPalette` zapisują się do Roomu i DataStore przez `name`. Reguła w `proguard-rules.pro`
  chroni je przed R8 — nie usuwaj jej i nie zmieniaj nazw stałych bez migracji.
- **Testy chodzą po kodzie niezminifikowanym.** Build release może być zepsuty mimo zielonych
  testów, i objawia się to jako dane, które się nie wczytują, a nie jako awaria.
- **Nie ma testów Compose UI.** Żadna zmiana wizualna nie jest wyłapywana maszynowo.

## 7. Weryfikacja

```bash
cd /mnt/workspace/Kratkoza-codex
./scripts/test-kratkoza.sh          # wszystkie testy muszą przechodzić
./scripts/lint-kratkoza.sh          # lint musi być czysty
./scripts/build-kratkoza-debug.sh
```

Skrypty wyliczają katalog projektu z własnego położenia, więc uruchomione z Twojego worktree budują
Twój kod. Logi też mają nazwę po katalogu. `GRADLE_USER_HOME` jest wspólny — jeśli build stoi,
prawdopodobnie czeka na blokadę drugiego builda; to normalne.

Lint ma `abortOnError` i `warningsAsErrors`, więc **ostrzeżenie wywraca build**. Jeśli lint zgłasza
coś, co kłóci się z wymaganiem z `project.md`, wycisz regułę w `app/lint.xml` **z komentarzem
wyjaśniającym dlaczego** — tam już są cztery takie przypadki.

Po dodaniu napisu sprawdź parzystość kluczy między `values/` i `values-pl/`.

## 8. Commity

- Opis po polsku, w trybie oznajmującym: „Cyfra rozdana na całej planszy nie daje się już wybrać".
- Tłumacz **dlaczego**, nie tylko co. Jeśli poprawiasz błąd, napisz, co było jego przyczyną — a jeśli
  przyczyną była wcześniejsza decyzja, napisz to wprost.
- **Nie podbijaj `versionCode` ani `versionName` na gałęzi zadaniowej.** Robi to jedna osoba,
  scalając do `develop`. Gdyby podbijał każdy u siebie, dwie gałęzie zgłosiłyby ten sam numer dla
  różnego kodu.
- Dodaj własną atrybucję współautorstwa. Nie podpisuj się cudzą.

### Jak rozpoznać, z czego pochodzi APK

Wersja jest widoczna w Ustawieniach, żeby zrzut ekranu sam mówił, z którego builda pochodzi — raz
już straciliśmy na tym cykl. Skoro numeru na gałęzi nie podbijamy, rozpoznanie niesie **przyrostek
nazwy gałęzi**, doklejany automatycznie przez `app/build.gradle.kts`:

- build z `develop` albo `master`: `0.6.0 (12)`
- build z gałęzi zadaniowej: `0.6.0+fix/game-screen-readability (12)`

Nie musisz nic z tym robić — dzieje się samo. Nie usuwaj tego mechanizmu i nie obchodź go, wpisując
numer ręcznie.

Instalacja na urządzeniu działa mimo niezmienionego `versionCode`: Android blokuje wyłącznie
instalację wersji **niższej** niż zainstalowana, a ta sama liczba to zwykła reinstalacja.

## 9. Uczciwość raportowania

To jest ważniejsze niż cokolwiek powyżej.

- Napisz wprost, **co zostało zweryfikowane maszynowo, a co wymaga obejrzenia na urządzeniu**.
  Aplikacji nie uruchamia żaden z agentów — robi to właściciel projektu.
- Nie pisz, że coś działa, jeśli tego nie sprawdziłeś. „Powinno działać" i „działa" to dwa różne
  zdania.
- Jeśli testy nie przechodzą, powiedz to i pokaż wynik. Jeśli pominąłeś część zadania, powiedz
  którą i dlaczego.
- Jeśli w trakcie okaże się, że zadanie jest źle postawione, powiedz to zamiast wykonywać je
  bezmyślnie. Uzasadniony sprzeciw jest mile widziany; ciche odejście od zakresu nie.
