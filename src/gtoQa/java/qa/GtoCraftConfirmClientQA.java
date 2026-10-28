package qa;

import java.lang.reflect.Field;
import java.math.BigInteger;
import java.util.List;
import java.util.Map;

import appeng.api.networking.IGridNode;
import appeng.api.networking.security.IActionHost;
import appeng.menu.ISubMenu;
import appeng.api.storage.ISubMenuHost;
import appeng.client.gui.TextOverride;
import appeng.client.gui.WidgetContainer;
import appeng.client.gui.me.crafting.CraftConfirmScreen;
import appeng.client.gui.style.StyleManager;
import appeng.menu.me.crafting.CraftConfirmMenu;
import appeng.menu.me.crafting.CraftingPlanSummary;
import com.moakiee.ae2lt.client.crafting.AE2LtCraftConfirmScreen;
import com.moakiee.thunderbolt.ae2.crafting.ExactAmountFormatter;
import com.moakiee.thunderbolt.ae2.crafting.ExactPlanReport;
import com.moakiee.thunderbolt.ae2.crafting.ExactPlanReports;
import com.moakiee.thunderbolt.core.crafting.algorithm.menu.CraftingAlgorithmNameMenu;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;

/** Render both native and LT confirmations after the real dedicated-server menu checks. */
final class GtoCraftConfirmClientQA {
    private static final BigInteger BYTES = BigInteger.ONE.shiftLeft(100).add(BigInteger.valueOf(37));
    private static final String ALGORITHM = "QA preserved planner";
    private static int phase, ticks;
    private static UiMenu menu;

    static boolean tick(Minecraft mc) throws Exception {
        if (++ticks % 15 != 0) return false;
        switch (phase++) {
            case 0 -> {
                menu = new UiMenu(mc.player.getInventory());
                menu.setPlan(new CraftingPlanSummary(73, false, List.of()));
                mc.player.containerMenu = menu;
                mc.setScreen(new CraftConfirmScreen(menu, mc.player.getInventory(), Component.empty(),
                        StyleManager.loadStyleDoc("/screens/craft_confirm.json")));
            }
            case 1 -> {
                require(mc.screen instanceof CraftConfirmScreen && !(mc.screen instanceof AE2LtCraftConfirmScreen),
                        "native confirmation was unexpectedly replaced");
                require(nativeTitle(mc).getString().contains(ALGORITHM), "Core algorithm name hook was lost");
                Screenshot.grab(mc.gameDirectory, "confirm-native-algorithm.png", mc.getMainRenderTarget(), message -> { });
                setExact(false);
            }
            case 2 -> {
                require(nativeTitle(mc).getString().contains(ExactAmountFormatter.compact(BYTES, 1)),
                        "native title truncated the exact byte count");
                require(!status(mc).equals(Component.translatable("gui.thunderbolt.exact_plan.preview")),
                        "executable exact summary was incorrectly marked as preview");
                setExact(true);
            }
            case 3 -> {
                require(nativeTitle(mc).getString().contains(ExactAmountFormatter.compact(BYTES, 1)),
                        "simulated exact title truncated its byte count");
                require(status(mc).equals(Component.translatable("gui.thunderbolt.exact_plan.preview")),
                        "Core simulated exact preview status hook was lost: " + status(mc));
                Screenshot.grab(mc.gameDirectory, "confirm-native-exact-preview.png", mc.getMainRenderTarget(), message -> { });
                menu.setPlan(new CraftingPlanSummary(73, false, List.of()));
            }
            case 4 -> {
                require(nativeTitle(mc).getString().contains(ALGORITHM), "ordinary title retained stale exact bytes");
                require(!status(mc).equals(Component.translatable("gui.thunderbolt.exact_plan.preview")),
                        "ordinary plan retained stale preview status");
                field(menu, "ae2lt$showReport").setBoolean(menu, true);
            }
            case 5 -> {
                require(mc.screen instanceof AE2LtCraftConfirmScreen, "native confirmation did not route to LT report");
                var report = (AE2LtCraftConfirmScreen) mc.screen;
                var widgets = ((WidgetContainer) field(report, "widgets").get(report)).getWidgets();
                var follow = widgets.get("startWithFollow");
                boolean dev25 = net.minecraftforge.fml.ModList.get().getModContainerById("ae2")
                        .orElseThrow().getModInfo().getVersion().toString().equals("15.2610.40");
                require((follow != null) == dev25, "unexpected native follow action availability");
                if (follow != null) require(follow.visible && follow.getX() >= report.getGuiLeft()
                                && follow.getX() + follow.getWidth() <= report.getGuiLeft() + report.getXSize()
                                && follow.getY() >= report.getGuiTop()
                                && follow.getY() + follow.getHeight() <= widgets.get("selectCpu").getY(),
                        "native follow action is detached from the report or overlaps its CPU selector");
                Screenshot.grab(mc.gameDirectory, "confirm-lt-report.png", mc.getMainRenderTarget(), message -> { });
                return true;
            }
            default -> throw new AssertionError("unexpected confirmation QA phase");
        }
        return false;
    }

    private static void setExact(boolean simulation) {
        var exact = new CraftingPlanSummary(Long.MAX_VALUE, simulation, List.of());
        ExactPlanReports.attach(exact, new ExactPlanReport(BYTES, Map.of(), false, true));
        menu.setPlan(exact);
    }

    private static Component nativeTitle(Minecraft mc) throws Exception {
        try {
            return ((AbstractWidget) field(mc.screen, "dialogTitle").get(mc.screen)).getMessage();
        } catch (NoSuchFieldException legacyTitle) {
            return text(mc, "dialog_title");
        }
    }

    @SuppressWarnings("unchecked")
    private static Component text(Minecraft mc, String id) throws Exception {
        var overrides = (Map<String, TextOverride>) field(mc.screen, "textOverrides").get(mc.screen);
        return overrides.get(id).getContent();
    }

    private static Component status(Minecraft mc) throws Exception { return text(mc, "cpu_status"); }

    private static Field field(Object instance, String name) throws Exception {
        for (Class<?> type = instance.getClass(); type != null; type = type.getSuperclass()) {
            try {
                var field = type.getDeclaredField(name);
                field.setAccessible(true);
                return field;
            } catch (NoSuchFieldException ignored) { }
        }
        throw new NoSuchFieldException(name);
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }

    private static final class UiMenu extends CraftConfirmMenu implements CraftingAlgorithmNameMenu {
        UiMenu(Inventory inventory) { super(18, inventory, new Host()); }
        @Override public Component thunderbolt$getCraftingAlgorithmName() { return Component.literal(ALGORITHM); }
        @Override public void removed(Player player) { }
    }

    private static final class Host extends BlockEntity implements ISubMenuHost, IActionHost {
        Host() { super(BlockEntityType.CHEST, BlockPos.ZERO, Blocks.CHEST.defaultBlockState()); }
        public IGridNode getActionableNode() { return null; }
        public void returnToMainMenu(Player player, ISubMenu menu) { }
        public ItemStack getMainMenuIcon() { return new ItemStack(Items.CRAFTING_TABLE); }
    }
}
