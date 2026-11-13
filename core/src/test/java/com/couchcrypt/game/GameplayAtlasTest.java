package com.couchcrypt.game;

import com.badlogic.gdx.utils.JsonReader;
import com.badlogic.gdx.utils.JsonValue;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class GameplayAtlasTest {
    @Test void trimGeometryKeepsUprightAndDownedArtAtTheSameGroundRoot() {
        GameplayAtlas atlas = read(fixture());
        GameplayAtlas.Frame standing = atlas.frames[0], downed = atlas.frames[1];
        assertEquals(-8, standing.left); assertEquals(0, standing.bottom);
        assertEquals(-23, downed.left); assertEquals(0, downed.bottom);
        assertEquals(96, downed.sourceHeight, "A fallen pose keeps its logical canvas");
        float rootY = 200, scale = atlas.actors[0].pixelScale;
        assertEquals(rootY, rootY + standing.bottom * scale);
        assertEquals(rootY, rootY + downed.bottom * scale);
        assertTrue(downed.height * scale < standing.height * scale,
            "Trimming must not enlarge a fallen body to standing height");
        assertEquals(64L * 64 * 4, atlas.textureBytes);
    }

    @Test void loopingClipsSupportReverseGaitAndActionsHoldTheirFinalPose() {
        GameplayAtlas atlas = read(fixture());
        GameplayAtlas.Actor warden = atlas.actors[ActorAnimation.Actor.WARDEN.ordinal()];
        GameplayAtlas.Clip walk = warden.clip(ActorAnimation.State.MOVE, ActorAnimation.Facing.SE);
        assertEquals(0, walk.frame(.01f)); assertEquals(1, walk.frame(.11f));
        assertEquals(1, walk.frame(-.01f)); assertEquals(0, walk.frame(.21f));
        GameplayAtlas.Clip attack = warden.clip(ActorAnimation.State.ATTACK, ActorAnimation.Facing.SE);
        assertEquals(0, attack.frame(-1)); assertEquals(1, attack.frame(100));
        assertEquals(0, attack.frame(Float.NaN));
    }

    @Test void incompleteCastAndMissingAuthoredFacingsAreRejected() {
        assertThrows(IllegalArgumentException.class,
            () -> read(fixture().replace("\"version\":1", "\"version\":1,\"preview\":true")));
        JsonValue missingActor = new JsonReader().parse(fixture());
        missingActor.require("actors").remove(6);
        assertThrows(IllegalArgumentException.class, () -> new GameplayAtlas(missingActor));
        JsonValue missingFacing = new JsonReader().parse(fixture());
        missingFacing.require("actors").get(0).require("clips").require("MOVE").remove("NE");
        assertThrows(IllegalArgumentException.class, () -> new GameplayAtlas(missingFacing));
        JsonValue missingMask = new JsonReader().parse(fixture());
        missingMask.require("regions").remove("shadow");
        assertThrows(IllegalArgumentException.class, () -> new GameplayAtlas(missingMask));
    }

    @Test void badPageTrimDurationOrLoopPolicyFailsBeforeTexturesAreLoaded() {
        assertThrows(IllegalArgumentException.class,
            () -> read(fixture().replace("\"rect\":[2,2,10,50]", "\"rect\":[60,2,10,50]")));
        assertThrows(IllegalArgumentException.class,
            () -> read(fixture().replace("\"offset\":[40,40]", "\"offset\":[90,40]")));
        assertThrows(IllegalArgumentException.class,
            () -> read(fixture().replace("\"durationsMs\":[100,100]", "\"durationsMs\":[0,100]")));
        assertThrows(IllegalArgumentException.class,
            () -> read(fixture().replace("\"file\":\"test.png\"", "\"file\":\"../test.png\"")));
        JsonValue wrongLoop = new JsonReader().parse(fixture());
        wrongLoop.require("actors").get(0).require("clips").require("ATTACK").require("SE")
            .require("loop").set(true);
        assertThrows(IllegalArgumentException.class, () -> new GameplayAtlas(wrongLoop));
    }

    @Test void aLargerGameplayBankCannotSilentlyExceedTheSixMiBEnvelope() {
        JsonValue root = new JsonReader().parse(fixture());
        JsonValue pages = root.require("pages");
        for (int i = 0; i < 2; i++)
            pages.addChild(new JsonReader().parse("{\"file\":\"page-" + i + ".png\",\"size\":[1024,1024]}"));
        assertThrows(IllegalArgumentException.class, () -> new GameplayAtlas(root));
    }

    private static GameplayAtlas read(String json) { return new GameplayAtlas(new JsonReader().parse(json)); }

    /** Small synthetic pixels test metadata behavior, not the appearance of production art. */
    private static String fixture() {
        StringBuilder json = new StringBuilder("{\"version\":1,\"pages\":[{\"file\":\"test.png\",\"size\":[64,64]}],\"frames\":[");
        json.append("{\"page\":0,\"rect\":[2,2,10,50],\"source\":[96,96],\"offset\":[40,40],\"pivot\":[48,90]},");
        json.append("{\"page\":0,\"rect\":[16,2,40,15],\"source\":[96,96],\"offset\":[25,75],\"pivot\":[48,90]}],\"actors\":[");
        boolean firstActor = true;
        for (ActorAnimation.Actor actor : ActorAnimation.Actor.values()) {
            if (!firstActor) json.append(','); firstActor = false;
            json.append("{\"id\":\"").append(actor).append("\",\"canvas\":[96,96],\"worldHeight\":120,\"clips\":{");
            boolean firstState = true;
            for (ActorAnimation.State state : ActorAnimation.State.values()) {
                if (!firstState) json.append(','); firstState = false;
                json.append('"').append(state).append("\":{"); boolean firstFacing = true;
                for (ActorAnimation.Facing facing : ActorAnimation.Facing.values()) {
                    if (!firstFacing) json.append(','); firstFacing = false;
                    boolean loop = state == ActorAnimation.State.IDLE || state == ActorAnimation.State.MOVE
                        || state == ActorAnimation.State.WINDUP;
                    json.append('"').append(facing).append("\":{\"frames\":[0,1],\"durationsMs\":[100,100],\"loop\":")
                        .append(loop).append('}');
                }
                json.append('}');
            }
            json.append("}}");
        }
        json.append("],\"regions\":{"); boolean firstRegion = true;
        for (String region : GameplayAtlas.REQUIRED_REGIONS) {
            if (!firstRegion) json.append(','); firstRegion = false;
            json.append('"').append(region).append("\":{\"frame\":0,\"worldHeight\":120}");
        }
        return json.append("}}").toString();
    }
}
