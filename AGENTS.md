# Logisticmatica – Agent Handoff
> Stand: 2026-07-26 · Version: `0.1.0-dev` · Commit: `0111455` · Branch: `main` · erstellt für KI-Agents
>
> Sprache dieses Dokuments: Englisch (die restliche Projektdoku – README, Code-Kommentare, Commits – ist ebenfalls Englisch).

## 1. TL;DR
Logisticmatica is a **Fabric mod for Minecraft 26.2** and an add-on to **Litematica**. It replaces Litematica's weak material handling with a fully configurable material HUD, schematic-bound container tracking (mark chests → their contents count and are shown in-world), non-destructive material substitution, and a central hub UI. It targets large multiplayer redstone/build projects. **State: early development; client features are user-tested, and the full server-authoritative sharing stack is implemented, built and dedicated-server load-tested. Real two-client multiplayer behavior still needs maintainer verification.**

## 2. Zweck & Kontext
- **Problem it solves:** Litematica's material list shows only the first ~10 entries and only counts the player's own inventory; there is no way to bind storage containers to a build, swap materials you don't have for ones you do, or share/permission placements on a server.
- **Zielnutzer:** Technical Minecraft players building large schematics (esp. on a shared Fabric server). Maintainer: **SkyRaax** (GitHub), runs an own Fabric server.
- **Constraints:**
  - Public, open-source, to be released on **Modrinth** (slug `logisticmatica`). Everything must stay clean and well-documented — treat it as a public mod from day one.
  - **One jar, `environment: "*"`.** Client features may use Litematica/MaLiLib; the **server side must have ZERO Litematica/MaLiLib dependency** and must load on a vanilla-Fabric dedicated server.
  - **Commits:** author solely as the maintainer. **Do NOT add `Co-Authored-By:` / AI-attribution trailers.**
- **Non-Ziele (for now):** No Forge/NeoForge. No support for MC versions other than 26.2. No destructive editing of the user's `.litematic` files.

## 3. Aktueller Stand & Reifegrad
| Bereich | Status |
|---|---|
| Build / CI | ✅ `./gradlew build` green; GitHub Actions green on last pushes |
| Phase 0 (scaffold, CI) | ✅ done |
| Phase 1 (material HUD, config, hotkeys, Mod Menu, list GUI) | ✅ done, user-tested |
| Phase 2 (container tracking, highlight, peek, content-into-list) | ✅ done, user-tested |
| Focus picker (choose which schematic the list follows) | ✅ done |
| Material substitution (non-destructive block→block overlay) | ✅ done, user-tested |
| Central hub menu + Litematica-menu button | ✅ done, user-tested |
| Container↔schematic binding, colour-coding, per-schematic persistence (with contents) | ✅ done, user-tested (contents survive rejoin ✓; colour-coded per schematic ✓) |
| In-world label = projected 2D icon+text panel, name-tag-style scaling | ✅ built; scaling reworked to be perspective/FOV/zoom-correct (awaiting final confirm) |
| **Phase 3/4 — server component (sharing, permissions, live sync)** | ✅ protocol v2 implemented with public directory/access requests; automated tests + dedicated-server load smoke pass, real two-client manual test pending |
| Deploy / release | ❌ no Modrinth release yet; version is `0.1.0-dev` |
| Automated tests | ✅ 11 JUnit tests for wire bounds/round-trip, roles/public ACL, request privacy/persistence and revision behavior |

