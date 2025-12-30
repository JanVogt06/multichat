package server;

import java.io.*;
import java.net.Socket;

/**
 * Diese Klasse verwaltet die Kommunikation mit einem einzelnen Client.
 * Jeder ClientHandler laeuft in einem eigenen Thread und ist fuer die
 * Authentifizierung, Chat-Kommunikation und Verwaltung eines Clients zustaendig.
 */
public class ClientHandler extends Thread {

    // Socket-Verbindung zum Client
    private final Socket socket;

    // Referenz zum UserManager fuer Benutzerverwaltung
    private final UserManager userManager;

    // Referenz zum Server fuer Broadcasting
    private final Server server;

    // Eingabestrom vom Client
    private DataInputStream input;

    // Ausgabestrom zum Client
    private DataOutputStream output;

    // Benutzername des angemeldeten Clients
    private String username;

    // Flag, das angibt, ob der Client bereit fuer Chat-Nachrichten ist
    // volatile sorgt fuer Thread-Sicherheit bei Lese-/Schreibzugriffen
    private volatile boolean readyForChat = false;

    // Flag fuer Verbindungsstatus
    private volatile boolean connected = true;

    // Aktueller Raum des Clients
    private String currentRoom = null;


    /**
     * Konstruktor fuer einen neuen ClientHandler.
     *
     * @param socket Socket-Verbindung zum Client
     * @param userManager UserManager fuer Benutzerverwaltung
     * @param server Server-Instanz fuer Broadcasting
     */
    public ClientHandler(Socket socket, UserManager userManager, Server server) {
        this.socket = socket;
        this.userManager = userManager;
        this.server = server;
    }


    /**
     * Hauptmethode des Threads. Wird beim Start des Threads ausgefuehrt.
     * Steuert den gesamten Ablauf: Verbindung, Authentifizierung, Chat-Loop.
     */
    @Override
    public void run() {
        try {
            // Eingabe- und Ausgabestroeme initialisieren
            input = new DataInputStream(socket.getInputStream());
            output = new DataOutputStream(socket.getOutputStream());

            server.log("Neuer Client verbunden: " + socket.getInetAddress());

            // Authentifizierungsprozess durchfuehren (Login oder Registrierung)
            username = authenticate();

            if (username == null) {
                // Authentifizierung fehlgeschlagen oder abgebrochen
                close();
                return;
            }

            server.log("User '" + username + "' eingeloggt");

            // Warte auf READY-Signal vom Client, bevor Chat-Nachrichten gesendet werden
            String readySignal = input.readUTF();
            if (readySignal.equals("READY")) {
                server.log("Client '" + username + "' ist bereit fuer Chat");

                // Client ist nun bereit, Chat-Nachrichten zu empfangen
                readyForChat = true;

                // GUI ueber neuen Nutzer informieren
                server.notifyUserJoined(username);

                // Aktuelle Liste aller angemeldeten Benutzer senden
                sendUserList();

                // Alle anderen Clients ueber neuen Benutzer informieren
                server.broadcast(">>> " + username + " hat den Chat betreten", this);
            }

            // Hauptschleife fuer Chat-Kommunikation
            chatLoop();

        } catch (IOException e) {
            if (connected) {
                server.log("Fehler bei Client " + username + ": " + e.getMessage());
            }
        } finally {
            // Ressourcen freigeben und Client abmelden
            close();
        }
    }


    /**
     * Authentifizierungsprozess fuer den Client.
     * Wartet auf LOGIN oder REGISTER Befehle vom Client.
     *
     * @return Benutzername nach erfolgreicher Anmeldung, null bei Abbruch
     * @throws IOException bei Kommunikationsfehlern
     */
    private String authenticate() throws IOException {
        server.log("Warte auf Authentifizierung...");

        while (connected) {
            // Nachricht vom Client lesen (Format: BEFEHL:username:password)
            String message = input.readUTF();
            server.log("Empfangen: " + message);

            // Nachricht in Teile aufteilen
            String[] parts = message.split(":", 3);
            String command = parts[0];

            // Je nach Befehl entsprechende Aktion ausfuehren
            if (command.equals("REGISTER")) {
                handleRegister(parts);

            } else if (command.equals("LOGIN")) {
                // Bei erfolgreichem Login wird der Username zurueckgegeben
                String loginResult = handleLogin(parts);
                if (loginResult != null) {
                    return loginResult;
                }

            } else {
                // Unbekannter Befehl
                sendResponse("ERROR", "Unbekannter Befehl");
            }
        }

        return null;
    }


