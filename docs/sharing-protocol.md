# Logisticmatica sharing protocol v4

Logisticmatica uses a server-authoritative Fabric play protocol. The server owns project membership,
permissions, placement transforms, schematic versions, substitutions and tracked-container state.
Clients render and edit Litematica placements, but every shared mutation is validated by the server.

## Connection and transport

Both directions use one versioned envelope:

```text
protocol version | action/event id | request UUID | bounded binary body
```

The payload identifiers are `logisticmatica:sharing_c2s_v1` and
`logisticmatica:sharing_s2c_v1`. Fabric's large-payload registration is used with a 36 MiB envelope
limit; a compressed schematic itself is limited to 32 MiB. Strings and collection sizes are bounded
before allocation.
Server state is capped at 256 projects, 256 non-owner members per project, 65,536 tracked containers
per project and 262,144 globally. These high ceilings are abuse and memory-safety boundaries rather
than ordinary gameplay limits. Container state is no longer embedded in project-directory entries:
subscribed clients receive snapshots and changes in packets of at most 64 containers, while each
container snapshot remains limited to 256 distinct item types. Upload-time marks have no distance or
loaded-chunk restriction.


On join, the client sends `HELLO`. The server replies with the protocol version, feature mask,
maximum schematic size, mod version and a persistent server UUID, then sends the complete project
directory and online-player directory. The UUID namespaces the client's download cache so different
servers cannot reuse one another's project files. Projects without `VIEW` expose only directory
metadata; their hash, file size, substitutions, container counts and non-owner member roster are omitted.

## Messages

Client to server actions:

- `HELLO`, `LIST_PROJECTS`
- `CREATE_PROJECT`, `DOWNLOAD_PROJECT`, `UPDATE_SCHEMATIC`
- `UPDATE_TRANSFORM`, `UPDATE_SUBSTITUTIONS`
- `INVITE`, `RESPOND_INVITE`, `SET_PERMISSIONS`, `REMOVE_MEMBER`
- `DELETE_PROJECT`, `LEAVE_PROJECT`
- `TOGGLE_CONTAINER`, `REFRESH_CONTAINER`
- `LIST_PLAYERS`, `SET_PUBLIC_ACCESS`, `REQUEST_ACCESS`, `RESPOND_ACCESS`
- `SUBSCRIBE_PROJECT`, `UNSUBSCRIBE_PROJECT`

Server to client events:

- `HELLO`, `PROJECTS`, `PROJECT_CHANGED`, `PROJECT_REMOVED`, `PROJECT_DATA`
- `PLAYERS`
- translated `NOTICE` and `ERROR` responses with bounded formatting arguments
- `CONTAINER_SNAPSHOT`, `CONTAINERS_CHANGED`

Project updates carry a monotonically increasing revision. Mutations that could overwrite another
editor's placement or schematic state include the expected revision; stale writes are rejected and
the newest project list is returned. Containers use a separate monotonically increasing revision;
volatile inventory refreshes therefore do not invalidate placement edits and a missed delta is
recovered by requesting a fresh chunked snapshot.

## Permissions

Permissions are independent bit flags on each project:

| Capability | Allows |
|---|---|
| `VIEW` | See the accepted project and download its schematic |
| `MOVE` | Change dimension, origin, rotation or mirror |
| `UPDATE_SCHEMATIC` | Replace the shared `.litematic` blob |
| `SUBSTITUTE` | Change the shared block-substitution map |
| `MANAGE_CONTAINERS` | Bind/unbind server-readable containers |
| `INVITE` | Invite online players |
| `MANAGE_PERMISSIONS` | Edit or remove non-owner members |
| `DELETE` | Permanently delete the project |

Viewer, Builder, Editor and Manager are convenience presets; the UI also exposes every flag
individually. The owner has all capabilities. Server operators can administer projects through the
`logisticmatica.admin` permission node, backed by fabric-permissions-api with operator fallback.
Pending invitees and access requesters receive only project metadata, not the schematic,
substitutions, non-owner member list or container contents.

| Preset | Capabilities |
|---|---|
| Viewer | `VIEW` |
| Builder | Viewer + `MANAGE_CONTAINERS` |
| Editor | Builder + `MOVE`, `UPDATE_SCHEMATIC`, `SUBSTITUTE` |
| Manager | Editor + `INVITE`, `MANAGE_PERMISSIONS` |
| Owner | Every capability including `DELETE` |

The access-request screen presents these four member presets as an ordered lowest-to-highest scale,
shows the selected preset's cumulative capabilities, and names the requested role before submission.

