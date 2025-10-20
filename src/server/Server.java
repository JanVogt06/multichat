package server;

import java.io.*;
import java.net.*;
import java.util.HashMap;
import java.util.Map;

public class Server {

    private static final int PORT = 3143;
    private ServerSocket serverSocket;
    private boolean running;

    // Speichert registrierte User (username → password)
    private Map<String, String> registeredUsers;


    public Server() {
        this.running = false;
        this.registeredUsers = new HashMap<>();

        // Testuser zum Entwickeln (optional)
        registeredUsers.put("admin", "admin123");
        registeredUsers.put("test", "test");
    }


    public void start() {
        try {
            serverSocket = new ServerSocket(PORT);
            running = true;

            System.out.println("✓ Server gestartet auf Port " + PORT);
            System.out.println("✓ Registrierte User: " + registeredUsers.size());
            System.out.println("✓ Warte auf Clients...\n");

            while (running) {
                Socket clientSocket = serverSocket.accept();

                System.out.println("=".repeat(50));
                System.out.println("✓ Neuer Client verbunden: " +
                        clientSocket.getInetAddress());
                System.out.println("=".repeat(50) + "\n");

                handleClient(clientSocket);
            }

        } catch (IOException e) {
            System.err.println("✗ Fehler beim Starten des Servers: " + e.getMessage());
        }
    }


    private void handleClient(Socket clientSocket) {
        try {
            DataInputStream input = new DataInputStream(clientSocket.getInputStream());
            DataOutputStream output = new DataOutputStream(clientSocket.getOutputStream());

            // Authentifizierung MUSS zuerst erfolgen
            String username = authenticate(input, output);

            if (username == null) {
                // Authentifizierung fehlgeschlagen
                System.out.println("✗ Client konnte sich nicht authentifizieren\n");
                clientSocket.close();
                return;
            }

            System.out.println("✓ User '" + username + "' ist jetzt eingeloggt\n");

            // Normaler Chat (wie vorher)
            chatLoop(input, output, username);

            // Aufräumen
            input.close();
            output.close();
            clientSocket.close();
            System.out.println("✓ User '" + username + "' hat sich abgemeldet");
            System.out.println("=".repeat(50) + "\n");

        } catch (IOException e) {
            System.err.println("✗ Fehler bei Client-Kommunikation: " + e.getMessage());
        }
    }


    // Authentifizierungs-Logik
    private String authenticate(DataInputStream input, DataOutputStream output)
            throws IOException {

        System.out.println("→ Warte auf Authentifizierung...");

        while (true) {
            String message = input.readUTF();
            System.out.println("← Empfangen: " + message);

            String[] parts = message.split(":", 3);
            String command = parts[0];

            if (command.equals("REGISTER")) {
                // Format: REGISTER:username:password
                if (parts.length != 3) {
                    output.writeUTF("ERROR:Ungültiges Format");
                    continue;
                }

                String username = parts[1];
                String password = parts[2];

                if (registeredUsers.containsKey(username)) {
                    output.writeUTF("ERROR:Username bereits vergeben");
                    System.out.println("✗ Registrierung fehlgeschlagen: Username existiert");
                } else {
                    registeredUsers.put(username, password);
                    output.writeUTF("SUCCESS:Registrierung erfolgreich");
                    System.out.println("✓ Neuer User registriert: " + username);
                }
                output.flush();

            } else if (command.equals("LOGIN")) {
                // Format: LOGIN:username:password
                if (parts.length != 3) {
                    output.writeUTF("ERROR:Ungültiges Format");
                    continue;
                }

                String username = parts[1];
                String password = parts[2];

                if (!registeredUsers.containsKey(username)) {
                    output.writeUTF("ERROR:Username nicht gefunden");
                    System.out.println("✗ Login fehlgeschlagen: Username existiert nicht");
                } else if (!registeredUsers.get(username).equals(password)) {
                    output.writeUTF("ERROR:Falsches Passwort");
                    System.out.println("✗ Login fehlgeschlagen: Falsches Passwort");
                } else {
                    output.writeUTF("SUCCESS:Login erfolgreich");
                    System.out.println("✓ Login erfolgreich: " + username);
                    output.flush();
                    return username;  // ← Erfolgreich eingeloggt!
                }
                output.flush();

            } else {
                output.writeUTF("ERROR:Unbekannter Befehl");
                output.flush();
            }
        }
    }


    // Chat-Schleife (nach erfolgreicher Anmeldung)
    private void chatLoop(DataInputStream input, DataOutputStream output, String username) {
        try {
            while (true) {
                try {
                    String message = input.readUTF();
                    System.out.println("← " + username + ": " + message);

                    String response = "[Echo] " + message;
                    System.out.println("→ " + username + ": " + response + "\n");

                    output.writeUTF(response);
                    output.flush();

                } catch (EOFException e) {
                    System.out.println("✓ " + username + " hat Verbindung getrennt\n");
                    break;
                }
            }
        } catch (IOException e) {
            System.err.println("✗ Fehler im Chat: " + e.getMessage());
        }
    }


    public void stop() {
        try {
            running = false;
            if (serverSocket != null && !serverSocket.isClosed()) {
                serverSocket.close();
                System.out.println("✓ Server gestoppt");
            }
        } catch (IOException e) {
            System.err.println("✗ Fehler beim Stoppen: " + e.getMessage());
        }
    }


    public static void main(String[] args) {
        Server server = new Server();
        server.start();
    }
}