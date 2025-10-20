package client;

import java.io.*;
import java.net.*;
import java.util.Scanner;  // NEU: für Konsolen-Eingabe

public class Client {

    private static final String SERVER_HOST = "localhost";
    private static final int SERVER_PORT = 3143;

    private Socket socket;
    private DataOutputStream output;
    private DataInputStream input;
    private Scanner scanner;  // NEU: liest Tastatur-Eingabe


    public void connect() {
        try {
            System.out.println("Verbinde zu Server " + SERVER_HOST + ":" + SERVER_PORT + "...");

            socket = new Socket(SERVER_HOST, SERVER_PORT);
            System.out.println("✓ Verbunden mit Server!");

            // Streams erstellen
            output = new DataOutputStream(socket.getOutputStream());
            input = new DataInputStream(socket.getInputStream());
            System.out.println("✓ Streams erstellt\n");

            // NEU: Scanner für Tastatur-Eingabe
            scanner = new Scanner(System.in);

            // NEU: Interaktive Chat-Schleife
            startChat();

            disconnect();

        } catch (UnknownHostException e) {
            System.err.println("✗ Server nicht gefunden: " + SERVER_HOST);
        } catch (IOException e) {
            System.err.println("✗ Verbindung fehlgeschlagen: " + e.getMessage());
        }
    }


    // NEU: Interaktiver Chat
    private void startChat() {
        System.out.println("=".repeat(50));
        System.out.println("Chat gestartet!");
        System.out.println("Tippe deine Nachricht und drücke Enter.");
        System.out.println("Tippe 'exit' zum Beenden.");
        System.out.println("=".repeat(50) + "\n");

        try {
            // Endlosschleife - läuft bis "exit"
            while (true) {
                // Eingabe-Prompt
                System.out.print("Du: ");
                String message = scanner.nextLine();  // ← wartet auf Eingabe

                // Beenden wenn "exit" eingegeben wird
                if (message.equalsIgnoreCase("exit")) {
                    System.out.println("\n✓ Chat beendet.");
                    break;  // Schleife verlassen
                }

                // Leere Nachrichten ignorieren
                if (message.trim().isEmpty()) {
                    continue;  // Zurück zum Anfang der Schleife
                }

                // Nachricht senden
                sendMessage(message);

                // Antwort empfangen
                receiveMessage();

                System.out.println();  // Leerzeile für bessere Lesbarkeit
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