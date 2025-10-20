package server;

import java.io.*;
import java.net.Socket;

public class ClientHandler extends Thread {

    private final Socket socket;              // ← final
    private final UserManager userManager;    // ← final
    private DataInputStream input;
    private DataOutputStream output;
    private String username;


    public ClientHandler(Socket socket, UserManager userManager) {
        this.socket = socket;
        this.userManager = userManager;
    }


    @Override
    public void run() {
        try {
            input = new DataInputStream(socket.getInputStream());
            output = new DataOutputStream(socket.getOutputStream());

            System.out.println("✓ Neuer Client verbunden: " + socket.getInetAddress());

            // Authentifizierung
            username = authenticate();

            // ← Fix: Diese Prüfung ist tatsächlich unnötig, weil authenticate()
            //    entweder einen String zurückgibt oder eine Exception wirft
            // Wir behalten sie aber zur Sicherheit (oder entfernen sie)

            System.out.println("✓ User '" + username + "' eingeloggt");

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
                    return loginResult;  // ← Login erfolgreich
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


    private void chatLoop() throws IOException {
        while (true) {
            try {
                String message = input.readUTF();
                System.out.println("← " + username + ": " + message);

                String response = "[Echo] " + message;
                output.writeUTF(response);
                output.flush();

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


    private void close() {
        try {
            if (input != null) input.close();
            if (output != null) output.close();
            if (socket != null) socket.close();
            System.out.println("✓ ClientHandler geschlossen: " + username);
        } catch (IOException e) {
            System.err.println("✗ Fehler beim Schließen: " + e.getMessage());
        }
    }


    // ← Methode wird später für Broadcast gebraucht!
    public String getUsername() {
        return username;
    }
}