# Steam Server Blacklist Tool

[English](README.md) | **Deutsch**

Kleines Desktop-Tool, das eine `server_blacklist.txt` für Source-Spiele erstellt, vor allem für **Counter-Strike: Source**.
Die Datei wird im Ingame-Serverbrowser importiert. Danach verschwinden russischsprachige Server, Server aus
gewählten Ländern oder Server mit bestimmten Wörtern im Namen aus der Serverliste.

## Warum?

Der CS:S-Serverbrowser ist voll mit Fake-Servern: hunderte Einträge auf einer IP, unmögliche
Spielerzahlen, Weiterleitungen auf andere Server. Das Spiel kann Server über seine Blacklist ausblenden,
aber jeden einzeln von Hand einzutragen ist mühsam. Dieses Tool findet sie und schreibt die Datei.

## Funktionen

- Geführter Ablauf in dunkler Oberfläche: Start, Steam-Key, Filter, Prüfen, Speichern
- Lädt die komplette aktuelle Serverliste eines Spiels von Steam
- Markiert IPs nach einfachen Regeln:
  - **Kyrillische Schrift** im Servernamen oder in der Spielbeschreibung (erwischt russisch-/ukrainischsprachige Server, egal wo sie gehostet sind)
  - **Land** der IP (z.B. `RU`, `RU,BY`)
  - **Stichwörter** im Servernamen
- Ergebnis prüfen und Haken bei IPs entfernen, die bleiben sollen
- Bestehende Blacklist erweitern oder bearbeiten, einzelne IPs hinzufügen oder ganze IP-Listen einfügen
- Speichern über den normalen Datei-Dialog des Systems (unter Windows der Explorer)
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

Das Tool führt dich in fünf Schritten durch:

1. **Start:** "Neue Blacklist erstellen", "Vorhandene Blacklist erweitern" (deine bisherige Datei laden und
   ergänzen) oder "Vorhandene Blacklist nur bearbeiten" (ohne Serversuche).
2. **Steam-Key:** Die Serverliste kommt über die offizielle Steam Web API, dafür braucht es einen kostenlosen Key.
   "Key-Seite öffnen" öffnet <https://steamcommunity.com/dev/apikey>. Als Domain geht alles, z.B. `localhost`.
   Den 32-stelligen Key einfügen. Er wird für das nächste Mal gespeichert.
3. **Filter:** auswählen, was weg soll. Eine IP wird markiert, wenn **eine** gewählte Regel zutrifft:

   | Option | Bedeutung |
   |---|---|
   | "Russisch- und ukrainischsprachige Server" | Servername oder Spielbeschreibung (Spalte "Spiel" im Serverbrowser) enthält kyrillische Buchstaben. Standardmäßig an |
   | "Server aus bestimmten Ländern" | Ländercodes ([ISO 3166-1 alpha-2](https://de.wikipedia.org/wiki/ISO-3166-1-Kodierliste)), kommagetrennt, z.B. `RU` oder `RU, BY` |
   | "Bestimmte Wörter im Servernamen" | Kommagetrennte Stichwörter, Groß-/Kleinschreibung egal |

   "Server suchen" dauert etwa 20–30 Sekunden, beim ersten Mal wird zusätzlich die Länder-Datenbank geladen
   (siehe [Funktionsweise](#funktionsweise)). Gehst du innerhalb von 10 Minuten zurück und änderst die Filter,
   wird die geladene Liste wiederverwendet und das Ergebnis ist sofort da.
4. **Prüfen:** Markierte IPs sind angehakt, die Spalte "Grund" zeigt die zutreffende Regel. Haken bei IPs
   entfernen, die bleiben sollen. Die Liste lässt sich durchsuchen, auf Wunsch auch mit den nicht markierten IPs.
5. **Speichern:** Endgültige Liste prüfen, Namen per Doppelklick ändern (sie erscheinen im Spiel), IPs
   hinzufügen oder entfernen, dann "Datei speichern …". Danach zeigt das Tool, wie die Datei ins Spiel kommt.

### Im Spiel importieren

1. Serverbrowser öffnen, Tab **Server-Blacklist** (englischer Client: "Blacklisted Servers").
2. **Import servers from file** klicken und die gespeicherte `server_blacklist.txt` wählen.

Der Import ergänzt die bereits geblacklisteten Server. Alternativ das Spiel schließen und die Datei nach
`Steam/steamapps/common/Counter-Strike Source/cstrike/cfg/server_blacklist.txt` kopieren.
Das ersetzt die aktuelle Blacklist des Spiels. Wer sie behalten will, startet mit "Vorhandene Blacklist erweitern" und lädt sie vorher.

Sehr große Listen lassen den Serverbrowser beim Öffnen kurz einfrieren.

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
- Die Regeln sind Heuristiken. Liste im Schritt "Prüfen" vor dem Speichern kontrollieren.

## Datenschutz

API Key und Einstellungen bleiben auf deinem Rechner, im Klartext, gespeichert über Java Preferences
(unter Windows: Registry unter `HKEY_CURRENT_USER\Software\JavaSoft\Prefs`). Der Key wird nur an
`api.steampowered.com` gesendet. Für die Spielbeschreibung kontaktiert das Tool die gelisteten Gameserver direkt,
so wie es auch der Serverbrowser im Spiel tut.

## Selbst bauen

Benötigt JDK 17+ und beim ersten Build Internet. Kein Build-Tool nötig: Die Scripts laden
[FlatLaf](https://www.formdev.com/flatlaf/) (dunkles Theme und native Datei-Dialoge) von Maven Central,
prüfen die SHA-256-Prüfsumme und packen es mit in die JAR. Das Release bleibt so eine einzige Datei.

```sh
./build.sh                    # Linux/macOS/Git Bash
.\build.ps1                   # Windows PowerShell
java -jar build/steam-blacklist-tool.jar
```

Optional mit Version: `VERSION=1.2.0 ./build.sh` oder `.\build.ps1 -Version 1.2.0`.

Aufbau (`src/main/java/blacklist/`):

| Datei | Zweck |
|---|---|
| `MainWindow.java` | Wizard-Oberfläche |
| `Theme.java`, `FileDialogs.java` | Dunkles Theme und native Datei-Dialoge (FlatLaf) |
| `BlacklistTableModel.java`, `ScanTableModel.java` | Tabellen für die Schritte Prüfen und Speichern |
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
Bitte keine Abhängigkeiten außer FlatLaf einbauen.

## Danksagung

- [Ballganda/css-server-blacklist](https://github.com/Ballganda/css-server-blacklist): ursprüngliche Idee und Filter-Heuristiken
- [FlatLaf](https://www.formdev.com/flatlaf/) von FormDev Software (Apache License 2.0): dunkles Theme und native Datei-Dialoge
- [Steam-Guide zur Server-Blacklist](https://steamcommunity.com/sharedfiles/filedetails/?id=3013281836)

## Lizenz

[MIT](LICENSE)

Kein offizielles Projekt von Valve Corporation. Steam und Counter-Strike sind Marken der Valve Corporation.
