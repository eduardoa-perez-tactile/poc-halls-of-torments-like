# Enemy variants

The later approved [size and rank hierarchy](enemy-size-hierarchy.md) adds shared
body/footprint sizing, a bone chevron for durable Moss/Dusk variants, and a crimson
double down-chevron with live health for elites. These cues replace the old small body scales
and violet elite aura. The following variant statistics and material palettes
remain unchanged.

The 24 November variants reuse all existing enemy frames and attacks. Original
enemies retain their base stats. Ghoul/Brute also spawn as Moss or Frost;
Wraith/Cultist also spawn as Ember or Dusk. All bosses stay Original.

## Staged introductions

The 26 November learning-curve correction replaces immediate, equally weighted
variant availability in the twenty-minute run. Learn original species first;
each alternate begins with a 5% per-species spawn share at its unlock time and
ramps to 20% over ninety combat seconds. Independent buckets keep already
introduced variants stable when the next one unlocks. Original enemies remain
at least 60% of each species even after both alternates mature.

| Combat time | Newly eligible variant |
| --- | --- |
| 0:00–4:00 | Original enemies only |
| 4:00 | Moss Ghoul |
| 6:00 | Frost Ghoul |
| 8:00 | Ember Wraith |
| 10:00 | Dusk Wraith |
| 11:00 | Moss Brute |
| 13:00 | Frost Brute |
| 14:00 | Ember Cultist |
| 16:00 | Dusk Cultist |

These are eligibility times, not guaranteed appearances: the active authored
pack must include that species, and safe placement/caps still apply. Elites use
the same gates. Existing enemies keep their spawn identity and stats. Eligibility
uses elapsed combat time, so choices, manual pause, background and disconnect
cannot advance it. Selection uses a separate deterministic spawn hash with no
extra combat RNG draws, resources or frame-loop allocations. Timelines shorter
than twenty minutes retain their original immediate variant mix, and explicit
art-review lineups can still request every identity at any time.

The [encounter schedule](twenty-minute-encounters.md) also reduces opening
density and makes early Wraiths a minority. Variant stat modifiers stay unchanged.

| Variant | Health | Movement speed | Damage |
| --- | ---: | ---: | ---: |
| Original | unchanged | unchanged | unchanged |
| Moss | +40% | -15% | unchanged |
| Frost | -20% | +25% | unchanged |
| Ember | -15% | unchanged | +25% |
| Dusk | +15% | +10% | -15% |

Multipliers apply once at spawn on top of phase, co-op and elite scaling. A reused
pool slot starts from its new kind's base values. Movement modifiers affect
pursuit/retreat, preserving attack windups, cadence, dash distances/timing and
projectile speeds. Damage applies to real contact/strike damage and is captured
by Cultist projectiles, surviving caster death/reuse. Base soul rewards and arrival checkpoints remain unchanged. The later
[difficulty pass](twenty-minute-encounters.md) strengthens ordinary phase stats,
movement and reinforcement limits while keeping these variant modifiers intact. These are initial balance values for user
playtesting, not a proven twenty-minute difficulty curve.

## Material palettes and resources

The user approved the refined treatment on 24 November. Frost uses pale cold skin
instead of a dark cyan wash; Dusk changes purple cloth to blue while retaining
bone, skin and gold contrast. Source hue/luminance masks approximate materials,
rather than using new painted silhouettes or authored per-pixel material masks.
Moss/Ember keep their existing tints. Damage flashes temporarily use the original
body paint; Wraith ghosts keep their variant and existing opacity.

`EnemyPalette` uses one arena SpriteBatch shader with a single texture sample.
Cached TextureRegion wrappers encode Frost/Dusk as U+4/U+6; the vertex shader
restores source UVs before interpolation/sampling. This avoids per-enemy shader or
uniform switches and adds no palette draw calls, textures or retained Pixmaps.
Ordinary regions use the original sample-times-vertex-colour path. Wrappers are
bounded startup allocations; the shader is managed and explicitly disposed.

Attack warnings now have bright ivory/gold cores and dark edges: boss hazard
boundary/countdown rings, the exact Brute slam radius, Wraith lane runes and the
Cultist late target cue. Warning times, damage geometry and strike/recovery colours
are preserved. Their extra outline quads reuse existing textures and batching.

## Checks

The 26 November onboarding change passed 62 focused tests covering variant
eligibility/distribution, real spawn initialization, frozen choice clocks,
explicit art-review identities, timeline pressure, boss arrivals, discovered
chests and late joining. JDK 21 desktop compilation and Android debug assembly
passed. No device installation or gameplay playthrough was performed for this
change; opening survival and first-upgrade pacing need user playtesting.

Earlier art/material verification:

JDK 21 desktop compilation, Android debug assembly and 39 focused tests passed.
Tests cover spawn composition, actual movement/damage, caster reuse, enemy pool
reuse/reset, pause/reconnect, timelines and existing attacks/feedback. Five stale
timeline fixtures were reproduced against the pre-change world, then repaired to
resolve legitimate pending choices through the existing APIs; assertions stayed
unchanged. Desktop shader captures checked all four authored facings and source
UV/dimension preservation across the atlas. Local media/logs are ignored under
`.run/enemy-material-*`, `.run/enemy-variant-stats-*` and `.run/enemy-variant-review/`.

A five-second Android 13/API 33 `Television_1080p` co-op art fixture rendered the
palette shader successfully, with the same 32,467,788 accounted texture bytes
(30.96 MiB including QR) and zero retained gameplay Pixmaps. Its associated
300-interval report had p95/p99/max 18.56/19.34/20.85 ms and 143 intervals over
16.7 ms; log delivery trailed the end marker. Short attack-fixture screenshots
also confirmed the Brute/Wraith and boss warnings against the floor. These checks
do not prove balance, sustained physical-TV 60 FPS or worst-case GPU performance.
