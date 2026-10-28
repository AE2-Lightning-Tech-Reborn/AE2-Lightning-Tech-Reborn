package com.moakiee.ae2lt.compat.neoeco;

import static org.junit.jupiter.api.Assertions.*;

import appeng.api.networking.crafting.ICraftingProvider;
import appeng.api.stacks.KeyCounter;
import com.moakiee.ae2lt.me.key.LightningKey;
import com.moakiee.thunderbolt.api.crafting.batch.BatchJobView;
import com.moakiee.thunderbolt.api.crafting.batch.BatchProviderResolver;
import com.moakiee.thunderbolt.api.crafting.batch.IBatchCraftingProvider;
import com.moakiee.thunderbolt.compat.neoeco.NeoEcoFastPathBatchAdapter;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.lang.reflect.Proxy;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import javax.tools.ToolProvider;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.junit.jupiter.api.io.TempDir;

/** Exercises the actual Core adapter against test-owned, isolated public protocol contracts. */
class NeoEcoAllocatedApiTest {
    private static final String CORE = "com.moakiee.thunderbolt.compat.neoeco.";
    private static final String ECO = "cn.dancingsnow.neoecoae.";
    @TempDir static Path temporary;
    private static final Map<String, Path> contracts = new LinkedHashMap<>();

    @BeforeAll
    static void bootstrapAndCompileContracts() throws Exception {
        com.moakiee.ae2lt.testing.MinecraftTestBootstrap.bootstrap();
        var classpath = new LinkedHashSet<>(Arrays.asList(
                System.getProperty("java.class.path").split(File.pathSeparator)));
        for (ClassLoader loader = NeoEcoAllocatedApiTest.class.getClassLoader(); loader != null; loader = loader.getParent()) {
            if (loader instanceof URLClassLoader urls) for (URL url : urls.getURLs())
                if (url.getProtocol().equals("file")) classpath.add(Path.of(url.toURI()).toString());
        }
        var resource = NeoEcoAllocatedApiTest.class.getResource("/neoeco-allocated-contract");
        assertNotNull(resource);
        var sources = Path.of(resource.toURI());
        for (var variant : List.of("valid", "wrong_count", "instance_factory", "extra_callback", "wrong_reserve")) {
            var sourceDirectory = Files.createDirectories(temporary.resolve(variant).resolve("sources"));
            var outputDirectory = Files.createDirectories(temporary.resolve(variant).resolve("classes"));
            var arguments = new ArrayList<>(List.of("-encoding", "UTF-8", "-proc:none", "--release", "17",
                    "-classpath", String.join(File.pathSeparator, classpath), "-d", outputDirectory.toString()));
            try (var files = Files.walk(sources)) {
                for (var source : files.filter(path -> path.toString().endsWith(".java")).toList()) {
                    var text = Files.readString(source);
                    text = switch (variant) {
                        case "wrong_count" -> text.replace("public long craftCount() { return provider.count; }",
                                "public int craftCount() { return (int) provider.count; }");
                        case "instance_factory" -> text.replace("public static Batch prepareAllocated", "public Batch prepareAllocated");
                        case "extra_callback" -> text.replace("Reservation reserve(double amount);",
                                "Reservation reserve(double amount); void missingCallback();");
                        case "wrong_reserve" -> text.replace("Reservation reserve(double amount);", "boolean reserve(double amount);");
                        default -> text;
                    };
                    var destination = sourceDirectory.resolve(sources.relativize(source));
                    Files.createDirectories(destination.getParent());
                    Files.writeString(destination, text);
                    arguments.add(destination.toString());
                }
            }
            var diagnostics = new ByteArrayOutputStream();
            assertEquals(0, ToolProvider.getSystemJavaCompiler().run(null, diagnostics, diagnostics,
                    arguments.toArray(String[]::new)), () -> diagnostics.toString(StandardCharsets.UTF_8));
            contracts.put(variant, outputDirectory);
        }
    }

