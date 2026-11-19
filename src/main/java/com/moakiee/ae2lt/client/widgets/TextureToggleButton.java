package com.moakiee.ae2lt.client.widgets;

import java.util.List;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import com.moakiee.ae2lt.AE2LightningTech;
import com.moakiee.ae2lt.api.client.TextureToolbarButton;

/** AE2LT's named toolbar styles, using the shared addon-facing renderer. */
public class TextureToggleButton extends TextureToolbarButton {
    public TextureToggleButton(ButtonType type, Listener listener) {
        super(type.textures, listener::onChange);
    }

    private static ResourceLocation texture(String path) {
        return ResourceLocation.fromNamespaceAndPath(
                AE2LightningTech.MODID, "textures/gui/buttons/" + path + ".png");
    }

    // ====== 3 态便捷 API:索引 2 = EJECT(若该 ButtonType 声明了第三贴图)======

    /** 显式切到第三态(若 {@link ButtonType} 不存在该状态,会落到最后一个有效状态)。 */
    public void setEjectState() {
        setStateIndex(2);
    }

    public boolean isEjectState() {
        return getStateIndex() == 2 && getStateCount() >= 3;
    }

    public void setTooltipEject(List<Component> lines) {
        setTooltipAt(2, lines);
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
        CPU_SELECTION(texture("algorithm_settings")),
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
