package com.couchcrypt.lobby;

import org.junit.jupiter.api.Test;
import com.couchcrypt.game.PartyProgression;
import com.couchcrypt.game.ItemCatalog;
import static org.junit.jupiter.api.Assertions.*;

class LobbyStateTest {
    @Test void equipmentRequestsAreOwnSlotBoundAndSkillsTakePriorityEvenWithTheSameId() {
        var state = new LobbyState(); state.join(null, "a", 1); state.join(null, "b", 1);
        state.setPhase(LobbyState.Phase.SESSION);
        var options = equipmentOptions(); var cards = equipmentCards(options);
        var expected = options[0]; var expectedCard = cards[0];
        state.publishEquipment(0, 10, 27, options, cards);
        state.publishEquipment(1, 20, 30, options, cards);
        options[0] = options[1]; cards[0] = null;
        assertSame(expected, state.equipmentFeedback(0).first());
        assertSame(expectedCard, state.equipmentFeedback(0).firstCard());
        assertFalse(state.chooseEquipment("a", 20, 0));
        assertFalse(state.chooseEquipment("intruder", 10, 0));
        assertFalse(state.chooseEquipment("a", 10, -2)); assertFalse(state.chooseEquipment("a", 10, 3));
        assertFalse(state.chooseUpgrade("a", 10, 0), "Gear IDs cannot select a skill offer");
        assertTrue(state.chooseEquipment("a", 10, 2));
        state.publishProgression(0, 2, 0, 22, 10, 2, 30, PartyProgression.Upgrade.values());
        var request = new LobbyState.EquipmentRequest();
        assertFalse(state.pollEquipment(0, request), "A new skill choice cancels an unconsumed hidden gear click");
        assertFalse(state.chooseEquipment("a", 10, 1));
        assertTrue(state.chooseUpgrade("a", 10, 0));
        state.publishProgression(0, 2, 0, 22, 0, 0, 0, null);
        assertTrue(state.chooseEquipment("a", 10, 1));
        assertFalse(state.pollEquipment(1, request));
        assertTrue(state.pollEquipment(0, request));
        assertEquals(10, request.offerId); assertEquals(1, request.option);
        assertEquals(state.snapshot(1).first().generation(), request.generation);
        assertFalse(state.pollEquipment(0, request));
        assertFalse(state.chooseEquipment("a", 10, 2), "Polled equipment clicks remain claimed until the host acknowledges");
        state.publishEquipment(0, 0, 0, null, null);
        assertNull(state.equipmentFeedback(0));
        assertFalse(state.chooseEquipment("a", 10, 0));
        state.publishEquipment(0, 11, 30, equipmentOptions(), equipmentCards(equipmentOptions()));
        assertTrue(state.chooseEquipment("a", 11, -1));
        assertTrue(state.pollEquipment(0, request)); assertEquals(-1, request.option);
    }

    @Test void equipmentPresentationSurvivesPauseBackgroundAndReconnectButNotReplacement() {
        var state = new LobbyState(); var joined = state.join(null, "a", 1);
        state.setPhase(LobbyState.Phase.SESSION);
        var options = equipmentOptions(); var cards = equipmentCards(options);
        state.publishEquipment(0, 7, 24, options, cards);
        var request = new LobbyState.EquipmentRequest();
        state.chooseEquipment("a", 7, 0); state.setPhase(LobbyState.Phase.PAUSED);
        assertFalse(state.pollEquipment(0, request)); assertFalse(state.chooseEquipment("a", 7, 0));
        assertEquals(24, state.equipmentFeedback(0).secondsLeft());
        state.setPhase(LobbyState.Phase.INVITE); assertEquals(7, state.equipmentFeedback(0).id());
        state.setPhase(LobbyState.Phase.SESSION); assertTrue(state.chooseEquipment("a", 7, 1));
        state.setHostActive(false); assertFalse(state.pollEquipment(0, request));
        assertFalse(state.chooseEquipment("a", 7, 0));
        state.setHostActive(true); assertTrue(state.chooseEquipment("a", 7, 2));
        state.disconnected("a", 2); assertFalse(state.pollEquipment(0, request));
        state.join(joined.token(), "reconnected", 3);
        assertEquals(7, state.equipmentFeedback(0).id());
        assertFalse(state.chooseEquipment("a", 7, 0)); assertTrue(state.chooseEquipment("reconnected", 7, 0));
        long oldGeneration = state.snapshot(3).first().generation();
        state.remove(0); state.setPhase(LobbyState.Phase.LOBBY); state.join(null, "fresh", 4);
        state.setPhase(LobbyState.Phase.SESSION);
        assertNull(state.equipmentFeedback(0)); assertFalse(state.pollEquipment(0, request));
        assertFalse(state.chooseEquipment("fresh", 7, 0));
        state.publishEquipment(0, 8, 30, options, cards);
        assertTrue(state.chooseEquipment("fresh", 8, 0)); assertTrue(state.pollEquipment(0, request));
        assertNotEquals(oldGeneration, request.generation);
    }

