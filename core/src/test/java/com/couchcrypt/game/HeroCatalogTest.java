package com.couchcrypt.game;

import java.util.List;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static com.couchcrypt.game.PartyProgression.Upgrade.*;

class HeroCatalogTest {
    @Test void listsEveryLearnableSkillInStableOrderWithoutOtherClassTraits() {
        assertEquals(List.of(POWER, HASTE, FLEET, VITALITY, EVASION, SWEEP, AURA, STORM, MAGNET, CRITICAL),
            HeroCatalog.profile(ArenaWorld.Hero.WARDEN).skills());
        assertEquals(List.of(POWER, HASTE, FLEET, VITALITY, EVASION, MULTISHOT, PIERCE, AURA, STORM, MAGNET, CRITICAL),
            HeroCatalog.profile(ArenaWorld.Hero.RANGER).skills());
    }

    @Test void catalogIsImmutableAndDoesNotShrinkWhenRunStatsReachCaps() {
        HeroCatalog.Profile profile = HeroCatalog.profile(ArenaWorld.Hero.WARDEN);
        assertThrows(UnsupportedOperationException.class, () -> profile.skills().clear());
        ArenaWorld world = new ArenaWorld(); world.activatePlayer(0);
        world.players[0].damageMultiplier = 4; world.players[0].auraLevel = 3;
        assertSame(profile, HeroCatalog.profile(world.players[0].hero));
        assertTrue(profile.skills().contains(POWER));
        assertTrue(profile.skills().contains(AURA));
    }

    @Test void displayedBaseStatsDriveFreshCharactersMovementAttacksAndDodge() {
        for (ArenaWorld.Hero hero : ArenaWorld.Hero.values()) {
            ArenaWorld world = new ArenaWorld(); world.setHero(0, hero); world.reset(); world.activatePlayer(0);
            ArenaWorld.Player player = world.players[0];
            assertEquals(hero.baseHealth, player.health);
            world.setInput(0, 1, 0, false); world.update(ArenaWorld.STEP);
            assertEquals(hero.moveSpeed * ArenaWorld.STEP, player.x, .0001f);
            // A target inside either weapon's range triggers exactly one fresh attack.
            ArenaWorld.Enemy enemy = world.enemies[0];
            enemy.active = true; enemy.x = 80; enemy.health = enemy.maxHealth = 100; enemy.attackCooldown = 10;
            world.activeEnemies = 1; world.clearInputs(); world.update(ArenaWorld.STEP);
            assertEquals(hero.attackInterval, player.attackCooldown, .0001f);
            if (hero == ArenaWorld.Hero.WARDEN) assertEquals(hero.baseDamage, 100 - enemy.health, .0001f);
            else assertEquals(hero.baseDamage, world.bolts[0].damage, .0001f);
            world.setInput(0, 1, 0, true); world.update(ArenaWorld.STEP);
            assertEquals(hero.dodgeCooldown, player.dodgeCooldown, .0001f);
        }
    }
}
