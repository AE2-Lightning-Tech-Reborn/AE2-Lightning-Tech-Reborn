package qa;

import appeng.api.config.Actionable;
import appeng.api.networking.IGrid;
import appeng.api.networking.security.IActionSource;
import appeng.api.networking.storage.IStorageService;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.AEKey;
import appeng.api.storage.MEStorage;
import com.moakiee.ae2lt.blockentity.TianshuSupercomputerControllerBlockEntity;
import com.moakiee.ae2lt.crafting.big.*;
import com.moakiee.ae2lt.logic.persistence.ControllerMachineStateSavedData;
import com.moakiee.ae2lt.logic.persistence.ControllerMachineStateSavedData.MachineType;
import com.moakiee.ae2lt.registry.ModBlocks;
import com.moakiee.thunderbolt.core.crafting.big.BigExecutionProgram;
import java.lang.reflect.Proxy;
import java.math.BigInteger;
import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.*;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.*;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.ChestBlockEntity;

/** Real world chest and production controller SavedData across the restart probe's three JVMs.
 * The fixture supplies a deterministic Grid and injects only the external receipt failure. */
final class GtoRefundRestartQA {
    private static final BlockPos HOST=new BlockPos(8,100,8), CHEST=new BlockPos(8,100,4);
    private static final AEKey RAW=AEItemKey.of(Items.IRON_INGOT);
    private static final org.slf4j.Logger LOG=com.mojang.logging.LogUtils.getLogger();
    private static void require(boolean value,String message) { if(!value) throw new AssertionError(message); }

    static void prepare(ServerLevel level,CompoundTag checkpoint) throws Exception {
        level.setBlockAndUpdate(CHEST,Blocks.CHEST.defaultBlockState());
        var receiver=new Receiver((ChestBlockEntity)level.getBlockEntity(CHEST)); receiver.fail=true;
        var owner=UUID.randomUUID(); var host=host(level,owner,receiver);
        var job=new BigCraftingJob(AEItemKey.of(Items.GOLD_INGOT),BigInteger.ONE,BigExecutionProgram.of(List.of()));
        job.escrow.put(RAW,BigInteger.TEN); job.cancel();
        var tag=new CompoundTag();tag.put("job",job.save(level.registryAccess()));
        host.bigCrafting().load(tag,level.registryAccess());host.bigCrafting().tick();
        check(host.bigCrafting(),level,receiver,job.id);
        // Actual production removal persists under its controller UUID. The checkpoint stores
        // identifiers only; the job must be loaded from world SavedData in each later JVM.
        host.prepareForControllerRemoval();
        require(ControllerMachineStateSavedData.get(level).hasState(MachineType.TIANSHU,owner),"Removal discarded refund state");
        checkpoint.putUUID("refundOwner",owner);checkpoint.putUUID("refundJob",job.id);
        LOG.info("GTO_EXTENDED_QA REFUND_PREPARED owner={} job={} physical=2 unresolved=10 spendable=0",owner,job.id);
    }

    static void restore(ServerLevel level,CompoundTag checkpoint,String phase) throws Exception {
        var owner=checkpoint.getUUID("refundOwner"); var id=checkpoint.getUUID("refundJob");
        var saved=ControllerMachineStateSavedData.get(level);
        require(saved.hasState(MachineType.TIANSHU,owner),"Refund SavedData did not survive independent JVM");
        var receiver=new Receiver((ChestBlockEntity)level.getBlockEntity(CHEST));
        var host=host(level,owner,receiver);
        host.bigCrafting().load(saved.getState(MachineType.TIANSHU,owner).getCompound("bigCrafting"),level.registryAccess());
        host.bigCrafting().tick();host.bigCrafting().cancel();host.bigCrafting().toggleSuspended();
        check(host.bigCrafting(),level,receiver,id);
        host.prepareForControllerRemoval();host.bigCrafting().tick();
        check(host.bigCrafting(),level,receiver,id);
        LOG.info("GTO_EXTENDED_QA REFUND_RESTORED phase={} owner={} job={} physical=2 unresolved=10 spendable=0",phase,owner,id);
    }

    private static void check(BigCraftingService service,ServerLevel level,Receiver receiver,UUID id) {
        require(receiver.stored()==2,"Unknown refund was materialized after cancellation/removal/restart: "+receiver.stored());
        require(service.busy() && service.job().id.equals(id),"Unknown refund UUID/custodian was cleaned up");
        require(service.job().escrow.isEmpty(),"Unknown claim became spendable escrow");
        var encoded=service.save(level.registryAccess()).getCompound("job");
        require(encoded.getInt("version")==3,"Unknown refund was written with an unsafe legacy codec");
        require(BigStackCodec.read(encoded.getList("unsettledRefunds",Tag.TAG_COMPOUND),level.registryAccess())
                .equals(Map.of(RAW,BigInteger.TEN)),"Unresolved claim was duplicated or lost");
    }

    private static TianshuSupercomputerControllerBlockEntity host(ServerLevel level,UUID id,Receiver receiver) throws Exception {
        var storage=proxy(IStorageService.class,Map.of("getInventory",receiver));
        var grid=proxy(IGrid.class,Map.of("getStorageService",storage,"getActiveMachines",Set.of(),"getNodes",Set.of()));
        var host=new TianshuSupercomputerControllerBlockEntity(HOST,ModBlocks.TIANSHU_SUPERCOMPUTER_CONTROLLER.get().defaultBlockState()) {
            @Override public IGrid getGrid() { return grid; }
            @Override public IActionSource getActionSource() { return IActionSource.empty(); }
            @Override public void markCpuDirty() {}
        };
        host.setLevel(level);
        // Fixture setup establishes the same ownership invariants that a formed controller claims.
        for(var entry:Map.<String,Object>of("machineId",id,"loadedRuntimeId",id,"persistentStateOwner",true).entrySet()) {
            var field=TianshuSupercomputerControllerBlockEntity.class.getDeclaredField(entry.getKey());
            field.setAccessible(true);field.set(host,entry.getValue());
        }
        require(ControllerMachineStateSavedData.get(level).claim(MachineType.TIANSHU,id,level,HOST),"Refund controller owner conflicted");
        return host;
    }

    private static <T>T proxy(Class<T> type,Map<String,Object> values) {
        return type.cast(Proxy.newProxyInstance(type.getClassLoader(),new Class<?>[]{type},(p,m,a)->{
            if(values.containsKey(m.getName()))return values.get(m.getName());
            if(m.getReturnType()==boolean.class)return false;
            if(m.getReturnType()==long.class)return 0L;
            if(m.getReturnType()==int.class)return 0;
            return null;
        }));
    }

    private static final class Receiver implements MEStorage {
        final ChestBlockEntity chest; boolean fail;
        Receiver(ChestBlockEntity chest) { this.chest=Objects.requireNonNull(chest,"Persisted physical refund receiver missing"); }
        long stored() { return chest.getItem(0).getCount(); }
        public long insert(AEKey key,long amount,Actionable mode,IActionSource source) {
            require(key.equals(RAW),"Wrong refund key");
            long accepted=Math.min(amount,fail?2:10);
            if(mode==Actionable.MODULATE) {
                chest.setItem(0,new ItemStack(Items.IRON_INGOT,Math.toIntExact(stored()+accepted)));chest.setChanged();
                if(fail)throw new IllegalStateException("Injected missing external receipt after physical acceptance");
            }
            return accepted;
        }
        public long extract(AEKey key,long amount,Actionable mode,IActionSource source) { return 0; }
        public Component getDescription() { return Component.literal("Persisted external refund chest"); }
    }
}
