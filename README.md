# Couch Crypt

A LibGDX Android TV couch co-op arena survivor, using Couch Crypt as its working title. One replayable arena includes remote-selected melee/bow heroes, timed waves, a final boss, shared XP, personal phone upgrades and per-player build results. World artwork remains temporary.

## Run on desktop

Requirements: JDK 21. The Gradle wrapper downloads pinned build dependencies on first use.

```sh
export JAVA_HOME=/opt/homebrew/opt/openjdk@21/libexec/openjdk.jdk/Contents/Home
./gradlew :desktop:run
```

On other machines, select your installed JDK 21 instead. Use the arrow keys, Enter, and Escape; Android TV uses D-pad, OK, and Back. Desktop mouse clicks are also supported.

If automatic LAN selection picks the wrong adapter:

```sh
./gradlew :desktop:run --args='--host=192.168.1.50 --profile'
```

## Run on the TV emulator with real phones

Requirements: Python 3.9+, Android SDK, ADB, and the `Television_1080p` AVD. The local setup uses the Android 33 TV ARM64 image. Select another AVD with `--avd NAME`.

```sh
python3 scripts/tv-dev.py
```

This builds the debug APK, starts or reuses the TV emulator, installs the app, and relays the emulator's controller server through this computer's LAN address. Keep the command running while phones are connected. Ctrl+C stops the app/relay, removes its ADB forwarding, and shuts down an emulator started by this command. An already running emulator stays running. The log is under `.run/emulator.log` (ignored by Git).

Options: `--no-build` to reuse an APK, `--headless` for a brief automated UI check, `--host YOUR_LAN_IP` to choose the address, and `--profile` for periodic frame/memory logging.

The emulator's own `10.0.2.x` address is not reachable by physical phones; the helper bridges that boundary. Phones and the computer must be on the same LAN, and the local firewall must allow incoming TCP 8787. Guest Wi-Fi client isolation can prevent joining.

## Run on a physical Android TV

Set `ANDROID_HOME` to your SDK, or set `sdk.dir` in an untracked `local.properties`. Build and install:

```sh
./gradlew :android:assembleDebug
adb -s YOUR_TV_SERIAL install -r android/build/outputs/apk/debug/android-debug.apk
adb -s YOUR_TV_SERIAL shell am start -n com.couchcrypt.tv/com.couchcrypt.android.AndroidLauncher
```

Select **Play** using the remote. A phone scans the QR and gets a slot automatically. The TV has one party; there is no room code to enter. Physical TVs serve their own phones directly; no computer relay or internet service is needed.

## Lobby behavior

- One phone is enough for solo; a second phone is optional for co-op. A third receives an explicit full-party message.
- A disconnected slot is held for 45 seconds; the same browser can reclaim it using its stored token. The remote can free a slot immediately.
- Tokens are scoped to this host and app session. A duplicate tab cannot take over an active controller. A new app process creates a fresh session; scan again after restarting the app.
- The TV remote owns shared menus and player removal. Phones cannot start the game or send shared UI actions; each phone can choose its own offered level-up upgrade.
- **Start Solo** unlocks at one connection; **Start Co-op** appears at two. They start twenty minutes of escalating waves followed by the final boss. Phones control movement and dodge; attacks automatically target nearby enemies.
- Press Back in the session to open **Pause**: Resume, Invite Player 2, Restart Run, or Return to Lobby. When both places are occupied, **Manage Party** replaces Invite Player 2. Invite/Manage shows the QR and player slots without leaving the session; Back returns to Pause. Returning to the lobby requires confirmation and keeps the party connected.
- A disconnected participant pauses the world. Pause names the missing Player I/II and shows the remaining 45-second reservation. Reconnect that phone, or use its direct **Remove Player** action and confirm to release its place. Resume becomes available when the remaining participants are connected; the run and upgrade timers stay frozen throughout recovery.
- Player 2 can join in the lobby or later from **Pause -> Invite Player 2**. The normal join area shows only the QR and a Same Wi-Fi reminder; address/port troubleshooting is under Network.
- Home/Back preserves connections in the lobby. Android backgrounding informs connected phones that the host is away.
- Network shows available LAN addresses and can retry a failed controller server.

The gameplay artwork is temporary pending a separate isometric art pass; the current focus is functionality.

## Complete arena loop

- Choose **Warden** (sword cone) or **Ranger** (bow) on each TV lobby player card. Either hero works in solo; each participant keeps that hero for the run.
- Open **Info** from character selection to read the hero's short bio, base stats and all learnable skills. The TV remote browses this reference; closing it preserves your selected hero.
- Survive twelve authored phases across twenty minutes: early swarm/hunt introductions, mixed middle encounters, and late siege/onslaught combinations. Short reinforcement breathers create room to recover; existing enemies keep fighting. Five elite checkpoints reward souls and healing. At twenty minutes, **the Hollow Sovereign** arrives; kill it to win. Reaching zero on the wave timer does not grant victory.
- One connected player gets a full-screen view. Two get independent vertical split-screen cameras in the same world.
- Invite a second phone from Pause; it enters beside its companion when resumed.
- The approved compact TV HUD uses forged-rim health orbs, health/XP rails, shared party level, up to five acquired skill emblems with current ranks and a +N overflow count, plus Dodge readiness. Gold/teal identify the two players. One-line notifications and a slim conditional boss lane leave combat visible. Ground warnings and slow projectile spreads signal boss attacks; below half health it enrages.
- Phones show player identity, movement, and Dodge during play; health stays on the TV. Leveling opens three illustrated skill cards and the 30-second timer, with exact effect and current → next previews. Movement/Dodge stop while choosing; the controller returns after the host applies the selection or timeout. Combat continues.
- Use the joystick to move and Dodge to escape. Warden hits a cone and knocks enemies back; Ranger fires arrows. Weapon traits add wider sweeps, multishot and piercing. Additional choices unlock a radial soul attack, chain lightning, critical hits and pickup attraction.
- Collected souls award shared XP: the party levels together, with matching level/XP shown for each player. Each phone has 30 seconds to pick its own upgrade while combat continues; expiry applies the first offer. Manual pause freezes the timer. Additional earned levels queue choices, and a late companion receives catch-up choices.
- A living ally can revive a fallen companion by staying nearby for two seconds. In split screen, a small direction cue points toward an off-screen fallen ally. Nearby, an unboxed revival label and progress rail show recovery; the fallen player's HUD also shows revival progress. If everyone falls, the run ends.
- Victory and Defeat use their approved painted scenes and separate title art, with live time/level/kill summaries. The remote selects Play/Try Again, Return to Lobby, or Run Details for larger unboxed hero illustrations, damage/revive highlights, skill effect summaries and ranks.
- Short original sound cues cover combat, pickups, levels and outcomes. Toggle Sound in Settings. Regenerate the compact mono WAV assets with `python3 scripts/GenerateCombatAudio.py`.

