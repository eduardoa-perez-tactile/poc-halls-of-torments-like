package com.couchcrypt.game;

import java.util.Arrays;
import static com.couchcrypt.game.ItemCatalog.Stat.*;

/** Bounded run-only equipment. Skill stats remain untouched; combat reads effective values here. */
public final class RunEquipment {
    public static final int SLOT_COUNT = 7, MAX_REWARDS = 8;
    public static final float CHOICE_SECONDS = 30f;
    private static final ItemCatalog.Stat[] STATS = ItemCatalog.Stat.values();
    private static final String[] SLOT_NAMES = {"HELMET", "AMULET", "RING I", "RING II", "CHEST", "BOOTS", "GLOVES"};
    public static final class Offer {
        public boolean pending;
        public int id;
        public float secondsLeft;
        public final ItemCatalog.Item[] options = new ItemCatalog.Item[3];
        public final PartyProgression.CardPreview[] cards = new PartyProgression.CardPreview[3];
        private final int[] targets = new int[3];
    }
    private final ArenaWorld world;
    private final ItemCatalog.Item[][] worn = {new ItemCatalog.Item[SLOT_COUNT], new ItemCatalog.Item[SLOT_COUNT]};
    private final float[][] totals = {new float[STATS.length], new float[STATS.length]};
    private final int[] applied = new int[2], previewSkillCount = new int[2];
    private final boolean[] participants = new boolean[2], moving = new boolean[2];
    private final float[] sustain = new float[2], xpRemainder = new float[2];
    private int earned, nextId, cacheIndex, eliteRewards;
    public final Offer[] offers = {new Offer(), new Offer()};

    public RunEquipment(ArenaWorld world) { this.world = world; }
    public void reset() {
        earned = cacheIndex = eliteRewards = 0;
        for (int slot = 0; slot < 2; slot++) clearPlayer(slot);
    }
    private void clearPlayer(int slot) {
        participants[slot] = moving[slot] = false; applied[slot] = 0;
        sustain[slot] = 2f; xpRemainder[slot] = 0;
        Arrays.fill(worn[slot], null); Arrays.fill(totals[slot], 0);
        Offer offer = offers[slot]; offer.pending = false; offer.id = 0; offer.secondsLeft = 0;
        Arrays.fill(offer.options, null); Arrays.fill(offer.cards, null);
    }
    public void activatePlayer(int slot) {
        if (!valid(slot) || participants[slot]) return;
        participants[slot] = true; startNext(slot);
    }
    public void deactivatePlayer(int slot) { if (valid(slot)) clearPlayer(slot); }
    public int rewardsEarned() { return earned; }
    public int appliedCount(int slot) { return valid(slot) ? applied[slot] : 0; }
    public ItemCatalog.Item equipped(int slot, int position) {
        return valid(slot) && position >= 0 && position < SLOT_COUNT ? worn[slot][position] : null;
    }
    public static String slotName(int position) { return SLOT_NAMES[position]; }

