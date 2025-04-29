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

public class SkiResortGUI extends JFrame {

    private static final int WIDTH = 800;
    private static final int HEIGHT = 600;
    private static final int STATION_RADIUS = 30;

    private final Map<String, StationView> stationViews = new HashMap<>();
    private final Map<String, LiftView> liftViews = new HashMap<>();
    private final Map<String, RouteView> routeViews = new HashMap<>();

    private final JLabel statusLabel;
    private final SkiCanvas skiCanvas;
    private Timer updateTimer;

    public SkiResortGUI() {
        super("Symulacja Stoku Narciarskiego");

        // Konfiguracja głównego okna
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setSize(WIDTH, HEIGHT);
        setLayout(new BorderLayout());

        // Panel statusu
        statusLabel = new JLabel("Symulacja stoku narciarskiego");
        add(statusLabel, BorderLayout.SOUTH);

        // Panel rysowania
        skiCanvas = new SkiCanvas();
        add(skiCanvas, BorderLayout.CENTER);

        // Obsługa zamykania
        addWindowListener(new WindowAdapter() {
            @Override
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

        // Zaimplementuj dokładną synchronizację z główną pętlą TUI
        // zamiast niezależnego timera, użyjmy tej samej pętli co w TUI
        updateTimer = null; // nie używamy niezależnego timera

        // Dodajemy metodę aktualizacji GUI do głównej pętli TUI w SkiResortSimulation
        SkiResortSimulation.setGuiUpdateCallback(() -> {
            SwingUtilities.invokeLater(() -> {
                updateView();
                skiCanvas.repaint();
            });
        });
    }

    private void initializeSimulation() {
        try {
            // Uruchomienie symulacji w osobnym wątku
            new Thread(() -> {
                try {
                    SkiResortSimulation.main(new String[]{});
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }).start();

            // Poczekaj chwilę na inicjalizację symulacji
            Thread.sleep(1000);

            // Inicjalizacja widoków stacji
            initializeStationViews();

            // Inicjalizacja widoków tras
            initializeRouteViews();

            // Inicjalizacja widoków wyciągów
            initializeLiftViews();

        } catch (Exception e) {
            e.printStackTrace();
            statusLabel.setText("Błąd inicjalizacji: " + e.getMessage());
        }
    }

    private void initializeStationViews() {
        // Pozycje stacji w układzie trójkątnym
        for (Stacja stacja : SkiResortSimulation.stacje) {
            int poziom = stacja.getPoziom();
            double x, y;

            switch (poziom) {
                case 0: // Baza - na dole, na środku
                    x = WIDTH / 2;
                    y = HEIGHT - 100;
                    break;
                case 1: // Stacja pośrednia - w środku wysokości, po lewej
                    x = WIDTH / 4;
                    y = HEIGHT / 2;
                    break;
                case 2: // Szczyt - na górze, na środku
                    x = WIDTH / 2;
                    y = 100;
                    break;
                default:
                    x = WIDTH / 2;
                    y = HEIGHT / 2;
            }

            stationViews.put(stacja.nazwa, new StationView(stacja, x, y));
        }
    }

    private void initializeRouteViews() {
        for (Trasa trasa : SkiResortSimulation.trasy) {
            if (trasa.duration > 0) { // tylko trasy zjazdowe
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

        // Aktualizacja danych wyciągów
        for (LiftView liftView : liftViews.values()) {
            liftView.update();
        }

        // Aktualizacja etykiety statusu
        updateStatus();
    }

    private void updateStatus() {
        // Aktualizacja etykiety statusu
        StringBuilder status = new StringBuilder("Status: ");

        int totalSkiers = 0;
        int totalOnLifts = 0;
        int totalOnRoutes = 0;

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
                .append(" | Na wyciągach: ").append(totalOnLifts)
                .append(" | Zjeżdżających: ").append(totalOnRoutes);

        statusLabel.setText(status.toString());
    }

    // Klasa widoku stacji
    private class StationView {
        private final Stacja stacja;
        private final double x, y;
        private int narciarze;

        public StationView(Stacja stacja, double x, double y) {
            this.stacja = stacja;
            this.x = x;
            this.y = y;
            this.narciarze = stacja.getLiczbaNarciarzy();
        }

        public void update() {
            this.narciarze = stacja.getLiczbaNarciarzy();
        }

        public void draw(Graphics2D g2d) {
            // Rysuj stację jako koło
            g2d.setColor(new Color(0, 0, 128)); // Ciemnoniebieski
            Ellipse2D.Double circle = new Ellipse2D.Double(
                    x - STATION_RADIUS, y - STATION_RADIUS,
                    STATION_RADIUS * 2, STATION_RADIUS * 2);
            g2d.fill(circle);

            // Rysuj etykietę stacji
            g2d.setColor(Color.WHITE);
            g2d.setFont(new Font("Arial", Font.BOLD, 14));
            g2d.drawString(stacja.nazwa, (float)(x - STATION_RADIUS + 5), (float)y);

            // Rysuj liczbę narciarzy
            g2d.drawString("" + narciarze, (float)(x - 5), (float)(y + 15));
        }
    }

    // Klasa widoku wyciągu
    private class LiftView {
        private final Wyciag wyciag;
        private final StationView start, end;
        private int naWyciagu;
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
            // Kolor zależny od statusu
            if (wMaintenance) {
                g2d.setColor(Color.RED);
            } else {
                g2d.setColor(Color.BLACK);
            }

            // Grubość linii zależna od pojemności
            float lineWidth = 1.0f + (float)wyciag.capacity / 5.0f;
            g2d.setStroke(new BasicStroke(lineWidth));

            // Linia wyciągu
            Line2D.Double line = new Line2D.Double(start.x, start.y, end.x, end.y);
            g2d.draw(line);

            // Kierunek wyciągu - strzałka w górę
            drawArrow(g2d, start.x, start.y, end.x, end.y);

            // Etykieta z nazwą wyciągu
            double midX = (start.x + end.x) / 2;
            double midY = (start.y + end.y) / 2;

            // Przesuń etykietę w zależności od nazwy wyciągu, aby uniknąć nakładania się
            float labelXOffset, labelYOffset;
            if (wyciag.name.equals("A")) {
                // Specjalne przesunięcie dla wyciągu A - dodaj słowo "wyciąg"
                g2d.setColor(Color.BLACK);
                g2d.drawString("Wyciąg " + wyciag.name + " (" + naWyciagu + ")", (float)(midX + 15), (float)(midY - 15));
            } else if (wyciag.name.equals("B")) {
                // Specjalne przesunięcie dla wyciągu B
                g2d.setColor(Color.BLACK);
                g2d.drawString("Wyciąg " + wyciag.name + " (" + naWyciagu + ")", (float)(midX - 40), (float)(midY - 50));
            } else {
                // Standardowe przesunięcie dla innych wyciągów
                g2d.setColor(Color.BLACK);
                g2d.drawString("Wyciąg " + wyciag.name + " (" + naWyciagu + ")", (float)(midX - 25), (float)(midY + 40));
            }

            // Narysuj "krzesełka" z narciarzami jeśli są
            if (naWyciagu > 0) {
                drawSkiersOnLift(g2d);
            }

            // Przywróć domyślną szerokość linii
            g2d.setStroke(new BasicStroke(1.0f));
        }

        private void drawArrow(Graphics2D g2d, double x1, double y1, double x2, double y2) {
            double dx = x2 - x1;
            double dy = y2 - y1;
            double length = Math.sqrt(dx * dx + dy * dy);

            // Normalizacja wektora kierunku
            double dirX = dx / length;
            double dirY = dy / length;

            // Punkt dla strzałki - w 2/3 długości wyciągu
            double arrowX = x1 + dirX * length * 2/3;
            double arrowY = y1 + dirY * length * 2/3;

            // Rysuj strzałkę
            double arrowSize = 10;
            double perpX = -dirY;
            double perpY = dirX;

            Polygon arrow = new Polygon();
            arrow.addPoint((int)(arrowX + dirX * arrowSize), (int)(arrowY + dirY * arrowSize));
            arrow.addPoint((int)(arrowX - dirX * arrowSize + perpX * arrowSize), (int)(arrowY - dirY * arrowSize + perpY * arrowSize));
            arrow.addPoint((int)(arrowX - dirX * arrowSize - perpX * arrowSize), (int)(arrowY - dirY * arrowSize - perpY * arrowSize));

            Color prevColor = g2d.getColor();
            g2d.fill(arrow);
            g2d.setColor(prevColor);
        }

        private void drawSkiersOnLift(Graphics2D g2d) {
            double dx = end.x - start.x;
            double dy = end.y - start.y;
            double length = Math.sqrt(dx * dx + dy * dy);

            // Rysuj "krzesełka" z narciarzami
            for (int i = 0; i < naWyciagu; i++) {
                // Rozmieść narciarzy równomiernie na wyciągu
                double ratio = (i + 1.0) / (naWyciagu + 1.0);
                double x = start.x + dx * ratio;
                double y = start.y + dy * ratio;

                // Rysuj krzesełko
                g2d.setColor(Color.DARK_GRAY);
                g2d.fillRect((int)(x - 5), (int)(y - 5), 10, 10);

                // Rysuj narciarza
                g2d.setColor(Color.RED);
                g2d.fillOval((int)(x - 3), (int)(y - 3), 6, 6);
            }
        }
    }

    // Klasa widoku trasy
    private class RouteView {
        private final Trasa trasa;
        private final StationView start, end;
        private int naTrasie;
        private final double controlX, controlY;

        public RouteView(Trasa trasa, StationView start, StationView end) {
            this.trasa = trasa;
            this.start = start;
            this.end = end;
            this.naTrasie = trasa.getNaTrasie();

            // Punkt kontrolny dla krzywej
            double midX = (start.x + end.x) / 2;
            double midY = (start.y + end.y) / 2;
            this.controlX = midX + (start.y - end.y) / 4;  // Przesuń punkt kontrolny w bok
            this.controlY = midY + (end.x - start.x) / 4;
        }

        public void update() {
            this.naTrasie = trasa.getNaTrasie();
        }

        public void draw(Graphics2D g2d) {
            // Zapisz stan grafiki
            Font originalFont = g2d.getFont();
            Stroke originalStroke = g2d.getStroke();

            // Ustaw styl linii
            g2d.setColor(new Color(0, 100, 0)); // Ciemnozielony
            g2d.setStroke(new BasicStroke(5.0f));

            // Rysuj zakrzywioną trasę
            QuadCurve2D.Double curve = new QuadCurve2D.Double(
                    start.x, start.y,
                    controlX, controlY,
                    end.x, end.y);
            g2d.draw(curve);

            // Rysuj strzałkę wskazującą kierunek zjazdu
            drawArrow(g2d, start.x, start.y, end.x, end.y);

            // Etykieta trasy - użyj pogrubionej czcionki
            g2d.setColor(new Color(0, 100, 0)); // Ciemnozielony
            g2d.setFont(new Font("Arial", Font.BOLD, 12));
            double midX = (start.x + end.x) / 2;
            double midY = (start.y + end.y) / 2;

            // Przesuń etykiety tras, aby nie nachodziły na siebie i na wyciągi - umieszczamy je dalej od obrazka
            if (trasa.name.equals("szczyt-baza")) {
                g2d.drawString("Trasa " + trasa.name + " (" + naTrasie + ")", (float)(midX + 15), (float)(midY + 65));
            } else if (trasa.name.equals("polowa-baza")) {
                g2d.drawString("Trasa " + trasa.name + " (" + naTrasie + ")", (float)(midX - 130), (float)(midY + 70));
            } else if (trasa.name.equals("szczyt-polowa")) {
                g2d.drawString("Trasa " + trasa.name + " (" + naTrasie + ")", (float)(midX - 160), (float)(midY - 40));
            } else {
                g2d.drawString("Trasa " + trasa.name + " (" + naTrasie + ")", (float)(midX + 45), (float)(midY + 45));
            }

            // Rysuj narciarzy na trasie
            if (naTrasie > 0) {
                drawSkiersOnRoute(g2d);
            }

            // Przywróć oryginalny stan
            g2d.setStroke(originalStroke);
            g2d.setFont(originalFont);
        }

        private void drawArrow(Graphics2D g2d, double x1, double y1, double x2, double y2) {
            double dx = x2 - x1;
            double dy = y2 - y1;
            double length = Math.sqrt(dx * dx + dy * dy);

            // Normalizacja wektora kierunku
            double dirX = dx / length;
            double dirY = dy / length;

            // Punkt dla strzałki - w 2/3 długości trasy (na krzywej)
            double t = 2.0/3.0;  // parametr krzywej
            double u = 1 - t;
            double tt = t * t;
            double uu = u * u;
            double arrowX = uu * x1 + 2 * u * t * controlX + tt * x2;
            double arrowY = uu * y1 + 2 * u * t * controlY + tt * y2;

            // Wektor styczny w danym punkcie krzywej
            double tangentX = 2 * (1-t) * (controlX-x1) + 2 * t * (x2-controlX);
            double tangentY = 2 * (1-t) * (controlY-y1) + 2 * t * (y2-controlY);

            // Normalizacja wektora stycznego
            double tangentLength = Math.sqrt(tangentX * tangentX + tangentY * tangentY);
            dirX = tangentX / tangentLength;
            dirY = tangentY / tangentLength;

            // Rysuj strzałkę
            double arrowSize = 10;
            double perpX = -dirY;
            double perpY = dirX;

            Polygon arrow = new Polygon();
            arrow.addPoint((int)(arrowX + dirX * arrowSize), (int)(arrowY + dirY * arrowSize));
            arrow.addPoint((int)(arrowX - dirX * arrowSize + perpX * arrowSize), (int)(arrowY - dirY * arrowSize + perpY * arrowSize));
            arrow.addPoint((int)(arrowX - dirX * arrowSize - perpX * arrowSize), (int)(arrowY - dirY * arrowSize - perpY * arrowSize));

            g2d.setColor(new Color(0, 100, 0)); // Ciemnozielony
            g2d.fill(arrow);
        }

        private void drawSkiersOnRoute(Graphics2D g2d) {
            for (int i = 0; i < naTrasie; i++) {
                // Rozmieść narciarzy wzdłuż krzywej Beziera
                double t = (i + 1.0) / (naTrasie + 1.0);

                // Wzór parametryczny dla krzywej kwadratowej
                double u = 1 - t;
                double tt = t * t;
                double uu = u * u;
                double x = uu * start.x + 2 * u * t * controlX + tt * end.x;
                double y = uu * start.y + 2 * u * t * controlY + tt * end.y;

                // Rysuj narciarza
                g2d.setColor(Color.RED);
                g2d.fillOval((int)(x - 4), (int)(y - 4), 8, 8);

                // Narysuj "ślad" narciarski
                g2d.setColor(Color.WHITE);
                g2d.setStroke(new BasicStroke(1.0f));

                // Oblicz wektor kierunku
                double nextT = Math.min(1.0, t + 0.05);
                double nextU = 1 - nextT;
                double nextTT = nextT * nextT;
                double nextUU = nextU * nextU;
                double nextX = nextUU * start.x + 2 * nextU * nextT * controlX + nextTT * end.x;
                double nextY = nextUU * start.y + 2 * nextU * nextT * controlY + nextTT * end.y;

                double dx = nextX - x;
                double dy = nextY - y;
                double len = Math.sqrt(dx * dx + dy * dy);
                if (len > 0) {
                    dx /= len;
                    dy /= len;

                    // Narysuj dwa ślady nart
                    double perpX = -dy * 2;
                    double perpY = dx * 2;

                    g2d.drawLine(
                            (int)(x + perpX), (int)(y + perpY),
                            (int)(x + perpX - dx * 10), (int)(y + perpY - dy * 10));
                    g2d.drawLine(
                            (int)(x - perpX), (int)(y - perpY),
                            (int)(x - perpX - dx * 10), (int)(y - perpY - dy * 10));
                }
            }
        }
    }

    // Klasa płótna do rysowania
    private class SkiCanvas extends JPanel {
        @Override
        protected void paintComponent(Graphics g) {
            super.paintComponent(g);
            Graphics2D g2d = (Graphics2D) g;

            // Włącz antyaliasing
            g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g2d.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);

            // Tło
            g2d.setColor(new Color(173, 216, 230)); // Jasnoniebieski
            g2d.fillRect(0, 0, getWidth(), getHeight());

            // Rysuj górę/śnieg
            drawMountainBackground(g2d);

            // Rysuj trasy
            for (RouteView routeView : routeViews.values()) {
                routeView.draw(g2d);
            }

            // Rysuj wyciągi
            for (LiftView liftView : liftViews.values()) {
                liftView.draw(g2d);
            }

            // Rysuj stacje
            for (StationView stationView : stationViews.values()) {
                stationView.draw(g2d);
            }

            // Dodaj tytuł
            Font originalFont = g2d.getFont();
            g2d.setFont(new Font("Arial", Font.BOLD, 16));
            g2d.setColor(Color.BLACK);
            g2d.drawString("Symulacja Stoku Narciarskiego", 10, 25);

            // Dodaj legendę
            drawLegend(g2d);

            g2d.setFont(originalFont);
        }

        private void drawLegend(Graphics2D g2d) {
            // Ustaw parametry legendy
            int legendX = getWidth() - 210;
            int legendY = 45;
            int itemHeight = 20;
            int boxSize = 12;
            int textOffset = boxSize + 5;

            // Tło legendy - półprzezroczysty biały prostokąt
            g2d.setColor(new Color(255, 255, 255, 200));
            g2d.fillRect(legendX - 10, 10, 200, 165);
            g2d.setColor(Color.BLACK);
            g2d.drawRect(legendX - 10, 10, 200, 165);

            // Nagłówek legendy
            g2d.setFont(new Font("Arial", Font.BOLD, 14));
            g2d.drawString("LEGENDA:", legendX, 30);
            g2d.setFont(new Font("Arial", Font.PLAIN, 12));

            // Stacje
            g2d.setColor(new Color(0, 0, 128)); // Ciemnoniebieski
            g2d.fillOval(legendX, legendY, boxSize, boxSize);
            g2d.setColor(Color.BLACK);
            g2d.drawString("Stacja narciarska", legendX + textOffset, legendY + boxSize);

            // Wyciągi
            legendY += itemHeight;
            g2d.setColor(Color.BLACK);
            g2d.setStroke(new BasicStroke(2.0f));
            g2d.drawLine(legendX, legendY + boxSize/2, legendX + boxSize, legendY + boxSize/2);
            g2d.drawString("Wyciąg (działający)", legendX + textOffset, legendY + boxSize);

            // Wyciągi w serwisie
            legendY += itemHeight;
            g2d.setColor(Color.RED);
            g2d.drawLine(legendX, legendY + boxSize/2, legendX + boxSize, legendY + boxSize/2);
            g2d.drawString("Wyciąg (w serwisie)", legendX + textOffset, legendY + boxSize);

            // Trasy zjazdowe
            legendY += itemHeight;
            g2d.setColor(new Color(0, 100, 0)); // Ciemnozielony
            g2d.setStroke(new BasicStroke(3.0f));
            g2d.drawLine(legendX, legendY + boxSize/2, legendX + boxSize, legendY + boxSize/2);
            g2d.drawString("Trasa zjazdowa", legendX + textOffset, legendY + boxSize);

            // Narciarze na wyciągu
            legendY += itemHeight;
            g2d.setColor(Color.DARK_GRAY);
            g2d.fillRect(legendX, legendY, boxSize, boxSize);
            g2d.setColor(Color.RED);
            g2d.fillOval(legendX + boxSize/4, legendY + boxSize/4, boxSize/2, boxSize/2);
            g2d.setColor(Color.BLACK);
            g2d.drawString("Narciarz na wyciągu", legendX + textOffset, legendY + boxSize);

            // Narciarze na trasie
            legendY += itemHeight;
            g2d.setColor(Color.RED);
            g2d.fillOval(legendX + boxSize/4, legendY + boxSize/4, boxSize/2, boxSize/2);
            g2d.setColor(Color.BLACK);
            g2d.drawString("Narciarz na trasie", legendX + textOffset, legendY + boxSize);

            // Przywróć normalną grubość linii
            g2d.setStroke(new BasicStroke(1.0f));
        }

        private void drawMountainBackground(Graphics2D g2d) {
            // Rysuj tło gór
            g2d.setColor(Color.WHITE);
            Polygon mountain = new Polygon();
            mountain.addPoint(0, HEIGHT);
            mountain.addPoint(WIDTH/2, 50);
            mountain.addPoint(WIDTH, HEIGHT);
            g2d.fill(mountain);

            // Dodaj "śnieg" na górze
            g2d.setColor(new Color(240, 240, 255)); // Bardzo jasny niebieski
            int x[] = {WIDTH/4, WIDTH/2, 3*WIDTH/4};
            int y[] = {3*HEIGHT/4, HEIGHT/4, 3*HEIGHT/4};
            g2d.fillPolygon(x, y, 3);
        }
    }

    public static void main(String[] args) {
        try {
            // Ustaw wygląd natywny dla systemu
            UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
        } catch (Exception e) {
            e.printStackTrace();
        }

        // Uruchom aplikację w bezpieczny sposób dla Swing
        SwingUtilities.invokeLater(() -> {
            SkiResortGUI gui = new SkiResortGUI();
            gui.start();
        });
    }
}