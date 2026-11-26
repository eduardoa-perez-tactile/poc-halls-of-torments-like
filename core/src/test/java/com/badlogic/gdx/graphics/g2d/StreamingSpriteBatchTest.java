package com.badlogic.gdx.graphics.g2d;

import com.badlogic.gdx.Application;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Graphics;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.GL30;
import com.badlogic.gdx.graphics.Mesh;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.glutils.ShaderProgram;
import com.badlogic.gdx.math.Affine2;
import com.badlogic.gdx.math.Matrix4;
import com.badlogic.gdx.utils.BufferUtils;
import com.badlogic.gdx.utils.GdxNativesLoader;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.nio.Buffer;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;
import java.nio.IntBuffer;
import java.nio.ShortBuffer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/** Real pinned SpriteBatch/Mesh code, with only driver calls replaced by a recording GL20 proxy. */
class StreamingSpriteBatchTest {
    private Application oldApp;
    private Graphics oldGraphics;
    private GL20 oldGl, oldGl20;
    private GL30 oldGl30;
    private Mesh.VertexDataType oldVertexType;
    private Driver driver;
    private ShaderProgram shader, otherShader;
    private final StubTexture first = new StubTexture(101, 64, 32), second = new StubTexture(102, 128, 96);

    @BeforeEach void installDriver() {
        GdxNativesLoader.load();
        oldApp = Gdx.app; oldGraphics = Gdx.graphics; oldGl = Gdx.gl; oldGl20 = Gdx.gl20; oldGl30 = Gdx.gl30;
        oldVertexType = SpriteBatch.overrideVertexType;
        driver = new Driver();
        Gdx.app = proxy(Application.class, (object, method, args) -> method.getName().equals("getType")
            ? Application.ApplicationType.Desktop : objectMethodOrDefault(object, method, args));
        Gdx.graphics = proxy(Graphics.class, (object, method, args) -> switch (method.getName()) {
            case "getWidth", "getBackBufferWidth" -> 1920;
            case "getHeight", "getBackBufferHeight" -> 1080;
            default -> objectMethodOrDefault(object, method, args);
        });
        Gdx.gl = Gdx.gl20 = proxy(GL20.class, driver); Gdx.gl30 = null;
        SpriteBatch.overrideVertexType = Mesh.VertexDataType.VertexBufferObject;
        shader = SpriteBatch.createDefaultShader();
        otherShader = SpriteBatch.createDefaultShader();
        assertTrue(shader.isCompiled()); assertTrue(otherShader.isCompiled());
    }

    @AfterEach void restoreDriver() {
        if (shader != null) shader.dispose();
        if (otherShader != null) otherShader.dispose();
        Mesh.clearAllMeshes(Gdx.app);
        ShaderProgram.clearAllShaderPrograms(Gdx.app);
        SpriteBatch.overrideVertexType = oldVertexType;
        Gdx.app = oldApp; Gdx.graphics = oldGraphics; Gdx.gl = oldGl; Gdx.gl20 = oldGl20; Gdx.gl30 = oldGl30;
    }

    @Test void inheritedDrawApisSubmitExactlyTheOriginalVerticesTexturesAndDrawOrderWithFewerUploads() {
        Consumer<SpriteBatch> scene = batch -> {
            TextureRegion region = new TextureRegion(first, 3, 5, 27, 19);
            TextureRegion other = new TextureRegion(second, 11, 13, 39, 41);
            batch.begin();
            batch.setColor(.31f, .74f, .96f, .57f);
            batch.draw(first, -12.5f, 23.75f, 18f, 27f);
            batch.draw(second, 51f, 12f, 4f, 7f, 33f, 22f, .8f, 1.2f, 37f, 3, 5, 31, 21, true, false);
            batch.draw(region, 13.3f, 44.8f, 7f, 9f, 25f, 35f, 1.1f, .7f, -24f);
            batch.setPackedColor(Color.toFloatBits(.91f, .27f, .41f, .73f));
            batch.draw(other, -44f, 62f, 4f, 8f, 21f, 32f, .9f, 1.3f, 81f, true);
            batch.draw(first, 33f, 52f, 21f, 12f, .1f, .2f, .7f, .8f);
            batch.draw(second, 7f, 18f, 3, 4, 21, 17);
            batch.draw(region, 17f, 23f, new Affine2().setToTrnRotScl(42f, 27f, 19f, .7f, 1.2f));
            batch.end();
        };
        Capture original = capture(false, 64, scene), streaming = capture(true, 64, scene);
        assertSameSubmission(original, streaming);
        assertEquals(7, original.vertexUploads);
        assertEquals(1, streaming.vertexUploads, "Alternating textures share one vertex upload, retaining every draw run");
        assertEquals(streaming.vertexUploads, streaming.reportedUploads);
    }

