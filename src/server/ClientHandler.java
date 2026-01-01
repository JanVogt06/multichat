package server;

import java.io.*;
import java.net.Socket;
import java.util.List;

/**
 * Verwaltet die Kommunikation mit einem einzelnen Client.
 * Läuft in eigenem Thread und kümmert sich um Login, Chat und Dateitransfer.
 */
public class ClientHandler extends Thread {

    private final Socket socket;
    private final UserManager userManager;
    private final Server server;

    private DataInputStream input;
    private DataOutputStream output;

    private String username;
    private volatile boolean readyForChat = false;
    private volatile boolean connected = true;
    private String currentRoom = null;


    public ClientHandler(Socket socket, UserManager userManager, Server server) {
        this.socket = socket;
        this.userManager = userManager;
        this.server = server;
    }


    @Override
    public void run() {
        try {
            input = new DataInputStream(socket.getInputStream());
            output = new DataOutputStream(socket.getOutputStream());

            server.log("Neuer Client verbunden: " + socket.getInetAddress());

            // Login/Registrierung
            username = authenticate();
            if (username == null) {
                close();
                return;
            }

            server.log("User '" + username + "' eingeloggt");

            // Auf READY warten
            String readySignal = input.readUTF();
            if (readySignal.equals("READY")) {
                server.log("Client '" + username + "' ist bereit");
                readyForChat = true;
                server.notifyUserJoined(username);
                sendUserList();
                server.broadcast(">>> " + username + " hat den Chat betreten", this);
            }

            // Hauptschleife
            chatLoop();

        } catch (IOException e) {
            if (connected) {
                server.log("Fehler bei Client " + username + ": " + e.getMessage());
            }
        } finally {
            close();
        }
    }


    /**
     * Login oder Registrierung durchführen.
     */
    private String authenticate() throws IOException {
        server.log("Warte auf Authentifizierung...");

        while (connected) {
            String message = input.readUTF();
            server.log("Empfangen: " + message);

            String[] parts = message.split(":", 3);
            String command = parts[0];

            if (command.equals("REGISTER")) {
                handleRegister(parts);
            } else if (command.equals("LOGIN")) {
                String loginResult = handleLogin(parts);
                if (loginResult != null) {
                    return loginResult;
                }
            } else {
                sendResponse("ERROR", "Unbekannter Befehl");
            }
        }
        return null;
    }


    private void handleRegister(String[] parts) throws IOException {
        if (parts.length != 3) {
            sendResponse("ERROR", "Ungültiges Format");
            return;
        }

        String user = parts[1];
        String pass = parts[2];

        if (userManager.registerUser(user, pass)) {
            sendResponse("SUCCESS", "Registrierung erfolgreich");
            server.log("Neuer User registriert: " + user);
        } else {
            sendResponse("ERROR", "Username bereits vergeben");
        }
    }


    private String handleLogin(String[] parts) throws IOException {
        if (parts.length != 3) {
            sendResponse("ERROR", "Ungültiges Format");
            return null;
        }

        String user = parts[1];
        String pass = parts[2];

        if (!userManager.userExists(user)) {
            sendResponse("ERROR", "Username nicht gefunden");
            return null;
        }

        if (userManager.isUserBanned(user)) {
            sendResponse("ERROR", "Dein Account wurde gesperrt");
            return null;
        }

        if (!userManager.validatePassword(user, pass)) {
            sendResponse("ERROR", "Falsches Passwort");
            return null;
        }

        sendResponse("SUCCESS", "Login erfolgreich");
        return user;
    }


    private void sendUserList() throws IOException {
        var usernames = server.getConnectedUsernames();
        String userList = ">>> Angemeldete User: " + String.join(", ", usernames);
        sendMessage(userList);
    }


