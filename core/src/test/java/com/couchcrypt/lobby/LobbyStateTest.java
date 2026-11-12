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
}
