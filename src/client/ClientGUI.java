package client;

import javax.swing.*;
import javax.swing.border.TitledBorder;
import javax.swing.filechooser.FileNameExtensionFilter;
import java.awt.*;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.io.*;
import java.net.Socket;

/**
 * Chat-Client mit grafischer Oberfläche.
 * Kann Nachrichten senden/empfangen und Dateien hoch-/runterladen.
 */
public class ClientGUI extends JFrame {

    private static final String SERVER_HOST = "localhost";
    private static final int SERVER_PORT = 3143;

    // GUI-Komponenten
    private JLabel currentRoomLabel;
    private JTextArea chatTextArea;
    private JTextField messageField;
    private JButton sendButton;

    private JList<String> roomList;
    private DefaultListModel<String> roomListModel;

    private JList<String> userList;
    private DefaultListModel<String> userListModel;

    private JButton createRoomButton;
    private JButton joinRoomButton;
    private JButton leaveRoomButton;

    private JButton uploadFileButton;
    private JButton showFilesButton;
    private JButton downloadFileButton;

    // Netzwerk
    private Socket socket;
    private DataInputStream input;
    private DataOutputStream output;
    private Thread listenerThread;
    private volatile boolean connected = false;

    // Benutzerdaten
    private String username;
    private String currentRoom;


