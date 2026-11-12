# Foundation and lobby

The TV/desktop host owns a two-slot LobbyState. Its synchronized methods are short; network sockets and asset I/O do not run in the render loop. The LibGDX UI reads snapshots twice per second and changes labels only when state or reconnect countdown changes.

NanoWSD serves an allowlisted set of local assets over HTTP and accepts WebSockets on the same TCP port, 8787. ZXing generates the join QR from the TV's plain local address. There is one party per TV. The controller page discovers an opaque internal session identifier from GET /session, then automatically connects; players never enter or see a room number. The page has no runtime third-party dependencies.

The WebSocket handshake validates the internal session and browser Origin, claims one of two slots, and returns a player number plus an opaque reconnect token. New phones may join in the lobby or when the host opens Invite Player 2. Existing phones can reclaim reserved slots during any phase. The page sends a heartbeat every two seconds. Ten seconds without a heartbeat disconnects a phone; its slot is then reserved for 45 seconds. The host can remove an occupied or reserved slot. Late callbacks from a previous socket cannot disconnect its replacement. Unknown controller messages cannot invoke remote UI commands.

The browser stores its token under a host/room-specific localStorage key, with a memory fallback if storage is blocked. It explicitly handles full rooms, duplicate tabs, invalid rooms, host removal, backgrounding, and bounded reconnect attempts. A removed phone stops retrying until its user explicitly joins again.

Session identifiers isolate browser reconnect tokens across app restarts. This is an unencrypted home-LAN prototype, not an internet-facing service. Do not expose or port-forward it onto the public internet.

The Android launcher supplies an optional advertised host through the joinHost intent extra. On a physical TV, local IPv4 discovery supplies the address. During emulator development, the Python launcher forwards an ephemeral loopback ADB port and serves a bounded streaming relay on the computer's LAN port 8787. This keeps QR links usable by real phones without depending on the emulator's isolated address.

LibGDX uses a fixed logical 1920x1080 FitViewport with safe margins. The UI is laid out once per screen; geometric D-pad navigation selects enabled controls, and modals preserve/restore focus. Desktop supports keyboard and mouse. Android supports D-pad/OK/Back and uses immersive landscape rendering with touchscreen optional and the TV launcher declared.

Resources are shared across screens. File textures and retained font atlas data support Android GL context restoration; QR textures are regenerated on resume and disposed on replacement. Server threads are stopped and GPU resources disposed at application shutdown.

Home, lobby, and dialogs now share the approved crypt backdrop and matching stone/brass/ruby theme. Normal and focused plates are separate painted textures baked with identical source bounds; nine-slice views reuse them at different control sizes. There is no post-processing glow or per-frame image processing. At startup, small PNGs for phone panels, button states, wordmark, and backdrop are encoded from the existing baked pixmaps; the server does not retain full-resolution source images for those routes. See docs/home-menu-art.md for the exact generation prompts and asset provenance.

AndroidLauncher uses singleTask launch mode so reopening from the TV launcher reuses the existing activity, room, and controller server. A second activity would otherwise fail to bind the same local port and show an offline lobby. The QR area now displays an explicit network/host error instead of a blank artwork panel if hosting or QR generation is unavailable.

Gameplay systems, movement input, split-screen cameras, class selection, combat, and progression are deliberately outside this milestone.

Sources: [LibGDX project structure](https://libgdx.com/wiki/start/project-generation), [Android TV setup](https://developer.android.com/training/tv/get-started/create), [NanoHTTPD](https://github.com/NanoHttpd/nanohttpd), [ZXing](https://github.com/zxing/zxing).
