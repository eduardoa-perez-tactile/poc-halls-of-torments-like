# Enemy attacks and magic crossbow

The approved Wraith, Brute and Cultist proofs are integrated into the simulation and renderer. The Ranger now fires animated arcane bolts from its existing crossbow. Cultist soulflame and Sovereign hellfire have separate painted eight-frame loops, with brighter heads, rune fragments and short trails. Projectiles retain launch-facing snapshots and rotate along projected world velocity; ground cues use the same 2:1 isometric plane as the tiles.

## Combat behavior

- **Wraith:** locks its aim, charges for 500 ms, dashes for 250 ms and recovers through 1300 ms. A swept crossing makes one damage attempt per player, including consuming a dodged crossing. It has no passive contact damage.
- **Brute:** warns for 750 ms over its actual 55-unit strike circle, slams once and recovers through 1450 ms. The marked centre uses the measured fist contact for the locked authored facing, including elite scale. It has no passive contact damage.
- **Cultist:** locks aim when its 700 ms cast starts, releases one shot and recovers stationary through 1440 ms. It no longer retargets a player during release.

The Wraith lane uses three separate low runes rather than a guide bar. Wind-up cues remain amber; active danger turns red. The later approved [enemy hierarchy](enemy-size-hierarchy.md) replaces the violet elite identity ring with two crimson overhead down-chevrons and a health bar. All ground cues draw before the complete actor pass. The compact player foot aura and Soul Circle pulse retain their separately approved sprites. Physical knockback translates an uncommitted warning with the enemy while retaining its aim; a Brute's committed strike stays where it landed. Damage, hit handling, Dodge invulnerability and player attack cadence remain simulation-owned. Attack, aura and missile ages freeze with combat during phone choices, manual pause and disconnect gates.

The three enemy attacks have distinct short cues; the crossbow release/contact now have a sharper arcane tone. The mixer still has at most six tracked voices and four starts per update. All 21 mono 22.05 kHz/16-bit cues total 236,146 PCM bytes. Native Android screenrecord does not capture this audio.

## Art and resources

Four separately authored facings preserve asymmetric costumes/staff placement; the approved southwest poses and exact shipped idle lockbacks remain selected. See [attack source/normalization details](enemy-attack-art.md) and [magic missile source/rebuild details](magic-projectiles.md). Whole-sheet art was made with built-in ImageGen; exact prompts, raw sources, source hashes and normalized frames live in the two tracked canonical ZIPs. Installed Python/Pillow perform deterministic exports. No tools, dependencies or art packs were installed.

Both supplemental banks borrow spare pixels in the existing 6 MiB actor/environment bank. Existing artwork and sampling gutters are byte-for-byte unchanged. No new textures or retained decoded Pixmaps are allocated; shared pages remain file-backed and managed. The accounted combined texture ceiling remains 32 MiB; the preceding border pass measured 31.8699455 MiB including live QR. These counts cover accounted textures, not all driver or process memory.

## Verification and preview

Focused checks cover attack aim, timing, swept crossing, one-hit masks, Dodge, knocked-back warnings, party-choice freezing, pool reset, all authored facings, aura/flight loops, source geometry, atlas nonoverlap and bounded audio routing. The intentional older Cultist live-retarget fixture now checks locked aim; a geometry check ties every Brute collision centre to its painted fist contact; an unrelated stunned-enemy velocity change caught by the tests was corrected. Desktop compilation and Android debug packaging use explicit JDK 21 and cached offline dependencies.

Independent archive-only supplemental rebuilds reproduce both metadata files and all three PNGs exactly, preserving all 227 original frame/gutter samples. Native preview evidence and APK verification are local under `.run/art-exploration/enemy-integration-v1/`. Profile-only F1 creates a short three-enemy close encounter; normal spawning never calls that fixture. The user owns balance and game-feel playtesting; no full arena survival run or sustained physical-TV 60 FPS claim is made.
