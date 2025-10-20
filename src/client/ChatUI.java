package client;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.EOFException;
import java.io.IOException;
import java.util.Scanner;

public class ChatUI {

    private final DataInputStream input;
    private final DataOutputStream output;
    private final Scanner scanner;
    private final String username;
    private volatile boolean running;  // Thread-sicher
    private Thread listenerThread;


    public ChatUI(DataInputStream input, DataOutputStream output,
                  Scanner scanner, String username) {
        this.input = input;
        this.output = output;
        this.scanner = scanner;
        this.username = username;
        this.running = true;
    }


    public void start() {
        System.out.println("=".repeat(50));
        System.out.println("Chat gestartet! Eingeloggt als: " + username);
        System.out.println("Tippe 'exit' zum Beenden.");
        System.out.println("=".repeat(50) + "\n");

        // Starte den Nachrichten-Listener-Thread
        startMessageListener();

        // Signalisiere dem Server: "Ich bin bereit für Chat-Nachrichten"
        try {
            output.writeUTF("READY");
            output.flush();
        } catch (IOException e) {
            System.err.println("✗ Fehler beim Senden von READY: " + e.getMessage());
        }

        try {
            // Haupt-Thread: Liest Benutzereingaben
            while (running) {
                System.out.print("> ");
                String message = scanner.nextLine();

                if (message.equalsIgnoreCase("exit")) {
                    System.out.println("\n✓ Chat beendet.");
                    stopMessageListener();
                    break;
                }

                if (message.trim().isEmpty()) {
                    continue;
                }

                // Zeige formatierte Nachricht
                System.out.println("[" + username + "] " + message);

                sendMessage(message);
            }

        } catch (Exception e) {
            System.err.println("✗ Chat-Fehler: " + e.getMessage());
        }
    }


    private void startMessageListener() {
        listenerThread = new Thread(() -> {
            try {
                while (running) {
                    String message = input.readUTF();

                    // Nachrichten vom Server anzeigen
                    System.out.println(message);
                    System.out.print("> ");
                    System.out.flush();
                }
            } catch (EOFException e) {
                if (running) {
                    System.out.println("\n✗ Verbindung zum Server unterbrochen.");
                }
            } catch (IOException e) {
                if (running) {
                    System.err.println("\n✗ Fehler beim Empfangen: " + e.getMessage());
                }
            }
        });

        listenerThread.setDaemon(true);
        listenerThread.start();
        System.out.println("✓ Nachrichten-Listener gestartet\n");
    }


    private void stopMessageListener() {
        running = false;
        if (listenerThread != null && listenerThread.isAlive()) {
            try {
                listenerThread.join(1000);  // Warte max. 1 Sekunde
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
    }


    private void sendMessage(String message) throws IOException {
        output.writeUTF(message);
        output.flush();
    }
}