## 4. Tech-Stack & Abhängigkeiten
- **Language/Runtime:** Java **25** (toolchain + `--release 25`), UTF-8.
- **Build:** Gradle **9.5.1** (wrapper), **Fabric Loom `1.17.+`**. **Mojang official mappings** (accesswidener header `accessWidener v2 official`; there is **no `mappings` line** and no Yarn).
- **Platform:** Fabric Loader **0.19.3**, Fabric API **0.152.1+26.2** (only server-safe modules bundled via Jar-in-Jar: `fabric-api-base`, `fabric-networking-api-v1`, `fabric-resource-loader-v1`, `fabric-lifecycle-events-v1`, `fabric-command-api-v2`).
- **Add-on deps (client, `implementation`, from `https://masa.dy.fi/maven/sakura-ryoko`):** `malilib-fabric-26.2:0.29.3`, `litematica-fabric-26.2:0.28.3`. **`modImplementation` does NOT exist in this Loom setup — use plain `implementation`.**
- **Bundled libs:** `me.lucko:fabric-permissions-api:0.7.0` (active `logisticmatica.admin` ACL integration), `jsr305:3.0.2` (`@Nullable`).
- **Optional:** `com.terraformersmc:modmenu:20.0.0-beta.2` (`compileOnly`; we ship our own `ModMenuApi` because MaLiLib does not auto-list add-ons).
- All version numbers live in **`gradle.properties`**.

## 5. Architektur & Hauptkomponenten
Single jar, two sides. **Client** builds on Litematica/MaLiLib; **server** is self-contained and server-authoritative. All client entry points guard on `FabricLoader.getInstance().isModLoaded("litematica")` before touching Litematica/MaLiLib classes.

Client subsystems (all registered from `client/LogisticmaticaInitHandler`):
- **Config** (`client/config/Configs`) — MaLiLib `IConfigHandler`, nested `Hud` / `Colors` / `Hotkeys`; persists to `config/logisticmatica.json`. Shown via our own `LogisticmaticaModMenu` + `client/gui/GuiConfigs`.
- **Material HUD** (`client/hud/MaterialHudRenderer`) — MaLiLib `IRenderer.onExtractGuiOverlayPost`; reads `DataManager.getMaterialList()`, full list (no 10-line cap), have/need colouring, auto-fits to screen height and **paginates** (Cycle HUD Page hotkey).
- **Focus** (`client/FocusState` + `client/gui/GuiFocusPicker`) — remembers which `LitematicaSchematic` the HUD/list/substitutions follow (Litematica exposes no such accessor). Picker lists both placements and loaded schematics.
- **Container tracking** — mark a looked-at container (hotkey) → bound to the focused schematic; contents cached and folded into the material list. See §10 for the data model.
  - `client/ContainerTracker` (singleton state + persistence), `client/ContainerBlocks` (double-chest canonicalisation), `client/ContainerScan` + `mixin/MixinAbstractContainerScreen` (server: read contents on screen close), `client/ContainerContentTickHandler` (single-player: re-read from world each second), `client/MarkContainerCallback`.
  - Rendering: `client/ContainerHighlightRenderer` (batched, colour-per-schematic box outline + translucent fill), `client/ContainerLabelRenderer` (2D icon+text panel projected onto each container), `client/ContainerPeekRenderer` (look-at inventory-grid overlay via MaLiLib `InventoryOverlay`).
  - `client/SchematicKey` (stable key + loaded-schematic lookup), `client/SchematicColors` (deterministic colour per schematic).
- **Substitution** (`client/SubstitutionManager` + `client/Substitutions` + `client/ISubstitutableContainer` + `mixin/litematica/MixinLitematicaBlockStateContainer`) — a non-destructive `Block→Block` overlay on the schematic container's `get()`, edited via `client/gui/GuiSubstitutions` + `GuiBlockPicker`. Persists to `config/logisticmatica/substitutions.json`, re-applied on load (`MixinSchematicHolder`).
- **Hub UI** (`client/gui/GuiHub` + `client/gui/NavBar`) — one menu reaching Materials/Focus/Containers/Substitutions/Sharing/Settings; opened by a hotkey and by a button injected into Litematica's main menu (`mixin/litematica/MixinGuiMainMenu`).
- **Hotkey activity guard** (`client/HotkeyActivity`) — lets the single-key menu hotkey defer to a chord that shares its key (see §11).


