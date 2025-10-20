package client;

import java.io.*;
import java.net.*;
import java.util.Scanner;

/**
 * Hauptklasse des Chat-Clients.
 * Stellt die Verbindung zum Server her und koordiniert Login und Chat.
 */
public class Client {

    // Server-Adresse (localhost für lokale Tests)
    private static final String SERVER_HOST = "localhost";

    // Port des Servers
    private static final int SERVER_PORT = 3143;

    // Socket-Verbindung zum Server
    private Socket socket;

    // Ausgabestrom zum Server
    private DataOutputStream output;

    // Eingabestrom vom Server
    private DataInputStream input;

    // Scanner für Benutzereingaben
    private Scanner scanner;


    /**
     * Stellt Verbindung zum Server her und startet den Chat-Prozess.
     * Durchläuft folgende Schritte:
     * 1. Verbindung aufbauen
     * 2. Login/Registrierung
     * 3. Chat starten
     * 4. Verbindung trennen
     */
    public void connect() {
        try {
            System.out.println("Verbinde zu " + SERVER_HOST + ":" + SERVER_PORT + "...");

            // Socket zum Server öffnen
            socket = new Socket(SERVER_HOST, SERVER_PORT);

            // Streams initialisieren
            output = new DataOutputStream(socket.getOutputStream());
            input = new DataInputStream(socket.getInputStream());
            scanner = new Scanner(System.in);

            System.out.println("Verbunden!\n");

            // Login-Prozess starten
            LoginUI loginUI = new LoginUI(input, output, scanner);
            String username = loginUI.showLoginMenu();

            // Wenn Benutzer abbricht (null), Verbindung trennen
            if (username == null) {
                System.out.println("Auf Wiedersehen!");
                disconnect();
                return;
            }

            // Chat starten
            ChatUI chatUI = new ChatUI(input, output, scanner, username);
            chatUI.start();

            // Nach Verlassen des Chats Verbindung trennen
            disconnect();

        } catch (UnknownHostException e) {
            // Server-Adresse konnte nicht aufgelöst werden
            System.err.println("Server nicht gefunden");
        } catch (IOException e) {
            // Verbindungsfehler
            System.err.println("Verbindungsfehler: " + e.getMessage());
        }
    }


    /**
     * Trennt die Verbindung und gibt alle Ressourcen frei.
     */
    private void disconnect() {
        try {
            // Scanner schließen
            if (scanner != null) scanner.close();

            // Streams schließen
            if (output != null) output.close();
            if (input != null) input.close();

            // Socket schließen
            if (socket != null) socket.close();

            System.out.println("Verbindung getrennt");
        } catch (IOException e) {
            System.err.println("Fehler beim Trennen: " + e.getMessage());
        }
    }


    /**
     * Main-Methode zum Starten des Clients.
     *
     * @param args Kommandozeilenargumente (nicht verwendet)
     */
    public static void main(String[] args) {
        Client client = new Client();
        client.connect();
    }
}