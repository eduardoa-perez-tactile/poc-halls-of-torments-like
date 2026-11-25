package com.couchcrypt.ui;

import com.couchcrypt.game.ArenaWorld;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ResultsSummaryViewTest {
    @Test void outcomesDistinguishMiniBossProgressFromFinalBossVictory() {
        ArenaWorld world = new ArenaWorld(); world.activatePlayer(0);
        world.outcome = ArenaWorld.Outcome.DEFEAT;
        assertEquals("THE CRYPT CLAIMS ANOTHER SOUL", ResultsSummaryView.outcomeLine(world));
        world.bell = world.enemies[2]; world.bellSpawned = true;
        world.bell.maxHealth = 1000; world.bell.health = 625;
        assertEquals("REACHED THE MOURNING BELL  /  63% HEALTH REMAINING", ResultsSummaryView.outcomeLine(world));
        world.bellDefeated = true;
        assertEquals("THE MOURNING BELL FELL  /  THE SOVEREIGN AWAITS", ResultsSummaryView.outcomeLine(world));
        world.sovereign = world.enemies[0]; world.sovereignSpawned = true;
        world.sovereign.maxHealth = 1000; world.sovereign.health = 540;
        assertEquals("REACHED THE SOVEREIGN  /  54% HEALTH REMAINING", ResultsSummaryView.outcomeLine(world));
        world.sovereignDefeated = true;
        assertEquals("THE SOVEREIGN FELL  /  THE GRAVE KING AWAITS", ResultsSummaryView.outcomeLine(world));
        world.boss = world.enemies[1]; world.bossSpawned = true;
        world.boss.maxHealth = 8200; world.boss.health = 984;
        assertEquals("REACHED THE GRAVE KING  /  12% HEALTH REMAINING", ResultsSummaryView.outcomeLine(world));
        world.outcome = ArenaWorld.Outcome.VICTORY;
        assertEquals("THE GRAVE KING HAS FALLEN", ResultsSummaryView.outcomeLine(world));
    }
}
