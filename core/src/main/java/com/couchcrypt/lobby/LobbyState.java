package com.couchcrypt.lobby;

import java.util.UUID;
import com.couchcrypt.game.PartyProgression;

/** The only owner of player slots. Network callbacks and the render thread use snapshots. */
public final class LobbyState {
    public static final long RECONNECT_MILLIS = 45_000;
    public static final long INPUT_TIMEOUT_MILLIS = 300;
    public enum Phase { LOBBY, SESSION, PAUSED, INVITE, RESULTS }
    public record Player(int index, boolean connected, boolean reserved, int secondsLeft, long generation) {}
    public record Feedback(float health, float maxHealth, float dodgeCooldown, boolean alive) {}
    /** Reuse one instance per player on the render thread. Axes are screen right and up. */
    public static final class Controls {
        public float x, y;
        public boolean dodge, connected;
        public long generation;
    }
    /** Reused by the render thread; choices never mutate simulation state on a socket thread. */
    public static final class UpgradeRequest {
        public int offerId, option;
        public long generation;
    }
    public record UpgradeFeedback(int id, int level, float secondsLeft,
        PartyProgression.Upgrade first, PartyProgression.Upgrade second, PartyProgression.Upgrade third,
        PartyProgression.CardPreview firstCard, PartyProgression.CardPreview secondCard, PartyProgression.CardPreview thirdCard) {}
    public record ProgressionFeedback(int level, int xp, int xpToNext, UpgradeFeedback upgrade) {}
    public record Snapshot(Player first, Player second, int connected, boolean hostActive, Phase phase, long revision) {}
    public record Join(int slot, String token, String error) {
        static Join rejected(String reason) { return new Join(-1, null, reason); }
    }
    private static final class Slot {
        String token;
        String connection;
        long expires, generation, lastInput;
        int sequence = -1;
        float x, y, health = 100, maxHealth = 100, dodgeCooldown;
        boolean pendingDodge, dodgeHeld, alive = true;
        int level = 1, xp, xpToNext = 6, offerId, offerLevel, claimedOfferId;
        float offerSeconds;
        final PartyProgression.Upgrade[] options = new PartyProgression.Upgrade[3];
        final PartyProgression.CardPreview[] cards = new PartyProgression.CardPreview[3];
        boolean upgradeQueued;
        int upgradeOption;
        void clearUpgradeRequest() {
            if (upgradeQueued) claimedOfferId = 0;
            upgradeQueued = false;
        }
        void clearProgression() {
            level = 1; xp = 0; xpToNext = 6; offerId = offerLevel = claimedOfferId = 0;
            offerSeconds = 0; upgradeQueued = false;
            for (int i = 0; i < 3; i++) { options[i] = null; cards[i] = null; }
        }
        void clearInput() { x = 0; y = 0; pendingDodge = false; dodgeHeld = false; lastInput = 0; }
        void clear() {
            token = null; connection = null; expires = 0; sequence = -1; clearInput();
            health = maxHealth = 100; dodgeCooldown = 0; alive = true; clearProgression();
        }
    }
    private final Slot[] slots = {new Slot(), new Slot()};
    private boolean hostActive = true;
    private Phase phase = Phase.LOBBY;
    private long revision, nextGeneration;

    public synchronized Join join(String token, String connection, long now) {
        expire(now);
        if (token != null && !token.isEmpty()) {
            for (int i = 0; i < slots.length; i++) {
                Slot slot = slots[i];
                if (token.equals(slot.token)) {
                    if (slot.connection != null) return Join.rejected("already_connected");
                    slot.connection = connection;
                    slot.expires = 0;
                    slot.sequence = -1; slot.clearInput();
                    revision++;
                    return new Join(i, slot.token, null);
                }
            }
        }
        for (int i = 0; i < slots.length; i++) {
            Slot slot = slots[i];
            if (slot.token == null) {
                if (phase != Phase.LOBBY && phase != Phase.INVITE) return Join.rejected("invite_required");
                slot.token = UUID.randomUUID().toString();
                slot.connection = connection;
                slot.generation = ++nextGeneration;
                slot.sequence = -1; slot.clearInput(); slot.clearProgression();
                revision++;
                return new Join(i, slot.token, null);
            }
        }
        return Join.rejected("lobby_full");
    }

