# Survivor skill expansion

Authorized on 2026-11-25 as one integrated expansion, explicitly without rerolls. Run-only progression remains level 20 / nineteen personal picks, three phone cards and one shared thirty-second window. Equipment remains forty-eight items, seven positions and eight collected-chest rewards. No persistent currency or purchased PowerUp system is introduced.

## Reference and provenance

Mechanics reference: [poc-rouge-like](https://github.com/eduardoa-perez-tactile/poc-rouge-like/tree/9da6c8e0fb82ba4615bda63e9620413463256d99/data/game/families), snapshot `9da6c8e0fb82ba4615bda63e9620413463256d99`. Selected inspirations are King Bible, Cross, Santa Water, Laurel, Clock Lancet/Jail of Crystal, Iron Blue Will/Waltz of Pearls, weapon evolutions, Vacuum and Orologion. Runtime rules are authored for Couch Crypt; the raw wiki export is not shipped. Existing damage, health, movement, attack speed, pickup, regeneration, armour and XP concepts already exist and are not duplicated into a second system.

Original expansion icon/weapon/relic artwork and exact generation prompts are retained in `art-source/survivor-expansion/`. Existing icon/source art is preserved. No Vampire Survivors imagery is used.

## New personal upgrades

The six abilities and two supporting stats each have three ranks. Existing twelve upgrades retain their IDs, indices and effects. New indices 12–23 are stable across TV, phone and results.

| Upgrade | Eligibility | Initial tuning |
| --- | --- | --- |
| Ricochet Bolt | Ranger | After pierce exhaustion, redirect toward an unhit enemy within 260 units; 1/2/3 redirects, each retaining 75% of the previous hit damage. No repeated hit on the same enemy identity. |
| Sentinel Blades | Warden | 2/3/4 blades orbit at 90 units, 12/16/20 base contact damage; each blade has a 0.6-second contact gate per enemy. |
| Consecrated Ground | Both | 11/15/19 base damage per half-second, 75/85/95 radius, 3/3.5/4-second base duration. Cast every 4/2/1.33 seconds with at most 1/2/3 fields per owner. |
| Returning Glaive | Both | 20/26/32 base damage, 280/320/360 outbound travel, 3.2-second cast cooldown. One active per owner, one hit per enemy identity on each leg. Returns toward its moving owner. |
| Soul Ward | Both | Absorb one damaging hit, recharge in 18/15/12 combat seconds. One charge; no additional resurrection or stacked shields. |
| Frostbound Strikes | Both | Primary attacks slow ordinary movement by 15/20/25% for 1/1.25/1.5 base seconds. |
| Swift Projectiles | Ranger, or an acquired Returning Glaive | +10% base projectile speed per rank, maximum +30%; preserve outbound range rather than extending travel with faster speed. |
| Lingering Power | Acquired ground fields, frost or Binding Circle | +15% base applicable duration per rank, maximum +45%. Does not alter Dodge, invulnerability, enemy warning clocks or choice windows. |

Secondary damage reads the existing equipment damage multiplier and routes actual damage/kill ownership through the normal hit system. Existing sustain limits remain authoritative. Frost and Stasis reduce movement only; committed dash/attack geometry and attack clocks are preserved. Elites receive half slow strength; bosses receive one quarter strength and half duration. Slows do not multiply.

## Evolutions

Each transformation is a one-time personal level-up choice. Eligible evolutions receive a protected rotating offer slot; there is no extra ceremony or random single-item recipe dependency.

| Evolution | Prerequisite | Effect |
| --- | --- | --- |
| Echo Storm | Chain Storm 3 | Weaker fixed-position repeat after 0.3 seconds, 45% of the original strike damage, 42-unit radius. |
| Binding Circle | Soul Circle 3 | Pulses apply a 20% ordinary movement slow for 1.1 base seconds, benefiting from Lingering Power and normal resistance. |
| Shatter Bolt | Ranger, three effective bolt pierces | The final collision makes a 65-unit burst at 45% bolt damage. Earlier collision victims are excluded. Equipment may help meet the prerequisite. |
| Reaping Stroke | Warden, capped Wide Sweep | A delayed weaker cone from the committed attack position/direction, 40% primary damage after 0.18 seconds. Visual remains compact at the weapon. |

Echoes, shatters and follow-ups cannot recursively create further evolutions. Delayed damage has its own bounded queue and retains its owner's identity.

## World relics

Soul Harvest attracts only existing loose XP souls. Shared XP is awarded once on actual collection, with its normal bounded queued level-ups. Healing and discoverable equipment chests remain stationary. If its collector is removed or downed, attracted souls fall back to the nearest living ally.

Stasis slows enemies present at collection by 50% for three combat seconds before elite/boss resistance. It does not slow the arena clock, incoming projectiles, new spawns or committed attack windups. Weaker slow sources must not prolong a stronger Stasis effect.

Five relic appearances alternate Soul Harvest/Stasis at 13%, 32%, 51%, 70% and 89% of the authored timeline. Placement uses a separate bounded RNG stream near a living player and avoids enemies, landmarks, chests and other relics. Four dedicated persistent slots prevent competition with XP/healing/chests; a full pool defers the next placement. Appearance grants no XP, equipment, pause or choice. Pickup gives a short existing audio cue and one compact notification.

## Offers, co-op and presentation

The opening levels expose class traits, Soul Circle, Chain Storm and all new eligible abilities. Subsequent deterministic weighted offers favour developing acquired skills while retaining discovery. Supporting stats appear only when useful; capped/unsupported cards are excluded and each offer contains three distinct choices. Timeout still selects current option zero. No reroll actions, charges or buttons exist.

Fresh late joiners receive class foundation picks up to the current party level (at most nineteen), including acquired prerequisites before evolved choices, with no inherited timers or phone review. Reconnect preserves the existing build, offer identity and countdown. Combat-owned fields, blades, projectiles, slows, relic motion and delayed hits freeze during skill/loot choices, manual pause, disconnect and backgrounding.

Character Info has remote-controlled pages; Run Details retains four entries per player per page with every effect described. The five-emblem HUD prioritizes class traits/evolutions and retains actual ranks plus unique overflow. Friendly fields/status markers draw before hostile warnings and the complete actor pass. Weapons and relics join the existing depth sort. Both cameras render the same simulation coordinates and clocks.

## Resources and verification

Fixed new capacities: eight orbiters, six fields, two glaives, four relics and twenty-four delayed strikes. Each owner has reserved orbiter/field/glaive capacity. Existing enemy, projectile, hazard and pickup limits remain bounded. No frame-loop allocations or per-camera simulation are added.

The skill atlas grows from 512×384 to 512×512 RGBA: +262,144 GPU bytes. Original twelve 128×128 cells remain unchanged; twelve new skill glyphs and four world glyphs occupy sixteen 64×64 cells in the lower strip. The managed file-backed upload retains no decoded Pixmap. Expected combined residency with the maximum 360×360 QR is 33,483,596 bytes (31.93 MiB), below 32 MiB. Native startup accounting is the authority for the actual launched build.

Profile-only `4` opens a brief expanded-combat fixture without a survival fast-forward. It is not a normal run build or progression path.

Focused checks cover all new mechanics/evolutions, overlapping slow sources, pool reuse, global choice freezes, late join and reconnect, offer eligibility, atlas cropping, phone acknowledgments and affected existing combat/equipment/chest flows. Android/desktop compilation and a short rendering check complete technical validation. Build variety, difficulty, boss duration and sustained physical-TV frame rate still require user playtesting.

2026-11-25 verification: the final expansion/progression/HUD/results/art group passed 34 tests. Earlier affected combat, choice pause, loot, late-join, equipment, chest, audio and lobby/server groups also passed. `node scripts/test-controller-equipment.js` passed the new mixed-size icon/evolution checks and existing two-thumb/acknowledgment/pause/reconnect checks. JDK 21 built desktop classes and the debug Android APK. A temporary native renderer harness captured solo and split-screen fixtures after 48 combat ticks; both returned GL error zero (33/62 draw calls in this fixture). These short observations are not sustained performance or balance measurements. The pre-existing emulator host/relay configuration was left unchanged; its saved host belongs to a different LAN, so phone-to-emulator connectivity was not claimed.
