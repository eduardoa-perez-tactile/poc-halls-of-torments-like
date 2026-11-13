package com.couchcrypt.ui;

import com.badlogic.gdx.utils.GdxRuntimeException;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/** Exercises real worker races without requiring a GL context or native image fixture. */
class OutcomeArtLoaderTest {
    @Test void coalescesWaitingRequestsAndDeliversOnlyTheLatestFailureOnce() throws Exception {
        CountDownLatch started = new CountDownLatch(1), release = new CountDownLatch(1);
        List<Boolean> decoded = new CopyOnWriteArrayList<>();
        RuntimeException stale = new RuntimeException("stale"), latest = new RuntimeException("latest");
        OutcomeArt.Loader loader = new OutcomeArt.Loader(victory -> {
            decoded.add(victory);
            if (decoded.size() == 1) {
                started.countDown();
                try { assertTrue(release.await(2, TimeUnit.SECONDS)); }
                catch (InterruptedException error) { throw new IllegalStateException(error); }
                throw stale;
            }
            throw latest;
        });
        try {
            long old = loader.request(true);
            assertTrue(started.await(2, TimeUnit.SECONDS));
            loader.request(false);
            long current = loader.request(true);
            release.countDown();
            GdxRuntimeException failure = awaitFailure(loader, current);
            assertSame(latest, failure.getCause());
            assertEquals(List.of(true, true), decoded, "Only latest waiting request should decode");
            assertNull(loader.poll(old, null));
            assertNull(loader.poll(current, null), "Failure must be consumed once");
            assertEquals(0, loader.preparedPixmapBytes());
        } finally { release.countDown(); loader.dispose(); }
    }

    @Test void cancellingOrDisposingDuringDecodeInvalidatesCompletion() throws Exception {
        CountDownLatch started = new CountDownLatch(1), release = new CountDownLatch(1), finished = new CountDownLatch(1);
        OutcomeArt.Loader loader = new OutcomeArt.Loader(victory -> {
            started.countDown();
            try { release.await(2, TimeUnit.SECONDS); }
            catch (InterruptedException ignored) { /* Disposal may interrupt a cancellable decoder. */ }
            finally { finished.countDown(); }
            throw new IllegalStateException("Late decode failure");
        });
        try {
            long id = loader.request(true);
            assertTrue(started.await(2, TimeUnit.SECONDS));
            loader.cancel();
            assertNull(loader.poll(id, null));
            loader.dispose(); release.countDown();
            assertTrue(finished.await(2, TimeUnit.SECONDS));
            assertNull(loader.poll(id, null));
            assertEquals(0, loader.preparedPixmapBytes());
            assertThrows(IllegalStateException.class, () -> loader.request(false));
            loader.dispose(); // Shutdown is safe when the owner also cleans up in its finally path.
        } finally { release.countDown(); loader.dispose(); }
    }

    private static GdxRuntimeException awaitFailure(OutcomeArt.Loader loader, long request) throws Exception {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(2);
        while (System.nanoTime() < deadline) {
            try { assertNull(loader.poll(request, null)); }
            catch (GdxRuntimeException failure) { return failure; }
            Thread.sleep(2);
        }
        throw new AssertionError("Worker did not publish the latest completion");
    }
}