    /**
     * Verarbeitet eine Registrierungsanfrage.
     *
     * @param parts Array mit [REGISTER, username, password]
     * @throws IOException bei Kommunikationsfehlern
     */
    private void handleRegister(String[] parts) throws IOException {
        // Ueberpruefe, ob die Nachricht das richtige Format hat
        if (parts.length != 3) {
            sendResponse("ERROR", "Ungueltiges Format");
            return;
        }

        String user = parts[1];
        String pass = parts[2];

        // Versuche Benutzer zu registrieren
        if (userManager.registerUser(user, pass)) {
            sendResponse("SUCCESS", "Registrierung erfolgreich");
            server.log("Neuer User registriert: " + user);
        } else {
            sendResponse("ERROR", "Username bereits vergeben");
        }
    }


    /**
     * Verarbeitet eine Login-Anfrage.
     *
     * @param parts Array mit [LOGIN, username, password]
     * @return Username bei erfolgreichem Login, sonst null
     * @throws IOException bei Kommunikationsfehlern
     */
    private String handleLogin(String[] parts) throws IOException {
        // Überprüfe, ob die Nachricht das richtige Format hat
        if (parts.length != 3) {
            sendResponse("ERROR", "Ungültiges Format");
            return null;
        }

        String user = parts[1];
        String pass = parts[2];

        // Überprüfe, ob Benutzer existiert
        if (!userManager.userExists(user)) {
            sendResponse("ERROR", "Username nicht gefunden");
            return null;
        }

        // Überprüfe, ob Benutzer gebannt ist
        if (userManager.isUserBanned(user)) {
            sendResponse("ERROR", "Dein Account wurde gesperrt");
            server.log("Gebannter User '" + user + "' versuchte sich einzuloggen");
            return null;
        }

        // Überprüfe Passwort
        if (!userManager.validatePassword(user, pass)) {
            sendResponse("ERROR", "Falsches Passwort");
            return null;
        }

        // Login erfolgreich
        sendResponse("SUCCESS", "Login erfolgreich");
        return user;
    }


    /**
     * Sendet die Liste aller angemeldeten Benutzer an diesen Client.
     *
     * @throws IOException bei Kommunikationsfehlern
     */
    private void sendUserList() throws IOException {
        var usernames = server.getConnectedUsernames();
        String userList = ">>> Angemeldete User: " + String.join(", ", usernames);
        sendMessage(userList);
    }


