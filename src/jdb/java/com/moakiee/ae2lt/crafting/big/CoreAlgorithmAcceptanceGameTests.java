package com.moakiee.ae2lt.crafting.big;

import appeng.api.config.Actionable;
import appeng.api.crafting.IPatternDetails;
import appeng.api.networking.crafting.ICraftingService;
import appeng.api.networking.security.IActionSource;
import appeng.api.networking.storage.IStorageService;
import appeng.api.stacks.*;
import appeng.api.storage.MEStorage;
import com.moakiee.thunderbolt.api.crafting.*;
import com.moakiee.thunderbolt.compat.gto.inv.NetworkCraftingSimulationState;
import com.moakiee.thunderbolt.core.crafting.big.BigExecutionProgram;
import com.moakiee.thunderbolt.core.crafting.planner.FastCraftingPlanner;
import com.moakiee.thunderbolt.core.crafting.planner.PlanningCancellation;
import java.lang.reflect.Proxy;
import java.math.BigInteger;
import java.util.*;
import java.util.concurrent.CancellationException;
import java.util.concurrent.TimeUnit;
import net.minecraft.gametest.framework.*;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraftforge.gametest.*;

/** Actual Forge/Core/LT code with explicitly fault-injected external MEStorage receipts. */
@GameTestHolder("ae2lt_core5")
@PrefixGameTestTemplate(false)
public final class CoreAlgorithmAcceptanceGameTests {
    private static AEKey raw() { return AEItemKey.of(Items.IRON_INGOT); }
    private static AEKey output() { return AEItemKey.of(Items.GOLD_INGOT); }
    private static void require(boolean value, String message) { if (!value) throw new AssertionError(message); }

    @GameTest(template="empty", templateNamespace="ae2lt_global_batch", timeoutTicks=80)
    public static void planningCutoffDeclinesInsteadOfInventingMissingMaterials(GameTestHelper h) {
        var outputs = new GenericStack[66_000];
        outputs[0] = new GenericStack(output(), 1);
        for (int i=1; i<outputs.length; i++) {
            var item = Items.IRON_INGOT.getDefaultInstance();
            var tag = new CompoundTag(); tag.putInt("core5_variant", i); item.setTag(tag);
            outputs[i] = new GenericStack(AEItemKey.of(item), 1);
        }
        var pattern = pattern(new IPatternDetails.IInput[0], outputs);
        // Independent witness: no inputs, and the first output is the requested target.
        require(pattern.getInputs().length==0 && pattern.getPrimaryOutput().what().equals(output()), "Invalid witness");
        var crafting = crafting(pattern);
        for (boolean simulation : new boolean[]{false,true}) {
            long deadline = System.nanoTime()+TimeUnit.SECONDS.toNanos(3);
            try (var scope = PlanningCancellation.bind(context(deadline))) {
                var result=FastCraftingPlanner.tryAttempt(crafting, inventory(Map.of()), h.getLevel(), output(),1,simulation);
                require(!result.handled() && result.plan()==null && result.simulationFallback()==null,
                        "Incomplete actual export became a definitive missing-material plan");
            }
        }
        h.succeed();
    }

    @GameTest(template="empty", templateNamespace="ae2lt_global_batch", timeoutTicks=80)
    public static void planningCompleteMissingAndExecutableControlsRemainDistinct(GameTestHelper h) {
        var input = new IPatternDetails.IInput() {
            public GenericStack[] getPossibleInputs() { return new GenericStack[]{new GenericStack(raw(),1)}; }
            public long getMultiplier() { return 1; }
            public boolean isValid(AEKey key, net.minecraft.world.level.Level level) { return raw().equals(key); }
            public AEKey getRemainingKey(AEKey key) { return null; }
        };
        var crafting=crafting(pattern(new IPatternDetails.IInput[]{input},new GenericStack[]{new GenericStack(output(),1)}));
        try(var scope=PlanningCancellation.bind(context(System.nanoTime()+TimeUnit.SECONDS.toNanos(3)))) {
            var missing=FastCraftingPlanner.tryAttempt(crafting,inventory(Map.of()),h.getLevel(),output(),1,false);
            require(missing.handled() && missing.plan()==null && missing.simulationFallback()!=null,"Complete missing control lost fallback");
        }
        try(var scope=PlanningCancellation.bind(context(System.nanoTime()+TimeUnit.SECONDS.toNanos(3)))) {
            var ready=FastCraftingPlanner.tryAttempt(crafting,inventory(Map.of(raw(),1L)),h.getLevel(),output(),1,false);
            require(ready.handled() && ready.plan()!=null,"Certified feasible control did not produce a plan");
        }
        h.succeed();
    }

    @GameTest(template="empty", templateNamespace="ae2lt_global_batch", timeoutTicks=40)
    public static void ownershipRejectedRefundRemainsOwnedAcrossSaveAndRetry(GameTestHelper h) {
        refundWithReceipt(h,0);
    }

