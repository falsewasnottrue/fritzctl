# fritzctl

CLI-Tool zur Verwaltung der Fritzbox.

## Build

```bash
./gradlew shadowJar
```

Das fertige JAR liegt unter `build/libs/fritzctl.jar`. Das mitgelieferte Wrapper-Skript `fritzctl` im Projektverzeichnis ruft es direkt auf.

## Allgemeine Optionen

```
fritzctl [--host <hostname>] [--verbose] <gruppe> <befehl> [optionen]
```

| Option | Kurz | Standard | Beschreibung |
|--------|------|----------|--------------|
| `--host` | `-H` | `fritz.box` | Hostname oder IP-Adresse der Fritzbox |
| `--verbose` | `-v` | – | Ausführliche Debug-Ausgaben aktivieren |

---

## Authentifizierung

### `fritzctl auth login`

Meldet sich an der Fritzbox an. Die erhaltene Session-ID (SID) wird in `~/.fritzctl/session` gespeichert und steht nachfolgenden Befehlen automatisch zur Verfügung.

```
fritzctl [--host <hostname>] [--verbose] auth login [--user <name>] [--password <passwort>]
```

**Optionen:**

| Option | Kurz | Beschreibung |
|--------|------|--------------|
| `--user` | `-u` | Benutzername. Wird nicht angegeben, prüft fritzctl zuerst die Umgebungsvariable `FB_USER`. Ist diese ebenfalls nicht gesetzt, erfolgt eine interaktive Abfrage. |
| `--password` | `-p` | Passwort. Wird nicht angegeben, prüft fritzctl zuerst die Umgebungsvariable `FB_PASSWORD`. Ist diese ebenfalls nicht gesetzt, erfolgt eine interaktive Abfrage (Eingabe wird nicht angezeigt). |

**Priorität der Anmeldedaten:**

```
Umgebungsvariable  →  CLI-Option  →  Interaktive Eingabe
```

**Umgebungsvariablen:**

| Variable | Beschreibung |
|----------|--------------|
| `FB_USER` | Benutzername |
| `FB_PASSWORD` | Passwort |

**Beispiele:**

```bash
# Interaktive Eingabe von Benutzername und Passwort
fritzctl auth login

# Benutzername als Option, Passwort interaktiv
fritzctl auth login --user admin

# Alle Angaben als Optionen
fritzctl auth login --user admin --password geheim

# Über Umgebungsvariablen (z. B. in Skripten)
export FB_USER=admin
export FB_PASSWORD=geheim
fritzctl auth login

# Andere Fritzbox-Adresse
fritzctl --host 192.168.1.1 auth login --user admin

# Mit Debug-Ausgaben (zeigt Challenge, Response-Berechnung, HTTP-Antworten)
fritzctl --verbose auth login
```

**Authentifizierungsverfahren:**

fritzctl unterstützt beide von der Fritzbox angebotenen Verfahren und wählt automatisch das sicherere:

- **PBKDF2** (Fritz!OS ≥ 7.24): Die Challenge beginnt mit `2$`. Das Passwort wird zweistufig mit PBKDF2-HMAC-SHA256 gehasht. Iteration und Salt werden von der Fritzbox vorgegeben.
- **MD5** (Fritz!OS < 7.24, Fallback): MD5-Hash über `challenge-passwort` in UTF-16LE-Kodierung.

**Fehlerfälle:**

| Fehler | Ursache |
|--------|---------|
| Verbindung fehlgeschlagen | Fritzbox nicht erreichbar unter dem angegebenen Host |
| Anmeldung fehlgeschlagen | Falscher Benutzername oder falsches Passwort |
| Anmeldung blockiert (N Sekunden) | Zu viele fehlgeschlagene Versuche; Fritzbox sperrt weitere Logins temporär |

---

### `fritzctl auth logout`

Meldet sich von der Fritzbox ab und löscht die lokal gespeicherte Session.

```
fritzctl [--host <hostname>] [--verbose] auth logout
```

```bash
# Normaler Logout
fritzctl auth logout

# Mit Debug-Ausgaben
fritzctl --verbose auth logout
```
