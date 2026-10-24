# Steam Server Blacklist Tool

[English](README.md) | **Deutsch**

Kleines Desktop-Tool, das eine `server_blacklist.txt` für Source-Spiele erstellt, vor allem für **Counter-Strike: Source**.
Die Datei wird im Ingame-Serverbrowser importiert. Danach verschwinden Fake-Server, Spam-Server,
Redirect-/Bot-Server oder alle Server aus einem gewählten Land (z.B. Russland) aus der Serverliste.

## Warum?

Der CS:S-Serverbrowser ist voll mit Fake-Servern: hunderte Einträge auf einer IP, unmögliche
Spielerzahlen, Weiterleitungen auf andere Server. Das Spiel kann Server über seine Blacklist ausblenden,
aber jeden einzeln von Hand einzutragen ist mühsam. Dieses Tool findet sie und schreibt die Datei.

## Funktionen

- Lädt die komplette aktuelle Serverliste eines Spiels von Steam
- Markiert IPs nach einfachen, einstellbaren Regeln:
  - **Land** der IP (z.B. `RU`, `RU,BY`)
  - **Kyrillische Schrift** im Servernamen oder in der Spielbeschreibung (erwischt russisch-/ukrainischsprachige Server, egal wo sie gehostet sind)
  - **Viele Server auf einer IP** (typisch für Fake-/Spam-Hoster, Standard: ab 30)
  - **Unrealistische Slotzahl** (`max_players` über einem Grenzwert, Standard: 66)
  - **Stichwörter** im Servernamen
- Ergebnis prüfen und IPs vor dem Übernehmen an- oder abwählen
- IPs manuell hinzufügen, ganze IP-Listen einfügen, bestehende Blacklist-Dateien importieren und zusammenführen
- Schreibt eine gültige `server_blacklist.txt`: ein Eintrag pro IP, immer mit Port `0` (= alle Ports der IP), ohne Duplikate

## Download

