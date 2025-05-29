package org.code;

import com.google.gson.Gson; // Biblioteka do parowania JSON
import java.io.FileReader; // Do oczytywania plików
import java.util.*; // Stryktury danych
import java.util.concurrent.*; // Programowanie wielowątkowe
import java.util.concurrent.atomic.AtomicBoolean; // Wielowątkowe zmienne logiczne
import java.util.concurrent.atomic.AtomicInteger; // Wielowątkowe liczniki

enum Status {
    WAITING, // narciarz czeka na wyciąg
    ON_LIFT, // narciarz znajduje się na wyciągu
    AT_STATION, // narciarz jest na stacji
    SKIING // narcirz zjeżdża trasą
}

enum WyciagStatus {
    RUNNING, // wyciąg działa normalnie
    MAINTENANCE // wyciąg jest w trakcie serwisu
}

public class SkiResortSimulation {
    // zbiory obiektów, które przechowują wszystkie elementy symulacji
    static List<Stacja> stacje = new ArrayList<>(); // Lista wszystkich stacji
    static List<Trasa> trasy = new ArrayList<>(); // Lista wszystkich tras zjazdowych
    static List<Wyciag> wyciagi = new ArrayList<>(); // Lista wszystkich wyciągów
    static List<Narciarz> narciarze = new ArrayList<>(); // Lista wszystkich narciarzy

    // Callback aktualizujący GUI
    private static Runnable guiUpdateCallback = null;

    // Lock dla głównej pętli symulacji
    private static final Object simulationLock = new Object();
    private static volatile boolean shouldUpdate = false;

    static class StationCfg {
        String name; // baza, polowa, szczyt
        String type; // bazowa, posrednia, szczyt
    }

    static class RouteCfg {
        String name; // np. szczyt-baza
        int duration; // czas zjazdu - te wszystkie zmienne muszą być w config!
    }

    static class LiftCfg {
        String name; // nazwa wyciągu
        String route; // ...
        int capacity;
        int interval; // czas wjazdu
        int boardingGroupSize;
        int maintenanceTime;
        int maintenanceDuration;
    }

    static class Config {
        List<StationCfg> stacje; // konfiguracja stacji
        List<RouteCfg> trasy;
        List<LiftCfg> wyciagi;
        int liczbaNarciarzy;
        int globalBoardingInterval; // co ile sekund pojawia się jedna jednostka do wsiadania (krzesełko / gondola / orczyk)
    }

    public static void setGuiUpdateCallback(Runnable callback) {
        guiUpdateCallback = callback;
    }

    public static void triggerGuiUpdate() {
        synchronized(simulationLock) {
            shouldUpdate = true;
            simulationLock.notify();
        }
    }

