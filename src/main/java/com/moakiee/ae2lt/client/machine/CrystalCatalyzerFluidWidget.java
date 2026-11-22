package com.moakiee.ae2lt.client.machine;

import java.util.function.IntSupplier;
import java.util.function.Supplier;

import com.lowdragmc.lowdraglib2.gui.ui.UIElement;
import com.moakiee.ae2lt.client.ldlib.LdlibScreens;
import com.moakiee.ae2lt.menu.CrystalCatalyzerMenu;
import net.neoforged.neoforge.fluids.FluidStack;

public class CrystalCatalyzerFluidWidget extends MachineFluidWidget {
    /** Inner tank width in pixels (between the bright inner frame lines). */
    public static final int TANK_INNER_WIDTH = 16;
    /** Inner tank height in pixels (y=18..y=70 inclusive inside the inner frame). */
    public static final int TANK_INNER_HEIGHT = 53;

    /** Widget origin is absolute (26, 18) in the GUI background texture. */
    private static final int TANK_SRC_X = 26;
    private static final int TANK_SRC_Y = 18;

    /** Minor tick: 3 bright pixels wide at x=39..41, every 10 rows starting y=21. */
    private static final int MINOR_TICK_SRC_X = 39;
    private static final int MINOR_TICK_WIDTH = 3;
    private static final int[] MINOR_TICK_SRC_YS = { 21, 31, 41, 51, 61 };

    /** Major tick: 5 bright pixels wide at x=37..41, every 10 rows starting y=26. */
    private static final int MAJOR_TICK_SRC_X = 37;
    private static final int MAJOR_TICK_WIDTH = 5;
    private static final int[] MAJOR_TICK_SRC_YS = { 26, 36, 46, 56, 66 };

    public CrystalCatalyzerFluidWidget(CrystalCatalyzerMenu menu,
            Supplier<FluidStack> fluidSupplier, IntSupplier capacitySupplier) {
        super(TANK_INNER_WIDTH, TANK_INNER_HEIGHT, fluidSupplier, capacitySupplier,
                "ae2lt.gui.crystal_catalyzer.fluid.tooltip", menu::clientClearFluidTank,
                menu::clientExtractFluid, menu::clientInsertFluid, tickMarks());
    }

    private static UIElement tickMarks() {
        var overlay = new UIElement();
        overlay.setAllowHitTest(false);
        addTicks(overlay, MINOR_TICK_SRC_X, MINOR_TICK_WIDTH, MINOR_TICK_SRC_YS);
        addTicks(overlay, MAJOR_TICK_SRC_X, MAJOR_TICK_WIDTH, MAJOR_TICK_SRC_YS);
        return LdlibScreens.at(overlay, 0, 0, TANK_INNER_WIDTH, TANK_INNER_HEIGHT);
    }

    private static void addTicks(UIElement overlay, int srcX, int width, int[] sourceRows) {
        for (int srcY : sourceRows) {
            var sprite = CrystalCatalyzerScreen.ATLAS.slice(srcX, srcY, width, 1);
            var tick = new UIElement().style(style -> style.background(sprite));
            tick.setAllowHitTest(false);
            overlay.addChild(LdlibScreens.at(tick, srcX - TANK_SRC_X, srcY - TANK_SRC_Y, width, 1));
        }
    }
}
