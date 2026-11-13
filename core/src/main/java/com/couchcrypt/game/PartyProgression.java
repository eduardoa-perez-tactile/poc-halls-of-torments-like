package com.couchcrypt.game;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/** Shared party XP with independent, timed build choices owned by the simulation thread. */
public final class PartyProgression {
    public static final int MAX_LEVEL = 20;
    public static final float CHOICE_SECONDS = 30f;
    // Nineteen earned choices: 116 / 516 / 1,166 / 1,991 cumulative XP at levels 5 / 10 / 15 / 20.
    // Rising costs leave time to play between the unchanged thirty-second phone choices.
    private static final int[] LONG_RUN_THRESHOLDS = {
        12, 22, 34, 48, 60, 70, 80, 90, 100, 110, 120, 130, 140, 150, 155, 160, 165, 170, 175
    };

    public enum Upgrade {
        POWER("power", "Soulfire", "Damage +25% of base."),
        HASTE("haste", "Quickening", "Attack speed +15% of base."),
        FLEET("fleet", "Fleet Foot", "Move speed +12% of base."),
        VITALITY("vitality", "Vitality", "Max health +20. Restore 20 health."),
        EVASION("evasion", "Shadow Step", "Dodge cooldown -15% of base."),
        SWEEP("sweep", "Wide Sweep", "Warden sword reach +20% of base."),
        MULTISHOT("multishot", "Split Shot", "Ranger fires one extra arrow."),
        PIERCE("pierce", "Piercing Arrow", "Ranger arrows pierce one more target."),
        AURA("aura", "Soul Circle", "Unlock or strengthen a nearby damage aura."),
        STORM("storm", "Chain Storm", "Unlock or strengthen automatic chain strikes."),
        MAGNET("magnet", "Soul Draw", "Pickup attraction +30% of base."),
        CRITICAL("critical", "Critical Edge", "Critical hit chance +10 percentage points.");

        public final String id, title, description;
        Upgrade(String id, String title, String description) {
            this.id = id; this.title = title; this.description = description;
        }

        /** Class eligibility is independent of the current run's upgrade caps. */
        public boolean supports(ArenaWorld.Hero hero) {
            return switch (this) {
                case SWEEP -> hero == ArenaWorld.Hero.WARDEN;
                case MULTISHOT, PIERCE -> hero == ArenaWorld.Hero.RANGER;
                default -> true;
            };
        }
    }

    /** Immutable phone presentation, prepared only when an authoritative offer is created. */
    public record CardPreview(int icon, int rank, String category, String headline, String before, String after) {}
    /** Snapshot of an automatically granted rank, independent of later personal choices. */
    public record FoundationSkill(String id, String title, int icon, int rank) {}

    public static final class Offer {
        public boolean pending, catchUp;
        public int id, level;
        public float secondsLeft, durationSeconds = CHOICE_SECONDS;
        public final Upgrade[] options = new Upgrade[3];
        public final CardPreview[] cards = new CardPreview[3];
    }

    private static final Upgrade[] UPGRADES = Upgrade.values();
    private static final Upgrade[] WARDEN_FOUNDATION = {
        Upgrade.POWER, Upgrade.SWEEP, Upgrade.VITALITY, Upgrade.AURA, Upgrade.HASTE, Upgrade.STORM,
        Upgrade.POWER, Upgrade.FLEET, Upgrade.SWEEP, Upgrade.VITALITY, Upgrade.HASTE, Upgrade.AURA,
        Upgrade.EVASION, Upgrade.CRITICAL, Upgrade.POWER, Upgrade.MAGNET,
        Upgrade.SWEEP, Upgrade.AURA, Upgrade.STORM
    };
    private static final Upgrade[] RANGER_FOUNDATION = {
        Upgrade.POWER, Upgrade.MULTISHOT, Upgrade.VITALITY, Upgrade.AURA, Upgrade.HASTE, Upgrade.STORM,
        Upgrade.POWER, Upgrade.FLEET, Upgrade.PIERCE, Upgrade.VITALITY, Upgrade.HASTE, Upgrade.AURA,
        Upgrade.EVASION, Upgrade.CRITICAL, Upgrade.POWER, Upgrade.MAGNET,
        Upgrade.MULTISHOT, Upgrade.AURA, Upgrade.STORM
    };
    private final ArenaWorld world;
    private final boolean[] participants = new boolean[2];
    private final int[] applied = new int[2];
    private final int[] catchUpUntil = new int[2], foundationCounts = new int[2];
    private final String[] foundations = {"", ""};
    private final List<List<FoundationSkill>> foundationSkills = Arrays.asList(List.of(), List.of());
    private final int[][] ranks = { new int[UPGRADES.length], new int[UPGRADES.length] };
    private int nextOfferId;
    public int level, xp, xpToNext, totalXp;
    public final Offer[] offers = { new Offer(), new Offer() };

