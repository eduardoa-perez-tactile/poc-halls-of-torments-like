package com.couchcrypt.lobby;

import java.util.UUID;

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
        void clearInput() { x = 0; y = 0; pendingDodge = false; dodgeHeld = false; lastInput = 0; }
        void clear() {
            token = null; connection = null; expires = 0; sequence = -1; clearInput();
            health = maxHealth = 100; dodgeCooldown = 0; alive = true;
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
                slot.sequence = -1; slot.clearInput();
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
                slot.clearInput();
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
        if (hostActive != active) { hostActive = active; clearInputs(); revision++; }
    }

    public synchronized void setPhase(Phase next) {
        if (phase != next) { phase = next; clearInputs(); revision++; }
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
