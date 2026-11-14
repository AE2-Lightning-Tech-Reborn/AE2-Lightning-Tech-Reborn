package com.moakiee.ae2lt.compat.mmcr;

import appeng.api.crafting.IPatternDetails;
import appeng.api.networking.IManagedGridNode;
import appeng.api.networking.crafting.ICraftingProvider;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.KeyCounter;
import appeng.helpers.patternprovider.PatternProviderLogic;
import appeng.helpers.patternprovider.PatternProviderLogicHost;
import com.moakiee.ae2lt.logic.provider.ProviderTarget;
import com.moakiee.ae2lt.mixin.PatternProviderLogicAccessor;
import com.moakiee.thunderbolt.api.crafting.batch.BatchJobView;
import com.moakiee.thunderbolt.api.crafting.batch.BatchProviderAdapters;
import com.moakiee.thunderbolt.api.crafting.batch.BatchProviderResolver;
import com.moakiee.thunderbolt.api.crafting.batch.IBatchCraftingProvider;
import com.moakiee.thunderbolt.api.crafting.batch.PreparedBatch;
import com.mojang.logging.LogUtils;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Field;
import java.lang.reflect.Constructor;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.neoforged.fml.ModList;
import org.jetbrains.annotations.Nullable;

/** Optional MMCR 1.21.1 native starts. All recipe admission and material ownership remain in MMCR. */
public final class MmcrBatchIntegration {
    private static final String PREFIX = "cn.howxu.mmcr.";
    private static volatile @Nullable Access access;
    private static volatile boolean registered;

    private MmcrBatchIntegration() {}

    public static void initialize() {
        if (registered || ModList.get() == null || !ModList.get().isLoaded("mmcr")) return;
        try {
            var verified = new Access();
            BatchProviderAdapters.register(ResourceLocation.fromNamespaceAndPath("ae2lt", "mmcr"), 100,
                    new Resolver(verified));
            access = verified;
            registered = true;
        } catch (ReflectiveOperationException | LinkageError unavailable) {
            LogUtils.getLogger().warn("AE2LT disabled MMCR batch integration: unsupported native contract", unavailable);
        }
    }

    /** Classification never reads world state or removes the bound recipe identity. */
    public static boolean isBatchPattern(IPatternDetails pattern) {
        var api = access;
        if (!registered || api == null || !api.pattern.isInstance(pattern)) return false;
        try {
            var original = (IPatternDetails) call(api.original, pattern);
            return original.getClass().getName().equals("appeng.crafting.pattern.AEProcessingPattern")
                    && ((Number) call(api.multiplier, call(api.data, pattern))).longValue() > 0L;
        } catch (RuntimeException unavailable) {
            return false;
        }
    }

    /** Undo MMCR's fixed packet compression only while its dynamic endpoint is registered. */
    public static LinkedHashMap<IPatternDetails, Long> normalizePlan(Map<IPatternDetails, Long> source) {
        var api = access;
        var unchanged = new LinkedHashMap<>(source);
        if (!registered || api == null) return unchanged;
        var result = new LinkedHashMap<IPatternDetails, Long>();
        try {
            for (var entry : source.entrySet()) {
                var pattern = entry.getKey();
                long copies = entry.getValue();
                if (copies <= 0L) return unchanged;
                if (isBatchPattern(pattern)) {
                    long multiplier = ((Number) call(api.multiplier, call(api.data, pattern))).longValue();
                    pattern = (IPatternDetails) call(api.unit, pattern);
                    copies = Math.multiplyExact(copies, multiplier);
                }
                result.merge(pattern, copies, Math::addExact);
            }
            return result;
        } catch (RuntimeException invalid) {
            return unchanged;
        }
    }

    private record Resolver(Access api) implements BatchProviderResolver {
        @Override
        public @Nullable IBatchCraftingProvider resolve(ICraftingProvider provider) {
            try {
                if (!(provider instanceof PatternProviderLogic logic)
                        || !(logic instanceof PatternProviderLogicAccessor accessor)) return null;
                PatternProviderLogicHost host = accessor.getHost();
                if (!(host instanceof BlockEntity block) || !api.host.isInstance(host)
                        || !(block.getLevel() instanceof ServerLevel level)
                        || !Boolean.TRUE.equals(call(api.isBatchHost, null, host))) return null;
                return new Endpoint(api, logic, block, level);
            } catch (RuntimeException | LinkageError incompatible) {
                return null;
            }
        }

