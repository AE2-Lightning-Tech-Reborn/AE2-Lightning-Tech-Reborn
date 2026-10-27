package com.moakiee.ae2lt.compat.neoeco;

import appeng.api.crafting.IPatternDetails;
import appeng.api.networking.crafting.ICraftingProvider;
import appeng.api.stacks.KeyCounter;
import com.moakiee.thunderbolt.api.crafting.batch.*;
import java.lang.reflect.*;
import java.math.BigInteger;
import java.util.UUID;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;
import net.minecraftforge.fml.ModList;

/** Optional integration with the published GTO facade. No NeoECO implementation is bundled. */
public final class NeoEcoCraftingCompat {
    private static Api api;
    private NeoEcoCraftingCompat() {}

    public static void initialize() {
        if (ModList.get() == null || !ModList.get().isLoaded("neoecoae")) return;
        try {
            api = new Api(NeoEcoCraftingCompat.class.getClassLoader());
        } catch (ReflectiveOperationException failure) {
            throw new IllegalStateException("Unsupported NeoECO GTO facade", failure);
        }
        BatchProviderAdapters.register(new ResourceLocation("ae2lt", "neoeco_gto"), -100,
                (provider, pattern, job) -> api.provider.isInstance(provider) ? new Adapter(provider) : null);
    }

    public static boolean supports(ICraftingProvider provider) {
        return api != null && api.provider.isInstance(provider);
    }

    public static boolean isIndeterminate(Throwable failure) {
        return failure.getClass().getName().equals(
                "cn.dancingsnow.neoecoae.api.me.provider.ECOIndeterminateBatchException");
    }

    public static boolean pushForJob(ICraftingProvider provider, IPatternDetails pattern,
                                     KeyCounter[] inputs, UUID jobId) {
        return api == null ? provider.pushPattern(pattern, inputs)
                : (boolean) invoke(api.push, null, provider, pattern, inputs, jobId);
    }

    public static void cancel(Level level, UUID jobId) {
        if (api != null && level != null) invoke(api.cancel, null, level, jobId);
    }

    public static void complete(Level level, UUID jobId) {
        if (api != null && level != null) invoke(api.finish, null, level, jobId, true);
    }

    public static boolean acquireMigration(Object grid, Object owner) {
        return api == null || (boolean) invoke(api.acquireMigration,
                invoke(api.migrationForGrid, null, grid), owner);
    }

    public static boolean ownsMigration(Object grid, Object owner) {
        return api == null || (boolean) invoke(api.ownsMigration,
                invoke(api.migrationForGrid, null, grid), owner);
    }

    public static void releaseMigration(Object grid, Object owner) {
        if (api != null) invoke(api.releaseMigration, invoke(api.migrationForGrid, null, grid), owner);
    }

    public static ExactBatch prepareExact(ICraftingProvider provider, IPatternDetails pattern,
            KeyCounter[] inputs, BigInteger requested, Level level, UUID jobId) {
        if (!supports(provider)) return null;
        Object batch = invoke(api.exact, null, provider, pattern, inputs, requested, level, jobId);
        if (batch == null) return null;
        var count = (BigInteger) invoke(api.exactCount, batch);
        if (count.signum() <= 0 || count.compareTo(requested) > 0)
            throw new IllegalStateException("NeoECO exceeded exact admission");
        return new ExactBatch(count, batch);
    }

    public record ExactBatch(BigInteger copies, Object batch) {
        public boolean submit() { return (boolean) invoke(api.exactSubmit, batch); }
    }

    private record Adapter(ICraftingProvider provider) implements IBatchCraftingProvider {
        public java.util.List<IPatternDetails> getAvailablePatterns() { return provider.getAvailablePatterns(); }
        public boolean isBusy() { return provider.isBusy(); }
        public long getBatchCapacity(IPatternDetails pattern) { return isBusy() ? 0 : Long.MAX_VALUE; }
        public long pushBatch(IPatternDetails pattern, KeyCounter[] inputs, long copies) {
            throw new IllegalStateException("NeoECO dispatch requires the owning job");
        }
        public PreparedBatch prepareBatch(IPatternDetails pattern, KeyCounter[] inputs,
                                           long copies, BatchJobView job) {
            return prepare(pattern, inputs, copies, job);
        }
        private PreparedBatch prepare(IPatternDetails pattern, KeyCounter[] inputs,
                                       long copies, BatchJobView job) {
            Object batch = invoke(api.prepare, null, provider, pattern, inputs, copies,
                    job.level(), job.craftingId());
            if (batch == null) return new PreparedBatch() {
                public long capacity() { return 0; }
                public long push(long offered) { return offered; }
            };
            long count = (long) invoke(api.count, batch);
            if (count <= 0 || count > copies) throw new IllegalStateException("Invalid NeoECO batch capacity");
            return new PreparedBatch() {
                private boolean submitted;
                public long capacity() { return count; }
                public long push(long offered) {
                    if (submitted) throw new IllegalStateException("Batch already submitted");
                    submitted = true;
                    if (offered < count) {
                        var smaller = prepare(pattern, inputs, offered, job);
                        return smaller.push(offered);
                    }
                    try {
                        return (boolean) invoke(api.submit, batch, api.energy) ? offered - count : offered;
                    } catch (RuntimeException failure) {
                        if (isIndeterminate(failure)) throw failure;
                        // The facade guarantees rollback for an ordinary provider rejection/exception.
                        return offered;
                    }
                }
            };
        }
    }

