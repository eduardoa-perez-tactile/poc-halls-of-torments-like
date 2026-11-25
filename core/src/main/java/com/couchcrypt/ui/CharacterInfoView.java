package com.couchcrypt.ui;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.scenes.scene2d.Touchable;
import com.badlogic.gdx.scenes.scene2d.ui.Image;
import com.badlogic.gdx.scenes.scene2d.ui.Label;
import com.badlogic.gdx.scenes.scene2d.ui.Table;
import com.badlogic.gdx.utils.Align;
import com.badlogic.gdx.utils.Scaling;
import com.couchcrypt.game.ArenaWorld;
import com.couchcrypt.game.HeroCatalog;
import com.couchcrypt.game.PartyProgression;
import java.util.Locale;
import java.util.function.Consumer;

/** Informational hero reference, laid out once using existing shared art and skill icons. */
public final class CharacterInfoView {
    private static final int SKILLS_PER_PAGE = 8;
    private CharacterInfoView() {}

    public static int pageCount(ArenaWorld.Hero hero) {
        return Math.max(1, (HeroCatalog.profile(hero).skills().size() + SKILLS_PER_PAGE - 1) / SKILLS_PER_PAGE);
    }

    public static java.util.List<PartyProgression.Upgrade> pageSkills(ArenaWorld.Hero hero, int page) {
        var skills = HeroCatalog.profile(hero).skills();
        int first = Math.max(0, Math.min(page, pageCount(hero) - 1)) * SKILLS_PER_PAGE;
        return skills.subList(first, Math.min(skills.size(), first + SKILLS_PER_PAGE));
    }

    public static Table create(UiArt art, ArenaWorld.Hero hero, TextureRegion[] skillIcons,
        Runnable back, Consumer<RemoteButton> register) {
        HeroCatalog.Profile profile = HeroCatalog.profile(hero);
        Color accent = hero == ArenaWorld.Hero.WARDEN ? UiArt.GOLD : UiArt.PLAYER_TWO;
        Table root = new Table(); root.pad(48, 64, 48, 64);
        root.add(art.label("KNOW YOUR HERO", art.section, UiArt.GOLD)).padBottom(20).row();

        Table body = new Table();
        body.add(identity(art, profile, accent)).width(490).top();
        body.add(rule(art, accent)).width(1).height(710).padLeft(38).padRight(38);
        Table skillColumn = new Table(); skillColumn.top().left();
        skillColumn.add(art.label("AVAILABLE SKILLS", art.section, UiArt.IVORY)).left().padBottom(8).row();
        skillColumn.add(art.label("Earn shared XP. Choose your own upgrades on your phone.", art.small, UiArt.MUTED))
            .left().padBottom(24).row();
        Table roster = new Table(); roster.top().left();
        populateSkills(roster, art, profile, skillIcons, 0);
        skillColumn.add(roster).width(1220).height(540).top().left();
        body.add(skillColumn).width(1220).top();
        root.add(body).expandY().center().row();

        RemoteButton backButton = new RemoteButton(art, back, false);
        backButton.add(art.label("BACK TO HEROES", art.section, UiArt.BUTTON_INK));
        register.accept(backButton);
        Table footer = new Table();
        int pages = pageCount(hero);
        if (pages > 1) {
            int[] page = {0};
            Label pageLabel = art.label("1 / " + pages, art.body, UiArt.IVORY);
            Runnable refresh = () -> {
                populateSkills(roster, art, profile, skillIcons, page[0]);
                pageLabel.setText((page[0] + 1) + " / " + pages);
            };
            RemoteButton previous = new RemoteButton(art, () -> { page[0] = (page[0] + pages - 1) % pages; refresh.run(); }, false);
            previous.add(art.label("PREVIOUS", art.section, UiArt.BUTTON_INK));
            RemoteButton next = new RemoteButton(art, () -> { page[0] = (page[0] + 1) % pages; refresh.run(); }, false);
            next.add(art.label("NEXT", art.section, UiArt.BUTTON_INK));
            // Register the fixed controls once; paging never leaves hidden controls in host navigation.
            register.accept(previous); register.accept(next);
            footer.add(previous).width(290).height(78).padRight(20);
            footer.add(pageLabel).width(100).padRight(20);
            footer.add(next).width(290).height(78).padRight(40);
        }
        footer.add(backButton).width(480).height(78);
        root.add(footer).padTop(18);
        return root;
    }

