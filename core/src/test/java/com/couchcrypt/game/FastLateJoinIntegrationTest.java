package com.couchcrypt.game;

import com.couchcrypt.lobby.LobbyState;
import java.util.HashSet;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/** Direct joining/reconnection boundaries, without a survival simulation. */
class FastLateJoinIntegrationTest {
    @Test void newLateJoinersReceiveEveryInheritedEntitlementWithoutOpeningChoices() {
        for (ArenaWorld.Hero hero : ArenaWorld.Hero.values()) {
            for (int level : new int[] {1, 2, 10, PartyProgression.MAX_LEVEL}) {
                for (int rewards = 0; rewards <= RunEquipment.MAX_REWARDS; rewards++) {
                    ArenaWorld world = party(hero, level, rewards);
                    world.activatePlayer(1);
                    var skills = world.progression; var gear = world.equipment;
                    assertEquals(level - 1, skills.foundationCount(1));
                    assertEquals(level - 1, skills.appliedUpgradeCount(1));
                    assertEquals(rewards, gear.foundationCount(1));
                    assertEquals(rewards, gear.appliedCount(1));
                    assertFalse(skills.offers[1].pending); assertFalse(gear.offers[1].pending);
                    assertFalse(gear.choosing(1), "An inherited entitlement must never block the controller");
                    assertEquals(gear.maxHealth(world.players[1]), world.players[1].health);
                    assertEquals(level - 1, skills.foundationSkills(1).stream()
                        .mapToInt(PartyProgression.FoundationSkill::rank).sum());
                    for (var skill : skills.foundationSkills(1))
                        assertTrue(PartyProgression.Upgrade.values()[skill.icon()].supports(hero));
                    var items = new HashSet<ItemCatalog.Item>();
                    for (int position = 0; position < RunEquipment.SLOT_COUNT; position++) {
                        var item = gear.equipped(1, position);
                        if (item == null) continue;
                        assertTrue(item.supports(hero)); assertTrue(items.add(item), "Default gear must not duplicate an item");
                    }
                    assertEquals(Math.min(rewards, RunEquipment.SLOT_COUNT), items.size());
                    assertEquals(items.size(), gear.foundationItems(1).size(), "Summary represents the final worn build");
                    assertEquals(0, skills.appliedUpgradeCount(0), "Joining never overwrites the companion's personal choices");
                    assertEquals(0, gear.appliedCount(0));
                }
            }
        }
    }

    @Test void aFullyDefaultedLateJoinerCanMoveAndDodgeOnTheirFirstSimulationStep() {
        for (ArenaWorld.Hero hero : ArenaWorld.Hero.values()) {
            ArenaWorld world = party(hero, PartyProgression.MAX_LEVEL, RunEquipment.MAX_REWARDS);
            world.activatePlayer(1);
            chooseAllHostSkills(world); // Another participant's pending level-up pauses the whole party.
            var player = world.players[1]; float start = player.x;
            world.setInput(1, 1, 0, true);
            world.update(ArenaWorld.STEP);
            assertTrue(player.x > start); assertTrue(player.dodgeTime > 0); assertTrue(player.dodgeCooldown > 0);
            assertFalse(world.equipment.choosing(1));
        }
    }

    @Test void futureRewardsOfferNormalPersonalChoicesWithAccuratePreviewsAndRestoreControls() {
        ArenaWorld world = party(ArenaWorld.Hero.RANGER, 10, 4); world.activatePlayer(1);
        chooseAllHostSkills(world);
        var skills = world.progression; var gear = world.equipment;
        var foundation = skills.foundationSkills(1); var items = gear.foundationItems(1);
        skills.addXp(skills.xpToNext - skills.xp); gear.grantReward();
        assertTrue(skills.offers[1].pending); assertTrue(gear.offers[1].pending);
        assertFalse(skills.offers[1].catchUp); assertFalse(gear.offers[1].catchUp);
        assertEquals(30, skills.offers[1].durationSeconds); assertEquals(30, gear.offers[1].durationSeconds);
        for (int i = 0; i < 3; i++)
            assertEquals(skills.preview(1, skills.offers[1].options[i]), skills.offers[1].cards[i]);
        gear.update(3);
        assertEquals(30, gear.offers[1].secondsLeft, "Hidden gear timer freezes behind a normal skill choice");
        assertTrue(skills.choose(1, skills.offers[1].id, 1));
        chooseAllHostSkills(world);
        gear.update(ArenaWorld.STEP);
        assertTrue(gear.choose(1, gear.offers[1].id, -1));
        assertEquals(10, skills.appliedUpgradeCount(1)); assertEquals(5, gear.appliedCount(1));
        assertSame(foundation, skills.foundationSkills(1)); assertSame(items, gear.foundationItems(1));
        assertFalse(gear.choosing(1));
        float start = world.players[1].x;
        world.setInput(1, 1, 0, true); world.update(ArenaWorld.STEP);
        assertTrue(world.players[1].x > start); assertTrue(world.players[1].dodgeTime > 0);
    }

