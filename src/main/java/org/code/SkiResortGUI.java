package org.code;

import javax.swing.*;
import java.awt.*;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.awt.geom.Ellipse2D;
import java.awt.geom.Line2D;
import java.awt.geom.QuadCurve2D;
import java.util.HashMap;
import java.util.Map;
import java.util.Timer;

// Zdecydowałem się na nieużywanie javaFX, ponieważ nie miałem okazji testować jeszcze kodu ze Swing. Stwierdziłem, że spróbuję
// swoich sił. Animacja nie jest płynna, co umożliwiałaby javaFX, ale terminal aktualizuje się co 2 sekundy, więc interpretacja GUI
// jest adekwatna i czytelna w formie, którą zastosowałem niżej.

public class SkiResortGUI extends JFrame {

    // Stałe, które określają wymiary okna i elementów graficznych
    private static final int WIDTH = 800; // Szerokość okna
    private static final int HEIGHT = 600;
    private static final int STATION_PROMIEN = 30; // Promień okręgu, który reprezentuje pojedynczą stację

    // Mapy przechowujące widoki poszczególnych elementów stoku
    private final Map<String, StationView> stationViews = new HashMap<>(); // Widoki stacji narciarskich
    private final Map<String, LiftView> liftViews = new HashMap<>();
    private final Map<String, RouteView> routeViews = new HashMap<>();

    // Elementy interfejsu
    private final JLabel statusEtykieta; // Etykieta, która będzie wyświetlać status symulacji
    private final StokWidok stokWidok; // Płótno do rysowania elementów stoku
    private Timer updateTimer; // Timer do aktualizacji widoku co 2 sekundy

    public SkiResortGUI() {
        super("Symulacja Stoku Narciarskiego");

        // Główne okno
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setSize(WIDTH, HEIGHT);
        setLayout(new BorderLayout());

        // Panel statusu na dole okna
        statusEtykieta = new JLabel("Symulacja Stoku Narciarskiego");
        add(statusEtykieta, BorderLayout.SOUTH);

        // Panel rysowania w centrum okna
        stokWidok = new StokWidok();
        add(stokWidok, BorderLayout.CENTER);

        // Zamykanie okna
        addWindowListener(new WindowAdapter() {
            public void windowClosing(WindowEvent e) {
                if (updateTimer != null) {
                    updateTimer.cancel();
                }
                super.windowClosing(e);
            }
        });
    }

    public void start() {
        // Pokazanie okna
        setVisible(true);

        // Uruchomienie symulacji w tle
        initializeSimulation();

        // Aby była synchronizacja z terminalem, zamiast używać niezależnego timera, używam tej samej pętli co w TUI
        updateTimer = null; // brak niezależnego timera

        SkiResortSimulation.setGuiUpdateCallback(() -> {
            SwingUtilities.invokeLater(() -> {
                updateView();
                stokWidok.repaint();
            });
        });
    }

    private void initializeSimulation() {
        try {
            // Uruchamiam symulację w osobnym wątku
            new Thread(() -> {
                try {
                    SkiResortSimulation.main(new String[]{});
                } catch (Exception e) {
                    e.printStackTrace(); // podstawowa metoda obsługi wyjątków w Javie, drukuje na wyjście błędów pełny ślad stosu wywołań ...
                }
            }).start();

            Thread.sleep(1000);

            initializeStationViews();
            initializeRouteViews();
            initializeLiftViews();
        } catch (Exception e) {
            e.printStackTrace();
            statusEtykieta.setText("Błąd inicjalizacji: " + e.getMessage());
        }
    }

    private void initializeStationViews() {
        for (Stacja stacja : SkiResortSimulation.stacje) { // Chcę rozmieścić pozycje stacji w układzie trójkąta, ale podstawa wzdłu osi OY
            int poziom = stacja.getPoziom();
            double x, y;

            switch (poziom) {
                case 0: // Baza - dół, środek
                    x = WIDTH / 2;
                    y = HEIGHT - 100;
                    break;
                case 1: // Pośrednia, środek wysokości, po lewej
                    x = WIDTH / 4;
                    y = HEIGHT / 2;
                    break;
                case 2: // Szczyt - góra, środek
                    x = WIDTH / 2;
                    y = 100;
                    break;
                default:
                    x = WIDTH / 2;
                    y = HEIGHT / 2;
            }

            // Dodanie widoku stacji do mapy
            stationViews.put(stacja.nazwa, new StationView(stacja, x, y));
        }
    }

