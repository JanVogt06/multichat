package server;

import javax.swing.*;
import javax.swing.border.TitledBorder;
import java.awt.*;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;

/**
 * Grafische Benutzeroberfläche für den Chat-Server.
 * Zeigt Server-Log, angemeldete Nutzer und Räume an.
 * Ermöglicht das Starten/Stoppen des Servers und Verwalten von Nutzern.
 */
public class ServerGUI extends JFrame {

    // ===== GUI-Komponenten =====

    // Textbereich für Server-Log (zeigt alle Aktivitäten)
    private JTextArea logTextArea;

    // Liste der angemeldeten Nutzer
    private JList<String> userList;
    private DefaultListModel<String> userListModel;

    // Liste der Räume
    private JList<String> roomList;
    private DefaultListModel<String> roomListModel;

    // Buttons für Server-Steuerung
    private JButton startButton;
    private JButton stopButton;
    private JButton removeUserButton;

    // ===== Server-Referenz =====
    private Server server;
    private Thread serverThread;


    /**
     * Konstruktor - Erstellt und initialisiert die GUI.
     */
    public ServerGUI() {
        // Fenster-Grundeinstellungen
        setTitle("Chat-Server");
        setDefaultCloseOperation(JFrame.DO_NOTHING_ON_CLOSE);
        setSize(800, 600);
        setLocationRelativeTo(null); // Zentriert auf Bildschirm

        // Fenster-Schließen abfangen (Server sauber beenden)
        addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent e) {
                handleWindowClosing();
            }
        });

        // GUI-Komponenten erstellen und anordnen
        initComponents();

        // Anfangszustand: Server nicht gestartet
        updateButtonStates(false);
    }


    /**
     * Initialisiert alle GUI-Komponenten und ordnet sie im Fenster an.
     */
    private void initComponents() {
        // Hauptlayout: BorderLayout für flexible Anordnung
        setLayout(new BorderLayout(10, 10));

        // ===== LINKE SEITE: Server-Log =====
        JPanel logPanel = createLogPanel();

        // ===== RECHTE SEITE: Nutzer- und Raumlisten =====
        JPanel rightPanel = createRightPanel();

        // ===== UNTEN: Buttons =====
        JPanel buttonPanel = createButtonPanel();

        // Komponenten zum Hauptfenster hinzufügen
        add(logPanel, BorderLayout.CENTER);
        add(rightPanel, BorderLayout.EAST);
        add(buttonPanel, BorderLayout.SOUTH);

        // Rand um das gesamte Fenster
        ((JPanel) getContentPane()).setBorder(
                BorderFactory.createEmptyBorder(10, 10, 10, 10)
        );
    }


    /**
     * Erstellt das Panel mit dem Server-Log.
     *
     * @return JPanel mit dem Log-Textbereich
     */
    private JPanel createLogPanel() {
        JPanel panel = new JPanel(new BorderLayout());

        // Textbereich für Log erstellen
        logTextArea = new JTextArea();
        logTextArea.setEditable(false); // Nur lesen, nicht bearbeiten
        logTextArea.setFont(new Font("Monospaced", Font.PLAIN, 12));
        logTextArea.setLineWrap(true);
        logTextArea.setWrapStyleWord(true);

        // Scrollbar hinzufügen
        JScrollPane scrollPane = new JScrollPane(logTextArea);
        scrollPane.setVerticalScrollBarPolicy(JScrollPane.VERTICAL_SCROLLBAR_ALWAYS);

        panel.add(scrollPane, BorderLayout.CENTER);

        return panel;
    }


    /**
     * Erstellt das rechte Panel mit Nutzer- und Raumlisten.
     *
     * @return JPanel mit beiden Listen
     */
    private JPanel createRightPanel() {
        JPanel panel = new JPanel();
        panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
        panel.setPreferredSize(new Dimension(200, 0));

        // ===== Nutzerliste =====
        userListModel = new DefaultListModel<>();
        userList = new JList<>(userListModel);
        userList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);

        JScrollPane userScrollPane = new JScrollPane(userList);
        userScrollPane.setBorder(new TitledBorder("Angemeldete Nutzer"));
        userScrollPane.setPreferredSize(new Dimension(200, 200));

        // ===== Raumliste =====
        roomListModel = new DefaultListModel<>();
        roomList = new JList<>(roomListModel);
        roomList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);

        JScrollPane roomScrollPane = new JScrollPane(roomList);
        roomScrollPane.setBorder(new TitledBorder("Räume"));
        roomScrollPane.setPreferredSize(new Dimension(200, 200));

        // Zur Panel hinzufügen
        panel.add(userScrollPane);
        panel.add(Box.createVerticalStrut(10)); // Abstand
        panel.add(roomScrollPane);

        return panel;
    }


    /**
     * Erstellt das untere Panel mit den Steuerungs-Buttons.
     *
     * @return JPanel mit Buttons
     */
    private JPanel createButtonPanel() {
        JPanel panel = new JPanel(new FlowLayout(FlowLayout.CENTER, 20, 10));

        // Buttons erstellen
        startButton = new JButton("Server starten");
        stopButton = new JButton("Server beenden");
        removeUserButton = new JButton("Nutzer entfernen");

        // Action-Listener für Buttons
        startButton.addActionListener(e -> handleStartServer());
        stopButton.addActionListener(e -> handleStopServer());
        removeUserButton.addActionListener(e -> handleRemoveUser());

        // Buttons zum Panel hinzufügen
        panel.add(startButton);
        panel.add(stopButton);
        panel.add(removeUserButton);

        return panel;
    }


    /**
     * Aktualisiert den Zustand der Buttons basierend auf Server-Status.
     *
     * @param serverRunning true wenn Server läuft, sonst false
     */
    private void updateButtonStates(boolean serverRunning) {
        startButton.setEnabled(!serverRunning);
        stopButton.setEnabled(serverRunning);
        removeUserButton.setEnabled(serverRunning);
    }


    // ===== EVENT-HANDLER =====

    /**
     * Wird aufgerufen wenn "Server starten" geklickt wird.
     * Startet den Server in einem separaten Thread.
     */
    private void handleStartServer() {
        // Server-Instanz erstellen mit GUI-Referenz
        server = new Server(this);

        // Server in separatem Thread starten (damit GUI nicht blockiert)
        serverThread = new Thread(() -> {
            server.start();
        }, "Server-Thread");

        // Thread starten
        serverThread.start();

        // Buttons aktualisieren
        updateButtonStates(true);
    }


    /**
     * Wird aufgerufen wenn "Server beenden" geklickt wird.
     */
    private void handleStopServer() {
        if (server != null) {
            // Server stoppen
            server.stop();
            server = null;
        }

        // Buttons aktualisieren
        updateButtonStates(false);
    }


    /**
     * Wird aufgerufen wenn "Nutzer entfernen" geklickt wird.
     */
    private void handleRemoveUser() {
        String selectedUser = userList.getSelectedValue();

        if (selectedUser == null) {
            JOptionPane.showMessageDialog(this,
                    "Bitte wähle einen Nutzer aus der Liste aus.",
                    "Kein Nutzer ausgewählt",
                    JOptionPane.WARNING_MESSAGE);
            return;
        }

        // Bestätigung anfordern
        int choice = JOptionPane.showConfirmDialog(this,
                "Nutzer '" + selectedUser + "' wirklich entfernen?",
                "Nutzer entfernen",
                JOptionPane.YES_NO_OPTION);

        if (choice == JOptionPane.YES_OPTION) {
            if (server != null) {
                boolean removed = server.removeClientByUsername(selectedUser);
                if (removed) {
                    log("Nutzer entfernt: " + selectedUser);
                } else {
                    log("Fehler: Nutzer '" + selectedUser + "' konnte nicht entfernt werden");
                }
            }
        }
    }


    /**
     * Wird aufgerufen wenn das Fenster geschlossen wird.
     */
    private void handleWindowClosing() {
        int choice = JOptionPane.showConfirmDialog(this,
                "Server wirklich beenden?",
                "Beenden",
                JOptionPane.YES_NO_OPTION);

        if (choice == JOptionPane.YES_OPTION) {
            // Server sauber beenden
            if (server != null) {
                server.stop();
            }
            dispose();
            System.exit(0);
        }
    }


    // ===== ÖFFENTLICHE METHODEN ZUR AKTUALISIERUNG DER GUI =====

    /**
     * Fügt eine Nachricht zum Server-Log hinzu.
     * Thread-sicher durch SwingUtilities.invokeLater.
     *
     * @param message Die anzuzeigende Nachricht
     */
    public void log(String message) {
        SwingUtilities.invokeLater(() -> {
            logTextArea.append(message + "\n");
            // Automatisch nach unten scrollen
            logTextArea.setCaretPosition(logTextArea.getDocument().getLength());
        });
    }


    /**
     * Fügt einen Nutzer zur Nutzerliste hinzu.
     *
     * @param username Der Nutzername
     */
    public void addUser(String username) {
        SwingUtilities.invokeLater(() -> {
            if (!userListModel.contains(username)) {
                userListModel.addElement(username);
            }
        });
    }


    /**
     * Entfernt einen Nutzer aus der Nutzerliste.
     *
     * @param username Der Nutzername
     */
    public void removeUser(String username) {
        SwingUtilities.invokeLater(() -> {
            userListModel.removeElement(username);
        });
    }


    /**
     * Fügt einen Raum zur Raumliste hinzu.
     *
     * @param roomName Der Raumname
     */
    public void addRoom(String roomName) {
        SwingUtilities.invokeLater(() -> {
            if (!roomListModel.contains(roomName)) {
                roomListModel.addElement(roomName);
            }
        });
    }


    /**
     * Entfernt einen Raum aus der Raumliste.
     *
     * @param roomName Der Raumname
     */
    public void removeRoom(String roomName) {
        SwingUtilities.invokeLater(() -> {
            roomListModel.removeElement(roomName);
        });
    }


    /**
     * Leert alle Listen (z.B. beim Server-Neustart).
     */
    public void clearAll() {
        SwingUtilities.invokeLater(() -> {
            userListModel.clear();
            roomListModel.clear();
        });
    }


    /**
     * Main-Methode - Startet die Server-GUI.
     */
    public static void main(String[] args) {
        // GUI im Event-Dispatch-Thread starten (Swing-Konvention)
        SwingUtilities.invokeLater(() -> {
            ServerGUI gui = new ServerGUI();
            gui.setVisible(true);
        });
    }
}