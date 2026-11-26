# Twenty-minute encounter targets

The normal run uses `ArenaTimeline.TWENTY_MINUTES`. Mourning Bell arrives at 5:00 and Hollow Sovereign at 12:00 elapsed combat time; waves and the clock continue. At 20:00 the Grave King arrives, even if either mini survives. Victory requires killing the Grave King. Pauses, phone choices and disconnects retain the existing simulation freeze. `SHORT` remains the explicit three-minute development fixture, with Bell at 0:45 and Sovereign at 1:48; it is not a player-facing mode. See [boss behavior and art](boss-encounters.md).

| Active time | Phase / composition | Checkpoint |
| --- | --- | --- |
| 0:00–1:30 | The Crypt Stirs: ghoul swarm | — |
| 1:30–3:00 | Restless Hunt: two ghouls and one original wraith per pack | — |
| 3:00–5:00 | The Iron March: brute, ghoul, wraith | Brute elite |
| 5:00–7:00 | Choir of Ash: cultist formation | Mourning Bell at 5:00 |
| 7:00–9:00 | Broken Ranks: brute/cultist siege | Cultist elite |
| 9:00–11:00 | Wraith Procession: mobile hunters | — |
| 11:00–13:00 | Sovereign's Guard: mixed siege | Brute elite at 11:00; Sovereign mini boss at 12:00 |
| 13:00–15:00 | The Closing Circle: all four regular kinds | — |
| 15:00–16:30 | Dread Reinforcements: brute pressure | Cultist elite |
| 16:30–18:00 | Last Vigil: cultist formation | — |
| 18:00–19:00 | The Hollow Host: combined assault | Brute elite |
| 19:00–20:00 | Before the Throne: combined assault | — |
| 20:00 onward | Grave King and limited reinforcements | Grave King kill required |

