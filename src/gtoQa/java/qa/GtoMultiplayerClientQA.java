package qa;

import java.nio.file.*;
import com.moakiee.ae2lt.client.provider.OverloadedPatternProviderScreen;
import com.moakiee.ae2lt.client.provider.OverloadedPatternProviderAdvancedScreen;
import com.moakiee.ae2lt.menu.OverloadedPatternProviderMenu;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ClientChatReceivedEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid="gto_compat_qa",value=Dist.CLIENT)
public final class GtoMultiplayerClientQA {
    private static boolean done,off,on,passed;
    private static int phase,ticks;
    private static boolean enabled(){return Boolean.getBoolean("ae2lt.multiplayerProbe");}
    @SubscribeEvent public static void message(ClientChatReceivedEvent event){
        if(!enabled())return;var s=event.getMessage().getString();
        if(s.equals("GTO_MP_GO_OFF"))off=true;
        if(s.equals("GTO_MP_GO_ON"))on=true;
        if(s.equals("GTO_MP_PASS"))passed=true;
        if(s.equals("GTO_MP_FAIL"))fail(new AssertionError("dedicated server probe failed"));
    }
    private static void require(boolean ok,String message){if(!ok)throw new AssertionError(message);}
    private static void ack(int stage){Minecraft.getInstance().player.connection.sendCommand("ltqa_ack "+stage);}
    private static void click(String name)throws Exception{
        var mc=Minecraft.getInstance();var field=mc.screen.getClass().getDeclaredField(name);field.setAccessible(true);
        var button=(AbstractWidget)field.get(mc.screen);require(button.visible&&button.active,"real GUI button unavailable "+name);
        require(mc.screen.mouseClicked(button.getX()+button.getWidth()/2.0,button.getY()+button.getHeight()/2.0,0),"real GUI did not accept click");
        mc.screen.mouseReleased(button.getX()+button.getWidth()/2.0,button.getY()+button.getHeight()/2.0,0);
    }
    @SubscribeEvent public static void tick(TickEvent.ClientTickEvent event){
        if(event.phase!=TickEvent.Phase.END||!enabled()||done)return;var mc=Minecraft.getInstance();
        mc.options.pauseOnLostFocus=false;if(mc.player==null||mc.level==null)return;
        try{
            require(++ticks<5000,"multiplayer GUI timed out phase="+phase);
            if (phase == 7) {
                if (GtoCraftConfirmClientQA.tick(mc)) finish(mc);
                return;
            }
            if(mc.screen instanceof OverloadedPatternProviderScreen<?>){click("advancedSettingsButton");return;}
            if(!(mc.screen instanceof OverloadedPatternProviderAdvancedScreen<?>))return;
            var menu=(OverloadedPatternProviderMenu)mc.player.containerMenu;boolean writer=mc.player.getGameProfile().getName().equals("GtoQA1");
            switch(phase){
                case 0->{require(menu.isMachineAdaptationEnabled(),"initial menu state incorrect");ack(0);phase++;}
                case 1->{if(off){if(writer)click("machineAdaptationButton");phase++;}}
                case 2->{if(!menu.isMachineAdaptationEnabled()){ack(1);Screenshot.grab(mc.gameDirectory,"multiplayer-off.png",mc.getMainRenderTarget(),m->{});phase++;}}
                case 3->{if(on){if(writer)click("machineAdaptationButton");phase++;}}
                case 4->{if(menu.isMachineAdaptationEnabled()){ack(2);phase++;}}
                case 5->{
                    // The dedicated server replaces both containers. A fresh normal screen is opened
                    // and clicked above; the changed container ID is proof this is a new server menu.
                    if(mc.player.containerMenu.containerId!=firstContainer){require(menu.isMachineAdaptationEnabled(),"reopened menu lost state");ack(3);phase++;}
                }
                case 6->{if(passed){
                    // Menu sync can finish while the real EMI/JEI loader is still registering
                    // recipes. Keep the original session budget and wait for its completion
                    // before the unload/restore probe and normal client shutdown.
                    if (!(boolean)Class.forName("dev.emi.emi.runtime.EmiReloadManager")
                            .getMethod("isLoaded").invoke(null)) return;
                    checkJemiLifecycle(mc);
                    Screenshot.grab(mc.gameDirectory,"multiplayer-on-reopened.png",mc.getMainRenderTarget(),m->{});
                    if (Boolean.getBoolean("ae2lt.confirmProbe")) phase = 7;
                    else finish(mc);
                }}
                default->throw new AssertionError("unknown multiplayer client phase "+phase);
            }
            if(firstContainer==-1)firstContainer=mc.player.containerMenu.containerId;
        }catch(Throwable failure){fail(failure);}
    }
    private static void finish(Minecraft mc) throws Exception {
        Files.writeString(Path.of("multiplayer-result.txt"),"PASS: real dedicated TCP session; two independent players; actual advanced screen clicks; server checks; both clients received off/on GuiSync; fresh menu reopen; JEMI runtime unload/restore hover."
                + (Boolean.getBoolean("ae2lt.confirmProbe") ? " Native confirmation exact bytes, algorithm, preview restoration and LT report rendered." : "") + "\n");
        done=true;mc.stop();
    }
    private static int firstContainer=-1;
    private static void checkJemiLifecycle(Minecraft mc) throws Exception {
        var type=Class.forName("dev.emi.emi.jemi.JemiPlugin");
        var runtime=type.getField("runtime");
        var saved=runtime.get(null);
        require(saved!=null,"JEI runtime did not become available");
        var hover=type.getDeclaredMethod("lambda$register$5",net.minecraft.client.gui.screens.Screen.class,int.class,int.class);
        hover.setAccessible(true);
        try {
            runtime.set(null,null);
            var empty=(dev.emi.emi.api.stack.EmiStackInteraction)hover.invoke(null,mc.screen,0,0);
            require(empty.isEmpty(),"unavailable JEI runtime exposed a stale hover ingredient");
        } finally { runtime.set(null,saved); }
        require(hover.invoke(null,mc.screen,0,0)!=null,"restored JEI hover provider failed");
    }
    private static void fail(Throwable failure){
        if(done)return;done=true;failure.printStackTrace();
        try{Files.writeString(Path.of("multiplayer-result.txt"),"FAIL: "+failure+"\n");}catch(Exception ignored){}
        Minecraft.getInstance().stop();
    }
}
