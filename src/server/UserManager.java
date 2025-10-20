package server;

import java.util.HashMap;
import java.util.Map;

public class UserManager {

    private final Map<String, String> registeredUsers;  // ← final


    public UserManager() {
        this.registeredUsers = new HashMap<>();

        // Testuser
        registeredUsers.put("admin", "admin123");
        registeredUsers.put("test", "test");
    }


    public boolean userExists(String username) {
        return registeredUsers.containsKey(username);
    }


    public boolean registerUser(String username, String password) {
        if (userExists(username)) {
            return false;
        }
        registeredUsers.put(username, password);
        return true;
    }


    public boolean validatePassword(String username, String password) {
        if (!userExists(username)) {
            return false;
        }
        return registeredUsers.get(username).equals(password);
    }


    public int getUserCount() {
        return registeredUsers.size();
    }
}