Sharing subsystems:
- **Wire layer** (`share/*`) — protocol v2, versioned large-payload envelopes, bounded binary codec and privacy-filtered per-player views; no client-only imports.
- **Server authority** (`server/*`) — world-local atomic persistence, SHA-256-addressed and vanilla-NBT-validated schematics, project revisions, invites/access requests, public ACL modes, rotating container scans and broadcasts.
- **Client bridge** (`client/share/ClientShareManager`) — server-presence handshake/warning, verified cache per server UUID, Litematica placement mapping, echo-guarded live transforms/substitutions and shared-container projection.
- **Sharing UI** (`client/gui/GuiSharing`, `GuiSharedProjectDetails`, `GuiMemberPermissions`, `GuiPlacementPicker`, `GuiPlayerPicker`, `GuiSharingHelp`) — searchable server directory and placement/player pickers, access requests/public modes, explained roles/capabilities, safe schematic replacement and member administration.
## 6. Verzeichnis- & Datei-Landkarte
| Pfad | Zweck |
|---|---|
| `build.gradle`, `gradle.properties`, `settings.gradle` | Build config + all version numbers |
| `.github/workflows/build.yml` | CI: JDK 25, `./gradlew build`, uploads `build/libs/` |
| `src/main/resources/fabric.mod.json` | Mod metadata, entrypoints, mixin configs, deps (litematica/malilib in `suggests`+`breaks`, never `depends`) |
| `src/main/resources/logisticmatica.mixin.json` | Vanilla mixins, `required:true` → `MixinAbstractContainerScreen` |
| `src/main/resources/logisticmatica.litematica.mixins.json` | Litematica-targeting mixins, **`required:false`** (5 mixins) |
| `src/main/resources/logisticmatica.accesswidener` | Empty (`accessWidener v2 official` only) |
| `src/main/resources/assets/logisticmatica/lang/{en_us,de_de}.json` | All UI strings (en + de) |
| `.../Logisticmatica.java` | Common `main` entrypoint — registers sharing payloads and the authoritative server service; never touches Litematica |
| `client/LogisticmaticaClient.java` | `client` entrypoint; guards on Litematica, registers `LogisticmaticaInitHandler` |
| `client/LogisticmaticaInitHandler.java` | Registers everything (config, renderers, hotkeys, tick/world handlers) |
| `client/config/Configs.java` | All config options + hotkeys |
| `client/ContainerTracker.java` | **Core state**: schematic→positions, contents cache, per-world persistence |
| `client/ContainerLabelRenderer.java` | The projected 2D in-world label panel (name-tag-style) |
| `client/ContainerHighlightRenderer.java` | Batched, colour-per-schematic box outline + fill |
| `client/SubstitutionManager.java` | Substitution overlay engine + persistence |
| `mixin/litematica/*` | The five Litematica hooks (all `remap=false`) |
| `client/gui/*` | All screens/widgets (hub, lists, pickers, substitutions) |
| `src/main/java/.../client/` | 59 client classes (renderers, callbacks, state, helpers and sharing UI/bridge) |
| `share/*` | Side-neutral protocol constants, payload codecs, permission flags and immutable wire views |
| `server/*` | Dedicated-server-safe authority, persistence, schematic validation and vanilla container reads |
| `client/share/ClientShareManager.java` | Maps authoritative projects to Litematica placements and applies live state |
| `docs/sharing-protocol.md` | Protocol, storage, ACL, limits and security documentation |
| `src/test/java/.../{share,server}/` | Eleven pure JUnit protocol/model tests across three test classes |
## 7. Einrichten · Bauen · Starten · Testen · Deployen
Prerequisite: **JDK 25** on `PATH`. On Windows use `gradlew.bat` (Git Bash: `./gradlew`).
```bash
# Build (produces build/libs/logisticmatica-fabric-26.2-0.1.0-dev.jar + a -sources.jar)
./gradlew build

# Just compile (faster feedback)
./gradlew compileJava

# Clean rebuild
./gradlew clean build
```
- Loom exposes `runClient` and `runServer`; no custom launch file is checked in. `runServer --args=nogui` successfully loads Logisticmatica on the dedicated-server path and then stops at the unaccepted local Minecraft EULA.
- **Deploy:** none automated. Future: publish `build/libs/*.jar` to Modrinth.
- **Client runtime data:** `config/logisticmatica.json`, `config/logisticmatica/substitutions.json`, `config/logisticmatica/containers_<world>.json`, and `config/logisticmatica/shared/<server-uuid>/`.
- **Server runtime data:** `<world>/data/logisticmatica/projects.json` plus content-addressed blobs in `<world>/data/logisticmatica/schematics/`.

