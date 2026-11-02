package com.moakiee.ae2lt.gametest;

import java.lang.management.ManagementFactory;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.Executors;
import com.google.gson.GsonBuilder;
import com.moakiee.ae2lt.performance.PlainKeyCacheOwner;
import com.moakiee.ae2lt.performance.PlainKeyReuse;
import com.moakiee.thunderbolt.config.ThunderboltCommonConfig;
import com.moakiee.thunderbolt.core.nbt.NbtCopyOptions;
import com.moakiee.thunderbolt.core.nbt.NbtCopyViews;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.AEFluidKey;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.IntTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.ItemLike;
import net.minecraft.world.level.material.Fluids;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

/** Real transformed-runtime semantics and opt-in allocation/time microbenchmarks. Not shipped. */
@GameTestHolder("ae2lt_io")
@PrefixGameTestTemplate(false)
@net.minecraftforge.fml.common.Mod.EventBusSubscriber(modid = "ae2lt",
        bus = net.minecraftforge.fml.common.Mod.EventBusSubscriber.Bus.MOD)
public final class ObjectReuseGameTests {
    private static volatile Object sink;
    private static int assertions;
    private static Item capabilityProbe;
    private static Item dynamicProbe;

    private ObjectReuseGameTests() {}

    @net.minecraftforge.eventbus.api.SubscribeEvent
    public static void registerProbe(net.minecraftforge.registries.RegisterEvent event) {
        event.register(net.minecraft.core.registries.Registries.ITEM, registry -> {
            capabilityProbe = new Item(new Item.Properties()) {
                @Override public boolean shouldOverrideMultiplayerNbt() { return true; }
                @Override public CompoundTag getShareTag(ItemStack stack) { return stack.serializeNBT(); }
                @Override public void readShareTag(ItemStack stack, CompoundTag tag) {
                    if (tag != null) stack.deserializeNBT(tag);
                }
                @Override
                public net.minecraftforge.common.capabilities.ICapabilityProvider initCapabilities(
                        ItemStack stack, CompoundTag nbt) {
                    return new net.minecraftforge.common.capabilities.ICapabilitySerializable<CompoundTag>() {
                        private int value = 42;
                        @Override public <T> net.minecraftforge.common.util.LazyOptional<T> getCapability(
                                net.minecraftforge.common.capabilities.Capability<T> capability,
                                net.minecraft.core.Direction side) {
                            return net.minecraftforge.common.util.LazyOptional.empty();
                        }
                        @Override public CompoundTag serializeNBT() {
                            var result = new CompoundTag();
                            result.putInt("value", value);
                            return result;
                        }
                        @Override public void deserializeNBT(CompoundTag tag) { value = tag.getInt("value"); }
                    };
                }
            };
            registry.register(new net.minecraft.resources.ResourceLocation("ae2lt", "object_reuse_capability_probe"),
                    capabilityProbe);
            dynamicProbe = new DynamicLimitItem();
            registry.register(new net.minecraft.resources.ResourceLocation("ae2lt", "object_reuse_dynamic_probe"),
                    dynamicProbe);
        });
    }

    public static final class DynamicLimitItem extends Item {
        public DynamicLimitItem() { super(new Item.Properties()); }
        @Override public int getMaxStackSize(ItemStack stack) { return stack.getCount(); }
    }

    @GameTest(template = "wireless_io_empty", batch = "aa_object_reuse", timeoutTicks = 1200)
    public static void semanticsAndBenchmarks(GameTestHelper helper) throws Exception {
        assertions = 0;
        var report = new LinkedHashMap<String, Object>();
        report.put("plainKeys", false);
        report.put("nbtCopyOwner", "thunderbolt");
        report.put("fastNbtCopies", NbtCopyOptions.enabled());
        report.put("nbtModesValidated", List.of(false, true));
        report.put("java", System.getProperty("java.version"));
        validate();
        report.put("assertions", assertions);
        if (Boolean.getBoolean("ae2lt.objectReuse.benchmark")) report.put("benchmarks", benchmark());
        Path target = Path.of(System.getProperty("ae2lt.objectReuse.report", "object-reuse-result.json"));
        Files.createDirectories(target.toAbsolutePath().getParent());
        Files.writeString(target, new GsonBuilder().setPrettyPrinting().create().toJson(report));
        helper.succeed();
    }

