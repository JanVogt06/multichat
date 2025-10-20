package client;

import java.io.*;
import java.net.*;
import java.util.Scanner;

public class Client {

    private static final String SERVER_HOST = "localhost";
    private static final int SERVER_PORT = 3143;

    private Socket socket;
    private DataOutputStream output;
    private DataInputStream input;
    private Scanner scanner;


    public void connect() {
        try {
            System.out.println("Verbinde zu " + SERVER_HOST + ":" + SERVER_PORT + "...");

            socket = new Socket(SERVER_HOST, SERVER_PORT);
            output = new DataOutputStream(socket.getOutputStream());
            input = new DataInputStream(socket.getInputStream());
            scanner = new Scanner(System.in);

            System.out.println("✓ Verbunden!\n");

            // Login
            LoginUI loginUI = new LoginUI(input, output, scanner);
            String username = loginUI.showLoginMenu();

            if (username == null) {
                System.out.println("Auf Wiedersehen!");
                disconnect();
                return;
            }

            // Chat
            ChatUI chatUI = new ChatUI(input, output, scanner, username);
            chatUI.start();

            disconnect();

        } catch (UnknownHostException e) {
            System.err.println("✗ Server nicht gefunden");
        } catch (IOException e) {
            System.err.println("✗ Verbindungsfehler: " + e.getMessage());
        }
    }


    private void disconnect() {
        try {
            if (scanner != null) scanner.close();
            if (output != null) output.close();
            if (input != null) input.close();
            if (socket != null) socket.close();
            System.out.println("✓ Verbindung getrennt");
        } catch (IOException e) {
            System.err.println("✗ Fehler beim Trennen: " + e.getMessage());
        }
    }


    public static void main(String[] args) {
        Client client = new Client();
        client.connect();
    }
}