    @Test void equipmentPublicationRejectsExpiredNonFiniteAndIncompleteOffers() {
        var state = new LobbyState(); state.join(null, "a", 1); state.setPhase(LobbyState.Phase.SESSION);
        var options = equipmentOptions(); var cards = equipmentCards(options);
        state.publishEquipment(0, 1, 100, options, cards);
        assertEquals(PartyProgression.CHOICE_SECONDS, state.equipmentFeedback(0).secondsLeft());
        state.chooseEquipment("a", 1, 0);
        state.publishEquipment(0, 1, 0, options, cards);
        assertNull(state.equipmentFeedback(0)); assertFalse(state.pollEquipment(0, new LobbyState.EquipmentRequest()));
        for (float invalid : new float[]{Float.NaN, Float.POSITIVE_INFINITY, -1}) {
            state.publishEquipment(0, 2, invalid, options, cards); assertNull(state.equipmentFeedback(0));
        }
        state.publishEquipment(0, 3, 30, options, null); assertNull(state.equipmentFeedback(0));
        cards[1] = null;
        state.publishEquipment(0, 4, 30, options, cards); assertNull(state.equipmentFeedback(0));
    }

    private static ItemCatalog.Item[] equipmentOptions() {
        return ItemCatalog.ALL.subList(0, 3).toArray(ItemCatalog.Item[]::new);
    }

    private static PartyProgression.CardPreview[] equipmentCards(ItemCatalog.Item[] options) {
        var cards = new PartyProgression.CardPreview[3];
        for (int i = 0; i < 3; i++) cards[i] = new PartyProgression.CardPreview(options[i].icon(), 0,
            "DEFENSE", "HELMET: EMPTY", "EMPTY", options[i].description());
        return cards;
    }

    @Test void longRunCapStillPublishesQueuedPersonalEntitlementsAndClearsXpRail() {
        var state = new LobbyState(); state.join(null, "a", 1); state.setPhase(LobbyState.Phase.SESSION);
        var choices = PartyProgression.Upgrade.values();
        state.publishProgression(0, PartyProgression.MAX_LEVEL + 1, 100, 175,
            50, PartyProgression.MAX_LEVEL, PartyProgression.CHOICE_SECONDS, choices);
        var feedback = state.progressionFeedback(0);
        assertEquals(PartyProgression.MAX_LEVEL, feedback.level());
        assertEquals(0, feedback.xp()); assertEquals(0, feedback.xpToNext());
        assertEquals(PartyProgression.MAX_LEVEL, feedback.upgrade().level());
        assertEquals(PartyProgression.CHOICE_SECONDS, feedback.upgrade().secondsLeft());
        assertTrue(state.chooseUpgrade("a", 50, 0), "Reaching the shared cap must not hide queued personal upgrades");
        var request = new LobbyState.UpgradeRequest();
        assertTrue(state.pollUpgrade(0, request)); assertEquals(50, request.offerId);
        state.publishProgression(0, PartyProgression.MAX_LEVEL, 0, 0, 0, 0, 0, null);
        assertNull(state.progressionFeedback(0).upgrade());
    }