    @Test void rawSpritePayloadOffsetsAndCapacityFlushesPreserveEveryIndexedTriangle() {
        float[] payload = rawSprites(13);
        Consumer<SpriteBatch> scene = batch -> {
            batch.begin();
            batch.draw(first, payload, 20, 11 * 20);
            batch.draw(second, payload, 40, 3 * 20);
            batch.draw(first, payload, 0, 2 * 20);
            batch.end();
        };
        Capture original = capture(false, 3, scene), streaming = capture(true, 3, scene);
        assertSameSubmission(original, streaming);
        assertEquals(16 * 6, streaming.packets.stream().mapToInt(packet -> packet.vertices.length / 5).sum());
        assertTrue(streaming.maximumVertexBytes <= 3 * 4 * 20, "Fixed staging never grows to fit a bulk draw");
    }

    @Test void exceedingTheRunTableFlushesInOrderWithoutDroppingOrCombiningTextureRuns() {
        Consumer<SpriteBatch> scene = batch -> {
            batch.begin();
            for (int i = 0; i < 600; i++) batch.draw((i & 1) == 0 ? first : second, i, i * .25f, 3f, 4f);
            batch.end();
        };
        Capture original = capture(false, 800, scene), streaming = capture(true, 800, scene);
        assertSameSubmission(original, streaming);
        assertEquals(600, streaming.packets.size());
        assertTrue(streaming.vertexUploads < 10, "A bounded run table submits groups instead of growing");
        assertTrue(streaming.maximumVertexBytes <= 800 * 4 * 20);
    }

    @Test void explicitAndStateBarriersSubmitPendingTexturesBeforeTheStateChanges() {
        Consumer<SpriteBatch> scene = batch -> {
            batch.begin();
            pair(batch, 0); batch.flush(); assertEquals(2, driver.packets.size(), "Explicit flush is a native submission barrier");
            pair(batch, 10); batch.setProjectionMatrix(new Matrix4().setToOrtho2D(-500, -300, 1000, 600));
            assertEquals(4, driver.packets.size(), "Projection must not affect already staged vertices");
            pair(batch, 20); batch.setTransformMatrix(new Matrix4().setToTranslation(13f, 29f, 0));
            assertEquals(6, driver.packets.size(), "Transform changes flush old state first");
            pair(batch, 30); batch.disableBlending(); assertEquals(8, driver.packets.size());
            pair(batch, 40); batch.enableBlending(); assertEquals(10, driver.packets.size());
            pair(batch, 50); batch.setBlendFunctionSeparate(GL20.GL_ONE, GL20.GL_ONE_MINUS_SRC_ALPHA,
                GL20.GL_ONE, GL20.GL_ZERO); assertEquals(12, driver.packets.size());
            pair(batch, 60); batch.setShader(otherShader); assertEquals(14, driver.packets.size());
            pair(batch, 70); batch.setShader(null); assertEquals(16, driver.packets.size());
            pair(batch, 80); batch.flush();
            // The real ground-ring renderer uses this barrier before an independent native pass.
            driver.markers.add(driver.packets.size());
            pair(batch, 90); batch.end(); assertEquals(20, driver.packets.size(), "end submits all pending runs");
            assertFalse(batch.isDrawing());
            batch.begin(); batch.draw(first, 99f, 83f); batch.end();
        };
        Capture original = capture(false, 64, scene), streaming = capture(true, 64, scene);
        assertSameSubmission(original, streaming);
        assertEquals(original.markers, streaming.markers);
        assertEquals(List.of(18), streaming.markers);
    }

