package qa;
import java.math.BigInteger;
import appeng.api.stacks.*;
import com.moakiee.ae2lt.compat.gto.GtoTerminalAmounts;
import com.moakiee.thunderbolt.ae2.crafting.ExactAmountFormatter;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.world.item.Items;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
@Mod.EventBusSubscriber(modid="gto_compat_qa",value=Dist.CLIENT)
public final class GtoClientQA {
    static { org.apache.logging.log4j.core.config.Configurator.setLevel("qa",org.apache.logging.log4j.Level.INFO); }
    private static final org.slf4j.Logger LOGGER = com.mojang.logging.LogUtils.getLogger();
    private static int ticks;
    private static int loading;
    private static int menuTicks;
    private static boolean connecting;
    private static boolean done;
    @SubscribeEvent public static void tick(TickEvent.ClientTickEvent event) {
        if(event.phase!=TickEvent.Phase.END || done) return;
        var mc=Minecraft.getInstance();
        String connect = System.getProperty("ae2lt.gtoQaConnect");
        String world = System.getProperty("ae2lt.gtoQaWorld");
        if (connect == null && world != null && !connecting
                && mc.screen instanceof net.minecraft.client.gui.screens.TitleScreen && ++menuTicks >= 400) {
            connecting = true;
            LOGGER.info("GTO_CLIENT_QA opening isolated world after initial resources and main menu settled");
            mc.createWorldOpenFlows().loadLevel(mc.screen, world);
        }
        if (connect != null && !connecting && mc.screen instanceof net.minecraft.client.gui.screens.TitleScreen
                && ++menuTicks >= 400) {
            connecting = true;
            LOGGER.info("GTO_CLIENT_QA connecting after initial resources and main menu settled");
            net.minecraft.client.gui.screens.ConnectScreen.startConnecting(mc.screen, mc,
                    net.minecraft.client.multiplayer.resolver.ServerAddress.parseString(connect),
                    new net.minecraft.client.multiplayer.ServerData("GTO QA", connect, false), false);
        }
        if (mc.screen instanceof net.minecraft.client.gui.screens.DisconnectedScreen) {
            for (var field : mc.screen.getClass().getDeclaredFields()) {
                if (net.minecraft.network.chat.Component.class.isAssignableFrom(field.getType())) {
                    try {
                        field.setAccessible(true);
                        LOGGER.error("GTO_CLIENT_QA disconnected: {}", field.get(mc.screen));
                    } catch (ReflectiveOperationException failure) { LOGGER.error("Disconnect inspection failed", failure); }
                }
            }
            LOGGER.info("GTO_CLIENT_QA FAIL disconnected before joining world");
            done = true;
            mc.stop();
            return;
        }
        if (Boolean.getBoolean("ae2lt.bigTerminalProbe") || Boolean.getBoolean("ae2lt.multiblockLayoutProbe")
                || Boolean.getBoolean("ae2lt.emiMultiblockProbe") || Boolean.getBoolean("ae2lt.phaseFreecamClientProbe")
                || Boolean.getBoolean("ae2lt.providerGuiProbe")) return;
        if(++loading%100==0)LOGGER.info("GTO_CLIENT_QA SCREEN "+(mc.screen==null?"none":mc.screen.getClass().getName())+" world="+(mc.level!=null));
        if(mc.level==null || mc.player==null) return;
        if(++ticks<120)return;
        done=true;
        try {
            if (Boolean.getBoolean("ae2lt.gtoMixinAudit")) GtoMixinAudit.run();
            var key=AEItemKey.of(Items.DIAMOND);var amount=BigInteger.TEN.pow(30);
            var nativeText=key.formatAmount(1,AmountFormat.SLOT);
            GtoTerminalAmounts.with(key,amount,()->{
                if(!key.formatAmount(1,AmountFormat.SLOT).equals(ExactAmountFormatter.slot(amount,key.getAmountPerUnit())))
                    throw new AssertionError("exact terminal quantity mixin did not apply");
            });
            if(!nativeText.equals(key.formatAmount(1,AmountFormat.SLOT)))throw new AssertionError("terminal scope leaked");
            Class.forName("appeng.me.cells.CreativeCellInventory");
            Class.forName("appeng.client.gui.me.common.MEStorageScreen");
            Class.forName("com.moakiee.ae2lt.client.tianshu.TianshuMaintenanceTermScreen");
            Screenshot.grab(mc.gameDirectory,"gto-compat-client.png",mc.getMainRenderTarget(),message->{});
            LOGGER.info("GTO_CLIENT_QA PASS connected world rendered; exact terminal formatting; screen transforms");
        } catch(Throwable failure) {LOGGER.error("QA failure",failure);LOGGER.info("GTO_CLIENT_QA FAIL "+failure);}
        mc.stop();
    }
}
