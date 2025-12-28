package client;

import javax.swing.*;
import javax.swing.border.TitledBorder;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.KeyEvent;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.net.Socket;

/**
 * Grafische Benutzeroberfläche für den Chat-Client.
 * Zeigt Chat-Nachrichten, Räume und Nutzer im aktuellen Raum an.
 * Ermöglicht das Senden von Nachrichten und die Raumverwaltung.
 */
public class ClientGUI extends JFrame {

    // ===== Verbindungskonstanten =====
    private static final String SERVER_HOST = "localhost";
    private static final int SERVER_PORT = 3143;

    // ===== GUI-Komponenten =====

    // Anzeige des aktuellen Raums
    private JLabel currentRoomLabel;

    // Chat-Bereich
    private JTextArea chatTextArea;
    private JTextField messageField;
    private JButton sendButton;

    // Raumliste
    private JList<String> roomList;
    private DefaultListModel<String> roomListModel;

    // Nutzerliste (im aktuellen Raum)
    private JList<String> userList;
    private DefaultListModel<String> userListModel;

    // Raum-Buttons
    private JButton createRoomButton;
    private JButton joinRoomButton;
    private JButton leaveRoomButton;

    // Datei-Buttons (für Meilenstein 3 vorbereitet)
    private JButton uploadFileButton;
    private JButton showFilesButton;
    private JButton downloadFileButton;

    // ===== Netzwerk-Komponenten =====
    private Socket socket;
    private DataInputStream input;
    private DataOutputStream output;
    private Thread listenerThread;
    private volatile boolean connected = false;

    // ===== Benutzer-Daten =====
    private String username;
    private String currentRoom;


