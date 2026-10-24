# Steam Server Blacklist Tool

**English** | [Deutsch](README.de.md)

A small desktop tool to build a `server_blacklist.txt` for Source engine games, mainly **Counter-Strike: Source**.
Import the file in the in-game server browser and Russian-language servers, servers from chosen countries
or servers with certain words in their name disappear from your server list.

## Why?

The CS:S server browser is flooded with fake servers: hundreds of entries on a single IP, impossible
player counts, redirects to other servers. The game can hide servers via its built-in blacklist, but
adding them one by one by hand is tedious. This tool finds them for you and writes the blacklist file.

## Features

- Step-by-step wizard with a dark interface: start, Steam key, filters, review, save
- Downloads the complete current server list of a game from Steam
- Flags IPs by simple rules:
  - **Cyrillic script** in the server name or game description (catches Russian/Ukrainian-language servers hosted anywhere)
  - **Country** of the IP (e.g. `RU`, `RU,BY`)
  - **Keywords** in the server name
- Review the result and untick IPs you want to keep
- Extend or edit an existing blacklist, add single IPs or paste whole IP lists
- Saves through the normal system file dialog (Explorer on Windows)
- Writes a valid `server_blacklist.txt`: one entry per IP, always with port `0` (= all ports of that IP), no duplicates

## Download

### Windows (no Java installation needed)

