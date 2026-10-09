# LogsSTG

Paper plugin that opens a local page with the live server console and a line to run commands.

**License:** [MIT](LICENSE)

## Supported versions

| Minecraft | Server | Java |
|-----------|--------|------|
| 1.21.11 | Paper `1.21.11-R0.1-SNAPSHOT` | 21+ |

## Install

1. Download `LogsSTG-1.0.0.jar` from [Releases](https://github.com/STG-SERIES/LogsSTG/releases)
2. Put it in the server `plugins` folder
3. Restart the server
4. Open [http://127.0.0.1:8765/](http://127.0.0.1:8765/)

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
⚠️ ### Warning! If you want the logs to be accesible from other devices of your local network, you need to set the `host:` to `0.0.0.0`

| Key | Default | Meaning |
|-----|---------|---------|
| `host` | `127.0.0.1` | Address the page binds to |
| `port` | `8765` | Port |
| `history` | `1000` | Recent lines sent when the page opens (50–5000) |

Leave `host` on `127.0.0.1`. Binding any other address lets anyone who can reach that port run commands as the console.

## Build

Requires Java 21+ and Maven.

```bash
mvn package
```

The jar is `target/LogsSTG.jar`.
