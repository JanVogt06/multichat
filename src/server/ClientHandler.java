package server;

import java.io.*;
import java.net.Socket;
import java.util.List;

/**
 * Verwalten der Kommunikation mit einem einzelnen Client.
 * Unterstützt jetzt Räume (Mehrraum-System) und bietet eine public
 * requestClose()-Methode, die von Admin-Operationen (Kick) genutzt wird.
 */
public class ClientHandler extends Thread {

    private final Socket socket;
    private final UserManager userManager;
    private final Server server;
    private DataInputStream input;
    private DataOutputStream output;
    private String username;
    private volatile boolean readyForChat = false;

    // Aktueller Raum des Clients
    private String currentRoom;

    // Lauf-Flag: wenn false soll die Hauptschleife sauber enden
    private volatile boolean running = true;

    public ClientHandler(Socket socket, UserManager userManager, Server server) {
        this.socket = socket;
        this.userManager = userManager;
        this.server = server;
        this.currentRoom = null;
    }

    @Override
    public void run() {
        try {
            input = new DataInputStream(socket.getInputStream());
            output = new DataOutputStream(socket.getOutputStream());

            server.log("Neuer Client verbunden: " + socket.getInetAddress());

            username = authenticate();
            server.log("User '" + username + "' eingeloggt");

            // Warte auf READY-Signal vom Client
            String readySignal = input.readUTF();
            if ("READY".equals(readySignal)) {
                server.log("Client '" + username + "' ist bereit für Chat");
                readyForChat = true;

                // automatisch in DEFAULT_ROOM einreihen
                currentRoom = Server.DEFAULT_ROOM;
                server.joinRoom(currentRoom, this);

                // sende Raumliste und Userliste
                sendRoomList();
                sendUserList();

                // andere im Raum informieren
                server.broadcastToRoom(">>> " + username + " hat den Raum betreten: " + currentRoom, currentRoom, this);
            }

            // Hauptloop: läuft solange running == true
            chatLoop();

        } catch (IOException e) {
            // Wenn running noch true war, ist es ein unerwarteter Fehler
            if (running) {
                server.log("Fehler bei Client " + username + ": " + e.getMessage());
            } else {
                // normaler Abbruch durch requestClose()
                server.log("Verbindung von " + username + " wurde geschlossen (requestClose).");
            }
        } finally {
            close();
        }
    }

