package server;

import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;

/**
 * Repräsentiert einen Chat-Raum.
 * Verwaltet die Mitglieder des Raums und ermöglicht das Senden
 * von Nachrichten an alle Mitglieder.
 */
public class Room {

    // Maximale Anzahl gespeicherter Nachrichten
    private static final int MAX_HISTORY_SIZE = 50;

    // Name des Raums
    private final String name;

    // Liste der Mitglieder (ClientHandler)
    private final List<ClientHandler> members;

    // Ersteller des Raums
    private final String createdBy;

    // Chat-Historie (letzte Nachrichten)
    private final LinkedList<String> chatHistory;


    /**
     * Konstruktor für einen neuen Raum.
     *
     * @param name Name des Raums
     * @param createdBy Benutzername des Erstellers
     */
    public Room(String name, String createdBy) {
        this.name = name;
        this.createdBy = createdBy;
        this.members = new ArrayList<>();
        this.chatHistory = new LinkedList<>();
    }


    /**
     * Gibt den Namen des Raums zurück.
     *
     * @return Raumname
     */
    public String getName() {
        return name;
    }


    /**
     * Gibt den Ersteller des Raums zurück.
     *
     * @return Benutzername des Erstellers
     */
    public String getCreatedBy() {
        return createdBy;
    }


    /**
     * Fügt einen Client zum Raum hinzu.
     *
     * @param client Der hinzuzufügende Client
     */
    public synchronized void addMember(ClientHandler client) {
        if (!members.contains(client)) {
            members.add(client);
        }
    }


    /**
     * Entfernt einen Client aus dem Raum.
     *
     * @param client Der zu entfernende Client
     */
    public synchronized void removeMember(ClientHandler client) {
        members.remove(client);
    }


    /**
     * Prüft ob ein Client Mitglied des Raums ist.
     *
     * @param client Der zu prüfende Client
     * @return true wenn Mitglied
     */
    public synchronized boolean hasMember(ClientHandler client) {
        return members.contains(client);
    }


    /**
     * Gibt die Anzahl der Mitglieder zurück.
     *
     * @return Anzahl der Mitglieder
     */
    public synchronized int getMemberCount() {
        return members.size();
    }


    /**
     * Prüft ob der Raum leer ist.
     *
     * @return true wenn keine Mitglieder
     */
    public synchronized boolean isEmpty() {
        return members.isEmpty();
    }


    /**
     * Gibt die Liste der Mitglieder-Namen zurück.
     *
     * @return Liste mit Benutzernamen
     */
    public synchronized List<String> getMemberNames() {
        List<String> names = new ArrayList<>();
        for (ClientHandler client : members) {
            String username = client.getUsername();
            if (username != null) {
                names.add(username);
            }
        }
        return names;
    }


    /**
     * Fügt eine Nachricht zur Chat-Historie hinzu.
     *
     * @param message Die Nachricht
     */
    public synchronized void addToHistory(String message) {
        chatHistory.addLast(message);

        // Älteste Nachrichten entfernen wenn Limit erreicht
        while (chatHistory.size() > MAX_HISTORY_SIZE) {
            chatHistory.removeFirst();
        }
    }


    /**
     * Gibt die Chat-Historie zurück.
     *
     * @return Liste der letzten Nachrichten
     */
    public synchronized List<String> getChatHistory() {
        return new ArrayList<>(chatHistory);
    }


    /**
     * Sendet die Chat-Historie an einen Client.
     *
     * @param client Der Client
     */
    public synchronized void sendHistoryTo(ClientHandler client) {
        if (chatHistory.isEmpty()) {
            return;
        }

        try {
            // Markierung für Historie-Beginn
            client.sendMessage("=== Letzte Nachrichten ===");

            // Alle Nachrichten aus der Historie senden
            for (String message : chatHistory) {
                client.sendMessage(message);
            }

            // Markierung für Historie-Ende
            client.sendMessage("=== Ende der Historie ===");
        } catch (IOException e) {
            // Client nicht erreichbar - ignorieren
        }
    }


    /**
     * Sendet eine Nachricht an alle Mitglieder des Raums.
     * Speichert die Nachricht auch in der Historie.
     *
     * @param message Die Nachricht
     * @param sender Der Sender (wird ausgeschlossen), kann null sein
     */
    public synchronized void broadcast(String message, ClientHandler sender) {
        // Nachricht zur Historie hinzufügen (nur Chat-Nachrichten, keine System-Nachrichten)
        if (message.startsWith("[") && !message.startsWith(">>>") && !message.startsWith("<<<")) {
            addToHistory(message);
        }

        List<ClientHandler> disconnected = new ArrayList<>();

        for (ClientHandler client : members) {
            // Nicht an Sender zurückschicken
            if (client == sender) continue;

            try {
                client.sendMessage(message);
            } catch (IOException e) {
                disconnected.add(client);
            }
        }

        // Getrennte Clients entfernen
        members.removeAll(disconnected);
    }


    /**
     * Sendet eine Nachricht an ALLE Mitglieder (inkl. Sender).
     *
     * @param message Die Nachricht
     */
    public synchronized void broadcastToAll(String message) {
        List<ClientHandler> disconnected = new ArrayList<>();

        for (ClientHandler client : members) {
            try {
                client.sendMessage(message);
            } catch (IOException e) {
                disconnected.add(client);
            }
        }

        // Getrennte Clients entfernen
        members.removeAll(disconnected);
    }


    @Override
    public String toString() {
        return "Room{name='" + name + "', members=" + getMemberCount() + "}";
    }
}