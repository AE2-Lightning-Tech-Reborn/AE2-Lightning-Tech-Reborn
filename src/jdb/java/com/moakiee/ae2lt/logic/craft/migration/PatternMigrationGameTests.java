package com.moakiee.ae2lt.logic.craft.migration;

import appeng.api.config.Actionable;
import appeng.api.crafting.PatternDetailsHelper;
import appeng.api.networking.security.IActionSource;
import appeng.api.parts.PartHelper;
import appeng.api.stacks.AEItemKey;
import appeng.api.util.AEColor;
import appeng.blockentity.crafting.PatternProviderBlockEntity;
import appeng.blockentity.storage.DriveBlockEntity;
import appeng.core.definitions.*;
import appeng.me.storage.NetworkStorage;
import com.moakiee.ae2lt.block.MatrixMultiblockDirectionalBlock;
import com.moakiee.ae2lt.blockentity.*;
import com.moakiee.ae2lt.logic.craft.*;
import com.moakiee.ae2lt.registry.ModBlocks;
import com.mojang.authlib.GameProfile;
import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.*;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.*;
import net.minecraft.world.item.crafting.*;
import net.minecraft.world.level.block.Block;
import net.minecraftforge.common.util.FakePlayerFactory;
import net.minecraftforge.gametest.*;

@GameTestHolder("ae2lt_migration")
@PrefixGameTestTemplate(false)
public final class PatternMigrationGameTests {
    @GameTest(templateNamespace="ae2lt_migration", template="migration_test", timeoutTicks=300)
    public static void exactPatternsMoveAndOriginalDuplicatesReturnToMe(GameTestHelper h) {
        fixture(h, f -> {
            var first = pattern(h, false); var settings = pattern(h, true);
            var inv = f.provider.getLogic().getPatternInv();
            inv.setItemDirect(0, first.copy()); inv.setItemDirect(1, first.copy()); inv.setItemDirect(2, settings.copy());
            var processing = processingPattern(List.of(
                    new appeng.api.stacks.GenericStack(AEItemKey.of(Items.IRON_INGOT), 1)),
                    List.of(new appeng.api.stacks.GenericStack(AEItemKey.of(Items.GOLD_INGOT), 1)));
            inv.setItemDirect(3, processing.copy());
            f.controller.getPatternMigration().start(f.player);
            await(h, f, () -> {
                var s=f.controller.getPatternMigration().snapshot();
                h.assertTrue(s.moved()==2 && s.recovered()==1 && s.incompatible()==1, "exact settings retained; original duplicate recovered");
                h.assertTrue(inv.getStackInSlot(0).isEmpty() && inv.getStackInSlot(1).isEmpty()
                        && inv.getStackInSlot(2).isEmpty() && ItemStack.matches(processing, inv.getStackInSlot(3)), "only physical crafting items move");
                h.assertTrue(count(f, first)==1 && count(f, settings)==1, "full encoded definitions arrive intact");
                h.assertTrue(f.port.getGrid().getStorageService().getInventory().extract(AEItemKey.of(first), 99,
                        Actionable.SIMULATE, IActionSource.ofPlayer(f.player))==1, "ME receives original encoded item, never blank");
                done(h,f);
            });
        });
    }

    @GameTest(templateNamespace="ae2lt_migration", template="migration_test", timeoutTicks=300)
    public static void fullTargetStillRecoversDuplicatesIntoBackpack(GameTestHelper h) {
        fixture(h, f -> {
            var first=pattern(h,false); var second=pattern(h,true);
            for (var storage:f.port.getPatternStorages()) for(int i=0;i<storage.capacity();i++) storage.getInventory().setStackInSlot(i,first.copy());
            f.drive.getInternalInventory().setItemDirect(0,ItemStack.EMPTY);
            var inv=f.provider.getLogic().getPatternInv(); inv.setItemDirect(0,first.copy()); inv.setItemDirect(1,second.copy());
            f.controller.getPatternMigration().start(f.player);
            await(h,f,()->{
                var s=f.controller.getPatternMigration().snapshot();
                h.assertTrue(s.moved()==0 && s.recovered()==1 && s.noSpace()==1,"full warehouse continues duplicate collection");
                h.assertTrue(inv.getStackInSlot(0).isEmpty() && ItemStack.matches(second,inv.getStackInSlot(1)),"new definition stays at source");
                h.assertTrue(f.player.getInventory().items.stream().filter(i->ItemStack.isSameItemSameTags(i,first)).mapToInt(ItemStack::getCount).sum()==1,"backpack receives exactly original item");
                done(h,f);
            });
        });
    }