    @GameTest(template="empty", templateNamespace="ae2lt_global_batch", timeoutTicks=40)
    public static void ownershipPartialRefundRetainsOnlyTheUnacceptedRemainder(GameTestHelper h) {
        refundWithReceipt(h,3);
    }

    private static void refundWithReceipt(GameTestHelper h,long accepted) {
        var fixture=new BigServiceTestFixture(h.getLevel());
        var receiver=new ReceiptStorage(h); receiver.quota=accepted;
        fixture.gridValues.put("getStorageService",BigServiceTestFixture.proxy(IStorageService.class,Map.of("getInventory",receiver)));
        fixture.load(returningJob());
        var id=fixture.service.job().id;
        fixture.service.tick();
        require(receiver.stored()==accepted,"External accepted receipt changed");
        require(fixture.service.job().escrow.get(raw()).equals(BigInteger.valueOf(10-accepted)),"Unaccepted refund lost its custodian");
        var saved=fixture.service.save(h.getLevel().registryAccess());
        var restored=BigServiceTestFixture.serviceFor(h.getLevel(),fixture.grid);
        restored.load(saved,h.getLevel().registryAccess());
        require(restored.job().id.equals(id),"Refund job UUID changed");
        receiver.quota=10;
        h.runAfterDelay(1,()->{
            try {
                restored.tick();
                require(restored.job()==null && receiver.stored()==10,"Refund save/retry duplicated or lost the remainder");
                h.succeed();
            } catch(Throwable failure) { h.fail(failure.toString()); }
        });
    }

    @GameTest(template="empty", templateNamespace="ae2lt_global_batch", timeoutTicks=40)
    public static void ownershipUnknownRefundMustNotReplayAfterSave(GameTestHelper h) {
        var fixture=new BigServiceTestFixture(h.getLevel());
        var receiver=new ReceiptStorage(h); receiver.quota=2; receiver.throwAfterAccept=true;
        fixture.gridValues.put("getStorageService",BigServiceTestFixture.proxy(IStorageService.class,Map.of("getInventory",receiver)));
        fixture.load(returningJob());
        try { fixture.service.tick(); } catch (IllegalStateException expected) {
            require(expected.getMessage().equals("receipt unavailable after acceptance"),"Unrelated failure");
        }
        require(receiver.stored()==2,"Fault injection did not transfer the intended partial amount");
        var id=fixture.service.job().id;
        require(fixture.service.job().escrow.isEmpty(),"Unknown refund was left spendable");
        require(fixture.service.job().unsettledRefunds.equals(Map.of(raw(),BigInteger.TEN)),"Unknown refund lost its sole unresolved claim");
        var saved=fixture.service.save(h.getLevel().registryAccess());
        var restored=BigServiceTestFixture.serviceFor(h.getLevel(),fixture.grid);
        restored.load(saved,h.getLevel().registryAccess());
        receiver.throwAfterAccept=false; receiver.quota=10;
        h.runAfterDelay(1,()->{
            try {
                restored.tick();
                restored.cancel(); restored.toggleSuspended(); restored.tick();
                require(receiver.stored()==2,"UNKNOWN external refund was blindly replayed after NBT restore: initial=10, actual="+receiver.stored());
                require(restored.busy() && restored.job().id.equals(id),"Unknown refund job was cleaned up or replaced");
                require(restored.job().escrow.isEmpty() && restored.job().unsettledRefunds.equals(Map.of(raw(),BigInteger.TEN)),"Cancellation materialized or discarded the unresolved claim");
                require(restored.save(h.getLevel().registryAccess()).getCompound("job").getInt("version")==3,"Quarantine must not be silently readable by an old refund-replaying codec");
                h.succeed();
            } catch(Throwable failure) { h.fail(failure.toString()); }
        });
    }

    @GameTest(template="empty", templateNamespace="ae2lt_global_batch", timeoutTicks=40)
    public static void ownershipSnapshotInsideStorageCallCannotReplayTheInFlightOffer(GameTestHelper h) {
        var fixture=new BigServiceTestFixture(h.getLevel()); var receiver=new ReceiptStorage(h); receiver.quota=10;
        fixture.gridValues.put("getStorageService",BigServiceTestFixture.proxy(IStorageService.class,Map.of("getInventory",receiver)));
        fixture.load(returningJob());
        var snapshot=new java.util.concurrent.atomic.AtomicReference<CompoundTag>();
        receiver.beforeInsert=()->snapshot.set(fixture.service.save(h.getLevel().registryAccess()));
        fixture.service.tick();
        require(fixture.service.job()==null && receiver.stored()==10,"Known full receipt did not close original refund");
        var restored=BigServiceTestFixture.serviceFor(h.getLevel(),fixture.grid);
        restored.load(Objects.requireNonNull(snapshot.get()),h.getLevel().registryAccess());
        restored.tick(); restored.cancel();
        require(receiver.stored()==10 && restored.busy(),"Snapshot captured during external call replayed its offer");
        require(restored.job().escrow.isEmpty() && restored.job().unsettledRefunds.equals(Map.of(raw(),BigInteger.TEN)),"In-flight snapshot became spendable inventory");
        h.succeed();
    }

