# fritzctl

CLI-Tool zur Verwaltung der Fritzbox.

## Build

```bash
./gradlew shadowJar
```

Das fertige JAR liegt unter `build/libs/fritzctl.jar`. Das mitgelieferte Wrapper-Skript `fritzctl` im Projektverzeichnis ruft es direkt auf.

## Allgemeine Optionen

```
fritzctl [--host <hostname>] [--verbose] [-o <format>] <gruppe> <befehl> [optionen]
```

| Option | Kurz | Standard | Beschreibung |
|--------|------|----------|--------------|
| `--host` | `-H` | `fritz.box` | Hostname oder IP-Adresse der Fritzbox |
| `--verbose` | `-v` | – | Ausführliche Debug-Ausgaben aktivieren |
| `--output` | `-o` | – | Ausgabeformat für maschinelle Weiterverarbeitung (`yaml`, `json`) |

**Ausgabeformate (`-o`):**

Ohne `-o` erzeugen alle Befehle eine menschenlesbare Ausgabe. Mit `-o` wird stattdessen ein strukturiertes Format ausgegeben, das sich zur Weiterverarbeitung in Skripten eignet.

| Wert | Beschreibung |
|------|--------------|
| `yaml` | YAML-Ausgabe |
| `json` | JSON-Ausgabe |

Jede strukturierte Antwort enthält mindestens das Feld `status` mit dem Wert `ok` oder `error`. Im Fehlerfall ist zusätzlich das Feld `message` gesetzt.

```bash
# YAML-Ausgabe
fritzctl -o yaml auth login --user admin --password geheim

# JSON-Ausgabe
fritzctl -o json auth login --user admin --password geheim
```

Beispiel-Ausgabe (`-o json`, Erfolg):
```json
{
  "status": "ok",
  "host": "fritz.box",
  "username": "admin",
  "sid": "8b4994376ab804ca",
  "sessionFile": "/Users/alice/.fritzctl/session"
}
```

Beispiel-Ausgabe (`-o json`, Fehler):
```json
{
  "status": "error",
  "message": "Anmeldung fehlgeschlagen. Benutzername oder Kennwort falsch."
}
```

---

## Kommandogruppen

| Gruppe | Beschreibung |
|--------|--------------|
| [`auth`](#auth) | Authentifizierung – Anmelden und Abmelden an der Fritzbox |
| [`device`](#device) | Smarthome-Geräte – Auflisten, Anzeigen und Schalten (AHA-HTTP-Schnittstelle) |

---

## auth

Befehle zur Authentifizierung an der Fritzbox. Die Session-ID wird nach erfolgreicher Anmeldung in `~/.fritzctl/session` gespeichert und von allen anderen Befehlen automatisch gelesen.

| Befehl | Beschreibung |
|--------|--------------|
| [`auth login`](#fritzctl-auth-login) | An der Fritzbox anmelden |
| [`auth logout`](#fritzctl-auth-logout) | Von der Fritzbox abmelden |

### `fritzctl auth login`

Meldet sich an der Fritzbox an. Die erhaltene Session-ID (SID) wird in `~/.fritzctl/session` gespeichert und steht nachfolgenden Befehlen automatisch zur Verfügung.

```
fritzctl [--host <hostname>] [--verbose] [-o <format>] auth login [--user <name>] [--password <passwort>]
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

# Ausgabe als YAML
fritzctl -o yaml auth login --user admin

# Ausgabe als JSON (z. B. für Weiterverarbeitung mit jq)
fritzctl -o json auth login --user admin | jq .sid
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
fritzctl [--host <hostname>] [--verbose] [-o <format>] auth logout
```

```bash
# Normaler Logout
fritzctl auth logout

# Mit Debug-Ausgaben
fritzctl --verbose auth logout

# Ausgabe als JSON
fritzctl -o json auth logout
```

---

## device

Befehle zur Verwaltung von Smarthome-Geräten über die AVM AHA-HTTP-Schnittstelle (`/webservices/homeautoswitch.lua`). Kompatibel mit Fritz!OS ab 6.x. Alle Befehle setzen eine aktive Session voraus (siehe [`auth login`](#fritzctl-auth-login)).

Die Geräte-Identifikation erfolgt über die **AIN** (Actor Identification Number), z. B. `11630 0015376`. Die AIN ist in der Fritz!Box-Oberfläche unter den Geräteeigenschaften sichtbar oder über `fritzctl device list` abrufbar.

| Befehl | Beschreibung |
|--------|--------------|
| [`device list`](#fritzctl-device-list) | Alle Smarthome-Geräte auflisten |
| [`device get`](#fritzctl-device-get) | Details zu einem Gerät anzeigen |
| [`device on`](#fritzctl-device-on) | Gerät einschalten |
| [`device off`](#fritzctl-device-off) | Gerät ausschalten |
| [`device toggle`](#fritzctl-device-toggle) | Schaltzustand eines Geräts wechseln |

### `fritzctl device list`

Gibt eine Tabelle aller bekannten Smarthome-Geräte aus (AIN, Name, Status, Schaltzustand, Temperatur, Leistung).

```
fritzctl [-o <format>] device list
```

```bash
fritzctl device list
fritzctl -o json device list | jq '.devices[].name'
```

---

### `fritzctl device get`

Zeigt Details zu einem einzelnen Gerät (Name, Status, Schaltzustand, Temperatur, Leistung, Energie).

```
fritzctl [-o <format>] device get <AIN>
```

| Argument | Beschreibung |
|----------|--------------|
| `AIN` | Actor Identification Number des Geräts, z. B. `11630 0015376` |

```bash
fritzctl device get "11630 0015376"
fritzctl -o json device get "11630 0015376" | jq .switchState
```

---

### `fritzctl device on`

Schaltet ein Gerät ein (z. B. FRITZ!DECT 200/210-Steckdose).

```
fritzctl [-o <format>] device on <AIN>
```

```bash
fritzctl device on "11630 0015376"
```

---

### `fritzctl device off`

Schaltet ein Gerät aus.

```
fritzctl [-o <format>] device off <AIN>
```

```bash
fritzctl device off "11630 0015376"
```

---

### `fritzctl device toggle`

Wechselt den Schaltzustand eines Geräts (ein → aus oder aus → ein).

```
fritzctl [-o <format>] device toggle <AIN>
```

```bash
fritzctl device toggle "11630 0015376"
```
