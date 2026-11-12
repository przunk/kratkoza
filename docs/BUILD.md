# Kratkoza — budowanie

Wszystkie skrypty siedzą w `scripts/` i same ustawiają JDK i Android SDK z
`/mnt/workspace/.tooling`. Nie trzeba niczego eksportować ręcznie.

| Skrypt | Co robi |
|---|---|
| `scripts/build-debug.sh` | debug APK do `dist/` |
| `scripts/build-release.sh` | release APK do `dist/` + którym kluczem podpisany i jego SHA-1 |
| `scripts/test.sh` | pełny pakiet testów jednostkowych, sprawdzian napisów i metadanych sklepu |
| `scripts/lint.sh` | Android Lint |
| `scripts/build-bundle.sh` | AAB na Google Play do `dist/`; hasła pytane, nigdzie niezapisywane |
| `scripts/release.sh` | całe wydanie jednym poleceniem — patrz „Wydanie” niżej |
| `scripts/check-release.sh` | uruchamia `release.sh` naprawdę na jednorazowych klonach i sprawdza wynik |
| `scripts/check-store-metadata.sh` | limity znaków Play i te same pliki w każdym języku |
| `scripts/check-tooling.sh` | co z narzędzi jest, a czego brakuje |
| `scripts/push-kratkoza.sh` | wypchnięcie na GitHuba, token pytany za każdym razem |
| `scripts/project.env` | wszystko, czym Kratkoza różni się od szablonu |
| `scripts/use-tooling.sh` | wspólne środowisko, tylko do `source` |

Wszystkie budujące przyjmują dodatkowe argumenty Gradle'a, np.:

```bash
./scripts/test.sh --tests '*SudokuGeneratorTest*'
./scripts/test.sh --really      # wymusza uruchomienie testów zamiast wyniku z cache
```

Skrypty celowo **nie wypluwają logu Gradle'a**. Sukces to kilka linii; porażka to lista tego, co
nie przeszło — nazwy testów z komunikatami asercji, błędy kompilacji, znaleziska lintu. Pełny log
zawsze leży w `/tmp/kratkoza-<katalog>-*.log`. Zero uruchomionych testów to porażka, nie sukces.

### Nazwy artefaktów

Każdy plik do zainstalowania albo wgrania trafia do `dist/`, a ostatnia linia skryptu podaje jego
pełną ścieżkę i rozmiar:

| artefakt | nazwa |
|---|---|
| debug APK | `kratkoza-debug-<versionName>-<versionCode>-<sha>[-dirty]-<czas>.apk` |
| release APK | `kratkoza-<versionName>-<versionCode>-<sha>[-dirty]-<czas>.apk` |
| bundle | `kratkoza-<versionName>-<versionCode>-<sha>[-dirty].aab` |

`<sha>` to krótki SHA commita, z którego powstał build; `-dirty` znaczy, że w drzewie były
niezacommitowane zmiany i SHA wskazuje tylko commit bazowy. `versionCode` sam nie wystarcza do
wskazania źródeł: równoległe gałęzie mają te same liczniki, a `--amend` licznika nie zmienia. Build z
gałęzi ma w `versionName` jej nazwę, z `/` zamienionym na `-`.

## Stosunek do szablonu w `.tooling`

Od 2026-11-12 skrypty są kopią szablonu `/mnt/workspace/.tooling/templates/android/`, skopiowaną do
projektu, nie wołaną stamtąd. Różnice wobec szablonu, każda z powodem:

- **build w drzewie projektu** (`app/build`), nie poza montowaniem — `settings.gradle.kts` Kratkozy
  nigdy nie przenosił katalogu builda, a przejście na skrypty miało nie zmienić żadnego pliku Gradle'a;
- **SHA commita i `-dirty` w nazwach artefaktów** (AGENTS.md §11; szablon tego jeszcze nie ma);
- **`release.sh` w modelu z przewijaniem `master`** i hotfixami (AGENTS.md §5 i §11; szablon ma
  jeszcze model ze scalaniem do `master`) oraz `check-release.sh`, którego szablon nie ma;
- **`lint.sh`** jako osobny skrypt, wołany przez `release.sh` razem z testami;
- **SHA-1 podpisu w `build-release.sh`**, bo to pierwsza rzecz do porównania, gdy nie działa
  logowanie do Play Games;
- **raport porażek testów z JUnit XML**, przeniesiony ze starego `test-kratkoza.sh`.

Przeniesienie którejś z tych zmian do szablonu jest osobną decyzją, bo zmienia skrypty wszystkich
projektów.

## Lint

