package com.couchcrypt.game;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.Mesh;
import com.badlogic.gdx.graphics.VertexAttribute;
import com.badlogic.gdx.graphics.VertexAttributes;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.graphics.glutils.ShaderProgram;
import com.badlogic.gdx.math.Matrix4;
import com.badlogic.gdx.utils.Disposable;

/** Static GPU geometry for large ranges; small warnings retain normal SpriteBatch batching. */
final class PlayerFootRing implements Disposable {
    static final float STROKE_MARGIN = RingGeometry.STROKE_MARGIN;
    private final Mesh mesh;
    private final ShaderProgram shader;
    private final Matrix4 projection = new Matrix4();
    private final TextureRegion pixel;
    private final RingGeometry.Small small;
    int drawCalls;

    PlayerFootRing(TextureRegion pixel) {
        this.pixel = pixel;
        small = new RingGeometry.Small((pixel.getU() + pixel.getU2()) * .5f, (pixel.getV() + pixel.getV2()) * .5f);
        ShaderProgram loadedShader = new ShaderProgram(VERTEX, FRAGMENT);
        if (!loadedShader.isCompiled()) {
            String log = loadedShader.getLog(); loadedShader.dispose();
            throw new IllegalStateException("Ground outline shader failed: " + log);
        }
        Mesh loadedMesh = null;
        try {
            RingGeometry geometry = new RingGeometry();
            // Large outlines share 768 vertices. Managed static buffers restore after context loss.
            loadedMesh = new Mesh(true, geometry.vertices.length / RingGeometry.STRIDE, geometry.indices.length,
                new VertexAttribute(VertexAttributes.Usage.Position, 2, "a_unit"),
                new VertexAttribute(VertexAttributes.Usage.Normal, 2, "a_normal"),
                new VertexAttribute(VertexAttributes.Usage.Generic, 2, "a_edge"));
            loadedMesh.setVertices(geometry.vertices); loadedMesh.setIndices(geometry.indices);
        } catch (RuntimeException | Error failure) {
            if (loadedMesh != null) loadedMesh.dispose();
            loadedShader.dispose(); throw failure;
        }
        shader = loadedShader; mesh = loadedMesh;
    }

    void draw(SpriteBatch batch, float x, float y, float radius, int slot) {
        if (slot == 0) draw(batch, x, y, radius, 1f, .82f, .42f, .57f);
        else draw(batch, x, y, radius, .36f, .95f, .90f, .57f);
    }

    /** Flush in place so ground warning/actor ordering and the caller's batch state are preserved. */
    void draw(SpriteBatch batch, float x, float y, float radius, float red, float green, float blue, float alpha) {
        if (!Float.isFinite(radius) || radius <= 0) return;
        if (radius <= 90f) {
            small.update(x, y, radius, red, green, blue, alpha);
            batch.draw(pixel.getTexture(), small.vertices, 0, small.vertices.length);
            return;
        }
        batch.flush();
        if (batch.isBlendingEnabled()) {
            Gdx.gl.glEnable(GL20.GL_BLEND);
            Gdx.gl.glBlendFuncSeparate(batch.getBlendSrcFunc(), batch.getBlendDstFunc(),
                batch.getBlendSrcFuncAlpha(), batch.getBlendDstFuncAlpha());
        } else Gdx.gl.glDisable(GL20.GL_BLEND);
        shader.bind();
        shader.setUniformMatrix("u_projection", projection.set(batch.getProjectionMatrix()).mul(batch.getTransformMatrix()));
        shader.setUniformf("u_ring", x, y, radius);
        shader.setUniformf("u_color", RingGeometry.channel(red), RingGeometry.channel(green),
            RingGeometry.channel(blue), RingGeometry.alpha(alpha));
        mesh.render(shader, GL20.GL_TRIANGLES, 0, RingGeometry.FULL_INDICES);
        drawCalls++;
        batch.getShader().bind();
    }

    @Override public void dispose() { mesh.dispose(); shader.dispose(); }

    private static final String VERTEX = """
        #ifdef GL_ES
        precision highp float;
        #define LOWP lowp
        #else
        #define LOWP
        #endif
        attribute vec2 a_unit;
        attribute vec2 a_normal;
        attribute vec2 a_edge;
        uniform mat4 u_projection;
        uniform vec3 u_ring;
        uniform vec4 u_color;
        varying LOWP vec4 v_color;
        void main() {
            vec2 position = u_ring.xy + a_unit * u_ring.z + a_normal * a_edge.x;
            gl_Position = u_projection * vec4(position, 0.0, 1.0);
            v_color = vec4(u_color.rgb, u_color.a * a_edge.y);
        }
        """;

    private static final String FRAGMENT = """
        #ifdef GL_ES
        precision mediump float;
        #define LOWP lowp
        #else
        #define LOWP
        #endif
        varying LOWP vec4 v_color;
        void main() {
            gl_FragColor = v_color;
        }
        """;
}