    public ClientGUI() {
        setTitle("Chat-Client");
        setDefaultCloseOperation(JFrame.DO_NOTHING_ON_CLOSE);
        setSize(900, 600);
        setLocationRelativeTo(null);

        addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent e) {
                handleWindowClosing();
            }
        });

        initComponents();
        setConnectedState(false);
    }


    private void initComponents() {
        setLayout(new BorderLayout(10, 10));

        add(createTopPanel(), BorderLayout.NORTH);
        add(createCenterPanel(), BorderLayout.CENTER);
        add(createRightPanel(), BorderLayout.EAST);

        ((JPanel) getContentPane()).setBorder(
                BorderFactory.createEmptyBorder(10, 10, 10, 10)
        );
    }


    private JPanel createTopPanel() {
        JPanel panel = new JPanel(new FlowLayout(FlowLayout.LEFT));
        currentRoomLabel = new JLabel("Aktueller Raum: (keiner)");
        currentRoomLabel.setFont(new Font("SansSerif", Font.BOLD, 14));
        panel.add(currentRoomLabel);
        return panel;
    }


    private JPanel createCenterPanel() {
        JPanel panel = new JPanel(new BorderLayout(5, 5));

        chatTextArea = new JTextArea();
        chatTextArea.setEditable(false);
        chatTextArea.setFont(new Font("SansSerif", Font.PLAIN, 13));
        chatTextArea.setLineWrap(true);
        chatTextArea.setWrapStyleWord(true);

        JScrollPane chatScrollPane = new JScrollPane(chatTextArea);
        chatScrollPane.setVerticalScrollBarPolicy(JScrollPane.VERTICAL_SCROLLBAR_ALWAYS);

        JPanel inputPanel = new JPanel(new BorderLayout(5, 0));
        messageField = new JTextField();
        messageField.setFont(new Font("SansSerif", Font.PLAIN, 13));
        messageField.addActionListener(e -> sendMessage());

        sendButton = new JButton("Senden");
        sendButton.addActionListener(e -> sendMessage());

        inputPanel.add(messageField, BorderLayout.CENTER);
        inputPanel.add(sendButton, BorderLayout.EAST);

        panel.add(chatScrollPane, BorderLayout.CENTER);
        panel.add(inputPanel, BorderLayout.SOUTH);

        return panel;
    }


    private JPanel createRightPanel() {
        JPanel panel = new JPanel();
        panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
        panel.setPreferredSize(new Dimension(200, 0));

        // Raumliste
        roomListModel = new DefaultListModel<>();
        roomList = new JList<>(roomListModel);
        roomList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);

        JScrollPane roomScrollPane = new JScrollPane(roomList);
        roomScrollPane.setBorder(new TitledBorder("Räume"));
        roomScrollPane.setPreferredSize(new Dimension(180, 120));
        roomScrollPane.setMaximumSize(new Dimension(200, 150));

        // Nutzerliste
        userListModel = new DefaultListModel<>();
        userList = new JList<>(userListModel);
        userList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);

        JScrollPane userScrollPane = new JScrollPane(userList);
        userScrollPane.setBorder(new TitledBorder("Nutzer im Raum"));
        userScrollPane.setPreferredSize(new Dimension(180, 120));
        userScrollPane.setMaximumSize(new Dimension(200, 150));

        panel.add(roomScrollPane);
        panel.add(Box.createVerticalStrut(5));
        panel.add(createRoomButtonPanel());
        panel.add(Box.createVerticalStrut(10));
        panel.add(userScrollPane);
        panel.add(Box.createVerticalStrut(5));
        panel.add(createFileButtonPanel());

        return panel;
    }


    private JPanel createRoomButtonPanel() {
        JPanel panel = new JPanel();
        panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
        panel.setMaximumSize(new Dimension(200, 100));

        createRoomButton = new JButton("Raum erstellen");
        joinRoomButton = new JButton("Raum beitreten");
        leaveRoomButton = new JButton("Raum verlassen");

        Dimension buttonSize = new Dimension(180, 25);
        createRoomButton.setMaximumSize(buttonSize);
        joinRoomButton.setMaximumSize(buttonSize);
        leaveRoomButton.setMaximumSize(buttonSize);

        createRoomButton.setAlignmentX(Component.CENTER_ALIGNMENT);
        joinRoomButton.setAlignmentX(Component.CENTER_ALIGNMENT);
        leaveRoomButton.setAlignmentX(Component.CENTER_ALIGNMENT);

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


    private JPanel createFileButtonPanel() {
        JPanel panel = new JPanel();
        panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
        panel.setMaximumSize(new Dimension(200, 100));

        uploadFileButton = new JButton("Datei hochladen");
        showFilesButton = new JButton("Dateien anzeigen");
        downloadFileButton = new JButton("Datei herunterladen");

        Dimension buttonSize = new Dimension(180, 25);
        uploadFileButton.setMaximumSize(buttonSize);
        showFilesButton.setMaximumSize(buttonSize);
        downloadFileButton.setMaximumSize(buttonSize);

        uploadFileButton.setAlignmentX(Component.CENTER_ALIGNMENT);
        showFilesButton.setAlignmentX(Component.CENTER_ALIGNMENT);
        downloadFileButton.setAlignmentX(Component.CENTER_ALIGNMENT);

        uploadFileButton.addActionListener(e -> handleUploadFile());
        showFilesButton.addActionListener(e -> handleShowFiles());
        downloadFileButton.addActionListener(e -> handleDownloadFile());

        panel.add(uploadFileButton);
        panel.add(Box.createVerticalStrut(3));
        panel.add(showFilesButton);
        panel.add(Box.createVerticalStrut(3));
        panel.add(downloadFileButton);

        return panel;
    }


    private void setConnectedState(boolean isConnected) {
        this.connected = isConnected;

        createRoomButton.setEnabled(isConnected);
        joinRoomButton.setEnabled(isConnected);
        leaveRoomButton.setEnabled(isConnected);

        // Datei-Buttons nur aktiv wenn in einem Raum
        boolean canUseFiles = isConnected && (currentRoom != null);
        uploadFileButton.setEnabled(canUseFiles);
        showFilesButton.setEnabled(canUseFiles);
        downloadFileButton.setEnabled(canUseFiles);

        boolean canChat = isConnected && (currentRoom != null);
        messageField.setEnabled(canChat);
        sendButton.setEnabled(canChat);

        if (!isConnected) {
            currentRoom = null;
            currentRoomLabel.setText("Aktueller Raum: (keiner)");
        }
    }


    // ========================================================================
    // NETZWERK
    // ========================================================================


    public boolean connectAndLogin() {
        try {
            socket = new Socket(SERVER_HOST, SERVER_PORT);
            input = new DataInputStream(socket.getInputStream());
            output = new DataOutputStream(socket.getOutputStream());

            LoginDialog loginDialog = new LoginDialog(this, input, output);
            loginDialog.setVisible(true);

            if (loginDialog.isLoginSuccessful()) {
                this.username = loginDialog.getUsername();
                setTitle("Chat-Client - " + username);

                connected = true;
                setConnectedState(true);

                output.writeUTF("READY");
                output.flush();

                startMessageListener();
                return true;
            } else {
                disconnect();
                return false;
            }

        } catch (IOException e) {
            JOptionPane.showMessageDialog(this,
                    "Verbindung fehlgeschlagen:\n" + e.getMessage(),
                    "Verbindungsfehler",
                    JOptionPane.ERROR_MESSAGE);
            return false;
        }
    }


    /**
     * Startet einen Thread der ständig auf Nachrichten vom Server wartet.
     */
    private void startMessageListener() {
        listenerThread = new Thread(() -> {
            try {
                while (connected) {
                    String message = input.readUTF();

                    // WICHTIG: FILE_DATA muss HIER behandelt werden (im Listener-Thread),
                    // weil die Binärdaten direkt danach kommen und sofort gelesen werden müssen.
                    // Wenn wir das an invokeLater übergeben, liest der nächste readUTF()
                    // die Binärdaten als Text - das geht schief!
                    if (message.startsWith("FILE_DATA:")) {
                        String fileName = message.substring(10);
                        receiveFileDataNow(fileName);
                    } else {
                        // Alle anderen Nachrichten normal auf dem Swing-Thread verarbeiten
                        handleServerMessage(message);
                    }
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
            // Verbindung getrennt
            if (message.startsWith("DISCONNECT:")) {
                appendChat("Vom Server getrennt: " + message.substring(11));
                setConnectedState(false);
                return;
            }

            // Raumliste
            if (message.startsWith("ROOM_LIST:")) {
                updateRoomList(message.substring(10));
                return;
            }

            // Nutzerliste
            if (message.startsWith("USER_LIST:")) {
                updateUserList(message.substring(10));
                return;
            }

            // Raum erstellt
            if (message.startsWith("ROOM_CREATED:")) {
                appendChat("Raum '" + message.substring(13) + "' wurde erstellt.");
                return;
            }

            // Raum beigetreten
            if (message.startsWith("ROOM_JOINED:")) {
                String roomName = message.substring(12);
                setCurrentRoom(roomName);
                chatTextArea.setText("");
                appendChat("=== Raum: " + roomName + " ===");
                return;
            }

            // Raum verlassen
            if (message.startsWith("ROOM_LEFT:")) {
                setCurrentRoom(null);
                appendChat("Du hast den Raum verlassen.");
                clearUsers();
                return;
            }

            // Raum gelöscht
            if (message.startsWith("ROOM_DELETED:")) {
                appendChat("Raum '" + message.substring(13) + "' wurde gelöscht.");
                return;
            }

            // Fehler
            if (message.startsWith("ERROR:")) {
                JOptionPane.showMessageDialog(this, message.substring(6),
                        "Fehler", JOptionPane.ERROR_MESSAGE);
                return;
            }

            // Warnung vom Admin
            if (message.startsWith("WARNING:")) {
                JOptionPane.showMessageDialog(this,
                        "WARNUNG VOM SERVER:\n\n" + message.substring(8),
                        "Warnung", JOptionPane.WARNING_MESSAGE);
                return;
            }

            // ============================================================
            // DATEI-ANTWORTEN
            // ============================================================

            // Upload fehlgeschlagen
            if (message.startsWith("UPLOAD_ERROR:")) {
                JOptionPane.showMessageDialog(this,
                        "Upload fehlgeschlagen:\n" + message.substring(13),
                        "Fehler", JOptionPane.ERROR_MESSAGE);
                return;
            }

            // Upload erfolgreich
            if (message.startsWith("UPLOAD_SUCCESS:")) {
                appendChat("Datei '" + message.substring(15) + "' hochgeladen.");
                return;
            }

            // Dateiliste empfangen
            if (message.startsWith("FILE_LIST:")) {
                showFileListDialog(message.substring(10));
                return;
            }

            // Download fehlgeschlagen
            if (message.startsWith("DOWNLOAD_ERROR:")) {
                JOptionPane.showMessageDialog(this,
                        "Download fehlgeschlagen:\n" + message.substring(15),
                        "Fehler", JOptionPane.ERROR_MESSAGE);
                return;
            }

            // FILE_DATA wird im Listener-Thread behandelt (siehe startMessageListener)
            // weil die Binärdaten sofort gelesen werden müssen

            // Normale Chat-Nachricht
            appendChat(message);
        });
    }


    private void updateRoomList(String roomData) {
        roomListModel.clear();
        if (!roomData.isEmpty()) {
            for (String room : roomData.split(",")) {
                if (!room.trim().isEmpty()) {
                    roomListModel.addElement(room.trim());
                }
            }
        }
    }


    private void updateUserList(String userData) {
        userListModel.clear();
        if (!userData.isEmpty()) {
            for (String user : userData.split(",")) {
                if (!user.trim().isEmpty()) {
                    userListModel.addElement(user.trim());
                }
            }
        }
    }


    private void sendMessage() {
        String message = messageField.getText().trim();
        if (message.isEmpty()) return;

        try {
            output.writeUTF(message);
            output.flush();
            appendChat("[" + username + "] " + message);
            messageField.setText("");
        } catch (IOException e) {
            appendChat("Fehler beim Senden: " + e.getMessage());
        }
    }


    private void disconnect() {
        connected = false;
        try {
            if (input != null) input.close();
            if (output != null) output.close();
            if (socket != null) socket.close();
        } catch (IOException e) {
            // Ignorieren
        }
    }


    // ========================================================================
    // RAUM-FUNKTIONEN
    // ========================================================================


    private void handleCreateRoom() {
        String roomName = JOptionPane.showInputDialog(this,
                "Raumname eingeben:", "Neuen Raum erstellen", JOptionPane.PLAIN_MESSAGE);

        if (roomName != null && !roomName.trim().isEmpty()) {
            try {
                output.writeUTF("CREATE_ROOM:" + roomName.trim());
                output.flush();
            } catch (IOException e) {
                appendChat("Fehler: " + e.getMessage());
            }
        }
    }


    private void handleJoinRoom() {
        String selectedRoom = roomList.getSelectedValue();

        if (selectedRoom == null) {
            JOptionPane.showMessageDialog(this, "Bitte wähle einen Raum aus.",
                    "Hinweis", JOptionPane.WARNING_MESSAGE);
            return;
        }

        if (selectedRoom.equals(currentRoom)) {
            JOptionPane.showMessageDialog(this, "Du bist bereits in diesem Raum.",
                    "Hinweis", JOptionPane.INFORMATION_MESSAGE);
            return;
        }

        try {
            output.writeUTF("JOIN_ROOM:" + selectedRoom);
            output.flush();
        } catch (IOException e) {
            appendChat("Fehler: " + e.getMessage());
        }
    }


    private void handleLeaveRoom() {
        if (currentRoom == null) {
            JOptionPane.showMessageDialog(this, "Du bist in keinem Raum.",
                    "Hinweis", JOptionPane.WARNING_MESSAGE);
            return;
        }

        // Warnung wenn letzter im Raum
        if (userListModel.size() <= 1) {
            int choice = JOptionPane.showConfirmDialog(this,
                    "Du bist die letzte Person. Der Raum wird gelöscht.\nFortfahren?",
                    "Raum verlassen", JOptionPane.YES_NO_OPTION, JOptionPane.WARNING_MESSAGE);
            if (choice != JOptionPane.YES_OPTION) return;
        }

        try {
            output.writeUTF("LEAVE_ROOM");
            output.flush();
        } catch (IOException e) {
            appendChat("Fehler: " + e.getMessage());
        }
    }


    // ========================================================================
    // DATEI-FUNKTIONEN (Meilenstein 3)
    // ========================================================================
    //
    // So funktioniert der Upload:
    // 1. Benutzer wählt Datei aus (JFileChooser)
    // 2. Wir schicken "UPLOAD_FILE:dateiname" an Server
    // 3. Server antwortet "READY_FOR_UPLOAD"
    // 4. Wir schicken: Dateigröße (4 Bytes) + alle Datei-Bytes
    // 5. Server speichert und antwortet "UPLOAD_SUCCESS"
    //
    // So funktioniert der Download:
    // 1. Wir schicken "DOWNLOAD_FILE:dateiname" an Server
    // 2. Server antwortet "FILE_DATA:dateiname"
    // 3. Server schickt: Dateigröße (4 Bytes) + alle Datei-Bytes
    // 4. Wir zeigen Speichern-Dialog und schreiben die Bytes in eine Datei
    // ========================================================================


    /**
     * Öffnet Datei-Dialog und lädt die gewählte Datei hoch.
     */
    private void handleUploadFile() {
        if (currentRoom == null) {
            JOptionPane.showMessageDialog(this, "Du musst zuerst einem Raum beitreten.",
                    "Hinweis", JOptionPane.WARNING_MESSAGE);
            return;
        }

        // Datei auswählen
        JFileChooser fileChooser = new JFileChooser();
        fileChooser.setDialogTitle("Datei zum Hochladen auswählen");

        // Nur PDF und Bilder erlauben
        FileNameExtensionFilter filter = new FileNameExtensionFilter(
                "PDF und Bilder (PDF, PNG, JPG, GIF)",
                "pdf", "png", "jpg", "jpeg", "gif"
        );
        fileChooser.setFileFilter(filter);

        int result = fileChooser.showOpenDialog(this);
        if (result != JFileChooser.APPROVE_OPTION) return;

        File selectedFile = fileChooser.getSelectedFile();

        // Größe prüfen (max 10 MB)
        if (selectedFile.length() > 10 * 1024 * 1024) {
            JOptionPane.showMessageDialog(this, "Datei zu groß (max. 10 MB).",
                    "Fehler", JOptionPane.ERROR_MESSAGE);
            return;
        }

        // Upload in separatem Thread (blockiert sonst die GUI)
        new Thread(() -> uploadFile(selectedFile)).start();
    }


    /**
     * Lädt eine Datei auf den Server.
     *
     * Ablauf:
     * 1. Datei in byte-Array einlesen
     * 2. "UPLOAD_FILE:name" senden
     * 3. Kurz warten (Server bereitet sich vor)
     * 4. Größe als int senden (4 Bytes)
     * 5. Alle Datei-Bytes senden
     */
    private void uploadFile(File file) {
        try {
            // Datei komplett einlesen
            byte[] fileData = new byte[(int) file.length()];
            try (FileInputStream fis = new FileInputStream(file)) {
                fis.read(fileData);
            }

            // synchronized: Nur ein Thread darf gleichzeitig senden
            synchronized (output) {
                // Befehl senden
                output.writeUTF("UPLOAD_FILE:" + file.getName());
                output.flush();

                // Kurz warten bis Server bereit ist
                Thread.sleep(100);

                // Dateigröße senden (writeInt = 4 Bytes)
                output.writeInt(fileData.length);

                // Datei-Bytes senden
                output.write(fileData);
                output.flush();
            }

            SwingUtilities.invokeLater(() -> {
                appendChat("Lade '" + file.getName() + "' hoch...");
            });

        } catch (IOException | InterruptedException e) {
            SwingUtilities.invokeLater(() -> {
                JOptionPane.showMessageDialog(this,
                        "Fehler beim Hochladen: " + e.getMessage(),
                        "Fehler", JOptionPane.ERROR_MESSAGE);
            });
        }
    }


    /**
     * Fordert die Dateiliste vom Server an.
     */
    private void handleShowFiles() {
        if (currentRoom == null) {
            JOptionPane.showMessageDialog(this, "Du musst zuerst einem Raum beitreten.",
                    "Hinweis", JOptionPane.WARNING_MESSAGE);
            return;
        }

        try {
            output.writeUTF("LIST_FILES");
            output.flush();
        } catch (IOException e) {
            appendChat("Fehler: " + e.getMessage());
        }
    }


    /**
     * Zeigt die Dateiliste in einem Dialog an.
     */
    private void showFileListDialog(String fileData) {
        if (fileData.isEmpty()) {
            JOptionPane.showMessageDialog(this, "Keine Dateien im Raum.",
                    "Dateien", JOptionPane.INFORMATION_MESSAGE);
            return;
        }

        String[] files = fileData.split(",");

        // Dialog bauen
        JDialog dialog = new JDialog(this, "Dateien in: " + currentRoom, true);
        dialog.setSize(350, 300);
        dialog.setLocationRelativeTo(this);

        // Liste
        DefaultListModel<String> listModel = new DefaultListModel<>();
        for (String file : files) {
            if (!file.trim().isEmpty()) {
                listModel.addElement(file.trim());
            }
        }
        JList<String> fileList = new JList<>(listModel);
        fileList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);

        // Download-Button
        JButton downloadBtn = new JButton("Herunterladen");
        downloadBtn.addActionListener(e -> {
            String selected = fileList.getSelectedValue();
            if (selected != null) {
                dialog.dispose();
                downloadFileFromServer(selected);
            }
        });

        // Schließen-Button
        JButton closeBtn = new JButton("Schließen");
        closeBtn.addActionListener(e -> dialog.dispose());

        // Layout
        JPanel buttonPanel = new JPanel(new FlowLayout());
        buttonPanel.add(downloadBtn);
        buttonPanel.add(closeBtn);

        dialog.setLayout(new BorderLayout(5, 5));
        dialog.add(new JLabel("  " + listModel.size() + " Datei(en):"), BorderLayout.NORTH);
        dialog.add(new JScrollPane(fileList), BorderLayout.CENTER);
        dialog.add(buttonPanel, BorderLayout.SOUTH);

        dialog.setVisible(true);
    }


    /**
     * Fragt nach Dateiname und lädt sie herunter.
     */
    private void handleDownloadFile() {
        if (currentRoom == null) {
            JOptionPane.showMessageDialog(this, "Du musst zuerst einem Raum beitreten.",
                    "Hinweis", JOptionPane.WARNING_MESSAGE);
            return;
        }

        String fileName = JOptionPane.showInputDialog(this,
                "Dateiname eingeben:\n(Tipp: Erst 'Dateien anzeigen' klicken)",
                "Datei herunterladen", JOptionPane.PLAIN_MESSAGE);

        if (fileName != null && !fileName.trim().isEmpty()) {
            downloadFileFromServer(fileName.trim());
        }
    }


    /**
     * Fordert eine Datei vom Server an.
     */
    private void downloadFileFromServer(String fileName) {
        try {
            output.writeUTF("DOWNLOAD_FILE:" + fileName);
            output.flush();
            appendChat("Lade '" + fileName + "' herunter...");
        } catch (IOException e) {
            appendChat("Fehler: " + e.getMessage());
        }
    }


    /**
     * Empfängt die Datei-Bytes vom Server und speichert sie.
     *
     * WICHTIG: Diese Methode wird direkt im Listener-Thread aufgerufen!
     * Das muss so sein, weil die Binärdaten direkt nach "FILE_DATA:" kommen.
     * Wenn wir in einen anderen Thread wechseln würden, liest der Listener-Thread
     * die Binärdaten als nächste UTF-Nachricht - und das geht schief.
     *
     * Ablauf:
     * 1. readInt() liest die Dateigröße (4 Bytes)
     * 2. readFully() liest genau so viele Bytes
     * 3. Speichern-Dialog zeigen (auf dem Swing-Thread)
     * 4. Bytes in Datei schreiben
     */
    private void receiveFileDataNow(String fileName) {
        try {
            // Größe lesen (4 Bytes) - direkt im Listener-Thread!
            int fileSize = input.readInt();

            // Alle Bytes lesen - readFully wartet bis alles da ist
            byte[] fileData = new byte[fileSize];
            input.readFully(fileData);

            // Jetzt sind alle Daten gelesen, der Stream ist wieder "sauber"
            // Ab hier können wir auf den Swing-Thread wechseln für den Dialog
            SwingUtilities.invokeLater(() -> {
                JFileChooser fileChooser = new JFileChooser();
                fileChooser.setDialogTitle("Datei speichern");
                fileChooser.setSelectedFile(new File(fileName));

                int result = fileChooser.showSaveDialog(this);
                if (result == JFileChooser.APPROVE_OPTION) {
                    File targetFile = fileChooser.getSelectedFile();
                    try (FileOutputStream fos = new FileOutputStream(targetFile)) {
                        fos.write(fileData);
                        appendChat("Gespeichert: " + targetFile.getAbsolutePath());
                    } catch (IOException e) {
                        JOptionPane.showMessageDialog(this,
                                "Fehler beim Speichern: " + e.getMessage(),
                                "Fehler", JOptionPane.ERROR_MESSAGE);
                    }
                } else {
                    appendChat("Download abgebrochen.");
                }
            });

        } catch (Exception e) {
            SwingUtilities.invokeLater(() -> {
                JOptionPane.showMessageDialog(this,
                        "Fehler beim Empfangen: " + e.getMessage(),
                        "Fehler", JOptionPane.ERROR_MESSAGE);
            });
        }
    }


    // ========================================================================
    // HILFSMETHODEN
    // ========================================================================


    private void handleWindowClosing() {
        int choice = JOptionPane.showConfirmDialog(this,
                "Chat beenden?", "Beenden", JOptionPane.YES_NO_OPTION);

        if (choice == JOptionPane.YES_OPTION) {
            disconnect();
            dispose();
            System.exit(0);
        }
    }


    public void appendChat(String message) {
        chatTextArea.append(message + "\n");
        chatTextArea.setCaretPosition(chatTextArea.getDocument().getLength());
    }


    public void setCurrentRoom(String roomName) {
        this.currentRoom = roomName;

        if (roomName != null) {
            currentRoomLabel.setText("Aktueller Raum: " + roomName);
            messageField.setEnabled(true);
            sendButton.setEnabled(true);
            uploadFileButton.setEnabled(true);
            showFilesButton.setEnabled(true);
            downloadFileButton.setEnabled(true);
            messageField.requestFocus();
        } else {
            currentRoomLabel.setText("Aktueller Raum: (keiner)");
            messageField.setEnabled(false);
            sendButton.setEnabled(false);
            uploadFileButton.setEnabled(false);
            showFilesButton.setEnabled(false);
            downloadFileButton.setEnabled(false);
        }
    }


    public void clearUsers() {
        userListModel.clear();
    }


    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            ClientGUI gui = new ClientGUI();
            if (gui.connectAndLogin()) {
                gui.setVisible(true);
            } else {
                System.exit(0);
            }
        });
    }
}