    public PartyProgression(ArenaWorld world) { this.world = world; reset(); }

    /** Identities survive reset, so a packet from the previous run cannot select a new offer. */
    public void reset() {
        level = 1; xp = totalXp = 0; xpToNext = threshold(level);
        for (int slot = 0; slot < offers.length; slot++) {
            participants[slot] = false; applied[slot] = 0;
            catchUpUntil[slot] = foundationCounts[slot] = 0; foundations[slot] = "";
            foundationSkills.set(slot, List.of());
            clearRanks(slot);
            Offer offer = offers[slot];
            offer.pending = offer.catchUp = false; offer.id = offer.level = 0; offer.secondsLeft = 0f;
            offer.durationSeconds = CHOICE_SECONDS;
            for (int i = 0; i < offer.options.length; i++) { offer.options[i] = UPGRADES[i]; offer.cards[i] = null; }
        }
    }

    /** A fresh participant receives all earlier ranks as defaults; active-participant reconnect is untouched. */
    public void activatePlayer(int slot) {
        if (!validSlot(slot) || participants[slot]) return;
        participants[slot] = true; applied[slot] = 0;
        clearRanks(slot);
        catchUpUntil[slot] = level - 1;
        grantFoundation(slot, catchUpUntil[slot]);
        startNextOffer(slot);
    }

    public void deactivatePlayer(int slot) {
        if (!validSlot(slot)) return;
        participants[slot] = false; applied[slot] = 0;
        catchUpUntil[slot] = foundationCounts[slot] = 0; foundations[slot] = "";
        foundationSkills.set(slot, List.of());
        clearRanks(slot);
        Offer offer = offers[slot];
        offer.pending = offer.catchUp = false; offer.id = offer.level = 0; offer.secondsLeft = 0;
        offer.durationSeconds = CHOICE_SECONDS;
        for (int i = 0; i < offer.cards.length; i++) offer.cards[i] = null;
    }

    /** Collected soul XP is counted once for the party, whoever picked it up. */
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

    /** Pause freezes this clock. A long delta expires one offer, never skips queued choices. */
    public void update(float delta) {
        if (!Float.isFinite(delta) || delta <= 0) return;
        for (int slot = 0; slot < offers.length; slot++) {
            Offer offer = offers[slot];
            if (!offer.pending) startNextOffer(slot);
            if (!offer.pending) continue;
            offer.secondsLeft = Math.max(0f, offer.secondsLeft - delta);
            if (offer.secondsLeft <= .0001f) choose(slot, offer.id, 0);
        }
    }

    /** Only a player's current offer can be consumed, once. The host supplies the slot. */
    public boolean choose(int slot, int offerId, int optionIndex) {
        if (!validSlot(slot) || !participants[slot] || !world.players[slot].active) return false;
        Offer offer = offers[slot];
        if (!offer.pending || offer.id != offerId || optionIndex < 0 || optionIndex >= offer.options.length) return false;
        Upgrade upgrade = offer.options[optionIndex];
        if (!available(world.players[slot], upgrade)) {
            // External stat/hero changes invalidate an old selection without silently choosing another skill.
            offer.id = newOfferId();
            if (!populateOptions(slot)) { offer.pending = false; applied[slot] = level - 1; }
            return false;
        }
        apply(world.players[slot], upgrade);
        ranks[slot][upgrade.ordinal()]++;
        applied[slot]++;
        offer.pending = false; offer.secondsLeft = 0;
        startNextOffer(slot);
        return true;
    }

