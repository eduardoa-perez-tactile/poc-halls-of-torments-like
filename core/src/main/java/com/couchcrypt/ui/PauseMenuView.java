package com.couchcrypt.ui;

import com.badlogic.gdx.scenes.scene2d.ui.Label;
import com.badlogic.gdx.scenes.scene2d.ui.Table;
import java.util.function.Consumer;

/** A compact pause composition over the frozen arena, using resident painted plates. */
public final class PauseMenuView {
    private static final float ACTION_WIDTH = 500;
    private static final float ACTION_HEIGHT = ACTION_WIDTH * 224f / 1024f;
    public final Table root = new Table();
    public final Label message;

    public PauseMenuView(UiArt art, String status, String[] options, Runnable[] actions,
        Consumer<RemoteButton> register) {
        root.setBackground(art.panel);
        root.pad(48);

        Table identity = new Table(); identity.top().left();
        Label title = art.label("PAUSED", art.heading, UiArt.IVORY);
        identity.add(title).left().padBottom(24).row();
        Table rule = new Table(); rule.setBackground(art.goldLine);
        identity.add(rule).width(88).height(2).left().padBottom(32).row();
        message = art.label(status, art.body, UiArt.IVORY); message.setWrap(true);
        identity.add(message).width(380).left().top().row();
        identity.add().expandY().row();
        Label frozen = art.label("Combat and choice timers\nare frozen.", art.small, UiArt.MUTED);
        identity.add(frozen).left().padTop(40);

        Table menu = new Table();
        for (int i = 0; i < options.length; i++) {
            RemoteButton button = new RemoteButton(art.summaryNormal, art.summaryFocused, actions[i]);
            Label caption = art.label(options[i], art.section, UiArt.BUTTON_INK);
            caption.setFontScale(.9f); button.add(caption);
            register.accept(button);
            menu.add(button).width(ACTION_WIDTH).height(ACTION_HEIGHT).padTop(i == 0 ? 0 : 12).row();
        }
        root.add(identity).width(400).fillY().padRight(56);
        root.add(menu).width(ACTION_WIDTH);
    }
}
