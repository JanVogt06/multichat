package server;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.sql.*;
import java.util.ArrayList;
import java.util.List;

/**
 * Verwaltet alle Benutzerkonten des Chat-Systems.
 *
 * Diese Klasse verwendet SQLite als Datenbank für die dauerhafte Speicherung
 * der Benutzerdaten. Passwörter werden NICHT im Klartext gespeichert, sondern
 * als SHA-256 Hash - das bedeutet, selbst wenn jemand die Datenbank stiehlt,
 * kann er die Passwörter nicht lesen.
 */
public class UserManager {

    // ==================== KONSTANTEN ====================

    /**
     * Pfad zur SQLite-Datenbankdatei.
     * Die Datei wird automatisch erstellt, falls sie nicht existiert.
     */
    private static final String DATABASE_FILE = "users.db";

    /**
     * JDBC-Verbindungs-URL für SQLite.
     * "jdbc:sqlite:" ist das Protokoll, danach kommt der Dateipfad.
     */
    private static final String DATABASE_URL = "jdbc:sqlite:" + DATABASE_FILE;


    // ==================== KONSTRUKTOR ====================

    /**
     * Konstruktor für den UserManager.
     * Initialisiert die Datenbankverbindung und erstellt die Tabelle falls nötig.
     */
    public UserManager() {
        // Datenbank und Tabelle initialisieren
        initializeDatabase();
    }


    // ==================== DATENBANK-INITIALISIERUNG ====================

    /**
     * Initialisiert die Datenbank.
     *
     * Diese Methode wird beim Start aufgerufen und:
     * 1. Erstellt die Datenbankdatei falls sie nicht existiert
     * 2. Erstellt die users-Tabelle falls sie nicht existiert
     * 3. Fügt Test-Benutzer hinzu falls die Tabelle leer ist
     */
    private void initializeDatabase() {
        /*
         * try-with-resources: Die Verbindung wird automatisch geschlossen,
         * auch wenn ein Fehler auftritt. Das ist wichtig, damit keine
         * "offenen" Datenbankverbindungen zurückbleiben.
         */
        try (Connection conn = getConnection()) {

            /*
             * Statement-Objekt zum Ausführen von SQL-Befehlen.
             * Ein Statement kann beliebige SQL-Befehle ausführen.
             */
            Statement stmt = conn.createStatement();

            /*
             * SQL-Befehl zum Erstellen der users-Tabelle.
             *
             * CREATE TABLE IF NOT EXISTS = Erstelle nur, wenn noch nicht vorhanden
             *
             * Spalten:
             * - id: Eindeutige ID, wird automatisch hochgezählt (AUTOINCREMENT)
             * - username: Benutzername, muss einzigartig sein (UNIQUE)
             * - password_hash: Der SHA-256 Hash des Passworts (NICHT das Klartext-Passwort!)
             * - created_at: Zeitpunkt der Registrierung
             * - banned: 0 = nicht gebannt, 1 = gebannt
             */
            String createTableSQL = """
                CREATE TABLE IF NOT EXISTS users (
                    id INTEGER PRIMARY KEY AUTOINCREMENT,
                    username TEXT UNIQUE NOT NULL,
                    password_hash TEXT NOT NULL,
                    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                    banned INTEGER DEFAULT 0
                )
                """;

            stmt.execute(createTableSQL);

            /*
             * Falls die Tabelle leer ist (erster Start), fügen wir
             * Test-Benutzer hinzu, damit man sich direkt einloggen kann.
             *
             * WICHTIG: Wir nutzen dieselbe Connection (conn), um SQLITE_BUSY zu vermeiden!
             * SQLite erlaubt nur eine schreibende Verbindung gleichzeitig.
             */
            ResultSet rs = stmt.executeQuery("SELECT COUNT(*) FROM users");
            if (rs.next() && rs.getInt(1) == 0) {
                // Tabelle ist leer - Testbenutzer anlegen
                System.out.println("Erstelle Standard-Benutzer...");

                // PreparedStatement für INSERT (mit derselben Connection!)
                String insertSQL = "INSERT INTO users (username, password_hash) VALUES (?, ?)";
                try (PreparedStatement pstmt = conn.prepareStatement(insertSQL)) {
                    // Admin anlegen
                    pstmt.setString(1, "admin");
                    pstmt.setString(2, hashPassword("admin123"));
                    pstmt.executeUpdate();

                    // Test-User anlegen
                    pstmt.setString(1, "test");
                    pstmt.setString(2, hashPassword("test"));
                    pstmt.executeUpdate();
                }

                System.out.println("Standard-Benutzer erstellt: admin/admin123, test/test");
            }

            System.out.println("Datenbank initialisiert: " + DATABASE_FILE);

        } catch (SQLException e) {
            System.err.println("Fehler bei Datenbank-Initialisierung: " + e.getMessage());
            e.printStackTrace();
        }
    }


