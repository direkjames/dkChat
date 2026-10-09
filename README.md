# dkChat

Global chat plugin for our PurpurMC 26.3 server. Private plugin, made by **direk james**.

## Status

| Phase | Features | Status |
|---|---|---|
| 1 | Chat formats by permission, name hover tooltips, color pipeline, PlaceholderAPI, EssentialsX nicknames, DiscordSRV relay, `/dchat reload` | ✅ Done |
| 2 | `/msg` `/m` `/tell` `/whisper` `/r`, social spy, `/ignore`, LiteBans mute support | Planned |
| 3 | `[item]` `[inv]` `[echest]` with anti-dupe and anti-spam limits | Planned |
| 4 | `/ad` with Vault cost and rank cooldowns, timed announcements with `{center}`, clear chat | Planned |

## Requirements

- PurpurMC / Paper 26.x, Java 25
- Optional: PlaceholderAPI, EssentialsX, LuckPerms, DiscordSRV

## Building

Open the project in IntelliJ IDEA, then run the Maven `package` goal. The jar is written to `target/dkChat-<version>.jar`.

If Maven can't resolve `paper-api`, set `paper.version` in `pom.xml` to the exact version listed at
https://repo.papermc.io/repository/maven-public/io/papermc/paper/paper-api/

## Config files

| File | What it holds |
|---|---|
| `config.yml` | General on/off switches |
| `formats.yml` | Chat formats per rank, and name hover lines |
| `messages.yml` | Every message the plugin sends |

All text supports `&` codes, HEX (`&#FF00AA`, `<#FF00AA>`) and MiniMessage. Run `/dchat reload` after editing.

### Format placeholders

| Placeholder | Meaning |
|---|---|
| `{name}` | Display name, including EssentialsX `/nick` nicknames |
| `{player}` | Real Minecraft username |
| `{message}` | The chat message |
| `%...%` | Any PlaceholderAPI placeholder |

Each player gets the highest `priority` format they have the `permission` for. OP players have every permission, so they always get the highest one.

## Commands

| Command | Permission | Description |
|---|---|---|
| `/dchat help` | none | Command list |
| `/dchat format` | none | Shows which chat format you are using |
| `/dchat reload` | `dkchat.admin` | Reloads all config files |

## Permissions

| Permission | Default | Description |
|---|---|---|
| `dkchat.admin` | op | Admin commands |
| `dkchat.color.legacy` | op | `&` colors and named MiniMessage colors in chat |
| `dkchat.color.format` | op | Bold, italic, underline, strikethrough in chat |
| `dkchat.color.magic` | op | Obfuscated text in chat |
| `dkchat.color.hex` | op | HEX colors in chat |
| `dkchat.color.gradient` | op | `<gradient>` and `<rainbow>` in chat |
| `dkchat.color.*` | op | All of the color permissions |
| `dkchat.format.<name>` | none | Whatever permission you set on a format in `formats.yml` |

Players can never use click, hover or other interactive tags in their own messages, and PlaceholderAPI placeholders are never parsed in what players type.

## Compatibility notes

- **EssentialsX nicknames:** keep `change-displayname: true` in the Essentials config. If Essentials adds rank prefixes to display names (`add-prefix-suffix`), turn that off so prefixes don't show twice.
- **DiscordSRV:** dkChat formats chat through Paper's chat event and passes the colored message along, so DiscordSRV relays it. If your DiscordSRV config has `UseModernPaperChatEvent`, set it to `true`.
- **LiteBans:** muted players' chat is cancelled by LiteBans before dkChat formats it.