    public static void main(String[] args) throws Exception {

        // Wczytanie configu JSON
        Gson gson = new Gson();
        FileReader reader = new FileReader("src/main/resources/config.json");
        Config cfg = gson.fromJson(reader, Config.class);
        reader.close();

        // Tworzenie stacji na podstawie konfiguracji
        Map<String, Stacja> stationMap = new HashMap<>(); // Mapa do szybkiego wyszukiwania stacji po nazwie
        for (StationCfg sc : cfg.stacje) {
            Stacja st = new Stacja(sc.name, sc.type);
            stacje.add(st);
            stationMap.put(sc.name, st);
        }

        // Tworzenie tras na podstawie konfiguracji
        Map<String, Trasa> routeMap = new HashMap<>(); // Mapa do szybkiego wyszukiwania tras po nazwie
        for(RouteCfg rc : cfg.trasy) {
            String[] parts = rc.name.split("-"); // Rozdzielenie nazwy trasy na stację początkową i końcową
            Stacja a = stationMap.get(parts[0]); // Pobranie stacji początkowej
            Stacja b = stationMap.get(parts[1]); // Pobranie stacji końcowej
            Trasa tr = new Trasa(rc.name, a, b, rc.duration); // Utworzenie nowej trasy
            trasy.add(tr);
            routeMap.put(rc.name, tr);
        }

        // Tworzenie wyciągów
        for(LiftCfg lc : cfg.wyciagi) {
            Trasa tr = routeMap.get(lc.route); // Pobieranie trasy dla wyciągu
            if(tr == null) {
                // Jeśli brak trasy w konfiguracji, tworzymy "zerową" trasę dla wyciągu
                String[] parts = lc.route.split("-");
                Stacja a = stationMap.get(parts[0]); // Stacja dolna
                Stacja b = stationMap.get(parts[1]); // Stacja górna
                tr = new Trasa(lc.route, a, b, 0); // Czas zjazdu = 0 (tylko dla wyciągu, nie do zjazdu)
                trasy.add(tr);
                routeMap.put(lc.route, tr);
            }
            // Utworzenie wyciągu z podanymi parametrami
            Wyciag w = new Wyciag(
                    lc.name, tr, lc.capacity, lc.interval, lc.boardingGroupSize,
                    lc.maintenanceTime, lc.maintenanceDuration,
                    cfg.globalBoardingInterval // Przekazujemy globalny interwał wsiadania
            );
            wyciagi.add(w);
        }

        // Tworzenie obiektów narciarzy
        Stacja baza = stationMap.get("baza"); // Wszyscy narciarze zaczynają w stacji bazowej
        for(int i = 1; i<= cfg.liczbaNarciarzy; i++) {
            Narciarz nar = new Narciarz(i, baza, wyciagi); // ID narciarza, stacja początkowa, lista wyciągów
            narciarze.add(nar);
        }

        // Rejestracja narciarzy na stacji startowej
        for(Narciarz nar : narciarze) {
            if(nar.aktualnaStacja != null) {
                nar.aktualnaStacja.narciarzPrzybyl(nar); // Zwiększenie licznika narciarzy na stacji
                nar.status = Status.WAITING; // Ustawienie statusu na "oczekujący"
            }
        }

        // Start wątków (najpierw wyciągi, potem narciarze)
        for (Wyciag w : wyciagi) {
            w.start(); // Uruchomienie wątku wyciągu
        }
        for (Narciarz nar : narciarze) {
            nar.start();
        }

        // Wątek do okresowego wyzwalania aktualizacji
        Timer updateTimer = new Timer(true);
        updateTimer.scheduleAtFixedRate(new TimerTask() {
            @Override
            public void run() {
                triggerGuiUpdate();
            }
        }, 2000, 2000);

        // Pętla dla TUI - co ok. 2 sekundy wypisuje statystyki symulacji
        while (true) {
            synchronized(simulationLock) {
                while (!shouldUpdate) {
                    simulationLock.wait(); // Pasywne czekanie na powiadomienie
                }
                shouldUpdate = false;
            }

            System.out.println("========================================");

            // Wypisanie informacji o stacjach
            for(Stacja st :stacje) {
                System.out.println("Stacja " + st.nazwa + ": " + st.getLiczbaNarciarzy() + " narciarzy");
            }

            // Wypisanie informacji o wyciągach
            for(Wyciag w : wyciagi) {
                String statusText = w.getStatus() == WyciagStatus.MAINTENANCE ? " [SERWIS]" : "";
                System.out.println("Wyciąg " + w.name + " (" + w.trasa.name + "): " + w.getNaWyciagu() +
                        " na wyciągu" + statusText);
            }

            // Wypisanie informacji o trasach zjazdowych
            for(Trasa tr : trasy) {
                if(tr.duration > 0) { // Tylko trasy faktycznie używane do zjazdu (nie zerowe)
                    System.out.println("Trasa " + tr.name + ": " + tr.getNaTrasie() + " w trakcie zjazdu");
                }
            }

            // Wywołanie callbacku aktualizacji GUI
            if(guiUpdateCallback != null) {
                guiUpdateCallback.run();
            }

        }
    }
}

class Stacja {
    String nazwa;
    String typ;
    private AtomicInteger liczbaNarciarzy = new AtomicInteger(0);
    private int poziom; // 0 - baza, 1 - pośrednia, 2 - szczyt

    // Kontrstruktor stacji
    public Stacja(String nazwa, String typ) {
        this.nazwa = nazwa;
        this.typ = typ;
        this.poziom = okreslPoziom();
    }

    // Metoda określająca poziom stacji na podstawie nazwy i typu
    private int okreslPoziom() {
        String t = typ.toLowerCase();
        if(t.contains("baz")) return 0;
        if(t.contains("posred") || t.contains("pol")) return 1;
        if(t.contains("szczyt")) return 2;
        return 0;
    }

