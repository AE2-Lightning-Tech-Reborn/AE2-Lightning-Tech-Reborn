package com.moakiee.ae2lt.client.machine;

import com.moakiee.ae2lt.client.ldlib.LdlibAeSubScreen;

import org.lwjgl.glfw.GLFW;

import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.ItemLike;

import appeng.api.orientation.RelativeSide;
import appeng.blockentity.AEBaseBlockEntity;
import appeng.blockentity.networking.CableBusBlockEntity;
import appeng.client.gui.AEBaseScreen;
import com.moakiee.ae2lt.client.ldlib.LdlibAe2Button;
import appeng.menu.SlotSemantics;

import com.moakiee.ae2lt.menu.MachineOutputConfigMenu;
import appeng.menu.AEBaseMenu;

public class MachineOutputConfigScreen<M extends AEBaseMenu & MachineOutputConfigMenu, P extends AEBaseScreen<M>>
        extends LdlibAeSubScreen<M, P> {
    private final String buttonTooltipPrefix;
    private final MachineOutputSideButton frontButton;
    private final MachineOutputSideButton backButton;
    private final MachineOutputSideButton leftButton;
    private final MachineOutputSideButton rightButton;
    private final MachineOutputSideButton topButton;
    private final MachineOutputSideButton bottomButton;

    public MachineOutputConfigScreen(P parent, Component label) {
        this(parent, label, "/screens/overload_processing_output_config.json",
                "ae2lt.gui.overload_factory.output_side.clear", "ae2lt.gui.overload_factory.output_side");
    }

    protected MachineOutputConfigScreen(P parent, Component label, String stylePath,
            String clearKey, String buttonTooltipPrefix) {
        super(parent, stylePath);
        this.buttonTooltipPrefix = buttonTooltipPrefix;

        widgets.add("return", LdlibAe2Button.back(label, this::returnToParent));

        widgets.add("clear", LdlibAe2Button.clear(Component.translatable(clearKey), menu::clientClearOutputSides));

        this.frontButton = addSideButton("front", RelativeSide.FRONT, "gui.tooltips.ae2.SideFront");
        this.backButton = addSideButton("back", RelativeSide.BACK, "gui.tooltips.ae2.SideBack");
        this.topButton = addSideButton("top", RelativeSide.TOP, "gui.tooltips.ae2.SideTop");
        this.rightButton = addSideButton("right", RelativeSide.RIGHT, "gui.tooltips.ae2.SideRight");
        this.bottomButton = addSideButton("bottom", RelativeSide.BOTTOM, "gui.tooltips.ae2.SideBottom");
        this.leftButton = addSideButton("left", RelativeSide.LEFT, "gui.tooltips.ae2.SideLeft");
    }

    @Override
    protected boolean shouldAddToolbar() {
        return false;
    }

    @Override
    protected void init() {
        super.init();
        for (var slot : menu.slots) {
            var semantic = menu.getSlotSemantic(slot);
            if (semantic != null) {
                setSlotsHidden(semantic, true);
            }
        }
    }

    @Override
    protected void updateBeforeRender() {
        super.updateBeforeRender();

        refreshButtonDisplays();
        this.frontButton.setOn(menu.isOutputSideEnabled(RelativeSide.FRONT));
        this.backButton.setOn(menu.isOutputSideEnabled(RelativeSide.BACK));
        this.leftButton.setOn(menu.isOutputSideEnabled(RelativeSide.LEFT));
        this.rightButton.setOn(menu.isOutputSideEnabled(RelativeSide.RIGHT));
        this.topButton.setOn(menu.isOutputSideEnabled(RelativeSide.TOP));
        this.bottomButton.setOn(menu.isOutputSideEnabled(RelativeSide.BOTTOM));
    }

    @Override
    public void onClose() {
        returnToParent();
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (keyCode == GLFW.GLFW_KEY_ESCAPE || this.minecraft.options.keyInventory.matches(keyCode, scanCode)) {
            returnToParent();
            return true;
        }

        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    private MachineOutputSideButton addSideButton(String widgetId, RelativeSide side, String labelKey) {
        var button = new MachineOutputSideButton(
                Component.translatable(labelKey),
                buttonTooltipPrefix,
                () -> menu.clientToggleOutputSide(side));
        widgets.add(widgetId, button);
        return button;
    }

    private void refreshButtonDisplays() {
        var host = menu.getHost();
        if (host == null || host.getLevel() == null) {
            return;
        }

        for (var relative : RelativeSide.values()) {
            var absolute = host.getOrientation().getSide(relative);
            getButton(relative).setDisplay(getDisplayIcon(host, absolute));
        }
    }

    private MachineOutputSideButton getButton(RelativeSide side) {
        return switch (side) {
            case FRONT -> frontButton;
            case BACK -> backButton;
            case LEFT -> leftButton;
            case RIGHT -> rightButton;
            case TOP -> topButton;
            case BOTTOM -> bottomButton;
        };
    }

    private ItemLike getDisplayIcon(AEBaseBlockEntity host, Direction side) {
        var level = host.getLevel();
        if (level == null) {
            return null;
        }

        var pos = host.getBlockPos().relative(side);
        var tile = level.getBlockEntity(pos);
        if (tile instanceof CableBusBlockEntity cable) {
            var part = cable.getPart(side.getOpposite());
            if (part != null) {
                return part.getPartItem();
            }
        }

        return level.getBlockState(pos).getBlock();
    }
}