The 26 November learning-curve correction supersedes the opening density of the
24 November difficulty pass. Base species still enter at 0:00 (Ghoul), 1:30
(Wraith), 3:00 (Brute) and 5:00 (Cultist). Early Wraiths are now a minority in
their pack. Colour/stat variants enter separately through the
[staged variant schedule](enemy-variants.md#staged-introductions), rather than all
being eligible from the first spawn of each species. Boss and elite times stay fixed.

Regular
packs contain three to five enemies. Nominal solo reinforcement rates (before
soft caps, unsafe-spawn skips and phase-entry delays) increase monotonically:
31.3 / 37.5 / 45 / 61.8 / 70 / 75 / 104.8 / 114.6 / 126.9 / 137.5 / 148.6 / 165
enemies per minute. The old fifteen-minute reinforcement slump is removed.
Breathers occupy four seconds of each 24-second opening cycle, then three, then
two from minute eleven. Phase changes retain a 1.4-second pack delay.

| Active time | Solo pack interval | Solo soft cap |
| --- | ---: | ---: |
| 0:00–1:30 | 4.8 s | 16 |
| 1:30–3:00 | 4.2 s | 20 |
| 3:00–5:00 | 3.5 s | 24 |
| 5:00–7:00 | 3.4 s | 28 |
| 7:00–9:00 | 3.0 s | 32 |
| 9:00–11:00 | 2.1 s | 36 |

From 11:00 onward, pack intervals and caps retain the previous difficulty pass.
Reduced early reinforcement also means fewer potential souls; XP per enemy,
level costs, healing rules and equipment sustain are unchanged. First-upgrade
timing and survival remain user-playtest questions.

Enemy health and damage interpolate between authored phase-start values rather
than jumping at boundaries. Health rises 1→4.8× and contact/cultist damage 1→2×;
the last node is reached at minute nineteen and remains steady afterward.
Existing enemies retain their spawn snapshots. Solo soft caps rise 16→50; two
living players add twelve slots (maximum 62), shorten pack intervals by 18% and
add 60% ordinary enemy health. Variant and elite modifiers apply once. An ally
falling reduces future reinforcement pressure without changing existing stats.

After the warm-up, packs split across approach directions and cover both living
players. Wraiths move toward a short 120-unit interception lead while the target
moves; attack aim still locks at its existing windup. Full-run base movement is
Ghoul 65 / Wraith 120 / Brute 46 / Cultist 60 units/s, gaining up to 35% over the run
before existing variant/elite movement factors. Cultist shot speed rises 210→250;
its warning duration, shot damage scaling and locked aim remain intact.

Hard pools remain 64 enemies, 96 friendly bolts, 96 hostile shots, 96 pickups, 12 hazards and 96 effects. Soft caps reserve room for elites/boss. Every spawn must remain at least 300 world units from both living players, including near arena edges; unsafe reinforcements are skipped. The initial equipment balance pass gives the boss 8,200 health solo and 13,120 with two participating players, using the existing attacks, phase transition and warning times. Boss reinforcements stop above 12 solo / 18 co-op active enemies and otherwise arrive one per 3.5 seconds. Existing crowds must be defeated; there is no timer-based purge.

Soul base rewards remain ghoul/wraith 1, cultist 2, brute 3, elite 8 and boss 20. Enemies spawned with two living players carry an 82% XP reward snapshot, approximately compensating for the extra reinforcements. Exact integer hundredths carry across drops, so small rewards are conserved without per-pickup rounding loss. Existing enemy rewards and dropped souls do not change when another player joins. See `PartyProgression` for the full-run XP curve (1,991 collected XP to level 20); collection/kill efficiency and personal build choices determine actual progress.

These are implementation targets, not a claim of completed balance. Human solo/co-op playtests should assess first upgrade timing, ranged pressure, recovery around elite checkpoints, late-run kill throughput and boss duration. Focused automated tests jump directly to boundaries and fixtures; they do not simulate a twenty-minute survival session. The profile-only late fixture starts at 18:30 with bounded representative density for short performance observation.

## Health supply and playtest diagnostics

Full-run ordinary kills generate 16 HP every 14 kills before minute three, then 12 HP
per 20 kills. Successful ordinary drops are separated by at least 18 combat seconds
solo or 12 with two living players. Blocked events are consumed rather than queued.
Elite recovery is 24 HP. Healing on the floor is capped at three solo/four co-op;
regular drops live 35 combat seconds, elite drops 45, fading during their final five.
An elite may replace the oldest ordinary heal at the cap. Healing never merges or
evicts XP. A saturated XP drop reclaims old healing first, then conserves XP through
its existing merge path. Expiry and generation clocks freeze with all combat pauses.
The three-minute fixture retains its former healing rules.

Equipment regeneration, life steal, kill healing, items, skill ranks and XP costs
are unchanged. ProfileWorld reports party level/XP, kills, active enemies, generated/
expired/effectively collected healing, regeneration, gear sustain and Sovereign
fight time. These counters expose progression and sustain without adding HUD UI.

## Difficulty-pass verification

JDK 21 focused runs passed 107 tests across timeline continuity/cadence, safe mixed
packs, variant/co-op snapshots, boss arrivals and delayed releases, actual boss
projectile damage, healing generation/expiry/caps, XP conservation, equipment
sustain, choice pauses, reconnect/late joining and combat integration. Desktop
classes and the Android debug APK compiled. These checks use short fixtures;
no whole-run automated playthrough was performed.

The installed APK rendered the 12:00 Sovereign in solo/co-op and exercised phone
joins and queued reward choices. A separate ten-second moving co-op/final fixture
produced one 301-frame report wholly inside measurement markers: interval p95/
p99/max 19.59/20.10/20.96 ms; render-callback wall p95/p99/max 10.13/14.94/18.76 ms.
148/300 intervals exceeded 16.7 ms; none exceeded 33.4 ms. Accounted GPU textures were
33,221,452 bytes (31.6824 MiB), with zero retained gameplay Pixmaps (518,988 bytes
application-wide). GPU duration is unmeasured; sustained physical-TV 60 FPS and
boss fight length remain human playtest requirements.

A second idle installed game had occupied controller port 8787 during the initial
native setup; stopping that background host resolved it. The existing LAN relay,
its 49538→8787 ADB forward and saved 192.168.14.248:8787 QR override were preserved.
The app was returned to its normal lobby with profile fixtures disabled and
synthetic phones closed. Native evidence and the playtest APK stay ignored under
`.run/difficulty-pass/` and `.run/playtest-builds/`.