    public int getPoziom() {
        return poziom;
    }

    public void narciarzPrzybyl(Narciarz nar) {
        liczbaNarciarzy.incrementAndGet(); // Zwiększenie licznika narciarzy na stacji
    }

    public void narciarzOdszedl(Narciarz nar) {
        liczbaNarciarzy.decrementAndGet(); // Zmniejszenie licznika narciarzy na stacji
    }

    public int getLiczbaNarciarzy() {
        return liczbaNarciarzy.get();
    }
}

class Trasa {
    String name;
    Stacja stacja1; // Pierwsza stacja na trasie
    Stacja stacja2; // Druga stacja na trasie
    Stacja stacjaDolna;
    Stacja stacjaGorna;
    int duration;

    private AtomicInteger naTrasie = new AtomicInteger(0);

    // Konstruktor trasy
    public Trasa(String name, Stacja s1, Stacja s2, int duration) {
        this.name = name;
        this.stacja1 = s1;
        this.stacja2 = s2;
        this.duration = duration;

        // Określenie, która stacja jest dolna, a która górna na podstawie poziomu
        if (s1 != null && s2 != null) {
            if(s1.getPoziom() <= s2.getPoziom()){
                stacjaDolna = s1;
                stacjaGorna = s2;
            }
            else {
                stacjaDolna = s2;
                stacjaGorna = s1;
            }
        }
    }

    public void narciarzStart() {
        naTrasie.incrementAndGet(); // Zwiększenie licznika narciarzy na trasie
    }

    public void narciarzKoniec() {
        naTrasie.decrementAndGet();
    }

    public int getNaTrasie() {
        return naTrasie.get();
    }
}

// Dziedziczy po Thread - każdy narciarz to osobny wątek
class Narciarz extends Thread {
    int id;
    Stacja aktualnaStacja;
    Status status;
    private List<Wyciag> wyciagi;
    private Random random = new Random();
    Semaphore semaforDojechal = new Semaphore(0); // Semafor do synchronizacji z wyciągiem

    // Konstruktor narciarza
    public Narciarz(int id, Stacja start, List<Wyciag> wyciagi) {
        this.id = id;
        this.aktualnaStacja = start;
        this.status = Status.WAITING; // Początkowy status - oczekujący
        this.wyciagi = wyciagi;
    }

    public void powiadomDojechal() {
        semaforDojechal.release(); // Zwolnienie semafora - narciarz może kontynuować działanie
    }

    // Narciarz oczekuje, aż wyciąg nie będzie w serwisie i dołącza do kolejki w wyciągu
    public void wsiadzNaWyciag(Wyciag wyciag) throws InterruptedException {
        // Pasywne czekanie na dostępność wyciągu używając wait/notify
        synchronized(wyciag.maintenanceLock) {
            while(wyciag.getStatus() == WyciagStatus.MAINTENANCE || wyciag.isMaintenancePending()) {
                wyciag.maintenanceLock.wait(); // Pasywne czekanie - wątek śpi
            }
        }
        wyciag.kolejkaOczekujacych.put(this); // Dodanie narciarza do kolejki oczekujących
        semaforDojechal.acquire(); // Blokada - czeka, aż wyciąg powiadomi o dotarciu
    }

    // Wybór losowej stacji docelowej (zawsze wybiera stację na innym poziomie niż obecnie się znajduje)
    private Stacja wybierzLosowaStacjeDocelowa() {
        List<Stacja> cele = new ArrayList<>(); // Lista możliwych stacji docelowych
        int p = aktualnaStacja.getPoziom();
        if(p == 0) {
            // jest w bazie, wybiera pośrednią lub szczyt (1 lub 2)
            for(Stacja st : SkiResortSimulation.stacje) {
                if(st.getPoziom() > 0) cele.add(st);
            }
        } else if (p == 1) {
            for(Stacja st : SkiResortSimulation.stacje) {
                if(st.getPoziom() != 1) cele.add(st);
            }
        } else {
            for(Stacja st : SkiResortSimulation.stacje) {
                if(st.getPoziom() < 2) cele.add(st);
            }
        }
        if(cele.isEmpty()) return null;
        return cele.get(random.nextInt(cele.size())); // Wybór losowej stacji z listy
    }

