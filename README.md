# Chat-System - Meilenstein 1

Ein Multi-Client-Chat-System in Java für das Fortgeschrittene Programmierpraktikum (WS 2025/26) an der FSU Jena.

## Funktionen

### Server
- Multi-Client-Verbindungen über Socket (Port 3143)
- Benutzerregistrierung und Login mit persistenter Speicherung
- Echtzeit-Nachrichtenbroadcasting
- Anzeige verbundener Benutzer
- Join/Leave-Benachrichtigungen
- Thread-basierte Client-Verwaltung

### Client
- Socket-Verbindung zum Server
- Login/Registrierungs-UI (Konsole)
- Chat-Interface mit parallelem Senden/Empfangen
- Kontinuierlicher Message-Listener
- Graceful Disconnect mit "exit"-Befehl

## Starten

**Server:**
```bash
java server.Server
```

**Client:**
```bash
java client.Client
```

## Technische Details

- **Kommunikation:** DataInputStream/DataOutputStream
- **Protokoll:** Strukturierte Befehle (LOGIN:user:pass, REGISTER:user:pass)
- **Threading:** ClientHandler pro Verbindung, separater Listener-Thread im Client
- **Synchronisation:** READY-Handshake verhindert Race Conditions
- **Port:** 3143

## Testbenutzer

- `admin` / `admin123`
- `test` / `test`

## Nächste Schritte

Meilenstein 2 & 3: GUI, Mehrraum-System, Dateiverwaltung, Datenbank-Integration