    @Test void newCompanionRequiresHostInviteButExistingPlayerCanReconnectDuringSession() {
        var state = new LobbyState();
        var first = state.join(null, "a", 1);
        state.setPhase(LobbyState.Phase.SESSION);
        assertEquals("invite_required", state.join(null, "b", 2).error());
        state.disconnected("a", 3);
        assertEquals(0, state.join(first.token(), "a2", 4).slot());
        state.setPhase(LobbyState.Phase.PAUSED);
        assertEquals("invite_required", state.join(null, "b", 5).error());
        state.setPhase(LobbyState.Phase.INVITE);
        assertEquals(1, state.join(null, "b", 6).slot());
        assertEquals(2, state.snapshot(6).connected());
    }
    @Test void assignsTwoDistinctPlayersAndRejectsThird() {
        var state = new LobbyState();
        var first = state.join(null, "a", 1);
        var second = state.join(null, "b", 1);
        assertEquals(0, first.slot()); assertEquals(1, second.slot());
        assertNotEquals(first.token(), second.token());
        assertEquals("lobby_full", state.join(null, "c", 1).error());
        assertEquals(2, state.snapshot(1).connected());
    }
    @Test void disconnectedPhoneReclaimsItsReservedPlayer() {
        var state = new LobbyState();
        var first = state.join(null, "old", 1);
        state.disconnected("old", 10);
        assertTrue(state.snapshot(11).first().reserved());
        assertFalse(state.snapshot(11).first().connected());
        assertEquals(0, state.join(first.token(), "new", 12).slot());
        // A delayed close from the old socket cannot disconnect its replacement.
        state.disconnected("old", 13);
        assertEquals(1, state.snapshot(13).connected());
    }
    @Test void reservationExpiresAndDoesNotBlockAnotherPhoneForever() {
        var state = new LobbyState();
        state.join(null, "a", 1); state.join(null, "b", 1);
        state.disconnected("a", 10);
        assertEquals("lobby_full", state.join(null, "c", 11).error());
        assertEquals(0, state.join(null, "c", 10 + LobbyState.RECONNECT_MILLIS).slot());
    }
    @Test void duplicateBrowserTabCannotStealAnActiveController() {
        var state = new LobbyState();
        var first = state.join(null, "a", 1);
        assertEquals("already_connected", state.join(first.token(), "b", 2).error());
        assertEquals(1, state.snapshot(2).connected());
    }
    @Test void hostRemovalImmediatelyFreesSlotAndInvalidatesOldToken() {
        var state = new LobbyState();
        var old = state.join(null, "a", 1);
        assertEquals("a", state.remove(0));
        var replacement = state.join(null, "b", 2);
        assertNotEquals(old.token(), replacement.token());
        state.disconnected("a", 3);
        assertTrue(state.snapshot(3).first().connected());
    }
    @Test void hostPauseDoesNotEvictConnectedPhones() {
        var state = new LobbyState();
        state.join(null, "a", 1); state.setHostActive(false);
        var snapshot = state.snapshot(1);
        assertFalse(snapshot.hostActive()); assertEquals(1, snapshot.connected());
    }
    @Test void controlsAreSocketBoundNormalizedAndRejectNonFiniteValues() {
        var state = new LobbyState(); state.join(null, "a", 1); state.join(null, "b", 1);
        state.setPhase(LobbyState.Phase.SESSION);
        assertFalse(state.input("intruder", 0, 1, 1, true, 10));
        assertFalse(state.input("a", 0, Float.NaN, 0, false, 10));
        assertFalse(state.input("a", 0, Float.POSITIVE_INFINITY, 0, false, 10));
        assertTrue(state.input("a", 0, 20, 20, false, 10));
        var controls = new LobbyState.Controls(); state.readControls(0, controls, 10);
        assertEquals(1, controls.x * controls.x + controls.y * controls.y, .0001f);
        state.readControls(1, controls, 10); assertEquals(0, controls.x); assertEquals(0, controls.y);
    }
    @Test void sequencePreventsReplayAndDodgeIsConsumedOncePerPress() {
        var state = new LobbyState(); state.join(null, "a", 1); state.setPhase(LobbyState.Phase.SESSION);
        var controls = new LobbyState.Controls();
        assertTrue(state.input("a", 2, 1, 0, true, 10));
        state.readControls(0, controls, 10); assertTrue(controls.dodge);
        state.readControls(0, controls, 11); assertFalse(controls.dodge);
        assertFalse(state.input("a", 2, -1, 0, true, 12));
        assertFalse(state.input("a", 1, -1, 0, true, 12));
        state.input("a", 3, 1, 0, true, 12); state.readControls(0, controls, 12); assertFalse(controls.dodge);
        state.input("a", 4, 1, 0, false, 13); state.input("a", 5, 1, 0, true, 14);
        state.readControls(0, controls, 14); assertTrue(controls.dodge); assertEquals(1, controls.x);
    }
    @Test void stalePauseBackgroundAndDisconnectImmediatelyReleaseControls() {
        var state = new LobbyState(); state.join(null, "a", 1); state.setPhase(LobbyState.Phase.SESSION);
        var controls = new LobbyState.Controls();
        state.input("a", 0, 1, 1, true, 10);
        state.readControls(0, controls, 311); assertEquals(0, controls.x); assertFalse(controls.dodge); assertTrue(controls.connected);
        state.input("a", 1, 1, 0, true, 312); state.setPhase(LobbyState.Phase.PAUSED);
        state.setPhase(LobbyState.Phase.SESSION); state.readControls(0, controls, 313); assertEquals(0, controls.x); assertFalse(controls.dodge);
        state.input("a", 2, 1, 0, false, 314); state.setHostActive(false);
        assertFalse(state.input("a", 3, 1, 0, true, 315));
        state.setHostActive(true); state.readControls(0, controls, 316); assertEquals(0, controls.x); assertFalse(controls.dodge);
        state.input("a", 4, 1, 0, true, 317); state.disconnected("a", 318);
        state.readControls(0, controls, 318); assertEquals(0, controls.x); assertFalse(controls.connected); assertFalse(controls.dodge);
    }
    @Test void reconnectPreservesIdentityButFreshJoinChangesItAndResetsInputSequence() {
        var state = new LobbyState(); var first = state.join(null, "a", 1);
        long generation = state.snapshot(1).first().generation(); state.setPhase(LobbyState.Phase.SESSION);
        state.input("a", 100, 1, 0, true, 2); state.disconnected("a", 3);
        state.join(first.token(), "replacement", 4);
        assertEquals(generation, state.snapshot(4).first().generation());
        assertTrue(state.input("replacement", 0, 0, 1, false, 5));
        state.remove(0); state.setPhase(LobbyState.Phase.LOBBY); state.join(null, "new", 6);
        assertNotEquals(generation, state.snapshot(6).first().generation());
    }
    @Test void upgradeChoicesAreOwnSlotBoundCurrentAndConsumedOnce() {
        var state = new LobbyState(); state.join(null, "a", 1); state.join(null, "b", 1);
        state.setPhase(LobbyState.Phase.SESSION);
        var choices = PartyProgression.Upgrade.values();
        var first = choices[0];
        state.publishProgression(0, 2, 1, 8, 11, 2, 30, choices);
        state.publishProgression(1, 2, 1, 8, 22, 2, 30, choices);
        choices[0] = choices[3];
        assertSame(first, state.progressionFeedback(0).upgrade().first(), "Publication copies the option references");
        assertFalse(state.chooseUpgrade("a", 22, 0), "A phone cannot choose its companion's offer");
        assertFalse(state.chooseUpgrade("intruder", 11, 0));
        assertFalse(state.chooseUpgrade("a", 11, -1)); assertFalse(state.chooseUpgrade("a", 11, 3));
        assertTrue(state.chooseUpgrade("a", 11, 1));
        var request = new LobbyState.UpgradeRequest();
        assertFalse(state.pollUpgrade(1, request));
        assertTrue(state.pollUpgrade(0, request));
        assertEquals(11, request.offerId); assertEquals(1, request.option);
        assertEquals(state.snapshot(1).first().generation(), request.generation);
        assertFalse(state.pollUpgrade(0, request));
        assertFalse(state.chooseUpgrade("a", 11, 2), "A polled request cannot be replayed before publication catches up");
        state.publishProgression(0, 2, 1, 8, 11, 2, 29, choices);
        assertFalse(state.chooseUpgrade("a", 11, 0));
        state.publishProgression(0, 3, 0, 10, 33, 3, 30, choices);
        assertFalse(state.chooseUpgrade("a", 11, 0));
        assertTrue(state.chooseUpgrade("a", 33, 2));
    }