    @GameTest(templateNamespace="ae2lt_migration", template="migration_test", timeoutTicks=300)
    public static void fullMeAndBackpackLeaveSourceUnchanged(GameTestHelper h) {
        fixture(h,f->{
            var first=pattern(h,false); f.port.getPatternStorages().get(0).getInventory().setStackInSlot(0,first.copy());
            f.drive.getInternalInventory().setItemDirect(0,ItemStack.EMPTY);
            for(int i=0;i<f.player.getInventory().items.size();i++)f.player.getInventory().items.set(i,new ItemStack(Items.COBBLESTONE,64));
            var inv=f.provider.getLogic().getPatternInv();inv.setItemDirect(0,first.copy());
            f.controller.getPatternMigration().start(f.player);
            await(h,f,()->{
                var s=f.controller.getPatternMigration().snapshot();
                h.assertTrue(s.recovered()==0 && s.refundBlocked()==1 && ItemStack.matches(first,inv.getStackInSlot(0)),"both destinations full conserve source");
                done(h,f);
            });
        });
    }

    @GameTest(templateNamespace="ae2lt_migration", template="migration_test", timeoutTicks=300)
    public static void idempotentStartLeaseStopAndOffline(GameTestHelper h) {
        fixture(h,f->{
            var manager=f.controller.getPatternMigration(); manager.start(f.player);manager.start(f.player);
            h.assertTrue(manager.snapshot().active(),"duplicate start cannot cancel");
            var other=new MatrixPatternMigration(f.controller);other.start(f.player);
            h.assertTrue(other.snapshot().reason()==PatternMigrationSnapshot.Reason.LEASE_BUSY,"second task on same network is rejected");
            manager.stop(PatternMigrationSnapshot.Reason.CANCELLED);
            h.assertTrue(manager.snapshot().stage()==PatternMigrationSnapshot.Stage.STOPPED,"explicit cancellation persists report");
            manager.start(f.player);unregister(f.player);
            h.runAfterDelay(2,()->{
                h.assertTrue(manager.snapshot().reason()==PatternMigrationSnapshot.Reason.OFFLINE,"offline aborts without requiring open menu");
                done(h,f);
            });
        });
    }

    @GameTest(templateNamespace="ae2lt_migration", template="migration_test", timeoutTicks=300)
    public static void recoveryPersistsAndBlocksNewMigrationWhenFull(GameTestHelper h) {
        fixture(h,f->{
            f.player.setGameMode(net.minecraft.world.level.GameType.CREATIVE);
            var first=pattern(h,false);var tag=new CompoundTag();tag.put("PatternMigrationRecovery",first.save(new CompoundTag()));
            var manager=f.controller.getPatternMigration();manager.readFrom(tag);
            var saved=new CompoundTag();manager.writeTo(saved);
            h.assertTrue(ItemStack.matches(first,ItemStack.of(saved.getCompound("PatternMigrationRecovery"))),"persistent recovery keeps full components");
            f.drive.getInternalInventory().setItemDirect(0,ItemStack.EMPTY);
            for(int i=0;i<f.player.getInventory().items.size();i++)f.player.getInventory().items.set(i,new ItemStack(Items.COBBLESTONE,64));
            manager.start(f.player);h.assertTrue(manager.snapshot().reason()==PatternMigrationSnapshot.Reason.RECOVERY,"recovery must clear before next task");
            f.player.getInventory().items.set(0,ItemStack.EMPTY);manager.recover(f.player);
            h.assertTrue(manager.recoveryStack().isEmpty() && ItemStack.matches(first,f.player.getInventory().items.get(0)),"menu recovery returns one original item");
            done(h,f);
        });
    }

    @GameTest(templateNamespace="ae2lt_migration", template="migration_test", timeoutTicks=300)
    public static void transformedRefundFilterBlocksUncertifiedStorageBus(GameTestHelper h) {
        fixture(h,f->{
            var first=pattern(h,false);var storage=new NetworkStorage();var unsafe=new FakeStorageBusInventory();
            storage.mount(100,unsafe);
            var anonymous=new UnnamedInventory();storage.mount(90,anonymous);
            h.assertTrue(storage.insert(AEItemKey.of(first),1,Actionable.SIMULATE,IActionSource.empty())==1,"outside scope native storage works");
            try(var scope=PatternMigrationRefundScope.open(new PatternMigrationRefundScope.Policy())){
                h.assertTrue(storage.insert(AEItemKey.of(first),1,Actionable.SIMULATE,IActionSource.empty())==0,"simulation excludes unknown bus");
                h.assertTrue(storage.insert(AEItemKey.of(first),1,Actionable.MODULATE,IActionSource.empty())==0 && unsafe.inserted==0 && anonymous.inserted==0,"actual insertion uses identical filter");
            }
            done(h,f);
        });
    }