    public synchronized void disconnected(String connection, long now) {
        for (Slot slot : slots) {
            if (connection.equals(slot.connection)) {
                slot.connection = null;
                slot.clearInput(); slot.clearUpgradeRequest();
                slot.expires = now + RECONNECT_MILLIS;
                revision++;
            }
        }
    }

    public synchronized String remove(int index) {
        Slot slot = slots[index];
        String connection = slot.connection;
        slot.clear();
        revision++;
        return connection;
    }

    public synchronized boolean owns(String connection) {
        for (Slot slot : slots) if (connection.equals(slot.connection)) return true;
        return false;
    }

    public synchronized void setHostActive(boolean active) {
        if (hostActive != active) { hostActive = active; clearInputs(); if (!active) clearUpgradeRequests(); revision++; }
    }

    public synchronized void setPhase(Phase next) {
        if (phase != next) { phase = next; clearInputs(); if (next != Phase.SESSION) clearUpgradeRequests(); revision++; }
    }

    /** Only the server supplies connection identities; clients cannot choose a player index. */
    public synchronized boolean input(String connection, int sequence, float x, float y, boolean dodge, long now) {
        if (sequence < 0 || !Float.isFinite(x) || !Float.isFinite(y)) return false;
        for (Slot slot : slots) {
            if (!connection.equals(slot.connection) || sequence <= slot.sequence) continue;
            slot.sequence = sequence;
            if (!hostActive || phase != Phase.SESSION) { slot.clearInput(); return false; }
            x = Math.max(-1, Math.min(1, x)); y = Math.max(-1, Math.min(1, y));
            float lengthSquared = x * x + y * y;
            if (lengthSquared > 1) { float scale = 1f / (float) Math.sqrt(lengthSquared); x *= scale; y *= scale; }
            slot.x = x; slot.y = y; slot.lastInput = now;
            slot.pendingDodge |= dodge && !slot.dodgeHeld;
            slot.dodgeHeld = dodge;
            return true;
        }
        return false;
    }

    public synchronized void readControls(int index, Controls out, long nowMillis) {
        Slot slot = slots[index];
        out.connected = slot.connection != null; out.generation = slot.generation;
        if (!out.connected || !hostActive || phase != Phase.SESSION || nowMillis - slot.lastInput > INPUT_TIMEOUT_MILLIS) slot.clearInput();
        out.x = slot.x; out.y = slot.y; out.dodge = slot.pendingDodge;
        slot.pendingDodge = false;
    }

    public synchronized void setPlayerFeedback(int index, float health, float maxHealth, float dodgeCooldown, boolean alive) {
        Slot slot = slots[index];
        if (!Float.isFinite(health) || !Float.isFinite(maxHealth) || !Float.isFinite(dodgeCooldown)) return;
        slot.maxHealth = Math.max(1, maxHealth); slot.health = Math.max(0, Math.min(slot.maxHealth, health));
        slot.dodgeCooldown = Math.max(0, dodgeCooldown); slot.alive = alive;
    }

    public synchronized Feedback feedback(int index) {
        Slot slot = slots[index];
        return new Feedback(slot.health, slot.maxHealth, slot.dodgeCooldown, slot.alive);
    }

    /** Compatibility path for callers without the new immutable card presentation. */
    public synchronized void publishProgression(int index, int level, int xp, int xpToNext,
        int offerId, int offerLevel, float secondsLeft, PartyProgression.Upgrade[] options) {
        publishProgression(index, level, xp, xpToNext, offerId, offerLevel, secondsLeft, options, null);
    }

