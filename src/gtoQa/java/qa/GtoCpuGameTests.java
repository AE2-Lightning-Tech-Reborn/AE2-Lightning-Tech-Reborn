package qa;

import java.lang.reflect.*;
import java.util.*;
import appeng.api.config.Actionable;
import appeng.api.crafting.*;
import appeng.api.networking.*;
import appeng.api.networking.crafting.*;
import appeng.api.networking.security.IActionSource;
import appeng.api.stacks.*;
import appeng.api.storage.MEStorage;
import appeng.blockentity.crafting.CraftingBlockEntity;
import appeng.core.definitions.AEBlocks;
import appeng.crafting.CraftingPlan;
import appeng.crafting.inv.ListCraftingInventory;
import appeng.me.cluster.implementations.CraftingCPUCluster;
import appeng.me.service.CraftingService;
import appeng.me.storage.NetworkStorage;
import com.moakiee.ae2lt.compat.gto.GtoCompatibleCraftingCpuLogic;
import com.moakiee.ae2lt.overload.pattern.PatternConversionService;
import com.moakiee.ae2lt.overload.runtime.model.*;
import com.moakiee.ae2lt.overload.runtime.pattern.OverloadPatternSupport;
import com.moakiee.ae2lt.registry.ModItems;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.*;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.*;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.phys.AABB;
import net.minecraftforge.gametest.*;
import it.unimi.dsi.fastutil.objects.Object2LongOpenHashMap;

@GameTestHolder("ae2lt")
@PrefixGameTestTemplate(false)
public class GtoCpuGameTests {
    @GameTest(templateNamespace = "minecraft", template = "bastion/mobs/empty", timeoutTicks = 100)
    public static void physicalGtoCpuDropsBufferedOutputsOnceOnRemoval(GameTestHelper helper) {
        checkRemovalDrops(helper, true);
    }

    @GameTest(templateNamespace = "minecraft", template = "bastion/mobs/empty", timeoutTicks = 100)
    public static void idlePhysicalGtoCpuDropsRestoredBufferedOutputsOnceOnRemoval(GameTestHelper helper) {
        checkRemovalDrops(helper, false);
    }

    private static void checkRemovalDrops(GameTestHelper helper, boolean activeJob) {
        helper.setBlock(new BlockPos(0, 2, 0), AEBlocks.CRAFTING_STORAGE_64K.block());
        helper.setBlock(new BlockPos(0, 1, 0), AEBlocks.CREATIVE_ENERGY_CELL.block());
        helper.runAfterDelay(20, () -> {
            var fixture = new Fixture(helper);
            try {
                var save = new CompoundTag();
                if (activeJob) {
                    fixture.startAndReload(); // One input dispatched, two remain in CPU inventory.
                    fixture.logic.writeToNBT(save);
                } else {
                    var inventory = new ListCraftingInventory(key -> { });
                    inventory.insert(fixture.input, 2, Actionable.MODULATE);
                    var state = new CompoundTag();
                    state.put("inventory", inventory.writeToNBT());
                    save.put("ae2ltGtoOverloadCpu", state);
                }
                var remainders = new ListCraftingInventory(key -> { });
                remainders.insert(fixture.input, 5, Actionable.MODULATE);
                remainders.insert(fixture.actual, 7, Actionable.MODULATE);
                save.getCompound("ae2ltGtoOverloadCpu").put(
                        "deferredDeliveryRemainders", remainders.writeToNBT());
                fixture.stock.acceptsInsertion = false;
                fixture.logic = new GtoCompatibleCraftingCpuLogic(fixture.cpu);
                fixture.cpu.craftingLogic = fixture.logic;
                fixture.logic.readFromNBT(save);
                var link = fixture.logic.getLastLink();
                var block = (CraftingBlockEntity) helper.getBlockEntity(new BlockPos(0, 2, 0));
                var pos = block.getBlockPos();

                block.breakCluster();
                assertDroppedAmount(helper, pos, fixture.input, 7);
                assertDroppedAmount(helper, pos, fixture.actual, 7);
                helper.assertFalse(fixture.logic.hasJob(), "removal cleared all LT persistent state");
                if (link != null) helper.assertTrue(link.isCanceled(), "removed CPU canceled its job link");

                // A repeated visit must not reproduce either ordinary stock or buffered outputs.
                block.breakCluster();
                fixture.logic.dropStoredItemsForRemoval(helper.getLevel(), pos);
                assertDroppedAmount(helper, pos, fixture.input, 7);
                assertDroppedAmount(helper, pos, fixture.actual, 7);
                helper.succeed();
            } catch (Exception failure) {
                throw new RuntimeException(failure);
            } finally {
                fixture.service.removeNode(fixture.providerNode);
                fixture.network.unmount(fixture.stock);
            }
        });
    }