    @GameTest(templateNamespace="ae2lt_migration",template="migration_test",timeoutTicks=300)
    public static void nativeAssemblerAndPartProviderUseOnlyPatternSlots(GameTestHelper h) {
        fixture(h,f->{
            var cable=f.port.getBlockPos().east();var level=h.getLevel();
            var assemblerPos=cable.south().east();
            level.setBlockAndUpdate(assemblerPos,AEBlocks.MOLECULAR_ASSEMBLER.block().defaultBlockState());
            var assembler=(appeng.blockentity.crafting.MolecularAssemblerBlockEntity)level.getBlockEntity(assemblerPos);
            var encoded=pattern(h,false);assembler.getInternalInventory().setItemDirect(10,encoded.copy());
            var host=(appeng.api.parts.IPartHost)level.getBlockEntity(cable);
            PartHelper.setPart(level,cable,Direction.UP,f.player,AEParts.PATTERN_PROVIDER.asItem());
            var part=(appeng.helpers.patternprovider.PatternProviderLogicHost)host.getPart(Direction.UP);
            var changed=pattern(h,true);part.getLogic().getPatternInv().setItemDirect(0,changed.copy());
            h.runAfterDelay(5,()->{
                f.controller.getPatternMigration().start(f.player);
                await(h,f,()->{
                    h.assertTrue(f.controller.getPatternMigration().snapshot().moved()==2,"both concrete block and part sources are discovered");
                    h.assertTrue(assembler.getInternalInventory().getStackInSlot(10).isEmpty()&&part.getLogic().getPatternInv().getStackInSlot(0).isEmpty(),"physical pattern slots move");
                    done(h,f);
                });
            });
        });
    }

    @GameTest(templateNamespace="ae2lt_migration",template="migration_test",timeoutTicks=300)
    public static void autonomousAssemblerOutputPreventsOtherSourceTransfer(GameTestHelper h) {
        fixture(h,f->{
            var pos=f.port.getBlockPos().east().south().east();
            h.getLevel().setBlockAndUpdate(pos,AEBlocks.MOLECULAR_ASSEMBLER.block().defaultBlockState());
            var assembler=(appeng.blockentity.crafting.MolecularAssemblerBlockEntity)h.getLevel().getBlockEntity(pos);
            assembler.getInternalInventory().setItemDirect(9,new ItemStack(Items.STICK));
            f.provider.getLogic().getPatternInv().setItemDirect(0,pattern(h,false));
            h.runAfterDelay(5,()->{
                var manager=f.controller.getPatternMigration();manager.start(f.player);
                h.runAfterDelay(4,()->{
                    h.assertTrue(manager.snapshot().stage()==PatternMigrationSnapshot.Stage.WAITING
                            && !f.provider.getLogic().getPatternInv().getStackInSlot(0).isEmpty(),"autonomous pending output blocks network migration");
                    assembler.getInternalInventory().setItemDirect(9,ItemStack.EMPTY);
                    await(h,f,()->{h.assertTrue(manager.snapshot().moved()==1,"cleared output allows migration");done(h,f);});
                });
            });
        });
    }

    @GameTest(templateNamespace="ae2lt_migration",template="migration_test",timeoutTicks=300)
    public static void targetDisconnectAbortsAndNewSourceWaitsForNextTask(GameTestHelper h) {
        fixture(h,f->{
            var manager=f.controller.getPatternMigration();manager.start(f.player);
            var pos=f.drive.getBlockPos().north();
            h.getLevel().setBlockAndUpdate(pos,AEBlocks.PATTERN_PROVIDER.block().defaultBlockState());
            var added=(PatternProviderBlockEntity)h.getLevel().getBlockEntity(pos);var encoded=pattern(h,false);
            added.getLogic().getPatternInv().setItemDirect(0,encoded.copy());
            await(h,f,()->{
                h.assertTrue(!added.getLogic().getPatternInv().getStackInSlot(0).isEmpty(),"new node is excluded by the captured epoch");
                h.runAfterDelay(6,()->{manager.start(f.player);
                await(h,f,()->{
                    h.assertTrue(added.getLogic().getPatternInv().getStackInSlot(0).isEmpty(),"next click includes the new node");
                    manager.start(f.player);f.port.getMainNode().destroy();manager.tick();
                    h.assertTrue(manager.snapshot().reason()==PatternMigrationSnapshot.Reason.TARGET_CHANGED,"disconnect releases task and retains report");
                    done(h,f);
                });
                });
            });
        });
    }

