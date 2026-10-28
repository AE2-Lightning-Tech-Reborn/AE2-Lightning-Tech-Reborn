package qa;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import appeng.menu.MenuOpener;
import appeng.menu.locator.MenuLocators;
import com.moakiee.ae2lt.blockentity.OverloadedPatternProviderBlockEntity;
import com.moakiee.ae2lt.menu.OverloadedPatternProviderMenu;
import com.moakiee.ae2lt.registry.ModBlocks;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.GameType;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import com.mojang.brigadier.arguments.IntegerArgumentType;

/** Two real TCP clients must independently observe each server-side state transition. */
@Mod.EventBusSubscriber(modid="gto_compat_qa")
public final class GtoMultiplayerServerQA {
    private static final org.slf4j.Logger LOG=com.mojang.logging.LogUtils.getLogger();
    private static final BlockPos POS=new BlockPos(0,100,0);
    private static final Map<Integer,Set<UUID>> ACKS=new HashMap<>();
    private static CompletableFuture<?> chunk;
    private static OverloadedPatternProviderBlockEntity provider;
    private static int phase,ticks;
    private static boolean done;
    private static boolean enabled(){return "multiplayer".equals(System.getProperty("ae2lt.extendedQa"));}
    private static void require(boolean ok,String message){if(!ok)throw new AssertionError(message);}
    @SubscribeEvent public static void commands(RegisterCommandsEvent event){
        if(!enabled())return;
        event.getDispatcher().register(Commands.literal("ltqa_ack").then(Commands.argument("stage",IntegerArgumentType.integer(0,3)).executes(ctx->{
            var p=ctx.getSource().getPlayerOrException();int stage=IntegerArgumentType.getInteger(ctx,"stage");
            try{
                require(p.getGameProfile().getName().equals("GtoQA1")||p.getGameProfile().getName().equals("GtoQA2"),"unexpected test identity");
                require(p.containerMenu instanceof OverloadedPatternProviderMenu,"ACK did not originate from an actual open LT menu");
                var menu=(OverloadedPatternProviderMenu)p.containerMenu;
                boolean expected=stage!=1;
                require(menu.isMachineAdaptationEnabled()==expected&&provider.isMachineAdaptationEnabled()==expected,"client/server menu state disagreed");
                ACKS.computeIfAbsent(stage,k->new HashSet<>()).add(p.getUUID());
                LOG.info("GTO_EXTENDED_QA MP_ACK player={} uuid={} stage={} adaptation={}",p.getGameProfile().getName(),p.getUUID(),stage,expected);
            }catch(Throwable failure){done=true;LOG.error("GTO_EXTENDED_QA FAIL multiplayer ACK",failure);}
            return 1;
        })));
    }
    private static void open(ServerPlayer p){
        p.setGameMode(GameType.CREATIVE);p.teleportTo(0.5,101,2.5);
        require(MenuOpener.open(OverloadedPatternProviderMenu.TYPE,p,MenuLocators.forBlockEntity(provider)),"dedicated server rejected actual menu");
    }
    private static boolean both(int stage){return ACKS.getOrDefault(stage,Set.of()).size()==2;}
    private static void broadcast(net.minecraft.server.MinecraftServer server,String action){
        for(var p:server.getPlayerList().getPlayers())p.sendSystemMessage(Component.literal("GTO_MP_"+action));
    }
    @SubscribeEvent public static void tick(TickEvent.ServerTickEvent event){
        if(event.phase!=TickEvent.Phase.END||!enabled()||done)return;
        var server=event.getServer();var level=server.overworld();
        try{
            if(++ticks>10000)throw new AssertionError("two-player GUI session timed out phase="+phase);
            switch(phase){
                case 0->{
                    var pos=new net.minecraft.world.level.ChunkPos(POS);
                    level.getChunkSource().addRegionTicket(net.minecraft.server.level.TicketType.FORCED,pos,2,pos);
                    chunk=level.getChunkSource().getChunkFuture(pos.x,pos.z,net.minecraft.world.level.chunk.ChunkStatus.FULL,true);phase++;
                }
                case 1->{
                    if(!chunk.isDone())return;chunk.join();
                    level.setBlockAndUpdate(POS,ModBlocks.OVERLOADED_PATTERN_PROVIDER.get().defaultBlockState());
                    provider=(OverloadedPatternProviderBlockEntity)level.getBlockEntity(POS);
                    provider.setMachineAdaptationEnabled(true);provider.setProviderMode(OverloadedPatternProviderBlockEntity.ProviderMode.WIRELESS);phase++;
                }
                case 2->{
                    var players=server.getPlayerList().getPlayers();if(players.size()<2)return;
                    require(players.size()==2,"unexpected extra player in isolated server");
                    for(var p:players){
                        require(p.getUUID().equals(UUID.nameUUIDFromBytes(("OfflinePlayer:"+p.getGameProfile().getName()).getBytes(java.nio.charset.StandardCharsets.UTF_8))),"offline UUID mismatch");
                        open(p);
                    }
                    require(!players.get(0).getUUID().equals(players.get(1).getUUID()),"clients reused the same identity");phase++;
                }
                case 3->{if(both(0)){broadcast(server,"GO_OFF");phase++;}}
                case 4->{if(both(1)){broadcast(server,"GO_ON");phase++;}}
                case 5->{if(both(2)){for(var p:server.getPlayerList().getPlayers()){p.closeContainer();open(p);}phase++;}}
                case 6->{if(both(3)){
                    require(server.getPlayerList().getPlayerCount()==2,"clients were not simultaneously connected");
                    LOG.info("GTO_EXTENDED_QA PASS multiplayer clients=2 distinctOfflineIdentities=true actualAdvancedScreen=true C2S=off/on GuiSyncBoth=true freshContainerReopen=true");
                    broadcast(server,"PASS");done=true;
                }}
                default->throw new AssertionError("unknown multiplayer phase "+phase);
            }
        }catch(Throwable failure){done=true;LOG.error("GTO_EXTENDED_QA FAIL dedicated multiplayer phase="+phase,failure);broadcast(server,"FAIL");}
    }
}
