package server;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Verwaltet alle Chat-Räume auf dem Server.
 * Ermöglicht das Erstellen, Löschen und Finden von Räumen.
 */
public class RoomManager {

    // Map: Raumname -> Room-Objekt
    private final Map<String, Room> rooms;

    // Referenz zum Server für Benachrichtigungen
    private final Server server;


    /**
     * Konstruktor für den RoomManager.
     *
     * @param server Referenz zum Server
     */
    public RoomManager(Server server) {
        this.rooms = new HashMap<>();
        this.server = server;
    }


    /**
     * Erstellt einen neuen Raum.
     *
     * @param name Name des Raums
     * @param creator Der Client, der den Raum erstellt
     * @return true wenn erfolgreich, false wenn Name schon existiert
     */
    public synchronized boolean createRoom(String name, ClientHandler creator) {
        // Prüfen ob Name schon existiert
        if (rooms.containsKey(name)) {
            return false;
        }

        // Prüfen ob Name gültig ist
        if (name == null || name.trim().isEmpty()) {
            return false;
        }

        // Raum erstellen
        Room room = new Room(name, creator.getUsername());
        rooms.put(name, room);

        server.log("Raum erstellt: " + name + " (von " + creator.getUsername() + ")");

        // GUI benachrichtigen
        server.notifyRoomCreated(name);

        return true;
    }


    /**
     * Löscht einen Raum.
     *
     * @param name Name des Raums
     * @return true wenn erfolgreich gelöscht
     */
    public synchronized boolean deleteRoom(String name) {
        Room room = rooms.remove(name);

        if (room != null) {
            server.log("Raum gelöscht: " + name);

            // GUI benachrichtigen
            server.notifyRoomDeleted(name);

            return true;
        }

        return false;
    }


    /**
     * Findet einen Raum anhand des Namens.
     *
     * @param name Name des Raums
     * @return Room-Objekt oder null wenn nicht gefunden
     */
    public synchronized Room getRoom(String name) {
        return rooms.get(name);
    }


    /**
     * Prüft ob ein Raum existiert.
     *
     * @param name Name des Raums
     * @return true wenn Raum existiert
     */
    public synchronized boolean roomExists(String name) {
        return rooms.containsKey(name);
    }


    /**
     * Gibt eine Liste aller Raumnamen zurück.
     *
     * @return Liste der Raumnamen
     */
    public synchronized List<String> getRoomNames() {
        return new ArrayList<>(rooms.keySet());
    }


    /**
     * Gibt die Anzahl der Räume zurück.
     *
     * @return Anzahl der Räume
     */
    public synchronized int getRoomCount() {
        return rooms.size();
    }


    /**
     * Lässt einen Client einem Raum beitreten.
     *
     * @param roomName Name des Raums
     * @param client Der Client
     * @return true wenn erfolgreich
     */
    public synchronized boolean joinRoom(String roomName, ClientHandler client) {
        Room room = rooms.get(roomName);

        if (room == null) {
            return false;
        }

        // Client zum Raum hinzufügen
        room.addMember(client);

        server.log(client.getUsername() + " ist Raum '" + roomName + "' beigetreten");

        return true;
    }


    /**
     * Entfernt einen Client aus einem Raum.
     * Löscht den Raum wenn er danach leer ist.
     *
     * @param roomName Name des Raums
     * @param client Der Client
     * @return true wenn Raum danach gelöscht wurde
     */
    public synchronized boolean leaveRoom(String roomName, ClientHandler client) {
        Room room = rooms.get(roomName);

        if (room == null) {
            return false;
        }

        // Client aus Raum entfernen
        room.removeMember(client);

        server.log(client.getUsername() + " hat Raum '" + roomName + "' verlassen");

        // Raum löschen wenn leer
        if (room.isEmpty()) {
            deleteRoom(roomName);
            return true; // Raum wurde gelöscht
        }

        return false; // Raum existiert noch
    }


    /**
     * Entfernt einen Client aus allen Räumen.
     * Wird aufgerufen wenn ein Client die Verbindung trennt.
     *
     * @param client Der Client
     */
    public synchronized void removeClientFromAllRooms(ClientHandler client) {
        List<String> emptyRooms = new ArrayList<>();

        for (Map.Entry<String, Room> entry : rooms.entrySet()) {
            Room room = entry.getValue();

            if (room.hasMember(client)) {
                room.removeMember(client);

                // Andere Mitglieder benachrichtigen
                room.broadcastToAll("<<< " + client.getUsername() + " hat den Raum verlassen");

                // Merken wenn Raum leer ist
                if (room.isEmpty()) {
                    emptyRooms.add(entry.getKey());
                }
            }
        }

        // Leere Räume löschen
        for (String roomName : emptyRooms) {
            deleteRoom(roomName);
        }
    }


    /**
     * Gibt die Mitgliederliste eines Raums als String zurück.
     *
     * @param roomName Name des Raums
     * @return Komma-separierte Liste der Mitglieder
     */
    public synchronized String getMemberListString(String roomName) {
        Room room = rooms.get(roomName);

        if (room == null) {
            return "";
        }

        return String.join(",", room.getMemberNames());
    }


    /**
     * Gibt die Raumliste als String zurück.
     *
     * @return Komma-separierte Liste der Räume
     */
    public synchronized String getRoomListString() {
        return String.join(",", rooms.keySet());
    }
}