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

/** A character-and-build composition, laid out once when opening the run chronicle. */
public final class RunDetailsView {
    private static final PartyProgression.Upgrade[] UPGRADES = PartyProgression.Upgrade.values();

    private RunDetailsView() {}

    /** Parent supplies the dimmed outcome scene and owns every texture and font used here. */
    public static Table create(UiArt art, ArenaWorld world, TextureRegion[] skillIcons,
        Runnable back, Consumer<RemoteButton> register) {
        Table root = new Table();
        root.pad(38, 80, 38, 80);
        root.add(art.label("RUN DETAILS", art.heading, UiArt.IVORY)).padBottom(18).row();

        Table shared = new Table();
        shared.add(stat(art, formatTime(world.elapsed), "TIME SURVIVED", UiArt.GOLD, 200));
        shared.add(rule(art, UiArt.GOLD)).width(70).height(1).pad(0, 28, 0, 28);
        shared.add(stat(art, Integer.toString(world.progression.level), "PARTY LEVEL", UiArt.GOLD, 200));
        shared.add(rule(art, UiArt.GOLD)).width(70).height(1).pad(0, 28, 0, 28);
        shared.add(stat(art, Integer.toString(world.souls), "SOULS GATHERED", UiArt.PLAYER_TWO, 200));
        root.add(shared).padBottom(22).row();

        int active = 0;
        for (ArenaWorld.Player player : world.players) if (player.active) active++;
        Table party = new Table();
        boolean added = false;
        for (ArenaWorld.Player player : world.players) {
            if (!player.active) continue;
            if (added) party.add(rule(art, UiArt.GOLD)).width(1).height(592).pad(0, 36, 0, 36);
            party.add(player(art, world, player, skillIcons, active == 1 ? 1080 : 800)).top();
            added = true;
        }
        if (!added) party.add(art.label("No heroes took part in this descent.", art.body, UiArt.MUTED));
        root.add(party).expandY().top().row();

        RemoteButton backButton = new RemoteButton(art, back, false);
        backButton.add(art.label("BACK TO SUMMARY", art.section, UiArt.BUTTON_INK));
        register.accept(backButton);
        root.add(backButton).width(500).height(78).padTop(18);
        return root;
    }

    private static Table player(UiArt art, ArenaWorld world, ArenaWorld.Player player,
        TextureRegion[] icons, float width) {
        Color accent = player.slot == 0 ? UiArt.GOLD : UiArt.PLAYER_TWO;
        Table column = new Table(); column.top();
        Table identity = new Table();
        TextureRegion portrait = player.hero == ArenaWorld.Hero.WARDEN ? art.knight : art.ranger;
        Image hero = new Image(portrait); hero.setScaling(Scaling.fit); hero.setTouchable(Touchable.disabled);
        identity.add(hero).width(210).height(258).padRight(24);

        float statWidth = width - 234;
        Table record = new Table(); record.left();
        record.add(art.label(player.slot == 0 ? "PLAYER I" : "PLAYER II", art.small, accent)).left().padBottom(6).row();
        record.add(art.label(player.hero.name(), art.section, UiArt.IVORY)).left().padBottom(25).row();
        Table numbers = new Table();
        float numberWidth = statWidth / 3f;
        numbers.add(stat(art, Integer.toString(player.kills), "SLAIN", accent, numberWidth));
        numbers.add(stat(art, Integer.toString((int) player.damageDealt), "DAMAGE", accent, numberWidth));
        numbers.add(stat(art, Integer.toString(player.revives), "REVIVES", accent, numberWidth));
        record.add(numbers).left().padBottom(16).row();
        record.add(art.label((int) player.damageTaken + " DAMAGE TAKEN", art.small, UiArt.MUTED)).left();
        identity.add(record).width(statWidth).left();
        column.add(identity).width(width).height(258).row();
        column.add(rule(art, accent)).width(width).height(1).padTop(4).padBottom(17).row();

        int skills = 0, ranks = 0;
        for (PartyProgression.Upgrade upgrade : UPGRADES) {
            int rank = world.progression.rank(player.slot, upgrade);
            if (rank > 0) { skills++; ranks += rank; }
        }
        Table buildHeader = new Table();
        buildHeader.add(art.label("YOUR BUILD", art.small, accent)).left();
        buildHeader.add().expandX();
        buildHeader.add(art.label(skills + " SKILLS  /  " + ranks + " RANKS", art.small, UiArt.MUTED)).right();
        column.add(buildHeader).width(width).padBottom(12).row();

        Table build = new Table();
        float cellWidth = (width - 20f) / 2f;
        int shown = 0;
        for (PartyProgression.Upgrade upgrade : UPGRADES) {
            int rank = world.progression.rank(player.slot, upgrade);
            if (rank <= 0) continue;
            Table entry = new Table(); entry.left();
            TextureRegion icon = icons != null && upgrade.ordinal() < icons.length ? icons[upgrade.ordinal()] : null;
            if (icon != null) {
                Image image = new Image(icon); image.setScaling(Scaling.fit); image.setTouchable(Touchable.disabled);
                entry.add(image).size(48).padRight(12);
            }
            entry.add(art.label(upgrade.title, art.small, UiArt.IVORY)).left().expandX();
            entry.add(art.label("" + rank, art.small, accent)).right().padLeft(8).padRight(10);
            build.add(entry).width(cellWidth).height(54).padBottom(4);
            shown++;
            if ((shown & 1) == 1) build.add().width(20);
            else build.row();
        }
        if (shown == 0) build.add(art.label("No upgrades chosen this run", art.body, UiArt.MUTED)).left().width(width).height(58);
        else if ((shown & 1) == 1) build.add().width(cellWidth);
        column.add(build).width(width).left().top();
        return column;
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