Normal sessions use `ArenaTimeline.TWENTY_MINUTES`; `ArenaTimeline.SHORT` remains a three-minute development fixture. The party caps at level 20 (19 personal choices). Intended level targets are 4–5 at three minutes, 9–10 at eight, 14–15 at fourteen, and 17–20 before the boss, depending on clear speed and collection. These are tuning targets for user playtesting, not proven balance. See [progression tuning](docs/twenty-minute-progression.md).

## Focused verification

```sh
./gradlew :core:test :desktop:classes :android:assembleDebug
node --check assets/web/controller.js
```

The small test suite covers slot allocation, reconnect reservations/expiry, duplicate tabs, host removal, room validation, and the real HTTP/WebSocket handshake. No whole-game playthroughs or broad regression runs are part of this milestone.

The focused tests cover simulation invariants and input/protocol safety. User playtesting covers combat feel, difficulty, two physical phones, and camera scanning. No automated full-round playthrough is required.

F3 toggles desktop diagnostics. With profiling enabled, F4 previews the boss HUD, F6 starts the actual boss encounter, and F7/F8 show sample Victory/Defeat layouts for design review. F5 grants the XP needed for a level-up. F10 previews a real level-12 build; F11 jumps directly to a bounded late-run encounter with real level-18 builds for brief performance checks. In a two-player session, F9 first places a fallen Player II off-screen, then brings them into revival range for a brief UI check. `--profile` (or the Android `profile` intent extra) reports frame intervals, CPU submission times, Java heap, GPU texture bytes, retained CPU pixmaps, and draw calls. These are brief diagnostics, not a guarantee of performance on every TV. See [performance notes](docs/performance.md).

Outcome backgrounds decode and downsample on one bounded worker. Prewarmed titles and live result controls appear immediately over the existing backdrop; the approved outcome scene replaces it when ready. GPU upload remains on the render thread, only the active outcome texture is retained, and leaving results cancels pending work. The performance notes describe the remaining measurement limits.

## Structure

- `core`: shared LibGDX UI, lobby state, QR generation, and embedded HTTP/WebSocket server.
- `desktop`: LWJGL3 launcher for development.
- `android`: Android TV launcher, manifest, and native library packaging.
- `assets`: original artwork, licensed fonts, and offline controller page.
- `scripts/tv-dev.py`: emulator-to-LAN development relay with cleanup.

Home, lobby, pause, dialogs, and the phone page share the same carved stone, aged brass, and ruby-gem theme. Buttons have separate normal and bright gold focused/pressed artwork with aligned crops. Native UI textures are baked once and shared by all controls.

The approved centered Home uses a crypt backdrop, forged-gold logo, and pale carved-stone controls for Play, How to Join, Settings, and Exit. These are live remote controls over separate artwork, not a flattened menu screenshot. Reusing that backdrop throughout the app keeps the shared texture footprint at about 25.82 MiB plus QR during play, including prewarmed outcome titles and skill icons. Only the active outcome's background is loaded for results, bringing it to about 30.88 MiB plus QR, within the 32 MiB budget. Settings contains the functional Network submenu; Exit asks for confirmation. Phones receive small, already baked versions of the same art rather than full-size source paintings.

The Android launcher has a safe-zone adaptive icon, legacy icon densities, and 16:9 title banners. Its actual banner has been checked in the TV emulator's Installed Games row. Reproduce these code-native assets with `java -Djava.awt.headless=true scripts/GenerateLauncherArt.java` using JDK 21.

See [AGENTS.md](AGENTS.md), [architecture](docs/architecture.md), [art direction](docs/art-direction.md), [approved menu artwork and prompts](docs/home-menu-art.md), [TV launcher guidelines and assets](docs/tv-launcher.md), and [UI flow](docs/ui-flow.md), and [arena design/reference](docs/arena-design.md).


Approved outcome art is stored as separate background/title layers: `assets/art/end-victory-background.png`, `end-victory-title.png`, `end-defeat-background.png`, and `end-defeat-title.png`. Live buttons, summary labels and focus states remain Scene2D controls. See [Victory artwork/prompts](docs/end-victory-art.md) and [Defeat artwork/prompts](docs/end-defeat-art.md), and [skill emblems/prompts](docs/skill-icons-art.md). These were created with built-in image generation and approved on 2026-11-13.