    @Test void pauseBackgroundDisconnectAndReplacementDiscardUnconsumedChoices() {
        var state = new LobbyState(); var joined = state.join(null, "a", 1);
        state.setPhase(LobbyState.Phase.SESSION);
        var choices = PartyProgression.Upgrade.values();
        var request = new LobbyState.UpgradeRequest();
        state.publishProgression(0, 2, 0, 8, 10, 2, 30, choices);
        state.chooseUpgrade("a", 10, 1); state.setPhase(LobbyState.Phase.PAUSED);
        assertFalse(state.pollUpgrade(0, request)); assertFalse(state.chooseUpgrade("a", 10, 1));
        assertEquals(10, state.progressionFeedback(0).upgrade().id(), "Pause preserves presentation");
        state.setPhase(LobbyState.Phase.SESSION); assertTrue(state.chooseUpgrade("a", 10, 2));
        state.setHostActive(false); assertFalse(state.pollUpgrade(0, request));
        assertFalse(state.chooseUpgrade("a", 10, 0)); state.setHostActive(true);
        assertTrue(state.chooseUpgrade("a", 10, 1)); state.disconnected("a", 2);
        assertFalse(state.pollUpgrade(0, request));
        state.join(joined.token(), "replacement", 3);
        assertFalse(state.chooseUpgrade("a", 10, 0)); assertTrue(state.chooseUpgrade("replacement", 10, 0));
        long previousGeneration = state.snapshot(3).first().generation();
        state.remove(0); state.setPhase(LobbyState.Phase.LOBBY); state.join(null, "fresh", 4);
        state.setPhase(LobbyState.Phase.SESSION);
        assertFalse(state.pollUpgrade(0, request)); assertFalse(state.chooseUpgrade("fresh", 10, 0));
        assertNull(state.progressionFeedback(0).upgrade());
        state.publishProgression(0, 2, 0, 8, 20, 2, 30, choices);
        state.chooseUpgrade("fresh", 20, 0); assertTrue(state.pollUpgrade(0, request));
        assertNotEquals(previousGeneration, request.generation);
    }

