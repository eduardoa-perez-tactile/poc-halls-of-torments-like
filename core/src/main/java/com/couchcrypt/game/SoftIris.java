package com.couchcrypt.game;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.Mesh;
import com.badlogic.gdx.graphics.VertexAttribute;
import com.badlogic.gdx.graphics.VertexAttributes;
import com.badlogic.gdx.graphics.glutils.ShaderProgram;
import com.badlogic.gdx.utils.Disposable;

/** Constant cosmetic iris in the current camera's viewport; no world visibility or texture state. */
public final class SoftIris implements Disposable {
    private final ShaderProgram shader;
    private final Mesh quad;
    private boolean disposed;

    public SoftIris() {
        ShaderProgram loadedShader = new ShaderProgram(VERTEX, FRAGMENT);
        if (!loadedShader.isCompiled()) {
            String log = loadedShader.getLog();
            loadedShader.dispose();
            throw new IllegalStateException("Soft iris shader failed: " + log);
        }
        Mesh loadedQuad = null;
        try {
            // Managed LibGDX resources restore on Android GL-context recreation.
            loadedQuad = new Mesh(true, 4, 6,
                new VertexAttribute(VertexAttributes.Usage.Position, 2, ShaderProgram.POSITION_ATTRIBUTE));
            loadedQuad.setVertices(new float[] {-1f, -1f, 1f, -1f, 1f, 1f, -1f, 1f});
            loadedQuad.setIndices(new short[] {0, 1, 2, 2, 3, 0});
        } catch (RuntimeException | Error failure) {
            if (loadedQuad != null) loadedQuad.dispose();
            loadedShader.dispose();
            throw failure;
        }
        shader = loadedShader;
        quad = loadedQuad;
    }

    /**
     * Draw after the world batch ends and before the HUD. The caller owns viewport/scissor.
     * One static quad, one draw call, no textures, framebuffers or per-frame allocations.
     */
    public void render() {
        Gdx.gl.glDepthMask(false);
        Gdx.gl.glEnable(GL20.GL_BLEND);
        Gdx.gl.glBlendFunc(GL20.GL_SRC_ALPHA, GL20.GL_ONE_MINUS_SRC_ALPHA);
        try {
            shader.bind();
            quad.render(shader, GL20.GL_TRIANGLES);
        } finally {
            // Match SpriteBatch.end(): the next floor or HUD batch establishes its own shader/blending.
            Gdx.gl.glDisable(GL20.GL_BLEND);
            Gdx.gl.glDepthMask(true);
        }
    }

    @Override public void dispose() {
        if (disposed) return;
        disposed = true;
        quad.dispose();
        shader.dispose();
    }

    private static final String VERTEX = """
        #ifdef GL_ES
        precision highp float;
        #define MEDIUMP mediump
        #else
        #define MEDIUMP
        #endif
        attribute vec2 a_position;
        varying MEDIUMP vec2 v_irisPosition;
        void main() {
            v_irisPosition = a_position;
            gl_Position = vec4(a_position, 0.0, 1.0);
        }
        """;

    private static final String FRAGMENT = """
        #ifdef GL_ES
        precision mediump float;
        #define MEDIUMP mediump
        #else
        #define MEDIUMP
        #endif
        varying MEDIUMP vec2 v_irisPosition;
        void main() {
            float darkness = 0.94 * smoothstep(0.72, 1.08, length(v_irisPosition));
            gl_FragColor = vec4(vec3(7.0, 11.0, 20.0) / 255.0, darkness);
        }
        """;
}