    /**
     * Stellt eine Verbindung zur SQLite-Datenbank her.
     *
     * WICHTIG: Der Aufrufer ist dafür verantwortlich, die Verbindung
     * wieder zu schließen! Am besten mit try-with-resources.
     *
     * @return Connection-Objekt zur Datenbank
     * @throws SQLException bei Verbindungsfehlern
     */
    private Connection getConnection() throws SQLException {
        /*
         * DriverManager.getConnection() ist die Standard-Methode in JDBC,
         * um eine Datenbankverbindung herzustellen.
         *
         * Bei SQLite wird die Datenbankdatei automatisch erstellt,
         * falls sie noch nicht existiert.
         */
        return DriverManager.getConnection(DATABASE_URL);
    }


    // ==================== PASSWORT-HASHING ====================

    /**
     * Berechnet den SHA-256 Hash eines Passworts.
     *
     * SHA-256 ist eine kryptografische Hash-Funktion, die:
     * - Aus beliebig langen Eingaben immer einen 256-Bit (32 Byte) Hash erzeugt
     * - Einweg ist: Man kann aus dem Hash das Passwort NICHT zurückberechnen
     * - Deterministisch ist: Gleiches Passwort = immer gleicher Hash
     *
     * Beispiel:
     * "admin123" → "240be518fabd2724ddb6f04eeb1da5967448d7e831c08c8fa822809f74c720a9"
     * "admin124" → "5765ef64cf93d6c9e9d1..." (komplett anders!)
     *
     * @param password Das Klartext-Passwort
     * @return Der SHA-256 Hash als Hex-String (64 Zeichen)
     */
    private String hashPassword(String password) {
        try {
            /*
             * MessageDigest ist die Java-Klasse für kryptografische Hash-Funktionen.
             * Wir holen uns eine Instanz für den SHA-256 Algorithmus.
             */
            MessageDigest digest = MessageDigest.getInstance("SHA-256");

            /*
             * Das Passwort in Bytes umwandeln (UTF-8 Encoding).
             * Die Hash-Funktion arbeitet auf Byte-Ebene, nicht mit Strings.
             */
            byte[] passwordBytes = password.getBytes(StandardCharsets.UTF_8);

            /*
             * Den Hash berechnen.
             * digest() nimmt die Bytes und gibt den Hash als Byte-Array zurück.
             * Bei SHA-256 sind das immer genau 32 Bytes.
             */
            byte[] hashBytes = digest.digest(passwordBytes);

            /*
             * Die Hash-Bytes in einen Hex-String umwandeln.
             * Jedes Byte wird zu 2 Hex-Zeichen (00-FF).
             * 32 Bytes → 64 Hex-Zeichen
             *
             * Beispiel: Byte 0x5E → "5e"
             */
            StringBuilder hexString = new StringBuilder();
            for (byte b : hashBytes) {
                /*
                 * String.format("%02x", b) wandelt ein Byte in 2 Hex-Zeichen um.
                 * %02x bedeutet:
                 * - x = hexadezimal (Kleinbuchstaben)
                 * - 02 = immer 2 Stellen, mit führender 0 falls nötig
                 *
                 * & 0xff ist nötig, weil Java Bytes als signed (-128 bis 127) speichert,
                 * wir aber unsigned (0 bis 255) brauchen für die Hex-Darstellung.
                 */
                hexString.append(String.format("%02x", b & 0xff));
            }

            return hexString.toString();

        } catch (NoSuchAlgorithmException e) {
            /*
             * Dieser Fehler sollte nie auftreten, da SHA-256
             * in jeder Java-Installation verfügbar ist.
             */
            throw new RuntimeException("SHA-256 Algorithmus nicht verfügbar!", e);
        }
    }


    // ==================== BENUTZER-VERWALTUNG ====================

