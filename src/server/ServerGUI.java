package server;

import javax.swing.*;
import javax.swing.border.TitledBorder;
import java.awt.*;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Grafische Benutzeroberfläche für den Chat-Server.
 * Zeigt Server-Log, angemeldete Nutzer (mit Raum) und Räume an.
 * Ermöglicht das Starten/Stoppen des Servers und Verwalten von Nutzern.
 */
public class ServerGUI extends JFrame {

    // ===== GUI-Komponenten =====

    // Textbereich für Server-Log (zeigt alle Aktivitäten)
    private JTextArea logTextArea;

    // Liste der angemeldeten Nutzer (zeigt "username [raum]")
    private JList<String> userList;
    private DefaultListModel<String> userListModel;

    // Liste der Räume
    private JList<String> roomList;
    private DefaultListModel<String> roomListModel;

    // Buttons für Server-Steuerung
    private JButton startButton;
    private JButton stopButton;

    // Buttons für Nutzerverwaltung
    private JButton kickUserButton;
    private JButton warnUserButton;
    private JButton banUserButton;
    private JButton showUsersButton;

    // ===== Server-Referenz =====
    private Server server;
    private Thread serverThread;

    // ===== Nutzer-Raum-Zuordnung =====
    // Speichert welcher Nutzer in welchem Raum ist
    private final Map<String, String> userRoomMap = new HashMap<>();


    /**
     * Konstruktor - Erstellt und initialisiert die GUI.
     */
    public ServerGUI() {
        // Fenster-Grundeinstellungen
        setTitle("Chat-Server");
        setDefaultCloseOperation(JFrame.DO_NOTHING_ON_CLOSE);
        setSize(900, 650);
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
        panel.setBorder(new TitledBorder("Server-Log"));

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
        panel.setPreferredSize(new Dimension(220, 0));

        // ===== Nutzerliste =====
        userListModel = new DefaultListModel<>();
        userList = new JList<>(userListModel);
        userList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        userList.setFont(new Font("SansSerif", Font.PLAIN, 12));

        JScrollPane userScrollPane = new JScrollPane(userList);
        userScrollPane.setBorder(new TitledBorder("Angemeldete Nutzer"));
        userScrollPane.setPreferredSize(new Dimension(200, 180));
        userScrollPane.setMaximumSize(new Dimension(220, 200));

        // ===== Nutzer-Buttons =====
        JPanel userButtonPanel = createUserButtonPanel();

        // ===== Raumliste =====
        roomListModel = new DefaultListModel<>();
        roomList = new JList<>(roomListModel);
        roomList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);

        JScrollPane roomScrollPane = new JScrollPane(roomList);
        roomScrollPane.setBorder(new TitledBorder("Räume"));
        roomScrollPane.setPreferredSize(new Dimension(200, 150));
        roomScrollPane.setMaximumSize(new Dimension(220, 180));

        // Zur Panel hinzufügen
        panel.add(userScrollPane);
        panel.add(Box.createVerticalStrut(5));
        panel.add(userButtonPanel);
        panel.add(Box.createVerticalStrut(10));
        panel.add(roomScrollPane);

