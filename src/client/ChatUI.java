package client;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.Scanner;

public class ChatUI {

    private final DataInputStream input;
    private final DataOutputStream output;
    private final Scanner scanner;
    private final String username;


    public ChatUI(DataInputStream input, DataOutputStream output,
                  Scanner scanner, String username) {
        this.input = input;
        this.output = output;
        this.scanner = scanner;
        this.username = username;
    }


    public void start() {
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
            System.err.println("✗ Chat-Fehler: " + e.getMessage());
        }
    }


    private void sendMessage(String message) throws IOException {
        output.writeUTF(message);
        output.flush();
    }


    private void receiveMessage() throws IOException {
        String message = input.readUTF();
        System.out.println("Server: " + message);
    }
}