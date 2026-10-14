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
    private static boolean done;
    @SubscribeEvent public static void tick(TickEvent.ClientTickEvent event) {
        if(event.phase!=TickEvent.Phase.END || done) return;
        var mc=Minecraft.getInstance();
        if(++loading%100==0)LOGGER.info("GTO_CLIENT_QA SCREEN "+(mc.screen==null?"none":mc.screen.getClass().getName())+" world="+(mc.level!=null));
        if(mc.level==null || mc.player==null) return;
        if(++ticks<120)return;
        done=true;
        try {
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