        return panel;
    }


    /**
     * Erstellt das Panel mit Buttons für die Nutzerverwaltung.
     *
     * @return JPanel mit Nutzer-Buttons
     */
    private JPanel createUserButtonPanel() {
        JPanel panel = new JPanel();
        panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
        panel.setMaximumSize(new Dimension(220, 120));

        // Buttons erstellen
        kickUserButton = new JButton("Nutzer kicken");
        warnUserButton = new JButton("Nutzer verwarnen");
        banUserButton = new JButton("Nutzer bannen");

        // Buttons gleich breit machen
        Dimension buttonSize = new Dimension(200, 25);
        kickUserButton.setMaximumSize(buttonSize);
        warnUserButton.setMaximumSize(buttonSize);
        banUserButton.setMaximumSize(buttonSize);

        kickUserButton.setAlignmentX(Component.CENTER_ALIGNMENT);
        warnUserButton.setAlignmentX(Component.CENTER_ALIGNMENT);
        banUserButton.setAlignmentX(Component.CENTER_ALIGNMENT);

        // Action-Listener
        kickUserButton.addActionListener(e -> handleKickUser());
        warnUserButton.addActionListener(e -> handleWarnUser());
        banUserButton.addActionListener(e -> handleBanUser());

        // Zum Panel hinzufügen
        panel.add(kickUserButton);
        panel.add(Box.createVerticalStrut(3));
        panel.add(warnUserButton);
        panel.add(Box.createVerticalStrut(3));
        panel.add(banUserButton);

        return panel;
    }


    /**
     * Erstellt das untere Panel mit den Server-Steuerungs-Buttons.
     *
     * @return JPanel mit Buttons
     */
    private JPanel createButtonPanel() {
        JPanel panel = new JPanel(new FlowLayout(FlowLayout.CENTER, 15, 10));

        // Buttons erstellen
        startButton = new JButton("Server starten");
        stopButton = new JButton("Server beenden");
        showUsersButton = new JButton("Registrierte Nutzer");

        // Action-Listener für Buttons
        startButton.addActionListener(e -> handleStartServer());
        stopButton.addActionListener(e -> handleStopServer());
        showUsersButton.addActionListener(e -> handleShowRegisteredUsers());

        // Buttons zum Panel hinzufügen
        panel.add(startButton);
        panel.add(stopButton);
        panel.add(showUsersButton);

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
        kickUserButton.setEnabled(serverRunning);
        warnUserButton.setEnabled(serverRunning);
        banUserButton.setEnabled(serverRunning);
        showUsersButton.setEnabled(serverRunning);
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

        // Nutzer-Raum-Map leeren
        userRoomMap.clear();

        // Buttons aktualisieren
        updateButtonStates(false);
    }


    /**
     * Wird aufgerufen wenn "Nutzer kicken" geklickt wird.
     * Entfernt den Nutzer vom Server (er kann sich wieder einloggen).
     */
    private void handleKickUser() {
        String selectedUser = getSelectedUsername();

        if (selectedUser == null) {
            JOptionPane.showMessageDialog(this,
                    "Bitte wähle einen Nutzer aus der Liste aus.",
                    "Kein Nutzer ausgewählt",
                    JOptionPane.WARNING_MESSAGE);
            return;
        }

        // Bestätigung anfordern
        int choice = JOptionPane.showConfirmDialog(this,
                "Nutzer '" + selectedUser + "' wirklich kicken?\n\n" +
                        "(Der Nutzer kann sich erneut einloggen)",
                "Nutzer kicken",
                JOptionPane.YES_NO_OPTION);

        if (choice == JOptionPane.YES_OPTION) {
            if (server != null) {
                boolean removed = server.removeClientByUsername(selectedUser);
                if (removed) {
                    log("Nutzer gekickt: " + selectedUser);
                } else {
                    log("Fehler: Nutzer '" + selectedUser + "' konnte nicht gekickt werden");
                }
            }
        }
    }


    /**
     * Wird aufgerufen wenn "Nutzer verwarnen" geklickt wird.
     * Sendet eine Warnnachricht an den ausgewählten Nutzer.
     */
    private void handleWarnUser() {
        String selectedUser = getSelectedUsername();

        if (selectedUser == null) {
            JOptionPane.showMessageDialog(this,
                    "Bitte wähle einen Nutzer aus der Liste aus.",
                    "Kein Nutzer ausgewählt",
                    JOptionPane.WARNING_MESSAGE);
            return;
        }

        // Nachricht eingeben
        String message = JOptionPane.showInputDialog(this,
                "Warnungsnachricht für '" + selectedUser + "':",
                "Nutzer verwarnen",
                JOptionPane.WARNING_MESSAGE);

        if (message != null && !message.trim().isEmpty()) {
            if (server != null) {
                boolean sent = server.warnUser(selectedUser, message.trim());
                if (sent) {
                    log("Warnung an " + selectedUser + " gesendet: " + message);
                } else {
                    log("Fehler: Warnung konnte nicht gesendet werden");
                }
            }
        }
    }


    /**
     * Wird aufgerufen wenn "Nutzer bannen" geklickt wird.
     * Sperrt den Nutzer dauerhaft (kann sich nicht mehr einloggen).
     */
    private void handleBanUser() {
        String selectedUser = getSelectedUsername();

        if (selectedUser == null) {
            JOptionPane.showMessageDialog(this,
                    "Bitte wähle einen Nutzer aus der Liste aus.",
                    "Kein Nutzer ausgewählt",
                    JOptionPane.WARNING_MESSAGE);
            return;
        }

        // Bestätigung mit deutlicher Warnung
        int choice = JOptionPane.showConfirmDialog(this,
                "Nutzer '" + selectedUser + "' wirklich PERMANENT bannen?\n\n" +
                        "Der Nutzer wird sofort getrennt und kann sich\n" +
                        "nicht mehr einloggen!",
                "Nutzer bannen",
                JOptionPane.YES_NO_OPTION,
                JOptionPane.WARNING_MESSAGE);

        if (choice == JOptionPane.YES_OPTION) {
            if (server != null) {
                boolean banned = server.banUser(selectedUser);
                if (banned) {
                    log("Nutzer permanent gebannt: " + selectedUser);
                } else {
                    log("Fehler: Nutzer '" + selectedUser + "' konnte nicht gebannt werden");
                }
            }
        }
    }


    /**
     * Wird aufgerufen wenn "Registrierte Nutzer" geklickt wird.
     * Zeigt alle in der Datenbank registrierten Benutzer an.
     */
    private void handleShowRegisteredUsers() {
        if (server == null) {
            return;
        }

        // Alle Benutzer aus der Datenbank holen
        List<String> users = server.getRegisteredUsers();

        if (users.isEmpty()) {
            JOptionPane.showMessageDialog(this,
                    "Keine registrierten Benutzer gefunden.",
                    "Registrierte Benutzer",
                    JOptionPane.INFORMATION_MESSAGE);
            return;
        }

        // Dialog mit Liste der Benutzer erstellen
        JDialog dialog = new JDialog(this, "Registrierte Benutzer", true);
        dialog.setSize(400, 300);
        dialog.setLocationRelativeTo(this);

        // Liste erstellen
        DefaultListModel<String> listModel = new DefaultListModel<>();
        for (String user : users) {
            listModel.addElement(user);
        }
        JList<String> list = new JList<>(listModel);
        list.setFont(new Font("Monospaced", Font.PLAIN, 12));

        JScrollPane scrollPane = new JScrollPane(list);

        // Entbannen-Button
        JButton unbanButton = new JButton("Ausgewählten Nutzer entbannen");
        unbanButton.addActionListener(e -> {
            String selected = list.getSelectedValue();
            if (selected != null && selected.contains("[GEBANNT]")) {
                // Username extrahieren (vor dem ersten Leerzeichen)
                String username = selected.split(" ")[0];
                if (server.unbanUser(username)) {
                    log("Nutzer entbannt: " + username);
                    // Liste aktualisieren
                    listModel.clear();
                    for (String u : server.getRegisteredUsers()) {
                        listModel.addElement(u);
                    }
                }
            } else {
                JOptionPane.showMessageDialog(dialog,
                        "Bitte einen gebannten Nutzer auswählen.",
                        "Hinweis",
                        JOptionPane.INFORMATION_MESSAGE);
            }
        });

        // Schließen-Button
        JButton closeButton = new JButton("Schließen");
        closeButton.addActionListener(e -> dialog.dispose());

        // Button-Panel
        JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.CENTER, 10, 5));
        buttonPanel.add(unbanButton);
        buttonPanel.add(closeButton);

        // Layout
        dialog.setLayout(new BorderLayout(5, 5));
        dialog.add(new JLabel("  " + users.size() + " registrierte Benutzer:"), BorderLayout.NORTH);
        dialog.add(scrollPane, BorderLayout.CENTER);
        dialog.add(buttonPanel, BorderLayout.SOUTH);

        dialog.setVisible(true);
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


    // ===== HILFSMETHODEN =====

    /**
     * Extrahiert den Benutzernamen aus der Auswahl in der Nutzerliste.
     * Die Liste zeigt "username [raum]", wir brauchen nur den Username.
     *
     * @return Der Benutzername oder null wenn nichts ausgewählt
     */
    private String getSelectedUsername() {
        String selected = userList.getSelectedValue();
        if (selected == null) {
            return null;
        }

        // Format: "username [raum]" oder "username"
        // Wir nehmen alles vor dem ersten Leerzeichen oder '['
        int spaceIndex = selected.indexOf(' ');
        int bracketIndex = selected.indexOf('[');

        if (bracketIndex > 0) {
            return selected.substring(0, bracketIndex).trim();
        } else if (spaceIndex > 0) {
            return selected.substring(0, spaceIndex);
        } else {
            return selected;
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
            // Prüfen ob User schon in der Liste ist
            if (userRoomMap.containsKey(username)) {
                return;
            }

            userRoomMap.put(username, null); // Noch in keinem Raum
            updateUserListDisplay();
        });
    }


    /**
     * Entfernt einen Nutzer aus der Nutzerliste.
     *
     * @param username Der Nutzername
     */
    public void removeUser(String username) {
        SwingUtilities.invokeLater(() -> {
            userRoomMap.remove(username);
            updateUserListDisplay();
        });
    }


    /**
     * Aktualisiert den Raum eines Nutzers.
     *
     * @param username Der Nutzername
     * @param roomName Der Raumname (null wenn in keinem Raum)
     */
    public void updateUserRoom(String username, String roomName) {
        SwingUtilities.invokeLater(() -> {
            if (userRoomMap.containsKey(username)) {
                userRoomMap.put(username, roomName);
                updateUserListDisplay();
            }
        });
    }


    /**
     * Aktualisiert die Anzeige der Nutzerliste.
     * Zeigt jeden Nutzer im Format "username [raum]" an.
     */
    private void updateUserListDisplay() {
        userListModel.clear();
        for (Map.Entry<String, String> entry : userRoomMap.entrySet()) {
            String username = entry.getKey();
            String room = entry.getValue();

            if (room != null && !room.isEmpty()) {
                userListModel.addElement(username + " [" + room + "]");
            } else {
                userListModel.addElement(username);
            }
        }
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
            userRoomMap.clear();
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