Every project appears in the server directory and has one public-access policy:

| Public mode | Anonymous project permissions |
|---|---|
| Request only | Metadata only; a player may request a role |
| Public Viewer | Viewer |
| Public Supplier | Builder |
| Public Editor | Editor |

Directory labels retain the public-mode names Viewer, Supplier and Editor. Builder remains the
individual member preset, even though Public Supplier deliberately uses the same capability mask.

Public access never grants invitations, permission management or deletion. Accepted member rights
take precedence over the public preset. Invitations and access requests are distinct persisted
states so either side can cancel or respond without accidentally accepting the other flow.
Container capabilities govern Logisticmatica's shared marks and snapshots only; they never bypass
vanilla interaction rules or a server's claim/protection plugins.

## Schematic validation and storage

Uploads are parsed on the server as compressed NBT with a 256 MiB decompressed-data budget. Accepted
bytes are addressed by SHA-256 and written atomically to:

```text
<world>/data/logisticmatica/projects.json
<world>/data/logisticmatica/schematics/<sha256>.litematic
```

Downloaded bytes are checked against the advertised SHA-256 before the client writes or loads them.
Replacing a schematic writes a new content-addressed blob and changes only the project's hash and
revision; its UUID, transform, members, permissions, substitutions and containers remain intact.
The server package imports only vanilla Minecraft, Fabric and the bundled permissions API; it has no
Litematica or MaLiLib dependency.
`CREATE_PROJECT` also carries the uploader's existing schematic-bound container marks. The server
registers every eligible mark in the project's dimension, regardless of its distance from the
uploader. Loaded positions are canonicalized, validated and given a fresh vanilla inventory
snapshot immediately. Marks in unloaded chunks are registered with an empty server snapshot and
filled authoritatively as soon as their chunk is naturally loaded. Client-provided cached counts are
never trusted; duplicates and invalid loaded positions remain local and are reported as a partial
migration. Container state is streamed separately from directory metadata. The practical ceiling is
65,536 marks per project and 262,144 globally; bounded snapshot chunks prevent large projects from
creating one invalid response. Over-limit uploads are rejected before creation and never truncated.

## Placement and container synchronization

A downloaded placement uses the shared project UUID as its Litematica placement UUID. This provides
a stable mapping across reconnects. Remote transforms and substitutions are applied behind an echo
guard so Litematica events do not bounce the same mutation back to the server. Placements without
`MOVE` are locked locally, while the server still enforces the permission for every packet.
After a successful upload, the uploader's existing placement is rebound in place to the returned
project UUID and authoritative cache file, then activated. The unified Projects screen presents each
server project exactly once beside genuinely local placements and schematics; the source schematic
behind a rebound placement is not exposed as a duplicate row. The active server project is remembered
per server and dimension. Activating an unloaded project downloads and focuses it, while deactivating
only clears Logisticmatica's material and container overlays and leaves other Litematica rendering
untouched. Accepted container marks are promoted to project-owned bindings; rejected marks remain
local and are reported as a partial migration. Persisted local snapshots remain the no-server
fallback. An explicit Export Local Copy action writes an independent, server-named `.litematic` file;
shared cache files and shared placements cannot be uploaded as new projects implicitly.

For a shared schematic, the normal mark-container hotkey sends a server request instead of creating
a private client mark. The server canonicalizes double chests, requires the player to be in the same
dimension and within eight blocks. This proximity check applies only to a new manual mark, not to
upload-time promotion. Only the active project is subscribed for container data. On subscription the
server sends a bounded authoritative snapshot, then scans up to 64 registered positions per tick and
broadcasts changed contents as ordered deltas to subscribed authorized clients. Switching or clearing
focus unsubscribes and removes that project's projected bindings immediately; returning later starts
with a current snapshot.

The material list, world highlights, floating content labels and look-at peek follow the explicit
Logisticmatica focus. Clearing focus hides all of them without deleting server data; removing a
shared placement also removes its projected server bindings locally until the placement is loaded
again. The separate container overview screen retains its explicit All/Focused selector.
Container labels default to one narrow vertical list that grows upward and is clamped to the screen;
the maximum visible item rows and the former column layout remain configurable.


## Compatibility

Protocol changes that break message layout must increment `ShareProtocol.VERSION`. A client and
server with different protocol versions reject sharing while leaving all non-sharing client features
usable. The stable `_v1` payload identifiers are transport channel names; compatibility is decided by
the version field inside every envelope.
