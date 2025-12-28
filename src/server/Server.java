package server;

import java.io.*;
import java.net.ServerSocket;
import java.net.Socket;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

/**
 * Hauptklasse des Chat-Servers.
 * Verwaltet alle Client-Verbindungen und koordiniert die Kommunikation
 * zwischen den Clients.
 */
public class Server {

    // Port auf dem der Server lauscht
    private static final int PORT = 3143;

    // Pfad zur Log-Datei
    private static final String LOG_FILE = "server.log";

    // ServerSocket für eingehende Verbindungen
    private ServerSocket serverSocket;

    // Flag für Server-Status
    private volatile boolean running;

    // Verwaltung der Benutzerkonten
    private final UserManager userManager;

    // Liste aller verbundenen Clients
    private final List<ClientHandler> clients;

    // Referenz zur GUI (kann null sein für Konsolen-Betrieb)
    private ServerGUI gui;

    // Verwaltung der Räume
    private RoomManager roomManager;

    // Writer für die Log-Datei
    private PrintWriter logWriter;

    // Formatter für Zeitstempel im Log
    private final DateTimeFormatter timeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");


    /**
     * Konstruktor für den Server.
     * Initialisiert UserManager und Client-Liste.
     */
    public Server() {
        this.running = false;
        this.userManager = new UserManager();
        this.clients = new ArrayList<>();
        this.gui = null;
        this.roomManager = new RoomManager(this);
    }


    /**
     * Konstruktor mit GUI-Referenz.
     *
     * @param gui Die ServerGUI-Instanz
     */
    public Server(ServerGUI gui) {
        this();
        this.gui = gui;
    }


    /**
     * Setzt die GUI-Referenz.
     *
     * @param gui Die ServerGUI-Instanz
     */
    public void setGUI(ServerGUI gui) {
        this.gui = gui;
    }


    /**
     * Initialisiert die Log-Datei.
     * Erstellt eine neue Datei oder hängt an bestehende an.
     */
    private void initLogFile() {
        try {
            // FileWriter mit append=true um an bestehende Datei anzuhängen
            logWriter = new PrintWriter(new FileWriter(LOG_FILE, true), true);
            logWriter.println();
            logWriter.println("=".repeat(60));
            logWriter.println("Server-Session gestartet: " + LocalDateTime.now().format(timeFormatter));
            logWriter.println("=".repeat(60));
        } catch (IOException e) {
            System.err.println("Fehler beim Erstellen der Log-Datei: " + e.getMessage());
        }
    }


    /**
     * Schließt die Log-Datei.
     */
    private void closeLogFile() {
        if (logWriter != null) {
            logWriter.println("=".repeat(60));
            logWriter.println("Server-Session beendet: " + LocalDateTime.now().format(timeFormatter));
            logWriter.println("=".repeat(60));
            logWriter.close();
            logWriter = null;
        }
    }


    /**
     * Gibt eine Nachricht aus - an GUI, Konsole und Log-Datei.
     *
     * @param message Die Nachricht
     */
    public void log(String message) {
        // Zeitstempel erstellen
        String timestamp = LocalDateTime.now().format(timeFormatter);
        String logMessage = "[" + timestamp + "] " + message;

        // An GUI ausgeben (ohne Zeitstempel, da übersichtlicher)
        if (gui != null) {
            gui.log(message);
        } else {
            // Konsolen-Ausgabe mit Zeitstempel
            System.out.println(logMessage);
        }

        // In Log-Datei schreiben
        if (logWriter != null) {
            logWriter.println(logMessage);
        }
    }


    /**
     * Informiert die GUI über einen neuen Nutzer.
     *
     * @param username Der Nutzername
     */
    public void notifyUserJoined(String username) {
        if (gui != null) {
            gui.addUser(username);
        }
    }


    /**
     * Informiert die GUI über einen abgemeldeten Nutzer.
     *
     * @param username Der Nutzername
     */
    public void notifyUserLeft(String username) {
        if (gui != null) {
            gui.removeUser(username);
        }
    }


