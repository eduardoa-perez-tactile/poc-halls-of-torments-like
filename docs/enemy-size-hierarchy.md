# Enemy size and threat hierarchy

The user approved the existing-art proposal on 24 November 2026 and requested
implementation with aligned hitboxes and attacks. Approval captures are preserved
in `art-source/enemy-size-hierarchy/`; no enemy PNG, palette, source pose or texture
page was regenerated.

## Appearance

`EnemySizing` uses the resident SE opaque body heights rather than padded canvas
sizes. Warden's reference height is 103.75 world units. Normal Ghoul/Wraith/Cultist/
Brute bodies are 92.34/99.60/105.83/140.06 units, corresponding to 89/96/102/135% of
that reference. All four authored facings retain their source proportions and
ground pivots. Idle, movement and attack poses use the same multiplier.

Moss gains 10% cosmetic bulk. Elites gain 22% over the normal species, replacing
Moss bulk instead of stacking it. Dusk/Frost/Ember retain species size. The boss
scale remains 2.2×. Existing health, damage, speed, XP, rewards, attack cadence and
warning durations are preserved; these changes do not establish a harder run.

Normal enemies have no rank marker. Durable Moss/Dusk enemies have one outlined
bone down-chevron. The user's follow-up rejected both crown treatments and chose
the down-arrow language. Elites use two stacked crimson down-chevrons plus a live
crimson health bar, replacing the durable chevron. The former violet elite foot
aura is removed. Overhead cues follow stable body bounds and draw above the
complete actor pass; existing ground warnings/windup cues draw below it. Brute
rank cues make a small smooth rise with the raised-arm pose instead of crossing
the head. The count as well as colour distinguishes the two marked tiers.

The current chevron and health bar use the resident white texel, with no new
textures, retained Pixmaps, frame-loop allocations or layout rebuilds. The
separately approved atmospheric iris and HUD remain in place.

## Contact and attacks

The same factor drives conservative circular ground footprints. Normal radii
are approximately 29.31 Ghoul, 21.25 Wraith, 20.16 Cultist and 42.96 Brute. Thin Wraith/
Cultist bodies use narrower source bounds so extra height cannot create broad
invisible contacts. Moss/elite footprint factors follow their body factors once.
Boss ground radius remains 54. Spawn safety and arena clamping use the final radius.

Brute warning and damage centres project the original measured fist anchors with
the shared size and locked facing. The 55-unit strike radius and its 750 ms windup
remain unchanged. Swept Wraith, bolt and hostile-shot checks retain their existing
ground-plane collision model.

Released hostile shots snapshot caster kind, variant and size, so turning, death
or enemy-pool reuse cannot detach a missile from its launch pose. Missile art and
velocity retain their own scale and timing. Impact effects likewise snapshot the
struck enemy's variant so body-level feedback retains the correct lift.

## Focused checks

Tests compare the actual resident body widths/heights and four-facing Brute fist
metadata with shared geometry. Simulation checks exercise inside/outside swept
bolt and contact boundaries, variant/elite pool reuse and released-shot/impact
identity. Existing attack timing, Dodge, choice-pause, animation and variant-stat
checks remain part of the affected verification.

Profile-only keys 1/2 (or media Next/Previous for ADB without keyboard input)
provide a quiet four-facing size lineup and a real elite Cultist cast fixture.
They are unavailable in normal play. Native evidence and
build outputs stay ignored under `.run/enemy-size-hierarchy/`.

The JDK 21 focused run passed 43 tests across sizing, swept/contact boundaries,
Brute geometry, enemy attacks/art/variants, animation, projectile art and complete
arena combat integration. Desktop classes and Android debug assembly passed.
Brief native captures checked all four lineup facings in solo/co-op, real elite
Cultist release/approach/contact in all four facings and close Brute warning/impact.
All existing runtime art PNGs were byte-identical to the starting commit.

A short moving co-op boss fixture reported 31.68 MiB combined GPU texture use,
with zero retained gameplay Pixmaps. Its 301-frame report straddles the script's
measurement markers: interval p95/p99 were 18.64/19.97 ms and render-callback wall
p95 was 11.66 ms; GPU duration was unmeasured. This is brief emulator evidence,
not proof of sustained physical-TV 60 FPS or difficulty balance. Native checks
included the separate approved outer-TV iris correction. The final chevron revision
was rebuilt for desktop/Android and rechecked natively in solo/co-op lineups and
the raised-arm Brute attack. Existing PNGs remain unchanged.
