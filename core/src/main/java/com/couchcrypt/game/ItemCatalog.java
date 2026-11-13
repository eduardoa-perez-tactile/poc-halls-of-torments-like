package com.couchcrypt.game;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Original, run-only equipment. Catalog data is prepared once; combat borrows immutable entries. */
public final class ItemCatalog {
    public enum Slot { HELMET, AMULET, RING, CHEST, BOOTS, GLOVES }

    /** Fractions are additive equipment bonuses; flat-value stats are identified below. */
    public enum Stat {
        DAMAGE("damage"), ATTACK_SPEED("attack speed"), MOVE_SPEED("move speed"),
        MAX_HEALTH("max health"), DODGE_COOLDOWN("dodge cooldown"), REACH("sword reach"),
        PROJECTILES("arrow"), PIERCE("arrow pierce"), AURA_DAMAGE("Soul Circle damage"),
        STORM_DAMAGE("Chain Storm damage"), PICKUP_RANGE("pickup range"),
        CRIT_CHANCE("critical chance"), CRIT_DAMAGE("critical damage"),
        DAMAGE_REDUCTION("damage reduction"), REGENERATION("health/s"),
        LIFE_STEAL("damage returned as health"), THORNS("contact retaliation damage"),
        XP_GAIN("shared soul XP"), KILL_HEAL("health/kill"), BLOCK_CHANCE("block chance"),
        LOW_HEALTH_DAMAGE("damage below 40% health"), MOVING_HASTE("attack speed while moving"),
        STILL_DAMAGE("damage while standing still");

        public final String label;
        Stat(String label) { this.label = label; }
        public boolean flat() {
            return this == MAX_HEALTH || this == PROJECTILES || this == PIERCE || this == REGENERATION
                || this == THORNS || this == KILL_HEAL;
        }
    }

    public record Modifier(Stat stat, float amount) {
        public Modifier {
            if (stat == null || !Float.isFinite(amount) || amount == 0)
                throw new IllegalArgumentException("A modifier needs a finite, nonzero amount");
            if ((stat == Stat.PROJECTILES || stat == Stat.PIERCE) && amount != Math.round(amount))
                throw new IllegalArgumentException("Projectile and pierce bonuses must be whole numbers");
        }
    }

    public record Item(String id, String title, Slot slot, int icon, String description, List<Modifier> modifiers) {
        public Item {
            if (id == null || !id.matches("[a-z][a-z0-9_]{0,47}") || title == null || title.isBlank()
                || title.length() > 64 || slot == null || icon < 0 || icon >= 12
                || description == null || description.isBlank() || description.length() > 128)
                throw new IllegalArgumentException("Invalid equipment presentation");
            modifiers = List.copyOf(modifiers);
            if (modifiers.isEmpty() || modifiers.size() > 3)
                throw new IllegalArgumentException("Equipment needs one to three modifiers");
            for (int i = 0; i < modifiers.size(); i++)
                for (int j = i + 1; j < modifiers.size(); j++)
                    if (modifiers.get(i).stat() == modifiers.get(j).stat())
                        throw new IllegalArgumentException("Duplicate equipment stat");
        }

        public float amount(Stat stat) {
            for (Modifier modifier : modifiers) if (modifier.stat() == stat) return modifier.amount();
            return 0;
        }
        public float bonus(Stat stat) { return amount(stat); }
        public boolean supports(ArenaWorld.Hero hero) {
            if (hero == null) return false;
            if (amount(Stat.REACH) != 0 && hero != ArenaWorld.Hero.WARDEN) return false;
            return (amount(Stat.PROJECTILES) == 0 && amount(Stat.PIERCE) == 0) || hero == ArenaWorld.Hero.RANGER;
        }
    }

