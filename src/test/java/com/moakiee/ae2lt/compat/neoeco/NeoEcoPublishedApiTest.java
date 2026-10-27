package com.moakiee.ae2lt.compat.neoeco;

import static org.junit.jupiter.api.Assertions.*;
import java.lang.reflect.InvocationTargetException;
import java.math.BigInteger;
import java.net.URLClassLoader;
import java.nio.file.Path;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BooleanSupplier;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

class NeoEcoPublishedApiTest {
    @ParameterizedTest(name = "{0}")
    @MethodSource("com.moakiee.ae2lt.compat.neoeco.NeoEcoPublishedFixtures#releases")
    void publishedMigrationLeasePreventsTwoOwnersFromMutatingOneGrid(NeoEcoPublishedFixtures.Release release) throws Exception {
        Path jar = release.requireJar();
        try (var loader = new URLClassLoader(new java.net.URL[]{jar.toUri().toURL()}, getClass().getClassLoader())) {
            var api = new NeoEcoCraftingCompat.Api(loader);
            var lease = api.migrationForGrid.invoke(null, new Object());
            var first = new Object(); var second = new Object();
            assertEquals(true, api.acquireMigration.invoke(lease, first));
            assertEquals(false, api.acquireMigration.invoke(lease, second));
            api.releaseMigration.invoke(lease, second);
            assertEquals(true, api.ownsMigration.invoke(lease, first));
            api.releaseMigration.invoke(lease, first);
            assertEquals(true, api.acquireMigration.invoke(lease, second));
        }
    }
    @ParameterizedTest(name = "{0}")
    @MethodSource("com.moakiee.ae2lt.compat.neoeco.NeoEcoPublishedFixtures#releases")
    void publishedGtoFacadeKeepsExactCountsAndAllowsOnlyOneSubmission(NeoEcoPublishedFixtures.Release release) throws Exception {
        Path jar = release.requireJar();
        try (var loader = new URLClassLoader(new java.net.URL[]{jar.toUri().toURL()}, getClass().getClassLoader())) {
            var api = new NeoEcoCraftingCompat.Api(loader);
            var preparation = loader.loadClass(
                    "cn.dancingsnow.neoecoae.api.me.provider.ECOFastPathDispatchProvider$ExactPreparation");
            var calls = new AtomicInteger();
            var count = BigInteger.ONE.shiftLeft(100).add(BigInteger.valueOf(37));
            var admission = preparation.getConstructor(BigInteger.class, BooleanSupplier.class)
                    .newInstance(count, (BooleanSupplier) () -> { calls.incrementAndGet(); return true; });
            var constructor = api.exactCount.getDeclaringClass().getDeclaredConstructor(preparation);
            constructor.setAccessible(true);
            var batch = constructor.newInstance(admission);
            assertEquals(count, api.exactCount.invoke(batch));
            assertEquals(true, api.exactSubmit.invoke(batch));
            var failure = assertThrows(InvocationTargetException.class, () -> api.exactSubmit.invoke(batch));
            assertInstanceOf(IllegalStateException.class, failure.getCause());
            assertEquals(1, calls.get());
        }
    }
}