        @Override public boolean cacheResolutionAcrossTicks() { return true; }
    }

    private static final class Endpoint implements IBatchCraftingProvider {
        private final Access api;
        private final PatternProviderLogic provider;
        private final BlockEntity host;
        private final ServerLevel level;
        private final ProviderTarget target;
        private final Map<AEItemKey, IPatternDetails> canonical = new LinkedHashMap<>();
        private boolean uncertain;

        private Endpoint(Access api, PatternProviderLogic provider, BlockEntity host, ServerLevel level) {
            this.api = api;
            this.provider = provider;
            this.host = host;
            this.level = level;
            target = new ProviderTarget(level.dimension(), host.getBlockPos(), Direction.UP);
        }

        @Override public List<IPatternDetails> getAvailablePatterns() { return provider.getAvailablePatterns(); }
        @Override public boolean isBusy() { return uncertain || provider.isBusy() || !current(); }

        @Override
        public long getBatchCapacity(IPatternDetails details) {
            if (uncertain || !current() || !isBatchPattern(details) || provider.isBusy()) return 0L;
            // A >1 advisory bound opts in; concrete preparation may deliberately offer just the first proof.
            // Unsafe/nonlinear recipes retain MMCR's ordinary one-pattern path.
            return liveLimit(details);
        }

        @Override
        public @Nullable PreparedBatch prepareBatch(IPatternDetails details, KeyCounter[] prototype,
                long maxCraft, BatchJobView job) {
            if (uncertain || !current() || prototype == null || !isBatchPattern(details)) return unavailable();
            var current = view(details);
            if (!current.idle()) return unavailable();
            long limit = Math.min(Math.min(maxCraft, liveLimit(details)), current.maximum());
            if (limit <= 0L) return unavailable();
            IPatternDetails key = canonical(details);
            int offer = target.startBatchOffer(key, limit);
            KeyCounter[] borrowed = copy(prototype, 1L);
            return new PreparedBatch() {
                private boolean used;
                @Override public long capacity() { return offer; }
                @Override public long push(long copies) {
                    if (used) throw new IllegalStateException("MMCR prepared start already submitted");
                    used = true;
                    if (copies <= 0L) return 0L;
                    if (copies > offer) throw new IllegalArgumentException("MMCR offer exceeds prepared input slice");
                    return dispatch(details, key, borrowed, copies);
                }
            };
        }

        @Override
        public long pushBatch(IPatternDetails details, KeyCounter[] prototype, long maxCraft) {
            // Callers bypassing PreparedBatch still submit exactly one owned slice. They must not
            // mistake the adaptive candidate for ownership of a larger extracted request.
            if (maxCraft <= 0L) return 0L;
            if (uncertain || !current() || !isBatchPattern(details)) return maxCraft;
            View current;
            try { current = view(details); }
            catch (RuntimeException | LinkageError incompatible) { return maxCraft; }
            if (!current.idle()) return maxCraft;
            long limit = Math.min(liveLimit(details), current.maximum());
            if (maxCraft > limit) return maxCraft;
            return dispatch(details, canonical(details), prototype, maxCraft);
        }

        private long dispatch(IPatternDetails details, IPatternDetails key, KeyCounter[] prototype, long copies) {
            if (uncertain || !current() || copies > liveLimit(details)) return copies;
            View before;
            try { before = view(details); }
            catch (RuntimeException | LinkageError incompatible) { return copies; }
            if (!before.idle() || copies > before.maximum()) return copies;
            // Prepared input extraction is bounded to this one packet. Do not silently offer a
            // smaller adaptive chunk after extraction: an uncertain native call owns its whole slice.
            if (copies > target.startBatchOffer(key, copies)) return copies;
            var unavailable = new boolean[1];
            var result = target.pushStartPattern(key, copies,
                    () -> unavailable[0] || !available(details),
                    offered -> {
                        var accepted = push(details, prototype, offered, before);
                        // MMCR's bool does not distinguish an occupied special lane, a beforeStart
                        // condition, or missing auxiliary resources. Only its pure live cap is a
                        // capacity fact; a clean native refusal cannot erase the proven baseline.
                        unavailable[0] = accepted.ownedCopies() <= 0L;
                        return accepted;
                    });
            return copies - result.ownedCopies();
        }