    private static void assertDroppedAmount(GameTestHelper helper, BlockPos pos, AEItemKey key, long expected) {
        long actual = helper.getLevel().getEntitiesOfClass(ItemEntity.class, new AABB(pos).inflate(4)).stream()
                .map(ItemEntity::getItem)
                .filter(stack -> key.equals(AEItemKey.of(stack)))
                .mapToLong(ItemStack::getCount).sum();
        helper.assertTrue(actual == expected, "dropped " + key + ": expected " + expected + ", got " + actual);
    }

    @GameTest(templateNamespace="minecraft",template="bastion/mobs/empty",timeoutTicks=100)
    public static void physicalGtoCpuPreservesOverloadPauseSaveReturnAndNativeFallback(GameTestHelper helper) {
        checkPhysicalCpu(helper, false);
    }
    static void checkPhysicalCpu(GameTestHelper helper, boolean dynamic) {
        helper.setBlock(new BlockPos(0,2,0),AEBlocks.CRAFTING_STORAGE_64K.block());
        helper.setBlock(new BlockPos(0,1,0),AEBlocks.CREATIVE_ENERGY_CELL.block());
        var fixture=new Fixture[1];
        helper.runAfterDelay(20,()->{
            try { fixture[0]=new Fixture(helper, dynamic);fixture[0].startAndReload(); }
            catch(Exception failure){throw new RuntimeException(failure);}
        });
        helper.runAfterDelay(22,()->fixture[0].step());
        helper.runAfterDelay(24,()->{fixture[0].step();fixture[0].finish();helper.succeed();});
    }

    static void checkDynamicCancellation(GameTestHelper helper) {
        helper.setBlock(new BlockPos(0,2,0),AEBlocks.CRAFTING_STORAGE_64K.block());
        helper.setBlock(new BlockPos(0,1,0),AEBlocks.CREATIVE_ENERGY_CELL.block());
        helper.runAfterDelay(20, () -> {
            var f = new Fixture(helper, true);
            try {
                f.startAndReload();
                var link = f.logic.getLastLink();
                f.logic.cancel();
                helper.assertTrue(link.isCanceled() && !f.logic.hasJob(), "dynamic job did not cancel");
                helper.assertTrue(f.logic.insert(f.actual, 1, Actionable.MODULATE) == 0, "canceled CPU claimed a later machine return");
                helper.assertTrue(f.stock.counts.get(f.input) + f.logic.getStored(f.input) == 2, "cancellation lost or duplicated undispatched inputs");
                helper.assertTrue(f.stock.counts.get(f.actual) == 1, "cancellation duplicated an already delivered output");
                helper.succeed();
            } catch (Exception failure) { throw new RuntimeException(failure); }
            finally { f.service.removeNode(f.providerNode); f.network.unmount(f.stock); }
        });
    }

