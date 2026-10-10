package com.moakiee.ae2lt.client;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

import appeng.client.gui.Icon;
import appeng.client.gui.style.Blitter;
import appeng.client.gui.widgets.IconButton;

import com.moakiee.ae2lt.AE2LightningTech;

/**
 * 按状态索引显示贴图和 tooltip。点击通知 {@link Listener}，由调用方更新状态。
 * {@link ButtonType} 定义各状态贴图；boolean 设置映射到索引 0 和 1。
 */
public class TextureToggleButton extends IconButton {

    private final List<ResourceLocation> textures;
    private final List<List<Component>> tooltips;
    private final Listener listener;

    private int stateIndex;

    public TextureToggleButton(ButtonType type, Listener listener) {
        super(btn -> listener.onChange(0));
        // Let AE2's IconButton render its own 1.20.1 toolbar chrome. The
        // background icon is used as a harmless placeholder for the custom
        // texture rendered on top of it.
        setDisableBackground(true);
        this.textures = type.textures;
        this.tooltips = new ArrayList<>(type.textures.size());
        for (int i = 0; i < type.textures.size(); i++) {
            this.tooltips.add(Collections.emptyList());
        }
        this.listener = listener;
    }

    private static ResourceLocation texture(String path) {
        return new ResourceLocation(
                AE2LightningTech.MODID, "textures/gui/buttons/" + path + ".png");
    }

    /** 通用状态切换入口,索引会被 clamp 到合法范围。 */
    public void setStateIndex(int index) {
        if (this.textures.isEmpty()) {
            this.stateIndex = 0;
            return;
        }
        if (index < 0) index = 0;
        if (index >= this.textures.size()) index = this.textures.size() - 1;
        this.stateIndex = index;
    }

    public int getStateIndex() {
        return this.stateIndex;
    }

    /** 通用 tooltip 设置;对越界索引静默忽略。 */
    public void setTooltipAt(int index, List<Component> lines) {
        if (index < 0 || index >= this.tooltips.size()) {
            return;
        }
        this.tooltips.set(index, lines == null ? Collections.emptyList() : lines);
    }

    // ====== 2 态兼容 API:索引 0 = OFF,索引 1 = ON ======

    public void setState(boolean isOn) {
        setStateIndex(isOn ? 1 : 0);
    }

    public void setTooltipOn(List<Component> lines) {
        setTooltipAt(1, lines);
    }

    public void setTooltipOff(List<Component> lines) {
        setTooltipAt(0, lines);
    }

    @Override
    protected Icon getIcon() {
        return Icon.TOOLBAR_BUTTON_BACKGROUND;
    }

    @Override
    public void onPress() {
        this.listener.onChange(this.stateIndex);
    }

    @Override
    public void renderWidget(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        if (!this.visible) {
            return;
        }

        // This calls AE2 1.20.1's native IconButton renderer, including its
        // focus outline and exact 16x16 toolbar footprint.
        boolean wasActive = this.active;
        this.active = true; // Native backgrounds do not dim when a button is disabled.
        super.renderWidget(guiGraphics, mouseX, mouseY, partialTick);
        this.active = wasActive;

        if (this.textures.isEmpty()) {
            return;
        }

        int idx = Math.min(this.stateIndex, this.textures.size() - 1);
        var blitter = Blitter.texture(this.textures.get(idx), 16, 16).src(0, 0, 16, 16);
        if (!this.active) {
            blitter.opacity(0.5f);
        }
        blitter.dest(getX(), getY()).blit(guiGraphics);
    }

    @Override
    public List<Component> getTooltipMessage() {
        if (this.tooltips.isEmpty()) {
            return Collections.emptyList();
        }
        int idx = Math.min(this.stateIndex, this.tooltips.size() - 1);
        return this.tooltips.get(idx);
    }

    public enum ButtonType {
        // 索引: 0 = OFF/normal, 1 = ON/alt, 2 = EJECT(可选)
        MODE(texture("wired_mode"), texture("wireless_mode")),
        // 返回按钮 (3 态):对应过载样板供应器的 ReturnMode { OFF, AUTO, EJECT }。
        AUTO_RETURN(texture("auto_input_off"), texture("auto_input_on"), texture("auto_input_ejection")),
        WIRELESS_STRATEGY(texture("single_target"), texture("even_distribution")),
        FILTERED_IMPORT(texture("filtered_import_off"), texture("filtered_import_on")),
        SPEED(texture("speed_normal"), texture("speed_fast")),
        // 输出按钮 (2 态):对应 ExportMode { OFF, AUTO }。
        AUTO_EXPORT(texture("auto_export_off"), texture("auto_export_on")),
        // 输入按钮 (3 态):对应 ImportMode { OFF, AUTO, EJECT }。
        AUTO_IMPORT(texture("auto_input_off"), texture("auto_input_on"), texture("auto_input_ejection")),
        PATTERN_MIGRATION(texture("auto_input_off"), texture("auto_input_ejection")),
        // 过载电源 PowerMode { NORMAL=off, OVERLOAD=on }。
        OVERLOAD_MODE(texture("overloaded_off"), texture("overloaded_on")),
        // 水晶催化器 Mode { CRYSTAL=off, DUST=on }。
        CRYSTAL_CATALYZER_MODE(texture("catalyzer_crystal_mode"), texture("catalyzer_dust_mode")),
        // 频率配置入口。机器与无线终端共用同一图标和工具栏样式。
        FREQUENCY_BIND(texture("frequency_select")),
        // 天枢有线与无线样板编码终端共用的库存维持入口。
        INVENTORY_MAINTENANCE(texture("inventory_maintenance")),
        // 多方块控制器左侧操作栏。
        QUICK_BUILD(texture("quick_build")),
        CPU_SELECTION(
                texture("algorithm_settings"),
                texture("lightning_high_voltage"),
                texture("quick_compute_off")),
        QUICK_COMPUTE(texture("quick_compute_off"), texture("quick_compute_on")),
        PATTERN_STORAGE_UPGRADE(texture("pattern_storage_upgrade")),
        ADAPTIVE_BATCH(texture("adaptive_batch_off"), texture("adaptive_batch_on"));

        private final List<ResourceLocation> textures;

        ButtonType(ResourceLocation texture) {
            this.textures = List.of(texture);
        }

        ButtonType(ResourceLocation textureOff, ResourceLocation textureOn) {
            this.textures = List.of(textureOff, textureOn);
        }

        ButtonType(ResourceLocation textureOff, ResourceLocation textureOn, ResourceLocation textureEject) {
            this.textures = List.of(textureOff, textureOn, textureEject);
        }
    }

    /**
     * 按下时由按钮回传"当前正在显示的状态索引",调用方通常忽略它,
     * 直接发个 cycle 包让服务端把状态向后推进一格。
     */
    @FunctionalInterface
    public interface Listener {
        void onChange(int previousStateIndex);
    }
}
