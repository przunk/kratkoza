# Kratkoza — dokumentacja

**Kratkoza** to gra Sudoku na Androida. Aplikacja jest w pełni grywalna w trybie solo:
generowanie plansz na pięciu poziomach, kandydaci, wykrywanie błędów, Undo, timer z pauzą,
automatyczny zapis, historia i statystyki, ustawienia, motyw jasny/ciemny/systemowy, polski
i angielski.

## Specyfikacja

Źródło prawdy leży w katalogu głównym projektu i **nie jest dokumentacją do edycji**:

- [`../project.md`](../project.md) — wymagania funkcjonalne
- [`../technical_requirements.md`](../technical_requirements.md) — wymagania techniczne

## Dokumenty

| Dokument | O czym |
|---|---|
| [ARCHITECTURE.md](ARCHITECTURE.md) | mapa kodu, warstwy, decyzje projektowe i ich uzasadnienia |
| [BUILD.md](BUILD.md) | jak zbudować, przetestować i podpisać |
| [OPEN_QUESTIONS.md](OPEN_QUESTIONS.md) | decyzje, których `project.md` świadomie nie rozstrzyga — z wariantami do ustalenia |
| [STATUS.md](STATUS.md) | co jest zrobione, czego jeszcze nie ma |
| [WISHLIST.md](WISHLIST.md) | pomysły spoza specyfikacji, czekające na decyzję |

## Szybki start

```bash
./scripts/build-kratkoza-debug.sh    # APK do wgrania
./scripts/test-kratkoza.sh           # testy
```