    @GameTest(template="empty", templateNamespace="ae2lt_global_batch", timeoutTicks=40)
    public static void ownershipLegacyRefundVersionsStillReturnKnownOwnedInputs(GameTestHelper h) {
        var fixture=new BigServiceTestFixture(h.getLevel()); var receiver=new ReceiptStorage(h);
        fixture.gridValues.put("getStorageService",BigServiceTestFixture.proxy(IStorageService.class,Map.of("getInventory",receiver)));
        for(int version:new int[]{1,2}) {
            var tag=new CompoundTag(); var encoded=returningJob().save(h.getLevel().registryAccess());
            encoded.putInt("version",version); tag.put("job",encoded);
            receiver.chest.clearContent(); receiver.quota=10;
            fixture.service.load(tag,h.getLevel().registryAccess()); fixture.service.tick();
            require(fixture.service.job()==null && receiver.stored()==10,"Legacy known refund failed: "+version);
        }
        h.succeed();
    }

    private static BigCraftingJob returningJob() {
        var job=new BigCraftingJob(output(),BigInteger.ONE,BigExecutionProgram.of(List.of()));
        job.escrow.put(raw(),BigInteger.TEN); job.cancel(); return job;
    }

    private static final class ReceiptStorage implements MEStorage {
        final ChestBlockEntity chest;
        long quota;
        boolean throwAfterAccept;
        Runnable beforeInsert=()->{};
        ReceiptStorage(GameTestHelper h) {
            var pos=new BlockPos(1,2,1); h.setBlock(pos,Blocks.CHEST);
            chest=(ChestBlockEntity)h.getBlockEntity(pos);
            require(chest!=null && chest.isEmpty(),"Physical refund receiver was not empty");
        }
        long stored() { return chest.getItem(0).getCount(); }
        public long insert(AEKey key,long amount,Actionable mode,IActionSource source) {
            require(key.equals(raw()),"Wrong external refund key");
            long accepted=Math.min(amount,quota);
            if(mode==Actionable.MODULATE) {
                beforeInsert.run();
                chest.setItem(0,new ItemStack(Items.IRON_INGOT,Math.toIntExact(stored()+accepted)));
                chest.setChanged(); quota-=accepted;
                if(throwAfterAccept) throw new IllegalStateException("receipt unavailable after acceptance");
            }
            return accepted;
        }
        public long extract(AEKey key,long amount,Actionable mode,IActionSource source) { return 0; }
        public Component getDescription() { return Component.literal("Fault-injected receipt receiver"); }
    }

    private static PlanningAttemptContext context(long deadline) {
        return new PlanningAttemptContext() {
            public long deadlineNanos() { return deadline; }
            public void checkpoint() { if(Thread.currentThread().isInterrupted() || System.nanoTime()-deadline>=0) throw new CancellationException("same absolute deadline"); }
            public void report(PlanningDiagnosticSnapshot snapshot) {}
        };
    }

    private static ICraftingService crafting(IPatternDetails pattern) {
        return (ICraftingService)Proxy.newProxyInstance(ICraftingService.class.getClassLoader(),new Class<?>[]{ICraftingService.class},
                (p,m,a)->switch(m.getName()) {
                    case "getCraftingFor" -> output().equals(a[0]) ? List.of(pattern) : List.of();
                    case "getCraftables" -> Set.of(output());
                    case "canEmitFor" -> false;
                    default -> BigServiceTestFixture.defaultValue(m.getReturnType());
                });
    }

    private static NetworkCraftingSimulationState inventory(Map<AEKey,Long> available) {
        var counter=new KeyCounter(); available.forEach(counter::add);
        MEStorage storage=new MEStorage() {
            public long insert(AEKey k,long n,Actionable a,IActionSource s) { throw new AssertionError("Planning mutated storage"); }
            public long extract(AEKey k,long n,Actionable a,IActionSource s) {
                require(a==Actionable.SIMULATE,"Planning extracted owned input"); return Math.min(n,available.getOrDefault(k,0L));
            }
            public Component getDescription() { return Component.literal("Planning snapshot"); }
        };
        return new NetworkCraftingSimulationState(BigServiceTestFixture.proxy(IStorageService.class,
                Map.of("getInventory",storage,"getCachedInventory",counter)),IActionSource.empty());
    }

    private static IPatternDetails pattern(IPatternDetails.IInput[] inputs, GenericStack[] outputs) {
        return new IPatternDetails() {
            public AEItemKey getDefinition() { return AEItemKey.of(Items.PAPER); }
            public IInput[] getInputs() { return inputs; }
            public GenericStack[] getOutputs() { return outputs; }
            public boolean supportsPushInputsToExternalInventory() { return false; }
        };
    }
}