    /** Authoritative party entitlement, shared once and retained for later joining. */
    public void grantReward() {
        if (earned >= MAX_REWARDS) return;
        earned++;
        for (int slot = 0; slot < 2; slot++) startNext(slot);
    }
    public void rewardElite() { if (eliteRewards < 5) { eliteRewards++; grantReward(); } }
    public void update(float delta) {
        if (!Float.isFinite(delta) || delta <= 0) return;
        float duration = world.durationSeconds();
        while (cacheIndex < 3 && world.elapsed >= duration * (cacheIndex == 0 ? .075f : cacheIndex == 1 ? .5f : .95f)) {
            cacheIndex++; grantReward();
        }
        for (int slot = 0; slot < 2; slot++) {
            sustain[slot] = Math.min(2f, sustain[slot] + delta * 2f);
            Offer offer = offers[slot];
            if (!offer.pending || world.progression.offers[slot].pending) continue;
            if (previewSkillCount[slot] != world.progression.appliedUpgradeCount(slot)) refreshOffer(slot);
            if (!offer.pending) continue;
            offer.secondsLeft = Math.max(0, offer.secondsLeft - delta);
            if (offer.secondsLeft <= .0001f) choose(slot, offer.id, 0);
        }
    }
    public boolean choosing(int slot) { return world.progression.offers[slot].pending || offers[slot].pending; }
    public boolean choose(int slot, int id, int option) {
        if (!valid(slot) || !participants[slot] || !world.players[slot].active || world.progression.offers[slot].pending) return false;
        Offer offer = offers[slot];
        if (!offer.pending || offer.id != id || option < -1 || option >= 3) return false;
        if (option >= 0 && refreshOffer(slot)) return false;
        if (option >= 0) equip(slot, offer.targets[option], offer.options[option]);
        applied[slot]++; offer.pending = false; offer.secondsLeft = 0;
        startNext(slot); return true;
    }
    /** A level choice can cap an item hidden behind it. Refresh once after skill changes, not every tick. */
    private boolean refreshOffer(int slot) {
        previewSkillCount[slot] = world.progression.appliedUpgradeCount(slot);
        Offer offer = offers[slot];
        for (ItemCatalog.Item item : offer.options) if (!eligible(slot, item)) {
            float remaining = offer.secondsLeft;
            offer.pending = false; startNext(slot);
            offer.secondsLeft = remaining;
            return true;
        }
        return false;
    }
    private void startNext(int slot) {
        Offer offer = offers[slot];
        if (!participants[slot] || offer.pending || applied[slot] >= earned) return;
        int seed = world.runSeed ^ (applied[slot] + 1) * 0x45d9f3b ^ slot * 0x27d4eb2d;
        int first = Math.floorMod(seed ^ seed >>> 16, ItemCatalog.ALL.size());
        int count = 0;
        // Empty slots lead the default; later replacements still vary by replay seed.
        for (int pass = 0; pass < 2 && count < 3; pass++) {
            for (int i = 0; i < ItemCatalog.ALL.size() && count < 3; i++) {
                ItemCatalog.Item item = ItemCatalog.ALL.get((first + i) % ItemCatalog.ALL.size());
                if (!eligible(slot, item)) continue;
                int target = target(slot, item.slot());
                if ((worn[slot][target] == null) != (pass == 0)) continue;
                boolean repeated = false;
                for (int j = 0; j < count; j++) repeated |= offer.options[j].slot() == item.slot();
                if (repeated) continue;
                offer.options[count] = item; offer.targets[count] = target;
                ItemCatalog.Item old = worn[slot][target];
                String headline = old == null ? "EQUIP " + SLOT_NAMES[target] : SLOT_NAMES[target] + ": REPLACES " + old.title();
                offer.cards[count] = new PartyProgression.CardPreview(item.icon(), 0, category(item.slot()), headline,
                    old == null ? "EMPTY" : modifiers(old), modifiers(item));
                count++;
            }
        }
        if (count < 3) return;
        if (++nextId <= 0) nextId = 1;
        offer.id = nextId; offer.pending = true; offer.secondsLeft = CHOICE_SECONDS;
        previewSkillCount[slot] = world.progression.appliedUpgradeCount(slot);
    }
    private boolean eligible(int slot, ItemCatalog.Item item) {
        ArenaWorld.Player player = world.players[slot];
        if (!item.supports(player.hero) || !hasUsefulBonus(player, item)) return false;
        if (item.amount(AURA_DAMAGE) != 0 && player.auraLevel == 0) return false;
        if (item.amount(STORM_DAMAGE) != 0 && player.stormLevel == 0) return false;
        for (ItemCatalog.Item held : worn[slot]) if (held == item) return false;
        return true;
    }
    private boolean hasUsefulBonus(ArenaWorld.Player player, ItemCatalog.Item item) {
        for (ItemCatalog.Modifier modifier : item.modifiers()) {
            if (modifier.amount() <= 0) continue;
            boolean useful = switch (modifier.stat()) {
                case PROJECTILES -> projectileCount(player) < 5;
                case PIERCE -> pierce(player) < 3;
                case DAMAGE -> bonus(player.slot, DAMAGE) < .6f;
                case ATTACK_SPEED -> bonus(player.slot, ATTACK_SPEED) < .5f;
                case MOVE_SPEED -> bonus(player.slot, MOVE_SPEED) < .4f;
                case MAX_HEALTH -> bonus(player.slot, MAX_HEALTH) < 120;
                case DODGE_COOLDOWN -> dodgeCooldown(player) > .45f && bonus(player.slot, DODGE_COOLDOWN) < .3f;
                case REACH -> bonus(player.slot, REACH) < .3f;
                case AURA_DAMAGE -> bonus(player.slot, AURA_DAMAGE) < .6f;
                case STORM_DAMAGE -> bonus(player.slot, STORM_DAMAGE) < .6f;
                case PICKUP_RANGE -> bonus(player.slot, PICKUP_RANGE) < .5f;
                case CRIT_CHANCE -> critChance(player) < .75f;
                case CRIT_DAMAGE -> bonus(player.slot, CRIT_DAMAGE) < .75f;
                case DAMAGE_REDUCTION -> damageReduction(player) < .4f;
                case REGENERATION -> regeneration(player) < 1.5f;
                case LIFE_STEAL -> bonus(player.slot, LIFE_STEAL) < .025f;
                case THORNS -> thorns(player) < 25;
                case XP_GAIN -> bonus(player.slot, XP_GAIN) < .25f;
                case KILL_HEAL -> bonus(player.slot, KILL_HEAL) < 1;
                case BLOCK_CHANCE -> blockChance(player) < .2f;
                case LOW_HEALTH_DAMAGE, STILL_DAMAGE -> bonus(player.slot, DAMAGE) + bonus(player.slot, modifier.stat()) < .6f;
                case MOVING_HASTE -> bonus(player.slot, ATTACK_SPEED) + bonus(player.slot, MOVING_HASTE) < .5f;
            };
            if (useful) return true;
        }
        return false;
    }
    private int target(int slot, ItemCatalog.Slot type) {
        return switch (type) {
            case HELMET -> 0; case AMULET -> 1; case CHEST -> 4; case BOOTS -> 5; case GLOVES -> 6;
            case RING -> worn[slot][2] == null ? 2 : worn[slot][3] == null ? 3 : 2 + applied[slot] % 2;
        };
    }
    void equip(int slot, int position, ItemCatalog.Item item) {
        worn[slot][position] = item; Arrays.fill(totals[slot], 0);
        for (ItemCatalog.Item held : worn[slot]) if (held != null)
            for (ItemCatalog.Modifier modifier : held.modifiers()) totals[slot][modifier.stat().ordinal()] += modifier.amount();
        ArenaWorld.Player player = world.players[slot];
        player.health = Math.min(player.health, maxHealth(player));
    }
    private static String category(ItemCatalog.Slot slot) {
        return switch (slot) { case HELMET, CHEST -> "DEFENSE"; case BOOTS -> "MOBILITY"; case GLOVES, RING -> "OFFENSE"; default -> "UTILITY"; };
    }
    static String modifiers(ItemCatalog.Item item) {
        StringBuilder result = new StringBuilder(96);
        for (ItemCatalog.Modifier modifier : item.modifiers()) {
            if (result.length() > 0) result.append(" / ");
            float value = modifier.amount(); ItemCatalog.Stat stat = modifier.stat();
            boolean absolute = stat == MAX_HEALTH || stat == REGENERATION || stat == THORNS || stat == KILL_HEAL || stat == PROJECTILES || stat == PIERCE;
            if (value > 0) result.append('+');
            result.append(number(absolute ? value : value * 100));
            if (!absolute) result.append('%');
            result.append(' ').append(switch (stat) {
                case DAMAGE -> "DMG"; case ATTACK_SPEED -> "ATK SPD"; case MOVE_SPEED -> "MOVE"; case MAX_HEALTH -> "HP";
                case DODGE_COOLDOWN -> "DODGE RED"; case REACH -> "REACH"; case PROJECTILES -> "ARROWS"; case PIERCE -> "PIERCE";
                case AURA_DAMAGE -> "CIRCLE"; case STORM_DAMAGE -> "STORM"; case PICKUP_RANGE -> "PICKUP"; case CRIT_CHANCE -> "CRIT";
                case CRIT_DAMAGE -> "CRIT DMG"; case DAMAGE_REDUCTION -> "DMG RED"; case REGENERATION -> "HP/s";
                case LIFE_STEAL -> "LIFE STEAL"; case THORNS -> "THORNS"; case XP_GAIN -> "XP"; case KILL_HEAL -> "HP/KILL";
                case BLOCK_CHANCE -> "BLOCK"; case LOW_HEALTH_DAMAGE -> "DMG <=40% HP"; case MOVING_HASTE -> "MOVING ATK"; case STILL_DAMAGE -> "STILL DMG";
            });
        }
        return result.toString();
    }
    private static String number(float value) {
        int hundredths = Math.round(value * 100);
        int whole = hundredths / 100, fraction = Math.abs(hundredths % 100);
        if (fraction == 0) return Integer.toString(whole);
        String sign = hundredths < 0 && whole == 0 ? "-" : "";
        return sign + whole + "." + (fraction < 10 ? "0" : "")
            + (fraction % 10 == 0 ? fraction / 10 : fraction);
    }
    public float bonus(int slot, ItemCatalog.Stat stat) { return totals[slot][stat.ordinal()]; }
    public float maxHealth(ArenaWorld.Player player) { return Math.max(20, player.maxHealth + clamp(bonus(player.slot, MAX_HEALTH), -60, 120)); }
    public float damageMultiplier(ArenaWorld.Player player) {
        float conditional = player.health <= maxHealth(player) * .4f ? bonus(player.slot, LOW_HEALTH_DAMAGE) : 0;
        if (!moving[player.slot]) conditional += bonus(player.slot, STILL_DAMAGE);
        return player.damageMultiplier * (1 + clamp(bonus(player.slot, DAMAGE) + conditional, -.35f, .6f));
    }
    public float baseDamageMultiplier(ArenaWorld.Player player, float skillValue) { return skillValue * (1 + clamp(bonus(player.slot, DAMAGE), -.35f, .6f)); }
    public float baseAttackRate(ArenaWorld.Player player, float skillValue) { return skillValue * (1 + clamp(bonus(player.slot, ATTACK_SPEED), -.35f, .5f)); }
    public float attackRate(ArenaWorld.Player player) { return attackRate(player, player.attackRateMultiplier); }
    public float attackRate(ArenaWorld.Player player, float skillValue) { return skillValue * (1 + clamp(bonus(player.slot, ATTACK_SPEED) + (moving[player.slot] ? bonus(player.slot, MOVING_HASTE) : 0), -.35f, .5f)); }
    public float moveMultiplier(ArenaWorld.Player player) { return moveMultiplier(player, player.moveMultiplier); }
    public float moveMultiplier(ArenaWorld.Player player, float skillValue) { return skillValue * (1 + clamp(bonus(player.slot, MOVE_SPEED), -.25f, .4f)); }
    public float dodgeCooldown(ArenaWorld.Player player) { return dodgeCooldown(player, player.dodgeCooldownMultiplier); }
    public float dodgeCooldown(ArenaWorld.Player player, float skillValue) { return Math.max(.45f, player.hero.dodgeCooldown * skillValue * (1 - clamp(bonus(player.slot, DODGE_COOLDOWN), -.25f, .3f))); }
    public float reachMultiplier(ArenaWorld.Player player) { return reachMultiplier(player, player.cleaveMultiplier); }
    public float reachMultiplier(ArenaWorld.Player player, float skillValue) { return skillValue * (1 + clamp(bonus(player.slot, REACH), -.2f, .3f)); }
    public int projectileCount(ArenaWorld.Player player) { return Math.max(1, Math.min(5, player.projectileCount + Math.round(bonus(player.slot, PROJECTILES)))); }
    public int pierce(ArenaWorld.Player player) { return Math.max(0, Math.min(3, player.pierce + Math.round(bonus(player.slot, PIERCE)))); }
    public float pickupMultiplier(ArenaWorld.Player player) { return pickupMultiplier(player, player.pickupMultiplier); }
    public float pickupMultiplier(ArenaWorld.Player player, float skillValue) { return skillValue * (1 + clamp(bonus(player.slot, PICKUP_RANGE), -.2f, .5f)); }
    public float critChance(ArenaWorld.Player player) { return critChance(player, player.critChance); }
    public float critChance(ArenaWorld.Player player, float skillValue) { return clamp(skillValue + bonus(player.slot, CRIT_CHANCE), 0, .75f); }
    public float critDamage(ArenaWorld.Player player) { return 2 + clamp(bonus(player.slot, CRIT_DAMAGE), -.5f, .75f); }
    public float auraMultiplier(ArenaWorld.Player player) { return 1 + clamp(bonus(player.slot, AURA_DAMAGE), -.3f, .6f); }
    public float stormMultiplier(ArenaWorld.Player player) { return 1 + clamp(bonus(player.slot, STORM_DAMAGE), -.3f, .6f); }
    public float damageReduction(ArenaWorld.Player player) { return clamp(bonus(player.slot, DAMAGE_REDUCTION), 0, .4f); }
    public float blockChance(ArenaWorld.Player player) { return clamp(bonus(player.slot, BLOCK_CHANCE), 0, .2f); }
    public float thorns(ArenaWorld.Player player) { return clamp(bonus(player.slot, THORNS), 0, 25); }
    public float regeneration(ArenaWorld.Player player) { return clamp(bonus(player.slot, REGENERATION), 0, 1.5f); }
    public void setMoving(ArenaWorld.Player player, boolean value) { moving[player.slot] = value; }
    public void drain(ArenaWorld.Player player, float damage) { sustainHeal(player, Math.min(.8f, damage * clamp(bonus(player.slot, LIFE_STEAL), 0, .025f))); }
    public void killHeal(ArenaWorld.Player player) { sustainHeal(player, clamp(bonus(player.slot, KILL_HEAL), 0, 1)); }
    private void sustainHeal(ArenaWorld.Player player, float amount) {
        if (!player.alive || amount <= 0) return;
        float healed = Math.min(amount, Math.min(sustain[player.slot], maxHealth(player) - player.health));
        player.health += healed; sustain[player.slot] -= healed;
    }
    public int collectedXp(ArenaWorld.Player player, int base) {
        double total = base * (1.0 + clamp(bonus(player.slot, XP_GAIN), 0, .25f)) + xpRemainder[player.slot];
        int result = (int) Math.min(Integer.MAX_VALUE, total); xpRemainder[player.slot] = total >= Integer.MAX_VALUE ? 0 : (float) (total - result); return result;
    }
    private static boolean valid(int slot) { return slot >= 0 && slot < 2; }
    private static float clamp(float value, float min, float max) { return Math.max(min, Math.min(max, value)); }
}