    @Test void tokenReconnectKeepsGenerationAndRestoresExistingPersonalChoicesWithoutGrantingAgain() {
        ArenaWorld world = party(ArenaWorld.Hero.RANGER, 10, 4); world.activatePlayer(1);
        chooseAllHostSkills(world);
        var state = new LobbyState();
        state.join(null, "first", 1); var joined = state.join(null, "second", 2);
        state.setPhase(LobbyState.Phase.SESSION);
        long generation = state.snapshot(2).second().generation();
        var player = world.players[1]; var skills = world.progression; var gear = world.equipment;
        skills.addXp(skills.xpToNext - skills.xp); gear.grantReward();
        assertTrue(skills.choose(1, skills.offers[1].id, 2));
        chooseAllHostSkills(world);
        gear.update(4);
        int gearId = gear.offers[1].id; float gearSeconds = gear.offers[1].secondsLeft;
        skills.addXp(skills.xpToNext - skills.xp); skills.update(5);
        int skillId = skills.offers[1].id; float skillSeconds = skills.offers[1].secondsLeft;
        player.health -= 17; player.dodgeCooldown = 1.25f; player.reviveProgress = .2f;
        float health = player.health;
        var foundation = skills.foundationSkills(1); var items = gear.foundationItems(1);
        int[] ranks = new int[PartyProgression.Upgrade.values().length];
        for (var upgrade : PartyProgression.Upgrade.values()) ranks[upgrade.ordinal()] = skills.rank(1, upgrade);
        ItemCatalog.Item[] worn = new ItemCatalog.Item[RunEquipment.SLOT_COUNT];
        for (int i = 0; i < worn.length; i++) worn[i] = gear.equipped(1, i);
        state.disconnected("second", 100);
        assertTrue(state.snapshot(100).second().reserved());
        var restored = state.join(joined.token(), "restored", 200);
        assertNull(restored.error()); assertEquals(1, restored.slot());
        assertEquals(generation, state.snapshot(200).second().generation());
        world.activatePlayer(1); // CryptLobby reconciles the same reserved generation this way.
        assertEquals(health, player.health); assertEquals(1.25f, player.dodgeCooldown); assertEquals(.2f, player.reviveProgress);
        assertEquals(skillId, skills.offers[1].id); assertEquals(skillSeconds, skills.offers[1].secondsLeft);
        assertEquals(gearId, gear.offers[1].id); assertEquals(gearSeconds, gear.offers[1].secondsLeft);
        assertTrue(skills.offers[1].pending); assertTrue(gear.offers[1].pending);
        for (var upgrade : PartyProgression.Upgrade.values()) assertEquals(ranks[upgrade.ordinal()], skills.rank(1, upgrade));
        for (int i = 0; i < worn.length; i++) assertSame(worn[i], gear.equipped(1, i));
        assertSame(foundation, skills.foundationSkills(1)); assertSame(items, gear.foundationItems(1));
        assertEquals(9, skills.foundationCount(1)); assertEquals(4, gear.foundationCount(1));
        assertEquals(10, skills.appliedUpgradeCount(1)); assertEquals(4, gear.appliedCount(1));
    }

    @Test void removingThenRejoiningBuildsFreshDefaultsAndRejectsOldChoicesAcrossReset() {
        ArenaWorld world = party(ArenaWorld.Hero.WARDEN, 5, 2); world.activatePlayer(1);
        chooseAllHostSkills(world);
        var skills = world.progression; var gear = world.equipment;
        skills.addXp(skills.xpToNext - skills.xp); gear.grantReward();
        int staleSkill = skills.offers[1].id, staleGear = gear.offers[1].id;
        world.deactivatePlayer(1); world.activatePlayer(1);
        assertEquals(5, skills.foundationCount(1)); assertEquals(3, gear.foundationCount(1));
        assertFalse(gear.choosing(1));
        assertFalse(skills.choose(1, staleSkill, 0)); assertFalse(gear.choose(1, staleGear, 0));
        chooseAllHostSkills(world);
        skills.addXp(skills.xpToNext - skills.xp); gear.grantReward();
        assertTrue(skills.offers[1].id > staleSkill); assertTrue(gear.offers[1].id > staleGear);
        assertFalse(skills.choose(1, staleSkill, 0));
        assertTrue(skills.choose(1, skills.offers[1].id, 0));
        assertFalse(gear.choose(1, staleGear, 0));
        world.reset(); world.activatePlayer(0); world.activatePlayer(1);
        assertEquals(0, skills.foundationCount(1)); assertEquals(0, gear.foundationCount(1));
        assertFalse(gear.choosing(1)); assertTrue(skills.foundationSkills(1).isEmpty()); assertTrue(gear.foundationItems(1).isEmpty());
        skills.addXp(skills.xpToNext); gear.grantReward();
        assertTrue(skills.offers[1].id > staleSkill); assertTrue(gear.offers[1].id > staleGear);
        assertFalse(skills.choose(1, staleSkill, 0));
    }

    private static ArenaWorld party(ArenaWorld.Hero joiningHero, int level, int rewards) {
        ArenaWorld world = new ArenaWorld(ArenaTimeline.TWENTY_MINUTES);
        world.setHero(1, joiningHero); world.activatePlayer(0);
        while (world.progression.level < level)
            world.progression.addXp(world.progression.xpToNext - world.progression.xp);
        for (int i = 0; i < rewards; i++) world.equipment.grantReward();
        return world;
    }

    private static void chooseAllHostSkills(ArenaWorld world) {
        while (world.progression.offers[0].pending)
            assertTrue(world.progression.choose(0, world.progression.offers[0].id, 0));
    }
}
