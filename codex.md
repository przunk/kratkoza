# Zadanie: wygaszenie Daily i licznik podpowiedzi

> Uzgodnione z właścicielem 2026-09-02. Obowiązuje `codex_instructions.md`.

**Gałąź:** `feature/daily-expiry-and-hint-tracking`, odbita od aktualnego `develop` (`fa38115`).

## Kontrakt produktu

- Rankingowa próba Daily zachowuje kwalifikację tylko do końca cyklu planszy, czyli do następnej
  zmiany Daily o 07:00 UTC.
- Po przekroczeniu granicy plansza pozostaje grywalna, ale staje się trwale nierankingowa. Zegar nie
  cofa się i nie gubi czasu przy zmianie trybu.
- Wygaśnięcie jest sprawdzane po wczytaniu zapisu, podczas otwartej gry i bezpośrednio przed
  utrwaleniem wyniku, żeby ukończenie tuż po granicy nie trafiło do rankingu.
- Gra kwalifikująca się do rankingu nie pokazuje i nie wykonuje podpowiedzi. Po wygaśnięciu Daily
  podpowiedź jest dostępna jak w zwykłej grze.
- Licznik podpowiedzi zwiększa się tylko wtedy, gdy podpowiedź faktycznie odsłoni cyfrę. Próba bez
  zaznaczenia, na pełnym polu albo w grze rankingowej nie zmienia licznika.
- Licznik jest częścią aktywnego zapisu, wyniku w `game_history` i trwałego archiwum ukończonych
  plansz Solo. Jest widoczny w historii plansz.
- Cofnięcie nie zmniejsza licznika podpowiedzi, a restart aplikacji go nie zeruje.

## Dane

- Rezerwacja wersji Room: **6**.
- Migracja 5→6 dodaje `hintCount INTEGER NOT NULL DEFAULT 0` do `saved_games`, `game_history` i
  `solved_boards` bez usuwania danych.
- Wyeksportowany schemat 6 trafia do repozytorium.
- Migracja ma test na prawdziwym SQLite z danymi wersji 5.

## Etapy i weryfikacja

1. Wygaszenie kwalifikacji na granicy cyklu Daily wraz z testami zegara, wznowienia i ukończenia.
2. Licznik podpowiedzi, migracja, zapis wyniku i prezentacja w historii wraz z testami warstw.
3. Celowane testy w trakcie, następnie pełny skrypt testów, lint i release APK.
4. Końcowy APK kopiowany do `/mnt/workspace/Kratkoza/dist/`; zachowanie na telefonie potwierdza
   właściciel przed scaleniem.
