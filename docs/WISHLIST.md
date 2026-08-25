# Kratkoza — wishlista

Pomysły zgłoszone w trakcie pracy, które **nie są** wymaganiami z `project.md` i nie mają
przypisanego etapu. Nic stąd nie trafia do kodu bez osobnej decyzji.

Rzeczy, które `project.md` §40 nazywa „nice to have", są tam — nie tutaj. Ta lista zbiera to,
czego w specyfikacji w ogóle nie ma.

---

## 1. Grafika albo powolna animacja nad menu

**Zgłoszone:** 2026-08-25.

Ekran główny jest czysty, ale pusty nad przyciskami. Pomysł: wstawić tam grafikę albo
minimalistyczną, powolną animację — na przykład pole 3×3, w którym cyfry same się układają.

**Dlaczego to nie jest oczywiste:**

- `project.md` §36 wymaga, żeby animacje były subtelne i szybkie i nie odciągały nadmiernie
  uwagi. Dotyczy to ekranu gry, ale duch tego samego wymagania stosuje się do menu.
- Animacja w tle na ekranie głównym chodzi, dopóki ktoś na niego patrzy. Trzeba ją zatrzymać,
  gdy aplikacja idzie w tło, inaczej pali baterię za nic.
- Musi wyglądać dobrze w obu motywach i nie walczyć o uwagę z przyciskiem „Kontynuuj", który
  jest tam najważniejszy.

**Warianty:**

- **A — statyczna grafika.** Najtaniej, zero ryzyka, żadnego kosztu wydajności. Wymaga
  narysowania czegoś ładnego; obecny zestaw ikon daje punkt wyjścia dla stylu.
- **B — powolna animacja pola 3×3.** Cyfry pojawiają się i znikają w rytmie kilku sekund,
  bardzo niski kontrast, żeby nie krzyczała. Da się zrobić samym Compose, bez zależności.
- **C — plansza z ostatniej rozgrywki jako tło.** Powiązane z treścią, ale zaśmieca ekran
  i dubluje miniaturę z listy zapisanych gier.

Rekomendacja: **B**, jeśli ma być ruch — z twardym wymogiem zatrzymania animacji, gdy ekran
nie jest widoczny. **A**, jeśli priorytetem jest spokój.

---

## 2. Subtelne potrząśnięcie planszą przy błędzie

**Zgłoszone:** 2026-08-25.

Dziś błędnie wpisana cyfra rozświetla na czerwono swój wiersz, kolumnę i blok. Pomysł: dołożyć do
tego krótkie, delikatne drgnięcie całej planszy — sygnał, który czuje się kątem oka, nie patrząc
wprost na pole.

**Dlaczego to nie jest oczywiste:**

- `project.md` §36 wymienia animacje dla wpisania cyfry i ukończenia sekcji. Potrząśnięcia tam nie
  ma, a §36 zabrania animacjom przeszkadzać w szybkim wpisywaniu — ruch całej planszy w trakcie
  serii wpisów łatwo przekracza tę granicę.
- Ruch całego ekranu bywa problemem dla osób wrażliwych na animacje (choroba lokomocyjna,
  przedsionkowe zaburzenia równowagi). Android ma systemowe ograniczenie animacji i Compose je
  respektuje, więc trzeba się upewnić, że drgnięcie też pod nie podlega.
- Musi być bardzo krótkie i o małej amplitudzie, inaczej stanie się karą, a §16 mówi wprost, że
  błąd nie kończy gry i nie blokuje rozgrywki.

**Warianty:**

- **A** — drgnięcie samej komórki, nie całej planszy. Najmniej inwazyjne, nie rusza układu.
- **B** — drgnięcie całej planszy, 2–3 wychylenia po kilka pikseli, poniżej 200 ms.
- **C** — bez ruchu, zamiast tego krótka wibracja (haptyka). Nie przeszkadza wzrokowo i działa
  także wtedy, gdy gracz patrzy na panel cyfr, a nie na planszę.

Rekomendacja: **A albo C.** Potrząśnięcie całą planszą wygląda efektownie na filmiku i męczy przy
setnej pomyłce.