    @Test
    void rejectsMalformedPublicContractsBeforeUse() throws Exception {
        for (var variant : List.of("wrong_count", "instance_factory", "extra_callback", "wrong_reserve")) {
            try (var fixture = new Fixture(variant)) {
                assertNull(fixture.loadApi(), variant);
                assertNull(fixture.resolver.resolve(fixture.provider), variant);
                assertEquals(0, fixture.get("preparations"));
                assertEquals(0, fixture.get("submissions"));
            }
        }
    }

    @Test
    void deniedAndMissingApiDiscoveryAreSafe() throws Exception {
        var denied = new ClassLoader(getClass().getClassLoader()) {
            @Override protected Class<?> loadClass(String name, boolean resolve) throws ClassNotFoundException {
                if (name.startsWith(ECO)) throw new SecurityException("denied");
                return super.loadClass(name, resolve);
            }
        };
        assertNull(assertDoesNotThrow(() -> loadApi(denied)));
        var provider = (ICraftingProvider) Proxy.newProxyInstance(denied, new Class<?>[]{ICraftingProvider.class},
                (proxy, method, args) -> switch (method.getName()) {
                    case "getAvailablePatterns" -> List.of();
                    case "isBusy" -> false;
                    case "hashCode" -> System.identityHashCode(proxy);
                    case "equals" -> proxy == args[0];
                    default -> throw new AssertionError("Discovery must not transfer ownership: " + method);
                });
        assertNull(assertDoesNotThrow(() -> new NeoEcoFastPathBatchAdapter().resolve(provider)));
        assertNull(loadApi(getClass().getClassLoader()));
    }

    @Test
    @EnabledIfSystemProperty(named = "ae2lt.apiAudit.enabled", matches = "true")
    void actualForgeReleaseHasNoAllocatedBridge() throws Exception {
        var jar = Path.of(System.getProperty("ae2lt.apiAudit.neoeco"));
        try (var loader = new URLClassLoader(new URL[]{jar.toUri().toURL()}, getClass().getClassLoader())) {
            assertNotNull(loader.getResource("cn/dancingsnow/neoecoae/api/me/ECOCraftingCPULogic.class"));
            assertNull(loadApi(loader));
        }
    }

    @Test
    void invalidCountsCannotReachSubmitOrCorruptRemainder() throws Exception {
        for (long count : new long[]{Long.MIN_VALUE, -1, 0, 11, Long.MAX_VALUE}) {
            try (var fixture = new Fixture("valid")) {
                fixture.set("count", count);
                var input = inputs();
                assertThrows(IllegalArgumentException.class,
                        () -> fixture.endpoint().prepareBatch(null, input, 10, job()));
                assertEquals(0, fixture.get("submissions"));
                assertEquals(0, fixture.get("ordinary"));
                assertEquals(2, input[0].get(LightningKey.HIGH_VOLTAGE));
            }
        }
    }

    @Test
    void partialCommitPreservesTemplateAndInvokesDefaultCallbacks() throws Exception {
        try (var fixture = new Fixture("valid")) {
            var input = inputs();
            var job = job();
            assertEquals(7, fixture.endpoint().pushBatch(null, input, 10, job));
            assertEquals(1, fixture.get("submissions"));
            assertEquals(0, fixture.get("ordinary"));
            assertEquals(job.craftingId(), fixture.get("jobId"));
            assertEquals(2, input[0].get(LightningKey.HIGH_VOLTAGE));
        }
    }

    @Test
    void uncertainSubmitNeverRetriesAndRetainsAllocatedAccounting() throws Exception {
        try (var fixture = new Fixture("valid")) {
            fixture.set("fail", true);
            var input = inputs();
            var batch = fixture.endpoint().prepareBatch(null, input, 10, job());
            assertEquals(3, batch.capacity());
            var failure = assertThrows(RuntimeException.class, () -> batch.push(3));
            assertTrue(failure.getClass().getSimpleName().contains("Indeterminate"));
            assertThrows(IllegalStateException.class, () -> batch.push(3));
            assertEquals(1, fixture.get("submissions"));
            assertEquals(0, fixture.get("ordinary"));
            assertEquals(2, input[0].get(LightningKey.HIGH_VOLTAGE));
        }
    }

