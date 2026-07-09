# Vault Hunters Lite — Resource Pack

Custom icons for the plugin's items: Vault Crystal, Essence, Knowledge
Star, Catalyst, Backpack, Link Wand, the three ability items, the Vault
Altar, and the Vault Crate. Entirely optional — without the pack, players
just see the renamed vanilla items. With it, they see proper custom icons.

## How it works

The plugin stamps a pinned `custom_model_data` id on every custom item
(1001+, see `VhItemType`). This pack's `assets/minecraft/items/*.json`
definitions dispatch on those ids to the custom models under
`assets/vhlite/`, and fall back to the vanilla look for untagged items.
No plugin changes are ever needed to iterate on art.

Built for **pack_format 88** (the `resource_major` reported by Paper 26.2's
`version.json`). If a future MC version complains the pack is outdated,
bump `pack_format` in `pack.mcmeta` — the item-definition format has been
stable since 1.21.4, but double-check `assets/minecraft/items` syntax
against the wiki if icons stop applying after a big version jump.

## Building the zip

```sh
./build.sh        # writes build/VaultHuntersLite-ResourcePack.zip + prints SHA-1
```

## Serving it to players (no client mods, vanilla prompt)

1. Host the zip anywhere players can reach over HTTP(S). Easy options:
   - attach it to a GitHub release of this repo and use the download URL;
   - drop it on any static file host.
2. In `server.properties` on the Minecraft server:
   ```properties
   resource-pack=https://your-host/VaultHuntersLite-ResourcePack.zip
   resource-pack-sha1=<the SHA-1 build.sh printed>
   resource-pack-prompt=Custom Vault Hunters icons\!
   require-resource-pack=false
   ```
3. Restart. Players get a one-click accept prompt on join; the sha1 lets
   their client cache it. Re-upload + update the sha1 whenever art changes.

With discopanel, set those keys in the server's config page and it handles
the rest.

## Editing the art

Textures are generated from ASCII art in `tools/generate_textures.py` —
each texture is a 16x16 character grid with a shared palette. Edit the art,
run the script, rebuild the zip:

```sh
python3 tools/generate_textures.py && ./build.sh
```

Not covered (on purpose): the Vault Map (vanilla map rendering) and
Vaultforged/Unidentified gear (they use many materials; retexturing them
means per-material dispatch files — doable later with the same pattern).