    public static final List<Item> ALL = List.of(
        item("gatekeeper_helm", "Gatekeeper Helm", Slot.HELMET, 3,
            mod(Stat.MAX_HEALTH, 20), mod(Stat.DAMAGE_REDUCTION, .06f)),
        item("duelist_visor", "Duelist Visor", Slot.HELMET, 11,
            mod(Stat.CRIT_CHANCE, .06f), mod(Stat.CRIT_DAMAGE, .12f)),
        item("pilgrim_cowl", "Pilgrim Cowl", Slot.HELMET, 10,
            mod(Stat.PICKUP_RANGE, .25f), mod(Stat.XP_GAIN, .04f)),
        item("ashen_circlet", "Ashen Circlet", Slot.HELMET, 8,
            mod(Stat.AURA_DAMAGE, .20f), mod(Stat.MAX_HEALTH, 10)),
        item("stormglass_crown", "Stormglass Crown", Slot.HELMET, 9,
            mod(Stat.STORM_DAMAGE, .20f), mod(Stat.ATTACK_SPEED, .05f)),
        item("fletchers_hood", "Fletcher's Hood", Slot.HELMET, 6,
            mod(Stat.PROJECTILES, 1), mod(Stat.DAMAGE, -.08f)),
        item("executioner_mask", "Executioner Mask", Slot.HELMET, 0,
            mod(Stat.DAMAGE, .18f), mod(Stat.MAX_HEALTH, -10)),
        item("vigilant_sallet", "Vigilant Sallet", Slot.HELMET, 4,
            mod(Stat.BLOCK_CHANCE, .06f), mod(Stat.DODGE_COOLDOWN, .08f)),

        item("emberheart_pendant", "Emberheart Pendant", Slot.AMULET, 3,
            mod(Stat.REGENERATION, .25f), mod(Stat.MAX_HEALTH, 10)),
        item("bloodglass_talisman", "Bloodglass Talisman", Slot.AMULET, 0,
            mod(Stat.LIFE_STEAL, .01f), mod(Stat.MOVE_SPEED, -.05f)),
        item("hunters_compass", "Hunter's Compass", Slot.AMULET, 10,
            mod(Stat.PICKUP_RANGE, .35f), mod(Stat.MOVE_SPEED, .06f)),
        item("last_light_charm", "Last Light Charm", Slot.AMULET, 0,
            mod(Stat.LOW_HEALTH_DAMAGE, .22f), mod(Stat.DAMAGE_REDUCTION, .04f)),
        item("stillwater_medallion", "Stillwater Medallion", Slot.AMULET, 11,
            mod(Stat.STILL_DAMAGE, .20f), mod(Stat.CRIT_CHANCE, .04f)),
        item("soulkeepers_locket", "Soulkeeper's Locket", Slot.AMULET, 10,
            mod(Stat.XP_GAIN, .06f), mod(Stat.KILL_HEAL, .15f)),
        item("bell_of_ashes", "Bell of Ashes", Slot.AMULET, 8,
            mod(Stat.AURA_DAMAGE, .25f), mod(Stat.PICKUP_RANGE, .15f)),
        item("tempest_knot", "Tempest Knot", Slot.AMULET, 9,
            mod(Stat.STORM_DAMAGE, .25f), mod(Stat.DODGE_COOLDOWN, .06f)),

        item("rustbound_signet", "Rustbound Signet", Slot.RING, 0,
            mod(Stat.DAMAGE, .12f)),
        item("razorstone_band", "Razorstone Band", Slot.RING, 11,
            mod(Stat.CRIT_DAMAGE, .25f)),
        item("owlstone_ring", "Owlstone Ring", Slot.RING, 11,
            mod(Stat.CRIT_CHANCE, .06f)),
        item("quickpulse_loop", "Quickpulse Loop", Slot.RING, 1,
            mod(Stat.ATTACK_SPEED, .10f)),
        item("pale_root_ring", "Pale Root Ring", Slot.RING, 3,
            mod(Stat.REGENERATION, .20f), mod(Stat.MAX_HEALTH, 5)),
        item("gravebloom_band", "Gravebloom Band", Slot.RING, 3,
            mod(Stat.KILL_HEAL, .20f), mod(Stat.MAX_HEALTH, 10)),
        item("thornscript_seal", "Thornscript Seal", Slot.RING, 5,
            mod(Stat.THORNS, 7), mod(Stat.DAMAGE_REDUCTION, .03f)),
        item("wanderers_loop", "Wanderer's Loop", Slot.RING, 2,
            mod(Stat.MOVE_SPEED, .08f), mod(Stat.DODGE_COOLDOWN, .06f)),

        item("cryptguard_plate", "Cryptguard Plate", Slot.CHEST, 3,
            mod(Stat.MAX_HEALTH, 30), mod(Stat.DAMAGE_REDUCTION, .10f), mod(Stat.MOVE_SPEED, -.08f)),
        item("briar_mail", "Briar Mail", Slot.CHEST, 5,
            mod(Stat.THORNS, 12), mod(Stat.DAMAGE_REDUCTION, .07f)),
        item("wayfarer_leathers", "Wayfarer Leathers", Slot.CHEST, 2,
            mod(Stat.MOVE_SPEED, .08f), mod(Stat.DODGE_COOLDOWN, .10f)),
        item("penitents_habit", "Penitent's Habit", Slot.CHEST, 3,
            mod(Stat.REGENERATION, .35f), mod(Stat.DAMAGE, -.05f)),
        item("oathbound_cuirass", "Oathbound Cuirass", Slot.CHEST, 4,
            mod(Stat.BLOCK_CHANCE, .10f), mod(Stat.MAX_HEALTH, 15)),
        item("ravenweave_coat", "Ravenweave Coat", Slot.CHEST, 11,
            mod(Stat.CRIT_CHANCE, .05f), mod(Stat.STILL_DAMAGE, .15f)),
        item("red_martyr_vest", "Red Martyr Vest", Slot.CHEST, 0,
            mod(Stat.LOW_HEALTH_DAMAGE, .25f), mod(Stat.MAX_HEALTH, 15)),
        item("choirweave_robe", "Choirweave Robe", Slot.CHEST, 8,
            mod(Stat.AURA_DAMAGE, .15f), mod(Stat.STORM_DAMAGE, .15f), mod(Stat.MAX_HEALTH, -10)),

        item("pilgrims_stride", "Pilgrim's Stride", Slot.BOOTS, 2,
            mod(Stat.MOVE_SPEED, .12f)),
        item("ironheel_boots", "Ironheel Boots", Slot.BOOTS, 3,
            mod(Stat.DAMAGE_REDUCTION, .08f), mod(Stat.MOVE_SPEED, -.04f)),
        item("shadowlace_steps", "Shadowlace Steps", Slot.BOOTS, 4,
            mod(Stat.DODGE_COOLDOWN, .14f)),
        item("marching_spurs", "Marching Spurs", Slot.BOOTS, 1,
            mod(Stat.MOVING_HASTE, .16f), mod(Stat.MOVE_SPEED, .04f)),
        item("rooted_soles", "Rooted Soles", Slot.BOOTS, 0,
            mod(Stat.STILL_DAMAGE, .18f), mod(Stat.DAMAGE_REDUCTION, .04f)),
        item("scavenger_treads", "Scavenger Treads", Slot.BOOTS, 10,
            mod(Stat.PICKUP_RANGE, .30f), mod(Stat.XP_GAIN, .03f)),
        item("hearthwoven_slippers", "Hearthwoven Slippers", Slot.BOOTS, 3,
            mod(Stat.REGENERATION, .20f), mod(Stat.MOVE_SPEED, .05f)),
        item("duelrunner_boots", "Duelrunner Boots", Slot.BOOTS, 11,
            mod(Stat.CRIT_CHANCE, .04f), mod(Stat.DODGE_COOLDOWN, .08f)),

        item("swiftstitch_gloves", "Swiftstitch Gloves", Slot.GLOVES, 1,
            mod(Stat.ATTACK_SPEED, .15f)),
        item("farreach_gauntlets", "Farreach Gauntlets", Slot.GLOVES, 5,
            mod(Stat.REACH, .15f), mod(Stat.DAMAGE, .05f)),
        item("needlegrip_gloves", "Needlegrip Gloves", Slot.GLOVES, 7,
            mod(Stat.PIERCE, 1), mod(Stat.ATTACK_SPEED, .05f)),
        item("reapers_wrappings", "Reaper's Wrappings", Slot.GLOVES, 0,
            mod(Stat.LIFE_STEAL, .008f), mod(Stat.DAMAGE, .06f)),
        item("soulthread_mitts", "Soulthread Mitts", Slot.GLOVES, 10,
            mod(Stat.PICKUP_RANGE, .40f), mod(Stat.XP_GAIN, .03f)),
        item("barbed_fists", "Barbed Fists", Slot.GLOVES, 5,
            mod(Stat.THORNS, 9), mod(Stat.BLOCK_CHANCE, .05f)),
        item("headsman_grips", "Headsman Grips", Slot.GLOVES, 11,
            mod(Stat.CRIT_DAMAGE, .20f), mod(Stat.DAMAGE, .08f)),
        item("stormcallers_touch", "Stormcaller's Touch", Slot.GLOVES, 9,
            mod(Stat.AURA_DAMAGE, .12f), mod(Stat.STORM_DAMAGE, .18f))
    );