1. **Java 17 oder neuer** installieren, z.B. von [Adoptium](https://adoptium.net/).
2. `steam-blacklist-tool-<version>.jar` aus dem
   [neuesten Release](https://github.com/j6845/steam_blacklist_server_tool/releases/latest) herunterladen.
3. Per Doppelklick starten oder im Terminal:
   ```
   java -jar steam-blacklist-tool-<version>.jar
   ```

Die JAR ist die einzige Datei, die du brauchst. Läuft unter Windows, Linux und macOS.

## Bedienung

### 1. Steam Web API Key holen

Die Serverliste kommt über die offizielle Steam Web API. Dafür braucht es einen kostenlosen Key:
<https://steamcommunity.com/dev/apikey> (als Domain geht alles, z.B. `localhost`).

### 2. Server suchen (Tab "Server suchen")

1. Key in "Steam Web API Key" einfügen.
2. AppID des Spiels eintragen (`240` = Counter-Strike: Source).
3. Regeln einstellen. Eine IP wird markiert, wenn **eine** Regel zutrifft. Zahl auf `0` setzen oder Feld leer lassen schaltet die Regel ab.

   | Feld | Bedeutung |
   |---|---|
   | "Länder" | Ländercodes ([ISO 3166-1 alpha-2](https://de.wikipedia.org/wiki/ISO-3166-1-Kodierliste)), kommagetrennt, z.B. `RU` oder `RU,BY` |
   | "+ kyrillische Schrift in Server-/Spielname" | Markiert IPs, bei denen ein Servername oder die Spielbeschreibung (Spalte "Spiel" im Serverbrowser) kyrillische Buchstaben enthält. Standardmäßig an |
   | "Ab N Servern pro IP" | Markiert IPs mit mindestens N Servern |
   | "max_players über" | Markiert Server mit mehr Slots als angegeben |
   | "Name enthält" | Kommagetrennte Stichwörter, Groß-/Kleinschreibung egal |

4. "Server laden" klicken. Das dauert etwa 20–30 Sekunden, beim ersten Mal wird zusätzlich die Länder-Datenbank geladen (siehe [Funktionsweise](#funktionsweise)).
5. Ergebnis prüfen. Markierte IPs sind schon angehakt, die Spalte "Grund" zeigt die zutreffende Regel.
   Regel geändert? "Neu bewerten" wendet sie ohne neuen Download an.
6. "Ausgewählte zur Blacklist" klicken.

### 3. Blacklist speichern (Tab "Blacklist")

- "Eintrag hinzufügen..." fügt eine einzelne IP hinzu, "IPs einfügen..." eine eingefügte Liste (eine IP pro Zeile).
- "Datei importieren..." führt eine bestehende `server_blacklist.txt` zusammen, z.B. die, die dein Spiel schon nutzt.
- Doppelklick auf einen Namen zum Bearbeiten. Der Name wird im Spiel im Tab "Blacklisted Servers" angezeigt.
- "Speichern als..." schreibt die Datei.

### 4. Im Spiel importieren

1. Serverbrowser öffnen, Tab **Blacklisted Servers**.
2. **Import servers from file** klicken und die gespeicherte `server_blacklist.txt` wählen.

Der Import ergänzt die bereits geblacklisteten Server. Alternativ das Spiel schließen und die Datei nach
`Steam/steamapps/common/Counter-Strike Source/cstrike/cfg/server_blacklist.txt` kopieren.
Das ersetzt die aktuelle Blacklist des Spiels. Wer sie behalten will, importiert sie vorher ins Tool.

Sehr große Listen lassen den Serverbrowser beim Öffnen kurz einfrieren. Liste also gezielt halten.

## Funktionsweise

- **Serverliste:** Steam Web API `IGameServersService/GetServerList`. Die API liefert maximal 10.000 Server pro Anfrage,
  CS:S allein listet aber weit mehr (rund 58.000, überwiegend Fakes). Erreicht eine Anfrage das Limit, teilt das Tool
  sie weiter auf (nach Region, Spielern, VAC, dedicated, Betriebssystem, Passwort, zuletzt Map), bis jeder Teil
  vollständig ist, und führt alles ohne Duplikate zusammen.
- **Spielbeschreibung:** Die Spalte "Spiel" liefert die Steam Web API nicht. Das Tool fragt die Gameserver deshalb
  direkt mit der Standard-Source-Abfrage `A2S_INFO` per UDP, genau wie der Serverbrowser im Spiel. Gefragt werden nur
  IPs ohne kyrillischen Servernamen, höchstens 5 Server pro IP: Jede abgefragte Adresse belegt einen Eintrag in der
  NAT-Tabelle deines Routers, und zehntausende Fake-Server würden sie überlaufen lassen.
- **Länderzuordnung:** die öffentlichen Vergabestatistiken der fünf Regional Internet Registries
  (RIPE NCC, ARIN, APNIC, LACNIC, AFRINIC). Kein Konto, kein weiterer Key. Beim ersten Start werden ca. 45 MB nach
  `~/.steam_blacklist_tool/rir/` geladen und nach 7 Tagen erneuert.
- **Dateiformat:** Valve KeyValues, UTF-8 ohne BOM:
  ```
  "serverblacklist"
  {
  	"server"
  	{
  		"name"			"Land RU | 2.56.88.98 | Beispiel-Servername"
  		"date"			"1747308377"
  		"addr"			"2.56.88.98:0"
  	}
  }
  ```
  `date` ist ein Unix-Zeitstempel. `addr` ist immer `IP:0` und sperrt damit alle Ports der IP, genau wie
  das Spiel selbst, wenn man einen Server von Hand blacklistet.

## Grenzen

- Das Land ist das **Registrierungsland** des IP-Blocks, nicht zwingend der Ort, an dem der Server steht.
- Die Blacklist kennt nur einzelne IPs, keine IP-Bereiche. "Alle russischen Server" heißt: alle, die
  **gerade** gelistet sind. Neue Server tauchen erst nach erneutem Laden auf.
- Die Kyrillisch-Regel trifft auch Namen, die nur zur Deko einen kyrillischen Buchstaben nutzen (z.B. `м` in `Gλмß`).
- Spielbeschreibungen gibt es nur von Servern, die auf die UDP-Abfrage antworten, und für höchstens 5 Server pro IP.
- Die Regeln sind Heuristiken. Liste vor dem Übernehmen prüfen, besonders bei "viele Server pro IP",
  denn auch große seriöse Hoster betreiben viele Server auf einer IP.

## Datenschutz

API Key und Einstellungen bleiben auf deinem Rechner, im Klartext, gespeichert über Java Preferences
(unter Windows: Registry unter `HKEY_CURRENT_USER\Software\JavaSoft\Prefs`). Der Key wird nur an
`api.steampowered.com` gesendet. Für die Spielbeschreibung kontaktiert das Tool die gelisteten Gameserver direkt,
so wie es auch der Serverbrowser im Spiel tut.

## Selbst bauen

Benötigt JDK 17+. Keine weiteren Abhängigkeiten, kein Build-Tool nötig.

```sh
./build.sh                    # Linux/macOS/Git Bash
.\build.ps1                   # Windows PowerShell
java -jar build/steam-blacklist-tool.jar
```

Optional mit Version: `VERSION=1.2.0 ./build.sh` oder `.\build.ps1 -Version 1.2.0`.

Aufbau (`src/main/java/blacklist/`):

| Datei | Zweck |
|---|---|
| `MainWindow.java` | Swing-Oberfläche |
| `SteamServerQuery.java` | Download der Serverliste über die Steam Web API |
| `A2sQuery.java` | Spielbeschreibung per A2S_INFO-UDP-Abfrage |
| `CountryDb.java` | IP-zu-Land-Zuordnung aus den RIR-Daten |
| `ServerScan.java` | Gruppierung nach IP und Auswertung der Regeln |
| `BlacklistFile.java` | Lesen und Schreiben der `server_blacklist.txt` |
| `BlacklistEntry.java`, `Ip.java`, `Json.java` | Datentyp und kleine Helfer |

## Release erstellen

Releases baut GitHub Actions ([`.github/workflows/release.yml`](.github/workflows/release.yml)).
Einen Versions-Tag pushen, der Workflow baut die JAR und hängt sie an ein neues GitHub-Release:

```sh
git tag v1.0.0
git push origin v1.0.0
```

## Mitmachen

Issues und Pull Requests sind willkommen, egal ob Fehlerberichte, neue Filterideen oder Unterstützung weiterer Source-Spiele.
Bitte keine externen Abhängigkeiten einbauen.

## Danksagung

- [Ballganda/css-server-blacklist](https://github.com/Ballganda/css-server-blacklist): ursprüngliche Idee und Filter-Heuristiken
- [Steam-Guide zur Server-Blacklist](https://steamcommunity.com/sharedfiles/filedetails/?id=3013281836)

## Lizenz

[MIT](LICENSE)

Kein offizielles Projekt von Valve Corporation. Steam und Counter-Strike sind Marken der Valve Corporation.