    // Narciarz najpierw tworzy "mapę" i szuka listy wyciągów jakimi ma dostać się do wybranej stacji, a potem odtwarza ją podczas przmieszczenia się na stoku
    private List<Wyciag> znajdzSciezkeWyciagow(Stacja from, Stacja to) {
        Map<Stacja, Wyciag> poprzedni = new HashMap<>();
        Queue<Stacja> kolejka = new ArrayDeque<>(); // Kolejka stacji do przetworzenia
        Set<Stacja> odwiedzone = new HashSet<>(); // Zbiór odwiedzonych stacji
        kolejka.add(from); // Dodanie stacji początkowej
        odwiedzone.add(from); // Oznaczenie jako odwiedzoną

        Stacja dest = null;
        while (!kolejka.isEmpty()) {
            Stacja cur = kolejka.poll(); // Pobieranie następnej stacji z kolejki
            if (cur == to) {
                dest = cur; // cur - current
                break;
            }

            // Przeglądanie wszystkich wyciągów
            for (Wyciag w : wyciagi) {
                // Pomijamy wyciągi w serwisie
                if(w.getStatus() == WyciagStatus.MAINTENANCE || w.isMaintenancePending()) continue;

                // Sprawdzamy czy wyciąg zaczyna się w aktualnej stacji
                if(w.trasa.stacjaDolna == cur) {
                    Stacja next = w.trasa.stacjaGorna; // Następna stacja to górna stacja wyciągu
                    if(!odwiedzone.contains(next)) { // Jeśli jeszcze nie odwiedzona
                        odwiedzone.add(next);
                        poprzedni.put(next, w);
                        kolejka.add(next);
                    }
                }
            }
        }

        // Odtworzenie ścieżki od stacji docelowej do początkowej
        List<Wyciag> sciezka = new ArrayList<>();
        if(dest != null) {
            Stacja cur = dest;
            while(cur != from) {
                Wyciag w = poprzedni.get(cur);
                if(w == null) break;
                sciezka.add(0, w); // Dodawanie na początek listy (odwrócona kolejność)
                cur = w.trasa.stacjaDolna;
            }
        }
        return sciezka; // Zwrócenie znalezionej ścieżki wyciągów
    }

    // Metoda znajdująca lub tworząca trasę zjazdową między stacjami
    private Trasa znajdzTraseZjazdu(Stacja from, Stacja to) {
        String nazwa = from.nazwa + "-" + to.nazwa;
        // Najpierw szukamy istniejącej trasy
        for (Trasa t : SkiResortSimulation.trasy) {
            if(t.name.equals(nazwa) && t.duration > 0) return t;
        }

        // Jeśli nie ma, tworzymy nową z losowym czasem zjazdu (5-10 sekund)
        int d = 5 + random.nextInt(5);
        Trasa nowa = new Trasa(nazwa, from, to, d);
        SkiResortSimulation.trasy.add(nowa);
        return nowa;
    }