    /**
     * Konstruktor - Erstellt die GUI.
     * Wird erst nach erfolgreichem Login sichtbar gemacht.
     */
    public ClientGUI() {
        // Fenster-Grundeinstellungen
        setTitle("Chat-Client");
        setDefaultCloseOperation(JFrame.DO_NOTHING_ON_CLOSE);
        setSize(900, 600);
        setLocationRelativeTo(null);

        // Fenster-Schließen abfangen
        addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent e) {
                handleWindowClosing();
            }
        });

        // GUI-Komponenten erstellen
        initComponents();

        // Anfangszustand: Nicht verbunden
        setConnectedState(false);
    }


    /**
     * Initialisiert alle GUI-Komponenten.
     */
    private void initComponents() {
        setLayout(new BorderLayout(10, 10));

        // ===== OBEN: Aktueller Raum =====
        JPanel topPanel = createTopPanel();

        // ===== MITTE: Chat-Bereich =====
        JPanel centerPanel = createCenterPanel();

        // ===== RECHTS: Listen und Buttons =====
        JPanel rightPanel = createRightPanel();

        // Komponenten hinzufügen
        add(topPanel, BorderLayout.NORTH);
        add(centerPanel, BorderLayout.CENTER);
        add(rightPanel, BorderLayout.EAST);

        // Rand um das Fenster
        ((JPanel) getContentPane()).setBorder(
                BorderFactory.createEmptyBorder(10, 10, 10, 10)
        );
    }


    /**
     * Erstellt das obere Panel mit der Raum-Anzeige.
     */
    private JPanel createTopPanel() {
        JPanel panel = new JPanel(new FlowLayout(FlowLayout.LEFT));

        currentRoomLabel = new JLabel("Aktueller Raum: (keiner)");
        currentRoomLabel.setFont(new Font("SansSerif", Font.BOLD, 14));

        panel.add(currentRoomLabel);

        return panel;
    }


    /**
     * Erstellt den mittleren Bereich mit Chat und Eingabefeld.
     */
    private JPanel createCenterPanel() {
        JPanel panel = new JPanel(new BorderLayout(5, 5));

        // Chat-Textbereich
        chatTextArea = new JTextArea();
        chatTextArea.setEditable(false);
        chatTextArea.setFont(new Font("SansSerif", Font.PLAIN, 13));
        chatTextArea.setLineWrap(true);
        chatTextArea.setWrapStyleWord(true);

        JScrollPane chatScrollPane = new JScrollPane(chatTextArea);
        chatScrollPane.setVerticalScrollBarPolicy(JScrollPane.VERTICAL_SCROLLBAR_ALWAYS);

        // Eingabe-Panel (Textfeld + Button)
        JPanel inputPanel = new JPanel(new BorderLayout(5, 0));

        messageField = new JTextField();
        messageField.setFont(new Font("SansSerif", Font.PLAIN, 13));

        // Enter-Taste zum Senden
        messageField.addActionListener(e -> sendMessage());

        sendButton = new JButton("Senden");
        sendButton.addActionListener(e -> sendMessage());

        inputPanel.add(messageField, BorderLayout.CENTER);
        inputPanel.add(sendButton, BorderLayout.EAST);

        panel.add(chatScrollPane, BorderLayout.CENTER);
        panel.add(inputPanel, BorderLayout.SOUTH);

        return panel;
    }


    /**
     * Erstellt das rechte Panel mit Listen und Buttons.
     */
    private JPanel createRightPanel() {
        JPanel panel = new JPanel();
        panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
        panel.setPreferredSize(new Dimension(200, 0));

        // ===== Raumliste =====
        roomListModel = new DefaultListModel<>();
        roomList = new JList<>(roomListModel);
        roomList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);

        JScrollPane roomScrollPane = new JScrollPane(roomList);
        roomScrollPane.setBorder(new TitledBorder("Räume"));
        roomScrollPane.setPreferredSize(new Dimension(180, 120));
        roomScrollPane.setMaximumSize(new Dimension(200, 150));

        // ===== Raum-Buttons =====
        JPanel roomButtonPanel = createRoomButtonPanel();

        // ===== Nutzerliste =====
        userListModel = new DefaultListModel<>();
        userList = new JList<>(userListModel);
        userList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);

        JScrollPane userScrollPane = new JScrollPane(userList);
        userScrollPane.setBorder(new TitledBorder("Nutzer im Raum"));
        userScrollPane.setPreferredSize(new Dimension(180, 120));
        userScrollPane.setMaximumSize(new Dimension(200, 150));

        // ===== Datei-Buttons (für Meilenstein 3) =====
        JPanel fileButtonPanel = createFileButtonPanel();

        // Alles zum Panel hinzufügen
        panel.add(roomScrollPane);
        panel.add(Box.createVerticalStrut(5));
        panel.add(roomButtonPanel);
        panel.add(Box.createVerticalStrut(10));
        panel.add(userScrollPane);
        panel.add(Box.createVerticalStrut(5));
        panel.add(fileButtonPanel);

        return panel;
    }


    /**
     * Erstellt das Panel mit Raum-Buttons.
     */
    private JPanel createRoomButtonPanel() {
        JPanel panel = new JPanel();
        panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
        panel.setMaximumSize(new Dimension(200, 100));

        createRoomButton = new JButton("Raum erstellen");
        joinRoomButton = new JButton("Raum beitreten");
        leaveRoomButton = new JButton("Raum verlassen");

        // Buttons gleich breit machen
        Dimension buttonSize = new Dimension(180, 25);
        createRoomButton.setMaximumSize(buttonSize);
        joinRoomButton.setMaximumSize(buttonSize);
        leaveRoomButton.setMaximumSize(buttonSize);

        createRoomButton.setAlignmentX(Component.CENTER_ALIGNMENT);
        joinRoomButton.setAlignmentX(Component.CENTER_ALIGNMENT);
        leaveRoomButton.setAlignmentX(Component.CENTER_ALIGNMENT);

        // Action Listener
        createRoomButton.addActionListener(e -> handleCreateRoom());
        joinRoomButton.addActionListener(e -> handleJoinRoom());
        leaveRoomButton.addActionListener(e -> handleLeaveRoom());

        panel.add(createRoomButton);
        panel.add(Box.createVerticalStrut(3));
        panel.add(joinRoomButton);
        panel.add(Box.createVerticalStrut(3));
        panel.add(leaveRoomButton);

        return panel;
    }


    /**
     * Erstellt das Panel mit Datei-Buttons (für Meilenstein 3).
     */
    private JPanel createFileButtonPanel() {
        JPanel panel = new JPanel();
        panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
        panel.setMaximumSize(new Dimension(200, 100));

        uploadFileButton = new JButton("Datei hochladen");
        showFilesButton = new JButton("Dateien anzeigen");
        downloadFileButton = new JButton("Datei herunterladen");

        // Buttons gleich breit machen
        Dimension buttonSize = new Dimension(180, 25);
        uploadFileButton.setMaximumSize(buttonSize);
        showFilesButton.setMaximumSize(buttonSize);
        downloadFileButton.setMaximumSize(buttonSize);

        uploadFileButton.setAlignmentX(Component.CENTER_ALIGNMENT);
        showFilesButton.setAlignmentX(Component.CENTER_ALIGNMENT);
        downloadFileButton.setAlignmentX(Component.CENTER_ALIGNMENT);

        // Deaktiviert für Meilenstein 2 (wird in Meilenstein 3 aktiviert)
        uploadFileButton.setEnabled(false);
        showFilesButton.setEnabled(false);
        downloadFileButton.setEnabled(false);

        // Tooltips
        uploadFileButton.setToolTipText("Kommt in Meilenstein 3");
        showFilesButton.setToolTipText("Kommt in Meilenstein 3");
        downloadFileButton.setToolTipText("Kommt in Meilenstein 3");

        panel.add(uploadFileButton);
        panel.add(Box.createVerticalStrut(3));
        panel.add(showFilesButton);
        panel.add(Box.createVerticalStrut(3));
        panel.add(downloadFileButton);

        return panel;
    }


    /**
     * Setzt den Verbindungszustand und aktualisiert die GUI entsprechend.
     */
    private void setConnectedState(boolean isConnected) {
        this.connected = isConnected;

        // Raum-Buttons aktivieren/deaktivieren
        createRoomButton.setEnabled(isConnected);
        joinRoomButton.setEnabled(isConnected);
        leaveRoomButton.setEnabled(isConnected);

        // Nachrichtenfeld nur aktivieren wenn verbunden UND in einem Raum
        boolean canChat = isConnected && (currentRoom != null);
        messageField.setEnabled(canChat);
        sendButton.setEnabled(canChat);

        if (!isConnected) {
            currentRoom = null;
            currentRoomLabel.setText("Aktueller Raum: (keiner)");
        }
    }


    // ===== NETZWERK-METHODEN =====

    /**
     * Verbindet zum Server und zeigt den Login-Dialog.
     *
     * @return true wenn Login erfolgreich
     */
    public boolean connectAndLogin() {
        try {
            // Verbindung zum Server herstellen
            socket = new Socket(SERVER_HOST, SERVER_PORT);
            input = new DataInputStream(socket.getInputStream());
            output = new DataOutputStream(socket.getOutputStream());

            // Login-Dialog anzeigen
            LoginDialog loginDialog = new LoginDialog(this, input, output);
            loginDialog.setVisible(true);

            // Prüfen ob Login erfolgreich war
            if (loginDialog.isLoginSuccessful()) {
                this.username = loginDialog.getUsername();
                setTitle("Chat-Client - " + username);

                // Verbindung erfolgreich
                connected = true;
                setConnectedState(true);

                // READY-Signal senden
                output.writeUTF("READY");
                output.flush();

                // Listener-Thread für eingehende Nachrichten starten
                startMessageListener();

                return true;
            } else {
                // Login abgebrochen
                disconnect();
                return false;
            }

        } catch (IOException e) {
            JOptionPane.showMessageDialog(this,
                    "Verbindung zum Server fehlgeschlagen:\n" + e.getMessage(),
                    "Verbindungsfehler",
                    JOptionPane.ERROR_MESSAGE);
            return false;
        }
    }


    /**
     * Startet den Thread zum Empfangen von Nachrichten.
     */
    private void startMessageListener() {
        listenerThread = new Thread(() -> {
            try {
                while (connected) {
                    String message = input.readUTF();
                    handleServerMessage(message);
                }
            } catch (IOException e) {
                if (connected) {
                    SwingUtilities.invokeLater(() -> {
                        appendChat("Verbindung zum Server verloren.");
                        setConnectedState(false);
                    });
                }
            }
        }, "Message-Listener");

        listenerThread.setDaemon(true);
        listenerThread.start();
    }


    /**
     * Verarbeitet eine Nachricht vom Server.
     */
    private void handleServerMessage(String message) {
        SwingUtilities.invokeLater(() -> {
            // Prüfen auf spezielle Nachrichten
            if (message.startsWith("DISCONNECT:")) {
                String reason = message.substring(11);
                appendChat("Vom Server getrennt: " + reason);
                setConnectedState(false);
                return;
            }

            // Raumliste empfangen
            if (message.startsWith("ROOM_LIST:")) {
                String roomData = message.substring(10);
                updateRoomList(roomData);
                return;
            }

            // Nutzerliste empfangen
            if (message.startsWith("USER_LIST:")) {
                String userData = message.substring(10);
                updateUserList(userData);
                return;
            }

            // Raum erstellt
            if (message.startsWith("ROOM_CREATED:")) {
                String roomName = message.substring(13);
                appendChat("Raum '" + roomName + "' wurde erstellt.");
                return;
            }

            // Raum beigetreten
            if (message.startsWith("ROOM_JOINED:")) {
                String roomName = message.substring(12);
                setCurrentRoom(roomName);
                appendChat("Du bist Raum '" + roomName + "' beigetreten.");
                // Chat leeren beim Raumwechsel
                chatTextArea.setText("");
                appendChat("=== Raum: " + roomName + " ===");
                return;
            }

            // Raum verlassen
            if (message.startsWith("ROOM_LEFT:")) {
                String roomName = message.substring(10);
                setCurrentRoom(null);
                appendChat("Du hast Raum '" + roomName + "' verlassen.");
                clearUsers();
                return;
            }

            // Raum gelöscht
            if (message.startsWith("ROOM_DELETED:")) {
                String roomName = message.substring(13);
                appendChat("Raum '" + roomName + "' wurde gelöscht.");
                return;
            }

            // Fehler vom Server
            if (message.startsWith("ERROR:")) {
                String error = message.substring(6);
                JOptionPane.showMessageDialog(this,
                        error,
                        "Fehler",
                        JOptionPane.ERROR_MESSAGE);
                return;
            }

            // Normale Chat-Nachricht
            appendChat(message);
        });
    }


    /**
     * Aktualisiert die Raumliste.
     */
    private void updateRoomList(String roomData) {
        roomListModel.clear();
        if (!roomData.isEmpty()) {
            String[] rooms = roomData.split(",");
            for (String room : rooms) {
                if (!room.trim().isEmpty()) {
                    roomListModel.addElement(room.trim());
                }
            }
        }
    }


    /**
     * Aktualisiert die Nutzerliste.
     */
    private void updateUserList(String userData) {
        userListModel.clear();
        if (!userData.isEmpty()) {
            String[] users = userData.split(",");
            for (String user : users) {
                if (!user.trim().isEmpty()) {
                    userListModel.addElement(user.trim());
                }
            }
        }
    }


    /**
     * Sendet eine Nachricht an den Server.
     */
    private void sendMessage() {
        String message = messageField.getText().trim();

        if (message.isEmpty()) {
            return;
        }

        try {
            // Nachricht senden
            output.writeUTF(message);
            output.flush();

            // Eigene Nachricht im Chat anzeigen
            appendChat("[" + username + "] " + message);

            // Eingabefeld leeren
            messageField.setText("");

        } catch (IOException e) {
            appendChat("Fehler beim Senden: " + e.getMessage());
        }
    }


    /**
     * Trennt die Verbindung zum Server.
     */
    private void disconnect() {
        connected = false;

        try {
            if (input != null) input.close();
            if (output != null) output.close();
            if (socket != null) socket.close();
        } catch (IOException e) {
            // Ignorieren beim Schließen
        }
    }


    // ===== EVENT-HANDLER =====

    /**
     * Raum erstellen.
     */
    private void handleCreateRoom() {
        String roomName = JOptionPane.showInputDialog(this,
                "Raumname eingeben:",
                "Neuen Raum erstellen",
                JOptionPane.PLAIN_MESSAGE);

        if (roomName != null && !roomName.trim().isEmpty()) {
            try {
                output.writeUTF("CREATE_ROOM:" + roomName.trim());
                output.flush();
            } catch (IOException e) {
                appendChat("Fehler: " + e.getMessage());
            }
        }
    }


    /**
     * Raum beitreten.
     */
    private void handleJoinRoom() {
        String selectedRoom = roomList.getSelectedValue();

        if (selectedRoom == null) {
            JOptionPane.showMessageDialog(this,
                    "Bitte wähle einen Raum aus der Liste.",
                    "Kein Raum ausgewählt",
                    JOptionPane.WARNING_MESSAGE);
            return;
        }

        // Prüfen ob schon im Raum
        if (selectedRoom.equals(currentRoom)) {
            JOptionPane.showMessageDialog(this,
                    "Du bist bereits in diesem Raum.",
                    "Hinweis",
                    JOptionPane.INFORMATION_MESSAGE);
            return;
        }

        try {
            output.writeUTF("JOIN_ROOM:" + selectedRoom);
            output.flush();
        } catch (IOException e) {
            appendChat("Fehler: " + e.getMessage());
        }
    }


    /**
     * Raum verlassen.
     */
    private void handleLeaveRoom() {
        if (currentRoom == null) {
            JOptionPane.showMessageDialog(this,
                    "Du bist in keinem Raum.",
                    "Fehler",
                    JOptionPane.WARNING_MESSAGE);
            return;
        }

        // Bestätigung wenn letzter im Raum
        int memberCount = userListModel.size();
        if (memberCount <= 1) {
            int choice = JOptionPane.showConfirmDialog(this,
                    "Du bist die letzte Person in diesem Raum.\nDas Verlassen wird den Raum löschen.\n\nFortfahren?",
                    "Raum verlassen bestätigen",
                    JOptionPane.YES_NO_OPTION,
                    JOptionPane.WARNING_MESSAGE);

            if (choice != JOptionPane.YES_OPTION) {
                return;
            }
        }

        try {
            output.writeUTF("LEAVE_ROOM");
            output.flush();
        } catch (IOException e) {
            appendChat("Fehler: " + e.getMessage());
        }
    }


    /**
     * Fenster schließen.
     */
    private void handleWindowClosing() {
        int choice = JOptionPane.showConfirmDialog(this,
                "Chat wirklich beenden?",
                "Beenden",
                JOptionPane.YES_NO_OPTION);

        if (choice == JOptionPane.YES_OPTION) {
            disconnect();
            dispose();
            System.exit(0);
        }
    }


    // ===== HILFSMETHODEN =====

    /**
     * Fügt eine Nachricht zum Chat hinzu.
     */
    public void appendChat(String message) {
        chatTextArea.append(message + "\n");
        // Automatisch nach unten scrollen
        chatTextArea.setCaretPosition(chatTextArea.getDocument().getLength());
    }


    /**
     * Setzt den aktuellen Raum und aktualisiert die GUI entsprechend.
     */
    public void setCurrentRoom(String roomName) {
        this.currentRoom = roomName;
        if (roomName != null) {
            currentRoomLabel.setText("Aktueller Raum: " + roomName);
            // Nachrichteneingabe aktivieren
            messageField.setEnabled(true);
            sendButton.setEnabled(true);
            messageField.requestFocus();
        } else {
            currentRoomLabel.setText("Aktueller Raum: (keiner)");
            // Nachrichteneingabe deaktivieren
            messageField.setEnabled(false);
            sendButton.setEnabled(false);
        }
    }


    /**
     * Fügt einen Raum zur Liste hinzu.
     */
    public void addRoom(String roomName) {
        if (!roomListModel.contains(roomName)) {
            roomListModel.addElement(roomName);
        }
    }


    /**
     * Entfernt einen Raum aus der Liste.
     */
    public void removeRoom(String roomName) {
        roomListModel.removeElement(roomName);
    }


    /**
     * Fügt einen Nutzer zur Liste hinzu.
     */
    public void addUser(String username) {
        if (!userListModel.contains(username)) {
            userListModel.addElement(username);
        }
    }


    /**
     * Entfernt einen Nutzer aus der Liste.
     */
    public void removeUser(String username) {
        userListModel.removeElement(username);
    }


    /**
     * Leert die Nutzerliste.
     */
    public void clearUsers() {
        userListModel.clear();
    }


    /**
     * Main-Methode - Startet die Client-GUI.
     */
    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            ClientGUI gui = new ClientGUI();

            // Verbinden und einloggen
            if (gui.connectAndLogin()) {
                gui.setVisible(true);
            } else {
                System.exit(0);
            }
        });
    }
}