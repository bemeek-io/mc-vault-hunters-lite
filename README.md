# Vault Hunters Lite

A **server-side, vanilla-client** take on the Vault Hunters loop for Paper.
Gather resources, forge a Vault Crystal at an altar, and raid a procedurally
generated vault — leveling up, investing RPG stats, researching features, and
linking your storage as you go.

No client mods. No modpack. Friends on the vanilla launcher just join.

## Why this exists

The Vault Hunters modpack is great, but:

1. **It needs dozens of client mods** — a dealbreaker for vanilla players.
2. **It's heavy** — laggy on non-gaming machines.
3. **Vaults get samey** — the same rooms, the same loop, run after run.
4. **It's ability-focused** — where an RPG-style stat grind would feel better.

This plugin keeps what's loved (unique resources gating vault access, vault
leveling with beginner's grace and spirit revival, searchable linked storage,
knowledge-based feature unlocks) and fixes the four complaints above.

## The loop

1. **Gather.** Right-click a **lodestone** (the Vault Altar) to see your
   crystal's price: 4 resources drawn from your level's tier, stable until you
   level up — so you can plan the gathering trip. Pay it to **infuse a Vault
   Crystal**.
2. **Raid.** Right-click the altar with the crystal. A private vault is
   generated for you (and your party — `/vh party invite`); you're teleported
   in with a time limit on the bossbar and an objective revealed on entry.
3. **Get out.** Complete the objective to open the glowing exit pad, loot on
   the way, and step out with everything. Run out of time or die past level 5
   and your carried items feed a **Spirit** — revive it at the altar with
   **Vault Essence** looted from vault mobs and chests.
4. **Grow.** Vault XP levels you up: **skill points** buy Strength / Vitality /
   Swiftness / Fortune / Resilience (`/vh skills`), and **knowledge points**
   unlock whole features (`/vh knowledge`): backpacks, chest linking, three
   ability items, and catalysts.

## Anti-samey vaults

Every run multiplies four independent axes (all from one seed — the same seed
reproduces the same vault):

| Axis | Options |
|---|---|
| **Layout** | Linear gauntlet · 5×5 maze (with loops) · hub-and-arena — mazes and hubs sometimes grow a **second floor** reached by ladder shafts |
| **Theme** | Stone Keep · Overgrown Hollow · Frozen Depths (lv 5) · Desert Tomb (lv 8) · Nether Forge (lv 12) · End Rift (lv 16) — each with its own weighted block palette, mobs, and boss |
| **Objective** | Slay the guardian · gather artifacts · defend the beacon through 3 waves · treasure hunt · escape the collapse |
| **Modifiers** | 0–2 per run: Rush, Plentiful, Chaos, Gilded, Fragile, Frenzy — or force your own with crafted **Catalysts** |

## Performance design (the "lite" part)

- **One persistent void world.** Each run gets a region-aligned slot; closing
  a run just tags its slot, and the `.mca` files are deleted on the next
  startup. No world creation freezes, no block-clearing loops — ever.
- **Async generation.** The vault is computed off-thread into a buffer, then
  applied in paced batches (default 20k blocks/tick) behind the entry title.
- **Hard mob caps** per instance (default 24, +12 per extra player). Mobs come
  only from a 2-second spawner task near players; natural spawning is off.
- **No per-tick work.** Two scheduled tasks total: a 1-second run tick
  (clock/objectives/exit poll — no `PlayerMoveEvent`) and the spawner.
- Storage GUIs read chests live only while open; nothing is indexed.

## Commands

- `/vh` — hub menu · `stats` `skills` `knowledge` `storage` `spirit`
  `party <invite|accept|decline|leave|kick>` `leave` (abandon the run)
- `/vhadmin` — `give <item> [n]` · `setlevel <player> <n>` ·
  `addknowledge <player> <n>` · `testgen [seed] [level]` (same seed = same
  vault; the generator's regression tool) · `endvault [player]` ·
  `purgeslots` · `reload`

## Safety nets

- **Beginner's grace:** below vault level 5 (configurable), death or timeout
  in a vault costs nothing.
- **Crash recovery:** entry inventories are snapshotted per run. If the server
  dies mid-vault, players get their kit back on next join plus a replacement
  crystal. In-vault loot is the only loss.
- **Disconnects** mid-run are settled home without penalty.

## Building & running

```sh
./gradlew build          # jar in build/libs/
./gradlew runServer      # local Paper 26.2 test server (run/ directory)
```

Requires nothing at runtime beyond Paper 26.2. All state lives in the plugin
data folder as YAML (`playerdata/`, `instances.yml`, `storage.yml`,
`spirits.yml`) — hand-editable, no database.

## Config highlights (`config.yml`)

`grace-level` · `altar.block` + resource `tiers` and amount scaling ·
`vault.time-limit-seconds` + party/level scaling knobs · `generation.blocks-per-tick`
· `skills.*-per-point` rates · `knowledge.costs` · `storage.max-linked-chests`
· `spirit.essence-cost-*` · `xp.*` curve.
