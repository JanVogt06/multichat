package server;

import java.io.*;
import java.net.ServerSocket;
import java.net.Socket;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;

/**
 * Hauptklasse des Chat-Servers mit Mehrraum-Unterstützung, Admin-Operationen und Dateilogging.
 */
public class Server {

    private static final int PORT = 3143;
    private ServerSocket serverSocket;
    private boolean running;
    private final UserManager userManager;
    private final List<ClientHandler> clients;
    private final Map<String, ChatRoom> rooms;

    public static final String DEFAULT_ROOM = "Lobby";

    // Logging
    private PrintWriter logWriter;
    private final DateTimeFormatter tsFmt = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    public Server() {
        this.running = false;
        this.userManager = new UserManager();
        this.clients = new ArrayList<>();
        this.rooms = new HashMap<>();
        createRoom(DEFAULT_ROOM);
    }

    /**
     * Startet den Server und initialisiert das Logfile.
     */
    public void start() {
        try {
            // Log-Datei öffnen (append)
            logWriter = new PrintWriter(new FileWriter("server.log", true), true);
            log("=== Server-Start: " + LocalDateTime.now().format(tsFmt) + " ===");

            serverSocket = new ServerSocket(PORT);
            running = true;

            log("Chat-Server gestartet");
            log("Port: " + PORT);
            log("Registrierte User: " + userManager.getUserCount());

            while (running) {
                Socket clientSocket = serverSocket.accept();
                ClientHandler handler = new ClientHandler(clientSocket, userManager, this);
                synchronized (clients) {
                    clients.add(handler);
                }
                handler.start();
                log("Neue Verbindung: " + clientSocket.getInetAddress());
            }
        } catch (IOException e) {
            log("Server-Fehler: " + e.getMessage());
        } finally {
            // beim Beenden Log sauber schließen
            if (logWriter != null) {
                log("=== Server-Ende: " + LocalDateTime.now().format(tsFmt) + " ===");
                logWriter.close();
            }
        }
    }

    /**
     * Zentraler Logger: schreibt in Console und in die Logdatei (falls offen).
     */
    public synchronized void log(String message) {
        String ts = LocalDateTime.now().format(tsFmt);
        String line = "[" + ts + "] " + message;
        System.out.println(line);
        if (logWriter != null) {
            logWriter.println(line);
            logWriter.flush();
        }
    }

    /* ---------------- Room-Management ---------------- */

    public synchronized boolean createRoom(String roomName) {
        if (rooms.containsKey(roomName)) return false;
        rooms.put(roomName, new ChatRoom(roomName));
        log("Raum erstellt: " + roomName);
        return true;
    }

    public synchronized boolean deleteRoom(String roomName) {
        if (!rooms.containsKey(roomName)) return false;
        ChatRoom room = rooms.get(roomName);
        if (room.size() > 0) return false;
        if (DEFAULT_ROOM.equals(roomName)) return false;
        rooms.remove(roomName);
        log("Raum gelöscht: " + roomName);
        return true;
    }

    public synchronized boolean joinRoom(String roomName, ClientHandler client) {
        ChatRoom room = rooms.get(roomName);
        if (room == null) return false;
        room.addClient(client);
        log(client.getUsername() + " ist Raum beigetreten: " + roomName);
        return true;
    }

    public synchronized void leaveRoom(String roomName, ClientHandler client) {
        ChatRoom room = rooms.get(roomName);
        if (room == null) return;
        room.removeClient(client);
        log(client.getUsername() + " hat Raum verlassen: " + roomName);
        if (room.size() == 0 && !DEFAULT_ROOM.equals(roomName)) {
            rooms.remove(roomName);
            log("Raum (leer) automatisch gelöscht: " + roomName);
        }
    }

    public synchronized void broadcastToRoom(String message, String roomName, ClientHandler sender) {
        if (roomName == null) {
            log("Global Broadcast: " + message);
            List<ClientHandler> disconnected = new ArrayList<>();
            for (ClientHandler client : clients) {
                if (client == sender) continue;
                if (!client.isReadyForChat()) continue;
                try {
                    client.sendMessage(message);
                } catch (IOException e) {
                    log("Fehler beim Senden an " + client.getUsername() + ": " + e.getMessage());
                    disconnected.add(client);
                }
            }
            clients.removeAll(disconnected);
            return;
        }

        ChatRoom room = rooms.get(roomName);
        if (room == null) return;
        room.broadcast(message, sender);
    }

    public synchronized List<String> getRoomList() {
        return new ArrayList<>(rooms.keySet());
    }

    public synchronized void removeClient(ClientHandler client) {
        clients.remove(client);
        for (ChatRoom r : rooms.values()) {
            r.removeClient(client);
        }
        log("Client entfernt: " + client.getUsername() + " (Gesamt: " + clients.size() + ")");
    }

    public synchronized List<String> getConnectedUsernames() {
        List<String> usernames = new ArrayList<>();
        for (ClientHandler client : clients) {
            String username = client.getUsername();
            if (username != null && client.isReadyForChat()) {
                usernames.add(username);
            }
        }
        return usernames;
    }

    /* ----------------- Admin-Operationen ----------------- */

    public synchronized ClientHandler findClientByUsername(String username) {
        for (ClientHandler c : clients) {
            if (username.equals(c.getUsername())) return c;
        }
        return null;
    }

    public synchronized boolean warnUser(String username, String message) {
        ClientHandler c = findClientByUsername(username);
        if (c == null) return false;
        try {
            c.sendMessage("WARN:" + message);
            log("Warn an " + username + ": " + message);
            return true;
        } catch (IOException e) {
            log("Fehler beim Warnen von " + username + ": " + e.getMessage());
            return false;
        }
    }

    public synchronized boolean kickUser(String username) {
        ClientHandler c = findClientByUsername(username);
        if (c == null) return false;
        try {
            c.sendMessage("KICK:Du wurdest vom Server getrennt");
        } catch (IOException ignored) { }
        // verlaessliche Methode: requestClose() rufen
        c.requestClose();
        log("Gekickt: " + username);
        return true;
    }

    public synchronized boolean banUser(String username) {
        if (!userManager.userExists(username)) return false;
        userManager.banUser(username);
        ClientHandler c = findClientByUsername(username);
        if (c != null) {
            kickUser(username);
        }
        log("Gebannt: " + username);
        return true;
    }

    public void stop() {
        try {
            running = false;
            // Alle Clients schließen
            List<ClientHandler> copy;
            synchronized (clients) {
                copy = new ArrayList<>(clients);
            }
            for (ClientHandler ch : copy) {
                ch.requestClose();
            }
            if (serverSocket != null) serverSocket.close();
            log("Server gestoppt");
        } catch (IOException e) {
            log("Fehler beim Stoppen: " + e.getMessage());
        } finally {
            if (logWriter != null) {
                log("=== Server-Stopped ===");
                logWriter.close();
            }
        }
    }

    public UserManager getUserManager() {
        return userManager;
    }

    /**
     * Hilfsmethode: liefert alle ClientHandler (für z.B. Broadcast-Updates).
     */
    public synchronized List<ClientHandler> getAllClientHandlers() {
        return new ArrayList<>(clients);
    }

    public static void main(String[] args) {
        Server server = new Server();
        server.start();
    }
}
