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
import java.util.function.Consumer;

/** A finished character-and-build composition, prepared once when opening the run chronicle. */
public final class RunDetailsView {
    private static final PartyProgression.Upgrade[] UPGRADES = PartyProgression.Upgrade.values();

    private RunDetailsView() {}

    /** Parent supplies the dimmed outcome scene and owns every texture and font used here. */
    public static Table create(UiArt art, ArenaWorld world, TextureRegion[] skillIcons,
        Runnable back, Consumer<RemoteButton> register) {
        Table root = new Table();
        root.pad(32, 80, 32, 80);
        Table heading = new Table();
        heading.add(rule(art, UiArt.GOLD)).width(130).height(1).padRight(28);
        heading.add(art.label("RUN DETAILS", art.heading, UiArt.IVORY));
        heading.add(rule(art, UiArt.GOLD)).width(130).height(1).padLeft(28);
        root.add(heading).padBottom(14).row();

        Table shared = new Table();
        shared.add(stat(art, formatTime(world.elapsed), "TIME SURVIVED", UiArt.GOLD, 210));
        shared.add(rule(art, UiArt.GOLD)).width(70).height(1).pad(0, 28, 0, 28);
        shared.add(stat(art, Integer.toString(world.progression.level), "PARTY LEVEL", UiArt.GOLD, 210));
        shared.add(rule(art, UiArt.GOLD)).width(70).height(1).pad(0, 28, 0, 28);
        shared.add(stat(art, Integer.toString(world.souls), "SOULS GATHERED", UiArt.PLAYER_TWO, 210));
        root.add(shared).padBottom(12).row();

        int active = 0, tallest = 0;
        for (ArenaWorld.Player player : world.players) if (player.active) {
            active++;
            int skills = skillCount(world, player.slot);
            tallest = Math.max(tallest, preferredPlayerHeight(skills));
        }
        Table party = new Table();
        boolean added = false;
        for (ArenaWorld.Player player : world.players) {
            if (!player.active) continue;
            if (added) party.add(rule(art, UiArt.GOLD)).width(1).height(tallest).pad(0, 36, 0, 36);
            party.add(player(art, world, player, skillIcons, active == 1 ? 1080 : 800)).top();
            added = true;
        }
        if (!added) party.add(art.label("No heroes took part in this descent.", art.body, UiArt.MUTED));
        root.add(party).expandY().center().row();

        RemoteButton backButton = new RemoteButton(art, back, false);
        backButton.add(art.label("BACK TO SUMMARY", art.section, UiArt.BUTTON_INK));
        register.accept(backButton);
        root.add(backButton).width(500).height(78).padTop(16);
        return root;
    }

    private static Table player(UiArt art, ArenaWorld world, ArenaWorld.Player player,
        TextureRegion[] icons, float width) {
        Color accent = player.slot == 0 ? UiArt.GOLD : UiArt.PLAYER_TWO;
        int skills = skillCount(world, player.slot), ranks = 0;
        boolean detailed = skills <= 4;
        float identityHeight = detailed ? 318 : 236;
        for (PartyProgression.Upgrade upgrade : UPGRADES) ranks += world.progression.rank(player.slot, upgrade);

        Table column = new Table(); column.top();
        Table identity = new Table();
        Image hero = new Image(heroRegion(art, player.hero));
        hero.setScaling(Scaling.fit); hero.setTouchable(Touchable.disabled);
        identity.add(hero).width(230).height(identityHeight).padRight(24);

        float statWidth = width - 254;
        Table record = new Table(); record.left();
        record.add(art.label(player.slot == 0 ? "PLAYER I" : "PLAYER II", art.small, accent)).left().padBottom(7).row();
        record.add(scaled(art, player.hero.name(), art.heading, .76f, UiArt.IVORY)).left().padBottom(detailed ? 28 : 18).row();
        Table numbers = new Table();
        float numberWidth = statWidth / 3f;
        numbers.add(stat(art, Integer.toString(player.kills), "SLAIN", accent, numberWidth));
        numbers.add(stat(art, Integer.toString((int) player.damageDealt), "DAMAGE", accent, numberWidth));
        numbers.add(stat(art, Integer.toString(player.revives), "REVIVES", accent, numberWidth));
        record.add(numbers).left().padBottom(16).row();
        record.add(art.label((int) player.damageTaken + " DAMAGE TAKEN", art.small, UiArt.MUTED)).left();
        identity.add(record).width(statWidth).left();
        column.add(identity).width(width).height(identityHeight).row();
        column.add(rule(art, accent)).width(width).height(1).padTop(4).padBottom(14).row();

        Table buildHeader = new Table();
        buildHeader.add(art.label("YOUR BUILD", art.small, accent)).left();
        buildHeader.add().expandX();
        buildHeader.add(art.label(skills + " SKILLS  /  " + ranks + " RANKS", art.small, UiArt.MUTED)).right();
        column.add(buildHeader).width(width).padBottom(12).row();

        Table build = new Table();
        float cellWidth = detailed ? width : (width - 20f) / 2f;
        int shown = 0;
        for (PartyProgression.Upgrade upgrade : UPGRADES) {
            int rank = world.progression.rank(player.slot, upgrade);
            if (rank <= 0) continue;
            build.add(skill(art, player, upgrade, rank, icons, detailed, accent))
                .width(cellWidth).height(detailed ? 78 : 54).padBottom(detailed ? 2 : 4);
            shown++;
            if (detailed) build.row();
            else if ((shown & 1) == 1) build.add().width(20);
            else build.row();
        }
        if (shown == 0) build.add(art.label("No upgrades chosen this run", art.body, UiArt.MUTED)).left().width(width).height(64);
        else if (!detailed && (shown & 1) == 1) build.add().width(cellWidth);
        column.add(build).width(width).left().top();
        return column;
    }

