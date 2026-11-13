package com.couchcrypt.game;

import java.util.Arrays;

/** Reusable, bounded presentation of a participant's acquired build. */
public final class PlayerHudModel {
    public static final int MAX_SKILLS = 5;
    private static final PartyProgression.Upgrade[] UPGRADES = PartyProgression.Upgrade.values();
    private static final PartyProgression.Upgrade[] WEAPON_TRAITS = {
        PartyProgression.Upgrade.SWEEP, PartyProgression.Upgrade.MULTISHOT, PartyProgression.Upgrade.PIERCE
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
            if (upgrade != PartyProgression.Upgrade.SWEEP && upgrade != PartyProgression.Upgrade.MULTISHOT
                && upgrade != PartyProgression.Upgrade.PIERCE) append(world, slot, upgrade);
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