    /**
     * Informiert die GUI über einen neuen Raum.
     *
     * @param roomName Der Raumname
     */
    public void notifyRoomCreated(String roomName) {
        if (gui != null) {
            gui.addRoom(roomName);
        }
    }


    /**
     * Informiert die GUI über einen gelöschten Raum.
     *
     * @param roomName Der Raumname
     */
    public void notifyRoomDeleted(String roomName) {
        if (gui != null) {
            gui.removeRoom(roomName);
        }
    }


    /**
     * Gibt den RoomManager zurück.
     *
     * @return Der RoomManager
     */
    public RoomManager getRoomManager() {
        return roomManager;
    }


    /**
     * Startet den Server und wartet auf eingehende Client-Verbindungen.
     * Für jeden neuen Client wird ein eigener ClientHandler-Thread erstellt.
     */
    public void start() {
        try {
            // Log-Datei initialisieren
            initLogFile();

            // ServerSocket auf dem definierten Port öffnen
            serverSocket = new ServerSocket(PORT);
            running = true;

            // Startmeldung ausgeben
            log("=".repeat(50));
            log("Chat-Server gestartet");
            log("Port: " + PORT);
            log("Log-Datei: " + LOG_FILE);
            log("Registrierte User: " + userManager.getUserCount());
            log("=".repeat(50));

            // Endlosschleife: Warte auf neue Client-Verbindungen
            while (running) {
                try {
                    // Blockiert bis ein Client sich verbindet
                    Socket clientSocket = serverSocket.accept();

                    // Neuen ClientHandler für diesen Client erstellen
                    ClientHandler handler = new ClientHandler(clientSocket, userManager, this);

                    // Handler zur Liste hinzufügen
                    synchronized (clients) {
                        clients.add(handler);
                    }

                    // Handler-Thread starten
                    handler.start();

                    log("Neuer Client verbunden: " + clientSocket.getInetAddress());

                } catch (IOException e) {
                    // Wenn Server gestoppt wurde, ist das normal
                    if (running) {
                        log("Fehler beim Akzeptieren: " + e.getMessage());
                    }
                }
            }

        } catch (IOException e) {
            log("Server-Fehler: " + e.getMessage());
        }
    }


    /**
     * Sendet eine Nachricht an alle angemeldeten Clients (außer dem Sender).
     * Nur Clients, die im Chat-Modus sind, erhalten die Nachricht.
     *
     * @param message Die zu sendende Nachricht
     * @param sender Der ClientHandler, der die Nachricht gesendet hat (wird ausgeschlossen)
     */
    public synchronized void broadcast(String message, ClientHandler sender) {
        log("Broadcast: " + message);

        // Liste für Clients, bei denen die Verbindung fehlgeschlagen ist
        List<ClientHandler> disconnectedClients = new ArrayList<>();

        // Durchlaufe alle verbundenen Clients
        synchronized (clients) {
            for (ClientHandler client : clients) {
                // Nachricht nicht an den Sender zurückschicken
                if (client == sender) continue;

                // Nur an Clients senden, die bereit für Chat sind
                if (!client.isReadyForChat()) continue;

                try {
                    // Nachricht an Client senden
                    client.sendMessage(message);
                } catch (IOException e) {
                    // Verbindung fehlgeschlagen, Client zur Löschliste hinzufügen
                    log("Fehler beim Senden an " + client.getUsername() + ": " + e.getMessage());
                    disconnectedClients.add(client);
                }
            }

            // Getrennte Clients aus der Liste entfernen
            clients.removeAll(disconnectedClients);
        }
    }


