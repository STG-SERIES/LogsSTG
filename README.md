# LogsSTG

Paper plugin that opens a local page with the live server console and a line to run commands.

**License:** [MIT](LICENSE)

## Supported versions

Each folder is a full build. Use the jar that matches the server.

| Folder | Paper API | `api-version` | Java |
|--------|-----------|---------------|------|
| `versions/1.21.11` | `1.21.11-R0.1-SNAPSHOT` | `1.21` | 21+ |
| `versions/26.1` | `26.1.1.build.29-alpha` | `26.1` | 25+ |
| `versions/26.1.2` | `26.1.2.build.74-stable` | `26.1` | 25+ |
| `versions/26.2` | `26.2.build.133-stable` | `26.2` | 25+ |
| `versions/26.3` | `26.3.build.168-beta` | `26.3` | 25+ |

Paper does not publish a bare 26.1 API artifact. `versions/26.1` targets the 26.1.1 line.

## Install

1. Download the jar for your server from [Releases](https://github.com/STG-SERIES/LogsSTG/releases)
2. Put it in the server `plugins` folder
3. Restart the server
4. Open [http://127.0.0.1:8765/](http://127.0.0.1:8765/)

| Server | Release |
|--------|---------|
| Paper 1.21.11 | [LogsSTG-1.0.0.jar](https://github.com/STG-SERIES/LogsSTG/releases/tag/v1.0.0) |
| Paper 26.1.1 | [LogsSTG-1.0.0-26.1.jar](https://github.com/STG-SERIES/LogsSTG/releases/tag/v1.0.0-26.1) |
| Paper 26.1.2 | [LogsSTG-1.0.0-26.1.2.jar](https://github.com/STG-SERIES/LogsSTG/releases/tag/v1.0.0-26.1.2) |
| Paper 26.2 | [LogsSTG-1.0.0-26.2.jar](https://github.com/STG-SERIES/LogsSTG/releases/tag/v1.0.0-26.2) |
| Paper 26.3 | [LogsSTG-1.0.0-26.3.jar](https://github.com/STG-SERIES/LogsSTG/releases/tag/v1.0.0-26.3) |

The page is the log. The line at the bottom sends a console command. A leading `/` is optional. Up and down arrows recall earlier commands.

Startup logs the exact address, for example `Live logs at http://127.0.0.1:8765/`.

## Config

`plugins/LogsSTG/config.yml`

```yaml
# Loopback only. Anyone who can open this port can run console commands.
host: 127.0.0.1
port: 8765
# Lines kept for a viewer that connects after they were printed.
history: 1000
```
### ⚠️ Warning! If you want the logs to be accesible from other devices of your local network, you need to set the `host:` to `0.0.0.0`

| Key | Default | Meaning |
|-----|---------|---------|
| `host` | `127.0.0.1` | Address the page binds to |
| `port` | `8765` | Port |
| `history` | `1000` | Recent lines sent when the page opens (50–5000) |

Leave `host` on `127.0.0.1`. Binding any other address lets anyone who can reach that port run commands as the console.

## Build

Java 25+ is required to build every module at once. Java 21 is enough for `versions/1.21.11` alone.

```bash
mvn package
```

Jars:

- `versions/1.21.11/target/LogsSTG.jar`
- `versions/26.1/target/LogsSTG.jar`
- `versions/26.1.2/target/LogsSTG.jar`
- `versions/26.2/target/LogsSTG.jar`
- `versions/26.3/target/LogsSTG.jar`

Build one version:

```bash
mvn -f versions/26.2/pom.xml package
```
