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

Normal earned entitlements produce three personal cards within one host-owned 30-second party window; timeout selects option zero. Combat, the arena clock and gear clocks pause globally during skill choices. The TV displays LEVEL UP, CHECK YOUR PHONE and one shared countdown and compact ready status. A completed player waits; play resumes automatically after every pending/queued skill choice is resolved. Manual pause/background/disconnect also freeze choice clocks. The twenty-minute target remains active combat time, so selection pauses add wall-clock time.

A new late joiner automatically receives all previously earned class-appropriate ranks and gear rewards, with no inherited choices or build-review step. Future rewards use normal personal choices. Reconnect preserves the current build, health, offer IDs and remaining time; removal/rejoin creates fresh defaults and full effective arrival health. Class caps apply; no ally build is copied.

Focused checks cover XP boundaries/caps, exact previews, bounded personal queues with shared choice rounds, automatic defaults, reconnect ownership/state, global combat/gear-clock freezing, timeout defaults and resuming after the last selection. Full-run collection rates, build strength and pause cadence still require user playtesting.
