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