    @Test void authoritativeClearAndInvalidTimersCannotLeaveAnOfferSelectable() {
        var state = new LobbyState(); state.join(null, "a", 1); state.setPhase(LobbyState.Phase.SESSION);
        var choices = PartyProgression.Upgrade.values(); var request = new LobbyState.UpgradeRequest();
        state.publishProgression(0, 2, 3, 8, 10, 2, 30, choices); state.chooseUpgrade("a", 10, 2);
        state.publishProgression(0, 2, 3, 8, 0, 0, 0, null);
        assertFalse(state.pollUpgrade(0, request)); assertNull(state.progressionFeedback(0).upgrade());
        assertFalse(state.chooseUpgrade("a", 10, 0));
        state.publishProgression(0, 2, 3, 8, 20, 2, Float.NaN, choices);
        assertNull(state.progressionFeedback(0).upgrade());
        state.publishProgression(0, 2, 3, 8, 30, 2, 100, choices);
        assertEquals(30, state.progressionFeedback(0).upgrade().secondsLeft());
    }
    @Test void publishedCardsCopyArrayEntriesAndDoNotExposeMutableOfferArrays() {
        var world = new com.couchcrypt.game.ArenaWorld(); world.activatePlayer(0); world.progression.addXp(6);
        var offer = world.progression.offers[0];
        var state = new LobbyState(); state.join(null, "phone", 1);
        var expected = offer.cards[0]; var option = offer.options[0];
        state.publishProgression(0, 2, 0, 10, offer.id, 2, 30, offer.options, offer.cards);
        offer.cards[0] = null; offer.options[0] = PartyProgression.Upgrade.CRITICAL;
        var published = state.progressionFeedback(0).upgrade();
        assertSame(expected, published.firstCard()); assertEquals(option, published.first());
        state.publishProgression(0, 2, 0, 10, 0, 0, 0, null, null);
        assertNull(state.progressionFeedback(0).upgrade());
    }

}