        private ProviderTarget.BatchChunk push(IPatternDetails details, KeyCounter[] prototype,
                int copies, View before) {
            Object data;
            long parallelism;
            IPatternDetails execution;
            KeyCounter[] holders, originals;
            try {
                data = call(api.data, details);
                long absolute = Math.multiplyExact(((Number) call(api.multiplier, data)).longValue(), copies);
                parallelism = Math.multiplyExact(absolute,
                        ((Number) call(api.crafts, data)).longValue());
                execution = (IPatternDetails) call(api.scaled, details, absolute);
                holders = copy(prototype, copies);
                originals = copy(holders, 1L);
            } catch (RuntimeException | LinkageError invalidInputs) {
                // No native call has happened: CPU ownership is intact and the whole slice can return.
                return ProviderTarget.BatchChunk.REJECTED;
            }
            boolean accepted;
            try {
                accepted = provider.pushPattern(execution, holders);
            } catch (RuntimeException failure) {
                throw new IllegalStateException("MMCR native start has uncertain material ownership", failure);
            }
            if (!accepted) {
                if (!same(holders, originals)) {
                    throw new IllegalStateException("MMCR rejected a start after changing input ownership");
                }
                return ProviderTarget.BatchChunk.REJECTED;
            }
            String recipe = call(api.recipe, data).toString();
            boolean complete = view(details).provesStart(before, recipe, parallelism);
            // Native true owns the complete holder packet even if payment paused. Such a packet
            // is settled exactly once, but must never be used as evidence for a larger next batch.
            if (!complete) {
                uncertain = true;
                LogUtils.getLogger().warn("AE2LT paused MMCR batch dispatch at {}: materials were accepted "
                        + "but a complete native start was not confirmed", host.getBlockPos());
            }
            return new ProviderTarget.BatchChunk(copies, complete, false);
        }

        private long liveLimit(IPatternDetails details) {
            try {
                if (!isBatchPattern(details)) return 0L;
                var node = (IManagedGridNode) call(api.node, host);
                if (node.getGrid() == null) return 0L;
                var snapshot = call(api.capture, null, node.getGrid());
                @SuppressWarnings("unchecked")
                var limits = (Map<AEItemKey, ?>) call(api.limits, snapshot);
                var unit = (IPatternDetails) call(api.unit, details);
                var limit = limits.get(unit.getDefinition());
                if (limit == null) return 0L;
                if (!call(api.safety, limit).toString().equals("PROVEN_LINEAR")) return 1L;
                long multiplier = ((Number) call(api.multiplier, call(api.data, details))).longValue();
                return ((Number) call(api.maximum, limit)).longValue() / multiplier;
            } catch (RuntimeException incompatible) {
                return 0L;
            }
        }

        private IPatternDetails canonical(IPatternDetails details) {
            // The identity map in ProviderTarget receives one stable object per bound definition.
            // Recipe bindings are part of this key; no bare original pattern is interned here.
            if (canonical.size() >= 1024 && !canonical.containsKey(details.getDefinition())) {
                canonical.clear();
                target.clearRuntimeState();
            }
            return canonical.computeIfAbsent(details.getDefinition(), ignored -> details);
        }

        private boolean current() {
            return !host.isRemoved() && host.getLevel() == level && level.getServer().isSameThread()
                    && level.isLoaded(host.getBlockPos()) && level.getBlockEntity(host.getBlockPos()) == host;
        }

        private boolean available(IPatternDetails pattern) {
            try { return !uncertain && current() && !provider.isBusy() && view(pattern).idle(); }
            catch (RuntimeException | LinkageError incompatible) { return false; }
        }

