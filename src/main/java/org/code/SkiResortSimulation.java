package org.code;

import com.google.gson.Gson;
import java.io.FileReader;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;


// --------------------- ENUMY ---------------------
enum Status {
    WAITING,    // narciarz czeka na wyciąg
    ON_LIFT,    // na wyciągu
    AT_STATION, // na stacji
    SKIING      // zjazd
}

enum WyciagStatus {
    RUNNING,
    MAINTENANCE
}

// ---------------- GŁÓWNA KLASA SYMULACJI ----------------
public class SkiResortSimulation {

    // ZBIORY OBIEKTÓW
    static List<Stacja> stacje = new ArrayList<>();
    static List<Trasa> trasy = new ArrayList<>();
    static List<Wyciag> wyciagi = new ArrayList<>();
    static List<Narciarz> narciarze = new ArrayList<>();

    // Callback aktualizujący GUI - dodane dla synchronizacji
    private static Runnable guiUpdateCallback = null;

    // ------ KLASY KONFIGURACJI (odpowiadają config.json) ------
    static class StationCfg {
        String name; // "baza", "polowa" itd.
        String type; // "bazowa", "posrednia", "szczyt"
    }

    static class RouteCfg {
        String name;     // np. "szczyt-baza"
        int duration;    // czas zjazdu
    }

    static class LiftCfg {
        String name;                // np. "A"
        String route;               // np. "baza-szczyt"
        int capacity;               // max osób jednocześnie na wyciągu
        int interval;               // czas jazdy (wjazdu) w sekundach
        int boardingGroupSize;      // ile osób wchodzi naraz (np. 4-os kanapa)
        int maintenanceTime;        // kiedy serwis
        int maintenanceDuration;    // jak długo serwis
    }

    // Konfiguracja – + nowy parametr globalBoardingInterval
    static class Config {
        List<StationCfg> stacje;
        List<RouteCfg> trasy;
        List<LiftCfg> wyciagi;
        int liczbaNarciarzy;
        int globalBoardingInterval; // co ile sekund pojawia się "jedna jednostka" do wsiadania
    }

    // Ustawienie callbacku do aktualizacji GUI
    public static void setGuiUpdateCallback(Runnable callback) {
        guiUpdateCallback = callback;
    }

    // --------------- METODA main ---------------
    public static void main(String[] args) throws Exception {

        // 1. Wczytanie configu
        Gson gson = new Gson();
        FileReader reader = new FileReader("src/main/resources/config.json");
        Config cfg = gson.fromJson(reader, Config.class);
        reader.close();

        // Uzupełnienie domyślnych wartości serwisu
        for (LiftCfg lc : cfg.wyciagi) {
            if (lc.maintenanceTime <= 0) {
                lc.maintenanceTime = 60;
            }
            if (lc.maintenanceDuration <= 0) {
                lc.maintenanceDuration = 20;
            }
            if (lc.boardingGroupSize <= 0) {
                lc.boardingGroupSize = 1; // minimalnie 1 osoba naraz
            }
        }

        // 2. Tworzenie stacji
        Map<String, Stacja> stationMap = new HashMap<>();
        for (StationCfg sc : cfg.stacje) {
            Stacja st = new Stacja(sc.name, sc.type);
            stacje.add(st);
            stationMap.put(sc.name, st);
        }

        // 3. Tworzenie tras (zjazd)
        Map<String, Trasa> routeMap = new HashMap<>();
        for (RouteCfg rc : cfg.trasy) {
            String[] parts = rc.name.split("-");
            Stacja a = stationMap.get(parts[0]);
            Stacja b = stationMap.get(parts[1]);
            Trasa tr = new Trasa(rc.name, a, b, rc.duration);
            trasy.add(tr);
            routeMap.put(rc.name, tr);
        }

        // 4. Tworzenie wyciągów
        for (LiftCfg lc : cfg.wyciagi) {
            Trasa tr = routeMap.get(lc.route);
            if (tr == null) {
                // jeśli brak trasy, tworzymy "zerową"
                String[] parts = lc.route.split("-");
                Stacja a = stationMap.get(parts[0]);
                Stacja b = stationMap.get(parts[1]);
                tr = new Trasa(lc.route, a, b, 0);
                trasy.add(tr);
                routeMap.put(lc.route, tr);
            }
            Wyciag w = new Wyciag(
                    lc.name, tr,
                    lc.capacity, lc.interval, lc.boardingGroupSize,
                    lc.maintenanceTime, lc.maintenanceDuration,
                    cfg.globalBoardingInterval // przekazujemy globalny
            );
            wyciagi.add(w);
        }

        // 5. Tworzenie narciarzy
        Stacja baza = stationMap.get("baza");
        for (int i = 1; i <= cfg.liczbaNarciarzy; i++) {
            Narciarz nar = new Narciarz(i, baza, wyciagi);
            narciarze.add(nar);
        }

        // 6. Rejestracja na stacji startowej
        for (Narciarz nar : narciarze) {
            if (nar.aktualnaStacja != null) {
                nar.aktualnaStacja.narciarzPrzybyl(nar);
                nar.status = Status.WAITING;
            }
        }

        // 7. Start wątków (najpierw wyciągi, potem narciarze)
        for (Wyciag w : wyciagi) {
            w.start();
        }
        for (Narciarz nar : narciarze) {
            nar.start();
        }

        // 8. Prosta pętla UI – co 2 sekundy
        while (true) {
            System.out.println("========================================");
            for (Stacja st : stacje) {
                System.out.println("Stacja " + st.nazwa + ": " + st.getLiczbaNarciarzy() + " narciarzy");
            }
            for (Wyciag w : wyciagi) {
                String statusText = w.getStatus() == WyciagStatus.MAINTENANCE ? " [SERWIS]" : "";
                System.out.println("Wyciąg " + w.name + " (" + w.trasa.name + "): " +
                        w.getNaWyciagu() + " na wyciągu" + statusText);
            }
            for (Trasa tr : trasy) {
                if (tr.duration > 0) {
                    System.out.println("Trasa " + tr.name + ": " + tr.getNaTrasie() + " w trakcie zjazdu");
                }
            }

            // Wywołaj callback aktualizacji GUI, jeśli został ustawiony
            if (guiUpdateCallback != null) {
                guiUpdateCallback.run();
            }

            Thread.sleep(2000);
        }
    }
}

