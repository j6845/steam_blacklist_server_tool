# Steam Server Blacklist Tool

**English** | [Deutsch](README.de.md)

A small desktop tool to build a `server_blacklist.txt` for Source engine games, mainly **Counter-Strike: Source**.
Import the file in the in-game server browser and fake servers, spam servers, redirect/bot servers or
all servers from a chosen country (e.g. Russia) disappear from your server list.

## Why?

The CS:S server browser is flooded with fake servers: hundreds of entries on a single IP, impossible
player counts, redirects to other servers. The game can hide servers via its built-in blacklist, but
adding them one by one by hand is tedious. This tool finds them for you and writes the blacklist file.

## Features

- Downloads the full current server list of a game from Steam
- Flags IPs by simple, adjustable rules:
  - **Country** of the IP (e.g. `RU`, `RU,BY`)
  - **Many servers on one IP** (typical for fake/spam hosts, default: 30 or more)
  - **Unrealistic slot count** (`max_players` above a limit, default: 66)
  - **Keywords** in the server name
- Review the result and select/deselect IPs before adding them
- Add IPs by hand, paste whole IP lists, import and merge existing blacklist files
- Writes a valid `server_blacklist.txt`: one entry per IP, always with port `0` (= all ports of that IP), no duplicates

## Download

1. Install **Java 17 or newer**, for example from [Adoptium](https://adoptium.net/).
2. Download `steam-blacklist-tool-<version>.jar` from the
   [latest release](https://github.com/j6845/steam_blacklist_server_tool/releases/latest).
3. Start it with a double-click, or from a terminal:
   ```
   java -jar steam-blacklist-tool-<version>.jar
   ```

The JAR is the only file you need. Works on Windows, Linux and macOS.

## Usage

The user interface is in German. Labels are quoted below as they appear in the app.

### 1. Get a Steam Web API key

The server list is fetched through the official Steam Web API, which needs a free key:
<https://steamcommunity.com/dev/apikey> (any domain name works, e.g. `localhost`).

### 2. Find servers (tab "Server suchen")

1. Paste your key into "Steam Web API Key".
2. Set the game's AppID ("AppID", `240` = Counter-Strike: Source).
3. Set the rules. An IP is flagged if **any** rule matches. Set a number to `0` or leave a field empty to turn that rule off.

   | Field | Meaning |
   |---|---|
   | "Länder" | Country codes ([ISO 3166-1 alpha-2](https://en.wikipedia.org/wiki/ISO_3166-1_alpha-2)), comma separated, e.g. `RU` or `RU,BY` |
   | "Ab N Servern pro IP" | Flag IPs that host at least N servers |
   | "max_players über" | Flag servers with more slots than this |
   | "Name enthält" | Comma-separated keywords, matched case-insensitively against the server name |

4. Click "Server laden". The first run also downloads the country database (see [How it works](#how-it-works)), so it takes a moment.
5. Check the result table. Flagged IPs are already ticked. The "Grund" column says which rule matched.
   Changed a rule? "Neu bewerten" applies it without downloading again.
6. Click "Ausgewählte zur Blacklist".

### 3. Save the blacklist (tab "Blacklist")

- "Eintrag hinzufügen..." adds a single IP, "IPs einfügen..." adds a pasted list (one IP per line).
- "Datei importieren..." merges an existing `server_blacklist.txt`, for example the one your game already uses.
- Double-click a name to edit it. The name is what the game shows in the "Blacklisted Servers" tab.
- "Speichern als..." writes the file.

### 4. Import in the game

1. Open the server browser and switch to the **Blacklisted Servers** tab.
2. Click **Import servers from file** and select the saved `server_blacklist.txt`.

Importing adds to the servers you already blacklisted. Alternatively, close the game and copy the file to
`Steam/steamapps/common/Counter-Strike Source/cstrike/cfg/server_blacklist.txt`.
This replaces the game's current blacklist, so import that one into the tool first if you want to keep it.

Very large lists make the server browser freeze for a moment when it opens. Keep the list focused.

## How it works

- **Server list:** Steam Web API `IGameServersService/GetServerList`. The API limits results per request,
  so the tool splits the query by region and by empty/non-empty servers, then merges and deduplicates.
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
- Rules are heuristics. Check the list before you add it, especially for the "many servers per IP" rule,
  since big legitimate hosters can run many servers on one IP too.

## Privacy

Your API key and settings are stored only on your computer, in plain text, via Java Preferences
(on Windows: registry under `HKEY_CURRENT_USER\Software\JavaSoft\Prefs`). The key is only sent to
`api.steampowered.com`.

## Building from source

Requires JDK 17+. No other dependencies, no build tool needed.

```sh
./build.sh                    # Linux/macOS/Git Bash
.\build.ps1                   # Windows PowerShell
java -jar build/steam-blacklist-tool.jar
```

Optionally set a version: `VERSION=1.2.0 ./build.sh` or `.\build.ps1 -Version 1.2.0`.

Source layout (`src/main/java/blacklist/`):

| File | Purpose |
|---|---|
| `MainWindow.java` | Swing UI |
| `SteamServerQuery.java` | Steam Web API server list download |
| `CountryDb.java` | IP to country lookup from RIR data |
| `ServerScan.java` | Grouping by IP and rule evaluation |
| `BlacklistFile.java` | Reading and writing `server_blacklist.txt` |
| `BlacklistEntry.java`, `Ip.java`, `Json.java` | Data type and small helpers |

## Releasing

Releases are built by GitHub Actions ([`.github/workflows/release.yml`](.github/workflows/release.yml)).
Push a version tag and the workflow builds the JAR and attaches it to a new GitHub release:

```sh
git tag v1.0.0
git push origin v1.0.0
```

## Contributing

Issues and pull requests are welcome, whether it's bug reports, new filter ideas or support for other Source games.
Please keep the project free of external dependencies.

## Credits

- [Ballganda/css-server-blacklist](https://github.com/Ballganda/css-server-blacklist): the original idea and filter heuristics
- [Steam guide on the server blacklist](https://steamcommunity.com/sharedfiles/filedetails/?id=3013281836)

## License

[MIT](LICENSE)

Not affiliated with Valve Corporation. Steam and Counter-Strike are trademarks of Valve Corporation.
