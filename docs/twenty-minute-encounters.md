# Twenty-minute encounter targets

The normal run now uses `ArenaTimeline.TWENTY_MINUTES`. The timer counts down 20 minutes of active simulation, then starts the Hollow Sovereign encounter. Victory requires killing the boss. Pauses and disconnects retain the existing simulation freeze. `SHORT` remains the explicit three-minute development fixture; it is not a player-facing mode.

| Active time | Phase / composition | Checkpoint |
| --- | --- | --- |
| 0:00–1:30 | The Crypt Stirs: ghoul swarm | — |
| 1:30–3:00 | Restless Hunt: wraiths and ghouls | — |
| 3:00–5:00 | The Iron March: brute, ghoul, wraith | Brute elite |
| 5:00–7:00 | Choir of Ash: cultist formation | — |
| 7:00–9:00 | Broken Ranks: brute/cultist siege | Cultist elite |
| 9:00–11:00 | Wraith Procession: mobile hunters | — |
| 11:00–13:00 | Sovereign's Guard: mixed siege | Brute elite |
| 13:00–15:00 | The Closing Circle: all four regular kinds | — |
| 15:00–16:30 | Dread Reinforcements: brute pressure | Cultist elite |
| 16:30–18:00 | Last Vigil: cultist formation | — |
| 18:00–19:00 | The Hollow Host: combined assault | Brute elite |
| 19:00–20:00 | Before the Throne: combined assault | — |
| 20:00 onward | Hollow Sovereign and limited reinforcements | Kill required |

Regular packs contain three to five enemies. Reinforcement intervals progress from 3.5 to 2.1 seconds, with a 4–5 second reinforcement breather in each 24-second cycle. Existing enemies continue fighting during breathers. Phase changes allow 1.4 seconds before the next regular pack. Temporarily blocked checkpoint spawns retry without duplicating their elite.

Health rises from 1× to 3.2× base; contact/cultist damage rises from 1× to 1.5×. Regular movement speed rises at most 20%; existing elite speed and health multipliers remain. Solo reinforcement caps grow from 26 to 46 active enemies; two living players add 12 slots and shorten reinforcement intervals by 18%, with 20% more regular enemy health. When an ally falls, new reinforcement pressure uses the solo limit/rate; existing enemies retain their spawned stats.

Hard pools remain 64 enemies, 96 friendly bolts, 96 hostile shots, 96 pickups, 12 hazards and 96 effects. Soft caps reserve room for elites/boss. Every spawn must remain at least 300 world units from both living players, including near arena edges; unsafe reinforcements are skipped. The initial equipment balance pass gives the boss 8,200 health solo and 13,120 with two participating players, using the existing attacks, phase transition and warning times. Boss reinforcements stop above 12 solo / 18 co-op active enemies and otherwise arrive one per 3.5 seconds. Existing crowds must be defeated; there is no timer-based purge.

Soul base rewards remain ghoul/wraith 1, cultist 2, brute 3, elite 8 and boss 20. Enemies spawned with two living players carry an 82% XP reward snapshot, approximately compensating for the extra reinforcements. Exact integer hundredths carry across drops, so small rewards are conserved without per-pickup rounding loss. Existing enemy rewards and dropped souls do not change when another player joins. See `PartyProgression` for the full-run XP curve (1,991 collected XP to level 20); collection/kill efficiency and personal build choices determine actual progress.

These are implementation targets, not a claim of completed balance. Human solo/co-op playtests should assess first upgrade timing, ranged pressure, recovery around elite checkpoints, late-run kill throughput and boss duration. Focused automated tests jump directly to boundaries and fixtures; they do not simulate a twenty-minute survival session. The profile-only late fixture starts at 18:30 with bounded representative density for short performance observation.