    /**
     * Hauptschleife fuer den Chat-Betrieb.
     * Empfaengt Nachrichten vom Client und verarbeitet Befehle oder Chat-Nachrichten.
     *
     * @throws IOException bei Kommunikationsfehlern
     */
    private void chatLoop() throws IOException {
        // Raumliste an Client senden
        sendRoomList();

        while (connected) {
            try {
                // Nachricht vom Client lesen
                String message = input.readUTF();

                // Pruefen ob es ein Befehl ist
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

                } else {
                    // Normale Chat-Nachricht
                    handleChatMessage(message);
                }

            } catch (EOFException e) {
                // Client hat Verbindung beendet (normal)
                server.log(username + " hat sich abgemeldet");
                break;
            }
        }
    }


    /**
     * Verarbeitet eine Chat-Nachricht.
     * Sendet die Nachricht an alle Mitglieder des aktuellen Raums.
     *
     * @param message Die Chat-Nachricht
     * @throws IOException bei Kommunikationsfehlern
     */
    private void handleChatMessage(String message) throws IOException {
        if (currentRoom == null) {
            sendMessage("ERROR:Du musst zuerst einem Raum beitreten");
            return;
        }

        // Nachricht formatieren
        String formattedMessage = "[" + username + "] " + message;

        // An alle im Raum senden (ausser Sender)
        Room room = server.getRoomManager().getRoom(currentRoom);
        if (room != null) {
            room.broadcast(formattedMessage, this);
            server.log("[Raum: " + currentRoom + "] " + formattedMessage);
        }
    }


    /**
     * Verarbeitet den CREATE_ROOM Befehl.
     */
    private void handleCreateRoom(String roomName) throws IOException {
        roomName = roomName.trim();

        if (roomName.isEmpty()) {
            sendMessage("ERROR:Raumname darf nicht leer sein");
            return;
        }

        RoomManager roomManager = server.getRoomManager();

        if (roomManager.createRoom(roomName, this)) {
            // Raum erstellt - Client tritt automatisch bei
            if (currentRoom != null) {
                // Zuerst alten Raum verlassen
                leaveCurrentRoom();
            }

            currentRoom = roomName;
            roomManager.joinRoom(roomName, this);

            // Client informieren
            sendMessage("ROOM_CREATED:" + roomName);
            sendMessage("ROOM_JOINED:" + roomName);

            // GUI über Raumwechsel informieren
            server.notifyUserRoomChanged(username, roomName);

            // Raumliste an alle Clients senden
            broadcastRoomListToAll();

            // Nutzerliste fuer den Raum senden
            sendUserListForCurrentRoom();

        } else {
            sendMessage("ERROR:Raum existiert bereits");
        }
    }


    /**
     * Verarbeitet den JOIN_ROOM Befehl.
     */
    private void handleJoinRoom(String roomName) throws IOException {
        roomName = roomName.trim();

        RoomManager roomManager = server.getRoomManager();

        if (!roomManager.roomExists(roomName)) {
            sendMessage("ERROR:Raum existiert nicht");
            return;
        }

        // Aktuellen Raum verlassen falls vorhanden
        if (currentRoom != null) {
            boolean roomDeleted = leaveCurrentRoom();

            // Wenn der alte Raum gelöscht wurde, alle Clients informieren
            if (roomDeleted) {
                broadcastRoomListToAll();
            }
        }

        // Neuem Raum beitreten
        currentRoom = roomName;
        roomManager.joinRoom(roomName, this);

        // Client informieren
        sendMessage("ROOM_JOINED:" + roomName);

        // GUI über Raumwechsel informieren
        server.notifyUserRoomChanged(username, roomName);

        // Chat-Historie senden
        Room room = roomManager.getRoom(roomName);
        if (room != null) {
            room.sendHistoryTo(this);

            // Andere im Raum informieren
            room.broadcast(">>> " + username + " hat den Raum betreten", this);
        }

        // Nutzerliste fuer den Raum senden
        sendUserListForCurrentRoom();

        // Nutzerliste an alle im Raum aktualisieren
        broadcastUserListToRoom(roomName);

        server.log(username + " ist Raum '" + roomName + "' beigetreten");
    }


    /**
     * Verarbeitet den LEAVE_ROOM Befehl.
     */
    private void handleLeaveRoom() throws IOException {
        if (currentRoom == null) {
            sendMessage("ERROR:Du bist in keinem Raum");
            return;
        }

        String leftRoom = currentRoom;
        boolean roomDeleted = leaveCurrentRoom();

        // Client informieren
        sendMessage("ROOM_LEFT:" + leftRoom);

        if (roomDeleted) {
            sendMessage("ROOM_DELETED:" + leftRoom);
        }

        // Raumliste an alle aktualisieren (immer, auch wenn Raum nicht geloescht)
        broadcastRoomListToAll();
    }


    /**
     * Verlaesst den aktuellen Raum.
     *
     * @return true wenn der Raum danach geloescht wurde
     */
    private boolean leaveCurrentRoom() throws IOException {
        if (currentRoom == null) {
            return false;
        }

        RoomManager roomManager = server.getRoomManager();
        Room room = roomManager.getRoom(currentRoom);
        String roomName = currentRoom;

        // Andere im Raum informieren
        if (room != null) {
            room.broadcast("<<< " + username + " hat den Raum verlassen", this);
        }

        // Raum verlassen
        boolean deleted = roomManager.leaveRoom(currentRoom, this);

        server.log(username + " hat Raum '" + currentRoom + "' verlassen");

        currentRoom = null;

        // GUI über Raumwechsel informieren (null = kein Raum mehr)
        server.notifyUserRoomChanged(username, null);

        // Nutzerliste im Raum aktualisieren (falls Raum noch existiert)
        if (!deleted) {
            broadcastUserListToRoom(roomName);
        }

        return deleted;
    }


    /**
     * Sendet die Raumliste an den Client.
     */
    private void sendRoomList() throws IOException {
        String roomList = server.getRoomManager().getRoomListString();
        sendMessage("ROOM_LIST:" + roomList);
    }


    /**
     * Sendet die Nutzerliste fuer den aktuellen Raum.
     */
    private void sendUserListForCurrentRoom() throws IOException {
        if (currentRoom != null) {
            String userList = server.getRoomManager().getMemberListString(currentRoom);
            sendMessage("USER_LIST:" + userList);
        } else {
            // Globale Nutzerliste
            var usernames = server.getConnectedUsernames();
            sendMessage("USER_LIST:" + String.join(",", usernames));
        }
    }


    /**
     * Sendet die aktualisierte Raumliste an alle verbundenen Clients.
     */
    private void broadcastRoomListToAll() {
        String roomList = "ROOM_LIST:" + server.getRoomManager().getRoomListString();
        // Nutze die neue broadcastToAll Methode im Server
        server.broadcastToAll(roomList);
    }


    /**
     * Sendet die aktualisierte Nutzerliste an alle Mitglieder eines Raums.
     *
     * @param roomName Name des Raums
     */
    private void broadcastUserListToRoom(String roomName) {
        RoomManager roomManager = server.getRoomManager();
        Room room = roomManager.getRoom(roomName);

        if (room != null) {
            String userList = "USER_LIST:" + roomManager.getMemberListString(roomName);
            room.broadcastToAll(userList);
        }
    }


    /**
     * Gibt den aktuellen Raum zurueck.
     *
     * @return Raumname oder null
     */
    public String getCurrentRoom() {
        return currentRoom;
    }


    /**
     * Sendet eine Antwort an den Client (waehrend Authentifizierung).
     *
     * @param status Status der Operation (SUCCESS oder ERROR)
     * @param message Beschreibung/Nachricht
     * @throws IOException bei Kommunikationsfehlern
     */
    private void sendResponse(String status, String message) throws IOException {
        output.writeUTF(status + ":" + message);
        output.flush();
    }


    /**
     * Sendet eine Chat-Nachricht an diesen Client.
     * Wird vom Server aufgerufen. Nachricht wird nur gesendet,
     * wenn der Client im Chat-Modus ist.
     *
     * @param message Die zu sendende Nachricht
     * @throws IOException bei Kommunikationsfehlern
     */
    public void sendMessage(String message) throws IOException {
        if (readyForChat && connected) {
            output.writeUTF(message);
            output.flush();
        }
    }


    /**
     * Prueft, ob dieser Client bereit fuer Chat-Nachrichten ist.
     *
     * @return true wenn bereit, sonst false
     */
    public boolean isReadyForChat() {
        return readyForChat;
    }


    /**
     * Trennt die Verbindung zum Client mit einer Nachricht.
     *
     * @param reason Grund fuer die Trennung
     */
    public void disconnect(String reason) {
        try {
            if (connected && output != null) {
                // Trennungsnachricht senden
                output.writeUTF("DISCONNECT:" + reason);
                output.flush();
            }
        } catch (IOException e) {
            // Ignorieren - Verbindung wird sowieso geschlossen
        } finally {
            connected = false;
            close();
        }
    }


    /**
     * Schliesst die Verbindung und gibt alle Ressourcen frei.
     * Benachrichtigt andere Clients ueber das Verlassen.
     */
    private void close() {
        try {
            connected = false;
            readyForChat = false;

            // Andere Clients benachrichtigen, falls Client angemeldet war
            if (username != null) {
                server.broadcast("<<< " + username + " hat den Chat verlassen", this);
                server.removeClient(this);

                // Raumliste aktualisieren falls Raum geloescht wurde
                broadcastRoomListToAll();
            }

            // Streams und Socket schliessen
            if (input != null) input.close();
            if (output != null) output.close();
            if (socket != null && !socket.isClosed()) socket.close();

            server.log("ClientHandler geschlossen: " + username);
        } catch (IOException e) {
            server.log("Fehler beim Schliessen: " + e.getMessage());
        }
    }


    /**
     * Gibt den Benutzernamen dieses Clients zurueck.
     *
     * @return Benutzername oder null wenn nicht angemeldet
     */
    public String getUsername() {
        return username;
    }
}