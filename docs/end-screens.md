# End-of-run presentation

The November 24 polish uses the approved Warden and Ranger portrait regions from
character selection throughout Victory, Defeat, Skills and Equipment. It reuses
the shared crypt, outcome titles, fonts, button skins and icon atlases; it adds no
textures or retained decoded Pixmaps.

Victory and Defeat center the participating hero or pair above labeled run time,
party level and enemies slain. A restrained translucent charcoal band separates
those statistics from the stone steps. Defeat identifies reaching the Sovereign
and its remaining health when a boss encounter was reached. Replay, Return to
Lobby and Run Details are three painted controls in one row. Replay text follows
the connected party, explicitly warns when co-op would restart solo, and offers
a lobby reconnect route when no phones remain.

Run Details retains the unboxed character-and-build layout. Portraits have a
consistent 318-unit identity area across Skills, Equipment and pages. Four entries
per player per page preserve effect descriptions for full builds and give equipment
larger names, bonuses and icons. The remote turns both player columns together;
the page counter and disabled boundary controls show what remains. Switching
Skills/Equipment starts on page one and keeps focus on the switch. Back restores
Run Details focus on the summary. Solo emphasizes damage taken instead of revives.

Skill summaries show permanent effective values including equipped gear, with
conditional gear bonuses described in Equipment. Soul Circle and Chain Storm
include their damage and range/target/cadence values. Large personal totals use
grouped digits and fit their allocated statistic width. Phone result status carries
an additive outcome field, shows Victory or Defeat, keeps gameplay controls hidden
and leaves shared navigation with the remote. Leaving Results clears the outcome.

## Focused verification

JDK 21 desktop compilation and Android debug packaging passed. Focused Java checks
cover the result protocol, clearing outcomes, conditional stat semantics and
bounded pages for full late-join builds. The controller JavaScript harness covers
both outcomes and compatibility with hosts that omit the outcome field.

Native 1920x1080 desktop rendering checked solo/co-op results, every available
skill/equipment page, remote focus and page boundaries, Back restoration, a lost
companion and replay with no connected phones. Separate max-level fixtures cover
all three skill pages; native text measurement covered all 48 item descriptions.
The summary backing was visually checked in both outcomes. These checks do not
establish physical-TV viewing-distance readability, gameplay balance or sustained
60 FPS. No survival playthrough was run. Ignored captures are under
`.run/end-screen-verification/` and `.run/end-screen-details-check/`.