    public int appliedUpgradeCount(int slot) { return validSlot(slot) ? applied[slot] : 0; }
    public int rank(int slot, Upgrade upgrade) { return validSlot(slot) && upgrade != null ? ranks[slot][upgrade.ordinal()] : 0; }
    public boolean catchingUp(int slot) { return validSlot(slot) && participants[slot] && applied[slot] < catchUpUntil[slot]; }
    public int catchUpRemaining(int slot) { return catchingUp(slot) ? catchUpUntil[slot] - applied[slot] : 0; }
    public int foundationCount(int slot) { return validSlot(slot) ? foundationCounts[slot] : 0; }
    public String foundationSummary(int slot) { return validSlot(slot) ? foundations[slot] : ""; }
    public List<FoundationSkill> foundationSkills(int slot) { return validSlot(slot) ? foundationSkills.get(slot) : List.of(); }

    private void grantFoundation(int slot, int count) {
        ArenaWorld.Player player = world.players[slot];
        Upgrade[] sequence = player.hero == ArenaWorld.Hero.WARDEN ? WARDEN_FOUNDATION : RANGER_FOUNDATION;
        for (int i = 0; i < count && i < MAX_LEVEL - 1; i++) {
            Upgrade upgrade = sequence[Math.min(i, sequence.length - 1)];
            if (!available(player, upgrade)) {
                upgrade = null;
                for (Upgrade candidate : UPGRADES) if (available(player, candidate)) { upgrade = candidate; break; }
            }
            if (upgrade == null) break;
            apply(player, upgrade); ranks[slot][upgrade.ordinal()]++; applied[slot]++;
        }
        foundationCounts[slot] = applied[slot];
        if (applied[slot] == 0) { foundations[slot] = ""; foundationSkills.set(slot, List.of()); return; }
        StringBuilder summary = new StringBuilder(200);
        List<FoundationSkill> skills = new ArrayList<>(UPGRADES.length);
        for (Upgrade upgrade : UPGRADES) {
            int rank = ranks[slot][upgrade.ordinal()];
            if (rank == 0) continue;
            if (summary.length() > 0) summary.append(" / ");
            summary.append(upgrade.title).append(' ').append(rank);
            skills.add(new FoundationSkill(upgrade.id, upgrade.title, upgrade.ordinal(), rank));
        }
        foundations[slot] = summary.toString();
        foundationSkills.set(slot, List.copyOf(skills));
    }

    /** Allocated only for the results screen; combat uses fixed rank arrays. */
    public String buildSummary(int slot) {
        if (!validSlot(slot)) return "No upgrades";
        StringBuilder summary = new StringBuilder(160);
        int shown = 0, omitted = 0;
        for (Upgrade upgrade : UPGRADES) {
            int rank = ranks[slot][upgrade.ordinal()];
            if (rank == 0) continue;
            if (shown == 8) { omitted++; continue; }
            if (shown > 0) summary.append(shown == 4 ? "\n" : " / ");
            summary.append(upgrade.title);
            if (rank > 1) summary.append(' ').append(rank);
            shown++;
        }
        if (shown == 0) return "No upgrades";
        if (omitted > 0) summary.append(" +").append(omitted).append(" more");
        return summary.toString();
    }

