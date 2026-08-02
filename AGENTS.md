# AGENTS.md

## Cursor Cloud specific instructions

Flashback is a **client-side Minecraft 1.21.1 Fabric mod** (Java 21, Gradle via `./gradlew`). It records
gameplay and exports cinematic replays; there is no server, database, or web service — the only "service"
is the in-game dev client. Java 21 is preinstalled and dependencies are pre-warmed by the update script.

### Build / lint / test
- Build + lint: `./gradlew build`. This compiles, runs `check` (which includes `validateAccessWidener`),
  and produces the shaded, remapped mod jar at `build/libs/flashback-<version>.jar`.
- There are **no unit tests** — the `test` task reports `NO-SOURCE`. Don't expect a test suite; validate
  changes by running the dev client (below).
- The many `Cannot remap <method> ... does not exist in target` lines during build are **normal**. They
  come from the optional cross-version compat mixins and are warnings, not failures.

### Running the dev client (the app)
- Run with: `LIBGL_ALWAYS_SOFTWARE=1 ./gradlew runClient`.
- The VM has no GPU, so software OpenGL (`LIBGL_ALWAYS_SOFTWARE=1`, via Mesa `llvmpipe`) is required.
  Rendering is slow — give menus/world-gen extra time. An X display is already running at `DISPLAY=:1`.
- `runClient` opens a real Minecraft window and does not exit; run it in a background/tmux session and
  drive it via computer-use for manual testing.
- Audio errors (`Failed to open OpenAL device` / `ALSA ... cannot find card '0'`) are **expected and
  harmless** — the VM has no audio device and Minecraft simply disables sound.
- Login is handled automatically by DevAuth in dev; no Microsoft account is needed to launch and test.

### Exercising core functionality (recording a replay)
- Dev game directory is `run/`. Recordings export to `run/flashback/replays/<name>.zip`.
- Easiest flow: enter a singleplayer world, then use the chat commands `/flashback start` and
  `/flashback finish` (finishing opens a "Save Replay" screen). Equivalent buttons also appear on the
  pause (Esc) screen. A valid replay zip contains `metadata.json`, `level_chunk_caches/`, `icon.png`,
  and a `*.flashback` packet stream.

### Dependencies gotcha
- `build.gradle` pins the optional compile-only `maven.modrinth:starlight` to `1.1.3+1.20.4` because
  Modrinth removed the jar for the previously-used `1.1.3+1.20.2` (its `.pom` still resolves but the
  `.jar` 404s, breaking dependency resolution). It's the same Starlight version, so the compat classes
  are identical. Keep it resolvable — reverting to `1.1.3+1.20.2` will break `./gradlew build`.
