# Twenty-minute progression

The normal arena uses a rising shared XP curve with the existing level-20 cap: at most 19 personal upgrades per player. Individual skill effects and caps are unchanged, including class eligibility, exact card previews and the bounded projectile counts. The three-minute development fixture retains its fast XP curve; timelines longer than that fixture use the long curve.

| Intended checkpoint | Party level target | Cumulative collected XP |
| --- | --- | --- |
| 3 minutes | 4–5 | 68–116 |
| 8 minutes | 9–10 | 416–516 |
| 14 minutes | 14–15 | 1,016–1,166 |
| 20 minutes | 17–20 | 1,481–1,991 |

These are tuning targets, not measured playtest outcomes. The authored schedule can generate roughly 2,025 solo XP before spawn skips, pool caps, phase-transition delays and missed pickups; missed kills/pickups lower the final level. Later costs rise from 100 to 175 souls, intended to space choices roughly a minute or more apart rather than keep reopening the chooser. Reaching the cap near the final encounter requires nearly complete throughput and collection; levels 17–19 are more plausible with losses, pending playtesting. No timer grants XP automatically.

Souls are shared once, not duplicated per player. The encounter implementation normalizes co-op drops against its faster reinforcement cadence, carrying fractional rewards so repeated small drops do not lose all their value. Existing pickups retain their value when another player joins.

Normal earned entitlements produce three personal cards with a host-owned 30-second timer; timeout selects option zero. A late join snapshots existing entitlements, immediately receives older ranks through a balanced Warden/Ranger foundation, and keeps the latest three skill decisions personal at 15 seconds each. Older equipment rewards similarly grant up to six foundation items, leaving two personal 15-second gear decisions. At level 20 with eight loot rewards this accounts for all 19 skill ranks and eight item entitlements through 16 automatic ranks, six automatic items and five personal choices. Inherited backlog therefore takes at most 75 active-simulation seconds if every choice times out, replacing the old 13.5-minute combined queue.

The phone shows the actual granted foundation and remaining choices. Manual selections can finish sooner. New rewards earned after joining are outside the snapshot and retain normal 30-second choices; they wait until inherited gear is complete. Pause/background/disconnect freeze both clocks. Reconnect preserves the current build, health, IDs and remaining time; removal/rejoin creates a fresh participant with a new foundation and full effective arrival health. Class caps and all actual rank/stat effects still apply; no ally's build is copied.

Focused tests check XP milestone boundaries/cap, overflow safety, both heroes spending all 19 entitlements, capped late joining, independent queues, class-eligible distinct cards, fresh queued timers and cap publication/selection. Gameplay collection rates, build strength and chooser cadence still require the user's solo/co-op playtests.


The focused catch-up verification passed 59 JVM tests covering fixed snapshots, all inherited entitlements for both heroes, the 75-second timeout bound, new-reward deferral, accurate foundation-aware previews, decline, stale IDs and reconnect ownership/state. The real-controller state harness and JDK 21 desktop/Android builds passed. A brief emulator-5554 fixture joined Player II at party level 20 with eight earned loot rewards, checked the granted foundation and frozen 15-second clocks in portrait/landscape, and selected three personal upgrades before observing the inherited gear phase. Expanded foundation details and short landscape cards use the existing contained scrolling; collapsing the summary restores card space. The temporary browser/controller were closed and the emulator restored to a fresh lobby. No full survival session, artwork changes or new performance trace were needed.