    private static Table skill(UiArt art, ArenaWorld.Player player, PartyProgression.Upgrade upgrade, int rank,
        TextureRegion[] icons, boolean detailed, Color accent) {
        Table entry = new Table(); entry.left();
        TextureRegion icon = icons != null && upgrade.ordinal() < icons.length ? icons[upgrade.ordinal()] : null;
        if (icon != null) {
            Image image = new Image(icon); image.setScaling(Scaling.fit); image.setTouchable(Touchable.disabled);
            entry.add(image).size(detailed ? 68 : 48).padRight(detailed ? 16 : 10);
        }
        Table copy = new Table(); copy.left();
        copy.add(art.label(upgrade.title, detailed ? art.body : art.small, UiArt.IVORY)).left().row();
        if (detailed) copy.add(art.label(effect(player, upgrade), art.small, UiArt.MUTED)).left().padTop(4);
        entry.add(copy).left().expandX();
        entry.add(art.label("RANK " + rank, art.small, accent)).right().padLeft(8).padRight(10);
        return entry;
    }

    /** Existing portrait files contain large transparent side margins; crop those without new textures. */
    private static TextureRegion heroRegion(UiArt art, ArenaWorld.Hero hero) {
        TextureRegion source = hero == ArenaWorld.Hero.WARDEN ? art.knight : art.ranger;
        float left = hero == ArenaWorld.Hero.WARDEN ? 234f / 887f : 134f / 887f;
        float width = hero == ArenaWorld.Hero.WARDEN ? 481f / 887f : 570f / 887f;
        int x = Math.round(source.getRegionWidth() * left);
        int w = Math.min(source.getRegionWidth() - x, Math.round(source.getRegionWidth() * width));
        return new TextureRegion(source, x, 0, w, source.getRegionHeight());
    }

    private static String effect(ArenaWorld.Player player, PartyProgression.Upgrade upgrade) {
        return switch (upgrade) {
            case POWER -> "+" + percent(player.damageMultiplier - 1) + "% attack damage";
            case HASTE -> "+" + percent(player.attackRateMultiplier - 1) + "% attack speed";
            case FLEET -> "+" + percent(player.moveMultiplier - 1) + "% movement speed";
            case VITALITY -> (int) player.maxHealth + " maximum health";
            case EVASION -> "Dodge cooldown: " + tenths(player.hero.dodgeCooldown * player.dodgeCooldownMultiplier) + "s";
            case SWEEP -> "+" + percent(player.cleaveMultiplier - 1) + "% sword reach";
            case MULTISHOT -> player.projectileCount + " arrows per attack";
            case PIERCE -> "Arrows pierce " + player.pierce + (player.pierce == 1 ? " extra target" : " extra targets");
            case AURA -> "Damage pulses every 0.7s";
            case STORM -> "Chains across " + (player.stormLevel + 1) + " targets every 2s";
            case MAGNET -> "+" + percent(player.pickupMultiplier - 1) + "% pickup attraction";
            case CRITICAL -> "Critical chance: " + percent(player.critChance) + "%";
        };
    }

    private static int percent(float value) { return Math.round(value * 100); }
    private static String tenths(float value) { int n = Math.round(value * 10); return n / 10 + "." + n % 10; }

    private static int skillCount(ArenaWorld world, int slot) {
        int skills = 0;
        for (PartyProgression.Upgrade upgrade : UPGRADES) if (world.progression.rank(slot, upgrade) > 0) skills++;
        return skills;
    }

    private static int preferredPlayerHeight(int skills) {
        return (skills <= 4 ? 318 + Math.max(1, skills) * 80 : 236 + (skills + 1) / 2 * 58) + 53;
    }

    private static Table stat(UiArt art, String value, String caption, Color accent, float width) {
        Table stat = new Table();
        Label number = scaled(art, value, art.heading, .72f, accent);
        number.setAlignment(Align.center);
        stat.add(number).width(width).padBottom(4).row();
        stat.add(art.label(caption, art.small, UiArt.MUTED)).width(width).center();
        return stat;
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