// ----------------- MODELE -----------------

class Stacja {
    String nazwa;
    String typ;
    private AtomicInteger liczbaNarciarzy = new AtomicInteger(0);
    private int poziom; // 0=baza,1=pośrednia,2=szczyt

    public Stacja(String nazwa, String typ) {
        this.nazwa = nazwa;
        this.typ = typ;
        this.poziom = okreslPoziom();
    }

    private int okreslPoziom() {
        if (typ != null) {
            String t = typ.toLowerCase();
            if (t.contains("baz")) return 0;
            if (t.contains("posred") || t.contains("pol")) return 1;
            if (t.contains("szczyt")) return 2;
        }
        String n = nazwa.toLowerCase();
        if (n.contains("baza"))   return 0;
        if (n.contains("polowa") || n.contains("pośred")) return 1;
        if (n.contains("szczyt") || n.contains("góra") || n.contains("top")) return 2;
        return 0;
    }

    public int getPoziom() {
        return poziom;
    }

    public void narciarzPrzybyl(Narciarz nar) {
        liczbaNarciarzy.incrementAndGet();
    }

    public void narciarzOdszedl(Narciarz nar) {
        liczbaNarciarzy.decrementAndGet();
    }

    public int getLiczbaNarciarzy() {
        return liczbaNarciarzy.get();
    }
}

class Trasa {
    String name;
    Stacja stacja1;
    Stacja stacja2;
    Stacja stacjaDolna;
    Stacja stacjaGorna;
    int duration; // sekundy – tak jak w config.json

    private AtomicInteger naTrasie = new AtomicInteger(0);

    public Trasa(String name, Stacja s1, Stacja s2, int duration) {
        this.name = name;
        this.stacja1 = s1;
        this.stacja2 = s2;
        this.duration = duration;
        if (s1 != null && s2 != null) {
            if (s1.getPoziom() <= s2.getPoziom()) {
                stacjaDolna = s1;
                stacjaGorna = s2;
            } else {
                stacjaDolna = s2;
                stacjaGorna = s1;
            }
        }
    }

    public void narciarzStart() {
        naTrasie.incrementAndGet();
    }

    public void narciarzKoniec() {
        naTrasie.decrementAndGet();
    }

    public int getNaTrasie() {
        return naTrasie.get();
    }
}

class Narciarz extends Thread {
    int id;
    Stacja aktualnaStacja;
    Status status;
    private List<Wyciag> wyciagi;
    private Random random = new Random();
    Semaphore semaforDojechal = new Semaphore(0);

    public Narciarz(int id, Stacja start, List<Wyciag> wyciagi) {
        this.id = id;
        this.aktualnaStacja = start;
        this.status = Status.WAITING;
        this.wyciagi = wyciagi;
    }

    public void powiadomDojechal() {
        semaforDojechal.release();
    }

