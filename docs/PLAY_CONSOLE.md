# Zadania po Twojej stronie: Google Play Console

Wszystko poniżej blokuje etap 2 rankingu i **żaden agent nie może tego zrobić za Ciebie** — wymaga
konta, płatności i decyzji, które wiążą prawnie. Kolejność ma znaczenie: punkt 2 jest nieodwracalny,
a punkt 6 wymaga, żeby aplikacja już istniała w konsoli.

Zaznaczaj, co gotowe.

---

## 1. Konto dewelopera

- [x] Konto Google Play Developer — jednorazowa opłata 25 USD, weryfikacja tożsamości potrafi
      zająć kilka dni.

Zacznij od tego, bo czas weryfikacji jest poza Twoją kontrolą, a wszystko inne na nim stoi.

## 2. Aplikacja w konsoli — decyzja nieodwracalna

- [x] Utwórz aplikację; nazwa, język domyślny, typ „gra", darmowa.
- [x] **Potwierdź `applicationId`: `com.przunk.kratkoza`.**

To ostatni moment. Po pierwszej publikacji identyfikatora nie da się zmienić — zmiana oznacza nową
aplikację, nową stronę w sklepie i utratę wszystkich instalacji. Jeśli masz jakiekolwiek wątpliwości
co do nazwy, teraz jest czas.

## 3. Klucz podpisujący

- [ ] Klucz upload już masz. **Zrób kopię zapasową poza tym dyskiem.**
- [x] Zanotuj odcisk **SHA-1** klucza — będzie potrzebny w punkcie 6:

```bash
/mnt/workspace/.tooling/jdk/bin/keytool -list -v \
  -keystore ~/keys/kratkoza-upload.jks -alias kratkoza | grep SHA1
```

Utrata pliku albo hasła oznacza koniec aktualizacji pod tym `applicationId`. To najtańsza kopia
zapasowa, jaką w tym projekcie zrobisz.

## 4. Wymagane formularze

Play nie wpuści aplikacji bez nich, a wypełnić może je tylko właściciel.

- [ ] **Polityka prywatności** pod publicznym adresem URL. Kratkoza działa offline i nie zbiera
      danych — po podpięciu Play Games dojdzie identyfikator gracza. Napisz to wprost.
- [ ] **Data safety** — co aplikacja zbiera i wysyła. Dziś: nic. Po etapie 2: identyfikator gracza
      i wyniki.
- [ ] **Ocena treści** — kwestionariusz. Sudoku bez treści wrażliwych.
- [ ] **Grupa docelowa i reklamy** — brak reklam, brak zakupów.

## 5. Strona w sklepie

- [ ] Krótki i pełny opis, po polsku i angielsku — aplikacja jest w obu językach.
- [x] Ikona 512×512 PNG.
- [x] Grafika promocyjna 1024×500.
- [ ] Zrzuty ekranu telefonu — minimum dwa, przydadzą się: plansza, Daily, statystyki.

## 6. Play Games Services — to blokuje ranking

- [x] **Play Games Services → utwórz grę** i powiąż ją z aplikacją `com.przunk.kratkoza`.
- [x] Projekt Cloud i klient OAuth utworzone i podpięte pod aplikację.
- [x] Ikona i grafika gry, konfiguracja projektu.

### Odciski SHA-1 — krok po kroku

To jest najczęstsze miejsce potknięcia w całej konfiguracji, więc rozpisane dokładnie.

**Dlaczego trzy, a nie jeden.** Klient OAuth typu Android to para: *nazwa pakietu + jeden odcisk
SHA-1*. Play Games wpuszcza urządzenie do logowania tylko wtedy, gdy certyfikat, którym naprawdę
podpisano zainstalowany APK, ma swojego klienta OAuth w projekcie Cloud powiązanym z grą. Na
urządzeniach ląduje **kilka różnych certyfikatów**, więc jeden klient nie wystarczy:

| certyfikat | trafia na urządzenie gdy | skąd wziąć odcisk |
|---|---|---|
| **klucz podpisujący aplikację** | gracz pobiera z Play — Google przepisuje podpis | Play Console → Testowanie i publikowanie → **Integralność aplikacji** |
| klucz upload | instalujesz lokalnie to, co sam podpisałeś | `keytool` na `google-play-upload.jks` |
| klucz debug | instalujesz APK z `build-kratkoza-debug.sh` | już znany, patrz niżej |

Pominięcie pierwszego wiersza daje objaw „logowanie działa u mnie, nie działa u nikogo innego" —
bo u Ciebie na telefonie siedzi build podpisany kluczem upload albo debug, a u graczy build
przepisany przez Google.

**Krok 1 — zbierz trzy odciski.**

Debug (sprawdzony na tej maszynie, nic nie musisz robić):

```
DB:9E:B7:2C:7C:72:87:14:3F:AB:00:4A:E1:0C:D8:CE:C0:C2:7F:53
```