    private String authenticate() throws IOException {
        server.log("Warte auf Authentifizierung...");

        while (running) {
            String message = input.readUTF();
            server.log("Empfangen: " + message);
            String[] parts = message.split(":", 3);
            String command = parts[0];

            if ("REGISTER".equals(command)) {
                handleRegister(parts);
            } else if ("LOGIN".equals(command)) {
                String loginResult = handleLogin(parts);
                if (loginResult != null) return loginResult;
            } else {
                sendResponse("ERROR", "Unbekannter Befehl");
            }
        }
        // wenn running false geworden ist: Abbruch
        throw new IOException("Authentifizierung abgebrochen");
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
            server.log("Neuer User: " + user);
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

        // Falls gebannt: Ablehnen
        if (userManager.isBanned(user)) {
            sendResponse("ERROR", "User ist gebannt");
            return null;
        }

        if (!userManager.userExists(user)) {
            sendResponse("ERROR", "Username nicht gefunden");
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
        StringBuilder sb = new StringBuilder();
        sb.append(">>> Angemeldete User: ");
        List<String> usernames = server.getConnectedUsernames();
        boolean first = true;
        for (String u : usernames) {
            if (!first) sb.append(", ");
            // Wenn möglich, Raum für den User ermitteln (vereinfachte Darstellung)
            String room = findRoomForUser(u);
            sb.append(u).append("(").append(room != null ? room : "?").append(")");
            first = false;
        }
        sendMessage(sb.toString());
    }

    // Vereinfachte Methode: versucht die Raumzugehörigkeit für bekannten User zu ermitteln.
    private String findRoomForUser(String username) {
        // Wenn es der eigene Username ist, gib currentRoom zurück
        if (this.username != null && this.username.equals(username)) return currentRoom;
        // Ansonsten keine sichere Info hier (Server müsste API liefern). Rückgabe null möglich.
        return null;
    }

    private void sendRoomList() throws IOException {
        List<String> rooms = server.getRoomList();
        String roomList = "ROOMLIST:" + String.join(",", rooms);
        sendMessage(roomList);
    }

    private void chatLoop() throws IOException {
        while (running) {
            try {
                String message = input.readUTF();
                server.log(username + ": " + message);

                // Kommandos für Raumverwaltung oder normale Nachrichten
                if (message.startsWith("CREATE_ROOM:")) {
                    String roomName = message.substring("CREATE_ROOM:".length());
                    boolean created = server.createRoom(roomName);
                    sendMessage(created ? "SUCCESS:Raum " + roomName + " erstellt" : "ERROR:Raum existiert bereits");
                    broadcastRoomListUpdate();
                    continue;
                }

                if (message.startsWith("DELETE_ROOM:")) {
                    String roomName = message.substring("DELETE_ROOM:".length());
                    boolean deleted = server.deleteRoom(roomName);
                    sendMessage(deleted ? "SUCCESS:Raum " + roomName + " gelöscht" : "ERROR:Raum konnte nicht gelöscht werden (evtl. nicht leer oder Default)");
                    broadcastRoomListUpdate();
                    continue;
                }

                if (message.startsWith("JOIN:")) {
                    String roomName = message.substring("JOIN:".length());
                    if (!server.joinRoom(roomName, this)) {
                        sendMessage("ERROR:Raum nicht gefunden");
                    } else {
                        if (currentRoom != null) {
                            server.leaveRoom(currentRoom, this);
                            server.broadcastToRoom("<<< " + username + " hat den Raum verlassen: " + currentRoom, currentRoom, this);
                        }
                        currentRoom = roomName;
                        sendMessage("SUCCESS:Beigetreten " + roomName);
                        server.broadcastToRoom(">>> " + username + " hat den Raum betreten: " + roomName, roomName, this);
                        sendRoomList();
                        sendUserList();
                    }
                    continue;
                }

                if ("LEAVE".equals(message)) {
                    if (currentRoom != null) {
                        server.leaveRoom(currentRoom, this);
                        server.broadcastToRoom("<<< " + username + " hat den Raum verlassen: " + currentRoom, currentRoom, this);
                    }
                    currentRoom = Server.DEFAULT_ROOM;
                    server.joinRoom(currentRoom, this);
                    sendMessage("SUCCESS:Zurück in " + currentRoom);
                    server.broadcastToRoom(">>> " + username + " hat den Raum betreten: " + currentRoom, currentRoom, this);
                    continue;
                }

                // Normale Nachricht -> an aktuellen Raum senden
                String formatted = "[" + username + "] " + message;
                server.broadcastToRoom(formatted, currentRoom, this);

            } catch (EOFException e) {
                server.log(username + " hat sich abgemeldet");
                break;
            }
        }
    }

    private void broadcastRoomListUpdate() {
        // Sende aktualisierte Raumliste an alle verbundenen Clients (einfacher Ansatz)
        List<ClientHandler> clients = server.getAllClientHandlers(); // wir werden diese Methode im Server bereitstellen
        for (ClientHandler ch : clients) {
            try {
                ch.sendRoomList();
            } catch (IOException ignored) { }
        }
    }

    private void sendResponse(String status, String message) throws IOException {
        output.writeUTF(status + ":" + message);
        output.flush();
    }

    public void sendMessage(String message) throws IOException {
        if (readyForChat && running) {
            output.writeUTF(message);
            output.flush();
        }
    }

    public boolean isReadyForChat() {
        return readyForChat;
    }

    /**
     * Public method to request a clean close of this handler from outside
     * (e.g. Admin Kick). It attempts to stop the main loop and close socket/streams.
     */
    public void requestClose() {
        running = false;
        // Closing the socket/inputstream will interrupt blocking readUTF()
        try {
            if (socket != null && !socket.isClosed()) socket.close();
        } catch (IOException ignored) { }
    }

    private void close() {
        try {
            if (username != null) {
                // Benachrichtige Raum
                if (currentRoom != null) {
                    server.broadcastToRoom("<<< " + username + " hat den Raum verlassen", currentRoom, this);
                    server.leaveRoom(currentRoom, this);
                }
                server.removeClient(this);
            }

            if (input != null) try { input.close(); } catch (IOException ignored) {}
            if (output != null) try { output.close(); } catch (IOException ignored) {}
            if (socket != null && !socket.isClosed()) try { socket.close(); } catch (IOException ignored) {}

            server.log("ClientHandler geschlossen: " + username);
        } catch (Exception e) {
            server.log("Fehler beim Schließen: " + e.getMessage());
        }
    }

    public String getUsername() {
        return username;
    }
}
