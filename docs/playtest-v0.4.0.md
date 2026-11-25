# Couch Crypt v0.4.0 playtest

Android TV development playtest, package `com.couchcrypt.tv`, version **0.4.0**, Android version code **4**, minimum Android 8.0 / API 26. The debug-signed APK upgrades the existing installation without clearing its preferences.

This release includes the complete survivor expansion without rerolls: Ricochet Bolt, Sentinel Blades, Consecrated Ground, Returning Glaive, Soul Ward, Frostbound Strikes, Swift Projectiles, Lingering Power, four selectable evolutions, Soul Harvest and Stasis. Existing three-card phone selection, nineteen personal level picks, equipment, co-op, reconnect and late joining remain in place. It also carries the committed Dodge/combat-feedback and discoverable-chest improvements accumulated on develop since v0.3.0.

See [the skill reference](survivor-expansion.md) for exact effects and evolution prerequisites. The new artwork is original and the combined accounted texture budget remains below 32 MiB. The pending wall/gate repair in the local working tree is separate work and is not part of this release.

## Playtest

Open Play on the TV, connect phones to the same Wi-Fi as the computer hosting the emulator relay, and scan the lobby QR code. Each player chooses skills and equipment on their own phone. The remote controls shared menus. For physical TVs, use the TV's reachable LAN address instead of the emulator relay.

Try a Ranger piercing/ricochet build and a Warden Sentinel Blades build, develop a skill to unlock its evolution, and collect both relic types. Check readability when both players have several secondary attacks, the usefulness of the three offered choices, and whether bosses remain challenging.

The preceding implementation passed 34 focused expansion/progression/HUD/results/art tests, affected combat/co-op/protocol checks, phone-controller checks and short native solo/co-op rendering checks. These do not establish twenty-minute balance, boss duration or sustained physical-TV performance. User playtesting owns those assessments.
