# ChatFix

A Paper plugin that replaces the default chat handling and gives operators control over per-player prefixes and
postfixes, local/global chat separation, and hidden message segments.

## Requirements

- Paper 1.21 or compatible fork
- Java 17+

## Features

### Local and global chat

Messages are local by default: they are delivered only to players in the same world within a configurable radius.
Prefixing a message with `!` sends it as a global message to every online player, regardless of world or distance.
Global chat can be disabled by an administrator; while disabled, players attempting to use `!` receive a notice and the
message is not sent.

### Hidden segments

Any part of a message wrapped in curly braces, e.g. `{secret}`, is removed for regular players but shown as-is to the
sender and to players with the `chatmanager.seehidden` permission. If removing hidden segments leaves no visible text,
regular players do not receive the message at all.

### Per-player prefix/postfix

Administrators can set a prefix and a postfix per player, independently for local and global chat. Values support `&`
-based color codes and are stored persistently in `chatdata.yml`, keyed by player UUID.

### Localization

Plugin messages are available in Russian and English. The language is set in `config.yml`:

- `en` — English
- `ru` — Russian
- `auto` (default) — resolved from the receiving player's client locale; non-player senders (console) get English

## Commands

All commands require the `chatmanager.admin` permission.

| Command                                                              | Description                                                                                                                                                                   |
|----------------------------------------------------------------------|-------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| `/chatfix set <targets> <local\|global> <prefix\|postfix> <text>`    | Sets a prefix or postfix for one or more online players, selected via a standard player selector.                                                                             |
| `/chatfix offline <target> <local\|global> <prefix\|postfix> <text>` | Sets a prefix or postfix for a player by name. The player does not need to be online: the name is resolved from the local cache first, then from the Mojang API if not found. |
| `/chatfix radius <value>`                                            | Sets the local chat radius, in blocks.                                                                                                                                        |
| `/chatfix globalchat <on\|off>`                                      | Enables or disables global chat.                                                                                                                                              |

Enclose `<text>` in quotes if it contains spaces.

## Permissions

| Permission              | Description                                                           | Default |
|-------------------------|-----------------------------------------------------------------------|---------|
| `chatmanager.admin`     | Access to `/chatfix` (prefixes/postfixes, radius, global chat toggle) | op      |
| `chatmanager.seehidden` | View hidden `{segments}` in chat                                      | op      |

## Configuration

`config.yml`:

```yaml
settings:
  # Radius, in blocks, within which local messages are visible.
  # Changed with /chatfix radius <value>
  local-chat-radius: 15.0
  # Whether global chat ("!message") is enabled.
  # Changed with /chatfix globalchat <on|off>
  global-chat-enabled: true
  # Plugin message language: en, ru, or auto (based on player locale)
  language: auto
```

`local-chat-radius` and `global-chat-enabled` are also updated at runtime by the corresponding commands and persisted
back to this file.

## Data storage

Player prefixes, postfixes, and the name-to-UUID cache are stored in `chatdata.yml` inside the plugin's data folder.
Writes are debounced (one write per second of activity) and are flushed synchronously on server shutdown.

## Installation

1. Place the plugin jar in the server's `plugins` folder.
2. Start the server once to generate `config.yml`.
3. Adjust `config.yml` as needed and reload or restart the server.