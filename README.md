# BlueArcade - Capture The Wool

This resource is a **BlueArcade 3 module** and requires the core plugin to run.
Get BlueArcade 3 here: https://store.blueva.net/resources/resource/1-blue-arcade/

## Description
Capture enemy wools from their base and bring them back to yours. First team to capture all wools wins.

## Game type notes
This is a **Minigame**: it is designed for standalone arenas, but it can also be used inside party rotations. Minigames usually provide longer, feature-rich rounds.

## What you get with BlueArcade 3 + this module
- Party system (lobbies, queues, and shared party flow).
- Store-ready menu integration and vote menus.
- Victory effects and end-game celebrations.
- Scoreboards, timers, and game lifecycle management.
- Player stats tracking and placeholders.
- XP system, leaderboards, and achievements.
- Arena management tools and setup commands.

## Features
- Team size and team count configuration.
- Owner/capture team mapping per wool.
- Per-player chest duplication (items are never consumed from real chests).
- Block protection on wool locations and player-placed-only breaking.
- Team restricted zones to limit area access per team.
- Respawn on death with configurable delay.

## Arena setup
### Common steps
Use these steps to register the arena and attach the module:

- `/baa create [id] <standalone|party>` — Create a new arena in standalone or party mode.
- `/baa arena [id] setname [name]` — Give the arena a friendly display name.
- `/baa arena [id] setlobby` — Set the lobby spawn for the arena.
- `/baa arena [id] minplayers [amount]` — Define the minimum players required to start.
- `/baa arena [id] maxplayers [amount]` — Define the maximum players allowed.
- `/baa game [arena_id] add [minigame]` — Attach this minigame module to the arena.
- `/baa stick` — Get the setup tool to select regions.
- `/baa game [arena_id] [minigame] bounds set` — Save the game bounds for this arena.
- ~~`/baa game [arena_id] [minigame] spawn add`~~ — Not used in Capture The Wool.
  Use **`/baa game [arena_id] capture_the_wool team spawn <team_id>`** to configure team spawns.
- ~~`/baa game [arena_id] [minigame] time [minutes]`~~ — Not used in Capture The Wool.
  Capture The Wool has no time limit. The match ends when a team captures all enemy wool objectives.

### Module-specific steps
Finish the setup with the commands below:
- `/baa game [arena_id] capture_the_wool team count <value>` — Set the number of teams (minimum 2).
- `/baa game [arena_id] capture_the_wool team size <value>` — Set the players per team (minimum 2).
- `/baa game [arena_id] capture_the_wool team spawn <team_id>` — Set the spawn point for a team at your current location.
- `/baa game [arena_id] capture_the_wool team spawn remove <team_id>` — Remove the spawn point for a specific team.
- `/baa game [arena_id] capture_the_wool team zone <team_id>` — Add a restricted zone for a team (select region with the setup tool).
- `/baa game [arena_id] capture_the_wool wool create <wool_id> <MATERIAL> <owner_team>` — Create a wool objective with shared ID, material, owner team, and spawn point (select exactly one block with the setup tool).
- `/baa game [arena_id] capture_the_wool wool capture <wool_id> <capture_team>` — Set capture point and stealing team for that wool (select exactly one block with the setup tool). The owner team is rejected as capture team.
- `/baa game [arena_id] capture_the_wool wool list` — Show all configured wool objectives by owner team.
- `/baa game [arena_id] capture_the_wool region set` — Select and save the regeneration region.
- `/baa game [arena_id] capture_the_wool region clear` — Clear the regeneration region if needed.

> **Important:** `team_id` and `wool_id` are numeric-only values (`1`, `2`, `3`, ...). Also, `team_id` must be between `1` and your configured `team count`.

## Technical details
- **Minigame ID:** `capture_the_wool`
- **Module Type:** `MINIGAME`

## Links & Support
- Website: https://www.blueva.net
- Documentation: https://docs.blueva.net/books/blue-arcade
- Support: https://discord.com/invite/CRFJ32NdcK