    private static Table identity(UiArt art, HeroCatalog.Profile profile, Color accent) {
        Table column = new Table(); column.top();
        Table identity = new Table();
        Image hero = new Image(new HeroPortraitDrawable(heroRegion(art, profile.hero()), 1));
        hero.setScaling(Scaling.fit); hero.setTouchable(Touchable.disabled);
        identity.add(hero).width(196).height(310).padRight(16);
        Table introduction = new Table(); introduction.left();
        Label name = art.label(profile.name().toUpperCase(Locale.ROOT), art.heading, UiArt.IVORY);
        name.setFontScale(.8f);
        introduction.add(name).left().padBottom(8).row();
        introduction.add(wrapped(art, profile.role(), art.small, accent)).width(278).left().padBottom(22).row();
        introduction.add(wrapped(art, profile.summary(), art.body, UiArt.IVORY)).width(278).left();
        identity.add(introduction).width(278).left();
        column.add(identity).width(490).padBottom(16).row();
        column.add(wrapped(art, profile.bio(), art.small, UiArt.MUTED)).width(490).padBottom(20).row();
        column.add(rule(art, accent)).width(490).height(1).padBottom(14).row();
        column.add(art.label("BASE STATS", art.small, accent)).left().padBottom(10).row();

        ArenaWorld.Hero h = profile.hero();
        Table stats = new Table();
        statPair(stats, art, "HEALTH", Integer.toString(Math.round(h.baseHealth)),
            "DAMAGE", Integer.toString(Math.round(h.baseDamage)), accent);
        statPair(stats, art, "ATTACK RATE", decimal(1f / h.attackInterval) + " /s",
            "MOVE SPEED", Math.round(h.moveSpeed / ArenaWorld.Hero.WARDEN.moveSpeed * 100) + "%", accent);
        statPair(stats, art, "RANGE", h == ArenaWorld.Hero.WARDEN ? "CLOSE" : "LONG",
            "DODGE", decimal(h.dodgeCooldown) + "s cooldown", accent);
        column.add(stats).width(490);
        return column;
    }

    private static void statPair(Table table, UiArt art, String firstLabel, String firstValue,
        String secondLabel, String secondValue, Color accent) {
        table.add(stat(art, firstLabel, firstValue, accent)).width(245).left().padBottom(12);
        table.add(stat(art, secondLabel, secondValue, accent)).width(245).left().padBottom(12).row();
    }

    private static Table stat(UiArt art, String label, String value, Color accent) {
        Table stat = new Table(); stat.left();
        stat.add(art.label(label, art.small, UiArt.MUTED)).left().row();
        stat.add(art.label(value, art.body, accent)).left().padTop(3);
        return stat;
    }

    private static void populateSkills(Table roster, UiArt art, HeroCatalog.Profile profile, TextureRegion[] icons, int page) {
        roster.clearChildren();
        var skills = pageSkills(profile.hero(), page);
        // Four entries down each column, preserving canonical order across pages.
        for (int row = 0; row < 4; row++) {
            for (int col = 0; col < 2; col++) {
                int index = col * 4 + row;
                if (index < skills.size())
                    roster.add(skill(art, skills.get(index), icons)).width(590).height(130).top();
                else roster.add().width(590).height(130);
                if (col == 0) roster.add().width(40);
            }
            roster.row();
        }
    }

    private static Table skill(UiArt art, PartyProgression.Upgrade upgrade, TextureRegion[] icons) {
        Table entry = new Table(); entry.top().left();
        TextureRegion icon = icons != null && upgrade.ordinal() < icons.length ? icons[upgrade.ordinal()] : null;
        if (icon != null) {
            Image image = new Image(icon); image.setScaling(Scaling.fit); image.setTouchable(Touchable.disabled);
            entry.add(image).size(62).top().padRight(16);
        }
        Table copy = new Table(); copy.top().left();
        copy.add(art.label(upgrade.title + (upgrade.evolution() ? " / EVOLUTION" : ""), art.body, UiArt.IVORY)).left().padBottom(6).row();
        copy.add(wrapped(art, upgrade.description, art.small, UiArt.MUTED)).width(512).left();
        entry.add(copy).width(512).top().left();
        return entry;
    }

    private static Label wrapped(UiArt art, String text, com.badlogic.gdx.graphics.g2d.BitmapFont font, Color color) {
        Label label = art.label(text, font, color); label.setWrap(true); label.setAlignment(Align.left);
        return label;
    }

    /** Reuse the chooser identity without allocating or uploading another texture. */
    private static TextureRegion heroRegion(UiArt art, ArenaWorld.Hero hero) {
        return hero == ArenaWorld.Hero.WARDEN ? art.wardenFace : art.rangerFace;
    }

    private static Image rule(UiArt art, Color color) {
        Image rule = new Image(art.pixel); rule.setColor(color.r, color.g, color.b, .3f);
        rule.setTouchable(Touchable.disabled); return rule;
    }

    private static String decimal(float value) { return String.format(Locale.ROOT, "%.1f", value); }
}
