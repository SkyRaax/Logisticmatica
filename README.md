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
      entries), searchable, sortable and filterable. Sort, amount, filters, icons, header and
      background are one live view shared by the menu and HUD; the HUD can also be positioned
      and scaled.
- [x] **Container tracking** — mark containers so their contents count towards the list,
      with a global visual switch, persistent per-container visibility, wall-penetrating highlights
      and a perspective-correct content preview in the world. A Find action flashes any selected
      container's outline for 15 seconds.
- [x] **Material substitution** — persistently swap a material in a placement (e.g. a door
      wood type) for one you actually have, editable straight from the material list.
- [x] **Placement sharing & live sync** *(server component)* — choose any loaded placement,
      invite teammates, replace its schematic without losing the placement, and synchronize moves.
- [x] **Granular permissions** *(server component)* — decide exactly who may view, move,
      edit, substitute, manage containers or re-invite on each placement; projects can also be
      request-only, public-viewable, public-supplier or public-editor.
- [x] **Shared project status** *(server component)* - Planning, Collecting, Ready to Build,
      Building, Paused, Blocked or Completed is visible in Projects, project details and the HUD.
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
2. Open **Logisticmatica > Projects**. This is the canonical searchable workspace for server
   projects, local placements and unplaced local schematics. A shared placement is shown once as its
   server project, never again as a duplicate local row.
3. Select **Choose Placement to Share** for a genuinely local placement, or open a server project to
   activate it. Activating downloads it when necessary, focuses its material list and container data,
   and remembers it per server and dimension. Restricted projects reveal metadata only; players can
   request a Viewer, Supplier, Editor or Manager role.
4. Owners and managers can invite players from a searchable online-player list, approve access
   requests, select role presets and fine-tune individual capabilities.
5. **Download / Reload** refreshes the linked server placement. **Export Local Copy** writes an
   independent file whose name includes the source server. **Replace from Placement** uploads a local
   placement's file while preserving the shared origin, rotation, permissions, substitutions and
   containers. A linked shared placement cannot be shared again by accident.
6. Public access can allow everyone to view, supply shared containers or edit while never granting
   permission administration or deletion.
7. Moving, rotating or mirroring a shared placement is broadcast live. The active project's marked
   containers are loaded from an authoritative chunked server snapshot and then kept current through
   live deltas. Project details expose local notification controls for placement, schematic,
   substitution, project-status, container and access changes. Live container-content notifications
   are hidden by default; when enabled they list exact added and removed items. **Unfocus Project** hides Logisticmatica's list,
   highlights, labels and peek without deleting the project or hiding unrelated Litematica
   placements.
8. **End Sharing** first refreshes the owner's authoritative container snapshot, removes the server
   project for every participant, and detaches the owner's existing placement back to a local
   schematic. Its placement state and container marks remain locally usable.

If the client joins a server without Logisticmatica, it displays a warning and disables only server
actions in Projects; all local material, container and substitution features continue to work.

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
