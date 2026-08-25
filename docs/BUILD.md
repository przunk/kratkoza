# Kratkoza — budowanie

Wszystkie skrypty siedzą w `scripts/` i same ustawiają JDK, Android SDK i Gradle
z `/mnt/workspace/.tooling`. Nie trzeba niczego eksportować ręcznie.

| Skrypt | Co robi |
|---|---|
| `scripts/build-kratkoza-debug.sh` | debug APK do wgrania na telefon |
| `scripts/build-kratkoza-release.sh` | release APK + weryfikacja, którym kluczem podpisany, do `dist/` |
| `scripts/test-kratkoza.sh` | testy jednostkowe |
| `scripts/lint-kratkoza.sh` | Android Lint |
| `scripts/build-kratkoza-bundle.sh` | AAB na Google Play, do `dist/` |
| `scripts/push-kratkoza.sh` | wypchnięcie na GitHuba, token pytany za każdym razem |
| `scripts/kratkoza-env.sh` | wspólne środowisko, tylko do `source` |

Wszystkie przyjmują dodatkowe argumenty Gradle'a, np.:

```bash
./scripts/test-kratkoza.sh --tests '*SudokuGeneratorTest*'
```

Skrypty celowo **nie wypluwają logu Gradle'a**. Sukces to jedna linia; porażka to lista tego, co
nie przeszło — nazwy testów z komunikatami asercji, błędy kompilacji, znaleziska lintu — czytana
z raportów JUnit i lintu, nie z logu. Pełny log zawsze leży w `/tmp/kratkoza-*.log`.

## Stosunek do skryptów w `.tooling`

Skrypty Kratkozy **nie są** pochodnymi `build-taskflow-release.sh` ani `build-opowio-release.sh` —
zostały napisane od zera, z tamtych wzięte są konwencje:

- układ `source <env>.sh` → build → weryfikacja
- artefakt do `dist/`, nazwany `kratkoza-<versionName>-<versionCode>-<stempel>.apk`
  (tak nazywa Opowio; TaskFlow i LocusLog wrzucają do `app/build/outputs/<projekt>/`)
- odczyt certyfikatu z **gotowego pliku** zamiast ufania konfiguracji Gradle'a
- raport rozmiaru APK

Świadomie pominięte, bo tamte skrypty mają po ~17 kB i wypisują kilkadziesiąt linii na starcie:
banner środowiska, podsumowanie czasów per faza, raporty `--profile`, wykrywanie JDK dla natywnego
Windowsa, podmienianie `local.properties`. Tutaj priorytetem jest krótki output. Jeśli któraś z tych
rzeczy okaże się potrzebna, dokłada się ją pojedynczo.

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
Można to nadpisać przez `KRATKOZA_GRADLE_USER_HOME`.

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

`build-kratkoza-release.sh` odczytuje certyfikat z gotowego pliku i mówi wprost, którym kluczem
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
./scripts/build-kratkoza-bundle.sh
```

Pyta o wszystko na bieżąco — ścieżkę do keystore'a, alias, hasła — i **nigdzie ich nie zapisuje**:
ani w repozytorium, ani w `~/.gradle/gradle.properties`, ani w historii powłoki. Trafiają do
Gradle'a przez zmienne `ORG_GRADLE_PROJECT_*` (tak Gradle czyta właściwość projektu ze środowiska)
i żyją tyle, co proces skryptu.

Artefakt ląduje w `dist/` jako `kratkoza-<versionName>-<versionCode>.aab`, razem z sumą SHA-256.

Bez terminala — na przykład w CI — te same wartości czyta ze zmiennych:

```bash
KRATKOZA_UPLOAD_KEYSTORE=/path/to/kratkoza-upload.jks \
KRATKOZA_UPLOAD_KEY_ALIAS=kratkoza \
KRATKOZA_UPLOAD_STORE_PASSWORD=… \
KRATKOZA_UPLOAD_KEY_PASSWORD=… \
./scripts/build-kratkoza-bundle.sh
```

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

## Repozytorium

`master` jest bazą, praca idzie na `develop`. Wydania powstają przez scalenie `develop` w `master`.

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

Wygodniejsza alternatywa, jeśli ciągłe wpisywanie zacznie przeszkadzać:
`git config --global credential.helper 'cache --timeout=3600'` trzyma token w pamięci przez godzinę
i nie zapisuje go na dysk. `credential.helper store` **zapisuje token czystym tekstem** w
`~/.git-credentials` — działa, ale trzeba wiedzieć, na co się zgadzasz.

## Testy

Testy jednostkowe pokrywają całą logikę domenową i repozytorium — bez emulatora, bez Robolectrica.
Testy instrumentacyjne (`app/src/androidTest`) wymagają urządzenia lub emulatora, którego w tym
środowisku WSL nie ma; uruchamia się je przez `./gradlew :app:connectedDebugAndroidTest`
z podłączonym urządzeniem.
