package com.couchcrypt.game;

import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.graphics.glutils.ShaderProgram;

/** Material-aware enemy recolouring using the original atlas and the normal SpriteBatch vertex layout. */
public final class EnemyPalette {
    private EnemyPalette() { }

    public static boolean materialAware(EnemyVariant variant) {
        return variant == EnemyVariant.FROST || variant == EnemyVariant.DUSK;
    }

    /** Cache this wrapper at art construction; the vertex shader removes the palette marker before sampling. */
    public static TextureRegion region(TextureRegion source, EnemyVariant variant) {
        if (!materialAware(variant)) return source;
        float offset = variant == EnemyVariant.FROST ? 4f : 6f;
        TextureRegion marked = new TextureRegion();
        marked.setTexture(source.getTexture());
        // Individual U setters preserve the original sampling inset of one-pixel utility regions.
        marked.setU(source.getU() + offset);
        marked.setU2(source.getU2() + offset);
        marked.setV(source.getV());
        marked.setV2(source.getV2());
        return marked;
    }

    public static ShaderProgram createShader() {
        ShaderProgram shader = new ShaderProgram(VERTEX, FRAGMENT);
        if (!shader.isCompiled()) {
            String log = shader.getLog();
            shader.dispose();
            throw new IllegalStateException("Enemy palette shader failed: " + log);
        }
        return shader;
    }

    private static final String VERTEX = """
        #ifdef GL_ES
        precision highp float;
        #define LOWP lowp
        #define MEDIUMP mediump
        #define HIGHP highp
        #else
        #define LOWP
        #define MEDIUMP
        #define HIGHP
        #endif
        attribute vec4 a_position;
        attribute vec4 a_color;
        attribute vec2 a_texCoord0;
        uniform mat4 u_projTrans;
        varying LOWP vec4 v_color;
        varying HIGHP vec2 v_texCoords;
        varying MEDIUMP float v_palette;
        void main() {
            v_color = a_color;
            v_color.a *= 255.0 / 254.0;
            v_texCoords = a_texCoord0;
            v_palette = 0.0;
            if (a_texCoord0.x > 5.5) {
                v_texCoords.x -= 6.0;
                v_palette = 2.0;
            } else if (a_texCoord0.x > 3.5) {
                v_texCoords.x -= 4.0;
                v_palette = 1.0;
            }
            gl_Position = u_projTrans * a_position;
        }
        """;

    private static final String FRAGMENT = """
        #ifdef GL_ES
        precision mediump float;
        #define LOWP lowp
        #define MEDIUMP mediump
        #define HIGHP highp
        #else
        #define LOWP
        #define MEDIUMP
        #define HIGHP
        #endif
        varying LOWP vec4 v_color;
        varying HIGHP vec2 v_texCoords;
        varying MEDIUMP float v_palette;
        uniform sampler2D u_texture;
        void main() {
            vec4 source = texture2D(u_texture, v_texCoords);
            vec3 rgb = source.rgb;
            if (v_palette > 0.5) {
                const vec3 luminanceWeights = vec3(0.299, 0.587, 0.114);
                float luminance = dot(rgb, luminanceWeights);
                vec3 target;
                float mask;
                if (v_palette < 1.5) {
                    float warmth = smoothstep(0.025, 0.11, rgb.r - rgb.b);
                    float skinRatio = smoothstep(0.53, 0.69, rgb.g / max(rgb.r, 0.001));
                    float lightSkin = smoothstep(0.10, 0.22, luminance);
                    mask = warmth * skinRatio * lightSkin;
                    target = vec3(0.50, 0.72, 0.88);
                } else {
                    mask = smoothstep(0.005, 0.055, min(rgb.r, rgb.b) - rgb.g);
                    target = vec3(0.20, 0.36, 0.57);
                }
                vec3 recoloured = clamp(target * (luminance / dot(target, luminanceWeights)), 0.0, 1.0);
                rgb = mix(rgb, recoloured, mask);
            }
            gl_FragColor = vec4(rgb, source.a) * v_color;
        }
        """;
}
