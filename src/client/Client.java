package client;

import java.io.*;
import java.net.*;
import java.util.Scanner;

public class Client {

    private static final String SERVER_HOST = "localhost";
    private static final int SERVER_PORT = 3143;

    private Socket socket;
    private DataOutputStream output;
    private DataInputStream input;
    private Scanner scanner;
    private String username;  // NEU: Speichert Username nach Login


    public void connect() {
        try {
            System.out.println("Verbinde zu Server " + SERVER_HOST + ":" + SERVER_PORT + "...");

            socket = new Socket(SERVER_HOST, SERVER_PORT);
            System.out.println("✓ Verbunden mit Server!\n");

            output = new DataOutputStream(socket.getOutputStream());
            input = new DataInputStream(socket.getInputStream());
            scanner = new Scanner(System.in);

            // NEU: Erst Anmeldung, dann Chat
            if (login()) {
                startChat();
            }

            disconnect();

        } catch (UnknownHostException e) {
            System.err.println("✗ Server nicht gefunden: " + SERVER_HOST);
        } catch (IOException e) {
            System.err.println("✗ Verbindung fehlgeschlagen: " + e.getMessage());
        }
    }


    // NEU: Login/Registrierungs-Dialog
    private boolean login() {
        System.out.println("=".repeat(50));
        System.out.println("           CHAT-ANMELDUNG");
        System.out.println("=".repeat(50));

        while (true) {
            System.out.println("\n1) Login");
            System.out.println("2) Registrierung");
            System.out.println("3) Beenden");
            System.out.print("\nWähle Option (1-3): ");

            String choice = scanner.nextLine().trim();

            if (choice.equals("1")) {
                if (performLogin()) {
                    return true;  // ← Login erfolgreich!
                }
            } else if (choice.equals("2")) {
                performRegistration();
                // Nach Registrierung nochmal ins Menü
            } else if (choice.equals("3")) {
                System.out.println("Auf Wiedersehen!");
                return false;  // ← Beenden
            } else {
                System.out.println("✗ Ungültige Eingabe!");
            }
        }
    }


    private boolean performLogin() {
        System.out.println("\n--- LOGIN ---");
        System.out.print("Username: ");
        String user = scanner.nextLine().trim();

        System.out.print("Passwort: ");
        String pass = scanner.nextLine().trim();

        if (user.isEmpty() || pass.isEmpty()) {
            System.out.println("✗ Username und Passwort dürfen nicht leer sein!");
            return false;
        }

        try {
            // Sende LOGIN-Befehl
            String message = "LOGIN:" + user + ":" + pass;
            output.writeUTF(message);
            output.flush();

            // Warte auf Antwort
            String response = input.readUTF();
            String[] parts = response.split(":", 2);

            if (parts[0].equals("SUCCESS")) {
                this.username = user;
                System.out.println("\n✓ " + parts[1]);
                System.out.println("✓ Willkommen, " + username + "!\n");
                return true;
            } else {
                System.out.println("\n✗ " + parts[1] + "\n");
                return false;
            }

        } catch (IOException e) {
            System.err.println("✗ Verbindungsfehler: " + e.getMessage());
            return false;
        }
    }


    private void performRegistration() {
        System.out.println("\n--- REGISTRIERUNG ---");
        System.out.print("Neuer Username: ");
        String user = scanner.nextLine().trim();

        System.out.print("Neues Passwort: ");
        String pass = scanner.nextLine().trim();

        if (user.isEmpty() || pass.isEmpty()) {
            System.out.println("✗ Username und Passwort dürfen nicht leer sein!");
            return;
        }

        if (user.contains(":") || pass.contains(":")) {
            System.out.println("✗ Doppelpunkt ':' ist nicht erlaubt!");
            return;
        }

        try {
            // Sende REGISTER-Befehl
            String message = "REGISTER:" + user + ":" + pass;
            output.writeUTF(message);
            output.flush();

            // Warte auf Antwort
            String response = input.readUTF();
            String[] parts = response.split(":", 2);

            if (parts[0].equals("SUCCESS")) {
                System.out.println("\n✓ " + parts[1]);
                System.out.println("Du kannst dich jetzt einloggen!\n");
            } else {
                System.out.println("\n✗ " + parts[1] + "\n");
            }

        } catch (IOException e) {
            System.err.println("✗ Verbindungsfehler: " + e.getMessage());
        }
    }


    private void startChat() {
        System.out.println("=".repeat(50));
        System.out.println("Chat gestartet! Eingeloggt als: " + username);
        System.out.println("Tippe 'exit' zum Beenden.");
        System.out.println("=".repeat(50) + "\n");

        try {
            while (true) {
                System.out.print(username + ": ");
                String message = scanner.nextLine();

                if (message.equalsIgnoreCase("exit")) {
                    System.out.println("\n✓ Chat beendet.");
                    break;
                }

                if (message.trim().isEmpty()) {
                    continue;
                }

                sendMessage(message);
                receiveMessage();
                System.out.println();
            }

        } catch (Exception e) {
            System.err.println("✗ Fehler im Chat: " + e.getMessage());
        }
    }


    public void sendMessage(String message) {
        try {
            output.writeUTF(message);
            output.flush();
        } catch (IOException e) {
            System.err.println("✗ Fehler beim Senden: " + e.getMessage());
        }
    }


    public void receiveMessage() {
        try {
            String message = input.readUTF();
            System.out.println("Server: " + message);
        } catch (IOException e) {
            System.err.println("✗ Fehler beim Empfangen: " + e.getMessage());
        }
    }


    public void disconnect() {
        try {
            if (scanner != null) scanner.close();
            if (output != null) output.close();
            if (input != null) input.close();
            if (socket != null && !socket.isClosed()) {
                socket.close();
            }
            System.out.println("✓ Verbindung getrennt");

        } catch (IOException e) {
            System.err.println("✗ Fehler beim Trennen: " + e.getMessage());
        }
    }


    public static void main(String[] args) {
        Client client = new Client();
        client.connect();
    }
}