    private void initializeRouteViews() {
        for (Trasa trasa : SkiResortSimulation.trasy) {
            if (trasa.duration > 0) {
                // trasy zjazdowe (czas > 0)
                StationView start = stationViews.get(trasa.stacjaGorna.nazwa);
                StationView end = stationViews.get(trasa.stacjaDolna.nazwa);

                if (start != null && end != null) {
                    routeViews.put(trasa.name, new RouteView(trasa, start, end));
                }
            }
        }
    }

    private void initializeLiftViews() {
        for (Wyciag wyciag : SkiResortSimulation.wyciagi) {
            StationView start = stationViews.get(wyciag.trasa.stacjaDolna.nazwa);
            StationView end = stationViews.get(wyciag.trasa.stacjaGorna.nazwa);

            if (start != null && end != null) {
                liftViews.put(wyciag.name, new LiftView(wyciag, start, end));
            }
        }
    }

    private void updateView() {
        // Aktualizacja danych stacji
        for (StationView stationView : stationViews.values()) {
            stationView.update();
        }

        // Aktualizacja danych tras
        for (RouteView routeView : routeViews.values()) {
            routeView.update();
        }

        // ... wyciągów
        for (LiftView liftView : liftViews.values()) {
            liftView.update();
        }

        updateStatus();
    }

    private void updateStatus() {
        StringBuilder status = new StringBuilder("Status symulacji: ");

        int totalSkiers = 0;
        int totalOnLifts = 0;
        int totalOnRoutes = 0;

        // Zliczanie narciarzy na stacjach
        for (Stacja st : SkiResortSimulation.stacje) {
            totalSkiers += st.getLiczbaNarciarzy();
        }

        for (Wyciag w : SkiResortSimulation.wyciagi) {
            totalOnLifts += w.getNaWyciagu();
        }

        for (Trasa tr : SkiResortSimulation.trasy) {
            if (tr.duration > 0) {
                totalOnRoutes += tr.getNaTrasie();
            }
        }

        status.append("Narciarzy na stacjach: ").append(totalSkiers)
                .append(" | Na wyciągach: ").append(totalOnLifts).append(" | Zjeżdżających: ")
                .append(totalOnRoutes);

        statusEtykieta.setText(status.toString());
    }

    private class StationView {
        private final Stacja stacja; // Referencja do obiektu stacji
        private final double x, y; // Współrzędne stacji na ekranie
        private int narciarze; // Liczba narciarzy na stacji - w kółku w GUI

        public StationView(Stacja stacja, double x, double y) {
            this.stacja = stacja; // Rozróżnienie między polami a zmiennymi lokalnymi (można by zrobić np. name = someName, ale mniej czytelne)
            this.x = x;
            this.y = y;
            this.narciarze = stacja.getLiczbaNarciarzy();
        }

        public void update() {
            this.narciarze = stacja.getLiczbaNarciarzy();
        }

        public void draw(Graphics2D g2d) {
            // Rysowanie stacji jako koła
            g2d.setColor(new Color(0, 0, 128)); // Ciemno niebieski
            Ellipse2D.Double circle = new Ellipse2D.Double(
                    x - STATION_PROMIEN, y - STATION_PROMIEN, STATION_PROMIEN * 2, STATION_PROMIEN * 2); // Wyznaczenie krańców okręgu
            g2d.fill(circle); // Zamalowanie

            // Eytkieta - nazwa stacji
            g2d.setColor(Color.WHITE);
            g2d.setFont(new Font("Arial", Font.BOLD, 14));
            g2d.drawString(stacja.nazwa, (float) (x - STATION_PROMIEN + 5), (float) y); // Położenie nazwy stacji względem środka ekranu

            // Rysuj liczbę narciarzy
            g2d.drawString("" + narciarze, (float)(x - 5), (float)(y + 15));
        }
    }

