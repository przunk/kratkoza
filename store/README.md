# Materiały do Google Play

Grafiki do zakładki **Rozwijanie → Obecność w sklepie → Główna strona sklepu**.
Nie są zasobami aplikacji — nie trafiają do APK ani do `app/src/main/res/`.

| plik | wymiar | gdzie wgrać |
|---|---|---|
| `icon-512.png` | 512×512 | Ikona aplikacji |
| `feature-graphic-1024x500.png` | 1024×500 | Grafika promocyjna |

Oba są 8-bitowym RGB bez kanału alfa i mieszczą się grubo poniżej limitów Play
(1 MB dla ikony, 15 MB dla grafiki promocyjnej). Transparentność jest w obu miejscach
odrzucana, dlatego tło jest pełne.

## Skąd się biorą

Rysuje je `scripts/make-store-assets.py` — czysty Python, bez bibliotek graficznych:

```
python3 scripts/make-store-assets.py
```

Skrypt nadpisuje oba pliki w tym katalogu. Kolory bierze z palety Slate
(`ui/theme/Color.kt`), więc po zmianie palety wystarczy go przepuścić ponownie.
Rysunek powstaje w czterokrotnym powiększeniu i jest uśredniany w dół — stąd gładkie
krawędzie bez żadnego antyaliasingu.

## Czego tu jeszcze nie ma

**Zrzuty ekranu telefonu** — Play wymaga minimum dwóch. Muszą powstać z działającej
aplikacji, więc nie da się ich wygenerować skryptem. Warto pokazać planszę w trakcie gry,
ekran Daily i statystyki.

## Uwaga o spójności z ikoną aplikacji

Ikona w sklepie i ikona na ekranie głównym telefonu to **dwa osobne zasoby** i dziś
różnią się schematem: `app/src/main/res/drawable/ic_launcher_foreground.xml` ma ciemną
planszę z jasnymi liniami, a `icon-512.png` odwrotnie — jasną planszę z ciemnymi liniami.
Play tego nie zablokuje, ale wytyczne zalecają, żeby wyglądały tak samo.

## Zrzuty ekranu

`store/screenshots/` — sześć plików, trzy ekrany w obu motywach, 720×1600 (360×800 dp przy xhdpi).

Generuje je `StoreScreenshotsTest`, czyli zwykły przebieg testów:

```
./scripts/test-kratkoza.sh
```

Jest to **generator, nie sprawdzian** — dlatego mieszka w drzewie testów. Renderowanie ekranu Compose
wymaga środowiska Androida, a to jedyne miejsce w projekcie, gdzie takie jest bez urządzenia. Wynik
jest deterministyczny, więc niezmieniony interfejs daje pliki co do bajtu takie same i git nic nie
widzi.

### Jak to działa, i czego nie da się tak zrobić

`captureToImage` z reguły testowej Compose **nie zadziała** — woła `forceRedraw` i czeka na klatkę
z prawdziwego okna, którego tu nie ma. Zamiast tego widok jest rysowany wprost na bitmapę, z
pominięciem okna.

Rozmiar urządzenia trzeba podać przez `@Config(qualifiers = …)`. Bez tego Compose układa się pod
domyślny ekran Robolectrica i treść siedzi w rogu bitmapy.

**Ekranu głównego tą drogą nie zrobisz.** Jego grafika planuje kolejną klatkę animacji w
nieskończoność, więc przesunięcie zegara do dowolnego momentu nigdy nie wraca — test wisi, a nie
pada. Zrzut ekranu głównego trzeba na razie zrobić z telefonu.