    @Test
    void rejectedBatchRetriesOneFreshCopyAndResetsCapacityNextTick() throws Exception {
        try (var fixture = new Fixture("valid")) {
            fixture.set("accept", false);
            var endpoint = fixture.endpoint();
            var input = inputs();
            endpoint.beginDispatchTick(10);
            assertEquals(9, endpoint.pushBatch(null, input, 10, job()));
            assertEquals(1, fixture.get("submissions"));
            assertEquals(1, fixture.get("ordinary"));
            assertEquals(2, input[0].get(LightningKey.HIGH_VOLTAGE));
            assertEquals(1, endpoint.getBatchCapacity(null));
            endpoint.beginDispatchTick(11);
            assertEquals(Long.MAX_VALUE, endpoint.getBatchCapacity(null));
        }
    }

    @Test
    void noJobDoesNotAttemptPreparationOrOrdinaryOwnershipTransfer() throws Exception {
        try (var fixture = new Fixture("valid")) {
            var endpoint = fixture.endpoint();
            assertEquals(10, endpoint.pushBatch(null, inputs(), 10, null));
            assertEquals(10, endpoint.pushBatch(null, inputs(), 10));
            assertEquals(0, fixture.get("preparations"));
            assertEquals(0, fixture.get("ordinary"));
        }
    }

    private static Object loadApi(ClassLoader loader) throws Exception {
        var load = Class.forName(CORE + "NeoEcoAllocatedApi").getDeclaredMethod("load", ClassLoader.class);
        load.setAccessible(true);
        return load.invoke(null, loader);
    }

    private static KeyCounter[] inputs() {
        var input = new KeyCounter();
        input.add(LightningKey.HIGH_VOLTAGE, 2);
        return new KeyCounter[]{input};
    }

    private static BatchJobView job() {
        var id = UUID.randomUUID();
        return (BatchJobView) Proxy.newProxyInstance(BatchJobView.class.getClassLoader(), new Class<?>[]{BatchJobView.class},
                (proxy, method, args) -> switch (method.getName()) {
                    case "level" -> null;
                    case "craftingId" -> id;
                    default -> throw new AssertionError(method);
                });
    }

    private static final class Fixture implements AutoCloseable {
        final URLClassLoader loader;
        final ICraftingProvider provider;
        final BatchProviderResolver resolver;

        Fixture(String variant) throws Exception {
            loader = new URLClassLoader(new URL[]{contracts.get(variant).toUri().toURL(),
                    NeoEcoFastPathBatchAdapter.class.getProtectionDomain().getCodeSource().getLocation()},
                    NeoEcoAllocatedApiTest.class.getClassLoader()) {
                @Override protected Class<?> loadClass(String name, boolean resolve) throws ClassNotFoundException {
                    synchronized (getClassLoadingLock(name)) {
                        if (name.startsWith(CORE) || name.startsWith(ECO)) {
                            var type = findLoadedClass(name);
                            if (type == null) type = findClass(name);
                            if (resolve) resolveClass(type);
                            return type;
                        }
                        return super.loadClass(name, resolve);
                    }
                }
            };
            provider = (ICraftingProvider) loader.loadClass(ECO + "api.me.ECOFastPathFacade$Provider")
                    .getConstructor().newInstance();
            set("key", LightningKey.HIGH_VOLTAGE);
            resolver = (BatchProviderResolver) loader.loadClass(CORE + "NeoEcoFastPathBatchAdapter")
                    .getConstructor().newInstance();
        }

        Object loadApi() throws Exception {
            var load = loader.loadClass(CORE + "NeoEcoAllocatedApi").getDeclaredMethod("load", ClassLoader.class);
            load.setAccessible(true);
            return load.invoke(null, loader);
        }

        IBatchCraftingProvider endpoint() { return java.util.Objects.requireNonNull(resolver.resolve(provider)); }
        Object get(String field) throws Exception { return provider.getClass().getField(field).get(provider); }
        void set(String field, Object value) throws Exception { provider.getClass().getField(field).set(provider, value); }
        @Override public void close() throws Exception { loader.close(); }
    }
}