`abortOnError` i `warningsAsErrors` są włączone, więc ostrzeżenie lintu wywraca build.
`app/lint.xml` wycisza cztery reguły — każdą z komentarzem, dlaczego:

- `OldTargetApi`, `LockedOrientationActivity`, `DiscouragedApi` — lint kłóci się z wymaganiami
  z `project.md` §2 (targetSdk 36, portrait only)
- `AndroidGradlePluginVersion`, `GradleDependency`, `NewerVersionAvailable` — reguły „jest nowsza
  wersja" odpytują sieć i wywracają zielony build bez zmiany choćby jednej linii kodu

## Środowisko

- JDK 17 (Temurin) z `.tooling/jdk`
- Android SDK z `.tooling/android-sdk` — platformy 36 i 37, build-tools 37.0.0
- Gradle 9.6.1 przez wrapper
- `compileSdk = 37`, `targetSdk = 36`, `minSdk = 29`

`GRADLE_USER_HOME` ląduje w `/tmp/kratkoza-gradle-home`, czyli na dysku linuksowym. Cache trzymany
na montowanym 9p (`/mnt/workspace`) dominuje czas builda i potrafi wywracać natywne usługi Gradle'a.
Można to nadpisać przez `PROJECT_GRADLE_USER_HOME`.

Pierwszy build po restarcie maszyny pobiera zależności od nowa — to jedyny koszt tej decyzji.

## Podpisywanie release

Klucz **nigdy** nie trafia do repozytorium. Poświadczenia idą do `~/.gradle/gradle.properties`:

```properties
PRZUNK_UPLOAD_STORE_FILE=/absolute/path/to/kratkoza-release.jks
PRZUNK_UPLOAD_STORE_PASSWORD=…
PRZUNK_UPLOAD_KEY_ALIAS=…
PRZUNK_UPLOAD_KEY_PASSWORD=…
```

Bez nich build **nadal się udaje**, ale podpisuje kluczem debug. To celowe: niepodpisanego APK nie da
się w ogóle zainstalować, więc fallback ratuje wgrywanie na telefon na maszynie bez klucza
wydawniczego. Takiego APK nie wolno publikować.

`build-release.sh` odczytuje certyfikat z gotowego pliku i mówi wprost, którym kluczem
podpisał — konfiguracja Gradle'a może wyglądać poprawnie i mimo to zadziałać fallback, a błąd jest
inaczej niewidoczny aż do odrzucenia uploadu.

Wygenerowanie klucza (raz):

```bash
/mnt/workspace/.tooling/jdk/bin/keytool -genkeypair -v \
  -keystore ~/keys/kratkoza-release.jks -keyalg RSA -keysize 4096 -validity 10000 \
  -alias kratkoza
```

## Bundle na Google Play

```bash
./scripts/build-bundle.sh
```

Zwykle nie uruchamia się go samodzielnie, tylko przez `release.sh`, który buduje bundle z taga.
Pyta o wszystko na bieżąco — ścieżkę do keystore'a (domyślnie `KEYSTORE_DEFAULT` z `project.env`),
alias, hasła — i **nigdzie ich nie zapisuje**: ani w repozytorium, ani w
`~/.gradle/gradle.properties`, ani w historii powłoki. Trafiają do Gradle'a przez zmienne
`ORG_GRADLE_PROJECT_*` i żyją tyle, co proces skryptu.

Bez terminala te same wartości czyta ze zmiennych `PRZUNK_UPLOAD_STORE_FILE`,
`PRZUNK_UPLOAD_KEY_ALIAS`, `PRZUNK_UPLOAD_STORE_PASSWORD` i `PRZUNK_UPLOAD_KEY_PASSWORD`. Dawne
`KRATKOZA_UPLOAD_*` nie są już czytane.

Trzy rzeczy działają tu inaczej niż w skrypcie APK:

- **Twarde przerwanie przy kluczu debug.** Przy APK fallback na klucz debug ma sens — niepodpisanego
  APK nie da się w ogóle zainstalować. Przy bundlu na Play jest bezużyteczny: upload i tak zostanie
  odrzucony, a jedyne, co fallback kupuje, to dowiedzenie się o tym później zamiast teraz.
  Certyfikat jest odczytywany z **gotowego pliku** — konfiguracja Gradle'a może wyglądać poprawnie
  i mimo to zadziałać fallback.
- **Weryfikacja przez `jarsigner` i `keytool`, nie `apksigner`.** Bundle niesie podpis JAR-a, nie
  APK-a, więc apksigner go nie przeczyta.