1. Download `steam-blacklist-tool-<version>-windows.zip` from the
   [latest release](https://github.com/j6845/steam_blacklist_server_tool/releases/latest).
2. Extract it anywhere.
3. Double-click `SteamBlacklistTool\Start.bat`.

The zip contains the tool and a minimal Java runtime (about 30 MB) that only includes what the tool needs.
Nothing gets installed.

### Linux, macOS, or Java already installed

Download `steam-blacklist-tool-<version>.jar` from the same release and start it with Java 17 or newer:

```
java -jar steam-blacklist-tool-<version>.jar
```

## Usage

The app is in German and walks you through five steps. Labels are quoted below as they appear in the app.

1. **Start:** "Neue Blacklist erstellen" (new list), "Vorhandene Blacklist erweitern" (load your existing
   file and add to it) or "Vorhandene Blacklist nur bearbeiten" (edit a file without searching).
2. **Steam-Key:** the server list comes from the official Steam Web API, which needs a free key.
   "Key-Seite öffnen" opens <https://steamcommunity.com/dev/apikey>. Any domain name works there, e.g. `localhost`.
   Paste the 32-character key. It is remembered for next time.
3. **Filter:** pick what to hide. An IP is flagged if **any** selected rule matches:

   | Option | Meaning |
   |---|---|
   | "Russisch- und ukrainischsprachige Server" | A server name or the game description (the "Game" column in the server browser) contains Cyrillic letters. On by default |
   | "Server aus bestimmten Ländern" | Country codes ([ISO 3166-1 alpha-2](https://en.wikipedia.org/wiki/ISO_3166-1_alpha-2)), comma separated, e.g. `RU` or `RU, BY` |
   | "Bestimmte Wörter im Servernamen" | Comma-separated keywords, matched case-insensitively |

   "Server suchen" takes about 20–30 seconds; the first run also downloads the country database
   (see [How it works](#how-it-works)). If you go back and change the filters within 10 minutes, the
   downloaded list is reused and the result appears instantly.
4. **Prüfen:** flagged IPs are ticked. The "Grund" column says which rule matched. Untick IPs you want to keep,
   search the list, or show the IPs that were not flagged as well.
5. **Speichern:** check the final list, rename entries (double-click; the name is shown in the game),
   add or remove IPs, then "Datei speichern …". The app then shows how to import the file in the game.

### Import in the game

1. Open the server browser and switch to the **Blacklisted Servers** tab (German client: "Server-Blacklist").
2. Click **Import servers from file** and select the saved `server_blacklist.txt`.

Importing adds to the servers you already blacklisted. Alternatively, close the game and copy the file to
`Steam/steamapps/common/Counter-Strike Source/cstrike/cfg/server_blacklist.txt`.
This replaces the game's current blacklist, so start with "Vorhandene Blacklist erweitern" and load that file first if you want to keep it.

Very large lists make the server browser freeze for a moment when it opens.

## How it works

- **Server list:** Steam Web API `IGameServersService/GetServerList`. The API returns at most 10,000 servers
  per request, and CS:S alone lists far more (around 58,000, mostly fakes). Whenever a request hits that cap,
  the tool splits it further (by region, players, VAC, dedicated, OS, password, finally by map) until every
  part is complete, then merges and deduplicates.
- **Game description:** the Steam Web API doesn't provide the "Game" column, so the tool asks the game
  servers directly with the standard Source `A2S_INFO` UDP query, the same query the in-game server browser
  sends. Only IPs without a Cyrillic server name are asked, at most 5 servers per IP: each queried address
  takes up an entry in your router's NAT table, and asking tens of thousands of fake servers would overflow it.
- **Country lookup:** the public allocation statistics of the five Regional Internet Registries
  (RIPE NCC, ARIN, APNIC, LACNIC, AFRINIC). No account or extra key needed. About 45 MB are downloaded to
  `~/.steam_blacklist_tool/rir/` on first use and refreshed after 7 days.
- **File format:** Valve KeyValues, UTF-8 without BOM:
  ```
  "serverblacklist"
  {
  	"server"
  	{
  		"name"			"Land RU | 2.56.88.98 | Example server name"
  		"date"			"1747308377"
  		"addr"			"2.56.88.98:0"
  	}
  }
  ```
  `date` is a Unix timestamp. `addr` is always `IP:0`, which blocks every port of that IP, the same way
  the game does when you blacklist a server yourself.

## Limitations

- The country is the **registration country** of the IP block, not necessarily where the server physically runs.
- The blacklist only knows single IPs, not IP ranges. "All Russian servers" means all of them listed
  **right now**. New servers show up after loading again.
- The Cyrillic rule also hits names that only use a Cyrillic look-alike letter for style (e.g. `м` in `Gλмß`).
- Game descriptions are only known for servers that answer the UDP query, and for at most 5 servers per IP.
- Rules are heuristics. Check the list in the review step before saving.

## Privacy

Your API key and settings are stored only on your computer, in plain text, via Java Preferences
(on Windows: registry under `HKEY_CURRENT_USER\Software\JavaSoft\Prefs`). The key is only sent to
`api.steampowered.com`. For the game description, the tool contacts the listed game servers directly,
just like the in-game server browser does.

## Building from source

Requires JDK 17+ and an internet connection for the first build. No build tool needed: the scripts
download [FlatLaf](https://www.formdev.com/flatlaf/) (dark theme and native file dialogs) from Maven Central,
verify its SHA-256 checksum and bundle it into the JAR, so the release stays a single file.

```sh
./build.sh                    # Linux/macOS/Git Bash
.\build.ps1                   # Windows PowerShell
java -jar build/steam-blacklist-tool.jar
```

Optionally set a version: `VERSION=1.2.0 ./build.sh` or `.\build.ps1 -Version 1.2.0`.

The Windows package with the bundled runtime is built on Windows with `.\package.ps1 -Version 1.2.0`.
It uses `jlink` to create a Java runtime with only the modules the tool needs.

Source layout (`src/main/java/blacklist/`):

| File | Purpose |
|---|---|
| `MainWindow.java` | Wizard UI |
| `Theme.java`, `FileDialogs.java` | Dark theme and native file dialogs (FlatLaf) |
| `BlacklistTableModel.java`, `ScanTableModel.java` | Tables for the review and save steps |
| `SteamServerQuery.java` | Steam Web API server list download |
| `A2sQuery.java` | Game description via A2S_INFO UDP query |
| `CountryDb.java` | IP to country lookup from RIR data |
| `ServerScan.java` | Grouping by IP and rule evaluation |
| `BlacklistFile.java` | Reading and writing `server_blacklist.txt` |
| `BlacklistEntry.java`, `Ip.java`, `Json.java` | Data type and small helpers |

## Releasing

Releases are built by GitHub Actions ([`.github/workflows/release.yml`](.github/workflows/release.yml)).
Push a version tag and the workflow builds the JAR and the Windows package and attaches both to a new GitHub release:

```sh
git tag v1.0.0
git push origin v1.0.0
```

## Contributing

Issues and pull requests are welcome, whether it's bug reports, new filter ideas or support for other Source games.
Please don't add dependencies beyond FlatLaf.

## Credits

- [Ballganda/css-server-blacklist](https://github.com/Ballganda/css-server-blacklist): the original idea and filter heuristics
- [FlatLaf](https://www.formdev.com/flatlaf/) by FormDev Software (Apache License 2.0): dark theme and native file dialogs
- [Steam guide on the server blacklist](https://steamcommunity.com/sharedfiles/filedetails/?id=3013281836)

## License

[MIT](LICENSE)

Not affiliated with Valve Corporation. Steam and Counter-Strike are trademarks of Valve Corporation.
