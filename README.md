# ChatFix

A Paper plugin that replaces the default chat handling and gives operators control over per-player prefixes and
postfixes, local/world/global chat separation, per-player chat settings, and hidden message segments.

## Requirements

- Paper 1.21 or compatible fork
- Java 17+

## Features

### Chat modes

The mode is selected by the start of the message:

| Message   | Mode   | Who receives it                                                                   |
|-----------|--------|-----------------------------------------------------------------------------------|
| `hello`   | local  | Players in the sender's world within `local-chat-radius`                          |
| `!hello`  | world  | Players in the sender's world; within `world-chat-radius` (`0` = the whole world) |
| `!!hello` | global | Every online player, regardless of world or distance                              |

Each mode can be turned on or off (`local-chat-enabled`, `world-chat-enabled`, `global-chat-enabled`).

When a player writes in a disabled mode, the result depends on `feedback-enabled`:

- `true` (default) — the message is not sent and the player is told that the administration disabled this chat.
- `false` — no notice; the whole message (including the leading `!` / `!!`) is sent to local chat instead. If local chat
  is disabled too, the message is silently dropped.

### Hidden segments

Any part of a message wrapped in curly braces, e.g. `{secret}`, is removed for regular players but shown as-is to the
sender and to players with the `chatmanager.seehidden` permission. If removing hidden segments leaves no visible text,
regular players do not receive the message at all.

### Per-player prefix/postfix

Administrators can set a prefix and a postfix per player, independently for local, world and global chat. Values support
`&`-based color codes.

If no prefix is set for a mode, the default prefix `<PlayerName>: ` is used. To send messages without any prefix, set it
explicitly to an empty string (`""`).

### Per-player chat settings

Every player can override the global settings for each mode individually — whether the mode is enabled for them and (for
local and world chat) its radius. These settings apply to the **sender**: they define which modes the player can write
in and how far their messages reach. Overrides are switched on and off with a flag, so the stored value is kept when you
go back to `default`.

### Offline players

`/chatfix offline` works for players who have never joined the server: the data is stored under `offline-players` by
nickname (no network requests are made). When the player joins for the first time, the entry is moved to
`players` under their UUID.

### Localization

Plugin messages are available in Russian and English. The language is set in `config.yml`:

- `en` — English
- `ru` — Russian
- `auto` (default) — resolved from the receiving player's client locale; non-player senders (console) get English

## Commands

All commands require the `chatmanager.admin` permission. Enclose `<text>` in quotes if it contains spaces.

### Per-player

`set` takes a player selector (online players only); `offline` takes a nickname (`A-Z`, `a-z`, `0-9`, `_`, up to 16
characters). Both accept the same `<mode> <param> <value>` tail.

| Command                                                          | Description                                                                                                         |
|------------------------------------------------------------------|---------------------------------------------------------------------------------------------------------------------|
| `/chatfix set <targets> <mode> prefix <text>`                    | Sets the prefix. `<mode>` is `local`, `world` or `global`.                                                          |
| `/chatfix set <targets> <mode> postfix <text>`                   | Sets the postfix.                                                                                                   |
| `/chatfix set <targets> <mode> enabled <true\|false\|default>`   | Enables/disables the mode for this player, or returns to the global setting.                                        |
| `/chatfix set <targets> <local\|world> radius <number\|default>` | Sets the player's radius (local: min 1, world: min 0 = whole world), or returns to the global one.                  |
| `/chatfix offline <target> <mode> <param> <value>`               | Same as `set`, by nickname. Resolved from `chatdata.yml`, then online players, otherwise stored for the first join. |

### Global settings

| Command                         | Description                                                    |
|---------------------------------|----------------------------------------------------------------|
| `/chatfix radius <value>`       | Sets the local chat radius, in blocks.                         |
| `/chatfix worldradius <value>`  | Sets the world chat radius, in blocks (`0` = the whole world). |
| `/chatfix localchat <on\|off>`  | Enables or disables local chat.                                |
| `/chatfix worldchat <on\|off>`  | Enables or disables world chat.                                |
| `/chatfix globalchat <on\|off>` | Enables or disables global chat.                               |
| `/chatfix feedback <on\|off>`   | Enables or disables notices about disabled chat modes.         |

## Permissions

| Permission              | Description                                                | Default |
|-------------------------|------------------------------------------------------------|---------|
| `chatmanager.admin`     | Access to `/chatfix` (per-player and global chat settings) | op      |
| `chatmanager.seehidden` | View hidden `{segments}` in chat                           | op      |

## Configuration

`config.yml`:

```yaml
settings:
  # Whether local chat (messages without "!") is enabled.
  local-chat-enabled: true
  # Radius, in blocks, within which local messages are visible.
  local-chat-radius: 15.0
  # Whether world chat ("!message") is enabled.
  world-chat-enabled: true
  # World chat radius, in blocks. 0 = the whole world.
  world-chat-radius: 0.0
  # Whether global chat ("!!message") is enabled.
  global-chat-enabled: true
  # If a player writes in a disabled mode:
  #   true  - tell them the chat is disabled;
  #   false - silently send the message to local chat (keeping the leading "!" / "!!").
  feedback-enabled: true
  # Plugin message language: en, ru, or auto (based on player locale)
  language: auto
```

Every value except `language` can be changed at runtime with the corresponding command and is persisted back to this
file.

## Data storage

Everything else is stored in `chatdata.yml` inside the plugin's data folder:

```yaml
players:
  UUID:
    name: Player
    local-prefix: '&6Player: '
    local-postfix: ''
    world-prefix: ''
    world-postfix: ''
    global-prefix: ''
    global-postfix: ''
    custom-local-chat-mode: true      # use the value below instead of the global one
    local-chat-enabled: false
    custom-local-chat-radius: false
    local-chat-radius: 30.0
    custom-world-chat-mode: false
    world-chat-enabled: true
    custom-world-chat-radius: true
    world-chat-radius: 100.0
    custom-global-chat-mode: false
    global-chat-enabled: true
offline-players: # never joined yet; moved to "players" on first join
  SomeNewPlayer:
    local-prefix: '&aSomeNewPlayer: '
```

Writes are debounced (one write per second of activity) and are flushed synchronously on server shutdown.

## Installation

1. Place the plugin jar in the server's `plugins` folder.
2. Start the server once to generate `config.yml`.
3. Adjust `config.yml` as needed and restart the server. Servers upgrading from an older version: missing keys fall back
   to the defaults above, so add the new ones to your existing `config.yml` manually.