    private class LiftView {
        private final Wyciag wyciag; // Referencja do obiektu wyciągu
        private final StationView start, end; // Stacja początkowa i końcowa
        private int naWyciagu; // Liczba narciarzy na wyciągu
        private boolean wMaintenance;

        public LiftView(Wyciag wyciag, StationView start, StationView end) {
            this.wyciag = wyciag;
            this.start = start;
            this.end = end;
            this.naWyciagu = wyciag.getNaWyciagu();
            this.wMaintenance = wyciag.getStatus() == WyciagStatus.MAINTENANCE;
        }

        public void update() {
            this.naWyciagu = wyciag.getNaWyciagu();
            this.wMaintenance = wyciag.getStatus() == WyciagStatus.MAINTENANCE;
        }

        public void draw(Graphics2D g2d) {
            // Kolor zmienia się, bo jest zależny od statusu - czerwony oznacza serwis
            if (wMaintenance) {
                g2d.setColor(Color.RED);
            } else {
                g2d.setColor(Color.BLACK);
            }

            // Stała grubość linii
            float lineWidth = 3.0f;
            g2d.setStroke(new BasicStroke(lineWidth));

            // Linia wyciągu - prosta od stacji dolnej do górnej
            Line2D.Double line = new Line2D.Double(start.x, start.y, end.x, end.y);
            g2d.draw(line);

            // Kierunek wyciągu - strzałka w górę
            drawArrow(g2d, start.x, start.y, end.x, end.y);

            // Etykieta - nazwa wyciągu
            double midX = (start.x + end.x) / 2;
            double midY = (start.y + end.y) / 2;

            // Rozmieszczenie etykiet
            if (wyciag.name.equals("A")) {
                g2d.setColor(Color.BLACK);
                g2d.drawString("Wyciąg " + wyciag.name + " (" + naWyciagu + ")", (float) (midX + 15), (float) (midY - 15));
            } else if (wyciag.name.equals("B")) {
                g2d.setColor(Color.BLACK);
                g2d.drawString("Wyciąg " + wyciag.name + " (" + naWyciagu + ")", (float) (midX - 40), (float) (midY - 50));
            } else {
                g2d.setColor(Color.BLACK);
                g2d.drawString("Wyciąg " + wyciag.name + " (" + naWyciagu + ")", (float) (midX - 25), (float) (midY + 40));
            }

            if (naWyciagu > 0) { // Interpretacja graficzna narciarzy
                drawSkiersOnLift(g2d);
            }
        }

        private void drawArrow(Graphics2D g2d, double x1, double y1, double x2, double y2) {
            double dx = x2 - x1;
            double dy = y2 - y1;
            double length = Math.sqrt(dx * dx + dy * dy);

            double dirX = dx / length;
            double dirY = dy / length;

            // Punkt dla strzałki w 2/3 długości wyciągu
            double arrowX = x1 + dirX * length * 2 / 3;
            double arrowY = y1 + dirY * length * 2 / 3;

            // Strzałka jako trójkąt
            double arrowSize = 10;
            double perpX = -dirY; // Wektor prostopadły do kierunku wyciągu
            double perpY = dirX;
            Polygon arrow = new Polygon();
            arrow.addPoint((int) (arrowX + dirX * arrowSize), (int) (arrowY + dirY * arrowSize));
            arrow.addPoint((int) (arrowX - dirX * arrowSize + perpX * arrowSize), (int) (arrowY - dirY * arrowSize + perpY * arrowSize));
            arrow.addPoint((int) (arrowX - dirX * arrowSize - perpX * arrowSize), (int) (arrowY - dirY * arrowSize - perpY * arrowSize));

            Color prevColor = g2d.getColor();
            g2d.fill(arrow);
            g2d.setColor(prevColor);
        }

