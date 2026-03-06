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
| [`net`](#net) | Netzwerk – Verbundene Geräte und WAN-Status (TR-064-Schnittstelle) |
| [`wifi`](#wifi) | WLAN – Netze anzeigen, Clients auflisten, WLAN ein-/ausschalten (TR-064-Schnittstelle) |
| [`call`](#call) | Telefonie – Anrufliste anzeigen (TR-064-Schnittstelle) |
| [`info`](#info) | Fritz!Box – Modell, Firmware, Uptime und Systemprotokoll (TR-064-Schnittstelle) |

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

---

## net

Befehle zur Netzwerk-Diagnose über die TR-064/UPnP-Schnittstelle der Fritz!Box (Port 49000). Kompatibel mit Fritz!OS ab 6.x. Alle Befehle setzen eine aktive Session voraus (siehe [`auth login`](#fritzctl-auth-login)).

Die Authentifizierung erfolgt über HTTP-Digest mit dem gespeicherten Benutzernamen und der SID als Passwort (Fritz!OS ≥ 7.25).

| Befehl | Beschreibung |
|--------|--------------|
| [`net hosts`](#fritzctl-net-hosts) | Alle verbundenen Netzwerk-Geräte auflisten |
| [`net wan`](#fritzctl-net-wan) | WAN-Status anzeigen |

### `fritzctl net hosts`

Listet alle bekannten Netzwerk-Geräte auf (LAN und WLAN), sortiert nach Status (online zuerst) und Name.

```
fritzctl [-o <format>] net hosts
```

```bash
fritzctl net hosts
fritzctl -o json net hosts | jq '.hosts[] | select(.active) | .ip'
```

Ausgabefelder: `name`, `ip`, `mac`, `type` (LAN/WLAN/Powerline), `active`

---

### `fritzctl net wan`

Zeigt den aktuellen WAN-Status: Verbindungstyp, externe IP-Adresse, maximale und aktuelle Up-/Download-Geschwindigkeit sowie Verbindungsuptime.

```
fritzctl [-o <format>] net wan
```

```bash
fritzctl net wan
fritzctl -o json net wan | jq .externalIp
```

Ausgabefelder: `accessType`, `linkStatus`, `externalIp`, `upstreamMaxBps`, `downstreamMaxBps`, `upstreamCurrentBps`, `downstreamCurrentBps`, `uptimeSeconds`

---

## wifi

Befehle zur WLAN-Verwaltung über die TR-064-Schnittstelle (Service `WLANConfiguration:1/2/3`). Alle Befehle setzen eine aktive Session voraus.

| Befehl | Beschreibung |
|--------|--------------|
| [`wifi status`](#fritzctl-wifi-status) | Alle WLAN-Netze mit SSID, Band, Kanal und Status anzeigen |
| [`wifi clients`](#fritzctl-wifi-clients) | Verbundene WLAN-Geräte auflisten |
| [`wifi on`](#fritzctl-wifi-on--off) | WLAN einschalten |
| [`wifi off`](#fritzctl-wifi-on--off) | WLAN ausschalten |

### `fritzctl wifi status`

Zeigt alle konfigurierten WLAN-Netze (2,4 GHz, 5 GHz, Gast) mit SSID, Kanal, Standard (n/ac/ax) und Status.

```
fritzctl [-o <format>] wifi status
```

```bash
fritzctl wifi status
fritzctl -o json wifi status | jq '.networks[] | select(.enabled) | .ssid'
```

---

### `fritzctl wifi clients`

Listet alle derzeit per WLAN verbundenen Geräte auf (Name, IP, MAC, Online-Status).

```
fritzctl [-o <format>] wifi clients
```

```bash
fritzctl wifi clients
fritzctl -o json wifi clients | jq '[.clients[] | select(.active) | .name]'
```

---

### `fritzctl wifi on` / `off`

Schaltet das Haupt-WLAN (2,4 GHz + 5 GHz) ein oder aus. Mit `--guest` wird stattdessen das Gastnetz gesteuert.

```
fritzctl [-o <format>] wifi on [--guest]
fritzctl [-o <format>] wifi off [--guest]
```

| Option | Kurz | Beschreibung |
|--------|------|--------------|
| `--guest` | `-g` | Gastnetz statt Haupt-WLAN |

```bash
fritzctl wifi off
fritzctl wifi on --guest
```

---

## call

Befehle zur Anzeige der Anrufliste über die TR-064-Schnittstelle (Service `X_AVM-DE_OnTel:1`).

| Befehl | Beschreibung |
|--------|--------------|
| [`call list`](#fritzctl-call-list) | Anrufliste anzeigen |

### `fritzctl call list`

Gibt die Anrufliste der Fritz!Box aus (eingehend, ausgehend, verpasst). Standardmäßig werden die letzten 30 Einträge gezeigt.

```
fritzctl [-o <format>] call list [--missed] [--in] [--out] [--limit <n>]
```

| Option | Kurz | Beschreibung |
|--------|------|--------------|
| `--missed` | `-m` | Nur verpasste Anrufe |
| `--in` | `-i` | Nur eingehende Anrufe |
| `--out` | `-o` | Nur ausgehende Anrufe |
| `--limit` | `-n` | Max. Anzahl Einträge (Standard: 30, 0 = alle) |

```bash
fritzctl call list
fritzctl call list --missed
fritzctl call list --limit 10
fritzctl -o json call list | jq '[.calls[] | select(.type == "verpasst") | .partner]'
```

Ausgabefelder: `date`, `type` (eingehend/ausgehend/verpasst), `partner`, `name`, `duration`, `device`

---

## info

Befehle zur Anzeige von Fritz!Box-Geräteinformationen über die TR-064-Schnittstelle (Service `DeviceInfo:1`).

| Befehl | Beschreibung |
|--------|--------------|
| [`info status`](#fritzctl-info-status) | Modell, Firmware, Seriennummer und Uptime |
| [`info log`](#fritzctl-info-log) | Systemprotokoll anzeigen |

### `fritzctl info status`

Zeigt Hersteller, Modell, Seriennummer, Firmware- und Hardware-Version sowie die aktuelle Uptime.

```
fritzctl [-o <format>] info status
```

```bash
fritzctl info status
fritzctl -o json info status | jq .firmwareVersion
```

---

### `fritzctl info log`

Gibt das Systemprotokoll der Fritz!Box aus (neueste Einträge zuerst).

```
fritzctl [-o <format>] info log [--limit <n>]
```

| Option | Kurz | Beschreibung |
|--------|------|--------------|
| `--limit` | `-n` | Max. Anzahl Einträge (Standard: 50, 0 = alle) |

```bash
fritzctl info log
fritzctl info log --limit 10
fritzctl -o json info log | jq '.log[]'
```
