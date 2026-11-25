# Run equipment: references and scope

The Halls of Torment wiki inspired six equipment categories (helmet, amulet, ring, chest, boots and gloves), two ring slots, personal loot choices and distinct offense/defense/mobility combinations. Its [item overview](https://hot.fandom.com/wiki/Item) describes equipment separately from permanent retrieval, bottles, marks and artifacts. Couch Crypt implements current-run equipment only.

Indexed wiki material was available through search; direct Fandom page fetches returned HTTP 402/restriction errors. The [chest equipment page](https://hot.fandom.com/wiki/Chest) informed health/defense tradeoffs, the [ring page](https://hot.fandom.com/wiki/Ring) informed crit and movement/stationary build choices, and the [gloves page](https://hot.fandom.com/wiki/Gloves) informed attack speed, pickup reach and retaliation roles. The [boots page](https://hot.fandom.com/wiki/Boots) informed movement-dependent offense; the [amulet page](https://hot.fandom.com/wiki/Amulet) informed recovery and low-health risk/reward. These are mechanics references, not exact reproductions or an exhaustive port of the source game's catalog.

## Implemented catalog

`ItemCatalog` contains 48 original items: eight alternatives in each category. All 23 represented stats have runtime effects. Most items combine one or two modifiers; a few deliberate tradeoffs use three. Weapon-specific gear is filtered to Warden sword reach or Ranger arrows/piercing. Ability damage gear names the affected Soul Circle or Chain Storm explicitly. Descriptions are derived once from the same modifier data used by the simulation.

Each item has a new original illustration in a bounded shared equipment atlas. Existing skill emblems, menus, fonts and other artwork remain unchanged. The legacy effect-category icon index remains available for compatibility; `artIndex` identifies the item's own illustration. Names, descriptions, values and combinations are original Couch Crypt data. No source game text or artwork was copied. See [item artwork](items-art.md) for generation prompts and provenance.

Summons, elemental status conversions, gold economies, potion inventories, permanent retrieval, equipment unlock quests, marks and artifacts require systems outside the current arena and are omitted rather than represented by inactive items. There are no rarity variants or persistent inventory in this pass.

## Earning and choosing gear

Equipment is separate from XP levels. The party earns one personal choice for each
of up to five collected elite chests, plus automatic timed reliquaries at 1:30,
10:00 and 19:00 of the normal twenty-minute combat timeline. Defeating an elite
leaves a stationary chest at its death position; either living player can walk
over it to unlock that reward for the whole party. Chests have a brief landing
delay, do not expire or attract toward players, and do not use the soul/healing
pickup pool. Elite souls and healing still drop immediately. There are at most eight rewards per run.
Skill choices take priority; earned gear waits behind them, so separate rewards
can appear consecutively. The TV and phone name the original source of each round.
This describes the rules, not the unrecorded cause of a particular playtest reward.

Each phone offers **two** items with their illustrations and current → next bonuses.
Pick one to equip its slot or replace the item already there, or Keep Current Gear
to decline that reward. There are seven positions, including two rings. Combat
pauses for the shared 30-second round; timeout picks the first option. Gear lasts
for the current run only. XP skill upgrades retain three choices.

## Balance envelope

Individual bonuses are generally 3–25%, 5–30 health, 0.15–0.35 health/second or 7–12 retaliation damage. Life-steal items grant 0.8–1% before runtime caps. XP gear is modest (3–6%) so it improves collection builds without replacing the rising level curve. More arrows, broader reach and piercing remain class-specific and respect the existing projectile pools/caps.

A conservative static analysis independently taking the best item in each of seven slots (even allowing a duplicate best ring) stays below +80% damage, +80% attack speed, +120 health, 35% damage reduction, 1.5 health/second, 3% life steal, 20% XP and 35 percentage points of critical chance. These maxima cannot all occur in the same loadout. Runtime clamps provide a separate final bound; health, moving and stationary conditional bonuses are not always active.

This numerical envelope and short combat fixtures support an initial tuning pass, not a claim of complete balance. Human playtesting still needs to assess recovery combinations, stationary damage risk and high-damage ability builds across the full run.

## Elite chest verification (2026-11-25)

62 focused Java tests passed across chest collection, equipment, skill/loot pauses,
late joining, arena combat and atlas metadata. JDK 21 compiled desktop classes and
assembled the Android debug APK. A brief hidden native desktop render checked
solo/co-op chest placement and then Player 2 collecting one chest, creating both
personal offers and freezing combat. This was a small fixture, not a survival or
physical-TV performance test. The existing TV emulator/phone relay was left alone.

The original chest uses spare space in the resident Grave King page. All earlier
atlas metadata and pixels outside the new sprite/gutter patch were compared
against Git; page sizes and GPU texture use are unchanged. The APK contains the
updated atlas. Art provenance is in `art-source/elite-loot-chest/README.md`.