    @GameTest(templateNamespace="ae2lt_migration",template="migration_test",timeoutTicks=6000)
    public static void oneHundredThousandPhysicalSlotsUseBoundedSlices(GameTestHelper h) {
        fixture(h,f->{
            try {
                var logic=f.provider.getLogic();
                var inventory=new appeng.util.inv.AppEngInternalInventory(logic,100000,1);
                setField(logic,"patternInventory",inventory);
                var processing=processingPattern(List.of(new appeng.api.stacks.GenericStack(AEItemKey.of(Items.IRON_INGOT),1)),
                        List.of(new appeng.api.stacks.GenericStack(AEItemKey.of(Items.GOLD_INGOT),1)));
                // Fill without firing 100000 setup notifications. Actual migration uses the native host callbacks.
                var backing=(net.minecraft.core.NonNullList<ItemStack>)MigrationReflection.field(inventory,"stacks");
                for(int i=0;i<backing.size();i++)backing.set(i,processing.copy());
                backing.set(0,pattern(h,false));backing.set(99999,pattern(h,true));
                f.controller.getPatternMigration().start(f.player);
                measure(h,f,new long[4]);
            } catch(Exception e){throw new AssertionError(e);}
        });
    }

    private static void measure(GameTestHelper h,Fixture f,long[] stats){
        h.runAfterDelay(1,()->{
            var s=f.controller.getPatternMigration().snapshot();stats[0]++;stats[1]=Math.max(stats[1],s.lastSliceNanos());stats[2]+=s.lastSliceNanos();
            if(s.active()){measure(h,f,stats);return;}
            h.assertTrue(s.stage()==PatternMigrationSnapshot.Stage.COMPLETE&&s.scanned()>=100000&&s.incompatible()==99998&&s.moved()==2,"100000 real slots finish with exact counts: "+s);
            System.out.println("MIGRATION_100K_PHYSICAL ticks="+stats[0]+" maxSliceNs="+stats[1]+" sampledTotalNs="+stats[2]+" budgetHits="+s.budgetHits());
            done(h,f);
        });
    }

    @GameTest(templateNamespace="ae2lt_migration", template="migration_test", timeoutTicks=300)
    public static void nativeCpuRejectsStartThenPausesAndResumesTask(GameTestHelper h) {
        fixture(h,f->{
            var encoded=pattern(h,false);var details=PatternDetailsHelper.decodePattern(encoded,h.getLevel());
            var grid=f.port.getGrid();
            var cpu=(appeng.me.cluster.implementations.CraftingCPUCluster)grid.getCraftingService().getCpus().iterator().next();
            var plan=new appeng.crafting.CraftingPlan(new appeng.api.stacks.GenericStack(AEItemKey.of(Items.OAK_PLANKS),4),
                    8,false,false,new appeng.api.stacks.KeyCounter(),new appeng.api.stacks.KeyCounter(),
                    new appeng.api.stacks.KeyCounter(),Map.of(details,1L));
            h.assertTrue(cpu.submitJob(grid,plan,IActionSource.ofPlayer(f.player),null).successful(),"real native CPU accepts fixture job");
            var manager=f.controller.getPatternMigration();manager.start(f.player);
            h.assertTrue(manager.snapshot().reason()==PatternMigrationSnapshot.Reason.BUSY,"busy CPU rejects initial start");
            cpu.cancelJob();manager.start(f.player);
            f.provider.getLogic().getPatternInv().setItemDirect(0,encoded.copy());
            h.assertTrue(cpu.submitJob(grid,plan,IActionSource.ofPlayer(f.player),null).successful(),"new task begins during migration");
            h.runAfterDelay(3,()->{
                h.assertTrue(manager.snapshot().stage()==PatternMigrationSnapshot.Stage.WAITING,"running migration pauses");
                h.assertTrue(!f.provider.getLogic().getPatternInv().getStackInSlot(0).isEmpty(),"pause keeps source unchanged");
                cpu.cancelJob();await(h,f,()->{
                    h.assertTrue(manager.snapshot().moved()==1,"network idle automatically resumes original task");done(h,f);
                });
            });
        });
    }