    /** Copies immutable records into private fixed storage; no caller-owned arrays escape. */
    public synchronized void publishProgression(int index, int level, int xp, int xpToNext,
        int offerId, int offerLevel, float secondsLeft, PartyProgression.Upgrade[] options,
        PartyProgression.CardPreview[] cards) {
        Slot slot = slots[index];
        slot.level = Math.max(1, Math.min(20, level));
        slot.xpToNext = slot.level == 20 ? 0 : Math.max(1, xpToNext);
        slot.xp = Math.max(0, Math.min(slot.xpToNext, xp));
        boolean offered = offerId > 0 && options != null && options.length >= 3
            && options[0] != null && options[1] != null && options[2] != null
            && Float.isFinite(secondsLeft) && secondsLeft > 0;
        int nextId = offered ? offerId : 0;
        if (slot.offerId != nextId || nextId == 0) {
            slot.upgradeQueued = false;
            slot.claimedOfferId = 0;
        }
        slot.offerId = nextId;
        slot.offerLevel = offered ? Math.max(2, Math.min(20, offerLevel)) : 0;
        slot.offerSeconds = offered ? Math.min(30, secondsLeft) : 0;
        for (int i = 0; i < 3; i++) {
            slot.options[i] = offered ? options[i] : null;
            slot.cards[i] = offered && cards != null && cards.length >= 3 ? cards[i] : null;
        }
    }

    /** The request can only select the caller's own currently published offer. */
    public synchronized boolean chooseUpgrade(String connection, int offerId, int option) {
        if (!hostActive || phase != Phase.SESSION || offerId <= 0 || option < 0 || option >= 3) return false;
        for (Slot slot : slots) {
            if (!connection.equals(slot.connection)) continue;
            if (slot.offerId != offerId || slot.offerSeconds <= 0 || slot.claimedOfferId == offerId) return false;
            slot.claimedOfferId = offerId;
            slot.upgradeOption = option;
            slot.upgradeQueued = true;
            return true;
        }
        return false;
    }

    /** One render-thread delivery, carrying identity to reject remove/rejoin races. */
    public synchronized boolean pollUpgrade(int index, UpgradeRequest out) {
        Slot slot = slots[index];
        if (!slot.upgradeQueued || !hostActive || phase != Phase.SESSION || slot.connection == null) return false;
        out.offerId = slot.offerId; out.option = slot.upgradeOption; out.generation = slot.generation;
        slot.upgradeQueued = false;
        return true;
    }

    /** Allocations here belong to the server thread, never the render loop. */
    public synchronized ProgressionFeedback progressionFeedback(int index) {
        Slot slot = slots[index];
        UpgradeFeedback upgrade = slot.offerId > 0
            ? new UpgradeFeedback(slot.offerId, slot.offerLevel, slot.offerSeconds, slot.options[0], slot.options[1], slot.options[2],
                slot.cards[0], slot.cards[1], slot.cards[2]) : null;
        return new ProgressionFeedback(slot.level, slot.xp, slot.xpToNext, upgrade);
    }

    private void clearUpgradeRequests() { for (Slot slot : slots) slot.clearUpgradeRequest(); }

    private void clearInputs() { for (Slot slot : slots) slot.clearInput(); }

    public synchronized Snapshot snapshot(long now) {
        expire(now);
        int count = 0;
        for (Slot slot : slots) if (slot.connection != null) count++;
        return new Snapshot(player(0, now), player(1, now), count, hostActive, phase, revision);
    }

    private Player player(int index, long now) {
        Slot slot = slots[index];
        return new Player(index, slot.connection != null, slot.token != null,
            slot.expires == 0 ? 0 : (int) Math.max(0, (slot.expires - now + 999) / 1000), slot.generation);
    }

    private void expire(long now) {
        for (Slot slot : slots) {
            if (slot.token != null && slot.connection == null && now >= slot.expires) {
                slot.clear(); revision++;
            }
        }
    }
}
