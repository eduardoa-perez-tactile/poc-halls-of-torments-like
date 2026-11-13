package com.couchcrypt.lobby;

import java.util.UUID;
import com.couchcrypt.game.PartyProgression;
import com.couchcrypt.game.ItemCatalog;
import com.couchcrypt.game.RunEquipment;

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
    public static final class EquipmentRequest {
        public int offerId, option;
        public long generation;
    }
    public record UpgradeFeedback(int id, int level, float secondsLeft, boolean catchUp, float durationSeconds,
        PartyProgression.Upgrade first, PartyProgression.Upgrade second, PartyProgression.Upgrade third,
        PartyProgression.CardPreview firstCard, PartyProgression.CardPreview secondCard, PartyProgression.CardPreview thirdCard) {}
    public record ProgressionFeedback(int level, int xp, int xpToNext, UpgradeFeedback upgrade) {}
    public record EquipmentFeedback(int id, float secondsLeft, boolean catchUp, float durationSeconds,
        ItemCatalog.Item first, ItemCatalog.Item second, ItemCatalog.Item third,
        PartyProgression.CardPreview firstCard, PartyProgression.CardPreview secondCard, PartyProgression.CardPreview thirdCard) {}
    public record StarterBuild(int skillsGranted, int equipmentGranted) {}
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
        float offerSeconds, offerDuration = PartyProgression.CHOICE_SECONDS;
        boolean offerCatchUp;
        final PartyProgression.Upgrade[] options = new PartyProgression.Upgrade[3];
        final PartyProgression.CardPreview[] cards = new PartyProgression.CardPreview[3];
        boolean upgradeQueued;
        int upgradeOption;
        int equipmentId, claimedEquipmentId, equipmentOption;
        float equipmentSeconds, equipmentDuration = PartyProgression.CHOICE_SECONDS;
        boolean equipmentCatchUp;
        boolean participating;
        StarterBuild starterBuild;
        boolean equipmentQueued;
        final ItemCatalog.Item[] equipmentOptions = new ItemCatalog.Item[3];
        final PartyProgression.CardPreview[] equipmentCards = new PartyProgression.CardPreview[3];
        void clearUpgradeRequest() {
            if (upgradeQueued) claimedOfferId = 0;
            upgradeQueued = false;
        }
        void clearEquipmentRequest() {
            if (equipmentQueued) claimedEquipmentId = 0;
            equipmentQueued = false;
        }
        void clearEquipment() {
            equipmentId = claimedEquipmentId = equipmentOption = 0;
            equipmentSeconds = 0; equipmentDuration = PartyProgression.CHOICE_SECONDS; equipmentCatchUp = false; equipmentQueued = false;
            for (int i = 0; i < 3; i++) { equipmentOptions[i] = null; equipmentCards[i] = null; }
        }
        void clearProgression() {
            level = 1; xp = 0; xpToNext = 6; offerId = offerLevel = claimedOfferId = 0;
            offerSeconds = 0; offerDuration = PartyProgression.CHOICE_SECONDS; offerCatchUp = false; upgradeQueued = false;
            participating = false; starterBuild = null;
            for (int i = 0; i < 3; i++) { options[i] = null; cards[i] = null; }
            clearEquipment();
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
                slot.clearInput(); slot.clearUpgradeRequest(); slot.clearEquipmentRequest();
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
        if (phase != next) {
            phase = next; clearInputs(); if (next != Phase.SESSION) clearUpgradeRequests();
            if (next == Phase.LOBBY || next == Phase.RESULTS)
                for (Slot slot : slots) slot.participating = false;
            revision++;
        }
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
        publishProgression(index, level, xp, xpToNext, offerId, offerLevel, secondsLeft, options, cards,
            false, PartyProgression.CHOICE_SECONDS);
    }

    public synchronized void publishProgression(int index, int level, int xp, int xpToNext,
        int offerId, int offerLevel, float secondsLeft, PartyProgression.Upgrade[] options,
        PartyProgression.CardPreview[] cards, boolean catchUp, float durationSeconds) {
        Slot slot = slots[index];
        slot.level = Math.max(1, Math.min(PartyProgression.MAX_LEVEL, level));
        slot.xpToNext = slot.level == PartyProgression.MAX_LEVEL ? 0 : Math.max(1, xpToNext);
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
        slot.offerLevel = offered ? Math.max(2, Math.min(PartyProgression.MAX_LEVEL, offerLevel)) : 0;
        slot.offerCatchUp = offered && catchUp;
        slot.offerDuration = choiceDuration(catchUp, durationSeconds);
        slot.offerSeconds = offered ? Math.min(slot.offerDuration, secondsLeft) : 0;
        // A skill choice takes priority over gear; do not carry an unseen gear click behind it.
        if (offered) slot.clearEquipmentRequest();
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
            ? new UpgradeFeedback(slot.offerId, slot.offerLevel, slot.offerSeconds, slot.offerCatchUp, slot.offerDuration, slot.options[0], slot.options[1], slot.options[2],
                slot.cards[0], slot.cards[1], slot.cards[2]) : null;
        return new ProgressionFeedback(slot.level, slot.xp, slot.xpToNext, upgrade);
    }

    /** Equipment remains presented while paused or hidden behind a personal skill choice. */
    public synchronized void publishEquipment(int index, int offerId, float secondsLeft,
        ItemCatalog.Item[] options, PartyProgression.CardPreview[] cards) {
        publishEquipment(index, offerId, secondsLeft, options, cards, false, PartyProgression.CHOICE_SECONDS);
    }

    public synchronized void publishEquipment(int index, int offerId, float secondsLeft,
        ItemCatalog.Item[] options, PartyProgression.CardPreview[] cards, boolean catchUp, float durationSeconds) {
        Slot slot = slots[index];
        boolean offered = offerId > 0 && options != null && options.length >= 3
            && options[0] != null && options[1] != null && options[2] != null
            && cards != null && cards.length >= 3 && cards[0] != null && cards[1] != null && cards[2] != null
            && Float.isFinite(secondsLeft) && secondsLeft > 0;
        int nextId = offered ? offerId : 0;
        if (slot.equipmentId != nextId || nextId == 0) {
            slot.equipmentQueued = false; slot.claimedEquipmentId = 0;
        }
        slot.equipmentId = nextId;
        slot.equipmentCatchUp = offered && catchUp;
        slot.equipmentDuration = choiceDuration(catchUp, durationSeconds);
        slot.equipmentSeconds = offered ? Math.min(slot.equipmentDuration, secondsLeft) : 0;
        for (int i = 0; i < 3; i++) {
            slot.equipmentOptions[i] = offered ? options[i] : null;
            slot.equipmentCards[i] = offered ? cards[i] : null;
        }
    }

    public synchronized boolean chooseEquipment(String connection, int offerId, int option) {
        if (!hostActive || phase != Phase.SESSION || offerId <= 0 || option < -1 || option >= 3) return false;
        for (Slot slot : slots) {
            if (!connection.equals(slot.connection)) continue;
            if (slot.offerId > 0 || slot.equipmentId != offerId || slot.equipmentSeconds <= 0
                || slot.claimedEquipmentId == offerId) return false;
            slot.claimedEquipmentId = offerId;
            slot.equipmentOption = option; slot.equipmentQueued = true;
            return true;
        }
        return false;
    }

    public synchronized boolean pollEquipment(int index, EquipmentRequest out) {
        Slot slot = slots[index];
        if (!slot.equipmentQueued || slot.offerId > 0 || !hostActive || phase != Phase.SESSION || slot.connection == null) return false;
        out.offerId = slot.equipmentId; out.option = slot.equipmentOption; out.generation = slot.generation;
        slot.equipmentQueued = false;
        return true;
    }

    /** Server-thread snapshot; no caller-owned offer array escapes. */
    public synchronized EquipmentFeedback equipmentFeedback(int index) {
        Slot slot = slots[index];
        return slot.equipmentId > 0
            ? new EquipmentFeedback(slot.equipmentId, slot.equipmentSeconds, slot.equipmentCatchUp, slot.equipmentDuration,
                slot.equipmentOptions[0], slot.equipmentOptions[1], slot.equipmentOptions[2],
                slot.equipmentCards[0], slot.equipmentCards[1], slot.equipmentCards[2]) : null;
    }

    /** Publish current-run membership and a nonblocking confirmation, without per-frame snapshots. */
    public synchronized void publishStarterBuild(int index, boolean participating, int skillsGranted, int equipmentGranted) {
        Slot slot = slots[index];
        slot.participating = participating;
        int skills = participating ? Math.max(0, Math.min(PartyProgression.MAX_LEVEL - 1, skillsGranted)) : 0;
        int equipment = participating ? Math.max(0, Math.min(RunEquipment.MAX_REWARDS, equipmentGranted)) : 0;
        if (skills + equipment == 0) slot.starterBuild = null;
        else if (slot.starterBuild == null || slot.starterBuild.skillsGranted() != skills
            || slot.starterBuild.equipmentGranted() != equipment) slot.starterBuild = new StarterBuild(skills, equipment);
    }

    public synchronized StarterBuild starterBuild(int index) { return slots[index].starterBuild; }

    private static float choiceDuration(boolean catchUp, float duration) {
        return Float.isFinite(duration) && duration > 0 ? Math.min(PartyProgression.CHOICE_SECONDS, duration)
            : catchUp ? 15f : PartyProgression.CHOICE_SECONDS;
    }

    private void clearUpgradeRequests() {
        for (Slot slot : slots) { slot.clearUpgradeRequest(); slot.clearEquipmentRequest(); }
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
            slot.expires == 0 || slot.participating ? 0 : (int) Math.max(0, (slot.expires - now + 999) / 1000), slot.generation);
    }

    private void expire(long now) {
        for (Slot slot : slots) {
            if (slot.token != null && slot.connection == null && !slot.participating && now >= slot.expires) {
                slot.clear(); revision++;
            }
        }
    }
}