    @GameTest(templateNamespace="ae2lt_migration",template="migration_test",timeoutTicks=300)
    public static void eaeCoreUsesPhysicalSlotsAndDefersCatalogRefresh(GameTestHelper h) throws Exception {
        var owner=place(h,new BlockPos(3,2,3),"expatternprovider:assembler_matrix_pattern");
        var pos=owner.getBlockPos();
        var cluster=Class.forName("com.glodblock.github.extendedae.common.me.matrix.ClusterAssemblerMatrix")
                .getConstructor(BlockPos.class,BlockPos.class).newInstance(pos,pos);
        setField(owner,"cluster",cluster);
        var inventory=(appeng.api.inventories.InternalInventory)MigrationReflection.call(owner,"getPatternInventory");
        var first=pattern(h,false);inventory.setItemDirect(0,first.copy());inventory.setItemDirect(1,first.copy());
        var cached=(List<?>)MigrationReflection.call(owner,"getAvailablePatterns");
        h.assertTrue(cached.size()==2,"native EAE cache initially contains physical patterns");
        try(var mutations=PatternMigrationMutationScope.open()){
            inventory.extractItem(0,1,false);inventory.extractItem(1,1,false);
            h.assertTrue(cached.size()==2,"EAE catalog stays deferred during one batch");
        }
        h.assertTrue(cached.isEmpty(),"native EAE refresh flushes after batch");
        var node=proxyNode(owner);
        var sources=new PatternMigrationSources().discover(node,null);
        h.assertTrue(sources.size()==1&&sources.get(0).count==inventory.size()&&sources.get(0).priority==0,
                "EAE physical core receives highest priority");
        var crafter=place(h,new BlockPos(4,2,3),"expatternprovider:assembler_matrix_crafter");
        ((java.util.Collection<Object>)MigrationReflection.field(cluster,"availableCrafters")).add(crafter);
        if(MigrationReflection.hasField(crafter,"outputBuffer")){
            setField(MigrationReflection.field(crafter,"outputBuffer"),"size",1L);
        }else{
            var threads=(Object[])MigrationReflection.field(crafter,"threads");
            var inv=(appeng.api.inventories.InternalInventory)MigrationReflection.call(threads[0],"getInternalInventory");
            ((net.minecraft.core.NonNullList<ItemStack>)MigrationReflection.field(inv,"stacks")).set(9,new ItemStack(Items.OAK_PLANKS));
        }
        h.assertTrue(sources.get(0).busy.getAsBoolean(),"one pending EAE output blocks migration");
        h.succeed();
    }

    @GameTest(templateNamespace="ae2lt_migration",template="migration_test",timeoutTicks=300)
    @SuppressWarnings("unchecked")
    public static void eaepAggregateDeduplicatesPhysicalCoreInventories(GameTestHelper h) throws Exception {
        if(!net.minecraftforge.fml.ModList.get().isLoaded("extendedae_plus")){System.out.println("MIGRATION_EAEP_SKIPPED");h.succeed();return;}
        try {
            Class.forName(PatternMigrationSources.EAEP_MATRIX);
        } catch (ClassNotFoundException legacyForgeVersion) {
            eaepDirectCoreUsesPhysicalInventory(h);
            return;
        }
        var first=place(h,new BlockPos(3,2,3),"extendedae_plus:assembler_matrix_hybrid_plus");
        var last=place(h,new BlockPos(4,2,3),"extendedae_plus:assembler_matrix_hybrid_plus");
        var aggregate=place(h,new BlockPos(5,2,3),"extendedae_plus:super_assembler_matrix_frame");
        var type=Class.forName("com.extendedae_plus.content.matrix.supermatrix.SuperAssemblerMatrixCluster");
        int capacity=100;
        var cluster=type.getMethod("ultimate",BlockPos.class,BlockPos.class).invoke(null,first.getBlockPos(),aggregate.getBlockPos());
        setField(first,"patternInventory",new appeng.util.inv.AppEngInternalInventory((appeng.util.inv.InternalInventoryHost)first,capacity-40,1));
        setField(last,"patternInventory",new appeng.util.inv.AppEngInternalInventory((appeng.util.inv.InternalInventoryHost)last,40,1));
        ((List<Object>)MigrationReflection.field(cluster,"patternCores")).addAll(List.of(first,last));
        for(var owner:List.of(first,last))setField(owner,"superMatrixCluster",cluster);
        setField(aggregate,"superCluster",cluster);
        var adapter=new PatternMigrationSources();var sources=adapter.discover(proxyNode(aggregate),null);
        h.assertTrue(sources.size()==2&&sources.stream().mapToInt(source->source.count).sum()==capacity,"aggregate maps underlying physical inventories");
        h.assertTrue(sources.get(1).count==40,"last core exposes only actual forty physical slots");
        h.assertTrue(adapter.discover(proxyNode(last),null).isEmpty(),"direct core and aggregate are not counted twice");
        var pending=(it.unimi.dsi.fastutil.objects.Object2LongLinkedOpenHashMap<appeng.api.stacks.AEKey>)MigrationReflection.field(cluster,"outputBuffer");
        pending.addTo(AEItemKey.of(Items.OAK_PLANKS),1);
        h.assertTrue(sources.get(0).busy.getAsBoolean(),"undelivered outputs mean executing even when capacity is available");
        System.out.println("MIGRATION_EAEP_ACTUAL_JAR_PASS capacity="+capacity);h.succeed();
    }

