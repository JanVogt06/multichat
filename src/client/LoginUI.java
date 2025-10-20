package client;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.Scanner;

/**
 * Benutzeroberfläche für Login und Registrierung.
 * Verwaltet die Interaktion mit dem Benutzer während des Anmeldevorgangs.
 */
public class LoginUI {

    // Eingabestrom vom Server
    private final DataInputStream input;

    // Ausgabestrom zum Server
    private final DataOutputStream output;

    // Scanner für Benutzereingaben
    private final Scanner scanner;


    /**
     * Konstruktor für die LoginUI.
     *
     * @param input Eingabestrom vom Server
     * @param output Ausgabestrom zum Server
     * @param scanner Scanner für Benutzereingaben
     */
    public LoginUI(DataInputStream input, DataOutputStream output, Scanner scanner) {
        this.input = input;
        this.output = output;
        this.scanner = scanner;
    }


    /**
     * Zeigt das Login-Menü an und verarbeitet Benutzereingaben.
     * Gibt den Benutzernamen nach erfolgreichem Login zurück.
     *
     * @return Benutzername bei erfolgreichem Login, null wenn Benutzer abbricht
     */
    public String showLoginMenu() {
        // Überschrift anzeigen
        System.out.println("=".repeat(50));
        System.out.println("           CHAT-ANMELDUNG");
        System.out.println("=".repeat(50));

        // Hauptschleife für Menü
        while (true) {
            // Menüoptionen anzeigen
            System.out.println("\n1) Login");
            System.out.println("2) Registrierung");
            System.out.println("3) Beenden");
            System.out.print("\nWähle Option (1-3): ");

            // Benutzereingabe lesen und Leerzeichen entfernen
            String choice = scanner.nextLine().trim();

            // Switch-Statement zur Verarbeitung der Auswahl
            // Moderner als if-else und ab Java 14 auch mit -> Syntax möglich
            switch (choice) {
                case "1":
                    // Login durchführen
                    String user = performLogin();
                    if (user != null) {
                        // Login erfolgreich, Username zurückgeben
                        return user;
                    }
                    break;

                case "2":
                    // Registrierung durchführen
                    performRegistration();
                    break;

                case "3":
                    // Programm beenden
                    return null;

                default:
                    // Ungültige Eingabe
                    System.out.println("Ungültige Eingabe!");
            }
        }
    }


    /**
     * Führt einen Login-Versuch durch.
     *
     * @return Benutzername bei Erfolg, null bei Fehler
     */
    private String performLogin() {
        System.out.println("\n--- LOGIN ---");

        // Benutzername eingeben
        System.out.print("Username: ");
        String user = scanner.nextLine().trim();

        // Passwort eingeben
        System.out.print("Passwort: ");
        String pass = scanner.nextLine().trim();

        // Validierung: Leere Felder nicht erlauben
        if (user.isEmpty() || pass.isEmpty()) {
            System.out.println("Felder dürfen nicht leer sein!");
            return null;
        }

        try {
            // Login-Request an Server senden (Format: LOGIN:username:password)
            output.writeUTF("LOGIN:" + user + ":" + pass);
            output.flush();

            // Antwort vom Server lesen
            String response = input.readUTF();

            // Antwort aufteilen (Format: STATUS:message)
            String[] parts = response.split(":", 2);

            if (parts[0].equals("SUCCESS")) {
                // Login erfolgreich
                System.out.println("\n" + parts[1]);
                System.out.println("Willkommen, " + user + "!\n");
                return user;
            } else {
                // Login fehlgeschlagen
                System.out.println("\n" + parts[1] + "\n");
                return null;
            }

        } catch (IOException e) {
            System.err.println("Verbindungsfehler: " + e.getMessage());
            return null;
        }
    }


    /**
     * Führt eine Registrierung durch.
     */
    private void performRegistration() {
        System.out.println("\n--- REGISTRIERUNG ---");

        // Benutzername eingeben
        System.out.print("Neuer Username: ");
        String user = scanner.nextLine().trim();

        // Passwort eingeben
        System.out.print("Neues Passwort: ");
        String pass = scanner.nextLine().trim();

        // Validierung: Leere Felder nicht erlauben
        if (user.isEmpty() || pass.isEmpty()) {
            System.out.println("Felder dürfen nicht leer sein!");
            return;
        }

        // Validierung: Doppelpunkt ist Trennzeichen im Protokoll und daher nicht erlaubt
        if (user.contains(":") || pass.contains(":")) {
            System.out.println("Doppelpunkt ':' ist nicht erlaubt!");
            return;
        }

        try {
            // Registrierungs-Request an Server senden (Format: REGISTER:username:password)
            output.writeUTF("REGISTER:" + user + ":" + pass);
            output.flush();

            // Antwort vom Server lesen
            String response = input.readUTF();

            // Antwort aufteilen (Format: STATUS:message)
            String[] parts = response.split(":", 2);

            if (parts[0].equals("SUCCESS")) {
                // Registrierung erfolgreich
                System.out.println("\n" + parts[1]);
                System.out.println("Du kannst dich jetzt einloggen!\n");
            } else {
                // Registrierung fehlgeschlagen
                System.out.println("\n" + parts[1] + "\n");
            }

        } catch (IOException e) {
            System.err.println("Verbindungsfehler: " + e.getMessage());
        }
    }
}