    static void checkEaepManualState(GameTestHelper helper) {
        helper.setBlock(new BlockPos(0,2,0),AEBlocks.CRAFTING_STORAGE_64K.block());
        helper.setBlock(new BlockPos(0,1,0),AEBlocks.CREATIVE_ENERGY_CELL.block());
        helper.runAfterDelay(20, () -> {
            var f = new Fixture(helper);
            try {
                var missing = new KeyCounter(); missing.add(f.input, 3);
                var original = new CraftingPlan(new GenericStack(f.expected, 3), 1, true, false,
                        new KeyCounter(), new KeyCounter(), missing, new Object2LongOpenHashMap<>(Map.of(f.pattern, 3L)));
                var forced = (ICraftingPlan) Class.forName("com.extendedae_plus.crafting.ForcedCraftingPlan")
                        .getConstructor(ICraftingPlan.class).newInstance(original);
                helper.assertTrue(f.logic.trySubmitJob(f.grid, forced, IActionSource.empty(), null).successful(), "forced job rejected");
                f.logic.setPaused(true);
                var state = Class.forName("com.extendedae_plus.api.crafting.IManualCraftingState");
                helper.assertTrue(state.isInstance(f.logic), "physical CPU lost manual-state interface");
                var snapshot = state.getMethod("eap$getManualWaitingSnapshot");
                helper.assertTrue(((Map<?, ?>) snapshot.invoke(f.logic)).get(f.input).equals(3L), "manual missing snapshot absent");
                helper.assertTrue(f.logic.insert(f.input, 1, Actionable.SIMULATE) == 1
                        && ((Map<?, ?>) snapshot.invoke(f.logic)).get(f.input).equals(3L), "simulation changed manual waiting");
                helper.assertTrue(f.logic.insert(f.input, 1, Actionable.MODULATE) == 1, "manual ingredient refused");
                var save = new CompoundTag(); f.logic.writeToNBT(save);
                f.logic = new GtoCompatibleCraftingCpuLogic(f.cpu); f.cpu.craftingLogic = f.logic; f.logic.readFromNBT(save);
                helper.assertTrue(((Map<?, ?>) snapshot.invoke(f.logic)).get(f.input).equals(2L), "manual waiting did not survive reload");
                helper.assertTrue(f.logic.insert(f.input, 2, Actionable.MODULATE) == 2
                        && ((Map<?, ?>) snapshot.invoke(f.logic)).isEmpty(), "manual waiting did not settle exactly");
                f.logic.cancel();
                helper.assertTrue(((Map<?, ?>) snapshot.invoke(f.logic)).isEmpty(), "canceled manual state was retained");
                helper.succeed();
            } catch (Exception failure) { throw new RuntimeException(failure); }
            finally { f.service.removeNode(f.providerNode); f.network.unmount(f.stock); }
        });
    }

