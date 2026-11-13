# Project working agreement

## Scope and product direction

- This is a LibGDX Android TV couch co-op game inspired by the arena-survival gameplay of Halls of Torment.
- Support solo play and two-player co-op in one shared world, with independent split-screen cameras when two players are present. Player 2 is optional and can join later through Invite Player 2 in the remote-controlled pause menu.
- Players join the TV lobby by scanning a QR code on phones connected to the same local network. Phones open a controller webpage; no companion app is required.
- The TV remote is the master controller for shared UI: navigation, character selection, starting, pause, restart, and returning to the lobby. Per the user's 2026-11-12 instruction, each player chooses their own level-up upgrade on their phone; phones otherwise provide gameplay controls and connection status, not shared menu navigation or Ready buttons.
- Target the medieval/isometric dark-fantasy direction of PVGames' Other Worlds: Core Tiles: https://pvgames.itch.io/other-worlds-core-tiles. The reference is inspiration. The user authorized original generated lobby artwork from the first build; keep its prompts and provenance in docs/art-direction.md.
- The UI flow is Home -> Lobby -> Session -> Results, with Pause -> Invite Player 2 available during a solo session. One connected phone enables Start Solo; two enable Start Co-op. Keep the QR area minimal: Scan to Join, the code, and Same Wi-Fi. There is one party per TV; internal session identifiers must not appear as room numbers or a room-entry requirement.
- Implemented design: vertical split cameras, shared XP/party level, nearby ally revival, and automatic pause on participating-controller disconnect. User-approved upgrade flow: both players level up together, each picks on their own phone within 30 seconds while combat continues; no forced level-up pause. Manual pause/background/disconnect freeze simulation and choice timers. Timeout applies the first offered upgrade.
- Current authorized milestone (2026-11-13): complete one replayable twenty-minute arena, using the Halls of Torment wiki as a mechanics reference. Include remote-selected Warden/Ranger heroes, distinct sword/bow weapons, class weapon traits and automatic secondary abilities, shared XP/phone choices, mixed enemy waves, elites, a telegraphed final boss, readable combat feedback, short sound cues, and results with individual statistics/builds. Twenty minutes of waves lead into the final encounter; victory requires killing the boss. Retain the short timeline as a development fixture. Keep solo/co-op, pause, reconnect, late joining, approved menus and compact HUD. Multiple arenas, persistent meta progression and monetization are not part of this one-arena milestone.
- The user approved the centered Home-menu concept on 2026-11-12: large original forged-gold Couch Crypt branding, torchlit ruins, carved stone controls (Play / How to Join / Settings), and a separate Exit. Build functional remote controls over separate art layers; do not use a flattened menu screenshot as the interface. Other major visual direction changes require design approval before implementation.
- Keep this visual theme consistent across Home, lobby, pause, dialogs, and phone controls. Buttons must have clearly distinct normal and focused/pressed states; use the matching painted gold state with shared crop/anchors so focus does not move the button artwork.
- Current authorized progression milestone: shared XP from souls, visible XP and level on each player HUD, three personal upgrade choices on each phone, independent 30-second timers, real stat effects, default picks on expiry, bounded queued level-ups and late-join catch-up. Persistent meta progression and additional dungeons remain future work; the final boss is authorized in the complete-arena milestone.
- Phone upgrade selection uses three illustrated dark-fantasy cards with a large skill emblem, category/next rank, exact effect headline, current → next values, and the host-owned 30-second timer. Portrait stacks three cards; landscape shows three columns. Combat continues while the choosing phone sends neutral input. Keep cards visible through selection acknowledgment, paused/invite/background states, and reconnect restoration; show the controller again only after the host clears the offer. This supersedes the earlier requirement to keep joystick/Dodge visible during choices.
- Character selection includes a remote-controlled Info action with a short summary, original brief bio, authoritative base stats, and every skill that hero can acquire. The reference list includes capped skills and is separate from current-run upgrade offers; preserve the character choice and restore focus when closing Info.
- Combat pacing uses authored swarm, brute-pressure and cultist-formation combinations with brief reinforcement breathers and scheduled elites. The normal session uses the twenty-minute timeline, with bounded early/middle/late pressure and progression tuned for that duration. The three-minute timeline remains a development fixture. Balance and sustained physical-TV performance require user playtesting; do not claim either is proven by short automated checks.
- Approved combat HUD (2026-11-13): compact lower-edge health orbs with forged gold/teal rims and adjacent health rails, shared XP progress/party level on each player HUD, up to five acquired skill emblems with actual current ranks and a +N unique-skill overflow count, plus Dodge readiness/cooldown. Weapon traits lead the skill row only when acquired; do not invent a base-weapon upgrade rank. Keep the playfield clear: no large panels, report boxes, portraits or repeated class names. Keep the conditional boss strip, one-line notifications and ally revival cues. Reuse the existing skill atlas; new code-native orb/rim/rail art is limited to one file-backed managed 512 x 256 RGBA atlas (0.5 MiB GPU, no retained decoded CPU pixmap), within the combined 32 MiB GPU budget. Phones retain the dedicated upgrade-card behavior above. The approved menu/dialog styling is unchanged; a full build view in Pause remains future work.
- Approved end screens (2026-11-13): the user approved both replacement art-led concepts: Victory opens the crypt toward dawn; Defeat shows the fallen party in a cold hall. Use separate scene/title layers with live summary and remote-controlled Play/Try Again, Return to Lobby, and Run Details. The user clarified that Run Details information/layout are fine; polish the presentation with larger hero art, balanced spacing and richer skill entries rather than replacing the information architecture. Keep detailed statistics/builds behind Run Details, using an unboxed character-and-build layout with hero illustrations and skill emblems; do not restore the rejected brown report-card panels. Prewarm shared titles/icons during startup, load only the active outcome background, dispose it on leaving results, and retain the combined 32 MiB texture budget.
- The user considers the first gameplay floor, actor animations, enemies, and props temporary: they do not yet match the desired isometric art direction. Focus on functionality now; revisit and approve the gameplay art in a later visual pass. Preserve the approved menu/icon direction.
- Treat 60 FPS (16.7 ms per frame) and low memory consumption as requirements. Keep texture dimensions bounded, reuse resources, avoid per-frame allocations and layout rebuilds, and keep network I/O off the render thread. Measure briefly and report limitations honestly; emulator results do not guarantee performance on physical TVs.

