package com.couchcrypt.game;

/** Shared party XP with independent, timed upgrade choices owned by the simulation thread. */
public final class PartyProgression {
    public static final int MAX_LEVEL = 20;
    public static final float CHOICE_SECONDS = 30f;

    public enum Upgrade {
        POWER("power", "Soulfire", "Damage +25% of base."),
        HASTE("haste", "Quickening", "Attack speed +15% of base."),
        FLEET("fleet", "Fleet Foot", "Move speed +12% of base."),
        VITALITY("vitality", "Vitality", "Max health +20. Restore 20 health."),
        EVASION("evasion", "Shadow Step", "Dodge cooldown -15% of base.");

        public final String id, title, description;
        Upgrade(String id, String title, String description) {
            this.id = id; this.title = title; this.description = description;
        }
    }

    public static final class Offer {
        public boolean pending;
        public int id, level;
        public float secondsLeft;
        public final Upgrade[] options = new Upgrade[3];
    }

    private static final Upgrade[] UPGRADES = Upgrade.values();
    private final ArenaWorld world;
    private final boolean[] participants = new boolean[2];
    private final int[] applied = new int[2];
    private int nextOfferId;
    public int level, xp, xpToNext, totalXp;
    public final Offer[] offers = { new Offer(), new Offer() };

    public PartyProgression(ArenaWorld world) { this.world = world; reset(); }

    /** Offer identities intentionally survive reset, so an old phone packet cannot pick a new offer. */
    public void reset() {
        level = 1; xp = totalXp = 0; xpToNext = threshold(level);
        for (int slot = 0; slot < offers.length; slot++) {
            participants[slot] = false; applied[slot] = 0;
            Offer offer = offers[slot];
            offer.pending = false; offer.id = offer.level = 0; offer.secondsLeft = 0f;
            for (int i = 0; i < offer.options.length; i++) offer.options[i] = UPGRADES[i];
        }
    }

    /** Joining at a later party level grants the same bounded number of upgrade entitlements. */
    public void activatePlayer(int slot) {
        if (!validSlot(slot) || participants[slot]) return;
        participants[slot] = true; applied[slot] = 0;
        startNextOffer(slot);
    }

    public void deactivatePlayer(int slot) {
        if (!validSlot(slot)) return;
        participants[slot] = false; applied[slot] = 0;
        Offer offer = offers[slot];
        offer.pending = false; offer.id = offer.level = 0; offer.secondsLeft = 0;
    }

    /** Collected soul XP is counted once for the party, regardless of which player collected it. */
    public void addXp(int amount) {
        if (amount <= 0) return;
        totalXp = (int) Math.min(Integer.MAX_VALUE, (long) totalXp + amount);
        if (level >= MAX_LEVEL) return;
        long available = (long) xp + amount;
        while (level < MAX_LEVEL && available >= xpToNext) {
            available -= xpToNext;
            level++;
            xpToNext = level == MAX_LEVEL ? 0 : threshold(level);
            for (int slot = 0; slot < offers.length; slot++) startNextOffer(slot);
        }
        xp = level == MAX_LEVEL ? 0 : (int) available;
    }

    /** Not called while paused. A long delta can expire one offer, never skip queued unread offers. */
    public void update(float delta) {
        if (!Float.isFinite(delta) || delta <= 0) return;
        for (int slot = 0; slot < offers.length; slot++) {
            Offer offer = offers[slot];
            if (!offer.pending) continue;
            offer.secondsLeft = Math.max(0f, offer.secondsLeft - delta);
            if (offer.secondsLeft <= .0001f) choose(slot, offer.id, 0);
        }
    }

    /** A connection may select only its current offer, once. The server supplies the player slot. */
    public boolean choose(int slot, int offerId, int optionIndex) {
        if (!validSlot(slot) || !participants[slot] || !world.players[slot].active) return false;
        Offer offer = offers[slot];
        if (!offer.pending || offer.id != offerId || optionIndex < 0 || optionIndex >= offer.options.length) return false;
        apply(world.players[slot], offer.options[optionIndex]);
        applied[slot]++;
        offer.pending = false; offer.secondsLeft = 0;
        startNextOffer(slot);
        return true;
    }

    public int appliedUpgradeCount(int slot) { return validSlot(slot) ? applied[slot] : 0; }

    private void startNextOffer(int slot) {
        Offer offer = offers[slot];
        if (!participants[slot] || offer.pending || applied[slot] >= level - 1) return;
        offer.pending = true; offer.id = ++nextOfferId; offer.level = applied[slot] + 2;
        offer.secondsLeft = CHOICE_SECONDS;
        int first = (offer.level - 2 + slot) % UPGRADES.length, count = 0;
        for (int i = 0; i < UPGRADES.length && count < offer.options.length; i++) {
            Upgrade candidate = UPGRADES[(first + i) % UPGRADES.length];
            if (available(world.players[slot], candidate)) offer.options[count++] = candidate;
        }
        // With nineteen entitlements at most, three of the five stat caps always remain available.
        while (count < offer.options.length) offer.options[count++] = Upgrade.VITALITY;
    }

    private static boolean available(ArenaWorld.Player player, Upgrade upgrade) {
        return switch (upgrade) {
            case POWER -> player.damageMultiplier < 4f - .00001f;
            case HASTE -> player.attackRateMultiplier < 3f - .00001f;
            case FLEET -> player.moveMultiplier < 2.2f - .00001f;
            case VITALITY -> player.maxHealth < 300f;
            case EVASION -> player.dodgeCooldownMultiplier > .25f + .00001f;
        };
    }

    private static void apply(ArenaWorld.Player player, Upgrade upgrade) {
        switch (upgrade) {
            case POWER -> player.damageMultiplier = Math.min(4f, player.damageMultiplier + .25f);
            case HASTE -> {
                float previous = player.attackRateMultiplier;
                player.attackRateMultiplier = Math.min(3f, previous + .15f);
                player.attackCooldown *= previous / player.attackRateMultiplier;
            }
            case FLEET -> player.moveMultiplier = Math.min(2.2f, player.moveMultiplier + .12f);
            case VITALITY -> {
                player.maxHealth = Math.min(300f, player.maxHealth + 20f);
                if (player.alive) player.health = Math.min(player.maxHealth, player.health + 20f);
            }
            case EVASION -> {
                float previous = player.dodgeCooldownMultiplier;
                player.dodgeCooldownMultiplier = Math.max(.25f, previous - .15f);
                player.dodgeCooldown *= player.dodgeCooldownMultiplier / previous;
            }
        }
    }

    private static int threshold(int level) { return 6 + (level - 1) * 4; }
    private static boolean validSlot(int slot) { return slot >= 0 && slot < 2; }
}
