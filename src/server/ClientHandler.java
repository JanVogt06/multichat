package server;

import java.io.*;
import java.net.Socket;

/**
 * Diese Klasse verwaltet die Kommunikation mit einem einzelnen Client.
 * Jeder ClientHandler läuft in einem eigenen Thread und ist für die
 * Authentifizierung, Chat-Kommunikation und Verwaltung eines Clients zuständig.
 */
public class ClientHandler extends Thread {

    // Socket-Verbindung zum Client
    private final Socket socket;

    // Referenz zum UserManager für Benutzerverwaltung
    private final UserManager userManager;

    // Referenz zum Server für Broadcasting
    private final Server server;

    // Eingabestrom vom Client
    private DataInputStream input;

    // Ausgabestrom zum Client
    private DataOutputStream output;

    // Benutzername des angemeldeten Clients
    private String username;

    // Flag, das angibt, ob der Client bereit für Chat-Nachrichten ist
    // volatile sorgt für Thread-Sicherheit bei Lese-/Schreibzugriffen
    private volatile boolean readyForChat = false;


    /**
     * Konstruktor für einen neuen ClientHandler.
     *
     * @param socket Socket-Verbindung zum Client
     * @param userManager UserManager für Benutzerverwaltung
     * @param server Server-Instanz für Broadcasting
     */
    public ClientHandler(Socket socket, UserManager userManager, Server server) {
        this.socket = socket;
        this.userManager = userManager;
        this.server = server;
    }


    /**
     * Hauptmethode des Threads. Wird beim Start des Threads ausgeführt.
     * Steuert den gesamten Ablauf: Verbindung, Authentifizierung, Chat-Loop.
     */
    @Override
    public void run() {
        try {
            // Eingabe- und Ausgabeströme initialisieren
            input = new DataInputStream(socket.getInputStream());
            output = new DataOutputStream(socket.getOutputStream());

            System.out.println("Neuer Client verbunden: " + socket.getInetAddress());

            // Authentifizierungsprozess durchführen (Login oder Registrierung)
            username = authenticate();

            System.out.println("User '" + username + "' eingeloggt");

            // Warte auf READY-Signal vom Client, bevor Chat-Nachrichten gesendet werden
            String readySignal = input.readUTF();
            if (readySignal.equals("READY")) {
                System.out.println("Client '" + username + "' ist bereit für Chat");

                // Client ist nun bereit, Chat-Nachrichten zu empfangen
                readyForChat = true;

                // Aktuelle Liste aller angemeldeten Benutzer senden
                sendUserList();

                // Alle anderen Clients über neuen Benutzer informieren
                server.broadcast(">>> " + username + " hat den Chat betreten", this);
            }

            // Hauptschleife für Chat-Kommunikation
            chatLoop();

        } catch (IOException e) {
            System.err.println("Fehler bei Client " + username + ": " + e.getMessage());
        } finally {
            // Ressourcen freigeben und Client abmelden
            close();
        }
    }


    /**
     * Authentifizierungsprozess für den Client.
     * Wartet auf LOGIN oder REGISTER Befehle vom Client.
     *
     * @return Benutzername nach erfolgreicher Anmeldung
     * @throws IOException bei Kommunikationsfehlern
     */
    private String authenticate() throws IOException {
        System.out.println("Warte auf Authentifizierung...");

        while (true) {
            // Nachricht vom Client lesen (Format: BEFEHL:username:password)
            String message = input.readUTF();
            System.out.println("Empfangen: " + message);

            // Nachricht in Teile aufteilen
            String[] parts = message.split(":", 3);
            String command = parts[0];

            // Je nach Befehl entsprechende Aktion ausführen
            if (command.equals("REGISTER")) {
                handleRegister(parts);

            } else if (command.equals("LOGIN")) {
                // Bei erfolgreichem Login wird der Username zurückgegeben
                String loginResult = handleLogin(parts);
                if (loginResult != null) {
                    return loginResult;
                }

            } else {
                // Unbekannter Befehl
                sendResponse("ERROR", "Unbekannter Befehl");
            }
        }
    }


