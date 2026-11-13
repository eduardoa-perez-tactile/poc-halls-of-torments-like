package com.couchcrypt.game;

import java.util.Arrays;
import java.util.List;

/** Static character information; base stats and class eligibility come from the simulation. */
public final class HeroCatalog {
    public record Profile(ArenaWorld.Hero hero, String name, String role, String summary, String bio,
                          List<PartyProgression.Upgrade> skills) {
        public Profile { skills = List.copyOf(skills); }
    }

    private static final Profile WARDEN = new Profile(ArenaWorld.Hero.WARDEN, "Warden", "Melee / Crowd Control",
        "Sweeps a sword through nearby foes, knocking them back.",
        "Once sworn to guard the crypt's gates, the Warden now descends to silence what escaped them.",
        skills(ArenaWorld.Hero.WARDEN));
    private static final Profile RANGER = new Profile(ArenaWorld.Hero.RANGER, "Ranger", "Ranged / Mobility",
        "Fires arrows at nearby foes and moves swiftly to keep a distance.",
        "Following a trail of vanished souls, the Ranger hunts the darkness beneath the old kingdom.",
        skills(ArenaWorld.Hero.RANGER));

    private HeroCatalog() {}

    public static Profile profile(ArenaWorld.Hero hero) {
        return switch (hero) { case WARDEN -> WARDEN; case RANGER -> RANGER; };
    }

    private static List<PartyProgression.Upgrade> skills(ArenaWorld.Hero hero) {
        return Arrays.stream(PartyProgression.Upgrade.values()).filter(upgrade -> upgrade.supports(hero)).toList();
    }
}
