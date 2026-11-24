package com.couchcrypt.game;

import com.couchcrypt.ui.RunDetailsView;

import com.couchcrypt.game.ArenaTimeline;
import com.couchcrypt.game.ArenaWorld;
import com.couchcrypt.game.ItemCatalog;
import com.couchcrypt.game.PartyProgression;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class RunDetailsPresentationTest {
    @Test void finishedBuildIgnoresTransientHealthAndMovementConditions() {
        ArenaWorld world = new ArenaWorld(ArenaTimeline.TWENTY_MINUTES);
        world.activatePlayer(0);
        var player = world.players[0];
        equip(world, "red_martyr_vest", 4);
        equip(world, "rooted_soles", 5);
        player.damageMultiplier = 1.5f;
        player.auraLevel = 2; player.stormLevel = 2;
        world.equipment.setMoving(player, true); player.health = world.equipment.maxHealth(player);
        String damage = RunDetailsView.skillEffect(world, player, PartyProgression.Upgrade.POWER);
        String aura = RunDetailsView.skillEffect(world, player, PartyProgression.Upgrade.AURA);
        String storm = RunDetailsView.skillEffect(world, player, PartyProgression.Upgrade.STORM);
        float liveDamage = world.equipment.damageMultiplier(player);
        world.equipment.setMoving(player, false); player.health = 1;
        assertTrue(world.equipment.damageMultiplier(player) > liveDamage);
        assertEquals(damage, RunDetailsView.skillEffect(world, player, PartyProgression.Upgrade.POWER));
        assertEquals(aura, RunDetailsView.skillEffect(world, player, PartyProgression.Upgrade.AURA));
        assertEquals(storm, RunDetailsView.skillEffect(world, player, PartyProgression.Upgrade.STORM));
        assertEquals("+50% attack damage", damage);
        assertEquals("15.0 damage / 0.7s; 140 radius", aura);
        assertEquals("33.0 damage; 3 targets / 2s", storm);
        equip(world, "marching_spurs", 5);
        player.attackRateMultiplier = 1.3f;
        world.equipment.setMoving(player, false);
        String attackRate = RunDetailsView.skillEffect(world, player, PartyProgression.Upgrade.HASTE);
        liveDamage = world.equipment.attackRate(player);
        world.equipment.setMoving(player, true);
        assertTrue(world.equipment.attackRate(player) > liveDamage);
        assertEquals(attackRate, RunDetailsView.skillEffect(world, player, PartyProgression.Upgrade.HASTE));
    }

    @Test void fullLateJoinBuildRemainsAvailableAcrossBoundedPages() {
        ArenaWorld world = new ArenaWorld(ArenaTimeline.TWENTY_MINUTES);
        world.progression.addXp(3000);
        world.activatePlayer(0);
        world.setHero(1, ArenaWorld.Hero.RANGER); world.activatePlayer(1);
        assertEquals(20, world.progression.level);
        int eligible = 0;
        for (var upgrade : PartyProgression.Upgrade.values())
            if (world.progression.rank(0, upgrade) > 0) eligible++;
        assertTrue(eligible > 4, "The completed run must exercise the old compact-build cutoff");
        assertEquals(3, RunDetailsView.pageCount(world, false));
        assertEquals(2, RunDetailsView.pageCount(world, true));
        world.deactivatePlayer(0);
        assertEquals(3, RunDetailsView.pageCount(world, false), "The remaining Ranger retains every acquired skill page");
    }

    private static void equip(ArenaWorld world, String id, int position) {
        world.equipment.equip(0, position, ItemCatalog.byId(id));
    }
}
