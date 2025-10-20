package server;

import java.io.*;
import java.net.*;

public class Server {

    private static final int PORT = 3143;
    private ServerSocket serverSocket;
    private boolean running;


    public Server() {
        this.running = false;
    }


    public void start() {
        try {
            serverSocket = new ServerSocket(PORT);
            running = true;

            System.out.println("✓ Server gestartet auf Port " + PORT);
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
            // Streams erstellen
            DataInputStream input = new DataInputStream(clientSocket.getInputStream());
            DataOutputStream output = new DataOutputStream(clientSocket.getOutputStream());

            System.out.println("Client-Session gestartet\n");

            // NEU: Schleife für mehrere Nachrichten
            while (true) {
                try {
                    // Nachricht empfangen
                    String message = input.readUTF();
                    System.out.println("← Client: " + message);

                    // Antwort vorbereiten
                    String response = "Echo: " + message;

                    // Antwort senden
                    System.out.println("→ Server: " + response + "\n");
                    output.writeUTF(response);
                    output.flush();

                } catch (EOFException e) {
                    // EOFException = Client hat Verbindung getrennt
                    System.out.println("✓ Client hat Verbindung getrennt\n");
                    break;  // Schleife verlassen
                }
            }

            // Verbindung schließen
            input.close();
            output.close();
            clientSocket.close();
            System.out.println("✓ Client-Session beendet");
            System.out.println("=".repeat(50) + "\n");

        } catch (IOException e) {
            System.err.println("✗ Fehler bei Client-Kommunikation: " + e.getMessage());
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