        private View view(IPatternDetails details) {
            if (!current() || !isBatchPattern(details)) return View.EMPTY;
            var data = call(api.data, details);
            var recipeId = call(api.recipe, data);
            var poolId = call(api.pool, data);
            var sourceRecipe = call(api.recipeLookup, null, recipeId);
            if (sourceRecipe == null || !poolId.equals(call(api.sourcePool, sourceRecipe))) return View.EMPTY;
            var original = (IPatternDetails) call(api.original, details);
            long crafts = ((Number) call(api.crafts, data)).longValue();
            long perCopy = Math.multiplyExact(crafts, ((Number) call(api.multiplier, data)).longValue());
            var binding = construct(api.binding, poolId, recipeId, crafts);
            @SuppressWarnings("unchecked")
            var positions = (Iterable<BlockPos>) call(api.links, host);
            var states = new LinkedHashMap<String, Lane>();
            var controllers = new java.util.LinkedHashSet<String>();
            boolean idle = false;
            long maximum = 0L;
            int inspected = 0;
            for (var pos : positions) {
                if (++inspected > 256) return View.EMPTY;
                if (!level.isLoaded(pos)) continue;
                var controller = level.getBlockEntity(pos);
                if (!api.controller.isInstance(controller) || controller.isRemoved()
                        || Boolean.TRUE.equals(read(api.loading, controller))
                        || read(api.pendingFactory, controller) != null
                        || read(api.pendingCrafting, controller) != null
                        || read(api.pendingMemory, controller) != null
                        || !poolId.equals(read(api.selectedPool, controller))
                        || !poolId.equals(read(api.validatedPool, controller))) continue;
                var snapshot = call(api.snapshot, controller);
                var structure = call(api.structure, snapshot);
                if (!Boolean.TRUE.equals(call(api.formed, structure))
                        || !Boolean.TRUE.equals(call(api.loaded, structure))) continue;
                @SuppressWarnings("unchecked")
                var ports = (java.util.Set<BlockPos>) call(api.ports, snapshot);
                if (!ports.contains(host.getBlockPos())) continue;
                var modifiers = call(api.modifiers, call(api.components, controller));
                var effective = call(api.resolve, api.resolver, sourceRecipe, snapshot, modifiers);
                if (!Boolean.TRUE.equals(call(api.matches, null, binding, sourceRecipe, effective, original))) continue;
                long controllerMaximum = ((Number) call(api.parallelismLimit, effective)).longValue() / perCopy;
                if (controllerMaximum <= 0L) continue;
                boolean paused = Boolean.TRUE.equals(call(api.paused, controller));
                long version = ((Number) call(api.structureVersion, structure)).longValue();
                String address = pos.asLong() + ":" + version + ":";
                controllers.add(address);
                if (Boolean.TRUE.equals(call(api.factoryPresent, snapshot))) {
                    var factory = call(api.factory, snapshot);
                    paused |= Boolean.TRUE.equals(call(api.factoryPaused, factory));
                    paused |= call(api.factoryFailure, factory) != null;
                    int active = ((Number) call(api.laneCount, factory)).intValue();
                    int limit = ((Number) call(api.laneLimit, factory)).intValue();
                    var lanes = (List<?>) call(api.presentations, factory);
                    int activeForRecipe = 0;
                    for (var lane : lanes) {
                        boolean working = Boolean.TRUE.equals(call(api.laneActive, lane));
                        if (working && call(api.laneRecipe, lane).toString().equals(recipeId.toString())) activeForRecipe++;
                        states.put(address + call(api.laneId, lane), new Lane(address,
                                call(api.laneRecipe, lane).toString(),
                                ((Number) call(api.laneParallelism, lane)).longValue(), working,
                                !paused && call(api.laneFailure, lane) == null));
                    }
                    boolean available = !paused && active < limit
                            && activeForRecipe < ((Number) call(api.recipeThreads, effective)).intValue();
                    idle |= available;
                    if (available) maximum = Math.max(maximum, controllerMaximum);
                } else {
                    var crafting = call(api.crafting, snapshot);
                    var recipe = call(api.craftingRecipe, crafting);
                    boolean working = recipe != null;
                    var status = call(api.craftingStatus, crafting);
                    boolean healthy = !paused && call(api.craftingFailure, crafting) == null
                            && call(api.status, status).toString().equals(working ? "CRAFTING" : "IDLE");
                    boolean available = !working && healthy;
                    idle |= available;
                    if (available) maximum = Math.max(maximum, controllerMaximum);
                    states.put(address + "normal", new Lane(address, recipe == null ? "" : recipe.toString(),
                            ((Number) call(api.craftingParallelism, crafting)).longValue(), working, healthy));
                }
            }
            return new View(idle, maximum, Set.copyOf(controllers), Map.copyOf(states));
        }
    }

