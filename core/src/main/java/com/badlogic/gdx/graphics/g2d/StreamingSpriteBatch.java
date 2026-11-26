package com.badlogic.gdx.graphics.g2d;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.Mesh;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.VertexAttribute;
import com.badlogic.gdx.graphics.VertexAttributes;
import com.badlogic.gdx.graphics.glutils.ShaderProgram;

/**
 * Pinned LibGDX 1.14.2 adapter: inherited draw APIs write its package-private vertices/idx.
 * Only texture-switch flushes are deferred. Every explicit/state/capacity barrier still submits
 * immediately, preserving painted vertices, texture order, shader uniforms and blend state.
 * Recheck the pinned package-private contract when updating LibGDX; no reflection/source fork.
 */
public class StreamingSpriteBatch extends SpriteBatch {
    private static final int MAX_RUNS = 256;
    private final Mesh streamingMesh;
    private final float[] stagedVertices;
    private final Texture[] textures = new Texture[MAX_RUNS];
    private final int[] offsets = new int[MAX_RUNS], counts = new int[MAX_RUNS];
    private int stagedFloats, runCount;
    private long vertexUploads;
    private boolean deferFlush, disposed;

    public StreamingSpriteBatch(int size, ShaderProgram shader) {
        super(size, shader);
        Mesh created = null;
        try {
            stagedVertices = new float[size * 20];
            created = new Mesh(false, size * 4, size * 6,
                new VertexAttribute(VertexAttributes.Usage.Position, 2, ShaderProgram.POSITION_ATTRIBUTE),
                new VertexAttribute(VertexAttributes.Usage.ColorPacked, 4, ShaderProgram.COLOR_ATTRIBUTE),
                new VertexAttribute(VertexAttributes.Usage.TextureCoordinates, 2, "a_texCoord0"));
            short[] indices = new short[size * 6];
            for (int sprite = 0, at = 0; sprite < size; sprite++) {
                int vertex = sprite * 4;
                indices[at++] = (short) vertex; indices[at++] = (short) (vertex + 1); indices[at++] = (short) (vertex + 2);
                indices[at++] = (short) (vertex + 2); indices[at++] = (short) (vertex + 3); indices[at++] = (short) vertex;
            }
            created.setIndices(indices);
            streamingMesh = created;
        } catch (RuntimeException | Error failure) {
            try { if (created != null) created.dispose(); }
            finally { super.dispose(); }
            throw failure;
        }
    }

    @Override protected void switchTexture(Texture texture) {
        boolean previous = deferFlush;
        deferFlush = true;
        try { super.switchTexture(texture); }
        finally { deferFlush = previous; }
    }

    @Override public void flush() {
        if (idx > 0) {
            // Preserve the original handling of malformed partial raw-sprite payloads too.
            if (idx % 20 != 0) {
                submit(); super.flush(); vertexUploads++; return;
            }
            if (runCount == MAX_RUNS || stagedFloats + idx > stagedVertices.length) submit();
            textures[runCount] = lastTexture;
            offsets[runCount] = stagedFloats / 20 * 6;
            counts[runCount++] = idx / 20 * 6;
            System.arraycopy(vertices, 0, stagedVertices, stagedFloats, idx);
            stagedFloats += idx;
            idx = 0;
        }
        if (!deferFlush) submit();
    }

    private void submit() {
        if (runCount == 0) return;
        ShaderProgram shader = getShader();
        Gdx.gl.glActiveTexture(GL20.GL_TEXTURE0);
        streamingMesh.setVertices(stagedVertices, 0, stagedFloats);
        if (isBlendingEnabled()) {
            Gdx.gl.glEnable(GL20.GL_BLEND);
            if (getBlendSrcFunc() != -1) Gdx.gl.glBlendFuncSeparate(getBlendSrcFunc(), getBlendDstFunc(),
                getBlendSrcFuncAlpha(), getBlendDstFuncAlpha());
        } else Gdx.gl.glDisable(GL20.GL_BLEND);
        try {
            streamingMesh.bind(shader);
            vertexUploads++;
            for (int run = 0; run < runCount; run++) {
                textures[run].bind();
                renderCalls++; totalRenderCalls++;
                maxSpritesInBatch = Math.max(maxSpritesInBatch, counts[run] / 6);
                streamingMesh.render(shader, GL20.GL_TRIANGLES, offsets[run], counts[run], false);
            }
        } finally {
            try { streamingMesh.unbind(shader); }
            finally {
                for (int run = 0; run < runCount; run++) textures[run] = null;
                runCount = stagedFloats = 0;
                Gdx.gl.glActiveTexture(GL20.GL_TEXTURE0);
            }
        }
    }

    /** Cumulative vertex-buffer submissions; index data is static and textures are borrowed. */
    public long vertexUploadCount() { return vertexUploads; }

    @Override public void dispose() {
        if (disposed) return;
        disposed = true;
        try { streamingMesh.dispose(); }
        finally {
            for (int run = 0; run < runCount; run++) textures[run] = null;
            runCount = stagedFloats = 0;
            super.dispose();
        }
    }
}