    public void run (){
        try {
            while(true) {
                // Wybór celu podróży
                Stacja cel = wybierzLosowaStacjeDocelowa();
                if(cel == null) {
                    Thread.sleep(50); // Krótka pauza jeśli nie można wybrać celu (np. wszystkie wyciągi w serwisie)
                    continue;
                }

                // Wjazd w górę, jeśli cel jest wyżej niż aktualna stacja
                if(cel.getPoziom() > aktualnaStacja.getPoziom()) {
                    // Znajduje ścieżkę wyciągów do celu
                    List<Wyciag> sciezka = znajdzSciezkeWyciagow(aktualnaStacja, cel);
                    if(sciezka.isEmpty()) {
                        Thread.sleep(100); // Pauza, jeśli nie znaleziono ścieżki
                        continue;
                    }

                    // Korzystanie z każdego wyciągu po kolei
                    for(Wyciag w : sciezka) {
                        status = Status.WAITING; // Oczekiwanie na wyciąg
                        wsiadzNaWyciag(w);
                        aktualnaStacja = w.trasa.stacjaGorna; // Aktualizacja pozycji
                        status = Status.AT_STATION; // Aktualizacja pozycji - na jakiej stacji znajduje się narciarz?
                    }
                }

                // Zjazd w dół, jeśli cel jest niżej niż aktualna stacja
                else if(cel.getPoziom() < aktualnaStacja.getPoziom()) {
                    status = Status.AT_STATION; // Chwilowa przerwa na stacji, żeby wyświetlił się podczas aktualizacji GUI, a nie od razu zjechał
                    Thread.sleep(20);
                    aktualnaStacja.narciarzOdszedl(this); // Opuszczenie stacji
                    Trasa zjazd = znajdzTraseZjazdu(aktualnaStacja, cel); // Znajdowanie trasy zjazdu
                    status = Status.SKIING;
                    zjazd.narciarzStart(); // Rejestracja rozpoczęcia zjazdu
                    Thread.sleep(zjazd.duration * 1000L);
                    zjazd.narciarzKoniec();
                    aktualnaStacja = cel; // Aktualizacja pozycji
                    aktualnaStacja.narciarzPrzybyl(this); // Rejestracja przybycia na nową stację
                    status = Status.WAITING; // Czeka na kolejną aktywność
                }
                else {
                    Thread.sleep(50); // Jeśli cel na tym samym poziomie - chwila przerwy
                    continue;
                }

                // Odpoczynek narciarza między przejazdami (1-3) sekundy, żeby było go widać w GUI
                Thread.sleep(1000 + random.nextInt(2000));
            }
        } catch (InterruptedException e) {

        }
    }
}

class Wyciag extends Thread {
    String name;
    Trasa trasa;
    int capacity;
    int interval;
    int boardingGroupSize;
    int maintenanceTime;
    int maintenanceDuration;
    int globalBoardingInterval; // Czas co ile podjeżdża wyciąg jest ten sam dla całego stoku, różnią się jednak pojedmnością - capacity

    private long startTime; // Czas rozpoczęcia działania wyciągu
    private AtomicBoolean inMaintenance = new AtomicBoolean(false); // Flaga, która oznacza trwający serwis
    private volatile boolean maintenancePending = false; // Flaga, która oznacza planowany serwis

    // Lock dla synchronizacji serwisu
    final Object maintenanceLock = new Object();
    // Lock dla synchronizacji opróżniania wyciągu
    private final Object emptyLiftLock = new Object();


    // Kolejka oczekujących narciarzy
    BlockingQueue<Narciarz> kolejkaOczekujacych = new LinkedBlockingQueue<>();
    // Mapa narciarzy aktualnie na wyciągu i czasu ich przybycia na górną stację
    private ConcurrentHashMap<Narciarz, Long> narciarzeDoCzasuPrzybycia = new ConcurrentHashMap<>();
    private AtomicInteger naWyciagu = new AtomicInteger(0); // Licznik narciarzy na wyciągu

    // Konstruktor wyciągu
    public Wyciag(String name, Trasa trasa, int capacity, int interval, int boardingGroupSize,
                  int maintenanceTime, int maintenanceDuration, int globalBoardingInterval) {
        this.name = name; // Rozróżnienie między polami a zmiennymi lokalnymi (można by zrobić np. name = someName, ale mniej czytelne)
        this.trasa = trasa;
        this.capacity = capacity;
        this.interval = interval;
        this.boardingGroupSize = boardingGroupSize;
        this.maintenanceTime = maintenanceTime;
        this.maintenanceDuration = maintenanceDuration;
        this.globalBoardingInterval = globalBoardingInterval;

        this.startTime = System.currentTimeMillis(); // Inicjalizacja czasu startu
    }

    // Sprawdzenie, czy wyciąg jest w stanie oczekiwania na serwis
    public boolean isMaintenancePending() {
        return maintenancePending;
    }

    // Getter dla liczby narciarzy na wyciągu - getter zwraca wartość prywatnego pola klasy
    public int getNaWyciagu() {
        return naWyciagu.get();
    }

    public WyciagStatus getStatus() {
        return inMaintenance.get() ? WyciagStatus.MAINTENANCE : WyciagStatus.RUNNING;
    }

