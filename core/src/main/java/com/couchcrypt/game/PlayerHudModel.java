package com.couchcrypt.game;

import java.util.Arrays;

/** Reusable, bounded presentation of a participant's acquired build. */
public final class PlayerHudModel {
    public static final int MAX_SKILLS = 5;
    private static final PartyProgression.Upgrade[] UPGRADES = PartyProgression.Upgrade.values();
    private static final PartyProgression.Upgrade[] WEAPON_TRAITS = {
        PartyProgression.Upgrade.SWEEP, PartyProgression.Upgrade.MULTISHOT, PartyProgression.Upgrade.PIERCE,
        PartyProgression.Upgrade.ECHO_STORM, PartyProgression.Upgrade.BINDING_CIRCLE,
        PartyProgression.Upgrade.SHATTER_BOLT, PartyProgression.Upgrade.REAPING_STROKE,
        PartyProgression.Upgrade.RICOCHET, PartyProgression.Upgrade.SENTINEL
    };

    public final int[] iconIndices = new int[MAX_SKILLS];
    public final int[] ranks = new int[MAX_SKILLS];
    public int count, overflow;

    public PlayerHudModel() { clear(); }

    /** No allocations. Weapon traits lead, followed by other owned skills in catalog order. */
    public void refresh(ArenaWorld world, int slot) {
        clear();
        if (slot < 0 || slot >= world.players.length || !world.players[slot].active) return;
        for (PartyProgression.Upgrade upgrade : WEAPON_TRAITS) append(world, slot, upgrade);
        for (PartyProgression.Upgrade upgrade : UPGRADES) {
            boolean prioritized = false;
            for (PartyProgression.Upgrade trait : WEAPON_TRAITS) if (upgrade == trait) { prioritized = true; break; }
            if (!prioritized) append(world, slot, upgrade);
        }
    }

    private void append(ArenaWorld world, int slot, PartyProgression.Upgrade upgrade) {
        int rank = world.progression.rank(slot, upgrade);
        if (rank <= 0 || !upgrade.supports(world.players[slot].hero)) return;
        if (count == MAX_SKILLS) { overflow++; return; }
        iconIndices[count] = upgrade.ordinal();
        ranks[count++] = rank;
    }

    private void clear() {
        count = overflow = 0;
        Arrays.fill(iconIndices, -1);
        Arrays.fill(ranks, 0);
    }
}