    /**
     * Verarbeitet eine Registrierungsanfrage.
     *
     * @param parts Array mit [REGISTER, username, password]
     * @throws IOException bei Kommunikationsfehlern
     */
    private void handleRegister(String[] parts) throws IOException {
        // Überprüfe, ob die Nachricht das richtige Format hat
        if (parts.length != 3) {
            sendResponse("ERROR", "Ungültiges Format");
            return;
        }

        String user = parts[1];
        String pass = parts[2];

        // Versuche Benutzer zu registrieren
        if (userManager.registerUser(user, pass)) {
            sendResponse("SUCCESS", "Registrierung erfolgreich");
            System.out.println("Neuer User: " + user);
        } else {
            sendResponse("ERROR", "Username bereits vergeben");
        }
    }


    /**
     * Verarbeitet eine Login-Anfrage.
     *
     * @param parts Array mit [LOGIN, username, password]
     * @return Username bei erfolgreichem Login, sonst null
     * @throws IOException bei Kommunikationsfehlern
     */
    private String handleLogin(String[] parts) throws IOException {
        // Überprüfe, ob die Nachricht das richtige Format hat
        if (parts.length != 3) {
            sendResponse("ERROR", "Ungültiges Format");
            return null;
        }

        String user = parts[1];
        String pass = parts[2];

        // Überprüfe, ob Benutzer existiert
        if (!userManager.userExists(user)) {
            sendResponse("ERROR", "Username nicht gefunden");
            return null;
        }

        // Überprüfe Passwort
        if (!userManager.validatePassword(user, pass)) {
            sendResponse("ERROR", "Falsches Passwort");
            return null;
        }

        // Login erfolgreich
        sendResponse("SUCCESS", "Login erfolgreich");
        return user;
    }


    /**
     * Sendet die Liste aller angemeldeten Benutzer an diesen Client.
     *
     * @throws IOException bei Kommunikationsfehlern
     */
    private void sendUserList() throws IOException {
        var usernames = server.getConnectedUsernames();
        String userList = ">>> Angemeldete User: " + String.join(", ", usernames);
        sendMessage(userList);
    }


    /**
     * Hauptschleife für den Chat-Betrieb.
     * Empfängt Nachrichten vom Client und broadcastet sie an alle anderen.
     *
     * @throws IOException bei Kommunikationsfehlern
     */
    private void chatLoop() throws IOException {
        while (true) {
            try {
                // Nachricht vom Client lesen
                String message = input.readUTF();
                System.out.println(username + ": " + message);

                // Nachricht formatieren mit Absender
                String formattedMessage = "[" + username + "] " + message;

                // An alle anderen Clients senden
                server.broadcast(formattedMessage, this);

            } catch (EOFException e) {
                // Client hat Verbindung beendet (normal)
                System.out.println(username + " hat sich abgemeldet");
                break;
            }
        }
    }


    /**
     * Sendet eine Antwort an den Client (während Authentifizierung).
     *
     * @param status Status der Operation (SUCCESS oder ERROR)
     * @param message Beschreibung/Nachricht
     * @throws IOException bei Kommunikationsfehlern
     */
    private void sendResponse(String status, String message) throws IOException {
        output.writeUTF(status + ":" + message);
        output.flush();
    }


    /**
     * Sendet eine Chat-Nachricht an diesen Client.
     * Wird vom Server aufgerufen. Nachricht wird nur gesendet,
     * wenn der Client im Chat-Modus ist.
     *
     * @param message Die zu sendende Nachricht
     * @throws IOException bei Kommunikationsfehlern
     */
    public void sendMessage(String message) throws IOException {
        if (readyForChat) {
            output.writeUTF(message);
            output.flush();
        }
    }


    /**
     * Prüft, ob dieser Client bereit für Chat-Nachrichten ist.
     *
     * @return true wenn bereit, sonst false
     */
    public boolean isReadyForChat() {
        return readyForChat;
    }


    /**
     * Schließt die Verbindung und gibt alle Ressourcen frei.
     * Benachrichtigt andere Clients über das Verlassen.
     */
    private void close() {
        try {
            // Andere Clients benachrichtigen, falls Client angemeldet war
            if (username != null) {
                server.broadcast("<<< " + username + " hat den Chat verlassen", this);
                server.removeClient(this);
            }

            // Streams und Socket schließen
            if (input != null) input.close();
            if (output != null) output.close();
            if (socket != null) socket.close();

            System.out.println("ClientHandler geschlossen: " + username);
        } catch (IOException e) {
            System.err.println("Fehler beim Schließen: " + e.getMessage());
        }
    }


    /**
     * Gibt den Benutzernamen dieses Clients zurück.
     *
     * @return Benutzername oder null wenn nicht angemeldet
     */
    public String getUsername() {
        return username;
    }
}