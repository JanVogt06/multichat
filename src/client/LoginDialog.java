package client;

import javax.swing.*;
import java.awt.*;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;

/**
 * Dialog für Login und Registrierung.
 * Wird angezeigt bevor der Chat startet.
 */
public class LoginDialog extends JDialog {

    // ===== GUI-Komponenten =====
    private JTextField usernameField;
    private JPasswordField passwordField;
    private JButton loginButton;
    private JButton registerButton;
    private JLabel statusLabel;

    // ===== Netzwerk =====
    private final DataInputStream input;
    private final DataOutputStream output;

    // ===== Ergebnis =====
    private boolean loginSuccessful = false;
    private String username;


    /**
     * Konstruktor - Erstellt den Login-Dialog.
     *
     * @param parent Das übergeordnete Fenster
     * @param input Eingabestrom vom Server
     * @param output Ausgabestrom zum Server
     */
    public LoginDialog(JFrame parent, DataInputStream input, DataOutputStream output) {
        super(parent, "Chat Anmeldung", true); // Modal
        this.input = input;
        this.output = output;

        setSize(350, 200);
        setLocationRelativeTo(parent);
        setResizable(false);
        setDefaultCloseOperation(DISPOSE_ON_CLOSE);

        initComponents();
    }


    /**
     * Initialisiert die GUI-Komponenten.
     */
    private void initComponents() {
        setLayout(new BorderLayout(10, 10));

        // ===== Titel =====
        JLabel titleLabel = new JLabel("Chat Anmeldung", SwingConstants.CENTER);
        titleLabel.setFont(new Font("SansSerif", Font.BOLD, 18));
        titleLabel.setBorder(BorderFactory.createEmptyBorder(10, 0, 0, 0));

        // ===== Eingabefelder =====
        JPanel inputPanel = new JPanel(new GridBagLayout());
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(5, 5, 5, 5);
        gbc.fill = GridBagConstraints.HORIZONTAL;

        // Benutzername
        gbc.gridx = 0;
        gbc.gridy = 0;
        inputPanel.add(new JLabel("Benutzername:"), gbc);

        gbc.gridx = 1;
        gbc.weightx = 1.0;
        usernameField = new JTextField(15);
        inputPanel.add(usernameField, gbc);

        // Passwort
        gbc.gridx = 0;
        gbc.gridy = 1;
        gbc.weightx = 0;
        inputPanel.add(new JLabel("Passwort:"), gbc);

        gbc.gridx = 1;
        gbc.weightx = 1.0;
        passwordField = new JPasswordField(15);
        inputPanel.add(passwordField, gbc);

        // Enter-Taste zum Login
        passwordField.addActionListener(e -> handleLogin());

        // ===== Status-Label =====
        statusLabel = new JLabel(" ", SwingConstants.CENTER);
        statusLabel.setForeground(Color.RED);

        // ===== Buttons =====
        JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.CENTER, 20, 10));

        loginButton = new JButton("Anmelden");
        registerButton = new JButton("Registrieren");

        loginButton.addActionListener(e -> handleLogin());
        registerButton.addActionListener(e -> handleRegister());

        buttonPanel.add(loginButton);
        buttonPanel.add(registerButton);

        // ===== Zusammenbauen =====
        add(titleLabel, BorderLayout.NORTH);
        add(inputPanel, BorderLayout.CENTER);

        JPanel southPanel = new JPanel(new BorderLayout());
        southPanel.add(statusLabel, BorderLayout.NORTH);
        southPanel.add(buttonPanel, BorderLayout.SOUTH);
        add(southPanel, BorderLayout.SOUTH);
    }


    /**
     * Verarbeitet den Login-Versuch.
     */
    private void handleLogin() {
        String user = usernameField.getText().trim();
        String pass = new String(passwordField.getPassword());

        // Validierung
        if (user.isEmpty() || pass.isEmpty()) {
            showStatus("Bitte alle Felder ausfüllen!", true);
            return;
        }

        // Buttons deaktivieren während der Anfrage
        setButtonsEnabled(false);
        showStatus("Anmeldung läuft...", false);

        // Login in separatem Thread (um GUI nicht zu blockieren)
        new Thread(() -> {
            try {
                // LOGIN-Befehl senden
                output.writeUTF("LOGIN:" + user + ":" + pass);
                output.flush();

                // Antwort lesen
                String response = input.readUTF();
                String[] parts = response.split(":", 2);

                SwingUtilities.invokeLater(() -> {
                    if (parts[0].equals("SUCCESS")) {
                        // Login erfolgreich
                        loginSuccessful = true;
                        username = user;
                        dispose();
                    } else {
                        // Login fehlgeschlagen
                        showStatus(parts.length > 1 ? parts[1] : "Login fehlgeschlagen", true);
                        setButtonsEnabled(true);
                    }
                });

            } catch (IOException e) {
                SwingUtilities.invokeLater(() -> {
                    showStatus("Verbindungsfehler: " + e.getMessage(), true);
                    setButtonsEnabled(true);
                });
            }
        }).start();
    }


    /**
     * Verarbeitet die Registrierung.
     */
    private void handleRegister() {
        String user = usernameField.getText().trim();
        String pass = new String(passwordField.getPassword());

        // Validierung
        if (user.isEmpty() || pass.isEmpty()) {
            showStatus("Bitte alle Felder ausfüllen!", true);
            return;
        }

        // Doppelpunkt nicht erlaubt (Protokoll-Trennzeichen)
        if (user.contains(":") || pass.contains(":")) {
            showStatus("Doppelpunkt ':' ist nicht erlaubt!", true);
            return;
        }

        // Buttons deaktivieren während der Anfrage
        setButtonsEnabled(false);
        showStatus("Registrierung läuft...", false);

        // Registrierung in separatem Thread
        new Thread(() -> {
            try {
                // REGISTER-Befehl senden
                output.writeUTF("REGISTER:" + user + ":" + pass);
                output.flush();

                // Antwort lesen
                String response = input.readUTF();
                String[] parts = response.split(":", 2);

                SwingUtilities.invokeLater(() -> {
                    if (parts[0].equals("SUCCESS")) {
                        // Registrierung erfolgreich
                        showStatus("Registrierung erfolgreich! Bitte anmelden.", false);
                        statusLabel.setForeground(new Color(0, 128, 0)); // Grün
                    } else {
                        // Registrierung fehlgeschlagen
                        showStatus(parts.length > 1 ? parts[1] : "Registrierung fehlgeschlagen", true);
                    }
                    setButtonsEnabled(true);
                });

            } catch (IOException e) {
                SwingUtilities.invokeLater(() -> {
                    showStatus("Verbindungsfehler: " + e.getMessage(), true);
                    setButtonsEnabled(true);
                });
            }
        }).start();
    }


    /**
     * Zeigt eine Status-Nachricht an.
     */
    private void showStatus(String message, boolean isError) {
        statusLabel.setText(message);
        statusLabel.setForeground(isError ? Color.RED : new Color(0, 128, 0));
    }


    /**
     * Aktiviert/Deaktiviert die Buttons.
     */
    private void setButtonsEnabled(boolean enabled) {
        loginButton.setEnabled(enabled);
        registerButton.setEnabled(enabled);
        usernameField.setEnabled(enabled);
        passwordField.setEnabled(enabled);
    }


    /**
     * Prüft ob der Login erfolgreich war.
     *
     * @return true wenn erfolgreich eingeloggt
     */
    public boolean isLoginSuccessful() {
        return loginSuccessful;
    }


    /**
     * Gibt den Benutzernamen zurück.
     *
     * @return Benutzername oder null
     */
    public String getUsername() {
        return username;
    }
}