# Project working agreement

## Scope and product direction

- This is a LibGDX Android TV couch co-op game inspired by the arena-survival gameplay of Halls of Torment.
- Support two players in one shared world with independent split-screen cameras.
- Players join the TV lobby by scanning a QR code on phones connected to the same local network. Phones open a controller webpage; no companion app is required.
- The TV remote is the master controller for all shared UI: navigation, character selection, starting, pause, upgrades, restart, and returning to the lobby. Phones provide gameplay controls and connection status, not shared menu navigation or Ready buttons.
- Target the medieval/isometric dark-fantasy direction of PVGames' Other Worlds: Core Tiles: https://pvgames.itch.io/other-worlds-core-tiles. The reference is inspiration. The user authorized original generated lobby artwork from the first build; keep its prompts and provenance in docs/art-direction.md.
- The reviewed UI proposal is Home -> Lobby -> Objective/countdown -> Split-screen game -> Results. The lobby contains the QR code, two connection slots, character selection, and remote-controlled Start enabled when both phones are connected.
- Fixed vertical split, shared experience, sequential remote-selected upgrades for both players, revival, and automatic pause on disconnect are proposed design choices; confirm them through design review before treating them as final requirements.
- Current authorized milestone: implement the LibGDX foundation and lobby, TV remote navigation, and QR joining for two phones, with original art. Leave gameplay, movement controls, split-screen rendering, and combat for the next step.
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
- Gameplay is outside the current foundation/lobby milestone.