    @Test void frameReuseHasFixedNativeStorageAndDisposalReleasesBothMeshesButNotBorrowedArtOrShaders() {
        int nativeBefore = BufferUtils.getAllocatedBytesUnsafe();
        StreamingSpriteBatch batch = new StreamingSpriteBatch(8, shader);
        int handles = driver.liveBuffers.size(), nativeAllocated = BufferUtils.getAllocatedBytesUnsafe();
        int generations = driver.bufferGenerations;
        float[] inheritedVertices = batch.vertices;
        try {
            assertTrue(handles >= 4, "The pinned base mesh and streaming mesh are both tracked");
            for (int frame = 0; frame < 200; frame++) {
                batch.begin();
                for (int i = 0; i < 20; i++) batch.draw((i & 1) == 0 ? first : second, i, frame, 7f, 9f);
                batch.end();
                assertSame(inheritedVertices, batch.vertices);
                assertEquals(handles, driver.liveBuffers.size());
                assertEquals(generations, driver.bufferGenerations, "Frames cannot create more VBO/IBO handles");
                assertEquals(nativeAllocated, BufferUtils.getAllocatedBytesUnsafe(), "Native staging storage remains fixed");
                driver.packets.clear();
            }
            assertTrue(batch.vertexUploadCount() > 0);
        } finally { batch.dispose(); }
        assertTrue(driver.liveBuffers.isEmpty());
        assertEquals(nativeBefore, BufferUtils.getAllocatedBytesUnsafe());
        assertFalse(first.disposed); assertFalse(second.disposed);
        assertFalse(driver.deletedPrograms.contains(shader.getHandle()), "Borrowed shader remains owned by its caller");
        int deletions = driver.bufferDeletions;
        batch.dispose();
        assertEquals(deletions, driver.bufferDeletions, "Disposal cannot delete native handles twice");
    }

    @Test void repeatedEmptyBarriersNeverUploadOrDraw() {
        StreamingSpriteBatch batch = new StreamingSpriteBatch(4, shader);
        try {
            batch.begin(); batch.flush(); batch.flush(); batch.end();
            batch.begin(); batch.end();
            assertEquals(0, batch.vertexUploadCount());
            assertEquals(0, batch.totalRenderCalls);
            assertTrue(driver.packets.isEmpty());
        } finally { batch.dispose(); }
    }

    private void pair(SpriteBatch batch, float x) {
        batch.draw(first, x, 2f, 3f, 4f); batch.draw(second, x + 1f, 5f, 6f, 7f);
    }

    private Capture capture(boolean streaming, int size, Consumer<SpriteBatch> scene) {
        driver.resetObservations();
        SpriteBatch batch = streaming ? new StreamingSpriteBatch(size, shader) : new SpriteBatch(size, shader);
        try {
            scene.accept(batch);
            return new Capture(new ArrayList<>(driver.packets), new ArrayList<>(driver.markers), driver.vertexUploads,
                streaming ? ((StreamingSpriteBatch) batch).vertexUploadCount() : driver.vertexUploads,
                driver.maximumVertexBytes, batch.totalRenderCalls, batch.maxSpritesInBatch);
        } finally { batch.dispose(); }
    }

    private static void assertSameSubmission(Capture expected, Capture actual) {
        assertEquals(expected.packets.size(), actual.packets.size(), "Ordered native texture runs");
        for (int i = 0; i < expected.packets.size(); i++) {
            Packet a = expected.packets.get(i), b = actual.packets.get(i);
            assertEquals(a.texture, b.texture, "Texture at draw " + i);
            assertEquals(a.program, b.program, "Shader at draw " + i);
            assertEquals(a.blending, b.blending, "Blend enable at draw " + i);
            assertEquals(a.depthWrite, b.depthWrite, "Depth write at draw " + i);
            assertArrayEquals(a.blend, b.blend, "Blend functions at draw " + i);
            assertArrayEquals(a.matrix, b.matrix, "Projection/transform at draw " + i);
            assertArrayEquals(a.vertices, b.vertices, "Raw float bits in indexed triangle order at draw " + i);
        }
        assertEquals(expected.renderCalls, actual.renderCalls);
        assertEquals(expected.maximumSprites, actual.maximumSprites);
    }

    private static float[] rawSprites(int count) {
        float[] result = new float[count * 20];
        for (int sprite = 0; sprite < count; sprite++) for (int vertex = 0; vertex < 4; vertex++) {
            int at = sprite * 20 + vertex * 5;
            result[at] = sprite * 11.5f + vertex; result[at + 1] = sprite * -7.25f + vertex * 3;
            result[at + 2] = Color.toFloatBits(.31f + sprite * .01f, .41f, .91f, .57f);
            result[at + 3] = vertex * .21f; result[at + 4] = sprite * .03f;
        }
        return result;
    }