    private void startNextOffer(int slot) {
        Offer offer = offers[slot];
        if (!participants[slot] || offer.pending || applied[slot] >= level - 1) return;
        offer.level = applied[slot] + 2;
        if (!populateOptions(slot)) {
            // Normal nineteen-choice runs cannot exhaust this catalog. No invalid/duplicate cards if debug stats do.
            applied[slot] = level - 1;
            return;
        }
        offer.pending = true; offer.id = newOfferId(); offer.catchUp = false;
        offer.durationSeconds = CHOICE_SECONDS;
        offer.secondsLeft = offer.durationSeconds;
    }

    private boolean populateOptions(int slot) {
        ArenaWorld.Player player = world.players[slot];
        Offer offer = offers[slot];
        int count = 0;
        Upgrade recommended = player.health < world.equipment.maxHealth(player) * .6f && available(player, Upgrade.VITALITY)
            ? Upgrade.VITALITY : available(player, Upgrade.POWER) ? Upgrade.POWER : Upgrade.HASTE;
        if (available(player, recommended)) offer.options[count++] = recommended;
        // Secondary attacks appear early; weapon-specific cards make the very first choice tangible.
        Upgrade featured = offer.level == 3 ? Upgrade.AURA : offer.level == 4 ? Upgrade.STORM
            : player.hero == ArenaWorld.Hero.WARDEN ? Upgrade.SWEEP
            : (offer.level & 1) == 0 ? Upgrade.MULTISHOT : Upgrade.PIERCE;
        if (available(player, featured) && !contains(offer, count, featured)) offer.options[count++] = featured;
        int seed = world.runSeed ^ offer.level * 0x45d9f3b ^ slot * 0x27d4eb2d;
        seed ^= seed >>> 16; seed *= 0x45d9f3b; seed ^= seed >>> 16;
        int first = Math.floorMod(seed, UPGRADES.length);
        for (int i = 0; i < UPGRADES.length && count < offer.options.length; i++) {
            Upgrade candidate = UPGRADES[(first + i) % UPGRADES.length];
            if (available(player, candidate) && !contains(offer, count, candidate)) offer.options[count++] = candidate;
        }
        boolean complete = count == offer.options.length;
        for (int i = 0; i < offer.cards.length; i++) offer.cards[i] = complete ? preview(slot, offer.options[i]) : null;
        return complete;
    }