## Working process

- Work in the existing checkout. Never create or use Git worktrees or additional task checkouts for this project.
- Implement the agreed feature set first, then hand the build to the user for gameplay playtesting. User feedback drives the next iteration.
- Organize implementation into focused milestones without requiring user playtests between every internal step: project launchers, TV navigation/lobby, QR joining/reconnection, split-screen controller validation, combat, complete round, and art integration.
- Before an authorized milestone, briefly state its concrete outcome. Make routine implementation decisions independently; raise material changes to gameplay, controls, scope, or architecture before building around them.
- Provide concise progress updates and surface concrete blockers early.
- Keep changes focused. Avoid unrelated refactors, speculative systems, unnecessary frameworks, and generated clutter.
- When authorized and useful, maintain short current design documents under docs/ for game scope, UI flow, and architecture. Do not create documentation as a substitute for requested implementation.
- At handoff, explain what changed, what was actually checked, known limitations, and a short list of things for the user to try. Distinguish verified behavior from untested behavior.
- The user explicitly requests subagents for independent chunks of substantial work. Assign disjoint file ownership and integrate their results in the existing checkout. The primary agent owns branch switching, commits, merges, cleanup, and pushing; subagents must not create worktrees or switch branches.

## Testing rules

- Keep verification proportional to the change. Build/compile affected targets and use focused unit, protocol, or UI checks when they provide meaningful coverage.
- Do not run whole-game automated playthroughs, long survival sessions, exhaustive input exploration, or lengthy gameplay testing unless explicitly requested.
- The user leads gameplay playtests. Do not repeatedly interrupt implementation for broad playtesting.
- Keep full regression testing to a minimum. Limit checks to changed areas and directly affected integrations; broaden only for a concrete failure or material cross-cutting risk, and explain why.
- Do not write tests that simply mirror implementation, or add tests for trivial documentation or reversible cosmetic changes.
- Once relevant checks pass, continue toward delivery. Repeat them only after relevant changes, failures, or unresolved concerns.
- Use the TV emulator for brief launch, rendering, remote navigation, and connection checks when appropriate. A boot check during environment repair is sufficient; do not turn it into a gameplay test.
- Remove temporary files and stop temporary processes created for verification. Leave an emulator running only when it is useful for the requested work or user handoff, and state that it remains running.

## Gitflow and cleanup

- Use one branch at a time in this checkout. Preserve user changes; never overwrite, discard, or silently stash unrelated work to switch branches.
- main contains stable versions ready for user playtesting. develop is the integration branch for ongoing work.
- Create feature/<name> from develop. Make focused commits with descriptive messages, merge completed work into develop, and delete the merged feature branch.
- Create release/<version> from develop when release preparation is needed. Merge the completed release into main, tag the version, merge release changes back into develop, and delete the release branch.
- Create hotfix/<name> from main for fixes to a released version. Merge the fix into main and develop, create a new version tag, and delete the hotfix branch. Do not move existing release tags.
- Use merge commits for completed feature, release, and hotfix branches so the branch purpose remains visible in history.
- Delete local and remote branches created for this work once merged; only delete remote branches if they were actually pushed. Never delete user-owned or unmerged branches.
- Do not force-push, rewrite shared history, create worktrees, or leave abandoned task branches behind.
- Commit and push completed coherent milestones automatically. The user expects finished work to be visible on GitHub, not only in local commits. After merging a completed feature, push develop; publish main and version tags through the release/hotfix flow when a stable release is prepared.
- Branch merges do not automatically trigger full regression tests or gameplay playthroughs.

## Local environment

- Use JDK 21 explicitly for the project; the shell may default to JDK 27. Select it per command or project configuration without changing global shell settings unnecessarily.
- Android SDK on this machine: /Users/eduardoleale/Library/Android/sdk.
- Primary TV AVD: Television_1080p (1920x1080, ARM64). Inspect its current configuration rather than assuming an Android image version.
- Keep local SDK configuration, SDK downloads, emulator images, logs, screenshots, credentials, generated caches, and build outputs out of Git.
- Keep the one-arena game bounded; additional dungeons, advanced meta progression, persistent campaign saves, and monetization are outside this milestone. Temporary world art stays in use; the orb HUD direction was approved on 2026-11-13 and is implemented with live state and bounded shared art.