    static void checkNativeProvider(GameTestHelper h, boolean dynamic) {
        h.setBlock(new BlockPos(0,2,0), AEBlocks.CRAFTING_STORAGE_64K.block());
        h.setBlock(new BlockPos(0,1,0), AEBlocks.CREATIVE_ENERGY_CELL.block());
        h.setBlock(new BlockPos(1,2,0), AEBlocks.PATTERN_PROVIDER.block());
        h.setBlock(new BlockPos(2,2,0), net.minecraft.world.level.block.Blocks.CHEST);
        h.runAfterDelay(20, () -> {
            var cpu = ((CraftingBlockEntity) h.getBlockEntity(new BlockPos(0,2,0))).getCluster();
            var grid = cpu.getGrid(); var service = (CraftingService) grid.getCraftingService();
            var provider = ((appeng.blockentity.crafting.PatternProviderBlockEntity) h.getBlockEntity(new BlockPos(1,2,0))).getLogic();
            var input = AEItemKey.of(dynamic ? Items.RAW_IRON : Items.IRON_INGOT);
            var expectedStack = new ItemStack(dynamic ? Items.IRON_INGOT : Items.DIAMOND);
            if (!dynamic) expectedStack.setHoverName(Component.literal("expected"));
            var actualStack = new ItemStack(dynamic ? Items.IRON_INGOT : Items.DIAMOND);
            actualStack.setHoverName(Component.literal(dynamic ? "actual" : "expected"));
            var expected = AEItemKey.of(expectedStack); var actual = AEItemKey.of(actualStack);
            var encoded = PatternDetailsHelper.encodeProcessingPattern(new GenericStack[] {new GenericStack(input,1)},
                    new GenericStack[] {new GenericStack(expected,1)});
            try {
                if (dynamic) {
                    encoded = dynamicPattern(h);
                }
                provider.getPatternInv().setItemDirect(0, encoded);
                provider.updatePatterns();
            } catch (ReflectiveOperationException failure) { throw new IllegalStateException(failure); }
            h.runAfterDelay(2, () -> {
                var network = (NetworkStorage) grid.getStorageService().getInventory(); var stock = new Stock();
                network.mount(0, stock); stock.counts.add(input, 3);
                try {
                    var pattern = provider.getAvailablePatterns().get(0);
                    var used = new KeyCounter(); used.add(input, 1);
                    ICraftingPlan plan = new CraftingPlan(new GenericStack(expected,1), 1, false, false, used,
                            new KeyCounter(), new KeyCounter(), new Object2LongOpenHashMap<>(Map.of(pattern,1L)));
                    if (!dynamic) plan = (ICraftingPlan) Class.forName("com.extendedae_plus.crafting.ForcedCraftingPlan")
                            .getConstructor(ICraftingPlan.class).newInstance(plan);
                    var logic = (GtoCompatibleCraftingCpuLogic) cpu.craftingLogic;
                    h.assertTrue(logic.trySubmitJob(grid, plan, IActionSource.empty(), null).successful() && logic.usesLt(),
                            "actual provider job did not select LT execution");
                    logic.tickCraftingLogic(grid.getEnergyService(), service);
                    logic.setPaused(true);
                    var chest = (net.minecraft.world.level.block.entity.ChestBlockEntity) h.getBlockEntity(new BlockPos(2,2,0));
                    long received = java.util.stream.IntStream.range(0, chest.getContainerSize()).mapToLong(slot ->
                            input.equals(AEItemKey.of(chest.getItem(slot))) ? chest.getItem(slot).getCount() : 0).sum();
                    h.assertTrue(received == 1, "native GTO provider rejected or duplicated the LT-owned copy: " + received);
                    long accepted = service.insertIntoCpus(actual, 1, Actionable.MODULATE);
                    stock.insert(actual, 1 - accepted, Actionable.MODULATE, IActionSource.empty());
                    h.assertTrue(!logic.hasJob() && stock.counts.get(input) == 2 && stock.counts.get(actual) == 1,
                            "actual provider output did not settle exactly once");
                    h.succeed();
                } catch (ReflectiveOperationException failure) { throw new IllegalStateException(failure); }
                finally { network.unmount(stock); }
            });
        });
    }
    private static ItemStack dynamicPattern(GameTestHelper h) throws ReflectiveOperationException {
        var level = h.getLevel();
        var processing = PatternDetailsHelper.decodePattern(PatternDetailsHelper.encodeProcessingPattern(
                new GenericStack[] {new GenericStack(AEItemKey.of(Items.RAW_IRON), 1)},
                new GenericStack[] {new GenericStack(AEItemKey.of(Items.IRON_INGOT), 1)}), level);
        var catalog = Class.forName("com.sorrowmist.useless.content.recipe.AlloyFurnaceRecipeCatalog");
        var entries = (List<?>) catalog.getMethod("findPatternCandidates",
                net.minecraft.world.level.Level.class, IPatternDetails.class).invoke(null, level, processing);
        h.assertTrue(!entries.isEmpty(), "missing upstream iron smelting recipe");
        var entry = entries.getFirst();
        var encoding = Class.forName("com.sorrowmist.useless.content.machines.advanced_alloy_furnace.ae.OmniversalPatternEncoding");
        var encoded = (ItemStack) encoding.getMethod("reencodeFromRecipe", entry.getClass(),
                net.minecraft.world.level.Level.class).invoke(null, entry, level);
        h.assertTrue(!encoded.isEmpty(), "upstream recipe did not encode");

        // Keep the upstream recipe identity and payload, selecting its persisted ID_ONLY
        // output mode through the real data component. A bare dynamic wrapper does not
        // encode these modes and becomes an ordinary pattern when decoded after saving.
        var components = Class.forName("com.sorrowmist.useless.core.component.UComponents");
        var component = ((java.util.function.Supplier<?>) components.getField("OMNIVERSAL_PATTERN_DATA").get(null)).get();
        var type = Class.forName("com.sorrowmist.useless.compat.forge1201.component.DataComponentType");
        var access = Class.forName("com.sorrowmist.useless.compat.forge1201.component.ComponentAccess");
        var data = access.getMethod("get", ItemStack.class, type).invoke(null, encoded, component);
        var fields = data.getClass().getRecordComponents();
        var types = new Class<?>[fields.length];
        var values = new Object[fields.length];
        for (int i = 0; i < fields.length; i++) {
            types[i] = fields[i].getType();
            values[i] = fields[i].getName().equals("itemIdOutputSlots") ? List.of(0) : fields[i].getAccessor().invoke(data);
        }
        var dynamicData = data.getClass().getConstructor(types).newInstance(values);
        access.getMethod("set", ItemStack.class, type, Object.class).invoke(null, encoded, component, dynamicData);
        var decoded = PatternDetailsHelper.decodePattern(encoded, level);
        var dynamic = Class.forName("com.sorrowmist.useless.content.machines.advanced_alloy_furnace.ae.DynamicComponentPattern");
        h.assertTrue(dynamic.isInstance(decoded) && (boolean) dynamic.getMethod("isItemIdOutput", int.class).invoke(decoded, 0),
                "persisted upstream pattern lost its dynamic output mode");
        return encoded;
    }

