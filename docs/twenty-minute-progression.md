# Twenty-minute progression

The normal arena uses a rising shared XP curve with the existing level-20 cap: at most 19 personal upgrades per player. Individual skill effects and caps are unchanged, including class eligibility, exact card previews and the bounded projectile counts. The three-minute development fixture retains its fast XP curve; timelines longer than that fixture use the long curve.

| Intended checkpoint | Party level target | Cumulative collected XP |
| --- | --- | --- |
| 3 minutes | 4–5 | 68–116 |
| 8 minutes | 9–10 | 416–516 |
| 14 minutes | 14–15 | 1,016–1,166 |
| 20 minutes | 17–20 | 1,481–1,991 |

These are tuning targets, not measured playtest outcomes. The authored schedule can generate roughly 2,050 solo XP if enemies are killed and souls collected promptly; missed kills/pickups lower the final level. Later costs rise from 100 to 175 souls, intended to space choices roughly a minute or more apart rather than keep reopening the chooser. Efficient play can reach the cap near the final encounter, while less efficient collection leaves late upgrades available. No timer grants XP automatically.

Souls are shared once, not duplicated per player. The encounter implementation normalizes co-op drops against its faster reinforcement cadence, carrying fractional rewards so repeated small drops do not lose all their value. Existing pickups retain their value when another player joins.

Each earned entitlement still produces three personal cards and a host-owned 30-second timer. Timeout applies the first offered option. A pending offer keeps its identity, values and remaining time while later levels queue. Late joiners receive the same bounded number of personal choices; selecting promptly advances catch-up immediately, while waiting for every timeout can take up to 9.5 minutes at the cap. Pausing, disconnect and backgrounding continue to freeze the simulation-owned choice clock. The cap remains 20 in protocol and HUD; the protocol now references the authoritative progression constant.

Focused tests check XP milestone boundaries/cap, overflow safety, both heroes spending all 19 entitlements, capped late joining, independent queues, class-eligible distinct cards, fresh queued timers and cap publication/selection. Gameplay collection rates, build strength and chooser cadence still require the user's solo/co-op playtests.
