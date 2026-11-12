# Couch Crypt

A LibGDX Android TV couch co-op arena survivor, using Couch Crypt as its working title. The first playable slice includes original dark-fantasy art, TV remote menus, QR phone controllers, and solo or two-player combat.

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
- The TV remote owns menus and player removal. Phones cannot start the game or send shared UI actions.
- **Start Solo** unlocks at one connection; **Start Co-op** appears at two. They start a two-minute survival round. Phones control movement and dodge; attacks automatically target nearby enemies.
- Press Back in the session to open **Pause**: Resume, Invite Player 2, Restart Run, or Return to Lobby. Invite shows the QR without leaving the session; Back returns to Pause. Returning to the lobby requires confirmation and keeps the party connected. A disconnected player pauses the world. Reconnect that phone or remove its slot under Invite Player 2 before resuming.
- Player 2 can join in the lobby or later from **Pause -> Invite Player 2**. The normal join area shows only the QR and a Same Wi-Fi reminder; address/port troubleshooting is under Network.
- Home/Back preserves connections in the lobby. Android backgrounding informs connected phones that the host is away.
- Network shows available LAN addresses and can retry a failed controller server.

The gameplay artwork is temporary pending a separate isometric art pass; the current focus is functionality.

## First playable slice

- Survive for two minutes against ghouls. Collect shared souls and occasional healing drops.
- One connected player gets a full-screen view. Two get independent vertical split-screen cameras in the same world.
- Invite a second phone from Pause; it enters beside its companion when resumed.
- The TV displays ornate player vitals, timer/rewards, and queued combat notifications. A hidden boss status lane is ready for future encounters.
- Phones show only player identity, movement, and Dodge during play; health stays on the TV.
- Use the joystick to move and Dodge to escape. Both starter characters currently fire the same automatic magic bolt; differentiated weapons and progression are future work.
- A living ally can revive a fallen companion by staying nearby for two seconds. If everyone falls, the run ends.
- Victory/defeat offers Restart Run or Return to Lobby using the TV remote.

## Focused verification

```sh
./gradlew :core:test :desktop:classes :android:assembleDebug
node --check assets/web/controller.js
```

The small test suite covers slot allocation, reconnect reservations/expiry, duplicate tabs, host removal, room validation, and the real HTTP/WebSocket handshake. No whole-game playthroughs or broad regression runs are part of this milestone.

The focused tests cover simulation invariants and input/protocol safety. User playtesting covers combat feel, difficulty, two physical phones, and camera scanning. No automated full-round playthrough is required.

F3 toggles desktop diagnostics. F4 during a session toggles a labeled boss-HUD preview for visual review; boss gameplay is not implemented. `--profile` (or the Android `profile` intent extra) logs FPS, live Java heap, resident art texture bytes, and draw calls every five seconds. These are brief diagnostics, not a guarantee of performance on every TV. See [performance notes](docs/performance.md).

## Structure

- `core`: shared LibGDX UI, lobby state, QR generation, and embedded HTTP/WebSocket server.
- `desktop`: LWJGL3 launcher for development.
- `android`: Android TV launcher, manifest, and native library packaging.
- `assets`: original artwork, licensed fonts, and offline controller page.
- `scripts/tv-dev.py`: emulator-to-LAN development relay with cleanup.

Home, lobby, pause, dialogs, and the phone page share the same carved stone, aged brass, and ruby-gem theme. Buttons have separate normal and bright gold focused/pressed artwork with aligned crops. Native UI textures are baked once and shared by all controls.

The approved centered Home uses a crypt backdrop, forged-gold logo, and pale carved-stone controls for Play, How to Join, Settings, and Exit. These are live remote controls over separate artwork, not a flattened menu screenshot. Reusing that backdrop throughout the app keeps the shared texture footprint at about 23.07 MiB plus QR, including the 3 MiB gameplay atlas and floor, within the 32 MiB budget. Settings contains the functional Network submenu; Exit asks for confirmation. Phones receive small, already baked versions of the same art rather than full-size source paintings.

The Android launcher has a safe-zone adaptive icon, legacy icon densities, and 16:9 title banners. Its actual banner has been checked in the TV emulator's Installed Games row. Reproduce these code-native assets with `java -Djava.awt.headless=true scripts/GenerateLauncherArt.java` using JDK 21.

See [AGENTS.md](AGENTS.md), [architecture](docs/architecture.md), [art direction](docs/art-direction.md), [approved menu artwork and prompts](docs/home-menu-art.md), [TV launcher guidelines and assets](docs/tv-launcher.md), and [UI flow](docs/ui-flow.md).