    private static final Map<String, Item> BY_ID = index();
    private ItemCatalog() {}
    public static Item byId(String id) { return BY_ID.get(id); }
    /** Stable row-major image index shared by the file-backed item atlas and phone cards. */
    public static int artIndex(Item item) { return item == null ? -1 : ALL.indexOf(item); }

    private static Map<String, Item> index() {
        Map<String, Item> result = new HashMap<>();
        for (Item item : ALL)
            if (result.put(item.id(), item) != null) throw new IllegalStateException("Duplicate item: " + item.id());
        return Map.copyOf(result);
    }

    private static Modifier mod(Stat stat, float amount) { return new Modifier(stat, amount); }

    private static Item item(String id, String title, Slot slot, int icon, Modifier... modifiers) {
        List<Modifier> fixed = List.of(modifiers);
        StringBuilder description = new StringBuilder(100);
        for (Modifier modifier : fixed) {
            if (description.length() > 0) description.append("; ");
            float value = modifier.amount();
            if (modifier.stat() == Stat.DODGE_COOLDOWN) value = -value;
            if (value > 0) description.append('+');
            description.append(number(value * (modifier.stat().flat() ? 1 : 100)));
            if (!modifier.stat().flat()) description.append('%');
            description.append(' ').append(modifier.stat().label);
        }
        return new Item(id, title, slot, icon, description.toString(), fixed);
    }

    private static String number(float value) {
        int hundredths = Math.round(value * 100);
        int whole = hundredths / 100, fraction = Math.abs(hundredths % 100);
        if (fraction == 0) return Integer.toString(whole);
        String sign = hundredths < 0 && whole == 0 ? "-" : "";
        return sign + whole + "." + (fraction < 10 ? "0" : "")
            + (fraction % 10 == 0 ? fraction / 10 : fraction);
    }
}