    private static void validate() throws Exception {
        check(java.util.Arrays.stream(CompoundTag.class.getDeclaredMethods())
                .anyMatch(m -> m.getName().contains("thunderbolt$fuseTransform")),
                "compound copy mixin actually applied");
        check(java.util.Arrays.stream(ListTag.class.getDeclaredMethods())
                .anyMatch(m -> m.getName().contains("thunderbolt$fuseTransform")),
                "list copy mixin actually applied");
        check(NbtCopyOptions.enabled() == ThunderboltCommonConfig.fastNbtCopies(),
                "loaded Core config published to copy implementation");
        // Neither release registers the old experimental plain-key mixins. Keep AE2's
        // value semantics and independently verify that those holders remain absent.
        check(!(Items.STONE instanceof PlainKeyCacheOwner), "experimental item cache absent");
        check(!(Fluids.WATER instanceof PlainKeyCacheOwner), "experimental fluid cache absent");
        check(AEItemKey.of(ItemStack.EMPTY) == null, "empty item factory");
        check(AEFluidKey.of(FluidStack.EMPTY) == null, "empty fluid factory");
        var original = new ItemStack(Items.STONE, 64);
        var one = AEItemKey.of(Items.STONE);
        var many = AEItemKey.of(original);
        check(one.equals(many) && one.hashCode() == many.hashCode(), "plain item value identity");
        check(one != many, "plain items preserve AE2 factory ownership");
        check(original.getCount() == 64 && one.toStack(17).getCount() == 17, "stack counts preserved");
        check(one.getReadOnlyStack().getCount() == 1, "AE2 15 read-only stack semantics");
        PlainKeyReuse.clear();
        var itemLikeCalls = new java.util.concurrent.atomic.AtomicInteger();
        ItemLike countedItem = () -> { itemLikeCalls.incrementAndGet(); return Items.STONE; };
        AEItemKey.of(countedItem);
        AEItemKey.of(countedItem);
        check(itemLikeCalls.get() == 2, "ItemLike evaluated once per call including cold misses");
        check(!PlainKeyReuse.hasStaticStackLimit(DynamicLimitItem.class), "dynamic stack limit excluded");
        var dynamic16 = AEItemKey.of(new ItemStack(dynamicProbe, 16));
        var dynamic32 = AEItemKey.of(new ItemStack(dynamicProbe, 32));
        check(dynamic16 != dynamic32 && dynamic16.getMaxStackSize() == 16 && dynamic32.getMaxStackSize() == 32,
                "dynamic stack limits retain distinct metadata");
        var water = AEFluidKey.of(Fluids.WATER);
        check(water != AEFluidKey.of(new FluidStack(Fluids.WATER, 1000)),
                "plain fluids preserve AE2 factory ownership");
        check(!water.equals(AEFluidKey.of(Fluids.WATER, new CompoundTag())), "null/empty fluid NBT distinct");
        check(water.equals(AEFluidKey.fromTag(water.toTag())), "fluid persistence");

        int items = 0;
        for (var item : BuiltInRegistries.ITEM) {
            if (item == Items.AIR) continue;
            var stack = item.getDefaultInstance();
            if (stack.isEmpty()) continue;
            var key = AEItemKey.of(stack);
            check(key.matches(stack), "default item matches: " + BuiltInRegistries.ITEM.getKey(item));
            check(key.equals(AEItemKey.fromTag(key.toTag())), "item persistence");
            var packet = new net.minecraft.network.FriendlyByteBuf(io.netty.buffer.Unpooled.buffer());
            try {
                key.writeToPacket(packet);
                check(key.equals(AEItemKey.fromPacket(packet)), "item network roundtrip");
            } finally {
                packet.release();
            }
            check(key.getMaxStackSize() == stack.getMaxStackSize(), "item stack limit");
            var tagged = stack.copy();
            tagged.getOrCreateTag().putString("ae2lt-reuse-test", "separate");
            var taggedKey = AEItemKey.of(tagged);
            check(!key.equals(taggedKey) && taggedKey.matches(tagged), "tag identity");
            check(taggedKey != AEItemKey.of(tagged), "tagged keys bypass cache");
            items++;
        }
        check(items > 1000, "registered item coverage");

        // Directly exercise AE2's capability-aware factory with explicit serialized capability data.
        var factory = AEItemKey.class.getDeclaredMethod("of", ItemLike.class, CompoundTag.class, CompoundTag.class);
        factory.setAccessible(true);
        var caps = new CompoundTag();
        caps.putInt("ae2lt_test_capability", 42);
        var capKey = (AEItemKey) factory.invoke(null, Items.STONE, null, caps);
        var otherCapKey = (AEItemKey) factory.invoke(null, Items.STONE, null, caps);
        check(capKey != otherCapKey && capKey.equals(otherCapKey) && !capKey.equals(one), "capability bypass");
        check(capKey.equals(AEItemKey.fromTag(capKey.toTag())), "capability persistence");
        check(capabilityProbe != null, "registered capability probe");
        var capStack = new ItemStack(capabilityProbe, 17);
        // Forge's serializeCaps intentionally returns lazyData before first capability access.
        capStack.getCapability(net.minecraftforge.common.capabilities.ForgeCapabilities.ITEM_HANDLER).resolve();
        var realCapKey = AEItemKey.of(capStack);
        check(realCapKey != AEItemKey.of(capStack), "real Forge capabilities bypass cache");
        var savedCapStack = capStack.save(new CompoundTag());
        check(!savedCapStack.getCompound("ForgeCaps").isEmpty(), "real serialized capabilities present");
        var changedCapStack = savedCapStack.copy();
        changedCapStack.getCompound("ForgeCaps").getCompound("Parent").putInt("value", 99);
        var changedCapKey = AEItemKey.of(ItemStack.of(changedCapStack));
        check(!realCapKey.equals(changedCapKey), "capability values distinguish keys");
        check(changedCapKey.equals(AEItemKey.fromTag(changedCapKey.toTag())), "changed capability persistence");
        var capPacket = new net.minecraft.network.FriendlyByteBuf(io.netty.buffer.Unpooled.buffer());
        try {
            realCapKey.writeToPacket(capPacket);
            check(realCapKey.equals(AEItemKey.fromPacket(capPacket)), "real capability network roundtrip");
            capPacket.clear();
            changedCapKey.writeToPacket(capPacket);
            check(changedCapKey.equals(AEItemKey.fromPacket(capPacket)), "changed capability network roundtrip");
        } finally {
            capPacket.release();
        }
        check(!one.equals(AEItemKey.of(Items.STONE, new CompoundTag())), "null/empty item NBT distinct");
        for (var fluid : BuiltInRegistries.FLUID) {
            var plain = AEFluidKey.of(fluid);
            check(plain.equals(AEFluidKey.fromTag(plain.toTag())), "fluid persistence");
            var packet = new net.minecraft.network.FriendlyByteBuf(io.netty.buffer.Unpooled.buffer());
            try {
                plain.writeToPacket(packet);
                check(plain.equals(AEFluidKey.fromPacket(packet)), "fluid network roundtrip");
            } finally {
                packet.release();
            }
            var data = new CompoundTag();
            data.putString("identity", "separate");
            var tagged = AEFluidKey.of(fluid, data);
            check(!plain.equals(tagged) && tagged != AEFluidKey.of(fluid, data), "tagged fluid bypass");
            var savedIdentity = tagged.toTag();
            data.putString("identity", "changed");
            check(tagged.toTag().equals(savedIdentity), "fluid key copied input NBT");
        }

        boolean configured = NbtCopyOptions.enabled();
        try {
            for (boolean enabled : new boolean[] {false, true}) {
                NbtCopyOptions.configure(enabled);
                validateNbtCopies();
            }
        } finally {
            NbtCopyOptions.configure(configured);
        }
        check(NbtCopyOptions.enabled() == configured, "Core copy option restored");

        Object oldGeneration = PlainKeyReuse.generation();
        var owner = new PlainKeyCacheOwner() {
            private PlainKeyReuse.Entry entry;
            @Override public PlainKeyReuse.Entry ae2lt$getPlainKey() { return entry; }
            @Override public void ae2lt$setPlainKey(PlainKeyReuse.Entry value) { entry = value; }
        };
        PlainKeyReuse.publish(owner, oldGeneration, one);
        check(PlainKeyReuse.find(owner, oldGeneration) == one, "helper publishes observed generation");
        PlainKeyReuse.clear();
        Object currentGeneration = PlainKeyReuse.generation();
        check(PlainKeyReuse.find(owner, currentGeneration) == null, "helper invalidates old generation");
        var fresh = AEItemKey.of(Items.STONE);
        check(fresh != one && fresh.equals(one), "reload preserves independently owned equal keys");
        PlainKeyReuse.publish(owner, currentGeneration, fresh);
        PlainKeyReuse.publish(owner, oldGeneration, one);
        check(PlainKeyReuse.find(owner, currentGeneration) == fresh, "late old generation rejected");
        PlainKeyReuse.clear();
        var pool = Executors.newFixedThreadPool(8);
        try {
            var tasks = new ArrayList<Callable<AEItemKey>>();
            for (int i = 0; i < 64; i++) tasks.add(() -> AEItemKey.of(new ItemStack(Items.STONE, 64)));
            var results = pool.invokeAll(tasks);
            var first = results.get(0).get();
            for (int i = 0; i < results.size(); i++) {
                var key = results.get(i).get();
                check(key.equals(first), "concurrent equal keys");
                if (i != 0) check(key != first, "concurrent factory ownership");
            }
        } finally {
            pool.shutdownNow();
        }
    }

