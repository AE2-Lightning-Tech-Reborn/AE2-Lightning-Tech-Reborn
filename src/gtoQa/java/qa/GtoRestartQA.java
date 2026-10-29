package qa;

import java.nio.file.Files;
import java.util.Map;
import java.util.UUID;
import appeng.api.config.Actionable;
import appeng.api.crafting.PatternDetailsHelper;
import appeng.api.networking.security.IActionSource;
import appeng.api.stacks.*;
import appeng.block.crafting.PatternProviderBlock;
import appeng.block.crafting.PushDirection;
import appeng.blockentity.crafting.CraftingBlockEntity;
import com.moakiee.ae2lt.blockentity.OverloadedPatternProviderBlockEntity;
import appeng.blockentity.storage.DriveBlockEntity;
import appeng.core.definitions.AEBlocks;
import appeng.crafting.CraftingPlan;
import com.moakiee.ae2lt.compat.gto.GtoCompatibleCraftingCpuLogic;
import com.moakiee.ae2lt.overload.pattern.PatternConversionService;
import com.moakiee.ae2lt.overload.runtime.model.*;
import com.moakiee.ae2lt.overload.runtime.pattern.OverloadPatternSupport;
import com.moakiee.ae2lt.registry.ModItems;
import com.moakiee.ae2lt.registry.ModBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtIo;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.level.storage.LevelResource;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import it.unimi.dsi.fastutil.objects.Object2LongOpenHashMap;

/** Three real JVMs, one world. Production block entities own all job and material persistence. */
@Mod.EventBusSubscriber(modid = "gto_compat_qa")
public final class GtoRestartQA {
    private static final org.slf4j.Logger LOG = com.mojang.logging.LogUtils.getLogger();
    private static final BlockPos CPU = new BlockPos(0,100,0), DRIVE = CPU.east(), PROVIDER = CPU.east(2), CHEST = CPU.east(3);
    private static final AEItemKey RAW = AEItemKey.of(Items.IRON_INGOT);
    private static int ticks, settled;
    private static boolean done, submitted;
    private static boolean chunksReady;
    private static final java.util.List<java.util.concurrent.CompletableFuture<?>> chunks = new java.util.ArrayList<>();
    private static long processed;

