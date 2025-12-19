package server;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * Repräsentiert einen Chat-Raum und verwaltet die darin befindlichen Clients.
 */
public class ChatRoom {
    private final String name;
    private final List<ClientHandler> clients;

    public ChatRoom(String name) {
        this.name = name;
        this.clients = new ArrayList<>();
    }

    public String getName() {
        return name;
    }

    /**
     * Fügt einen Client zum Raum hinzu.
     */
    public synchronized void addClient(ClientHandler client) {
        if (!clients.contains(client)) {
            clients.add(client);
        }
    }

    /**
     * Entfernt einen Client aus dem Raum.
     */
    public synchronized void removeClient(ClientHandler client) {
        clients.remove(client);
    }

    /**
     * Gibt die aktuelle Anzahl an Clients im Raum zurück.
     */
    public synchronized int size() {
        return clients.size();
    }

    /**
     * Broadcastet eine Nachricht an alle Clients in diesem Raum (außer evtl. sender).
     */
    public synchronized void broadcast(String message, ClientHandler sender) {
        List<ClientHandler> disconnected = new ArrayList<>();
        for (ClientHandler c : clients) {
            if (c == sender) continue;
            if (!c.isReadyForChat()) continue;
            try {
                c.sendMessage(message);
            } catch (IOException e) {
                System.err.println("Fehler beim Senden an " + c.getUsername() + ": " + e.getMessage());
                disconnected.add(c);
            }
        }
        // entferne getrennte Clients
        clients.removeAll(disconnected);
    }

    /**
     * Liefert eine Liste der Usernamen im Raum.
     */
    public synchronized List<String> getUsernames() {
        List<String> names = new ArrayList<>();
        for (ClientHandler c : clients) {
            if (c.getUsername() != null && c.isReadyForChat()) {
                names.add(c.getUsername());
            }
        }
        return names;
    }
}
