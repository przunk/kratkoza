# Kratkoza — budowanie

Wszystkie skrypty siedzą w `scripts/` i same ustawiają JDK, Android SDK i Gradle
z `/mnt/workspace/.tooling`. Nie trzeba niczego eksportować ręcznie.

| Skrypt | Co robi |
|---|---|
| `scripts/build-kratkoza-debug.sh` | debug APK do wgrania na telefon |
| `scripts/build-kratkoza-release.sh` | release APK + weryfikacja, którym kluczem podpisany, do `dist/` |
| `scripts/test-kratkoza.sh` | testy jednostkowe |
| `scripts/lint-kratkoza.sh` | Android Lint |
| `scripts/kratkoza-env.sh` | wspólne środowisko, tylko do `source` |

Wszystkie przyjmują dodatkowe argumenty Gradle'a, np.:

```bash
./scripts/test-kratkoza.sh --tests '*SudokuGeneratorTest*'
```

Skrypty celowo **nie wypluwają logu Gradle'a**. Sukces to jedna linia; porażka to lista tego, co
nie przeszło — nazwy testów z komunikatami asercji, błędy kompilacji, znaleziska lintu — czytana
z raportów JUnit i lintu, nie z logu. Pełny log zawsze leży w `/tmp/kratkoza-*.log`.

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

## Testy

Testy jednostkowe pokrywają całą logikę domenową i repozytorium — bez emulatora, bez Robolectrica.
Testy instrumentacyjne (`app/src/androidTest`) wymagają urządzenia lub emulatora, którego w tym
środowisku WSL nie ma; uruchamia się je przez `./gradlew :app:connectedDebugAndroidTest`
z podłączonym urządzeniem.