    private record Lane(String controller, String recipe, long parallelism, boolean active, boolean healthy) {}
    private record View(boolean idle, long maximum, Set<String> controllers, Map<String, Lane> lanes) {
        static final View EMPTY = new View(false, 0L, Set.of(), Map.of());
        boolean provesStart(View previous, String recipe, long parallelism) {
            for (var entry : lanes.entrySet()) {
                var lane = entry.getValue();
                var old = previous.lanes.get(entry.getKey());
                if (previous.controllers.contains(lane.controller) && (old == null || !old.active)
                        && lane.active && lane.healthy
                        && lane.recipe.equals(recipe) && lane.parallelism == parallelism) return true;
            }
            return false;
        }
    }

    private static PreparedBatch unavailable() {
        return new PreparedBatch() {
            @Override public long capacity() { return 0L; }
            @Override public long push(long copies) { return copies; }
        };
    }

    private static KeyCounter[] copy(KeyCounter[] source, long multiplier) {
        var result = new KeyCounter[source.length];
        for (int i = 0; i < source.length; i++) {
            result[i] = new KeyCounter();
            for (var entry : source[i]) result[i].add(entry.getKey(), Math.multiplyExact(entry.getLongValue(), multiplier));
        }
        return result;
    }

    private static boolean same(KeyCounter[] first, KeyCounter[] second) {
        if (first.length != second.length) return false;
        for (int i = 0; i < first.length; i++) {
            if (first[i].size() != second[i].size()) return false;
            for (var entry : first[i]) if (second[i].get(entry.getKey()) != entry.getLongValue()) return false;
        }
        return true;
    }

    private static Object call(Method method, @Nullable Object target, Object... arguments) {
        try {
            return method.invoke(target, arguments);
        } catch (IllegalAccessException failure) {
            throw new IllegalStateException("MMCR native contract unavailable", failure);
        } catch (InvocationTargetException failure) {
            if (failure.getCause() instanceof RuntimeException runtime) throw runtime;
            throw new IllegalStateException("MMCR native contract failed", failure.getCause());
        }
    }

    private static Object read(Field field, Object target) {
        try {
            return field.get(target);
        } catch (IllegalAccessException unavailable) {
            throw new IllegalStateException("MMCR restoration state unavailable", unavailable);
        }
    }

    private static Object construct(Constructor<?> constructor, Object... arguments) {
        try {
            return constructor.newInstance(arguments);
        } catch (ReflectiveOperationException invalid) {
            throw new IllegalStateException("MMCR recipe binding unavailable", invalid);
        }
    }

    private static Field field(Class<?> type, String name) throws ReflectiveOperationException {
        var field = type.getDeclaredField(name);
        field.setAccessible(true);
        return field;
    }

    private static Class<?> type(String suffix) throws ClassNotFoundException {
        return Class.forName(PREFIX + suffix, false, MmcrBatchIntegration.class.getClassLoader());
    }

