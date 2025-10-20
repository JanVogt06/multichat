package client;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.Scanner;

public class LoginUI {

    private final DataInputStream input;      // ← final
    private final DataOutputStream output;    // ← final
    private final Scanner scanner;            // ← final


    public LoginUI(DataInputStream input, DataOutputStream output, Scanner scanner) {
        this.input = input;
        this.output = output;
        this.scanner = scanner;
    }


    public String showLoginMenu() {
        System.out.println("=".repeat(50));
        System.out.println("           CHAT-ANMELDUNG");
        System.out.println("=".repeat(50));

        while (true) {
            System.out.println("\n1) Login");
            System.out.println("2) Registrierung");
            System.out.println("3) Beenden");
            System.out.print("\nWähle Option (1-3): ");

            String choice = scanner.nextLine().trim();

            // ← Switch statt if (moderner, ab Java 14 auch mit ->)
            switch (choice) {
                case "1":
                    String user = performLogin();
                    if (user != null) {
                        return user;  // Login erfolgreich
                    }
                    break;

                case "2":
                    performRegistration();
                    break;

                case "3":
                    return null;  // Beenden

                default:
                    System.out.println("✗ Ungültige Eingabe!");
            }
        }
    }


    private String performLogin() {
        System.out.println("\n--- LOGIN ---");
        System.out.print("Username: ");
        String user = scanner.nextLine().trim();

        System.out.print("Passwort: ");
        String pass = scanner.nextLine().trim();

        if (user.isEmpty() || pass.isEmpty()) {
            System.out.println("✗ Felder dürfen nicht leer sein!");
            return null;
        }

        try {
            output.writeUTF("LOGIN:" + user + ":" + pass);
            output.flush();

            String response = input.readUTF();
            String[] parts = response.split(":", 2);

            if (parts[0].equals("SUCCESS")) {
                System.out.println("\n✓ " + parts[1]);
                System.out.println("✓ Willkommen, " + user + "!\n");
                return user;
            } else {
                System.out.println("\n✗ " + parts[1] + "\n");
                return null;
            }

        } catch (IOException e) {
            System.err.println("✗ Verbindungsfehler: " + e.getMessage());
            return null;
        }
    }


    private void performRegistration() {
        System.out.println("\n--- REGISTRIERUNG ---");
        System.out.print("Neuer Username: ");
        String user = scanner.nextLine().trim();

        System.out.print("Neues Passwort: ");
        String pass = scanner.nextLine().trim();

        if (user.isEmpty() || pass.isEmpty()) {
            System.out.println("✗ Felder dürfen nicht leer sein!");
            return;
        }

        if (user.contains(":") || pass.contains(":")) {
            System.out.println("✗ Doppelpunkt ':' ist nicht erlaubt!");
            return;
        }

        try {
            output.writeUTF("REGISTER:" + user + ":" + pass);
            output.flush();

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
}