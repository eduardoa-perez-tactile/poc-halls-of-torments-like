# End-of-run presentation

The November 24 polish uses the approved Warden and Ranger portrait regions from
character selection throughout Victory, Defeat, Skills and Equipment. It reuses
the shared crypt, outcome titles, fonts, button skins and icon atlases; it adds no
textures or retained decoded Pixmaps.

Victory and Defeat center the participating hero or pair above labeled run time,
party level and enemies slain. A restrained translucent charcoal band separates
those statistics from the stone steps. Defeat identifies reaching the Sovereign
and its remaining health when a boss encounter was reached. Replay, Return to
Lobby and Run Details are three painted controls in one row. Replay text follows
the connected party, explicitly warns when co-op would restart solo, and offers
a lobby reconnect route when no phones remain.

Run Details retains the unboxed character-and-build layout. Portraits have a
consistent 318-unit identity area across Skills, Equipment and pages. Four entries
per player per page preserve effect descriptions for full builds and give equipment
larger names, bonuses and icons. The remote turns both player columns together;
the page counter and disabled boundary controls show what remains. Switching
Skills/Equipment starts on page one and keeps focus on the switch. Back restores
Run Details focus on the summary. Solo emphasizes damage taken instead of revives.

Skill summaries show permanent effective values including equipped gear, with
conditional gear bonuses described in Equipment. Soul Circle and Chain Storm
include their damage and range/target/cadence values. Large personal totals use
grouped digits and fit their allocated statistic width. Phone result status carries
an additive outcome field, shows Victory or Defeat, keeps gameplay controls hidden
and leaves shared navigation with the remote. Leaving Results clears the outcome.

## Defeat transition (2026-11-24)

Whole-party defeat now holds the frozen arena for 350 ms, fades to black over 550 ms,
swaps to the existing Defeat results at full black, then reveals them over 650 ms.
The transition clock latches once, waits for the actual results swap and freezes
while the app is backgrounded. Remote activation/navigation and touch-through
are blocked until the reveal completes; Try Again resets the clock for the next
run. Combat, choice timers and scenery stay frozen during the final arena hold.
One downed co-op participant still uses the normal revival flow; Victory is
unchanged. A single resident-white-pixel stage cover supplies the fade with no
new texture, framebuffer, artwork or per-frame allocation. Profile F8 now defeats
the current party for a short presentation check without replacing its arena/build.

## Focused verification

JDK 21 desktop compilation and Android debug packaging passed. Focused Java checks
cover the result protocol, clearing outcomes, conditional stat semantics and
bounded pages for full late-join builds. The controller JavaScript harness covers
both outcomes and compatibility with hosts that omit the outcome field.

Native 1920x1080 desktop rendering checked solo/co-op results, every available
skill/equipment page, remote focus and page boundaries, Back restoration, a lost
companion and replay with no connected phones. Separate max-level fixtures cover
all three skill pages; native text measurement covered all 48 item descriptions.
The summary backing was visually checked in both outcomes. These checks do not
establish physical-TV viewing-distance readability, gameplay balance or sustained
60 FPS. No survival playthrough was run. Ignored captures are under
`.run/end-screen-verification/` and `.run/end-screen-details-check/`.

### Defeat-fade verification

JDK 21 compiled desktop classes and the Android debug APK. 29 focused tests passed
for the new transition clock, defeat/revival simulation, audio routing, HUD and
result presentation. The installed APK was captured in native 1920×1080 co-op:
plain foot rings, fallen-party hold, fade to black and Defeat reveal. An OK press
immediately after result publication stayed blocked during reveal; Try Again
worked afterward and restored a fresh controllable session. A five-second silent
Android screenrecord preview and original extracted frames are under ignored
`.run/simple-auras-defeat/`. The app returned to the normal lobby with its existing
LAN QR host/relay intact; temporary controllers and capture processes were closed.

A ten-second moving co-op fixture produced one 302-frame report inside measurement
markers: interval p95/p99/max 18.20/19.14/20.47 ms and render-callback wall p95/p99/max
9.77/12.91/19.96 ms, 147 intervals above 16.7 ms and none above 33.4 ms. Combined accounted
GPU textures remain 33,221,452 bytes (31.6824 MiB); gameplay retains zero Pixmaps.
GPU duration is unmeasured, and physical-TV performance/readability still needs
human checking. Arena difficulty, equipment sustain and all runtime art files
remain byte-for-byte unchanged by this presentation pass.