    // Narciarz oczekuje, aż wyciąg nie będzie w serwisie
    // i dołączy do kolejki w wyciągu
    public void wsiadzNaWyciag(Wyciag wyciag) throws InterruptedException {
        while (wyciag.getStatus() == WyciagStatus.MAINTENANCE || wyciag.isMaintenancePending()) {
            Thread.sleep(10);
        }
        wyciag.kolejkaOczekujacych.put(this);
        semaforDojechal.acquire(); // czeka, aż wyciąg powie "dotarłeś"
    }

    // Wybór losowej stacji docelowej
    private Stacja wybierzLosowaStacjeDocelowa() {
        List<Stacja> cele = new ArrayList<>();
        int p = aktualnaStacja.getPoziom();
        if (p == 0) { // baza -> (1,2)
            for (Stacja st : SkiResortSimulation.stacje) {
                if (st.getPoziom() > 0) cele.add(st);
            }
        } else if (p == 1) { // pośrednia -> (0,2)
            for (Stacja st : SkiResortSimulation.stacje) {
                if (st.getPoziom() != 1) cele.add(st);
            }
        } else { // p==2, szczyt -> (0,1)
            for (Stacja st : SkiResortSimulation.stacje) {
                if (st.getPoziom() < 2) cele.add(st);
            }
        }
        if (cele.isEmpty()) return null;
        return cele.get(random.nextInt(cele.size()));
    }

    // BFS, by znaleźć ciąg wyciągów do stacji docelowej
    private List<Wyciag> znajdzSciezkeWyciagow(Stacja from, Stacja to) {
        Map<Stacja, Wyciag> poprzedni = new HashMap<>();
        Queue<Stacja> kolejka = new ArrayDeque<>();
        Set<Stacja> odwiedzone = new HashSet<>();
        kolejka.add(from);
        odwiedzone.add(from);

        Stacja dest = null;
        while (!kolejka.isEmpty()) {
            Stacja cur = kolejka.poll();
            if (cur == to) {
                dest = cur;
                break;
            }
            for (Wyciag w : wyciagi) {
                if (w.getStatus() == WyciagStatus.MAINTENANCE || w.isMaintenancePending()) continue;
                if (w.trasa.stacjaDolna == cur) {
                    Stacja next = w.trasa.stacjaGorna;
                    if (!odwiedzone.contains(next)) {
                        odwiedzone.add(next);
                        poprzedni.put(next, w);
                        kolejka.add(next);
                    }
                }
            }
        }

        List<Wyciag> sciezka = new ArrayList<>();
        if (dest != null) {
            Stacja cur = dest;
            while (cur != from) {
                Wyciag w = poprzedni.get(cur);
                if (w == null) break;
                sciezka.add(0, w);
                cur = w.trasa.stacjaDolna;
            }
        }
        return sciezka;
    }

    // Szukanie / tworzenie trasy zjazdowej
    private Trasa znajdzTraseZjazdu(Stacja from, Stacja to) {
        String nazwa = from.nazwa + "-" + to.nazwa;
        for (Trasa t : SkiResortSimulation.trasy) {
            if (t.name.equals(nazwa) && t.duration > 0) return t;
        }
        int d = 5 + random.nextInt(5);
        Trasa nowa = new Trasa(nazwa, from, to, d);
        SkiResortSimulation.trasy.add(nowa);
        return nowa;
    }

    @Override
    public void run() {
        try {
            while (true) {
                // Wybór celu
                Stacja cel = wybierzLosowaStacjeDocelowa();
                if (cel == null) {
                    Thread.sleep(50);
                    continue;
                }
                // wjazd w górę
                if (cel.getPoziom() > aktualnaStacja.getPoziom()) {
                    List<Wyciag> sciezka = znajdzSciezkeWyciagow(aktualnaStacja, cel);
                    if (sciezka.isEmpty()) {
                        Thread.sleep(100);
                        continue;
                    }
                    for (Wyciag w : sciezka) {
                        status = Status.WAITING;
                        wsiadzNaWyciag(w);
                        aktualnaStacja = w.trasa.stacjaGorna;
                        status = Status.AT_STATION;
                    }
                }
                // zjazd w dół
                else if (cel.getPoziom() < aktualnaStacja.getPoziom()) {
                    status = Status.AT_STATION;
                    Thread.sleep(20);
                    aktualnaStacja.narciarzOdszedl(this);
                    Trasa zjazd = znajdzTraseZjazdu(aktualnaStacja, cel);
                    status = Status.SKIING;
                    zjazd.narciarzStart();
                    Thread.sleep(zjazd.duration * 1000L);
                    zjazd.narciarzKoniec();
                    aktualnaStacja = cel;
                    aktualnaStacja.narciarzPrzybyl(this);
                    status = Status.WAITING;
                }
                else {
                    Thread.sleep(50);
                    continue;
                }
                // odpoczynek
                Thread.sleep(1000 + random.nextInt(2000));
            }
        } catch (InterruptedException e) {
            // koniec wątku
        }
    }
}