    private static void eaepDirectCoreUsesPhysicalInventory(GameTestHelper h) throws Exception {
        var owner = place(h, new BlockPos(3,2,3), "extendedae_plus:assembler_matrix_pattern_plus");
        h.assertTrue(owner.getClass().getName().equals(PatternMigrationSources.EAEP_CORE), "actual Forge Plus physical core");
        var pos = owner.getBlockPos();
        var cluster = Class.forName("com.glodblock.github.extendedae.common.me.matrix.ClusterAssemblerMatrix")
                .getConstructor(BlockPos.class, BlockPos.class).newInstance(pos, pos);
        setField(owner, "cluster", cluster);
        var inventory = (appeng.api.inventories.InternalInventory)MigrationReflection.call(owner, "getPatternInventory");
        h.assertTrue(inventory.size() == 72, "Forge 1.5.5 exposes all 72 physical slots");
        var encoded = pattern(h, false);
        inventory.setItemDirect(0, encoded.copy());
        inventory.setItemDirect(71, encoded.copy());
        var adapter = new PatternMigrationSources();
        var sources = adapter.discover(proxyNode(owner), null);
        h.assertTrue(sources.size() == 1 && sources.get(0).count == 72 && sources.get(0).priority == 0,
                "Plus subclass retains full physical capacity and preferred migration priority");
        h.assertTrue(adapter.discover(proxyNode(owner), null).isEmpty(), "same physical Plus core cannot be counted twice");
        h.assertTrue(!sources.get(0).busy.getAsBoolean(), "idle Plus core is eligible");
        var cached = (List<?>)MigrationReflection.call(owner, "getAvailablePatterns");
        h.assertTrue(cached.size() == 2, "first and last physical slots are decoded by the real host");
        try (var mutations = PatternMigrationMutationScope.open()) {
            h.assertTrue(ItemStack.matches(inventory.extractItem(0, 1, false), encoded)
                    && ItemStack.matches(inventory.extractItem(71, 1, false), encoded),
                    "native physical extraction preserves both encoded items");
            h.assertTrue(cached.size() == 2, "Plus host catalog refresh is deferred within the migration slice");
        }
        h.assertTrue(cached.isEmpty() && inventory.isEmpty(), "catalog refresh completes and physical slots are empty");
        System.out.println("MIGRATION_EAEP_ACTUAL_JAR_PASS capacity=72 legacyForge=true");
        h.succeed();
    }

    @GameTest(templateNamespace="ae2lt_migration",template="migration_test",timeoutTicks=300)
    public static void ecoPhysicalSlotsAndSharedLeaseUseActualApi(GameTestHelper h) throws Exception {
        if(!net.minecraftforge.fml.ModList.get().isLoaded("neoecoae")){System.out.println("MIGRATION_ECO_SKIPPED");h.succeed();return;}
        var bus=place(h,new BlockPos(3,2,3),"neoecoae:crafting_pattern_bus");
        var controller=place(h,new BlockPos(4,2,3),"neoecoae:crafting_system_l4");
        var type=Class.forName("cn.dancingsnow.neoecoae.multiblock.cluster.NECraftingCluster");
        var cluster=type.getConstructor(BlockPos.class,BlockPos.class).newInstance(bus.getBlockPos(),controller.getBlockPos());
        setField(cluster,"controller",controller);setField(bus,"cluster",cluster);setField(controller,"cluster",cluster);
        var physical=(appeng.api.inventories.InternalInventory)MigrationReflection.call(bus,"getTerminalPatternInventory");
        var adapter=new PatternMigrationSources();var sources=adapter.discover(proxyNode(bus),null);
        h.assertTrue(sources.size()==1&&sources.get(0).inventory==physical&&sources.get(0).count==physical.size(),"F source uses actual physical view");
        var encoded = pattern(h, false);
        physical.setItemDirect(0, encoded.copy());
        var raw = (appeng.api.inventories.InternalInventory) MigrationReflection.field(bus, "inventory");
        h.assertTrue(ItemStack.matches(raw.getStackInSlot(0), encoded), "terminal view forwards to physical slots");
        var extracted = sources.get(0).inventory.extractItem(0, 1, false);
        h.assertTrue(ItemStack.matches(extracted, encoded) && raw.getStackInSlot(0).isEmpty(), "physical extraction keeps exact NBT");
        try(var lease=EcoMigrationLease.acquire(new Object(),new Object())) {
            h.assertTrue(lease!=null&&lease.owned(), "Forge ECO without a coordinator does not block LT migration");
        }
        System.out.println("MIGRATION_ECO_ACTUAL_JAR_PASS slots="+physical.size());h.succeed();
    }