    /**
     * Hauptschleife: Empfängt Befehle und Nachrichten vom Client.
     */
    private void chatLoop() throws IOException {
        sendRoomList();

        while (connected) {
            try {
                String message = input.readUTF();

                // Raum-Befehle
                if (message.startsWith("CREATE_ROOM:")) {
                    handleCreateRoom(message.substring(12));
                } else if (message.startsWith("JOIN_ROOM:")) {
                    handleJoinRoom(message.substring(10));
                } else if (message.equals("LEAVE_ROOM")) {
                    handleLeaveRoom();
                } else if (message.equals("GET_ROOMS")) {
                    sendRoomList();
                } else if (message.equals("GET_USERS")) {
                    sendUserListForCurrentRoom();
                }

                // ============================================================
                // DATEI-BEFEHLE (Meilenstein 3)
                // ============================================================
                else if (message.startsWith("UPLOAD_FILE:")) {
                    handleUploadFile(message.substring(12));
                } else if (message.equals("LIST_FILES")) {
                    handleListFiles();
                } else if (message.startsWith("DOWNLOAD_FILE:")) {
                    handleDownloadFile(message.substring(14));
                }

                // Normale Chat-Nachricht
                else {
                    handleChatMessage(message);
                }

            } catch (EOFException e) {
                server.log(username + " hat sich abgemeldet");
                break;
            }
        }
    }


    private void handleChatMessage(String message) throws IOException {
        if (currentRoom == null) {
            sendMessage("ERROR:Du musst zuerst einem Raum beitreten");
            return;
        }

        String formattedMessage = "[" + username + "] " + message;

        Room room = server.getRoomManager().getRoom(currentRoom);
        if (room != null) {
            room.broadcast(formattedMessage, this);
            server.log("[Raum: " + currentRoom + "] " + formattedMessage);
        }
    }


    private void handleCreateRoom(String roomName) throws IOException {
        roomName = roomName.trim();

        if (roomName.isEmpty()) {
            sendMessage("ERROR:Raumname darf nicht leer sein");
            return;
        }

        RoomManager roomManager = server.getRoomManager();

        if (roomManager.createRoom(roomName, this)) {
            if (currentRoom != null) {
                leaveCurrentRoom();
            }

            currentRoom = roomName;
            roomManager.joinRoom(roomName, this);

            sendMessage("ROOM_CREATED:" + roomName);
            sendMessage("ROOM_JOINED:" + roomName);

            server.notifyUserRoomChanged(username, roomName);
            broadcastRoomListToAll();
            sendUserListForCurrentRoom();
        } else {
            sendMessage("ERROR:Raum existiert bereits oder Name ungültig");
        }
    }


    private void handleJoinRoom(String roomName) throws IOException {
        roomName = roomName.trim();
        RoomManager roomManager = server.getRoomManager();

        if (!roomManager.roomExists(roomName)) {
            sendMessage("ERROR:Raum existiert nicht");
            return;
        }

        if (currentRoom != null) {
            boolean roomDeleted = leaveCurrentRoom();
            if (roomDeleted) {
                broadcastRoomListToAll();
            }
        }

        currentRoom = roomName;
        roomManager.joinRoom(roomName, this);

        sendMessage("ROOM_JOINED:" + roomName);
        server.notifyUserRoomChanged(username, roomName);

        Room room = roomManager.getRoom(roomName);
        if (room != null) {
            room.sendHistoryTo(this);
            room.broadcast(">>> " + username + " hat den Raum betreten", this);
        }

        sendUserListForCurrentRoom();
        broadcastUserListToRoom(roomName);
    }


    private void handleLeaveRoom() throws IOException {
        if (currentRoom == null) {
            sendMessage("ERROR:Du bist in keinem Raum");
            return;
        }

        String leftRoom = currentRoom;
        boolean roomDeleted = leaveCurrentRoom();

        sendMessage("ROOM_LEFT:" + leftRoom);
        if (roomDeleted) {
            sendMessage("ROOM_DELETED:" + leftRoom);
        }

        broadcastRoomListToAll();
    }


    private boolean leaveCurrentRoom() throws IOException {
        if (currentRoom == null) {
            return false;
        }

        RoomManager roomManager = server.getRoomManager();
        Room room = roomManager.getRoom(currentRoom);
        String roomName = currentRoom;

        if (room != null) {
            room.broadcast("<<< " + username + " hat den Raum verlassen", this);
        }

        boolean deleted = roomManager.leaveRoom(currentRoom, this);
        currentRoom = null;
        server.notifyUserRoomChanged(username, null);

        if (!deleted) {
            broadcastUserListToRoom(roomName);
        }

        return deleted;
    }


