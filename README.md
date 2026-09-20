# BadgerQuest

Daily rotating quest system for Paper 1.21+. Each player is dealt a fresh set of
quests every day, drawn from a configurable item pool, with streak rewards for
consecutive days of activity.

The plugin is intentionally small in scope: no branching quest chains, no
scripted dialogue, no build-your-own-quest editor. What it does do is give a
server a lightweight retention mechanic that survives restarts, resets on a
schedule you choose, and rewards players for showing up.

## How it works

At the start of the reset hour the plugin generates a private quest list for
every player:

- `quests_per_day` quests per player (default: 7)
- `items_per_quest` objectives per quest (default: 3)
- Objectives are drawn at random from `item_pool` in `config.yml`
- Each objective's target quantity is rolled inside its own `min`/`max` range

Players see their list with `/quest`. As they pick items up (through natural
gameplay: mining, farming, crafting, trading), matching items are consumed and
counted against active objectives. A bossbar surfaces progress on the item they
most recently picked up and fades after a few seconds.

When every objective in a quest is complete the quest is marked done. When
every quest is done the day is complete, and the player's streak advances.

## Streaks and milestones

The streak counter tracks consecutive days on which the player finished all
their quests. Miss `reset_after_days` days and the streak resets to zero.

Milestone rewards fire as console commands with `%player%` substituted. Any
day number can be a milestone; the default config rewards days 3, 7, 14 and 30.

## Storage

Player state (assigned quests, progress, streak counter, last completion date)
persists in SQLite in the plugin's data folder. Nothing is written to server
world data, so removing the plugin cleanly leaves no trace behind.

## Commands and permissions

| Command | Description | Permission |
|---|---|---|
| `/quest` | Open the quest menu | `badgerquest.use` (default: true) |
| `/quest reload` | Reload config | `badgerquest.admin` (op) |
| `/quest reset <player>` | Reset a player's quests for the day | `badgerquest.admin` (op) |

Command aliases: `/bq`, `/badgerquest`.

## Configuration highlights

- `reset_hour` — 0-23, server local hour when quests regenerate
- `timezone` — `"system"` or an IANA zone like `"Asia/Tehran"`
- `item_pool` — list of objectives (material, custom-model-data, display name,
  min/max quantity). Custom-model-data of `-1` means "ignore the CMD, match any
  item of that material".
- `bossbar` — color, style, fade timing, title format with `{item}`, `{have}`,
  `{need}`, `{remaining}` placeholders
- `streak.milestone_rewards` — arbitrary day number to command list

See the shipped `config.yml` for the full annotated schema.

## PlaceholderAPI

The following placeholders are exposed when PlaceholderAPI is installed:

- `%badgerquest_streak%` — current streak in days
- `%badgerquest_quests_done_today%` — number of quests completed today
- `%badgerquest_active_quest%` — the display name of the player's current quest

## Build

```
mvn clean package
```

Drop the shaded jar from `target/` into your `plugins/` folder. Paper 1.21+ is
required; PlaceholderAPI is optional (`softdepend`).

## Dependencies

- Paper API 1.21.4
- PlaceholderAPI 2.11.6 (optional)
- SQLite JDBC 3.46

## License

All rights reserved. Personal project; please open an issue before reusing.