    private static AEItemKey variant(String name) {
        var stack = new ItemStack(Items.DIAMOND); stack.setHoverName(Component.literal(name));
        return AEItemKey.of(stack);
    }
    private static void require(boolean value, String message) { if (!value) throw new AssertionError(message); }
    @SubscribeEvent public static void tick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || done || !"restart".equals(System.getProperty("ae2lt.extendedQa"))) return;
        var server = event.getServer(); var level = server.overworld();
        try {
            appeng.hooks.ticking.TickHandler.instance().playerJoined = true;
            if (++ticks == 1) {
                for (int x=-1;x<=1;x++) for(int z=-1;z<=1;z++) {
                    var pos = new net.minecraft.world.level.ChunkPos(x,z);
                    level.getChunkSource().addRegionTicket(net.minecraft.server.level.TicketType.FORCED,pos,2,pos);
                    chunks.add(level.getChunkSource().getChunkFuture(x,z,net.minecraft.world.level.chunk.ChunkStatus.FULL,true));
                }
                return;
            }
            if (!chunksReady) {
                if(chunks.stream().anyMatch(f -> !f.isDone())) return;
                chunks.forEach(java.util.concurrent.CompletableFuture::join);
                chunksReady = true;
                for (int x=-1;x<=1;x++) for(int z=-1;z<=1;z++) level.setChunkForced(x,z,true);
                if ("prepare".equals(System.getProperty("ae2lt.extendedPhase"))) {
                    level.setBlockAndUpdate(CPU, AEBlocks.CRAFTING_STORAGE_64K.block().defaultBlockState());
                    level.setBlockAndUpdate(CPU.below(), AEBlocks.CREATIVE_ENERGY_CELL.block().defaultBlockState());
                    level.setBlockAndUpdate(DRIVE, AEBlocks.DRIVE.block().defaultBlockState());
                    level.setBlockAndUpdate(PROVIDER, ModBlocks.OVERLOADED_PATTERN_PROVIDER.get().defaultBlockState()
                            .setValue(PatternProviderBlock.PUSH_DIRECTION, PushDirection.EAST));
                    level.setBlockAndUpdate(CHEST, Blocks.CHEST.defaultBlockState());
                    ((DriveBlockEntity)level.getBlockEntity(DRIVE)).getInternalInventory().setItemDirect(0,
                            new ItemStack(ModItems.INFINITE_STORAGE_CELL.get()));
                }
                LOG.info("GTO_EXTENDED_QA restart chunks ready; phase={}",System.getProperty("ae2lt.extendedPhase"));
            }
            require(ticks < 1000, "real JVM restart timed out; phase=" + System.getProperty("ae2lt.extendedPhase"));
            if (ticks < 80) return;
            var block = (CraftingBlockEntity) level.getBlockEntity(CPU);
            var cpu = block.getCluster();
            require(cpu != null && cpu.isActive(), "saved physical CPU/grid did not become active");
            var grid = cpu.getGrid(); var inventory = grid.getStorageService().getInventory();
            var logic = (GtoCompatibleCraftingCpuLogic)cpu.craftingLogic;
            var chest = (ChestBlockEntity)level.getBlockEntity(CHEST);
            var checkpoint = server.getWorldPath(LevelResource.ROOT).resolve("gto-restart-qa.nbt").toFile();
            var phase = System.getProperty("ae2lt.extendedPhase");
            if ("prepare".equals(phase)) {
                if (!submitted) {
                    var source = PatternDetailsHelper.encodeProcessingPattern(new GenericStack[]{new GenericStack(RAW,1)},
                            new GenericStack[]{new GenericStack(variant("expected"),1)});
                    var parsed = OverloadPatternSupport.toParsedDefinition(source, PatternDetailsHelper.decodePattern(source,level));
                    var encoded = new PatternConversionService().createOverloadPatternStack(ModItems.OVERLOAD_PATTERN.get(),parsed,
                            EncodedOverloadPattern.builder().input(0,MatchMode.STRICT).output(0,MatchMode.ID_ONLY).build());
                    var provider = (OverloadedPatternProviderBlockEntity)level.getBlockEntity(PROVIDER);
                    provider.getExposedPatternInventory().setItemDirect(0,encoded);
                    provider.getLogic().updatePatterns();
                    var pattern = PatternDetailsHelper.decodePattern(encoded,level);
                    require(inventory.insert(RAW,3,Actionable.MODULATE,IActionSource.empty())==3,"actual disk stock rejected");
                    var used = new KeyCounter();used.add(RAW,3);
                    var plan = new CraftingPlan(new GenericStack(variant("expected"),3),1,false,false,used,new KeyCounter(),new KeyCounter(),
                            new Object2LongOpenHashMap<>(Map.of(pattern,3L)));
                    require(logic.trySubmitJob(grid,plan,IActionSource.empty(),null).successful(),"physical LT job admission failed");
                    require(logic.usesLt(),"job bypassed LT executor");
                    LOG.info("GTO_EXTENDED_QA restart submitted activeProvider={} publishedPatterns={}",
                            provider.getMainNode().isActive(), provider.getLogic().getAvailablePatterns().size());
                    submitted = true;
                    return;
                }
                long inMachine=0;
                for(int slot=0;slot<chest.getContainerSize();slot++) if(chest.getItem(slot).is(Items.IRON_INGOT)) inMachine+=chest.getItem(slot).getCount();
                if(inMachine==0){
                    if(ticks%100==0) LOG.info("GTO_EXTENDED_QA restart pending hasJob={} stored={} waiting={} remaining={}",
                            logic.hasJob(),logic.getStored(RAW),logic.getWaitingFor(variant("expected")),logic.getRemainingOutputAmount());
                    return;
                }
                require(logic.hasJob() && logic.getWaitingFor(variant("expected"))>0,"job did not reach a real pending machine output");
                var data = new CompoundTag();data.putUUID("craftId",logic.getLastLink().getCraftingID());
                data.putLong("inMachineAtCheckpoint",inMachine);data.putBoolean("finished",false);
                NbtIo.writeCompressed(data,checkpoint);block.setChanged();chest.setChanged();
                LOG.info("GTO_EXTENDED_QA READY_TO_STOP phase=prepare craftId={} machineInputs={} remaining={} waiting={}",
                        logic.getLastLink().getCraftingID(),inMachine,logic.getRemainingOutputAmount(),logic.getWaitingFor(variant("expected")));
                done=true;
            } else if("resume".equals(phase)) {
                var data=NbtIo.readCompressed(checkpoint);
                if(!submitted) {
                    require(!data.getBoolean("finished"),"fixture already completed before second JVM");
                    require(logic.hasJob() && logic.usesLt(),"running LT job was not restored from disk");
                    require(logic.getLastLink().getCraftingID().equals(data.getUUID("craftId")),"crafting identity changed across JVMs");
                    require(logic.getRemainingOutputAmount()==3,"restored remaining amount changed");
                    LOG.info("GTO_EXTENDED_QA RESTORED craftId={} remaining={} waiting={}",data.getUUID("craftId"),logic.getRemainingOutputAmount(),logic.getWaitingFor(variant("expected")));
                    submitted=true;
                }
                // The chest represents an external processor. Consume each delivered input once, then
                // return the transformed item through AE2's normal storage/crafting route.
                for(int slot=0;slot<chest.getContainerSize();slot++) {
                    var stack=chest.getItem(slot);
                    if(stack.is(Items.IRON_INGOT)) {
                        int count=stack.getCount();chest.setItem(slot,ItemStack.EMPTY);chest.setChanged();processed+=count;
                        require(processed<=3,"restart duplicated input dispatch");
                        require(inventory.insert(variant("actual"),count,Actionable.MODULATE,IActionSource.empty())==count,"machine output return rejected");
                    }
                }
                if(logic.hasJob()){settled=0;return;}
                if(++settled<40)return;
                require(processed==3,"completion did not consume all original inputs");
                require(inventory.extract(variant("actual"),10,Actionable.SIMULATE,IActionSource.empty())==3,"finished outputs missing or duplicated");
                require(inventory.extract(RAW,10,Actionable.SIMULATE,IActionSource.empty())==0,"original inputs were refunded twice");
                data.putBoolean("finished",true);data.putLong("processed",processed);NbtIo.writeCompressed(data,checkpoint);
                LOG.info("GTO_EXTENDED_QA PASS phase=resume sameCraftId={} consumed=3 delivered=3",data.getUUID("craftId"));done=true;
            } else if("verify".equals(phase)) {
                if(++settled<80)return;
                var data=NbtIo.readCompressed(checkpoint);
                require(data.getBoolean("finished") && data.getLong("processed")==3,"completion checkpoint missing");
                require(!logic.hasJob(),"third JVM resurrected completed job");
                require(inventory.extract(variant("actual"),10,Actionable.SIMULATE,IActionSource.empty())==3,"third JVM changed exact output count");
                require(inventory.extract(RAW,10,Actionable.SIMULATE,IActionSource.empty())==0 && chest.isEmpty(),"third JVM duplicated input or external task");
                LOG.info("GTO_EXTENDED_QA PASS phase=verify completed job stayed terminal; disk outputs=3; raw=0; processor empty");done=true;
            } else throw new AssertionError("Unknown restart phase "+phase);
        } catch(Throwable failure) {done=true;LOG.error("GTO_EXTENDED_QA FAIL real JVM recovery",failure);}
    }
}