        private void drawSkiersOnLift(Graphics2D g2d) {
            double dx = end.x - start.x;
            double dy = end.y - start.y;
            double length = Math.sqrt(dx * dx + dy * dy);

            // Rysowanie krzesełek z narciarzami
            for (int i = 0; i < naWyciagu; i++) {
                // Narciarze rozmieszczeni równomiernie na wyciągu (taki sam odstęp)
                double ratio = (i + 1.0) / (naWyciagu + 1.0);
                double x = start.x + dx * ratio;
                double y = start.y + dy * ratio;

                // Krzesełko to mały kwadrat
                g2d.setColor(Color.DARK_GRAY);
                g2d.fillRect((int) (x - 5), (int) (y - 5), 10, 10);

                // Narciarz to mały czerwony punkt
                g2d.setColor(Color.RED);
                g2d.fillOval((int) (x - 3), (int) (y - 3), 6, 6);
            }
        }
    }

    private class RouteView {
        private final Trasa trasa;
        private final StationView start, end;
        private int naTrasie;
        private final double pkontrolnyX, pkontrolnyY;


        public RouteView(Trasa trasa, StationView start, StationView end) {
            this.trasa = trasa;
            this.start = start;
            this.end = end;
            this.naTrasie = trasa.getNaTrasie();

            // Punkt kontrolny dla przesunięcia w bok od środka linii prostej, aby stworzyć krzywy zjazd (łuk)
            double midX = (start.x + end.x) / 2;
            double midY = (start.y + end.y) / 2;
            this.pkontrolnyX = midX + (start.y - end.y) / 4; // Przesunięcie w bok
            this.pkontrolnyY = midY + (end.x - start.x) / 4;
        }

        public void update() {
            this.naTrasie = trasa.getNaTrasie();
        }

        public void draw(Graphics2D g2d) {
            Font originalFont = g2d.getFont(); // Zapisywanie stanu dla późniejszego przywrócenia aplikacji
            Stroke originalStroke = g2d.getStroke();

            // Gruba zielona linia dla trasy
            g2d.setColor(new Color(0, 100, 0)); // Ciemnozielony
            g2d.setStroke(new BasicStroke(5.0f));

            // Z punktów kontrolnych dla krzywej rysujemy trasę
            QuadCurve2D.Double curve = new QuadCurve2D.Double(
                    start.x, start.y, pkontrolnyX, pkontrolnyY, end.x, end.y);
            g2d.draw(curve);

            // Strzałka, która wskazuje kierunek zjazdu
            drawArrow(g2d, start.x, start.y, end.x, end.y);

            // Etykieta trasy - nazwa i liczba narciarzy na trasie obecnie
            g2d.setColor(new Color(0, 100, 0));
            g2d.setFont(new Font("Arial", Font.BOLD, 12));
            double midX = (start.x + end.x) / 2;
            double midY = (start.y + end.y) / 2;

            // Ustawienie nazw trasy
            if (trasa.name.equals("szczyt-baza")) {
                g2d.drawString("Trasa " + trasa.name + " (" + naTrasie + ")", (float) (midX + 15), (float) (midY + 65));
            } else if (trasa.name.equals("polowa-baza")) {
                g2d.drawString("Trasa " + trasa.name + " (" + naTrasie + ")", (float) (midX - 130), (float) (midY + 70));
            } else {
                g2d.drawString("Trasa " + trasa.name + " (" + naTrasie + ")", (float) (midX - 160), (float) (midY - 40));
            }

            // Rysowanie narciarzy
            if (naTrasie > 0) {
                drawSkiersOnRoute(g2d);
            }

            g2d.setStroke(originalStroke);
            g2d.setFont(originalFont);
        }

