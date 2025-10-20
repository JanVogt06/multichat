package server;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
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

    // ServerSocket für eingehende Verbindungen
    private ServerSocket serverSocket;

    // Flag für Server-Status
    private boolean running;

    // Verwaltung der Benutzerkonten
    private final UserManager userManager;

    // Liste aller verbundenen Clients
    private final List<ClientHandler> clients;


    /**
     * Konstruktor für den Server.
     * Initialisiert UserManager und Client-Liste.
     */
    public Server() {
        this.running = false;
        this.userManager = new UserManager();
        this.clients = new ArrayList<>();
    }


    /**
     * Startet den Server und wartet auf eingehende Client-Verbindungen.
     * Für jeden neuen Client wird ein eigener ClientHandler-Thread erstellt.
     */
    public void start() {
        try {
            // ServerSocket auf dem definierten Port öffnen
            serverSocket = new ServerSocket(PORT);
            running = true;

            // Startmeldung ausgeben
            System.out.println("=".repeat(50));
            System.out.println("Chat-Server gestartet");
            System.out.println("Port: " + PORT);
            System.out.println("Registrierte User: " + userManager.getUserCount());
            System.out.println("=".repeat(50) + "\n");

            // Endlosschleife: Warte auf neue Client-Verbindungen
            while (running) {
                // Blockiert bis ein Client sich verbindet
                Socket clientSocket = serverSocket.accept();

                // Neuen ClientHandler für diesen Client erstellen
                ClientHandler handler = new ClientHandler(clientSocket, userManager, this);

                // Handler zur Liste hinzufügen
                clients.add(handler);

                // Handler-Thread starten
                handler.start();
            }

        } catch (IOException e) {
            System.err.println("Server-Fehler: " + e.getMessage());
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
        System.out.println("Broadcast: " + message);

        // Liste für Clients, bei denen die Verbindung fehlgeschlagen ist
        List<ClientHandler> disconnectedClients = new ArrayList<>();

        // Durchlaufe alle verbundenen Clients
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
                System.err.println("Fehler beim Senden an " + client.getUsername() + ": " + e.getMessage());
                disconnectedClients.add(client);
            }
        }

        // Getrennte Clients aus der Liste entfernen
        clients.removeAll(disconnectedClients);
    }


    /**
     * Entfernt einen Client aus der Liste der verbundenen Clients.
     *
     * @param client Der zu entfernende ClientHandler
     */
    public synchronized void removeClient(ClientHandler client) {
        clients.remove(client);
        System.out.println("Client entfernt: " + client.getUsername() + " (Gesamt: " + clients.size() + ")");
    }


    /**
     * Gibt die Namen aller angemeldeten Benutzer zurück.
     * Nur Clients, die im Chat-Modus sind, werden berücksichtigt.
     *
     * @return Liste mit allen Benutzernamen
     */
    public synchronized List<String> getConnectedUsernames() {
        List<String> usernames = new ArrayList<>();

        for (ClientHandler client : clients) {
            String username = client.getUsername();

            // Nur authentifizierte Clients, die im Chat-Modus sind
            if (username != null && client.isReadyForChat()) {
                usernames.add(username);
            }
        }

        return usernames;
    }


    /**
     * Gibt die Anzahl der verbundenen Clients zurück.
     *
     * @return Anzahl der Clients
     */
    public synchronized int getClientCount() {
        return clients.size();
    }


    /**
     * Stoppt den Server und schließt den ServerSocket.
     */
    public void stop() {
        try {
            running = false;
            if (serverSocket != null) {
                serverSocket.close();
            }
            System.out.println("Server gestoppt");
        } catch (IOException e) {
            System.err.println("Fehler beim Stoppen: " + e.getMessage());
        }
    }


    /**
     * Main-Methode zum Starten des Servers.
     *
     * @param args Kommandozeilenargumente (nicht verwendet)
     */
    public static void main(String[] args) {
        Server server = new Server();
        server.start();
    }
}