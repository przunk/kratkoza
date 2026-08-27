# Zadania po Twojej stronie: Google Play Console

Wszystko poniżej blokuje etap 2 rankingu i **żaden agent nie może tego zrobić za Ciebie** — wymaga
konta, płatności i decyzji, które wiążą prawnie. Kolejność ma znaczenie: punkt 2 jest nieodwracalny,
a punkt 6 wymaga, żeby aplikacja już istniała w konsoli.

Zaznaczaj, co gotowe.

---

## 1. Konto dewelopera

- [ ] Konto Google Play Developer — jednorazowa opłata 25 USD, weryfikacja tożsamości potrafi
      zająć kilka dni.

Zacznij od tego, bo czas weryfikacji jest poza Twoją kontrolą, a wszystko inne na nim stoi.

## 2. Aplikacja w konsoli — decyzja nieodwracalna

- [ ] Utwórz aplikację; nazwa, język domyślny, typ „gra", darmowa.
- [ ] **Potwierdź `applicationId`: `com.przunk.kratkoza`.**

To ostatni moment. Po pierwszej publikacji identyfikatora nie da się zmienić — zmiana oznacza nową
aplikację, nową stronę w sklepie i utratę wszystkich instalacji. Jeśli masz jakiekolwiek wątpliwości
co do nazwy, teraz jest czas.

## 3. Klucz podpisujący

- [ ] Klucz upload już masz. **Zrób kopię zapasową poza tym dyskiem.**
- [ ] Zanotuj odcisk **SHA-1** klucza — będzie potrzebny w punkcie 6:

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
- [ ] Ikona 512×512 PNG.
- [ ] Grafika promocyjna 1024×500.
- [ ] Zrzuty ekranu telefonu — minimum dwa, przydadzą się: plansza, Daily, statystyki.

## 6. Play Games Services — to blokuje ranking

- [ ] **Play Games Services → utwórz grę** i powiąż ją z aplikacją `com.przunk.kratkoza`.
- [ ] **Dodaj odciski SHA-1 do poświadczeń OAuth.** Uwaga, to najczęstsze miejsce potknięcia —
      trzeba dodać **dwa lub trzy**, nie jeden:

  | klucz | skąd | po co |
  |---|---|---|
  | **klucz podpisujący aplikację** | Play Console → Integralność aplikacji | buildy z Play — bez tego logowanie działa lokalnie i pada u graczy |
  | klucz upload | `keytool -list -v -keystore ~/keys/kratkoza-upload.jks -alias kratkoza` | buildy wysyłane przez Ciebie |
  | klucz debug | `keytool -list -v -keystore ~/.android/debug.keystore -alias androiddebugkey -storepass android` | APK z `build-kratkoza-debug.sh` |

  Jeśli masz włączone Play App Signing (domyślne), Google **przepisuje** podpis po wysłaniu. Wtedy
  odcisk, który liczy się dla graczy, to ten z „Integralności aplikacji", a nie Twój klucz upload.
  Zarejestrowanie tylko uploadowego to klasyczny błąd, który objawia się jako „logowanie działa
  u mnie, nie działa u nikogo innego".

- [ ] **Utwórz trzy tablice wyników**, po jednej na poziom Daily. Sortowanie: **wynik większy jest
      lepszy**, format liczbowy całkowity.
- [ ] **Opublikuj konfigurację Play Games** — dopóki jest w wersji roboczej, nie działa nawet dla
      Ciebie.
- [ ] Dodaj siebie jako testera w sekcji testów Play Games.
- [ ] **Przekaż mi identyfikatory tablic.** Wyglądają jak `CgkI...`. Bez nich integracji nie da się
      napisać inaczej niż w ciemno.

Trzy fakty sprawdzone w dokumentacji, żebyś nie odkrywał ich w konsoli:

- limit to **70 tablic na grę**,
- **wersje dzienna i tygodniowa każdej tablicy powstają same** — nie zakładaj ich osobno,
- **nie ma filtrowania po regionie**; województwa wymagałyby 48 osobnych tablic tworzonych ręcznie,
  więc na start robimy tylko globalne.

## 7. Pierwsze wysłanie

- [ ] Zbuduj bundle: `./scripts/build-kratkoza-bundle.sh` — sam sprawdzi, czy podpisał kluczem
      upload, i odmówi, jeśli spadł na klucz debug.
- [ ] Wyślij na **testy zamknięte**, nie od razu do produkcji.
- [ ] Dołącz `app/build/outputs/mapping/release/mapping.txt`. Bez tego raport awarii z telefonu
      to lista ponumerowanych metod w klasie `a`.

---

## Czego jeszcze nie wiadomo

**Rejony.** Play Games nie umie filtrować po geografii, a `project.md` §27 przewiduje zakres
regionalny. Do wyboru: 48 tablic tworzonych ręcznie, własny backend, albo odłożenie regionów.
Nie ma potrzeby rozstrzygać tego przed pierwszym wysłaniem — globalne tablice wystarczą, żeby
ranking w ogóle ruszył.
