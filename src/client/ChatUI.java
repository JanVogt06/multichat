package client;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.EOFException;
import java.io.IOException;
import java.util.Scanner;

/**
 * Benutzeroberfläche für den Chat.
 * Verwaltet das Senden und Empfangen von Nachrichten.
 * Verwendet einen separaten Thread für den Nachrichtenempfang.
 */
public class ChatUI {

    // Eingabestrom vom Server
    private final DataInputStream input;

    // Ausgabestrom zum Server
    private final DataOutputStream output;

    // Scanner für Benutzereingaben
    private final Scanner scanner;

    // Benutzername des angemeldeten Benutzers
    private final String username;

    // Flag für Laufzeitstatus (volatile für Thread-Sicherheit)
    private volatile boolean running;

    // Thread für das Empfangen von Nachrichten
    private Thread listenerThread;


    /**
     * Konstruktor für die ChatUI.
     *
     * @param input Eingabestrom vom Server
     * @param output Ausgabestrom zum Server
     * @param scanner Scanner für Benutzereingaben
     * @param username Benutzername des angemeldeten Benutzers
     */
    public ChatUI(DataInputStream input, DataOutputStream output,
                  Scanner scanner, String username) {
        this.input = input;
        this.output = output;
        this.scanner = scanner;
        this.username = username;
        this.running = true;
    }


    /**
     * Startet die Chat-Oberfläche.
     * Startet den Listener-Thread und wartet auf Benutzereingaben.
     */
    public void start() {
        // Willkommensnachricht anzeigen
        System.out.println("=".repeat(50));
        System.out.println("Chat gestartet! Eingeloggt als: " + username);
        System.out.println("Tippe 'exit' zum Beenden.");
        System.out.println("=".repeat(50) + "\n");

        // Thread für Nachrichtenempfang starten
        startMessageListener();

        // READY-Signal an Server senden, um anzuzeigen, dass Client bereit ist
        try {
            output.writeUTF("READY");
            output.flush();
        } catch (IOException e) {
            System.err.println("Fehler beim Senden von READY: " + e.getMessage());
        }

        try {
            // Hauptschleife: Liest Benutzereingaben und sendet Nachrichten
            while (running) {
                // Eingabeaufforderung
                System.out.print("> ");
                String message = scanner.nextLine();

                // Beenden bei 'exit'
                if (message.equalsIgnoreCase("exit")) {
                    System.out.println("\nChat beendet.");
                    stopMessageListener();
                    break;
                }

                // Leere Nachrichten ignorieren
                if (message.trim().isEmpty()) {
                    continue;
                }

                // Eigene Nachricht lokal anzeigen
                System.out.println("[" + username + "] " + message);

                // Nachricht an Server senden
                sendMessage(message);
            }

        } catch (Exception e) {
            System.err.println("Chat-Fehler: " + e.getMessage());
        }
    }


    /**
     * Startet einen Thread, der kontinuierlich auf Nachrichten vom Server wartet.
     * Dieser Thread läuft parallel zur Hauptschleife.
     */
    private void startMessageListener() {
        listenerThread = new Thread(() -> {
            try {
                // Endlosschleife für Nachrichtenempfang
                while (running) {
                    // Blockiert bis Nachricht vom Server kommt
                    String message = input.readUTF();

                    // Nachricht anzeigen
                    System.out.println(message);

                    // Eingabeaufforderung erneut anzeigen
                    System.out.print("> ");
                    System.out.flush();
                }
            } catch (EOFException e) {
                // Verbindung zum Server wurde beendet
                if (running) {
                    System.out.println("\nVerbindung zum Server unterbrochen.");
                }
            } catch (IOException e) {
                // Fehler beim Empfangen
                if (running) {
                    System.err.println("\nFehler beim Empfangen: " + e.getMessage());
                }
            }
        });

        // Als Daemon-Thread markieren (beendet sich automatisch beim Programmende)
        listenerThread.setDaemon(true);

        // Thread starten
        listenerThread.start();
        System.out.println("Nachrichten-Listener gestartet\n");
    }


    /**
     * Stoppt den Listener-Thread.
     * Wartet maximal 1 Sekunde auf Beendigung.
     */
    private void stopMessageListener() {
        running = false;

        if (listenerThread != null && listenerThread.isAlive()) {
            try {
                // Warte maximal 1000ms auf Thread-Beendigung
                listenerThread.join(1000);
            } catch (InterruptedException e) {
                // Thread wurde während des Wartens unterbrochen
                // Interrupted-Status wiederherstellen
                Thread.currentThread().interrupt();
            }
        }
    }


    /**
     * Sendet eine Nachricht an den Server.
     *
     * @param message Die zu sendende Nachricht
     * @throws IOException bei Verbindungsfehlern
     */
    private void sendMessage(String message) throws IOException {
        output.writeUTF(message);
        output.flush();
    }
}