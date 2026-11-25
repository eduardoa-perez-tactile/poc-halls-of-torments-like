package com.couchcrypt.ui;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.scenes.scene2d.Touchable;
import com.badlogic.gdx.scenes.scene2d.ui.Image;
import com.badlogic.gdx.scenes.scene2d.ui.Label;
import com.badlogic.gdx.scenes.scene2d.ui.Table;
import com.badlogic.gdx.utils.Align;
import com.badlogic.gdx.utils.Scaling;
import com.couchcrypt.game.ArenaWorld;
import com.couchcrypt.game.PartyProgression;
import com.couchcrypt.game.ItemCatalog;
import com.couchcrypt.game.RunEquipment;
import com.couchcrypt.game.SkillExpansion;
import java.text.NumberFormat;
import java.util.Locale;
import java.util.function.Consumer;

/** A finished character-and-build composition, prepared only when opening or paging the chronicle. */
public final class RunDetailsView {
    private static final PartyProgression.Upgrade[] UPGRADES = PartyProgression.Upgrade.values();
    private static final int ENTRIES_PER_PAGE = 4;
    private static final float IDENTITY_HEIGHT = 318;
    private static final float ENTRY_HEIGHT = 96;

    private RunDetailsView() {}

    /** Both player columns turn together, preserving stable portrait positions and readable entries. */
    public static int pageCount(ArenaWorld world, boolean equipment) {
        int entries = equipment ? RunEquipment.SLOT_COUNT : 0;
        if (!equipment) for (ArenaWorld.Player player : world.players)
            if (player.active) entries = Math.max(entries, skillCount(world, player.slot));
        return Math.max(1, (entries + ENTRIES_PER_PAGE - 1) / ENTRIES_PER_PAGE);
    }

    /** Parent owns all art, the page index, and every remote action. Registration starts with Back, then Toggle. */
    public static Table create(UiArt art, ArenaWorld world, TextureRegion[] skillIcons, TextureRegion[] itemIcons,
        boolean equipment, int page, Runnable previousPage, Runnable nextPage, Runnable toggle, Runnable back,
        Consumer<RemoteButton> register) {
        int pages = pageCount(world, equipment);
        page = Math.max(0, Math.min(page, pages - 1));
        Table root = new Table();
        root.pad(28, 80, 32, 80);
        Table heading = new Table();
        heading.add(rule(art, UiArt.GOLD)).width(130).height(1).padRight(28);
        heading.add(art.label("RUN DETAILS", art.heading, UiArt.IVORY));
        heading.add(rule(art, UiArt.GOLD)).width(130).height(1).padLeft(28);
        root.add(heading).height(60).padBottom(6).row();

        Table shared = new Table();
        shared.add(stat(art, formatTime(world.elapsed), "TIME SURVIVED", UiArt.GOLD, 240));
        shared.add(rule(art, UiArt.GOLD)).width(70).height(1).pad(0, 28, 0, 28);
        shared.add(stat(art, number(world.progression.level), "PARTY LEVEL", UiArt.GOLD, 240));
        shared.add(rule(art, UiArt.GOLD)).width(70).height(1).pad(0, 28, 0, 28);
        shared.add(stat(art, number(world.souls), "SOULS GATHERED", UiArt.PLAYER_TWO, 240));
        root.add(shared).height(72).padBottom(12).row();

        int active = 0;
        for (ArenaWorld.Player player : world.players) if (player.active) active++;
        Table party = new Table();
        boolean added = false;
        for (ArenaWorld.Player player : world.players) {
            if (!player.active) continue;
            if (added) party.add(rule(art, UiArt.GOLD)).width(1).height(754).pad(0, 36, 0, 36);
            party.add(player(art, world, player, skillIcons, itemIcons, equipment, page,
                active == 1 ? 1080 : 800, active == 1)).top();
            added = true;
        }
        if (!added) party.add(art.label("No heroes took part in this descent.", art.body, UiArt.MUTED));
        root.add(party).expandY().center().row();

        RemoteButton backButton = action(art, "BACK TO SUMMARY", back);
        register.accept(backButton);
        RemoteButton toggleButton = action(art, equipment ? "VIEW SKILLS" : "VIEW EQUIPMENT", toggle);
        register.accept(toggleButton);
        Table footer = new Table();
        if (pages > 1) {
            RemoteButton previous = action(art, "PREVIOUS", previousPage);
            RemoteButton next = action(art, "NEXT", nextPage);
            previous.setDisabled(page == 0); next.setDisabled(page == pages - 1);
            ((Label) previous.getChildren().first()).setColor(previous.isDisabled() ? UiArt.BUTTON_DISABLED_INK : UiArt.BUTTON_INK);
            ((Label) next.getChildren().first()).setColor(next.isDisabled() ? UiArt.BUTTON_DISABLED_INK : UiArt.BUTTON_INK);
            register.accept(previous); register.accept(next);
            footer.add(previous).width(250).height(78).padRight(16);
            footer.add(art.label((page + 1) + " / " + pages, art.body, UiArt.IVORY)).width(90).center().padRight(16);
            footer.add(next).width(250).height(78).padRight(32);
        }
        footer.add(toggleButton).width(pages > 1 ? 450 : 500).height(78).padRight(24);
        footer.add(backButton).width(pages > 1 ? 450 : 500).height(78);
        root.add(footer).padTop(16);
        return root;
    }