    private static void validateNbtCopies() throws Exception {
        var compound = nestedTag();
        var copy = compound.copy();
        check(compound.equals(copy), "compound equality");
        copy.getCompound("child").getIntArray("array")[0] = 999;
        check(compound.getCompound("child").getIntArray("array")[0] == 1, "compound deep copy");
        var list = listTag();
        var listCopy = list.copy();
        listCopy.getCompound(0).putInt("changed", 1);
        check(!list.getCompound(0).contains("changed"), "list deep copy");
        var primitiveList = new ListTag();
        primitiveList.add(IntTag.valueOf(7));
        check(primitiveList.equals(primitiveList.copy()), "value tag list");
        check(new CompoundTag().equals(new CompoundTag().copy()) && new ListTag().copy().isEmpty(), "empty NBT");
        var mapCtor = CompoundTag.class.getDeclaredConstructor(Map.class);
        mapCtor.setAccessible(true);
        var linked = new LinkedHashMap<String, Tag>();
        linked.put("nested", compound);
        var foreignMap = (CompoundTag) mapCtor.newInstance(linked);
        var foreignCopy = foreignMap.copy();
        foreignCopy.getCompound("nested").putInt("changed", 1);
        check(!compound.contains("changed"), "non-HashMap fallback deep copy");
        var listCtor = ListTag.class.getDeclaredConstructor(List.class, byte.class);
        listCtor.setAccessible(true);
        var foreignList = (ListTag) listCtor.newInstance(new LinkedList<>(List.of(compound)), (byte) 10);
        check(foreignList.equals(foreignList.copy()), "non-ArrayList fallback");
        var mapField = CompoundTag.class.getDeclaredField("tags");
        mapField.setAccessible(true);
        check(mapField.get(copy).getClass() == java.util.HashMap.class, "native map output");
        var listField = ListTag.class.getDeclaredField("list");
        listField.setAccessible(true);
        check(listField.get(listCopy).getClass() == ArrayList.class, "native list output");
        var transformed = new NbtCopyViews.TransformMap(Map.of("x", IntTag.valueOf(1)), t -> IntTag.valueOf(9));
        check(transformed.copy().get("x").equals(IntTag.valueOf(9)), "supplied transform preserved");
        var transformedList = new NbtCopyViews.TransformIterable(List.of(IntTag.valueOf(1)), t -> IntTag.valueOf(9));
        check(transformedList.copy(0).equals(List.of(IntTag.valueOf(9))), "capacity is only a hint");

    }