## 8. Konfiguration & Secrets
- **No secrets.** No API keys, tokens, or `.env` files anywhere.
- **Build tokens:** pushing CI (`.github/workflows/`) once needed a GitHub token with the `workflow` scope (historical note; already resolved).
- **User-facing config** is MaLiLib-managed JSON in the MC instance `config/` dir (see §7). Not in the repo.
- `.gitignore` excludes `build/`, `.gradle/`, `run/`, IDE dirs, `logs/`, `dependencies/`, and a local `buildAndTest.sh`.

## 9. Externe Systeme & Integrationen
- **Litematica + MaLiLib** (client, LGPL-3.0) — the schematic engine we extend. Consumed as compiled artifacts from `masa.dy.fi/maven/sakura-ryoko`; source clones (`litematica-ref`, `malilib-ref`, `syncmatica-ref`) were used during development for API discovery.
- **Mod Menu** (optional) — config screen entry.
- **fabric-permissions-api** (bundled) — active backend for the `logisticmatica.admin` node, with operator fallback.
- **Sharing protocol v2:** custom bounded client↔server payloads over `fabric-networking-api-v1`, informed by **Syncmatica** (CC0); includes a public project directory, public ACL presets, access requests and online-player discovery; see `docs/sharing-protocol.md`.

## 10. Domänen-Glossar & Kernkonzepte
- **Schematic / Placement** — Litematica concepts. A *schematic* is loaded block data; a *placement* positions it in the world. A schematic can have multiple placements.
- **Material list** — Litematica's `MaterialListBase` (per-schematic totals, or per-placement compared against the world). We read/extend it.
- **Focused schematic** — the one Logisticmatica currently follows (`FocusState`); drives the HUD, list, substitutions, and which schematic a marked container binds to.
- **Marked / tracked container** — a chest/barrel/etc. the player bound to a schematic; its contents count towards that schematic's material list and are shown in-world.
- **Canonical block** — one representative `BlockPos` per container; a double chest is stored as its lower-coordinate half (`ContainerBlocks.canonical`).
- **Schematic key** — `SchematicKey.of(schematic)` = the `.litematic` file path, or `"name:<name>"` for unsaved ones. Keys bindings/substitutions/colours.
- **Substitution** — a non-destructive `Block→Block` overlay (e.g. spruce→oak) applied at container-read time, preserving block-state properties; the `.litematic` file is never modified.
- **Peek / Label** — *Peek* = the look-at inventory-grid overlay; *Label* = the floating per-container icon+text panel.

## 11. Konventionen, Muster & Fallstricke
**Style**
- Java, tab indentation, `@Nullable` (jsr305). Match surrounding comment density. Reference code as `path:line`.
- Commit messages: imperative, lower-second-line body; **English**; author = maintainer, **no AI co-author trailer**.
- Two GUI patterns: MaLiLib `GuiListBase` (searchable scrolling lists) and `GuiBase` (button screens). New top-level screens add the `NavBar` tab row and pass the list top in the `super(10, 66)` constructor arg.