    /**
     * Prüft, ob ein Benutzername bereits existiert.
     *
     * @param username Der zu prüfende Benutzername
     * @return true wenn der Benutzer existiert, sonst false
     */
    public boolean userExists(String username) {
        /*
         * SQL-Abfrage mit Parameter (das Fragezeichen ?).
         * Wir zählen, wie viele Benutzer mit diesem Namen existieren.
         * Das Ergebnis ist 0 (existiert nicht) oder 1 (existiert).
         */
        String sql = "SELECT COUNT(*) FROM users WHERE username = ?";

        try (Connection conn = getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {

            /*
             * PreparedStatement: Sicherer als normales Statement!
             *
             * Der Parameter (?) wird separat gesetzt. Dadurch wird
             * SQL-Injection verhindert - ein Angreifer kann keine
             * bösartigen SQL-Befehle einschleusen.
             *
             * Beispiel SQL-Injection (mit normalem Statement):
             * username = "admin'; DROP TABLE users; --"
             * → Würde die ganze Tabelle löschen!
             *
             * Mit PreparedStatement wird das als normaler Text behandelt.
             */
            pstmt.setString(1, username);  // Erstes ? mit username füllen

            /*
             * executeQuery() führt die SELECT-Abfrage aus und
             * gibt ein ResultSet zurück - eine Art "Tabelle" mit den Ergebnissen.
             */
            ResultSet rs = pstmt.executeQuery();

            /*
             * rs.next() springt zur ersten (und einzigen) Ergebnis-Zeile.
             * rs.getInt(1) holt den Wert der ersten Spalte (unsere COUNT-Zahl).
             */
            if (rs.next()) {
                return rs.getInt(1) > 0;  // true wenn COUNT > 0
            }

        } catch (SQLException e) {
            System.err.println("Fehler bei userExists: " + e.getMessage());
        }

        return false;
    }


    /**
     * Registriert einen neuen Benutzer.
     *
     * Das Passwort wird NICHT im Klartext gespeichert, sondern als Hash!
     * Selbst wir (die Server-Betreiber) können das Passwort nicht sehen.
     *
     * @param username Der gewünschte Benutzername
     * @param password Das Passwort (wird gehasht bevor es gespeichert wird)
     * @return true wenn Registrierung erfolgreich, false wenn Username schon vergeben
     */
    public boolean registerUser(String username, String password) {
        // Prüfen ob Username schon existiert
        if (userExists(username)) {
            return false;
        }

        /*
         * WICHTIG: Wir speichern NICHT das Passwort, sondern nur den Hash!
         *
         * In der Datenbank steht dann z.B.:
         * | username | password_hash                                                    |
         * |----------|------------------------------------------------------------------|
         * | admin    | 240be518fabd2724ddb6f04eeb1da5967448d7e831c08c8fa822809f74c720a9 |
         *
         * Niemand kann aus diesem Hash das Original-Passwort "admin123" zurückbekommen!
         */
        String passwordHash = hashPassword(password);

        String sql = "INSERT INTO users (username, password_hash) VALUES (?, ?)";

        try (Connection conn = getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {

            pstmt.setString(1, username);      // Erstes ? = username
            pstmt.setString(2, passwordHash);  // Zweites ? = password_hash

            /*
             * executeUpdate() für INSERT, UPDATE, DELETE.
             * Gibt die Anzahl der betroffenen Zeilen zurück (sollte 1 sein).
             */
            int rowsAffected = pstmt.executeUpdate();
            return rowsAffected > 0;

        } catch (SQLException e) {
            System.err.println("Fehler bei registerUser: " + e.getMessage());
            return false;
        }
    }


    /**
     * Validiert die Anmeldedaten eines Benutzers.
     *
     * So funktioniert der Login:
     * 1. Benutzer gibt Passwort ein: "admin123"
     * 2. Wir berechnen den Hash: "240be518fabd2724..."
     * 3. Wir vergleichen mit dem gespeicherten Hash in der Datenbank
     * 4. Wenn beide Hashes gleich sind → Passwort korrekt!
     *
     * Wir kennen das echte Passwort nie - wir vergleichen nur Hashes!
     *
     * @param username Der Benutzername
     * @param password Das eingegebene Passwort (Klartext)
     * @return true wenn die Kombination korrekt ist, sonst false
     */
    public boolean validatePassword(String username, String password) {
        /*
         * Wir holen den gespeicherten Hash aus der Datenbank
         * und vergleichen ihn mit dem Hash des eingegebenen Passworts.
         */
        String sql = "SELECT password_hash FROM users WHERE username = ?";

        try (Connection conn = getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {

            pstmt.setString(1, username);
            ResultSet rs = pstmt.executeQuery();

            if (rs.next()) {
                // Gespeicherten Hash aus der Datenbank holen
                String storedHash = rs.getString("password_hash");

                // Hash des eingegebenen Passworts berechnen
                String inputHash = hashPassword(password);

                /*
                 * Die beiden Hashes vergleichen.
                 *
                 * Wenn der Benutzer das richtige Passwort eingegeben hat,
                 * sind beide Hashes identisch!
                 *
                 * Beispiel:
                 * - Gespeichert: hash("admin123") = "240be518..."
                 * - Eingegeben: "admin123"
                 * - Berechnet: hash("admin123") = "240be518..."
                 * - Vergleich: "240be518..." == "240be518..." → true!
                 */
                return storedHash.equals(inputHash);
            }

        } catch (SQLException e) {
            System.err.println("Fehler bei validatePassword: " + e.getMessage());
        }

        return false;
    }


    /**
     * Gibt die Anzahl der registrierten Benutzer zurück.
     *
     * @return Anzahl der Benutzer
     */
    public int getUserCount() {
        String sql = "SELECT COUNT(*) FROM users";

        try (Connection conn = getConnection();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {

            if (rs.next()) {
                return rs.getInt(1);
            }

        } catch (SQLException e) {
            System.err.println("Fehler bei getUserCount: " + e.getMessage());
        }

        return 0;
    }


    // ==================== BENUTZER-BANN-SYSTEM ====================

    /**
     * Bannt einen Benutzer (sperrt ihn dauerhaft).
     *
     * Ein gebannter Benutzer kann sich nicht mehr einloggen.
     *
     * @param username Der zu bannende Benutzername
     * @return true wenn erfolgreich gebannt
     */
    public boolean banUser(String username) {
        String sql = "UPDATE users SET banned = 1 WHERE username = ?";

        try (Connection conn = getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {

            pstmt.setString(1, username);
            int rowsAffected = pstmt.executeUpdate();
            return rowsAffected > 0;

        } catch (SQLException e) {
            System.err.println("Fehler bei banUser: " + e.getMessage());
            return false;
        }
    }


    /**
     * Hebt den Bann eines Benutzers auf.
     *
     * @param username Der zu entbannende Benutzername
     * @return true wenn erfolgreich entbannt
     */
    public boolean unbanUser(String username) {
        String sql = "UPDATE users SET banned = 0 WHERE username = ?";

        try (Connection conn = getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {

            pstmt.setString(1, username);
            int rowsAffected = pstmt.executeUpdate();
            return rowsAffected > 0;

        } catch (SQLException e) {
            System.err.println("Fehler bei unbanUser: " + e.getMessage());
            return false;
        }
    }


    /**
     * Prüft, ob ein Benutzer gebannt ist.
     *
     * @param username Der zu prüfende Benutzername
     * @return true wenn gebannt, false wenn nicht gebannt oder nicht gefunden
     */
    public boolean isUserBanned(String username) {
        String sql = "SELECT banned FROM users WHERE username = ?";

        try (Connection conn = getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {

            pstmt.setString(1, username);
            ResultSet rs = pstmt.executeQuery();

            if (rs.next()) {
                /*
                 * banned ist 0 oder 1 in der Datenbank.
                 * getInt() holt den Wert, == 1 prüft ob gebannt.
                 */
                return rs.getInt("banned") == 1;
            }

        } catch (SQLException e) {
            System.err.println("Fehler bei isUserBanned: " + e.getMessage());
        }

        return false;
    }


    // ==================== BENUTZER-ABFRAGEN ====================

    /**
     * Gibt eine Liste aller registrierten Benutzer zurück.
     *
     * Jeder Eintrag enthält: "username (erstellt am: datum) [GEBANNT]"
     *
     * @return Liste mit Benutzer-Informationen
     */
    public List<String> getAllUsers() {
        List<String> users = new ArrayList<>();

        String sql = "SELECT username, created_at, banned FROM users ORDER BY username";

        try (Connection conn = getConnection();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {

            /*
             * rs.next() iteriert durch alle Ergebnis-Zeilen.
             * Für jeden Benutzer in der Datenbank wird eine Zeile zurückgegeben.
             */
            while (rs.next()) {
                String username = rs.getString("username");
                String createdAt = rs.getString("created_at");
                boolean banned = rs.getInt("banned") == 1;

                // Formatierte Ausgabe erstellen
                String userInfo = username + " (erstellt: " + createdAt + ")";
                if (banned) {
                    userInfo += " [GEBANNT]";
                }

                users.add(userInfo);
            }

        } catch (SQLException e) {
            System.err.println("Fehler bei getAllUsers: " + e.getMessage());
        }

        return users;
    }


    /**
     * Gibt nur die Benutzernamen aller registrierten Benutzer zurück.
     *
     * @return Liste mit Benutzernamen
     */
    public List<String> getAllUsernames() {
        List<String> usernames = new ArrayList<>();

        String sql = "SELECT username FROM users ORDER BY username";

        try (Connection conn = getConnection();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {

            while (rs.next()) {
                usernames.add(rs.getString("username"));
            }

        } catch (SQLException e) {
            System.err.println("Fehler bei getAllUsernames: " + e.getMessage());
        }

        return usernames;
    }


    /**
     * Löscht einen Benutzer aus der Datenbank.
     *
     * VORSICHT: Diese Aktion kann nicht rückgängig gemacht werden!
     *
     * @param username Der zu löschende Benutzername
     * @return true wenn erfolgreich gelöscht
     */
    public boolean deleteUser(String username) {
        String sql = "DELETE FROM users WHERE username = ?";

        try (Connection conn = getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {

            pstmt.setString(1, username);
            int rowsAffected = pstmt.executeUpdate();
            return rowsAffected > 0;

        } catch (SQLException e) {
            System.err.println("Fehler bei deleteUser: " + e.getMessage());
            return false;
        }
    }
}