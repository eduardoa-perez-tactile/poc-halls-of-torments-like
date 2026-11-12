# Couch Crypt

A LibGDX Android TV couch co-op foundation, using Couch Crypt as its working title. This milestone includes original dark-fantasy lobby art, TV remote navigation, and local QR joining for two phone browsers. Gameplay is the next milestone.

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

The emulator's own `10.0.2.x` address is not reachable by physical phones; the helper bridges that boundary. Both phones and the computer must be on the same LAN, and the local firewall must allow incoming TCP 8787. Guest Wi-Fi client isolation can prevent joining.

## Run on a physical Android TV

Set `ANDROID_HOME` to your SDK, or set `sdk.dir` in an untracked `local.properties`. Build and install:

```sh
./gradlew :android:assembleDebug
adb -s YOUR_TV_SERIAL install -r android/build/outputs/apk/debug/android-debug.apk
adb -s YOUR_TV_SERIAL shell am start -n com.couchcrypt.tv/com.couchcrypt.android.AndroidLauncher
```

Open **Gather Your Party** using the remote. Each phone scans the QR and gets a slot automatically. A fallback URL and room code are also shown. Physical TVs serve their own phones directly; no computer relay or internet service is needed.

## Lobby behavior

- Exactly two phones can join. A third receives an explicit full-room message.
- A disconnected slot is held for 45 seconds; the same browser can reclaim it using its stored token. The remote can free a slot immediately.
- Tokens are scoped to this host and room. A duplicate tab cannot take over an active controller. A new app process creates a fresh room code; scan again after restarting the app.
- The TV remote owns menus and player removal. Phones cannot start the game or send shared UI actions.
- **Start Game** unlocks at two connections and displays a party-ready confirmation. It deliberately does not launch gameplay.
- Home/Back preserves connections. Android backgrounding informs connected phones that the host is away.
- Network shows available LAN addresses and can retry a failed controller server.

## Focused verification

```sh
./gradlew :core:test :desktop:classes :android:assembleDebug
node --check assets/web/controller.js
```

The small test suite covers slot allocation, reconnect reservations/expiry, duplicate tabs, host removal, room validation, and the real HTTP/WebSocket handshake. No whole-game playthroughs or broad regression runs are part of this milestone.

Initial checks passed: eight core tests, desktop compilation, Android APK build/launch, D-pad/OK/Back navigation, and browser joining through the emulator LAN relay. A browser plus a separate WebSocket client filled both Android slots; a third client was rejected and the second client reclaimed its slot after disconnecting. Two physical phones and camera scanning still need the user's review.

F3 toggles desktop diagnostics. `--profile` (or the Android `profile` intent extra) logs FPS, live Java heap, resident art texture bytes, and draw calls every five seconds. These are brief diagnostics, not a guarantee of performance on every TV. See [performance notes](docs/performance.md).

## Structure

- `core`: shared LibGDX UI, lobby state, QR generation, and embedded HTTP/WebSocket server.
- `desktop`: LWJGL3 launcher for development.
- `android`: Android TV launcher, manifest, and native library packaging.
- `assets`: original artwork, licensed fonts, and offline controller page.
- `scripts/tv-dev.py`: emulator-to-LAN development relay with cleanup.

See [AGENTS.md](AGENTS.md), [architecture](docs/architecture.md), [art direction and prompts](docs/art-direction.md), and [UI flow](docs/ui-flow.md).