    private static net.minecraft.world.level.block.entity.BlockEntity place(GameTestHelper h,BlockPos relative,String id){
        var block=net.minecraft.core.registries.BuiltInRegistries.BLOCK.get(new net.minecraft.resources.ResourceLocation(id));
        h.assertTrue(block!=net.minecraft.world.level.block.Blocks.AIR,"optional block exists: "+id);
        var pos=h.absolutePos(relative);h.getLevel().setBlockAndUpdate(pos,block.defaultBlockState());return h.getLevel().getBlockEntity(pos);
    }
    private static void setField(Object object,String name,Object value)throws Exception{
        Class<?> type=object.getClass();while(type!=null){try{var field=type.getDeclaredField(name);field.setAccessible(true);field.set(object,value);return;}
            catch(NoSuchFieldException ignored){type=type.getSuperclass();}}throw new NoSuchFieldException(name);
    }
    private static appeng.api.networking.IGridNode proxyNode(Object owner){
        return (appeng.api.networking.IGridNode)java.lang.reflect.Proxy.newProxyInstance(appeng.api.networking.IGridNode.class.getClassLoader(),
                new Class<?>[]{appeng.api.networking.IGridNode.class},(proxy,method,args)->method.getName().equals("getOwner")?owner:null);
    }

    private static class FakeStorageBusInventory implements appeng.api.storage.MEStorage {
        long inserted;
        public long insert(appeng.api.stacks.AEKey key,long amount,Actionable mode,IActionSource source){if(mode==Actionable.MODULATE)inserted+=amount;return amount;}
        public void getAvailableStacks(appeng.api.stacks.KeyCounter out){}
        public net.minecraft.network.chat.Component getDescription(){return net.minecraft.network.chat.Component.literal("unsafe test bus");}
    }
    private static class UnnamedInventory implements appeng.api.storage.MEStorage {
        long inserted;
        public long insert(appeng.api.stacks.AEKey key,long amount,Actionable mode,IActionSource source){if(mode==Actionable.MODULATE)inserted+=amount;return amount;}
        public void getAvailableStacks(appeng.api.stacks.KeyCounter out){}
        public net.minecraft.network.chat.Component getDescription(){return net.minecraft.network.chat.Component.literal("unregistered endpoint");}
    }

    private static ItemStack processingPattern(List<appeng.api.stacks.GenericStack> inputs,
            List<appeng.api.stacks.GenericStack> outputs) {
        return PatternDetailsHelper.encodeProcessingPattern(inputs.toArray(appeng.api.stacks.GenericStack[]::new),
                outputs.toArray(appeng.api.stacks.GenericStack[]::new));
    }

