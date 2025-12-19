package server;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * Verwaltet alle Benutzerkonten des Chat-Systems.
 * Speichert Benutzernamen und Passwörter (zur Zeit im Speicher).
 * Zusätzliche Funktionalität: Bannen von Benutzern (in-memory).
 */
public class UserManager {

    private final Map<String, String> registeredUsers;
    private final Set<String> bannedUsers;

    public UserManager() {
        this.registeredUsers = new HashMap<>();
        this.bannedUsers = new HashSet<>();

        // Testbenutzer
        registeredUsers.put("admin", "admin123");
        registeredUsers.put("test", "test");
    }

    public synchronized boolean userExists(String username) {
        return registeredUsers.containsKey(username);
    }

    public synchronized boolean registerUser(String username, String password) {
        if (userExists(username)) {
            return false;
        }
        registeredUsers.put(username, password);
        return true;
    }

    public synchronized boolean validatePassword(String username, String password) {
        if (!userExists(username)) return false;
        return registeredUsers.get(username).equals(password);
    }

    public synchronized int getUserCount() {
        return registeredUsers.size();
    }

    /* ----------------- Ban-Funktionen ----------------- */

    public synchronized boolean isBanned(String username) {
        return bannedUsers.contains(username);
    }

    public synchronized void banUser(String username) {
        bannedUsers.add(username);
    }

    public synchronized void unbanUser(String username) {
        bannedUsers.remove(username);
    }

    public synchronized Set<String> getBannedUsers() {
        return new HashSet<>(bannedUsers);
    }
}
