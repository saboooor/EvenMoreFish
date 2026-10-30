# Addon persistence fork

This fork keeps user-installed native EMF addons in `plugins/EvenMoreFish/addons/` across startup and shutdown. Upstream 2.5.3-SNAPSHOT deletes that directory when creating or unloading its addon manager.

Shutdown now calls each loaded addon's `unload()` hook, continues cleanup if an addon throws, and clears the loaded list so repeated cleanup is harmless. Existing addon files and data are retained. Bundled addons such as ExcellentEconomy are still extracted and updated by EMF; their bundled filenames remain reserved.

## Command registration after PlugMan reload

Main command registration now runs on every `onEnable()`, after configuration loads. Paper removes lifecycle handlers on disable, so registering only in `onLoad()` left commands missing after a disable/enable cycle. For plugins loaded after startup, a first-tick fallback registers commands through Paper's existing registrar if the normal `COMMANDS` lifecycle event has not fired.

The fork tracks the actual main command, aliases, namespaced labels, and optional admin shortcut nodes. Disable removes only nodes still owned by this plugin; commands subsequently replaced by another plugin are preserved. Online players receive updated command trees after registration.

The late-load fallback uses a small reflective bridge to Paper's internal `PaperCommands` registrar because Paper provides no public late-registration event. This bridge was checked against cached server artifacts for the supported version adapters. A future change to those internals may need an update; errors are logged explicitly. This fixes command registration and is not a guarantee that arbitrary third-party plugins or every EMF subsystem support hot reloads. Live PlugMan reload still needs server verification.

Regression tests cover normal startup without duplicate registration, disable/enable with all command labels, dispatcher rebuilds, another plugin replacing a label, and registrar validity restoration on errors.

## Install the Discord addon

1. Stop the server and back up the existing EMF plugin JAR and data folder.
2. Replace the original EMF plugin JAR with this fork's built plugin JAR. Keep only one EMF plugin installed.
3. Copy the native `evenmorefish-discord-2.0.0.jar` into `plugins/EvenMoreFish/addons/`. Use a distinct filename; do not replace ExcellentEconomy.
4. Remove the old standalone Discord plugin from `plugins/` if present.
5. Start once to generate `plugins/EvenMoreFish/discord/config.yml`, set the Discord destination, then restart and run `/emfdiscord test`.

Embedding the Discord addon into the EMF JAR is unnecessary with this fork. The Discord addon is a separate project and is not bundled in this repository. `addons.additional-addons` continues to control bundled addon extraction; manually installed addons are discovered independently.

## Build and test

```sh
./gradlew :even-more-fish-api:test :even-more-fish-plugin:test :even-more-fish-plugin:shadowJar
```

The server plugin JAR is written to `even-more-fish-plugin/build/libs/`. CI also uploads built plugin JARs as a workflow artifact.

`AddonManagerTest` covers installed JAR/data preservation through startup, shutdown, and restart; missing or invalid addon directories; and cleanup when one addon fails. Live Minecraft/Discord behavior still requires server testing.
