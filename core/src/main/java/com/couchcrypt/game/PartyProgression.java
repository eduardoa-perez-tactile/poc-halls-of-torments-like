package com.couchcrypt.game;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/** Shared party XP and one party choice clock, with personal builds owned by simulation. */
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
        MULTISHOT("multishot", "Split Shot", "The magic crossbow fires one extra arcane bolt."),
        PIERCE("pierce", "Piercing Bolt", "Arcane bolts pierce one more target."),
        AURA("aura", "Soul Circle", "Unlock or strengthen a nearby damage aura."),
        STORM("storm", "Chain Storm", "Unlock or strengthen automatic chain strikes."),
        MAGNET("magnet", "Soul Draw", "Pickup attraction +30% of base."),
        CRITICAL("critical", "Critical Edge", "Critical hit chance +10 percentage points."),
        RICOCHET("ricochet", "Ricochet Bolt", "Redirect spent bolts toward new targets with reduced damage."),
        SENTINEL("sentinel", "Sentinel Blades", "Orbiting blades strike nearby enemies on contact."),
        CONSECRATION("consecration", "Consecrated Ground", "Automatically place short-lived damaging seals beneath enemies."),
        GLAIVE("glaive", "Returning Glaive", "Throw a returning blade that hits once on each leg."),
        WARD("ward", "Soul Ward", "Absorb one damaging hit, then recharge the protective ward."),
        FROST("frost", "Frostbound Strikes", "Primary attacks briefly slow enemies; elites and bosses resist."),
        PROJECTILE_SPEED("projectile_speed", "Swift Projectiles", "Projectile travel speed +10% of base; travel limits stay unchanged."),
        DURATION("duration", "Lingering Power", "Friendly ground fields and slows last 15% longer per rank."),
        ECHO_STORM("echo_storm", "Echo Storm", "Requires Chain Storm III. Strikes echo at their impact locations."),
        BINDING_CIRCLE("binding_circle", "Binding Circle", "Requires Soul Circle III. Pulses briefly slow nearby enemies."),
        SHATTER_BOLT("shatter_bolt", "Shatter Bolt", "Requires 3 bolt pierces. Final bolt impacts release a compact burst."),
        REAPING_STROKE("reaping_stroke", "Reaping Stroke", "Requires maximum Wide Sweep. Sword attacks gain a weaker follow-up.");

        public final String id, title, description;
        Upgrade(String id, String title, String description) {
            this.id = id; this.title = title; this.description = description;
        }

        /** Class eligibility is independent of the current run's upgrade caps. */
        public boolean supports(ArenaWorld.Hero hero) {
            return switch (this) {
                case SWEEP, SENTINEL, REAPING_STROKE -> hero == ArenaWorld.Hero.WARDEN;
                case MULTISHOT, PIERCE, RICOCHET, SHATTER_BOLT -> hero == ArenaWorld.Hero.RANGER;
                default -> true;
            };
        }

        public boolean evolution() {
            return this == ECHO_STORM || this == BINDING_CIRCLE || this == SHATTER_BOLT || this == REAPING_STROKE;
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
        Upgrade.POWER, Upgrade.SWEEP, Upgrade.SENTINEL, Upgrade.AURA, Upgrade.HASTE,
        Upgrade.CONSECRATION, Upgrade.GLAIVE, Upgrade.VITALITY, Upgrade.WARD, Upgrade.FROST,
        Upgrade.SWEEP, Upgrade.SWEEP, Upgrade.SWEEP, Upgrade.REAPING_STROKE,
        Upgrade.AURA, Upgrade.AURA, Upgrade.BINDING_CIRCLE, Upgrade.STORM, Upgrade.DURATION
    };
    private static final Upgrade[] RANGER_FOUNDATION = {
        Upgrade.POWER, Upgrade.MULTISHOT, Upgrade.RICOCHET, Upgrade.AURA, Upgrade.HASTE,
        Upgrade.CONSECRATION, Upgrade.GLAIVE, Upgrade.VITALITY, Upgrade.WARD, Upgrade.FROST,
        Upgrade.PIERCE, Upgrade.PIERCE, Upgrade.PIERCE, Upgrade.SHATTER_BOLT,
        Upgrade.AURA, Upgrade.AURA, Upgrade.BINDING_CIRCLE, Upgrade.STORM, Upgrade.PROJECTILE_SPEED
    };
    private final ArenaWorld world;
    private final boolean[] participants = new boolean[2];
    private final int[] applied = new int[2];
    private final int[] catchUpUntil = new int[2], foundationCounts = new int[2];
    private final String[] foundations = {"", ""};
    private final List<List<FoundationSkill>> foundationSkills = Arrays.asList(List.of(), List.of());
    private final int[][] ranks = { new int[UPGRADES.length], new int[UPGRADES.length] };
    private final int[][] evolutionLastShown = { new int[UPGRADES.length], new int[UPGRADES.length] };
    private int nextOfferId;
    private float choiceSecondsLeft;
    private boolean resolvingTimeout;
    public int level, xp, xpToNext, totalXp;
    /** Presentation identity for a shared skill-choice round; individual acknowledgments do not change it. */
    public int choiceRound;
    public final Offer[] offers = { new Offer(), new Offer() };

    public PartyProgression(ArenaWorld world) { this.world = world; reset(); }

    /** Identities survive reset, so a packet from the previous run cannot select a new offer. */
    public void reset() {
        level = 1; xp = totalXp = choiceRound = 0; xpToNext = threshold(level);
        choiceSecondsLeft = 0; resolvingTimeout = false;
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
        startNextRound();
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
        startNextRound();
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
        }
        xp = level == MAX_LEVEL ? 0 : (int) available;
        startNextRound();
    }

    /** Pause freezes this clock. A long delta resolves one party round, never skips queued rounds. */
    public void update(float delta) {
        if (!Float.isFinite(delta) || delta <= 0) return;
        startNextRound();
        if (!hasPendingOffers()) return;
        choiceSecondsLeft = Math.max(0f, choiceSecondsLeft - delta);
        for (int slot = 0; slot < offers.length; slot++) {
            Offer offer = offers[slot];
            if (offer.pending) offer.secondsLeft = choiceSecondsLeft;
        }
        if (choiceSecondsLeft <= .0001f) {
            resolvingTimeout = true;
            for (int slot = 0; slot < offers.length; slot++)
                if (offers[slot].pending) choose(slot, offers[slot].id, 0);
            resolvingTimeout = false;
            startNextRound();
        }
    }

    /** One authoritative deadline for every outstanding offer in this party round. */
    public float choiceSecondsLeft() { return choiceSecondsLeft; }

    /** Only a player's current offer can be consumed, once. The host supplies the slot. */
    public boolean choose(int slot, int offerId, int optionIndex) {
        if (!validSlot(slot) || !participants[slot] || !world.players[slot].active) return false;
        Offer offer = offers[slot];
        if (!offer.pending || offer.id != offerId || optionIndex < 0 || optionIndex >= offer.options.length) return false;
        Upgrade upgrade = offer.options[optionIndex];
        if (!available(world.players[slot], upgrade)) {
            // External stat/hero changes invalidate an old selection without silently choosing another skill.
            offer.id = newOfferId();
            if (!populateOptions(slot)) {
                offer.pending = false; applied[slot] = level - 1;
                startNextRound();
            }
            return false;
        }
        apply(world.players[slot], upgrade);
        ranks[slot][upgrade.ordinal()]++;
        applied[slot]++;
        offer.pending = false; offer.secondsLeft = 0;
        startNextRound();
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

    private boolean hasPendingOffers() {
        for (Offer offer : offers) if (offer.pending) return true;
        return false;
    }

    /** Everyone finishes the current round before any queued choice gets its fresh thirty seconds. */
    private void startNextRound() {
        if (resolvingTimeout || hasPendingOffers()) return;
        choiceSecondsLeft = 0;
        for (int slot = 0; slot < offers.length; slot++) startNextOffer(slot);
        if (hasPendingOffers()) {
            choiceSecondsLeft = CHOICE_SECONDS; choiceRound++;
            world.beginLevelUpFx(choiceRound);
        }
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
        // Protect an evolution slot, rotating skipped eligible evolutions so one cannot hide the others.
        Upgrade evolution = null;
        for (Upgrade candidate : UPGRADES)
            if (candidate.evolution() && available(player, candidate)
                && (evolution == null || evolutionLastShown[slot][candidate.ordinal()]
                    < evolutionLastShown[slot][evolution.ordinal()])) evolution = candidate;
        if (evolution != null) offer.options[count++] = evolution;
        boolean injured = player.health < world.equipment.maxHealth(player) * .6f;
        Upgrade recommended = injured ? Upgrade.VITALITY : offer.level <= 4 ? Upgrade.POWER : null;
        if (recommended != null && available(player, recommended) && !contains(offer, count, recommended))
            offer.options[count++] = recommended;
        Upgrade featured = switch (offer.level) {
            case 2 -> player.hero == ArenaWorld.Hero.WARDEN ? Upgrade.SWEEP : Upgrade.MULTISHOT;
            case 3 -> Upgrade.AURA;
            case 4 -> Upgrade.STORM;
            case 5 -> player.hero == ArenaWorld.Hero.WARDEN ? Upgrade.SENTINEL : Upgrade.RICOCHET;
            case 6 -> Upgrade.CONSECRATION;
            case 7 -> Upgrade.GLAIVE;
            case 8 -> Upgrade.WARD;
            case 9 -> Upgrade.FROST;
            default -> null;
        };
        if (featured != null && count < offer.options.length && available(player, featured)
            && !contains(offer, count, featured)) offer.options[count++] = featured;
        int seed = mix(world.runSeed ^ offer.level * 0x45d9f3b ^ slot * 0x27d4eb2d);
        while (count < offer.options.length) {
            int totalWeight = 0;
            for (Upgrade candidate : UPGRADES)
                if (available(player, candidate) && !contains(offer, count, candidate))
                    totalWeight += offerWeight(slot, candidate, offer.level);
            if (totalWeight == 0) break;
            seed = mix(seed + 0x6d2b79f5);
            int ticket = Math.floorMod(seed, totalWeight);
            for (Upgrade candidate : UPGRADES) {
                if (!available(player, candidate) || contains(offer, count, candidate)) continue;
                ticket -= offerWeight(slot, candidate, offer.level);
                if (ticket < 0) { offer.options[count++] = candidate; break; }
            }
        }
        boolean complete = count == offer.options.length;
        for (int i = 0; i < offer.cards.length; i++) {
            offer.cards[i] = complete ? preview(slot, offer.options[i]) : null;
            if (complete && offer.options[i].evolution()) evolutionLastShown[slot][offer.options[i].ordinal()] = offer.level;
        }
        return complete;
    }

    private int offerWeight(int slot, Upgrade candidate, int offeredLevel) {
        int rank = rank(slot, candidate);
        // Discovery stays available; later choices increasingly support abilities already selected.
        if (candidate.evolution()) return 12;
        if (rank > 0) return offeredLevel <= 5 ? 3 : 10;
        if (candidate.ordinal() >= Upgrade.RICOCHET.ordinal()) return offeredLevel <= 9 ? 7 : 3;
        return 4;
    }

    private static int mix(int value) {
        value ^= value >>> 16; value *= 0x45d9f3b; value ^= value >>> 16;
        return value;
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
                headline = "+" + (arrows - currentArrows) + " ARCANE BOLT";
                before = currentArrows + (currentArrows == 1 ? " BOLT" : " BOLTS");
                after = arrows + (arrows == 1 ? " BOLT" : " BOLTS");
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
            case RICOCHET -> {
                category = "WEAPON";
                headline = player.ricochetLevel == 0 ? "UNLOCK RICOCHETING BOLTS" : "+1 BOLT REDIRECT";
                before = player.ricochetLevel == 0 ? "NONE" : player.ricochetLevel + " REDIRECTS / 75% EACH BOUNCE";
                after = Math.min(3, player.ricochetLevel + 1) + " REDIRECTS / 75% EACH BOUNCE";
            }
            case SENTINEL -> {
                category = "WEAPON";
                headline = player.sentinelLevel == 0 ? "UNLOCK ORBITING BLADES" : "+1 BLADE / +4 BASE CONTACT DAMAGE";
                before = player.sentinelLevel == 0 ? "NONE" : sentinelValues(player, player.sentinelLevel);
                after = sentinelValues(player, Math.min(3, player.sentinelLevel + 1));
            }
            case CONSECRATION -> {
                category = "OFFENSE";
                headline = player.consecrationLevel == 0 ? "UNLOCK DAMAGING GROUND SEALS" : "+1 SEAL / FASTER, STRONGER CASTS";
                before = player.consecrationLevel == 0 ? "NONE" : fieldValues(player, player.consecrationLevel);
                after = fieldValues(player, Math.min(3, player.consecrationLevel + 1));
            }
            case GLAIVE -> {
                category = "WEAPON";
                headline = player.glaiveLevel == 0 ? "UNLOCK A RETURNING BLADE" : "+6 BASE DAMAGE / +40 REACH";
                before = player.glaiveLevel == 0 ? "NONE" : glaiveValues(player, player.glaiveLevel);
                after = glaiveValues(player, Math.min(3, player.glaiveLevel + 1));
            }
            case WARD -> {
                category = "DEFENSE";
                headline = player.wardLevel == 0 ? "UNLOCK ONE-HIT PROTECTIVE WARD" : "WARD RECHARGE -3s";
                before = player.wardLevel == 0 ? "NONE" : "1 HIT / " + number(SkillExpansion.wardCooldown(player.wardLevel)) + "s RECHARGE";
                after = "1 HIT / " + number(SkillExpansion.wardCooldown(Math.min(3, player.wardLevel + 1))) + "s RECHARGE";
            }
            case FROST -> {
                category = "OFFENSE";
                headline = player.frostLevel == 0 ? "PRIMARY HITS SLOW ENEMIES" : "+5 PTS SLOW / +0.25s BASE DURATION";
                before = player.frostLevel == 0 ? "NONE" : frostValues(player, player.frostLevel);
                after = frostValues(player, Math.min(3, player.frostLevel + 1));
            }
            case PROJECTILE_SPEED -> {
                category = "WEAPON"; current = SkillExpansion.projectileSpeed(player) * 100;
                next = (1 + .1f * Math.min(3, player.projectileSpeedLevel + 1)) * 100;
                headline = "+10% BASE PROJECTILE SPEED";
                before = number(current) + "% SPEED / SAME TRAVEL LIMIT";
                after = number(next) + "% SPEED / SAME TRAVEL LIMIT";
            }
            case DURATION -> {
                category = "UTILITY"; current = SkillExpansion.duration(player) * 100;
                next = (1 + .15f * Math.min(3, player.durationLevel + 1)) * 100;
                headline = "+15% BASE FIELD / SLOW DURATION";
                before = number(current) + "% DURATION / FIELDS AND SLOWS";
                after = number(next) + "% DURATION / FIELDS AND SLOWS";
            }
            case ECHO_STORM -> {
                category = "OFFENSE"; headline = "EVOLVE CHAIN STORM III";
                before = "SINGLE STRIKE PER CHAIN TARGET";
                after = "45% ECHO / 0.3s DELAY / 42 AREA";
            }
            case BINDING_CIRCLE -> {
                category = "OFFENSE"; headline = "EVOLVE SOUL CIRCLE III";
                before = "DAMAGE PULSES";
                after = "20% SLOW / " + number(Math.min(4, 1.1f * SkillExpansion.duration(player))) + "s / BOSS-RESISTED";
            }
            case SHATTER_BOLT -> {
                category = "WEAPON"; headline = "EVOLVE 3 BOLT PIERCES";
                before = "PIERCING BOLTS";
                after = "FINAL HIT: 45% BURST / 65 AREA";
            }
            case REAPING_STROKE -> {
                category = "WEAPON"; headline = "EVOLVE MAXIMUM WIDE SWEEP";
                before = "ONE SWORD STRIKE";
                after = "40% FOLLOW-UP / 0.18s DELAY";
            }
            default -> throw new IllegalStateException("Unknown upgrade " + upgrade);
        }
        return new CardPreview(upgrade.ordinal(), rank(slot, upgrade) + 1, category, headline, before, after);
    }

    private String sentinelValues(ArenaWorld.Player player, int rank) {
        return SkillExpansion.sentinelCount(rank) + " BLADES / "
            + number(SkillExpansion.sentinelDamage(rank) * world.equipment.baseDamageMultiplier(player, player.damageMultiplier)) + " DMG / 0.6s";
    }
    private String fieldValues(ArenaWorld.Player player, int rank) {
        return number(SkillExpansion.fieldDamage(rank) * world.equipment.baseDamageMultiplier(player, player.damageMultiplier)) + " DMG / 0.5s / "
            + number(Math.min(8, SkillExpansion.fieldDuration(rank) * SkillExpansion.duration(player))) + "s / "
            + SkillExpansion.fieldCount(rank) + " SEALS";
    }
    private String glaiveValues(ArenaWorld.Player player, int rank) {
        return number(SkillExpansion.glaiveDamage(rank) * world.equipment.baseDamageMultiplier(player, player.damageMultiplier)) + " DMG / "
            + number(SkillExpansion.glaiveRange(rank)) + " REACH / 3.2s";
    }
    private static String frostValues(ArenaWorld.Player player, int rank) {
        return number(SkillExpansion.frostAmount(rank) * 100) + "% SLOW / "
            + number(Math.min(4, SkillExpansion.frostDuration(rank) * SkillExpansion.duration(player))) + "s / RESISTED BY BOSSES";
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
        for (int i = 0; i < ranks[slot].length; i++) {
            ranks[slot][i] = 0; evolutionLastShown[slot][i] = 0;
        }
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
            case RICOCHET -> player.ricochetLevel < 3;
            case SENTINEL -> player.sentinelLevel < 3;
            case CONSECRATION -> player.consecrationLevel < 3;
            case GLAIVE -> player.glaiveLevel < 3;
            case WARD -> player.wardLevel < 3;
            case FROST -> player.frostLevel < 3;
            case PROJECTILE_SPEED -> player.projectileSpeedLevel < 3
                && (player.hero == ArenaWorld.Hero.RANGER || player.glaiveLevel > 0);
            case DURATION -> player.durationLevel < 3
                && (player.consecrationLevel > 0 || player.frostLevel > 0 || player.bindingCircle);
            case ECHO_STORM -> player.stormLevel >= 3 && !player.echoStorm;
            case BINDING_CIRCLE -> player.auraLevel >= 3 && !player.bindingCircle;
            case SHATTER_BOLT -> world.equipment.pierce(player) >= 3 && !player.shatterBolt;
            case REAPING_STROKE -> player.cleaveMultiplier >= 1.8f - .00001f && !player.reapingStroke;
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
            case RICOCHET -> player.ricochetLevel = Math.min(3, player.ricochetLevel + 1);
            case SENTINEL -> player.sentinelLevel = Math.min(3, player.sentinelLevel + 1);
            case CONSECRATION -> player.consecrationLevel = Math.min(3, player.consecrationLevel + 1);
            case GLAIVE -> player.glaiveLevel = Math.min(3, player.glaiveLevel + 1);
            case WARD -> player.wardLevel = Math.min(3, player.wardLevel + 1);
            case FROST -> player.frostLevel = Math.min(3, player.frostLevel + 1);
            case PROJECTILE_SPEED -> player.projectileSpeedLevel = Math.min(3, player.projectileSpeedLevel + 1);
            case DURATION -> player.durationLevel = Math.min(3, player.durationLevel + 1);
            case ECHO_STORM -> player.echoStorm = true;
            case BINDING_CIRCLE -> player.bindingCircle = true;
            case SHATTER_BOLT -> player.shatterBolt = true;
            case REAPING_STROKE -> player.reapingStroke = true;
        }
    }

    private int threshold(int level) {
        // Keep the fast three-minute arena and smaller boundary fixtures useful for development.
        return world.durationSeconds() > ArenaTimeline.SHORT.durationSeconds()
            ? LONG_RUN_THRESHOLDS[level - 1] : 6 + (level - 1) * 4;
    }
    private static boolean validSlot(int slot) { return slot >= 0 && slot < 2; }
}