// Wyciąg – z nowym boardingGroupSize + globalBoardingInterval
class Wyciag extends Thread {
    String name;                // jak w config
    Trasa trasa;
    int capacity;               // max osób jednocześnie na wyciągu
    int interval;               // czas jazdy (sek)
    int boardingGroupSize;      // ile osób wchodzi naraz
    int maintenanceTime;        // kiedy start serwisu
    int maintenanceDuration;    // ile trwa serwis
    int globalBoardingInterval; // co ile sek. "podjeżdża" krzesełko do wsiadania

    private long startTime;
    private AtomicBoolean inMaintenance = new AtomicBoolean(false);
    private volatile boolean maintenancePending = false;

    // kolejka oczekujących
    BlockingQueue<Narciarz> kolejkaOczekujacych = new LinkedBlockingQueue<>();
    // narciarze aktualnie w drodze
    private ConcurrentHashMap<Narciarz, Long> narciarzeDoCzasuPrzybycia = new ConcurrentHashMap<>();
    private AtomicInteger naWyciagu = new AtomicInteger(0);

    public Wyciag(String name, Trasa trasa, int capacity, int interval, int boardingGroupSize,
                  int maintenanceTime, int maintenanceDuration, int globalBoardingInterval) {
        this.name = name;
        this.trasa = trasa;
        this.capacity = capacity;
        this.interval = interval;
        this.boardingGroupSize = boardingGroupSize;
        this.maintenanceTime = maintenanceTime;
        this.maintenanceDuration = maintenanceDuration;
        this.globalBoardingInterval = globalBoardingInterval;

        this.startTime = System.currentTimeMillis();
    }

    public boolean isMaintenancePending() {
        return maintenancePending;
    }

    public int getNaWyciagu() {
        return naWyciagu.get();
    }

    public WyciagStatus getStatus() {
        return inMaintenance.get() ? WyciagStatus.MAINTENANCE : WyciagStatus.RUNNING;
    }

    private void obsluzWysiadajacych() {
        long now = System.currentTimeMillis();
        for (Map.Entry<Narciarz, Long> e : narciarzeDoCzasuPrzybycia.entrySet()) {
            if (now >= e.getValue()) {
                Narciarz nar = e.getKey();
                trasa.stacjaGorna.narciarzPrzybyl(nar);
                naWyciagu.decrementAndGet();
                nar.powiadomDojechal();
                nar.status = Status.AT_STATION;
                narciarzeDoCzasuPrzybycia.remove(nar);
            }
        }
    }

    @Override
    public void run() {
        try {
            long lastBoardTime = System.currentTimeMillis();
            while (true) {
                // Sprawdzaj czy serwis
                long elapsed = (System.currentTimeMillis() - startTime) / 1000;
                if (elapsed >= maintenanceTime) {
                    maintenancePending = true;
                    // czekaj aż pusty
                    while (naWyciagu.get() > 0) {
                        obsluzWysiadajacych();
                        Thread.sleep(10);
                    }
                    // serwis
                    inMaintenance.set(true);
                    System.out.println("Wyciąg " + name + " serwis przez " + maintenanceDuration + " sek.");
                    Thread.sleep(maintenanceDuration * 1000L);
                    inMaintenance.set(false);
                    maintenancePending = false;
                    startTime = System.currentTimeMillis();
                    lastBoardTime = System.currentTimeMillis();
                    System.out.println("Wyciąg " + name + " koniec serwisu.");
                }
                else {
                    // obsługa zsiadających
                    obsluzWysiadajacych();
                    // co globalBoardingInterval sek. wpuszczamy max boardingGroupSize narciarzy
                    long now = System.currentTimeMillis();
                    if (!maintenancePending && now - lastBoardTime >= globalBoardingInterval * 1000L) {
                        int boarded = 0;
                        while (boarded < boardingGroupSize && naWyciagu.get() < capacity) {
                            Narciarz next = kolejkaOczekujacych.poll();
                            if (next == null) break; // nikt nie czeka
                            // wsiada
                            trasa.stacjaDolna.narciarzOdszedl(next);
                            naWyciagu.incrementAndGet();
                            next.status = Status.ON_LIFT;
                            // dotrze na górę za "interval" sekund
                            long arrivalTime = now + interval * 1000L;
                            narciarzeDoCzasuPrzybycia.put(next, arrivalTime);
                            boarded++;
                        }
                        lastBoardTime = now;
                    }
                    Thread.sleep(10);
                }
            }
        } catch (InterruptedException e) {
            // koniec wątku wyciągu
        }
    }
}