    // ========================================================================
    // DATEI-UPLOAD UND DOWNLOAD
    // ========================================================================
    //
    // So funktioniert die Übertragung:
    //
    // UPLOAD (Client -> Server):
    // 1. Client schickt: "UPLOAD_FILE:bild.png"
    // 2. Server antwortet: "READY_FOR_UPLOAD"
    // 3. Client schickt: 4 Bytes (Dateigröße als int)
    // 4. Client schickt: Die Datei-Bytes
    // 5. Server speichert und antwortet: "UPLOAD_SUCCESS:bild.png"
    //
    // DOWNLOAD (Server -> Client):
    // 1. Client schickt: "DOWNLOAD_FILE:bild.png"
    // 2. Server antwortet: "FILE_DATA:bild.png"
    // 3. Server schickt: 4 Bytes (Dateigröße als int)
    // 4. Server schickt: Die Datei-Bytes
    //
    // Warum so kompliziert?
    // - Dateien sind Binärdaten, keine Textnachrichten
    // - Wir müssen wissen wie viele Bytes kommen (sonst wissen wir nicht wann die Datei zu Ende ist)
    // - writeInt() schreibt genau 4 Bytes, readInt() liest genau 4 Bytes
    // - readFully() wartet bis ALLE Bytes angekommen sind (wichtig bei großen Dateien!)
    // ========================================================================


    /**
     * Empfängt eine Datei vom Client und speichert sie.
     */
    private void handleUploadFile(String fileName) throws IOException {
        // Muss in einem Raum sein
        if (currentRoom == null) {
            sendMessage("UPLOAD_ERROR:Du musst zuerst einem Raum beitreten");
            return;
        }

        // Sicherheitscheck: Keine Pfade wie "../geheim.txt" erlauben
        if (fileName.contains("/") || fileName.contains("\\") || fileName.contains("..")) {
            sendMessage("UPLOAD_ERROR:Ungültiger Dateiname");
            return;
        }

        // Nur bestimmte Dateitypen erlauben
        String lowerName = fileName.toLowerCase();
        boolean erlaubt = lowerName.endsWith(".pdf") ||
                lowerName.endsWith(".png") ||
                lowerName.endsWith(".jpg") ||
                lowerName.endsWith(".jpeg") ||
                lowerName.endsWith(".gif");

        if (!erlaubt) {
            sendMessage("UPLOAD_ERROR:Nur PDF und Bilder (PNG, JPG, GIF) erlaubt");
            return;
        }

        // Raum-Ordner holen
        File roomDir = server.getRoomManager().getRoomDirectory(currentRoom);
        if (roomDir == null || !roomDir.exists()) {
            sendMessage("UPLOAD_ERROR:Raumverzeichnis nicht gefunden");
            return;
        }

        // Client sagen dass wir bereit sind
        sendMessage("READY_FOR_UPLOAD");

        // Dateigröße lesen (4 Bytes)
        // writeInt() auf Client-Seite schreibt die Zahl als 4 Bytes
        // readInt() liest genau diese 4 Bytes und macht wieder eine Zahl draus
        int fileSize = input.readInt();

        // Nicht zu große Dateien (max 10 MB)
        if (fileSize > 10 * 1024 * 1024) {
            // Trotzdem die Bytes lesen, sonst blockiert der Stream
            byte[] discard = new byte[fileSize];
            input.readFully(discard);
            sendMessage("UPLOAD_ERROR:Datei zu groß (max. 10 MB)");
            return;
        }

        // Datei-Bytes lesen
        // readFully() ist wichtig! Normales read() könnte weniger Bytes lesen
        // als wir brauchen (wenn die Daten in mehreren TCP-Paketen ankommen)
        byte[] fileData = new byte[fileSize];
        input.readFully(fileData);

        // In Datei speichern
        File targetFile = new File(roomDir, fileName);
        try (FileOutputStream fos = new FileOutputStream(targetFile)) {
            fos.write(fileData);
        }

        server.log("Datei hochgeladen: " + fileName + " (" + fileSize + " Bytes) von " + username);

        // Erfolg melden
        sendMessage("UPLOAD_SUCCESS:" + fileName);

        // Alle im Raum informieren
        Room room = server.getRoomManager().getRoom(currentRoom);
        if (room != null) {
            room.broadcast(">>> " + username + " hat Datei hochgeladen: " + fileName, this);
        }
    }