        private void drawArrow(Graphics2D g2d, double x1, double y1, double x2, double y2) {
            double dx = x2 - x1;
            double dy = y2 - y1;
            double length = Math.sqrt(dx * dx + dy * dy);

            double dirX = dx / length;
            double dirY = dy / length;

            // Punkt dla strzałki w 2/3 długiści trasy (na krzywej)
            double t = 2.0 / 3.0;
            double u = 1 - t;
            double tt = t * t;
            double uu = u * u;
            double arrowX = uu * x1 + 2 * u * t * pkontrolnyX + tt * x2;
            double arrowY = uu * y1 + 2 * u * t * pkontrolnyY + tt * y2;
            double zmiennaX = 2 * (1 - t) * (pkontrolnyX - x1) + 2 * t * (x2 - pkontrolnyX);
            double zmiennaY = 2 * (1 - t) * (pkontrolnyY - y1) + 2 * t * (y2 - pkontrolnyY);
            double zmiennaLength = Math.sqrt(zmiennaX * zmiennaX + zmiennaY * zmiennaY);
            dirX = zmiennaX / zmiennaLength;
            dirY = zmiennaY / zmiennaLength;

            double arrowSize = 10;
            double perpX = -dirY;
            double perpY = dirX;

            Polygon arrow = new Polygon();
            arrow.addPoint((int) (arrowX + dirX * arrowSize), (int) (arrowY + dirY * arrowSize));
            arrow.addPoint((int) (arrowX - dirX * arrowSize + perpX * arrowSize), (int) (arrowY - dirY * arrowSize + perpY * arrowSize));
            arrow.addPoint((int) (arrowX - dirX * arrowSize - perpX * arrowSize), (int) (arrowY - dirY * arrowSize - perpY * arrowSize));

            g2d.setColor(new Color(0, 100, 0));
            g2d.fill(arrow);
        }

        private void drawSkiersOnRoute(Graphics2D g2d) {
            for(int i = 0; i < naTrasie; i++) {
                // Rozmieść narciarzy wzdłuż krzywej - zjazdu
                double t = (i + 1.0) / (naTrasie + 1.0);

                double u = 1 - t;
                double tt = t * t;
                double uu = u * u;
                double x = uu * start.x + 2 * u * t * pkontrolnyX + tt * end.x;
                double y = uu * start.y + 2 * u * t * pkontrolnyY + tt * end.y;

                // Narciarz jako czerwony punkt
                g2d.setColor(Color.RED);
                g2d.fillOval((int) (x - 4), (int) (y - 4), 8, 8);

                // Imitacja ruchu (wiatru) narciarza w postaci dwóch białych linii za nim
                g2d.setColor(Color.WHITE);
                g2d.setStroke(new BasicStroke(1.0f));

                // Liczenie wektora kierunku dla następnego punktu na krzywej
                double nextT = Math.min(1.0, t + 0.05);
                double nextU = 1 - nextT;
                double nextTT = nextT * nextT;
                double nextUU = nextU * nextU;
                double nextX = nextUU * start.x + 2 * nextU * nextT * pkontrolnyX + nextTT * end.x;
                double nextY = nextUU * start.y + 2 * nextU * nextT * pkontrolnyY + nextTT * end.y;

                // Wektor kierunkowy dla śladu narciarza
                double dx = nextX - x;
                double dy = nextY - y;
                double len = Math.sqrt(dx * dx + dy * dy);
                if (len > 0) {
                    dx /= len;
                    dy /= len;

                    // Wektor prostopadły do kierunku ruchu - szerokość nart
                    double perpX = -dy * 2;
                    double perpY = dx * 2;

                    // Narysuj dwa ślady nart - dwie równoległe linie
                    g2d.drawLine(
                            (int) (x + perpX), (int) (y + perpY),
                            (int) (x + perpX - dx * 10), (int) (y + perpY - dy * 10));
                    g2d.drawLine(
                            (int) (x - perpX), (int) (y - perpY),
                            (int) (x - perpX - dx * 10), (int) (y - perpY - dy * 10));
                }
            }
        }
    }

    private class StokWidok extends JPanel {
            protected void paintComponent(Graphics g) {
                super.paintComponent(g);
                Graphics2D g2d = (Graphics2D) g;

                // Antyaliasing dla gładszego rysowania
                g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g2d.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);

                // Tło
                g2d.setColor(new Color(173, 216, 230)); // Jasnoniebieski
                g2d.fillRect(0, 0, getWidth(), getHeight());

                // Najpierw trasy - zielone linie
                for (RouteView routeView : routeViews.values()) {
                    routeView.draw(g2d);
                }

                // Następnie wyciągi - czarne lub czerwone linie
                for (LiftView liftView : liftViews.values()) {
                    liftView.draw(g2d);
                }

                // Na końcu stacje - niebieskie koła
                for (StationView stationView : stationViews.values()) {
                    stationView.draw(g2d);
                }