    private static final class Access {
        final Class<?> pattern = type("compat.appliedenergistics2.loaded.pattern.PatternBatchDetails");
        final Class<?> host = type("compat.appliedenergistics2.loaded.tile.PatternInterfaceBlockEntity");
        final Class<?> controller = type("internal.tile.MachineControllerBlockEntity");
        final Class<?> planning = type("compat.appliedenergistics2.loaded.PatternBatchPlanning");
        final Method original = pattern.getMethod("original"), data = pattern.getMethod("data");
        final Method unit = pattern.getMethod("unitPattern"), scaled = pattern.getMethod("scaled", long.class);
        final Class<?> dataType = data.getReturnType();
        final Method multiplier = dataType.getMethod("multiplier"), crafts = dataType.getMethod("recipeCraftsPerPattern");
        final Method recipe = dataType.getMethod("recipeId");
        final Method pool = dataType.getMethod("recipePoolId");
        final Class<?> recipeType = type("api.recipe.MachineRecipe");
        final Method recipeLookup = type("api.recipe.RecipeRegistry").getMethod("getRecipe", ResourceLocation.class);
        final Method sourcePool = recipeType.getMethod("recipePoolId");
        final Constructor<?> binding = type("compat.appliedenergistics2.loaded.pattern.PatternBatchBinding")
                .getConstructor(ResourceLocation.class, ResourceLocation.class, long.class);
        final Method node = host.getMethod("getMainNode"), links = host.getMethod("linkedControllerPositions");
        final Method isBatchHost = planning.getMethod("isBatchHost", PatternProviderLogicHost.class);
        final Method capture = planning.getMethod("capture", appeng.api.networking.IGrid.class);
        final Method limits = capture.getReturnType().getMethod("limits");
        final Class<?> limitType = type("compat.appliedenergistics2.loaded.PatternBatchPlanning$BatchLimit");
        final Method safety = limitType.getMethod("safety"), maximum = limitType.getMethod("maxMultiplier");
        // MMCR's public restoration query itself restores pending NBT. Probe the pinned fields
        // instead, before calling a snapshot getter whose ensureLoaded path is then a no-op.
        final Field loading = field(controller, "loadingRuntime");
        final Field pendingFactory = field(controller, "pendingFactoryRuntimeInput");
        final Field pendingCrafting = field(controller, "pendingCraftingRuntimeInput");
        final Field pendingMemory = field(controller, "pendingNormalPatternMemory");
        final Field selectedPool = field(controller, "selectedRecipePoolId");
        final Field validatedPool = field(controller, "validatedRecipePoolId");
        final Method snapshot = controller.getMethod("currentRuntimeSnapshot"), paused = controller.getMethod("isRedstonePaused");
        final Class<?> snapshotType = snapshot.getReturnType();
        final Method components = controller.getMethod("componentRuntime");
        final Method modifiers = components.getReturnType().getMethod("modifierList");
        final Class<?> resolverType = type("api.recipe.EffectiveRecipeResolver");
        final Object resolver;
        final Method resolve = resolverType.getMethod("resolve", recipeType, snapshotType, List.class);
        final Class<?> effectiveType = resolve.getReturnType();
        final Method parallelismLimit = effectiveType.getMethod("parallelismLimit");
        final Method recipeThreads = effectiveType.getMethod("recipeThreadLimit");
        final Method matches = type("compat.appliedenergistics2.loaded.PatternBatchSupport").getMethod("matches",
                binding.getDeclaringClass(), recipeType, effectiveType, IPatternDetails.class);
        final Method structure = snapshotType.getMethod("structure"), ports = snapshotType.getMethod("linkedPortPositions");
        final Method factoryPresent = snapshotType.getMethod("factoryControllerPresent"), factory = snapshotType.getMethod("factory");
        final Method crafting = snapshotType.getMethod("crafting");
        final Class<?> structureType = structure.getReturnType(), factoryType = factory.getReturnType();
        final Method formed = structureType.getMethod("formed"), loaded = structureType.getMethod("structureAreaLoaded");
        final Method structureVersion = structureType.getMethod("version");
        final Method factoryPaused = factoryType.getMethod("paused"), laneCount = factoryType.getMethod("activeLaneCount");
        final Method factoryFailure = factoryType.getMethod("failure");
        final Method laneLimit = factoryType.getMethod("laneLimit"), presentations = factoryType.getMethod("presentationLanes");
        final Class<?> laneType = type("internal.runtime.FactoryRuntime$ThreadSnapshot");
        final Method laneId = laneType.getMethod("laneId"), laneRecipe = laneType.getMethod("recipeId");
        final Method laneParallelism = laneType.getMethod("parallelism"), laneActive = laneType.getMethod("active");
        final Method laneFailure = laneType.getMethod("failure");
        final Class<?> craftingType = crafting.getReturnType();
        final Method craftingRecipe = craftingType.getMethod("recipeId"), craftingStatus = craftingType.getMethod("status");
        final Method craftingFailure = craftingType.getMethod("failure"), craftingParallelism = craftingType.getMethod("parallelism");
        final Method status = craftingStatus.getReturnType().getMethod("getStatus");

        Access() throws ReflectiveOperationException {
            resolver = resolverType.getConstructor().newInstance();
        }
    }
}
