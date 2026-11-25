# Equipment balance and unchanged sustain

Equipment is run-only: six categories occupy seven positions, including two rings. Five collectible elite chests and three discoverable arena chests create at most eight personal item choices per run. The arena chests appear at random locations at 1:30, 10:00 and 19:00; only collection earns gear, with no free timed offers. Each loot round offers two items with a stored elite/discovered-chest source label (skills remain three choices) and shares one thirty-second host clock and timeout default; early finishers wait and queued rounds receive fresh windows. Skill choices take priority and freeze any hidden equipment countdown; equipment selection pauses combat and the arena clock, with a LOOT FOUND / CHECK YOUR PHONE TV ceremony. Keep Current Gear consumes the current loot choice without changing equipment; timeout still equips the first offer. New late joiners receive all earlier skill ranks and gear rewards as automatic defaults, with no inherited choice backlog. Future rewards retain normal 30-second personal choices. Reconnecting participants keep their existing build and pending choices.

Equipment modifiers are separate from skill ranks. Replacing an item removes its complete contribution without losing acquired skills or granting a heal. Cards compare all old and new equipment contributions, including penalties; they do not claim those contributions bypass aggregate caps. Skill previews include unconditional equipment effects while conditional low-health/moving/stationary effects are described by their item. Class-specific and unavailable-ability items are filtered; arrow/pierce choices with no remaining effective capacity are excluded.

The later approved arena difficulty pass supersedes the incidental health-drop
values below: ordinary drops are 16 HP/14 kills before minute three and 12 HP/20 kills
afterward, with 18/12-second solo/co-op generation limits, a three/four-drop floor
cap and 35/45-second regular/elite expiry. Elite recovery is 24 HP. Healing cannot
merge into a stored reserve. Equipment stats and sustain below remain unchanged.
See [current encounter tuning](twenty-minute-encounters.md).

## Analytical comparisons

These are static arithmetic checks, not simulated run outcomes. Representative skill profiles at levels 5 / 12 / 18 use damage multipliers 1.5 / 2 / 2.5, attack-rate multipliers 1.15 / 1.45 / 1.75, critical chances 0 / 10 / 20%, and skill health 100 / 120 / 120. The level-12 profile has Soul Circle I / Chain Storm I; level 18 has Soul Circle II / Chain Storm I. Equipment examples use two / four / seven occupied positions to expose early and late growth. Exact attainable builds depend on offers and choices.

| Comparison against the same skills without equipment | Level 5 | Level 12 | Level 18 |
| --- | ---: | ---: | ---: |
| Offense main-weapon DPS, moving | 1.30x | 1.53x | 1.75x |
| Offense main-weapon DPS, stationary | 1.30x | 1.79x | 2.15x |
| Defense expected raw effective-health ratio | 1.79x | 1.97x | 2.25x |
| Ability gear, combined single-target Circle/Storm DPS | — | 1.48x | 1.68x |

Offense starts with Executioner Mask / Quickpulse Loop; adds Swiftstitch Gloves / Stillwater Medallion; then Rustbound Signet / Ravenweave Coat / Rooted Soles. The late set loses 10 health and its strongest damage requires standing still. Defense starts with Gatekeeper Helm / Cryptguard Plate; adds Emberheart Pendant / Ironheel Boots; then Pale Root Ring / Gravebloom Band / Barbed Fists. Its late set grants 75 health, 24% reduction, 5% block and 0.45 health/s regeneration, with movement penalties. Expected effective health divides health by `(1 - reduction) * (1 - block)`; random blocks, dodge, contact timing and healing are not a survival guarantee. Ability gear uses Ashen Circlet / Bell of Ashes / Choirweave Robe / Stormcaller's Touch, then Rustbound Signet / Quickpulse Loop / Duelrunner Boots. Main-weapon DPS excludes cleave, additional arrows, piercing and secondary attacks; the expected critical factor is `1 + chance * (critical multiplier - 1)`.

## Resulting tuning

- Combined flat plus conditional equipment damage is capped at +60%, reduced from the initial +80% ceiling after the stationary set reached about 2.42x weapon DPS. Other ceilings include +50% attack rate, +40% movement, +120 health, +30% dodge reduction with a 0.45-second minimum cooldown, 40% damage reduction, 20% block, 75% total critical chance, and 2.75x critical damage. Friendly arrows remain at five per attack with three piercing targets; every entity pool is unchanged.
- Regeneration caps at 1.5 health/s. Life steal and kill healing share one per-player budget replenished at two health/s (maximum reserve two); life steal also caps at 0.8 per hit and 2.5% damage. Contact retaliation cannot trigger life steal recursively, and no item healing revives a fallen player. XP gain caps at +25% and carries fractional awards; souls remain counted once, with bonus XP shared by the party.
- Ordinary health generation now uses 16 HP per 14 kills before minute three and 12 HP per 20 kills afterward, subject to 18/12-second solo/co-op cooldowns. Elite recovery is 24 HP. Floor caps, combat-time expiry and nonmerging values prevent stockpiling. See the current encounter document for the complete rules.
- Ordinary enemies now use the approved arena difficulty curve; equipment modifiers and skill progression remain unchanged. Grave King health remains 8,200 solo / 13,120 co-op. Hollow Sovereign is now 5,400 / 8,640 with its revised delayed volley. The XP curve and level-20 cap remain unchanged; bonus XP can bring efficient builds to the cap earlier.


Focused checks cover reversible modifiers, accurate upgraded health previews, class/ability eligibility, queues and timeout priority, late joining/reset IDs, damage/cadence/arrows, secondary attacks, attraction/reach, critical hits, contact retaliation, conditional power, regeneration, sustain limits, reward boundaries and late healing. No full twenty-minute simulation or gameplay balance claim is made. Human feedback and physical-TV measurements remain necessary for feel and sustained performance.