**Gotchas — read before editing renderers/mixins:**
- ⚠️ **Litematica mixins need `remap = false`** on both `@Mixin`/`@Inject` — otherwise the injector silently finds no target (Litematica method names are not in the intermediary mapping). Vanilla mixins use the default `remap = true`.
- ⚠️ **A helper class referenced by a mixin must NOT live in a mixin-owned package** (`IllegalClassLoadError`). Duck interfaces / listeners live under `client/` (e.g. `HudToggleListener`, `ISubstitutableContainer`, `LitematicaMenuButtonListener`).
- ⚠️ **MC 26.2 API renames:** `net.minecraft.resources.ResourceLocation` → **`net.minecraft.resources.Identifier`** (`Identifier.tryParse` / `fromNamespaceAndPath`; `BuiltInRegistries.ITEM.getValue(Identifier)`). `LevelRenderer.getLightColor` → **`net.minecraft.util.LightCoordsUtil.getLightCoords`**.
- ⚠️ **MC 26.2 removed immediate-mode item rendering:** no `MultiBufferSource`, no `ItemStackRenderState.render`. Items go through the deferred **submit pipeline** (`ItemStackRenderState.submit(pose, SubmitNodeCollector, light, overlay, outline)`), and a `SubmitNodeCollector` is only in scope inside `LevelRenderer.submitFeatures`. (We tried 3D icons this way; abandoned as too small — see §12.)
- ⚠️ **`GuiListBase` positions its list from the constructor `listY`, NOT a `getListY()` override** — pass `super(10, 66)` to leave room for the NavBar; overriding `getListY()` is dead code.
- ⚠️ **Container-content timing on servers:** the server sends a container's contents *after* the screen opens, so the snapshot is taken in `AbstractContainerScreen.onClose`, not `init`. Single-player reads the world each tick instead.
- ⚠️ **Camera matrices for the projected label:** `CameraRenderState` has public `projectionMatrix` + `viewRotationMatrix` + `pos`. There is **no** `RenderSystem.getProjectionMatrix()` in 26.2.
- ⚠️ **PowerShell 5.1 mangles embedded double quotes** passed to native exes (`git commit -m`, `gh --jq`) — keep `"` out of commit messages / jq expressions, or the commit splits into bogus pathspecs.
- ⚠️ **Finding real 26.2 signatures:** decompiled Mojang-mapped sources live in the Gradle cache: `~/.gradle/caches/neoformruntime/intermediate_results/mergeWithSources_*_output.jar` (read a class via `unzip -p <jar> net/minecraft/.../Foo.java`).

