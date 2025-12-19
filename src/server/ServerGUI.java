package server;

import java.awt.*;
import java.awt.event.*;
import java.util.List;
import javax.swing.*;

/**
 * Swing-basierte Administrations-GUI für den Chat-Server.
 * Startet den Server in einem Hintergrund-Thread mit:
 * - Raumerstellung / -löschung
 * - Userliste & Raumliste
 * - Log-Anzeige
 * - Warn / Kick / Ban Buttons
 */
public class ServerGUI extends JFrame {

    private final Server server;

    private final DefaultListModel<String> roomListModel = new DefaultListModel<>();
    private final JList<String> roomList = new JList<>(roomListModel);

    private final DefaultListModel<String> userListModel = new DefaultListModel<>();
    private final JList<String> userList = new JList<>(userListModel);

    private final JTextArea logArea = new JTextArea();
    private final JTextField newRoomField = new JTextField();
    private final JButton createRoomBtn = new JButton("Raum erstellen");
    private final JButton deleteRoomBtn = new JButton("Raum löschen");
    private final JButton warnBtn = new JButton("Warnen");
    private final JButton kickBtn = new JButton("Kicken");
    private final JButton banBtn = new JButton("Bannen");
    private final JButton stopServerBtn = new JButton("Server stoppen");
    private final JButton refreshBtn = new JButton("Aktualisieren");

    private final Timer refreshTimer;

    public ServerGUI(Server server) {
        super("Chat-Server - Admin");
        this.server = server;
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setSize(900, 600);
        setLocationRelativeTo(null);

        initLayout();
        initListeners();

        // Timer aktualisiert alle 2s Raum- und Userlisten
        refreshTimer = new Timer(2000, e -> refreshLists());
        refreshTimer.start();
    }

    private void initLayout() {
        // Links: Räume + Controls
        JPanel leftPanel = new JPanel(new BorderLayout(6, 6));
        leftPanel.setBorder(BorderFactory.createTitledBorder("Räume"));
        leftPanel.add(new JScrollPane(roomList), BorderLayout.CENTER);

        JPanel leftSouth = new JPanel(new BorderLayout(4,4));
        leftSouth.add(newRoomField, BorderLayout.CENTER);

        JPanel leftSouthButtons = new JPanel(new GridLayout(1,2,4,4));
        leftSouthButtons.add(createRoomBtn);
        leftSouthButtons.add(deleteRoomBtn);
        leftSouth.add(leftSouthButtons, BorderLayout.SOUTH);

        leftPanel.add(leftSouth, BorderLayout.SOUTH);

        // Mitte: Log
        JPanel centerPanel = new JPanel(new BorderLayout(6,6));
        centerPanel.setBorder(BorderFactory.createTitledBorder("Server-Log"));
        logArea.setEditable(false);
        logArea.setLineWrap(true);
        centerPanel.add(new JScrollPane(logArea), BorderLayout.CENTER);

        JPanel centerButtons = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        centerButtons.add(refreshBtn);
        centerButtons.add(stopServerBtn);
        centerPanel.add(centerButtons, BorderLayout.SOUTH);

        // Rechts: User + Admin-Buttons
        JPanel rightPanel = new JPanel(new BorderLayout(6,6));
        rightPanel.setBorder(BorderFactory.createTitledBorder("Benutzer"));
        rightPanel.add(new JScrollPane(userList), BorderLayout.CENTER);

        JPanel adminButtons = new JPanel(new GridLayout(3,1,4,4));
        adminButtons.add(warnBtn);
        adminButtons.add(kickBtn);
        adminButtons.add(banBtn);

        rightPanel.add(adminButtons, BorderLayout.SOUTH);

        // Gesamt-Layout
        getContentPane().setLayout(new BorderLayout(8,8));
        getContentPane().add(leftPanel, BorderLayout.WEST);
        getContentPane().add(centerPanel, BorderLayout.CENTER);
        getContentPane().add(rightPanel, BorderLayout.EAST);
    }