    /** Uses the same caps as apply(), without mutating simulation or depending on the phone. */
    CardPreview preview(int slot, Upgrade upgrade) {
        ArenaWorld.Player player = world.players[slot];
        String category, headline, before, after;
        float current, next;
        switch (upgrade) {
            case POWER -> {
                category = "OFFENSE";
                current = world.equipment.baseDamageMultiplier(player, player.damageMultiplier) * 100;
                next = world.equipment.baseDamageMultiplier(player, Math.min(4f, player.damageMultiplier + .25f)) * 100;
                headline = "+" + number(next - current) + "% BASE DAMAGE";
                before = number(current) + "% DAMAGE"; after = number(next) + "% DAMAGE";
            }
            case HASTE -> {
                category = "OFFENSE";
                current = world.equipment.baseAttackRate(player, player.attackRateMultiplier) * 100;
                next = world.equipment.baseAttackRate(player, Math.min(3f, player.attackRateMultiplier + .15f)) * 100;
                headline = "+" + number(next - current) + "% BASE ATTACK SPEED";
                before = number(current) + "% ATTACK SPEED"; after = number(next) + "% ATTACK SPEED";
            }
            case FLEET -> {
                category = "MOBILITY";
                current = world.equipment.moveMultiplier(player, player.moveMultiplier) * 100;
                next = world.equipment.moveMultiplier(player, Math.min(2.2f, player.moveMultiplier + .12f)) * 100;
                headline = "+" + number(next - current) + "% BASE MOVE SPEED";
                before = number(current) + "% MOVE SPEED"; after = number(next) + "% MOVE SPEED";
            }
            case VITALITY -> {
                category = "DEFENSE"; current = world.equipment.maxHealth(player);
                next = Math.max(20, Math.min(300f, player.maxHealth + 20f) + current - player.maxHealth);
                headline = "+" + number(next - current) + " MAX HP · HEAL UP TO 20";
                before = number(current) + " MAX HP"; after = number(next) + " MAX HP";
            }
            case EVASION -> {
                category = "MOBILITY";
                current = world.equipment.dodgeCooldown(player);
                next = world.equipment.dodgeCooldown(player, Math.max(.25f, player.dodgeCooldownMultiplier - .15f));
                headline = "-" + number(current - next) + "s DODGE COOLDOWN";
                before = number(current) + "s"; after = number(next) + "s";
            }
            case SWEEP -> {
                category = "WEAPON"; current = world.equipment.reachMultiplier(player) * 100;
                next = world.equipment.reachMultiplier(player, Math.min(1.8f, player.cleaveMultiplier + .2f)) * 100;
                headline = "+" + number(next - current) + "% BASE SWORD REACH";
                before = number(current) + "% SWORD REACH"; after = number(next) + "% SWORD REACH";
            }
            case MULTISHOT -> {
                category = "WEAPON"; int currentArrows = world.equipment.projectileCount(player);
                int arrows = Math.min(5, currentArrows + 1);
                headline = "+" + (arrows - currentArrows) + " ARROW";
                before = currentArrows + (currentArrows == 1 ? " ARROW" : " ARROWS");
                after = arrows + (arrows == 1 ? " ARROW" : " ARROWS");
            }
            case PIERCE -> {
                category = "WEAPON"; int currentPierce = world.equipment.pierce(player);
                int targets = Math.min(3, currentPierce + 1);
                headline = "+" + (targets - currentPierce) + " PIERCE TARGET";
                before = currentPierce + (currentPierce == 1 ? " EXTRA TARGET" : " EXTRA TARGETS");
                after = targets + (targets == 1 ? " EXTRA TARGET" : " EXTRA TARGETS");
            }
            case AURA -> {
                category = "OFFENSE"; int aura = Math.min(3, player.auraLevel + 1);
                current = (4 + player.auraLevel * 3) * world.equipment.baseDamageMultiplier(player, player.damageMultiplier) * world.equipment.auraMultiplier(player);
                next = (4 + aura * 3) * world.equipment.baseDamageMultiplier(player, player.damageMultiplier) * world.equipment.auraMultiplier(player);
                headline = player.auraLevel == 0 ? "UNLOCK" : "+" + number(next - current) + " AURA DAMAGE";
                before = player.auraLevel == 0 ? "NONE" : auraValues(current, player.auraLevel);
                after = auraValues(next, aura);
            }
            case STORM -> {
                category = "OFFENSE"; int storm = Math.min(3, player.stormLevel + 1);
                current = (12 + player.stormLevel * 5) * world.equipment.baseDamageMultiplier(player, player.damageMultiplier) * world.equipment.stormMultiplier(player);
                next = (12 + storm * 5) * world.equipment.baseDamageMultiplier(player, player.damageMultiplier) * world.equipment.stormMultiplier(player);
                headline = player.stormLevel == 0 ? "UNLOCK" : "+" + (storm - player.stormLevel) + " CHAIN TARGET";
                before = player.stormLevel == 0 ? "NONE" : stormValues(current, player.stormLevel);
                after = stormValues(next, storm);
            }
            case MAGNET -> {
                category = "UTILITY"; current = world.equipment.pickupMultiplier(player) * 100;
                next = world.equipment.pickupMultiplier(player, Math.min(2.5f, player.pickupMultiplier + .3f)) * 100;
                headline = "+" + number(next - current) + "% BASE PICKUP RANGE";
                before = number(current) + "% PICKUP RANGE"; after = number(next) + "% PICKUP RANGE";
            }
            case CRITICAL -> {
                category = "OFFENSE"; current = world.equipment.critChance(player) * 100;
                next = world.equipment.critChance(player, Math.min(.5f, player.critChance + .1f)) * 100;
                headline = "+" + number(next - current) + " PTS CRIT CHANCE";
                before = number(current) + "% CRIT"; after = number(next) + "% CRIT";
            }
            default -> throw new IllegalStateException("Unknown upgrade " + upgrade);
        }
        return new CardPreview(upgrade.ordinal(), rank(slot, upgrade) + 1, category, headline, before, after);
    }