    private static final class Fixture {
        final GameTestHelper h;
        final AEItemKey input;
        final AEItemKey expected;
        final AEItemKey actual;
        final IPatternDetails pattern;
        final CraftingCPUCluster cpu;
        final IGrid grid;
        final CraftingService service;
        final NetworkStorage network;
        final Stock stock=new Stock();
        final IGridNode providerNode;
        GtoCompatibleCraftingCpuLogic logic;
        int pushes;
        int returns;
        Fixture(GameTestHelper helper) { this(helper, false); }
        Fixture(GameTestHelper helper, boolean dynamic) {
            h=helper;
            var block=(CraftingBlockEntity)helper.getBlockEntity(new BlockPos(0,2,0));
            cpu=block.getCluster();h.assertTrue(cpu!=null && cpu.isActive(),"powered physical CPU formed");
            grid=cpu.getGrid();service=(CraftingService)grid.getCraftingService();
            input=AEItemKey.of(dynamic ? Items.RAW_IRON : Items.IRON_INGOT);
            network=(NetworkStorage)grid.getStorageService().getInventory();
            network.mount(0,stock);stock.counts.add(input,3);
            var template=new ItemStack(dynamic ? Items.IRON_INGOT : Items.DIAMOND);
            if (!dynamic) template.setHoverName(Component.literal("expected"));
            var variant=new ItemStack(dynamic ? Items.IRON_INGOT : Items.DIAMOND);variant.setHoverName(Component.literal("actual"));
            expected=AEItemKey.of(template);actual=AEItemKey.of(variant);
            var source=PatternDetailsHelper.encodeProcessingPattern(new GenericStack[]{new GenericStack(input,1)},new GenericStack[]{new GenericStack(expected,1)});
            if (dynamic) {
                try {
                    // GTO indexes provider patterns by canonical identity. Enter through the
                    // actual decoder, just as a placed provider and a saved job both do.
                    pattern = PatternDetailsHelper.decodePattern(dynamicPattern(helper), helper.getLevel());
                } catch (ReflectiveOperationException failure) { throw new IllegalStateException("Actual Useless pattern API changed", failure); }
            } else {
                var parsed=OverloadPatternSupport.toParsedDefinition(source,PatternDetailsHelper.decodePattern(source,helper.getLevel()));
                var encoded=new PatternConversionService().createOverloadPatternStack(ModItems.OVERLOAD_PATTERN.get(),parsed,
                        EncodedOverloadPattern.builder().input(0,MatchMode.STRICT).output(0,MatchMode.ID_ONLY).build());
                pattern=PatternDetailsHelper.decodePattern(encoded,helper.getLevel());
            }
            h.assertTrue(pattern!=null,"overload pattern decoded");
            var provider=new ICraftingProvider(){
                public List<IPatternDetails> getAvailablePatterns(){return List.of(pattern);}
                public boolean isBusy(){return false;}
                public boolean pushPattern(IPatternDetails details,KeyCounter[] inputs){
                    h.assertTrue(inputs.length==1 && inputs[0].get(input)==1,"one physical operation consumes one input");
                    inputs[0].clear();pushes++;return true;
                }
            };
            providerNode=(IGridNode)Proxy.newProxyInstance(IGridNode.class.getClassLoader(),new Class<?>[]{IGridNode.class},(p,m,a)->switch(m.getName()){
                case "getService"->a[0]==ICraftingProvider.class?provider:null;
                case "getOwner"->provider;case "getGrid"->grid;case "getLevel"->helper.getLevel();
                case "isActive","isOnline","isPowered","meetsChannelRequirements"->true;
                case "hashCode"->System.identityHashCode(p);case "equals"->p==a[0];
                case "toString"->"GTO QA provider";default->null;});
            service.addNode(providerNode,null);
            logic=(GtoCompatibleCraftingCpuLogic)cpu.craftingLogic;
        }
        void startAndReload() throws Exception {
            var used=new KeyCounter();used.add(input,3);
            var plan=new CraftingPlan(new GenericStack(expected,3),1,false,false,used,new KeyCounter(),new KeyCounter(),
                    new Object2LongOpenHashMap<>(Map.of(pattern,3L)));
            h.assertTrue(logic.trySubmitJob(grid,plan,IActionSource.empty(),null).successful(),"overload job submitted to physical GTO CPU");
            h.assertTrue(logic.usesLt(),"overload plan selected LT execution");
            logic.tickCraftingLogic(grid.getEnergyService(),service);
            h.assertTrue(pushes==1,"physical CPU respected one-operation budget");
            logic.setPaused(true);
            var save=new CompoundTag();logic.writeToNBT(save);
            h.assertTrue(save.contains("ae2ltGtoOverloadCpu"),"LT job saved under owned key");
            logic=new GtoCompatibleCraftingCpuLogic(cpu);cpu.craftingLogic=logic;logic.readFromNBT(save);
            h.assertTrue(logic.isPaused(),"pause persisted across reload");
            h.assertTrue(logic.getRemainingOutputAmount()==3,"remaining output persisted across reload");
            var linkOwner=appeng.crafting.CraftingLink.class.getDeclaredMethod("getCpu");linkOwner.setAccessible(true);
            h.assertTrue(linkOwner.invoke(logic.getLastLink())==cpu,"crafting link retains physical CPU identity");
            logic.tickCraftingLogic(grid.getEnergyService(),service);
            h.assertTrue(pushes==1,"paused job did not dispatch");
            // The real grid also ticks this physical CPU between delayed GameTest callbacks.
            // Keep it paused outside each explicitly measured operation budget.
            acceptReturn();
        }
        void step(){logic.setPaused(false);logic.tickCraftingLogic(grid.getEnergyService(),service);h.assertTrue(pushes==returns+1,"next physical operation dispatched: pushes="+pushes+" returns="+returns+" remaining="+logic.getRemainingOutputAmount()+" stored="+logic.getStored(input)+" pending="+logic.getPendingOutputs(expected)+" waiting="+logic.getWaitingFor(expected)+" paused="+logic.isPaused()+" active="+cpu.isActive());acceptReturn();logic.setPaused(true);}
        void acceptReturn(){
            long accepted=service.insertIntoCpus(actual,1,Actionable.MODULATE);
            stock.insert(actual,1-accepted,Actionable.MODULATE,IActionSource.empty());returns++;
            h.assertTrue(logic.getRemainingOutputAmount()==3-returns,"same-ID variant reduced remaining output exactly once");
        }
        void finish(){
            logic.setPaused(false);
            h.assertTrue(!logic.hasJob(),"overload job finished");
            h.assertTrue(stock.counts.get(input)==0 && stock.counts.get(actual)==3,"three inputs consumed and three actual outputs retained");
            var emitted=new KeyCounter();emitted.add(input,1);
            var ordinary=new CraftingPlan(new GenericStack(input,1),1,false,false,new KeyCounter(),emitted,new KeyCounter(),new Object2LongOpenHashMap<>());
            h.assertTrue(logic.trySubmitJob(grid,ordinary,IActionSource.empty(),null).successful(),"ordinary job submitted after overload completion");
            h.assertFalse(logic.usesLt(),"ordinary plan returned to native GTO execution");
            logic.insert(input,1,Actionable.MODULATE);
            h.assertFalse(logic.hasJob(),"native GTO job completed");
            service.removeNode(providerNode);network.unmount(stock);
        }
    }
    private static final class Stock implements MEStorage {
        final KeyCounter counts=new KeyCounter();
        boolean acceptsInsertion = true;
        public Component getDescription(){return Component.literal("QA stock");}
        public long insert(AEKey key,long amount,Actionable mode,IActionSource source){if(!acceptsInsertion)return 0;if(mode==Actionable.MODULATE)counts.add(key,amount);return amount;}
        public long extract(AEKey key,long amount,Actionable mode,IActionSource source){long got=Math.min(amount,Math.max(0,counts.get(key)));if(mode==Actionable.MODULATE)counts.remove(key,got);return got;}
        public void getAvailableStacks(KeyCounter out){out.addAll(counts);}
    }
}