Upload — wpisz hasło, gdy zapyta. Hasło nie trafi ani do historii powłoki, ani do wyniku:

```bash
read -rs -p "haslo keystore: " KS && echo && \
  /mnt/workspace/.tooling/jdk/bin/keytool -list -v \
    -keystore /mnt/d/Projects/google-play-upload.jks -storepass "$KS" \
  | grep -E "Alias name|SHA1:"; unset KS
```

Klucz podpisujący: **Play Console → Testowanie i publikowanie → Integralność aplikacji →
Certyfikat klucza podpisywania aplikacji**. Skopiuj wiersz `SHA-1`.

**Krok 2 — sprawdź, który z nich już masz.** W **Google Cloud Console** otwórz projekt powiązany
z grą, dalej **Interfejsy API i usługi → Dane logowania**. W sekcji *Identyfikatory klientów
OAuth 2.0* kliknij pozycję typu Android — pokaże nazwę pakietu i odcisk. Porównaj z trzema
powyżej i zapisz, którego wiersza dotyczy.

**Krok 3 — dołóż dwa brakujące.** Tam samo: **+ Utwórz dane logowania → Identyfikator klienta
OAuth → Typ aplikacji: Android**. Dla każdego brakującego odcisku:

- Nazwa: taka, żeby dało się je rozróżnić, np. `Kratkoza – klucz upload`,
- Nazwa pakietu: `com.przunk.kratkoza` (identyczna we wszystkich trzech),
- Odcisk certyfikatu SHA-1: wklej odcisk z kroku 1.

Wszystkie trzy muszą powstać **w tym samym projekcie Cloud**, tym, który jest powiązany z grą.
Klient OAuth w innym projekcie nie zadziała i nie da żadnego komunikatu o błędzie.

**Krok 4 — wróć do Play Games Services → Konfiguracja → Dane logowania** i upewnij się, że gra ma
poświadczenie typu Android wskazujące na klienta z kluczem podpisywania aplikacji. Jeśli konsola
pozwala dodać kolejne poświadczenia Android, dodaj też pozostałe dwa. Jeśli nie pozwala — nic
straconego, dopasowanie i tak idzie po projekcie Cloud.

**Krok 5 — dodaj siebie jako testera:** Play Games Services → **Testerzy**. Zanim gra zostanie
opublikowana, zalogują się wyłącznie konta z tej listy. Bez tego własny build wygląda jak zepsuty.

### Reszta konfiguracji

- [x] **Utwórz trzy tablice wyników**, po jednej na poziom Daily. Sortowanie: **wynik większy jest
      lepszy**, format liczbowy całkowity.
- [x] **Opublikuj konfigurację Play Games** — dopóki jest w wersji roboczej, nie działa nawet dla
      Ciebie.
- [x] **Przekaż mi identyfikatory tablic.** Wyglądają jak `CgkI...`. Znajdziesz je przy każdej
      tablicy w Play Games Services → Tablice wyników. Bez nich integracji nie da się napisać
      inaczej niż w ciemno.
- [x] **Nazwa trzeciej tablicy.** Nazwałeś ją „Daily – deadly”, a poziom w aplikacji nazywa się
      **Master**. Play pokazuje graczowi nazwę z konsoli, więc albo zmień ją na „Daily – master”,
      albo powiedz, że zostaje — wtedy tak samo nazwiemy poziom w aplikacji. Byle nie zostały dwie
      różne nazwy jednej rzeczy.

Trzy fakty sprawdzone w dokumentacji, żebyś nie odkrywał ich w konsoli:

- limit to **70 tablic na grę**,
- **wersje dzienna i tygodniowa każdej tablicy powstają same** — nie zakładaj ich osobno,
- **nie ma filtrowania po regionie**; województwa wymagałyby 48 osobnych tablic tworzonych ręcznie,
  więc na start robimy tylko globalne.

## 7. Pierwsze wysłanie

- [x] Zbuduj bundle: `./scripts/build-kratkoza-bundle.sh` — sam sprawdzi, czy podpisał kluczem
      upload, i odmówi, jeśli spadł na klucz debug.
- [x] Wyślij na **testy zamknięte**, nie od razu do produkcji.
- [ ] Dołącz `app/build/outputs/mapping/release/mapping.txt`. Bez tego raport awarii z telefonu
      to lista ponumerowanych metod w klasie `a`.

---

## Czego jeszcze nie wiadomo

**Rejony.** Play Games nie umie filtrować po geografii, a `project.md` §27 przewiduje zakres
regionalny. Do wyboru: 48 tablic tworzonych ręcznie, własny backend, albo odłożenie regionów.
Nie ma potrzeby rozstrzygać tego przed pierwszym wysłaniem — globalne tablice wystarczą, żeby
ranking w ogóle ruszył.