## 12. Wichtige Entscheidungen & Begründungen
- **Substitution is non-destructive** (`@ModifyReturnValue` overlay on the container `get()` + a rebuild), **not** a `SchematicUtils` file edit — the user's `.litematic` must never be modified; it stays revertible and per-session re-appliable. Chosen deliberately over the "actually rewrite the schematic" route.
- **In-world label is a 2D screen-projected panel, not 3D-billboarded icons.** The 26.2 submit-pipeline 3D icons were too small/hard to read; a 2D `GuiContext` panel gives crisp icons + text + column wrapping. Its scale is derived from the projected screen span of one world block, so it behaves like a name tag (perspective/FOV/zoom correct).
- **Marked containers are schematic-bound and shown for *all loaded* schematics, colour-coded** (user choice), so you can recognise which container belongs to which build with the big list hidden. Persistence is per-world grouped by schematic and **includes contents** (item id + count) so they survive a rejoin (servers can't re-read a closed container).
- **Highlight is one batched draw call** (per-vertex colours), not one context per box per frame — avoids thousands of allocations/sec.
- **Menu hotkey fix uses an activity timestamp**, not just `KeybindSettings.RELEASE_EXCLUSIVE` — the exclusive flag alone leaves a stale single-key release that fires a cycle later; the timestamp reliably suppresses the menu right after a chord.
- **Server-mod authority model is implemented.** Logisticmatica runs on the server, owns shared schematics/permissions/live state and validates every mutation. Client-only P2P remains rejected as too fragile.

## 13. Kürzlich erledigt
| Datum | Commit | Änderung |
|---|---|---|
| 2026-07-26 | `0111455` | Add protocol v2 project directory/public ACL/access requests, placement and player pickers, safe schematic replacement, in-game permission help and persisted-container key migration |
| 2026-07-26 | `00893d6` | Implement protocol v1, schematic transfer, live placement/substitution sync, granular ACL/invites, server containers, sharing UI, tests and docs |
| 2026-07-14 | `e633a91` | Name-tag-style panel scaling; menu hotkey suppressed after a chord |
| 2026-07-14 | `554e73f` | Bind tracked containers to a schematic; colour-code + persist (with contents) |
| 2026-07-14 | `6d605d3` | Fix list/control overlap (constructor `listY`); label scales with distance |
| 2026-07-14 | `449d437` | Replace in-world icon labels with a projected 2D icon+text panel |
| 2026-07-13 | `ab13c76` | (superseded) 3D submit-pipeline floating item icons |
| 2026-07-13 | `3bafbb7` | Translucent fill on the container highlight |
| 2026-07-13 | `9edf986` | Navigation tab row on top-level screens |
| 2026-07-13 | `5cb97dc` | Hotkey prefix (RELEASE), HUD page indicator, see-through + peek config |
| 2026-07-12 | `f05df1b` | Central hub menu + Litematica-menu button |
| 2026-07-12 | `3e2a864` | Chest-tracker-style peek panel |
| earlier | `b9210ba`, `b947b40`, `e974ac6`, `565d4ab`, Phase 0–2 | Substitution, chest tracker, focus picker, perf, HUD, scaffold |

## 14. Bekannte Probleme, Grenzen & Tech-Debt
- **Panel jitter (open):** the label may still lag ~1 frame during fast movement — camera matrices are captured in `ContainerLabelRenderer.onRenderWorldLast` (render pass) and used in `onExtractGuiOverlayPost` (extract pass). Fix: read `LevelRenderer.levelRenderState.cameraRenderState` via an `@Accessor` mixin in the GUI pass for current-frame matrices.
- **Litematica-menu button has no icon** — `ButtonGeneric` needs an `IGuiIcon`; our `icon.png` is 128×128 and MaLiLib's `drawTexturedRect` assumes a 256 texture, so it needs a pose-scaled draw + an `Identifier`. Deferred.
- **`Configs.Colors.CONTAINER_HIGHLIGHT` is now dead** — box colour is per-schematic; the option still exists but is unused. Consider removing/repurposing.
- **`hudMaxLines`, `RENDER_IN_GUIS`, etc.** are wired; a few config toggles are lightly tested.
- **Automated coverage is intentionally narrow:** eleven tests cover pure protocol/model code; Litematica events, screens, networking between two processes and rendering still require manual game testing.
- **Old global container marks are dropped on load** by the new schematic-grouped format (intended, no crash).
- **Sharing needs a real two-client end-to-end pass.** Build, tests and protocol-v2 dedicated-server class loading pass, but the project directory, invites/access requests, public ACL modes, replacement/download, live movement and container sync have not yet been exercised together on the maintainer's real server.
- **Invitations currently require the target player to be online.** Offline profile lookup is not implemented.
- **Unreferenced schematic blobs are not garbage-collected.** Project deletion removes metadata but retains content-addressed `.litematic` files for safe recovery/deduplication.

## 15. Offene Aufgaben & nächste Schritte  ← WICHTIGSTER TEIL
Priorisiert. Each is concrete enough to start immediately.

1. **(polish) Fix the label 1-frame jitter.** File: `client/ContainerLabelRenderer`. Add `mixin/AccessorLevelRenderer` (`@Accessor("levelRenderState") LevelRenderState`) to `logisticmatica.mixin.json`, and in `onExtractGuiOverlayPost` read `((AccessorLevelRenderer)(Object) mc.levelRenderer).…getLevelRenderState().cameraRenderState` for current-frame `viewRotationMatrix`/`projectionMatrix`/`pos`; drop the `onRenderWorldLast` capture. **Accept:** panel stays glued to the container during fast flight, no jitter. ❓ confirm with the maintainer whether jitter still occurs after `e633a91` before building this.
2. **(polish) Show the schematic per row in the container overview.** File: `client/gui/WidgetContainerEntry` (the `Snapshot` already carries `schematicKey`); add the schematic name + `SchematicColors.argb(key)` swatch to each row. **Accept:** each overview row shows which schematic it belongs to.
3. **(polish) Litematica-menu button icon** (see §14). File: `mixin/litematica/MixinGuiMainMenu` + a new `IGuiIcon` impl using `Identifier.fromNamespaceAndPath("logisticmatica","icon.png")` and a pose-scaled `RenderUtils.drawTexturedRect`. **Accept:** the button shows the mod icon like Litematica's own buttons.
4. **(highest priority) Real multiplayer acceptance test.** Install commit `0111455` on the maintainer's Fabric 26.2 server and two clients. Verify installed/missing-server notices; create via placement picker; directory visibility/privacy; invite/search/accept/decline; access request/approve/decline; all four public modes; download + SHA cache; replace schematic while preserving transform/ACL/containers; live move/rotate/mirror in both directions; viewer lock/denials; granular permission changes/removal/leave/delete; shared substitutions; container restore/bind/unbind and changing contents. Inspect both `latest.log` files and `<world>/data/logisticmatica/`. **Accept:** every flow works across reconnect and server restart without client-only classes on the server.
5. **(follow-up) Offline invitations.** Resolve cached profiles safely instead of requiring the invitee online; retain UUID identity and avoid blocking network lookups on the server thread.
6. **(follow-up) Safe blob garbage collection.** Add an explicit/admin maintenance path that removes only SHA blobs unreferenced by every project, preferably with a recovery grace period.
7. **(nice-to-have) Chest-tracker "where is item X" search** already consumes shared server snapshots; verify it in the two-client pass and improve dimension/project filtering if needed.
8. **(cleanup)** Remove/repurpose the dead `CONTAINER_HIGHLIGHT` colour config.

## 16. So verifizierst du deine Arbeit
- **Compile/build (authoritative gate):**
  ```bash
  ./gradlew build          # expect: BUILD SUCCESSFUL, jar in build/libs/
  ```
  On Windows the reliable exit check is: `./gradlew.bat build --console=plain -q; echo $LASTEXITCODE` → `0`. Do **not** pipe `2>&1 | Select-String` on gradle (it corrupts the exit code).
- **CI:** every push to `main` runs `.github/workflows/build.yml` (JDK 25). Keep it green.
- **Automated tests:** `./gradlew test` runs eleven JUnit tests for the bounded wire codec, permission/public-access presets, directory/request privacy, request persistence and revision behavior.
- **Dedicated-server smoke:** `./gradlew runServer --args=nogui` must list Logisticmatica without Litematica/MaLiLib and log `sharing protocol v2 registered`; a fresh local run then stops at the unaccepted Minecraft EULA.
- **Manual verification:** build the jar, install it on a MC **26.2** Fabric server plus two clients with Litematica + MaLiLib, and exercise the checklist in §15. For mixins, failures are usually visible in `latest.log` (Litematica mixins are `required:false`, so they warn instead of crash).
- After a nontrivial change: `git status` clean except your files; commit as the maintainer (no AI trailer).

## 17. Referenzen
- Repo: `https://github.com/SkyRaax/Logisticmatica` · License: [`LGPL-3.0-only`](LICENSE) · README: [`README.md`](README.md)
- Litematica: https://github.com/sakura-ryoko/litematica · MaLiLib: https://github.com/sakura-ryoko/malilib · Syncmatica (CC0, sync reference): https://github.com/sakura-ryoko/syncmatica
- fabric-permissions-api: https://github.com/lucko/fabric-permissions-api
- Modrinth (target): https://modrinth.com/mod/litematica (base), slug `logisticmatica` (this mod)
- ❓ offen/zu klären: issue tracker is empty; the sharing protocol is documented, but the real two-client deployment matrix is not yet recorded.

## 18. Pflege dieses Dokuments
Every agent updates `AGENTS.md` at the **end** of its work: bump the *Stand*-date + *Version/Commit* at the top, add to **§13 Kürzlich erledigt**, and revise **§15 Offene Aufgaben** and **§14 Bekannte Probleme**. Keep it faktentreu; mark unknowns as `❓ offen/zu klären` instead of guessing.
