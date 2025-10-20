package server;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.ArrayList;
import java.util.List;

public class Server {

    private static final int PORT = 3143;
    private ServerSocket serverSocket;
    private boolean running;
    private final UserManager userManager;
    private final List<ClientHandler> clients;


    public Server() {
        this.running = false;
        this.userManager = new UserManager();
        this.clients = new ArrayList<>();
    }


    public void start() {
        try {
            serverSocket = new ServerSocket(PORT);
            running = true;

            System.out.println("=".repeat(50));
            System.out.println("✓ Chat-Server gestartet");
            System.out.println("✓ Port: " + PORT);
            System.out.println("✓ Registrierte User: " + userManager.getUserCount());
            System.out.println("=".repeat(50) + "\n");

            while (running) {
                Socket clientSocket = serverSocket.accept();

                // Neuen ClientHandler-Thread starten
                ClientHandler handler = new ClientHandler(clientSocket, userManager, this);
                clients.add(handler);
                handler.start();
            }

        } catch (IOException e) {
            System.err.println("✗ Server-Fehler: " + e.getMessage());
        }
    }


    /**
     * Sendet eine Nachricht an alle angemeldeten Clients (außer Sender)
     * Nur an Clients, die im Chat-Modus sind!
     */
    public synchronized void broadcast(String message, ClientHandler sender) {
        System.out.println("📢 Broadcast: " + message);

        List<ClientHandler> disconnectedClients = new ArrayList<>();

        for (ClientHandler client : clients) {
            // Nachricht NICHT an Sender zurückschicken
            if (client == sender) continue;

            // Nur an Clients senden, die bereit für Chat sind
            if (!client.isReadyForChat()) continue;

            try {
                client.sendMessage(message);
            } catch (IOException e) {
                System.err.println("✗ Fehler beim Senden an " + client.getUsername() + ": " + e.getMessage());
                disconnectedClients.add(client);
            }
        }

        // Entferne disconnected Clients
        clients.removeAll(disconnectedClients);
    }


    /**
     * Entfernt einen Client aus der Liste
     */
    public synchronized void removeClient(ClientHandler client) {
        clients.remove(client);
        System.out.println("✓ Client entfernt: " + client.getUsername() + " (Gesamt: " + clients.size() + ")");
    }


    /**
     * Gibt die Namen aller angemeldeten User zurück (die im Chat-Modus sind)
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
     * Gibt die Anzahl der verbundenen Clients zurück
     */
    public synchronized int getClientCount() {
        return clients.size();
    }


    public void stop() {
        try {
            running = false;
            if (serverSocket != null) {
                serverSocket.close();
            }
            System.out.println("✓ Server gestoppt");
        } catch (IOException e) {
            System.err.println("✗ Fehler beim Stoppen: " + e.getMessage());
        }
    }


    public static void main(String[] args) {
        Server server = new Server();
        server.start();
    }
}