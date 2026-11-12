package com.couchcrypt.ui;

import com.badlogic.gdx.scenes.scene2d.ui.Button;
import com.badlogic.gdx.scenes.scene2d.utils.ChangeListener;
import com.badlogic.gdx.scenes.scene2d.Actor;

public final class RemoteButton extends Button {
    private final UiArt art;
    private final Runnable command;
    public RemoteButton(UiArt art, Runnable command) {
        super(art.normal); this.art = art; this.command = command;
        pad(18);
        addListener(new ChangeListener() {
            @Override public void changed(ChangeEvent event, Actor actor) {
                event.cancel();
                if (!isDisabled()) command.run();
            }
        });
    }
    public void focused(boolean value) { setStyle(value ? art.focused : art.normal); }
    public void activate() { if (!isDisabled()) command.run(); }
}
