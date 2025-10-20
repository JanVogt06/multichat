package server;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.ArrayList;
import java.util.List;

public class Server {

    private static final int PORT = 3143;
    private ServerSocket serverSocket;
    private boolean running;
    private final UserManager userManager;           // ← final
    private final List<ClientHandler> clients;       // ← final (Liste wird später für Broadcast gebraucht)


    public Server() {
        this.running = false;
        this.userManager = new UserManager();
        this.clients = new ArrayList<>();
    }


    public void start() {
        try {
            serverSocket = new ServerSocket(PORT);
            running = true;

            System.out.println("=".repeat(50));
            System.out.println("✓ Chat-Server gestartet");
            System.out.println("✓ Port: " + PORT);
            System.out.println("✓ Registrierte User: " + userManager.getUserCount());
            System.out.println("=".repeat(50) + "\n");

            while (running) {
                Socket clientSocket = serverSocket.accept();

                // Neuen ClientHandler-Thread starten
                ClientHandler handler = new ClientHandler(clientSocket, userManager);
                clients.add(handler);  // ← Wird später für Broadcast verwendet!
                handler.start();
            }

        } catch (IOException e) {
            System.err.println("✗ Server-Fehler: " + e.getMessage());
        }
    }


    // Methode wird später für sauberes Herunterfahren gebraucht
    public void stop() {
        try {
            running = false;
            if (serverSocket != null) {
                serverSocket.close();
            }
            System.out.println("✓ Server gestoppt");
        } catch (IOException e) {
            System.err.println("✗ Fehler beim Stoppen: " + e.getMessage());
        }
    }


    public static void main(String[] args) {
        Server server = new Server();
        server.start();
    }
}