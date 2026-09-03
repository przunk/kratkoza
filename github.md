# Przygotowanie repozytorium do publikacji

Stan ustaleń z 2026-09-03. Dokument opisuje decyzje przed zmianą widoczności repozytorium z
prywatnego na publiczne. Nie jest listą wymagań aplikacji.

## Licencja — decyzja otwarta

Publiczne repozytorium bez pliku `LICENSE` można czytać i forkować technicznie, ale domyślnie
pozostaje objęte pełnym prawem autorskim. Samo ustawienie widoczności na publiczną nie daje innym
osobom prawa do używania, modyfikowania ani rozpowszechniania kodu. Do czasu wyboru licencji należy
traktować projekt jako „all rights reserved”.

### MIT

- Pozwala używać, modyfikować i rozpowszechniać kod, również w produktach zamkniętych i płatnych.
- Wymaga zachowania informacji o prawach autorskich i tekstu licencji.
- Nie wymaga publikowania kodu zmian ani kodu produktu korzystającego z projektu.
- Wyłącza gwarancję i odpowiedzialność autora.
- Nie zawiera tak wyraźnej, rozbudowanej licencji patentowej jak Apache-2.0.
- Jest krótka, łatwa do zrozumienia i dobra, gdy priorytetem jest maksymalnie swobodne ponowne
  wykorzystanie kodu.

### Apache License 2.0

- Podobnie jak MIT pozwala na użycie komercyjne, modyfikacje i zamknięte produkty pochodne.
- Wymaga zachowania licencji, informacji o prawach autorskich oraz oznaczenia zmodyfikowanych
  plików; jeżeli projekt ma plik `NOTICE`, trzeba zachować wymagane informacje także z niego.
- Nie wymaga publikowania kodu zmian ani produktu pochodnego.
- Zawiera wyraźne udzielenie licencji patentowej przez autorów oraz mechanizm jej wygaśnięcia wobec
  podmiotu rozpoczynającego spór patentowy dotyczący projektu.
- Jest dłuższa od MIT, ale lepiej opisuje prawa ważne dla większych i komercyjnych użytkowników.
- To rekomendowany wariant dla Kratkozy, jeżeli celem ma być liberalna licencja open source.

### GNU GPL v3

- Pozwala używać, modyfikować i sprzedawać program, ale jest licencją silnego copyleftu.
- Rozpowszechniając binarium lub utwór pochodny, trzeba udostępnić odpowiadający mu kod źródłowy na
  warunkach GPLv3 i zachować te same wolności dla odbiorców.
- Zawiera postanowienia patentowe oraz ochronę przed tzw. tivoizacją; w określonych urządzeniach
  dystrybutor musi dostarczyć informacje potrzebne do instalowania zmodyfikowanej wersji.
- Samo uruchamianie zmodyfikowanego programu jako usługi sieciowej nie uruchamia obowiązku
  publikacji źródeł — taki obowiązek jest charakterystyczny dla AGPL, nie GPL.
- Utrudnia włączenie kodu do aplikacji zamkniętej. W Kratkozie trzeba byłoby również osobno ocenić
  zgodność dystrybucji z własnościowym SDK Google Play Games albo dodać właściwie sformułowany
  wyjątek. Dlatego GPLv3 nie jest tu rekomendowana bez konsultacji licencyjnej.

Powyższe jest technicznym podsumowaniem konsekwencji, nie poradą prawną. Ostateczny wybór należy do
właściciela praw do projektu.

## Ustalenia

- Adres autora zapisany w historii commitów może pozostać publiczny. Nie przepisujemy historii z
  tego powodu.
- Główny `README.md` został dodany jako strona wejściowa repozytorium.
- Wewnętrzne pliki Markdown, w tym `codex.md`, `codex_instructions.md`, `docs/STATUS.md` i
  `docs/WISHLIST.md`, na razie pozostają w repozytorium. Przed właściwą premierą można wrócić do
  skrócenia lub zarchiwizowania nieaktualnych notatek.
- Odniesienia do innych prywatnych projektów zostały usunięte z dokumentacji Kratkozy.
- Skrypty pozostają na razie dostosowane do obecnego warsztatu i `/mnt/workspace/.tooling`.
  Przenośne wykrywanie JDK/Android SDK oraz usunięcie lokalnych ścieżek zapisujemy jako późniejsze
  zadanie.
- `docs/PLAY_CONSOLE.md` został zaktualizowany do obecnego stanu Play Games i rankingu.
- CI oraz `distributionSha256Sum` dla Gradle Wrapper odkładamy na później.
- Przed publikacją należy sprawdzić na GitHubie `Settings → General → Default branch`, zdecydować,
  czy publiczną bazą ma być `master`, czy `develop`, oraz usunąć zdalne gałęzie całkowicie scalone.
- Lokalne gałęzie całkowicie scalone z `develop` zostały usunięte. Gałęzie niezakończone i
  niescalone pozostają.

## Pozostałe kontrole przed zmianą widoczności

- Wybrać licencję i dodać plik `LICENSE`.
- Przejrzeć treści istniejące wyłącznie na GitHubie: Issues, Pull requests, Releases, Actions,
  artefakty, Wiki i Discussions. Lokalny klon nie pozwala potwierdzić, czy nie zawierają informacji
  przeznaczonych wyłącznie dla prywatnego repozytorium.
- Uporządkować zdalne gałęzie scalone z `develop`; obecnie są to
  `feature/additive-daily-score`, `feature/daily-stage-1` i `fix/back-stack-double-pop`. Agent nie
  usuwa ich bez poświadczeń właściciela.
- Przed publikacją najnowszej wersji scalić i wypchnąć zaakceptowane, nadal lokalne gałęzie funkcji.
- Kod efektu tytułowego jest niezależną implementacją Compose. Repozytorium będące inspiracją,
  `developedbyed/3d-card-effect`, nie deklaruje licencji, więc nie należy kopiować z niego kodu ani
  zasobów bez zgody autora.