                // Tytuł symmulacji
                Font originalFont = g2d.getFont();
                g2d.setFont(new Font("Arial", Font.BOLD, 16));
                g2d.setColor(Color.BLACK);
                g2d.drawString("Symulacja Stoku Narciarskiego", 10, 25);

                // Legenda
                drawLegend(g2d);

                g2d.setFont(originalFont);
            }

            private void drawLegend(Graphics2D g2d) {
                // Parametry legendy (ustawienie względem OX, OY, wielkość itd)
                int legendX = getWidth() - 210;
                int legendY = 45;
                int itemWys = 20;
                int widokWielkosc = 12;
                int textOffset = widokWielkosc + 5;

                // Tło legendy - biały prostokąt
                g2d.setColor(new Color(255, 255, 255, 200));
                g2d.fillRect(legendX - 10, 10, 200, 165);
                g2d.setColor(Color.BLACK);
                g2d.drawRect(legendX - 10, 10, 200, 165);

                // Nagłówek legendy
                g2d.setFont(new Font("Arial", Font.BOLD, 14));
                g2d.drawString("LEGENDA:", legendX, 30);
                g2d.setFont(new Font("Arial", Font.PLAIN, 12));

                // Objaśnienie stacji narciarskiej
                g2d.setColor(new Color(0, 0, 128)); // Ciemnoniebieski
                g2d.fillOval(legendX, legendY, widokWielkosc, widokWielkosc);
                g2d.setColor(Color.BLACK);
                g2d.drawString("Stacja narciarska", legendX + textOffset, legendY + widokWielkosc);

                // Objaśnienie wyciągu działającego
                legendY += itemWys;
                g2d.setColor(Color.BLACK);
                g2d.setStroke(new BasicStroke(2.0f));
                g2d.drawLine(legendX, legendY + widokWielkosc / 2, legendX + widokWielkosc, legendY + widokWielkosc / 2);
                g2d.drawString("Wyciąg (działający)", legendX + textOffset, legendY + widokWielkosc);

                // Objaśnienie wyciągu w serwisie
                legendY += itemWys;
                g2d.setColor(Color.RED);
                g2d.drawLine(legendX, legendY + widokWielkosc / 2, legendX + widokWielkosc, legendY + widokWielkosc / 2);
                g2d.drawString("Wyciąg (w serwisie)", legendX + textOffset, legendY + widokWielkosc);

                // Objaśnienie trasy zjazdowej
                legendY += itemWys;
                g2d.setColor(new Color(0, 100, 0)); // Ciemnozielony
                g2d.setStroke(new BasicStroke(3.0f));
                g2d.drawLine(legendX, legendY + widokWielkosc / 2, legendX + widokWielkosc, legendY + widokWielkosc / 2);
                g2d.drawString("Trasa zjazdowa", legendX + textOffset, legendY + widokWielkosc);

                // Objaśnienie narciarza na wyciągu
                legendY += itemWys;
                g2d.setColor(Color.DARK_GRAY);
                g2d.fillRect(legendX, legendY, widokWielkosc, widokWielkosc);
                g2d.setColor(Color.RED);
                g2d.fillOval(legendX + widokWielkosc / 4, legendY + widokWielkosc / 4, widokWielkosc / 2, widokWielkosc / 2);
                g2d.setColor(Color.BLACK);
                g2d.drawString("Narciarz na wyciągu", legendX + textOffset, legendY + widokWielkosc);

                // Objaśnienie narciarza na trasie
                legendY += itemWys;
                g2d.setColor(Color.RED);
                g2d.fillOval(legendX + widokWielkosc / 4, legendY + widokWielkosc / 4, widokWielkosc / 2, widokWielkosc / 2);
                g2d.setColor(Color.BLACK);
                g2d.drawString("Narciarz na trasie", legendX + textOffset, legendY + widokWielkosc);
            }
        }

        public static void main(String[] args) {
            try {
                UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
            } catch (Exception e) {
                e.printStackTrace();
            }

            // Uruchomianie ze Swing
            SwingUtilities.invokeLater(() -> {
                SkiResortGUI gui = new SkiResortGUI();
                gui.start();
            });
        }
}