    private static Table player(UiArt art, ArenaWorld world, ArenaWorld.Player player,
        TextureRegion[] icons, TextureRegion[] itemIcons, boolean equipment, int page, float width, boolean solo) {
        Color accent = player.slot == 0 ? UiArt.GOLD : UiArt.PLAYER_TWO;
        Table column = new Table(); column.top();
        Table identity = new Table();
        TextureRegion portrait = player.hero == ArenaWorld.Hero.WARDEN ? art.wardenFace : art.rangerFace;
        Image hero = new Image(new HeroPortraitDrawable(portrait, .96f));
        hero.setScaling(Scaling.fit); hero.setTouchable(Touchable.disabled);
        identity.add(hero).width(320).height(IDENTITY_HEIGHT).padRight(24);
        float statWidth = width - 344;
        Table record = new Table(); record.left();
        record.add(art.label(player.slot == 0 ? "PLAYER I" : "PLAYER II", art.small, accent)).left().padBottom(7).row();
        record.add(scaled(art, player.hero.name(), art.heading, .76f, UiArt.IVORY)).left().padBottom(20).row();
        Table numbers = new Table();
        float numberWidth = statWidth / 3f;
        numbers.add(stat(art, number(player.kills), "SLAIN", accent, numberWidth));
        numbers.add(stat(art, number((int) player.damageDealt), "DAMAGE", accent, numberWidth));
        numbers.add(stat(art, number(solo ? (int) player.damageTaken : player.revives),
            solo ? "DAMAGE TAKEN" : "REVIVES", accent, numberWidth));
        record.add(numbers).left().padBottom(12).row();
        if (!solo) record.add(art.label(number((int) player.damageTaken) + " DAMAGE TAKEN", art.small, UiArt.MUTED)).left();
        identity.add(record).width(statWidth).left();
        column.add(identity).width(width).height(IDENTITY_HEIGHT).row();
        column.add(rule(art, accent)).width(width).height(1).padBottom(10).row();

        int skills = skillCount(world, player.slot), ranks = 0, equipped = 0;
        for (PartyProgression.Upgrade upgrade : UPGRADES) ranks += world.progression.rank(player.slot, upgrade);
        for (int position = 0; position < RunEquipment.SLOT_COUNT; position++)
            if (world.equipment.equipped(player.slot, position) != null) equipped++;
        Table header = new Table();
        header.add(art.label(equipment ? "YOUR EQUIPMENT" : "YOUR BUILD", art.small, accent)).left();
        header.add().expandX();
        header.add(art.label(equipment ? equipped + " / 7 SLOTS" : skills + " SKILLS  /  " + ranks + " RANKS",
            art.small, UiArt.MUTED)).right();
        column.add(header).width(width).height(26).padBottom(8).row();

        Table entries = new Table(); entries.top();
        int first = page * ENTRIES_PER_PAGE, shown = 0;
        if (equipment) {
            for (int position = first; position < Math.min(first + ENTRIES_PER_PAGE, RunEquipment.SLOT_COUNT); position++) {
                entries.add(item(art, world, player, position, itemIcons, width, accent)).width(width).height(ENTRY_HEIGHT).left().row();
                shown++;
            }
        } else {
            int index = 0;
            for (PartyProgression.Upgrade upgrade : UPGRADES) {
                int rank = world.progression.rank(player.slot, upgrade);
                if (rank <= 0) continue;
                if (index >= first && index < first + ENTRIES_PER_PAGE) {
                    entries.add(skill(art, world, player, upgrade, rank, icons, width, accent)).width(width).height(ENTRY_HEIGHT).left().row();
                    shown++;
                }
                index++;
            }
        }
        if (shown == 0) {
            entries.add(art.label(skills == 0 && !equipment ? "No upgrades chosen this run" : "Build complete on the previous page",
                art.body, UiArt.MUTED)).width(width).height(ENTRY_HEIGHT).left().row();
            shown++;
        }
        // Fixed body height prevents portraits and statistics jumping when pages contain fewer entries.
        entries.add().width(width).height((ENTRIES_PER_PAGE - shown) * ENTRY_HEIGHT);
        column.add(entries).width(width).height(ENTRIES_PER_PAGE * ENTRY_HEIGHT).top().row();
        Label note = art.label(equipment ? "Item conditions are listed with each bonus."
            : "Permanent values shown; conditional gear bonuses appear in Equipment.", art.small, UiArt.MUTED);
        note.setWrap(true);
        column.add(note).width(width).height(28).left();
        return column;
    }