    @SuppressWarnings("unchecked")
    private static <T> T proxy(Class<T> type, InvocationHandler handler) {
        return (T) Proxy.newProxyInstance(type.getClassLoader(), new Class<?>[]{type}, handler);
    }

    private static Object objectMethodOrDefault(Object object, Method method, Object[] args) {
        return switch (method.getName()) {
            case "hashCode" -> System.identityHashCode(object);
            case "equals" -> object == args[0];
            case "toString" -> "Recording " + method.getDeclaringClass().getSimpleName();
            default -> defaultValue(method.getReturnType());
        };
    }

    private static Object defaultValue(Class<?> type) {
        if (type == boolean.class) return false;
        if (type == int.class) return 0;
        if (type == long.class) return 0L;
        if (type == float.class) return 0f;
        if (type == double.class) return 0d;
        return null;
    }

    private static final class StubTexture extends Texture {
        private final int width, height;
        boolean disposed;
        StubTexture(int handle, int width, int height) { super(); glHandle = handle; this.width = width; this.height = height; }
        @Override public int getWidth() { return width; }
        @Override public int getHeight() { return height; }
        @Override public int getDepth() { return 0; }
        @Override public boolean isManaged() { return false; }
        @Override public void dispose() { disposed = true; }
    }

    private record Packet(int texture, int program, boolean blending, boolean depthWrite,
                          int[] blend, int[] matrix, int[] vertices) { }
    private record Capture(List<Packet> packets, List<Integer> markers, long vertexUploads, long reportedUploads,
                           int maximumVertexBytes, int renderCalls, int maximumSprites) { }

    private static final class Driver implements InvocationHandler {
        final Map<Integer, byte[]> buffers = new HashMap<>();
        final Set<Integer> liveBuffers = new HashSet<>(), deletedPrograms = new HashSet<>();
        final Map<Integer, int[]> matrices = new HashMap<>();
        final List<Packet> packets = new ArrayList<>();
        final List<Integer> markers = new ArrayList<>();
        int nextHandle = 10, arrayBuffer, elementBuffer, texture, program;
        int bufferGenerations, bufferDeletions, maximumVertexBytes;
        long vertexUploads;
        boolean blending, depthWrite = true;
        int[] blend = {GL20.GL_SRC_ALPHA, GL20.GL_ONE_MINUS_SRC_ALPHA, GL20.GL_SRC_ALPHA, GL20.GL_ONE_MINUS_SRC_ALPHA};

        void resetObservations() {
            packets.clear(); markers.clear(); vertexUploads = 0; maximumVertexBytes = 0;
            blending = false; depthWrite = true;
        }

