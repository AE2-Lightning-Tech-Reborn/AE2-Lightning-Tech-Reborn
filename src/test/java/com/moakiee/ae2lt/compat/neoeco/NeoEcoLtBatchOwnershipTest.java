package com.moakiee.ae2lt.compat.neoeco;

import static org.junit.jupiter.api.Assertions.*;

import appeng.api.networking.crafting.ICraftingProvider;
import appeng.api.stacks.KeyCounter;
import com.moakiee.ae2lt.me.key.LightningKey;
import com.moakiee.thunderbolt.api.crafting.batch.BatchJobView;
import java.io.File;
import java.lang.reflect.Proxy;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import javax.tools.ToolProvider;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** LT's adapter, independently of Core's adapter, against a test-owned public protocol. */
class NeoEcoLtBatchOwnershipTest {
    @TempDir static Path temporary;

    @BeforeAll
    static void compileProtocol() throws Exception {
        com.moakiee.ae2lt.testing.MinecraftTestBootstrap.bootstrap();
        var classpath = new LinkedHashSet<>(Arrays.asList(
                System.getProperty("java.class.path").split(File.pathSeparator)));
        for (var loader = NeoEcoLtBatchOwnershipTest.class.getClassLoader(); loader != null; loader = loader.getParent()) {
            if (loader instanceof URLClassLoader urls) for (var url : urls.getURLs())
                if (url.getProtocol().equals("file")) classpath.add(Path.of(url.toURI()).toString());
        }
        var resource = NeoEcoLtBatchOwnershipTest.class.getResource("/neoeco-allocated-contract");
        assertNotNull(resource);
        var arguments = new ArrayList<>(java.util.List.of("-encoding", "UTF-8", "-proc:none", "--release", "17",
                "-classpath", String.join(File.pathSeparator, classpath), "-d", temporary.toString()));
        try (var files = Files.walk(Path.of(resource.toURI()))) {
            files.filter(path -> path.toString().endsWith(".java")).forEach(path -> arguments.add(path.toString()));
        }
        assertEquals(0, ToolProvider.getSystemJavaCompiler().run(null, null, null, arguments.toArray(String[]::new)));
    }

    @Test
    void providerPreviewCannotConsumeTheCallersBorrowedTemplate() throws Exception {
        try (var fixture = new Fixture()) {
            var inputs = inputs();
            var batch = fixture.adapter.prepareBatch(null, inputs, 10, fixture.job());
            assertEquals(3, batch.capacity());
            assertEquals(2, inputs[0].get(LightningKey.HIGH_VOLTAGE));
            assertEquals(7, batch.push(10));
            assertEquals(1, fixture.get("submissions"));
        }
    }

    @Test
    void shrinkingUsesThePreparedJobIdentityAndTemplateAfterTheViewIsRebound() throws Exception {
        try (var fixture = new Fixture()) {
            var inputs = inputs();
            var original = fixture.id.get();
            var batch = fixture.adapter.prepareBatch(null, inputs, 10, fixture.job());
            fixture.id.set(UUID.randomUUID());
            inputs[0].clear();
            inputs[0].add(LightningKey.HIGH_VOLTAGE, 99);
            fixture.set("count", 2L);
            assertEquals(0, batch.push(2));
            assertEquals(original, fixture.get("jobId"));
            assertEquals(1, fixture.jobReads.get());
            assertEquals(2, fixture.get("preparations"));
            assertEquals(1, fixture.get("submissions"));
            assertEquals(99, inputs[0].get(LightningKey.HIGH_VOLTAGE));
        }
    }

    @Test
    void zeroOfferDoesNotPrepareOrSubmitAnotherBatch() throws Exception {
        try (var fixture = new Fixture()) {
            var batch = fixture.adapter.prepareBatch(null, inputs(), 10, fixture.job());
            assertEquals(0, batch.push(0));
            assertEquals(1, fixture.get("preparations"));
            assertEquals(0, fixture.get("submissions"));
            assertThrows(IllegalStateException.class, () -> batch.push(3));
        }
    }

    @Test
    void rejectedBatchReturnsEveryCopyAndCannotBeResubmitted() throws Exception {
        try (var fixture = new Fixture()) {
            fixture.set("accept", false);
            var batch = fixture.adapter.prepareBatch(null, inputs(), 10, fixture.job());
            assertEquals(10, batch.push(10));
            assertThrows(IllegalStateException.class, () -> batch.push(10));
            assertEquals(1, fixture.get("submissions"));
            assertEquals(0, fixture.get("ordinary"));
        }
    }

    @Test
    void uncertainSubmissionRetainsOwnershipAndCannotRetry() throws Exception {
        try (var fixture = new Fixture()) {
            fixture.set("fail", true);
            var batch = fixture.adapter.prepareBatch(null, inputs(), 10, fixture.job());
            var error = assertThrows(RuntimeException.class, () -> batch.push(3));
            assertTrue(NeoEcoCraftingCompat.isIndeterminate(error));
            assertThrows(IllegalStateException.class, () -> batch.push(3));
            assertEquals(1, fixture.get("submissions"));
            assertEquals(0, fixture.get("ordinary"));
        }
    }

    private static KeyCounter[] inputs() {
        var counter = new KeyCounter();
        counter.add(LightningKey.HIGH_VOLTAGE, 2);
        return new KeyCounter[]{counter};
    }

    private static final class Fixture implements AutoCloseable {
        final URLClassLoader loader = new URLClassLoader(new java.net.URL[]{temporary.toUri().toURL()},
                NeoEcoLtBatchOwnershipTest.class.getClassLoader());
        final ICraftingProvider provider;
        final NeoEcoCraftingCompat.Adapter adapter;
        final AtomicReference<UUID> id = new AtomicReference<>(UUID.randomUUID());
        final AtomicInteger jobReads = new AtomicInteger();

        Fixture() throws Exception {
            provider = (ICraftingProvider) loader.loadClass(
                    "cn.dancingsnow.neoecoae.api.me.ECOFastPathFacade$Provider").getConstructor().newInstance();
            set("key", LightningKey.HIGH_VOLTAGE);
            adapter = new NeoEcoCraftingCompat.Adapter(provider, new NeoEcoCraftingCompat.Api(loader));
        }

        BatchJobView job() {
            return (BatchJobView) Proxy.newProxyInstance(BatchJobView.class.getClassLoader(),
                    new Class<?>[]{BatchJobView.class}, (proxy, method, args) -> switch (method.getName()) {
                        case "level" -> null;
                        case "craftingId" -> { jobReads.incrementAndGet(); yield id.get(); }
                        default -> throw new AssertionError(method);
                    });
        }

        void set(String name, Object value) throws Exception { provider.getClass().getField(name).set(provider, value); }
        Object get(String name) throws Exception { return provider.getClass().getField(name).get(provider); }
        public void close() throws Exception { loader.close(); }
    }
}