    private static Table item(UiArt art, ArenaWorld world, ArenaWorld.Player player, int position,
        TextureRegion[] icons, float width, Color accent) {
        ItemCatalog.Item item = world.equipment.equipped(player.slot, position);
        Table entry = new Table(); entry.left();
        if (item != null && icons != null) {
            Image icon = new Image(icons[ItemCatalog.artIndex(item)]);
            icon.setScaling(Scaling.fit); icon.setTouchable(Touchable.disabled);
            entry.add(icon).size(72).padRight(16);
        } else entry.add().width(88);
        Table copy = new Table(); copy.left();
        Label title = art.label(RunEquipment.slotName(position) + " / " + (item == null ? "EMPTY" : item.title()),
            art.body, item == null ? UiArt.MUTED : UiArt.IVORY);
        title.setWrap(true);
        copy.add(title).width(width - 88).left().row();
        if (item != null) {
            Label description = scaled(art, item.description(), art.body, .89f, UiArt.IVORY);
            description.setWrap(true);
            copy.add(description).width(width - 88).left().padTop(3);
        }
        entry.add(copy).width(width - 88).left();
        return entry;
    }

    private static Table skill(UiArt art, ArenaWorld world, ArenaWorld.Player player, PartyProgression.Upgrade upgrade,
        int rank, TextureRegion[] icons, float width, Color accent) {
        Table entry = new Table(); entry.left();
        TextureRegion icon = icons != null && upgrade.ordinal() < icons.length ? icons[upgrade.ordinal()] : null;
        if (icon != null) {
            Image image = new Image(icon); image.setScaling(Scaling.fit); image.setTouchable(Touchable.disabled);
            entry.add(image).size(72).padRight(16);
        } else entry.add().width(88);
        Table copy = new Table(); copy.left();
        float copyWidth = width - 88;
        Table title = new Table(); title.left();
        title.add(art.label(upgrade.title, art.body, UiArt.IVORY)).left().expandX();
        title.add(art.label(upgrade.evolution() ? "EVOLUTION" : "RANK " + rank, art.small, accent)).right();
        copy.add(title).width(copyWidth).left().row();
        Label description = scaled(art, skillEffect(world, player, upgrade), art.body, .9f, UiArt.IVORY);
        description.setWrap(true);
        copy.add(description).width(copyWidth).left().padTop(3);
        entry.add(copy).width(copyWidth).left();
        return entry;
    }