    /**
     * Sendet eine Nachricht an ALLE verbundenen Clients (inkl. Sender).
     * Wird verwendet für System-Nachrichten wie Raumlisten-Updates.
     *
     * @param message Die zu sendende Nachricht
     */
    public synchronized void broadcastToAll(String message) {
        // Liste für Clients, bei denen die Verbindung fehlgeschlagen ist
        List<ClientHandler> disconnectedClients = new ArrayList<>();

        // Durchlaufe alle verbundenen Clients
        synchronized (clients) {
            for (ClientHandler client : clients) {
                // Nur an Clients senden, die bereit für Chat sind
                if (!client.isReadyForChat()) continue;

                try {
                    // Nachricht an Client senden
                    client.sendMessage(message);
                } catch (IOException e) {
                    // Verbindung fehlgeschlagen, Client zur Löschliste hinzufügen
                    log("Fehler beim Senden an " + client.getUsername() + ": " + e.getMessage());
                    disconnectedClients.add(client);
                }
            }

            // Getrennte Clients aus der Liste entfernen
            clients.removeAll(disconnectedClients);
        }
    }


    /**
     * Entfernt einen Client aus der Liste der verbundenen Clients.
     *
     * @param client Der zu entfernende ClientHandler
     */
    public void removeClient(ClientHandler client) {
        // Client aus allen Räumen entfernen
        roomManager.removeClientFromAllRooms(client);

        synchronized (clients) {
            clients.remove(client);
        }
        String username = client.getUsername();
        log("Client entfernt: " + username + " (Gesamt: " + clients.size() + ")");

        // GUI informieren
        if (username != null) {
            notifyUserLeft(username);
        }
    }


    /**
     * Entfernt einen Client anhand des Benutzernamens (für GUI "Nutzer entfernen").
     *
     * @param username Der Benutzername
     * @return true wenn erfolgreich, sonst false
     */
    public boolean removeClientByUsername(String username) {
        synchronized (clients) {
            for (ClientHandler client : clients) {
                if (username.equals(client.getUsername())) {
                    client.disconnect("Du wurdest vom Server entfernt.");
                    return true;
                }
            }
        }
        return false;
    }


    /**
     * Gibt die Namen aller angemeldeten Benutzer zurück.
     * Nur Clients, die im Chat-Modus sind, werden berücksichtigt.
     *
     * @return Liste mit allen Benutzernamen
     */
    public synchronized List<String> getConnectedUsernames() {
        List<String> usernames = new ArrayList<>();

        synchronized (clients) {
            for (ClientHandler client : clients) {
                String username = client.getUsername();

                // Nur authentifizierte Clients, die im Chat-Modus sind
                if (username != null && client.isReadyForChat()) {
                    usernames.add(username);
                }
            }
        }

        return usernames;
    }


    /**
     * Gibt die Anzahl der verbundenen Clients zurück.
     *
     * @return Anzahl der Clients
     */
    public int getClientCount() {
        synchronized (clients) {
            return clients.size();
        }
    }


    /**
     * Prüft ob der Server läuft.
     *
     * @return true wenn Server läuft
     */
    public boolean isRunning() {
        return running;
    }


    /**
     * Stoppt den Server und schließt den ServerSocket.
     */
    public void stop() {
        try {
            running = false;

            // Alle Clients trennen
            synchronized (clients) {
                for (ClientHandler client : clients) {
                    try {
                        client.disconnect("Server wird beendet.");
                    } catch (Exception e) {
                        // Ignorieren beim Herunterfahren
                    }
                }
                clients.clear();
            }

            // ServerSocket schließen
            if (serverSocket != null && !serverSocket.isClosed()) {
                serverSocket.close();
            }

            log("Server gestoppt");

            // Log-Datei schließen
            closeLogFile();

            // GUI leeren
            if (gui != null) {
                gui.clearAll();
            }

        } catch (IOException e) {
            log("Fehler beim Stoppen: " + e.getMessage());
        }
    }


    /**
     * Main-Methode zum Starten des Servers (Konsolen-Modus).
     *
     * @param args Kommandozeilenargumente (nicht verwendet)
     */
    public static void main(String[] args) {
        Server server = new Server();
        server.start();
    }
}