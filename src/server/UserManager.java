package server;

import java.util.HashMap;
import java.util.Map;

/**
 * Verwaltet alle Benutzerkonten des Chat-Systems.
 * Speichert Benutzernamen und Passwörter (im Speicher, nicht persistent).
 */
public class UserManager {

    // Map zur Speicherung von Benutzerdaten (Username -> Passwort)
    // final bedeutet, dass die Referenz nicht geändert werden kann
    private final Map<String, String> registeredUsers;


    /**
     * Konstruktor für den UserManager.
     * Initialisiert die Benutzerdatenbank mit Testbenutzern.
     */
    public UserManager() {
        this.registeredUsers = new HashMap<>();

        // Testbenutzer für Entwicklung/Tests hinzufügen
        registeredUsers.put("admin", "admin123");
        registeredUsers.put("test", "test");
    }


    /**
     * Prüft, ob ein Benutzername bereits existiert.
     *
     * @param username Der zu prüfende Benutzername
     * @return true wenn der Benutzer existiert, sonst false
     */
    public boolean userExists(String username) {
        return registeredUsers.containsKey(username);
    }


    /**
     * Registriert einen neuen Benutzer.
     *
     * @param username Der gewünschte Benutzername
     * @param password Das Passwort des Benutzers
     * @return true wenn Registrierung erfolgreich, false wenn Username schon vergeben
     */
    public boolean registerUser(String username, String password) {
        // Prüfe, ob Username bereits vergeben ist
        if (userExists(username)) {
            return false;
        }

        // Füge neuen Benutzer hinzu
        registeredUsers.put(username, password);
        return true;
    }


    /**
     * Validiert die Anmeldedaten eines Benutzers.
     *
     * @param username Der Benutzername
     * @param password Das zu prüfende Passwort
     * @return true wenn die Kombination korrekt ist, sonst false
     */
    public boolean validatePassword(String username, String password) {
        // Prüfe, ob Benutzer existiert
        if (!userExists(username)) {
            return false;
        }

        // Vergleiche eingegebenes Passwort mit gespeichertem Passwort
        return registeredUsers.get(username).equals(password);
    }


    /**
     * Gibt die Anzahl der registrierten Benutzer zurück.
     *
     * @return Anzahl der Benutzer
     */
    public int getUserCount() {
        return registeredUsers.size();
    }
}