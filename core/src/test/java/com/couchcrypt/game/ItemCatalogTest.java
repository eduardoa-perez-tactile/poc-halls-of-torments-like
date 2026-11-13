package com.couchcrypt.game;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.List;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ItemCatalogTest {
    @Test void catalogHasBroadRealChoicesInEveryEquipmentCategoryAndStableWireIdentities() {
        var ids = new HashSet<String>();
        var represented = EnumSet.noneOf(ItemCatalog.Stat.class);
        int[] categories = new int[ItemCatalog.Slot.values().length];
        int artIndex = 0;
        for (ItemCatalog.Item item : ItemCatalog.ALL) {
            assertTrue(ids.add(item.id()));
            assertSame(item, ItemCatalog.byId(item.id()));
            assertEquals(artIndex++, ItemCatalog.artIndex(item), "Phone and TV must identify the same illustration");
            assertTrue(item.title().length() <= 64);
            assertTrue(item.description().length() <= 128);
            assertTrue(item.icon() >= 0 && item.icon() < PartyProgression.Upgrade.values().length);
            categories[item.slot().ordinal()]++;
            for (ItemCatalog.Modifier modifier : item.modifiers()) represented.add(modifier.stat());
        }
        for (int count : categories) assertTrue(count >= 7, "Each category needs meaningful alternatives");
        assertEquals(EnumSet.allOf(ItemCatalog.Stat.class), represented);
        assertNull(ItemCatalog.byId("stale_or_unknown_item"));
        assertEquals(-1, ItemCatalog.artIndex(null));
    }

    @Test void weaponEquipmentCannotBeOfferedToTheWrongHero() {
        var extraArrow = ItemCatalog.byId("fletchers_hood");
        var pierce = ItemCatalog.byId("needlegrip_gloves");
        var reach = ItemCatalog.byId("farreach_gauntlets");
        assertFalse(extraArrow.supports(ArenaWorld.Hero.WARDEN));
        assertFalse(pierce.supports(ArenaWorld.Hero.WARDEN));
        assertTrue(extraArrow.supports(ArenaWorld.Hero.RANGER));
        assertTrue(pierce.supports(ArenaWorld.Hero.RANGER));
        assertFalse(reach.supports(ArenaWorld.Hero.RANGER));
        assertTrue(reach.supports(ArenaWorld.Hero.WARDEN));
        for (ItemCatalog.Item item : ItemCatalog.ALL) {
            assertFalse(item.supports(null));
            assertTrue(item.supports(ArenaWorld.Hero.WARDEN) || item.supports(ArenaWorld.Hero.RANGER));
        }
    }

    @Test void callersCannotMutateCatalogOrAnEquippedItemsSourceModifiers() {
        var source = new ArrayList<>(List.of(new ItemCatalog.Modifier(ItemCatalog.Stat.DAMAGE, .1f)));
        var item = new ItemCatalog.Item("fixture", "Fixture", ItemCatalog.Slot.RING, 0, "+10% damage", source);
        source.clear();
        assertEquals(.1f, item.amount(ItemCatalog.Stat.DAMAGE));
        assertEquals(0, item.amount(ItemCatalog.Stat.CRIT_CHANCE));
        assertThrows(UnsupportedOperationException.class, () -> item.modifiers().clear());
        assertThrows(UnsupportedOperationException.class, () -> ItemCatalog.ALL.clear());
        assertThrows(IllegalArgumentException.class,
            () -> new ItemCatalog.Modifier(ItemCatalog.Stat.DAMAGE, Float.NaN));
        assertThrows(IllegalArgumentException.class,
            () -> new ItemCatalog.Modifier(ItemCatalog.Stat.PROJECTILES, .5f));
    }

    @Test void evenIndependentBestSlotChoicesStayInsideTheIntendedEquipmentPowerEnvelope() {
        // Conservative upper bound: independently optimize every stat, even allowing the same ring twice.
        // No survival simulation is required to detect an accidentally overpowered catalog entry.
        assertTrue(maximumBonus(ItemCatalog.Stat.DAMAGE) <= .8f);
        assertTrue(maximumBonus(ItemCatalog.Stat.ATTACK_SPEED) <= .8f);
        assertTrue(maximumBonus(ItemCatalog.Stat.MAX_HEALTH) <= 120);
        assertTrue(maximumBonus(ItemCatalog.Stat.DAMAGE_REDUCTION) <= .35f);
        assertTrue(maximumBonus(ItemCatalog.Stat.REGENERATION) <= 1.5f);
        assertTrue(maximumBonus(ItemCatalog.Stat.LIFE_STEAL) <= .03f);
        assertTrue(maximumBonus(ItemCatalog.Stat.XP_GAIN) <= .20f);
        assertTrue(maximumBonus(ItemCatalog.Stat.CRIT_CHANCE) <= .35f);
        assertTrue(maximumBonus(ItemCatalog.Stat.LOW_HEALTH_DAMAGE) <= .50f);
        assertTrue(maximumBonus(ItemCatalog.Stat.STILL_DAMAGE) <= .60f);
    }

    private static float maximumBonus(ItemCatalog.Stat stat) {
        float maximum = 0;
        for (ItemCatalog.Slot slot : ItemCatalog.Slot.values()) {
            float best = 0;
            for (ItemCatalog.Item item : ItemCatalog.ALL) if (item.slot() == slot)
                best = Math.max(best, item.amount(stat));
            maximum += best * (slot == ItemCatalog.Slot.RING ? 2 : 1);
        }
        return maximum;
    }
}