    /** Finished build values exclude transient low-health, stationary and moving equipment conditions. */
    public static String skillEffect(ArenaWorld world, ArenaWorld.Player player, PartyProgression.Upgrade upgrade) {
        float damage = world.equipment.baseDamageMultiplier(player, player.damageMultiplier);
        return switch (upgrade) {
            case POWER -> signedPercent(damage - 1) + "% attack damage";
            case HASTE -> signedPercent(world.equipment.baseAttackRate(player, player.attackRateMultiplier) - 1) + "% attack speed";
            case FLEET -> signedPercent(world.equipment.moveMultiplier(player) - 1) + "% movement speed";
            case VITALITY -> number((int) world.equipment.maxHealth(player)) + " maximum health";
            case EVASION -> "Dodge cooldown: " + tenths(world.equipment.dodgeCooldown(player)) + "s";
            case SWEEP -> signedPercent(world.equipment.reachMultiplier(player) - 1) + "% sword reach";
            case MULTISHOT -> world.equipment.projectileCount(player) + " arcane bolts per attack";
            case PIERCE -> "Bolts pierce " + world.equipment.pierce(player) + (world.equipment.pierce(player) == 1 ? " extra target" : " extra targets");
            case AURA -> tenths((4 + player.auraLevel * 3) * damage * world.equipment.auraMultiplier(player))
                + " damage / 0.7s; " + (100 + 20 * player.auraLevel) + " radius";
            case STORM -> tenths((12 + player.stormLevel * 5) * damage * world.equipment.stormMultiplier(player))
                + " damage; " + (player.stormLevel + 1) + " targets / 2s";
            case MAGNET -> signedPercent(world.equipment.pickupMultiplier(player) - 1) + "% pickup attraction";
            case CRITICAL -> "Critical chance: " + percent(world.equipment.critChance(player)) + "%";
            case RICOCHET -> player.ricochetLevel + " redirects after piercing; 75% damage retained per bounce";
            case SENTINEL -> SkillExpansion.sentinelCount(player.sentinelLevel) + " blades; "
                + tenths(SkillExpansion.sentinelDamage(player.sentinelLevel) * damage) + " damage / 0.6s per contact";
            case CONSECRATION -> tenths(SkillExpansion.fieldDamage(player.consecrationLevel) * damage)
                + " damage / 0.5s; " + tenths(Math.min(8, SkillExpansion.fieldDuration(player.consecrationLevel) * SkillExpansion.duration(player)))
                + "s seals; cast / " + tenths(SkillExpansion.fieldCooldown(player.consecrationLevel))
                + "s; max " + SkillExpansion.fieldCount(player.consecrationLevel);
            case GLAIVE -> tenths(SkillExpansion.glaiveDamage(player.glaiveLevel) * damage)
                + " damage each way; " + Math.round(SkillExpansion.glaiveRange(player.glaiveLevel)) + " outward range";
            case WARD -> "Absorbs one hit; " + tenths(SkillExpansion.wardCooldown(player.wardLevel)) + "s recharge";
            case FROST -> percent(SkillExpansion.frostAmount(player.frostLevel)) + "% movement slow for "
                + tenths(Math.min(4, SkillExpansion.frostDuration(player.frostLevel) * SkillExpansion.duration(player)))
                + "s; elites/bosses resist";
            case PROJECTILE_SPEED -> signedPercent(SkillExpansion.projectileSpeed(player) - 1) + "% bolt and glaive speed; range unchanged";
            case DURATION -> signedPercent(SkillExpansion.duration(player) - 1) + "% seal and slow duration; duration caps apply";
            case ECHO_STORM -> "Chain strikes echo after 0.3s at 45% damage in a small area";
            case BINDING_CIRCLE -> "Soul Circle: 20% slow for " + tenths(Math.min(4, 1.1f * SkillExpansion.duration(player)))
                + "s; elites/bosses resist";
            case SHATTER_BOLT -> "Final bolt impacts burst for 45% damage against nearby enemies";
            case REAPING_STROKE -> "Sword attacks repeat after 0.18s at 40% damage";
        };
    }

    private static String signedPercent(float value) { int n = percent(value); return (n >= 0 ? "+" : "") + n; }
    private static int percent(float value) { return Math.round(value * 100); }
    private static String tenths(float value) { int n = Math.round(value * 10); return n / 10 + "." + n % 10; }
    private static String number(int value) { return NumberFormat.getIntegerInstance(Locale.US).format(value); }

    private static int skillCount(ArenaWorld world, int slot) {
        int skills = 0;
        for (PartyProgression.Upgrade upgrade : UPGRADES) if (world.progression.rank(slot, upgrade) > 0) skills++;
        return skills;
    }

    private static Table stat(UiArt art, String value, String caption, Color accent, float width) {
        Table stat = new Table();
        Label number = scaled(art, value, art.heading, .72f, accent);
        if (number.getPrefWidth() > width - 12)
            number.setFontScale(.72f * (width - 12) / number.getPrefWidth());
        number.setAlignment(Align.center);
        stat.add(number).width(width).padBottom(4).row();
        Label label = art.label(caption, art.small, UiArt.MUTED);
        label.setAlignment(Align.center);
        stat.add(label).width(width);
        return stat;
    }

    private static RemoteButton action(UiArt art, String text, Runnable command) {
        RemoteButton button = new RemoteButton(art, command, false);
        button.add(art.label(text, art.section, UiArt.BUTTON_INK));
        return button;
    }

    private static Label scaled(UiArt art, String text, BitmapFont font, float scale, Color color) {
        Label label = art.label(text, font, color); label.setFontScale(scale); return label;
    }

    private static Image rule(UiArt art, Color color) {
        Image rule = new Image(art.pixel); rule.setColor(color.r, color.g, color.b, .28f);
        rule.setTouchable(Touchable.disabled); return rule;
    }

    private static String formatTime(float seconds) {
        int total = Math.max(0, (int) seconds);
        return total / 60 + ":" + (total % 60 < 10 ? "0" : "") + total % 60;
    }
}