    public static ItemStack pattern(GameTestHelper h,boolean substitute){return pattern(h.getLevel(),substitute);}
    public static ItemStack pattern(net.minecraft.server.level.ServerLevel level,boolean substitute){
        var raw=level.getRecipeManager().byKey(new net.minecraft.resources.ResourceLocation("oak_planks")).orElseThrow();
        var recipe=(CraftingRecipe)raw;
        var input=new ItemStack[9];Arrays.fill(input,ItemStack.EMPTY);input[0]=new ItemStack(Items.OAK_LOG);
        return PatternDetailsHelper.encodeCraftingPattern(recipe,input,new ItemStack(Items.OAK_PLANKS,4),substitute,false);
    }
    private static long count(Fixture f,ItemStack item){
        long result=0;for(var storage:f.port.getPatternStorages())for(int i=0;i<storage.capacity();i++){
            var stack=storage.getInventory().getStackInSlot(i);if(ItemStack.isSameItemSameTags(stack,item))result+=stack.getCount();
        }return result;
    }
    private static void await(GameTestHelper h,Fixture f,Runnable checks){
        h.runAfterDelay(1,()->{
            var s=f.controller.getPatternMigration().snapshot();
            if(s.active()){await(h,f,checks);return;}
            h.assertTrue(s.stage()==PatternMigrationSnapshot.Stage.COMPLETE,"task completes: "+s);
            checks.run();
        });
    }
    private static void fixture(GameTestHelper h,java.util.function.Consumer<Fixture> ready){
        var f=build(h.getLevel(),h.absolutePos(new BlockPos(2,1,2)));
        h.runAfterDelay(50,()->{
            f.controller.scanAndForm(f.player);
            h.assertTrue(f.controller.isFormed(),"real multiblock forms: "+MatrixMultiblockScanner.scan(h.getLevel(),f.controller.getBlockPos(),Direction.EAST).issues());
            h.assertTrue(f.port.isLinkConnected(),"real target grid is powered and active");
            ready.accept(f);
        });
    }
    public record Fixture(MatrixControllerBlockEntity controller,MatrixPortBlockEntity port,
            PatternProviderBlockEntity provider,DriveBlockEntity drive,ServerPlayer player){}
    public static Fixture build(net.minecraft.server.level.ServerLevel level,BlockPos corner){
        var player=FakePlayerFactory.get(level,new GameProfile(UUID.randomUUID(),"MigrationTest"));register(player);
        BlockPos control=corner.offset(MatrixMultiblockTemplate.CONTROLLER_LOCAL);
        for(var entry:MatrixMultiblockTemplate.entries()){
            Block block=switch(entry.role()){
                case EMPTY -> net.minecraft.world.level.block.Blocks.AIR;
                case CASING -> ModBlocks.MATTER_WARPING_MATRIX_CASING.get();
                case CONSTRAINT_FRAME,PORT_CANDIDATE -> entry.localPos().equals(new BlockPos(6,5,3))?
                        ModBlocks.MATTER_WARPING_MATRIX_PORT.get():ModBlocks.MATTER_WARPING_MATRIX_CONSTRAINT_FRAME.get();
                case GLASS -> ModBlocks.MATTER_WARPING_MATRIX_GLASS.get();
                case CONTROLLER -> ModBlocks.MATTER_WARPING_MATRIX_CONTROLLER.get();
                case PATTERN_BAY -> entry.localPos().equals(new BlockPos(1,1,1)) || entry.localPos().equals(new BlockPos(2,1,1))?
                        ModBlocks.MATTER_WARPING_MATRIX_PATTERN_STORAGE_T1.get():net.minecraft.world.level.block.Blocks.AIR;
                case CRAFTING_BAY -> entry.localPos().equals(MatrixMultiblockTemplate.CRAFTING_CENTER_LOCAL)?
                        ModBlocks.MATTER_WARPING_MATRIX_MULTIDIMENSIONAL_MAIN_CORE.get():ModBlocks.TIANSHU_BLANK_UNIT.get();
            };
            var state=block.defaultBlockState();if(state.hasProperty(MatrixMultiblockDirectionalBlock.FACING))state=state.setValue(MatrixMultiblockDirectionalBlock.FACING,Direction.EAST);
            level.setBlockAndUpdate(corner.offset(entry.localPos()),state);
        }
        var controller=(MatrixControllerBlockEntity)level.getBlockEntity(control);
        controller.scanAndForm(player);
        var port=(MatrixPortBlockEntity)level.getBlockEntity(corner.offset(6,5,3));
        var cable=port.getBlockPos().east();
        PartHelper.setPart(level,cable,null,player,AEParts.GLASS_CABLE.item(AEColor.TRANSPARENT));
        level.setBlockAndUpdate(cable.below(),AEBlocks.CREATIVE_ENERGY_CELL.block().defaultBlockState());
        level.setBlockAndUpdate(cable.east(),AEBlocks.DRIVE.block().defaultBlockState());
        var drive=(DriveBlockEntity)level.getBlockEntity(cable.east());drive.getInternalInventory().setItemDirect(0,AEItems.ITEM_CELL_1K.stack());
        level.setBlockAndUpdate(cable.south(),AEBlocks.CRAFTING_STORAGE_1K.block().defaultBlockState());
        level.setBlockAndUpdate(cable.north(),AEBlocks.PATTERN_PROVIDER.block().defaultBlockState());
        var provider=(PatternProviderBlockEntity)level.getBlockEntity(cable.north());
        player.setPos(control.getX()+.5,control.getY(),control.getZ()+.5);
        return new Fixture(controller,port,provider,drive,player);
    }
    @SuppressWarnings("unchecked") public static void register(ServerPlayer player){
        try{
            var list=player.server.getPlayerList();var field=net.minecraft.server.players.PlayerList.class.getDeclaredField("playersByUUID");
            field.setAccessible(true);((Map<UUID,ServerPlayer>)field.get(list)).put(player.getUUID(),player);
        }catch(ReflectiveOperationException e){throw new AssertionError(e);}
    }
    @SuppressWarnings("unchecked") public static void unregister(ServerPlayer player){
        try{
            var list=player.server.getPlayerList();var field=net.minecraft.server.players.PlayerList.class.getDeclaredField("playersByUUID");
            field.setAccessible(true);((Map<UUID,ServerPlayer>)field.get(list)).remove(player.getUUID());
        }catch(ReflectiveOperationException e){throw new AssertionError(e);}
    }
    private static void done(GameTestHelper h,Fixture f){
        f.controller.getPatternMigration().stop(PatternMigrationSnapshot.Reason.CANCELLED);unregister(f.player);
        System.out.println("MIGRATION_GAMETEST_PASS");h.succeed();
    }
}