        @Override public Object invoke(Object object, Method method, Object[] args) {
            switch (method.getName()) {
                case "glCreateShader", "glCreateProgram" -> { return ++nextHandle; }
                case "glGetShaderiv" -> ((IntBuffer) args[2]).put(0, 1);
                case "glGetProgramiv" -> {
                    int property = (int) args[1];
                    ((IntBuffer) args[2]).put(0, property == GL20.GL_ACTIVE_ATTRIBUTES ? 3
                        : property == GL20.GL_ACTIVE_UNIFORMS ? 0 : 1);
                }
                case "glGetActiveAttrib" -> {
                    int index = (int) args[1];
                    ((IntBuffer) args[2]).put(0, 1); ((IntBuffer) args[3]).put(0, GL20.GL_FLOAT);
                    return new String[]{"a_position", "a_color", "a_texCoord0"}[index];
                }
                case "glGetAttribLocation" -> {
                    return switch ((String) args[1]) { case "a_position" -> 0; case "a_color" -> 1; default -> 2; };
                }
                case "glGetUniformLocation" -> { return args[1].equals("u_projTrans") ? 1 : 2; }
                case "glUseProgram" -> program = (int) args[0];
                case "glUniformMatrix4fv" -> {
                    int[] bits = new int[16];
                    if (args[3] instanceof float[] values) {
                        int offset = (int) args[4];
                        for (int i = 0; i < 16; i++) bits[i] = Float.floatToRawIntBits(values[offset + i]);
                    } else {
                        FloatBuffer values = ((FloatBuffer) args[3]).duplicate();
                        for (int i = 0; i < 16; i++) bits[i] = Float.floatToRawIntBits(values.get(values.position() + i));
                    }
                    matrices.put(program, bits);
                }
                case "glGenBuffer" -> {
                    int handle = ++nextHandle; liveBuffers.add(handle); bufferGenerations++; return handle;
                }
                case "glDeleteBuffer" -> {
                    int handle = (int) args[0];
                    assertTrue(liveBuffers.remove(handle), "Deleting an unknown/already disposed buffer " + handle);
                    buffers.remove(handle); bufferDeletions++;
                }
                case "glBindBuffer" -> {
                    if ((int) args[0] == GL20.GL_ARRAY_BUFFER) arrayBuffer = (int) args[1];
                    else if ((int) args[0] == GL20.GL_ELEMENT_ARRAY_BUFFER) elementBuffer = (int) args[1];
                }
                case "glBufferData" -> {
                    int target = (int) args[0], bytes = (int) args[1], handle = target == GL20.GL_ARRAY_BUFFER ? arrayBuffer : elementBuffer;
                    byte[] contents = bytes((Buffer) args[2], bytes);
                    buffers.put(handle, contents);
                    if (target == GL20.GL_ARRAY_BUFFER && args[2] != null) {
                        vertexUploads++; maximumVertexBytes = Math.max(maximumVertexBytes, bytes);
                    }
                }
                case "glBufferSubData" -> {
                    int target = (int) args[0], offset = (int) args[1], bytes = (int) args[2];
                    int handle = target == GL20.GL_ARRAY_BUFFER ? arrayBuffer : elementBuffer;
                    byte[] contents = buffers.get(handle);
                    System.arraycopy(bytes((Buffer) args[3], bytes), 0, contents, offset, bytes);
                    if (target == GL20.GL_ARRAY_BUFFER) {
                        vertexUploads++; maximumVertexBytes = Math.max(maximumVertexBytes, offset + bytes);
                    }
                }
                case "glBindTexture" -> texture = (int) args[1];
                case "glEnable" -> { if ((int) args[0] == GL20.GL_BLEND) blending = true; }
                case "glDisable" -> { if ((int) args[0] == GL20.GL_BLEND) blending = false; }
                case "glBlendFuncSeparate" -> blend = new int[]{(int) args[0], (int) args[1], (int) args[2], (int) args[3]};
                case "glDepthMask" -> depthWrite = (boolean) args[0];
                case "glDeleteProgram" -> deletedPrograms.add((int) args[0]);
                case "glDrawElements" -> {
                    assertEquals(GL20.GL_TRIANGLES, args[0]); assertEquals(GL20.GL_UNSIGNED_SHORT, args[2]);
                    int count = (int) args[1], offset = (int) args[3];
                    ByteBuffer indices = ByteBuffer.wrap(buffers.get(elementBuffer)).order(ByteOrder.nativeOrder());
                    ByteBuffer vertices = ByteBuffer.wrap(buffers.get(arrayBuffer)).order(ByteOrder.nativeOrder());
                    int[] expanded = new int[count * 5];
                    for (int i = 0; i < count; i++) {
                        int vertex = Short.toUnsignedInt(indices.getShort(offset + i * 2));
                        for (int component = 0; component < 5; component++) expanded[i * 5 + component] = vertices.getInt(vertex * 20 + component * 4);
                    }
                    packets.add(new Packet(texture, program, blending, depthWrite, blend.clone(),
                        matrices.get(program).clone(), expanded));
                }
                default -> { return objectMethodOrDefault(object, method, args); }
            }
            return defaultValue(method.getReturnType());
        }

        private static byte[] bytes(Buffer source, int count) {
            byte[] result = new byte[count];
            if (source == null) return result;
            ByteBuffer destination = ByteBuffer.wrap(result).order(ByteOrder.nativeOrder());
            if (source instanceof ByteBuffer data) data.duplicate().get(result);
            else if (source instanceof FloatBuffer data) {
                FloatBuffer values = data.duplicate();
                for (int i = 0; i < count / 4; i++) destination.putFloat(values.get());
            } else if (source instanceof ShortBuffer data) {
                ShortBuffer values = data.duplicate();
                for (int i = 0; i < count / 2; i++) destination.putShort(values.get());
            } else throw new AssertionError("Unexpected buffer type " + source.getClass());
            return result;
        }
    }
}
