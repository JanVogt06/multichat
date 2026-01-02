# Chat-System

Ein Multi-Client-Chat-System in Java für das Fortgeschrittene Programmierpraktikum (WS 2025/26) an der FSU Jena.

## Funktionen

### Server
- Multi-Client-Verbindungen über Socket (Port 3143)
- Grafische Benutzeroberfläche (ServerGUI)
- Benutzerregistrierung und Login mit SQLite-Datenbank
- Passwort-Hashing mit SHA-256
- Mehrraum-System mit dynamischer Raumverwaltung
- Dateiverzeichnis pro Raum (`room_files/Raumname/`)
- Empfangen und Speichern von PDF- und Bilddateien
- Server-Log mit Dateiprotokollierung (`server.log`)
- Admin-Funktionen: Benutzer verwarnen, kicken, bannen
- Anzeige aller Räume und Benutzer mit aktuellem Raum

### Client
- Grafische Benutzeroberfläche (ClientGUI)
- Login/Registrierungs-Dialog
- Mehrraum-System: Räume erstellen, beitreten, verlassen
- Chat mit Nachrichtenverlauf pro Raum
- Datei-Upload (PDF, PNG, JPG, GIF - max. 10 MB)
- Dateiliste anzeigen
- Datei-Download mit Speichern-Dialog
- Echtzeit-Aktualisierung von Raum- und Nutzerlisten

## Projektstruktur

```
├── src/
│   ├── server/
│   │   ├── Server.java          # Hauptserver mit Socket-Listener
│   │   ├── ServerGUI.java       # Server-Oberfläche
│   │   ├── ClientHandler.java   # Thread pro Client, Protokoll-Verarbeitung
│   │   ├── UserManager.java     # SQLite-Datenbank, Login, Bann-System
│   │   ├── RoomManager.java     # Raumverwaltung mit Dateiverzeichnissen
│   │   └── Room.java            # Einzelner Raum mit Mitgliedern
│   └── client/
│       ├── ClientGUI.java       # Client-Oberfläche mit Chat und Dateifunktionen
│       └── LoginDialog.java     # Login/Registrierungs-Fenster
├── lib/
│   └── sqlite-jdbc.jar          # SQLite JDBC-Treiber
├── room_files/                  # Dateiverzeichnisse pro Raum (wird erstellt)
├── users.db                     # SQLite-Datenbank (wird erstellt)
└── server.log                   # Server-Protokoll (wird erstellt)
```

## Starten

**Server:**
```bash
java -cp "out:lib/*" server.ServerGUI
```

**Client:**
```bash
java -cp "out:lib/*" client.ClientGUI
```

## Kompilieren

```bash
javac -d out -cp "lib/*" src/server/*.java src/client/*.java
```

## Technische Details

### Kommunikation
- **Streams:** DataInputStream/DataOutputStream
- **Text-Protokoll:** UTF-Strings für Befehle und Chat
- **Binär-Protokoll:** Längen-Präfix für Dateitransfer (4 Bytes Größe + Datei-Bytes)

### Protokoll-Befehle
| Befehl | Richtung | Beschreibung |
|--------|----------|--------------|
| `LOGIN:user:pass` | Client → Server | Anmeldung |
| `REGISTER:user:pass` | Client → Server | Registrierung |
| `CREATE_ROOM:name` | Client → Server | Raum erstellen |
| `JOIN_ROOM:name` | Client → Server | Raum beitreten |
| `LEAVE_ROOM` | Client → Server | Raum verlassen |
| `UPLOAD_FILE:name` | Client → Server | Datei-Upload starten |
| `LIST_FILES` | Client → Server | Dateiliste anfordern |
| `DOWNLOAD_FILE:name` | Client → Server | Datei anfordern |
| `FILE_DATA:name` | Server → Client | Datei-Download (Binärdaten folgen) |

### Dateitransfer

**Upload:**
1. Client sendet `UPLOAD_FILE:bild.png`
2. Server antwortet `READY_FOR_UPLOAD`
3. Client sendet Dateigröße (4 Bytes) + Datei-Bytes
4. Server speichert und antwortet `UPLOAD_SUCCESS:bild.png`

**Download:**
1. Client sendet `DOWNLOAD_FILE:bild.png`
2. Server sendet `FILE_DATA:bild.png`
3. Server sendet Dateigröße (4 Bytes) + Datei-Bytes
4. Client zeigt Speichern-Dialog

### Threading
- Server: Ein Thread pro Client (ClientHandler)
- Client: Listener-Thread für eingehende Nachrichten
- Swing-Thread für GUI-Updates
- Binärdaten werden im Listener-Thread gelesen (wichtig für korrektes Timing)

### Sicherheit
- Passwörter werden mit SHA-256 gehasht gespeichert
- Dateinamen werden auf Path-Traversal geprüft (`/`, `\`, `..`)
- Nur erlaubte Dateitypen (PDF, PNG, JPG, JPEG, GIF)
- Maximale Dateigröße: 10 MB

## Testbenutzer

- `admin` / `admin123`
- `test` / `test`

## Meilenstein-Übersicht

- [x] **Meilenstein 1:** Konsolen-Chat mit Socket-Kommunikation
- [x] **Meilenstein 2:** GUI, Mehrraum-System, SQLite-Datenbank, Admin-Funktionen
- [x] **Meilenstein 3:** Datei-Upload/-Download, Raumverzeichnisse