package server;

import java.io.*;
import java.net.Socket;

public class ClientHandler extends Thread {

    private final Socket socket;
    private final UserManager userManager;
    private final Server server;
    private DataInputStream input;
    private DataOutputStream output;
    private String username;
    private volatile boolean readyForChat = false;  // ← NEU: Chat-Status


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

            System.out.println("✓ Neuer Client verbunden: " + socket.getInetAddress());

            // Authentifizierung
            username = authenticate();

            System.out.println("✓ User '" + username + "' eingeloggt");

            // Warte auf READY-Signal vom Client
            String readySignal = input.readUTF();
            if (readySignal.equals("READY")) {
                System.out.println("✓ Client '" + username + "' ist bereit für Chat");

                // Markiere Client als bereit für Chat-Nachrichten
                readyForChat = true;

                // Jetzt erst Userliste senden und Broadcast
                sendUserList();
                server.broadcast(">>> " + username + " hat den Chat betreten", this);
            }

            // Chat-Loop
            chatLoop();

        } catch (IOException e) {
            System.err.println("✗ Fehler bei Client " + username + ": " + e.getMessage());
        } finally {
            close();
        }
    }


    private String authenticate() throws IOException {
        System.out.println("→ Warte auf Authentifizierung...");

        while (true) {
            String message = input.readUTF();
            System.out.println("← Empfangen: " + message);

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
            System.out.println("✓ Neuer User: " + user);
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


    private void chatLoop() throws IOException {
        while (true) {
            try {
                String message = input.readUTF();
                System.out.println("← " + username + ": " + message);

                // Broadcast an alle Clients
                String formattedMessage = "[" + username + "] " + message;
                server.broadcast(formattedMessage, this);

            } catch (EOFException e) {
                System.out.println("✓ " + username + " hat sich abgemeldet");
                break;
            }
        }
    }


    private void sendResponse(String status, String message) throws IOException {
        output.writeUTF(status + ":" + message);
        output.flush();
    }


    /**
     * Sendet eine Nachricht an diesen Client (wird vom Server aufgerufen)
     * Nur wenn Client im Chat-Modus ist!
     */
    public void sendMessage(String message) throws IOException {
        if (readyForChat) {  // ← Nur senden, wenn bereit
            output.writeUTF(message);
            output.flush();
        }
    }


    /**
     * Prüft, ob dieser Client bereit für Chat-Nachrichten ist
     */
    public boolean isReadyForChat() {
        return readyForChat;
    }


    private void close() {
        try {
            // Benachrichtige andere Clients
            if (username != null) {
                server.broadcast("<<< " + username + " hat den Chat verlassen", this);
                server.removeClient(this);
            }

            if (input != null) input.close();
            if (output != null) output.close();
            if (socket != null) socket.close();

            System.out.println("✓ ClientHandler geschlossen: " + username);
        } catch (IOException e) {
            System.err.println("✗ Fehler beim Schließen: " + e.getMessage());
        }
    }


    public String getUsername() {
        return username;
    }
}