- **Pilnowanie `versionCode`.** Play odrzuca kod wersji, który już widział. Skrypt mówi, gdy bundle
  o tym kodzie już leży w `dist/` — przebudowa jest w porządku, zapomniane podbicie nie.

### Klucz upload

Jeśli go jeszcze nie ma:

```bash
/mnt/workspace/.tooling/jdk/bin/keytool -genkeypair -v \
  -keystore ~/keys/kratkoza-upload.jks -storetype JKS \
  -keyalg RSA -keysize 4096 -validity 10000 -alias kratkoza
```

**Kopia zapasowa keystore'a jest obowiązkowa.** Utrata pliku albo hasła oznacza, że nie da się już
zaktualizować aplikacji pod tym samym `applicationId` — zostaje prośba do Google o reset klucza
upload albo publikacja od nowa jako inna aplikacja.

## Minifikacja (R8)

Build release ma `isMinifyEnabled` i `isShrinkResources` włączone. Efekt: **25 MB → 2,3 MB**,
z czego sam `classes.dex` spadł z 25,5 MB do 2,1 MB.

`app/proguard-rules.pro` trzyma tylko to, co naprawdę musi tam być, każda reguła z powodem.
Najważniejsza dotyczy **nazw stałych enum**: `Difficulty`, `GameType`, `ThemeMode` i `AppLanguage`
zapisujemy do Roomu i DataStore przez `name`, więc nazwa stałej jest częścią formatu na dysku.
R8 może swobodnie zmieniać nazwy pól, a `name()` zwraca to, jak pole ostatecznie się nazywa — bez
tej reguły build release zapisywałby `a` tam, gdzie debug zapisał `EASY`, i wszystko zapisane
wcześniej przestałoby się parsować. Po cichu, bo nieznana nazwa jest traktowana jako „nie ma".

Że reguła działa, widać w `app/build/outputs/mapping/release/mapping.txt` — stałe mapują się na
siebie (`EASY -> EASY`). Room jest bezpieczny dzięki własnym regułom biblioteki: `KratkozaDatabase`
i `KratkozaDatabase_Impl` zachowują pełne nazwy, bo Room szuka implementacji przez refleksję.

**Testy jednostkowe nie sprawdzają buildu release** — chodzą po kodzie niezminifikowanym. Co R8
zepsuje, widać dopiero na urządzeniu, i zwykle jako dane, które się nie wczytują, a nie jako awaria.
Po każdej zmianie reguł warto na telefonie przejść: nowa gra → zapis → wznowienie → ustawienia →
zmiana języka → statystyki.

Plik `mapping.txt` należy wysłać do Play razem z bundlem — bez niego raport awarii z telefonu to
lista ponumerowanych metod w klasie `a`.

## Wersjonowanie

`versionCode` to liczba commitów (`git rev-list --count HEAD`), liczona przez Gradle'a, nigdy
wpisywana ręcznie. `versionName` zmienia wyłącznie `release.sh`.

Żeby build z gałęzi dało się rozpoznać, `app/build.gradle.kts` dokleja do `versionName` nazwę
gałęzi, gdy nie jest to `develop`, `master` ani odłączony HEAD:

| Skąd build | Wersja w Ustawieniach |
|---|---|
| `develop` / `master` / tag | `0.32.0 (216)` |
| gałąź zadaniowa | `0.32.0+feature/digit-insert-mode (217)` |

Bundle wydania powstaje z odłączonego checkoutu taga, więc nie niesie przyrostka.

Ten sam `versionCode` **nie przeszkadza w instalacji** na urządzeniu — Android blokuje tylko wersję
niższą od zainstalowanej. Play jest inne: każdy wysłany `versionCode` musi być wyższy od
dotychczasowych, a przy liczbie commitów oznacza to nowy commit, nie `--amend`.

## Wydanie

Wydanie robię sam, jednym poleceniem; agent przygotowuje drzewo i tego skryptu nie uruchamia.

```bash
./scripts/release.sh X.Y.Z --check    # sprawdza wszystko, niczego nie zmienia
./scripts/release.sh X.Y.Z            # wykonuje; pyta o wpisanie wersji, potem o hasła keystore'a
```

Z `develop` dla zwykłego wydania, z `hotfix/<nazwa>` dla poprawki tego, co już wydane. Kolejno,
z zatrzymaniem na pierwszym błędzie:

1. gałąź `develop` albo `hotfix/*`, czyste drzewo, wolny tag, wersja wyższa od bieżącej i od
   każdego istniejącego taga, a `master` da się przewinąć do tej gałęzi;
