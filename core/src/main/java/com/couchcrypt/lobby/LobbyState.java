package com.couchcrypt.lobby;

import java.util.UUID;

/** The only owner of player slots. Network callbacks and the render thread use snapshots. */
public final class LobbyState {
    public static final long RECONNECT_MILLIS = 45_000;
    public record Player(int index, boolean connected, boolean reserved, int secondsLeft) {}
    public record Snapshot(Player first, Player second, int connected, boolean hostActive, long revision) {}
    public record Join(int slot, String token, String error) {
        static Join rejected(String reason) { return new Join(-1, null, reason); }
    }
    private static final class Slot {
        String token;
        String connection;
        long expires;
        void clear() { token = null; connection = null; expires = 0; }
    }
    private final Slot[] slots = {new Slot(), new Slot()};
    private boolean hostActive = true;
    private long revision;

    public synchronized Join join(String token, String connection, long now) {
        expire(now);
        if (token != null && !token.isEmpty()) {
            for (int i = 0; i < slots.length; i++) {
                Slot slot = slots[i];
                if (token.equals(slot.token)) {
                    if (slot.connection != null) return Join.rejected("already_connected");
                    slot.connection = connection;
                    slot.expires = 0;
                    revision++;
                    return new Join(i, slot.token, null);
                }
            }
        }
        for (int i = 0; i < slots.length; i++) {
            Slot slot = slots[i];
            if (slot.token == null) {
                slot.token = UUID.randomUUID().toString();
                slot.connection = connection;
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
        if (hostActive != active) { hostActive = active; revision++; }
    }

    public synchronized Snapshot snapshot(long now) {
        expire(now);
        int count = 0;
        for (Slot slot : slots) if (slot.connection != null) count++;
        return new Snapshot(player(0, now), player(1, now), count, hostActive, revision);
    }

    private Player player(int index, long now) {
        Slot slot = slots[index];
        return new Player(index, slot.connection != null, slot.token != null,
            slot.expires == 0 ? 0 : (int) Math.max(0, (slot.expires - now + 999) / 1000));
    }

    private void expire(long now) {
        for (Slot slot : slots) {
            if (slot.token != null && slot.connection == null && now >= slot.expires) {
                slot.clear(); revision++;
            }
        }
    }
}