    /**
     * Sendet die Liste aller Dateien im aktuellen Raum.
     */
    private void handleListFiles() throws IOException {
        if (currentRoom == null) {
            sendMessage("FILE_LIST:");
            return;
        }

        List<String> files = server.getRoomManager().getFilesInRoom(currentRoom);
        String fileList = String.join(",", files);

        sendMessage("FILE_LIST:" + fileList);
    }


    /**
     * Sendet eine Datei an den Client.
     */
    private void handleDownloadFile(String fileName) throws IOException {
        if (currentRoom == null) {
            sendMessage("DOWNLOAD_ERROR:Du bist in keinem Raum");
            return;
        }

        // Sicherheitscheck
        if (fileName.contains("/") || fileName.contains("\\") || fileName.contains("..")) {
            sendMessage("DOWNLOAD_ERROR:Ungültiger Dateiname");
            return;
        }

        File roomDir = server.getRoomManager().getRoomDirectory(currentRoom);
        if (roomDir == null) {
            sendMessage("DOWNLOAD_ERROR:Raumverzeichnis nicht gefunden");
            return;
        }

        File file = new File(roomDir, fileName);
        if (!file.exists() || !file.isFile()) {
            sendMessage("DOWNLOAD_ERROR:Datei nicht gefunden");
            return;
        }

        // Datei komplett in Speicher lesen
        byte[] fileData = new byte[(int) file.length()];
        try (FileInputStream fis = new FileInputStream(file)) {
            fis.read(fileData);
        }

        // Erst den Header schicken (normale UTF-Nachricht)
        sendMessage("FILE_DATA:" + fileName);

        // Dann die Binärdaten: Erst Größe (4 Bytes), dann die Bytes
        output.writeInt(fileData.length);
        output.write(fileData);
        output.flush();

        server.log("Datei gesendet: " + fileName + " (" + fileData.length + " Bytes) an " + username);
    }


    // ========================================================================
    // HILFSMETHODEN
    // ========================================================================


    private void sendRoomList() throws IOException {
        String roomList = server.getRoomManager().getRoomListString();
        sendMessage("ROOM_LIST:" + roomList);
    }


    private void sendUserListForCurrentRoom() throws IOException {
        if (currentRoom != null) {
            String userList = server.getRoomManager().getMemberListString(currentRoom);
            sendMessage("USER_LIST:" + userList);
        } else {
            var usernames = server.getConnectedUsernames();
            sendMessage("USER_LIST:" + String.join(",", usernames));
        }
    }


    private void broadcastRoomListToAll() {
        String roomList = "ROOM_LIST:" + server.getRoomManager().getRoomListString();
        server.broadcastToAll(roomList);
    }


    private void broadcastUserListToRoom(String roomName) {
        RoomManager roomManager = server.getRoomManager();
        Room room = roomManager.getRoom(roomName);

        if (room != null) {
            String userList = "USER_LIST:" + roomManager.getMemberListString(roomName);
            room.broadcastToAll(userList);
        }
    }


    public String getCurrentRoom() {
        return currentRoom;
    }


    private void sendResponse(String status, String message) throws IOException {
        output.writeUTF(status + ":" + message);
        output.flush();
    }


    public void sendMessage(String message) throws IOException {
        if (readyForChat && connected) {
            output.writeUTF(message);
            output.flush();
        }
    }


    public boolean isReadyForChat() {
        return readyForChat;
    }


    public void disconnect(String reason) {
        try {
            if (connected && output != null) {
                output.writeUTF("DISCONNECT:" + reason);
                output.flush();
            }
        } catch (IOException e) {
            // Ignorieren
        } finally {
            connected = false;
            close();
        }
    }


    private void close() {
        try {
            connected = false;
            readyForChat = false;

            if (username != null) {
                server.broadcast("<<< " + username + " hat den Chat verlassen", this);
                server.removeClient(this);
                broadcastRoomListToAll();
            }

            if (input != null) input.close();
            if (output != null) output.close();
            if (socket != null && !socket.isClosed()) socket.close();

        } catch (IOException e) {
            server.log("Fehler beim Schließen: " + e.getMessage());
        }
    }


    public String getUsername() {
        return username;
    }
}