    static final class Api {
        final Class<?> provider;
        final Method push, cancel, finish, prepare, count, submit, exact, exactCount, exactSubmit;
        final Method migrationForGrid, acquireMigration, ownsMigration, releaseMigration;
        final Object energy;
        Api(ClassLoader loader) throws ReflectiveOperationException {
            String prefix = "cn.dancingsnow.neoecoae.api.me.";
            provider = loader.loadClass(prefix + "provider.ECOFastPathDispatchProvider");
            Class<?> migration = loader.loadClass("cn.dancingsnow.neoecoae.grid.PatternMigrationCoordinator");
            migrationForGrid = migration.getMethod("forGrid", Object.class);
            acquireMigration = migration.getMethod("tryAcquire", Object.class);
            ownsMigration = migration.getMethod("isOwner", Object.class);
            releaseMigration = migration.getMethod("release", Object.class);
            Class<?> facade = loader.loadClass(prefix + "ECOFastPathFacade");
            Class<?> batch = loader.loadClass(prefix + "ECOFastPathFacade$PreparedBatch");
            Class<?> exactBatch = loader.loadClass(prefix + "ECOFastPathFacade$PreparedExactBatch");
            Class<?> account = loader.loadClass(prefix + "ECOFastPathFacade$EnergyAccount");
            Class<?> reservation = loader.loadClass(prefix + "ECOFastPathFacade$Reservation");
            push = facade.getMethod("pushPatternForJob", ICraftingProvider.class, IPatternDetails.class,
                    KeyCounter[].class, UUID.class);
            cancel = facade.getMethod("cancelJob", Level.class, UUID.class);
            finish = loader.loadClass("cn.dancingsnow.neoecoae.crafting.execution.worker.ECOCraftingJobLifecycle")
                    .getMethod("finish", Level.class, UUID.class, boolean.class);
            prepare = facade.getMethod("prepareAllocated", ICraftingProvider.class, IPatternDetails.class,
                    KeyCounter[].class, long.class, Level.class, UUID.class);
            count = batch.getMethod("craftCount");
            submit = batch.getMethod("submit", account);
            exact = facade.getMethod("prepareExactAllocated", ICraftingProvider.class, IPatternDetails.class,
                    KeyCounter[].class, BigInteger.class, Level.class, UUID.class);
            exactCount = exactBatch.getMethod("craftCount");
            exactSubmit = exactBatch.getMethod("submit");
            Object reserved = Proxy.newProxyInstance(loader, new Class<?>[]{reservation},
                    (p, m, args) -> objectMethod(p, m, args));
            energy = Proxy.newProxyInstance(loader, new Class<?>[]{account},
                    (p, m, args) -> m.getName().equals("reserve") ? reserved : objectMethod(p, m, args));
        }
    }

    private static Object objectMethod(Object proxy, Method method, Object[] args) {
        return switch (method.getName()) {
            case "toString" -> "AE2LT CPU-owned energy reservation";
            case "hashCode" -> System.identityHashCode(proxy);
            case "equals" -> proxy == args[0];
            default -> null; // commit/refund: the owning CPU retains the energy transaction.
        };
    }

    private static Object invoke(Method method, Object receiver, Object... args) {
        try { return method.invoke(receiver, args); }
        catch (InvocationTargetException failure) {
            if (failure.getCause() instanceof RuntimeException runtime) throw runtime;
            if (failure.getCause() instanceof Error error) throw error;
            throw new IllegalStateException("NeoECO dispatch failed", failure.getCause());
        } catch (ReflectiveOperationException failure) {
            throw new IllegalStateException("Cannot invoke NeoECO facade", failure);
        }
    }
}
