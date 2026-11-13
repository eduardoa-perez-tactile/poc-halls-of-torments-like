# The crypt: one replayable arena

This milestone completes one self-contained co-op run, not the full Halls of Torment content catalog. World artwork remains temporary. The approved compact, unboxed HUD and original menu assets remain in use.

## Reference and adaptation

The user supplied the [Halls of Torment wiki](https://hot.fandom.com/wiki/Halls_of_Torment_Wiki). Its main page returned HTTP 402 through the web reader; indexed pages supplied these mechanics references:

- [Swordsman](https://hot.fandom.com/wiki/Swordsman): cone melee, multiple targets and brief interruption/knockback informed the Warden sword sweep.
- [Archer](https://hot.fandom.com/wiki/Archer): bow spread/piercing informed the Ranger and weapon-specific traits.
- [Abilities](https://hot.fandom.com/wiki/Ability): acquiring additional attacks and improving traits informed Soul Circle and Chain Storm in the existing phone choices.
- [Haunted Caverns](https://hot.fandom.com/wiki/Haunted_Caverns): scheduled elite encounters leading to a final Lord informed the three-wave/final-boss structure.

These are original adaptations. Names, timings, balance, sounds and implementation belong to this project; no source game artwork or wiki text was imported.

## Run loop

The TV remote selects Warden or Ranger on each lobby player card, including Ranger in solo. Once a hero enters the run it stays fixed for that participant. A new late-joining participant may choose a hero before resuming.

Warden attacks a nearby cone with a sword and brief knockback. Ranger targets enemies with arrows and moves faster. Both attack automatically; phones retain movement, dodge and personal upgrade choices. Shared soul XP advances both players together, with independent thirty-second choices and existing default/queue/catch-up behavior.

Twelve upgrade types cover damage, haste, movement, vitality, evasion, wider sword reach, multishot, piercing, a radial soul attack, chain lightning, pickup attraction and critical chance. Offers exclude the other hero's weapon traits and capped skills. Early choices feature secondary abilities; replay seeds vary later options. Pickups attract toward nearby living heroes.

The default three-minute slice has three authored phases: clustered ghoul swarms, a brute supported by a ghoul and fast wraith, then a cultist formation with melee escorts. Packs approach from a shared direction, with small spacing offsets. Reinforcements take a three-second breather every eighteen seconds; existing enemies keep fighting. Elite checkpoints at the second and third phase boundaries keep their original rewards. Spawns keep at least 300 world units away from both living players, including at arena edges, and regular packs leave two enemy slots free.

`ArenaTimeline` defines timed phases, encounter cadence, breathers and optional elite checkpoints. `ArenaWorld()` uses the short preset; `new ArenaWorld(ArenaTimeline.TWENTY_MINUTES)` enables a six-phase, twenty-minute schedule without changing simulation code. Wave numbering, remaining time, checkpoints, enemy health ramp and the final boss boundary derive from that timeline. The long preset provides scheduling infrastructure only: it is not exposed in the menu, and twenty-minute progression/content balance remains future work. At the configured duration the Hollow Sovereign arrives. The timer reaching zero starts the boss; it does not award victory or impose an encounter deadline. Defeat comes when the active party falls. Victory requires the boss kill.

The boss marks ground impacts before detonation and fires slow projectile spreads. At half health it enrages, attacking faster with a wider spread. Dodge invulnerability applies. Solo/co-op health scaling is fixed at boss spawn. A late player can join through the existing pause flow.

Elite rewards, healing drops, wave/boss notices, attack arcs, arrows, hit/death feedback and short sound cues make the run readable. Audio can be toggled in Settings. The approved art-led end screens show a short elapsed-time/level/kills summary; Run Details shows collected souls and each player's damage, revives and chosen build. Play Again resets the run and varies its seed; Return to Lobby preserves controller connections.

## Bounds and handoff

Simulation remains fixed-step and preallocated: 64 enemies, 96 friendly arrows, 96 hostile shots, 12 ground warnings, 96 visual effects and 96 pickups. Combat rendering reuses existing atlases; no new gameplay textures or post-processing are required. Results load one approved scene/title pair at a time, staying below the combined 32 MiB texture budget. The eight mono sound cues are procedurally generated offline by scripts/GenerateCombatAudio.py and total roughly 131 KiB of PCM.

Focused tests use small combat fixtures for weapons, upgrades, telegraphs, boss outcomes and reset behavior. Brief device checks use profiling-only F5 (level) and F6 (boss) to avoid a full survival playthrough. User playtesting evaluates feel, difficulty, TV readability and two physical phones; sustained performance on representative physical TV hardware remains a separate acceptance check.
