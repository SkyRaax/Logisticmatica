# Logisticmatica

A Fabric mod for Minecraft **26.2** that turns Litematica's material handling into a
collaborative, server-aware build-logistics system.

Logisticmatica is an add-on for [Litematica](https://modrinth.com/mod/litematica). It keeps
Litematica's proven schematic engine and adds the parts that get painful on large,
multiplayer redstone/build projects: a fully configurable material HUD, container tracking,
persistent material substitution, and — backed by an optional server component — real
placement sharing with live sync and granular permissions.

> **Status: early development (0.1.x).** Nothing here is release-ready yet; see the roadmap.

## Features

- [x] **Better material list & configurable HUD** — the *full* list (not just the first 10
      entries), searchable, sortable and filterable, plus a HUD you can position, scale and style.
- [x] **Container tracking** — mark containers so their contents count towards the list,
      with wall-penetrating highlights and a content preview in the world.
- [x] **Material substitution** — persistently swap a material in a placement (e.g. a door
      wood type) for one you actually have, editable straight from the material list.
- [x] **Placement sharing & live sync** *(server component)* — share a placement, invite
      teammates, and have your moves/rotations reflected on their client in real time.
- [x] **Granular permissions** *(server component)* — decide exactly who may view, move,
      edit, substitute or re-invite on each of your placements.
- [x] **Persistence** — everything is saved per world/server; set it up once.

## Requirements

**Client**

- Minecraft 26.2, Fabric Loader ≥ 0.19.3
- [Fabric API](https://modrinth.com/mod/fabric-api)
- [Litematica](https://modrinth.com/mod/litematica) + [MaLiLib](https://modrinth.com/mod/malilib)
- Java 25

**Server** *(optional — unlocks sharing, permissions and live sync)*

- A Fabric server running Logisticmatica. The server side has **no** dependency on
  Litematica, so it runs on any vanilla-Fabric dedicated server.

## Sharing workflow

1. Install the same Logisticmatica jar on the Fabric server and every participating client.
2. Select a saved placement in Litematica, open **Logisticmatica > Sharing**, and choose
   **Share Selected Placement**.
3. Open the shared project to invite an online player and choose a role. Invitations must be
   accepted before the schematic can be downloaded.
4. Member capabilities can be edited individually: view/download, move, replace the schematic,
   edit substitutions, manage containers, invite, manage permissions, and delete.
5. Moving, rotating or mirroring a shared placement is broadcast live. Marked containers belonging
   to it are read by the server and their item counts are kept in sync for all permitted clients.

The server stores authority data inside the world at `data/logisticmatica/`; clients keep verified,
server-specific schematic downloads under `config/logisticmatica/shared/`. See
[`docs/sharing-protocol.md`](docs/sharing-protocol.md) for protocol and security details.

## Building from source

```bash
./gradlew build
```

Requires JDK 25. The built jar is written to `build/libs/`.

## Architecture

Logisticmatica ships as a single jar that behaves differently per side:

- **Client** — builds on Litematica/MaLiLib for the material list, HUD, container tracking
  and world rendering. All Litematica access is guarded, so a client without it degrades
  gracefully instead of crashing.
- **Server** — a self-contained, Litematica-free component that owns shared placements,
  enforces permissions and drives live sync over a custom networking protocol.

## Credits

- [Litematica](https://github.com/sakura-ryoko/litematica) and
  [MaLiLib](https://github.com/sakura-ryoko/malilib) by *masa* & *sakura-ryoko* — the
  schematic engine this add-on builds on (LGPL-3.0).
- [Syncmatica](https://github.com/sakura-ryoko/syncmatica) by *nnnik*, *samipourquoi* &
  *sakura-ryoko* — the placement-sync groundwork the server component is based on (CC0-1.0).
- [fabric-permissions-api](https://github.com/lucko/fabric-permissions-api) by *Lucko* — the
  permission backbone.

## License

[LGPL-3.0-only](LICENSE).
