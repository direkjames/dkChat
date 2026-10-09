# dkChat

Global chat plugin for our PurpurMC 26.3 server. Private plugin, made by **direk james**.

## Status

| Phase | Features | Status |
|---|---|---|
| 1 | Chat formats by permission, name hover tooltips, color pipeline, PlaceholderAPI, EssentialsX nicknames, DiscordSRV relay, `/dchat reload` | ✅ Done |
| 2 | `/msg` `/m` `/tell` `/whisper` `/w` `/r`, social spy, `/ignore`, LiteBans mute support | ✅ Done |
| 3 | `[item]` `[inv]` `[echest]` with anti-dupe and anti-spam limits | ✅ Done |
| 4 | `/ad` with Vault cost and rank cooldowns, timed announcements with `{center}`, clear chat | ✅ Done |

## Requirements

- PurpurMC / Paper 26.x, Java 25
- Optional: PlaceholderAPI, EssentialsX, LuckPerms, LiteBans, Vault (any economy, e.g. dkBank), DiscordSRV

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
| `announcements.yml` | Timed announcements |

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
| `/msg <player> <message>` (`/m` `/tell` `/whisper` `/w`) | `dkchat.msg` | Send a private message |
| `/r <message>` (`/reply`) | `dkchat.msg` | Reply to the last person you talked to |
| `/spy` (`/socialspy`) | `dkchat.spy` | Turn private message spy on or off |
| `/ignore <player>` | `dkchat.ignore` | Ignore or unignore a player |
| `/ignore list` | `dkchat.ignore` | Show who you are ignoring |
| `/ad <message>` (`/advertise`) | tier permission | Post an advertisement. `/ad` alone shows your cooldown |
| `/clearchat` (`/cc` `/chatclear`) | `dkchat.clearchat` | Clear your own chat |
| `/clearchat all` | `dkchat.clearchat.all` | Clear chat for everyone except `dkchat.clearchat.exempt` |
| `/dchat announce <id>` | `dkchat.admin` | Send an announcement right now |
| `/dchat view <id>` | `dkchat.showcase.view` | Opens an [inv] / [echest] preview (used by the chat click) |

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
| `dkchat.msg` | everyone | Private messages and replies |
| `dkchat.msg.seevanished` | op | Message and tab-complete vanished players |
| `dkchat.spy` | op | See private messages (on automatically, `/spy` toggles) |
| `dkchat.spy.exempt` | nobody | Hides this player's private messages from spies |
| `dkchat.ignore` | everyone | Use `/ignore` |
| `dkchat.ignore.exempt` | op | Can't be ignored (staff) |
| `dkchat.showcase.item` | everyone | `[i]` / `[item]` in chat |
| `dkchat.showcase.inventory` | everyone | `[inv]` / `[inventory]` in chat |
| `dkchat.showcase.enderchest` | everyone | `[echest]` / `[ec]` / `[enderchest]` in chat |
| `dkchat.showcase.view` | everyone | Open [inv] / [echest] previews |
| `dkchat.showcase.bypasscooldown` | op | No showcase cooldowns |
| `dkchat.ad` | op | `/ad` default tier |
| `dkchat.ad.vip`, `dkchat.ad.mvp` | none | Better `/ad` tiers (set in `config.yml`) |
| `dkchat.ad.bypass` | op | No `/ad` cooldown or cost |
| `dkchat.clearchat` | everyone | Clear your own chat |
| `dkchat.clearchat.all` | op | Clear chat for everyone |
| `dkchat.clearchat.exempt` | op | Not affected by `/clearchat all` |
| `dkchat.format.<name>` | none | Whatever permission you set on a format in `formats.yml` |

Players can never use click, hover or other interactive tags in their own messages, and PlaceholderAPI placeholders are never parsed in what players type.

## [item], [inv], [echest]

- Tags, cooldowns and looks are in the `showcase` section of `config.yml`.
- **Anti-dupe:** inventories are copied the moment the message is sent. Previews open in a GUI where every click and drag is cancelled, and every copied item is marked; if one ever appears outside a preview it is deleted and logged. Previews are closed when the plugin shuts down.
- **Anti-spam:** per-tag cooldowns (kept across relogs), each tag shown once per message, and a message on cooldown is blocked.
- **Anti-crash:** oversized items (shulkers full of books) have heavy data removed from the chat hover.
- Previews expire after `snapshot-expire-minutes`.

## /ad

- Tiers in `config.yml` (`advertisement.tiers`): each has a priority, permission, cooldown (seconds) and Vault cost. Players get the highest priority tier they have.
- Checks happen in this order: tier, length, cooldown, balance, LiteBans mute. Money is taken only when the ad is actually sent.
- Cooldowns are saved on the player and survive relogs and restarts. Players who `/ignore` the sender don't see their ads.
- Lines, optional title and sound are configurable; start a line with `{center}` to center it.

## Announcements

- `announcements.yml`: interval, `random` (no repeats until all are shown) or `sequential`, minimum players, sound.
- Each announcement can have `permission` (only these players see it) and `hide-permission` (these players don't).
- Lines support `{center}`, clickable links, PlaceholderAPI per player, `{name}` and `{player}`.

## Security and performance

- **No click/command injection:** player messages can't use click, hover, insert or similar tags. PlaceholderAPI values are cleaned of those tags too (custom tags or nicknames from other plugins can't add a `run_command` click), and display names and item names have click/hover/insert removed before they are shown.
- **Color permissions can't be bypassed:** players without `dkchat.color.hex` get a parser that only knows named colors, so no escape trick enables HEX.
- **Typed values in plugin messages** (like a name in `/msg`) are never parsed as tags.
- **Anti-dupe preview GUIs** with marked items as a second layer (see above). The check runs on a read-only view of item data and never copies block data, so it costs almost nothing per inventory click.
- **Bounded memory:** at most 500 previews are kept (oldest dropped first), expired previews and cooldowns are cleaned every minute, per-player data is dropped on quit, and `/msg` and `/r` are unregistered when the plugin disables.
- **Main thread safety:** inventories are only read on the main thread; LiteBans checks run off the main thread.
- **Older config files:** missing options use the defaults built into the jar, and the console lists which ones are missing.

## Compatibility notes

- **EssentialsX nicknames:** keep `change-displayname: true` in the Essentials config. If Essentials adds rank prefixes to display names (`add-prefix-suffix`), turn that off so prefixes don't show twice.
- **DiscordSRV:** dkChat formats chat through Paper's chat event and passes the colored message along, so DiscordSRV relays it. If your DiscordSRV config has `UseModernPaperChatEvent`, set it to `true`.
- **LiteBans:** muted players' public chat is cancelled by LiteBans before dkChat formats it. Muted players also can't use `/msg` or `/r`; dkChat asks LiteBans in the background before sending.
- **Spy and ignore data** are saved on each player (persistent data), so they survive relogs and restarts. Spy is on automatically for anyone with `dkchat.spy`.
- **Vanish:** players the sender can't see are treated as offline in `/msg`, `/r` and tab completion.