    // Metoda obsługująca wysiadających narciarzy (gdy dotrą do górnej stacji)
    private void obsluzWysiadajacych() {
        long now = System.currentTimeMillis();
        for(Map.Entry<Narciarz, Long> e :narciarzeDoCzasuPrzybycia.entrySet()) {
            // Sprawdzenie czy nadszedł czas przybycia
            if(now >= e.getValue()) {
                Narciarz nar = e.getKey();
                trasa.stacjaGorna.narciarzPrzybyl(nar); // Rejestracja przybycia na górną stację
                int currentCount = naWyciagu.decrementAndGet(); // Zmniejszenie licznika narciarzy na wyciągu
                nar.powiadomDojechal();
                nar.status = Status.AT_STATION; // Zmiana statusu narciarza
                narciarzeDoCzasuPrzybycia.remove(nar); // Usunięcie z mapy osbługiwanych

                // Powiadomienie o opróżnieniu wyciągu
                if(currentCount == 0) {
                    synchronized(emptyLiftLock) {
                        emptyLiftLock.notify();
                    }
                }
            }
        }
    }

    public void run() {
        try {
            long lastBoardTime = System.currentTimeMillis(); // Czas ostatniego wsiadania grupowego
            while(true) {
                // Sprawdzaj czy nadszedł czas na serwis
                long elapsed = (System.currentTimeMillis() - startTime) / 1000; // Czas który upłynął od startu
                if(elapsed >= maintenanceTime) {
                    maintenancePending = true; // Oznaczenie, że serwis jest planowany w najbliższym czasie

                    // Pasywne czekanie aż wyciąg będzie pusty
                    synchronized(emptyLiftLock) {
                        while(naWyciagu.get() > 0) {
                            obsluzWysiadajacych();
                            if(naWyciagu.get() > 0) {
                                emptyLiftLock.wait(100); // Czeka max 100ms lub do powiadomienia
                            }
                        }
                    }

                    // Przeprowadzenie serwisu
                    synchronized(maintenanceLock) {
                        inMaintenance.set(true); // Ustawienie statusu na serwis
                        System.out.println("Wyciąg " + name + " serwis przez " + maintenanceDuration + " sek.");
                        Thread.sleep(maintenanceDuration * 1000L); // Symulacja czasu trwania serwisu
                        inMaintenance.set(false); // Wyłączenie statusu serwisu
                        maintenancePending = false; // Wyłączenie flagi oczekiwania na serwis
                        startTime = System.currentTimeMillis(); // Reset czasu startu serwisu
                        lastBoardTime = System.currentTimeMillis(); // Reset czasu ostatniego wsiadania przed serwisem
                        System.out.println("Wyciąg " + name + " koniec serwisu.");

                        // Powiadomienie wszystkich czekających narciarzy
                        maintenanceLock.notifyAll();
                    }
                }
                else {
                    obsluzWysiadajacych();

                    // Co czas ustalony w globalBoardingInterval wpuszczamy max boardingGroupSize narciarzy
                    long now = System.currentTimeMillis();
                    // Sprawdzenie czy minął czas od ostatniego wsiadania i czy nie jest planowany serwis niedługo
                    if (!maintenancePending && now - lastBoardTime >= globalBoardingInterval * 1000L) {
                        int boarded = 0; // Licznik wsiadających w tej grupie
                        // Pętla wsiadania - dopóki nie osiągniemy limitu grupy lub pojemności danego wyciągu
                        while(boarded < boardingGroupSize && naWyciagu.get() < capacity) {
                            Narciarz next = kolejkaOczekujacych.poll(); // Pobranie następnego narciarza z kolejki
                            if(next == null) break; // Przerwanie jeśli kolejka jest już pusta

                            // Obsługa wsiadania
                            trasa.stacjaDolna.narciarzOdszedl(next); // Narciarz opuszcza stację dolną
                            naWyciagu.incrementAndGet(); // Więcej narciarzy na wyciągu (licznik)
                            next.status = Status.ON_LIFT; // Zmiana statusu narciarza

                            // Obliczenie czasu przybycia narciarza do górnej stacji
                            long arrivalTime = now + interval * 1000L;
                            narciarzeDoCzasuPrzybycia.put(next, arrivalTime); // Dodanie do mapy narciarza jadącego w górę
                            boarded++;
                        }
                        lastBoardTime = now; // Aktualizacja czasu ostatniego wsiadania
                    }
                    synchronized(this) {
                        this.wait(10);
                    }
                }
            }
        } catch (InterruptedException e) {

        }
    }

}
