🎿 Symulacja Stoku Narciarskiego

📋 Opis Projektu
Wielowątkowa symulacja ruchu narciarzy na stoku narciarskim zaimplementowana w Javie z wykorzystaniem programowania współbieżnego. Projekt symuluje realistyczne zachowania narciarzy, działanie wyciągów oraz ich okresowe konserwacje, wszystko w czasie rzeczywistym z graficzną wizualizacją.

✨ Funkcjonalności

🏔️ Infrastruktura Stoku

3 stacje narciarskie: Baza, Stacja Pośrednia, Szczyt
3 wyciągi z różnymi parametrami:

Pojemność (max liczba narciarzy jednocześnie)
Częstotliwość kursowania
Wielkość grup wsiadających
Harmonogram konserwacji


Trasy zjazdowe dynamicznie tworzone między stacjami

👥 Zachowanie Narciarzy

Każdy narciarz jako osobny wątek
Losowy wybór destynacji przy każdym zatrzymaniu
Inteligentne wyszukiwanie tras z przesiadkami
Realistyczne czasy przejazdów i odpoczynku

🔧 System Konserwacji

Okresowe serwisy wyciągów
Bezpieczne zatrzymywanie - czeka aż wszyscy narciarze dojadą
Powiadomienia o statusie konserwacji

🖥️ Wizualizacja

GUI w Swing z animowaną mapą stoku
Czas rzeczywisty - aktualizacje co 2 sekundy
Szczegółowe statystyki i liczniki
Legenda i intuicyjne kolory

🛠️ Technologie

Java 11+
Swing - interfejs graficzny
Gson - obsługa konfiguracji JSON
Programowanie wielowątkowe:

Thread, AtomicInteger, AtomicBoolean
BlockingQueue, Semaphore, ConcurrentHashMap
volatile keywords

Parametry konfiguracji:

liczbaNarciarzy - liczba narciarzy w symulacji
globalBoardingInterval - częstotliwość kursowania (sekundy)
capacity - maksymalna pojemność wyciągu
interval - czas przejazdu wyciągiem (sekundy)
boardingGroupSize - ile narciarzy wsiada jednocześnie
maintenanceTime - co ile sekund serwis (sekundy)
maintenanceDuration - czas trwania serwisu (sekundy)

Wątki i Synchronizacja
┌─────────────────┐    ┌─────────────────┐    ┌─────────────────┐
│   Narciarz #1   │    │   Narciarz #2   │    │   Narciarz #N   │
│    (Thread)     │    │    (Thread)     │    │    (Thread)     │
└─────────────────┘    └─────────────────┘    └─────────────────┘
         │                       │                       │
         └───────────────────────┼───────────────────────┘
                                 │
         ┌───────────────────────▼───────────────────────┐
         │              Współdzielone Zasoby              │
         │  • Stacje (AtomicInteger liczniki)            │
         │  • Wyciągi (BlockingQueue, Semaphore)         │
         │  • Trasy (AtomicInteger, ConcurrentHashMap)   │
         └───────────────────────┬───────────────────────┘
                                 │
┌─────────────────┐    ┌─────────▼───────┐    ┌─────────────────┐
│   Wyciąg A      │    │   Wyciąg B      │    │   Wyciąg C      │
│   (Thread)      │    │   (Thread)      │    │   (Thread)      │
└─────────────────┘    └─────────────────┘    └─────────────────┘
         │                       │                       │
         └───────────────────────┼───────────────────────┘
                                 │
                    ┌────────────▼────────────┐
                    │      GUI Thread         │
                    │   (Swing + Repaint)     │
                    └─────────────────────────┘

📊 Przykładowe Wyjście
========================================
Stacja baza: 15 narciarzy
Stacja polowa: 8 narciarzy  
Stacja szczyt: 12 narciarzy
Wyciąg A (baza-szczyt): 6 na wyciągu
Wyciąg B (baza-polowa): 3 na wyciągu [SERWIS]
Wyciąg C (polowa-szczyt): 4 na wyciągu
Trasa szczyt-baza: 5 w trakcie zjazdu
Trasa polowa-baza: 2 w trakcie zjazdu
Trasa szczyt-polowa: 3 w trakcie zjazdu
========================================