2. informacje o wydaniu we wszystkich językach, w limitach Play — pokazane do ostatniego czytania;
3. pełny pakiet testów i lint;
4. po wpisaniu wersji: `versionName` i commit `Release X.Y.Z` na gałęzi, `master` **przewinięty** do
   tego commita (nigdy nie scalany), tag `vX.Y.Z` na nim;
5. bundle zbudowany z odłączonego checkoutu taga przez `build-bundle.sh`, który sam pyta o hasła;
6. ślad wydania w `store/play-console/releases.md`, commitowany na gałęzi, nie na `master`, który
   zostaje na tagu; wpis mówi „przygotowane”, a wgranie i publikację odnotowuję ręcznie;
7. przy hotfixie: gałąź scalona z powrotem do `develop` (scaleniem, nie `cherry-pick`).

Skrypt nie wypycha i nie wgrywa. Ostatnie linie mówią, co wgrać gdzie, jaki SHA-256 ma pokazać Play
i jakie `git push` wykonać.

**Przerwane wydanie zostawia wykonane kroki** — nic nie jest cofane. Skrypt wypisuje, na którym
kroku stanął, gdzie są gałąź, `master` i tag, i co zostało do zrobienia ręcznie. Trybu wznowienia nie
ma; ponowne uruchomienie od początku odmówi, bo tag już istnieje.

`store/play-console/releases.md` ma w `.gitattributes` scalanie `union`: ślad wydania powstaje po
tagu, więc hotfix odbity od `master` nie zna wpisu poprzedniego wydania, i bez tego każde scalenie
hotfixu z `develop` kończyłoby się konfliktem w tym pliku. `check-release.sh` to wykrył.

**Zmiana w `release.sh` wymaga przebiegu `./scripts/check-release.sh`.** Klonuje repozytorium do
`/tmp`, instaluje w klonie skrypty z bieżącego drzewa, podmienia tam tylko testy, lint i budowanie
bundla (ten ostatni dalej sprawdza, że działa na tagu, i podpisuje plik jednorazowym kluczem) i
uruchamia wydanie naprawdę: odmowy, `--check`, wydanie z `develop`, hotfix przy niewydanej pracy na
`develop`, przerwanie po tagu.

### Historia `master` naprawiona 2026-11-12

`master` miał trzy commity scalające, których nie było na `develop`, i `release.sh` odmówiłby
przewinięcia. Naprawione jednorazowym scaleniem `master` do `develop` (`8ee63df`); szczegóły w
[STATUS.md](STATUS.md).

## Repozytorium

Model gałęzi opisuje `/mnt/workspace/AGENTS.md` §5: praca na gałęziach zadań, `develop` jako gałąź
bieżąca, `master` przewijany do wydania przez `release.sh`.

```bash
./scripts/push-kratkoza.sh              # bieżąca gałąź
./scripts/push-kratkoza.sh develop master
```

Skrypt pyta o token przy każdym uruchomieniu i **nigdzie go nie zapisuje** — ta sama umowa co przy
budowaniu bundla. Token nie trafia do `.git/config` ani do adresu zdalnego repozytorium, gdzie
pokazywałby go `git remote -v` i każdy komunikat błędu. Odpowiedzi dla gita idą przez `GIT_ASKPASS`,
więc token nie pojawia się też w terminalu.

Token potrzebuje prawa zapisu do `przunk/kratkoza`: klasyczny — zakres `repo`, precyzyjny —
Contents: Read and write.

Gdy historia została świadomie przepisana lokalnie, `KRATKOZA_FORCE_PUSH=1` nadpisze zdalną.
Używa `--force-with-lease`, które odmawia, jeśli zdalne repo ruszyło od ostatniego pobrania — więc
nadpisze własne przepisanie, ale nie czyjąś pracę, która w międzyczasie doszła.

Wygodniejsza alternatywa, jeśli ciągłe wpisywanie zacznie przeszkadzać:
`git config --global credential.helper 'cache --timeout=3600'` trzyma token w pamięci przez godzinę
i nie zapisuje go na dysk. `credential.helper store` **zapisuje token czystym tekstem** w
`~/.git-credentials` — działa, ale trzeba wiedzieć, na co się zgadzasz.

## Testy

Testy jednostkowe pokrywają całą logikę domenową i repozytorium — bez emulatora, bez Robolectrica.
Testy instrumentacyjne (`app/src/androidTest`) wymagają urządzenia lub emulatora, którego w tym
środowisku WSL nie ma; uruchamia się je przez `./gradlew :app:connectedDebugAndroidTest`
z podłączonym urządzeniem.