    private void initListeners() {
        createRoomBtn.addActionListener(e -> {
            String name = newRoomField.getText().trim();
            if (name.isEmpty()) {
                appendLog("Kein Raumname angegeben.");
                return;
            }
            boolean ok = server.createRoom(name);
            appendLog(ok ? "Raum erstellt: " + name : "Raum existiert bereits: " + name);
            newRoomField.setText("");
            refreshLists();
        });

        deleteRoomBtn.addActionListener(e -> {
            String selected = roomList.getSelectedValue();
            if (selected == null) {
                appendLog("Keinen Raum ausgewählt.");
                return;
            }
            boolean ok = server.deleteRoom(selected);
            appendLog(ok ? "Raum gelöscht: " + selected : "Raum konnte nicht gelöscht werden (vielleicht nicht leer oder Default).");
            refreshLists();
        });

        refreshBtn.addActionListener(e -> refreshLists());

        warnBtn.addActionListener(e -> {
            String selected = userList.getSelectedValue();
            if (selected == null) {
                appendLog("Keinen Benutzer ausgewählt.");
                return;
            }
            String username = parseUsername(selected);
            String message = JOptionPane.showInputDialog(this, "Warn-Nachricht an " + username + ":", "Warnung", JOptionPane.PLAIN_MESSAGE);
            if (message != null && !message.trim().isEmpty()) {
                boolean ok = server.warnUser(username, message.trim());
                appendLog(ok ? "Warnung gesendet an " + username : "Warnung fehlgeschlagen für " + username);
            }
        });

        kickBtn.addActionListener(e -> {
            String selected = userList.getSelectedValue();
            if (selected == null) {
                appendLog("Keinen Benutzer ausgewählt.");
                return;
            }
            String username = parseUsername(selected);
            boolean ok = server.kickUser(username);
            appendLog(ok ? "Gekickt: " + username : "Kick fehlgeschlagen: " + username);
            refreshLists();
        });

        banBtn.addActionListener(e -> {
            String selected = userList.getSelectedValue();
            if (selected == null) {
                appendLog("Keinen Benutzer ausgewählt.");
                return;
            }
            String username = parseUsername(selected);
            int ans = JOptionPane.showConfirmDialog(this, "Benutzer wirklich bannen? " + username, "Ban bestätigen", JOptionPane.YES_NO_OPTION);
            if (ans == JOptionPane.YES_OPTION) {
                boolean ok = server.banUser(username);
                appendLog(ok ? "Gebannt: " + username : "Ban fehlgeschlagen: " + username);
                refreshLists();
            }
        });

        stopServerBtn.addActionListener(e -> {
            int ans = JOptionPane.showConfirmDialog(this, "Server stoppen?", "Bestätigung", JOptionPane.YES_NO_OPTION);
            if (ans == JOptionPane.YES_OPTION) {
                appendLog("Server wird gestoppt...");
                new Thread(() -> server.stop()).start();
                refreshTimer.stop();
            }
        });

        // Doppelklick auf Raum -> alle User des Raumes anzeigen (falls verfügbar)
        roomList.addMouseListener(new MouseAdapter() {
            public void mouseClicked(MouseEvent evt) {
                if (evt.getClickCount() == 2) {
                    String room = roomList.getSelectedValue();
                    if (room != null) {
                        appendLog("Räume doppelklick: " + room);
                        // Erweiterung: man könnte hier per Server-API die User des Raums holen und anzeigen.
                    }
                }
            }
        });
    }

    private String parseUsername(String entry) {
        // liste erwartet Format "username" oder "username(room)" - wir extrahieren vor '('
        if (entry.contains("(")) {
            return entry.substring(0, entry.indexOf('('));
        }
        return entry;
    }

    private void appendLog(String s) {
        SwingUtilities.invokeLater(() -> {
            logArea.append(s + "\n");
            logArea.setCaretPosition(logArea.getDocument().getLength());
        });
    }

    private void refreshLists() {
        // Räume
        List<String> rooms = server.getRoomList();
        SwingUtilities.invokeLater(() -> {
            roomListModel.clear();
            for (String r : rooms) roomListModel.addElement(r);
        });

        // Benutzer
        List<String> users = server.getConnectedUsernames();
        SwingUtilities.invokeLater(() -> {
            userListModel.clear();
            for (String u : users) {
                userListModel.addElement(u);
            }
        });
    }

    public static void main(String[] args) {
        Server server = new Server();

        // Server in Hintergrund starten
        new Thread(() -> server.start()).start();

        // GUI starten (EDT)
        SwingUtilities.invokeLater(() -> {
            ServerGUI gui = new ServerGUI(server);
            gui.setVisible(true);
            gui.appendLog("GUI gestartet. Server startet im Hintergrund...");
        });
    }
}