    private static String auraValues(float damage, int level) {
        return number(damage) + " DMG / 0.7s / " + (100 + level * 20) + " RANGE";
    }
    private static String stormValues(float damage, int level) {
        return number(damage) + " DMG / " + (level + 1) + " TARGETS / 2s";
    }
    private static String number(float value) {
        int hundredths = Math.round(value * 100);
        int whole = hundredths / 100, fraction = Math.abs(hundredths % 100);
        return fraction == 0 ? Integer.toString(whole) : whole + "." + (fraction < 10 ? "0" : "")
            + (fraction % 10 == 0 ? fraction / 10 : fraction);
    }

    private static boolean contains(Offer offer, int count, Upgrade candidate) {
        for (int i = 0; i < count; i++) if (offer.options[i] == candidate) return true;
        return false;
    }

    private int newOfferId() {
        nextOfferId++;
        if (nextOfferId <= 0) nextOfferId = 1;
        return nextOfferId;
    }

    private void clearRanks(int slot) {
        for (int i = 0; i < ranks[slot].length; i++) ranks[slot][i] = 0;
    }

    private boolean available(ArenaWorld.Player player, Upgrade upgrade) {
        if (!upgrade.supports(player.hero)) return false;
        return switch (upgrade) {
            case POWER -> player.damageMultiplier < 4f - .00001f;
            case HASTE -> player.attackRateMultiplier < 3f - .00001f;
            case FLEET -> player.moveMultiplier < 2.2f - .00001f;
            case VITALITY -> player.maxHealth < 300f;
            case EVASION -> player.dodgeCooldownMultiplier > .25f + .00001f;
            case SWEEP -> player.cleaveMultiplier < 1.8f - .00001f;
            case MULTISHOT -> player.projectileCount < 5 && world.equipment.projectileCount(player) < 5;
            case PIERCE -> player.pierce < 3 && world.equipment.pierce(player) < 3;
            case AURA -> player.auraLevel < 3;
            case STORM -> player.stormLevel < 3;
            case MAGNET -> player.pickupMultiplier < 2.5f - .00001f;
            case CRITICAL -> player.critChance < .5f - .00001f && world.equipment.critChance(player) < .75f - .00001f;
        };
    }

    private void apply(ArenaWorld.Player player, Upgrade upgrade) {
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
                if (player.alive) player.health = Math.min(world.equipment.maxHealth(player), player.health + 20f);
            }
            case EVASION -> {
                float previous = player.dodgeCooldownMultiplier;
                player.dodgeCooldownMultiplier = Math.max(.25f, previous - .15f);
                player.dodgeCooldown *= player.dodgeCooldownMultiplier / previous;
            }
            case SWEEP -> player.cleaveMultiplier = Math.min(1.8f, player.cleaveMultiplier + .2f);
            case MULTISHOT -> player.projectileCount = Math.min(5, player.projectileCount + 1);
            case PIERCE -> player.pierce = Math.min(3, player.pierce + 1);
            case AURA -> player.auraLevel = Math.min(3, player.auraLevel + 1);
            case STORM -> player.stormLevel = Math.min(3, player.stormLevel + 1);
            case MAGNET -> player.pickupMultiplier = Math.min(2.5f, player.pickupMultiplier + .3f);
            case CRITICAL -> player.critChance = Math.min(.5f, player.critChance + .1f);
        }
    }

    private int threshold(int level) {
        // Keep the fast three-minute arena and smaller boundary fixtures useful for development.
        return world.durationSeconds() > ArenaTimeline.SHORT.durationSeconds()
            ? LONG_RUN_THRESHOLDS[level - 1] : 6 + (level - 1) * 4;
    }
    private static boolean validSlot(int slot) { return slot >= 0 && slot < 2; }
}