    private static CompoundTag nestedTag() {
        var result = new CompoundTag();
        var child = new CompoundTag();
        child.putIntArray("array", new int[] {1, 2, 3});
        result.put("child", child);
        for (int i = 0; i < 63; i++) result.putString("field" + i, "value" + i);
        return result;
    }

    private static ListTag listTag() {
        var list = new ListTag();
        for (int i = 0; i < 64; i++) {
            var child = new CompoundTag();
            child.putInt("value", i);
            list.add(child);
        }
        return list;
    }

    private static Map<String, Object> benchmark() {
        var allocation = (com.sun.management.ThreadMXBean) ManagementFactory.getThreadMXBean();
        check(allocation.isThreadAllocatedMemorySupported(), "allocation measurement supported");
        allocation.setThreadAllocatedMemoryEnabled(true);
        long thread = Thread.currentThread().getId();
        var stacks = new ItemStack[256];
        var tagged = new ItemStack[256];
        var items = List.of(Items.STONE, Items.COBBLESTONE, Items.DIRT, Items.OAK_PLANKS,
                Items.IRON_INGOT, Items.GOLD_INGOT, Items.DIAMOND, Items.REDSTONE);
        for (int i = 0; i < stacks.length; i++) {
            stacks[i] = new ItemStack(items.get(i % items.size()), i % 64 + 1);
            tagged[i] = stacks[i].copy();
            tagged[i].getOrCreateTag().putInt("variant", i);
        }
        var compound = nestedTag();
        var list = listTag();
        var actions = new LinkedHashMap<String, java.util.function.IntFunction<Object>>();
        actions.put("plainItemStack", i -> AEItemKey.of(stacks[i & 255]));
        actions.put("plainItemLike", i -> AEItemKey.of(items.get(i & 7)));
        actions.put("taggedItem", i -> AEItemKey.of(tagged[i & 255]));
        actions.put("plainFluid", i -> AEFluidKey.of((i & 1) == 0 ? Fluids.WATER : Fluids.LAVA));
        actions.put("compoundCopy64", i -> compound.copy());
        actions.put("listCopy64", i -> list.copy());
        var rows = new LinkedHashMap<String, List<Map<String, Object>>>();
        for (var name : actions.keySet()) rows.put(name, new ArrayList<>());
        for (int round = -6; round < 7; round++) {
            var names = new ArrayList<>(actions.keySet());
            java.util.Collections.rotate(names, round);
            for (var name : names) {
                int count = name.contains("Copy") ? 5000 : 200000;
                var action = actions.get(name);
                long bytes = allocation.getThreadAllocatedBytes(thread);
                long start = System.nanoTime();
                for (int i = 0; i < count; i++) sink = action.apply(i);
                long elapsed = System.nanoTime() - start;
                long allocated = allocation.getThreadAllocatedBytes(thread) - bytes;
                if (round >= 0) rows.get(name).add(Map.of("round", round, "operations", count,
                        "nsPerOp", (double) elapsed / count, "bytesPerOp", (double) allocated / count));
            }
        }
        return Map.of("warmupRounds", 6, "sampleRounds", 7, "samples", rows);
    }

    private static void check(boolean condition, String message) {
        assertions++;
        if (!condition) throw new IllegalStateException(message);
    }
}
