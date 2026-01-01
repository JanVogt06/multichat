package server;

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Verwaltet alle Chat-Räume auf dem Server.
 *
 * Für jeden Raum wird ein Ordner erstellt, in dem Dateien gespeichert werden.
 * Wenn ein Raum gelöscht wird, wird auch sein Ordner gelöscht.
 */
public class RoomManager {

    // Hier werden alle Raum-Ordner gespeichert
    private static final String ROOMS_DIRECTORY = "room_files";

    // Alle Räume: Name -> Room-Objekt
    private final Map<String, Room> rooms;

    // Referenz zum Server
    private final Server server;


    public RoomManager(Server server) {
        this.rooms = new HashMap<>();
        this.server = server;

        // Basis-Ordner erstellen falls nicht vorhanden
        File baseDir = new File(ROOMS_DIRECTORY);
        if (!baseDir.exists()) {
            baseDir.mkdirs();
        }
    }


    /**
     * Erstellt einen neuen Raum mit eigenem Datei-Ordner.
     */
    public synchronized boolean createRoom(String name, ClientHandler creator) {
        // Name schon vergeben?
        if (rooms.containsKey(name)) {
            return false;
        }

        // Name gültig?
        if (name == null || name.trim().isEmpty()) {
            return false;
        }

        // Keine Sonderzeichen die Probleme im Dateisystem machen
        if (name.contains("/") || name.contains("\\") || name.contains("..")) {
            return false;
        }

        // Raum erstellen
        Room room = new Room(name, creator.getUsername());
        rooms.put(name, room);

        // Ordner für Dateien erstellen: room_files/Raumname/
        File roomDir = new File(ROOMS_DIRECTORY, name);
        if (!roomDir.exists()) {
            roomDir.mkdirs();
            server.log("Ordner erstellt: " + roomDir.getPath());
        }

        server.log("Raum erstellt: " + name + " (von " + creator.getUsername() + ")");
        server.notifyRoomCreated(name);

        return true;
    }


    /**
     * Löscht einen Raum und seinen Datei-Ordner.
     */
    public synchronized boolean deleteRoom(String name) {
        Room room = rooms.remove(name);

        if (room != null) {
            // Ordner löschen (mit allen Dateien drin)
            File roomDir = new File(ROOMS_DIRECTORY, name);
            if (roomDir.exists()) {
                deleteDirectoryRecursively(roomDir);
                server.log("Ordner gelöscht: " + roomDir.getPath());
            }

            server.log("Raum gelöscht: " + name);
            server.notifyRoomDeleted(name);
            return true;
        }

        return false;
    }


    /**
     * Löscht einen Ordner samt Inhalt.
     *
     * Geht rekursiv durch: Erst alle Dateien/Unterordner löschen,
     * dann den Ordner selbst.
     */
    private void deleteDirectoryRecursively(File directory) {
        if (directory.isDirectory()) {
            File[] files = directory.listFiles();
            if (files != null) {
                for (File file : files) {
                    deleteDirectoryRecursively(file);
                }
            }
        }
        directory.delete();
    }


    /**
     * Gibt den Datei-Ordner eines Raums zurück.
     */
    public synchronized File getRoomDirectory(String roomName) {
        if (!rooms.containsKey(roomName)) {
            return null;
        }
        return new File(ROOMS_DIRECTORY, roomName);
    }


    /**
     * Listet alle Dateien in einem Raum auf.
     */
    public synchronized List<String> getFilesInRoom(String roomName) {
        List<String> fileNames = new ArrayList<>();

        File roomDir = getRoomDirectory(roomName);
        if (roomDir != null && roomDir.exists()) {
            File[] files = roomDir.listFiles();
            if (files != null) {
                for (File file : files) {
                    if (file.isFile()) {
                        fileNames.add(file.getName());
                    }
                }
            }
        }

        return fileNames;
    }


    /**
     * Findet einen Raum anhand des Namens.
     */
    public synchronized Room getRoom(String name) {
        return rooms.get(name);
    }


    /**
     * Prüft ob ein Raum existiert.
     */
    public synchronized boolean roomExists(String name) {
        return rooms.containsKey(name);
    }


    /**
     * Gibt alle Raumnamen zurück.
     */
    public synchronized List<String> getRoomNames() {
        return new ArrayList<>(rooms.keySet());
    }


    /**
     * Gibt die Anzahl der Räume zurück.
     */
    public synchronized int getRoomCount() {
        return rooms.size();
    }


    /**
     * Lässt einen Client einem Raum beitreten.
     */
    public synchronized boolean joinRoom(String roomName, ClientHandler client) {
        Room room = rooms.get(roomName);

        if (room == null) {
            return false;
        }

        room.addMember(client);
        server.log(client.getUsername() + " ist Raum '" + roomName + "' beigetreten");

        return true;
    }


    /**
     * Entfernt einen Client aus einem Raum.
     * Löscht den Raum wenn er danach leer ist.
     */
    public synchronized boolean leaveRoom(String roomName, ClientHandler client) {
        Room room = rooms.get(roomName);

        if (room == null) {
            return false;
        }

        room.removeMember(client);
        server.log(client.getUsername() + " hat Raum '" + roomName + "' verlassen");

        // Leerer Raum wird gelöscht
        if (room.isEmpty()) {
            deleteRoom(roomName);
            return true;
        }

        return false;
    }


    /**
     * Entfernt einen Client aus allen Räumen (bei Disconnect).
     */
    public synchronized void removeClientFromAllRooms(ClientHandler client) {
        List<String> emptyRooms = new ArrayList<>();

        for (Map.Entry<String, Room> entry : rooms.entrySet()) {
            Room room = entry.getValue();

            if (room.hasMember(client)) {
                room.removeMember(client);
                room.broadcastToAll("<<< " + client.getUsername() + " hat den Raum verlassen");

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
     * Gibt die Mitglieder eines Raums als komma-separierte Liste zurück.
     */
    public synchronized String getMemberListString(String roomName) {
        Room room = rooms.get(roomName);
        if (room == null) {
            return "";
        }
        return String.join(",", room.getMemberNames());
    }


    /**
     * Gibt alle Räume als komma-separierte Liste zurück.
     */
    public synchronized String getRoomListString() {
        return String.join(",", rooms.keySet());
    }
}