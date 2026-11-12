package com.couchcrypt.lobby;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class LobbyStateTest {
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
}
